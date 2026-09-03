import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useStore } from '../../app/store'
import { api } from '../../api/client'
import { useRoles } from '../../api/hooks'
import { useC } from '../../design/ThemeContext'
import { readSnapshotParams, SNAPSHOT_READY_ATTR } from '../../app/snapshotMode'
import { AmHome } from './am/AmHome'
import type { Colors } from '../../design/theme'

// Mock styles.css token names mapped onto the ThemeContext palette so the
// page picks up dark-mode automatically.
const tFromC = (C: Colors) => ({
  ink:         C.text,
  muted:       C.sub,
  line:        C.border,
  surface:     C.white,
  soft:        C.canvas,
  teal:        C.teal,
  tealLight:   C.tealPale,
  blue:        C.blue,
  amber:       C.amber,
  amberLight:  C.amberPale,
  red:         C.red,
  redLight:    C.redPale,
  green:       C.green,
  greenLight:  C.greenPale,
  canvas:      C.canvas,
  sidebar:     C.canvas,
  shadow:      '0 18px 45px rgba(27,38,49,0.08)',
})
type T = ReturnType<typeof tFromC>

// Persona metadata (sub-label + tab icon) keyed by backend role name. The
// canonical role *list* comes from /admin/roles (see useRoles); this map
// just supplies UI flavour for roles that drive a persona lens. Roles not
// in this map are hidden from the persona switcher (e.g. ADMIN, and CSM —
// the old "Account Management" tile, superseded by the AM V3 view).
const PERSONA_META: Record<string, { sub: string; icon: string }> = {
  AM:          { sub: 'CRs & stage SLAs',  icon: 'A' },
  LEADERSHIP:  { sub: 'Board view',        icon: 'L' },
  ENGINEERING: { sub: 'Capacity risk',     icon: 'E' },
  PM:          { sub: 'Delivery control',  icon: 'P' },
  REVENUE:     { sub: 'Mandays',           icon: 'R' },
}
// Tile order: AM (the live V3 view) first; the rest are work-in-progress
// until they're refactored to the new design.
const PERSONA_ORDER = ['AM', 'LEADERSHIP', 'ENGINEERING', 'PM', 'REVENUE']
// Fallback list used until /admin/roles loads (same personas, no order surprise).
const PERSONA_FALLBACK = [
  { id: 'AM',          label: 'Account Management',  sub: 'CRs & stage SLAs',  icon: 'A' },
  { id: 'LEADERSHIP',  label: 'Leadership',          sub: 'Board view',        icon: 'L' },
  { id: 'ENGINEERING', label: 'Engineering',         sub: 'Capacity risk',     icon: 'E' },
  { id: 'PM',          label: 'Project Management',  sub: 'Delivery control',  icon: 'P' },
  { id: 'REVENUE',     label: 'Revenue',             sub: 'Mandays',           icon: 'R' },
]

function lensConfig(persona: string, summary: any, radar: any) {
  const crs   = summary?.totalCrs      ?? '—'
  const bugs  = summary?.openBugs      ?? '—'
  const uatBugs = summary?.openUatBugs ?? '—'
  const projs = summary?.projectCount  ?? '—'
  const sold  = summary?.soldMandays   ?? '—'
  const burn  = summary?.burnPct       ?? '—'
  const rem   = summary?.remainingMandays ?? '—'
  const capUtil   = summary?.capacityAvgUtil    ?? '—'
  const capAvail  = summary?.capacityAvailable  ?? '—'
  const atRiskAccts = summary?.atRiskAccounts   ?? '—'
  const revenueManaged = summary?.revenueManaged != null
    ? formatRevenue(summary.revenueManaged) : '—'
  const alerts = radar?.alertSummary ?? {}
  switch (persona) {
    case 'LEADERSHIP':  return { headline: 'Board-level delivery health',            focus: 'Portfolio health, revenue exposure, production risk, and governance signals for the selected portfolio.',       metrics: [{ v: alerts.projectsAtRisk ?? '—', l: 'At-risk projects' }, { v: bugs,  l: 'Prod bugs open' }, { v: crs,  l: 'CRs tracked' }] }
    case 'ENGINEERING': return { headline: 'Engineering capacity command centre',    focus: 'Who is available next, where capacity is overloaded, and whether upcoming demand needs rebalancing.',          metrics: [{ v: projs, l: 'Active projects' }, { v: typeof capUtil === 'number' ? `${capUtil}%` : capUtil, l: 'Capacity util' }, { v: capAvail, l: 'Devs available' }] }
    case 'PM':          return { headline: 'Delivery control by portfolio',          focus: 'Monitor CRs, production bugs, UAT defects, launch readiness, and reporting actions.',                          metrics: [{ v: crs,   l: 'Open CRs' },        { v: bugs,  l: 'Prod bugs' },    { v: uatBugs, l: 'UAT bugs' }] }
    case 'REVENUE':     return { headline: 'Mandays, scope and burn control',        focus: 'Connect sold mandays, consumed effort, remaining runway, utilisation, and monthly burn.',                       metrics: [{ v: sold,  l: 'Sold mandays' },     { v: `${burn}%`, l: 'Scope utilised' }, { v: rem,   l: 'Days remaining' }] }
    case 'AM':          return { headline: 'Account delivery operations',            focus: 'Open CRs by client and stage, aging against stage SLAs, production pressure, and owner workload across the POD.', metrics: [{ v: crs, l: 'Open CRs' }, { v: bugs, l: 'Prod bugs open' }, { v: projs, l: 'Accounts' }] }
    default:            return { headline: 'Portfolio intelligence', focus: '', metrics: [] }
  }
}

function formatRevenue(v: any): string {
  const n = Number(v); if (!isFinite(n) || n === 0) return '—'
  if (n >= 1e7) return `₹${(n / 1e7).toFixed(1)}Cr`
  if (n >= 1e5) return `₹${(n / 1e5).toFixed(1)}L`
  return `₹${n.toLocaleString('en-IN')}`
}

function workbenchCards(persona: string, summary: any, radar: any) {
  const crs      = summary?.totalCrs     ?? '—'
  const bugs     = summary?.openBugs     ?? '—'
  const sold     = summary?.soldMandays  ?? '—'
  const burned   = summary?.consumedMandays ?? '—'
  const rem      = summary?.remainingMandays ?? '—'
  const burnPct  = summary?.burnPct      ?? '—'
  const projects = radar?.projects ?? []
  const atRisk   = projects.filter((p: any) => p.risk === 'critical').length
  const alerts   = radar?.alertSummary ?? {}

  const cards: { label: string; value: any; bullets: string[]; action: string }[] = []

  if (persona === 'LEADERSHIP') {
    cards.push({ label: 'Portfolio Health',    value: `${Math.max(0, 100 - atRisk * 12)}%`, bullets: [`${summary?.projectCount ?? '—'} active projects`, `${atRisk} at-risk`, `${bugs} prod bugs`],         action: 'Review before leadership standup' })
    cards.push({ label: 'Critical Exceptions', value: alerts.projectsAtRisk ?? '—',          bullets: [`${alerts.budgetAlerts ?? '—'} budget alerts`, `${bugs} prod bugs`, `${crs} open CRs`],              action: 'Review exceptions before standup' })
    cards.push({ label: 'Open CRs',            value: crs,                                    bullets: [`${summary?.projectCount ?? '—'} projects in scope`, 'Milestone status varies', 'TBC dates tracked'], action: 'Check CR board for delays' })
    cards.push({ label: 'Capacity Signal',     value: '—',                                    bullets: ['Engineering utilisation', 'QA coverage signal', 'Leave impact'],                                    action: 'Review capacity panel' })
  } else if (persona === 'ENGINEERING') {
    cards.push({ label: 'Team Capacity',       value: '—',    bullets: ['Engineers tracked', 'QA coverage', 'Tech leads'],       action: 'QA coverage check for next release' })
    cards.push({ label: 'Team Workload',       value: crs,    bullets: [`${crs} CR items`, `${bugs} prod bugs`, 'UAT blockers'],  action: 'Rebalance workload across projects' })
    cards.push({ label: 'Delivery Velocity',   value: '—',    bullets: ['Sprint committed', 'Sprint completed', 'Spillover'],     action: 'Check velocity trend' })
    cards.push({ label: 'Quality Metrics',     value: '—',    bullets: ['Regression pass rate', 'Escaped defects', 'SLA breaches'], action: 'Stabilise hotfix path' })
  } else if (persona === 'PM') {
    cards.push({ label: 'Overall CRs',         value: crs,   bullets: ['Delayed CRs', 'Timeline TBC', 'Effort pending'],        action: 'Close aging CRs before Friday report' })
    cards.push({ label: 'Production Bugs',     value: bugs,  bullets: ['P0/P1 bugs', 'SLA breached', 'Hotfixes due'],           action: 'Daily updates on critical bugs' })
    cards.push({ label: 'UAT Bugs',            value: '—',   bullets: ['Blockers', 'Major bugs', 'Customer validation pending'], action: 'Push customer sign-off' })
    cards.push({ label: 'Launch Items',        value: '—',   bullets: ['Releases planned', 'Go-live risk', 'Rollback owners'],  action: 'Sequence releases and validate rollback' })
  } else if (persona === 'REVENUE') {
    cards.push({ label: 'Sold Mandays',        value: sold,   bullets: [`Consumed: ${burned}`, `Remaining: ${rem}`, 'Top consuming projects'], action: 'Monitor fastest-consuming projects' })
    cards.push({ label: 'Scope Utilisation',   value: `${burnPct}%`, bullets: ['Monthly burn rate', 'Capacity utilisation', 'Scope risk'], action: 'Watch CRs crossing sold scope' })
    cards.push({ label: 'Consumed Mandays',    value: burned, bullets: ['CR delivery burn', 'Production support burn', 'Governance burn'], action: 'Support burn is elevated' })
    cards.push({ label: 'Remaining Mandays',   value: rem,    bullets: ['Forecast runway', 'At-risk buffer', 'Commercial action'], action: 'Reconfirm commercials for delayed CRs' })
  } else if (persona === 'AM') {
    cards.push({ label: 'Open CRs',            value: crs,   bullets: ['By client & stage above', 'Aging vs stage SLA', 'Client-hold parked'],    action: 'Chase stages breaching their aging target' })
    cards.push({ label: 'Production Pressure', value: bugs,  bullets: ['Created vs closed trend', 'P0/P1 first', 'SLA clock running'],            action: 'Daily update on open P0/P1' })
    cards.push({ label: 'Owner Load',          value: '—',   bullets: ['Assignee × stage matrix', 'Unassigned bucket', 'Launch vs BAU split'],    action: 'Rebalance unassigned CRs' })
    cards.push({ label: 'Client Scorecard',    value: `${summary?.projectCount ?? '—'} accounts`, bullets: ['Sorted by open work', 'Drill to CR list', 'Health rings next phase'], action: 'Review top-loaded clients' })
  }
  return cards
}

function drilldownCells(persona: string, cardIdx: number, summary: any) {
  const crs  = summary?.totalCrs  ?? '—'
  const bugs = summary?.openBugs  ?? '—'
  type Cell = { label: string; value: any; owner: string; status: string }
  const sets: Record<string, Cell[][]> = {
    LEADERSHIP: [
      [{ label:'Portfolio health', value:'—%', owner:'Leadership', status:'Tracked' }, { label:'At-risk projects', value:'—', owner:'PM', status:'Watch' }, { label:'Revenue at risk', value:'—', owner:'AM Lead', status:'Watch' }, { label:'Critical exceptions', value:'—', owner:'PM', status:'Active' }],
      [{ label:'Budget alerts', value:'—', owner:'Revenue', status:'Watch' }, { label:'Prod bugs P0/P1', value:bugs, owner:'Support', status:'Active' }, { label:'SLA breached', value:'—', owner:'PM', status:'Breach' }, { label:'Delayed CRs', value:'—', owner:'PJM', status:'Watch' }],
      [{ label:'Open CRs', value:crs, owner:'PM', status:'Active' }, { label:'Delayed CRs', value:'—', owner:'PM', status:'Watch' }, { label:'Timeline TBC', value:'—', owner:'PM', status:'Needs update' }, { label:'Effort pending', value:'—', owner:'Solution Mgr', status:'Active' }],
      [{ label:'Utilisation', value:'—%', owner:'EM', status:'Tracked' }, { label:'Dev items', value:'—', owner:'TL', status:'On track' }, { label:'QA bandwidth', value:'—', owner:'QA Lead', status:'Tight' }, { label:'Support coverage', value:'—', owner:'Support Mgr', status:'On track' }],
    ],
    PM: [
      [{ label:'Open CRs', value:crs, owner:'PM', status:'Active' }, { label:'Delayed CRs', value:'—', owner:'PM', status:'Watch' }, { label:'Timeline TBC', value:'—', owner:'PM', status:'Needs update' }, { label:'Effort pending', value:'—', owner:'Sol Mgr', status:'Active' }],
      [{ label:'P0/P1 bugs', value:'—', owner:'Support Mgr', status:'Watch' }, { label:'SLA breached', value:'—', owner:'PM', status:'Breach' }, { label:'Hotfixes due', value:'—', owner:'EM', status:'Planned' }, { label:'Client bridge', value:'—', owner:'CSM', status:'Active' }],
      [{ label:'Open UAT bugs', value:'—', owner:'QA Lead', status:'Active' }, { label:'Blockers', value:'—', owner:'QA Lead', status:'Watch' }, { label:'Customer validation', value:'—', owner:'CSM', status:'External' }, { label:'Retest queue', value:'—', owner:'QA Lead', status:'Queued' }],
      [{ label:'Launch items', value:'—', owner:'PM', status:'Planned' }, { label:'Go-live risk', value:'—', owner:'PM', status:'Watch' }, { label:'Release notes', value:'—', owner:'PM', status:'Due' }, { label:'Rollback owners', value:'—', owner:'EM', status:'Needs owner' }],
    ],
    ENGINEERING: [
      [{ label:'Squad load', value:'—%', owner:'EM', status:'Watch' }, { label:'Dev items', value:'—', owner:'TL', status:'On track' }, { label:'QA bandwidth', value:'—', owner:'QA Lead', status:'Tight' }, { label:'Support coverage', value:'—', owner:'Support Mgr', status:'On track' }],
      [{ label:'Open CR workload', value:crs, owner:'EM', status:'High' }, { label:'Prod bug queue', value:bugs, owner:'Support Mgr', status:'Watch' }, { label:'UAT blockers', value:'—', owner:'QA Lead', status:'Watch' }, { label:'Release hot spots', value:'—', owner:'TL', status:'Active' }],
      [{ label:'Committed velocity', value:'—pts', owner:'EM', status:'Planned' }, { label:'Completed velocity', value:'—pts', owner:'TL', status:'On track' }, { label:'Sprint spillover', value:'—pts', owner:'PM', status:'Watch' }, { label:'Cycle time', value:'—days', owner:'TL', status:'Measured' }],
      [{ label:'Regression pass', value:'—%', owner:'QA Lead', status:'Good' }, { label:'Escaped defects', value:'—', owner:'QA Lead', status:'Watch' }, { label:'SLA breaches', value:'—', owner:'Support', status:'Active' }, { label:'Automation coverage', value:'—%', owner:'QA Lead', status:'Improving' }],
    ],
    REVENUE: [
      [{ label:'Sold mandays', value:summary?.soldMandays??'—', owner:'Revenue', status:'Booked' }, { label:'Consumed', value:summary?.consumedMandays??'—', owner:'Delivery', status:'Measured' }, { label:'Remaining', value:summary?.remainingMandays??'—', owner:'Revenue', status:'Available' }, { label:'Top account', value:'—', owner:'AM Lead', status:'Watch' }],
      [{ label:'Scope utilisation', value:`${summary?.burnPct??'—'}%`, owner:'Revenue', status:'Measured' }, { label:'Monthly burn', value:'—MD', owner:'Delivery', status:'Tracked' }, { label:'Capacity utilisation', value:'—%', owner:'Engineering', status:'Measured' }, { label:'Scope risk', value:'—', owner:'Revenue', status:'Watch' }],
      [{ label:'CR delivery burn', value:'—', owner:'PJM', status:'Tracked' }, { label:'Prod support burn', value:'—', owner:'Support', status:'High' }, { label:'Governance burn', value:'—', owner:'AM/CSM', status:'Tracked' }, { label:'Unplanned burn', value:'—', owner:'Revenue', status:'Watch' }],
      [{ label:'Remaining runway', value:'—mo', owner:'Revenue', status:'Forecast' }, { label:'At-risk buffer', value:'—MD', owner:'Revenue', status:'Watch' }, { label:'Commercial action', value:'—', owner:'AM Lead', status:'Required' }, { label:'Next billing', value:'—', owner:'Revenue', status:'Scheduled' }],
    ],
  }
  return (sets[persona] ?? sets['PM'])[cardIdx] ?? []
}

function statusColor(status: string, T: T) {
  const red    = ['Gap']
  const amber  = ['Watch','Tight','High','Needs update','Below plan','Breach','Active','Delayed','Required','Needs owner','External','Due']
  const green  = ['Good','Clear','Low','Stable','Booked','Available','Improving','Measured']
  if (red.includes(status))   return { bg: T.redLight,   fg: T.red   }
  if (amber.includes(status)) return { bg: T.amberLight, fg: T.amber }
  if (green.includes(status)) return { bg: T.greenLight, fg: T.green }
  return { bg: T.tealLight, fg: T.teal }
}

function ragColors(rag: string, T: T) {
  if (rag === 'Red')    return { border: T.red,   badge: { bg: T.redLight,   fg: T.red   } }
  if (rag === 'Amber')  return { border: T.amber, badge: { bg: T.amberLight, fg: T.amber } }
  if (rag === 'Green')  return { border: T.green, badge: { bg: T.greenLight, fg: T.green } }
  return { border: T.blue, badge: { bg: T.tealLight, fg: T.blue } }
}

function riskColor(risk: string, T: T) {
  const r = risk.toLowerCase()
  if (r.includes('sla') || r.includes('breach') || r.includes('red') || r.includes('outage')) return { bg: T.redLight, fg: T.red }
  if (r.includes('delay') || r.includes('aging') || r.includes('capacity') || r.includes('await')) return { bg: T.amberLight, fg: T.amber }
  if (r.includes('release') || r.includes('scope')) return { bg: T.tealLight, fg: T.teal }
  return { bg: T.amberLight, fg: T.amber }
}

// Placeholder shown for lenses that haven't been refactored to the V3 design
// yet. Remove per lens (drop its `showWip` branch) once its refactor lands.
function WorkInProgress({ T, C, label, onGoLive }: {
  T: T
  C: ReturnType<typeof useC>
  label: string
  onGoLive: () => void
}) {
  return (
    <div style={{
      margin: '10px 0 18px', padding: '64px 24px', border: `1px dashed ${T.line}`,
      borderRadius: 8, background: T.surface, boxShadow: T.shadow, textAlign: 'center',
    }}>
      <svg width="140" height="96" viewBox="0 0 140 96" role="img" aria-label="Work in progress"
        style={{ display: 'block', margin: '0 auto 18px' }}>
        {/* traffic cone */}
        <polygon points="46,84 62,84 56,40 52,40" fill={C.amber} />
        <rect x="49.5" y="56" width="9" height="7" fill={C.white} opacity="0.9" />
        <rect x="38" y="84" width="32" height="5" rx="2.5" fill={C.amberDeep} />
        {/* striped barrier */}
        <rect x="76" y="56" width="52" height="14" rx="4" fill={C.mint} stroke={C.borderMed} />
        <polygon points="80,56 90,56 82,70 76,70 76,62" fill={C.indigo} />
        <polygon points="98,56 108,56 100,70 90,70" fill={C.indigo} />
        <polygon points="116,56 126,56 118,70 108,70" fill={C.indigo} />
        <rect x="80" y="70" width="4" height="19" fill={C.borderMed} />
        <rect x="120" y="70" width="4" height="19" fill={C.borderMed} />
        {/* floating gears */}
        <circle cx="94" cy="28" r="12" fill="none" stroke={C.indigo} strokeWidth="4" strokeDasharray="6 4" />
        <circle cx="94" cy="28" r="4" fill={C.indigo} />
        <circle cx="116" cy="14" r="7" fill="none" stroke={C.borderMed} strokeWidth="3" strokeDasharray="4 3" />
      </svg>
      <h2 style={{ margin: 0, fontSize: 20, fontWeight: 800, color: T.ink, letterSpacing: -0.3 }}>Work in progress</h2>
      <p style={{ margin: '8px auto 0', maxWidth: 520, color: T.muted, fontSize: 13.5, lineHeight: 1.6 }}>
        The <b style={{ color: T.ink }}>{label}</b> lens is being refactored to the new Orbit design and is
        temporarily disabled. It will be re-enabled once the refactor lands.
      </p>
      <button onClick={onGoLive} style={{
        marginTop: 18, padding: '10px 16px', borderRadius: 11, border: 'none',
        background: C.indigo, color: '#fff', fontSize: 13, fontWeight: 650, cursor: 'pointer',
      }}>
        Open Account Management →
      </button>
    </div>
  )
}

export function RadarPage() {
  const navigate = useNavigate()
  const C = useC()
  const T = tFromC(C)
  const { user, activePersona, setActivePersona, activePortfolioId, setActivePortfolioId } = useStore()
  const snapshot = readSnapshotParams()

  // Snapshot mode: lens (role) and portfolio come from the URL, not the persisted store.
  useEffect(() => {
    if (!snapshot.enabled) return
    if (snapshot.lens) setActivePersona(snapshot.lens)
    if (snapshot.portfolioId != null) setActivePortfolioId(snapshot.portfolioId)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [snapshot.enabled, snapshot.lens, snapshot.portfolioId])

  const { data: roles = [] } = useRoles()
  const PERSONAS = (roles.length ? roles : PERSONA_FALLBACK.map(p => ({ roleName: p.id, displayName: p.label, screenIds: [] })))
    .map(r => {
      const meta = PERSONA_META[r.roleName]
      if (!meta) return null
      return { id: r.roleName, label: r.displayName, sub: meta.sub, icon: meta.icon }
    })
    .filter((p): p is { id: string; label: string; sub: string; icon: string } => p !== null)
    .sort((a, b) => PERSONA_ORDER.indexOf(a.id) - PERSONA_ORDER.indexOf(b.id))

  const defaultPersona = user?.role && PERSONAS.find(p => p.id === user.role) ? user.role : (PERSONAS[0]?.id ?? 'AM')
  // Sanitize persisted/snapshot personas that no longer have a tile (e.g. CSM).
  const requested = activePersona || defaultPersona
  const persona = PERSONAS.some(p => p.id === requested) ? requested : defaultPersona
  // Non-AM lenses are disabled (work in progress) until they're refactored to
  // the V3 design — except in snapshot mode, where the legacy render stays
  // available so existing Slack snapshot links don't capture a WIP card.
  const showLegacy = persona !== 'AM' && snapshot.enabled
  const showWip = persona !== 'AM' && !snapshot.enabled

  const [selectedCard, setSelectedCard] = useState<number | null>(null)
  const [crTab, setCrTab] = useState<'workflow'|'milestone'|'aging'>('workflow')
  const [rangeDays, setRangeDays] = useState<number>(14)

  const { data: portfolioList = [] } = useQuery({
    queryKey: ['portfolios'],
    queryFn: () => api.get('/portfolios').then(r => r.data as any[]),
  })

  const portfolios = portfolioList as any[]
  const activePid  = activePortfolioId ?? portfolios[0]?.id ?? null

  useEffect(() => {
    if (portfolios.length > 0 && activePortfolioId == null) {
      setActivePortfolioId(portfolios[0].id)
    }
  }, [portfolios])

  // One bundled request replaces the former summary/kpis/accounts/exceptions
  // quartet; the derived names below keep the rest of the page and all child
  // props unchanged.
  const { data: portfolioDashboard } = useQuery({
    queryKey: ['portfolio-dashboard', activePid],
    queryFn:  () => api.get(`/portfolios/${activePid}/dashboard`).then(r => r.data),
    enabled:  activePid != null,
  })

  const portfolioSummary = portfolioDashboard?.summary
  const kpisData         = portfolioDashboard?.kpis
  const accountsData     = (portfolioDashboard?.accounts ?? []) as any[]
  const exceptionsData   = (portfolioDashboard?.exceptions ?? []) as any[]

  const { data: mandays } = useQuery({
    queryKey: ['portfolio-mandays', activePid],
    queryFn:  () => api.get(`/man-days/portfolio-summary?portfolioId=${activePid}`).then(r => r.data),
    enabled:  activePid != null && (persona === 'REVENUE' || persona === 'LEADERSHIP'),
  })

  const { data: radarData } = useQuery({
    queryKey: ['radar'],
    queryFn:  () => api.get('/dashboard/radar').then(r => r.data),
  })

  const { data: crStageSummary } = useQuery({
    queryKey: ['cr-stage-summary', activePid],
    queryFn:  () => api.get(`/cr/stage-summary?portfolioId=${activePid}`).then(r => r.data),
    enabled:  activePid != null && (persona === 'LEADERSHIP' || persona === 'PM'),
  })

  const { data: bugSummary } = useQuery({
    queryKey: ['bug-summary-portfolio', activePid],
    queryFn:  () => api.get(`/bugs/prod/summary?portfolioId=${activePid}`).then(r => r.data),
    enabled:  activePid != null,
  })

  const { data: uatBugSummary } = useQuery({
    queryKey: ['uat-bug-summary-portfolio', activePid],
    queryFn:  () => api.get(`/bugs/uat/summary?portfolioId=${activePid}`).then(r => r.data),
    enabled:  activePid != null,
  })

  const { data: capacityPortfolio } = useQuery({
    queryKey: ['capacity-portfolio-summary', activePid],
    queryFn:  () => api.get(`/capacity/portfolio-summary?portfolioId=${activePid}`).then(r => r.data),
    enabled:  activePid != null,
  })

  const { data: agingBuckets } = useQuery({
    queryKey: ['cr-aging-buckets', activePid],
    queryFn:  () => api.get(`/cr/aging-buckets?portfolioId=${activePid}`).then(r => r.data),
    enabled:  activePid != null,
  })

  const { data: crStages = [] } = useQuery<any[]>({
    queryKey: ['cr-stages'],
    queryFn:  () => api.get('/cr/stages').then(r => r.data),
    staleTime: 600_000,
  })

  const { data: portfolioReleases = [] } = useQuery({
    queryKey: ['portfolio-releases', activePid, rangeDays],
    queryFn:  () => api.get(`/portfolios/${activePid}/releases?days=${rangeDays}`).then(r => r.data as any[]),
    enabled:  activePid != null,
  })

  const { data: portfolioGovernance = [] } = useQuery({
    queryKey: ['portfolio-governance', activePid],
    queryFn:  () => api.get(`/portfolios/${activePid}/governance`).then(r => r.data as any[]),
    enabled:  activePid != null,
  })

  // Use open-only counts from kpisData; fall back to bugSummary if kpis not loaded yet
  const openBugs = kpisData?.prodBugs ?? (
    bugSummary?.p0Open != null
      ? ((bugSummary.p0Open ?? 0) + (bugSummary.p1Open ?? 0) + (bugSummary.p2Open ?? 0) + (bugSummary.p3Open ?? 0))
      : portfolioSummary?.openBugs
  )
  const openCrs = kpisData?.openCrs ?? portfolioSummary?.totalCrs

  const openUatBugs = uatBugSummary
    ? ((uatBugSummary.p0Open ?? 0) + (uatBugSummary.p1Open ?? 0) + (uatBugSummary.p2Open ?? 0) + (uatBugSummary.p3Open ?? 0))
    : null

  const summary = {
    ...(portfolioSummary ?? {}),
    ...(mandays ?? {}),
    totalCrs: openCrs,
    openBugs,
    openUatBugs:        openUatBugs ?? '—',
    atRiskAccounts:     portfolioSummary?.atRiskAccounts ?? '—',
    revenueManaged:     portfolioSummary?.revenueManaged,
    capacityAvgUtil:    capacityPortfolio?.avgUtil ?? '—',
    capacityAvailable:  capacityPortfolio?.available ?? '—',
  }

  const lens     = lensConfig(persona, summary, radarData)
  const cards    = workbenchCards(persona, summary, radarData)
  const activePortfolio = portfolios.find((p: any) => p.id === activePid)

  const stageMap   = crStageSummary ?? {}
  // Driven by /cr/stages (lifecycle_mappings table) — see V72 migration.
  // Falls back to the legacy hardcoded order only if the endpoint hasn't loaded yet.
  const stageOrder: string[] = crStages.length
    ? crStages.map(s => s.name)
    : ['Received','More Information','Validated','Business Solutioning','FSD Approval','Solutioning','Effort Estimation','Approval','To Do','In Progress','UAT Released','Customer Validation','Closed','Client Hold','Invalid']

  const kpis = kpisData ?? {}
  const healthPct    = kpis.healthPct    ?? (activePortfolio?.healthPct ?? '—')
  const accountCount = kpis.accountCount ?? (portfolioSummary?.projectCount ?? '—')

  const p0 = kpis.p0 ?? bugSummary?.p0Open ?? 0
  const p1 = kpis.p1 ?? bugSummary?.p1Open ?? 0
  const p2 = kpis.p2 ?? bugSummary?.p2Open ?? 0
  const p3 = kpis.p3 ?? bugSummary?.p3Open ?? 0
  const slaBreached = kpis.slaBreached ?? bugSummary?.slaBreached ?? 0
  const slaAtRisk   = kpis.slaAtRisk   ?? bugSummary?.slaAtRisk   ?? 0
  const totalOpenBugs = p0 + p1 + p2 + p3

  const revenueAtRisk = kpis.revenueAtRisk
  const capLoad       = capacityPortfolio?.avgUtil
  const KPI_TILES = [
    { label: 'POD Health',        value: healthPct !== '—' ? `${healthPct}%` : '—',    detail: `${accountCount} active accounts`,           status: (typeof healthPct === 'number' && healthPct < 80) ? 'warn' : 'good', nav: '/cr' },
    { label: 'Open CRs',          value: openCrs ?? '—',                                detail: 'Excluding Closed / Invalid',                status: 'warn',   nav: `/cr?portfolioId=${activePid}` },
    { label: 'Open Prod Bugs',     value: totalOpenBugs || openBugs || '—',             detail: `${slaBreached} SLA breached`,               status: (p0 > 0 || slaBreached > 0) ? 'danger' : 'warn', nav: '/bugs' },
    { label: 'Revenue at Risk',    value: revenueAtRisk != null ? formatRevenue(revenueAtRisk) : '—', detail: `${summary?.atRiskAccounts ?? 0} accounts below amber`, status: Number(revenueAtRisk) > 0 ? 'danger' : 'warn', nav: '/reports' },
    { label: 'Capacity Load',      value: capLoad != null ? `${capLoad}%` : '—',         detail: `${capacityPortfolio?.overloaded ?? 0} devs overloaded`, status: Number(capLoad) > 85 ? 'danger' : 'warn', nav: '/capacity' },
  ]

  const PROD_ROWS = [
    { sev: 'P0', count: p0, label: 'Customer outage', sla: p0 > 0 && slaBreached > 0 ? 'Breached' : 'On track', hot: true,  nav: '/bugs?severity=P0' },
    { sev: 'P1', count: p1, label: 'High impact',     sla: `${slaAtRisk} at risk`,                                hot: true,  nav: '/bugs?severity=P1' },
    { sev: 'P2', count: p2, label: 'Active defects',  sla: 'On track',                                            hot: false, nav: '/bugs?severity=P2' },
    { sev: 'P3', count: p3, label: 'Queued fixes',    sla: 'On track',                                            hot: false, nav: '/bugs?severity=P3' },
  ]

  // Snapshot sidecar polls for this attribute before capturing. We're ready once the
  // portfolio summary AND radar feed have resolved for the requested portfolio.
  const snapshotReady = !snapshot.enabled
    ? false
    : (activePid != null && portfolioSummary != null && radarData != null)

  return (
    <div
      {...(snapshotReady ? { [SNAPSHOT_READY_ATTR]: 'true' } : {})}
      style={{ padding: 28, background: T.canvas, minHeight: '100vh', fontFamily: 'Inter, ui-sans-serif, system-ui, sans-serif' }}>

      {/* ── Header ─────────────────────────────────────────────────────────── */}
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 24, marginBottom: 4 }}>
        <div>
          <p style={{ margin: 0, color: T.teal, fontSize: 12, fontWeight: 800, textTransform: 'uppercase', letterSpacing: 0.8 }}>Orbitter</p>
          <h1 style={{ margin: '4px 0 0', fontSize: 28, fontWeight: 950, color: T.ink, lineHeight: 1.1, letterSpacing: -0.5 }}>
            Delivery Command Center
          </h1>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap', justifyContent: 'flex-end', flexShrink: 0 }}>
          <span style={{ color: T.muted, fontSize: 12, fontWeight: 700 }}>Range</span>
          <select
            value={rangeDays}
            onChange={e => setRangeDays(Number(e.target.value))}
            style={{ minHeight: 38, border: `1px solid ${T.line}`, borderRadius: 8, background: T.surface, padding: '0 34px 0 12px', color: T.ink, font: 'inherit' }}>
            <option value={14}>Current sprint (14d)</option>
            <option value={7}>Next 7 days</option>
            <option value={30}>Next 30 days</option>
            <option value={90}>Quarter to date</option>
          </select>
          <button onClick={() => navigate('/reports')} style={{ minHeight: 38, padding: '0 16px', borderRadius: 8, border: `1px solid ${T.line}`, background: T.surface, color: T.ink, fontSize: 13, fontWeight: 700, cursor: 'pointer' }}>⇩</button>
          <button onClick={() => navigate('/reports')} style={{ minHeight: 38, padding: '0 16px', borderRadius: 8, border: 'none', background: T.teal, color: '#fff', fontSize: 13, fontWeight: 800, cursor: 'pointer' }}>
            Generate report
          </button>
        </div>
      </header>

      {/* ── Persona switcher ───────────────────────────────────────────────── */}
      <div style={{ display: 'grid', gridTemplateColumns: `repeat(${Math.max(1, PERSONAS.length)}, minmax(140px, 1fr))`, gap: 10, padding: '22px 0 12px' }}>
        {PERSONAS.map(p => {
          const active = persona === p.id
          return (
            <button key={p.id} onClick={() => { setActivePersona(p.id); setSelectedCard(null) }} style={{
              display: 'grid', gridTemplateColumns: '36px minmax(0,1fr) auto',
              alignItems: 'center', gap: 12, minHeight: 78, padding: 12,
              border: `1px solid ${active ? T.teal : T.line}`, borderRadius: 8, cursor: 'pointer',
              background: active ? T.teal : T.surface, color: active ? 'rgba(255,255,255,0.85)' : T.muted,
              fontWeight: 700, textAlign: 'left', transition: 'all 160ms ease',
              boxShadow: active ? '0 4px 18px rgba(91,124,250,0.22)' : 'none',
            }}>
              <span style={{ display: 'grid', placeItems: 'center', width: 36, height: 36, borderRadius: 8, background: active ? 'rgba(255,255,255,0.14)' : C.indigoPale, color: active ? '#fff' : T.teal, fontWeight: 900, fontSize: 16 }}>{p.icon}</span>
              <span>
                <strong style={{ display: 'block', color: active ? '#fff' : T.ink, fontSize: 13 }}>{p.label}</strong>
                <small style={{ display: 'block', marginTop: 2, color: active ? 'rgba(255,255,255,0.85)' : T.muted, fontSize: 11 }}>{p.sub}</small>
              </span>
              <b style={{ display: 'grid', placeItems: 'center', minWidth: 34, height: 34, padding: '0 8px', borderRadius: 999, background: active ? 'rgba(255,255,255,0.14)' : T.soft, color: active ? '#fff' : T.teal, fontSize: 12 }}>
                {p.id === 'LEADERSHIP' ? (radarData?.alertSummary?.projectsAtRisk ?? '—') :
                 p.id === 'PM'         ? (summary.totalCrs ?? '—') :
                 p.id === 'REVENUE'    ? '₹' : '—'}
              </b>
            </button>
          )
        })}
      </div>

      {/* ── Portfolio / POD selector (legacy lenses only — AmHome has its own POD scope) ── */}
      {showLegacy && (
      <div style={{ display: 'grid', gap: 14, margin: '10px 0 18px', padding: 18, border: `1px solid ${T.line}`, borderRadius: 8, background: T.sidebar, boxShadow: T.shadow }}>
        <div>
          <p style={{ margin: 0, color: T.teal, fontSize: 11, fontWeight: 800, textTransform: 'uppercase', letterSpacing: 0.8 }}>POD context</p>
          <h2 style={{ margin: '3px 0 0', fontSize: 24, fontWeight: 700, color: T.ink }}>{activePortfolio?.name ?? '—'}</h2>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: `repeat(${Math.max(1, portfolios.length)}, minmax(0,1fr))`, gap: 12 }}>
          {portfolios.map((pod: any) => {
            const isActive = pod.id === activePid
            const podHealth = pod.id === activePid ? (healthPct !== '—' ? healthPct : pod.healthPct ?? '—') : pod.healthPct ?? '—'
            const podCount  = pod.id === activePid ? accountCount : pod.projectCount ?? '—'
            return (
              <button key={pod.id} onClick={() => { setActivePortfolioId(pod.id); setSelectedCard(null) }} style={{
                minHeight: 112, display: 'grid', alignContent: 'center', gap: 6, padding: 18,
                border: `1px solid ${isActive ? T.teal : T.line}`, borderRadius: 8, cursor: 'pointer',
                background: isActive ? T.teal : T.surface, textAlign: 'left', transition: 'all 160ms ease',
                boxShadow: isActive ? '0 4px 18px rgba(91,124,250,0.22)' : 'none',
              }}>
                <strong style={{ display: 'block', color: isActive ? '#fff' : T.ink, fontSize: 26, lineHeight: 1, fontWeight: 950 }}>{pod.name}</strong>
                <span style={{ color: isActive ? 'rgba(255,255,255,0.85)' : T.muted, fontSize: 13, fontWeight: 700 }}>
                  {podCount !== '—' ? `${podCount} account${podCount === 1 ? '' : 's'}` : pod.clientName ?? 'Portfolio'}
                  {podHealth !== '—' ? ` · ${podHealth}% health` : ''}
                </span>
              </button>
            )
          })}
          {portfolios.length === 0 && (
            <div style={{ padding: 18, border: `1px dashed ${T.line}`, borderRadius: 8, color: T.muted, fontSize: 13 }}>
              No portfolios configured — add one in Admin → Portfolio setup.
            </div>
          )}
        </div>
      </div>
      )}

      {/* ── LEADERSHIP: KPI tiles ─────────────────────────────────────────── */}
      {showLegacy && persona === 'LEADERSHIP' && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, minmax(140px,1fr))', gap: 12, marginBottom: 18 }}>
          {KPI_TILES.map(tile => {
            const fg = tile.status === 'danger' ? T.red : tile.status === 'warn' ? T.amber : T.green
            return (
              <div key={tile.label} onClick={() => navigate(tile.nav)} style={{ padding: 18, border: `1px solid ${T.line}`, borderRadius: 8, background: T.surface, boxShadow: T.shadow, cursor: 'pointer', transition: 'box-shadow 160ms ease' }}
                onMouseEnter={e => (e.currentTarget as HTMLElement).style.boxShadow = '0 4px 20px rgba(91,124,250,0.18)'}
                onMouseLeave={e => (e.currentTarget as HTMLElement).style.boxShadow = T.shadow}>
                <p style={{ margin: '0 0 8px', fontSize: 12, fontWeight: 800, color: T.muted, textTransform: 'uppercase', letterSpacing: 0.5 }}>{tile.label}</p>
                <strong style={{ display: 'block', fontSize: 34, fontWeight: 950, color: T.ink, lineHeight: 1, marginBottom: 6 }}>{tile.value}</strong>
                <span style={{ fontSize: 13, fontWeight: 700, color: fg }}>{tile.detail}</span>
              </div>
            )
          })}
        </div>
      )}

      {/* ── Lens headline + metrics (legacy lenses only) ──────────────────── */}
      {showLegacy && (
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 24, margin: '0 0 18px', padding: 22, border: `1px solid ${T.line}`, borderRadius: 8, background: `linear-gradient(135deg,${T.surface},${T.soft})` }}>
        <div style={{ flex: 1 }}>
          <p style={{ margin: 0, color: T.teal, fontSize: 11, fontWeight: 800, textTransform: 'uppercase', letterSpacing: 0.8 }}>{PERSONAS.find(p => p.id === persona)?.label ?? ''} lens</p>
          <h2 style={{ margin: '4px 0 0', fontSize: 20, fontWeight: 700, color: T.ink }}>{lens.headline}</h2>
          <p style={{ maxWidth: 780, marginBottom: 0, color: T.muted, fontSize: 14 }}>{lens.focus}</p>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(95px,1fr))', gap: 10, minWidth: 'min(360px,100%)', alignSelf: 'flex-start' }}>
          {lens.metrics.map((m: any) => (
            <span key={m.l} style={{ padding: 12, border: `1px solid ${T.line}`, borderRadius: 8, background: 'rgba(255,255,255,0.82)' }}>
              <strong style={{ display: 'block', fontSize: 22, fontWeight: 950, color: T.ink }}>{m.v}</strong>
              <small style={{ color: T.muted, fontSize: 12 }}>{m.l}</small>
            </span>
          ))}
        </div>
      </div>
      )}

      {/* ── ACCOUNT MANAGEMENT (the live V3 view — renders directly, no POD
             context / lens / workbench chrome) ─────────────────────────────── */}
      {persona === 'AM' && <AmHome />}

      {/* ── Other lenses: work in progress until refactored to the V3 design ── */}
      {showWip && (
        <WorkInProgress
          T={T} C={C}
          label={PERSONAS.find(p => p.id === persona)?.label ?? persona}
          onGoLive={() => { setActivePersona('AM'); setSelectedCard(null) }}
        />
      )}

      {/* ── Workbench cards + drilldown (legacy lenses only) ──────────────── */}
      {showLegacy && (
      <div style={{ display: 'grid', gap: 14, marginBottom: 18, padding: 18, border: `1px solid ${T.line}`, borderRadius: 8, background: T.surface, boxShadow: T.shadow }}>
        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 16 }}>
          <div>
            <p style={{ margin: 0, color: T.teal, fontSize: 11, fontWeight: 800, textTransform: 'uppercase', letterSpacing: 0.8 }}>{PERSONAS.find(p => p.id === persona)?.label ?? ''} workbench</p>
            <h2 style={{ margin: '4px 0 0', fontSize: 20, fontWeight: 700, color: T.ink }}>{activePortfolio?.name ?? 'Portfolio'} · {PERSONAS.find(p => p.id === persona)?.sub}</h2>
          </div>
          <span style={{ maxWidth: 360, color: T.muted, fontSize: 12, fontWeight: 800, textAlign: 'right' }}>
            Auto-ranked from Jira, releases, and capacity signals
          </span>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(0,1fr))', gap: 10 }}>
          {cards.map((card, idx) => {
            const isSel = selectedCard === idx
            return (
              <button key={idx} onClick={() => setSelectedCard(isSel ? null : idx)} style={{
                display: 'grid', gap: 8, minHeight: 160, padding: 14, textAlign: 'left',
                border: `1px solid ${isSel ? 'rgba(91,124,250,0.75)' : T.line}`, borderRadius: 8,
                background: isSel ? T.tealLight : T.sidebar, cursor: 'pointer',
                boxShadow: isSel ? 'inset 0 0 0 2px rgba(91,124,250,0.08)' : 'none',
                transition: 'all 160ms ease',
              }}>
                <span style={{ color: T.teal, fontSize: 11, fontWeight: 900, textTransform: 'uppercase' }}>{card.label}</span>
                <strong style={{ fontSize: 17, fontWeight: 700, color: T.ink }}>{String(card.value)}</strong>
                <ul style={{ display: 'grid', gap: 5, margin: 0, paddingLeft: 17, color: T.muted, fontSize: 13 }}>
                  {card.bullets.map((b, i) => <li key={i}>{b}</li>)}
                </ul>
                <b style={{ alignSelf: 'end', color: T.ink, fontSize: 12 }}>{card.action}</b>
              </button>
            )
          })}
        </div>

        {selectedCard != null && (
          <div style={{ display: 'grid', gap: 12, padding: 14, border: `1px solid ${T.line}`, borderRadius: 8, background: T.soft }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16 }}>
              <div>
                <p style={{ margin: 0, color: T.teal, fontSize: 11, fontWeight: 800, textTransform: 'uppercase', letterSpacing: 0.8 }}>Drilldown</p>
                <h3 style={{ margin: '3px 0 0', fontSize: 18, fontWeight: 700, color: T.ink }}>{cards[selectedCard]?.label}</h3>
              </div>
              <span style={{ color: T.muted, fontSize: 12, fontWeight: 800, textAlign: 'right' }}>Live from Orbit data</span>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(0,1fr))', gap: 10 }}>
              {drilldownCells(persona, selectedCard, summary).map((cell: any, i: number) => {
                const sc = statusColor(cell.status, T)
                return (
                  <div key={i} style={{ display: 'grid', gap: 6, padding: 12, border: `1px solid ${T.line}`, borderRadius: 8, background: T.surface }}>
                    <span style={{ color: T.muted, fontSize: 12, fontWeight: 900 }}>{cell.label}</span>
                    <strong style={{ fontSize: 20, fontWeight: 950, color: T.ink }}>{cell.value}</strong>
                    <small style={{ color: T.muted, fontSize: 12 }}>{cell.owner}</small>
                    <b style={{ justifySelf: 'start', padding: '4px 8px', borderRadius: 999, background: sc.bg, color: sc.fg, fontSize: 11, fontWeight: 700 }}>{cell.status}</b>
                  </div>
                )
              })}
            </div>
          </div>
        )}
      </div>
      )}

      {/* ── LEADERSHIP sections ───────────────────────────────────────────── */}
      {showLegacy && persona === 'LEADERSHIP' && (
        <>
          {/* Account Details */}
          <section style={{ marginBottom: 20 }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
              <div>
                <p style={{ margin: 0, color: T.teal, fontSize: 11, fontWeight: 800, textTransform: 'uppercase', letterSpacing: 0.8 }}>Account details</p>
                <h3 style={{ margin: '2px 0 0', fontSize: 18, fontWeight: 800, color: T.ink }}>{activePortfolio?.name ?? 'Portfolio'} · {(accountsData as any[]).length} account{(accountsData as any[]).length !== 1 ? 's' : ''}</h3>
              </div>
              <button onClick={() => navigate('/reports')} style={{ fontSize: 12, padding: '5px 12px', borderRadius: 8, border: `1px solid ${T.line}`, background: T.surface, color: T.muted, cursor: 'pointer', fontWeight: 600 }}>Export briefing</button>
            </div>
            {(accountsData as any[]).length === 0 ? (
              <div style={{ padding: 24, border: `1px dashed ${T.line}`, borderRadius: 8, color: T.muted, fontSize: 13, textAlign: 'center' }}>
                No accounts found for this portfolio. Assign projects in Admin → Portfolio.
              </div>
            ) : (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px,1fr))', gap: 14 }}>
                {(accountsData as any[]).map((acct: any) => {
                  const { border, badge } = ragColors(acct.rag ?? 'Green', T)
                  const stageLabel: Record<string,string> = {
                    PRE_LAUNCH: 'Pre-launch', HYPERCARE: 'Hypercare',
                    STEADY_STATE: 'Steady-state', AT_RISK: 'At-risk',
                  }
                  const stageColor: Record<string,string> = {
                    PRE_LAUNCH: T.blue, HYPERCARE: T.amber,
                    STEADY_STATE: T.green, AT_RISK: T.red,
                  }
                  const stg = acct.stage ?? 'STEADY_STATE'
                  return (
                    <div key={acct.id} onClick={() => navigate(`/accounts/${acct.id}`)} style={{
                      background: T.surface, borderRadius: 8, cursor: 'pointer',
                      border: `1px solid ${T.line}`, borderTop: `4px solid ${border}`,
                      boxShadow: T.shadow, padding: '14px 14px 16px', transition: 'all 160ms ease',
                    }}>
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                        <div>
                          <div style={{ fontSize: 15, fontWeight: 700, color: T.ink }}>{acct.name}</div>
                          <div style={{ fontSize: 11, color: T.muted, marginTop: 2 }}>{acct.clientName}</div>
                        </div>
                        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4 }}>
                          <span style={{ padding: '4px 10px', borderRadius: 999, fontSize: 11, fontWeight: 800, background: badge.bg, color: badge.fg }}>
                            {acct.rag}
                          </span>
                          <span style={{ padding: '2px 8px', borderRadius: 999, fontSize: 10, fontWeight: 700, background: stageColor[stg] + '18', color: stageColor[stg] }}>
                            {stageLabel[stg] ?? stg}
                          </span>
                        </div>
                      </div>
                      {/* Health bar */}
                      {acct.healthPct != null && (
                        <div style={{ marginBottom: 10 }}>
                          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: T.muted, marginBottom: 3 }}>
                            <span>Health</span>
                            <strong style={{ color: acct.healthPct < 50 ? T.red : acct.healthPct < 75 ? T.amber : T.green }}>{acct.healthPct}%</strong>
                          </div>
                          <div style={{ height: 5, background: T.line, borderRadius: 3, overflow: 'hidden' }}>
                            <div style={{ height: '100%', width: `${acct.healthPct}%`, borderRadius: 3, transition: 'width 400ms ease', background: acct.healthPct < 50 ? T.red : acct.healthPct < 75 ? T.amber : T.green }} />
                          </div>
                        </div>
                      )}
                      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 8 }}>
                        {[['Open CRs', acct.openCrs], ['Prod Bugs', acct.prodBugs], ['SLA Breach', acct.slaBreached]].map(([l, v]: any) => (
                          <div key={l} style={{ padding: '10px 8px', borderRadius: 6, background: T.soft, textAlign: 'center' }}>
                            <div style={{ fontSize: 20, fontWeight: 800, color: (l === 'Prod Bugs' || l === 'SLA Breach') && v > 0 ? T.red : T.ink, lineHeight: 1 }}>{v ?? 0}</div>
                            <div style={{ fontSize: 11, color: T.muted, marginTop: 3 }}>{l}</div>
                          </div>
                        ))}
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </section>

          {/* CR Governance */}
          <section style={{ marginBottom: 20, border: `1px solid ${T.line}`, borderRadius: 8, background: T.surface, overflow: 'hidden' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12, padding: 14, borderBottom: `1px solid ${T.line}`, background: T.sidebar }}>
              <div>
                <p style={{ margin: 0, color: T.teal, fontSize: 11, fontWeight: 800, textTransform: 'uppercase', letterSpacing: 0.8 }}>CR Governance</p>
                <h3 style={{ margin: '2px 0 0', fontSize: 22, fontWeight: 950, color: T.ink }}>{activePortfolio?.name ?? 'Portfolio'} governance views</h3>
              </div>
              <div style={{ display: 'flex', padding: 3, gap: 3, background: T.soft, borderRadius: 8 }}>
                {(['workflow','milestone','aging'] as const).map(tab => (
                  <button key={tab} onClick={() => setCrTab(tab)} style={{ minHeight: 30, padding: '0 12px', border: 0, borderRadius: 6, background: crTab === tab ? T.surface : 'transparent', color: crTab === tab ? T.ink : T.muted, fontWeight: 800, cursor: 'pointer', boxShadow: crTab === tab ? '0 2px 10px rgba(0,0,0,0.08)' : 'none', textTransform: 'capitalize' }}>{tab}</button>
                ))}
              </div>
            </div>
            <div style={{ padding: 16 }}>
              {crTab === 'workflow' && (
                <>
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(130px,1fr))', gap: 10, marginBottom: 14 }}>
                    {stageOrder.map((stage, i) => {
                      const count = stageMap[stage] ?? 0
                      const isHold  = stage.toLowerCase().includes('hold')
                      const isDone  = ['Closed','Canceled','Invalid'].includes(stage)
                      const isReady = ['UAT Released','Customer Validation'].includes(stage)
                      const bg = isHold ? T.redLight : isDone ? T.greenLight : isReady ? C.bluePale : T.tealLight
                      const bd = isHold ? 'rgba(194,65,58,0.32)' : isDone ? 'rgba(24,128,82,0.35)' : isReady ? 'rgba(37,99,235,0.28)' : 'rgba(91,124,250,0.35)'
                      return (
                        <div key={stage} style={{ minHeight: 80, display: 'flex', flexDirection: 'column', justifyContent: 'space-between', padding: '10px 12px', border: `1px solid ${bd}`, borderRadius: 8, background: bg }}>
                          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 6 }}>
                            <span style={{ fontSize: 10, fontWeight: 800, color: T.muted, lineHeight: 1 }}>{String(i + 1).padStart(2, '0')}</span>
                            <span style={{ display: 'grid', placeItems: 'center', width: 26, height: 26, borderRadius: 6, color: '#fff', background: count > 0 ? T.ink : C.muted, fontSize: 11, fontWeight: 900 }}>{count}</span>
                          </div>
                          <strong style={{ fontSize: 12, lineHeight: 1.3, color: T.ink }}>{stage}</strong>
                        </div>
                      )
                    })}
                  </div>
                  {/* Split stats */}
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 10, padding: '10px 0 2px' }}>
                    {[
                      { n: stageMap['Effort Estimation'] ?? 0,    l: 'effort pending' },
                      { n: stageMap['Client Hold'] ?? 0,          l: 'on client hold' },
                      { n: (stageMap['Approval'] ?? 0) + (stageMap['FSD Approval'] ?? 0), l: 'awaiting approval' },
                    ].map(({ n, l }) => (
                      <div key={l} style={{ padding: '10px 14px', background: T.soft, borderRadius: 8, border: `1px solid ${T.line}` }}>
                        <strong style={{ fontSize: 24, fontWeight: 950, color: T.ink }}>{n}</strong>
                        <span style={{ fontSize: 13, color: T.muted, fontWeight: 700, marginLeft: 8 }}>{l}</span>
                      </div>
                    ))}
                  </div>
                </>
              )}
              {crTab === 'milestone' && (() => {
                // Funnel: % of CRs that have reached at-or-past each milestone.
                // Each milestone maps to the first stage in stageOrder where it has been "completed".
                const milestoneStartIdx: Record<string, number> = {
                  BRD: 2, FSD: 3, Dev: 8, QA: 9, UAT: 10, Prod: 12,
                }
                const liveStages = stageOrder.filter(s => !['Client Hold','Invalid'].includes(s))
                const totalLive  = liveStages.reduce((s, x) => s + (stageMap[x] ?? 0), 0)
                const pctFor = (ms: string) => {
                  if (totalLive === 0) return 0
                  const past = stageOrder.slice(milestoneStartIdx[ms])
                    .filter(s => !['Client Hold','Invalid'].includes(s))
                    .reduce((s, x) => s + (stageMap[x] ?? 0), 0)
                  return Math.round(past * 100 / totalLive)
                }
                const stateFor = (ms: string, pct: number) =>
                  pct >= 95 ? 'Done' : pct >= 60 ? 'Active' : pct >= 30 ? 'In flight' : pct > 0 ? 'Trailing' : 'Awaited'
                return (
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 10 }}>
                  {['BRD','FSD','Dev','QA','UAT','Prod'].map((ms) => {
                    const pct = pctFor(ms)
                    return (
                      <div key={ms} style={{ minHeight: 104, display: 'grid', alignContent: 'center', justifyItems: 'center', gap: 8, padding: 12, border: `1px solid ${T.line}`, borderRadius: 8, background: T.soft, textAlign: 'center' }}>
                        <div style={{ width: 42, height: 42, borderRadius: '50%', background: `conic-gradient(${T.teal} ${pct * 3.6}deg, ${T.line} 0)`, display: 'grid', placeItems: 'center' }}>
                          <div style={{ width: 28, height: 28, borderRadius: '50%', background: T.soft }} />
                        </div>
                        <strong style={{ fontSize: 15, fontWeight: 800, color: T.ink }}>{ms}</strong>
                        <span style={{ fontSize: 13, fontWeight: 700, color: T.teal }}>{pct}%</span>
                        <small style={{ color: T.muted, fontSize: 12 }}>{stateFor(ms, pct)}</small>
                      </div>
                    )
                  })}
                </div>
                )
              })()}
              {crTab === 'aging' && (
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2,1fr)', gap: 10 }}>
                  {([
                    ['0–3 days',  '0_3',  T.green, T.greenLight],
                    ['4–7 days',  '4_7',  T.amber, T.amberLight],
                    ['8–14 days', '8_14', T.amber, T.amberLight],
                    ['>14 days',  '15p',  T.red,   T.redLight],
                  ] as [string, string, string, string][]).map(([label, key, color, bg]) => (
                    <div key={label} style={{ minHeight: 106, display: 'grid', alignContent: 'center', gap: 6, border: `1px solid ${color}44`, borderRadius: 8, background: bg, textAlign: 'center', padding: 12 }}>
                      <strong style={{ fontSize: 34, fontWeight: 950, color: T.ink }}>{agingBuckets ? (agingBuckets[key] ?? 0) : '—'}</strong>
                      <span style={{ fontSize: 14, fontWeight: 700, color: T.ink }}>{label}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </section>

          {/* Release Readiness + Governance Meetings side by side */}
          {(portfolioReleases.length > 0 || portfolioGovernance.length > 0) && (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 20 }}>
              {/* Release readiness */}
              <section style={{ border: `1px solid ${T.line}`, borderRadius: 8, background: T.surface, overflow: 'hidden' }}>
                <div style={{ padding: 14, borderBottom: `1px solid ${T.line}`, background: T.sidebar }}>
                  <p style={{ margin: 0, color: T.teal, fontSize: 11, fontWeight: 800, textTransform: 'uppercase', letterSpacing: 0.8 }}>Release readiness</p>
                  <h3 style={{ margin: '2px 0 0', fontSize: 18, fontWeight: 950, color: T.ink }}>Next {rangeDays} days</h3>
                </div>
                <div style={{ padding: 14 }}>
                  {portfolioReleases.length === 0 ? (
                    <p style={{ margin: 0, color: T.muted, fontSize: 13, textAlign: 'center', padding: 20 }}>No releases scheduled</p>
                  ) : (
                    <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'grid', gap: 8 }}>
                      {portfolioReleases.slice(0, 6).map((r: any) => {
                        const c = r.type === 'launch' ? T.blue : r.type === 'support' ? T.red : T.green
                        return (
                          <li key={r.id} style={{ padding: '10px 12px', borderRadius: 8, border: `1px solid ${T.line}`, background: T.soft, display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10 }}>
                            <div style={{ minWidth: 0 }}>
                              <div style={{ fontSize: 11, fontWeight: 800, color: c, textTransform: 'uppercase', letterSpacing: 0.4 }}>{r.type}</div>
                              <div style={{ fontSize: 13, fontWeight: 700, color: T.ink, marginTop: 2 }}>{r.label ?? 'Release'}</div>
                            </div>
                            <div style={{ fontSize: 12, fontWeight: 800, color: T.ink, whiteSpace: 'nowrap' }}>{r.date}</div>
                          </li>
                        )
                      })}
                    </ul>
                  )}
                </div>
              </section>

              {/* Governance meetings */}
              <section style={{ border: `1px solid ${T.line}`, borderRadius: 8, background: T.surface, overflow: 'hidden' }}>
                <div style={{ padding: 14, borderBottom: `1px solid ${T.line}`, background: T.sidebar }}>
                  <p style={{ margin: 0, color: T.teal, fontSize: 11, fontWeight: 800, textTransform: 'uppercase', letterSpacing: 0.8 }}>Governance cadence</p>
                  <h3 style={{ margin: '2px 0 0', fontSize: 18, fontWeight: 950, color: T.ink }}>Upcoming meetings</h3>
                </div>
                <div style={{ padding: 14 }}>
                  {portfolioGovernance.length === 0 ? (
                    <p style={{ margin: 0, color: T.muted, fontSize: 13, textAlign: 'center', padding: 20 }}>No governance meetings configured</p>
                  ) : (
                    <table style={{ width: '100%', fontSize: 12, borderCollapse: 'collapse' }}>
                      <thead><tr>
                        {['Title','Cadence','Next due','Status'].map(h =>
                          <th key={h} style={{ padding: '6px 8px', textAlign: 'left', fontSize: 10, fontWeight: 800, color: T.muted, textTransform: 'uppercase', letterSpacing: 0.4 }}>{h}</th>)}
                      </tr></thead>
                      <tbody>
                        {portfolioGovernance.slice(0, 6).map((g: any, i: number) => {
                          const stColor = g.status === 'On track' ? T.green : g.status === 'Missed' ? T.red : T.amber
                          return (
                            <tr key={g.id} style={{ borderTop: i > 0 ? `1px solid ${T.line}` : 'none' }}>
                              <td style={{ padding: '7px 8px', color: T.ink, fontWeight: 600 }}>{g.title}</td>
                              <td style={{ padding: '7px 8px', color: T.muted }}>{g.cadence}</td>
                              <td style={{ padding: '7px 8px', color: T.ink, fontWeight: 700 }}>{g.nextDue ?? '—'}</td>
                              <td style={{ padding: '7px 8px' }}>{g.status && <span style={{ fontSize: 10, fontWeight: 800, color: stColor }}>{g.status}</span>}</td>
                            </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  )}
                </div>
              </section>
            </div>
          )}

          {/* Production Issues + Executive Exceptions side by side */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 20 }}>

            {/* Production Issues */}
            <section style={{ border: `1px solid ${T.line}`, borderRadius: 8, background: T.surface, overflow: 'hidden' }}>
              <div style={{ padding: '14px 14px 10px', borderBottom: `1px solid ${T.line}`, background: T.sidebar }}>
                <p style={{ margin: 0, color: T.teal, fontSize: 11, fontWeight: 800, textTransform: 'uppercase', letterSpacing: 0.8 }}>Production issues</p>
                <h3 style={{ margin: '2px 0 0', fontSize: 18, fontWeight: 950, color: T.ink }}>Severity breakdown · {activePortfolio?.name}</h3>
              </div>
              <div style={{ padding: 14, display: 'grid', gap: 8 }}>
                {PROD_ROWS.map(row => {
                  const sevColor = row.sev === 'P0' || row.sev === 'P1' ? T.red : row.sev === 'P2' ? T.amber : T.green
                  const sevBg    = row.sev === 'P0' || row.sev === 'P1' ? T.redLight : row.sev === 'P2' ? T.amberLight : T.greenLight
                  return (
                    <div key={row.sev} onClick={() => navigate(row.nav)} style={{
                      display: 'flex', alignItems: 'center', gap: 12, padding: '12px 14px',
                      borderRadius: 8, border: `1px solid ${row.hot && row.count > 0 ? sevColor + '44' : T.line}`,
                      background: row.hot && row.count > 0 ? sevBg : T.sidebar,
                      cursor: 'pointer', transition: 'opacity 160ms ease',
                    }}
                    onMouseEnter={e => (e.currentTarget as HTMLElement).style.opacity = '0.85'}
                    onMouseLeave={e => (e.currentTarget as HTMLElement).style.opacity = '1'}>
                      <span style={{ display: 'grid', placeItems: 'center', width: 34, height: 34, borderRadius: 8, background: sevBg, color: sevColor, fontWeight: 900, fontSize: 12, flexShrink: 0 }}>{row.sev}</span>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <strong style={{ display: 'block', fontSize: 14, fontWeight: 700, color: T.ink }}>{row.count} {row.label}</strong>
                        <small style={{ fontSize: 12, color: T.muted }}>{row.sla}</small>
                      </div>
                      <span style={{ fontSize: 12, fontWeight: 700, color: row.sla === 'Breached' || row.sla.includes('breached') ? T.red : T.muted, flexShrink: 0 }}>
                        {row.sla}
                      </span>
                    </div>
                  )
                })}
                {PROD_ROWS.every(r => r.count === 0) && (
                  <p style={{ margin: 8, color: T.muted, fontSize: 13, textAlign: 'center' }}>No production issues · All SLAs clear</p>
                )}
              </div>
            </section>

            {/* Executive Exceptions */}
            <section style={{ border: `1px solid ${T.line}`, borderRadius: 8, background: T.surface, overflow: 'hidden' }}>
              <div style={{ padding: '14px 14px 10px', borderBottom: `1px solid ${T.line}`, background: T.sidebar }}>
                <p style={{ margin: 0, color: T.teal, fontSize: 11, fontWeight: 800, textTransform: 'uppercase', letterSpacing: 0.8 }}>Executive exceptions</p>
                <h3 style={{ margin: '2px 0 0', fontSize: 18, fontWeight: 950, color: T.ink }}>Action required · {(exceptionsData as any[]).length} items</h3>
              </div>
              {(exceptionsData as any[]).length === 0 ? (
                <p style={{ margin: 0, padding: 24, color: T.muted, fontSize: 13, textAlign: 'center' }}>No exceptions flagged · Portfolio on track</p>
              ) : (
                <div style={{ overflowX: 'auto' }}>
                  <table style={{ width: '100%', minWidth: 480, borderCollapse: 'collapse', fontSize: 13 }}>
                    <thead>
                      <tr style={{ background: T.sidebar }}>
                        {['Client','Risk','Owner','Impact','Next action'].map(h => (
                          <th key={h} style={{ padding: '8px 12px', textAlign: 'left', fontWeight: 800, fontSize: 11, color: T.muted, textTransform: 'uppercase', letterSpacing: 0.4, borderBottom: `1px solid ${T.line}`, whiteSpace: 'nowrap' }}>{h}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {(exceptionsData as any[]).map((ex: any, i: number) => {
                        const rc = riskColor(ex.risk, T)
                        return (
                          <tr key={i} style={{ borderBottom: `1px solid ${T.line}` }}
                            onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = T.soft}
                            onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = ''}>
                            <td style={{ padding: '10px 12px', fontWeight: 700, color: T.ink }}>{ex.client}</td>
                            <td style={{ padding: '10px 12px' }}>
                              <span style={{ padding: '3px 8px', borderRadius: 999, fontSize: 11, fontWeight: 700, background: rc.bg, color: rc.fg }}>{ex.risk}</span>
                            </td>
                            <td style={{ padding: '10px 12px', color: T.muted }}>{ex.owner}</td>
                            <td style={{ padding: '10px 12px', color: T.muted, maxWidth: 200 }}>{ex.impact}</td>
                            <td style={{ padding: '10px 12px', color: T.teal, fontWeight: 600 }}>{ex.nextAction}</td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </section>

          </div>
        </>
      )}

      {/* ── ENGINEERING section ───────────────────────────────────────────── */}
      {showLegacy && persona === 'ENGINEERING' && (
        <section style={{ border: `1px solid ${T.line}`, borderRadius: 8, background: T.surface, overflow: 'hidden', marginBottom: 20 }}>
          <div style={{ padding: '14px 14px 10px', borderBottom: `1px solid ${T.line}`, background: T.sidebar }}>
            <p style={{ margin: 0, color: T.teal, fontSize: 11, fontWeight: 800, textTransform: 'uppercase', letterSpacing: 0.8 }}>Capacity</p>
            <h3 style={{ margin: '2px 0 0', fontSize: 22, fontWeight: 950, color: T.ink }}>Team utilisation — {activePortfolio?.name ?? 'Portfolio'}</h3>
          </div>
          <div style={{ padding: 16, color: T.muted, fontSize: 14 }}>Navigate to Capacity &amp; Team for detailed developer availability, Gantt chart, and heatmap.</div>
          <div style={{ display: 'flex', gap: 12, padding: '0 16px 16px' }}>
            <button onClick={() => navigate('/capacity')} style={{ padding: '8px 16px', borderRadius: 8, border: `1px solid ${T.line}`, background: T.surface, color: T.teal, fontWeight: 700, cursor: 'pointer', fontSize: 13 }}>Open Capacity →</button>
            <button onClick={() => navigate('/mandays')} style={{ padding: '8px 16px', borderRadius: 8, border: `1px solid ${T.line}`, background: T.surface, color: T.teal, fontWeight: 700, cursor: 'pointer', fontSize: 13 }}>Man-Days →</button>
          </div>
        </section>
      )}

      {/* ── REVENUE section ───────────────────────────────────────────────── */}
      {showLegacy && persona === 'REVENUE' && (
        <section style={{ border: `1px solid ${T.line}`, borderRadius: 8, background: T.surface, overflow: 'hidden', marginBottom: 20 }}>
          <div style={{ padding: '14px 14px 10px', borderBottom: `1px solid ${T.line}`, background: T.sidebar }}>
            <p style={{ margin: 0, color: T.teal, fontSize: 11, fontWeight: 800, textTransform: 'uppercase', letterSpacing: 0.8 }}>Mandays</p>
            <h3 style={{ margin: '2px 0 0', fontSize: 22, fontWeight: 950, color: T.ink }}>Scope &amp; burn — {activePortfolio?.name ?? 'Portfolio'}</h3>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 14, padding: 16 }}>
            {[['Sold mandays', summary.soldMandays ?? '—', 'Total contracted'],['Consumed', summary.consumedMandays ?? '—', `${summary.burnPct ?? '—'}% utilised`],['Remaining', summary.remainingMandays ?? '—', 'Forecast runway']].map(([l, v, s]) => (
              <div key={l} style={{ minHeight: 126, border: `1px solid ${T.line}`, borderRadius: 8, background: T.soft, padding: 14, display: 'grid', alignContent: 'space-between' }}>
                <span style={{ color: T.muted, fontSize: 14, fontWeight: 800 }}>{l}</span>
                <strong style={{ fontSize: 34, fontWeight: 950, color: T.ink }}>{String(v)}</strong>
                <small style={{ color: T.muted, fontSize: 13 }}>{s}</small>
              </div>
            ))}
          </div>
          <div style={{ display: 'flex', gap: 12, padding: '0 16px 16px' }}>
            <button onClick={() => navigate('/mandays')} style={{ padding: '8px 16px', borderRadius: 8, border: `1px solid ${T.line}`, background: T.surface, color: T.teal, fontWeight: 700, cursor: 'pointer', fontSize: 13 }}>Full Man-Days detail →</button>
          </div>
        </section>
      )}

    </div>
  )
}
