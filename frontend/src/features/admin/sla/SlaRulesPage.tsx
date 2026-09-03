import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useC } from '../../../design/ThemeContext'
import { api } from '../../../api/client'

const SEVERITIES = ['P0', 'P1', 'P2', 'P3']
const SEV_LABELS: Record<string, string> = {
  P0: 'P0 — Critical / Customer outage',
  P1: 'P1 — High impact',
  P2: 'P2 — Medium',
  P3: 'P3 — Low / Queued',
}

function SevChip({ sev, C }: { sev: string; C: any }) {
  const styles: Record<string, { bg: string; fg: string }> = {
    P0: { bg: C.redPale,   fg: C.redDeep },
    P1: { bg: C.amberPale, fg: C.amberDeep },
    P2: { bg: C.amberPale, fg: C.amberDeep },
    P3: { bg: C.canvas,    fg: C.sub },
  }
  const s = styles[sev] ?? { bg: C.canvas, fg: C.sub }
  return <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 4, fontWeight: 700, background: s.bg, color: s.fg }}>{sev}</span>
}

type Rule = { id: number; client: string; clientId: number | null; sev: string; resp: string; res: string; wk: boolean }

function parseHours(s: string) { return parseFloat(s.replace('h', '').trim()) }

function RuleModal({ initial, clients, onSave, onClose, title }: {
  initial?: Partial<Rule>; clients: any[]; onSave: (body: any) => Promise<any>; onClose: () => void; title: string
}) {
  const C = useC()
  const [clientId, setClientId] = useState<string>(initial?.clientId != null ? String(initial.clientId) : '')
  const [sev,      setSev]      = useState(initial?.sev  ?? 'P2')
  const [resp,     setResp]     = useState(initial?.resp ?? '')
  const [res,      setRes]      = useState(initial?.res  ?? '')
  const [wk,       setWk]       = useState(initial?.wk   ?? false)
  const [saving,   setSaving]   = useState(false)
  const [err,      setErr]      = useState('')

  const respH = parseHours(resp)
  const resH  = parseHours(res)
  const valid  = !isNaN(respH) && !isNaN(resH) && respH > 0 && resH > respH

  const save = async () => {
    if (!valid) { setErr('At-risk threshold must be less than the breach threshold.'); return }
    setSaving(true); setErr('')
    try { await onSave({ clientId: clientId !== '' ? Number(clientId) : null, sev, resp, res, wk }); onClose() }
    catch { setErr('Save failed — check values and try again.') }
    finally { setSaving(false) }
  }

  const inp: React.CSSProperties = { fontSize: 13, padding: '7px 10px', borderRadius: 7, border: `1px solid ${C.border}`, outline: 'none', width: '100%', boxSizing: 'border-box', color: C.text, background: C.white }

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,22,41,.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
      <div style={{ background: C.white, borderRadius: 14, width: 500, boxShadow: '0 20px 60px rgba(0,0,0,.3)' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '16px 20px', borderBottom: `1px solid ${C.border}` }}>
          <div style={{ fontSize: 15, fontWeight: 700, color: C.text }}>{title}</div>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 18, color: C.muted }}>✕</button>
        </div>
        <div style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div>
            <label style={{ display: 'block', fontSize: 12, fontWeight: 600, marginBottom: 5, color: C.text }}>Client</label>
            <select value={clientId} onChange={e => setClientId(e.target.value)} style={inp}>
              <option value=''>Global default (applies to all clients)</option>
              {clients.map((c: any) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
            {clientId === '' && <p style={{ margin: '4px 0 0', fontSize: 11, color: C.sub }}>Applies to all clients unless a client-specific override exists.</p>}
          </div>
          <div>
            <label style={{ display: 'block', fontSize: 12, fontWeight: 600, marginBottom: 5, color: C.text }}>Severity</label>
            <select value={sev} onChange={e => setSev(e.target.value)} style={inp}>
              {SEVERITIES.map(s => <option key={s} value={s}>{SEV_LABELS[s]}</option>)}
            </select>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div>
              <label style={{ display: 'block', fontSize: 12, fontWeight: 600, marginBottom: 5, color: C.text }}>At-risk threshold</label>
              <input value={resp} onChange={e => setResp(e.target.value)} placeholder='e.g. 2h' style={inp} />
              <p style={{ margin: '4px 0 0', fontSize: 11, color: C.sub }}>Flagged "At risk" after this many hours</p>
            </div>
            <div>
              <label style={{ display: 'block', fontSize: 12, fontWeight: 600, marginBottom: 5, color: C.text }}>Breach threshold</label>
              <input value={res} onChange={e => setRes(e.target.value)} placeholder='e.g. 4h' style={inp} />
              <p style={{ margin: '4px 0 0', fontSize: 11, color: C.sub }}>Marked "Breached" after this many hours</p>
            </div>
          </div>
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
            <input type='checkbox' id='wk-modal' checked={wk} onChange={e => setWk(e.target.checked)} style={{ marginTop: 2, flexShrink: 0 }} />
            <label htmlFor='wk-modal' style={{ fontSize: 12, color: C.sub, lineHeight: 1.5 }}>
              Include weekends — use calendar hours. Unchecked = business hours only (Mon–Fri, 9am–6pm IST).
            </label>
          </div>
          {err && <p style={{ margin: 0, fontSize: 12, color: C.red, background: C.redPale, padding: '8px 12px', borderRadius: 7 }}>{err}</p>}
          <button disabled={!valid || saving} onClick={save}
            style={{ padding: 10, borderRadius: 7, border: 'none', background: C.teal, color: '#fff', fontSize: 13, fontWeight: 700, cursor: valid && !saving ? 'pointer' : 'not-allowed', opacity: valid && !saving ? 1 : 0.55 }}>
            {saving ? 'Saving…' : 'Save rule'}
          </button>
        </div>
      </div>
    </div>
  )
}

export function SlaRulesPage() {
  const C  = useC()
  const qc = useQueryClient()
  const [showAdd,     setShowAdd]  = useState(false)
  const [editRule,    setEditRule] = useState<Rule | null>(null)
  const [recomputing, setRec]      = useState(false)
  const [recMsg,      setRecMsg]   = useState('')
  const [slaField,    setSlaField] = useState<string | null>(null)
  const [savingJira,  setSavingJ]  = useState(false)

  const { data: rawRules = [], isLoading, error } = useQuery({
    queryKey: ['sla-rules'],
    queryFn:  () => api.get('/admin/sla-rules').then(r => r.data as Rule[]),
  })

  const { data: clientList = [] } = useQuery({
    queryKey: ['clients-sla'],
    queryFn:  () => api.get('/clients').then(r => r.data as any[]),
  })

  const { data: jiraConfig } = useQuery({
    queryKey: ['jira-config'],
    queryFn:  () => api.get('/jira-sync/config').then(r => r.data),
  })

  const refresh = () => qc.invalidateQueries({ queryKey: ['sla-rules'] })
  const create  = (body: any) => api.post('/admin/sla-rules', body).then(refresh)
  const update  = (id: number, body: any) => api.put(`/admin/sla-rules/${id}`, body).then(refresh)
  const remove  = (id: number) => api.delete(`/admin/sla-rules/${id}`).then(refresh)

  const recompute = async () => {
    setRec(true); setRecMsg('')
    try   { await api.post('/admin/sla-rules/recompute'); setRecMsg('SLA status recomputed for all open bugs.') }
    catch { setRecMsg('Recompute failed — check server logs.') }
    finally { setRec(false) }
  }

  const saveJiraSlaField = async () => {
    setSavingJ(true)
    try { await api.put('/jira-sync/config', { slaField: effectiveSlaField }); qc.invalidateQueries({ queryKey: ['jira-config'] }) }
    finally { setSavingJ(false) }
  }

  const effectiveSlaField = slaField ?? jiraConfig?.slaField ?? ''
  const rules    = rawRules as Rule[]
  const defaults  = rules.filter(r => r.client === 'Global')
  const overrides = rules.filter(r => r.client !== 'Global')

  if (isLoading) return <div style={{ padding: 40, color: C.sub }}>Loading…</div>
  if (error)     return <div style={{ padding: 40, color: C.red }}>Failed to load SLA rules.</div>

  const TH = { padding: '8px 14px', fontSize: 11, fontWeight: 800, color: C.muted, textTransform: 'uppercase' as const, letterSpacing: 0.5, textAlign: 'left' as const, background: C.canvas }
  const TD = { padding: '10px 14px', fontSize: 13, color: C.text }
  const btnEdit = { fontSize: 11, padding: '3px 10px', borderRadius: 6, cursor: 'pointer' as const, border: `1px solid ${C.border}`, background: 'transparent', color: C.sub }
  const btnDel  = { fontSize: 11, padding: '3px 10px', borderRadius: 6, cursor: 'pointer' as const, border: `1px solid ${C.redPale}`, background: C.redPale, color: C.red }

  return (
    <div style={{ padding: '22px 24px', maxWidth: 960 }}>

      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: 20 }}>
        <div>
          <div style={{ fontSize: 20, fontWeight: 800, color: C.text, letterSpacing: -0.4 }}>SLA rules</div>
          <div style={{ fontSize: 13, color: C.sub, marginTop: 3 }}>Global defaults apply to all clients. Client overrides take precedence.</div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button onClick={recompute} disabled={recomputing}
            style={{ fontSize: 12, padding: '7px 14px', borderRadius: 7, border: `1px solid ${C.border}`, background: C.white, color: C.teal, cursor: 'pointer', fontWeight: 700 }}>
            {recomputing ? 'Recomputing…' : '⟳ Recompute SLA'}
          </button>
          <button onClick={() => setShowAdd(true)}
            style={{ fontSize: 12, padding: '7px 14px', borderRadius: 7, border: 'none', background: C.teal, color: '#fff', cursor: 'pointer', fontWeight: 700 }}>
            + Add rule
          </button>
        </div>
      </div>

      {recMsg && (
        <div style={{ marginBottom: 14, padding: '10px 14px', borderRadius: 8, background: C.greenPale, color: C.green, fontSize: 13, fontWeight: 700 }}>{recMsg}</div>
      )}

      {/* Precedence info */}
      <div style={{ padding: '12px 16px', background: C.indigoPale, border: `1px solid ${C.borderMed}`, borderRadius: 10, fontSize: 12, color: C.tealDeep, lineHeight: 1.7, marginBottom: 24 }}>
        <strong>Precedence:</strong> Client override → Global default &nbsp;·&nbsp;
        <strong>On track</strong>: elapsed &lt; at-risk threshold &nbsp;·&nbsp;
        <strong>At risk</strong>: elapsed ≥ at-risk &nbsp;·&nbsp;
        <strong>Breached</strong>: elapsed ≥ breach threshold<br />
        SLA timer starts at issue creation. Business hours = Mon–Fri 9am–6pm IST (unless weekends enabled).
      </div>

      {/* Global defaults */}
      <div style={{ marginBottom: 24 }}>
        <div style={{ fontSize: 14, fontWeight: 800, color: C.text, marginBottom: 10 }}>Global defaults</div>
        <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 10, overflow: 'hidden' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr>{['Severity','At-risk after','Breach after','Weekends',''].map(h => <th key={h} style={TH}>{h}</th>)}</tr>
            </thead>
            <tbody>
              {defaults.length === 0 && (
                <tr><td colSpan={5} style={{ padding: 20, textAlign: 'center', color: C.muted, fontSize: 13 }}>
                  No global defaults. Add P0–P3 rules to enable SLA tracking.
                </td></tr>
              )}
              {defaults.map((r, i) => (
                <tr key={r.id} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none' }}>
                  <td style={TD}><SevChip sev={r.sev} C={C} /></td>
                  <td style={TD}>{r.resp}</td>
                  <td style={TD}>{r.res}</td>
                  <td style={{ ...TD, color: r.wk ? C.green : C.muted }}>{r.wk ? 'Yes' : 'No (biz hours)'}</td>
                  <td style={{ ...TD, textAlign: 'right' }}>
                    <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                      <button onClick={() => setEditRule(r)} style={btnEdit}>Edit</button>
                      <button onClick={() => remove(r.id)} style={btnDel}>Delete</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Client overrides */}
      <div style={{ marginBottom: 28 }}>
        <div style={{ fontSize: 14, fontWeight: 800, color: C.text, marginBottom: 10 }}>Client overrides</div>
        <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 10, overflow: 'hidden' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr>{['Client','Severity','At-risk after','Breach after','Weekends',''].map(h => <th key={h} style={TH}>{h}</th>)}</tr>
            </thead>
            <tbody>
              {overrides.length === 0 && (
                <tr><td colSpan={6} style={{ padding: 20, textAlign: 'center', color: C.muted, fontSize: 13 }}>
                  No client overrides. Global defaults apply to all clients.
                </td></tr>
              )}
              {overrides.map((r, i) => (
                <tr key={r.id} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none' }}>
                  <td style={{ ...TD, fontWeight: 700 }}>{r.client}</td>
                  <td style={TD}><SevChip sev={r.sev} C={C} /></td>
                  <td style={TD}>{r.resp}</td>
                  <td style={TD}>{r.res}</td>
                  <td style={{ ...TD, color: r.wk ? C.green : C.muted }}>{r.wk ? 'Yes' : 'No'}</td>
                  <td style={{ ...TD, textAlign: 'right' }}>
                    <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                      <button onClick={() => setEditRule(r)} style={btnEdit}>Edit</button>
                      <button onClick={() => remove(r.id)} style={btnDel}>Delete</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Jira SLA field */}
      <div style={{ padding: '18px 20px', border: `1px solid ${C.border}`, borderRadius: 10, background: C.white }}>
        <div style={{ fontSize: 14, fontWeight: 800, color: C.text, marginBottom: 6 }}>Jira SLA field <span style={{ fontWeight: 400, color: C.sub, fontSize: 12 }}>(optional — Jira Service Management only)</span></div>
        <p style={{ margin: '0 0 12px', fontSize: 12, color: C.sub, lineHeight: 1.6 }}>
          For Jira Service Management (JSM), enter the custom field name that carries SLA data (e.g. <code>customfield_10020</code>).
          When set, Orbit reads SLA status directly from Jira during sync. When blank, SLA is computed from the rules above.
        </p>
        <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
          <input
            value={effectiveSlaField}
            onChange={e => setSlaField(e.target.value)}
            placeholder='Leave blank to use computed rules (recommended for Jira Software)'
            style={{ fontSize: 13, padding: '8px 12px', borderRadius: 8, border: `1px solid ${C.border}`, outline: 'none', width: 400, color: C.text }}
          />
          <button onClick={saveJiraSlaField} disabled={savingJira}
            style={{ padding: '8px 16px', borderRadius: 8, border: 'none', background: C.teal, color: '#fff', fontSize: 13, fontWeight: 700, cursor: 'pointer' }}>
            {savingJira ? 'Saving…' : 'Save'}
          </button>
        </div>
      </div>

      {/* Modals */}
      {showAdd && (
        <RuleModal title='Add SLA rule' clients={clientList} onSave={create} onClose={() => setShowAdd(false)} />
      )}
      {editRule && (
        <RuleModal title='Edit SLA rule' initial={editRule} clients={clientList} onSave={body => update(editRule.id, body)} onClose={() => setEditRule(null)} />
      )}
    </div>
  )
}
