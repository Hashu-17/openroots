import { useEffect, useState } from 'react'
import * as Select from '@radix-ui/react-select'
import { ArrowLeft, Check, ChevronDown, FolderKanban, Radio, Settings2, Users } from 'lucide-react'
import DevicesView from '../features/devices/DevicesView'
import TelemetryPanel from '../TelemetryPanel'
import type { Device, Project, TelemetryRecord } from '../types/api'

type ProjectTab = 'overview' | 'devices' | 'members' | 'settings'

type ProjectWorkspaceProps = {
  projects: Project[]
  project?: Project
  projectId: string
  devices: Device[]
  devicesLoading: boolean
  devicesError: string
  devicesNotice: string
  telemetryDevice: Device | null
  telemetry: TelemetryRecord[]
  telemetryLoading: boolean
  telemetryError: string
  onBack: () => void
  onProjectChange: (projectId: string) => void
  onReloadDevices: () => Promise<void>
  onShowTelemetry: (device: Device) => void
  onDeleteDevice: (deviceId: string) => void
  onCloseTelemetry: () => void
}

function formatDate(value: string) {
  return new Date(value).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
}

function Unavailable({ title, description, icon }: { title: string; description: string; icon: React.ReactNode }) {
  return <div className="flex min-h-72 flex-col items-center justify-center border border-dashed border-[#394345] px-6 text-center"><span className="mb-4 text-[#6f817f]">{icon}</span><h2 className="font-display text-2xl font-bold text-[#e7ebe7]">{title}</h2><p className="mt-2 max-w-md text-sm text-[#82918e]">{description}</p></div>
}

export default function ProjectWorkspace(props: ProjectWorkspaceProps) {
  const [tab, setTab] = useState<ProjectTab>('overview')
  const { projects, project, projectId, devices, devicesLoading, devicesError, devicesNotice, telemetryDevice, telemetry, telemetryLoading, telemetryError } = props
  const projectTabs: { id: ProjectTab; label: string; icon: typeof FolderKanban; available: boolean }[] = [
    { id: 'overview', label: 'Overview', icon: FolderKanban, available: true },
    { id: 'devices', label: 'Devices', icon: Radio, available: true },
    { id: 'members', label: 'Members', icon: Users, available: false },
    { id: 'settings', label: 'Settings', icon: Settings2, available: false },
  ]

  useEffect(() => { setTab('overview') }, [projectId])

  useEffect(() => {
    if (tab !== 'devices' || !projectId) return
    const interval = window.setInterval(() => { void props.onReloadDevices() }, 5000)
    return () => window.clearInterval(interval)
  }, [projectId, props.onReloadDevices, tab])

  return <>
    <button className="mb-8 inline-flex items-center gap-2 border-0 bg-transparent p-0 font-mono text-[10px] font-bold uppercase tracking-[.12em] text-[#7f908e] transition hover:text-[#e6b85a]" onClick={props.onBack} type="button"><ArrowLeft className="size-4" />Back to projects</button>
    <section className="mb-8 flex flex-col justify-between gap-6 lg:flex-row lg:items-end">
      <div><p className="mb-2 font-mono text-[10px] font-bold uppercase tracking-[.16em] text-[#6f817f]">Project workspace</p><h1 className="font-display text-4xl font-bold leading-none text-[#f1f3ed] sm:text-5xl">{project?.name ?? 'Project'}</h1><p className="mt-3 font-mono text-[11px] text-[#82918e]">Created {project ? formatDate(project.createdAt) : '—'}</p></div>
      <div className="w-full max-w-xs"><span className="mb-2 block font-mono text-[10px] font-bold uppercase tracking-[.1em] text-[#6f817f]">Switch project</span><Select.Root value={projectId} onValueChange={props.onProjectChange}><Select.Trigger className="flex h-11 w-full items-center justify-between border border-[#394345] bg-[#171e20] px-3 text-left text-sm text-[#e7ebe7] outline-none focus:border-[#41c7a1] focus:ring-4 focus:ring-[#41c7a1]/10"><Select.Value /><Select.Icon><ChevronDown className="size-4 text-[#82918e]" /></Select.Icon></Select.Trigger><Select.Portal><Select.Content className="z-50 overflow-hidden border border-[#394345] bg-[#171e20] shadow-2xl"><Select.Viewport className="p-1">{projects.map((item) => <Select.Item className="relative flex cursor-pointer select-none items-center py-2 pl-8 pr-3 text-sm text-[#e7ebe7] outline-none data-[highlighted]:bg-[#263234]" key={item.id} value={item.id}><Select.ItemIndicator className="absolute left-2"><Check className="size-4 text-[#41c7a1]" /></Select.ItemIndicator><Select.ItemText>{item.name}</Select.ItemText></Select.Item>)}</Select.Viewport></Select.Content></Select.Portal></Select.Root></div>
    </section>
    <nav className="mb-7 flex gap-1 overflow-x-auto border-b border-[#2a3335]">{projectTabs.map(({ id, label, icon: Icon, available }) => <button className={`inline-flex shrink-0 items-center gap-2 border-b-2 px-3 pb-3 font-mono text-[10px] font-bold uppercase tracking-[.1em] transition ${tab === id ? 'border-[#41c7a1] text-[#e7ebe7]' : 'border-transparent text-[#6f817f] hover:text-[#b5c1be]'} ${!available ? 'cursor-not-allowed opacity-40' : ''}`} disabled={!available} key={id} onClick={() => setTab(id)} type="button"><Icon className="size-3.5" />{label}{!available && <span className="text-[8px]">Soon</span>}</button>)}</nav>
    {tab === 'overview' && <section className="grid gap-4 md:grid-cols-3">
      <div className="border border-[#2b3537] bg-[#171e20] p-6"><p className="font-mono text-[10px] uppercase tracking-[.12em] text-[#6f817f]">Registered devices</p><strong className="mt-5 block font-display text-5xl text-[#f1f3ed]">{devices.length}</strong><span className="mt-2 block text-sm text-[#82918e]">Hardware in this project</span></div>
      <div className="border border-[#2b3537] bg-[#171e20] p-6"><p className="font-mono text-[10px] uppercase tracking-[.12em] text-[#6f817f]">Sensor channels</p><strong className="mt-5 block font-display text-5xl text-[#e6b85a]">{devices.reduce((total, device) => total + device.sensors.length, 0)}</strong><span className="mt-2 block text-sm text-[#82918e]">Ready for telemetry</span></div>
      <div className="border border-[#2b3537] bg-[#171e20] p-6"><p className="font-mono text-[10px] uppercase tracking-[.12em] text-[#6f817f]">Members</p><strong className="mt-5 block font-display text-5xl text-[#82918e]">—</strong><span className="mt-2 block text-sm text-[#82918e]">Member management is coming later</span></div>
    </section>}
    {tab === 'members' && <Unavailable title="Member management is not enabled yet" description="Authentication and project roles are the next backend boundary." icon={<Users className="size-6" />} />}
    {tab === 'settings' && <Unavailable title="Project settings are not enabled yet" description="Project creation and configuration will arrive with the next management slice." icon={<Settings2 className="size-6" />} />}
    {tab === 'devices' && <>
      <DevicesView projectId={projectId} devices={devices} loading={devicesLoading} error={devicesError} notice={devicesNotice} onRegistered={props.onReloadDevices} onShowTelemetry={props.onShowTelemetry} onDelete={props.onDeleteDevice} />
      {telemetryDevice && <TelemetryPanel deviceName={telemetryDevice.name} records={telemetry} loading={telemetryLoading} error={telemetryError} onClose={props.onCloseTelemetry} />}
    </>}
  </>
}
