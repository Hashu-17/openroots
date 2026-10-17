package live

import (
	"context"
	"log"
	"net/http"
	"regexp"
	"time"

	"github.com/coder/websocket"
	"github.com/coder/websocket/wsjson"
	"github.com/openroots/ingestion/internal/registry"
)

var deviceUUID = regexp.MustCompile(`^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$`)

func Handler(deviceRegistry *registry.Registry, hub *Hub, allowedOrigins []string, logger *log.Logger) http.HandlerFunc {
	return func(writer http.ResponseWriter, request *http.Request) {
		deviceID := request.PathValue("deviceID")
		if !deviceUUID.MatchString(deviceID) {
			http.Error(writer, "deviceID must be a lowercase canonical UUID", http.StatusBadRequest)
			return
		}
		if _, exists := deviceRegistry.Device(deviceID); !exists {
			http.Error(writer, "device not found", http.StatusNotFound)
			return
		}
		if request.URL.RawQuery != "" {
			http.Error(writer, "query parameters are not supported", http.StatusBadRequest)
			return
		}
		controller := http.NewResponseController(writer)
		if err := controller.SetReadDeadline(time.Time{}); err != nil {
			logger.Printf("could not clear websocket read deadline: %v", err)
			http.Error(writer, "websocket is unavailable", http.StatusInternalServerError)
			return
		}
		if err := controller.SetWriteDeadline(time.Time{}); err != nil {
			logger.Printf("could not clear websocket write deadline: %v", err)
			http.Error(writer, "websocket is unavailable", http.StatusInternalServerError)
			return
		}

		connection, err := websocket.Accept(writer, request, &websocket.AcceptOptions{
			OriginPatterns: allowedOrigins,
		})
		if err != nil {
			logger.Printf("websocket handshake rejected for device %s: %v", deviceID, err)
			return
		}
		defer connection.CloseNow()

		subscription, accepted := hub.Subscribe(deviceID)
		if !accepted {
			_ = connection.Close(websocket.StatusTryAgainLater, "subscriber capacity reached")
			return
		}
		defer subscription.Close()

		// Keep reading control frames so ping, pong, and close messages work even
		// though this endpoint only sends application data to the client.
		ctx := connection.CloseRead(context.Background())
		for {
			select {
			case <-ctx.Done():
				return
			case event, open := <-subscription.Events():
				if !open {
					_ = connection.Close(websocket.StatusTryAgainLater, "live subscription closed")
					return
				}
				writeContext, cancel := context.WithTimeout(ctx, 5*time.Second)
				err := wsjson.Write(writeContext, connection, event)
				cancel()
				if err != nil {
					logger.Printf("websocket write failed for device %s: %v", deviceID, err)
					return
				}
			}
		}
	}
}
