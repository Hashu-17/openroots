import * as Tooltip from '@radix-ui/react-tooltip'
import { Clock3, FolderKanban, LoaderCircle, Plus } from 'lucide-react'
import type { Project } from '../types/api'

type ProjectsIndexProps = {
  projects: Project[]
  loading: boolean
  error: string
  onOpenProject: (projectId: string) => void
}

// Shortenin the timestampformat
function formatDate(value: string) {
  return new Date(value).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
}

// This screen lists projects and reports loading, empty, and error states.
export default function ProjectsIndex({ projects, loading, error, onOpenProject }: ProjectsIndexProps) {
  // Each project card calls the parent's navigation callback when selected.
  return <>
    <section className="mb-10 flex flex-col justify-between gap-7 sm:flex-row sm:items-end">
      <div><p className="mb-2 font-mono text-[10px] font-bold uppercase tracking-[.16em] text-[#6f817f]">Workspace index</p><h1 className="font-display text-4xl font-bold leading-none text-[#f1f3ed] sm:text-6xl">Projects</h1><p className="mt-4 max-w-xl text-sm text-[#8d9b99]">Choose an operational space to inspect its hardware, sensors, and team context.</p></div>
      <Tooltip.Root><Tooltip.Trigger asChild><button className="inline-flex h-11 cursor-not-allowed items-center justify-center gap-2 border border-[#394345] bg-[#1a2224] px-4 font-mono text-xs font-bold text-[#667674]" disabled type="button"><Plus className="size-4" />New project</button></Tooltip.Trigger><Tooltip.Portal><Tooltip.Content className="rounded bg-[#e7ebe7] px-2.5 py-1.5 font-mono text-[10px] text-[#101416]" sideOffset={6}>Project creation comes with the next backend slice.<Tooltip.Arrow className="fill-[#e7ebe7]" /></Tooltip.Content></Tooltip.Portal></Tooltip.Root>
    </section>
    {error && <div className="mb-5 border-l-4 border-[#dc755d] bg-[#321f1d] px-4 py-3 text-sm text-[#f1b0a0]" role="alert">{error}</div>}
    {/* Choose the content based on whether projects are loading, available, or absent. */}
    {loading ? <div className="flex min-h-64 items-center justify-center gap-3 text-sm text-[#82918e]"><LoaderCircle className="size-5 animate-spin text-[#41c7a1]" />Loading project index...</div>
      : projects.length === 0 ? <div className="border border-dashed border-[#394345] px-6 py-20 text-center text-sm text-[#82918e]">No projects are available in this workspace.</div>
        : <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">{projects.map((project, index) => <button className="group relative min-h-52 border border-[#2b3537] bg-[#171e20] p-6 text-left transition hover:-translate-y-0.5 hover:border-[#55706c] hover:bg-[#1c2527]" key={project.id} onClick={() => onOpenProject(project.id)} type="button">
          <span className="absolute right-5 top-5 font-mono text-[10px] text-[#4e5e5d]">{String(index + 1).padStart(2, '0')}</span><span className="mb-10 grid size-9 place-items-center border border-[#36514c] bg-[#172825] text-[#41c7a1]"><FolderKanban className="size-4" /></span>
          <strong className="block font-display text-2xl font-bold text-[#f1f3ed] group-hover:text-[#e6b85a]">{project.name}</strong><span className="mt-3 flex items-center gap-1.5 font-mono text-[10px] uppercase tracking-[.08em] text-[#7f908e]"><Clock3 className="size-3.5" />Created {formatDate(project.createdAt)}</span><span className="mt-5 block font-mono text-[10px] font-bold uppercase tracking-[.1em] text-[#41c7a1] opacity-0 transition group-hover:opacity-100">Open project →</span>
        </button>)}</div>}
  </>
}
