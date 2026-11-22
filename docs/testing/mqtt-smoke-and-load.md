# MQTT smoke and load tests

These are black-box tests against a running stack. They run manually on a
self-hosted runner, never on pull requests; the PR checks are described in
[`README.md`](README.md).

`mqtt_smoke_test.py` checks the full path with one registered device: it
publishes one QoS 1 message, waits for broker acknowledgement, then polls the
backend API until the saved reading appears. `.github/workflows/mqtt-smoke.yml`
runs this check manually using a GitHub self-hosted runner on Windows.

`mqtt_load_test.py` keeps one MQTT connection open per device, publishes on a
fixed schedule, and then proves the readings reached PostgreSQL. It records
every reading it sent, polls the `telemetry` table until each one appears, and
reports missing readings and publish-to-database latency next to the broker
acknowledgement (PUBACK) latency. It talks only to the broker and the
database, so the same script tests either ingestion implementation: Spring on
`spring-mqtt-ingestion` or the Go service on `go-mqtt-ingestion`.

## How the tests get device credentials

Neither test stores a device password. Each run signs in as a test account that
is `OWNER` or `ADMIN` of the test project (`OPENROOTS_API_EMAIL` and
`OPENROOTS_API_PASSWORD`) and issues itself short-lived device credentials
through the credentials API. The backend syncs each credential to the broker
(see [authentication](../security/authentication.md)), so the broker login is
always the one the backend manages. The credentials are revoked at the end of
the run and expire on their own if a run crashes: one hour for the smoke test,
the planned run time plus 30 minutes for the load test.

Issuing a credential replaces the device's broker password, so do not run two
copies of the same test at once. Each workflow has a `concurrency` group for
this. Revoked credentials stay in the credential list as inactive rows.

## Prepare a local test fleet

Use a disposable development/test database. Fleet setup calls the existing
device-registration and credentials APIs and creates ordinary test records; it
does not need a schema change or a migration.

1. Start the local stack with `docker compose up -d --build` and make sure the
   broker, backend, and database are healthy.
2. Copy `tests/support/mqtt/.env.example` to `tests/support/mqtt/.env`. Set
   `MQTT_SMOKE_DEVICE_ID`, `OPENROOTS_API_EMAIL` and `OPENROOTS_API_PASSWORD`
   (a test account that is `OWNER` or `ADMIN` of the test project), and
   `OPENROOTS_LOAD_DB_DSN` to the database, for example
   `postgresql://iot_backend:PASSWORD@127.0.0.1:5432/iot_backend`. Keep the
   file out of source control.
3. Install the Python dependencies, then create or reuse a 100-device fleet.
   `--run-seconds` is the total time the load test will run; the credentials
   expire 30 minutes after it:

   ```powershell
   python -m pip install -r tests/support/mqtt/requirements-mqtt.txt
   python tests/support/mqtt/provision_mqtt_fleet.py --count 100 --run-seconds 1200
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
python tests/load/mqtt/mqtt_load_test.py --devices tests/support/mqtt/load-devices.txt --label spring --period 10 --warmup 30 --duration 300
```

On the Go branch, pass `--label go` and add the Go container to the resource
samples:

```powershell
python tests/load/mqtt/mqtt_load_test.py --devices tests/support/mqtt/load-devices.txt --label go --period 10 --warmup 30 --duration 300 --containers iot-backend,iot-database,openroots-mosquitto,openroots-ingestion
```

The script reads each device's secret from the device file, and the broker
address and database DSN from `tests/support/mqtt/.env` for local runs;
environment variables override that file. Repeat `--channel` to publish several
registered channels per message. When you are done, revoke the credentials and
delete the device file, which holds the secrets:

```powershell
python tests/support/mqtt/provision_mqtt_fleet.py --revoke
```

### What a run does

1. Connects every device and fails fast if the broker rejects one.
2. Publishes on a fixed schedule. `--schedule synchronized` (default) sends
   every device at the same instant each period; `--schedule staggered`
   spreads devices evenly across the period. Publishing never waits for a
   PUBACK, so a slow server cannot stretch the interval or the run.
3. Gives every reading a unique `capturedAt` (its scheduled time, in
   milliseconds). That value becomes `telemetry.recorded_at`, so each reading
   can be matched to exactly one row by device, channel and timestamp.
4. Polls the database every `--db-poll-interval` seconds (default 0.5) and
   stamps each reading when it first becomes visible. Latency is measured on
   the load generator's clock, with a resolution of one poll interval.
5. After publishing stops, waits for outstanding PUBACKs (`--ack-timeout`) and
   keeps polling up to `--drain-timeout` seconds (default 30) for readings that
   are still missing.
6. Traffic during `--warmup` is sent and checked but excluded from the stats
   and thresholds.

Messages follow the [MQTT telemetry contract](../contracts/mqtt-telemetry.md):
the body is `{"capturedAt": ..., "payload": {...}}` with no `deviceId`, since
the topic identifies the device. The Go service rejects unexpected fields.

### Pass/fail thresholds

A run exits non-zero when any limit is breached:

| Flag | Default | Meaning |
|---|---|---|
| `--max-missing` | `0` | Readings sent but never stored |
| `--max-publish-failures` | `0` | Publishes that failed or were never acknowledged |
| `--p95-ms` | `2000` | Publish-to-database p95 limit (`0` disables) |
| `--p99-ms` | `5000` | Publish-to-database p99 limit (`0` disables) |

Failed database polls, failed resource samples, or no in-load resource samples
also fail the run. `--skip-db-verify` reports broker acknowledgements only;
that proves the broker received the messages, not that they were stored.

### Results

Each run writes `load-test-results/<label>-<run-id>/` containing:

- `summary.md`: the readable result table, also printed to the console and the
  GitHub job summary.
- `summary.json`: settings, git commit, attempted/acknowledged/failed
  publishes, actual duration and rate, expected/stored/missing readings,
  PUBACK and publish-to-database p50/p95/p99/max, per-container CPU/RAM, and
  the pass/fail reasons.
- `readings.csv`: one row per reading sent, with its send time, PUBACK
  latency, database visibility time, and status (`stored`, `missing` or
  `publish_failed`).
- `resource-samples.csv`: Docker CPU/RAM/PID samples for `--containers`
  (default `iot-backend,iot-database,openroots-mosquitto`) every
  `--resource-sample-interval` seconds, plus a pre-test baseline.

The run ID defaults to the UTC start time. `--output-dir` changes the parent
folder.

## Comparing Spring and Go ingestion

Use the same broker, database, fleet, message pattern, and container resource
limits for both, and run them one at a time:

1. Check out `spring-mqtt-ingestion`, run `docker compose up -d --build`,
   provision the fleet, and run the load test three times with
   `--label spring --warmup 30 --duration 300`.
2. Stop the stack, check out `go-mqtt-ingestion`, start it the same way, and
   run three times with `--label go` and the extra `openroots-ingestion`
   container.
3. Confirm every run stored all expected readings and met the latency
   thresholds. Then compare the `publish_to_db_ms` percentiles and the
   ingestion container's CPU/RAM in each `summary.json`.

Docker sampling is container-level. Docker Desktop on Windows reports container
memory differently from Linux, so compare runs on the same runner platform.
The database polling adds a small, identical query load to both runs. Run
against a disposable development/staging stack, since this script writes real
telemetry and creates concurrent broker connections.

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
7. Choose the test account. It must be `OWNER` or `ADMIN` of that project,
   because only those roles can manage device credentials. Add another user
   with `POST /api/v1/projects/{projectId}/members` and
   `{"email": "...", "role": "ADMIN"}`.
8. Add the GitHub repository **secrets** under **Settings → Secrets and
   variables → Actions → New repository secret**:

   - `MQTT_SMOKE_DEVICE_ID` — the device UUID you copied from the app.
   - `MQTT_SMOKE_USER_EMAIL` and `MQTT_SMOKE_USER_PASSWORD` — the test
     account. Both workflows sign in with it.

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
- `tests/support/mqtt/provision_mqtt_fleet.py` — idempotently creates/reuses a
  fleet via the existing API, issues each device a temporary credential, and
  writes the device file for the load test. `--revoke` cleans up.
- `tests/support/mqtt/requirements-mqtt.txt` — Python MQTT client dependency installed
  by the workflow.
- `tests/load/mqtt/mqtt_load_test.py` — the multi-device load test with
  persistence verification; the smoke workflow does not run this script.
- `tests/support/mqtt/openroots_api.py` — shared `.env` loading, API sign-in,
  and issuing or revoking temporary device credentials.
- `docs/testing/mqtt-smoke-and-load.md` — setup and run guide.

For a local command-line smoke run, copy `tests/support/mqtt/.env.example` to
`tests/support/mqtt/.env`, fill in the device UUID and the test account, then
run `python tests/e2e/mqtt/mqtt_smoke_test.py` from the repository root. The
real `tests/support/mqtt/.env` is ignored by Git. GitHub Actions uses the
repository secrets instead of this local file.

This is a self-hosted runner, so only run workflow code you trust on this PC.
The smoke test writes one telemetry reading each time. It is manual-only so a
push does not write test data automatically.

## GitHub Actions load test

`.github/workflows/mqtt-load-test.yml` is manual only (**Actions → MQTT load
test → Run workflow**); it does not run on pushes, because it needs the local
stack and writes thousands of telemetry rows. It uses the same Windows
self-hosted runner and checks that the local Mosquitto container and backend
are already running. It also detects which stack is running: the Go stack is the
one with a running `openroots-ingestion` container. The results are labelled
`go` or `spring` from that, not from the branch. If the branch name says `go` or
`spring` and the other stack is running, the workflow stops before sending any
traffic.

Run-form inputs:

- There is no implementation input: start the stack from the branch you run the
  workflow on. The stack is detected, and the result heading shows the branch
  and commit.
- Fleet size, broker host/port, period, measured duration, warm-up, schedule
  (`synchronized` or `staggered`), and `repeats` (default 3 back-to-back
  runs).

Repository secrets:

- `MQTT_SMOKE_USER_EMAIL` / `MQTT_SMOKE_USER_PASSWORD` — the test account
  (`OWNER` or `ADMIN` of the project). Fleet setup signs in with it, issues
  each device a temporary credential that outlasts all `repeats`, and a final
  step that always runs revokes them and deletes the device file. No device
  password secret is needed.
- `MQTT_LOAD_DB_DSN` — PostgreSQL DSN reachable from the runner, for example
  `postgresql://iot_backend:PASSWORD@127.0.0.1:5432/iot_backend`. Used to
  verify that every reading was stored.
- `MQTT_SMOKE_DEVICE_ID` — used to find the project when no project UUID is
  entered.

The broker host defaults to `127.0.0.1`, because Compose publishes Mosquitto
on the loopback address unless `MQTT_BIND_ADDRESS` is set.

The workflow fails if any run breaches its thresholds. Each run's
`summary.md` appears in the job summary, and the whole `load-test-results/`
folder is uploaded as the `mqtt-load-test-<run id>` artifact:
**Actions → MQTT load test → completed run → Artifacts**.

The workflow installs its Python dependencies (`paho-mqtt`, `psycopg`) in a
fresh virtual environment for each run.
