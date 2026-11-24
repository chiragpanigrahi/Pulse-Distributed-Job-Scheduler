import React, { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import Layout from '../components/Layout'
import StatusBadge from '../components/StatusBadge'
import RunsTable from '../components/RunsTable'
import PipelineFlow from '../components/PipelineFlow'
import JobFormModal from '../components/JobFormModal'
import ConfirmDialog from '../components/ConfirmDialog'
import TriggerDialog from '../components/TriggerDialog'
import Icon from '../components/Icon'
import { PageHeader, Spinner, EmptyState } from '../components/ui'
import { jobService } from '../services/jobService'
import { useWebSocket } from '../hooks/useWebSocket'
import { formatDateTime, timeAgo } from '../lib/format'

export default function JobDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [job, setJob] = useState(null)
  const [executions, setExecutions] = useState([])
  const [showForm, setShowForm] = useState(false)
  const [showDelete, setShowDelete] = useState(false)
  const [showTrigger, setShowTrigger] = useState(false)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    try {
      const [jobData, execData] = await Promise.all([jobService.get(id), jobService.executions(id)])
      setJob(jobData)
      setExecutions(execData.content)
      setError(null)
    } catch (e) {
      setError(e?.response?.status === 404 ? 'This job no longer exists.' : 'Could not load this job. Check your connection and try again.')
    }
  }, [id])

  useEffect(() => { load() }, [load])

  const handleUpdate = useCallback((execution) => {
    if (execution.jobId !== id) return
    setExecutions((prev) => [execution, ...prev.filter((e) => e.id !== execution.id)].slice(0, 50))
  }, [id])
  useWebSocket(handleUpdate)

  if (error || !job) {
    return (
      <Layout>
        {error ? (
          <EmptyState icon="alert" title={error} action={<Link to="/jobs" className="btn-ghost">Back to jobs</Link>} />
        ) : <Spinner label="Loading job" />}
      </Layout>
    )
  }

  const handleAction = async (action) => {
    if (action === 'pause') await jobService.pause(job.id)
    if (action === 'resume') await jobService.resume(job.id)
    if (action === 'trigger') {
      if (job.parameters?.length) { setShowTrigger(true); return }
      await jobService.trigger(job.id)
    }
    load()
  }

  const isPipeline = job.executionMode === 'PIPELINE'
  const downstream = job.downstreamJobIds?.length || 0

  return (
    <Layout>
      <PageHeader
        back={<Link to="/jobs" className="mb-3 inline-flex items-center gap-1 text-sm text-ink-soft hover:text-ink"><Icon name="back" size={14} />Jobs</Link>}
        title={(<><h1 className="text-3xl font-semibold text-ink">{job.name}</h1><StatusBadge status={job.status} /></>)}
        subtitle={job.description}
        actions={(
          <>
            <button onClick={() => handleAction('trigger')} className="btn-primary"><Icon name="play" size={14} />Run now</button>
            {job.status === 'PAUSED'
              ? <button onClick={() => handleAction('resume')} className="btn-ghost">Resume</button>
              : <button onClick={() => handleAction('pause')} className="btn-ghost"><Icon name="pause" size={14} />Pause</button>}
            <button onClick={() => setShowForm(true)} className="btn-ghost"><Icon name="edit" size={14} />Edit</button>
            <button onClick={() => setShowDelete(true)} className="btn-danger" aria-label="Delete job"><Icon name="trash" size={15} /></button>
          </>
        )}
      />

      <div className="space-y-6">
        {isPipeline ? (
          <section className="panel p-5">
            <h2 className="mb-4 text-base font-semibold text-ink">Pipeline</h2>
            <PipelineFlow stages={job.stages || []} />
          </section>
        ) : (
          <section className="panel p-5">
            <h2 className="mb-3 text-base font-semibold text-ink">Webhook</h2>
            <p className="flex items-start gap-3 break-all font-mono text-sm text-ink">
              <span className="rounded-md border border-volt-bright/30 px-2 py-0.5 text-[11px] font-semibold text-volt-bright">{job.httpMethod}</span>
              {job.webhookUrl}
            </p>
          </section>
        )}

        <section className="panel grid gap-x-6 gap-y-5 p-5 sm:grid-cols-2 lg:grid-cols-5">
          <Fact label="Schedule" value={job.type === 'RECURRING' ? job.cronExpression : `Once, ${formatDateTime(job.runAt)}`} mono={job.type === 'RECURRING'} />
          <Fact label="Next run" value={formatDateTime(job.nextRunAt)} />
          <Fact label="Last run" value={timeAgo(job.lastRunAt)} />
          <Fact label="Worker pool" value={job.workerTag} mono />
          <Fact label="Created by" value={job.createdBy} />
          <Fact label="Timeout" value={`${job.timeoutSeconds}s`} />
          <Fact label="Retries" value={job.maxRetries} />
          <Fact label="After each run" value={downstream ? `Triggers ${downstream} other ${downstream === 1 ? 'job' : 'jobs'}` : 'Nothing'} />
          <Fact label="Parameters" value={job.parameters?.length ? job.parameters.map((p) => p.name).join(', ') : 'None'} mono={!!job.parameters?.length} />
        </section>

        <section className="panel overflow-hidden">
          <div className="px-5 pb-1 pt-5">
            <h2 className="text-base font-semibold text-ink">Run history</h2>
            <p className="mt-0.5 text-sm text-ink-soft">Select a run to inspect the request and response.</p>
          </div>
          {executions.length === 0
            ? <EmptyState icon="activity" title="No runs yet" body="Use Run now to try this job." />
            : <RunsTable executions={executions} onChanged={load} />}
        </section>
      </div>

      {showForm && <JobFormModal job={job} onClose={() => setShowForm(false)} onSubmit={async (p) => { await jobService.update(job.id, p); setShowForm(false); load() }} />}
      {showTrigger && (
        <TriggerDialog job={job} onCancel={() => setShowTrigger(false)} onRun={async (p) => { await jobService.trigger(job.id, p); setShowTrigger(false); load() }} />
      )}
      {showDelete && (
        <ConfirmDialog
          title="Delete this job?"
          message={`“${job.name}” will be deleted and will no longer run. Its run history goes with it.`}
          confirmLabel="Delete job"
          onCancel={() => setShowDelete(false)}
          onConfirm={async () => { await jobService.remove(job.id); navigate('/jobs') }}
        />
      )}
    </Layout>
  )
}

function Fact({ label, value, mono }) {
  return (
    <div className="min-w-0">
      <div className="text-xs text-ink-mute">{label}</div>
      <div className={`mt-1 break-words text-sm text-ink ${mono ? 'font-mono text-[13px]' : ''}`}>{value ?? '—'}</div>
    </div>
  )
}
