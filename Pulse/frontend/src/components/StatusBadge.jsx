import React from 'react'
import { STATUS_META, TONE_CLASSES } from '../lib/format'

export default function StatusBadge({ status }) {
  const meta = STATUS_META[status] || { label: String(status || 'Unknown'), tone: 'mute' }
  return (
    <span className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full border px-2.5 py-0.5 text-[11px] font-medium ${TONE_CLASSES[meta.tone]}`}>
      <span className={`h-1.5 w-1.5 rounded-full bg-current ${meta.live ? 'animate-pulse' : ''}`} />
      {meta.label}
    </span>
  )
}
