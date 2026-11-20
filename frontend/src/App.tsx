import { useCallback, useEffect, useRef, useState } from 'react' // I used 4 hooks here (react hooks are functions that let use React features in functional components)
import * as Tooltip from '@radix-ui/react-tooltip'
import { api, session, SESSION_EXPIRED_EVENT } from './api/client'
import AuthScreen from './views/AuthScreen'
import ProjectsIndex from './views/ProjectsIndex'
import ProjectWorkspace from './views/ProjectWorkspace'
import type { CurrentUser, Device, Project, TelemetryRecord } from './types/api'

/*
This is where the app starts.
It owns the data shared by multiple screens and decides which main screen is visible. (state management)
Without an access token it shows the sign-in screen; with one it shows the workspace.
The two main workspace screens are the project index and project workspace for a single project. Each screen handles its own UI, but App manages the shared state and server communication.

*/

type View = 'projects' | 'project'


export default function App() {
//const [statevalue, setStateValue] = useState(initialValue) is a hook that lets you add state to functional components.
  const [accessToken, setAccessToken] = useState<string | null>(() => session.getToken())
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null)
  const [authError, setAuthError] = useState('')
  const [projects, setProjects] = useState<Project[]>([])
  const [devices, setDevices] = useState<Device[]>([])
  const [selectedProjectId, setSelectedProjectId] = useState('')
  const [view, setView] = useState<View>('projects')
  const [projectsLoading, setProjectsLoading] = useState(true)
  const [devicesLoading, setDevicesLoading] = useState(false)
  const [projectsError, setProjectsError] = useState('')
  const [devicesError, setDevicesError] = useState('')
  const [devicesNotice, setDevicesNotice] = useState('')
  const [telemetryDevice, setTelemetryDevice] = useState<Device | null>(null)
  const [telemetry, setTelemetry] = useState<TelemetryRecord[]>([])
  const [telemetryLoading, setTelemetryLoading] = useState(false)
  const [telemetryError, setTelemetryError] = useState('')
  const loadedDevicesProjectId = useRef('')
  const selectedProject = projects.find((project) => project.id === selectedProjectId)

  // Forget everything that belongs to the previous session.
  const resetWorkspace = useCallback(() => {
    setCurrentUser(null)
    setProjects([])
    setDevices([])
    setSelectedProjectId('')
    setView('projects')
    setProjectsError('')
    setProjectsLoading(true)
    setTelemetryDevice(null)
    loadedDevicesProjectId.current = ''
  }, [])

  // The API client fires this when the backend rejects our token.
  useEffect(() => {
    function handleSessionExpired() {
      setAccessToken(null)
      resetWorkspace()
      setAuthError('Your session has expired. Please sign in again.')
    }
    window.addEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired)
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired)
  }, [resetWorkspace])

  // Load who is signed in and the projects they can see whenever a session starts.
  useEffect(() => {
    if (!accessToken) return
    let active = true
    async function loadSession() {
      try {
        const [user, nextProjects] = await Promise.all([api.me(), api.listProjects()])
        if (!active) return
        nextProjects.sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
        setCurrentUser(user)
        setProjects(nextProjects)
        setSelectedProjectId((current) => current || nextProjects[0]?.id || '')
      } catch (cause) {
        if (active) setProjectsError(cause instanceof Error ? cause.message : 'Unable to load projects.')
      } finally {
        if (active) setProjectsLoading(false)
      }
    }
    void loadSession()
    return () => { active = false }
  }, [accessToken])


  const reloadDevices = useCallback(async () => {
    if (!selectedProjectId) {
      setDevices([])
      return
    }
    const initialLoad = loadedDevicesProjectId.current !== selectedProjectId
    if (initialLoad) setDevicesLoading(true)
    setDevicesError('')
    try {
      setDevices(await api.listProjectDevices(selectedProjectId))
    } catch (cause) {
      setDevicesError(cause instanceof Error ? cause.message : 'Unable to load devices.')
    } finally {
      if (initialLoad) {
        loadedDevicesProjectId.current = selectedProjectId
        setDevicesLoading(false)
      }
    }
  }, [selectedProjectId])


  useEffect(() => { if (view === 'project' && selectedProjectId) void reloadDevices()}, [reloadDevices, selectedProjectId, view] )

  function signIn(token: string) {
    session.setToken(token)
    setAuthError('')
    setAccessToken(token)
  }

  function signOut() {
    session.clear()
    setAccessToken(null)
    resetWorkspace()
  }

  // Admin only (the backend answers 403 otherwise). The creator becomes the project's OWNER.
  async function createProject(name: string) {
    const project = await api.createProject(name)
    setProjects((current) => [project, ...current])
    setSelectedProjectId((current) => current || project.id)
  }

  function openProject(projectId: string) {
    setSelectedProjectId(projectId)
    setView('project')
    setDevicesError('')
    setDevicesNotice('')
  }

  async function showTelemetry(device: Device) {
    setTelemetryDevice(device)
    setTelemetry([])
    setTelemetryError('')
    setTelemetryLoading(true)
    try {
      setTelemetry(await api.listTelemetry(device.id))
    } catch (cause) {
      setTelemetryError(cause instanceof Error ? cause.message : 'Unable to load telemetry.')
    } finally {
      setTelemetryLoading(false)
    }
  }

  async function deleteDevice(deviceId: string) {
    const device = devices.find((item) => item.id === deviceId)
    if (!device || !window.confirm(`Delete ${device.name}? This permanently removes its sensors and telemetry.`)) return
    setDevicesError('')
    setDevicesNotice('')
    try {
      await api.deleteDevice(selectedProjectId, deviceId)
      await reloadDevices()
      setDevicesNotice(`${device.name} and all of its telemetry were deleted.`)
    } catch (cause) {
      setDevicesError(cause instanceof Error ? cause.message : 'Unable to delete device.')
    }
  }

  // No token: show the sign-in screen. The key resets the form after a sign-out or expiry.
  if (!accessToken) {
    return <AuthScreen key={authError} onAuthenticated={signIn} initialError={authError} />
  }

  // App switches between the project index and workspace only for now; each view handles its own screen UI.
  //  !! pls change ternary logic to switch statement if more views are added in the future !!
  return <Tooltip.Provider delayDuration={300}><div className="min-h-screen bg-[#101416] text-[#e7ebe7]">
    <header className="border-b border-[#2a3335] bg-[#141a1c]">
      <div className="mx-auto flex min-h-[78px] max-w-[1280px] items-center justify-between px-6 sm:px-10">
        <button className="group flex items-center gap-3 text-left" onClick={() => setView('projects')} type="button">
          <span className="font-display text-lg font-bold text-[#f1f3ed] transition group-hover:text-[#e6b85a]">OpenRoots</span>
        </button>
        <div className="flex items-center gap-3 font-mono text-[10px] font-semibold uppercase tracking-[.1em] text-[#7f908e]">
          <span className="size-2 rounded-full bg-[#41c7a1] shadow-[0_0_0_4px_rgba(65,199,161,.12)]" />
          {currentUser?.platformRole === 'ADMIN' && <span className="bg-[#2c2617] px-1.5 py-0.5 text-[9px] text-[#e6b85a]">Admin</span>}
          <button className="border border-[#394345] px-2 py-1 text-[#b5c1be] transition hover:border-[#e6b85a] hover:text-[#e6b85a]" onClick={signOut} type="button">Sign out</button>
        </div>
      </div>
    </header>
    <main className="mx-auto max-w-[1280px] px-6 pb-16 pt-10 sm:px-10 sm:pt-14">
      {view === 'projects'
        ? <ProjectsIndex
          projects={projects}
          loading={projectsLoading}
          error={projectsError}
          canCreateProjects={currentUser?.platformRole === 'ADMIN'}
          onOpenProject={openProject}
          onCreateProject={createProject}
        />
        : <ProjectWorkspace
          projects={projects}
          project={selectedProject}
          projectId={selectedProjectId}
          devices={devices}
          devicesLoading={devicesLoading}
          devicesError={devicesError}
          devicesNotice={devicesNotice}
          telemetryDevice={telemetryDevice}
          telemetry={telemetry}
          telemetryLoading={telemetryLoading}
          telemetryError={telemetryError}
          onBack={() => setView('projects')}
          onProjectChange={openProject}
          onReloadDevices={reloadDevices}
          onShowTelemetry={showTelemetry}
          onDeleteDevice={deleteDevice}
          onCloseTelemetry={() => setTelemetryDevice(null)}
        />}
    </main>
    <footer className="border-t border-[#2a3335] bg-[#141a1c]"><div className="mx-auto flex max-w-[1280px] flex-col gap-1 px-6 py-5 font-mono text-[10px] text-[#667674] sm:flex-row sm:items-center sm:justify-between sm:px-10"><span>OpenRoots local control surface</span><span>API /api/v1 · Signed in</span></div></footer>
  </div></Tooltip.Provider>
}
