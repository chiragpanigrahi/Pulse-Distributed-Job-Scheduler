import React, { useCallback, useEffect, useState } from 'react'
import Layout from '../components/Layout'
import ConfirmDialog from '../components/ConfirmDialog'
import Icon from '../components/Icon'
import { PageHeader, EmptyState, Field } from '../components/ui'
import { credentialService } from '../services/credentialService'

const TYPES = {
  BEARER: { label: 'Bearer token', primary: 'Token', secondary: null, blurb: 'Sent as an Authorization: Bearer header.' },
  BASIC: { label: 'Username and password', primary: 'Username', secondary: 'Password', blurb: 'Sent as an Authorization: Basic header.' },
  HEADER: { label: 'API key in a header', primary: 'Header value', secondary: null, header: true, blurb: 'Sent under the header name you choose.' },
  HMAC: { label: 'Signed request (HMAC)', primary: 'Signing secret', secondary: null, header: true, blurb: 'Pulse signs each request body and sends the signature in the header you choose.' },
}
const EMPTY = { name: '', description: '', type: 'BEARER', headerName: '', primaryValue: '', secondaryValue: '' }

export default function Credentials() {
  const [items, setItems] = useState(null)
  const [form, setForm] = useState(EMPTY)
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)
  const [pendingDelete, setPendingDelete] = useState(null)

  const load = useCallback(async () => setItems(await credentialService.list()), [])
  useEffect(() => { load().catch(() => setItems([])) }, [load])

  const meta = TYPES[form.type]
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }))

  const submit = async (e) => {
    e.preventDefault()
    setError(null)
    setSaving(true)
    try {
      await credentialService.create({
        ...form,
        headerName: meta.header ? form.headerName : undefined,
        secondaryValue: meta.secondary ? form.secondaryValue : undefined,
      })
      setForm(EMPTY)
      load()
    } catch (err) {
      setError(err?.response?.data?.message || 'Could not save this credential. Check the fields and try again.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Layout>
      <PageHeader
        title={<h1 className="text-3xl font-semibold text-ink">Credentials</h1>}
        subtitle={<>Store API keys and tokens once, encrypted. Use them in any webhook as <span className="font-mono text-xs text-volt-ice">{'{{secrets.name}}'}</span> or attach one to a job. Values are never shown again, and they are masked in the run inspector.</>}
      />

      <div className="grid gap-6 lg:grid-cols-5">
        <form onSubmit={submit} className="panel space-y-4 p-5 lg:col-span-2">
          <h2 className="text-base font-semibold text-ink">Add a credential</h2>
          <Field label="Name" hint="Letters, numbers, dashes, dots and underscores.">
            <input required placeholder="stripe-prod" value={form.name} onChange={set('name')} className="input font-mono" />
          </Field>
          <Field label="Description (optional)">
            <input value={form.description} onChange={set('description')} className="input" />
          </Field>
          <Field label="Type" hint={meta.blurb}>
            <select value={form.type} onChange={set('type')} className="input">
              {Object.entries(TYPES).map(([k, t]) => <option key={k} value={k}>{t.label}</option>)}
            </select>
          </Field>
          {meta.header && (
            <Field label="Header name">
              <input required placeholder="X-Signature" value={form.headerName} onChange={set('headerName')} className="input font-mono" />
            </Field>
          )}
          <Field label={meta.primary}>
            <input required type="password" value={form.primaryValue} onChange={set('primaryValue')} className="input" autoComplete="off" />
          </Field>
          {meta.secondary && (
            <Field label={meta.secondary}>
              <input required type="password" value={form.secondaryValue} onChange={set('secondaryValue')} className="input" autoComplete="off" />
            </Field>
          )}
          {error && <p role="alert" className="text-sm text-bad">{error}</p>}
          <button disabled={saving} className="btn-primary w-full">{saving ? 'Saving…' : 'Save credential'}</button>
        </form>

        <section className="panel overflow-hidden lg:col-span-3">
          {items && items.length === 0 ? (
            <EmptyState icon="key" title="No credentials yet" body="Add one on the left, then reference it by name in a job." />
          ) : (
            <ul className="divide-y divide-edge/50">
              {(items || []).map((c) => (
                <li key={c.id} className="flex items-center justify-between gap-4 px-5 py-4">
                  <div className="flex min-w-0 items-center gap-3">
                    <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-volt/15 text-volt-bright"><Icon name="key" size={16} /></span>
                    <div className="min-w-0">
                      <p className="truncate font-mono text-sm text-ink">{c.name}</p>
                      <p className="truncate text-xs text-ink-soft">
                        {TYPES[c.type]?.label || c.type}{c.headerName ? `, ${c.headerName}` : ''}{c.createdBy ? `. Added by ${c.createdBy}` : ''}
                      </p>
                      {c.description && <p className="truncate text-xs text-ink-mute">{c.description}</p>}
                    </div>
                  </div>
                  <button onClick={() => setPendingDelete(c)} className="btn-quiet hover:!text-bad" aria-label={`Delete ${c.name}`}><Icon name="trash" size={15} /></button>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      {pendingDelete && (
        <ConfirmDialog
          title="Delete this credential?"
          message={`Jobs that use “${pendingDelete.name}” will fail until you add it again.`}
          confirmLabel="Delete credential"
          onCancel={() => setPendingDelete(null)}
          onConfirm={async () => { await credentialService.remove(pendingDelete.id); setPendingDelete(null); load() }}
        />
      )}
    </Layout>
  )
}
