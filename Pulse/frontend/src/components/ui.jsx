import React, { useEffect } from 'react'
import Icon from './Icon'

export function PageHeader({ title, subtitle, actions, back }) {
  return (
    <header className="mb-7">
      {back}
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-3">{title}</div>
          {subtitle && <p className="mt-1.5 max-w-2xl text-sm text-ink-soft">{subtitle}</p>}
        </div>
        {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
      </div>
    </header>
  )
}

export function useEscape(onClose) {
  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])
}

export function Modal({ title, subtitle, onClose, children, footer, width = 'max-w-md', scroll = false }) {
  useEscape(onClose)
  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-night/75 px-4 py-10 backdrop-blur-sm animate-fadeIn" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose() }}>
      <div role="dialog" aria-modal="true" aria-label={title} className={`pearl w-full ${width} rounded-2xl p-6 shadow-lift`}>
        <div className="mb-5 flex items-start justify-between gap-4">
          <div>
            <h2 className="text-xl font-semibold text-ink">{title}</h2>
            {subtitle && <p className="mt-1 text-sm text-ink-soft">{subtitle}</p>}
          </div>
          <button onClick={onClose} className="btn-quiet !p-1.5" aria-label="Close"><Icon name="close" size={16} /></button>
        </div>
        <div className={scroll ? 'max-h-[65vh] overflow-y-auto pr-1' : ''}>{children}</div>
        {footer && <div className="mt-6 flex justify-end gap-2">{footer}</div>}
      </div>
    </div>
  )
}

export function EmptyState({ icon = 'jobs', title, body, action }) {
  return (
    <div className="flex flex-col items-center px-6 py-14 text-center">
      <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl border border-edge bg-raised/60 text-volt-bright">
        <Icon name={icon} size={22} />
      </div>
      <h3 className="text-base font-semibold text-ink">{title}</h3>
      {body && <p className="mt-1 max-w-sm text-sm text-ink-soft">{body}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}

export function Spinner({ label = 'Loading' }) {
  return (
    <div className="flex items-center justify-center gap-3 px-6 py-12 text-sm text-ink-soft" role="status">
      <span className="h-4 w-4 animate-spin rounded-full border-2 border-edge border-t-volt-bright" />
      {label}…
    </div>
  )
}

export function Field({ label, hint, children }) {
  return (
    <label className="block">
      <span className="label">{label}</span>
      {children}
      {hint && <span className="hint block">{hint}</span>}
    </label>
  )
}

export function HttpPill({ code }) {
  if (code == null) return <span className="text-ink-mute">—</span>
  const tone = code >= 500 ? 'text-bad' : code >= 400 ? 'text-hold' : code >= 200 && code < 300 ? 'text-ok' : 'text-ink-soft'
  return <span className={`font-mono text-xs ${tone}`}>{code}</span>
}
