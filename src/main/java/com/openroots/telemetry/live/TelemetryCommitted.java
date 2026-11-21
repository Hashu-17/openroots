package com.openroots.telemetry.live;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Published by the telemetry service for readings that were written in the current transaction. */
public record TelemetryCommitted(UUID deviceId, Instant recordedAt, List<StoredReading> readings) {

    public TelemetryCommitted {
        readings = List.copyOf(readings);
    }

    public record StoredReading(UUID sensorId, String channelName, double value) {
    }
}
