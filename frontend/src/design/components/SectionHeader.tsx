import { ReactNode } from 'react'
import { useC } from '../ThemeContext'
import { InfoDot } from './InfoDot'

interface SectionHeaderProps {
  title: string
  subtitle?: string
  info?: { title: string; body: string }
  actions?: ReactNode
}

// Mock .section-head — plain 17px/750 h3 with right-aligned controls, small
// note line underneath when subtitle is set.
export function SectionHeader({ title, subtitle, info, actions }: SectionHeaderProps) {
  const C = useC()
  return (
    <div style={{ margin: '26px 0 14px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap' }}>
        <h3 style={{ margin: 0, fontSize: 17, fontWeight: 750, letterSpacing: -0.2, color: C.text, display: 'flex', alignItems: 'center', gap: 6 }}>
          {title}
          {info && <InfoDot title={info.title} body={info.body} />}
        </h3>
        {actions && <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>{actions}</div>}
      </div>
      {subtitle && <div style={{ fontSize: 12, color: C.sub, marginTop: 4 }}>{subtitle}</div>}
    </div>
  )
}
