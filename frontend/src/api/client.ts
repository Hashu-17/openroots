import type {
  Device,
  Project,
  RegisterDeviceRequest,
  TelemetryRecord,
} from '../types/api'

type ApiResponse<T> = { success: boolean; message: string; data: T }

async function request<T>(url: string, options?: RequestInit): Promise<T> {

  const response = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })

  const rawBody = await response.text()
  let body: ApiResponse<T> | undefined

  try {
    body = rawBody ? (JSON.parse(rawBody) as ApiResponse<T>) : undefined
  } catch {
    throw new Error(`Backend returned an invalid response (${response.status}).`)
  }

  if (!response.ok || !body?.success) {
    const details = Array.isArray(body?.data) ? `: ${body.data.join(', ')}` : ''
    throw new Error(`${body?.message || `Backend request failed (${response.status}).`}${details}`)
  }
  return body.data
  
}

// Each function below represents one backend endpoint used by the frontend.
export const api = {
  listProjects: () => request<Project[]>('/api/v1/projects'),
  listProjectDevices: (projectId: string) =>
    request<Device[]>(`/api/v1/projects/${projectId}/devices`),
  registerDevice: (projectId: string, device: RegisterDeviceRequest) =>
    request<Device>(`/api/v1/projects/${projectId}/devices`, {
      method: 'POST',
      body: JSON.stringify(device),
    }),
  deleteDevice: (projectId: string, deviceId: string) =>
    request<void>(`/api/v1/projects/${projectId}/devices/${deviceId}`, { method: 'DELETE' }),
  listTelemetry: (deviceId: string) =>
    request<TelemetryRecord[]>(`/api/v1/telemetry?deviceId=${encodeURIComponent(deviceId)}`),
}
