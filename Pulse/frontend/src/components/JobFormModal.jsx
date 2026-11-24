import React, { useEffect, useState } from 'react'
import { credentialService } from '../services/credentialService'
import { jobService } from '../services/jobService'

const EMPTY_STAGE = {
  name: '', webhookUrl: '', httpMethod: 'POST', headersText: '', payloadText: '',
  timeoutSeconds: 30, maxRetries: 3, credentialName: '', condition: 'ON_SUCCESS',
  requiresApproval: false, awaitCallback: false, callbackTimeoutSeconds: 300,
  expectedStatusCodes: '', responseBodyContains: '',
}

const EMPTY_FORM = {
  name: '', description: '', type: 'RECURRING', cronExpression: '0 */5 * * * *', runAt: '',
  executionMode: 'SINGLE',
  webhookUrl: '', httpMethod: 'POST', headersText: '', payloadText: '', credentialName: '',
  timeoutSeconds: 30, maxRetries: 3,
  stages: [], parameters: [], workerTag: 'default',
  downstreamJobIds: [], downstreamTriggerCondition: 'ON_SUCCESS',
}

const pretty = (o) => (o && Object.keys(o).length ? JSON.stringify(o, null, 2) : '')

function stageToForm(s) {
  return {
    ...EMPTY_STAGE, ...s,
    headersText: pretty(s.headers), payloadText: pretty(s.payload),
    credentialName: s.credentialName || '',
    callbackTimeoutSeconds: s.callbackTimeoutSeconds ?? 300,
    expectedStatusCodes: (s.expectedStatusCodes || []).join(', '),
    responseBodyContains: s.responseBodyContains || '',
  }
}

function jobToForm(job) {
  if (!job) return EMPTY_FORM
  return {
    ...EMPTY_FORM,
    name: job.name || '', description: job.description || '', type: job.type || 'RECURRING',
    cronExpression: job.cronExpression || '0 */5 * * * *',
    runAt: job.runAt ? job.runAt.slice(0, 16) : '',
    executionMode: job.executionMode || 'SINGLE',
    webhookUrl: job.webhookUrl || '', httpMethod: job.httpMethod || 'POST',
    headersText: pretty(job.headers), payloadText: pretty(job.payload),
    credentialName: job.credentialName || '',
    timeoutSeconds: job.timeoutSeconds ?? 30, maxRetries: job.maxRetries ?? 3,
    stages: (job.stages || []).map(stageToForm),
    parameters: (job.parameters || []).map((p) => ({ ...p, defaultValue: p.defaultValue || '', label: p.label || '' })),
    workerTag: job.workerTag || 'default',
    downstreamJobIds: job.downstreamJobIds || [],
    downstreamTriggerCondition: job.downstreamTriggerCondition || 'ON_SUCCESS',
  }
}

const parseJson = (text, what) => {
  if (!text.trim()) return undefined
  try { return JSON.parse(text) } catch { throw new Error(`${what} must be valid JSON`) }
}

function stageToBody(s, i) {
  const codes = s.expectedStatusCodes.split(',').map((c) => c.trim()).filter(Boolean).map(Number)
  if (codes.some(Number.isNaN)) throw new Error(`Stage ${i + 1}: expected status codes must be numbers`)
  return {
    name: s.name, webhookUrl: s.webhookUrl, httpMethod: s.httpMethod,
    headers: parseJson(s.headersText, `Stage ${i + 1} headers`),
    payload: parseJson(s.payloadText, `Stage ${i + 1} payload`),
    timeoutSeconds: Number(s.timeoutSeconds), maxRetries: Number(s.maxRetries),
    credentialName: s.credentialName || undefined,
    condition: s.condition, requiresApproval: s.requiresApproval, awaitCallback: s.awaitCallback,
    callbackTimeoutSeconds: s.awaitCallback ? Number(s.callbackTimeoutSeconds) : undefined,
    expectedStatusCodes: codes.length ? codes : undefined,
    responseBodyContains: s.responseBodyContains || undefined,
  }
}

export default function JobFormModal({ job, onClose, onSubmit }) {
  const [form, setForm] = useState(jobToForm(job))
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [credentials, setCredentials] = useState([])
  const [otherJobs, setOtherJobs] = useState([])

  useEffect(() => setForm(jobToForm(job)), [job])
  useEffect(() => {
    credentialService.list().then(setCredentials).catch(() => {})
    jobService.list({ size: 100 }).then((d) => setOtherJobs(d.content.filter((j) => j.id !== job?.id))).catch(() => {})
  }, [job])

  const update = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))
  const setStage = (i, patch) => setForm((f) => ({ ...f, stages: f.stages.map((s, j) => (j === i ? { ...s, ...patch } : s)) }))
  const moveStage = (i, d) => setForm((f) => {
    const a = [...f.stages]; const k = i + d
    if (k < 0 || k >= a.length) return f
    ;[a[i], a[k]] = [a[k], a[i]]
    return { ...f, stages: a }
  })
  const setParam = (i, patch) => setForm((f) => ({ ...f, parameters: f.parameters.map((p, j) => (j === i ? { ...p, ...patch } : p)) }))

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError(null)
    let body
    try {
      const pipeline = form.executionMode === 'PIPELINE'
      body = {
        name: form.name, description: form.description, type: form.type,
        cronExpression: form.type === 'RECURRING' ? form.cronExpression : undefined,
        runAt: form.type === 'ONE_TIME' && form.runAt ? new Date(form.runAt).toISOString() : undefined,
        executionMode: form.executionMode,
        timeoutSeconds: Number(form.timeoutSeconds), maxRetries: Number(form.maxRetries),
        workerTag: form.workerTag || 'default',
        parameters: form.parameters.filter((p) => p.name.trim()),
        downstreamJobIds: form.downstreamJobIds,
        downstreamTriggerCondition: form.downstreamTriggerCondition,
      }
      if (pipeline) {
        if (form.stages.length === 0) throw new Error('Add at least one stage')
        body.stages = form.stages.map(stageToBody)
      } else {
        Object.assign(body, {
          webhookUrl: form.webhookUrl, httpMethod: form.httpMethod,
          headers: parseJson(form.headersText, 'Headers'),
          payload: parseJson(form.payloadText, 'Payload'),
          credentialName: form.credentialName || undefined,
        })
      }
    } catch (err) {
      setError(err.message)
      return
    }
    setSubmitting(true)
    try {
      await onSubmit(body)
    } catch (err) {
      setError(err?.response?.data?.message || 'Failed to save job')
    } finally {
      setSubmitting(false)
    }
  }

  const pipeline = form.executionMode === 'PIPELINE'

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-night/75 px-4 py-8 backdrop-blur-sm animate-fadeIn">
      <div className="pearl w-full max-w-2xl rounded-2xl p-6 shadow-lift">
        <h2 className="text-xl font-semibold text-ink">{job ? 'Edit job' : 'New job'}</h2>
        <p className="mb-5 mt-1 text-sm text-ink-soft">Choose when it runs and what it calls.</p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <Field label="Name"><input required value={form.name} onChange={update('name')} className={inputCls} /></Field>
          <Field label="Description"><textarea value={form.description} onChange={update('description')} rows={2} className={inputCls} /></Field>

          <div className="grid grid-cols-2 gap-3">
            <Field label="Schedule">
              <select value={form.type} onChange={update('type')} className={inputCls}>
                <option value="RECURRING">Repeats (cron)</option>
                <option value="ONE_TIME">Runs once</option>
              </select>
            </Field>
            <Field label="What it does">
              <select value={form.executionMode} onChange={update('executionMode')} className={inputCls}>
                <option value="SINGLE">One webhook call</option>
                <option value="PIPELINE">Pipeline of calls</option>
              </select>
            </Field>
          </div>

          {form.type === 'RECURRING' ? (
            <Field label="Cron expression" hint="6-field Spring cron, e.g. 0 */5 * * * *">
              <input required value={form.cronExpression} onChange={update('cronExpression')} className={`${inputCls} font-mono`} />
            </Field>
          ) : (
            <Field label="Run at"><input required type="datetime-local" value={form.runAt} onChange={update('runAt')} className={inputCls} /></Field>
          )}

          {!pipeline && (
            <>
              <div className="grid grid-cols-3 gap-3">
                <Field label="Method">
                  <select value={form.httpMethod} onChange={update('httpMethod')} className={inputCls}>
                    {['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].map((m) => <option key={m}>{m}</option>)}
                  </select>
                </Field>
                <Field label="Webhook URL" className="col-span-2">
                  <input required placeholder="https://…" value={form.webhookUrl} onChange={update('webhookUrl')} className={inputCls} />
                </Field>
              </div>
              <CredentialSelect value={form.credentialName} onChange={update('credentialName')} credentials={credentials} />
              <Field label="Headers (JSON)" hint="Templates allowed: {{params.x}}, {{secrets.name}}">
                <textarea value={form.headersText} onChange={update('headersText')} rows={2} className={`${inputCls} font-mono text-xs`} />
              </Field>
              <Field label="Payload (JSON)">
                <textarea value={form.payloadText} onChange={update('payloadText')} rows={3} className={`${inputCls} font-mono text-xs`} />
              </Field>
            </>
          )}

          {pipeline && (
            <section className="space-y-3">
              <div className="flex items-center justify-between">
                <h3 className="text-base font-semibold text-ink">Stages</h3>
                <button type="button" onClick={() => setForm((f) => ({ ...f, stages: [...f.stages, { ...EMPTY_STAGE, name: `stage-${f.stages.length + 1}` }] }))}
                  className="text-xs text-volt-bright hover:text-volt-ice">+ Add stage</button>
              </div>
              <p className="text-[11px] text-ink-mute">
                Chain values with {'{{stages.<name>.response.body.path}}'}. For callback stages, hand the other system {'{{callback.url}}'} in the payload or headers.
              </p>
              {form.stages.map((s, i) => (
                <div key={i} className="space-y-3 rounded-lg border border-edge/70 bg-night/50 p-4">
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-xs text-ink-mute">#{i + 1}</span>
                    <input required placeholder="stage name" value={s.name} onChange={(e) => setStage(i, { name: e.target.value })} className={`${inputCls} font-mono`} />
                    <button type="button" onClick={() => moveStage(i, -1)} className="px-1 text-ink-soft hover:text-ink">↑</button>
                    <button type="button" onClick={() => moveStage(i, 1)} className="px-1 text-ink-soft hover:text-ink">↓</button>
                    <button type="button" onClick={() => setForm((f) => ({ ...f, stages: f.stages.filter((_, j) => j !== i) }))} className="px-1 text-ink-soft hover:text-bad">✕</button>
                  </div>
                  <div className="grid grid-cols-4 gap-2">
                    <select value={s.httpMethod} onChange={(e) => setStage(i, { httpMethod: e.target.value })} className={inputCls}>
                      {['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].map((m) => <option key={m}>{m}</option>)}
                    </select>
                    <input required placeholder="https://…" value={s.webhookUrl} onChange={(e) => setStage(i, { webhookUrl: e.target.value })} className={`${inputCls} col-span-3`} />
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    <Field label="Run when">
                      <select value={s.condition} onChange={(e) => setStage(i, { condition: e.target.value })} className={inputCls}>
                        <option value="ON_SUCCESS">Previous succeeded</option>
                        <option value="ON_FAILURE">Previous failed</option>
                        <option value="ALWAYS">Always</option>
                      </select>
                    </Field>
                    <CredentialSelect value={s.credentialName} onChange={(e) => setStage(i, { credentialName: e.target.value })} credentials={credentials} />
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    <textarea placeholder="Headers (JSON)" rows={2} value={s.headersText} onChange={(e) => setStage(i, { headersText: e.target.value })} className={`${inputCls} font-mono text-xs`} />
                    <textarea placeholder="Payload (JSON)" rows={2} value={s.payloadText} onChange={(e) => setStage(i, { payloadText: e.target.value })} className={`${inputCls} font-mono text-xs`} />
                  </div>
                  <div className="grid grid-cols-4 gap-2">
                    <Field label="Timeout (s)"><input type="number" min={1} max={300} value={s.timeoutSeconds} onChange={(e) => setStage(i, { timeoutSeconds: e.target.value })} className={inputCls} /></Field>
                    <Field label="Retries"><input type="number" min={0} max={10} value={s.maxRetries} onChange={(e) => setStage(i, { maxRetries: e.target.value })} className={inputCls} /></Field>
                    <Field label="OK status codes"><input placeholder="200, 201" value={s.expectedStatusCodes} onChange={(e) => setStage(i, { expectedStatusCodes: e.target.value })} className={inputCls} /></Field>
                    <Field label="Body contains"><input value={s.responseBodyContains} onChange={(e) => setStage(i, { responseBodyContains: e.target.value })} className={inputCls} /></Field>
                  </div>
                  <div className="flex flex-wrap items-center gap-4 text-xs text-ink-soft">
                    <label className="flex items-center gap-1.5"><input type="checkbox" checked={s.requiresApproval} onChange={(e) => setStage(i, { requiresApproval: e.target.checked })} /> Require manual approval first</label>
                    <label className="flex items-center gap-1.5"><input type="checkbox" checked={s.awaitCallback} onChange={(e) => setStage(i, { awaitCallback: e.target.checked })} /> Wait for callback</label>
                    {s.awaitCallback && (
                      <label className="flex items-center gap-1.5">timeout (s)
                        <input type="number" min={1} value={s.callbackTimeoutSeconds} onChange={(e) => setStage(i, { callbackTimeoutSeconds: e.target.value })} className={`${inputCls} w-24`} />
                      </label>
                    )}
                  </div>
                </div>
              ))}
            </section>
          )}

          {!pipeline && (
            <div className="grid grid-cols-2 gap-3">
              <Field label="Timeout (s)"><input type="number" min={1} max={300} value={form.timeoutSeconds} onChange={update('timeoutSeconds')} className={inputCls} /></Field>
              <Field label="Max retries"><input type="number" min={0} max={10} value={form.maxRetries} onChange={update('maxRetries')} className={inputCls} /></Field>
            </div>
          )}

          <section className="space-y-2">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-semibold text-ink">Run parameters</h3>
              <button type="button" onClick={() => setForm((f) => ({ ...f, parameters: [...f.parameters, { name: '', label: '', defaultValue: '', required: false }] }))}
                className="text-xs text-volt-bright hover:text-volt-ice">+ Add</button>
            </div>
            {form.parameters.map((p, i) => (
              <div key={i} className="grid grid-cols-12 items-center gap-2">
                <input placeholder="name" value={p.name} onChange={(e) => setParam(i, { name: e.target.value })} className={`${inputCls} col-span-3 font-mono`} />
                <input placeholder="label" value={p.label} onChange={(e) => setParam(i, { label: e.target.value })} className={`${inputCls} col-span-3`} />
                <input placeholder="default" value={p.defaultValue} onChange={(e) => setParam(i, { defaultValue: e.target.value })} className={`${inputCls} col-span-3`} />
                <label className="col-span-2 flex items-center gap-1 text-xs text-ink-soft"><input type="checkbox" checked={p.required} onChange={(e) => setParam(i, { required: e.target.checked })} /> req.</label>
                <button type="button" onClick={() => setForm((f) => ({ ...f, parameters: f.parameters.filter((_, j) => j !== i) }))} className="text-ink-soft hover:text-bad">✕</button>
              </div>
            ))}
          </section>

          <div className="grid grid-cols-2 gap-3">
            <Field label="Worker pool" hint="Only replicas started with this tag will run it">
              <input value={form.workerTag} onChange={update('workerTag')} className={`${inputCls} font-mono`} />
            </Field>
            <Field label="Trigger downstream when">
              <select value={form.downstreamTriggerCondition} onChange={update('downstreamTriggerCondition')} className={inputCls}>
                <option value="ON_SUCCESS">Run succeeds</option>
                <option value="ON_FAILURE">Run fails</option>
                <option value="ALWAYS">Always</option>
              </select>
            </Field>
          </div>

          <Field label="Downstream jobs" hint="Ctrl/Cmd-click for multiple. Cycles are rejected.">
            <select multiple value={form.downstreamJobIds}
              onChange={(e) => setForm((f) => ({ ...f, downstreamJobIds: Array.from(e.target.selectedOptions, (o) => o.value) }))}
              className={`${inputCls} h-24`}>
              {otherJobs.map((j) => <option key={j.id} value={j.id}>{j.name}</option>)}
            </select>
          </Field>

          {error && <p className="rounded-xl border border-bad/30 bg-bad/10 px-3 py-2 text-sm text-bad">{error}</p>}

          <div className="flex justify-end gap-2 pt-2">
            <button type="button" onClick={onClose} className="btn-ghost">Cancel</button>
            <button type="submit" disabled={submitting} className="btn-primary">
              {submitting ? 'Saving…' : job ? 'Save changes' : 'Create job'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

const inputCls = 'input'

function CredentialSelect({ value, onChange, credentials }) {
  return (
    <Field label="Credential">
      <select value={value} onChange={onChange} className={inputCls}>
        <option value="">None</option>
        {credentials.map((c) => <option key={c.id} value={c.name}>{c.name} ({c.type})</option>)}
      </select>
    </Field>
  )
}

function Field({ label, hint, children, className = '' }) {
  return (
    <label className={`block ${className}`}>
      <span className="label">{label}</span>
      {children}
      {hint && <span className="hint block">{hint}</span>}
    </label>
  )
}
