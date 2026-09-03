import { ReactNode } from 'react'
import { useC } from '../ThemeContext'
import { R } from '../theme'

// Standard card chrome for data tables. The inner overflow-x keeps wide tables
// usable on small screens (horizontal scroll) instead of squashing columns.
// `footer` (e.g. <Pagination/>) stays outside the scroll area.
export function TableWrap({ children, footer }: { children: ReactNode; footer?: ReactNode }) {
  const C = useC()
  return (
    <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: R.lg, boxShadow: C.shadow, overflow: 'hidden' }}>
      <div style={{ overflowX: 'auto' }}>{children}</div>
      {footer}
    </div>
  )
}
