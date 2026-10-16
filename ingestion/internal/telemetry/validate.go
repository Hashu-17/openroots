package telemetry

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"math"
	"regexp"
	"strings"
	"time"

	"github.com/openroots/ingestion/internal/registry"
)

const maxPayloadBytes = 64 * 1024

var deviceTopicID = regexp.MustCompile(`^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$`)

type Reading struct {
	SensorID   string
	Value      float64
	RecordedAt time.Time
}

func DeviceIDFromTopic(topic string) (string, error) {
	levels := strings.Split(topic, "/")
	if len(levels) != 5 || levels[0] != "openroots" || levels[1] != "v1" || levels[2] != "devices" || levels[4] != "telemetry" {
		return "", fmt.Errorf("topic does not match openroots/v1/devices/{deviceId}/telemetry")
	}
	if !deviceTopicID.MatchString(levels[3]) {
		return "", fmt.Errorf("topic has an invalid or non-canonical device UUID")
	}
	return levels[3], nil
}

func Validate(payload []byte, schema registry.DeviceSchema) ([]Reading, error) {
	if len(payload) == 0 || len(payload) > maxPayloadBytes {
		return nil, fmt.Errorf("payload must be between 1 and %d bytes", maxPayloadBytes)
	}
	if err := rejectDuplicateKeys(payload); err != nil {
		return nil, err
	}

	decoder := json.NewDecoder(bytes.NewReader(payload))
	var fields map[string]json.RawMessage
	if err := decoder.Decode(&fields); err != nil {
		return nil, fmt.Errorf("decode telemetry envelope: %w", err)
	}
	if err := ensureEOF(decoder); err != nil {
		return nil, err
	}
	if fields == nil {
		return nil, fmt.Errorf("telemetry envelope must be a JSON object")
	}
	for field := range fields {
		if field != "capturedAt" && field != "payload" {
			return nil, fmt.Errorf("unexpected telemetry field %q", field)
		}
	}
	capturedAtRaw, exists := fields["capturedAt"]
	if !exists {
		return nil, fmt.Errorf("capturedAt is required")
	}
	var capturedAtText string
	if err := json.Unmarshal(capturedAtRaw, &capturedAtText); err != nil {
		return nil, fmt.Errorf("capturedAt must be a string")
	}
	payloadRaw, exists := fields["payload"]
	if !exists {
		return nil, fmt.Errorf("payload is required")
	}
	var channels map[string]json.RawMessage
	if err := json.Unmarshal(payloadRaw, &channels); err != nil || channels == nil {
		return nil, fmt.Errorf("payload must be a JSON object")
	}

	capturedAt, err := time.Parse(time.RFC3339Nano, capturedAtText)
	if err != nil {
		return nil, fmt.Errorf("capturedAt must be an RFC 3339 timestamp with an explicit offset")
	}
	if len(channels) == 0 {
		return nil, fmt.Errorf("payload must be a non-empty object")
	}

	readings := make([]Reading, 0, len(channels))
	for channel, rawValue := range channels {
		sensorID, exists := schema.Sensors[channel]
		if !exists {
			return nil, fmt.Errorf("channel %q is not registered for this device", channel)
		}
		value, err := decodeFiniteNumber(rawValue)
		if err != nil {
			return nil, fmt.Errorf("channel %q: %w", channel, err)
		}
		readings = append(readings, Reading{
			SensorID:   sensorID,
			Value:      value,
			RecordedAt: capturedAt.UTC(),
		})
	}
	return readings, nil
}

func decodeFiniteNumber(raw json.RawMessage) (float64, error) {
	decoder := json.NewDecoder(bytes.NewReader(raw))
	decoder.UseNumber()
	var value any
	if err := decoder.Decode(&value); err != nil {
		return 0, fmt.Errorf("value must be a JSON number")
	}
	if err := ensureEOF(decoder); err != nil {
		return 0, fmt.Errorf("value must contain one JSON number")
	}
	number, ok := value.(json.Number)
	if !ok {
		return 0, fmt.Errorf("value must be a JSON number")
	}
	parsed, err := number.Float64()
	if err != nil || math.IsNaN(parsed) || math.IsInf(parsed, 0) {
		return 0, fmt.Errorf("value must be a finite DOUBLE PRECISION number")
	}
	return parsed, nil
}

func rejectDuplicateKeys(payload []byte) error {
	decoder := json.NewDecoder(bytes.NewReader(payload))
	decoder.UseNumber()
	if err := walkJSONValue(decoder); err != nil {
		return fmt.Errorf("invalid telemetry JSON: %w", err)
	}
	return ensureEOF(decoder)
}

func walkJSONValue(decoder *json.Decoder) error {
	token, err := decoder.Token()
	if err != nil {
		return err
	}

	delimiter, isDelimiter := token.(json.Delim)
	if !isDelimiter {
		return nil
	}
	switch delimiter {
	case '{':
		keys := make(map[string]struct{})
		for decoder.More() {
			keyToken, err := decoder.Token()
			if err != nil {
				return err
			}
			key, ok := keyToken.(string)
			if !ok {
				return fmt.Errorf("object key is not a string")
			}
			if _, exists := keys[key]; exists {
				return fmt.Errorf("duplicate object key %q", key)
			}
			keys[key] = struct{}{}
			if err := walkJSONValue(decoder); err != nil {
				return err
			}
		}
		closing, err := decoder.Token()
		if err != nil || closing != json.Delim('}') {
			return fmt.Errorf("object is not closed")
		}
	case '[':
		for decoder.More() {
			if err := walkJSONValue(decoder); err != nil {
				return err
			}
		}
		closing, err := decoder.Token()
		if err != nil || closing != json.Delim(']') {
			return fmt.Errorf("array is not closed")
		}
	default:
		return fmt.Errorf("unexpected JSON delimiter %q", delimiter)
	}
	return nil
}

func ensureEOF(decoder *json.Decoder) error {
	if _, err := decoder.Token(); err != io.EOF {
		if err == nil {
			return fmt.Errorf("trailing JSON content")
		}
		return fmt.Errorf("trailing JSON content: %w", err)
	}
	return nil
}
