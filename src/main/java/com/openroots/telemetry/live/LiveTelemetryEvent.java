package com.openroots.telemetry.live;

/**
 * One stored reading pushed to live subscribers. The JSON shape is shared with the
 * Go ingestion service so the frontend works against either implementation.
 */
public record LiveTelemetryEvent(
        String type,
        String deviceId,
        String sensorId,
        String channelName,
        double value,
        String recordedAt) {

    public static final String TYPE = "telemetry.reading";
}
