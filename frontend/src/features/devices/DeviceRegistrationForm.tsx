import { useState, type FormEvent } from 'react'
import * as Tooltip from '@radix-ui/react-tooltip'
import { CirclePlus, X } from 'lucide-react'
import { api } from '../../api/client'
import type { SensorDraft } from '../../types/api'

const starterSensors: SensorDraft[] = [
  { channelName: 'temperature', unit: 'C' },
  { channelName: 'rain', unit: 'mm' },
  { channelName: 'uv', unit: 'index' },
]

type DeviceRegistrationFormProps = {
  projectId: string
  onRegistered: () => Promise<void>
}

export default function DeviceRegistrationForm({ projectId, onRegistered }: DeviceRegistrationFormProps) {
  // Each useState call stores one value that can change while the user edits or submits the form.
  const [deviceName, setDeviceName] = useState('')
  const [deviceType, setDeviceType] = useState('weather-station')
  const [sensors, setSensors] = useState<SensorDraft[]>(starterSensors)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  // Update only the selected sensor field while keeping the other sensors unchanged.
  function updateSensor(index: number, field: keyof SensorDraft, value: string) {
    setSensors((current) => current.map((sensor, sensorIndex) =>
      sensorIndex === index ? { ...sensor, [field]: value } : sensor,
    ))
  }

  
  async function submit(event: FormEvent<HTMLFormElement>) {
    // Prevent the browser from reloading the page when a form is submitted.
    event.preventDefault()
    setError('')
    setNotice('')
    const names = sensors.map((sensor) => sensor.channelName.trim()).filter(Boolean)

  
    if (!projectId || !deviceName.trim() || !deviceType.trim() || names.length === 0) {
      setError('Enter a device name, type, and at least one sensor.')
      return
    }
    // A blank sensor row is not allowed, even if another row has a valid channel name.
    if (sensors.some((sensor) => !sensor.channelName.trim())) {
      setError('Every sensor needs a channel name.')
      return
    }
   
    if (new Set(names).size !== names.length) {
      setError('Sensor channel names must be unique.')
      return
    }

    
    setSubmitting(true)
    try {
      //Trimming whitespace
      await api.registerDevice(projectId, {
        name: deviceName.trim(),
        type: deviceType.trim(),
        sensors: sensors.map((sensor) => ({
          channelName: sensor.channelName.trim(),
          unit: sensor.unit.trim() || null,
        })),
      })
      
      await onRegistered()
      // Reset the form 
      setDeviceName('')
      setDeviceType('weather-station')
      setSensors(starterSensors.map((sensor) => ({ ...sensor })))
      setNotice('Device registered and saved to PostgreSQL.')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to register device.')
    } finally {
      setSubmitting(false)
    }
  }

  return <form className="border border-[#2b3537] bg-[#171e20] pb-7" onSubmit={submit}>
    <div className="mb-7 border-b border-[#2b3537] px-5 py-5 sm:px-7">
      <p className="mb-1 font-mono text-[10px] font-bold uppercase tracking-[.12em] text-[#6f817f]">Add hardware</p>
      <h2 className="font-display text-xl font-bold text-[#f1f3ed]">Register a device</h2>
    </div>
    {error && <div className="mx-5 mb-5 border-l-4 border-[#dc755d] bg-[#321f1d] px-4 py-3 text-sm text-[#f1b0a0] sm:mx-7" role="alert">{error}</div>}
    {notice && <div className="mx-5 mb-5 border-l-4 border-[#41c7a1] bg-[#172825] px-4 py-3 text-sm text-[#91ddc4]" role="status">{notice}</div>}
    <div className="grid gap-3.5 px-5 sm:grid-cols-2 sm:px-7">
      {([['Device name', deviceName, setDeviceName, 'e.g. North greenhouse'], ['Device type', deviceType, setDeviceType, 'e.g. weather-station']] as const).map(([label, value, setter, placeholder]) => <label className="grid gap-2 font-mono text-[10px] font-bold uppercase tracking-[.08em] text-[#82918e]" key={label}>
        <span>{label}</span>
        <input className="h-11 w-full border border-[#394345] bg-[#111719] px-3 text-sm font-normal normal-case tracking-normal text-[#e7ebe7] outline-none placeholder:text-[#566563] focus:border-[#41c7a1] focus:ring-4 focus:ring-[#41c7a1]/10" value={value} onChange={(event) => setter(event.target.value)} placeholder={placeholder} />
      </label>)}
    </div>
    <div className="mx-5 my-7 flex items-start justify-between gap-3 sm:mx-7">
      <div><span className="font-mono text-[11px] font-bold uppercase tracking-[.08em] text-[#e7ebe7]">Sensors</span><small className="mt-1.5 block text-xs text-[#82918e]">Define the channels this device exposes.</small></div>
      <button className="inline-flex items-center gap-1.5 border-0 bg-transparent p-0 font-mono text-[11px] font-bold text-[#e6b85a] transition hover:text-[#f1f3ed]" type="button" onClick={() => setSensors((current) => [...current, { channelName: '', unit: '' }])}><CirclePlus className="size-4" />Add sensor</button>
    </div>
    <div className="grid gap-2 px-5 sm:px-7">
      {/* Render one pair of editable fields for every sensor currently in the draft. */}
      {sensors.map((sensor, index) => <div className="grid grid-cols-[25px_1fr_75px_25px] items-center gap-1.5" key={index}>
        <span className="font-mono text-[10px] text-[#6f817f]">{String(index + 1).padStart(2, '0')}</span>
        <input className="h-10 min-w-0 border border-[#394345] bg-[#111719] px-2.5 text-xs text-[#e7ebe7] outline-none placeholder:text-[#566563] focus:border-[#41c7a1] focus:ring-4 focus:ring-[#41c7a1]/10" aria-label={`Sensor ${index + 1} channel`} value={sensor.channelName} onChange={(event) => updateSensor(index, 'channelName', event.target.value)} placeholder="channel name" />
        <input className="h-10 min-w-0 border border-[#394345] bg-[#111719] px-2.5 text-xs text-[#e7ebe7] outline-none placeholder:text-[#566563] focus:border-[#41c7a1] focus:ring-4 focus:ring-[#41c7a1]/10" aria-label={`Sensor ${index + 1} unit`} value={sensor.unit} onChange={(event) => updateSensor(index, 'unit', event.target.value)} placeholder="unit" />
        <Tooltip.Root><Tooltip.Trigger asChild><button className="grid size-6 place-items-center border-0 bg-transparent text-[#82918e] transition hover:text-[#dc755d] disabled:cursor-not-allowed disabled:opacity-30" type="button" onClick={() => setSensors((current) => current.filter((_, sensorIndex) => sensorIndex !== index))} disabled={sensors.length === 1} aria-label={`Remove sensor ${index + 1}`}><X className="size-4" /></button></Tooltip.Trigger><Tooltip.Portal><Tooltip.Content className="rounded bg-[#e7ebe7] px-2 py-1 font-mono text-[10px] text-[#101416]" sideOffset={5}>Remove sensor<Tooltip.Arrow className="fill-[#e7ebe7]" /></Tooltip.Content></Tooltip.Portal></Tooltip.Root>
      </div>)}
    </div>
    <button className="mx-5 mt-7 flex h-12 w-[calc(100%-2.5rem)] items-center justify-center border-0 bg-[#dc755d] font-mono text-xs font-bold text-[#101416] transition hover:-translate-y-0.5 hover:bg-[#e98972] disabled:cursor-wait disabled:opacity-60 sm:mx-7 sm:w-[calc(100%-3.5rem)]" type="submit" disabled={submitting}>{submitting ? 'Saving device...' : 'Register device →'}</button>
    <p className="mx-5 mt-3 text-[11px] text-[#82918e] sm:mx-7">Saved to the active project in PostgreSQL. Authentication will be added in a later phase.</p>
  </form>
}
