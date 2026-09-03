import { useC } from '../ThemeContext'
import { R } from '../theme'

export interface Segment { label: string; value: number; color: string }
export interface SegmentBarProps {
  segments: Segment[]
  height?: number
  onSegmentClick?: (label: string) => void
}

// Proportional multi-segment bar (mock .seg-bar) — CSAT drills, backlog-aging
// splits. Zero segments vanish; flexBasis keeps tiny nonzero slices visible.
export function SegmentBar({ segments, height = 22, onSegmentClick }: SegmentBarProps) {
  const C = useC()
  const visible = segments.filter(s => s.value > 0)
  if (visible.length === 0) {
    return <div style={{ height, borderRadius: R.sm, background: C.mintFaint }} />
  }
  return (
    <div style={{ display: 'flex', height, borderRadius: R.sm, overflow: 'hidden' }}>
      {visible.map(s => (
        <div key={s.label}
          title={`${s.label}: ${s.value}`}
          onClick={onSegmentClick ? () => onSegmentClick(s.label) : undefined}
          style={{
            flexGrow: s.value, flexBasis: 14, minWidth: 0, background: s.color,
            display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden',
            fontSize: 11, fontWeight: 700, color: '#fff',
            cursor: onSegmentClick ? 'pointer' : 'default',
          }}>
          {s.value}
        </div>
      ))}
    </div>
  )
}
