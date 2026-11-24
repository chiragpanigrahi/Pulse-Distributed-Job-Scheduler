import React, { useState } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { Logo } from '../components/Sidebar'

export default function Login() {
  const { user, login, loading, error } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')

  if (user) return <Navigate to="/" replace />

  return (
    <div className="grid min-h-screen lg:grid-cols-[1.1fr_1fr]">
      <section className="hidden flex-col justify-between border-r border-edge/60 px-14 py-12 lg:flex">
        <div className="flex items-center gap-3">
          <Logo size={34} />
          <span className="font-display text-2xl font-semibold">Pulse</span>
        </div>

        <div>
          <h1 className="font-display text-6xl font-semibold leading-[1.02] tracking-tight text-pearl-gradient">Webhooks, in order.</h1>
          <p className="mt-5 max-w-md text-lg text-ink-soft">
            Chain API calls, pause for approvals, wait for callbacks, and read every request and response as JSON.
          </p>
          <PipelineArt />
        </div>

        <p className="text-sm text-ink-mute">Runs on Kafka, Redis and Postgres.</p>
      </section>

      <section className="flex items-center justify-center px-5 py-12">
        <form
          onSubmit={(e) => { e.preventDefault(); login(username, password) }}
          className="pearl w-full max-w-sm rounded-2xl p-7 shadow-lift"
        >
          <div className="mb-6 flex items-center gap-3 lg:hidden">
            <Logo size={26} />
            <span className="font-display text-xl font-semibold">Pulse</span>
          </div>
          <h2 className="text-2xl font-semibold text-ink">Sign in</h2>
          <p className="mb-6 mt-1 text-sm text-ink-soft">Use the admin account to manage jobs.</p>

          <label className="mb-4 block">
            <span className="label">Username</span>
            <input autoFocus autoComplete="username" required value={username} onChange={(e) => setUsername(e.target.value)} className="input" />
          </label>
          <label className="mb-5 block">
            <span className="label">Password</span>
            <input type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} className="input" />
          </label>

          {error && <p role="alert" className="mb-4 rounded-xl border border-bad/30 bg-bad/10 px-3 py-2 text-sm text-bad">{error}</p>}

          <button type="submit" disabled={loading} className="btn-primary w-full !py-2.5">{loading ? 'Signing in…' : 'Sign in'}</button>
          <p className="mt-4 text-center text-xs text-ink-mute">New install? The default login is admin / admin123. Change it before going live.</p>
        </form>
      </section>
    </div>
  )
}

/** Decorative: a four-stage pipeline with an approval gate on the third stage. */
function PipelineArt() {
  const steps = [
    { label: 'Charge card', sub: 'POST /payments', tone: '#3DDBD0' },
    { label: 'Reserve stock', sub: 'POST /inventory', tone: '#5B8DFF' },
    { label: 'Manager approval', sub: 'Waits for a person', tone: '#A592FF' },
    { label: 'Notify customer', sub: 'POST /email', tone: '#69CBFF' },
  ]
  return (
    <ol className="mt-10 max-w-sm" aria-label="Example pipeline: charge card, reserve stock, manager approval, notify customer">
      {steps.map((s, i) => (
        <li key={s.label} className="relative flex gap-4 pb-6 last:pb-0">
          {i < steps.length - 1 && (
            <span aria-hidden className="absolute left-[7px] top-5 h-full w-px bg-gradient-to-b from-volt/70 to-volt/10" />
          )}
          <span
            aria-hidden
            className="relative z-10 mt-1 h-[15px] w-[15px] shrink-0 rounded-full border-2 bg-night"
            style={{ borderColor: s.tone, boxShadow: `0 0 12px ${s.tone}55` }}
          />
          <div>
            <p className="text-sm font-semibold text-ink">{s.label}</p>
            <p className={`text-xs ${i === 2 ? 'text-wait' : 'font-mono text-ink-mute'}`}>{s.sub}</p>
          </div>
        </li>
      ))}
    </ol>
  )
}
