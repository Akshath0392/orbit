import { ReactNode } from 'react'

// Row of filter controls (selects, search inputs, action buttons). Wraps on
// narrow screens; use inside PageHeader's `actions` or above a table.
export function FilterBar({ children }: { children: ReactNode }) {
  return (
    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
      {children}
    </div>
  )
}
