package registry

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"regexp"
	"sync"
	"time"
)

const keyHeader = "X-OpenRoots-Registry-Key"

var uuidPattern = regexp.MustCompile(`^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$`)

type Snapshot struct {
	Devices []Device `json:"devices"`
}

type Device struct {
	DeviceID string   `json:"deviceId"`
	Sensors  []Sensor `json:"sensors"`
}

type Sensor struct {
	SensorID    string `json:"sensorId"`
	ChannelName string `json:"channelName"`
}

type DeviceSchema struct {
	Sensors map[string]string
}

type Registry struct {
	url       string
	apiKey    string
	client    *http.Client
	mu        sync.RWMutex
	devices   map[string]DeviceSchema
	updatedAt time.Time
}

func New(url, apiKey string) *Registry {
	return &Registry{
		url:    url,
		apiKey: apiKey,
		client: &http.Client{Timeout: 5 * time.Second},
		devices: make(map[string]DeviceSchema),
	}
}

func (r *Registry) Refresh(ctx context.Context) error {
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, r.url, nil)
	if err != nil {
		return fmt.Errorf("build registry request: %w", err)
	}
	request.Header.Set(keyHeader, r.apiKey)

	response, err := r.client.Do(request)
	if err != nil {
		return fmt.Errorf("request device registry: %w", err)
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		_, _ = io.Copy(io.Discard, io.LimitReader(response.Body, 4096))
		return fmt.Errorf("device registry returned HTTP %d", response.StatusCode)
	}

	var snapshot Snapshot
	decoder := json.NewDecoder(io.LimitReader(response.Body, 2<<20))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&snapshot); err != nil {
		return fmt.Errorf("decode device registry: %w", err)
	}
	if err := decoder.Decode(new(any)); err != io.EOF {
		return fmt.Errorf("device registry response contains trailing JSON")
	}

	devices := make(map[string]DeviceSchema, len(snapshot.Devices))
	for _, device := range snapshot.Devices {
		if !uuidPattern.MatchString(device.DeviceID) {
			return fmt.Errorf("registry returned a non-canonical device UUID")
		}
		if _, exists := devices[device.DeviceID]; exists {
			return fmt.Errorf("registry returned duplicate device IDs")
		}
		sensors := make(map[string]string, len(device.Sensors))
		for _, sensor := range device.Sensors {
			channel := sensor.ChannelName
			if !uuidPattern.MatchString(sensor.SensorID) || channel == "" {
				return fmt.Errorf("registry returned invalid sensor metadata for device %s", device.DeviceID)
			}
			if _, exists := sensors[channel]; exists {
				return fmt.Errorf("registry returned duplicate channel names for device %s", device.DeviceID)
			}
			sensors[channel] = sensor.SensorID
		}
		devices[device.DeviceID] = DeviceSchema{Sensors: sensors}
	}

	r.mu.Lock()
	r.devices = devices
	r.updatedAt = time.Now().UTC()
	r.mu.Unlock()
	return nil
}

func (r *Registry) Run(ctx context.Context, interval time.Duration, logger interface{ Printf(string, ...any) }) {
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			if err := r.Refresh(ctx); err != nil {
				logger.Printf("device registry refresh failed: %v", err)
			}
		}
	}
}

func (r *Registry) Device(deviceID string) (DeviceSchema, bool) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	schema, exists := r.devices[deviceID]
	return schema, exists
}

func (r *Registry) WaitForDevice(ctx context.Context, deviceID string, wait time.Duration) (DeviceSchema, bool) {
	deadline := time.NewTimer(wait)
	defer deadline.Stop()
	ticker := time.NewTicker(250 * time.Millisecond)
	defer ticker.Stop()
	for {
		if schema, exists := r.Device(deviceID); exists {
			return schema, true
		}
		select {
		case <-ctx.Done():
			return DeviceSchema{}, false
		case <-deadline.C:
			return DeviceSchema{}, false
		case <-ticker.C:
		}
	}
}

func (r *Registry) LastUpdated() time.Time {
	r.mu.RLock()
	defer r.mu.RUnlock()
	return r.updatedAt
}
