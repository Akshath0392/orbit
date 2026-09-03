import { useC } from '../ThemeContext'

interface BurnBarProps {
  pct: number
  h?: number
}

export function BurnBar({ pct, h = 6 }: BurnBarProps) {
  const C = useC()
  const color = pct > 80 ? C.red : pct > 60 ? C.amber : C.green
  return (
    <div style={{ background: C.border, borderRadius: 4, overflow: 'hidden', height: h, width: '100%' }}>
      <div style={{
        width: `${Math.min(pct, 100)}%`,
        height: '100%',
        background: color,
        borderRadius: 4,
        transition: 'width 400ms ease, background-color 200ms ease',
      }} />
    </div>
  )
}
