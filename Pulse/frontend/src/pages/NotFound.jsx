import React from 'react'
import { Link } from 'react-router-dom'
import { Logo } from '../components/Sidebar'

export default function NotFound() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center px-6 text-center">
      <Logo size={44} />
      <h1 className="mt-6 text-4xl font-semibold text-ink">We can’t find that page</h1>
      <p className="mt-2 text-sm text-ink-soft">The link may be old, or the job may have been deleted.</p>
      <Link to="/" className="btn-primary mt-6">Back to overview</Link>
    </div>
  )
}
