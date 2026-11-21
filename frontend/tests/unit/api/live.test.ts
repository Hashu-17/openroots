import { describe, expect, it } from 'vitest'
import { liveTelemetryUrl, mergeLiveReading } from '../../../src/api/live'
import type { LiveTelemetryEvent, TelemetryRecord } from '../../../src/types/api'

const deviceId = '7e2b2dc8-43c1-4e1f-b1ba-fd67d098f1a2'

function reading(channelName: string, value: number, recordedAt: string): LiveTelemetryEvent {
  return { type: 'telemetry.reading', deviceId, sensorId: 's', channelName, value, recordedAt }
}

describe('mergeLiveReading', () => {
  const older: TelemetryRecord = { id: 'a', deviceId, capturedAt: '2026-11-21T10:00:00Z', payload: { temperature: 20 } }

  it('adds a new reading at the top of the newest-first list', () => {
    const merged = mergeLiveReading([older], reading('temperature', 21, '2026-11-21T10:00:10Z'))
    expect(merged.map((record) => record.payload.temperature)).toEqual([21, 20])
  })

  it('combines channels with the same timestamp, whatever the timestamp format', () => {
    const first = mergeLiveReading([older], reading('humidity', 55, '2026-11-21T10:00:00.000Z'))
    expect(first).toHaveLength(1)
    expect(first[0].payload).toEqual({ temperature: 20, humidity: 55 })
  })

  it('places a late reading in time order', () => {
    const newer: TelemetryRecord = { id: 'b', deviceId, capturedAt: '2026-11-21T10:00:20Z', payload: { temperature: 22 } }
    const merged = mergeLiveReading([newer, older], reading('temperature', 21, '2026-11-21T10:00:10Z'))
    expect(merged.map((record) => record.payload.temperature)).toEqual([22, 21, 20])
  })
})

describe('liveTelemetryUrl', () => {
  it('uses wss behind https and keeps the host', () => {
    const location = { protocol: 'https:', host: 'example.org' } as Location
    expect(liveTelemetryUrl(deviceId, location)).toBe(`wss://example.org/api/v1/devices/${deviceId}/telemetry/live`)
  })
})
