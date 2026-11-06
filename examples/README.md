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

Use a disposable development/test database. Fleet setup calls the existing
device-registration API and creates ordinary test records; it does not need a
schema change or a migration. The same setup works in PowerShell and bash.

1. Start the local stack with `docker compose up -d --build` and make sure the
   broker, backend, and database are healthy.
2. Copy `examples/.env.example` to `examples/.env` and set
   `OPENROOTS_MQTT_DEVICE_PASSWORD` to a test-only password. Keep it out of
   source control and do not reuse a broker admin or real device password.
3. Find the UUID of an existing test project in the OpenRoots UI or from
   `GET http://127.0.0.1:8080/api/v1/projects`. Install the MQTT Python
   dependency, then create or reuse a 100-device fleet:

   ```powershell
   python -m pip install -r examples/requirements-mqtt.txt
   python examples/provision_mqtt_fleet.py --project-id PROJECT_UUID --count 100
   ```

   The script gives devices stable names (`openroots-load-test-001`, etc.),
   registers a numeric `temperature` sensor, provisions each UUID as its MQTT
   username with the shared test password, and writes their UUIDs to the
   ignored `examples/load-devices.txt`. Re-running it reuses devices with
   those names and resets their broker password. Broker admin credentials are
   read inside the Mosquitto container. Change `--name-prefix` to create a
   separate fleet.

   The script provisions the test fleet in the local development database
   selected by the running backend. Use a separate disposable test database
   when you need to keep ordinary development data isolated. Flyway applies
   existing migrations when the backend starts; do not add a migration just
   to create test devices.

## Run

From the repository root:

```powershell
python examples/mqtt_load_test.py --devices examples/load-devices.txt --period 10 --duration 300
```

The load-test script reads `OPENROOTS_MQTT_DEVICE_PASSWORD` and broker/API
settings from `examples/.env` for local runs. Environment variables override
that file.

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
- Anaconda Python installed in the runner user's profile at
  `%USERPROFILE%\anaconda3\python.exe`. The workflow invokes this executable
  directly because the runner job does not resolve Conda's `PATH`.
- Docker Desktop running Linux containers, with Docker Compose v2 available.
- A GitHub repository where Actions are enabled, plus an internet connection
  for the runner to connect outbound to GitHub and for the workflow to install
  `paho-mqtt` from PyPI. The runner needs no inbound port forwarding.

Check that the runner account can access
`%USERPROFILE%\anaconda3\python.exe`. The current workflow expects that Anaconda
installation path when creating its virtual environment.

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
4. Start the OpenRoots stack from the repository root (`E:\OpenRoots` in
   your checkout):

   ```powershell
   docker compose up -d --build
   docker compose ps
   ```

   Wait until the services are running. Check the backend at
   `http://localhost:8080/actuator/health`, then open the app at
   `http://localhost:5173`.
5. Select an existing project in the app. If the list is empty, this version
   of the app cannot create projects in the UI (the **New project** button is
   disabled). Create a local test project from PowerShell instead:

   ```powershell
   docker compose exec database psql -U iot_backend -d iot_backend -c "INSERT INTO project (name) VALUES ('MQTT Smoke Test') RETURNING id;"
   ```

   This command uses the sample `.env` database/user values. If yours differ,
   replace `iot_backend` with your `POSTGRES_USER` and `POSTGRES_DB` values
   from `.env`. Reload the app and select the new project.
6. In the project, use **Devices → Register a device**. Enter a device name
   and type. The form starts with `temperature`, `rain`, and `uv` sensors; keep
   `temperature` (unit `C`) for this smoke test. Submit, then copy the device
   UUID shown on the device card. The workflow sends a numeric value for the
   exact channel name `temperature`.
7. Copy `examples/.env.example` to `examples/.env`, put the registered device
   UUID and a unique test password in it, then run the provisioning script:

   ```powershell
   Copy-Item examples/.env.example examples/.env
   notepad examples/.env
   .\examples\provision_mqtt_device.ps1
   ```

   The script creates the MQTT username from the device UUID and assigns the
   `device-publisher` role. It reads the admin credentials from the broker
   container. `examples/.env` is ignored by Git; keep it local.
8. Add two GitHub repository secrets. Go to the GitHub repo's **Settings →
   Secrets and variables → Actions → New repository secret** and create:

   - `MQTT_SMOKE_DEVICE_ID` — the device UUID you copied from the app.
   - `MQTT_SMOKE_DEVICE_PASSWORD` — the password set in `examples/.env` and used by the provisioning script.

   Do not put either value in the workflow file or commit them.
9. Push the workflow and scripts to GitHub. From the repository root:

   ```powershell
   git add .github/workflows/mqtt-smoke.yml examples/README.md examples/.env.example examples/mqtt_smoke_test.py examples/mqtt_load_test.py examples/requirements-mqtt.txt .gitignore README.md
   git commit -m "Add GitHub MQTT smoke test"
   git push
   ```

   In the GitHub repo, open **Actions → MQTT smoke test → Run workflow**. A
   passing run means the broker acknowledged the publish and the backend API
   returned that exact reading from PostgreSQL.

### Where the scripts live

- `.github/workflows/mqtt-smoke.yml` — GitHub workflow definition. It creates
  the Python virtual environment and runs the smoke script on your Windows
  self-hosted runner.
- `examples/mqtt_smoke_test.py` — publishes one reading and polls the backend
  telemetry API until that reading is stored.
- `examples/provision_mqtt_device.ps1` — creates the local broker client from
  `examples/.env` and assigns its publisher role.
- `examples/provision_mqtt_fleet.py` — idempotently creates/reuses a fleet via
  the existing API, provisions matching broker clients, and writes the UUID
  list for the load test.
- `examples/requirements-mqtt.txt` — Python MQTT client dependency installed
  by the workflow.
- `examples/mqtt_load_test.py` — the separate continuous multi-device test for
  later; the smoke workflow does not run this script.
- `examples/README.md` — setup and run guide.

For a local command-line smoke run, copy `examples/.env.example` to
`examples/.env`, fill in the device UUID and MQTT password, then run
`python examples/mqtt_smoke_test.py` from the repository root. The real
`examples/.env` is ignored by Git. GitHub Actions uses the two repository
secrets instead of this local file.

This is a self-hosted runner, so only run workflow code you trust on this PC.
The smoke test writes one telemetry reading each time. It is manual-only so a
push does not write test data automatically.

## GitHub Actions load test

`.github/workflows/mqtt-load-test.yml` runs on pushes to
`spring-mqtt-ingestion` and can also be manually dispatched. It uses the same
Windows self-hosted runner and checks that the local Mosquitto container and
backend are already running. A branch push creates/reuses 100 devices and
runs them at a 10-second period for 5 minutes. Manual runs let you adjust the
fleet size, broker host/port, period, and duration. The project is found from
the existing `MQTT_SMOKE_DEVICE_ID` secret unless you enter a project UUID.
The workflow uses `MQTT_SMOKE_DEVICE_PASSWORD` as the shared test-fleet
password; no per-device GitHub secrets or local `examples/.env` are needed for
Actions.

The workflow installs its Python dependency in a fresh virtual environment
because GitHub Actions checks out the code into its workspace for each run.
That replaces the manual `pip install` command; it is not something you need
to run separately. The broker host and port default to the current Windows
setup (`192.168.29.147:1883`); adjust the manual-run inputs when the Docker
published endpoint changes.
