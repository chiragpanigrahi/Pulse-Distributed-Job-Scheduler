import React, { useState } from 'react'
import { Modal, Field } from './ui'

export default function TriggerDialog({ job, onCancel, onRun }) {
  const [values, setValues] = useState(() =>
    Object.fromEntries((job.parameters || []).map((p) => [p.name, p.defaultValue ?? '']))
  )
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await onRun(values)
    } catch (err) {
      setError(err?.response?.data?.message || 'Could not start the job. Try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal
      title={`Run ${job.name}`}
      subtitle="Fill in the values this run should use."
      onClose={onCancel}
      footer={(
        <>
          <button type="button" onClick={onCancel} className="btn-ghost">Cancel</button>
          <button form="trigger-form" disabled={busy} className="btn-primary">{busy ? 'Starting…' : 'Run now'}</button>
        </>
      )}
    >
      <form id="trigger-form" onSubmit={submit} className="space-y-4">
        {(job.parameters || []).map((p) => (
          <Field key={p.name} label={`${p.label || p.name}${p.required ? ' (required)' : ''}`} hint={`Available as {{params.${p.name}}}`}>
            <input
              required={p.required}
              value={values[p.name] ?? ''}
              onChange={(e) => setValues((v) => ({ ...v, [p.name]: e.target.value }))}
              className="input"
            />
          </Field>
        ))}
        {error && <p role="alert" className="text-sm text-bad">{error}</p>}
      </form>
    </Modal>
  )
}
