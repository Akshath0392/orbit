import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useC } from '../../design/ThemeContext'
import type { Colors } from '../../design/theme'
import { api } from '../../api/client'
import { Feature } from '../../app/featureFlags'
import { AmDrillModal } from '../radar/am/AmDrillModal'
import type { AmDrillFilters } from '../radar/am/useAmApi'
import { useAmClientOverview, useAmClientMilestones, useAmSettings, useAccountSprintScope } from '../radar/am/useAmApi'
import { AM_INFO, slaTone } from '../radar/am/metricInfo'
import { InfoDot } from '../../design/components/InfoDot'
import { useStore } from '../../app/store'
import { Breadcrumbs } from '../../design/components/Breadcrumbs'
import { DhPillarPane } from './DhPillarPane'
import { DEFAULT_TEAM_ROLE_LABELS, useTeamRoleLabels } from './useTeamRoleLabels'

const STAGE_LABEL: Record<string, string> = {
  PRE_LAUNCH: 'Pre-launch', HYPERCARE: 'Hypercare',
  STEADY_STATE: 'Steady-state', AT_RISK: 'At-risk',
}
const STAGE_COLOR = (C: Colors): Record<string, string> => ({
  PRE_LAUNCH: C.blue, HYPERCARE: C.amber,
  STEADY_STATE: C.green, AT_RISK: C.red,
})
const RAG_BG = (C: Colors): Record<string,string>   => ({ Red: C.redPale, Amber: C.amberPale, Green: C.greenPale })
// mock ragWord(): the hero badge speaks health as a word, not a colour name
const RAG_WORD: Record<string, string> = { Green: 'Healthy', Amber: 'Watch', Red: 'Critical' }
const RAG_FG = (C: Colors): Record<string,string>   => ({ Red: C.red, Amber: C.amber, Green: C.green })

// Client master page tabs (mock §5 / widget-parity plan W13).
const TABS = [
  ['overview', 'Overview'],
  ['speed', 'Delivery Speed'],
  ['quality', 'Delivery Quality'],
  ['pred', 'Delivery Predictability'],
  ['teams', 'Teams'],
  ['workbench', 'Account Workbench'],
] as const
type TabId = typeof TABS[number][0]

export function AccountDetailPage() {
  const C  = useC()
  const qc = useQueryClient()
  const navigate = useNavigate()
  const { projectId } = useParams<{ projectId: string }>()
  const [tab, setTab] = useState<TabId>('overview')
  const [riskOpen, setRiskOpen] = useState(false)
  const [riskForm, setRiskForm] = useState<any>({})
  const [drill, setDrill] = useState<{ title: string; filters: AmDrillFilters } | null>(null)

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['account-detail', projectId],
    queryFn: () => api.get(`/accounts/${projectId}`).then(r => r.data),
    enabled: !!projectId,
  })
  const clientId: number | null = data?.client?.id ?? null
  const clientName: string = data?.client?.name ?? ''
  const { data: amOverview } = useAmClientOverview(clientId)
  const { data: amSettings } = useAmSettings()

  if (isLoading) return <div style={{ padding: 40, color: C.sub }}>Loading account…</div>
  if (error || !data) return <div style={{ padding: 40, color: C.red }}>Account not found</div>

  const stage = data.stage ?? 'STEADY_STATE'
  const rag   = data.rag   ?? 'Green'
  const stageColor   = STAGE_COLOR(C)
  const ragBg        = RAG_BG(C)
  const ragFg        = RAG_FG(C)

  const saveOps = async (val: string) => {
    await api.put(`/accounts/${projectId}/ops-model`, { opsModel: val })
    qc.invalidateQueries({ queryKey: ['account-detail', projectId] })
  }

  const addRisk = async () => {
    if (!riskForm.risk?.trim()) return
    await api.post(`/accounts/${projectId}/risks`, riskForm)
    setRiskForm({}); setRiskOpen(false); refetch()
  }
  const deleteRisk = async (id: number) => {
    await api.delete(`/accounts/${projectId}/risks/${id}`)
    refetch()
  }

  const openDrill = (title: string, filters: Omit<AmDrillFilters, 'clientName'> = {}) =>
    setDrill({ title, filters: { clientName, ...filters } })

  const SectionCard: React.FC<{ title: string; right?: React.ReactNode; children: React.ReactNode }> = ({ title, right, children }) =>
    <section style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: 18, marginBottom: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <h3 style={{ margin: 0, fontSize: 14, fontWeight: 800, color: C.text, textTransform: 'uppercase', letterSpacing: 0.5 }}>{title}</h3>
        {right}
      </div>
      {children}
    </section>

  const inp: React.CSSProperties = {
    fontSize: 12, padding: '6px 10px', borderRadius: 6,
    border: `1px solid ${C.border}`, outline: 'none', color: C.text, background: C.white,
  }

  const mandaysPct: number | null = data.mandays?.purchased ? data.mandays?.consumedPct ?? null : null

  return (
    <div style={{ padding: '22px 24px', background: C.canvas, minHeight: '100vh' }}>

      {/* ── Sticky header — mock acct-hero: name, "POD · RAG · Client master view";
             account RAG lives ONLY in this badge (handoff §5) ────────────────── */}
      <div style={{ position: 'sticky', top: 0, zIndex: 5, background: C.canvas, paddingBottom: 8, marginBottom: 18 }}>
        <div className="no-print" style={{ padding: '2px 0 8px' }}>
          <Breadcrumbs items={[
            { label: 'Orbitter', to: '/radar' },
            ...(data.portfolio?.name ? [{ label: `${data.portfolio.name} POD` }] : []),
            { label: data.name },
          ]} />
        </div>
        <div style={{ position: 'relative', background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: '20px 22px' }}>
          <h2 style={{ margin: 0, fontSize: 27, letterSpacing: -0.6, fontWeight: 800, color: C.text }}>{data.name}</h2>
          <div style={{ fontSize: 13, color: C.sub, marginTop: 6, display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
            {data.portfolio?.name && <span>{data.portfolio.name} POD ·</span>}
            <span style={{ padding: '3px 9px', borderRadius: 999, fontSize: 11, fontWeight: 700, background: ragBg[rag], color: ragFg[rag] }}>{RAG_WORD[rag] ?? rag}</span>
            <span>· Client master view</span>
            {data.goLiveDate && <span>· Go-live {data.goLiveDate}</span>}
          </div>
          {(data.accountType || data.revenueExposure || data.contractEndDate) && (
            <div style={{ fontSize: 11, color: C.muted, marginTop: 4 }}>
              {[data.accountType,
                data.revenueExposure ? `₹${Number(data.revenueExposure).toLocaleString('en-IN')}` : null,
                data.contractEndDate ? `Contract ends ${data.contractEndDate}` : null].filter(Boolean).join(' · ')}
            </div>
          )}
          <div className="no-print" style={{ position: 'absolute', top: 18, right: 20, display: 'flex', gap: 10, alignItems: 'center' }}>
            <select value={data.opsModel ?? 'launch+bau'} onChange={e => saveOps(e.target.value)} style={inp} title="Engagement type">
              <option value="launch">Launch</option>
              <option value="launch+bau">Launch + BAU</option>
              <option value="bau">BAU</option>
            </select>
            {/* template-driven Delivery Report — not a page screenshot */}
            <button onClick={() => navigate(`/accounts/${projectId}/report`)} style={{ fontSize: 12, padding: '6px 14px', borderRadius: 7, border: `1px solid ${C.border}`, background: C.white, color: C.sub, cursor: 'pointer' }}>
              Export PDF
            </button>
          </div>
        </div>
        {/* Top tabs — mock §5: Overview · Speed · Quality · Predictability · Teams · Workbench */}
        <div style={{ display: 'flex', gap: 4, marginTop: 14, borderBottom: `1px solid ${C.border}`, overflowX: 'auto' }} className="no-print">
          {TABS.map(([id, label]) => (
            <button key={id} onClick={() => setTab(id)}
              style={{ padding: '8px 16px', border: 'none', background: 'none', cursor: 'pointer',
                fontSize: 13, fontWeight: 700, whiteSpace: 'nowrap',
                color: tab === id ? C.indigo : C.sub,
                borderBottom: tab === id ? `2px solid ${C.indigo}` : '2px solid transparent' }}>
              {label}
            </button>
          ))}
        </div>
      </div>

      {/* ── Overview tab ──────────────────────────────────────────────────── */}
      {tab === 'overview' && (
        <>
          {/* KPI row (W14) — sole home of these numbers. CSAT is an optional
              feed: no value entered → no tile (absence of data is the gate). */}
          <div style={{ display: 'grid', gridTemplateColumns: `repeat(${amOverview?.csat != null ? 4 : 3}, minmax(0,1fr))`, gap: 12, marginBottom: 16 }}>
            {amOverview?.csat != null && (
              <KpiCard label={<span>CSAT <InfoDot title={AM_INFO.csat.title} body={AM_INFO.csat.body} /></span>}
                value={amOverview.csat} sub="Launch / BAU avg" C={C} />
            )}
            <KpiCard label="Open CRs" value={amOverview?.openCrs ?? '—'}
              sub={`${amOverview?.clientHold ?? 0} on client hold`} C={C}
              onClick={() => openDrill(`${clientName} — open CRs`)} />
            <KpiCard label="Utilization" value={mandaysPct == null ? '—' : `${mandaysPct}%`}
              sub={mandaysPct == null ? 'no mandays data' : `${data.mandays?.consumed ?? 0} of ${data.mandays?.purchased ?? 0} man-days`}
              color={mandaysPct == null ? undefined : mandaysPct > 85 ? C.red : mandaysPct > 70 ? C.amber : C.green} C={C} />
            <KpiCard label="Prod Bugs" value={amOverview?.prodOpen ?? data.productionIssues?.totalOpen ?? 0}
              sub={Object.entries(amOverview?.prodBySeverity ?? {}).map(([k, v]) => `${v} ${k}`).join(' · ') || 'none open'}
              color={(amOverview?.prodBySeverity?.P0 ?? 0) > 0 ? C.red : undefined} C={C} />
          </div>

          {/* Two-col: mock acctSentimentCard (schedule confidence · sentiment ·
              escalations) + SLA & BAU (W15). Speed/stability stay on Workbench. */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 16 }}>
            <section style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: 18 }}>
              <h3 style={{ margin: '0 0 12px', fontSize: 14, fontWeight: 800, color: C.text, textTransform: 'uppercase', letterSpacing: 0.5 }}>Delivery health & sentiment</h3>
              <SentimentRow label={<span>Schedule confidence <InfoDot title="Schedule confidence"
                  body={`Banded from slip probability (v1 heuristic): High < 30%, Medium 30–60%, Low > 60%. Current slip probability: ${data.health?.slipProbabilityPct ?? '—'}%.`} /></span>}
                value={data.health?.scheduleConfidence ?? '—'} C={C} />
              <SentimentRow label="Client sentiment"
                value={`${data.health?.sentiment?.label ?? '—'} · ${data.health?.sentiment?.score ?? '—'} / 10`} C={C} />
              <SentimentRow label="Escalations (open)"
                value={<span style={{ color: (data.health?.escalationsOpen ?? 0) > 0 ? C.red : C.text }}>{data.health?.escalationsOpen ?? 0}</span>} C={C} />
              {(data.health?.sentiment?.reasons ?? []).length > 0 && (
                <ul style={{ margin: '10px 0 0', paddingLeft: 16, fontSize: 11, color: C.muted, lineHeight: 1.7 }}>
                  {(data.health.sentiment.reasons as string[]).map((r, i) => <li key={i}>{r}</li>)}
                </ul>
              )}
            </section>
            <SlaBauCard amOverview={amOverview} data={data} adoptionUrl={amSettings?.adoptionUrl ?? null}
              onDrill={openDrill} C={C} />
          </div>

          {/* Milestones & achievements (W16) — mock §5 order: before Stage SLA */}
          <Feature flag="section.client.milestones">
            <MilestonesSection clientId={clientId} C={C} />
          </Feature>

          {/* Stage SLA over open CRs (W17) — same numbers as the AM stage matrix */}
          <SectionCard title={`Stage SLA — open CRs (${(amOverview?.stages ?? []).reduce((n: number, s: any) => n + Number(s.total ?? 0), 0)})`}
            right={<span style={{ fontSize: 12, color: C.sub }}>click a stage for the CR list <InfoDot title={AM_INFO.stages.title} body={AM_INFO.stages.body} /></span>}>
            {(amOverview?.stages ?? []).length === 0 ? (
              <div style={{ padding: 14, textAlign: 'center', color: C.muted, fontSize: 12 }}>No open CRs</div>
            ) : (
              <table style={{ width: '100%', fontSize: 12, borderCollapse: 'collapse' }}>
                <thead><tr style={{ background: C.canvas }}>
                  {['Stage','Target','Open','Avg aging','Within SLA'].map(h =>
                    <th key={h} style={{ padding: '8px 10px', textAlign: 'left', fontSize: 11, fontWeight: 700, color: C.muted }}>{h}</th>)}
                </tr></thead>
                <tbody>
                  {(amOverview.stages as any[]).map((s: any, i: number) => {
                    const tone = s.withinSlaPct == null ? null : slaTone(Number(s.withinSlaPct))
                    return (
                      <tr key={s.stage} onClick={() => navigate(`/cr?clientId=${clientId}&stage=${encodeURIComponent(s.stage)}`)}
                        style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none', cursor: 'pointer' }}>
                        <td style={{ padding: '8px 10px', color: C.text, fontWeight: 600 }}>{s.stage}</td>
                        <td style={{ padding: '8px 10px', color: C.sub }}>{s.targetDays != null ? `${s.targetDays}d` : 'no SLA'}</td>
                        <td style={{ padding: '8px 10px', fontWeight: 700, color: C.text }}>{s.total}</td>
                        <td style={{ padding: '8px 10px', color: C.sub }}>{s.avgAgingDays}d</td>
                        <td style={{ padding: '8px 10px', fontWeight: 700,
                          color: tone == null ? C.muted : tone === 'g' ? C.green : tone === 'a' ? C.amber : C.red }}>
                          {s.withinSlaPct != null ? `${s.withinSlaPct}%` : '—'}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            )}
          </SectionCard>

          {/* Timeline & Health = Release Calendar only (mock §5 #5) — never hidden */}
          <SectionCard title="Timeline & Health — release calendar">
            {(data.releaseCalendar ?? []).length === 0 ? (
              <div style={{ padding: 14, textAlign: 'center', color: C.muted, fontSize: 12 }}>No releases scheduled</div>
            ) : (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: 8 }}>
                {(data.releaseCalendar as any[]).map((r: any) => {
                  const c = r.type === 'launch' ? C.indigo : r.type === 'support' ? C.red : C.green
                  return (
                    <div key={r.id} style={{ padding: 10, borderRadius: 8, background: c + '14', border: `1px solid ${c}33` }}>
                      <div style={{ fontSize: 11, fontWeight: 700, color: c, textTransform: 'uppercase' }}>{r.type}</div>
                      <div style={{ fontSize: 12, color: C.text, marginTop: 3, fontWeight: 600 }}>{r.date}</div>
                      <div style={{ fontSize: 12, color: C.sub, marginTop: 2 }}>{r.label}</div>
                    </div>
                  )
                })}
              </div>
            )}
          </SectionCard>

          {/* Sprint scope — phase-grouped tracker (W18) */}
          <Feature flag="section.client.sprint-scope">
            <SprintScopeSection projectId={Number(projectId)} C={C} />
          </Feature>

          {/* Risk register */}
          <SectionCard title={`Risk register (${(data.riskRegister ?? []).length})`}
            right={<button onClick={() => setRiskOpen(o => !o)} className="no-print"
              style={{ fontSize: 11, padding: '5px 12px', borderRadius: 6, border: 'none', background: C.indigo, color: '#fff', cursor: 'pointer', fontWeight: 600 }}>
              {riskOpen ? 'Cancel' : '+ Add risk'}
            </button>}>
            {riskOpen && (
              <div className="no-print" style={{ display: 'grid', gridTemplateColumns: 'repeat(6, 1fr) auto', gap: 8, marginBottom: 12, padding: 10, background: C.canvas, borderRadius: 8 }}>
                <input placeholder="Jira ticket"  style={inp} value={riskForm.jiraTicket  ?? ''} onChange={e => setRiskForm({...riskForm, jiraTicket:  e.target.value})}/>
                <input placeholder="Risk *"        style={inp} value={riskForm.risk        ?? ''} onChange={e => setRiskForm({...riskForm, risk:        e.target.value})}/>
                <input type="date"                  style={inp} value={riskForm.receivedOn  ?? ''} onChange={e => setRiskForm({...riskForm, receivedOn:  e.target.value})}/>
                <select                              style={inp} value={riskForm.rag         ?? ''} onChange={e => setRiskForm({...riskForm, rag:         e.target.value})}>
                  <option value="">RAG</option><option>Green</option><option>Amber</option><option>Red</option>
                </select>
                <input type="date"                  style={inp} value={riskForm.actionEnd   ?? ''} onChange={e => setRiskForm({...riskForm, actionEnd:   e.target.value})}/>
                <input placeholder="Owner"          style={inp} value={riskForm.actionOwner ?? ''} onChange={e => setRiskForm({...riskForm, actionOwner: e.target.value})}/>
                <button onClick={addRisk} disabled={!riskForm.risk?.trim()}
                  style={{ fontSize: 11, padding: '6px 14px', borderRadius: 6, border: 'none', background: C.indigo, color: '#fff', cursor: 'pointer', fontWeight: 600 }}>Add</button>
              </div>
            )}
            {(data.riskRegister ?? []).length === 0 ? (
              <div style={{ padding: 18, textAlign: 'center', color: C.muted, fontSize: 12 }}>No risks logged</div>
            ) : (
              <table style={{ width: '100%', fontSize: 12, borderCollapse: 'collapse' }}>
                <thead><tr style={{ background: C.canvas }}>
                  {['Jira','Risk','Received','RAG','Action end','Owner','Source',''].map(h =>
                    <th key={h} style={{ padding: '8px 10px', textAlign: 'left', fontSize: 11, fontWeight: 700, color: C.muted }}>{h}</th>)}
                </tr></thead>
                <tbody>
                  {(data.riskRegister as any[]).map((r: any, i: number) => (
                    <tr key={r.id} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none' }}>
                      <td style={{ padding: '8px 10px', fontFamily: 'monospace', color: C.indigo }}>{r.jiraTicket ?? '—'}</td>
                      <td style={{ padding: '8px 10px', color: C.text, maxWidth: 300 }}>{r.risk}</td>
                      <td style={{ padding: '8px 10px', color: C.sub }}>{r.receivedOn ?? '—'}</td>
                      <td style={{ padding: '8px 10px' }}>
                        {r.rag && <span style={{ padding: '2px 8px', borderRadius: 4, fontSize: 11, fontWeight: 700, background: ragBg[r.rag], color: ragFg[r.rag] }}>{r.rag}</span>}
                      </td>
                      <td style={{ padding: '8px 10px', color: C.sub }}>{r.actionEnd ?? '—'}</td>
                      <td style={{ padding: '8px 10px', color: C.text }}>{r.actionOwner ?? '—'}</td>
                      <td style={{ padding: '8px 10px', color: C.sub }}>{r.source ?? '—'}</td>
                      <td style={{ padding: '8px 10px' }}>
                        <button onClick={() => deleteRisk(r.id)} className="no-print"
                          style={{ fontSize: 11, padding: '2px 8px', borderRadius: 4, border: 'none', background: 'transparent', color: C.red, cursor: 'pointer' }}>✕</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </SectionCard>

          {/* CRs & Production Issues tiles (mock §5 #8 — last mock section) */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 16 }}>
            <BigTile label="Total CRs (open)" value={amOverview?.openCrs ?? '—'}
              subs={[`${amOverview?.openBauCrs ?? 0} BAU`, `${amOverview?.openLaunchCrs ?? 0} Launch`, `${amOverview?.clientHold ?? 0} hold`]}
              onClick={() => openDrill(`${clientName} — open CRs`)} C={C} />
            <BigTile label="Total Prod Issues (open)" value={amOverview?.prodOpen ?? 0}
              subs={Object.entries(amOverview?.prodBySeverity ?? {}).map(([k, v]) => `${v} ${k}`)}
              color={(amOverview?.prodBySeverity?.P0 ?? 0) > 0 ? C.red : undefined} C={C} />
          </div>

        </>
      )}

      {/* ── Pillar tabs (W20–W22) ─────────────────────────────────────────── */}
      {tab === 'speed'   && <DhPillarPane clientId={clientId} clientName={clientName} pillar="speed" />}
      {tab === 'quality' && <DhPillarPane clientId={clientId} clientName={clientName} pillar="quality" />}
      {tab === 'pred'    && <DhPillarPane clientId={clientId} clientName={clientName} pillar="pred" />}

      {/* ── Teams tab (W19) — mock: pill toggle + Role/Name tables, inline edit ── */}
      {tab === 'teams' && (
        <TeamsTab projectId={Number(projectId)} internal={data.internalTeam} client={data.clientTeam}
          clientName={clientName} onSaved={refetch} C={C} />
      )}

      {/* ── Workbench tab — mock wb-cards: This Week / Next Week / Attention ── */}
      {tab === 'workbench' && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0,1fr))', gap: 14 }}>
          <WbCard title="This Week" desc="Completed & shipped" C={C} rows={[
            ['CRs closed', data.workbench?.thisWeek?.crsClosed ?? 0],
            ['Bugs fixed', data.workbench?.thisWeek?.bugsFixed ?? 0],
            ['UAT sign-offs', data.workbench?.thisWeek?.uatSignOffs ?? 0],
          ]} />
          <WbCard title="Next Week" desc="Planned milestones" C={C} rows={[
            ['Go-lives', data.workbench?.nextWeek?.goLives ?? 0],
            ['UAT cycles', data.workbench?.nextWeek?.uatCycles ?? 0],
            ['Sign-offs due', data.workbench?.nextWeek?.signOffsDue ?? 0],
          ]} />
          <WbCard title="Attention" desc="Needs a decision" C={C} rows={[
            ['Blocked items', data.workbench?.attention?.blocked ?? 0],
            ['Awaiting client', data.workbench?.attention?.awaitingClient ?? 0],
            ['Escalations', data.workbench?.attention?.escalations ?? 0],
          ]} onRowClick={label => {
            if (label === 'Blocked items') navigate(`/cr?clientId=${clientId}&stage=Hold`)
            if (label === 'Escalations') navigate('/alerts')
          }} />
        </div>
      )}

      {drill && <AmDrillModal title={drill.title} filters={drill.filters} onClose={() => setDrill(null)} />}

      {/* Print styles */}
      <style>{`
        @media print {
          .no-print { display: none !important; }
          body { background: white; }
        }
      `}</style>
    </div>
  )
}

// ── Overview widgets ─────────────────────────────────────────────────────────

const KpiCard: React.FC<{label:React.ReactNode;value:any;sub?:string;color?:string;C:any;onClick?:()=>void}> = ({label,value,sub,color,C,onClick}) => (
  <div onClick={onClick} style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: 16, cursor: onClick ? 'pointer' : 'default' }}>
    <div style={{ fontSize: 11, fontWeight: 800, color: C.muted, textTransform: 'uppercase', letterSpacing: 0.4 }}>{label}</div>
    <div style={{ fontSize: 28, fontWeight: 900, color: color ?? C.text, marginTop: 6, letterSpacing: -1 }}>{value}</div>
    {sub && <div style={{ fontSize: 11, color: C.sub, marginTop: 3 }}>{sub}</div>}
  </div>
)

const BigTile: React.FC<{label:string;value:any;subs:string[];color?:string;C:any;onClick?:()=>void}> = ({label,value,subs,color,C,onClick}) => (
  <div onClick={onClick} style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: 18, cursor: onClick ? 'pointer' : 'default' }}>
    <div style={{ fontSize: 12, fontWeight: 700, color: C.muted, textTransform: 'uppercase', letterSpacing: 0.4 }}>{label}</div>
    <div style={{ fontSize: 34, fontWeight: 900, color: color ?? C.text, margin: '6px 0', letterSpacing: -1.4 }}>{value}</div>
    <div style={{ fontSize: 12, color: C.sub, fontWeight: 600 }}>{subs.length ? subs.join(' · ') : '—'}</div>
  </div>
)

// W15 — SLA adherence, Breached·Near·Met, engagement + adoption links.
// Engagement and adoption are optional feeds — unset means the row doesn't render.
const SlaBauCard: React.FC<{amOverview:any;data:any;adoptionUrl:string|null;onDrill:(t:string)=>void;C:any}> = ({amOverview,data,adoptionUrl,onDrill,C}) => {
  const adh = amOverview?.slaAdherencePct
  const tone = adh == null ? C.muted : adh >= 85 ? C.green : adh >= 70 ? C.amber : C.red
  const row = (label: React.ReactNode, value: React.ReactNode) => (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', padding: '7px 0', borderTop: `1px dashed ${C.border}` }}>
      <span style={{ fontSize: 12.5, color: C.sub }}>{label}</span>
      <span style={{ fontSize: 13, fontWeight: 750, color: C.text }}>{value}</span>
    </div>
  )
  return (
    <section style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: 18 }}>
      <h3 style={{ margin: '0 0 12px', fontSize: 14, fontWeight: 800, color: C.text, textTransform: 'uppercase', letterSpacing: 0.5 }}>
        SLA & BAU <InfoDot title={AM_INFO.slaAdh.title} body={AM_INFO.slaAdh.body} />
      </h3>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
        <span style={{ fontSize: 34, fontWeight: 900, letterSpacing: -1.4, color: tone }}>{adh != null ? `${adh}%` : '—'}</span>
        <span style={{ fontSize: 12, color: C.sub }}>SLA adherence (open CRs)</span>
      </div>
      <div style={{ display: 'flex', gap: 8, margin: '10px 0 6px', cursor: 'pointer' }}
        onClick={() => onDrill('Open CRs — SLA view')}
        title="Breached · Near breach (<25% window left) · Met">
        <Bnm label="Breached" value={amOverview?.slaBreached ?? 0} color={C.red} C={C} />
        <Bnm label="Near" value={amOverview?.slaNear ?? 0} color={C.amber} C={C} />
        <Bnm label="Met" value={amOverview?.slaMet ?? 0} color={C.green} C={C} />
        <InfoDot title={AM_INFO.bnm.title} body={AM_INFO.bnm.body} />
      </div>
      {row('Last UAT sign-off', data.bauOps?.lastUatSignOff ?? '—')}
      {row('Last go-live', data.goLiveDate ?? '—')}
      {amOverview?.engagementScore != null &&
        row(<span>Engagement <InfoDot title={AM_INFO.eng.title} body={AM_INFO.eng.body} /></span>,
          <span>{amOverview.engagementScore} / 100</span>)}
      {adoptionUrl &&
        row('Adoption',
          <a href={adoptionUrl} target="_blank" rel="noreferrer" style={{ color: C.indigo, fontWeight: 700, textDecoration: 'none' }}>Open dashboard ↗</a>)}
    </section>
  )
}

const Bnm: React.FC<{label:string;value:any;color:string;C:any}> = ({label,value,color,C}) => (
  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, padding: '4px 10px', borderRadius: 999, background: color + '18', color, fontSize: 12, fontWeight: 800 }}>
    {value} <span style={{ fontWeight: 600, fontSize: 11 }}>{label}</span>
  </span>
)

// W16 — done / upcoming rows derived from the client's sprints, zero manual input.
const MilestonesSection: React.FC<{clientId:number|null;C:any}> = ({ clientId, C }) => {
  const { data } = useAmClientMilestones(clientId)
  const fmt = (d: string | null) => d ? String(d).slice(0, 10) : '—'
  const row = (m: any, tone: string) => (
    <div key={m.label} style={{ display: 'grid', gridTemplateColumns: '18px 1fr 110px', gap: 10, alignItems: 'center', padding: '7px 0', borderBottom: `1px dashed ${C.border}` }}>
      <span style={{ color: tone }}>{tone === C.green ? '✓' : '◷'}</span>
      <span style={{ fontSize: 13, color: C.text }}>
        <b>{m.label ?? 'Sprint'}</b>
        <span style={{ color: C.sub, marginLeft: 8, fontSize: 12 }}>{m.detail}</span>
      </span>
      <span style={{ fontSize: 12, color: C.sub, textAlign: 'right' }}>{fmt(m.date)}</span>
    </div>
  )
  return (
    <section style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: 18, marginBottom: 16 }}>
      <h3 style={{ margin: '0 0 12px', fontSize: 14, fontWeight: 800, color: C.text, textTransform: 'uppercase', letterSpacing: 0.5 }}>
        Milestones & achievements
      </h3>
      {!data?.dataAvailable ? (
        <div style={{ fontSize: 12.5, color: C.muted }}>
          Auto-derived from sprints — appears once sprint sync has data for this client. No manual entry.
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 18 }}>
          <div>
            <div style={{ fontSize: 11, fontWeight: 800, color: C.muted, textTransform: 'uppercase', marginBottom: 6 }}>Done</div>
            {(data.done as any[]).length === 0 ? <div style={{ fontSize: 12, color: C.muted }}>None yet</div>
              : (data.done as any[]).map((m: any) => row(m, C.green))}
          </div>
          <div>
            <div style={{ fontSize: 11, fontWeight: 800, color: C.muted, textTransform: 'uppercase', marginBottom: 6 }}>Upcoming</div>
            {(data.upcoming as any[]).length === 0 ? <div style={{ fontSize: 12, color: C.muted }}>None planned</div>
              : (data.upcoming as any[]).map((m: any) => row(m, C.indigo))}
          </div>
        </div>
      )}
    </section>
  )
}

// W18 — phase-grouped tracker; sprint filter appears once sprint tags exist (F3).
const SprintScopeSection: React.FC<{projectId:number;C:any}> = ({ projectId, C }) => {
  const { data, isLoading } = useAccountSprintScope(projectId)
  const [sprintFilter, setSprintFilter] = useState<string>('')
  // long phases collapse to 8 rows — full lists made the page unusably tall
  const [expanded, setExpanded] = useState<Record<string, boolean>>({})
  const PHASE_ROW_CAP = 8
  const sprintNames: string[] = Array.from(new Set(
    ((data?.phases ?? []) as any[]).flatMap((p: any) => (p.rows as any[]).map(r => r.sprint).filter(Boolean))
  ))
  const rowsOf = (ph: any) => sprintFilter
    ? (ph.rows as any[]).filter(r => r.sprint === sprintFilter)
    : (ph.rows as any[])
  const badgeStyle = (b: string): React.CSSProperties => ({
    fontSize: 10, fontWeight: 800, textTransform: 'uppercase', letterSpacing: 0.4,
    padding: '2px 8px', borderRadius: 999,
    ...(b === 'delayed' ? { background: C.redPale, color: C.red }
      : b === 'on-hold' ? { background: C.amberPale, color: C.amberDeep }
      : b === 'delivered' ? { background: C.greenPale, color: C.greenDeep }
      : { background: C.indigoPale ?? C.canvas, color: C.indigo }),
  })
  return (
    <section style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: 18, marginBottom: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <h3 style={{ margin: 0, fontSize: 14, fontWeight: 800, color: C.text, textTransform: 'uppercase', letterSpacing: 0.5 }}>Sprint scope — delivery tracker</h3>
        {sprintNames.length > 0 ? (
          <select value={sprintFilter} onChange={e => setSprintFilter(e.target.value)}
            style={{ fontSize: 12, padding: '6px 10px', borderRadius: 6, border: `1px solid ${C.border}`, color: C.text, background: C.white }}>
            <option value="">All sprints</option>
            {sprintNames.map(s => <option key={s} value={s}>{s}</option>)}
          </select>
        ) : (
          <span style={{ fontSize: 11, color: C.muted }}>sprint tags appear once sprint sync has data</span>
        )}
      </div>
      {isLoading ? <div style={{ padding: 14, color: C.sub, fontSize: 12 }}>Loading…</div> : (
        (data?.phases ?? []).map((ph: any) => {
          const rows = rowsOf(ph)
          return (
          <div key={ph.phase} style={{ marginBottom: 12 }}>
            <div style={{ fontSize: 12, fontWeight: 800, color: C.sub, textTransform: 'uppercase', letterSpacing: 0.4, padding: '6px 0', borderBottom: `1px solid ${C.border}` }}>
              {ph.phase} ({rows.length}{sprintFilter ? ` of ${ph.count}` : ''})
            </div>
            {rows.length === 0 ? (
              <div style={{ padding: '8px 0', fontSize: 12, color: C.muted }}>None</div>
            ) : (
              <>
                {(expanded[ph.phase] ? rows : rows.slice(0, PHASE_ROW_CAP)).map((r: any) => {
                  // phase target date derived from stage aging target (no phase dates synced)
                  const target = r.targetDays != null
                    ? new Date(Date.now() + (r.targetDays - r.ageDays) * 86_400_000)
                        .toLocaleDateString('en-GB', { day: '2-digit', month: 'short' })
                    : null
                  const overdue = r.targetDays != null && r.ageDays > r.targetDays
                  return (
                    // mock .ss-row: ticket · module · sprint tag · delivery badge · phase target
                    <div key={r.key} style={{ display: 'grid', gridTemplateColumns: '110px 1fr 130px 110px 130px', gap: 10, alignItems: 'center', padding: '7px 0', borderBottom: `1px dashed ${C.border}` }}>
                      <span style={{ fontFamily: 'monospace', fontSize: 11, color: C.indigo }}>{r.key}</span>
                      <b style={{ fontSize: 12, color: C.text, fontWeight: 700, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={r.summary}>{r.summary}</b>
                      <span style={{ fontSize: 10.5, color: r.sprint ? C.tealDeep ?? C.sub : C.muted, fontWeight: 700, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={r.sprint ?? undefined}>
                        {r.sprint ?? '—'}
                      </span>
                      <span><span style={badgeStyle(r.badge)}>{r.badge}</span></span>
                      <span style={{ fontSize: 11, textAlign: 'right', color: overdue ? C.red : C.sub }}>
                        {target ? `Target: ${target}` : '—'}
                      </span>
                    </div>
                  )
                })}
                {rows.length > PHASE_ROW_CAP && (
                  <button onClick={() => setExpanded(e => ({ ...e, [ph.phase]: !e[ph.phase] }))}
                    style={{ background: 'none', border: 'none', padding: '8px 0', color: C.indigo, fontSize: 12, fontWeight: 700, cursor: 'pointer' }}>
                    {expanded[ph.phase] ? 'show less' : `show all (${rows.length})`}
                  </button>
                )}
              </>
            )}
          </div>
          )
        })
      )}
    </section>
  )
}

// ── Small presentational components ──────────────────────────────────────────



const MetaChip: React.FC<{label:string;value:any;color?:string;C:any}> = ({label,value,color,C}) => (
  <div style={{ padding: '10px 14px', border: `1px solid ${C.border}`, borderRadius: 8, background: C.canvas }}>
    <div style={{ fontSize: 10, fontWeight: 800, color: C.muted, textTransform: 'uppercase', letterSpacing: 0.4, marginBottom: 4 }}>{label}</div>
    <div style={{ fontSize: 14, fontWeight: 700, color: color ?? C.text }}>{value}</div>
  </div>
)


// Teams pane: pill toggle Internal Team / Client Team, Role·Name tables;
// inline edit (PM/ADMIN) writes through PUT /accounts/{id}/team.
// Internal role labels come from team_role_labels (Admin console) — keyed
// [role_key, PUT field]; client-side roles keep fixed labels.
const INTERNAL_ROLES: [string, string][] = [
  ['internal_pm', 'internalPm'], ['internal_am', 'internalAm'],
  ['internal_sol', 'internalSol'], ['internal_em', 'internalEm'],
  ['internal_tech_lead', 'internalTechLead'], ['internal_qa_lead', 'internalQaLead'],
  ['internal_support_mgr', 'internalSupportMgr'],
]
const CLIENT_ROLES: [string, string][] = [
  ['Executive Sponsor', 'clientSponsor'], ['Tech SPOC', 'clientTechSpoc'],
  ['Business SPOC', 'clientBizSpoc'], ['Project Manager', 'clientPm'],
]
// payload key (GET shape) per PUT field — the two shapes differ historically
const GET_KEY: Record<string, [string, string]> = {
  internalPm: ['internal', 'projectManager'], internalAm: ['internal', 'accountManager'],
  internalSol: ['internal', 'solutionsManager'], internalEm: ['internal', 'engineeringManager'],
  internalTechLead: ['internal', 'techLead'], internalQaLead: ['internal', 'qaLead'],
  internalSupportMgr: ['internal', 'supportManager'],
  clientSponsor: ['client', 'executiveSponsor'], clientTechSpoc: ['client', 'techSpoc'],
  clientBizSpoc: ['client', 'businessSpoc'], clientPm: ['client', 'projectManager'],
}

const TeamsTab: React.FC<{projectId:number;internal:any;client:any;clientName:string;onSaved:()=>void;C:any}> =
({ projectId, internal, client, clientName, onSaved, C }) => {
  const role = useStore(s => s.user?.role)
  const canEdit = role === 'ADMIN' || role === 'PM'
  const [view, setView] = useState<'internal' | 'client'>('internal')
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState(false)
  const { data: roleLabels } = useTeamRoleLabels()
  const labelOf = (roleKey: string) =>
    roleLabels?.find(l => l.roleKey === roleKey)?.label ?? DEFAULT_TEAM_ROLE_LABELS[roleKey] ?? roleKey

  const valueOf = (putKey: string) => {
    const [side, key] = GET_KEY[putKey]
    const v = (side === 'internal' ? internal : client)?.[key]
    return v && v !== '—' ? v : ''
  }
  const startEdit = () => {
    const f: Record<string, string> = {}
    for (const [, k] of [...INTERNAL_ROLES, ...CLIENT_ROLES]) f[k] = valueOf(k)
    setForm(f); setEditing(true)
  }
  const save = async () => {
    setSaving(true)
    try { await api.put(`/accounts/${projectId}/team`, form); setEditing(false); onSaved() }
    finally { setSaving(false) }
  }

  const roles: [string, string][] = view === 'internal'
    ? INTERNAL_ROLES.map(([roleKey, putKey]) => [labelOf(roleKey), putKey])
    : CLIENT_ROLES
  return (
    <section style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: 18 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
        <div style={{ display: 'flex', gap: 6 }}>
          {(['internal', 'client'] as const).map(v => (
            <button key={v} onClick={() => setView(v)}
              style={{ fontSize: 12, fontWeight: 700, padding: '6px 14px', borderRadius: 999, cursor: 'pointer',
                border: `1px solid ${view === v ? C.indigo : C.border}`,
                background: view === v ? C.indigo : C.white, color: view === v ? '#fff' : C.sub }}>
              {v === 'internal' ? 'Internal Team' : `Client Team (${clientName || '—'})`}
            </button>
          ))}
        </div>
        {canEdit && (
          editing ? (
            <div style={{ display: 'flex', gap: 8 }}>
              <button onClick={() => setEditing(false)} style={{ fontSize: 12, padding: '6px 12px', borderRadius: 7, border: `1px solid ${C.border}`, background: C.white, color: C.sub, cursor: 'pointer' }}>Cancel</button>
              <button onClick={save} disabled={saving} style={{ fontSize: 12, padding: '6px 12px', borderRadius: 7, border: 'none', background: C.indigo, color: '#fff', fontWeight: 600, cursor: 'pointer' }}>{saving ? 'Saving…' : 'Save'}</button>
            </div>
          ) : (
            <button onClick={startEdit} style={{ fontSize: 12, padding: '6px 12px', borderRadius: 7, border: `1px solid ${C.border}`, background: C.white, color: C.indigo, fontWeight: 600, cursor: 'pointer' }}>Edit team</button>
          )
        )}
      </div>
      <table style={{ width: '100%', fontSize: 13, borderCollapse: 'collapse' }}>
        <thead><tr style={{ background: C.canvas }}>
          {['Role', 'Name'].map(h => <th key={h} style={{ padding: '8px 12px', textAlign: 'left', fontSize: 11, fontWeight: 700, color: C.muted }}>{h}</th>)}
        </tr></thead>
        <tbody>
          {roles.map(([label, key], i) => (
            <tr key={key} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none' }}>
              <td style={{ padding: '9px 12px', fontWeight: 700, color: C.text, width: 220 }}>{label}</td>
              <td style={{ padding: '9px 12px', color: C.text }}>
                {editing ? (
                  <input value={form[key] ?? ''} onChange={e => setForm(f => ({ ...f, [key]: e.target.value }))}
                    placeholder="Not assigned"
                    style={{ fontSize: 12, padding: '5px 9px', borderRadius: 6, border: `1px solid ${C.border}`, background: C.white, color: C.text, outline: 'none', width: '60%' }} />
                ) : (
                  <span style={{ color: valueOf(key) ? C.text : C.muted }}>{valueOf(key) || 'Not assigned'}</span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}

// mock .wb-card: title + desc + label/value metric rows
const WbCard: React.FC<{title:string;desc:string;rows:[string, any][];C:any;onRowClick?:(label:string)=>void}> = ({title,desc,rows,C,onRowClick}) => (
  <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: 18 }}>
    <h4 style={{ margin: 0, fontSize: 14, fontWeight: 800, color: C.text }}>{title}</h4>
    <div style={{ fontSize: 11, color: C.muted, marginTop: 2, marginBottom: 8 }}>{desc}</div>
    {rows.map(([label, value]) => (
      <div key={label} onClick={onRowClick ? () => onRowClick(label) : undefined}
        style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0',
          borderBottom: `1px solid ${C.border}`, fontSize: 13, cursor: onRowClick ? 'pointer' : 'default' }}>
        <span style={{ color: C.sub }}>{label}</span>
        <span style={{ color: Number(value) > 0 && label === 'Escalations' ? C.red : C.text, fontWeight: 700, fontSize: 15 }}>{value}</span>
      </div>
    ))}
  </div>
)

// mock .wb-metric row: label left, value right (acctSentimentCard shape)
const SentimentRow: React.FC<{label:React.ReactNode;value:React.ReactNode;C:any}> = ({label,value,C}) => (
  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderBottom: `1px solid ${C.border}`, fontSize: 13 }}>
    <span style={{ color: C.sub }}>{label}</span>
    <span style={{ color: C.text, fontWeight: 700, fontSize: 14 }}>{value}</span>
  </div>
)

