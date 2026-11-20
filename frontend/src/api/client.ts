import type {
  AuthResponse,
  CurrentUser,
  Device,
  Project,
  RegisterDeviceRequest,
  TelemetryRecord,
} from '../types/api'

type ApiResponse<T> = { success: boolean; message: string; data: T }

// The access token lives in localStorage so a page reload keeps the session.
const TOKEN_KEY = 'openroots.accessToken'

// Fired when the backend rejects a token we sent (expired, revoked, or the account is gone).
// App listens for it and returns to the sign-in screen.
export const SESSION_EXPIRED_EVENT = 'openroots.sessionExpired'

export const session = {
  getToken: (): string | null => window.localStorage.getItem(TOKEN_KEY),
  setToken: (token: string) => window.localStorage.setItem(TOKEN_KEY, token),
  clear: () => window.localStorage.removeItem(TOKEN_KEY),
}

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const token = session.getToken()
  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options?.headers,
    },
  })

  // Only a rejected token ends the session. A 401 from the login form itself
  // (wrong password, no token sent) is just a failed sign-in.
  if (response.status === 401 && token) {
    session.clear()
    window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
  }

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
  login: (email: string, password: string) =>
    request<AuthResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),
  register: (email: string, password: string) =>
    request<void>('/api/v1/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),
  me: () => request<CurrentUser>('/api/v1/auth/me'),
  listProjects: () => request<Project[]>('/api/v1/projects'),
  createProject: (name: string) =>
    request<Project>('/api/v1/projects', {
      method: 'POST',
      body: JSON.stringify({ name }),
    }),
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
