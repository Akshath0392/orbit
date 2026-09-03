import { useC } from '../ThemeContext'

export interface BarSeries { name: string; values: number[]; color?: string }

interface GroupedBarsProps {
  labels: string[]                 // one per group, e.g. months or weeks
  seriesA: BarSeries               // defaults to amber (mock .gb.b1 — Created)
  seriesB?: BarSeries              // defaults to green (mock .gb.b2 — Resolved)
  big?: boolean                    // mock .gb-big: 106px area, 22px bars, larger labels
  scaleMax?: number                // share one scale across sibling charts
  onGroupClick?: (index: number) => void
}

// Mock .gb-chart / .gb-big — paired CSS bars with the value printed above every
// bar (.gv). Created = amber, Resolved = green (sanctioned status colours in
// the prod chart). No chart library (locked convention #1).
export function GroupedBars({ labels, seriesA, seriesB, big = false, scaleMax, onGroupClick }: GroupedBarsProps) {
  const C = useC()
  const all = [...seriesA.values, ...(seriesB?.values ?? [])]
  const mx = Math.max(1, scaleMax ?? 0, ...all)
  // mock: height = max(v / mx * 88, 6)px big · max(v / mx * 44, 4)px small
  const h = (v: number) => Math.max((v / mx) * (big ? 88 : 44), big ? 6 : 4)
  const barW = big ? 22 : 13
  const gvSize = big ? 11.5 : 9.5
  const bar = (v: number, color: string) => (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'flex-end', gap: 2 }}>
      <span style={{ fontSize: gvSize, fontWeight: 800, color: C.text, lineHeight: 1 }}>{v}</span>
      <div style={{ width: barW, borderRadius: '4px 4px 2px 2px', height: h(v), background: color }} />
    </div>
  )
  return (
    <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end' }}>
      {labels.map((label, i) => (
        <div key={`${label}-${i}`}
          onClick={onGroupClick ? () => onGroupClick(i) : undefined}
          title={`${label}: ${seriesA.name} ${seriesA.values[i] ?? 0}${seriesB ? ` · ${seriesB.name} ${seriesB.values[i] ?? 0}` : ''}`}
          style={{ flex: 1, textAlign: 'center', minWidth: 0, cursor: onGroupClick ? 'pointer' : 'default' }}>
          <div style={{ display: 'flex', gap: big ? 5 : 4, alignItems: 'flex-end', justifyContent: 'center', height: big ? 106 : 58 }}>
            {bar(seriesA.values[i] ?? 0, seriesA.color ?? C.amber)}
            {seriesB && bar(seriesB.values[i] ?? 0, seriesB.color ?? C.green)}
          </div>
          <span style={{ fontSize: big ? 12 : 10.5, color: C.text, fontWeight: 700, display: 'block', marginTop: 4 }}>{label}</span>
        </div>
      ))}
    </div>
  )
}
