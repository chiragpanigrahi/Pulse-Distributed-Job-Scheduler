import React, { useMemo, useState } from 'react'
import Icon from './Icon'
import { tryPrettyJson } from '../lib/format'

const TRUNCATED = '...(truncated)'

function copyText(text) {
  try { return navigator.clipboard.writeText(text) } catch { return Promise.resolve() }
}

function Primitive({ value }) {
  if (value === null) return <span className="text-ink-mute">null</span>
  if (typeof value === 'string') return <span className="break-all text-pearl-aqua">"{value}"</span>
  if (typeof value === 'number') return <span className="text-pearl-lilac">{String(value)}</span>
  return <span className="text-volt-bright">{String(value)}</span>
}

function Node({ name, value, depth, openDepth, last }) {
  const isObj = value !== null && typeof value === 'object'
  const [open, setOpen] = useState(depth < openDepth)
  const comma = last ? '' : ','
  const key = name !== undefined ? <span className="text-volt-ice">"{name}": </span> : null

  if (!isObj) {
    return <div className="pl-4">{key}<Primitive value={value} />{comma}</div>
  }

  const isArr = Array.isArray(value)
  const entries = isArr ? value.map((v, i) => [i, v]) : Object.entries(value)
  const [o, c] = isArr ? ['[', ']'] : ['{', '}']

  if (entries.length === 0) return <div className="pl-4">{key}<span className="text-ink-soft">{o}{c}</span>{comma}</div>

  return (
    <div className={depth === 0 ? '' : 'pl-4'}>
      <button onClick={() => setOpen(!open)} className="-ml-4 inline-flex items-center text-left hover:text-ink" aria-expanded={open}>
        <Icon name="chevron" size={12} className={`mr-1 text-ink-mute transition-transform ${open ? 'rotate-90' : ''}`} />
        {key}<span className="text-ink-soft">{o}</span>
        {!open && <span className="mx-1 text-ink-mute">{entries.length} {isArr ? 'items' : 'keys'}</span>}
        {!open && <span className="text-ink-soft">{c}{comma}</span>}
      </button>
      {open && (
        <>
          {entries.map(([k, v], i) => (
            <Node key={k} name={isArr ? undefined : k} value={v} depth={depth + 1} openDepth={openDepth} last={i === entries.length - 1} />
          ))}
          <div className="text-ink-soft">{c}{comma}</div>
        </>
      )}
    </div>
  )
}

/** Collapsible, colour-coded JSON with a raw view. Non-JSON text falls back to plain text. */
export default function JsonViewer({ text, emptyLabel = 'Nothing was sent.' }) {
  const parsed = useMemo(() => tryPrettyJson(text), [text])
  const [mode, setMode] = useState('tree')
  const [openDepth, setOpenDepth] = useState(2)
  const [tick, setTick] = useState(0)
  const [copied, setCopied] = useState(false)

  if (text == null || text === '') {
    return <div className="rounded-xl border border-edge/70 bg-night/60 px-4 py-6 text-center text-sm text-ink-mute">{emptyLabel}</div>
  }

  const truncated = typeof text === 'string' && text.endsWith(TRUNCATED)
  const canTree = parsed.ok
  const view = canTree ? mode : 'raw'

  const doCopy = async () => {
    await copyText(parsed.text)
    setCopied(true)
    setTimeout(() => setCopied(false), 1400)
  }
  const setAll = (depth) => { setOpenDepth(depth); setTick((t) => t + 1); setMode('tree') }

  return (
    <div className="overflow-hidden rounded-xl border border-edge/70 bg-night/70">
      <div className="flex flex-wrap items-center gap-1 border-b border-edge/60 px-2 py-1.5 text-xs">
        {canTree && (
          <div className="mr-1 flex rounded-lg bg-raised/60 p-0.5">
            {['tree', 'raw'].map((m) => (
              <button key={m} onClick={() => setMode(m)} className={`rounded-md px-2.5 py-1 font-medium capitalize ${view === m ? 'bg-volt/25 text-ink' : 'text-ink-soft hover:text-ink'}`}>{m}</button>
            ))}
          </div>
        )}
        {!canTree && <span className="px-2 text-ink-mute">Plain text</span>}
        <span className="flex-1" />
        {view === 'tree' && (
          <>
            <button onClick={() => setAll(99)} className="btn-quiet">Expand all</button>
            <button onClick={() => setAll(0)} className="btn-quiet">Collapse all</button>
          </>
        )}
        <button onClick={doCopy} className="btn-quiet"><Icon name={copied ? 'check' : 'copy'} size={13} />{copied ? 'Copied' : 'Copy'}</button>
      </div>
      <div className="max-h-[360px] overflow-auto px-4 py-3 font-mono text-[12.5px] leading-6 text-ink">
        {view === 'tree'
          ? <div className="pl-4"><Node key={tick} value={parsed.value} depth={0} openDepth={openDepth} last /></div>
          : <pre className="whitespace-pre-wrap break-all">{parsed.text}</pre>}
      </div>
      {truncated && (
        <div className="border-t border-edge/60 px-4 py-2 text-xs text-hold">
          This response was longer than 16,000 characters, so Pulse stored only the start of it.
        </div>
      )}
    </div>
  )
}
