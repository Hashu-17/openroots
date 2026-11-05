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
3. Copy `examples/.env.example` to `examples/.env`, set the device UUID and a
   test password, then provision the broker account with one script:

   ```powershell
   Copy-Item examples/.env.example examples/.env
   notepad examples/.env
   .\examples\provision_mqtt_device.ps1
   ```

   The script creates a Mosquitto client whose username is the device UUID and
   assigns it the `device-publisher` role. It reads broker admin credentials
   inside the Mosquitto container, so you do not need to enter them. Keep the
   test password in this ignored local `.env`; do not reuse the admin password.
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
