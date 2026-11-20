# Forgejo Actions MQTT smoke and load tests on Linux

This guide runs Forgejo Runner directly on the same Linux PC as the OpenRoots
Docker Compose stack. The workflows connect to `127.0.0.1`; no LAN address,
port forwarding, Conda, or Windows PC is needed. The workflows only run when
manually started from Forgejo.

## 1. Prepare the Linux PC

Use a supported 64-bit Ubuntu or Debian installation. Keep the PC awake and
connected to the network while a workflow runs.

Install Git, Python 3, Python's virtual-environment support, curl, jq, GnuPG,
and download tools:

```bash
sudo apt update
sudo apt install -y ca-certificates curl git gnupg jq python3 python3-pip python3-venv tar
```

Install Docker Engine and the Docker Compose v2 plugin using the official
[Docker Engine installation instructions](https://docs.docker.com/engine/install/).
Confirm these commands work:

```bash
docker --version
docker compose version
```

The workflows check out the repository with Forgejo's `actions/checkout@v4`,
which requires Node.js 20. Install a system-wide Node.js 20 package if
`node --version` does not report version 20:

```bash
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs
node --version
```

Conda is not used. Each workflow creates its own Python virtual environment
and installs `paho-mqtt` from `tests/support/mqtt/requirements-mqtt.txt`.

## 2. Pull the branch onto Linux

In the existing checkout, fetch and switch to the branch containing these
Forgejo files:

```bash
git fetch origin
git switch spring-mqtt-ingestion
git pull --ff-only origin spring-mqtt-ingestion
```

If the repository is not on this PC yet, clone it from Forgejo and switch to
the branch:

```bash
git clone https://YOUR-FORGEJO-HOST/OWNER/REPOSITORY.git OpenRoots
cd OpenRoots
git switch spring-mqtt-ingestion
```

Use the repository URL and authentication method for your Forgejo server.

## 3. Configure and start the local OpenRoots stack

The root `.env` belongs next to `docker-compose.yml`. It is separate from
`tests/support/mqtt/.env`. On a new checkout, copy the sample once and edit it:

```bash
cp .env.example .env
nano .env
```

Keep the database and username values if you want, but replace all three
example passwords with unique test-only values. Generate each password with
`openssl rand -hex 32` and use a different result for each password. Edit the
file so it contains these settings (keep any other settings from the sample):

```dotenv
POSTGRES_DB=iot_backend
POSTGRES_USER=iot_backend
POSTGRES_PASSWORD=PASTE_A_UNIQUE_RANDOM_VALUE_HERE

MQTT_ADMIN_USERNAME=openroots-admin
MQTT_ADMIN_PASSWORD=PASTE_A_DIFFERENT_RANDOM_VALUE_HERE
MQTT_INGESTOR_USERNAME=telemetry-ingestor-spring
MQTT_INGESTOR_PASSWORD=PASTE_ANOTHER_RANDOM_VALUE_HERE

MQTT_BIND_ADDRESS=127.0.0.1
MQTT_PORT=1883
BACKEND_PORT=8080
```

Do not leave any `change-this-...` sample password in the file. You can run
`openssl rand -hex 32` three times and paste one result into each password
line. The app and database use the same `POSTGRES_DB` and `POSTGRES_USER`
values shown here.

`127.0.0.1` is correct because Forgejo Runner and Compose are on the same Linux
host. The backend container still reaches Mosquitto through Compose's private
network. The MQTT port does not need to be published on the LAN for these
local tests. Keep `.env` private; Git ignores it.

From the repository root, start the stack and wait for it to become healthy:

```bash
docker compose up -d --build
docker compose ps
curl --fail http://127.0.0.1:8080/actuator/health
```

The Compose file names the broker container `openroots-mosquitto`; the
workflows use that name to check and provision broker accounts.

## 4. Create one smoke-test device in this local app

The Linux app database is independent of the Windows app database. Register a
test device in the app running on this Linux stack; do not copy a UUID from a
different database. Create or select a project, then register one device with
a numeric sensor whose exact channel name is `temperature` and unit `C`. Copy
the device UUID.

If the UI cannot create a project and none exists, create one in the database
using the user/database values from the root `.env`:

```bash
docker compose exec database psql -U POSTGRES_USER_VALUE -d POSTGRES_DB_VALUE \
  -c "INSERT INTO project (name) VALUES ('Forgejo MQTT Test') RETURNING id;"
```

Replace `POSTGRES_USER_VALUE` and `POSTGRES_DB_VALUE` with the values in `.env`,
then reload the app and select that project. Register the device from the UI.

Before the smoke workflow publishes, it provisions or resets this device's
MQTT username and password on the local broker. Do not run the PowerShell
provisioning script on Linux and do not create this broker account by hand.

## 5. Add three Forgejo repository secrets

GitHub secrets do not transfer to Forgejo. In Forgejo open this repository's
**Settings → Actions → Secrets** page. Its URL is
`/{owner}/{repo}/settings/actions/secrets`. Add these repository secrets:

| Secret name | Value |
| --- | --- |
| `MQTT_SMOKE_DEVICE_ID` | UUID of the `temperature` device registered in this Linux app database |
| `MQTT_SMOKE_DEVICE_PASSWORD` | A test-only password for that smoke device |
| `MQTT_LOAD_DEVICE_PASSWORD` | A test-only shared password for load-test devices |

Use single-line values. Keep them out of workflow files and Git. Forgejo will
not show a saved value later; delete and recreate a secret to correct it. The
load workflow registers or reuses devices in the project containing the smoke
device and sets their MQTT accounts to `MQTT_LOAD_DEVICE_PASSWORD`. There are
no per-device secrets and no `tests/support/mqtt/.env` needed by these workflows.

## 6. Enable Actions and register the Linux runner

In repository **Settings → Units**, confirm **Actions** is enabled. Open
**Settings → Actions → Runners**, select **Create new runner**, and copy the
runner UUID and token. Keep the token private.

Install the Forgejo Runner signed binary. These commands discover the current
Linux release and verify it with Forgejo's signing key:

```bash
ARCH=$(uname -m)
case "$ARCH" in
  x86_64) ARCH=amd64 ;;
  aarch64|arm64) ARCH=arm64 ;;
  *) echo "Unsupported runner architecture: $ARCH" >&2; exit 1 ;;
esac
RUNNER_VERSION=$(curl -fsSL https://data.forgejo.org/api/v1/repos/forgejo/runner/releases/latest | jq -r .name | sed 's/^v//')
RUNNER_URL="https://code.forgejo.org/forgejo/runner/releases/download/v${RUNNER_VERSION}/forgejo-runner-${RUNNER_VERSION}-linux-${ARCH}"
curl -fsSL "$RUNNER_URL" -o /tmp/forgejo-runner
curl -fsSL "${RUNNER_URL}.asc" -o /tmp/forgejo-runner.asc
gpg --keyserver hkps://keys.openpgp.org --recv-keys EB114F5E6C0DC2BCDD183550A4B61A2DC5923710
gpg --verify /tmp/forgejo-runner.asc /tmp/forgejo-runner
sudo install -m 0755 /tmp/forgejo-runner /usr/local/bin/forgejo-runner
forgejo-runner --version
```

Create a dedicated service account and grant it Docker access. The workflow
needs Docker to inspect Mosquitto and provision test accounts:

```bash
sudo useradd --create-home --shell /bin/bash forgejo-runner
sudo usermod -aG docker forgejo-runner
```

Generate a config file as that account, then edit it:

```bash
sudo -u forgejo-runner bash -lc \
  'forgejo-runner generate-config > /home/forgejo-runner/runner-config.yml'
sudo chown forgejo-runner:forgejo-runner /home/forgejo-runner/runner-config.yml
sudo nano /home/forgejo-runner/runner-config.yml
```

In the generated YAML, add the URL, UUID, and token from Forgejo and set the
custom label to exactly `openroots-smoke:host`:

```yaml
runner:
  labels:
    - "openroots-smoke:host"

server:
  connections:
    forgejo:
      url: "https://YOUR-FORGEJO-HOST/"
      uuid: "UUID_FROM_FORGEJO"
      token: "TOKEN_FROM_FORGEJO"
```

Keep other generated settings. Replace the uppercase placeholders with values
from the runner registration page. Use a Forgejo URL reachable from this Linux
PC, including the right scheme and port. The `host` executor is required so
the job can use the host Docker CLI and reach the stack on loopback.

Install the runner as a systemd service so it starts at boot and does not
depend on an open terminal:

```bash
sudo tee /etc/systemd/system/forgejo-runner.service >/dev/null <<'EOF'
[Unit]
Description=Forgejo Actions Runner
After=network-online.target docker.service
Wants=network-online.target

[Service]
User=forgejo-runner
Group=forgejo-runner
SupplementaryGroups=docker
WorkingDirectory=/home/forgejo-runner
ExecStart=/usr/local/bin/forgejo-runner daemon --config /home/forgejo-runner/runner-config.yml
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
sudo systemctl daemon-reload
sudo systemctl enable --now forgejo-runner
sudo systemctl status forgejo-runner --no-pager
```

Back in Forgejo **Settings → Actions → Runners**, confirm the runner is online
and has the `openroots-smoke` label. The runner connects outbound to Forgejo;
no inbound firewall port forwarding is required. It also needs outbound access
to `data.forgejo.org` for checkout, `code.forgejo.org` for artifact upload, and
PyPI for the MQTT dependency.

## 7. Run the smoke workflow

Open **Actions → MQTT smoke test (Forgejo) → Run workflow** and select the
`spring-mqtt-ingestion` branch. A passing run means the workflow checked out
the branch, provisioned the smoke device's broker credentials, published one
QoS 1 telemetry record, and observed that exact record through the backend
API.

## 8. Run the 100-device load workflow

After smoke passes, open **Actions → MQTT load test (Forgejo) → Run workflow**.
Defaults are 100 devices, one synchronized first publish, a message every 10
seconds per device, and a 300-second run. Leave `project_id` blank to infer the
project from `MQTT_SMOKE_DEVICE_ID`, or enter a project UUID.

The run creates or reuses devices named `openroots-load-test-001` onward and
writes their UUIDs to the ignored `tests/support/mqtt/load-devices.txt` in that job's
workspace. Reruns reuse the devices and reset their broker passwords. The run
log reports connections, acknowledgements, failures, publish rate, and PUBACK
latency. While the devices publish, it prints CPU and RAM samples for the
backend, database, and broker every five seconds. It also writes a baseline and
all samples to `load-test-results/resource-samples.csv`, then uploads that
directory as the `mqtt-load-test-results` artifact. Open the completed run in
Forgejo Actions and download the artifact to keep or graph the CSV. PUBACK
confirms Mosquitto accepted a message; it does not alone prove the backend
stored every row. Smoke separately verifies persistence for one record.

## Troubleshooting

- **Waiting for a runner:** confirm it is online and configured with
  `openroots-smoke:host`; workflow `runs-on` is `openroots-smoke`.
- **Docker permission denied:** ensure the service account is in the `docker`
  group, then run `sudo systemctl restart forgejo-runner`.
- **Mosquitto not running:** from the repository root, run `docker compose ps`
  and `docker compose up -d`.
- **Connection refused:** check root `.env` has `MQTT_BIND_ADDRESS=127.0.0.1`,
  `MQTT_PORT=1883`, and `BACKEND_PORT=8080`, then restart Compose.
- **Missing secret:** verify the three exact secret names above under Forgejo
  repository Actions secrets. These workflows do not read GitHub secrets or
  `tests/support/mqtt/.env`.
- **Runner logs:** `sudo journalctl -u forgejo-runner -f`.

Official references: [runner binary installation](https://forgejo.org/docs/latest/admin/actions/installation/binary/), [runner registration](https://forgejo.org/docs/latest/admin/actions/registration/), [runner labels](https://forgejo.org/docs/latest/admin/actions/configuration/), [Actions secrets](https://forgejo.org/docs/latest/user/actions/basic-concepts/).
