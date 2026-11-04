// These types describe the JSON data the backend sends to the frontend.

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
