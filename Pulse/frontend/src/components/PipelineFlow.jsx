import React from 'react'

function Chip({ children, tone = 'mute' }) {
  const tones = {
    mute: 'border-edge text-ink-soft',
    wait: 'border-wait/40 bg-wait/10 text-wait',
    live: 'border-volt-bright/40 bg-volt/10 text-volt-ice',
    bad: 'border-bad/40 bg-bad/10 text-bad',
    hold: 'border-hold/40 bg-hold/10 text-hold',
  }
  return <span className={`rounded-md border px-2 py-0.5 text-[11px] ${tones[tone]}`}>{children}</span>
}

const CONDITION_LABEL = { ALWAYS: 'Always runs', ON_FAILURE: 'Only after a failure' }

/** Horizontal map of a pipeline's stages with the rules that apply to each. */
export default function PipelineFlow({ stages }) {
  return (
    <ol className="flex gap-0 overflow-x-auto pb-2">
      {stages.map((st, i) => (
        <li key={`${st.name}-${i}`} className="flex items-center">
          <div className="w-[230px] shrink-0 rounded-xl border border-edge/80 bg-night/60 p-3.5">
            <div className="flex items-center gap-2">
              <span className="flex h-5 w-5 items-center justify-center rounded-full bg-volt/20 text-[11px] font-semibold text-volt-ice">{i + 1}</span>
              <span className="truncate text-sm font-semibold text-ink">{st.name}</span>
            </div>
            <p className="mt-2.5 flex items-center gap-1.5 truncate text-xs">
              <span className="rounded border border-volt-bright/30 px-1.5 font-mono text-[10px] text-volt-bright">{st.httpMethod || 'POST'}</span>
              <span className="truncate font-mono text-ink-soft" title={st.webhookUrl}>{st.webhookUrl?.replace(/^https?:\/\//, '')}</span>
            </p>
            <div className="mt-2.5 flex flex-wrap gap-1.5">
              {st.condition && CONDITION_LABEL[st.condition] && <Chip tone={st.condition === 'ON_FAILURE' ? 'bad' : 'mute'}>{CONDITION_LABEL[st.condition]}</Chip>}
              {st.requiresApproval && <Chip tone="wait">Needs approval</Chip>}
              {st.awaitCallback && <Chip tone="hold">Callback within {st.callbackTimeoutSeconds || 3600}s</Chip>}
              {st.credentialName && <Chip>Uses {st.credentialName}</Chip>}
            </div>
          </div>
          {i < stages.length - 1 && (
            <svg width="34" height="12" viewBox="0 0 34 12" className="shrink-0" aria-hidden>
              <path d="M0 6h28" stroke="#5B8DFF" strokeWidth="1.5" strokeDasharray="4 4" className="animate-flow" />
              <path d="M26 2l6 4-6 4" fill="none" stroke="#5B8DFF" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          )}
        </li>
      ))}
    </ol>
  )
}
