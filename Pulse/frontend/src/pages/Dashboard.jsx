import React, { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import Layout from '../components/Layout'
import ThroughputChart from '../components/ThroughputChart'
import RunsTable from '../components/RunsTable'
import StatusBadge from '../components/StatusBadge'
import Icon from '../components/Icon'
import { PageHeader, Spinner, EmptyState } from '../components/ui'
import { useInspector } from '../components/Inspector'
import { useLiveStatus } from '../hooks/useLiveStatus'
import { dashboardService } from '../services/dashboardService'
import { executionService } from '../services/executionService'
import { useWebSocket } from '../hooks/useWebSocket'
import { isFailure, timeAgo } from '../lib/format'

const DAY_MS = 24 * 60 * 60 * 1000

export default function Dashboard() {
  const navigate = useNavigate()
  const inspector = useInspector()
  const live = useLiveStatus()
  const [stats, setStats] = useState(null)
  const [error, setError] = useState(null)
  const [approvals, setApprovals] = useState([])

  const loadApprovals = useCallback(() => executionService.pendingApprovals().then(setApprovals).catch(() => {}), [])
  const load = useCallback(async () => {
    try {
      setStats(await dashboardService.stats())
      setError(null)
    } catch {
      setError('Could not load the overview. Pulse will keep trying.')
    }
  }, [])

  useEffect(() => { loadApprovals() }, [loadApprovals])
  useEffect(() => {
    load()
    const t = setInterval(load, 30000)
    return () => clearInterval(t)
  }, [load])

  // Patch the recent-runs list as updates stream in, without waiting for the next refresh.
  const onUpdate = useCallback((execution) => {
    if (['AWAITING_APPROVAL', 'RUNNING', 'SKIPPED', 'CANCELLED', 'SUCCESS'].includes(execution.status)) loadApprovals()
    setStats((prev) => {
      if (!prev) return prev
      const rest = (prev.recentExecutions || []).filter((e) => e.id !== execution.id)
      return { ...prev, recentExecutions: [execution, ...rest].slice(0, 20) }
    })
  }, [loadApprovals])
  useWebSocket(onUpdate)

  // Failures from the last day that were not followed by a success of the same stage.
  const failures = useMemo(() => {
    const recent = stats?.recentExecutions || []
    return recent
      .filter((e) => isFailure(e.status) && Date.now() - new Date(e.createdAt).getTime() < DAY_MS)
      .filter((e) => !recent.some((o) => o.jobId === e.jobId && o.stageName === e.stageName && o.status === 'SUCCESS'
        && new Date(o.createdAt) > new Date(e.createdAt)))
      .slice(0, 3)
  }, [stats])

  const decide = async (fn, id) => { await fn(id); loadApprovals(); load() }

  const headline = !stats ? 'Loading your scheduler…'
    : approvals.length > 0 ? `${approvals.length === 1 ? '1 run is' : `${approvals.length} runs are`} waiting for your approval.`
      : failures.length > 0 ? `${failures.length === 1 ? '1 run' : `${failures.length} runs`} failed recently and may need a look.`
        : stats.runningExecutions > 0 ? `${stats.runningExecutions} ${stats.runningExecutions === 1 ? 'run is' : 'runs are'} in progress. Nothing needs you.`
          : 'Everything is running normally.'

  const needsYou = approvals.length + failures.length

  return (
    <Layout>
      <PageHeader
        title={<h1 className="text-3xl font-semibold text-ink">Overview</h1>}
        subtitle={headline}
        actions={(
          <>
            <span className="mr-2 flex items-center gap-2 text-xs text-ink-soft">
              {live ? <span className="live-dot" /> : <span className="inline-block h-2 w-2 rounded-full bg-ink-mute" />}
              {live ? 'Live' : 'Offline'}
            </span>
            <button onClick={() => navigate('/jobs?new=1')} className="btn-primary"><Icon name="plus" size={16} />New job</button>
          </>
        )}
      />

      {error && <p role="alert" className="mb-5 rounded-xl border border-bad/30 bg-bad/10 px-4 py-3 text-sm text-bad">{error}</p>}
      {!stats && !error && <Spinner label="Loading overview" />}

      {stats && (
        <div className="space-y-6">
          {needsYou > 0 ? (
            <section className="pearl rounded-2xl p-5 shadow-lift">
              <div className="mb-3 flex items-center gap-3">
                <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-wait/15 text-wait"><Icon name="hourglass" size={17} /></span>
                <h2 className="text-lg font-semibold text-ink">{needsYou === 1 ? '1 thing needs you' : `${needsYou} things need you`}</h2>
              </div>
              <ul className="divide-y divide-edge/50">
                {approvals.map((a) => (
                  <li key={a.id} className="flex flex-wrap items-center justify-between gap-3 py-3.5">
                    <div>
                      <p className="text-sm text-ink"><Link to={`/jobs/${a.jobId}`} className="font-semibold hover:text-volt-ice">{a.jobName}</Link> needs approval before “{a.stageName}” runs</p>
                      <p className="mt-0.5 text-xs text-ink-soft">Waiting since {timeAgo(a.createdAt)}</p>
                    </div>
                    <div className="flex gap-2">
                      <button onClick={() => decide(executionService.approve, a.id)} className="btn-primary !py-1.5">Approve</button>
                      <button onClick={() => decide(executionService.reject, a.id)} className="btn-ghost !py-1.5">Reject</button>
                    </div>
                  </li>
                ))}
                {failures.map((f) => (
                  <li key={f.id} className="flex flex-wrap items-center justify-between gap-3 py-3.5">
                    <div>
                      <p className="flex flex-wrap items-center gap-2 text-sm text-ink">
                        <Link to={`/jobs/${f.jobId}`} className="font-semibold hover:text-volt-ice">{f.jobName}</Link>
                        <StatusBadge status={f.status} />
                      </p>
                      <p className="mt-0.5 text-xs text-ink-soft">{f.errorMessage || 'The webhook did not succeed.'} {timeAgo(f.createdAt)}</p>
                    </div>
                    <button onClick={() => inspector.open(f.id)} className="btn-ghost !py-1.5"><Icon name="braces" size={15} />See what happened</button>
                  </li>
                ))}
              </ul>
            </section>
          ) : (
            <section className="panel flex items-center gap-4 p-5">
              <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-ok/10 text-ok"><Icon name="check" size={19} /></span>
              <div>
                <h2 className="text-base font-semibold text-ink">All clear</h2>
                <p className="text-sm text-ink-soft">No approvals are waiting and nothing has failed in the last day.</p>
              </div>
            </section>
          )}

          <Metrics stats={stats} />

          <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
            <ThroughputChart executionsByHour={stats.executionsByHour} />
            <Outcome stats={stats} />
          </div>

          <section className="panel overflow-hidden">
            <div className="flex items-center justify-between px-5 pb-1 pt-5">
              <div>
                <h2 className="text-base font-semibold text-ink">Recent runs</h2>
                <p className="mt-0.5 text-sm text-ink-soft">Select a run to see what Pulse sent and what came back.</p>
              </div>
              <Link to="/activity" className="btn-quiet">See all</Link>
            </div>
            {(stats.recentExecutions || []).length === 0 ? (
              <EmptyState icon="activity" title="No runs yet" body="Runs show up here as soon as a job fires." action={<Link to="/jobs?new=1" className="btn-primary">Create a job</Link>} />
            ) : (
              <RunsTable executions={stats.recentExecutions.slice(0, 8)} showJob onChanged={() => { loadApprovals(); load() }} />
            )}
          </section>
        </div>
      )}
    </Layout>
  )
}

function Metrics({ stats }) {
  const total = stats.executionsLast24h
  const pct = total === 0 ? null : Math.round(stats.successRatePercent)
  const waiting = stats.awaitingApprovalCount + stats.pendingCallbackCount
  const cells = [
    { label: 'Jobs', value: stats.totalJobs, note: `${stats.scheduledJobs} scheduled, ${stats.pausedJobs} paused` },
    { label: 'Running now', value: stats.runningExecutions, note: stats.runningExecutions === 1 ? 'run in progress' : 'runs in progress' },
    { label: 'Waiting', value: waiting, note: `${stats.awaitingApprovalCount} for approval, ${stats.pendingCallbackCount} for callback` },
  ]
  const r = 26
  const c = 2 * Math.PI * r

  return (
    <section className="panel grid divide-edge/60 sm:grid-cols-2 sm:divide-x lg:grid-cols-4" aria-label="Key numbers">
      {cells.map((cell) => (
        <div key={cell.label} className="border-b border-edge/60 px-6 py-5 sm:border-b-0 sm:[&:nth-child(-n+2)]:border-b lg:[&:nth-child(-n+2)]:border-b-0">
          <div className="text-sm text-ink-soft">{cell.label}</div>
          <div className="mt-1 font-display text-[40px] font-semibold leading-none tracking-tight text-ink">{cell.value}</div>
          <div className="mt-2 text-xs text-ink-mute">{cell.note}</div>
        </div>
      ))}
      <div className="flex items-center justify-between gap-4 px-6 py-5">
        <div>
          <div className="text-sm text-ink-soft">Success rate</div>
          <div className="mt-1 font-display text-[40px] font-semibold leading-none tracking-tight text-ink">{pct == null ? '—' : `${pct}%`}</div>
          <div className="mt-2 text-xs text-ink-mute">{total === 0 ? 'No runs in the last 24 hours' : `${stats.successLast24h} of ${total} runs, last 24 hours`}</div>
        </div>
        <svg width="64" height="64" viewBox="0 0 64 64" role="img" aria-label={pct == null ? 'No data' : `${pct} percent succeeded`}>
          <defs>
            <linearGradient id="ring" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stopColor="#8FE6F5" />
              <stop offset="55%" stopColor="#2E6BFF" />
              <stop offset="100%" stopColor="#B7A8FF" />
            </linearGradient>
          </defs>
          <circle cx="32" cy="32" r={r} fill="none" stroke="#1B2A5E" strokeWidth="6" />
          {pct != null && (
            <circle cx="32" cy="32" r={r} fill="none" stroke="url(#ring)" strokeWidth="6" strokeLinecap="round"
              strokeDasharray={c} strokeDashoffset={c * (1 - pct / 100)} transform="rotate(-90 32 32)" />
          )}
        </svg>
      </div>
    </section>
  )
}

function Outcome({ stats }) {
  const total = stats.executionsLast24h
  const ok = stats.successLast24h
  const bad = stats.failedLast24h
  const other = Math.max(0, total - ok - bad)
  const share = (n) => (total ? (n / total) * 100 : 0)
  const rows = [
    ['Succeeded', ok, 'bg-ok'],
    ['Failed', bad, 'bg-bad'],
    ['In progress or skipped', other, 'bg-volt-bright'],
  ]
  return (
    <div className="panel p-5">
      <h2 className="text-base font-semibold text-ink">How runs ended</h2>
      <p className="mt-0.5 text-sm text-ink-soft">{total} {total === 1 ? 'run' : 'runs'} in the last 24 hours.</p>
      <div className="mt-5 flex h-2.5 overflow-hidden rounded-full bg-edge/60" role="img" aria-label="Share of runs by outcome">
        {rows.map(([label, n, color]) => n > 0 && <div key={label} className={color} style={{ width: `${share(n)}%` }} />)}
      </div>
      <ul className="mt-5 space-y-3">
        {rows.map(([label, n, color]) => (
          <li key={label} className="flex items-center justify-between text-sm">
            <span className="flex items-center gap-2.5 text-ink-soft"><span className={`h-2.5 w-2.5 rounded-full ${color}`} />{label}</span>
            <span className="font-mono text-ink">{n}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
