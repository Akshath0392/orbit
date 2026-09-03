import { useC } from '../ThemeContext'

interface TagProps {
  label: string
}

export function Tag({ label }: TagProps) {
  const C = useC()
  return (
    <span style={{
      fontSize: 10, padding: '2px 7px', borderRadius: 4,
      border: `1px solid ${C.border}`, color: C.sub,
      background: C.canvas, fontWeight: 500
    }}>
      {label}
    </span>
  )
}
