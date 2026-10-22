// These types describe the JSON data the backend sends to the frontend.
// Keeping them in one file lets the API client and screens agree on each field.
export type Project = { id: string; name: string; createdAt: string }

// Each sensor is one named measurement channel configured for a device.
export type Sensor = { id: string; channelName: string; unit: string | null }

// A Device is the saved form returned by the backend, including its generated ID and registration date.
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

// A telemetry record contains one received reading and its values, grouped by channel name.
export type TelemetryRecord = {
  id: string
  deviceId: string
  capturedAt: string
  payload: Record<string, unknown>
}
