package historyapi

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	_ "embed"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"regexp"
	"strconv"
	"time"

	"github.com/openroots/ingestion/internal/live"
	"github.com/openroots/ingestion/internal/registry"
	"github.com/openroots/ingestion/internal/storage"
)

const (
	defaultLimit    = 500
	maximumLimit    = 1000
	maximumRange    = 31 * 24 * time.Hour
	queryTimeout    = 10 * time.Second
	maximumCursor   = 256
	timestampLayout = time.RFC3339Nano
)

var canonicalUUID = regexp.MustCompile(`^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$`)

//go:embed viewer.html
var viewerHTML []byte

type Server struct {
	registry *registry.Registry
	store    *storage.Store
	logger   *log.Logger
}

type cursorPayload struct {
	RecordedAt string `json:"recordedAt"`
	SensorID   string `json:"sensorId"`
}

type historyResponse struct {
	DeviceID string                   `json:"deviceId"`
	From     time.Time                `json:"from"`
	To       time.Time                `json:"to"`
	Readings []storage.HistoryReading `json:"readings"`
	Next     string                   `json:"nextCursor,omitempty"`
}

func New(
	addr string,
	deviceRegistry *registry.Registry,
	store *storage.Store,
	hub *live.Hub,
	allowedOrigins []string,
	logger *log.Logger,
) *http.Server {
	api := &Server{registry: deviceRegistry, store: store, logger: logger}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", func(writer http.ResponseWriter, _ *http.Request) {
		writer.WriteHeader(http.StatusOK)
		_, _ = writer.Write([]byte("ok\n"))
	})
	mux.HandleFunc("GET /viewer", func(writer http.ResponseWriter, _ *http.Request) {
		writer.Header().Set("Content-Type", "text/html; charset=utf-8")
		writer.Header().Set("Cache-Control", "no-store")
		writer.WriteHeader(http.StatusOK)
		_, _ = writer.Write(viewerHTML)
	})
	mux.HandleFunc("GET /api/v1/devices/{deviceID}/telemetry", api.getDeviceHistory)
	mux.HandleFunc("GET /api/v1/devices/{deviceID}/telemetry/live", live.Handler(deviceRegistry, hub, allowedOrigins, logger))
	mux.HandleFunc("/", func(writer http.ResponseWriter, _ *http.Request) {
		writeError(writer, http.StatusNotFound, "route not found")
	})

	return &http.Server{
		Addr:              addr,
		Handler:           mux,
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       15 * time.Second,
		WriteTimeout:      15 * time.Second,
		IdleTimeout:       60 * time.Second,
	}
}

func (s *Server) getDeviceHistory(writer http.ResponseWriter, request *http.Request) {
	deviceID := request.PathValue("deviceID")
	if !canonicalUUID.MatchString(deviceID) {
		writeError(writer, http.StatusBadRequest, "deviceID must be a lowercase canonical UUID")
		return
	}
	if _, exists := s.registry.Device(deviceID); !exists {
		writeError(writer, http.StatusNotFound, "device not found")
		return
	}

	query, err := url.ParseQuery(request.URL.RawQuery)
	if err != nil {
		writeError(writer, http.StatusBadRequest, "invalid query string")
		return
	}
	for key := range query {
		if key != "from" && key != "to" && key != "limit" && key != "cursor" {
			writeError(writer, http.StatusBadRequest, fmt.Sprintf("unknown query parameter %q", key))
			return
		}
	}
	fromText, ok := singleQueryValue(query, "from")
	if !ok {
		writeError(writer, http.StatusBadRequest, "from is required exactly once")
		return
	}
	toText, ok := singleQueryValue(query, "to")
	if !ok {
		writeError(writer, http.StatusBadRequest, "to is required exactly once")
		return
	}
	from, err := time.Parse(time.RFC3339Nano, fromText)
	if err != nil {
		writeError(writer, http.StatusBadRequest, "from must be RFC 3339 with an explicit UTC offset")
		return
	}
	to, err := time.Parse(time.RFC3339Nano, toText)
	if err != nil {
		writeError(writer, http.StatusBadRequest, "to must be RFC 3339 with an explicit UTC offset")
		return
	}
	if !from.Before(to) {
		writeError(writer, http.StatusBadRequest, "from must be earlier than to")
		return
	}
	if to.Sub(from) > maximumRange {
		writeError(writer, http.StatusBadRequest, "requested time range cannot exceed 31 days")
		return
	}

	limit := defaultLimit
	if limitText, present := query["limit"]; present {
		if len(limitText) != 1 {
			writeError(writer, http.StatusBadRequest, "limit may be specified once")
			return
		}
		limit, err = strconv.Atoi(limitText[0])
		if err != nil || limit < 1 || limit > maximumLimit {
			writeError(writer, http.StatusBadRequest, "limit must be between 1 and 1000")
			return
		}
	}

	var after *storage.HistoryCursor
	if cursorText, present := query["cursor"]; present {
		if len(cursorText) != 1 {
			writeError(writer, http.StatusBadRequest, "cursor may be specified once")
			return
		}
		after, err = decodeCursor(cursorText[0])
		if err != nil {
			writeError(writer, http.StatusBadRequest, err.Error())
			return
		}
		if after.RecordedAt.Before(from) || !after.RecordedAt.Before(to) {
			writeError(writer, http.StatusBadRequest, "cursor is outside the requested time range")
			return
		}
	}

	ctx, cancel := context.WithTimeout(request.Context(), queryTimeout)
	defer cancel()
	readings, err := s.store.History(ctx, deviceID, from, to, after, limit+1)
	if err != nil {
		s.logger.Printf("telemetry history query failed for device %s: %v", deviceID, err)
		writeError(writer, http.StatusInternalServerError, "could not fetch telemetry history")
		return
	}

	var next string
	if len(readings) > limit {
		readings = readings[:limit]
		last := readings[len(readings)-1]
		next, err = encodeCursor(storage.HistoryCursor{RecordedAt: last.RecordedAt, SensorID: last.SensorID})
		if err != nil {
			s.logger.Printf("could not encode telemetry history cursor: %v", err)
			writeError(writer, http.StatusInternalServerError, "could not prepare telemetry history page")
			return
		}
	}

	writeJSON(writer, http.StatusOK, historyResponse{
		DeviceID: deviceID,
		From:     from.UTC(),
		To:       to.UTC(),
		Readings: readings,
		Next:     next,
	})
}

func singleQueryValue(query url.Values, key string) (string, bool) {
	values, exists := query[key]
	if !exists || len(values) != 1 || values[0] == "" {
		return "", false
	}
	return values[0], true
}

func decodeCursor(encoded string) (*storage.HistoryCursor, error) {
	if len(encoded) == 0 || len(encoded) > maximumCursor {
		return nil, fmt.Errorf("cursor is malformed")
	}
	data, err := base64.RawURLEncoding.DecodeString(encoded)
	if err != nil {
		return nil, fmt.Errorf("cursor is malformed")
	}
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	var cursor cursorPayload
	if err := decoder.Decode(&cursor); err != nil {
		return nil, fmt.Errorf("cursor is malformed")
	}
	var extra any
	if err := decoder.Decode(&extra); err != io.EOF {
		return nil, fmt.Errorf("cursor is malformed")
	}
	if !canonicalUUID.MatchString(cursor.SensorID) {
		return nil, fmt.Errorf("cursor sensorId is malformed")
	}
	recordedAt, err := time.Parse(time.RFC3339Nano, cursor.RecordedAt)
	if err != nil {
		return nil, fmt.Errorf("cursor recordedAt is malformed")
	}
	return &storage.HistoryCursor{RecordedAt: recordedAt, SensorID: cursor.SensorID}, nil
}

func encodeCursor(cursor storage.HistoryCursor) (string, error) {
	data, err := json.Marshal(cursorPayload{
		RecordedAt: cursor.RecordedAt.UTC().Format(timestampLayout),
		SensorID:   cursor.SensorID,
	})
	if err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(data), nil
}

func writeError(writer http.ResponseWriter, status int, message string) {
	writeJSON(writer, status, map[string]string{"error": message})
}

func writeJSON(writer http.ResponseWriter, status int, value any) {
	data, err := json.Marshal(value)
	if err != nil {
		status = http.StatusInternalServerError
		data = []byte(`{"error":"could not encode response"}`)
	}
	writer.Header().Set("Content-Type", "application/json")
	writer.WriteHeader(status)
	_, _ = writer.Write(append(data, '\n'))
}
