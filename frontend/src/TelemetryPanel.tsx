import { LoaderCircle, X } from 'lucide-react'
import type { TelemetryRecord } from './types/api'

type TelemetryPanelProps = {
  deviceName: string
  records: TelemetryRecord[]
  loading: boolean
  error: string
  onClose: () => void
}

// Format the received time so people can compare readings at a glance.
function formatTimestamp(value: string) {
  return new Date(value).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

// Display readings for one device, including loading, error, and no-data states.
export default function TelemetryPanel({ deviceName, records, loading, error, onClose }: TelemetryPanelProps) {
  // The parent supplies the selected device and data; this panel focuses on presentation.
  return <section className="mt-5 border border-[#36514c] bg-[#172825]">
    <div className="flex items-start justify-between gap-4 border-b border-[#36514c] px-5 py-5 sm:px-7">
      <div><p className="mb-1 font-mono text-[10px] font-bold uppercase tracking-[.12em] text-[#41c7a1]">Telemetry stream</p><h2 className="font-display text-xl font-bold text-[#f1f3ed]">{deviceName}</h2></div>
      <button className="grid size-8 place-items-center border border-[#36514c] bg-[#1c302d] text-[#91ddc4] transition hover:border-[#41c7a1]" onClick={onClose} type="button" aria-label="Close telemetry"><X className="size-4" /></button>
    </div>
    {error && <div className="m-5 border-l-4 border-[#dc755d] bg-[#321f1d] px-4 py-3 text-sm text-[#f1b0a0]">{error}</div>}
    {/* Render an explanation when there are no rows, otherwise show each reading and channel. */}
    {loading ? <div className="flex min-h-28 items-center justify-center gap-3 text-sm text-[#91ddc4]"><LoaderCircle className="size-5 animate-spin" />Loading telemetry...</div> : records.length === 0 ? <div className="px-5 py-10 text-center text-sm text-[#91b5ac]">No telemetry has been received yet.</div> : <div className="divide-y divide-[#36514c]">{records.map((record) => <article className="px-5 py-4 sm:px-7" key={record.id}><time className="font-mono text-[10px] uppercase tracking-[.08em] text-[#7fb8aa]" dateTime={record.capturedAt}>{formatTimestamp(record.capturedAt)}</time><div className="mt-3 flex flex-wrap gap-2">{Object.entries(record.payload).map(([channel, value]) => <span className="border border-[#36514c] bg-[#1c302d] px-3 py-2 font-mono text-xs text-[#e7ebe7]" key={channel}><strong className="text-[#e6b85a]">{channel}</strong><span className="mx-1.5 text-[#6f817f]">=</span>{String(value)}</span>)}</div></article>)}</div>}
  </section>
}
