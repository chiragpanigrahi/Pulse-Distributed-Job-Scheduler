import React, { useCallback, useEffect, useState } from 'react'
import Layout from '../components/Layout'
import RunsTable from '../components/RunsTable'
import { PageHeader, Spinner, EmptyState } from '../components/ui'
import { executionService } from '../services/executionService'
import { useWebSocket } from '../hooks/useWebSocket'

const FILTERS = [
  ['', 'All runs'],
  ['SUCCESS', 'Succeeded'],
  ['FAILED', 'Failed'],
  ['TIMEOUT', 'Timed out'],
  ['AWAITING_APPROVAL', 'Needs approval'],
  ['PENDING_CALLBACK', 'Waiting for callback'],
  ['RUNNING', 'Running'],
]
const PAGE_SIZE = 25

export default function Activity() {
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    try {
      setData(await executionService.list({ status, page, size: PAGE_SIZE }))
      setError(null)
    } catch {
      setError('Could not load runs. Check your connection and try again.')
    }
  }, [status, page])

  useEffect(() => { setData(null); load() }, [load])

  // New activity refreshes the first page so the newest run appears at the top.
  const onUpdate = useCallback(() => { if (page === 0) load() }, [page, load])
  useWebSocket(onUpdate)

  const rows = data?.content || []
  const totalPages = data?.totalPages || 1

  return (
    <Layout>
      <PageHeader
        title={<h1 className="text-3xl font-semibold text-ink">Activity</h1>}
        subtitle="Every run across all jobs, newest first. Select a run to see the JSON Pulse sent and received."
      />

      <div className="mb-5 flex flex-wrap gap-2" role="group" aria-label="Filter runs">
        {FILTERS.map(([value, label]) => (
          <button
            key={value}
            onClick={() => { setStatus(value); setPage(0) }}
            aria-pressed={status === value}
            className={`rounded-full border px-3.5 py-1.5 text-sm transition ${status === value ? 'border-volt-bright bg-volt/20 text-ink' : 'border-edge text-ink-soft hover:text-ink'}`}
          >
            {label}
          </button>
        ))}
      </div>

      <section className="panel overflow-hidden">
        {error && <p role="alert" className="px-5 py-4 text-sm text-bad">{error}</p>}
        {!data && !error && <Spinner label="Loading runs" />}
        {data && rows.length === 0 && (
          <EmptyState icon="activity" title="No runs match" body={status ? 'Try a different filter.' : 'Runs appear here as soon as a job fires.'} />
        )}
        {rows.length > 0 && <RunsTable executions={rows} showJob onChanged={load} />}
        {data && totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-edge/60 px-5 py-3 text-sm text-ink-soft">
            <span>Page {page + 1} of {totalPages}</span>
            <div className="flex gap-2">
              <button disabled={page === 0} onClick={() => setPage((p) => p - 1)} className="btn-ghost !py-1.5">Previous</button>
              <button disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)} className="btn-ghost !py-1.5">Next</button>
            </div>
          </div>
        )}
      </section>
    </Layout>
  )
}
