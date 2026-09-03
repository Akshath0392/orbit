import { useC } from '../ThemeContext'

interface AvatarProps {
  initials: string
  color?: string
  size?: number
}

export function Avatar({ initials, color, size = 28 }: AvatarProps) {
  const C = useC()
  const resolvedColor = color ?? C.indigo
  return (
    <div style={{
      width: size, height: size, borderRadius: '50%',
      background: resolvedColor, display: 'flex', alignItems: 'center',
      justifyContent: 'center', fontSize: size * 0.38,
      fontWeight: 700, color: '#fff', flexShrink: 0
    }}>
      {initials}
    </div>
  )
}
