import { useState } from 'react'
import { useC } from '../../../design/ThemeContext'
import { THead } from '../../../design/components/THead'
import { Badge } from '../../../design/components/Badge'
import { Modal } from '../../../design/components/Modal'
import { Select } from '../../../design/components/Select'
import { Pagination } from '../../../design/components/Pagination'

const SCHEDULES = [
  { id: 1, reportType: 'Weekly delivery', client: 'Nexus Corp', cronExpression: '0 0 8 * * MON', recipients: ['priya.k@orbit.io', 'amit.s@orbit.io'], active: true, lastRunAt: 'Jun 9 8:00am', nextRunAt: 'Jun 16 8:00am' },
  { id: 2, reportType: 'Daily snapshot', client: 'All clients', cronExpression: '0 0 8 * * MON-FRI', recipients: ['priya.k@orbit.io'], active: true, lastRunAt: 'Jun 11 8:01am', nextRunAt: 'Jun 12 8:00am' },
  { id: 3, reportType: 'Client backlog', client: 'Meridian Bank', cronExpression: '0 0 9 * * FRI', recipients: ['amit.s@orbit.io', 'rajesh.n@orbit.io'], active: true, lastRunAt: 'Jun 7 9:00am', nextRunAt: 'Jun 14 9:00am' },
  { id: 4, reportType: 'Executive summary', client: 'All clients', cronExpression: '0 0 8 1 * *', recipients: ['rajesh.n@orbit.io'], active: false, lastRunAt: 'Jun 1 8:00am', nextRunAt: 'Jul 1 8:00am' },
]

const CRON_LABELS: Record<string, string> = {
  '0 0 8 * * MON': 'Every Monday 8am',
  '0 0 8 * * MON-FRI': 'Weekdays 8am',
  '0 0 9 * * FRI': 'Every Friday 9am',
  '0 0 8 1 * *': 'Monthly (1st) 8am',
}

export function ReportSchedulesPage() {
  const C = useC()
  const [page, setPage] = useState(0)
  const [showModal, setShowModal] = useState(false)
  const [schedules, setSchedules] = useState(SCHEDULES)
  const [form, setForm] = useState({ reportType: 'Weekly delivery', client: 'All clients', cron: '0 0 8 * * MON', recipients: '' })

  const PAGE_SIZE = 10

  const toggleActive = (id: number) => {
    setSchedules(s => s.map(r => r.id === id ? { ...r, active: !r.active } : r))
  }

  const deleteSchedule = (id: number) => {
    setSchedules(s => s.filter(r => r.id !== id))
  }

  return (
    <div style={{ padding: '22px 24px' }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: 18 }}>
        <div>
          <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>Report schedules</div>
          <div style={{ fontSize: 12, color: C.sub, marginTop: 3 }}>Automated report delivery configuration</div>
        </div>
        <button
          onClick={() => setShowModal(true)}
          style={{ fontSize: 12, padding: '6px 14px', borderRadius: 7, border: 'none', background: C.indigo, color: '#fff', cursor: 'pointer', fontWeight: 500 }}>
          + New schedule
        </button>
      </div>

      {/* Summary */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 10, marginBottom: 20 }}>
        {[
          ['Active schedules', schedules.filter(s => s.active).length, C.green],
          ['Paused', schedules.filter(s => !s.active).length, C.muted],
          ['Next run', 'Jun 12 8:00am', C.indigo],
        ].map(([l, v, c]) => (
          <div key={String(l)} style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 10, padding: '12px 14px' }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: String(c) }}>{v}</div>
            <div style={{ fontSize: 11, color: C.sub, marginTop: 2 }}>{l}</div>
          </div>
        ))}
      </div>

      {/* Table */}
      <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden' }}>
        <table style={{ width: '100%', fontSize: 12 }}>
          <THead cols={['Report type', 'Client', 'Frequency', 'Recipients', 'Last run', 'Next run', 'Status', '']} />
          <tbody>
            {schedules.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE).map((s, i) => (
              <tr key={s.id} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none', background: s.active ? C.white : C.canvas }}>
                <td style={{ padding: '10px 12px', fontWeight: 500, color: C.text }}>{s.reportType}</td>
                <td style={{ padding: '10px 12px', color: C.sub }}>{s.client}</td>
                <td style={{ padding: '10px 12px', fontFamily: 'monospace', fontSize: 11, color: C.text }}>{CRON_LABELS[s.cronExpression] || s.cronExpression}</td>
                <td style={{ padding: '10px 12px', color: C.sub }}>
                  <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                    {s.recipients.map(r => (
                      <span key={r} style={{ fontSize: 10, padding: '1px 6px', borderRadius: 4, background: C.indigoPale, color: C.purpleDeep }}>{r.split('@')[0]}</span>
                    ))}
                  </div>
                </td>
                <td style={{ padding: '10px 12px', color: C.muted, fontSize: 11 }}>{s.lastRunAt}</td>
                <td style={{ padding: '10px 12px', color: s.active ? C.text : C.muted, fontSize: 11 }}>{s.active ? s.nextRunAt : '—'}</td>
                <td style={{ padding: '10px 12px' }}>
                  <Badge level={s.active ? 'healthy' : 'neutral'} label={s.active ? 'Active' : 'Paused'} />
                </td>
                <td style={{ padding: '10px 12px' }}>
                  <div style={{ display: 'flex', gap: 5 }}>
                    <button
                      onClick={() => toggleActive(s.id)}
                      style={{ fontSize: 11, padding: '3px 9px', borderRadius: 5, cursor: 'pointer', border: `1px solid ${C.border}`, background: 'transparent', color: C.sub }}>
                      {s.active ? 'Pause' : 'Resume'}
                    </button>
                    <button
                      onClick={() => deleteSchedule(s.id)}
                      style={{ fontSize: 11, padding: '3px 9px', borderRadius: 5, cursor: 'pointer', border: `1px solid ${C.redPale}`, background: C.redPale, color: C.red }}>
                      Delete
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <Pagination page={page} totalPages={Math.ceil(schedules.length / PAGE_SIZE)} onPageChange={setPage} />
      </div>

      {showModal && (
        <Modal title="New report schedule" onClose={() => setShowModal(false)}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div>
              <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Report type</div>
              <Select
                options={['Weekly delivery', 'Daily snapshot', 'Client backlog', 'Executive summary', 'Bug summary']}
                value={form.reportType}
                onChange={e => setForm(f => ({ ...f, reportType: e.target.value }))}
                style={{ width: '100%' }}
              />
            </div>
            <div>
              <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Client</div>
              <Select
                options={['All clients', 'Nexus Corp', 'Sigma Telecom', 'Meridian Bank', 'Apex Fintech', 'Polaris Retail']}
                value={form.client}
                onChange={e => setForm(f => ({ ...f, client: e.target.value }))}
                style={{ width: '100%' }}
              />
            </div>
            <div>
              <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Frequency</div>
              <Select
                options={[
                  { v: '0 0 8 * * MON', l: 'Every Monday 8am' },
                  { v: '0 0 8 * * MON-FRI', l: 'Weekdays 8am' },
                  { v: '0 0 9 * * FRI', l: 'Every Friday 9am' },
                  { v: '0 0 8 1 * *', l: 'Monthly (1st) 8am' },
                  { v: 'custom', l: 'Custom cron…' },
                ]}
                value={form.cron}
                onChange={e => setForm(f => ({ ...f, cron: e.target.value }))}
                style={{ width: '100%' }}
              />
            </div>
            <div>
              <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Recipients (comma-separated emails)</div>
              <input
                value={form.recipients}
                onChange={e => setForm(f => ({ ...f, recipients: e.target.value }))}
                placeholder="priya.k@orbit.io, amit.s@orbit.io"
                style={{ fontSize: 12, padding: '7px 10px', borderRadius: 7, border: `1px solid ${C.border}`, outline: 'none', width: '100%' }}
              />
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <button onClick={() => setShowModal(false)}
                style={{ flex: 1, padding: '9px', borderRadius: 7, border: `1px solid ${C.border}`, background: C.canvas, fontSize: 13, cursor: 'pointer', color: C.sub }}>
                Cancel
              </button>
              <button
                onClick={() => {
                  setSchedules(s => [...s, { id: Date.now(), ...form, cronExpression: form.cron, recipients: form.recipients.split(',').map(r => r.trim()).filter(Boolean), active: true, lastRunAt: '—', nextRunAt: 'Scheduled' }])
                  setShowModal(false)
                }}
                style={{ flex: 1, padding: '9px', borderRadius: 7, border: 'none', background: C.indigo, color: '#fff', fontSize: 13, fontWeight: 600, cursor: 'pointer' }}>
                Create schedule
              </button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}
