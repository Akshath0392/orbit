import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useC } from '../../../design/ThemeContext'
import type { Colors } from '../../../design/theme'
import { api } from '../../../api/client'

const STAGE_META = (C: Colors): Record<string, { label: string; desc: string; color: string }> => ({
  PRE_LAUNCH:   { label: 'Pre-launch',   desc: 'Before go-live — delivery pipeline and burn matter most',          color: C.blue },
  HYPERCARE:    { label: 'Hypercare',    desc: '0–90 days post go-live — production stability dominates',          color: C.amber },
  STEADY_STATE: { label: 'Steady-state', desc: '90+ days — balanced across support quality and CR velocity',       color: C.green },
  AT_RISK:      { label: 'At-risk',      desc: 'Manual flag by admin/CSM — all signals elevated',                  color: C.red },
})

const METRIC_META: Record<string, { label: string; desc: string; unit: string }> = {
  prod_bug_p0:       { label: 'P0 bugs',           desc: 'Open P0 production bugs',                   unit: 'count' },
  prod_bug_p1:       { label: 'P1 bugs',            desc: 'Open P1 production bugs',                   unit: 'count' },
  sla_breach:        { label: 'SLA breaches',       desc: 'Open prod bugs with SLA status = Breached', unit: 'count' },
  cr_on_hold_pct:    { label: 'CRs on hold',        desc: '% of open CRs in Hold / Client Hold stages', unit: '%' },
  uat_bug_count:     { label: 'UAT bugs',           desc: 'Open UAT bugs (before go-live indicator)',   unit: 'count' },
  manday_burn_risk:  { label: 'Manday burn risk',   desc: 'Triggers when burn > 80% of purchased days', unit: 'burn%' },
}

const STAGES = ['PRE_LAUNCH', 'HYPERCARE', 'STEADY_STATE', 'AT_RISK']
const METRICS = ['prod_bug_p0', 'prod_bug_p1', 'sla_breach', 'cr_on_hold_pct', 'uat_bug_count', 'manday_burn_risk']

type WeightRow = { id: number; metric: string; weight: number; sensitivity: number }

export function HealthProfilesPage() {
  const C  = useC()
  const qc = useQueryClient()
  const [activeStage, setActiveStage] = useState('PRE_LAUNCH')
  const [edits,       setEdits]       = useState<Record<string, { weight: string; sensitivity: string }>>({})
  const [saving,      setSaving]      = useState<string | null>(null)
  const [savedMsg,    setSavedMsg]    = useState('')

  const { data, isLoading } = useQuery({
    queryKey: ['health-profiles'],
    queryFn: () => api.get('/admin/health-profiles').then(r => r.data),
  })

  const stageWeights: WeightRow[] = (data?.weights?.[activeStage] ?? []) as WeightRow[]

  const getVal = (metric: string, field: 'weight' | 'sensitivity') => {
    if (edits[metric]?.[field] !== undefined) return edits[metric][field]
    const row = stageWeights.find(r => r.metric === metric)
    return row ? String(row[field]) : field === 'weight' ? '0' : '1.0'
  }

  const setVal = (metric: string, field: 'weight' | 'sensitivity', val: string) =>
    setEdits(e => ({ ...e, [metric]: { ...e[metric], [field]: val } }))

  const save = async (metric: string) => {
    const weight = parseInt(getVal(metric, 'weight'), 10)
    const sensitivity = parseFloat(getVal(metric, 'sensitivity'))
    if (isNaN(weight) || isNaN(sensitivity)) return
    setSaving(metric); setSavedMsg('')
    try {
      await api.put(`/admin/health-profiles/${activeStage}/${metric}`, { weight, sensitivity })
      qc.invalidateQueries({ queryKey: ['health-profiles'] })
      setEdits(e => { const n = { ...e }; delete n[metric]; return n })
      setSavedMsg(`${METRIC_META[metric]?.label ?? metric} saved.`)
    } finally { setSaving(null) }
  }

  const totalWeight = METRICS.reduce((sum, m) => sum + (parseInt(getVal(m, 'weight'), 10) || 0), 0)
  const sm = STAGE_META(C)[activeStage]

  const inp: React.CSSProperties = {
    fontSize: 13, padding: '5px 8px', borderRadius: 6,
    border: `1px solid ${C.border}`, outline: 'none',
    color: C.text, background: C.white, width: '100%', boxSizing: 'border-box',
  }

  if (isLoading) return <div style={{ padding: 40, color: C.sub }}>Loading…</div>

  return (
    <div style={{ padding: '22px 24px', maxWidth: 860 }}>

      {/* Header */}
      <div style={{ marginBottom: 22 }}>
        <div style={{ fontSize: 20, fontWeight: 800, color: C.text, letterSpacing: -0.4 }}>Health profiles</div>
        <div style={{ fontSize: 13, color: C.sub, marginTop: 3 }}>
          Configure how each delivery stage weights health signals. POD health = average of constituent project scores.
        </div>
      </div>

      {/* Explainer */}
      <div style={{ padding: '12px 16px', background: C.indigoPale, border: `1px solid ${C.borderMed}`, borderRadius: 10, fontSize: 12, color: C.tealDeep, lineHeight: 1.7, marginBottom: 22 }}>
        <strong>How it works:</strong> Each metric contributes up to its <em>weight</em> points of deduction from 100.
        <em>Sensitivity</em> controls how quickly the metric reaches full deduction (higher = penalises sooner).<br />
        <strong>Stage is auto-inferred</strong> from go-live date unless manually overridden per project in Admin → Projects.
        <br />
        <strong>Formula:</strong> healthPct = 100 − Σ(normalised_metric_value × weight), clamped to [0, 100]
      </div>

      {/* Stage tabs */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 20, flexWrap: 'wrap' }}>
        {STAGES.map(s => {
          const meta = STAGE_META(C)[s]
          const active = activeStage === s
          return (
            <button key={s} onClick={() => { setActiveStage(s); setEdits({}) }}
              style={{
                padding: '8px 16px', borderRadius: 8, border: `1px solid ${active ? meta.color : C.border}`,
                background: active ? meta.color : C.white, color: active ? '#fff' : C.text,
                fontSize: 13, fontWeight: 700, cursor: 'pointer', transition: 'all 160ms ease',
              }}>
              {meta.label}
            </button>
          )
        })}
      </div>

      {/* Stage description */}
      <div style={{ padding: '10px 14px', borderRadius: 8, background: C.canvas, marginBottom: 16, fontSize: 13, color: C.sub, borderLeft: `3px solid ${sm.color}` }}>
        {sm.desc}
      </div>

      {/* Weight table */}
      <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden', marginBottom: 12 }}>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ background: C.canvas }}>
              {['Metric','Description','Weight (0–100)','Sensitivity',''].map(h => (
                <th key={h} style={{ padding: '8px 14px', textAlign: 'left', fontSize: 11, fontWeight: 800, color: C.muted, textTransform: 'uppercase', letterSpacing: 0.4 }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {METRICS.map((metric, i) => {
              const meta    = METRIC_META[metric]
              const weight  = parseInt(getVal(metric, 'weight'), 10) || 0
              const isDirty = edits[metric] !== undefined
              const isSaving = saving === metric
              return (
                <tr key={metric} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none' }}>
                  <td style={{ padding: '10px 14px' }}>
                    <div style={{ fontSize: 13, fontWeight: 700, color: C.text }}>{meta.label}</div>
                    <div style={{ fontSize: 11, color: C.muted, fontFamily: 'monospace' }}>{metric}</div>
                  </td>
                  <td style={{ padding: '10px 14px', fontSize: 12, color: C.sub, maxWidth: 220 }}>{meta.desc}</td>
                  <td style={{ padding: '10px 14px', width: 120 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <input type="number" min={0} max={100} value={getVal(metric, 'weight')}
                        onChange={e => setVal(metric, 'weight', e.target.value)}
                        style={{ ...inp, width: 60 }} />
                      <div style={{ flex: 1, height: 6, background: C.border, borderRadius: 3, overflow: 'hidden' }}>
                        <div style={{ height: '100%', width: `${weight}%`, background: sm.color, borderRadius: 3, transition: 'width 200ms ease' }} />
                      </div>
                    </div>
                  </td>
                  <td style={{ padding: '10px 14px', width: 100 }}>
                    <input type="number" min={0.1} max={5} step={0.1} value={getVal(metric, 'sensitivity')}
                      onChange={e => setVal(metric, 'sensitivity', e.target.value)}
                      style={{ ...inp, width: 70 }} />
                  </td>
                  <td style={{ padding: '10px 14px' }}>
                    {isDirty && (
                      <button onClick={() => save(metric)} disabled={isSaving}
                        style={{ fontSize: 11, padding: '4px 12px', borderRadius: 6, border: 'none', background: C.teal, color: '#fff', cursor: 'pointer', fontWeight: 700 }}>
                        {isSaving ? 'Saving…' : 'Save'}
                      </button>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
          <tfoot>
            <tr style={{ borderTop: `2px solid ${C.border}`, background: C.canvas }}>
              <td colSpan={2} style={{ padding: '8px 14px', fontSize: 12, fontWeight: 700, color: C.text }}>Total max deduction</td>
              <td style={{ padding: '8px 14px', fontSize: 14, fontWeight: 900, color: totalWeight > 100 ? C.red : C.text }}>
                {totalWeight} pts {totalWeight > 100 && <span style={{ fontSize: 11, color: C.red }}>(exceeds 100 — health can hit 0)</span>}
              </td>
              <td colSpan={2} />
            </tr>
          </tfoot>
        </table>
      </div>

      {savedMsg && (
        <div style={{ padding: '8px 14px', borderRadius: 8, background: C.greenPale, color: C.green, fontSize: 13, fontWeight: 700, marginBottom: 12 }}>
          ✓ {savedMsg}
        </div>
      )}

      {/* Stage-inference rules */}
      <div style={{ padding: '14px 16px', background: C.white, border: `1px solid ${C.border}`, borderRadius: 10, fontSize: 12, color: C.sub, lineHeight: 1.8 }}>
        <div style={{ fontSize: 13, fontWeight: 700, color: C.text, marginBottom: 8 }}>Auto-inference rules</div>
        <div>• <strong>go_live_date IS NULL or future</strong> → Pre-launch</div>
        <div>• <strong>0–90 days since go_live_date</strong> → Hypercare</div>
        <div>• <strong>91+ days since go_live_date</strong> → Steady-state</div>
        <div>• <strong>health_stage manually set</strong> → overrides auto-inference (Admin → Projects → set stage)</div>
        <div>• <strong>AT_RISK</strong> is always manual — set by admin or CSM on the project</div>
      </div>

    </div>
  )
}
