export function timeAgo(iso) {
  if (!iso) return '—'
  const seconds = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000))
  if (seconds < 10) return 'just now'
  if (seconds < 60) return `${seconds}s ago`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  return `${Math.floor(hours / 24)}d ago`
}

export function formatDateTime(iso, withSeconds = false) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString(undefined, {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
    ...(withSeconds ? { second: '2-digit' } : {}),
  })
}

export function formatDuration(ms) {
  if (ms == null) return '—'
  if (ms < 1000) return `${ms} ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(ms < 10000 ? 2 : 1)} s`
  const m = Math.floor(ms / 60000)
  return `${m}m ${Math.round((ms % 60000) / 1000)}s`
}

/** Plain-language labels. Tone picks the colour in StatusBadge. */
export const STATUS_META = {
  SCHEDULED: { label: 'Scheduled', tone: 'hold' },
  RUNNING: { label: 'Running', tone: 'live', live: true },
  RETRYING: { label: 'Retrying', tone: 'live', live: true },
  PAUSED: { label: 'Paused', tone: 'mute' },
  COMPLETED: { label: 'Completed', tone: 'ok' },
  SUCCESS: { label: 'Succeeded', tone: 'ok' },
  FAILED: { label: 'Failed', tone: 'bad' },
  TIMEOUT: { label: 'Timed out', tone: 'bad' },
  PENDING: { label: 'Pending', tone: 'mute' },
  SKIPPED: { label: 'Skipped', tone: 'mute' },
  CANCELLED: { label: 'Rejected', tone: 'mute' },
  AWAITING_APPROVAL: { label: 'Needs approval', tone: 'wait' },
  PENDING_CALLBACK: { label: 'Waiting for callback', tone: 'live', live: true },
}

export const TONE_CLASSES = {
  ok: 'bg-ok/10 text-ok border-ok/30',
  live: 'bg-live/10 text-volt-bright border-live/30',
  hold: 'bg-hold/10 text-hold border-hold/30',
  wait: 'bg-wait/10 text-wait border-wait/30',
  bad: 'bg-bad/10 text-bad border-bad/30',
  mute: 'bg-edge/40 text-ink-soft border-edge',
}

export const isFailure = (status) => status === 'FAILED' || status === 'TIMEOUT'

/** Pretty-prints JSON text; returns { ok, value, text } so callers can fall back to raw text. */
export function tryPrettyJson(text) {
  if (text == null || text === '') return { ok: false, value: null, text: text ?? '' }
  try {
    const value = JSON.parse(text)
    if (value !== null && typeof value === 'object') {
      return { ok: true, value, text: JSON.stringify(value, null, 2) }
    }
  } catch { /* not JSON */ }
  return { ok: false, value: null, text }
}

const shellQuote = (s) => `'${String(s).replace(/'/g, `'\\''`)}'`

/** Builds a curl command from a captured request. Masked secrets stay masked. */
export function toCurl(request) {
  if (!request) return ''
  const parts = [`curl -X ${request.method || 'GET'} ${shellQuote(request.url)}`]
  Object.entries(request.headers || {}).forEach(([k, v]) => parts.push(`-H ${shellQuote(`${k}: ${v}`)}`))
  if (request.body) parts.push(`--data ${shellQuote(request.body)}`)
  return parts.join(' \\\n  ')
}
