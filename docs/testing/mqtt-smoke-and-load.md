# MQTT smoke and load tests

These are black-box tests against a running stack. They run manually on a
self-hosted runner, never on pull requests; the PR checks are described in
[`README.md`](README.md).

`mqtt_smoke_test.py` checks the full path with one registered device: it
publishes one QoS 1 message, waits for broker acknowledgement, then polls the
backend API until the saved reading appears. `.github/workflows/mqtt-smoke.yml`
runs this check manually using a GitHub self-hosted runner on Windows.

`mqtt_load_test.py` keeps one MQTT connection open per device and publishes a
telemetry message from every device at the same time at startup, then repeats
at the configured interval. This models a synchronized fleet and measures the
publisher-side MQTT acknowledgement latency.

## How the tests get device credentials

Neither test stores a device password. Each run signs in as a test user who is
`OWNER` or `ADMIN` of the test project (`MQTT_SMOKE_USER_EMAIL` and
`MQTT_SMOKE_USER_PASSWORD`) and issues itself short-lived device credentials
through the credentials API. The backend syncs each credential to the broker
(see [authentication](../security/authentication.md)), so the broker login is
always the one the backend manages. The credentials are revoked at the end of
the run and expire on their own if a run crashes: one hour for the smoke test,
the planned duration plus 30 minutes for the load test.

Issuing a credential replaces the device's broker password, so do not run two
copies of the same test at once. Each workflow has a `concurrency` group for
this. Revoked credentials stay in the credential list as inactive rows.

## Prepare a local test fleet

Use a disposable development/test database. Fleet setup calls the existing
device-registration and credentials APIs and creates ordinary test records; it
does not need a schema change or a migration.

1. Start the local stack with `docker compose up -d --build` and make sure the
   broker, backend, and database are healthy.
2. Copy `tests/support/mqtt/.env.example` to `tests/support/mqtt/.env` and set
   `MQTT_SMOKE_DEVICE_ID` and the test user's email and password.
3. Install the MQTT Python dependency, then create or reuse a 100-device fleet:

   ```powershell
   python -m pip install -r tests/support/mqtt/requirements-mqtt.txt
   python tests/support/mqtt/provision_mqtt_fleet.py --count 100 --run-seconds 300
   ```

   The project is found from `MQTT_SMOKE_DEVICE_ID`; pass `--project-id` to use
   another one. The script gives devices stable names
   (`openroots-load-test-001`, etc.), registers a numeric `temperature` sensor,
   and issues each device a temporary credential. It writes
   `tests/support/mqtt/load-devices.txt` (one `uuid secret` line per device)
   and `tests/support/mqtt/load-credentials.json` (credential ids only). Both
   are ignored by Git. Re-running it reuses devices with those names. Change
   `--name-prefix` to create a separate fleet.

   The script provisions the test fleet in the local development database
   selected by the running backend. Use a separate disposable test database
   when you need to keep ordinary development data isolated. Flyway applies
   existing migrations when the backend starts; do not add a migration just
   to create test devices.

## Run

From the repository root:

```powershell
python tests/load/mqtt/mqtt_load_test.py --devices tests/support/mqtt/load-devices.txt --period 10 --duration 300
```

The load-test script reads each device's secret from the device file and the
broker settings from `tests/support/mqtt/.env` for local runs. Environment
variables override that file. When you are done, revoke the credentials and
delete the device file, which holds the secrets:

```powershell
python tests/support/mqtt/provision_mqtt_fleet.py --revoke
```

To publish multiple channels, repeat `--channel` with names registered on
every device:

```powershell
python tests/load/mqtt/mqtt_load_test.py --devices tests/support/mqtt/load-devices.txt --period 10 --duration 300 --channel temperature --channel humidity
```

Defaults are 10 seconds, 120 seconds, QoS 1, and `temperature`. Configure
`--host`/`--port` for a different broker. The test reports successful PUBACKs,
failed publishes, rate, and PUBACK latency percentiles. It also samples Docker
CPU and RAM for `iot-backend`, `iot-database`, and `openroots-mosquitto` every
five seconds while publishing. Readings appear in the console and in
`load-test-results/resource-samples.csv`; `--resource-sample-interval` and
`--resource-output` can change the interval and path. The CSV includes a
pre-test baseline, UTC timestamps, CPU percentage, memory used and limit, and
container PID count. The final output summarizes average and peak CPU/RAM from
the in-load samples. PUBACK means the broker accepted the message; it does not
prove the backend committed it to PostgreSQL. After the run, verify the
expected telemetry rows in the development database. For a rough expectation,
a 5-minute run at 10-second intervals
produces about 30 messages per device after the synchronized first publish
(about 3,000 messages for 100 devices), with minor variation at the end of the
run.

## Load-test practice

Start with a short smoke run, then run a baseline with a gradual ramp-up,
steady-state traffic, and a longer soak. Test synchronized bursts separately
from staggered device schedules: they stress different parts of the system.
Keep payload size and channel count realistic, repeat runs, and record the
server and database resource usage alongside throughput, p95/p99 latency, and
errors. This script's five-second `docker stats` samples and MQTT PUBACK
latencies are a useful first load-test record. A more complete performance
setup usually adds a time-series monitor such as Prometheus/Grafana, host-level
CPU/RAM, application and database metrics, and explicit pass/fail targets.
This run's sampling is container-level; Docker Desktop on Windows reports
container memory differently from Linux, so compare runs on the same runner
platform. Increase fleet size or message frequency in controlled steps until
latency, error rate, or resource use exceeds the limits you set. Run against a
disposable development/staging stack, since this script writes real telemetry
and creates concurrent broker connections.

The load-test workflow uploads `load-test-results/resource-samples.csv` as
the `mqtt-load-test-results` artifact. Download it from the completed GitHub
workflow run. The live workflow log also shows each sample. A five
minute run with a five second interval produces roughly 60 in-load samples per
container, plus the baseline.

## GitHub Actions smoke test setup

The first workflow is a manually triggered end-to-end smoke test for one
pre-registered device. It does not create or delete projects/devices; it only
issues and revokes a temporary credential for that device. Start the
OpenRoots development stack on your Windows PC with `docker compose up -d
--build`; leave it running while invoking the workflow. The self-hosted runner
and stack are on the same PC, so its MQTT port and API are reachable at
`127.0.0.1:1883` and `http://127.0.0.1:8080`.

### Requirements

- Windows 10/11 PC with Git installed and available on `PATH`.
- A Python 3 that the runner's account can read. When the runner is a Windows
  service it runs as `NETWORK SERVICE`, which cannot read a Python installed in
  your user profile (such as Anaconda). Install Python for all users (for
  example `winget install Python.Python.3.12 --scope machine`) and set the
  repository **variable** `SMOKE_PYTHON` to its `python.exe`. Without the
  variable the workflows use the first `python` on the runner's `PATH`.
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
4. Start the OpenRoots stack from the repository root (`E:\OpenRoots` in
   your checkout):

   ```powershell
   docker compose up -d --build
   docker compose ps
   ```

   Wait until the services are running. Check the backend at
   `http://localhost:8080/actuator/health`, then open the app at
   `http://localhost:5173`.
5. Create a test project as a platform admin. Only platform admins can create
   projects, and the creator becomes its `OWNER`. Use the app, or the API:
   `POST /api/v1/projects` with `{"name": "MQTT Smoke Test"}` and the admin's
   bearer token.
6. In the project, use **Devices → Register a device**. Enter a device name
   and type. The form starts with `temperature`, `rain`, and `uv` sensors; keep
   `temperature` (unit `C`) for this smoke test. Submit, then copy the device
   UUID shown on the device card. The workflow sends a numeric value for the
   exact channel name `temperature`.
7. Choose the test user. It must be `OWNER` or `ADMIN` of that project, because
   only those roles can manage device credentials. Add another user with
   `POST /api/v1/projects/{projectId}/members` and
   `{"email": "...", "role": "ADMIN"}`.
8. Add three GitHub repository **secrets** under **Settings → Secrets and
   variables → Actions → New repository secret**:

   - `MQTT_SMOKE_DEVICE_ID` — the device UUID you copied from the app.
   - `MQTT_SMOKE_USER_EMAIL` and `MQTT_SMOKE_USER_PASSWORD` — the test user.

   Do not put these values in the workflow file or commit them.
9. In the GitHub repo, open **Actions → MQTT smoke test → Run workflow**. A
   passing run means the broker accepted the device login and the publish, and
   the backend API returned that exact reading from PostgreSQL.

### Where the scripts live

- `.github/workflows/mqtt-smoke.yml` — GitHub workflow definition. It creates
  the Python virtual environment and runs the smoke script on your Windows
  self-hosted runner.
- `tests/e2e/mqtt/mqtt_smoke_test.py` — publishes one reading and polls the backend
  telemetry API until that reading is stored.
- `tests/support/mqtt/openroots_api.py` — signs in and issues or revokes
  temporary device credentials; shared by the smoke test and fleet setup.
- `tests/support/mqtt/provision_mqtt_fleet.py` — idempotently creates/reuses a
  fleet via the existing API, issues each device a temporary credential, and
  writes the device file for the load test. `--revoke` cleans up.
- `tests/support/mqtt/requirements-mqtt.txt` — Python MQTT client dependency installed
  by the workflow.
- `tests/load/mqtt/mqtt_load_test.py` — the separate continuous multi-device test for
  later; the smoke workflow does not run this script.
- `docs/testing/mqtt-smoke-and-load.md` — setup and run guide.

For a local command-line smoke run, copy `tests/support/mqtt/.env.example` to
`tests/support/mqtt/.env`, fill in the device UUID and the test user, then run
`python tests/e2e/mqtt/mqtt_smoke_test.py` from the repository root. The real
`tests/support/mqtt/.env` is ignored by Git. GitHub Actions uses the repository
secrets instead of this local file.

This is a self-hosted runner, so only run workflow code you trust on this PC.
The smoke test writes one telemetry reading each time. It is manual-only so a
push does not write test data automatically.

## GitHub Actions load test

`.github/workflows/mqtt-load-test.yml` is manual only (**Actions → MQTT load
test → Run workflow**); it does not run on pushes, because it needs the local
stack and writes thousands of telemetry rows. It uses the same Windows
self-hosted runner and checks that the local Mosquitto container and backend
are already running. By default it creates/reuses 100 devices and runs them at
a 10-second period for 5 minutes; the run form lets you adjust the fleet size,
broker host/port, period, and duration. The project is found from
the existing `MQTT_SMOKE_DEVICE_ID` secret unless you enter a project UUID.
It signs in with the same `MQTT_SMOKE_USER_EMAIL` and `MQTT_SMOKE_USER_PASSWORD`
secrets, issues each fleet device a temporary credential, and a final step that
always runs revokes them and deletes the device file. No device password
secret is needed.

The workflow installs its Python dependency in a fresh virtual environment
because GitHub Actions checks out the code into its workspace for each run.
That replaces the manual `pip install` command; it is not something you need
to run separately. The broker host and port default to `127.0.0.1:1883`,
because Compose publishes Mosquitto on the loopback address only; adjust the
manual-run inputs when the Docker published endpoint changes.
