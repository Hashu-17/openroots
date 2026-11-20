// These types describe the JSON data the backend sends to the frontend.

// Returned by POST /api/v1/auth/login. The token goes in the Authorization header.
export type AuthResponse = { accessToken: string; tokenType: string; expiresInSeconds: number }

// Platform-wide role. Only ADMIN may create projects; project roles are separate.
export type PlatformRole = 'USER' | 'ADMIN'

// Returned by GET /api/v1/auth/me.
export type CurrentUser = { id: string; email: string; platformRole: PlatformRole }

export type Project = { id: string; name: string; createdAt: string }


export type Sensor = { id: string; channelName: string; unit: string | null }
export type Device = {
  id: string
  projectId: string
  name: string
  type: string
  status: string
  registeredAt: string
  sensors: Sensor[]
}

// SensorDraft is the editable form version of a sensor, before the backend assigns an ID.
export type SensorDraft = { channelName: string; unit: string }

// This is the JSON body sent when the user registers a device.
export type RegisterDeviceRequest = {
  name: string
  type: string
  sensors: { channelName: string; unit: string | null }[]
}

export type TelemetryRecord = {
  id: string
  deviceId: string
  capturedAt: string
  payload: Record<string, unknown>
}

// One stored reading pushed over the live telemetry WebSocket.
export type LiveTelemetryEvent = {
  type: 'telemetry.reading'
  deviceId: string
  sensorId: string
  channelName: string
  value: number
  recordedAt: string
}
