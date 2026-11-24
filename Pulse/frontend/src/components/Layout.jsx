import React, { useState } from 'react'
import Sidebar, { Logo } from './Sidebar'
import Icon from './Icon'

/** App shell: fixed sidebar on desktop, a slide-in menu on phones. */
export default function Layout({ children }) {
  const [open, setOpen] = useState(false)

  return (
    <div className="min-h-screen lg:flex">
      <aside className="fixed inset-y-0 left-0 hidden w-60 border-r border-edge/70 bg-panel/70 backdrop-blur lg:block">
        <Sidebar />
      </aside>

      <div className="flex items-center gap-3 border-b border-edge/70 bg-panel/80 px-4 py-3 backdrop-blur lg:hidden">
        <button onClick={() => setOpen(true)} className="btn-quiet !p-2" aria-label="Open menu"><Icon name="menu" /></button>
        <Logo size={24} />
        <span className="font-display text-lg font-semibold">Pulse</span>
      </div>

      {open && (
        <div className="fixed inset-0 z-40 lg:hidden" role="dialog" aria-modal="true">
          <div className="absolute inset-0 bg-night/70 backdrop-blur-sm animate-fadeIn" onClick={() => setOpen(false)} />
          <div className="absolute inset-y-0 left-0 w-64 border-r border-edge bg-panel animate-drawerIn">
            <Sidebar onNavigate={() => setOpen(false)} />
          </div>
        </div>
      )}

      <main className="min-w-0 flex-1 px-4 py-7 sm:px-8 lg:ml-60 lg:py-9">
        <div className="mx-auto max-w-6xl">{children}</div>
      </main>
    </div>
  )
}
