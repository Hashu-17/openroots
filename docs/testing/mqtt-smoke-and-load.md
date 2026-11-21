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

## Prepare a local test fleet

Use a disposable development/test database. Fleet setup calls the existing
device-registration API and creates ordinary test records; it does not need a
schema change or a migration. The same setup works in PowerShell and bash.

1. Start the local stack with `docker compose up -d --build` and make sure the
   broker, backend, and database are healthy.
2. Copy `tests/support/mqtt/.env.example` to `tests/support/mqtt/.env` and set
   `OPENROOTS_MQTT_DEVICE_PASSWORD` to a test-only password. Set
   `OPENROOTS_API_EMAIL` and `OPENROOTS_API_PASSWORD` to a test account that
   can manage devices in the test project (the API requires sign-in), and
   `OPENROOTS_LOAD_DB_DSN` to the database, for example
   `postgresql://iot_backend:PASSWORD@127.0.0.1:5432/iot_backend`. Keep it out of
   source control and do not reuse a broker admin or real device password.
3. Find the UUID of an existing test project in the OpenRoots UI or from
   `GET http://127.0.0.1:8080/api/v1/projects`. Install the MQTT Python
   dependency, then create or reuse a 100-device fleet:

   ```powershell
   python -m pip install -r tests/support/mqtt/requirements-mqtt.txt
   python tests/support/mqtt/provision_mqtt_fleet.py --project-id PROJECT_UUID --count 100
   ```

   The script gives devices stable names (`openroots-load-test-001`, etc.),
   registers a numeric `temperature` sensor, provisions each UUID as its MQTT
   username with the shared test password, and writes their UUIDs to the
   ignored `tests/support/mqtt/load-devices.txt`. Re-running it reuses devices with
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
python tests/load/mqtt/mqtt_load_test.py --devices tests/support/mqtt/load-devices.txt --label spring --period 10 --warmup 30 --duration 300
```

On the Go branch, pass `--label go` and add the Go container to the resource
samples:

```powershell
python tests/load/mqtt/mqtt_load_test.py --devices tests/support/mqtt/load-devices.txt --label go --period 10 --warmup 30 --duration 300 --containers iot-backend,iot-database,openroots-mosquitto,openroots-ingestion
```

The script reads the device password, broker address and database DSN from
`tests/support/mqtt/.env` for local runs; environment variables override that
file. Repeat `--channel` to publish several registered channels per message.

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
7. Copy `tests/support/mqtt/.env.example` to `tests/support/mqtt/.env`, put the registered device
   UUID and a unique test password in it, then run the provisioning script:

   ```powershell
   Copy-Item tests/support/mqtt/.env.example tests/support/mqtt/.env
   notepad tests/support/mqtt/.env
   .\tests\support\mqtt\provision_mqtt_device.ps1
   ```

   The script creates the MQTT username from the device UUID and assigns the
   `device-publisher` role. It reads the admin credentials from the broker
   container. `tests/support/mqtt/.env` is ignored by Git; keep it local.
8. Add the GitHub repository secrets. Go to the GitHub repo's **Settings →
   Secrets and variables → Actions → New repository secret** and create:

   - `MQTT_SMOKE_DEVICE_ID` — the device UUID you copied from the app.
   - `MQTT_SMOKE_DEVICE_PASSWORD` — the password set in `tests/support/mqtt/.env` and used by the provisioning script.
   - `MQTT_LOAD_API_EMAIL` and `MQTT_LOAD_API_PASSWORD` — a test account that
     is a member of the test project. The smoke test signs in with it to read
     telemetry; the load workflow uses it to register the fleet.

   Do not put either value in the workflow file or commit them.
9. In the GitHub repo, open **Actions → MQTT smoke test → Run workflow**. A
   passing run means the broker acknowledged the publish and the backend API
   returned that exact reading from PostgreSQL.

### Where the scripts live

- `.github/workflows/mqtt-smoke.yml` — GitHub workflow definition. It creates
  the Python virtual environment and runs the smoke script on your Windows
  self-hosted runner.
- `tests/e2e/mqtt/mqtt_smoke_test.py` — publishes one reading and polls the backend
  telemetry API until that reading is stored.
- `tests/support/mqtt/provision_mqtt_device.ps1` — creates the local broker client from
  `tests/support/mqtt/.env` and assigns its publisher role.
- `tests/support/mqtt/provision_mqtt_fleet.py` — idempotently creates/reuses a fleet via
  the existing API, provisions matching broker clients, and writes the UUID
  list for the load test.
- `tests/support/mqtt/requirements-mqtt.txt` — Python MQTT client dependency installed
  by the workflow.
- `tests/load/mqtt/mqtt_load_test.py` — the multi-device load test with
  persistence verification; the smoke workflow does not run this script.
- `tests/support/mqtt/openroots_api.py` — shared `.env` loading and API sign-in
  used by the smoke, fleet and load scripts.
- `docs/testing/mqtt-smoke-and-load.md` — setup and run guide.

For a local command-line smoke run, copy `tests/support/mqtt/.env.example` to
`tests/support/mqtt/.env`, fill in the device UUID and MQTT password, then run
`python tests/e2e/mqtt/mqtt_smoke_test.py` from the repository root. The real
`tests/support/mqtt/.env` is ignored by Git. GitHub Actions uses the two repository
secrets instead of this local file.

This is a self-hosted runner, so only run workflow code you trust on this PC.
The smoke test writes one telemetry reading each time. It is manual-only so a
push does not write test data automatically.

## GitHub Actions load test

`.github/workflows/mqtt-load-test.yml` is manual only (**Actions → MQTT load
test → Run workflow**); it does not run on pushes, because it needs the local
stack and writes thousands of telemetry rows. It uses the same Windows
self-hosted runner and checks that the local Mosquitto container and backend
are already running (and, for `implementation: go`, the `openroots-ingestion`
container).

Run-form inputs:

- `implementation`: `spring` or `go`. Labels the results and, for `go`, adds
  `openroots-ingestion` to the resource samples. Start the stack from the
  matching branch before running.
- Fleet size, broker host/port, period, measured duration, warm-up, schedule
  (`synchronized` or `staggered`), and `repeats` (default 3 back-to-back
  runs).

Repository secrets:

- `MQTT_LOAD_DEVICE_PASSWORD` — shared password for all fleet clients. The
  workflow provisions those broker accounts and publishes with it.
- `MQTT_LOAD_DB_DSN` — PostgreSQL DSN reachable from the runner, for example
  `postgresql://iot_backend:PASSWORD@127.0.0.1:5432/iot_backend`. Used to
  verify that every reading was stored.
- `MQTT_LOAD_API_EMAIL` / `MQTT_LOAD_API_PASSWORD` — test account used to
  register the fleet.
- `MQTT_SMOKE_DEVICE_ID` — used to find the project when no project UUID is
  entered.

The workflow fails if any run breaches its thresholds. Each run's
`summary.md` appears in the job summary, and the whole `load-test-results/`
folder is uploaded as the `mqtt-load-test-<implementation>-<run id>` artifact:
**Actions → MQTT load test → completed run → Artifacts**.

The workflow installs its Python dependencies (`paho-mqtt`, `psycopg`) in a
fresh virtual environment for each run.
