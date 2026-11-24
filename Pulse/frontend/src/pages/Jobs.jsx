import React, { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import Layout from '../components/Layout'
import StatusBadge from '../components/StatusBadge'
import JobFormModal from '../components/JobFormModal'
import ConfirmDialog from '../components/ConfirmDialog'
import TriggerDialog from '../components/TriggerDialog'
import Icon from '../components/Icon'
import { PageHeader, Spinner, EmptyState } from '../components/ui'
import { jobService } from '../services/jobService'
import { useWebSocket } from '../hooks/useWebSocket'
import { formatDateTime, timeAgo } from '../lib/format'

const FILTERS = [['', 'All'], ['SCHEDULED', 'Scheduled'], ['RUNNING', 'Running'], ['PAUSED', 'Paused'], ['COMPLETED', 'Completed'], ['FAILED', 'Failed']]

export default function Jobs() {
  const [params, setParams] = useSearchParams()
  const [jobs, setJobs] = useState(null)
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('')
  const [editingJob, setEditingJob] = useState(null)
  const [showForm, setShowForm] = useState(params.get('new') === '1')
  const [pendingDelete, setPendingDelete] = useState(null)
  const [triggerJob, setTriggerJob] = useState(null)
  const [liveStatus, setLiveStatus] = useState({}) // jobId -> latest execution status
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    try {
      const data = await jobService.list({ search: search || undefined, status: status || undefined, size: 100 })
      setJobs(data.content)
      setError(null)
    } catch {
      setError('Could not load jobs. Check your connection and try again.')
    }
  }, [search, status])

  useEffect(() => {
    const t = setTimeout(load, 250) // debounce typing in search
    return () => clearTimeout(t)
  }, [load])

  const handleUpdate = useCallback((execution) => {
    setLiveStatus((prev) => ({ ...prev, [execution.jobId]: execution.status }))
  }, [])
  useWebSocket(handleUpdate)

  const closeForm = () => {
    setShowForm(false)
    if (params.get('new')) { params.delete('new'); setParams(params, { replace: true }) }
  }
  const openCreate = () => { setEditingJob(null); setShowForm(true) }
  const openEdit = (job) => { setEditingJob(job); setShowForm(true) }

  const handleSubmit = async (payload) => {
    if (editingJob) await jobService.update(editingJob.id, payload)
    else await jobService.create(payload)
    closeForm()
    load()
  }

  const handleAction = async (action, job) => {
    if (action === 'pause') await jobService.pause(job.id)
    if (action === 'resume') await jobService.resume(job.id)
    if (action === 'trigger') {
      if (job.parameters?.length) { setTriggerJob(job); return }
      await jobService.trigger(job.id)
    }
    load()
  }

  const confirmDelete = async () => {
    await jobService.remove(pendingDelete.id)
    setPendingDelete(null)
    load()
  }

  return (
    <Layout>
      <PageHeader
        title={<h1 className="text-3xl font-semibold text-ink">Jobs</h1>}
        subtitle="Webhook calls and multi-step pipelines that run on a schedule or on demand."
        actions={<button onClick={openCreate} className="btn-primary"><Icon name="plus" size={16} />New job</button>}
      />

      <div className="mb-5 flex flex-wrap items-center gap-3">
        <div className="relative w-full sm:w-72">
          <Icon name="search" size={16} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-ink-mute" />
          <input aria-label="Search jobs" placeholder="Search by name" value={search} onChange={(e) => setSearch(e.target.value)} className="input !pl-10" />
        </div>
        <div className="flex flex-wrap gap-2" role="group" aria-label="Filter by status">
          {FILTERS.map(([value, label]) => (
            <button
              key={value}
              onClick={() => setStatus(value)}
              aria-pressed={status === value}
              className={`rounded-full border px-3.5 py-1.5 text-sm transition ${status === value ? 'border-volt-bright bg-volt/20 text-ink' : 'border-edge text-ink-soft hover:text-ink'}`}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      <section className="panel overflow-hidden">
        {error && <p role="alert" className="px-5 py-4 text-sm text-bad">{error}</p>}
        {!jobs && !error && <Spinner label="Loading jobs" />}
        {jobs && jobs.length === 0 && (
          <EmptyState
            icon="jobs"
            title={search || status ? 'No jobs match' : 'No jobs yet'}
            body={search || status ? 'Try a different search or filter.' : 'A job calls a webhook on a schedule, or runs a pipeline of calls in order.'}
            action={!search && !status && <button onClick={openCreate} className="btn-primary"><Icon name="plus" size={16} />Create your first job</button>}
          />
        )}
        {jobs && jobs.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[820px] text-left text-sm">
              <thead>
                <tr className="border-b border-edge/60 text-xs text-ink-mute">
                  <th className="px-5 py-3 font-medium">Job</th>
                  <th className="px-3 py-3 font-medium">Status</th>
                  <th className="px-3 py-3 font-medium">Schedule</th>
                  <th className="px-3 py-3 font-medium">Next run</th>
                  <th className="px-3 py-3 font-medium">Last run</th>
                  <th className="px-5 py-3" />
                </tr>
              </thead>
              <tbody>
                {jobs.map((job) => (
                  <tr key={job.id} className="border-b border-edge/40 transition-colors last:border-0 hover:bg-raised/40">
                    <td className="px-5 py-3.5">
                      <Link to={`/jobs/${job.id}`} className="font-medium text-ink hover:text-volt-bright">{job.name}</Link>
                      <div className="mt-1 flex flex-wrap items-center gap-2 text-xs">
                        {job.executionMode === 'PIPELINE' ? (
                          <span className="rounded-md border border-wait/30 bg-wait/10 px-1.5 py-0.5 text-wait">Pipeline, {job.stages?.length ?? 0} stages</span>
                        ) : <span className="max-w-[260px] truncate font-mono text-ink-mute" title={job.webhookUrl}>{job.webhookUrl}</span>}
                        {job.workerTag && job.workerTag !== 'default' && (
                          <span className="rounded-md border border-hold/30 bg-hold/10 px-1.5 py-0.5 text-hold">{job.workerTag}</span>
                        )}
                      </div>
                    </td>
                    <td className="px-3 py-3.5"><StatusBadge status={liveStatus[job.id] === 'RUNNING' ? 'RUNNING' : job.status} /></td>
                    <td className="px-3 py-3.5 text-ink-soft">
                      {job.type === 'RECURRING' ? <span className="font-mono text-xs">{job.cronExpression}</span> : 'Once'}
                    </td>
                    <td className="px-3 py-3.5 text-ink-soft">{formatDateTime(job.nextRunAt)}</td>
                    <td className="px-3 py-3.5 text-ink-soft">{timeAgo(job.lastRunAt)}</td>
                    <td className="px-5 py-3.5">
                      <div className="flex items-center justify-end gap-1">
                        <button onClick={() => handleAction('trigger', job)} className="btn-quiet !text-volt-bright"><Icon name="play" size={12} />Run</button>
                        {job.status === 'PAUSED' ? (
                          <button onClick={() => handleAction('resume', job)} className="btn-quiet">Resume</button>
                        ) : (
                          <button onClick={() => handleAction('pause', job)} className="btn-quiet">Pause</button>
                        )}
                        <button onClick={() => openEdit(job)} className="btn-quiet" aria-label={`Edit ${job.name}`}><Icon name="edit" size={14} /></button>
                        <button onClick={() => setPendingDelete(job)} className="btn-quiet hover:!text-bad" aria-label={`Delete ${job.name}`}><Icon name="trash" size={14} /></button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {showForm && <JobFormModal job={editingJob} onClose={closeForm} onSubmit={handleSubmit} />}
      {triggerJob && (
        <TriggerDialog
          job={triggerJob}
          onCancel={() => setTriggerJob(null)}
          onRun={async (p) => { await jobService.trigger(triggerJob.id, p); setTriggerJob(null); load() }}
        />
      )}
      {pendingDelete && (
        <ConfirmDialog
          title="Delete this job?"
          message={`“${pendingDelete.name}” will be deleted and will no longer run. Its run history goes with it.`}
          confirmLabel="Delete job"
          onCancel={() => setPendingDelete(null)}
          onConfirm={confirmDelete}
        />
      )}
    </Layout>
  )
}
