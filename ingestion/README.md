# OpenRoots Go telemetry service

The Go service subscribes to authenticated device topics on Mosquitto, validates
messages against the registered device and sensor schemas it fetches from
Spring, and writes accepted readings to TimescaleDB. Its history API and simple
HTML viewer listen on port `8081` inside the Compose network.

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

   Open `http://<server-lan-ip>:5173`, choose a project, and register a device
   with its sensor channel names. Copy the MQTT username, password, and publish
   topic shown after registration. The password is shown only once.
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
   not refreshed yet. On the server, open `http://localhost:8081/viewer`, select
   the project and device, and load the last 24 hours to confirm that Go stored
   the readings. Readings from one MQTT payload are displayed together at their
   shared capture time.

The initial LAN setup uses plain MQTT without TLS and is intended for a trusted
development network only. Do not expose the broker or development API directly
to the public internet.
