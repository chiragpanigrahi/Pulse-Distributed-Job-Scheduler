import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { executionService } from '../services/executionService'
import StatusBadge from './StatusBadge'
import JsonViewer from './JsonViewer'
import Icon from './Icon'
import { Spinner, useEscape } from './ui'
import { formatDateTime, formatDuration, toCurl, tryPrettyJson } from '../lib/format'

const InspectorContext = createContext(null)

export function useInspector() {
  const ctx = useContext(InspectorContext)
  if (!ctx) throw new Error('useInspector must be used within an InspectorProvider')
  return ctx
}

export function InspectorProvider({ children }) {
  const [executionId, setExecutionId] = useState(null)
  const open = useCallback((id) => setExecutionId(id), [])
  const close = useCallback(() => setExecutionId(null), [])
  const value = useMemo(() => ({ open, close }), [open, close])

  return (
    <InspectorContext.Provider value={value}>
      {children}
      {executionId && <InspectorDrawer key="drawer" initialId={executionId} onClose={close} />}
    </InspectorContext.Provider>
  )
}

const LIVE = new Set(['RUNNING', 'RETRYING', 'PENDING_CALLBACK'])

function methodTone(method) {
  return method === 'GET' ? 'text-ok border-ok/30' : method === 'DELETE' ? 'text-bad border-bad/30' : 'text-volt-bright border-volt-bright/30'
}

function HeadersTable({ headers }) {
  const [show, setShow] = useState(true)
  const entries = Object.entries(headers || {})
  return (
    <section>
      <div className="mb-2 flex items-center justify-between">
        <h3 className="text-sm font-semibold text-ink">Headers <span className="font-normal text-ink-mute">({entries.length})</span></h3>
        {entries.length > 0 && <button onClick={() => setShow(!show)} className="btn-quiet">{show ? 'Hide' : 'Show'}</button>}
      </div>
      {entries.length === 0 ? (
        <p className="text-sm text-ink-mute">No headers were captured.</p>
      ) : show && (
        <div className="overflow-hidden rounded-xl border border-edge/70 bg-night/70">
          <table className="w-full text-left font-mono text-xs">
            <tbody>
              {entries.map(([k, v]) => (
                <tr key={k} className="border-b border-edge/40 last:border-0 odd:bg-raised/20">
                  <td className="w-2/5 px-3 py-2 align-top text-volt-ice">{k}</td>
                  <td className="break-all px-3 py-2 text-ink">{v}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function InspectorDrawer({ initialId, onClose }) {
  useEscape(onClose)
  const [activeId, setActiveId] = useState(initialId)
  const [exchange, setExchange] = useState(null)
  const [execution, setExecution] = useState(null)
  const [stages, setStages] = useState([])
  const [error, setError] = useState(null)
  const [tab, setTab] = useState('request')
  const [copied, setCopied] = useState(null)

  const load = useCallback(async (id) => {
    try {
      const [ex, run] = await Promise.all([executionService.exchange(id), executionService.get(id)])
      setExchange(ex)
      setExecution(run)
      setError(null)
      return ex
    } catch (e) {
      setError(e?.response?.status === 404 ? 'This run no longer exists.' : 'Could not load this run. Check your connection and try again.')
      return null
    }
  }, [])

  useEffect(() => {
    setExchange(null)
    setExecution(null)
    load(activeId)
  }, [activeId, load])

  // Pipeline runs: show one chip per stage (the deciding attempt) so you can jump between them.
  const runId = exchange?.pipelineRunId
  const status = exchange?.status
  useEffect(() => {
    if (!runId) { setStages([]); return }
    executionService.pipelineRun(runId).then((rows) => {
      const best = new Map()
      rows.forEach((r) => {
        const cur = best.get(r.stageIndex)
        if (!cur || (r.attemptNumber ?? 0) >= (cur.attemptNumber ?? 0)) best.set(r.stageIndex, r)
      })
      setStages([...best.values()].sort((a, b) => a.stageIndex - b.stageIndex))
    }).catch(() => {})
  }, [runId, status])

  // Live runs refresh themselves until they settle.
  useEffect(() => {
    if (!status || !LIVE.has(status)) return undefined
    const t = setInterval(() => load(activeId), 3000)
    return () => clearInterval(t)
  }, [status, activeId, load])

  const request = exchange?.request
  const response = exchange?.response

  const flash = (what) => { setCopied(what); setTimeout(() => setCopied(null), 1400) }
  const copy = async (what, text) => {
    try { await navigator.clipboard.writeText(text) } catch { /* clipboard unavailable */ }
    flash(what)
  }
  const fullJson = () => JSON.stringify({
    run: { id: exchange.executionId, job: exchange.jobName, stage: exchange.stageName, attempt: exchange.attemptNumber, status: exchange.status, startedAt: exchange.startedAt, durationMs: exchange.durationMs },
    request: request ? { ...request, body: tryPrettyJson(request.body).ok ? JSON.parse(request.body) : request.body } : null,
    response: response ? { ...response, body: tryPrettyJson(response.body).ok ? JSON.parse(response.body) : response.body } : null,
  }, null, 2)
  const download = () => {
    const url = URL.createObjectURL(new Blob([fullJson()], { type: 'application/json' }))
    const a = document.createElement('a')
    a.href = url
    a.download = `${exchange.jobName}${exchange.stageName ? `-${exchange.stageName}` : ''}-${exchange.executionId.slice(0, 8)}.json`
    a.click()
    URL.revokeObjectURL(url)
  }

  const title = exchange ? (
    <>{exchange.jobName}{exchange.stageName && <span className="text-ink-soft"> / {exchange.stageName}</span>}</>
  ) : 'Loading run'

  return (
    <div className="fixed inset-0 z-50 animate-fadeIn" role="dialog" aria-modal="true" aria-label="Run inspector">
      <div className="absolute inset-0 bg-night/70 backdrop-blur-sm" onClick={onClose} />
      <div className="pearl absolute inset-y-0 right-0 flex w-full max-w-[760px] flex-col rounded-l-2xl shadow-lift animate-drawerIn">
        <div className="px-6 pb-4 pt-5">
          <div className="flex items-start justify-between gap-4">
            <div className="min-w-0">
              <p className="flex items-center gap-1.5 text-sm text-ink-soft"><Icon name="braces" size={15} className="text-volt-bright" />Run inspector</p>
              <h2 className="mt-1 truncate text-2xl font-semibold text-ink">{title}</h2>
            </div>
            <button onClick={onClose} className="btn-quiet !p-2" aria-label="Close inspector"><Icon name="close" size={18} /></button>
          </div>

          {exchange && (
            <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-ink-soft">
              <StatusBadge status={exchange.status} />
              {exchange.attemptNumber > 0 && <span>Attempt {exchange.attemptNumber}</span>}
              <span>{formatDateTime(exchange.startedAt, true)}</span>
              {exchange.durationMs != null && <span>Took {formatDuration(exchange.durationMs)}</span>}
            </div>
          )}

          {stages.length > 1 && (
            <div className="mt-4 flex flex-wrap gap-2" role="tablist" aria-label="Pipeline stages">
              {stages.map((s) => (
                <button
                  key={s.id}
                  role="tab"
                  aria-selected={s.id === activeId}
                  onClick={() => setActiveId(s.id)}
                  className={`rounded-full border px-3 py-1 text-xs font-medium transition ${s.id === activeId ? 'border-volt-bright bg-volt/25 text-ink' : 'border-edge bg-raised/40 text-ink-soft hover:text-ink'}`}
                >
                  {s.stageIndex + 1}. {s.stageName}
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto border-t border-edge/60 bg-night/40 px-6 py-5">
          {error && <p role="alert" className="rounded-xl border border-bad/30 bg-bad/10 px-4 py-3 text-sm text-bad">{error}</p>}
          {!error && !exchange && <Spinner label="Loading the request and response" />}

          {exchange && (
            <div className="space-y-5">
              <div className="rounded-xl border border-edge/70 bg-night/70 px-4 py-3.5">
                {request ? (
                  <>
                    <div className="flex items-start gap-3">
                      <span className={`mt-0.5 rounded-md border px-2 py-0.5 font-mono text-[11px] font-semibold ${methodTone(request.method)}`}>{request.method}</span>
                      <span className="break-all font-mono text-sm text-ink">{request.url}</span>
                    </div>
                    <p className="mt-2 text-sm text-ink-soft">
                      {response?.statusCode != null
                        ? <>Replied with <span className={`font-mono ${response.statusCode >= 400 ? 'text-bad' : 'text-ok'}`}>{response.statusCode}</span>{exchange.durationMs != null && <> in {formatDuration(exchange.durationMs)}</>}</>
                        : exchange.status === 'AWAITING_APPROVAL' ? 'Waiting for approval before this call is made.'
                          : LIVE.has(exchange.status) ? 'Waiting for a reply…' : 'No reply was received.'}
                    </p>
                  </>
                ) : (
                  <p className="text-sm text-ink-soft">
                    {exchange.status === 'AWAITING_APPROVAL' ? 'This stage has not run yet. It is waiting for someone to approve it.'
                      : exchange.status === 'SKIPPED' ? 'This stage was skipped, so nothing was sent.'
                        : 'No request was recorded for this run. Runs from before Pulse V3 did not save requests.'}
                  </p>
                )}
              </div>

              <div className="flex gap-1 border-b border-edge/60" role="tablist">
                {[['request', 'Request sent'], ['response', 'Response received'], ['details', 'Details']].map(([k, label]) => (
                  <button
                    key={k}
                    role="tab"
                    aria-selected={tab === k}
                    onClick={() => setTab(k)}
                    className={`-mb-px border-b-2 px-3 py-2.5 text-sm font-medium transition ${tab === k ? 'border-volt-bright text-ink' : 'border-transparent text-ink-soft hover:text-ink'}`}
                  >
                    {label}
                  </button>
                ))}
              </div>

              {tab === 'request' && (
                <div className="space-y-5">
                  <HeadersTable headers={request?.headers} />
                  <section>
                    <h3 className="mb-2 text-sm font-semibold text-ink">Body</h3>
                    <JsonViewer text={request?.body} emptyLabel={request ? 'This request had no body.' : 'Nothing was sent.'} />
                  </section>
                </div>
              )}

              {tab === 'response' && (
                <div className="space-y-5">
                  <HeadersTable headers={response?.headers} />
                  <section>
                    <h3 className="mb-2 text-sm font-semibold text-ink">Body</h3>
                    <JsonViewer text={response?.body} emptyLabel={response ? 'The response had no body.' : 'No response was received.'} />
                  </section>
                </div>
              )}

              {tab === 'details' && (
                <div className="space-y-5">
                  {exchange.errorMessage && (
                    <p role="alert" className="rounded-xl border border-bad/30 bg-bad/10 px-4 py-3 text-sm text-bad">{exchange.errorMessage}</p>
                  )}
                  <dl className="grid grid-cols-2 gap-x-6 gap-y-4 text-sm">
                    <div><dt className="text-xs text-ink-mute">Worker</dt><dd className="mt-0.5 font-mono text-xs text-ink">{execution?.workerId || '—'}</dd></div>
                    <div><dt className="text-xs text-ink-mute">Run ID</dt><dd className="mt-0.5 break-all font-mono text-xs text-ink">{exchange.executionId}</dd></div>
                    <div><dt className="text-xs text-ink-mute">Started</dt><dd className="mt-0.5 text-ink">{formatDateTime(exchange.startedAt, true)}</dd></div>
                    <div><dt className="text-xs text-ink-mute">Finished</dt><dd className="mt-0.5 text-ink">{formatDateTime(execution?.finishedAt, true)}</dd></div>
                  </dl>
                  {exchange.params && Object.keys(exchange.params).length > 0 && (
                    <section>
                      <h3 className="mb-2 text-sm font-semibold text-ink">Parameters used</h3>
                      <JsonViewer text={JSON.stringify(exchange.params)} />
                    </section>
                  )}
                  <section>
                    <h3 className="mb-2 text-sm font-semibold text-ink">Timeline</h3>
                    {(execution?.logLines || []).length === 0 ? (
                      <p className="text-sm text-ink-mute">No log lines for this run.</p>
                    ) : (
                      <ol className="space-y-3 border-l border-edge pl-4">
                        {execution.logLines.map((l, i) => (
                          <li key={i} className="relative text-sm text-ink">
                            <span className="absolute -left-[21px] top-1.5 h-2 w-2 rounded-full bg-volt-bright" />
                            {l.message}
                            <span className="ml-2 text-xs text-ink-mute">{formatDateTime(l.timestamp, true)}</span>
                          </li>
                        ))}
                      </ol>
                    )}
                  </section>
                </div>
              )}
            </div>
          )}
        </div>

        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-edge/60 px-6 py-4">
          <p className="text-xs text-ink-mute">Secrets are masked before anything is saved.</p>
          <div className="flex flex-wrap gap-2">
            <button disabled={!request} onClick={() => copy('curl', toCurl(request))} className="btn-ghost">
              <Icon name={copied === 'curl' ? 'check' : 'copy'} size={15} />{copied === 'curl' ? 'Copied' : 'Copy as cURL'}
            </button>
            <button disabled={!exchange} onClick={() => copy('json', fullJson())} className="btn-ghost">
              <Icon name={copied === 'json' ? 'check' : 'braces'} size={15} />{copied === 'json' ? 'Copied' : 'Copy JSON'}
            </button>
            <button disabled={!exchange} onClick={download} className="btn-primary"><Icon name="download" size={15} />Download</button>
          </div>
        </div>
      </div>
    </div>
  )
}
