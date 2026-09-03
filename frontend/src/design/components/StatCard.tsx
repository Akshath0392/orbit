import { useC } from '../ThemeContext'
import { R } from '../theme'

interface StatCardProps {
  label: string
  value: string | number
  sub?: string
  color?: string
  icon?: string
}

export function StatCard({ label, value, sub, color, icon }: StatCardProps) {
  const C = useC()
  return (
    <div style={{
      background: C.white, border: `1px solid ${C.border}`, borderRadius: R.sm,
      padding: '18px 16px', display: 'flex', flexDirection: 'column', gap: 4,
      boxShadow: C.shadowSm,
      transition: 'transform 160ms ease, box-shadow 160ms ease, border-color 160ms ease',
      cursor: 'default',
    }}
    onMouseEnter={e => {
      const el = e.currentTarget
      el.style.transform = 'translateY(-2px)'
      el.style.boxShadow = '0 14px 30px rgba(91,124,250,0.13)'
      el.style.borderColor = C.indigo
    }}
    onMouseLeave={e => {
      const el = e.currentTarget
      el.style.transform = ''
      el.style.boxShadow = C.shadowSm
      el.style.borderColor = C.border
    }}>
      <div style={{
        fontSize: 11, color: C.muted, fontWeight: 800, letterSpacing: 0.6,
        textTransform: 'uppercase', display: 'flex', alignItems: 'center', gap: 5
      }}>
        {icon && <span style={{ fontSize: 13 }}>{icon}</span>}
        {label}
      </div>
      <div style={{ fontSize: 34, fontWeight: 950, color: color || C.text, letterSpacing: -0.5, lineHeight: 1 }}>
        {value}
      </div>
      {sub && <div style={{ fontSize: 12, color: C.muted, fontWeight: 700 }}>{sub}</div>}
    </div>
  )
}
