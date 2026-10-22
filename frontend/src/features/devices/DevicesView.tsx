import { LoaderCircle, Radio, Trash2 } from 'lucide-react'
import DeviceRegistrationForm from './DeviceRegistrationForm'
import type { Device } from '../../types/api'

// These props connect the device screen to data and actions owned by its parent.
type DevicesViewProps = {
  projectId: string
  devices: Device[]
  loading: boolean
  error: string
  notice: string
  onRegistered: () => Promise<void>
  onShowTelemetry: (device: Device) => void
  onDelete: (deviceId: string) => void
}

// Convert the backend timestamp into a shorter date for the inventory row.
function formatDate(value: string) {
  return new Date(value).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
}

// Show the saved device list beside the form used to add another device.
export default function DevicesView({ projectId, devices, loading, error, notice, onRegistered, onShowTelemetry, onDelete }: DevicesViewProps) {
  // The outer section places the inventory and registration form side by side on wide screens.
  return <section className="grid items-start gap-5 lg:grid-cols-[minmax(0,1.35fr)_minmax(360px,.85fr)]">
    <div className="min-h-[480px] border border-[#2b3537] bg-[#171e20]">
      <div className="flex items-center justify-between border-b border-[#2b3537] px-5 py-5 sm:px-7">
        <div><p className="mb-1 font-mono text-[10px] font-bold uppercase tracking-[.12em] text-[#6f817f]">Hardware inventory</p><h2 className="font-display text-xl font-bold text-[#f1f3ed]">Devices</h2></div>
        <span className="bg-[#263234] px-2 py-1.5 font-mono text-[10px] font-bold uppercase tracking-[.08em] text-[#82918e]">{devices.length.toString().padStart(2, '0')} total</span>
      </div>
      {error && <div className="m-5 border-l-4 border-[#dc755d] bg-[#321f1d] px-4 py-3 text-sm text-[#f1b0a0]" role="alert">{error}</div>}
      {notice && <div className="m-5 border-l-4 border-[#41c7a1] bg-[#172825] px-4 py-3 text-sm text-[#91ddc4]" role="status">{notice}</div>}
      {/* Show one state at a time: loading, an empty inventory, or the list of devices. */}
      {loading ? <div className="flex min-h-80 items-center justify-center gap-3 text-sm text-[#82918e]"><LoaderCircle className="size-5 animate-spin text-[#41c7a1]" />Loading devices...</div>
        : devices.length === 0 ? <div className="flex min-h-80 flex-col items-center justify-center gap-2 px-7 text-center text-sm text-[#82918e]"><Radio className="mb-2 size-10 text-[#6f817f]" /><strong className="font-display text-lg text-[#e7ebe7]">No devices registered</strong><span>Add the first physical device from the form.</span></div>
          : <div>{devices.map((device) => <article className="border-b border-[#2b3537] px-5 py-5 last:border-b-0 sm:px-7" key={device.id}>
            <div className="flex items-start gap-3.5">
              <div className="grid size-9 shrink-0 place-items-center bg-[#202a2c] text-[#e6b85a]"><Radio className="size-4" /></div>
              <div className="min-w-0 flex-1">
                {/* Selecting a device opens its telemetry; the status badge is informational. */}
                <div className="flex flex-wrap items-center gap-2">
                  <button className="border-0 bg-transparent p-0 text-left font-display text-base font-bold text-[#f1f3ed] transition hover:text-[#e6b85a]" type="button" onClick={() => onShowTelemetry(device)}>{device.name}</button>
                  <span className="bg-[#172825] px-1.5 py-0.5 font-mono text-[9px] font-bold uppercase text-[#41c7a1]">{device.status}</span>
                </div>
                <span className="font-mono text-[11px] text-[#82918e]">{device.type}</span>
                <div className="mt-3"><span className="font-mono text-[9px] font-bold uppercase tracking-[.08em] text-[#6f817f]">Device UUID</span><code className="mt-1 block break-all font-mono text-[11px] text-[#e6b85a] select-all">{device.id}</code></div>
                <div className="mt-3 flex flex-wrap gap-1.5">{device.sensors.map((sensor) => <span className="inline-flex items-baseline gap-1.5 bg-[#263234] px-2 py-1.5 font-mono text-[11px] font-semibold text-[#dbe2de]" key={sensor.id}>{sensor.channelName}<small className="text-[9px] font-normal text-[#82918e]">{sensor.unit ?? 'unitless'}</small></span>)}</div>
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <time className="font-mono text-[10px] text-[#6f817f]" dateTime={device.registeredAt}>{formatDate(device.registeredAt)}</time>
                <button className="grid size-7 place-items-center border border-[#51302b] bg-[#321f1d] text-[#dc755d] transition hover:border-[#dc755d] hover:bg-[#452521]" type="button" onClick={() => onDelete(device.id)} aria-label={`Delete ${device.name}`} title="Delete device"><Trash2 className="size-3.5" /></button>
              </div>
            </div>
          </article>)}</div>}
    </div>
    <DeviceRegistrationForm projectId={projectId} onRegistered={onRegistered} />
    <section className="border border-[#2b3537] bg-[#171e20] p-5 lg:col-span-2 sm:p-7">
      <div className="flex items-center justify-between gap-4"><div><p className="mb-1 font-mono text-[10px] font-bold uppercase tracking-[.12em] text-[#6f817f]">Inspect</p><h2 className="font-display text-xl font-bold text-[#f1f3ed]">Device telemetry</h2></div><span className="font-mono text-[10px] uppercase tracking-[.08em] text-[#82918e]">Select a device</span></div>
      <div className="mt-4 flex flex-wrap gap-2">{devices.map((device) => <button className="border border-[#36514c] bg-[#172825] px-3 py-2 font-mono text-xs font-bold text-[#91ddc4] transition hover:border-[#41c7a1] hover:text-[#f1f3ed]" key={device.id} onClick={() => onShowTelemetry(device)} type="button">{device.name}</button>)}</div>
    </section>
  </section>
}
