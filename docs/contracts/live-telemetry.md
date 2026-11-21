# Live Telemetry WebSocket Contract

A signed-in user who can read a device's project can watch that device's readings arrive as they are stored. The same protocol is served by whichever service ingests telemetry: Spring on `spring-mqtt-ingestion`, the Go ingestion service on `go-mqtt-ingestion`. The frontend does not know which one answers; the reverse proxy (`frontend/nginx.conf`, or the Vite dev proxy) routes the path.

## Endpoint

```text
GET /api/v1/devices/{deviceId}/telemetry/live   (WebSocket upgrade)
```

`{deviceId}` is the device UUID. Query parameters are rejected, so a token can never be passed in the URL (URLs end up in proxy and server logs). The handshake is checked against an allowed-origin list (`OPENROOTS_LIVE_ALLOWED_ORIGINS`, default `http://localhost:5173,http://127.0.0.1:5173`).

## Authentication

Browsers cannot set an `Authorization` header on a WebSocket, so the client authenticates in-band. Its **first message** must arrive within 10 seconds:

```json
{"type": "auth", "token": "<access token from POST /api/v1/auth/login>"}
```

The server verifies the token with the same key and rules as the REST API, then applies the REST read rule: any member of the device's project may subscribe. Non-members get the same "not found" answer as for a missing device (BR-010), so the socket does not reveal whether a device exists.

On success the server sends:

```json
{"type": "ready"}
```

The server closes the socket when the access token expires; the client reconnects with a fresh token after the user signs in again. Messages from the client after authentication are ignored.

## Events

One message per stored channel value, sent only **after** the reading is committed to the database:

```json
{
  "type": "telemetry.reading",
  "deviceId": "7e2b2dc8-43c1-4e1f-b1ba-fd67d098f1a2",
  "sensorId": "2a1d8f0e-5b7c-4c11-9d6a-0f3e2b1c4d5e",
  "channelName": "temperature",
  "value": 22.4,
  "recordedAt": "2026-10-17T13:45:00Z"
}
```

`recordedAt` is the device's `capturedAt`. Values from one message share a `recordedAt`; clients group them into one record. The timestamp's fractional-second formatting can differ between implementations, so compare parsed times, not strings.

Delivery is best-effort. A subscriber that cannot keep up is disconnected rather than allowed to slow ingestion; the client reloads history from `GET /api/v1/telemetry?deviceId=...` when it needs a complete list.

## Close codes

| Code | Meaning | Client should |
|---|---|---|
| 4400 | Malformed path or first message | Stop |
| 4401 | Missing, invalid or expired token | Stop; sign in again |
| 4404 | Device not found, or not visible to this user | Stop |
| 4408 | No auth message within 10 seconds | Reconnect |
| 1013 | Subscriber capacity reached | Retry later |
| other | Network or server restart | Reconnect after a short delay |

## Limits

Each service accepts at most 256 concurrent subscribers. A subscriber whose outgoing buffer exceeds 64 KiB, or whose send blocks for 5 seconds, is disconnected.
