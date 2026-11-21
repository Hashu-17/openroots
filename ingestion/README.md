# OpenRoots Go telemetry service

The Go service subscribes to authenticated device topics on Mosquitto, validates
messages against the registered device and sensor schemas it fetches from
Spring, and writes accepted readings to TimescaleDB. It also serves two
authenticated endpoints on port `8081` (published to `127.0.0.1` by Compose):

- `GET /api/v1/devices/{deviceID}/telemetry?from=...&to=...` for bounded history.
- `GET /api/v1/devices/{deviceID}/telemetry/live`, a WebSocket that streams
  readings as they are committed.

Both check the same access token and project membership as Spring's REST API
(`OPENROOTS_JWT_SECRET` must match Spring's). The frontend reaches the live
socket through the reverse proxy. See
[live telemetry contract](../docs/contracts/live-telemetry.md).

## Raspberry Pi publisher on a trusted LAN

1. Copy `.env.example` to `.env` in the repository root if you have not already
   configured Compose. Set strong, private values for the database password,
   Mosquitto admin and ingestor passwords, and the Spring-to-Go registry key.
   Keep `.env` out of source control.
2. Find the server's stable LAN IP. Set `MQTT_BIND_ADDRESS` in `.env` to that
   address (for example, `192.168.1.20`). The default `127.0.0.1` keeps MQTT
   reachable only from the server itself. Permit TCP port `1883` from your
   trusted LAN in the host firewall; do not forward it from the internet.
3. Build and start the stack from the repository root:

   ```sh
   docker compose up --build -d
   ```

   Open `http://<server-lan-ip>:5173`, sign in, choose a project, and register
   a device with its sensor channel names. Issue the device a credential through
   the credentials API; the backend creates its broker account (username = device
   UUID, password = the credential secret). See
   [authentication](../docs/security/authentication.md#device-credentials).
   The publish topic is `openroots/v1/devices/<device UUID>/telemetry`.
4. Copy `ingestion/examples/raspberry_pi.env.example` to a private `.env` file
   on the Pi. Fill in the server LAN IP and the three values shown by the site;
   set `TELEMETRY_VALUES_JSON` to a JSON object mapping registered sensor
   channel names to numeric readings, for example
   `'{"temperature":22.5,"humidity":58}'`. Keep this file private.
5. On the Pi, install Python and run:

   ```sh
   python3 -m venv .venv
   . .venv/bin/activate
   python -m pip install 'paho-mqtt>=2,<3'
   set -a
   . ./.env
   set +a
   python /path/to/raspberry_pi_publisher.py
   ```

   The example repeatedly publishes all configured channel readings together
   in one non-retained JSON message at QoS 1. It defaults to one message every
   10 seconds; set `PUBLISH_INTERVAL_SECONDS=5` for a 5-second interval. Press
   Ctrl+C to stop. Each message gets one shared capture timestamp. A successful
   publish confirms Mosquitto accepted the credential and topic ACL. Go refreshes
   the registered device/sensor schema every few seconds; if the Pi publishes
   immediately after registration, wait briefly and retry if the service has
   not refreshed yet. To confirm Go stored the readings, sign in to the web app
   at `http://localhost:5173`, open the device's telemetry panel (it shows
   **Live** while streaming), or call the authenticated history endpoint above
   with a bearer token. Readings from one MQTT payload share one capture time.

The initial LAN setup uses plain MQTT without TLS and is intended for a trusted
development network only. Do not expose the broker or development API directly
to the public internet.
