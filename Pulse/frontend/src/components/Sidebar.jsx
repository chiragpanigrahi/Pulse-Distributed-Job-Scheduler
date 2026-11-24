import React from 'react'
import { NavLink } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { useLiveStatus } from '../hooks/useLiveStatus'
import Icon from './Icon'

export function Logo({ size = 30 }) {
  return (
    <span
      aria-hidden
      className="inline-block shrink-0 rounded-full"
      style={{
        width: size,
        height: size,
        background: 'radial-gradient(circle at 30% 28%, #F4F8FF 0%, #8FB2FF 22%, #2E6BFF 55%, #14246B 100%)',
        boxShadow: '0 0 18px rgba(46,107,255,.55), inset 0 -4px 8px rgba(5,11,31,.45)',
      }}
    />
  )
}

const NAV = [
  { to: '/', label: 'Overview', icon: 'overview', end: true },
  { to: '/jobs', label: 'Jobs', icon: 'jobs' },
  { to: '/activity', label: 'Activity', icon: 'activity' },
  { to: '/credentials', label: 'Credentials', icon: 'key' },
]

export default function Sidebar({ onNavigate }) {
  const { user, logout } = useAuth()
  const live = useLiveStatus()

  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center gap-3 px-5 py-6">
        <Logo />
        <span className="font-display text-xl font-semibold tracking-tight">Pulse</span>
      </div>

      <nav className="flex-1 space-y-1 px-3" aria-label="Main">
        {NAV.map(({ to, label, icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            onClick={onNavigate}
            className={({ isActive }) =>
              `flex items-center gap-3 rounded-xl border px-3 py-2.5 text-sm font-medium transition ${
                isActive
                  ? 'border-volt-bright/50 bg-volt/15 text-ink shadow-glow'
                  : 'border-transparent text-ink-soft hover:bg-raised/60 hover:text-ink'
              }`
            }
          >
            <Icon name={icon} size={17} />
            {label}
          </NavLink>
        ))}
      </nav>

      <div className="mx-3 mb-3 flex items-center gap-2 rounded-xl border border-edge/70 px-3 py-2 text-xs text-ink-soft">
        {live ? <span className="live-dot" /> : <span className="inline-block h-2 w-2 rounded-full bg-ink-mute" />}
        {live ? 'Live updates on' : 'Reconnecting…'}
      </div>

      <div className="flex items-center gap-3 border-t border-edge/70 px-5 py-4">
        <div className="flex h-8 w-8 items-center justify-center rounded-full bg-gradient-to-b from-volt-bright to-volt-deep text-xs font-semibold text-white">
          {user?.username?.[0]?.toUpperCase() ?? '?'}
        </div>
        <div className="min-w-0 flex-1 text-xs">
          <div className="truncate font-medium text-ink">{user?.username}</div>
          <div className="text-ink-mute">{user?.role}</div>
        </div>
        <button onClick={logout} className="btn-quiet !p-1.5" aria-label="Sign out" title="Sign out">
          <Icon name="logout" size={16} />
        </button>
      </div>
    </div>
  )
}
