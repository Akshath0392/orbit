import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import { useC } from '../../design/ThemeContext'
import { useStore } from '../../app/store'
import { useAmClientOverview } from '../radar/am/useAmApi'
import { Breadcrumbs } from '../../design/components/Breadcrumbs'
import { fmtDateTimeFull } from '../../lib/datetime'

type Section = { key: string; enabled: boolean }

const SECTION_NAMES: Record<string, string> = {
  executiveSummary: 'Executive Summary',
  keyMetrics: 'Key Metrics',
  productionIssues: 'Production Issues (open)',
  milestones: 'Milestones — This Week / Next Week',
  commercials: 'Commercials — Mandays',
  riskRegister: 'Risk Register',
}

// Mock renderReport/reportAcctBody: paper-white Delivery Report sheet whose
// section set + order come from the configurable acct template.
export function AccountReportPage() {
  const C = useC()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { projectId } = useParams<{ projectId: string }>()
  const isAdmin = useStore(s => s.user?.role) === 'ADMIN'
  const [editOpen, setEditOpen] = useState(false)

  const { data } = useQuery({
    queryKey: ['account-detail', projectId],
    queryFn: () => api.get(`/accounts/${projectId}`).then(r => r.data),
    enabled: !!projectId,
  })
  const { data: template } = useQuery({
    queryKey: ['report-template-default', 'acct'],
    queryFn: () => api.get('/report-templates/default', { params: { scope: 'acct' } }).then(r => r.data),
  })
  const clientId: number | null = data?.client?.id ?? null
  const { data: ov } = useAmClientOverview(clientId)

  if (!data || !template) return <div style={{ padding: 40, color: '#5b6472' }}>Preparing report…</div>

  const sections: Section[] = JSON.parse(template.sections)
  const mandays = data.mandays
  const util = mandays?.purchased ? mandays.consumedPct : null
  const now = fmtDateTimeFull(new Date())
  const period = new Date().toLocaleString('en-GB', { month: 'long', year: 'numeric' })
  const rag = data.rag ?? 'Green'
  const ragWord = { Green: 'Healthy', Amber: 'Watch', Red: 'Critical' }[rag as string] ?? rag

  // paper-white regardless of app theme (mock report-sheet)
  const ink = '#1b2333', sub = '#5b6472', muted = '#8a929f', line = '#e3e7ee'
  const secHead: React.CSSProperties = { fontSize: 12.5, textTransform: 'uppercase', letterSpacing: 0.8, color: '#4a68e0', margin: '0 0 11px', borderBottom: `1px solid ${line}`, paddingBottom: 6, fontWeight: 800 }
  const th: React.CSSProperties = { padding: '7px 10px', textAlign: 'left', fontSize: 11, fontWeight: 700, color: muted, borderBottom: `1px solid ${line}` }
  const td: React.CSSProperties = { padding: '7px 10px', fontSize: 12.5, color: ink, borderBottom: `1px solid ${line}` }
  const kpi = (label: string, value: any) => (
    <div key={label} style={{ border: `1px solid ${line}`, borderRadius: 10, padding: '12px 14px', textAlign: 'center' }}>
      <div style={{ fontSize: 22, fontWeight: 900, color: ink }}>{value ?? '—'}</div>
      <div style={{ fontSize: 11, color: muted, marginTop: 3 }}>{label}</div>
    </div>
  )

  const renderSection = (key: string): React.ReactNode => {
    switch (key) {
      case 'executiveSummary':
        return <p style={{ margin: 0, fontSize: 13.5, color: sub, lineHeight: 1.65 }}>
          <b style={{ color: ink }}>{data.name}</b> is currently <b style={{ color: ink }}>{ragWord}</b> on delivery health
          with <b style={{ color: ink }}>{ov?.slaAdherencePct ?? '—'}%</b> SLA adherence
          ({ov?.slaBreached ?? 0} breached · {ov?.slaNear ?? 0} near breach · {ov?.slaMet ?? 0} met).
          {ov?.csat != null ? `CSAT stands at ${ov.csat} with ` : 'There are '}{ov?.openCrs ?? 0} open CRs and {ov?.prodOpen ?? 0} open production
          bug{(ov?.prodOpen ?? 0) === 1 ? '' : 's'}.{util != null ? ` Scope utilization is at ${util}% of sold mandays.` : ''}
        </p>
      case 'keyMetrics':
        return <div style={{ display: 'grid', gridTemplateColumns: `repeat(${ov?.csat != null ? 5 : 4},1fr)`, gap: 12 }}>
          {kpi('SLA adherence', ov?.slaAdherencePct != null ? `${ov.slaAdherencePct}%` : '—')}
          {ov?.csat != null && kpi('CSAT', ov.csat)}
          {kpi('Open CRs', ov?.openCrs)}
          {kpi('Prod bugs', ov?.prodOpen)}
          {kpi('Utilization', util != null ? `${util}%` : '—')}
        </div>
      case 'productionIssues': {
        const rows: any[] = data.productionIssues?.rows ?? []
        return rows.length === 0
          ? <p style={{ margin: 0, fontSize: 13, color: muted }}>No open production issues for this account.</p>
          : <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead><tr>{['Issue', 'Sev', 'Summary', 'Status', 'Ageing', 'Owner'].map(h => <th key={h} style={th}>{h}</th>)}</tr></thead>
              <tbody>{rows.map((r: any) => (
                <tr key={r.key}><td style={{ ...td, fontWeight: 700 }}>{r.key}</td><td style={td}>{r.severity}</td>
                  <td style={td}>{r.summary}</td><td style={td}>{r.status}</td><td style={td}>{r.ageDays ?? r.ageing ?? '—'}</td><td style={td}>{r.owner ?? '—'}</td></tr>
              ))}</tbody>
            </table>
      }
      case 'milestones': {
        const wb = data.workbench
        const rel: any[] = (data.releaseCalendar ?? [])
        return <div style={{ fontSize: 13, color: sub, lineHeight: 1.9 }}>
          <div>✔ Completed this week: <b style={{ color: ink }}>{wb?.thisWeek?.crsClosed ?? 0}</b> CRs closed · <b style={{ color: ink }}>{wb?.thisWeek?.bugsFixed ?? 0}</b> bugs fixed · <b style={{ color: ink }}>{wb?.thisWeek?.uatSignOffs ?? 0}</b> UAT sign-offs</div>
          <div>→ Next week: <b style={{ color: ink }}>{wb?.nextWeek?.goLives ?? 0}</b> go-lives · <b style={{ color: ink }}>{wb?.nextWeek?.uatCycles ?? 0}</b> UAT cycles · <b style={{ color: ink }}>{wb?.nextWeek?.signOffsDue ?? 0}</b> sign-offs due</div>
          {rel.slice(0, 4).map((r: any) => (
            <div key={r.id} style={{ fontSize: 12.5 }}>• {r.date} — {r.label} <span style={{ color: muted }}>({r.type})</span></div>
          ))}
        </div>
      }
      case 'commercials':
        return mandays?.purchased
          ? <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 12 }}>
              {kpi('Sold', mandays.purchased)}{kpi('Consumed', mandays.consumed)}
              {kpi('Remaining', mandays.purchased - mandays.consumed)}{kpi('Utilization', `${util}%`)}
            </div>
          : <p style={{ margin: 0, fontSize: 13, color: muted }}>No mandays data for this account.</p>
      case 'riskRegister': {
        const risks: any[] = data.riskRegister ?? []
        return risks.length === 0
          ? <p style={{ margin: 0, fontSize: 13, color: muted }}>No risks logged.</p>
          : <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead><tr>{['Risk', 'RAG', 'Action Owner', 'Action End', 'Jira'].map(h => <th key={h} style={th}>{h}</th>)}</tr></thead>
              <tbody>{risks.map((r: any) => (
                <tr key={r.id}><td style={{ ...td, fontWeight: 700, width: '42%' }}>{r.risk}</td><td style={td}>{r.rag ?? '—'}</td>
                  <td style={td}>{r.actionOwner ?? '—'}</td><td style={td}>{r.actionEnd ?? '—'}</td><td style={td}>{r.jiraTicket ?? '—'}</td></tr>
              ))}</tbody>
            </table>
      }
      default: return null
    }
  }

  return (
    <div style={{ padding: '22px 24px', background: C.canvas, minHeight: '100vh' }}>
      <div className="no-print" style={{ maxWidth: 900, margin: '0 auto 14px' }}>
        <Breadcrumbs items={[
          { label: 'Orbitter', to: '/radar' },
          { label: data.name, to: `/accounts/${projectId}` },
          { label: 'Delivery report' },
        ]} />
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 8 }}>
          {isAdmin && (
            <button onClick={() => setEditOpen(true)} style={{ fontSize: 12, padding: '7px 14px', borderRadius: 7, border: `1px solid ${C.border}`, background: C.white, color: C.indigo, fontWeight: 600, cursor: 'pointer' }}>
              configure template ⚙
            </button>
          )}
          <button onClick={() => navigate(`/accounts/${projectId}`)} style={{ fontSize: 12, padding: '7px 14px', borderRadius: 7, border: `1px solid ${C.border}`, background: C.white, color: C.sub, cursor: 'pointer' }}>← Back</button>
          <button onClick={() => window.print()} style={{ fontSize: 12, padding: '7px 14px', borderRadius: 7, border: 'none', background: C.indigo, color: '#fff', fontWeight: 700, cursor: 'pointer' }}>Print / Save as PDF</button>
        </div>
      </div>

      <div className="report-sheet" style={{ maxWidth: 900, margin: '0 auto', background: '#fff', border: `1px solid ${line}`, borderRadius: 14, padding: '38px 42px', color: ink }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', borderBottom: `2px solid ${ink}`, paddingBottom: 16, marginBottom: 24 }}>
          <div>
            <div style={{ fontSize: 11, fontWeight: 800, letterSpacing: 1.2, color: '#4a68e0', textTransform: 'uppercase' }}>Orbit · Delivery Report</div>
            <h1 style={{ margin: '6px 0 0', fontSize: 26, letterSpacing: -0.6, color: ink }}>{data.name} — {template.name}</h1>
          </div>
          <div style={{ fontSize: 11.5, color: muted, textAlign: 'right', lineHeight: 1.7 }}>
            Period: {period}<br />Generated: {now}<br />Prepared via Orbit
          </div>
        </div>

        {sections.filter(s => s.enabled).map(s => (
          <div key={s.key} style={{ marginBottom: 24 }}>
            <h3 style={secHead}>{SECTION_NAMES[s.key] ?? s.key}</h3>
            {renderSection(s.key)}
          </div>
        ))}

        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 10.5, color: muted, borderTop: `1px solid ${line}`, paddingTop: 12 }}>
          <span>Generated from live Orbit data · template: {template.name}</span>
          <span>Confidential</span>
        </div>
      </div>

      {editOpen && <TemplateEditor template={template} onClose={() => setEditOpen(false)}
        onSaved={() => { setEditOpen(false); qc.invalidateQueries({ queryKey: ['report-template-default', 'acct'] }) }} C={C} />}

      <style>{`@media print {
        body * { visibility: hidden; }
        .report-sheet, .report-sheet * { visibility: visible; }
        .report-sheet { position: absolute; left: 0; top: 0; width: 100%; border: none !important; border-radius: 0 !important; }
      }`}</style>
    </div>
  )
}

// Admin template editor: toggle + reorder sections; PUT persists — the report
// re-renders from the saved template with no redeploy.
const TemplateEditor: React.FC<{template:any;onClose:()=>void;onSaved:()=>void;C:any}> = ({ template, onClose, onSaved, C }) => {
  const [sections, setSections] = useState<Section[]>(() => JSON.parse(template.sections))
  const [saving, setSaving] = useState(false)
  const move = (i: number, d: -1 | 1) => setSections(s => {
    const n = [...s]; const j = i + d
    if (j < 0 || j >= n.length) return s
    ;[n[i], n[j]] = [n[j], n[i]]; return n
  })
  const save = async () => {
    setSaving(true)
    try { await api.put(`/report-templates/${template.id}`, { sections: JSON.stringify(sections) }); onSaved() }
    finally { setSaving(false) }
  }
  return (
    <div onClick={onClose} style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.45)', zIndex: 1200, display: 'grid', placeItems: 'center' }}>
      <div onClick={e => e.stopPropagation()} style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: 22, width: 420 }}>
        <h3 style={{ margin: '0 0 4px', fontSize: 15, color: C.text }}>Report template — {template.name}</h3>
        <div style={{ fontSize: 11.5, color: C.muted, marginBottom: 14 }}>Toggle and reorder sections; applies to every export using this template.</div>
        {sections.map((s, i) => (
          <div key={s.key} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '7px 0', borderBottom: `1px solid ${C.border}` }}>
            <input type="checkbox" checked={s.enabled}
              onChange={e => setSections(list => list.map((x, j) => j === i ? { ...x, enabled: e.target.checked } : x))} />
            <span style={{ flex: 1, fontSize: 13, color: s.enabled ? C.text : C.muted }}>{SECTION_NAMES[s.key] ?? s.key}</span>
            <button onClick={() => move(i, -1)} disabled={i === 0} style={{ border: 'none', background: 'none', cursor: 'pointer', color: C.indigo, fontSize: 14 }}>↑</button>
            <button onClick={() => move(i, 1)} disabled={i === sections.length - 1} style={{ border: 'none', background: 'none', cursor: 'pointer', color: C.indigo, fontSize: 14 }}>↓</button>
          </div>
        ))}
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
          <button onClick={onClose} style={{ fontSize: 12, padding: '7px 14px', borderRadius: 7, border: `1px solid ${C.border}`, background: C.white, color: C.sub, cursor: 'pointer' }}>Cancel</button>
          <button onClick={save} disabled={saving} style={{ fontSize: 12, padding: '7px 14px', borderRadius: 7, border: 'none', background: C.indigo, color: '#fff', fontWeight: 700, cursor: 'pointer' }}>{saving ? 'Saving…' : 'Save template'}</button>
        </div>
      </div>
    </div>
  )
}
