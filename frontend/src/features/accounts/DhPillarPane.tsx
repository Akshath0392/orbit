import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { useC } from '../../design/ThemeContext'
import { api } from '../../api/client'
import { useStore } from '../../app/store'
import { Feature } from '../../app/featureFlags'
import { Modal } from '../../design/components/Modal'
import { Btn } from '../../design/components/Btn'
import { Card } from '../../design/components/Card'
import { InfoDot } from '../../design/components/InfoDot'
import { MetricCard } from '../../design/components/MetricCard'
import { ScoreRing, RagTone } from '../../design/components/ScoreRing'
import { SegmentBar } from '../../design/components/SegmentBar'
import { LoadingState } from '../../design/components/PageState'
import { useBreakpoint } from '../../design/useBreakpoint'
import { useAmClientDhMetrics, useAmSettings } from '../radar/am/useAmApi'
import { DH_DEFS, DhDef, DhPillar, dhInfoBody, dhRag } from '../radar/am/metricInfo'

const PILLAR_TITLE: Record<DhPillar, string> = {
  speed: 'Delivery Speed', quality: 'Delivery Quality', pred: 'Delivery Predictability',
}
const MONTH_SHORT = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
const ymShort = (ym: string) => MONTH_SHORT[Number(ym.slice(5, 7)) - 1] ?? ym

// Delivery-health pillar tab (mock DH panes, W20–W22): health ring with
// admin-configurable weights, pillar score vs POD average, trend/work-type
// filters, metric cards with RAG + 6-month trend, backlog-aging split.
// Metrics whose feed doesn't exist yet stay behind section.client.dh.* flags.
export function DhPillarPane({ clientId, clientName, pillar }: {
  clientId: number | null
  clientName: string
  pillar: DhPillar
}) {
  const C = useC()
  const navigate = useNavigate()
  const isAdmin = useStore(s => s.user?.role) === 'ADMIN'
  const bp = useBreakpoint()
  const [months, setMonths] = useState<6 | 3>(6)
  const [type, setType] = useState<'ALL' | 'LAUNCH' | 'BAU'>('ALL')
  // "open items" drills land on list pages with filters (no modal) — speed →
  // CR board, quality → bug triage (prod/uat tab per metric)
  const crLink = () => navigate(`/cr?clientId=${clientId}${type === 'ALL' ? '' : `&type=${type}`}`)
  const bugLink = (tab: 'prod' | 'uat') => navigate(`/bugs?clientId=${clientId}&tab=${tab}`)
  const [weightsOpen, setWeightsOpen] = useState(false)
  const { data, isLoading } = useAmClientDhMetrics(clientId, months, type === 'ALL' ? undefined : type)
  const { data: settings } = useAmSettings()

  const weights = {
    speed: settings?.dhSpeedWeight ?? 40,
    quality: settings?.dhQualityWeight ?? 35,
    pred: settings?.dhPredWeight ?? 25,
  }
  const pillars = data?.pillars ?? {}
  // Weighted health over the pillars that have data, weights renormalized
  // (mock health-score v1 — a missing pillar never drags the score to 0).
  const overall = useMemo(() => {
    let sum = 0, wsum = 0
    for (const p of ['speed', 'quality', 'pred'] as DhPillar[]) {
      if (pillars[p] != null) { sum += pillars[p] * weights[p]; wsum += weights[p] }
    }
    return wsum === 0 ? null : Math.round(sum / wsum)
  }, [pillars, weights.speed, weights.quality, weights.pred])

  const tone = (v: number | null | undefined): RagTone | null =>
    v == null ? null : v >= 80 ? 'g' : v >= 55 ? 'a' : 'r'
  const pillarScore = pillars[pillar]
  const vsPod = data?.vsPodAvg?.[pillar]

  const labels: string[] = (data?.months ?? []).map(ymShort)
  const trendOf = (values: number[] | undefined) =>
    (values ?? []).map((v, i) => ({ label: labels[i] ?? '', value: Number(v) }))
  const currentLabel = labels.length ? `· ${labels[labels.length - 1]} (current)` : undefined
  const thresholdsOf = (d: DhDef) =>
    `${d.dir === 'low' ? '≤' : '≥'}${d.g}${d.unit} green · ${d.dir === 'low' ? '≤' : '≥'}${d.a}${d.unit} amber · else red`

  const realCard = (key: string, values: number[] | undefined, opts: { trend?: boolean; drill?: () => void } = {}) => {
    const d = DH_DEFS[key]
    const cur = values && values.length ? Number(values[values.length - 1]) : null
    return (
      <MetricCard
        key={key}
        name={d.name}
        value={cur == null ? '—' : cur}
        unit={d.unit}
        currentLabel={currentLabel}
        rag={cur == null ? undefined : dhRag(d, cur)}
        trend={opts.trend === false ? undefined : trendOf(values)}
        thresholds={thresholdsOf(d)}
        info={{ title: d.name, body: dhInfoBody(d) }}
        onDrill={opts.drill ?? crLink}
      />
    )
  }

  const pendingCard = (key: string) => {
    const d = DH_DEFS[key]
    return (
      <Feature key={key} flag={d.flag!}>
        <MetricCard name={d.name} pending={d.pending}
          info={{ title: d.name, body: dhInfoBody(d) }} />
      </Feature>
    )
  }

  const agingCard = () => {
    const a = data?.aging
    const d = DH_DEFS.aging
    return (
      // mock: grid-column 1/-1 — the aging split owns a full row
      <div key="aging" style={{ gridColumn: '1/-1', background: C.white, border: `1px solid ${C.border}`, borderRadius: 14, boxShadow: C.shadowSm, padding: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <span style={{ fontSize: 13, fontWeight: 800, color: C.text }}>{d.name}</span>
          <InfoDot title={d.name} body={dhInfoBody(d)} />
          <span style={{ marginLeft: 'auto', fontSize: 11, color: C.muted }}>{a?.total ?? 0} open</span>
        </div>
        <div style={{ marginTop: 12 }}>
          <SegmentBar segments={[
            { label: '0–15d', value: Number(a?.b0_15 ?? 0), color: C.green },
            { label: '16–30d', value: Number(a?.b16_30 ?? 0), color: C.indigo },
            { label: '31–60d', value: Number(a?.b31_60 ?? 0), color: C.amber },
            { label: '60+d', value: Number(a?.b60plus ?? 0), color: C.red },
          ]} />
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 10, color: C.muted, marginTop: 4 }}>
            <span>0–15</span><span>16–30</span><span>31–60</span><span>60+</span>
          </div>
        </div>
        <div style={{ textAlign: 'right', marginTop: 10 }}>
          <span onClick={crLink}
            style={{ fontSize: 12, color: C.indigo, fontWeight: 700, cursor: 'pointer' }}>open items</span>
        </div>
      </div>
    )
  }

  const cards: React.ReactNode[] = []
  if (pillar === 'speed') {
    cards.push(realCard('lead', data?.lead))
    // cycle time lights up once the changelog backfill has populated
    // first_in_progress_at — data presence beats the flag
    cards.push(data?.cycle ? realCard('cycle', data.cycle) : pendingCard('cycle'))
    cards.push(realCard('tput', data?.throughput))
    const slaVal = data?.slaCompliancePct
    const slaDef = DH_DEFS.sla
    cards.push(
      <MetricCard key="sla" name={slaDef.name}
        value={slaVal == null ? '—' : slaVal} unit="%"
        currentLabel="· open CRs, point-in-time"
        rag={slaVal == null ? undefined : dhRag(slaDef, Number(slaVal))}
        formula="Snapshot of open CRs vs their stage aging targets — a monthly trend lands with the changelog sync."
        thresholds={thresholdsOf(slaDef)}
        info={{ title: slaDef.name, body: dhInfoBody(slaDef) }}
        onDrill={crLink}
      />)
    cards.push(agingCard())
  } else if (pillar === 'quality') {
    // no-source metrics (leakage, CFR, rework) stay out of the pane until a
    // feed exists — their pending cards read as broken
    // reopened rate lights up once the changelog backfill has populated
    // reopen_count — data presence beats the flag
    cards.push(data?.reopened
      ? realCard('reopen', data.reopened, { drill: () => bugLink('uat') })
      : pendingCard('reopen'))
    cards.push(realCard('incid', data?.incidents, { drill: () => bugLink('prod') }))
  } else {
    const pm = data?.predMetrics
    if (pm?.dataAvailable) {
      // per-sprint bars (mock: "Per-sprint bars") — labels are sprint names
      const sprintTrend = (field: string) =>
        ((pm.perSprint ?? []) as any[]).map(s => ({ label: String(s.label), value: Number(s[field]) }))
      const sprintCard = (key: string, value: number, field: string) => {
        const d = DH_DEFS[key]
        const trend = sprintTrend(field)
        return (
          <MetricCard key={key} name={d.name}
            value={value} unit="%"
            currentLabel={`· last ${pm.sprints} closed sprint${pm.sprints > 1 ? 's' : ''}${pm.approx ? ' (approx)' : ''}`}
            rag={dhRag(d, value)}
            trend={trend.length > 1 ? trend : undefined}
            formula={pm.approx ? 'Committed SP approximated from membership + current SP — exact from the first post-rollout sprint activation.' : undefined}
            thresholds={thresholdsOf(d)}
            info={{ title: d.name, body: dhInfoBody(d) }}
            onDrill={crLink} />
        )
      }
      cards.push(sprintCard('commit', Number(pm.commitmentPct), 'commitmentPct'))
      cards.push(sprintCard('spill', Number(pm.spilloverPct), 'spilloverPct'))
      cards.push(sprintCard('scope', Number(pm.scopeChangePct), 'scopeChangePct'))
    } else {
      cards.push(pendingCard('commit'))
      cards.push(pendingCard('spill'))
      cards.push(pendingCard('scope'))
    }
  }

  if (clientId == null) {
    return <Card><div style={{ fontSize: 13, color: C.sub }}>No client linked to this account — pillar metrics need a client.</div></Card>
  }

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap', marginBottom: 18 }}>
        <ScoreRing value={overall ?? '—'} tone={tone(overall)} label="health" size="lg"
          title={`Weighted ${weights.speed}/${weights.quality}/${weights.pred} over pillars with data`} />
        <div style={{ minWidth: 180 }}>
          <div style={{ fontSize: 15, fontWeight: 800, color: C.text }}>{PILLAR_TITLE[pillar]}</div>
          {/* mock header line: "pillar score 47 · weight 40% · vs POD avg 67 · configure weights ⚙" */}
          <div style={{ fontSize: 12.5, color: C.sub, marginTop: 3 }}>
            pillar score <b style={{ color: pillarScore == null ? C.muted : C.text }}>{pillarScore ?? '—'}</b>
            {' · '}weight <b style={{ color: C.text }}>{weights[pillar]}%</b>
            {vsPod != null && <> · vs POD avg <b style={{ color: pillarScore != null && pillarScore >= vsPod ? C.green : C.red }}>{vsPod}</b></>}
            {' · '}
            <span onClick={() => setWeightsOpen(true)}
              style={{ cursor: 'pointer', color: C.indigo, fontWeight: 700 }}
              title={isAdmin ? 'Adjust pillar weights' : 'View pillar weights (admin-editable)'}>configure weights ⚙</span>
          </div>
        </div>
        <div style={{ marginLeft: 'auto', display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <PillToggle value={String(months)} onChange={v => setMonths(Number(v) as 6 | 3)}
            options={[['6', '6 months'], ['3', '3 months']]} />
          <PillToggle value={type} onChange={v => setType(v as typeof type)}
            options={[['ALL', 'All'], ['LAUNCH', 'Launch'], ['BAU', 'BAU']]} />
        </div>
      </div>

      {isLoading ? <LoadingState /> : (
        <div style={{ display: 'grid', gap: 16, gridTemplateColumns: bp === 'mobile' ? '1fr' : bp === 'tablet' ? 'repeat(2, 1fr)' : 'repeat(3, 1fr)' }}>
          {cards}
        </div>
      )}

      {pillar === 'pred' && !data?.predMetrics?.dataAvailable && (
        <Card style={{ marginTop: 16 }}>
          <div style={{ fontSize: 13, color: C.sub }}>
            Predictability metrics light up once the Sprint field is mapped (Jira Sync → Field mapping),
            a full sync + changelog backfill has run, and this client has closed sprints with story points.
          </div>
        </Card>
      )}

      {weightsOpen && (
        <DhWeightsModal weights={weights} editable={isAdmin} onClose={() => setWeightsOpen(false)} />
      )}
    </div>
  )
}

function PillToggle({ value, onChange, options }: {
  value: string
  onChange: (v: string) => void
  options: [string, string][]
}) {
  const C = useC()
  return (
    <span style={{ display: 'inline-flex', border: `1px solid ${C.borderMed}`, borderRadius: 10, overflow: 'hidden' }}>
      {options.map(([v, label]) => (
        <button key={v} onClick={() => onChange(v)} style={{
          border: 'none', cursor: 'pointer', padding: '7px 13px', fontSize: 12, fontWeight: 700,
          background: v === value ? C.indigoPale : C.white, color: v === value ? C.tealDeep : C.muted,
        }}>{label}</button>
      ))}
    </span>
  )
}

function DhWeightsModal({ weights, editable, onClose }: {
  weights: { speed: number; quality: number; pred: number }
  editable: boolean
  onClose: () => void
}) {
  const C = useC()
  const qc = useQueryClient()
  const [form, setForm] = useState({ ...weights })
  const [error, setError] = useState('')
  const sum = form.speed + form.quality + form.pred

  const save = async () => {
    try {
      await api.put('/am/settings', {
        dhSpeedWeight: form.speed, dhQualityWeight: form.quality, dhPredWeight: form.pred,
      })
      qc.invalidateQueries({ queryKey: ['am-settings'] })
      onClose()
    } catch (e: any) {
      setError(e?.response?.data?.message ?? 'Save failed')
    }
  }

  const row = (label: string, key: 'speed' | 'quality' | 'pred') => (
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 90px', gap: 10, alignItems: 'center', padding: '6px 0' }}>
      <span style={{ fontSize: 13, color: C.text, fontWeight: 600 }}>{label}</span>
      <input type="number" min={0} max={100} value={form[key]} disabled={!editable}
        onChange={e => setForm({ ...form, [key]: Number(e.target.value) })}
        style={{ fontSize: 13, padding: '7px 10px', borderRadius: 8, border: `1px solid ${C.borderMed}`, color: C.text, background: editable ? C.white : C.canvas }} />
    </div>
  )

  return (
    <Modal title="Delivery-health pillar weights" width={420} onClose={onClose}>
      <div style={{ fontSize: 12, color: C.sub, marginBottom: 10 }}>
        Global, admin-configurable weighting of the three pillars (mock default 40/35/25). Must sum to 100.
      </div>
      {row('Delivery Speed', 'speed')}
      {row('Delivery Quality', 'quality')}
      {row('Delivery Predictability', 'pred')}
      <div style={{ fontSize: 12, fontWeight: 700, marginTop: 8, color: sum === 100 ? C.green : C.red }}>
        Total: {sum} {sum !== 100 && '— must equal 100'}
      </div>
      {error && <div style={{ fontSize: 12, color: C.red, marginTop: 6 }}>{error}</div>}
      {editable && (
        <div style={{ textAlign: 'right', marginTop: 14 }}>
          <Btn variant="primary" onClick={save} disabled={sum !== 100}>Save</Btn>
        </div>
      )}
    </Modal>
  )
}
