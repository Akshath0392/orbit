import { ReactNode } from 'react'
import { useC } from '../ThemeContext'
import { R } from '../theme'
import { ScoreRing, RagTone } from './ScoreRing'

export interface RankTileRow { label: ReactNode; value: ReactNode }

interface RankTileProps {
  rank: number
  title: string
  subtitle?: ReactNode         // e.g. "12,400 users" line under the name
  score: string | number       // shown inside the .hs-ring
  scoreTone?: RagTone | null
  scoreTitle?: string
  rows?: RankTileRow[]
  lead?: boolean               // mock .pod-bm.lead — rank-1 indigo border + glow
  grid3?: boolean              // single-POD mode: metrics re-flow to mock .bm-grid (3 columns)
  onClick?: () => void
}

// POD benchmarking card — mock .card.pod-bm: rank chip + name header, .hs-ring
// score at the right, .wb-metric dashed rows below (3-col .bm-grid when the
// page is locked to one POD).
export function RankTile({ rank, title, subtitle, score, scoreTone, scoreTitle, rows = [], lead, grid3, onClick }: RankTileProps) {
  const C = useC()
  return (
    <div onClick={onClick} style={{
      background: C.white, borderRadius: R.sm, padding: 18, minWidth: 0,
      border: `1px solid ${lead ? C.indigo : C.border}`,
      boxShadow: lead ? '0 8px 24px rgba(91,124,250,.22)' : C.shadowSm,
      cursor: onClick ? 'pointer' : 'default',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
            <span style={{
              display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
              minWidth: 26, height: 20, borderRadius: 7, background: C.indigo, color: '#fff',
              fontSize: 11, fontWeight: 800, padding: '0 6px', marginRight: 4,
            }}>#{rank}</span>
            <b style={{ fontSize: 16, letterSpacing: -0.3, color: C.text }}>{title}</b>
          </div>
          {subtitle && <div style={{ fontSize: 11, color: C.sub, fontWeight: 600, marginTop: 3 }}>{subtitle}</div>}
        </div>
        <ScoreRing value={score} tone={scoreTone} label="score" size="sm" title={scoreTitle} />
      </div>
      <div style={grid3 ? { display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '2px 34px' } : undefined}>
        {rows.map((r, i) => (
          <div key={i} style={{
            display: 'flex', alignItems: 'baseline', justifyContent: 'space-between',
            padding: '8px 0', borderTop: i === 0 ? 'none' : `1px dashed ${C.border}`,
          }}>
            <span style={{ fontSize: 12.5, color: C.sub }}>{r.label}</span>
            <span style={{ fontSize: 14, fontWeight: 780, color: C.text }}>{r.value}</span>
          </div>
        ))}
      </div>
    </div>
  )
}
