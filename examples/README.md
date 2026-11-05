# MQTT smoke and load tests

`mqtt_smoke_test.py` checks the full path with one registered device: it
publishes one QoS 1 message, waits for broker acknowledgement, then polls the
backend API until the saved reading appears. `.github/workflows/mqtt-smoke.yml`
runs this check manually using a GitHub self-hosted runner on Windows.

`mqtt_load_test.py` keeps one MQTT connection open per device and publishes a
telemetry message from every device at the same time at startup, then repeats
at the configured interval. This models a synchronized fleet and measures the
publisher-side MQTT acknowledgement latency.

## Prepare a local test fleet

1. Start the local stack with `docker compose up --build` and register test
   devices in the app. Give every test device the same numeric sensor channel
   (the default is `temperature`). Use a development database because the run
   writes telemetry rows.
2. Save the registered UUIDs, one per line, in `examples/load-devices.txt`.
   Lines beginning with `#` and blank lines are ignored.
3. In the local broker, create a publisher client for each UUID, using that
   UUID as its MQTT username and the same password for each. The broker's
   `device-publisher` role limits each username to its matching device topic.
   For each UUID, run these commands, replacing the placeholders:

   ```powershell
   docker compose exec mosquitto mosquitto_ctrl -h 127.0.0.1 -u openroots-admin -P ADMIN_PASSWORD dynsec createClient DEVICE_UUID -p DEVICE_PASSWORD
   docker compose exec mosquitto mosquitto_ctrl -h 127.0.0.1 -u openroots-admin -P ADMIN_PASSWORD dynsec addClientRole DEVICE_UUID device-publisher
   ```

   Use the admin username/password from your local `.env`. Keep these test
   credentials on the development broker; do not reuse the admin password.
4. Install the Python dependency and set the shared test-device password:

   ```powershell
   python -m pip install -r examples/requirements-mqtt.txt
   $env:OPENROOTS_MQTT_DEVICE_PASSWORD = "DEVICE_PASSWORD"
   ```

## Run

From the repository root:

```powershell
python examples/mqtt_load_test.py --devices examples/load-devices.txt --period 10 --duration 300
```

To publish multiple channels, repeat `--channel` with names registered on
every device:

```powershell
python examples/mqtt_load_test.py --devices examples/load-devices.txt --period 10 --duration 300 --channel temperature --channel humidity
```

Defaults are 10 seconds, 120 seconds, QoS 1, and `temperature`. Configure
`--host`/`--port` for a different broker. The test reports successful PUBACKs,
failed publishes, rate, and PUBACK latency percentiles. PUBACK means the broker
accepted the message; it does not prove the backend committed it to PostgreSQL.
After the run, verify the expected telemetry rows in the development database
and watch backend, broker, and database CPU/memory, connection counts, queueing,
and logs. For a rough expectation, a 5-minute run at 10-second intervals
produces about 30 messages per device after the synchronized first publish
(about 3,000 messages for 100 devices), with minor variation at the end of the
run.

## Load-test practice

Start with a short smoke run, then run a baseline with a gradual ramp-up,
steady-state traffic, and a longer soak. Test synchronized bursts separately
from staggered device schedules: they stress different parts of the system.
Keep payload size and channel count realistic, repeat runs, and record the
server and database resource usage alongside throughput, p95/p99 latency, and
errors. Increase fleet size or message frequency in controlled steps until
latency, error rate, or resource use exceeds the limits you set. Run against a
disposable development/staging stack, since this script writes real telemetry
and creates concurrent broker connections.

## GitHub Actions smoke test setup

The first workflow is a manually triggered end-to-end smoke test for one
pre-registered device. It does not create or delete projects/devices. Start the
OpenRoots development stack on your Windows PC with `docker compose up -d
--build`; leave it running while invoking the workflow. The self-hosted runner
and stack are on the same PC, so its MQTT port and API are reachable at
`127.0.0.1:1883` and `http://127.0.0.1:8080`.

### Requirements

- Windows 10/11 PC with Git installed and available on `PATH`.
- Python 3.10+ installed for all users, with the `py` launcher available.
- Docker Desktop running Linux containers, with Docker Compose v2 available.
- A GitHub repository where Actions are enabled, plus an internet connection
  for the runner to connect outbound to GitHub and for the workflow to install
  `paho-mqtt` from PyPI. The runner needs no inbound port forwarding.

### Install and register a Windows runner

1. On GitHub, open the repository **Settings → Actions → Runners**, choose
   **New self-hosted runner**, then select **Windows** and **x64**. Follow the
   page's commands to download and extract the runner into a dedicated folder.
2. In that folder, configure it with the repository URL and one-time token
   shown by GitHub. Add the custom label `openroots-smoke` when prompted (or
   pass it to `config.cmd --labels openroots-smoke`). Keep the runner token
   private.
3. Start the runner with `run.cmd` and leave its window running. You can
   configure it as a Windows service for an always-on runner. Confirm GitHub
   shows it as **Idle/Online**. The workflow targets the labels
   `self-hosted`, `Windows`, `X64`, and `openroots-smoke`.
4. Start the OpenRoots stack from the repository directory:

   ```powershell
   docker compose up -d --build
   ```

   In the app, select a project and register one device with a numeric sensor
   channel named `temperature` (or change `MQTT_SMOKE_CHANNEL` in the workflow
   to the actual channel name).
5. Provision that device's MQTT account on the local development broker. Its
   MQTT username must equal the registered device UUID because this broker's
   publisher ACL binds the topic UUID to `%u`. Create the client with a unique
   test password and attach the existing `device-publisher` role. For example,
   replace the uppercase placeholders with your local values:

   ```powershell
   docker compose exec mosquitto mosquitto_ctrl -h 127.0.0.1 -u openroots-admin -P ADMIN_PASSWORD dynsec createClient DEVICE_UUID -p DEVICE_PASSWORD
   docker compose exec mosquitto mosquitto_ctrl -h 127.0.0.1 -u openroots-admin -P ADMIN_PASSWORD dynsec addClientRole DEVICE_UUID device-publisher
   ```

6. In GitHub repository settings, open **Secrets and variables → Actions** and
   add repository secrets `MQTT_SMOKE_DEVICE_ID` (the registered UUID) and
   `MQTT_SMOKE_DEVICE_PASSWORD` (the device account password). Do not put
   either value in the workflow file or commit them.
7. Push `.github/workflows/mqtt-smoke.yml` and the `examples/` smoke test
   files to GitHub. In the **Actions** tab, select **MQTT smoke test → Run
   workflow**. A passing run means the broker acknowledged the message and the
   backend API returned the same timestamped reading from the database.

This is a self-hosted runner, so only run workflow code you trust on this PC.
The smoke test writes one telemetry reading each time. It is manual-only so a
push does not write test data automatically.
