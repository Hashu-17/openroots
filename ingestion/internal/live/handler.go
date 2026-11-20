package live

import (
	"context"
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"regexp"
	"time"

	"github.com/coder/websocket"
	"github.com/coder/websocket/wsjson"
	"github.com/openroots/ingestion/internal/auth"
)

// The protocol is shared with the Spring implementation; see docs/contracts/live-telemetry.md.
const (
	authTimeout     = 10 * time.Second
	maxAuthMessage  = 8 * 1024
	statusBadReq    = websocket.StatusCode(4400)
	statusUnauth    = websocket.StatusCode(4401)
	statusNotFound  = websocket.StatusCode(4404)
	statusAuthTimer = websocket.StatusCode(4408)
)

var deviceUUID = regexp.MustCompile(`^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$`)

type authMessage struct {
	Type  string `json:"type"`
	Token string `json:"token"`
}

// Handler streams one device's committed readings to an authenticated project member.
// Browsers cannot send an Authorization header on a WebSocket, so the first client
// message must be {"type":"auth","token":"<jwt>"}; tokens are never accepted in the URL.
func Handler(authorizer *auth.Authorizer, hub *Hub, allowedOrigins []string, logger *log.Logger) http.HandlerFunc {
	return func(writer http.ResponseWriter, request *http.Request) {
		deviceID := request.PathValue("deviceID")
		if !deviceUUID.MatchString(deviceID) {
			http.Error(writer, "deviceID must be a lowercase canonical UUID", http.StatusBadRequest)
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

		claims, status, reason := authenticate(request.Context(), connection, authorizer, deviceID)
		if status != 0 {
			_ = connection.Close(status, reason)
			return
		}

		subscription, accepted := hub.Subscribe(deviceID)
		if !accepted {
			_ = connection.Close(websocket.StatusTryAgainLater, "subscriber capacity reached")
			return
		}
		defer subscription.Close()

		// Keep reading control frames so ping, pong, and close messages work; any
		// further data messages from the client are ignored.
		ctx := connection.CloseRead(context.Background())
		if err := write(ctx, connection, map[string]string{"type": "ready"}); err != nil {
			return
		}
		expiry := time.NewTimer(time.Until(claims.ExpiresAt))
		defer expiry.Stop()
		for {
			select {
			case <-ctx.Done():
				return
			case <-expiry.C:
				_ = connection.Close(statusUnauth, "token expired")
				return
			case event, open := <-subscription.Events():
				if !open {
					_ = connection.Close(websocket.StatusTryAgainLater, "live subscription closed")
					return
				}
				if err := write(ctx, connection, event); err != nil {
					logger.Printf("websocket write failed for device %s: %v", deviceID, err)
					return
				}
			}
		}
	}
}

func authenticate(parent context.Context, connection *websocket.Conn, authorizer *auth.Authorizer, deviceID string) (auth.Claims, websocket.StatusCode, string) {
	connection.SetReadLimit(maxAuthMessage)
	ctx, cancel := context.WithTimeout(parent, authTimeout)
	defer cancel()
	messageType, data, err := connection.Read(ctx)
	if err != nil {
		return auth.Claims{}, statusAuthTimer, "authentication timeout"
	}
	var message authMessage
	if messageType != websocket.MessageText || json.Unmarshal(data, &message) != nil ||
		message.Type != "auth" || message.Token == "" {
		return auth.Claims{}, statusBadReq, "bad request"
	}
	claims, err := authorizer.AuthorizeRead(ctx, message.Token, deviceID)
	switch {
	case errors.Is(err, auth.ErrInvalidToken):
		return auth.Claims{}, statusUnauth, "unauthorized"
	case errors.Is(err, auth.ErrNotVisible):
		return auth.Claims{}, statusNotFound, "device not found"
	case err != nil:
		return auth.Claims{}, websocket.StatusInternalError, "authorization unavailable"
	}
	return claims, 0, ""
}

func write(ctx context.Context, connection *websocket.Conn, value any) error {
	writeContext, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()
	return wsjson.Write(writeContext, connection, value)
}
