// Client master page (docs/plan/orbitter-am-v3-reforms-plan.md §4) — one page
// per client for every role, at clients/:clientId. Absorbs the mock's former
// standalone Delivery Health dashboard as three pillar tabs. Metric cards whose
// feed doesn't exist yet render the awaiting-feed variant behind
// section.client.dh.* flags (V83, NONE) — admins see them, the pilot doesn't.
import { useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useC } from '../../design/ThemeContext'
import { Feature } from '../../app/featureFlags'
import { PageHeader } from '../../design/components/PageHeader'
import { Tabs } from '../../design/components/Tabs'
import { StatGrid } from '../../design/components/StatGrid'
import { Card } from '../../design/components/Card'
import { Btn } from '../../design/components/Btn'
import { Select } from '../../design/components/Select'
import { Modal } from '../../design/components/Modal'
import { ScoreRing } from '../../design/components/ScoreRing'
import { MetricCard } from '../../design/components/MetricCard'
import { SegmentBar } from '../../design/components/SegmentBar'
import { MilestoneList } from '../../design/components/MilestoneList'
import { SectionHeader } from '../../design/components/SectionHeader'
import { TableWrap } from '../../design/components/TableWrap'
import { InfoDot } from '../../design/components/InfoDot'
import { LoadingState, ErrorState } from '../../design/components/PageState'
import { useIsMobile } from '../../design/useBreakpoint'
import { AmDrillModal } from '../radar/am/AmDrillModal'
import { AmDrillFilters, useAmClientDhMetrics, useAmClientOverview, useAmSettings } from '../radar/am/useAmApi'
import { AM_INFO, DH_DEFS, DhDef, dhInfoBody, dhRag } from '../radar/am/metricInfo'

const TABS = ['Overview', 'Delivery Speed', 'Delivery Quality', 'Delivery Predictability', 'Teams', 'Account Workbench']
const PILLAR_BY_TAB: Record<string, 'speed' | 'quality' | 'pred'> = {
  'Delivery Speed': 'speed', 'Delivery Quality': 'quality', 'Delivery Predictability': 'pred',
}

// Health-score weights (mock state.dhW): Speed 40 · Quality 35 · Predictability 25.
type Weights = { speed: number; quality: number; pred: number }
const DEFAULT_W: Weights = { speed: 40, quality: 35, pred: 25 }
function loadWeights(): Weights {
  try { return { ...DEFAULT_W, ...JSON.parse(localStorage.getItem('orbit-dh-weights') || '{}') } }
  catch { return DEFAULT_W }
}

const monthShort = (ym: string) => new Date(`${ym}-01T00:00:00`).toLocaleString('en', { month: 'short' })
const thrText = (d: DhDef) => `${d.dir === 'low' ? '≤' : '≥'}${d.g} green · ${d.dir === 'low' ? '≤' : '≥'}${d.a} amber · else red`

type Drill = { title: string; filters: AmDrillFilters } | null

export function ClientMasterPage() {
  const { clientId } = useParams()
  const id = clientId ? Number(clientId) : null
  const C = useC()
  const navigate = useNavigate()
  const mobile = useIsMobile()
  const [tab, setTab] = useState('Overview')
  const [drill, setDrill] = useState<Drill>(null)
  const { data: ov, isLoading, isError } = useAmClientOverview(id)

  if (isLoading) return <LoadingState />
  if (isError || !ov) return <ErrorState error="Client not found." />

  const openDrill = (title: string, filters: Omit<AmDrillFilters, 'clientName'>) =>
    setDrill({ title, filters: { ...filters, clientName: ov.client } })

  return (
    <div style={{ padding: mobile ? '16px 14px' : '22px 24px' }}>
      <PageHeader
        title={ov.client}
        subtitle={`${ov.pod ?? 'Unassigned POD'} · client master page`}
        breadcrumbs={[
          { label: 'Orbitter', to: '/radar' },
          ...(ov.pod ? [{ label: `${ov.pod} POD` }] : []),
          { label: ov.client },
        ]}
      />
      <Tabs items={TABS} active={tab} onChange={setTab} />

      {tab === 'Overview' && <OverviewPane ov={ov} clientId={id} onDrill={openDrill} />}
      {PILLAR_BY_TAB[tab] && <PillarPane clientId={id} clientName={ov.client} pillar={PILLAR_BY_TAB[tab]} onDrill={openDrill} />}
      {tab === 'Teams' && (
        <Card>
          <div style={{ fontSize: 13, color: C.sub }}>
            Team composition and utilization live on each account (project) page — this client has
            {ov.utilization == null ? ' no mandays data linked here yet' : ' linked mandays data'}.
            Per-account teams open from the Radar account cards.
          </div>
        </Card>
      )}
      {tab === 'Account Workbench' && (
        <div style={{ display: 'grid', gap: 12, gridTemplateColumns: mobile ? '1fr' : 'repeat(auto-fit, minmax(220px, 1fr))' }}>
          <Card onClick={() => openDrill(`${ov.client} — open CRs`, {})} style={{ cursor: 'pointer' }}>
            <div style={{ fontSize: 13, fontWeight: 800, color: C.text }}>Open CR list →</div>
            <div style={{ fontSize: 12, color: C.sub, marginTop: 4 }}>Every open CR for {ov.client}, paginated.</div>
          </Card>
          <Card onClick={() => navigate('/bugs')} style={{ cursor: 'pointer' }}>
            <div style={{ fontSize: 13, fontWeight: 800, color: C.text }}>Production bugs →</div>
            <div style={{ fontSize: 12, color: C.sub, marginTop: 4 }}>Bug triage board with SLA clocks.</div>
          </Card>
          <Card onClick={() => navigate('/alerts')} style={{ cursor: 'pointer' }}>
            <div style={{ fontSize: 13, fontWeight: 800, color: C.text }}>Alert Center →</div>
            <div style={{ fontSize: 12, color: C.sub, marginTop: 4 }}>Breaches and risk alerts (bell lives here, not on this page).</div>
          </Card>
          <Card onClick={() => navigate('/reports')} style={{ cursor: 'pointer' }}>
            <div style={{ fontSize: 13, fontWeight: 800, color: C.text }}>Reports →</div>
            <div style={{ fontSize: 12, color: C.sub, marginTop: 4 }}>Printable report flow (paper-white, PDF-friendly).</div>
          </Card>
        </div>
      )}

      {drill && <AmDrillModal title={drill.title} filters={drill.filters} onClose={() => setDrill(null)} />}
    </div>
  )
}

// ── Overview ────────────────────────────────────────────────────────────────

function OverviewPane({ ov, clientId, onDrill }: {
  ov: any
  clientId: number | null
  onDrill: (t: string, f: Omit<AmDrillFilters, 'clientName'>) => void
}) {
  const C = useC()
  const mobile = useIsMobile()
  // pillar scores power the sentiment card; 6m/ALL is the default lens
  const { data: dh } = useAmClientDhMetrics(clientId, 6)
  const { data: amSettings } = useAmSettings()
  const adoptionUrl = amSettings?.adoptionUrl || null
  const p0 = Number(ov.prodBySeverity?.P0 ?? 0)

  return (
    <>
      {/* 1 · KPI row — sole home of these numbers (mock dedup rule). CSAT is
          an optional admin-entered feed — no value, no tile. */}
      <StatGrid items={[
        ...(ov.csat != null ? [{ label: 'CSAT', value: ov.csat, sub: 'Launch / BAU avg' }] : []),
        { label: 'Open CRs', value: ov.openCrs, sub: `${ov.clientHold} on client hold`, color: C.indigo },
        { label: 'Utilization', value: ov.utilization ?? '—', sub: ov.utilization == null ? 'No mandays data' : undefined },
        { label: 'Prod bugs open', value: ov.prodOpen, sub: `P0 ${p0}`, color: p0 > 0 ? C.red : undefined },
      ]} />

      {/* 2 · Delivery health & sentiment + SLA & BAU */}
      <div style={{ display: 'grid', gap: 14, gridTemplateColumns: mobile ? '1fr' : '1fr 1fr', marginTop: 4 }}>
        <Card>
          <div style={{ fontSize: 13, fontWeight: 800, color: C.text, marginBottom: 10 }}>Delivery Health &amp; Sentiment</div>
          <div style={{ display: 'flex', gap: 18, alignItems: 'center' }}>
            <PillarRing label="Speed" score={dh?.pillars?.speed} green={ov.healthGreenThreshold} amber={ov.healthAmberThreshold} />
            <PillarRing label="Quality" score={dh?.pillars?.quality} green={ov.healthGreenThreshold} amber={ov.healthAmberThreshold} />
            <PillarRing label="Predictability" score={dh?.pillars?.pred} green={ov.healthGreenThreshold} amber={ov.healthAmberThreshold} />
          </div>
          <div style={{ fontSize: 11, color: C.muted, marginTop: 10 }}>
            RAG-banded v1 scores over the real metrics only — full formula lands with the remaining feeds. Sentiment arrives with CSAT.
          </div>
        </Card>
        <Card>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 10 }}>
            <span style={{ fontSize: 13, fontWeight: 800, color: C.text }}>SLA &amp; BAU</span>
            <InfoDot title={AM_INFO.slaAdh.title} body={AM_INFO.slaAdh.body} />
          </div>
          <div style={{ display: 'flex', gap: 16, alignItems: 'baseline' }}>
            <span style={{ fontSize: 30, fontWeight: 900, color: C.text }}>{ov.slaAdherencePct != null ? `${ov.slaAdherencePct}%` : '—'}</span>
            <span style={{ fontSize: 12, color: C.sub }}>SLA adherence (open CRs vs stage targets)</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 10, fontSize: 12 }}>
            <span style={{ color: C.red, fontWeight: 800 }}>{ov.slaBreached} breached</span>
            <span style={{ color: C.amberDeep, fontWeight: 800 }}>{ov.slaNear} near</span>
            <span style={{ color: C.greenDeep, fontWeight: 800 }}>{ov.slaMet} met</span>
            <InfoDot title={AM_INFO.bnm.title} body={AM_INFO.bnm.body} />
          </div>
          <div style={{ fontSize: 12, color: C.sub, marginTop: 12, display: 'grid', gap: 4 }}>
            <span>Last UAT sign-off · last go-live: <strong style={{ color: C.muted }}>— sprint feed pending</strong></span>
            {ov.engagementScore != null && (
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                Engagement score: <strong style={{ color: C.text }}>{ov.engagementScore} / 100</strong>
                <InfoDot title={AM_INFO.eng.title} body={AM_INFO.eng.body} />
              </span>
            )}
            {adoptionUrl && (
              <span>Adoption: <a href={adoptionUrl} target="_blank" rel="noreferrer" style={{ color: C.indigo, fontWeight: 700, textDecoration: 'none' }}>Open dashboard ↗</a></span>
            )}
          </div>
        </Card>
      </div>

      {/* 3 · Milestones — auto-derived from Sprint Scope once the feed exists */}
      <Feature flag="section.client.milestones">
        <SectionHeader title="Milestones & Achievements" subtitle="Auto-derived from Sprint Scope — no manual input" />
        <Card>
          <MilestoneList done={[]} upcoming={[]} />
          <div style={{ fontSize: 11, color: C.muted, marginTop: 8 }}>
            Derives done / upcoming from FSD sign-off, Dev end, UAT and Production dates once the per-account Jira sprint filter lands.
          </div>
        </Card>
      </Feature>

      {/* 4 · Stage SLA — real per-client open CRs, stage names and counts drill */}
      <SectionHeader title="Stage SLA — Open CRs" info={AM_INFO.stages} />
      <TableWrap>
        <table style={{ width: '100%', fontSize: 12, borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ background: C.mintFaint }}>
              {['Stage', 'Open', 'Within SLA', 'Avg aging', 'Target'].map(h => (
                <th key={h} style={{ padding: '9px 12px', textAlign: h === 'Stage' ? 'left' : 'center', fontSize: 11, fontWeight: 800, color: C.muted, textTransform: 'uppercase', letterSpacing: 0.5 }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {(ov.stages ?? []).map((s: any) => (
              <tr key={s.stage} onClick={() => onDrill(`${ov.client} · ${s.stage}`, { stage: s.stage })}
                style={{ borderTop: `1px solid ${C.border}`, cursor: 'pointer' }}>
                <td style={{ padding: '9px 12px', fontWeight: 700, color: C.text }}>{s.stage}</td>
                <td style={{ padding: '9px 12px', textAlign: 'center', fontWeight: 800, color: C.indigo }}>{s.total}</td>
                <td style={{ padding: '9px 12px', textAlign: 'center' }}>{s.withinSlaPct != null ? `${s.withinSlaPct}%` : '—'}</td>
                <td style={{ padding: '9px 12px', textAlign: 'center' }}>{s.avgAgingDays}d</td>
                <td style={{ padding: '9px 12px', textAlign: 'center', color: C.sub }}>{s.targetDays != null ? `${s.targetDays}d` : 'untracked'}</td>
              </tr>
            ))}
            {(ov.stages ?? []).length === 0 && (
              <tr><td colSpan={5} style={{ padding: 14, textAlign: 'center', color: C.sub }}>No open CRs.</td></tr>
            )}
          </tbody>
        </table>
      </TableWrap>

      {/* 5 · Timeline & Health = Release Calendar only (mock §5) — never hidden */}
      <SectionHeader title="Timeline & Health" subtitle="Release calendar across this client's projects" />
      <Card>
        {(ov.releaseCalendar ?? []).length === 0 ? (
          <div style={{ fontSize: 12, color: C.muted, textAlign: 'center', padding: 8 }}>No releases scheduled</div>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: mobile ? '1fr 1fr' : 'repeat(auto-fill, minmax(180px, 1fr))', gap: 8 }}>
            {(ov.releaseCalendar as any[]).map((r: any) => {
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
      </Card>

      {/* 6 · Sprint Scope — placeholder until the per-account Jira filter lands */}
      <Feature flag="section.client.sprint-scope">
        <SectionHeader title="Sprint Scope" subtitle="Grouped by phase: Solutioning → Development → QA → Production release → Delivered" />
        <Card>
          <div style={{ fontSize: 13, color: C.sub }}>
            Awaiting the per-account Jira sprint filter (mock swap point) — rows group by phase from which stage dates are done.
          </div>
        </Card>
      </Feature>

      {/* 7 · CRs & Production Issues — two big tiles */}
      <div style={{ display: 'grid', gap: 14, gridTemplateColumns: mobile ? '1fr' : '1fr 1fr', marginTop: 14 }}>
        <Card onClick={() => onDrill(`${ov.client} — open CRs`, {})} style={{ cursor: 'pointer' }}>
          <div style={{ fontSize: 12, fontWeight: 800, color: C.muted, textTransform: 'uppercase', letterSpacing: 0.5 }}>Total open CRs</div>
          <div style={{ fontSize: 34, fontWeight: 950, color: C.text, margin: '6px 0' }}>{ov.openCrs + ov.clientHold}</div>
          <div style={{ fontSize: 12, color: C.sub }}>BAU <strong>{ov.openBauCrs}</strong> · Launch <strong>{ov.openLaunchCrs}</strong> · hold <strong>{ov.clientHold}</strong> — click for the list</div>
        </Card>
        <Card>
          <div style={{ fontSize: 12, fontWeight: 800, color: C.muted, textTransform: 'uppercase', letterSpacing: 0.5 }}>Total production issues</div>
          <div style={{ fontSize: 34, fontWeight: 950, color: p0 > 0 ? C.red : C.text, margin: '6px 0' }}>{ov.prodOpen}</div>
          <div style={{ fontSize: 12, color: C.sub }}>
            P0 <strong style={{ color: p0 > 0 ? C.red : C.text }}>{p0}</strong>
            {' · '}P1 <strong>{ov.prodBySeverity?.P1 ?? 0}</strong>
            {' · '}P2 <strong>{ov.prodBySeverity?.P2 ?? 0}</strong> — triage lives in Bugs
          </div>
        </Card>
      </div>
    </>
  )
}

// Mock .hs-ring — plain circle with a 4px RAG border (greenAt/amberAt map to tone).
function PillarRing({ label, score, green, amber }: { label: string; score?: number | null; green?: number; amber?: number }) {
  const C = useC()
  const tone = score == null ? null : score >= (green ?? 80) ? 'g' as const : score >= (amber ?? 60) ? 'a' as const : 'r' as const
  return (
    <div style={{ textAlign: 'center', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
      <ScoreRing value={score ?? '—'} tone={tone} label="health" size="sm" />
      <div style={{ fontSize: 10, fontWeight: 800, color: C.muted, textTransform: 'uppercase', letterSpacing: 0.4, marginTop: 6 }}>{label}</div>
    </div>
  )
}

// ── Pillar panes (mock dhPillarPane) ────────────────────────────────────────

function PillarPane({ clientId, clientName, pillar, onDrill }: {
  clientId: number | null
  clientName: string
  pillar: 'speed' | 'quality' | 'pred'
  onDrill: (t: string, f: Omit<AmDrillFilters, 'clientName'>) => void
}) {
  const C = useC()
  const mobile = useIsMobile()
  // exactly two live filters (mock decision): trend window + work type
  const [win, setWin] = useState<'6' | '3'>('6')
  const [dhType, setDhType] = useState<'ALL' | 'LAUNCH' | 'BAU'>('ALL')
  const [weights, setWeights] = useState<Weights>(loadWeights)
  const [weightsOpen, setWeightsOpen] = useState(false)
  const { data: dh, isLoading } = useAmClientDhMetrics(clientId, Number(win), dhType === 'ALL' ? undefined : dhType)

  const score: number | null = dh?.pillars?.[pillar] ?? null
  const months: string[] = dh?.months ?? []
  const trendOf = (values: number[] = []) => months.map((m, i) => ({ label: monthShort(m), value: Number(values[i] ?? 0) }))
  const currentLabel = months.length ? `${monthShort(months[months.length - 1])} (current)` : undefined
  const defs = Object.values(DH_DEFS).filter(d => d.pillar === pillar && d.name !== 'Backlog Aging')

  const saveWeights = (w: Weights) => {
    setWeights(w)
    try { localStorage.setItem('orbit-dh-weights', JSON.stringify(w)) } catch { /* private mode */ }
  }

  return (
    <>
      <Card style={{ marginBottom: 14 }}>
        <div style={{ display: 'flex', gap: 16, alignItems: 'center', flexWrap: 'wrap' }}>
          <PillarRing label={pillar === 'speed' ? 'Speed' : pillar === 'quality' ? 'Quality' : 'Predictability'} score={score} />
          <div style={{ flex: 1, minWidth: 180 }}>
            <div style={{ fontSize: 13, fontWeight: 800, color: C.text }}>
              Pillar score {score != null ? score : '—'} · weight {weights[pillar]}%
            </div>
            <div style={{ fontSize: 11, color: C.muted, marginTop: 2 }}>
              {score == null
                ? 'No real metrics feed this pillar yet — score lands with Phase C/D feeds.'
                : 'RAG-banded v1 over the real metrics below (92 / 62 / 32).'}
              {' '}
              <span onClick={() => setWeightsOpen(true)} style={{ color: C.indigo, fontWeight: 700, cursor: 'pointer' }}>⚙ configure weights</span>
            </div>
          </div>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <Select value={win} onChange={e => setWin(e.target.value as '6' | '3')}
              options={[{ v: '6', l: 'Trend: 6 months' }, { v: '3', l: 'Trend: 3 months' }]} />
            {(['ALL', 'LAUNCH', 'BAU'] as const).map(t => (
              <Btn key={t} variant={dhType === t ? 'primary' : undefined} onClick={() => setDhType(t)}>{t === 'ALL' ? 'All' : t === 'LAUNCH' ? 'Launch' : 'BAU'}</Btn>
            ))}
          </div>
        </div>
      </Card>

      {isLoading ? <LoadingState /> : (
        <div style={{ display: 'grid', gap: 14, gridTemplateColumns: mobile ? '1fr' : 'repeat(auto-fill, minmax(260px, 1fr))' }}>
          {defs.map(d => {
            if (!d.real) {
              return (
                <Feature key={d.name} flag={d.flag!}>
                  <MetricCard name={d.name} pending={d.pending!} info={{ title: d.name, body: dhInfoBody(d) }} />
                </Feature>
              )
            }
            const values: number[] = d.name === 'Lead Time' ? dh?.lead ?? []
              : d.name === 'Throughput' ? dh?.throughput ?? []
              : d.name === 'Production Incidents' ? dh?.incidents ?? []
              : d.name === 'Reopened Issues' ? dh?.reopened ?? []
              : []
            const isSla = d.name === 'SLA Compliance'
            const current = isSla ? dh?.slaCompliancePct : values[values.length - 1]
            return (
              <MetricCard
                key={d.name}
                name={d.name}
                value={current ?? '—'}
                unit={d.unit}
                currentLabel={isSla ? 'current snapshot' : currentLabel}
                rag={current != null ? dhRag(d, Number(current)) : undefined}
                trend={isSla ? undefined : trendOf(values)}
                formula={d.formula}
                thresholds={thrText(d)}
                info={{ title: d.name, body: dhInfoBody(d) }}
                onDrill={() => onDrill(`${clientName} — ${d.name.toLowerCase()} issues`, { type: dhType === 'ALL' ? undefined : dhType })}
              />
            )
          })}
          {pillar === 'speed' && <AgingCard dh={dh} clientName={clientName} dhType={dhType} onDrill={onDrill} />}
        </div>
      )}

      {weightsOpen && (
        <WeightsModal weights={weights} onSave={w => { saveWeights(w); setWeightsOpen(false) }} onClose={() => setWeightsOpen(false)} />
      )}
    </>
  )
}

function AgingCard({ dh, clientName, dhType, onDrill }: {
  dh: any
  clientName: string
  dhType: 'ALL' | 'LAUNCH' | 'BAU'
  onDrill: (t: string, f: Omit<AmDrillFilters, 'clientName'>) => void
}) {
  const C = useC()
  const a = dh?.aging ?? {}
  const d = DH_DEFS.aging
  const buckets = [
    { label: '0–15d', value: Number(a.b0_15 ?? 0), color: C.green },
    { label: '16–30d', value: Number(a.b16_30 ?? 0), color: C.blue },
    { label: '31–60d', value: Number(a.b31_60 ?? 0), color: C.amber },
    { label: '60+d', value: Number(a.b60plus ?? 0), color: C.red },
  ]
  return (
    <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 18, boxShadow: C.shadowSm, padding: 16 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <span style={{ fontSize: 13, fontWeight: 800, color: C.text }}>Backlog Aging</span>
        <InfoDot title={d.name} body={dhInfoBody(d)} />
        <span style={{ marginLeft: 'auto', fontSize: 11, fontWeight: 700, color: C.sub }}>{a.total ?? 0} open</span>
      </div>
      <div style={{ marginTop: 12 }}>
        <SegmentBar segments={buckets} onSegmentClick={() => onDrill(`${clientName} — open backlog`, { type: dhType === 'ALL' ? undefined : dhType })} />
      </div>
      <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginTop: 10, fontSize: 11, color: C.sub }}>
        {buckets.map(b => (
          <span key={b.label} style={{ display: 'inline-flex', alignItems: 'center', gap: 5 }}>
            <i style={{ width: 10, height: 10, borderRadius: 3, background: b.color, display: 'inline-block' }} />
            {b.label} <strong style={{ color: C.text }}>{b.value}</strong>
          </span>
        ))}
      </div>
    </div>
  )
}

function WeightsModal({ weights, onSave, onClose }: {
  weights: Weights
  onSave: (w: Weights) => void
  onClose: () => void
}) {
  const C = useC()
  const [w, setW] = useState<Weights>(weights)
  const total = w.speed + w.quality + w.pred
  const field = (key: keyof Weights, label: string) => (
    <label style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10, fontSize: 13, color: C.text, fontWeight: 600 }}>
      {label}
      <input type="number" min={0} max={100} value={w[key]}
        onChange={e => setW({ ...w, [key]: Number(e.target.value) })}
        style={{ width: 80, minHeight: 34, border: `1px solid ${C.border}`, borderRadius: 8, padding: '0 8px', background: C.white, color: C.text }} />
    </label>
  )
  return (
    <Modal title="Health-score weights" onClose={onClose} width={380}>
      <div style={{ display: 'grid', gap: 12 }}>
        {field('speed', 'Delivery Speed %')}
        {field('quality', 'Delivery Quality %')}
        {field('pred', 'Predictability %')}
        <div style={{ fontSize: 12, color: total === 100 ? C.sub : C.red }}>
          Total {total}% {total !== 100 && '— must sum to 100'}
        </div>
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <Btn onClick={onClose}>Cancel</Btn>
          <Btn variant="primary" onClick={() => total === 100 && onSave(w)}>Save</Btn>
        </div>
      </div>
    </Modal>
  )
}
