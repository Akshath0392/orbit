import { useC } from '../ThemeContext'

export type RagTone = 'g' | 'a' | 'r'

interface ScoreRingProps {
  value: string | number
  tone?: RagTone | null      // null → neutral ring (score source pending)
  label?: string             // tiny caption inside the ring, e.g. "score" / "health"
  size?: 'sm' | 'lg'         // mock .hs-ring.sm 58px · .hs-ring 74px
  title?: string
}

// Mock .hs-ring — a plain circle with a 4px RAG border and the score inside
// (NOT a progress arc; that's HealthRing). sm: 58px/19px value · lg: 74px/24px.
export function ScoreRing({ value, tone, label = 'score', size = 'sm', title }: ScoreRingProps) {
  const C = useC()
  const px = size === 'sm' ? 58 : 74
  const vs = size === 'sm' ? 19 : 24
  const border = tone === 'g' ? C.green : tone === 'a' ? C.amber : tone === 'r' ? C.red : C.borderMed
  return (
    <div title={title} style={{
      width: px, height: px, borderRadius: '50%', flexShrink: 0,
      display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
      background: C.white, boxShadow: C.shadow, border: `4px solid ${border}`,
    }}>
      <b style={{ fontSize: vs, letterSpacing: -1, lineHeight: 1, color: C.text }}>{value}</b>
      <span style={{ fontSize: 9, fontWeight: 700, textTransform: 'uppercase', letterSpacing: 0.5, color: C.muted }}>{label}</span>
    </div>
  )
}
