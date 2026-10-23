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

export type SensorDraft = { channelName: string; unit: string }

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
