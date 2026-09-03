import { useC } from '../ThemeContext'
import { R } from '../theme'
import { InfoDot } from './InfoDot'
import { TrendBars, TrendPoint } from './TrendBars'

export interface MetricCardProps {
  name: string
  value?: string | number
  unit?: string
  currentLabel?: string          // e.g. "Jul (current)"
  rag?: 'g' | 'a' | 'r'
  trend?: TrendPoint[]
  formula?: string
  thresholds?: string            // pre-formatted, e.g. "≤15 green · ≤30 amber · else red"
  info?: { title: string; body: string }
  onDrill?: () => void
  drillLabel?: string
  pending?: string               // awaiting-feed variant when set
}

// Delivery-health metric card (mock dhCard) — owns its Card chrome.
export function MetricCard({
  name, value, unit, currentLabel, rag, trend, formula, thresholds,
  info, onDrill, drillLabel = 'open items', pending,
}: MetricCardProps) {
  const C = useC()
  const ragStyle = rag && {
    g: { color: C.green, background: C.greenPale, label: 'Green' },
    a: { color: C.amberDeep, background: C.amberPale, label: 'Amber' },
    r: { color: C.red, background: C.redPale, label: 'Red' },
  }[rag]
  return (
    <div style={{
      background: C.white, border: `1px solid ${C.border}`, borderRadius: R.lg,
      boxShadow: C.shadowSm, padding: 16,
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <span style={{ fontSize: 13, fontWeight: 800, color: C.text }}>{name}</span>
        {info && <InfoDot title={info.title} body={info.body} />}
        {ragStyle && (
          <span style={{
            marginLeft: 'auto', fontSize: 10, fontWeight: 700, textTransform: 'uppercase',
            color: ragStyle.color, background: ragStyle.background,
            padding: '2px 8px', borderRadius: 999,
          }}>{ragStyle.label}</span>
        )}
      </div>
      {pending ? (
        <div style={{
          marginTop: 10, background: C.mintFaint, borderRadius: R.sm,
          padding: '14px 12px', fontSize: 12, color: C.muted,
        }}>{pending}</div>
      ) : (
        <>
          {value != null && (
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 6, marginTop: 8 }}>
              <span style={{ fontSize: 26, fontWeight: 800, color: C.text, letterSpacing: -1 }}>{value}</span>
              {unit && <span style={{ fontSize: 12, color: C.sub }}>{unit}</span>}
              {currentLabel && <span style={{ fontSize: 11, color: C.muted }}>{currentLabel}</span>}
            </div>
          )}
          {trend && (
            <div style={{ marginTop: 10 }}>
              <TrendBars points={trend} />
              <div style={{ fontSize: 10, color: C.muted, marginTop: 4 }}>
                Same formula computed per month · highlighted bar = current month
              </div>
            </div>
          )}
          {formula && <div style={{ fontSize: 11, color: C.sub, marginTop: 8 }}>{formula}</div>}
          {thresholds && <div style={{ fontSize: 10, color: C.muted, marginTop: 2 }}>{thresholds}</div>}
          {onDrill && (
            <div style={{ textAlign: 'right', marginTop: 8 }}>
              <span onClick={onDrill}
                style={{ fontSize: 12, color: C.indigo, fontWeight: 700, cursor: 'pointer' }}>
                {drillLabel}
              </span>
            </div>
          )}
        </>
      )}
    </div>
  )
}
