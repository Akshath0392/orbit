import { ReactNode } from 'react'
import { useC } from '../ThemeContext'
import { useIsMobile } from '../useBreakpoint'
import { Breadcrumbs, Crumb } from './Breadcrumbs'

interface PageHeaderProps {
  title: string
  subtitle?: string
  actions?: ReactNode
  breadcrumbs?: Crumb[]
}

export function PageHeader({ title, subtitle, actions, breadcrumbs }: PageHeaderProps) {
  const C = useC()
  const mobile = useIsMobile()
  return (
    <div style={{
      display: 'flex',
      flexDirection: mobile ? 'column' : 'row',
      alignItems: mobile ? 'stretch' : 'center',
      justifyContent: 'space-between',
      gap: mobile ? 10 : 16,
      marginBottom: 18,
    }}>
      <div>
        {breadcrumbs && <div style={{ marginBottom: 6 }}><Breadcrumbs items={breadcrumbs} /></div>}
        <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>{title}</div>
        {subtitle && <div style={{ fontSize: 12, color: C.sub, marginTop: 3 }}>{subtitle}</div>}
      </div>
      {actions && <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>{actions}</div>}
    </div>
  )
}
