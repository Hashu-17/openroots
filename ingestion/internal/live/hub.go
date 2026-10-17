package live

import (
	"sync"
	"time"

	"github.com/openroots/ingestion/internal/telemetry"
)

const (
	maxSubscribers = 256
	queueCapacity  = 64
)

type Event struct {
	Type        string  `json:"type"`
	DeviceID    string  `json:"deviceId"`
	SensorID    string  `json:"sensorId"`
	ChannelName string  `json:"channelName"`
	Value       float64 `json:"value"`
	RecordedAt  string  `json:"recordedAt"`
}

type subscriber struct {
	events chan Event
}

type Subscription struct {
	events <-chan Event
	hub    *Hub
	device string
	client *subscriber
}

func (s *Subscription) Events() <-chan Event {
	return s.events
}

func (s *Subscription) Close() {
	s.hub.unsubscribe(s.device, s.client)
}

type Hub struct {
	mu          sync.Mutex
	subscribers map[string]map[*subscriber]struct{}
	count       int
	closed      bool
}

func NewHub() *Hub {
	return &Hub{subscribers: make(map[string]map[*subscriber]struct{})}
}

func (h *Hub) Subscribe(deviceID string) (*Subscription, bool) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if h.closed || h.count >= maxSubscribers {
		return nil, false
	}
	client := &subscriber{events: make(chan Event, queueCapacity)}
	if h.subscribers[deviceID] == nil {
		h.subscribers[deviceID] = make(map[*subscriber]struct{})
	}
	h.subscribers[deviceID][client] = struct{}{}
	h.count++
	return &Subscription{events: client.events, hub: h, device: deviceID, client: client}, true
}

// Publish is non-blocking. A client that cannot keep up is disconnected so it
// cannot slow down MQTT ingestion or database acknowledgements.
func (h *Hub) Publish(deviceID string, readings []telemetry.Reading) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if h.closed {
		return
	}
	clients := h.subscribers[deviceID]
	for _, reading := range readings {
		event := Event{
			Type:        "telemetry.reading",
			DeviceID:    deviceID,
			SensorID:    reading.SensorID,
			ChannelName: reading.ChannelName,
			Value:       reading.Value,
			RecordedAt:  reading.RecordedAt.UTC().Format(time.RFC3339Nano),
		}
		for client := range clients {
			select {
			case client.events <- event:
			default:
				h.removeLocked(deviceID, client)
			}
		}
	}
}

func (h *Hub) Close() {
	h.mu.Lock()
	defer h.mu.Unlock()
	if h.closed {
		return
	}
	h.closed = true
	for deviceID, clients := range h.subscribers {
		for client := range clients {
			h.removeLocked(deviceID, client)
		}
	}
}

func (h *Hub) unsubscribe(deviceID string, client *subscriber) {
	h.mu.Lock()
	defer h.mu.Unlock()
	h.removeLocked(deviceID, client)
}

func (h *Hub) removeLocked(deviceID string, client *subscriber) {
	clients := h.subscribers[deviceID]
	if _, exists := clients[client]; !exists {
		return
	}
	delete(clients, client)
	close(client.events)
	h.count--
	if len(clients) == 0 {
		delete(h.subscribers, deviceID)
	}
}
