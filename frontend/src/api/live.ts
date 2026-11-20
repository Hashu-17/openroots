import type { LiveTelemetryEvent, TelemetryRecord } from '../types/api'

// Live telemetry over a WebSocket. The same protocol is served by Spring or by the
// Go ingestion service (the reverse proxy decides), so this client works with either:
//   1. open /api/v1/devices/{id}/telemetry/live
//   2. send {"type":"auth","token":...} as the first message (no token in the URL)
//   3. receive {"type":"ready"}, then one {"type":"telemetry.reading", ...} per stored value.
// The server closes the socket when the access token expires.

export type LiveStatus = 'connecting' | 'live' | 'offline'

const RECONNECT_DELAY_MS = 3000
// Close codes after which reconnecting cannot help.
const FATAL_CLOSE_CODES = new Set([4400, 4401, 4404])

export function liveTelemetryUrl(deviceId: string, location: Location = window.location): string {
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws'
  return `${scheme}://${location.host}/api/v1/devices/${encodeURIComponent(deviceId)}/telemetry/live`
}

export function openLiveTelemetry(
  deviceId: string,
  getToken: () => string | null,
  onReading: (event: LiveTelemetryEvent) => void,
  onStatus: (status: LiveStatus) => void,
): () => void {
  let socket: WebSocket | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | undefined
  let stopped = false

  function connect() {
    const token = getToken()
    if (!token) {
      onStatus('offline')
      return
    }
    onStatus('connecting')
    socket = new WebSocket(liveTelemetryUrl(deviceId))
    socket.onopen = () => socket?.send(JSON.stringify({ type: 'auth', token }))
    socket.onmessage = (message) => {
      let event: { type?: string }
      try {
        event = JSON.parse(String(message.data))
      } catch {
        return
      }
      if (event.type === 'ready') onStatus('live')
      else if (event.type === 'telemetry.reading') onReading(event as LiveTelemetryEvent)
    }
    socket.onclose = (closed) => {
      socket = null
      onStatus('offline')
      if (stopped || FATAL_CLOSE_CODES.has(closed.code)) return
      reconnectTimer = setTimeout(connect, RECONNECT_DELAY_MS)
    }
  }

  connect()
  return () => {
    stopped = true
    clearTimeout(reconnectTimer)
    socket?.close(1000)
  }
}

// Fold one live reading into the newest-first record list. Readings with the same
// capturedAt belong to one record, one channel at a time.
export function mergeLiveReading(records: TelemetryRecord[], event: LiveTelemetryEvent): TelemetryRecord[] {
  const time = Date.parse(event.recordedAt)
  const index = records.findIndex((record) => record.deviceId === event.deviceId && Date.parse(record.capturedAt) === time)
  if (index >= 0) {
    const existing = records[index]
    const updated = { ...existing, payload: { ...existing.payload, [event.channelName]: event.value } }
    return [...records.slice(0, index), updated, ...records.slice(index + 1)]
  }
  const record: TelemetryRecord = {
    id: `live:${event.deviceId}:${time}`,
    deviceId: event.deviceId,
    capturedAt: event.recordedAt,
    payload: { [event.channelName]: event.value },
  }
  const insertAt = records.findIndex((existing) => Date.parse(existing.capturedAt) < time)
  return insertAt < 0 ? [...records, record] : [...records.slice(0, insertAt), record, ...records.slice(insertAt)]
}
