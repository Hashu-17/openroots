import { useState, type FormEvent } from 'react'
import { api } from '../api/client'

type AuthScreenProps = {
  // Called with the access token after a successful sign-in.
  onAuthenticated: (token: string) => void
  // Shown above the form, e.g. after a session expired.
  initialError?: string
}

// Sign-in and registration share one screen. Registering does not sign the user in:
// it switches back to the sign-in form so they authenticate with their new password.
export default function AuthScreen({ onAuthenticated, initialError = '' }: AuthScreenProps) {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(initialError)
  const [notice, setNotice] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setNotice('')
    setSubmitting(true)
    try {
      if (mode === 'register') {
        await api.register(email, password)
        setMode('login')
        setPassword('')
        setNotice('Account created. Sign in to open your workspace.')
        return
      }
      const result = await api.login(email, password)
      onAuthenticated(result.accessToken)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to authenticate.')
    } finally {
      setSubmitting(false)
    }
  }

  function switchMode() {
    setMode(mode === 'login' ? 'register' : 'login')
    setError('')
    setNotice('')
  }

  return (
    <div className="min-h-screen bg-[#101416] px-6 py-10 text-[#e7ebe7] sm:px-10">
      <div className="mx-auto flex min-h-[calc(100vh-5rem)] max-w-[1280px] items-center justify-center">
        <section className="w-full max-w-md border border-[#2b3537] bg-[#171e20] p-7 shadow-2xl sm:p-10">
          <div className="mb-8 flex items-center gap-3">
            <span className="grid size-9 place-items-center bg-[#e6b85a] font-mono text-xs font-extrabold text-[#101416]">OR</span>
            <div><p className="font-mono text-[10px] font-bold uppercase tracking-[.14em] text-[#7f908e]">OpenRoots / Control</p><h1 className="font-display text-2xl font-bold text-[#f1f3ed]">Operations</h1></div>
          </div>
          <p className="mb-2 font-mono text-[10px] font-bold uppercase tracking-[.16em] text-[#6f817f]">{mode === 'login' ? 'Secure sign in' : 'Create account'}</p>
          <h2 className="font-display text-4xl font-bold text-[#f1f3ed]">{mode === 'login' ? 'Welcome back' : 'Join the workspace'}</h2>
          <p className="mt-3 text-sm text-[#82918e]">{mode === 'login' ? 'Authenticate to access your projects and devices.' : 'Create an account. An administrator adds you to projects.'}</p>
          {error && <div className="mt-5 border-l-4 border-[#dc755d] bg-[#321f1d] px-4 py-3 text-sm text-[#f1b0a0]" role="alert">{error}</div>}
          {notice && <div className="mt-5 border-l-4 border-[#41c7a1] bg-[#172825] px-4 py-3 text-sm text-[#a7dfcd]" role="status">{notice}</div>}
          <form className="mt-7 space-y-4" onSubmit={submit}>
            <label className="block"><span className="mb-2 block font-mono text-[10px] font-bold uppercase tracking-[.1em] text-[#82918e]">Email</span><input className="h-11 w-full border border-[#394345] bg-[#101416] px-3 text-sm outline-none focus:border-[#41c7a1] focus:ring-4 focus:ring-[#41c7a1]/10" autoComplete="email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} required /></label>
            <label className="block"><span className="mb-2 block font-mono text-[10px] font-bold uppercase tracking-[.1em] text-[#82918e]">Password</span><input className="h-11 w-full border border-[#394345] bg-[#101416] px-3 text-sm outline-none focus:border-[#41c7a1] focus:ring-4 focus:ring-[#41c7a1]/10" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} minLength={mode === 'register' ? 8 : undefined} type="password" value={password} onChange={(event) => setPassword(event.target.value)} required /></label>
            <button className="inline-flex h-11 w-full items-center justify-center bg-[#41c7a1] px-4 font-mono text-xs font-bold uppercase tracking-[.1em] text-[#101416] transition hover:bg-[#65d5b4] disabled:cursor-not-allowed disabled:opacity-50" disabled={submitting} type="submit">{submitting ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}</button>
          </form>
          <button className="mt-5 w-full text-center font-mono text-[10px] font-bold uppercase tracking-[.1em] text-[#82918e] hover:text-[#e6b85a]" onClick={switchMode} type="button">{mode === 'login' ? 'Need an account? Register' : 'Already have an account? Sign in'}</button>
        </section>
      </div>
    </div>
  )
}
