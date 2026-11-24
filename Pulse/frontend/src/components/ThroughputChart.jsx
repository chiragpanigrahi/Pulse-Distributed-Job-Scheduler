import React from 'react'
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts'

export default function ThroughputChart({ executionsByHour }) {
  const data = Object.entries(executionsByHour || {}).map(([hour, count]) => ({ hour, count }))
  const total = data.reduce((n, d) => n + d.count, 0)

  return (
    <div className="panel p-5">
      <h2 className="text-base font-semibold text-ink">Runs per hour</h2>
      <p className="mt-0.5 text-sm text-ink-soft">How busy the scheduler has been over the last 24 hours.</p>
      {total === 0 ? (
        <p className="py-14 text-center text-sm text-ink-mute">No runs yet. Create a job to see activity here.</p>
      ) : (
        <div className="mt-4">
          <ResponsiveContainer width="100%" height={190}>
            <BarChart data={data} margin={{ top: 6, right: 4, left: -22, bottom: 0 }} barCategoryGap="22%">
              <defs>
                <linearGradient id="barFill" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#7FA2FF" />
                  <stop offset="100%" stopColor="#2E6BFF" />
                </linearGradient>
              </defs>
              <CartesianGrid vertical={false} stroke="#1B2A5E" strokeDasharray="3 5" strokeOpacity={0.7} />
              <XAxis dataKey="hour" tick={{ fill: '#6F80B8', fontSize: 11 }} axisLine={false} tickLine={false} interval={3} />
              <YAxis tick={{ fill: '#6F80B8', fontSize: 11 }} axisLine={false} tickLine={false} width={40} allowDecimals={false} />
              <Tooltip
                cursor={{ fill: 'rgba(91,141,255,0.08)' }}
                contentStyle={{ background: '#101C45', border: '1px solid #1B2A5E', borderRadius: 12, fontSize: 12, color: '#E9EFFF' }}
                labelStyle={{ color: '#A6B5E6' }}
                formatter={(v) => [`${v} runs`, '']}
                separator=""
              />
              <Bar dataKey="count" fill="url(#barFill)" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  )
}
