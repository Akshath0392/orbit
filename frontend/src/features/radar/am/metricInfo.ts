// Metric explainers ported verbatim from the approved V3 mock
// (resouces/orbit-preview-1.html: AM_INFO + DH_DEFS). These are the ⓘ popover
// "logs" for every AM widget and delivery-health card. Text only — InfoDot
// renders with pre-line whitespace.

export const AM_INFO = {
  csat: {
    title: 'CSAT Launch / BAU — suggested logic',
    body: 'What: client satisfaction (1–10) split by engagement type, so launch experience and steady-state support are scored separately.\n\nSuggested logic: trigger a 1-question survey on every closed production ticket and every release sign-off. CSAT = mean of responses over the trailing 90 days, bucketed by whether the source ticket belongs to a Launch or BAU project.\n\nAlternative: blend a quarterly relationship survey 50/50 with the transactional score to smooth low response volumes. One of the two POD ranking drivers.\n\nStatus in Orbit: no survey source yet — CSAT slots show “—” and the POD score runs on SLA adherence alone until this lands (Phase D).',
  },
  eng: {
    title: 'Engagement — suggested logic',
    body: 'What: composite 0–100 score of how actively and healthily the engagement is running.\n\nSuggested logic (weighted blend):\n· Delivery speed 40% — median cycle time vs target, normalized.\n· Quality 30% — 1 − (reopen rate + defect escape rate).\n· Platform engagement 30% — client stakeholder WAU/MAU on the platform plus % of nudges acted on.\n\nEach component normalized 0–100, then weighted and summed.',
  },
  slaAdh: {
    title: 'SLA adherence — kept simple',
    body: 'One ratio:\n\nissues that met their SLA ÷ all issues with an SLA × 100\n\ni.e. met ÷ (met + near breach + breached), over the trailing period. Example: 12 met of 14 tracked → 86%.',
  },
  bnm: {
    title: 'Breached · Near · Met',
    body: 'Met — resolved within the SLA window (or open with comfortable time left).\n\nNear breach — still open with less than 25% of the SLA window remaining (the early-warning band).\n\nBreached — the SLA window expired before resolution.',
  },
  podScore: {
    title: 'POD score — interim basis',
    body: 'Mock formula: 60% CSAT + 40% SLA adherence.\n\nUntil a CSAT source exists, the score is SLA adherence alone: open CRs within their stage aging target ÷ all open CRs in SLA-tracked stages × 100, scored against absolute targets (not normalized across PODs — the worst POD reads as its real adherence, not 0).',
  },
  stages: {
    title: 'Stage SLA %',
    body: 'Share of open CRs younger than the stage’s aging target (stage_sla_targets, Admin-editable). Stages without a target (Hold, Unstaged) are counted but not scored. Client-hold time is not excluded from the clock yet.\n\nHeader colour: ≥85% green · ≥70% amber · below red.',
  },
  prod: {
    title: 'Production tickets',
    body: 'Created vs resolved production bugs per month from Jira sync. “Open now” is every unresolved production bug in the selected POD, split by severity.\n\nClick a card to open the week-on-week view: W1–W4 splits per month that reconcile to the monthly totals, with the running open count ending at “open now”.',
  },
  owners: {
    title: 'Owner share of open CRs',
    body: 'Share of open CRs by Jira assignee — top 9 owners, remainder grouped as Others. The mock’s separate Solutioning Manager / PjM views land once those Jira fields are mapped (the flag stays NONE until then).\n\nNote: the stage mix per owner moved out of view with the donut conversion; drill an owner to see their stages.',
  },
  velocity: {
    title: 'Velocity — sprint on sprint',
    body: 'Delivered ÷ committed story points for the last completed sprint, with the delta vs the sprint before. Needs Jira Agile (sprint) sync — Phase C. Until then velocity slots show “—”.',
  },
} as const

export type DhPillar = 'speed' | 'quality' | 'pred'

export interface DhDef {
  pillar: DhPillar
  name: string
  unit: string
  dir: 'low' | 'high'
  g: number
  a: number
  purpose: string
  formula: string
  jira: string
  incl: string
  real: boolean          // computable from currently-synced data
  flag?: string          // feature flag when not yet real (section.client.dh.*)
  pending?: string       // awaiting-feed label shown on the flagged card
}

export const DH_DEFS: Record<string, DhDef> = {
  lead: {
    pillar: 'speed', name: 'Lead Time', unit: 'd', dir: 'low', g: 15, a: 30,
    purpose: 'End-to-end wait the customer experiences for a work item.',
    formula: 'Avg(resolution date − issue created date) for CRs resolved in the period.',
    jira: 'created, resolutiondate, issuetype',
    incl: 'CRs resolved in the period. Excludes cancelled/invalid.',
    real: true,
  },
  cycle: {
    pillar: 'speed', name: 'Cycle Time', unit: 'd', dir: 'low', g: 8, a: 15,
    purpose: 'Execution speed once work actually starts.',
    formula: 'Avg(Production deploy − first transition to In Progress).',
    jira: 'status changelog (In Progress timestamp), deployment date',
    incl: 'Same as Lead Time, minus queue time before start.',
    real: false, flag: 'section.client.dh.cycle',
    pending: 'Awaiting status-changelog sync (In Progress timestamps) — Phase C.',
  },
  tput: {
    pillar: 'speed', name: 'Throughput', unit: '/mo', dir: 'high', g: 20, a: 12,
    purpose: 'Delivery volume per month by work type.',
    formula: 'Count(CRs resolved per month).',
    jira: 'issuetype, status=Done, resolutiondate',
    incl: 'Completed in month. Excludes cancelled and duplicates.',
    real: true,
  },
  sla: {
    pillar: 'speed', name: 'SLA Compliance', unit: '%', dir: 'high', g: 90, a: 75,
    purpose: 'Are committed response / resolution times being honored?',
    formula: 'Open CRs within their stage aging target ÷ open CRs in SLA-tracked stages × 100 (interim, stage-aging basis until JSM SLA fields sync).',
    jira: 'stage_sla_targets vs issue age; later: JSM SLA fields, resolutiondate, priority',
    incl: 'Open CRs in stages carrying a target. Client-hold excluded from targets.',
    real: true,
  },
  aging: {
    pillar: 'speed', name: 'Backlog Aging', unit: '', dir: 'low', g: 0, a: 0,
    purpose: 'Is the open backlog going stale?',
    formula: 'Open items bucketed by age: 0–15 · 16–30 · 31–60 · 60+ days.',
    jira: 'created, status category ≠ Done',
    incl: 'All open CRs for the client — real data from Jira sync.',
    real: true,
  },
  leak: {
    pillar: 'quality', name: 'Defect Leakage', unit: '%', dir: 'low', g: 5, a: 12,
    purpose: 'Bugs escaping to production vs caught before release.',
    formula: 'Production-found bugs ÷ total bugs found × 100 (by found-in environment).',
    jira: 'issuetype=Bug, environment / found-in field, created',
    incl: 'Bugs linked to releases in the period. Excludes legacy backlog bugs.',
    real: false, flag: 'section.client.dh.leakage',
    pending: 'Awaiting found-in-environment field mapping.',
  },
  reopen: {
    pillar: 'quality', name: 'Reopened Issues', unit: '%', dir: 'low', g: 3, a: 8,
    purpose: 'Completed work bouncing back — the rework smell.',
    formula: 'Issues re-transitioned out of Done ÷ issues completed × 100.',
    jira: 'status changelog (Done→any), resolutiondate',
    incl: 'Completed in trailing 60 days. Excludes bulk-edit admin reopens.',
    real: true, flag: 'section.client.dh.reopened',
    pending: 'Awaiting status-changelog sync (Done→reopen transitions) — Phase C.',
  },
  incid: {
    pillar: 'quality', name: 'Production Incidents', unit: '/mo', dir: 'low', g: 3, a: 6,
    purpose: 'Live customer impact, by severity.',
    formula: 'Count of production bugs created per month, trended.',
    jira: 'issuetype=Prod Bug, priority, created',
    incl: 'Production environment only. Excludes UAT / staging.',
    real: true,
  },
  cfr: {
    pillar: 'quality', name: 'Change Failure Rate', unit: '%', dir: 'low', g: 10, a: 20,
    purpose: 'How often releases hurt production (DORA metric).',
    formula: 'Deployments causing incident, rollback or hotfix ÷ total deployments × 100.',
    jira: 'deployments (CI/CD or fix versions), linked incidents, hotfix label',
    incl: 'Production deployments in the period. Config-only pushes configurable.',
    real: false, flag: 'section.client.dh.cfr',
    pending: 'Awaiting deployment-event feed (DORA) — Phase D.',
  },
  rework: {
    pillar: 'quality', name: 'Rework %', unit: '%', dir: 'low', g: 5, a: 12,
    purpose: 'Effort burned redoing finished work.',
    formula: '(Reopened SP + post-Done effort SP) ÷ delivered SP × 100.',
    jira: 'story points, worklogs after resolution, reopen transitions',
    incl: 'Delivered items in the period.',
    real: false, flag: 'section.client.dh.rework',
    pending: 'Awaiting story points + post-Done worklog feed.',
  },
  commit: {
    pillar: 'pred', name: 'Sprint Commitment Reliability', unit: '%', dir: 'high', g: 85, a: 70,
    purpose: 'Do we deliver what we commit at sprint start?',
    formula: 'Delivered committed SP ÷ SP committed at sprint start × 100.',
    jira: 'sprint scope snapshot at start, SP, resolution within sprint',
    incl: 'Committed-at-start only; added scope measured separately.',
    real: false, flag: 'section.client.dh.commitment',
    pending: 'Awaiting Jira sprint sync — Phase C.',
  },
  spill: {
    pillar: 'pred', name: 'Spillover %', unit: '%', dir: 'low', g: 10, a: 20,
    purpose: 'Work sliding into future sprints.',
    formula: 'Items moved to a later sprint ÷ committed items × 100.',
    jira: 'sprint changelog',
    incl: 'Committed items. Deliberately deferred (label) excluded.',
    real: false, flag: 'section.client.dh.spillover',
    pending: 'Awaiting Jira sprint sync — Phase C.',
  },
  scope: {
    pillar: 'pred', name: 'Scope Change %', unit: '%', dir: 'low', g: 10, a: 20,
    purpose: 'Mid-sprint churn injected into the plan.',
    formula: 'SP added after sprint start ÷ SP committed at start × 100.',
    jira: 'sprint scope changelog, story points',
    incl: 'Additions after start; removals tracked separately.',
    real: false, flag: 'section.client.dh.scope-change',
    pending: 'Awaiting Jira sprint sync — Phase C.',
  },
  release: {
    pillar: 'pred', name: 'Release Success Rate', unit: '%', dir: 'high', g: 95, a: 85,
    purpose: 'Releases landing clean — no rollback or emergency fix.',
    formula: 'Releases without rollback / hotfix within 48h ÷ total releases × 100.',
    jira: 'releases / fix versions, rollback & hotfix links',
    incl: 'Planned production releases. Hotfix-only deploys excluded.',
    real: false, flag: 'section.client.dh.release-success',
    pending: 'Awaiting release/rollback event feed — Phase D.',
  },
}

/** ⓘ body for a DH metric card, mock dhCard footer format. */
export function dhInfoBody(d: DhDef): string {
  return `Purpose: ${d.purpose}\n\nFormula: ${d.formula}\n\nJira fields: ${d.jira}\n\nIncludes: ${d.incl}\n\nThresholds: ${d.dir === 'low' ? '≤' : '≥'}${d.g} green · ${d.dir === 'low' ? '≤' : '≥'}${d.a} amber · else red\n\nHow the trend reads: same formula computed per month — the highlighted bar is the current month and equals the headline number.`
}

/** RAG for a metric value vs its thresholds. */
export function dhRag(d: DhDef, value: number): 'g' | 'a' | 'r' {
  const green = d.dir === 'low' ? value <= d.g : value >= d.g
  const amber = d.dir === 'low' ? value <= d.a : value >= d.a
  return green ? 'g' : amber ? 'a' : 'r'
}

/** Stage-SLA % header tone (matches DH sla thresholds). */
export function slaTone(pct: number): 'g' | 'a' | 'r' {
  return pct >= 85 ? 'g' : pct >= 70 ? 'a' : 'r'
}
