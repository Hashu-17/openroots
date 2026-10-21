# OpenRoots Platform Architecture Discussion

> **Status:** Planning record; this is a consolidated record of the conversation, not a verbatim chat transcript. It captures the questions, project facts, conclusions, and unresolved decisions discussed so far. It does not change the implementation or supersede the product requirements.
>
> **Prepared:** 2026-10-14

## Purpose

Record the architecture discussion about OpenRoots' current Spring Boot implementation, the initial TrueNAS deployment, telemetry retention, and possible growth to a live weather-oriented interface with thousands to tens of thousands of users. This document separates what the repository currently does from future options and assumptions.

## Executive summary

- The current backend is a **traditional Spring MVC application**, not a reactive WebFlux application. It uses Spring Boot 3.3.5, Java 21, embedded servlet web support, JPA, PostgreSQL/TimescaleDB, Flyway, validation, Spring Security, and Actuator.
- The current deployment is a three-container Compose stack: React/Nginx frontend, Spring backend, and TimescaleDB. The repository does not currently implement MQTT ingestion, WebSocket/SSE delivery, user authentication, or project authorization, even though some documents describe these as requirements or targets.
- A 16 GB TrueNAS host is a plausible starting point for a modest deployment such as the discussed 20 devices sending a few readings each minute. That is an estimate, not a capacity guarantee. TrueNAS, ZFS ARC, its other services, the database, and the application all share host memory. ARC does not reserve a fixed 8 GB.
- Spring Boot is not automatically a bottleneck because of its language or framework. For the initial workload, keeping the existing Spring application is a reasonable way to build product features. Measure real memory, database load, ingestion rate, query latency, and concurrent client behavior before replacing or splitting it.
- At a future target of 40,000–100,000 **simultaneously connected live clients**, treat live update fan-out as a distinct workload. A separate Go or Rust delivery service is a credible option to prototype and benchmark. Registered accounts, active sessions, open connections, and clients receiving each event are different quantities.
- Store telemetry in the time-series database with bounded time-range queries and retention/backup policies. A frontend showing one day by default should request a time range; it should not download all history and filter it in the browser.

## 1. Current project and deployment

### Runtime and database stack

The current `pom.xml` sets Spring Boot **3.3.5** and Java **21**. Its web dependency is `spring-boot-starter-web`, which gives the application the traditional Servlet/MVC stack. There is no WebFlux starter in the current dependency list. The app uses JPA/Hibernate and PostgreSQL, with Flyway migrations. The Compose database image is TimescaleDB 2.17.2 on PostgreSQL 16.

The application config sets a Hikari connection pool maximum of 10 and minimum idle of 5. No virtual-thread setting or JVM memory cap is present in the inspected configuration. Docker Compose does not currently set container memory limits for the backend or database.

### Containers and frontend

`docker-compose.yml` defines:

1. `frontend`, built from `frontend/` and served through Nginx;
2. `backend`, built from the root Dockerfile and exposed on port 8080 by default;
3. `database`, running TimescaleDB/PostgreSQL with a named persistent volume.

The frontend can be built and exercised separately using its own package scripts and Dockerfile. Docker Desktop's play/start control starts an existing container; it does not create a separate application-level feature. Container memory shown by Docker Desktop is the memory used by the selected container or group at that moment, while a Compose project view can aggregate several containers.

When comparing memory, select the specific container in Docker Desktop or inspect it with `docker stats <container>`. A Compose/project-level view can show a total across services, which is why the number may look like the earlier all-services run even when looking at one service. Compare the same metric and the same time window; host memory, container memory, JVM heap, and Java process RSS are different measurements.

### Implemented features versus documented targets

The repository currently has a REST API and UI for the implemented device and telemetry flows. In the inspected code, telemetry can be posted and retrieved by device, but the selected-device history query is not bounded by time. The current frontend therefore should not be assumed to fetch only one day until a date-range API and UI request are implemented.

The docs describe planned MQTT/HTTPS uplinks, user authentication, project membership, device credentials, commands, alerts, and real-time client updates. Those descriptions are target design unless the corresponding implementation exists. In particular, the current `SecurityConfig` uses `permitAll()`, and the authentication design document explicitly says authentication is not implemented. The current POM has no MQTT, WebSocket, or broker integration dependency; the related configuration classes are not evidence that those flows are operational.

**Deployment implication:** do not expose the current API as a production public service until authentication and authorization are implemented and reviewed. This is a current-state fact, separate from the performance discussion.

## 2. Initial workload and telemetry volume

The initial scenario discussed was approximately 20 devices, each reporting three channels once per minute. Under that specific assumption:

- 20 device requests per minute;
- 60 channel measurements per minute;
- 86,400 measurement values per day;
- 864,000 measurement values over ten days.

These counts are small as an ingestion rate for a properly configured relational/time-series database. They do not account for indexes, row overhead, retention metadata, backups, queries, retries, or higher reporting frequency. If each channel is stored as its own row (as in the inspected telemetry design), row count follows the channel measurement count.

The project should keep raw telemetry in the database for the configured retention period. “Move each day's telemetry to storage” should mean a clearly designed archival/export process only if older data must leave the active database. Backups are a separate recovery copy; they do not replace retention or a queryable history store.

## 3. UI history, retention, and backup

The desired UI behavior discussed was to show one day's telemetry by default, while allowing a user to inspect other days in a month or retention window. The robust API shape is a query bounded by explicit timestamps, for example `from` and `to`, with pagination or downsampling where appropriate. The database should do the filtering. A month-long chart can request a coarser or aggregated series rather than downloading every raw reading for every device and channel.

This approach limits the amount of data held by the API and browser for a request. Whether a month query is safe depends on the number of devices/channels, sample frequency, response size, and aggregation strategy; the date range alone does not guarantee safety.

Backups should run independently from UI queries and application memory limits. A streaming database dump/backup normally processes data in bounded chunks, so it should not require loading the entire database into RAM, though it still uses disk I/O, CPU, network bandwidth, and some working memory. Test backup and restore on the target host. Keep at least one backup copy on a separate device or location; a backup on the same pool does not cover pool loss.

For TimescaleDB, retention is normally expressed as a policy that removes old chunks. It is not the same as exporting old data to an archive. See [TimescaleDB data retention](https://docs.timescale.com/use-timescale/latest/data-retention/about-data-retention/).

## 4. Device message format and delivery

The project currently accepts telemetry through its HTTP API as JSON. That is a practical starting format: it is readable, easy to inspect during development, and broadly supported. The choice between JSON, plain text, or a compact binary encoding should be made from the actual device payload, bandwidth, parsing cost, and compatibility requirements. “Plain text” is not automatically more efficient than JSON, and binary encoding adds schema/tooling complexity. At the discussed initial cadence, the expected data volume does not by itself justify a protocol rewrite.

MQTT is a transport/protocol option for device-to-backend messaging, not a payload format by itself. A device can publish JSON or another encoded payload over MQTT. The current repository's dependencies and configuration do not yet implement an MQTT broker/client ingestion path. For a future high-volume deployment, separate device ingestion from browser delivery: MQTT (or another ingress) can receive device messages, while a broker/event stream can distribute normalized updates to live delivery services.

## 5. TrueNAS memory discussion

### The Gemini claims reviewed

The pasted Gemini response made several claims: ZFS will consume about 8 GB as cache; therefore only 6–8 GB remains for apps on a 16 GB host; Spring Boot becomes a bottleneck; Go is the best fit and uses 50–150 MB; Mosquitto stays below 100 MB; TimescaleDB fits in 2–3 GB; and the server can handle thousands of devices reporting every few seconds if lightweight software is chosen.

Those figures are not reliable sizing guarantees. The useful point is that apps, database processes, TrueNAS services, and ZFS compete for RAM. TrueNAS describes RAM as shared among services, apps, VMs, and ARC, and recommends at least 8 GB for basic operation; ARC is not a fixed 8 GB reservation. App memory reduces the RAM available for caching. Storage layout, drive count, L2ARC, other services, database workload, and NAS traffic all affect the real budget. See the [TrueNAS hardware guide](https://www.truenas.com/docs/scale/gettingstarted/scalehardwareguide/) and [ZFS primer](https://www.truenas.com/docs/references/zfsprimer/).

The language-specific memory numbers depend on configuration and workload. A small Go process can use less RAM than a Spring service, but that does not establish whole-system capacity or prove Spring is inadequate. The database, caches, connection count, query patterns, and payload processing can matter more than framework baseline memory.

### 16 GB assessment

For the initial 20-device, once-per-minute scenario, **16 GB is a plausible starting host for this stack**, subject to the NAS workload and observation under real use. This is a planning estimate, not a tested guarantee. Do not subtract an assumed fixed ARC size from 16 GB and call the remainder an application budget. Observe actual host memory, ARC behavior, swap, container RSS, database activity, and latency during representative operation.

The earlier 8 GB host discussion included a snapshot reporting about 1.7 GiB free before the backend started, with memory also attributed to ZFS cache and services. That single reading should not be treated as a permanent amount of safely available memory: cache is reclaimable to some degree, and available memory changes with workload. A backend allowed to grow near 1 GiB could leave little operational headroom on that 8 GB machine; a JVM cap is a guardrail, not proof that the app will fit or that the host will remain healthy.

When a JVM container limit is configured, budget for heap **and** metaspace, code cache, thread stacks, direct/native allocations, and JVM overhead. Container memory is not synonymous with Java heap. Apply a cap only after measuring startup and representative load, and leave room for the database and TrueNAS. Likewise, memory limits can constrain application spikes but do not create additional host RAM.

## 6. Spring Boot, Go, Rust, Quarkus, and GraalVM

### Why keep Spring Boot initially?

Spring Boot is a reasonable current choice because the project already uses it and its core work includes REST APIs, relational transactions, validation, security integration, persistence, and operational tooling. Spring has ecosystem support for MQTT integrations and WebSocket/SSE; support in the framework does not mean those features are configured in this repository today.

Quarkus is another Java framework with a strong focus on fast startup and native-image deployment. GraalVM Native Image compiles an application ahead of time into a native executable, which can reduce startup time and memory for some applications, with build-time/reflection constraints and different build complexity. Neither Quarkus nor native compilation automatically makes the database, queries, or fan-out workload faster. Compare them with a representative benchmark if startup or memory measurements show a real constraint.

Virtual threads can make blocking request code handle many concurrent waiting operations with lower thread overhead. They do not make CPU-bound work faster, make the database serve unlimited queries, or eliminate the need for backpressure and connection limits. The current project has not enabled them.

### What Go or Rust could do later

At larger scale, Go or Rust could be considered for a service with a measured need, for example:

- long-lived WebSocket/SSE connections and high fan-out;
- MQTT ingestion/normalization workers;
- CPU-intensive parsing or transformation;
- a resource-constrained edge agent or gateway.

Spring could continue to handle the product/control plane: user identity, subscriptions, authorization, device/project management, transactions, and history APIs. A split introduces operational and design work: service ownership, API/event contracts, authentication between services, retries, idempotency, observability, deployment, and data consistency. Do not split solely because a benchmark from another workload reports a language advantage.

### “Rust definitely helps” and large live UI scale

The future example discussed was a weather-responsive UI backed by sensor telemetry, potentially serving 40,000–100,000 users. That target changes the question: the design must account for **concurrent open clients** and the fan-out rate, not only REST request throughput or registered account count.

A rough traffic example illustrates why event cadence matters: 100,000 clients each receiving a 1 KB update every second implies about 100 MB/s (roughly 800 Mbps) of payload before protocol/network overhead. Sending that same update every ten seconds is about one tenth of that rate. Actual requirements depend on payload size, which clients subscribe to which devices/regions, update cadence, compression, reconnect behavior, and geographic distribution.

Rust may be a strong candidate for an efficient live-delivery gateway, and Go may also be a strong candidate. Neither language guarantees the target. Benchmark connection memory, CPU, fan-out throughput, latency, failure/reconnect behavior, and total network egress using the expected subscription pattern. Spring can also implement streaming endpoints, but the current application has no live-delivery implementation and should not be assumed to meet 100,000 concurrent connections without testing.

An eventual topology worth evaluating is:

```text
Devices -> MQTT/broker -> ingestion workers -> TimescaleDB
                                  |
                                  +-> live event stream -> delivery gateways -> browser/mobile clients

Browser/mobile clients -> Spring API for accounts, subscriptions, authorization, device management, history
```

The broker/event stream decouples ingestion from delivery and allows multiple delivery instances to distribute updates. It also adds infrastructure and operational complexity, so it belongs at a demonstrated scaling boundary rather than as a prerequisite for the current low-rate deployment.

The use of Rust by a large company for particular services is evidence that Rust can be useful for those workloads; it is not evidence that every component of OpenRoots should be Rust. Evaluate the service and workload, not the company name or language campaign.

## 7. Suggested evolution plan

These are planning steps, not work already completed:

1. **Finish and secure the monolith.** Implement the documented human/device authentication and project authorization before public exposure. Keep Spring MVC/JPA while the current deployment and team are building product functionality.
2. **Bound telemetry access.** Add time-range filters to the API and frontend; set retention explicitly; use aggregation/downsampling for broad chart ranges; avoid unbounded history responses.
3. **Measure the initial system.** Record backend and database container memory, host available memory/swap, CPU, ingestion success/latency, query latency, database size, and backup/restore duration on the intended TrueNAS host.
4. **Set resource limits from observations.** Configure container/JVM limits with enough host and database headroom. Validate startup, normal load, and short bursts. Monitor OOM kills and swap.
5. **Add live updates when the product needs them.** Define update cadence, subscription scope, stale-data behavior, client reconnect behavior, and whether users need every sample or only the latest/aggregated state.
6. **Load-test the target profile.** Specify concurrent clients, devices, sample frequency, payload size, subscription fan-out, history requests, and latency goals. Test the full path including database and network.
7. **Split a service only against a bottleneck.** If measured live connection/fan-out cost is the limiting factor, prototype a separate Go/Rust delivery gateway while retaining Spring's product and transaction functions. Compare cost and complexity against scaling Spring or using a managed delivery layer.

## 8. Decisions and non-decisions captured

### Working direction

- Continue with the existing Spring Boot application for the initial product and the currently discussed low telemetry rate.
- Keep TimescaleDB/PostgreSQL as the current relational and time-series store; do not switch to InfluxDB based only on generic claims about time-series performance.
- Make API history reads bounded and retention explicit.
- Consider a separate live-delivery service as a future benchmark candidate for high concurrent fan-out.
- Use measured workload results to decide whether Java, Go, Rust, Quarkus, GraalVM Native Image, or a split architecture addresses an actual constraint.

### Not decided

- Whether the final live gateway should use Go, Rust, Spring, or a managed service.
- The message broker/protocol and event schema for future ingestion and live delivery.
- The final concurrent-user target, telemetry cadence, payload size, latency objective, and availability target.
- The production server's exact TrueNAS edition/version, storage topology, and other workloads.
- Concrete memory limits, retention duration, archive format, and backup schedule. These require environment measurements and recovery requirements.

## References

- [TrueNAS hardware guide](https://www.truenas.com/docs/scale/gettingstarted/scalehardwareguide/)
- [TrueNAS ZFS primer](https://www.truenas.com/docs/references/zfsprimer/)
- [TimescaleDB data retention](https://docs.timescale.com/use-timescale/latest/data-retention/about-data-retention/)
- [Spring Boot system requirements and native images](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring MVC asynchronous requests](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html)
- [Spring WebSocket support](https://docs.spring.io/spring-framework/reference/web/websocket/stomp.html)
- Existing project docs: [`docs/product/requirements.md`](../product/requirements.md), [`docs/architecture/authentication.md`](../architecture/authentication.md), and [`docs/plans/phase-4-authentication.md`](phase-4-authentication.md).
