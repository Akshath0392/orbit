import { useC } from '../ThemeContext'

export interface TrendPoint { label: string; value: number }
export interface TrendBarsProps {
  points: TrendPoint[]
  currentIndex?: number          // defaults to the last point
  height?: number
  formatValue?: (v: number) => string
}

// Per-period value bars with current-period highlight (mock DH metric-card
// trend): past bars grey, current indigo, value above, period label below.
export function TrendBars({ points, currentIndex, height = 64, formatValue }: TrendBarsProps) {
  const C = useC()
  const cur = currentIndex ?? points.length - 1
  const max = Math.max(1, ...points.map(p => p.value))
  const fmt = formatValue ?? ((v: number) => String(v))
  return (
    <div style={{ display: 'flex', alignItems: 'flex-end', gap: 6 }}>
      {points.map((p, i) => (
        <div key={p.label} title={`${p.label}: ${p.value}`}
          style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 }}>
          <div style={{ fontSize: 10, fontWeight: 700, color: i === cur ? C.text : C.sub, whiteSpace: 'nowrap' }}>{fmt(p.value)}</div>
          <div style={{
            width: '100%', borderRadius: 3,
            height: Math.max(3, Math.round((p.value / max) * height)),
            background: i === cur ? C.indigo : C.borderMed,
          }} />
          <div style={{ fontSize: 10, color: C.muted, whiteSpace: 'nowrap', overflow: 'hidden', maxWidth: '100%' }}>{p.label}</div>
        </div>
      ))}
    </div>
  )
}
