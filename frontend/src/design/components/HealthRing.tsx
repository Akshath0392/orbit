import { useC } from '../ThemeContext'

interface HealthRingProps {
  score: number            // 0–100
  size?: number
  greenAt?: number         // per-client thresholds (locked convention #7) — pass them in
  amberAt?: number
}

// SVG score ring (mock .hs-ring). Colour derives from the provided thresholds,
// never hardcoded 80/60 at call sites with real client data.
export function HealthRing({ score, size = 54, greenAt = 80, amberAt = 60 }: HealthRingProps) {
  const C = useC()
  const clamped = Math.max(0, Math.min(100, score))
  const color = clamped >= greenAt ? C.green : clamped >= amberAt ? C.amber : C.red
  const stroke = 5
  const r = (size - stroke) / 2
  const circ = 2 * Math.PI * r
  return (
    <svg width={size} height={size} role="img" aria-label={`Health score ${clamped}`}>
      <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={C.border} strokeWidth={stroke} />
      <circle
        cx={size / 2} cy={size / 2} r={r} fill="none"
        stroke={color} strokeWidth={stroke} strokeLinecap="round"
        strokeDasharray={`${(clamped / 100) * circ} ${circ}`}
        transform={`rotate(-90 ${size / 2} ${size / 2})`}
      />
      <text x="50%" y="50%" dominantBaseline="central" textAnchor="middle"
        style={{ fontSize: size * 0.3, fontWeight: 800, fill: color }}>
        {Math.round(clamped)}
      </text>
    </svg>
  )
}
