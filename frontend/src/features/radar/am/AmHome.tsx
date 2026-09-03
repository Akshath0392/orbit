// Account Management persona home, V3 reforms
// (docs/plan/orbitter-am-v3-reforms-plan.md). Mirrors the approved V3 mock's
// sections AND their exact widget CSS (resouces/orbit-preview-1.html) — grid
// templates, paddings and font sizes are lifted from the mock, not approximated.
// Every section is flag-gated (section.radar.am.*); sections whose data source
// doesn't exist yet (csat, velocity, owners-sm, adoption) ship at NONE.
//
// The POD selector in the benchmarking header (mock state.gPod) scopes the
// ENTIRE page; section-local POD dropdowns lock to a "POD: X" chip while it's
// set. Clicking a benchmark card scopes the Client Scorecard (mock csPod).
import { ReactNode, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useC } from '../../../design/ThemeContext'
import { R } from '../../../design/theme'
import { api } from '../../../api/client'
import { useStore } from '../../../app/store'
import { Feature } from '../../../app/featureFlags'
import { useBreakpoint } from '../../../design/useBreakpoint'
import { SectionHeader } from '../../../design/components/SectionHeader'
import { MatrixTable, MatrixRow } from '../../../design/components/MatrixTable'
import { RankTile } from '../../../design/components/RankTile'
import { ScorecardTile } from '../../../design/components/ScorecardTile'
import { GroupedBars } from '../../../design/components/GroupedBars'
import { DonutChart } from '../../../design/components/DonutChart'
import { Card } from '../../../design/components/Card'
import { Select } from '../../../design/components/Select'
import { Btn } from '../../../design/components/Btn'
import { Modal } from '../../../design/components/Modal'
import { InfoDot } from '../../../design/components/InfoDot'
import { LoadingState } from '../../../design/components/PageState'
import { AmDrillModal } from './AmDrillModal'
import { AmCsatDrillModal } from './AmCsatDrillModal'
import {
  AmDrillFilters, useAmClients, useAmOwnerShare, useAmPodScore,
  useAmProdTrend, useAmProdWeekly, useAmSettings, useAmStageMatrix, useAmSummary,
  useAmVelocity,
} from './useAmApi'
import { AM_INFO, slaTone } from './metricInfo'

type Drill = { title: string; filters: AmDrillFilters } | null
type Pod = { id: number; name: string }

function usePortfolios() {
  return useQuery({
    queryKey: ['portfolios'],
    queryFn: () => api.get('/portfolios').then(r => r.data as Pod[]),
  })
}

// Mock .chip — small annotation pill used in section headers.
function Chip({ children }: { children: ReactNode }) {
  const C = useC()
  return (
    <span style={{
      fontSize: 11, fontWeight: 750, padding: '4px 11px', borderRadius: 999,
      background: C.mint, color: C.tealDeep, letterSpacing: 0.3,
    }}>{children}</span>
  )
}

// Mock .grid gN — fixed column grids that collapse on smaller screens.
function useGridCols(n: 1 | 2 | 3 | 4): string {
  const bp = useBreakpoint()
  if (n === 1 || bp === 'mobile') return '1fr'
  if (bp === 'tablet') return `repeat(${Math.min(n, 2)}, 1fr)`
  return `repeat(${n}, 1fr)`
}

export function AmHome() {
  const C = useC()
  const navigate = useNavigate()
  const { data: pods = [] } = usePortfolios()
  const [gPod, setGPod] = useState<number | null>(null)   // page-level POD scope, null = All PODs
  const [csPod, setCsPod] = useState<number | null>(null) // Client Scorecard scope (mock csPod)
  const [drill, setDrill] = useState<Drill>(null)
  const [csatDrill, setCsatDrill] = useState<{ podId: number | null; podName: string } | null>(null)
  const [ownerTab, setOwnerTab] = useState<'ALL' | 'LAUNCH' | 'BAU'>('ALL')
  const [clientSort, setClientSort] = useState('openCrs')

  const gPodName = pods.find(p => p.id === gPod)?.name ?? null
  const openDrill = (title: string, filters: Omit<AmDrillFilters, 'portfolioId'>) =>
    setDrill({ title, filters: { ...filters, portfolioId: gPod } })

  return (
    <div>
      <Feature flag="section.radar.am.benchmarking">
        <AmBenchmarking
          pods={pods} gPod={gPod} onGPod={setGPod}
          onPodOpen={id => {
            setCsPod(id)   // mock gotoClientScorecard: scope the scorecard, scroll to it
            setTimeout(() => document.getElementById('am-clients')?.scrollIntoView({ behavior: 'smooth' }), 30)
          }}
          onCsatDrill={(podId, podName) => setCsatDrill({ podId, podName })}
        />
      </Feature>

      <Feature flag="section.radar.am.adoption">
        <AmAdoptionCard />
      </Feature>

      <Feature flag="section.radar.am.clients">
        <AmClientScorecard
          gPod={gPod} gPodName={gPodName} pods={pods}
          csPod={csPod} onCsPod={setCsPod}
          sort={clientSort} onSort={setClientSort}
          onOpenClient={(cl) => cl.projectId != null
            ? navigate(`/accounts/${cl.projectId}`)         // mock §4.3 — client master page
            : cl.clientId != null
              ? navigate(`/clients/${cl.clientId}`)
              : openDrill(`${cl.client} — open CRs`, { clientName: cl.client })}
          onDrill={openDrill}
        />
      </Feature>

      <Feature flag="section.radar.am.prod-trend">
        <AmProdTrend pods={pods} gPod={gPod} />
      </Feature>

      <Feature flag="section.radar.am.velocity">
        <AmVelocitySection pods={pods} gPod={gPod} />
      </Feature>

      <Feature flag="section.radar.am.crs-summary">
        <AmCrSummary portfolioId={gPod} gPodName={gPodName} onDrill={openDrill} />
      </Feature>

      <Feature flag="section.radar.am.stages">
        <AmStageSection portfolioId={gPod} gPodName={gPodName} type="BAU" onDrill={openDrill} />
        <AmStageSection portfolioId={gPod} gPodName={gPodName} type="LAUNCH" onDrill={openDrill} />
      </Feature>

      <Feature flag="section.radar.am.owners">
        <SectionHeader
          title="Solutioning Manager view — share of open CRs"
          subtitle="Top 9 + Others · click a name for the CR list"
          info={AM_INFO.owners}
          actions={<PillToggle value={ownerTab} onChange={setOwnerTab}
            options={[['ALL', 'All'], ['LAUNCH', 'Launch'], ['BAU', 'BAU']]} />}
        />
        <AmOwnerDonut portfolioId={gPod} type={ownerTab === 'ALL' ? undefined : ownerTab} dim="sm" onDrill={openDrill} />

        <SectionHeader
          title="Project Management view — share of open CRs"
          subtitle="Top 9 + Others · click a name for the CR list"
        />
        <AmOwnerDonut portfolioId={gPod} type={ownerTab === 'ALL' ? undefined : ownerTab} dim="pjm" onDrill={openDrill} />
      </Feature>

      {drill && <AmDrillModal title={drill.title} filters={drill.filters} onClose={() => setDrill(null)} />}
      {csatDrill && (
        <AmCsatDrillModal portfolioId={csatDrill.podId} podName={csatDrill.podName}
          onClose={() => setCsatDrill(null)} />
      )}
    </div>
  )
}

// W5 Sprint Velocity — committed vs delivered SP per sprint, per POD.
// Self-manages the no-sprint-data state (Sprint field unmapped / no sync yet).
function AmVelocitySection({ pods, gPod }: { pods: Pod[]; gPod: number | null }) {
  const C = useC()
  const scoped: (Pod | null)[] = pods.length === 0 ? [null]
    : gPod != null ? pods.filter(p => p.id === gPod)
    : pods
  const cols2 = useGridCols(2)
  return (
    <>
      <SectionHeader title="Sprint Velocity — Committed vs Delivered"
        subtitle="Story points per sprint · % green ≥90 · amber ≥80 · ⓘ committed = scope at sprint start" info={AM_INFO.velocity} />
      <div style={{ display: 'grid', gap: 16, gridTemplateColumns: gPod != null ? '1fr' : cols2 }}>
        {scoped.map(pod => <AmVelocityPodCard key={pod?.id ?? 'all'} pod={pod} />)}
      </div>
    </>
  )
}

function AmVelocityPodCard({ pod }: { pod: Pod | null }) {
  const C = useC()
  const { data, isLoading } = useAmVelocity(pod?.id ?? null)
  if (isLoading) return <Card><LoadingState /></Card>
  const sprints: any[] = data?.sprints ?? []
  if (!data?.dataAvailable) {
    return (
      <Card>
        <b style={{ fontSize: 15, color: C.text }}>{pod?.name ?? 'All PODs'}</b>
        <div style={{ fontSize: 12.5, color: C.sub, marginTop: 8 }}>
          No sprint data yet — map the Sprint + story-points fields (Jira Sync → Field mapping),
          run a full sync and the changelog backfill. Velocity lights up from the next sprint on.
        </div>
      </Card>
    )
  }
  const pctTone = (pct: number | null) =>
    pct == null ? C.muted : pct >= 90 ? C.greenDeep : pct >= 80 ? C.amberDeep : C.redDeep
  return (
    <Card>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 10 }}>
        <b style={{ fontSize: 15, color: C.text }}>{pod?.name ?? 'All PODs'}</b>
        {data?.velocitySoS?.pct != null && (
          <span style={{ fontSize: 12, fontWeight: 700, color: C.sub }}>
            SoS {data.velocitySoS.pct}%
            {data.velocitySoS.delta != null && (
              <span style={{ marginLeft: 4, color: data.velocitySoS.delta >= 0 ? C.green : C.red }}>
                {data.velocitySoS.delta >= 0 ? '▲' : '▼'}{Math.abs(data.velocitySoS.delta)}
              </span>
            )}
          </span>
        )}
      </div>
      {sprints.map((s: any) => (
        <div key={s.sprintId} style={{ padding: '8px 0', borderTop: `1px dashed ${C.border}` }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 4 }}>
            <span style={{ fontWeight: 700, color: s.live ? C.indigo : C.text }}>
              {s.label ?? `Sprint ${s.sprintId}`}{s.live ? ' · live' : ''}{s.approx ? ' · approx' : ''}
            </span>
            <span style={{ fontWeight: 800, color: pctTone(s.pct) }}>
              {s.delivered} / {s.committed} SP{s.pct != null ? ` · ${s.pct}%` : ''}
            </span>
          </div>
          <div style={{ height: 8, background: C.canvas, borderRadius: 4, overflow: 'hidden' }}>
            <div style={{ height: '100%', borderRadius: 4,
              width: `${s.committed > 0 ? Math.min(100, 100 * s.delivered / s.committed) : 0}%`,
              background: s.live ? C.indigo : C.green }} />
          </div>
          <div style={{ fontSize: 10.5, color: C.muted, marginTop: 3 }}>
            {s.unpointedCount > 0 && `${s.unpointedCount} unpointed items · `}
            {s.live && s.breakdown && `in dev ${s.breakdown.dev} · UAT ${s.breakdown.uat} · prod-ready ${s.breakdown.prod} SP`}
          </div>
        </div>
      ))}
    </Card>
  )
}

// W2 Adoption — deliberately shallow (mock dedup rule: no adoption numbers
// stored in Orbit). Deep-links to the external dashboard from am_settings;
// admins get an inline URL editor, non-admins see nothing until it's set.
function AmAdoptionCard() {
  const C = useC()
  const qc = useQueryClient()
  const isAdmin = useStore(s => s.user?.role) === 'ADMIN'
  const { data: settings } = useAmSettings()
  const [editing, setEditing] = useState(false)
  const [url, setUrl] = useState('')
  const adoptionUrl = settings?.adoptionUrl || null

  if (!adoptionUrl && !isAdmin) return null

  const save = async () => {
    await api.put('/am/settings', { adoptionUrl: url || null })
    qc.invalidateQueries({ queryKey: ['am-settings'] })
    setEditing(false)
  }

  return (
    <>
      <SectionHeader title="Adoption" subtitle="Detail lives in the Adoption dashboard — Orbit stores no adoption numbers (dedup rule)" />
      <Card>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap', fontSize: 13, color: C.sub }}>
          {adoptionUrl
            ? <Btn variant="primary" onClick={() => window.open(adoptionUrl, '_blank')}>Open Adoption dashboard ↗</Btn>
            : <span style={{ color: C.muted }}>Deep link not set — paste the Adoption dashboard URL to light this up for everyone.</span>}
          {isAdmin && !editing && (
            <Btn onClick={() => { setUrl(adoptionUrl ?? ''); setEditing(true) }}>{adoptionUrl ? 'Change URL' : 'Set URL'}</Btn>
          )}
          {isAdmin && editing && (
            <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center', flex: 1, minWidth: 280 }}>
              <input value={url} onChange={e => setUrl(e.target.value)} placeholder="https://…"
                style={{ flex: 1, fontSize: 12, padding: '8px 10px', borderRadius: 8, border: `1px solid ${C.borderMed}`, color: C.text }} />
              <Btn variant="primary" onClick={save}>Save</Btn>
              <Btn onClick={() => setEditing(false)}>Cancel</Btn>
            </span>
          )}
        </div>
      </Card>
    </>
  )
}

// Mock .pill-toggle — bordered segment control.
function PillToggle<T extends string>({ value, onChange, options }: {
  value: T
  onChange: (v: T) => void
  options: [T, string][]
}) {
  const C = useC()
  return (
    <span style={{ display: 'inline-flex', border: `1px solid ${C.borderMed}`, borderRadius: 10, overflow: 'hidden' }}>
      {options.map(([v, label]) => (
        <button key={v} onClick={() => onChange(v)} style={{
          border: 'none', cursor: 'pointer', padding: '8px 14px', fontSize: 12.5, fontWeight: 700,
          background: v === value ? C.indigoPale : C.white, color: v === value ? C.tealDeep : C.muted,
        }}>{label}</button>
      ))}
    </span>
  )
}

// ── Sections ────────────────────────────────────────────────────────────────

function AmBenchmarking({ pods, gPod, onGPod, onPodOpen, onCsatDrill }: {
  pods: Pod[]
  gPod: number | null
  onGPod: (id: number | null) => void
  onPodOpen: (id: number) => void
  onCsatDrill: (podId: number, podName: string) => void
}) {
  const C = useC()
  const { data: scores = [], isLoading } = useAmPodScore()
  const shown = gPod == null ? scores : scores.filter((p: any) => p.portfolioId === gPod)
  const cols4 = useGridCols(4)

  return (
    <>
      <SectionHeader
        title="POD Benchmarking"
        info={AM_INFO.podScore}
        actions={
          <>
            <Chip>score = 60% CSAT + 40% SLA when CSAT entered · else SLA adherence</Chip>
            <Select value={gPod == null ? '' : String(gPod)}
              onChange={e => onGPod(e.target.value === '' ? null : Number(e.target.value))}
              options={[{ v: '', l: 'All PODs' }, ...pods.map(p => ({ v: String(p.id), l: p.name }))]} />
          </>
        }
      />
      {isLoading ? <LoadingState /> : (
        <div style={{ display: 'grid', gap: 16, gridTemplateColumns: gPod == null ? cols4 : '1fr' }}>
          {shown.map((pod: any) => {
            const breached = Number(pod.slaBreached ?? 0)
            const p0 = Number(pod.prodBySeverity?.P0 ?? 0)
            const score = pod.score
            return (
              <RankTile
                key={pod.portfolioId}
                rank={pod.rank}
                lead={pod.rank === 1}
                title={pod.name}
                score={score ?? '—'}
                scoreTone={score == null ? null : score >= 75 ? 'g' : score >= 50 ? 'a' : 'r'}
                scoreTitle={pod.scoreBasis}
                grid3={gPod != null}
                onClick={() => onPodOpen(pod.portfolioId)}
                rows={[
                  // CSAT is opt-in (admin-entered) — with no values the row
                  // degrades to the pure work-mix drill instead of "— / —".
                  {
                    label: pod.csatLaunch != null || pod.csatBau != null
                      ? <span>CSAT L / B <InfoDot title={AM_INFO.csat.title} body={AM_INFO.csat.body} /></span>
                      : 'Work mix',
                    value: (
                      <span style={{ color: C.text }}>
                        {(pod.csatLaunch != null || pod.csatBau != null) && <>{pod.csatLaunch ?? '—'} / {pod.csatBau ?? '—'}{' '}</>}
                        <span
                          onClick={e => { e.stopPropagation(); onCsatDrill(pod.portfolioId, pod.name) }}
                          style={{ color: C.indigo, fontWeight: 700, cursor: 'pointer', fontSize: 12 }}
                          title="Work mix by client — Launch / BAU split Backlog · In progress · Closed">
                          mix →
                        </span>
                      </span>
                    ),
                  },
                  {
                    label: 'SLA breaches (open CRs)',
                    value: (
                      <span style={{ color: breached > 0 ? C.red : C.text }}>
                        {breached} <span style={{ fontSize: 11, color: C.muted, fontWeight: 600 }}>of {pod.slaTracked}</span>
                      </span>
                    ),
                  },
                  {
                    label: 'Production issues open',
                    value: <span style={{ color: p0 > 0 ? C.red : C.text }}>{pod.prodOpen}</span>,
                  },
                  { label: 'CRs open (BAU)', value: pod.openBauCrs },
                  { label: 'Launch stories open', value: pod.openLaunchCrs },
                  {
                    label: 'Velocity (sprint on sprint)',
                    value: pod.velocitySoS?.pct != null ? (
                      <span title={pod.velocitySoS.approx ? 'committed SP approximated (pre-rollout sprint)' : undefined}>
                        {pod.velocitySoS.pct}%
                        {pod.velocitySoS.delta != null && (
                          <span style={{ marginLeft: 4, fontSize: 12, color: pod.velocitySoS.delta >= 0 ? C.green : C.red }}>
                            {pod.velocitySoS.delta >= 0 ? '▲' : '▼'}{Math.abs(pod.velocitySoS.delta)}
                          </span>
                        )}
                      </span>
                    ) : <span style={{ color: C.muted }}>—</span>,
                  },
                ]}
              />
            )
          })}
          {shown.length === 0 && <Card><div style={{ fontSize: 13, color: C.sub }}>No portfolios configured.</div></Card>}
        </div>
      )}
      <Feature flag="section.radar.am.csat">
        <Card style={{ marginTop: 10 }}>
          <div style={{ fontSize: 13, color: C.sub }}>CSAT (Launch / BAU) tiles land here once the survey source is decided.</div>
        </Card>
      </Feature>
    </>
  )
}

function AmClientScorecard({ gPod, gPodName, pods, csPod, onCsPod, sort, onSort, onOpenClient, onDrill }: {
  gPod: number | null
  gPodName: string | null
  pods: Pod[]
  csPod: number | null
  onCsPod: (id: number | null) => void
  sort: string
  onSort: (s: string) => void
  onOpenClient: (tile: { client: string; clientId: number | null; projectId: number | null }) => void
  onDrill: (t: string, f: Omit<AmDrillFilters, 'portfolioId'>) => void
}) {
  const C = useC()
  const navigate = useNavigate()
  // page-level POD wins; otherwise the section's own dropdown (mock csPod)
  const pod = gPod ?? csPod
  const podName = gPod != null ? gPodName : pods.find(p => p.id === csPod)?.name ?? null
  const { data: clients = [], isLoading } = useAmClients(pod)
  const cols3 = useGridCols(3)
  const prodTotal = (cl: any) =>
    cl.openProdBySeverity ? Object.values(cl.openProdBySeverity as Record<string, number>).reduce((s, v) => s + Number(v), 0) : 0
  const sorted = useMemo(() => [...clients].sort((a: any, b: any) => {
    if (sort === 'health') return (a.healthScore ?? 101) - (b.healthScore ?? 101) // worst first, "—" last
    if (sort === 'prod') return prodTotal(b) - prodTotal(a)
    if (sort === 'bau') return b.openBauCrs - a.openBauCrs
    if (sort === 'launch') return b.openLaunchCrs - a.openLaunchCrs
    if (sort === 'aging') return b.avgAgingDays - a.avgAgingDays
    if (sort === 'az') return String(a.client).localeCompare(String(b.client))
    return b.openCrs - a.openCrs
  }), [clients, sort])

  return (
    <>
      <div id="am-clients" />
      <SectionHeader
        title={`Client Scorecard${podName ? ` — ${podName}` : ''}`}
        subtitle="Same numbers as the POD tiles above. Click a client tile for its master page — Overview · Delivery Speed / Quality / Predictability · Teams · Workbench."
        actions={
          <>
            <Select value={sort} onChange={e => onSort(e.target.value)}
              options={[
                { v: 'openCrs', l: 'Sort: open CRs' }, { v: 'health', l: 'Sort: health (worst first)' },
                { v: 'prod', l: 'Sort: Prod open' },
                { v: 'bau', l: 'Sort: BAU CRs' }, { v: 'launch', l: 'Sort: Launch stories' },
                { v: 'aging', l: 'Sort: avg aging' }, { v: 'az', l: 'Sort: A–Z' },
              ]} />
            {gPod != null
              ? <Chip>POD: {gPodName}</Chip>
              : <Select value={csPod == null ? '' : String(csPod)}
                  onChange={e => onCsPod(e.target.value === '' ? null : Number(e.target.value))}
                  options={[{ v: '', l: 'All PODs' }, ...pods.map(p => ({ v: String(p.id), l: p.name }))]} />}
          </>
        }
      />
      {isLoading ? <LoadingState /> : (
        <div style={{ display: 'grid', gap: 16, gridTemplateColumns: cols3 }}>
          {sorted.map((cl: any) => {
            const p0 = Number(cl.openProdBySeverity?.P0 ?? 0)
            return (
              <ScorecardTile
                key={cl.client}
                title={cl.client}
                health={cl.healthScore == null ? null : {
                  value: cl.healthScore,
                  // rule 7 — per-client thresholds from the API, never hardcoded
                  tone: cl.healthScore >= (cl.healthGreenThreshold ?? 80) ? 'g'
                    : cl.healthScore >= (cl.healthAmberThreshold ?? 60) ? 'a' : 'r',
                }}
                subtitle={`${podName ? `${podName} · ` : ''}avg aging ${cl.avgAgingDays}d`}
                minis={[
                  { label: 'Prod open', value: <span style={p0 > 0 ? { color: C.red } : undefined}>{prodTotal(cl)}</span> },
                  { label: 'BAU CRs', value: cl.openBauCrs ?? 0, onClick: () => onDrill(`${cl.client} — open BAU CRs`, { clientName: cl.client, type: 'BAU' }) },
                  { label: 'Launch open', value: cl.openLaunchCrs ?? 0, onClick: () => onDrill(`${cl.client} — open Launch CRs`, { clientName: cl.client, type: 'LAUNCH' }) },
                  {
                    label: 'Velocity SoS',
                    value: cl.velocitySoS?.pct != null
                      ? <span title={cl.velocitySoS.approx ? 'committed SP approximated' : undefined}>{cl.velocitySoS.pct}%</span>
                      : <span style={{ color: C.muted }} title="No sprint data for this client yet">—</span>,
                  },
                ]}
                links={[{ label: 'CR list', onClick: () => cl.clientId != null
                  // mock: drills land on list pages — CR board pre-filtered, not a popup
                  ? navigate(`/cr?clientId=${cl.clientId}`)
                  : onDrill(`${cl.client} — open CRs`, { clientName: cl.client }) }]}
                onClick={() => onOpenClient({ client: cl.client, clientId: cl.clientId ?? null, projectId: cl.projectId ?? null })}
              />
            )
          })}
          {sorted.length === 0 && <Card><div style={{ fontSize: 13, color: C.sub }}>No clients with open work.</div></Card>}
        </div>
      )}
    </>
  )
}

// Quarter presets (mock prodQ: JFM/AMJ/JAS/OND + custom month range).
const QUARTERS: Record<string, [number, number]> = { JFM: [1, 3], AMJ: [4, 6], JAS: [7, 9], OND: [10, 12] }
const MONTH_SHORT = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
const ymLabel = (ym: string) => MONTH_SHORT[Number(ym.slice(5, 7)) - 1] ?? ym

function AmProdTrend({ pods, gPod }: { pods: Pod[]; gPod: number | null }) {
  const C = useC()
  const now = new Date()
  const year = now.getFullYear()
  const defaultQ = Object.keys(QUARTERS)[Math.floor(now.getMonth() / 3)]
  const [win, setWin] = useState(defaultQ)
  const [customFrom, setCustomFrom] = useState('')
  const [customTo, setCustomTo] = useState('')
  const [weekly, setWeekly] = useState<Pod | null>(null)
  const cols2 = useGridCols(2)

  const range = useMemo((): { months: number; from?: string; to?: string } => {
    if (QUARTERS[win]) {
      const [a, b] = QUARTERS[win]
      return { months: 3, from: `${year}-${String(a).padStart(2, '0')}`, to: `${year}-${String(b).padStart(2, '0')}` }
    }
    if (win === 'CUSTOM' && customFrom) return { months: 12, from: customFrom, to: customTo || undefined }
    return { months: 3 }
  }, [win, customFrom, customTo, year])
  const periodLabel = QUARTERS[win] ? `${win} ${year % 100}` : `${customFrom || '?'} → ${customTo || '?'}`
  // weekly drill needs an explicit from — derive it for rolling windows
  const weeklyFrom = range.from ?? (() => {
    const d = new Date(); d.setMonth(d.getMonth() - (range.months - 1))
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
  })()

  const scoped: (Pod | null)[] = pods.length === 0 ? [null]
    : gPod != null ? pods.filter(p => p.id === gPod)
    : pods
  const monthInput: React.CSSProperties = {
    border: `1px solid ${C.borderMed}`, background: C.white, borderRadius: 11,
    padding: '9px 13px', fontSize: 13, fontWeight: 600, color: C.sub,
  }

  return (
    <>
      <SectionHeader
        title="Production Tickets" info={AM_INFO.prod}
        actions={
          <>
            <Select value={win} onChange={e => setWin(e.target.value)}
              options={[
                ...Object.keys(QUARTERS).map(q => ({ v: q, l: `${q} ${year % 100}` })),
                { v: 'CUSTOM', l: 'Custom' },
              ]} />
            {win === 'CUSTOM' && (
              <>
                <input type="month" value={customFrom} onChange={e => setCustomFrom(e.target.value)} style={monthInput} />
                <input type="month" value={customTo} onChange={e => setCustomTo(e.target.value)} style={monthInput} />
              </>
            )}
          </>
        }
      />
      <div style={{ display: 'flex', gap: 16, alignItems: 'center', marginBottom: 12, fontSize: 12.5, fontWeight: 700, color: C.text, flexWrap: 'wrap' }}>
        <span><LegendKey color={C.amber} /> Created</span>
        <span><LegendKey color={C.green} /> Resolved</span>
        <span style={{ color: C.sub, fontWeight: 600 }}>· big number = open now · click a card for week-on-week detail</span>
      </div>
      <div style={{ display: 'grid', gap: 16, gridTemplateColumns: gPod != null ? '1fr' : cols2 }}>
        {scoped.map(pod => (
          <AmProdPodCard key={pod?.id ?? 'all'} pod={pod} range={range} onOpen={() => setWeekly(pod ?? { id: 0, name: 'All PODs' })} />
        ))}
      </div>
      {weekly && (
        <AmProdWeeklyModal
          pod={weekly.id === 0 ? null : weekly} podName={weekly.name}
          from={weeklyFrom} to={range.to} periodLabel={periodLabel}
          onClose={() => setWeekly(null)}
        />
      )}
    </>
  )
}

function LegendKey({ color }: { color: string }) {
  return <i style={{ display: 'inline-block', width: 11, height: 11, borderRadius: 3, verticalAlign: -1, marginRight: 5, background: color }} />
}

// Mock prod card — POD name + 36px "open now" (red when P0s exist), big
// amber/green grouped bars with printed values, P0·P1·P2·net footer.
function AmProdPodCard({ pod, range, onOpen }: {
  pod: Pod | null
  range: { months: number; from?: string; to?: string }
  onOpen: () => void
}) {
  const C = useC()
  const { data, isLoading } = useAmProdTrend(pod?.id ?? null, range.months, range.from, range.to)
  if (isLoading) return <Card><LoadingState /></Card>
  const sev = data?.openBySeverity ?? {}
  const openP0 = Number(sev.P0 ?? 0)
  const created: number[] = data?.created ?? []
  const closed: number[] = data?.closed ?? []
  const net = created.reduce((s, v) => s + v, 0) - closed.reduce((s, v) => s + v, 0)
  return (
    <div onClick={onOpen} style={{
      background: C.white, border: `1px solid ${C.border}`, borderRadius: R.sm,
      boxShadow: C.shadowSm, padding: 18, cursor: 'pointer', minWidth: 0,
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
        <b style={{ fontSize: 16, paddingTop: 4, color: C.text }}>{pod?.name ?? 'All PODs'}</b>
        <div style={{ textAlign: 'right' }}>
          <div style={{ fontSize: 36, fontWeight: 820, letterSpacing: -1.6, lineHeight: 1, color: openP0 > 0 ? C.red : C.text }}>
            {data?.openNow ?? '—'}
          </div>
          <div style={{ fontSize: 10, fontWeight: 800, textTransform: 'uppercase', letterSpacing: 0.4, color: C.sub }}>open now</div>
        </div>
      </div>
      <GroupedBars
        big
        labels={(data?.months ?? []).map(ymLabel)}
        seriesA={{ name: 'Created', values: created, color: C.amber }}
        seriesB={{ name: 'Resolved', values: closed, color: C.green }}
      />
      <div style={{ fontSize: 12, color: C.sub, fontWeight: 600, marginTop: 9 }}>
        {sev.P0 ?? 0} P0 · {sev.P1 ?? 0} P1 · {sev.P2 ?? 0} P2 · net{' '}
        <b style={{ color: net > 0 ? C.redDeep : C.greenDeep }}>{net > 0 ? '+' : ''}{net}</b> in period
      </div>
    </div>
  )
}

// Mock renderProdWow — Created / Resolved / Open blocks, one small chart per
// month, values printed on every bar, shared scale per block.
function AmProdWeeklyModal({ pod, podName, from, to, periodLabel, onClose }: {
  pod: Pod | null
  podName: string
  from: string
  to?: string
  periodLabel: string
  onClose: () => void
}) {
  const C = useC()
  const { data, isLoading } = useAmProdWeekly(pod?.id ?? null, from, to, true)
  const months: any[] = data?.months ?? []
  const block = (title: string, color: string, key: 'created' | 'resolved' | 'open') => {
    const mx = Math.max(1, ...months.flatMap((m: any) => m[key] ?? []))
    return (
      <div style={{ marginBottom: 18 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }}>
          <h3 style={{ margin: 0, fontSize: 15, fontWeight: 750, color: C.text }}>{title}</h3>
          <Chip>week on week</Chip>
        </div>
        <div style={{ display: 'grid', gap: 16, gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))' }}>
          {months.map((m: any) => (
            <div key={m.month} style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: R.sm, boxShadow: C.shadowSm, padding: 18 }}>
              <b style={{ fontSize: 13.5, display: 'block', marginBottom: 8, color: C.text }}>{ymLabel(m.month)}</b>
              <GroupedBars
                labels={['W1', 'W2', 'W3', 'W4']}
                seriesA={{ name: title, values: m[key] ?? [], color }}
                scaleMax={mx}
              />
              <div style={{ fontSize: 11.5, color: C.sub, fontWeight: 600, marginTop: 8 }}>
                {key === 'open'
                  ? <>End of month: <b style={{ color: C.text }}>{(m.open ?? [])[3]}</b> open</>
                  : <>Month total: <b style={{ color: C.text }}>{(m[key] ?? []).reduce((a: number, b: number) => a + b, 0)}</b></>}
              </div>
            </div>
          ))}
        </div>
      </div>
    )
  }
  return (
    <Modal title={`Production Tickets — ${podName} · ${periodLabel}`} width={860} onClose={onClose}>
      {isLoading ? <LoadingState /> : (
        <>
          <div style={{ fontSize: 12, color: C.sub, marginBottom: 14 }}>
            Created, Resolved and Open — week on week for each month. Weekly splits reconcile to the monthly totals;
            the running open count ends at <b style={{ color: C.text }}>{data?.openNow}</b> (open now).
          </div>
          {block('Created', C.amber, 'created')}
          {block('Resolved', C.green, 'resolved')}
          {block('Open', C.indigo, 'open')}
        </>
      )}
    </Modal>
  )
}

function AmCrSummary({ portfolioId, gPodName, onDrill }: {
  portfolioId: number | null
  gPodName: string | null
  onDrill: (t: string, f: Omit<AmDrillFilters, 'portfolioId'>) => void
}) {
  const C = useC()
  const { data, isLoading } = useAmSummary(portfolioId)
  const cols2 = useGridCols(2)
  if (isLoading) return <LoadingState />
  // mock V3: exactly two .card.stat tiles — Open CRs and Client Hold
  const stat = (k: string, v: ReactNode, sub: string, onClick?: () => void) => (
    <div onClick={onClick} style={{
      background: C.white, border: `1px solid ${C.border}`, borderRadius: R.sm,
      boxShadow: C.shadowSm, padding: 18, cursor: onClick ? 'pointer' : 'default',
    }}>
      <span style={{ display: 'block', fontSize: 12, fontWeight: 700, color: C.muted, textTransform: 'uppercase', letterSpacing: 0.4 }}>{k}</span>
      <span style={{ display: 'block', fontSize: 30, fontWeight: 800, letterSpacing: -1, lineHeight: 1, margin: '7px 0', color: C.text }}>{v}</span>
      <span style={{ display: 'block', fontSize: 12.5, color: C.muted }}>{sub}</span>
    </div>
  )
  return (
    <>
      <SectionHeader
        title="CRs Update — Executive Summary"
        actions={gPodName
          ? <Chip>POD: {gPodName}</Chip>
          : <Btn onClick={() => onDrill('All open CRs', {})}>View all open CRs →</Btn>}
      />
      <div style={{ display: 'grid', gap: 16, gridTemplateColumns: cols2 }}>
        {stat('Open CRs', data?.openCrs ?? '—', `${data?.clients ?? '—'} clients`, () => onDrill('All open CRs', {}))}
        {stat('Client Hold', data?.clientHold ?? '—', 'parked with client')}
      </div>
    </>
  )
}

function AmStageSection({ portfolioId, gPodName, type, onDrill }: {
  portfolioId: number | null
  gPodName: string | null
  type: 'BAU' | 'LAUNCH'
  onDrill: (t: string, f: Omit<AmDrillFilters, 'portfolioId'>) => void
}) {
  const C = useC()
  const { data, isLoading } = useAmStageMatrix(portfolioId, type)

  // V3 orientation: clients as ROWS, stages as COLUMNS, SLA % in the stage header
  const stages: any[] = data?.stages ?? []
  const columns = stages.map(s => s.stage)
  const columnSubs = stages.map(s => {
    if (s.withinSlaPct == null) return <span style={{ color: C.muted, fontWeight: 600 }}>no SLA</span>
    const tone = slaTone(Number(s.withinSlaPct))
    const color = tone === 'g' ? C.greenDeep : tone === 'a' ? C.amberDeep : C.redDeep
    return <span style={{ color }} title={`target ${s.targetDays}d`}>SLA {s.withinSlaPct}%</span>
  })
  const rows: MatrixRow[] = (data?.clients ?? []).map((client: string) => ({
    label: client,
    total: Number(data?.clientTotals?.[client] ?? 0),
    cells: Object.fromEntries(stages
      .filter(s => s.cells?.[client])
      .map(s => [s.stage, s.cells[client]])),
  }))

  return (
    <>
      <SectionHeader
        title={`Delivery Stages — ${type === 'BAU' ? 'BAU' : 'Launch'}`}
        info={AM_INFO.stages}
        actions={
          <>
            <Chip>{data?.total ?? '—'} CRs · click a count to drill</Chip>
            {gPodName && <Chip>POD: {gPodName}</Chip>}
          </>
        }
      />
      {isLoading ? <LoadingState /> : (
        <MatrixTable
          rowHeader="Client"
          columns={columns}
          columnSubs={columnSubs}
          rows={rows}
          onCellClick={(client, stage) => onDrill(`${client} · ${stage} (${type})`, { clientName: client, stage, type })}
          onColumnClick={stage => onDrill(`${stage} — all clients (${type})`, { stage, type })}
          onRowClick={client => onDrill(`${client} — open ${type} CRs`, { clientName: client, type })}
        />
      )}
    </>
  )
}

function AmOwnerDonut({ portfolioId, type, dim = 'assignee', onDrill }: {
  portfolioId: number | null
  type?: string
  dim?: 'assignee' | 'sm' | 'pjm'
  onDrill: (t: string, f: Omit<AmDrillFilters, 'portfolioId'>) => void
}) {
  const C = useC()
  const { data, isLoading } = useAmOwnerShare(portfolioId, type, dim)
  if (isLoading) return <LoadingState />
  if (data && data.configured === false) {
    return (
      <Card>
        <div style={{ fontSize: 13, color: C.sub }}>
          The {dim === 'sm' ? 'Solutioning Manager' : 'PjM'} Jira field isn't mapped yet — set it in
          Jira Sync → Field mapping (admin), then run a sync. Until then this view stays empty rather
          than showing assignee data under the wrong name.
        </div>
      </Card>
    )
  }
  const owners: { owner: string; count: number }[] = data?.owners ?? []
  // mock grouping: top 9 + Others
  const top = owners.slice(0, 9).map(o => ({ label: o.owner, value: o.count }))
  const rest = owners.slice(9).reduce((s, o) => s + o.count, 0)
  const donut = rest > 0 ? [...top, { label: 'Others', value: rest }] : top
  const dimLabel = dim === 'sm' ? 'Solutioning Manager' : dim === 'pjm' ? 'PjM' : 'assignee'
  const drillFilter = (owner: string): Omit<AmDrillFilters, 'portfolioId'> =>
    dim === 'sm' ? { smOwner: owner, type } : dim === 'pjm' ? { pjmOwner: owner, type } : { owner, type }

  return (
    <Card>
      {donut.length === 0
        ? <div style={{ fontSize: 13, color: C.sub }}>No open CRs for this filter.</div>
        : <DonutChart
            data={donut}
            holeValue={data?.total ?? 0}
            holeLabel={type ? `${type === 'LAUNCH' ? 'Launch' : 'BAU'} CRs` : 'Open CRs'}
            note={`Share of open CRs by ${dimLabel} · click a name for the CR list`}
            onSliceClick={owner => onDrill(`${owner} — open CRs (${dimLabel})`, drillFilter(owner))}
          />}
    </Card>
  )
}
