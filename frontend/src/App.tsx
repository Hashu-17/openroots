import { useEffect, useState, type FormEvent } from 'react'
import * as Select from '@radix-ui/react-select'
import * as Tooltip from '@radix-ui/react-tooltip'
import DevicesView from './DevicesView'
import TelemetryPanel from './TelemetryPanel'
import {
  ArrowLeft,
  Check,
  ChevronDown,
  CirclePlus,
  Clock3,
  FolderKanban,
  LoaderCircle,
  Plus,
  Radio,
  Settings2,
  Trash2,
  Users,
  X,
} from 'lucide-react'

type Project = { id: string; name: string; createdAt: string }
type Sensor = { id: string; channelName: string; unit: string | null }
type Device = {
  id: string
  projectId: string
  name: string
  type: string
  status: string
  registeredAt: string
  sensors: Sensor[]
}
type SensorDraft = { channelName: string; unit: string }
type TelemetryRecord = { id: string; deviceId: string; capturedAt: string; payload: Record<string, unknown> }
type ApiResponse<T> = { success: boolean; message: string; data: T }
type View = 'projects' | 'project'
type ProjectTab = 'overview' | 'devices' | 'members' | 'settings'

const starterSensors: SensorDraft[] = [
  { channelName: 'temperature', unit: 'C' },
  { channelName: 'rain', unit: 'mm' },
  { channelName: 'uv', unit: 'index' },
]

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, { headers: { 'Content-Type': 'application/json' }, ...options })
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

function formatDate(value: string) {
  return new Date(value).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
}

function App() {
  const [projects, setProjects] = useState<Project[]>([])
  const [devices, setDevices] = useState<Device[]>([])
  const [selectedProjectId, setSelectedProjectId] = useState('')
  const [view, setView] = useState<View>('projects')
  const [tab, setTab] = useState<ProjectTab>('overview')
  const [deviceName, setDeviceName] = useState('')
  const [deviceType, setDeviceType] = useState('weather-station')
  const [sensors, setSensors] = useState<SensorDraft[]>(starterSensors)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [telemetryDevice, setTelemetryDevice] = useState<Device | null>(null)
  const [telemetry, setTelemetry] = useState<TelemetryRecord[]>([])
  const [telemetryLoading, setTelemetryLoading] = useState(false)
  const [telemetryError, setTelemetryError] = useState('')

  const selectedProject = projects.find((project) => project.id === selectedProjectId)

  async function loadProjects() {
     try {
      const nextProjects = await request<Project[]>('/api/v1/projects')
      setProjects(nextProjects.sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt)))
      setSelectedProjectId((current) => current || nextProjects[0]?.id || '')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to load projects.')
    } finally {
      setLoading(false)
    }
  }

  async function loadDevices(projectId: string) {
    if (!projectId) return setDevices([])
    setDevices(await request<Device[]>(`/api/v1/projects/${projectId}/devices`))
  }

  async function showTelemetry(device: Device) {
    setTelemetryDevice(device)
    setTelemetry([])
    setTelemetryError('')
    setTelemetryLoading(true)
    try {
      setTelemetry(await request<TelemetryRecord[]>(`/api/v1/telemetry?deviceId=${device.id}`))
    } catch (cause) {
      setTelemetryError(cause instanceof Error ? cause.message : 'Unable to load telemetry.')
    } finally {
      setTelemetryLoading(false)
    }
  }

  useEffect(() => { void loadProjects() }, [])

  useEffect(() => {
    if (view !== 'project' || !selectedProjectId) return
    void loadDevices(selectedProjectId).catch((cause) => setError(cause instanceof Error ? cause.message : 'Unable to load devices.'))
  }, [selectedProjectId, view])

  useEffect(() => {
    if (view !== 'project' || tab !== 'devices' || !selectedProjectId) return
    const refreshDevices = () => {
      void loadDevices(selectedProjectId).catch((cause) => setError(cause instanceof Error ? cause.message : 'Unable to refresh devices.'))
    }
    const interval = window.setInterval(refreshDevices, 5000)
    return () => window.clearInterval(interval)
  }, [selectedProjectId, tab, view])

  function openProject(projectId: string) {
    setSelectedProjectId(projectId)
    setTab('overview')
    setView('project')
    setError('')
  }

  function updateSensor(index: number, field: keyof SensorDraft, value: string) {
    setSensors((current) => current.map((sensor, sensorIndex) => sensorIndex === index ? { ...sensor, [field]: value } : sensor))
  }

  function resetForm() {
    setDeviceName('')
    setDeviceType('weather-station')
    setSensors(starterSensors.map((sensor) => ({ ...sensor })))
  }

  async function registerDevice(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setNotice('')
    const names = sensors.map((sensor) => sensor.channelName.trim()).filter(Boolean)
    if (!selectedProjectId || !deviceName.trim() || !deviceType.trim() || names.length === 0) return setError('Enter a device name, type, and at least one sensor.')
    if (sensors.some((sensor) => !sensor.channelName.trim())) return setError('Every sensor needs a channel name.')
    if (new Set(names).size !== names.length) return setError('Sensor channel names must be unique.')
    setSubmitting(true)
    try {
      await request<Device>(`/api/v1/projects/${selectedProjectId}/devices`, {
        method: 'POST',
        body: JSON.stringify({ name: deviceName.trim(), type: deviceType.trim(), sensors: sensors.map((sensor) => ({ channelName: sensor.channelName.trim(), unit: sensor.unit.trim() || null })) }),
      })
      await loadDevices(selectedProjectId)
      resetForm()
      setNotice('Device registered and saved to PostgreSQL.')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to register device.')
    } finally {
      setSubmitting(false)
    }
  }

  async function deleteDevice(deviceId: string) {
    const device = devices.find((item) => item.id === deviceId)
    if (!device || !window.confirm(`Delete ${device.name}? This permanently removes its sensors and telemetry.`)) return
    setError('')
    setNotice('')
    try {
      await request<void>(`/api/v1/projects/${selectedProjectId}/devices/${deviceId}`, { method: 'DELETE' })
      await loadDevices(selectedProjectId)
      setNotice(`${device.name} and all of its telemetry were deleted.`)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to delete device.')
    }
  }

  const projectTabs: { id: ProjectTab; label: string; icon: typeof FolderKanban; available: boolean }[] = [
    { id: 'overview', label: 'Overview', icon: FolderKanban, available: true },
    { id: 'devices', label: 'Devices', icon: Radio, available: true },
    { id: 'members', label: 'Members', icon: Users, available: false },
    { id: 'settings', label: 'Settings', icon: Settings2, available: false },
  ]

  return (
    <Tooltip.Provider delayDuration={300}>
      <div className="min-h-screen bg-[#101416] text-[#e7ebe7]">
        <header className="border-b border-[#2a3335] bg-[#141a1c]">
          <div className="mx-auto flex min-h-[78px] max-w-[1280px] items-center justify-between px-6 sm:px-10">
            <button className="group flex items-center gap-3 text-left" onClick={() => setView('projects')} type="button">
              <span className="grid size-9 place-items-center bg-[#e6b85a] font-mono text-xs font-extrabold text-[#101416]">OR</span>
              <span><span className="block font-mono text-[10px] font-bold uppercase tracking-[.14em] text-[#7f908e]">OpenRoots / Control</span><span className="font-display text-lg font-bold text-[#f1f3ed] transition group-hover:text-[#e6b85a]">Operations</span></span>
            </button>
            <div className="flex items-center gap-2 font-mono text-[10px] font-semibold uppercase tracking-[.1em] text-[#7f908e]"><span className="size-2 rounded-full bg-[#41c7a1] shadow-[0_0_0_4px_rgba(65,199,161,.12)]" /><span className="hidden sm:inline">Development workspace</span></div>
          </div>
        </header>

        <main className="mx-auto max-w-[1280px] px-6 pb-16 pt-10 sm:px-10 sm:pt-14">
          {view === 'projects' ? (
            <>
              <section className="mb-10 flex flex-col justify-between gap-7 sm:flex-row sm:items-end">
                <div><p className="mb-2 font-mono text-[10px] font-bold uppercase tracking-[.16em] text-[#6f817f]">Workspace index</p><h1 className="font-display text-4xl font-bold leading-none text-[#f1f3ed] sm:text-6xl">Projects</h1><p className="mt-4 max-w-xl text-sm text-[#8d9b99]">Choose an operational space to inspect its hardware, sensors, and team context.</p></div>
                <Tooltip.Root><Tooltip.Trigger asChild><button className="inline-flex h-11 cursor-not-allowed items-center justify-center gap-2 border border-[#394345] bg-[#1a2224] px-4 font-mono text-xs font-bold text-[#667674]" disabled type="button"><Plus className="size-4" />New project</button></Tooltip.Trigger><Tooltip.Portal><Tooltip.Content className="rounded bg-[#e7ebe7] px-2.5 py-1.5 font-mono text-[10px] text-[#101416]" sideOffset={6}>Project creation comes with the next backend slice.<Tooltip.Arrow className="fill-[#e7ebe7]" /></Tooltip.Content></Tooltip.Portal></Tooltip.Root>
              </section>
              {error && <div className="mb-5 border-l-4 border-[#dc755d] bg-[#321f1d] px-4 py-3 text-sm text-[#f1b0a0]" role="alert">{error}</div>}
              {loading ? <div className="flex min-h-64 items-center justify-center gap-3 text-sm text-[#82918e]"><LoaderCircle className="size-5 animate-spin text-[#41c7a1]" />Loading project index...</div> : projects.length === 0 ? <div className="border border-dashed border-[#394345] px-6 py-20 text-center text-sm text-[#82918e]">No projects are available in this workspace.</div> : <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">{projects.map((project, index) => <button className="group relative min-h-52 border border-[#2b3537] bg-[#171e20] p-6 text-left transition hover:-translate-y-0.5 hover:border-[#55706c] hover:bg-[#1c2527]" key={project.id} onClick={() => openProject(project.id)} type="button"><span className="absolute right-5 top-5 font-mono text-[10px] text-[#4e5e5d]">{String(index + 1).padStart(2, '0')}</span><span className="mb-10 grid size-9 place-items-center border border-[#36514c] bg-[#172825] text-[#41c7a1]"><FolderKanban className="size-4" /></span><strong className="block font-display text-2xl font-bold text-[#f1f3ed] group-hover:text-[#e6b85a]">{project.name}</strong><span className="mt-3 flex items-center gap-1.5 font-mono text-[10px] uppercase tracking-[.08em] text-[#7f908e]"><Clock3 className="size-3.5" />Created {formatDate(project.createdAt)}</span><span className="mt-5 block font-mono text-[10px] font-bold uppercase tracking-[.1em] text-[#41c7a1] opacity-0 transition group-hover:opacity-100">Open project →</span></button>)}</div>}
            </>
          ) : (
            <>
              <button className="mb-8 inline-flex items-center gap-2 border-0 bg-transparent p-0 font-mono text-[10px] font-bold uppercase tracking-[.12em] text-[#7f908e] transition hover:text-[#e6b85a]" onClick={() => setView('projects')} type="button"><ArrowLeft className="size-4" />Back to projects</button>
              <section className="mb-8 flex flex-col justify-between gap-6 lg:flex-row lg:items-end"><div><p className="mb-2 font-mono text-[10px] font-bold uppercase tracking-[.16em] text-[#6f817f]">Project workspace</p><h1 className="font-display text-4xl font-bold leading-none text-[#f1f3ed] sm:text-5xl">{selectedProject?.name ?? 'Project'}</h1><p className="mt-3 font-mono text-[11px] text-[#82918e]">Created {selectedProject ? formatDate(selectedProject.createdAt) : '—'}</p></div><div className="w-full max-w-xs"><span className="mb-2 block font-mono text-[10px] font-bold uppercase tracking-[.1em] text-[#6f817f]">Switch project</span><Select.Root value={selectedProjectId} onValueChange={openProject}><Select.Trigger className="flex h-11 w-full items-center justify-between border border-[#394345] bg-[#171e20] px-3 text-left text-sm text-[#e7ebe7] outline-none focus:border-[#41c7a1] focus:ring-4 focus:ring-[#41c7a1]/10"><Select.Value /><Select.Icon><ChevronDown className="size-4 text-[#82918e]" /></Select.Icon></Select.Trigger><Select.Portal><Select.Content className="z-50 overflow-hidden border border-[#394345] bg-[#171e20] shadow-2xl"><Select.Viewport className="p-1">{projects.map((project) => <Select.Item className="relative flex cursor-pointer select-none items-center py-2 pl-8 pr-3 text-sm text-[#e7ebe7] outline-none data-[highlighted]:bg-[#263234]" key={project.id} value={project.id}><Select.ItemIndicator className="absolute left-2"><Check className="size-4 text-[#41c7a1]" /></Select.ItemIndicator><Select.ItemText>{project.name}</Select.ItemText></Select.Item>)}</Select.Viewport></Select.Content></Select.Portal></Select.Root></div></section>
              <nav className="mb-7 flex gap-1 overflow-x-auto border-b border-[#2a3335]">{projectTabs.map(({ id, label, icon: Icon, available }) => <button className={`inline-flex shrink-0 items-center gap-2 border-b-2 px-3 pb-3 font-mono text-[10px] font-bold uppercase tracking-[.1em] transition ${tab === id ? 'border-[#41c7a1] text-[#e7ebe7]' : 'border-transparent text-[#6f817f] hover:text-[#b5c1be]'} ${!available ? 'cursor-not-allowed opacity-40' : ''}`} disabled={!available} key={id} onClick={() => setTab(id)} type="button"><Icon className="size-3.5" />{label}{!available && <span className="text-[8px]">Soon</span>}</button>)}</nav>
              {error && <div className="mb-5 border-l-4 border-[#dc755d] bg-[#321f1d] px-4 py-3 text-sm text-[#f1b0a0]" role="alert">{error}</div>}
              {tab === 'overview' && <section className="grid gap-4 md:grid-cols-3"><div className="border border-[#2b3537] bg-[#171e20] p-6"><p className="font-mono text-[10px] uppercase tracking-[.12em] text-[#6f817f]">Registered devices</p><strong className="mt-5 block font-display text-5xl text-[#f1f3ed]">{devices.length}</strong><span className="mt-2 block text-sm text-[#82918e]">Hardware in this project</span></div><div className="border border-[#2b3537] bg-[#171e20] p-6"><p className="font-mono text-[10px] uppercase tracking-[.12em] text-[#6f817f]">Sensor channels</p><strong className="mt-5 block font-display text-5xl text-[#e6b85a]">{devices.reduce((total, device) => total + device.sensors.length, 0)}</strong><span className="mt-2 block text-sm text-[#82918e]">Ready for telemetry</span></div><div className="border border-[#2b3537] bg-[#171e20] p-6"><p className="font-mono text-[10px] uppercase tracking-[.12em] text-[#6f817f]">Members</p><strong className="mt-5 block font-display text-5xl text-[#82918e]">—</strong><span className="mt-2 block text-sm text-[#82918e]">Member management is coming later</span></div></section>}
              {tab === 'members' && <Unavailable title="Member management is not enabled yet" description="Authentication and project roles are the next backend boundary." icon={<Users className="size-6" />} />}
              {tab === 'settings' && <Unavailable title="Project settings are not enabled yet" description="Project creation and configuration will arrive with the next management slice." icon={<Settings2 className="size-6" />} />}
              {tab === 'devices' && <><DevicesView devices={devices} loading={loading} error={error} notice={notice} deviceName={deviceName} deviceType={deviceType} sensors={sensors} submitting={submitting} onSubmit={registerDevice} onShowTelemetry={showTelemetry} onDelete={deleteDevice} setDeviceName={setDeviceName} setDeviceType={setDeviceType} setSensors={setSensors} updateSensor={updateSensor} removeSensor={(index) => setSensors((current) => current.filter((_, sensorIndex) => sensorIndex !== index))} />{telemetryDevice && <TelemetryPanel deviceName={telemetryDevice.name} records={telemetry} loading={telemetryLoading} error={telemetryError} onClose={() => setTelemetryDevice(null)} />}</>}
            </>
          )}
        </main>
        <footer className="border-t border-[#2a3335] bg-[#141a1c]"><div className="mx-auto flex max-w-[1280px] flex-col gap-1 px-6 py-5 font-mono text-[10px] text-[#667674] sm:flex-row sm:items-center sm:justify-between sm:px-10"><span>OpenRoots local control surface</span><span>API /api/v1 · Persistence online</span></div></footer>
      </div>
    </Tooltip.Provider>
  )
}

function Unavailable({ title, description, icon }: { title: string; description: string; icon: React.ReactNode }) {
  return <div className="flex min-h-72 flex-col items-center justify-center border border-dashed border-[#394345] px-6 text-center"><span className="mb-4 text-[#6f817f]">{icon}</span><h2 className="font-display text-2xl font-bold text-[#e7ebe7]">{title}</h2><p className="mt-2 max-w-md text-sm text-[#82918e]">{description}</p></div>
}

function DeleteDevicePanel({ devices, onDelete }: { devices: Device[]; onDelete: (deviceId: string) => void }) {
  if (devices.length === 0) return null
  return <section className="mt-5 border border-[#51302b] bg-[#211817] p-5 sm:p-7"><div className="flex items-start justify-between gap-4"><div><p className="mb-1 font-mono text-[10px] font-bold uppercase tracking-[.12em] text-[#dc755d]">Danger zone</p><h2 className="font-display text-xl font-bold text-[#f1f3ed]">Delete a device</h2><p className="mt-2 text-sm text-[#b9928a]">Deleting a device permanently removes its sensors and stored telemetry.</p></div><Trash2 className="size-5 shrink-0 text-[#dc755d]" /></div><div className="mt-5 flex flex-wrap gap-2">{devices.map((device) => <button className="inline-flex items-center gap-2 border border-[#704037] bg-[#321f1d] px-3 py-2 font-mono text-[10px] font-bold text-[#f1b0a0] transition hover:border-[#dc755d] hover:bg-[#452521]" key={device.id} onClick={() => onDelete(device.id)} type="button"><Trash2 className="size-3.5" />{device.name}</button>)}</div></section>
}

function LegacyDevicesView({ devices, loading, error, notice, deviceName, deviceType, sensors, submitting, onSubmit, setDeviceName, setDeviceType, setSensors, updateSensor, removeSensor }: { devices: Device[]; loading: boolean; error: string; notice: string; deviceName: string; deviceType: string; sensors: SensorDraft[]; submitting: boolean; onSubmit: (event: FormEvent<HTMLFormElement>) => void; setDeviceName: (value: string) => void; setDeviceType: (value: string) => void; setSensors: React.Dispatch<React.SetStateAction<SensorDraft[]>>; updateSensor: (index: number, field: keyof SensorDraft, value: string) => void; removeSensor: (index: number) => void }) {
  return <section className="grid items-start gap-5 lg:grid-cols-[minmax(0,1.35fr)_minmax(360px,.85fr)]"><div className="min-h-[480px] border border-[#2b3537] bg-[#171e20]"><div className="flex items-center justify-between border-b border-[#2b3537] px-5 py-5 sm:px-7"><div><p className="mb-1 font-mono text-[10px] font-bold uppercase tracking-[.12em] text-[#6f817f]">Hardware inventory</p><h2 className="font-display text-xl font-bold text-[#f1f3ed]">Devices</h2></div><span className="bg-[#263234] px-2 py-1.5 font-mono text-[10px] font-bold uppercase tracking-[.08em] text-[#82918e]">{devices.length.toString().padStart(2, '0')} total</span></div>{error && <div className="m-5 border-l-4 border-[#dc755d] bg-[#321f1d] px-4 py-3 text-sm text-[#f1b0a0]">{error}</div>}{notice && <div className="m-5 border-l-4 border-[#41c7a1] bg-[#172825] px-4 py-3 text-sm text-[#91ddc4]">{notice}</div>}{loading ? <div className="flex min-h-80 items-center justify-center gap-3 text-sm text-[#82918e]"><LoaderCircle className="size-5 animate-spin text-[#41c7a1]" />Loading devices...</div> : devices.length === 0 ? <div className="flex min-h-80 flex-col items-center justify-center gap-2 px-7 text-center text-sm text-[#82918e]"><Radio className="mb-2 size-10 text-[#6f817f]" /><strong className="font-display text-lg text-[#e7ebe7]">No devices registered</strong><span>Add the first physical device from the form.</span></div> : <div>{devices.map((device) => <article className="grid grid-cols-[36px_1fr_auto] items-start gap-3.5 border-b border-[#2b3537] px-5 py-5 last:border-b-0 sm:px-7" key={device.id}><div className="grid size-9 place-items-center bg-[#202a2c] text-[#e6b85a]"><Radio className="size-4" /></div><div><div className="flex flex-wrap items-center gap-2"><strong className="font-display text-base font-bold text-[#f1f3ed]">{device.name}</strong><span className="bg-[#172825] px-1.5 py-0.5 font-mono text-[9px] font-bold uppercase text-[#41c7a1]">{device.status}</span></div><span className="font-mono text-[11px] text-[#82918e]">{device.type}</span><div className="mt-3"><span className="font-mono text-[9px] font-bold uppercase tracking-[.08em] text-[#6f817f]">Device UUID</span><code className="mt-1 block break-all font-mono text-[11px] text-[#e6b85a] select-all">{device.id}</code></div><div className="mt-3 flex flex-wrap gap-1.5">{device.sensors.map((sensor) => <span className="inline-flex items-baseline gap-1.5 bg-[#263234] px-2 py-1.5 font-mono text-[11px] font-semibold text-[#dbe2de]" key={sensor.id}>{sensor.channelName}<small className="text-[9px] font-normal text-[#82918e]">{sensor.unit ?? 'unitless'}</small></span>)}</div></div><time className="font-mono text-[10px] text-[#6f817f]" dateTime={device.registeredAt}>{formatDate(device.registeredAt)}</time></article>)}</div>}</div><form className="border border-[#2b3537] bg-[#171e20] pb-7" onSubmit={onSubmit}><div className="mb-7 border-b border-[#2b3537] px-5 py-5 sm:px-7"><p className="mb-1 font-mono text-[10px] font-bold uppercase tracking-[.12em] text-[#6f817f]">Add hardware</p><h2 className="font-display text-xl font-bold text-[#f1f3ed]">Register a device</h2></div><div className="grid gap-3.5 px-5 sm:grid-cols-2 sm:px-7">{([['Device name', deviceName, setDeviceName, 'e.g. North greenhouse'], ['Device type', deviceType, setDeviceType, 'e.g. weather-station']] as const).map(([label, value, setter, placeholder]) => <label className="grid gap-2 font-mono text-[10px] font-bold uppercase tracking-[.08em] text-[#82918e]" key={label}><span>{label}</span><input className="h-11 w-full border border-[#394345] bg-[#111719] px-3 text-sm font-normal normal-case tracking-normal text-[#e7ebe7] outline-none placeholder:text-[#566563] focus:border-[#41c7a1] focus:ring-4 focus:ring-[#41c7a1]/10" value={value} onChange={(event) => setter(event.target.value)} placeholder={placeholder} /></label>)}</div><div className="mx-5 my-7 flex items-start justify-between gap-3 sm:mx-7"><div><span className="font-mono text-[11px] font-bold uppercase tracking-[.08em] text-[#e7ebe7]">Sensors</span><small className="mt-1.5 block text-xs text-[#82918e]">Define the channels this device exposes.</small></div><button className="inline-flex items-center gap-1.5 border-0 bg-transparent p-0 font-mono text-[11px] font-bold text-[#e6b85a] transition hover:text-[#f1f3ed]" type="button" onClick={() => setSensors((current) => [...current, { channelName: '', unit: '' }])}><CirclePlus className="size-4" />Add sensor</button></div><div className="grid gap-2 px-5 sm:px-7">{sensors.map((sensor, index) => <div className="grid grid-cols-[25px_1fr_75px_25px] items-center gap-1.5" key={`${index}-${sensor.channelName}`}><span className="font-mono text-[10px] text-[#6f817f]">{String(index + 1).padStart(2, '0')}</span><input className="h-10 min-w-0 border border-[#394345] bg-[#111719] px-2.5 text-xs text-[#e7ebe7] outline-none placeholder:text-[#566563] focus:border-[#41c7a1] focus:ring-4 focus:ring-[#41c7a1]/10" aria-label={`Sensor ${index + 1} channel`} value={sensor.channelName} onChange={(event) => updateSensor(index, 'channelName', event.target.value)} placeholder="channel name" /><input className="h-10 min-w-0 border border-[#394345] bg-[#111719] px-2.5 text-xs text-[#e7ebe7] outline-none placeholder:text-[#566563] focus:border-[#41c7a1] focus:ring-4 focus:ring-[#41c7a1]/10" aria-label={`Sensor ${index + 1} unit`} value={sensor.unit} onChange={(event) => updateSensor(index, 'unit', event.target.value)} placeholder="unit" /><Tooltip.Root><Tooltip.Trigger asChild><button className="grid size-6 place-items-center border-0 bg-transparent text-[#82918e] transition hover:text-[#dc755d] disabled:cursor-not-allowed disabled:opacity-30" type="button" onClick={() => removeSensor(index)} disabled={sensors.length === 1} aria-label={`Remove sensor ${index + 1}`}><X className="size-4" /></button></Tooltip.Trigger><Tooltip.Portal><Tooltip.Content className="rounded bg-[#e7ebe7] px-2 py-1 font-mono text-[10px] text-[#101416]" sideOffset={5}>Remove sensor<Tooltip.Arrow className="fill-[#e7ebe7]" /></Tooltip.Content></Tooltip.Portal></Tooltip.Root></div>)}</div><button className="mx-5 mt-7 flex h-12 w-[calc(100%-2.5rem)] items-center justify-center border-0 bg-[#dc755d] font-mono text-xs font-bold text-[#101416] transition hover:-translate-y-0.5 hover:bg-[#e98972] disabled:cursor-wait disabled:opacity-60 sm:mx-7 sm:w-[calc(100%-3.5rem)]" type="submit" disabled={submitting}>{submitting ? 'Saving device...' : 'Register device →'}</button><p className="mx-5 mt-3 text-[11px] text-[#82918e] sm:mx-7">Saved to the active project in PostgreSQL. Authentication will be added in a later phase.</p></form></section>
}

void DeleteDevicePanel
void LegacyDevicesView

export default App
