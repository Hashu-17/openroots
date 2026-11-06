# MQTT Load Testing and Observability Plan

## Goal

Measure whether OpenRoots can ingest the target MQTT workload, verify that
telemetry is persisted, and record enough system metrics to explain slowdowns
or failures. Keep load generation, backend verification, and host monitoring
as separate measurements so a broker acknowledgement is not mistaken for a
database commit.

## Current baseline

- `.github/workflows/mqtt-smoke.yml` runs the one-message Windows smoke test.
- `.github/workflows/mqtt-smoke2.yml` is the Linux equivalent. Each workflow
  uses a self-hosted runner and repository secrets for one test device.
- `examples/mqtt_smoke_test.py` publishes one QoS 1 message, records its
  broker PUBACK time, and polls the telemetry API until the exact reading is
  visible. This checks one end-to-end message; it does not measure fleet
  capacity or report persistence latency.
- `examples/mqtt_load_test.py` connects one MQTT client per UUID, publishes
  synchronized initial traffic, then repeats at a configured interval. It
  reports connected devices, acknowledged and failed publishes, publish
  rate, and mean, p95, and maximum PUBACK latency. PUBACK means the broker
  accepted the message. The script does not currently verify every message
  was persisted, report p99, or capture server CPU and memory.
- The backend includes Spring Boot Actuator, but current configuration exposes
  only `health` and `info`; Prometheus metrics and MQTT ingestion counters are
  not configured. See `pom.xml` and `src/main/resources/application.yml`.

## Workload scenarios

Run against a disposable development or staging database. Preserve the
scenario settings and code revision with every result.

1. **Smoke:** one device, one message; verify broker acknowledgement and API
   persistence. This is already implemented.
2. **Synchronized burst:** ramp the number of clients through 10, 25, 50, and
   100. Publish once from all clients at the same time, then repeat at the
   target interval. This tests peak queueing.
3. **Steady state:** publish at the intended cadence for a defined duration.
   For 100 devices publishing once every 10 seconds, average load is about
   10 messages per second. Synchronized clients still produce a burst of
   about 100 messages at each shared interval.
4. **Staggered schedule:** spread device publish times across the interval
   and compare with synchronized traffic.
5. **Soak:** after short tests are stable, hold the target workload for a
   longer period to find slow resource growth, queue buildup, or connection
   leaks.

Use the same payload size, channel count, QoS, device credentials, and server
configuration across comparable runs. Define acceptable error rate, p95/p99
latency, and resource headroom before treating a result as a pass.

## Metrics to record

### Load generator and broker

- Scenario name, git commit, start time, duration, device count, interval,
  payload/channel count, and QoS.
- Successful connections, publish attempts, PUBACK successes, failures, and
  reconnects.
- Message throughput and PUBACK latency p50, p95, p99, and maximum. PUBACK
  latency measures broker response, not backend persistence.

### Backend persistence

- Expected versus persisted telemetry count for the run.
- End-to-end publish-to-persistence latency, measured by matching each test
  message with its stored `capturedAt` and payload.
- Backend errors and queue/executor backlog where available.

### Server and database resources

- Sample each Compose container's CPU, memory, network I/O, and process count
  during the run. `docker stats` is a useful live view; save periodic samples
  to a result file to compare runs.
- Record backend JVM heap, threads, and HTTP request metrics.
- Record PostgreSQL CPU/memory, connection count, and query/write latency.

## Reporting and storage

- Write load-generator results and periodic resource samples to CSV or JSON.
- Add a concise GitHub Actions step summary containing workload settings,
  throughput, error counts, and latency percentiles.
- Upload the full result files as workflow artifacts, including when a test
  fails, so runs can be compared by commit and scenario.
- Keep credentials out of logs and result files.

## Implementation sequence

1. Extend the load test to emit structured per-run results, including p50,
   p95, p99, failure counts, and workload metadata.
2. Add a post-run persistence check that compares expected messages with
   telemetry returned by the API and calculates persistence latency.
3. Add a one-second resource sampler for the backend, broker, and database
   containers. Save its output alongside load results.
4. Add a manual GitHub Actions load-test workflow with selectable fleet size,
   interval, and duration. Upload results as an artifact and summarize key
   figures in the Actions run.
5. Run the scenarios incrementally, inspect CPU, memory, database load,
   queueing, and errors, then adjust limits only when measurements identify a
   bottleneck.
6. For continuous dashboards, add Micrometer's Prometheus registry, expose
   `/actuator/prometheus` only to the monitoring network, instrument MQTT
   ingestion counters/timers, and collect them with Prometheus and Grafana.

## Runner and network notes

- A Linux workflow runner does not need Conda. Use `actions/setup-python` or
  the host's Python 3 installation, then create a repository-local venv.
- If the runner and OpenRoots Compose stack are on the same host, use
  `127.0.0.1` for host-published ports. If they are on separate hosts, use
  the stack host's reachable LAN address and allow only the required private
  network traffic.
- The Windows Compose output showed Mosquitto bound to
  `192.168.29.147:1883`. In that configuration, `127.0.0.1:1883` is refused
  because Mosquitto is not bound to loopback. Set `MQTT_BIND_ADDRESS` to
  `127.0.0.1` and recreate Mosquitto for loopback-only access, or connect to
  the configured LAN address.

## References

- [Docker container resource statistics](https://docs.docker.com/reference/cli/docker/container/stats/)
- [Spring Boot Actuator metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)
- [Prometheus instrumentation practices](https://prometheus.io/docs/practices/instrumentation/)
- [GitHub Actions artifacts](https://docs.github.com/en/actions/tutorials/store-and-share-data)
