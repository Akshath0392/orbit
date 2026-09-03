import { useC } from '../ThemeContext'

interface OrbitLogoProps {
  size?: number
}

export function OrbitLogo({ size = 42 }: OrbitLogoProps) {
  const C = useC()
  const cx = size / 2
  const cy = size / 2
  const outerR = size / 2 - 2          // circle radius (inside 2px stroke)
  const ringRx = size * 0.76           // orbital ring long axis
  const ringRy = size * 0.21           // orbital ring short axis
  const dotR   = size * 0.12           // central dot radius

  return (
    <svg
      width={size}
      height={size}
      viewBox={`0 0 ${size} ${size}`}
      aria-hidden="true"
      style={{ flexShrink: 0, overflow: 'visible' }}
    >
      {/* Outer planet circle */}
      <circle
        cx={cx} cy={cy} r={outerR}
        fill="none" stroke={C.indigo} strokeWidth="2"
      />
      {/* Orbital ring — ellipse rotated -28° */}
      <ellipse
        cx={cx} cy={cy} rx={ringRx} ry={ringRy}
        fill="none" stroke={C.amber} strokeWidth="2"
        transform={`rotate(-28 ${cx} ${cy})`}
      />
      {/* Core dot */}
      <circle cx={cx} cy={cy} r={dotR} fill={C.purple} />
    </svg>
  )
}
