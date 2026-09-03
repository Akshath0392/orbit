import { useNavigate } from 'react-router-dom'
import { useC } from '../ThemeContext'

export type Crumb = { label: string; to?: string }

// Mock `crumbs` pattern (Role ／ POD ／ Page): every drill-in page shows its
// trail; all crumbs except the last navigate. Replaces ad-hoc back buttons —
// explicit routes, not navigate(-1), so deep links behave the same.
export function Breadcrumbs({ items }: { items: Crumb[] }) {
  const C = useC()
  const navigate = useNavigate()
  if (items.length === 0) return null
  return (
    <nav aria-label="Breadcrumb" style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, flexWrap: 'wrap' }}>
      {items.map((it, i) => {
        const last = i === items.length - 1
        return (
          <span key={`${it.label}-${i}`} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            {i > 0 && <span style={{ color: C.muted }}>／</span>}
            {!last && it.to ? (
              <button onClick={() => navigate(it.to!)}
                style={{ background: 'none', border: 'none', padding: 0, color: C.indigo, cursor: 'pointer', fontSize: 12, fontWeight: 600 }}>
                {it.label}
              </button>
            ) : (
              <span style={{ color: last ? C.text : C.sub, fontWeight: last ? 700 : 500 }}>{it.label}</span>
            )}
          </span>
        )
      })}
    </nav>
  )
}
