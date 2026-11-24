import React from 'react'
import { Link } from 'react-router-dom'
import StatusBadge from './StatusBadge'
import Icon from './Icon'
import { HttpPill } from './ui'
import { useInspector } from './Inspector'
import { executionService } from '../services/executionService'
import { formatDuration, timeAgo } from '../lib/format'

/** One row per run attempt. Clicking a row opens the inspector with what was sent and received. */
export default function RunsTable({ executions, showJob = false, onChanged }) {
  const inspector = useInspector()

  const decide = async (e, fn, id) => {
    e.stopPropagation()
    await fn(id)
    onChanged?.()
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[640px] text-left text-sm">
        <thead>
          <tr className="border-b border-edge/60 text-xs text-ink-mute">
            <th className="px-5 py-3 font-medium">Status</th>
            <th className="px-3 py-3 font-medium">{showJob ? 'Job' : 'Stage'}</th>
            <th className="px-3 py-3 font-medium">Started</th>
            <th className="px-3 py-3 font-medium">Took</th>
            <th className="px-3 py-3 font-medium">HTTP</th>
            <th className="px-5 py-3" />
          </tr>
        </thead>
        <tbody>
          {executions.map((e) => (
            <tr
              key={e.id}
              onClick={() => inspector.open(e.id)}
              className="cursor-pointer border-b border-edge/40 transition-colors last:border-0 hover:bg-raised/40"
            >
              <td className="px-5 py-3"><StatusBadge status={e.status} /></td>
              <td className="px-3 py-3 text-ink">
                {showJob ? (
                  <>
                    <Link to={`/jobs/${e.jobId}`} onClick={(ev) => ev.stopPropagation()} className="font-medium hover:text-volt-bright">{e.jobName}</Link>
                    {e.stageName && <span className="text-ink-soft"> / {e.stageName}</span>}
                  </>
                ) : (
                  <>{e.stageIndex != null ? `${e.stageIndex + 1}. ${e.stageName}` : 'Single call'}{e.attemptNumber > 1 && <span className="ml-2 text-xs text-ink-mute">attempt {e.attemptNumber}</span>}</>
                )}
              </td>
              <td className="px-3 py-3 text-ink-soft">{timeAgo(e.startedAt || e.createdAt)}</td>
              <td className="px-3 py-3 font-mono text-xs text-ink-soft">{e.durationMs != null ? formatDuration(e.durationMs) : '—'}</td>
              <td className="px-3 py-3"><HttpPill code={e.responseStatusCode} /></td>
              <td className="px-5 py-3">
                <div className="flex items-center justify-end gap-1.5">
                  {e.status === 'AWAITING_APPROVAL' && (
                    <>
                      <button onClick={(ev) => decide(ev, executionService.approve, e.id)} className="btn-quiet !text-ok">Approve</button>
                      <button onClick={(ev) => decide(ev, executionService.reject, e.id)} className="btn-quiet !text-bad">Reject</button>
                    </>
                  )}
                  <button onClick={(ev) => { ev.stopPropagation(); inspector.open(e.id) }} className="btn-quiet"><Icon name="braces" size={13} />Inspect</button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
