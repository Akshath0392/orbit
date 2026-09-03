import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useC } from '../../../design/ThemeContext'
import { Tabs } from '../../../design/components/Tabs'
import { api } from '../../../api/client'
import { fmtDateTimeFull } from '../../../lib/datetime'

const PHASES = ['FSD', 'DEV', 'QA', 'UAT', 'PROD']
const TRIGGER_LABELS: Record<string, string> = {
  PRE_DUE: 'Pre-due reminder',
  DDAY: 'D-Day prompt',
  OVERDUE: 'Overdue loop',
  DIGEST: 'Daily digest',
}

export function AlertRulesPage() {
  const C = useC()
  const qc = useQueryClient()
  const [tab, setTab] = useState('Rules')
  const [toast, setToast] = useState('')
  const [toastOk, setToastOk] = useState(true)

  const showToast = (msg: string, ok = true) => {
    setToast(msg); setToastOk(ok); setTimeout(() => setToast(''), 3000)
  }

  const { data: rules = [] } = useQuery({
    queryKey: ['notif-rules'],
    queryFn: () => api.get('/admin/alert-rules/rules').then(r => r.data),
  })

  const { data: escalation = [] } = useQuery({
    queryKey: ['escalation-config'],
    queryFn: () => api.get('/admin/alert-rules/escalation').then(r => r.data),
  })

  const { data: spocs = [] } = useQuery({
    queryKey: ['global-spocs'],
    queryFn: () => api.get('/admin/alert-rules/spocs').then(r => r.data),
  })

  const { data: events = [] } = useQuery({
    queryKey: ['notif-events'],
    queryFn: () => api.get('/admin/alert-rules/events?size=50').then(r => r.data?.content ?? r.data),
  })

  const toggleRule = async (id: number) => {
    try {
      await api.put(`/admin/alert-rules/rules/${id}/toggle`)
      qc.invalidateQueries({ queryKey: ['notif-rules'] })
    } catch { showToast('Failed to toggle rule', false) }
  }

  const saveEscalation = async (id: number, patch: object) => {
    try {
      await api.put(`/admin/alert-rules/escalation/${id}`, patch)
      qc.invalidateQueries({ queryKey: ['escalation-config'] })
      showToast('Escalation config saved')
    } catch { showToast('Save failed', false) }
  }

  const saveSpoc = async (id: number, patch: object) => {
    try {
      await api.put(`/admin/alert-rules/spocs/${id}`, patch)
      qc.invalidateQueries({ queryKey: ['global-spocs'] })
      showToast('SPOC saved')
    } catch { showToast('Save failed', false) }
  }

  return (
    <div style={{ padding: '22px 24px' }}>
      {toast && (
        <div style={{ position: 'fixed', bottom: 24, right: 24, zIndex: 9999, padding: '10px 18px', borderRadius: 8, background: toastOk ? C.greenDeep : C.red, color: '#fff', fontSize: 13, fontWeight: 500, boxShadow: '0 8px 24px rgba(0,0,0,.18)' }}>
          {toast}
        </div>
      )}

      <div style={{ marginBottom: 20 }}>
        <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>Notification rules</div>
        <div style={{ fontSize: 12, color: C.sub, marginTop: 3 }}>Configure when and how orbit sends delivery reminders and escalations</div>
      </div>

      <Tabs items={['Rules', 'Escalation matrix', 'Global SPOCs', 'Event log']} active={tab} onChange={setTab} />

      {tab === 'Rules' && <RulesTab rules={rules} onToggle={toggleRule} C={C} />}
      {tab === 'Escalation matrix' && <EscalationTab rows={escalation} onSave={saveEscalation} C={C} />}
      {tab === 'Global SPOCs' && <SpocsTab spocs={spocs} onSave={saveSpoc} C={C} />}
      {tab === 'Event log' && <EventLogTab events={events} C={C} />}
    </div>
  )
}

// ── Rules tab ──────────────────────────────────────────────────────────────────

function RulesTab({ rules, onToggle, C }: { rules: any[]; onToggle: (id: number) => void; C: any }) {
  const grouped: Record<string, any[]> = {}
  for (const r of rules) {
    const key = r.triggerType || 'OTHER'
    if (!grouped[key]) grouped[key] = []
    grouped[key].push(r)
  }

  return (
    <div style={{ marginTop: 20 }}>
      {Object.entries(grouped).map(([type, group]) => (
        <div key={type} style={{ marginBottom: 24 }}>
          <div style={{ fontSize: 11, fontWeight: 600, color: C.sub, letterSpacing: 0.5, textTransform: 'uppercase', marginBottom: 8 }}>
            {TRIGGER_LABELS[type] || type}
          </div>
          <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 10, overflow: 'hidden' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
              <thead>
                <tr style={{ background: C.canvas }}>
                  {['Rule', 'Role', 'Phase', 'Offset', 'Time', 'Enabled'].map(h => (
                    <th key={h} style={{ padding: '8px 14px', textAlign: 'left', fontSize: 10, fontWeight: 600, color: C.sub, letterSpacing: 0.4, borderBottom: `1px solid ${C.border}` }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {group.map((r: any, i: number) => (
                  <tr key={r.id} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none' }}>
                    <td style={{ padding: '9px 14px', color: C.text, fontWeight: 500 }}>{r.ruleName}</td>
                    <td style={{ padding: '9px 14px', color: C.sub }}>{r.role || '—'}</td>
                    <td style={{ padding: '9px 14px' }}>
                      {r.phase ? <span style={{ background: C.indigoPale, color: C.indigo, padding: '2px 7px', borderRadius: 4, fontSize: 11, fontWeight: 600 }}>{r.phase}</span> : <span style={{ color: C.muted }}>All</span>}
                    </td>
                    <td style={{ padding: '9px 14px', color: C.sub }}>{r.offsetDays ? `T-${r.offsetDays}` : 'D-Day'}</td>
                    <td style={{ padding: '9px 14px', color: C.sub, fontFamily: 'monospace' }}>{r.triggerTime}</td>
                    <td style={{ padding: '9px 14px' }}>
                      <button
                        onClick={() => onToggle(r.id)}
                        style={{
                          padding: '3px 10px', borderRadius: 5, fontSize: 11, fontWeight: 600,
                          border: 'none', cursor: 'pointer',
                          background: r.enabled ? C.greenDeep : C.border,
                          color: r.enabled ? '#fff' : C.sub,
                        }}
                      >
                        {r.enabled ? 'On' : 'Off'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ))}
    </div>
  )
}

// ── Escalation matrix tab ──────────────────────────────────────────────────────

function EscalationTab({ rows, onSave, C }: { rows: any[]; onSave: (id: number, p: object) => void; C: any }) {
  const [editing, setEditing] = useState<Record<number, any>>({})

  const startEdit = (r: any) => setEditing(prev => ({
    ...prev, [r.id]: { phaseSpocEmail: r.phaseSpocEmail || '', phaseSpocName: r.phaseSpocName || '', reEscalationHours: r.reEscalationHours, deliverySpocEnabled: r.deliverySpocEnabled }
  }))
  const cancelEdit = (id: number) => setEditing(prev => { const n = { ...prev }; delete n[id]; return n })

  return (
    <div style={{ marginTop: 20 }}>
      <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 10, overflow: 'hidden' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
          <thead>
            <tr style={{ background: C.canvas }}>
              {['Role', 'Phase', 'Phase SPOC email', 'Phase SPOC name', 'Re-escalate (hrs)', 'Delivery SPOC', ''].map(h => (
                <th key={h} style={{ padding: '8px 14px', textAlign: 'left', fontSize: 10, fontWeight: 600, color: C.sub, letterSpacing: 0.4, borderBottom: `1px solid ${C.border}` }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((r: any, i: number) => {
              const ed = editing[r.id]
              return (
                <tr key={r.id} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none' }}>
                  <td style={{ padding: '9px 14px', fontWeight: 600, color: C.text }}>{r.role}</td>
                  <td style={{ padding: '9px 14px' }}>
                    <span style={{ background: C.indigoPale, color: C.indigo, padding: '2px 7px', borderRadius: 4, fontSize: 11, fontWeight: 600 }}>{r.phase}</span>
                  </td>
                  <td style={{ padding: '9px 14px' }}>
                    {ed ? <input value={ed.phaseSpocEmail} onChange={e => setEditing(p => ({ ...p, [r.id]: { ...p[r.id], phaseSpocEmail: e.target.value } }))} style={inp(C)} placeholder="spoc@company.com" /> : <span style={{ color: C.text }}>{r.phaseSpocEmail || <span style={{ color: C.muted }}>Not set</span>}</span>}
                  </td>
                  <td style={{ padding: '9px 14px' }}>
                    {ed ? <input value={ed.phaseSpocName} onChange={e => setEditing(p => ({ ...p, [r.id]: { ...p[r.id], phaseSpocName: e.target.value } }))} style={inp(C)} placeholder="Name" /> : <span style={{ color: C.text }}>{r.phaseSpocName || '—'}</span>}
                  </td>
                  <td style={{ padding: '9px 14px' }}>
                    {ed ? <input type="number" value={ed.reEscalationHours} onChange={e => setEditing(p => ({ ...p, [r.id]: { ...p[r.id], reEscalationHours: Number(e.target.value) } }))} style={{ ...inp(C), width: 60 }} /> : <span style={{ color: C.text }}>{r.reEscalationHours}h</span>}
                  </td>
                  <td style={{ padding: '9px 14px' }}>
                    {ed ? (
                      <button onClick={() => setEditing(p => ({ ...p, [r.id]: { ...p[r.id], deliverySpocEnabled: !p[r.id].deliverySpocEnabled } }))}
                        style={{ padding: '2px 8px', borderRadius: 4, border: 'none', cursor: 'pointer', fontSize: 11, background: ed.deliverySpocEnabled ? C.greenDeep : C.border, color: ed.deliverySpocEnabled ? '#fff' : C.sub }}>
                        {ed.deliverySpocEnabled ? 'Yes' : 'No'}
                      </button>
                    ) : <span style={{ color: r.deliverySpocEnabled ? C.green : C.muted }}>{r.deliverySpocEnabled ? 'Yes' : 'No'}</span>}
                  </td>
                  <td style={{ padding: '9px 14px' }}>
                    <div style={{ display: 'flex', gap: 6 }}>
                      {ed ? (
                        <>
                          <button onClick={() => { onSave(r.id, ed); cancelEdit(r.id) }} style={btn(C, true)}>Save</button>
                          <button onClick={() => cancelEdit(r.id)} style={btn(C, false)}>Cancel</button>
                        </>
                      ) : (
                        <button onClick={() => startEdit(r)} style={btn(C, false)}>Edit</button>
                      )}
                    </div>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}

// ── Global SPOCs tab ───────────────────────────────────────────────────────────

function SpocsTab({ spocs, onSave, C }: { spocs: any[]; onSave: (id: number, p: object) => void; C: any }) {
  const [editing, setEditing] = useState<Record<number, any>>({})
  const LABELS: Record<string, string> = {
    DELIVERY_SPOC: 'Delivery SPOC',
    SOLUTIONS_SPOC: 'Solutions SPOC',
    ENG_MANAGER: 'Engineering Manager',
  }
  const DESCS: Record<string, string> = {
    DELIVERY_SPOC: 'Notified on every phase miss across all projects',
    SOLUTIONS_SPOC: 'Notified on FSD closure delays; receives D-Day proactive alert',
    ENG_MANAGER: 'Receives daily digest for Dev + Prod phases; escalation target for TL/Developer delays',
  }

  return (
    <div style={{ marginTop: 20, display: 'flex', flexDirection: 'column', gap: 16 }}>
      {spocs.map((s: any) => {
        const ed = editing[s.id]
        return (
          <div key={s.id} style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 10, padding: '16px 20px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
              <div>
                <div style={{ fontSize: 14, fontWeight: 600, color: C.text }}>{LABELS[s.spocType] || s.spocType}</div>
                <div style={{ fontSize: 11, color: C.sub, marginTop: 2 }}>{DESCS[s.spocType]}</div>
              </div>
              {!ed && <button onClick={() => setEditing(p => ({ ...p, [s.id]: { email: s.email || '', name: s.name || '', slackUserId: s.slackUserId || '' } }))} style={btn(C, false)}>Edit</button>}
            </div>
            {ed ? (
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12, marginTop: 8 }}>
                <Field label="Email">
                  <input value={ed.email} onChange={e => setEditing(p => ({ ...p, [s.id]: { ...p[s.id], email: e.target.value } }))} style={inp(C)} placeholder="email@company.com" />
                </Field>
                <Field label="Name">
                  <input value={ed.name} onChange={e => setEditing(p => ({ ...p, [s.id]: { ...p[s.id], name: e.target.value } }))} style={inp(C)} placeholder="Full name" />
                </Field>
                <Field label="Slack user ID (optional)">
                  <input value={ed.slackUserId} onChange={e => setEditing(p => ({ ...p, [s.id]: { ...p[s.id], slackUserId: e.target.value } }))} style={inp(C)} placeholder="U0123ABCD" />
                </Field>
                <div style={{ gridColumn: '1/-1', display: 'flex', gap: 8 }}>
                  <button onClick={() => { onSave(s.id, ed); setEditing(p => { const n = { ...p }; delete n[s.id]; return n }) }} style={btn(C, true)}>Save</button>
                  <button onClick={() => setEditing(p => { const n = { ...p }; delete n[s.id]; return n })} style={btn(C, false)}>Cancel</button>
                </div>
              </div>
            ) : (
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 16 }}>
                <Stat label="Email" value={s.email || '—'} C={C} />
                <Stat label="Name" value={s.name || '—'} C={C} />
                <Stat label="Slack user ID" value={s.slackUserId || '—'} C={C} />
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}

// ── Event log tab ──────────────────────────────────────────────────────────────

function EventLogTab({ events, C }: { events: any[]; C: any }) {
  const EVENT_COLORS: Record<string, string> = {
    T2_REMINDER: C.indigo,
    T1_REMINDER: C.indigo,
    DDAY_PROMPT: C.amber,
    ESCALATION: C.red,
    OVERDUE_LOOP: C.red,
    DIGEST: C.green,
  }

  return (
    <div style={{ marginTop: 20, background: C.white, border: `1px solid ${C.border}`, borderRadius: 10, overflow: 'hidden' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
        <thead>
          <tr style={{ background: C.canvas }}>
            {['Sent at', 'Event', 'Project', 'Phase', 'Recipient', 'Response'].map(h => (
              <th key={h} style={{ padding: '8px 14px', textAlign: 'left', fontSize: 10, fontWeight: 600, color: C.sub, letterSpacing: 0.4, borderBottom: `1px solid ${C.border}` }}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {events.length === 0 && (
            <tr><td colSpan={6} style={{ padding: 24, textAlign: 'center', color: C.muted }}>No notification events yet</td></tr>
          )}
          {events.map((e: any, i: number) => (
            <tr key={e.id} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none' }}>
              <td style={{ padding: '9px 14px', color: C.sub, fontFamily: 'monospace', fontSize: 11 }}>{fmtTime(e.sentAt)}</td>
              <td style={{ padding: '9px 14px' }}>
                <span style={{ background: EVENT_COLORS[e.eventType] || C.border, color: '#fff', padding: '2px 7px', borderRadius: 4, fontSize: 10, fontWeight: 600 }}>{e.eventType}</span>
              </td>
              <td style={{ padding: '9px 14px', color: C.text }}>{e.project || '—'}</td>
              <td style={{ padding: '9px 14px' }}>
                {e.phase ? <span style={{ background: C.indigoPale, color: C.indigo, padding: '2px 6px', borderRadius: 4, fontSize: 11 }}>{e.phase}</span> : '—'}
              </td>
              <td style={{ padding: '9px 14px', color: C.text }}>{e.recipientName || e.recipientEmail || '—'}</td>
              <td style={{ padding: '9px 14px' }}>
                {e.userResponse ? <span style={{ color: e.userResponse === 'ON_TRACK' ? C.green : e.userResponse === 'DELAYED' ? C.red : C.sub }}>{e.userResponse}</span> : <span style={{ color: C.muted }}>—</span>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

// ── Small helpers ──────────────────────────────────────────────────────────────

function Stat({ label, value, C }: { label: string; value: string; C: any }) {
  return (
    <div>
      <div style={{ fontSize: 10, color: C.muted, fontWeight: 600, letterSpacing: 0.4, textTransform: 'uppercase', marginBottom: 4 }}>{label}</div>
      <div style={{ fontSize: 13, fontWeight: 500, color: C.text }}>{value}</div>
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  const C = useC()
  return (
    <div>
      <div style={{ fontSize: 11, fontWeight: 500, color: C.sub, marginBottom: 5 }}>{label}</div>
      {children}
    </div>
  )
}

function inp(C: any) {
  return { fontSize: 12, padding: '6px 10px', borderRadius: 6, border: `1px solid ${C.border}`, outline: 'none', width: '100%', boxSizing: 'border-box' as const }
}

function btn(C: any, primary: boolean) {
  return {
    fontSize: 11, padding: '4px 10px', borderRadius: 5, cursor: 'pointer', fontWeight: 500,
    border: primary ? 'none' : `1px solid ${C.border}`,
    background: primary ? C.indigo : 'transparent',
    color: primary ? '#fff' : C.sub,
  } as React.CSSProperties
}

function fmtTime(iso: string) {
  return fmtDateTimeFull(iso)
}
