import { useC } from '../ThemeContext'

interface SectionHeadProps {
  title: string
  action?: string
  onAction?: () => void
}

export function SectionHead({ title, action, onAction }: SectionHeadProps) {
  const C = useC()
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }}>
      <div style={{
        fontSize: 11, fontWeight: 600, color: C.sub,
        letterSpacing: 0.5, textTransform: 'uppercase'
      }}>
        {title}
      </div>
      {action && (
        <span
          onClick={onAction}
          style={{ fontSize: 11, color: C.indigo, cursor: 'pointer', fontWeight: 500 }}
        >
          {action} →
        </span>
      )}
    </div>
  )
}
