import { useC } from '../ThemeContext'
import { PIE_PAL } from '../theme'

export interface DonutDatum { label: string; value: number }
export interface DonutChartProps {
  data: DonutDatum[]
  holeValue: string | number
  holeLabel: string
  onSliceClick?: (label: string) => void   // caller pre-groups top-N + 'Others'
  size?: number
  note?: string                            // small caption under the legend
}

// Mock .pie-wrap — 218px conic-gradient donut with the legend BESIDE it:
// .pl-row dashed rows (name · count · %), clickable except the 'Others' bucket.
export function DonutChart({ data, holeValue, holeLabel, onSliceClick, size = 218, note }: DonutChartProps) {
  const C = useC()
  const total = data.reduce((s, d) => s + d.value, 0)
  const pct = (v: number) => total ? Math.round((v / total) * 100) : 0
  let acc = 0
  const stops = data.map((d, i) => {
    const from = acc
    acc += total ? (d.value / total) * 100 : 0
    return `${PIE_PAL[i % PIE_PAL.length]} ${from.toFixed(2)}% ${acc.toFixed(2)}%`
  })
  const inset = Math.round(size * (54 / 218))   // mock hole proportion
  return (
    <div style={{ display: 'flex', gap: 34, alignItems: 'center', flexWrap: 'wrap' }}>
      <div style={{
        position: 'relative', width: size, height: size, borderRadius: '50%', flexShrink: 0,
        background: total ? `conic-gradient(${stops.join(', ')})` : C.mintFaint,
        boxShadow: C.shadow,
      }}>
        <div style={{
          position: 'absolute', inset, borderRadius: '50%', background: C.white,
          display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
        }}>
          <b style={{ fontSize: 26, color: C.text, letterSpacing: -1, lineHeight: 1.1 }}>{holeValue}</b>
          <span style={{ fontSize: 10, fontWeight: 700, textTransform: 'uppercase', letterSpacing: 0.4, color: C.muted }}>{holeLabel}</span>
        </div>
      </div>
      <div style={{ flex: 1, minWidth: 250 }}>
        {data.map((d, i) => {
          const clickable = !!onSliceClick && d.label !== 'Others'
          return (
            <div key={d.label}
              onClick={clickable ? () => onSliceClick!(d.label) : undefined}
              style={{
                display: 'flex', alignItems: 'center', gap: 9, padding: '6px 0', fontSize: 12.5,
                borderBottom: i === data.length - 1 ? 'none' : `1px dashed ${C.border}`,
                cursor: clickable ? 'pointer' : 'default',
              }}>
              <i style={{ display: 'inline-block', width: 11, height: 11, borderRadius: 3, flexShrink: 0, background: PIE_PAL[i % PIE_PAL.length] }} />
              <span style={{
                flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                fontWeight: 650, color: clickable ? C.tealDeep : C.text,
              }}>{d.label}</span>
              <span style={{ color: C.sub }}><b style={{ color: C.text }}>{d.value}</b> · {pct(d.value)}%</span>
            </div>
          )
        })}
        {note && <div style={{ fontSize: 11, color: C.muted, marginTop: 8 }}>{note}</div>}
      </div>
    </div>
  )
}
