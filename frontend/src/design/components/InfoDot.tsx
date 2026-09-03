import { useState } from 'react'
import { useC } from '../ThemeContext'
import { R } from '../theme'

// Small ⓘ that toggles an explainer popover (mock .info-i / openInfo).
// Metric definitions belong next to the metric, not in a wiki nobody opens.
export function InfoDot({ title, body }: { title: string; body: string }) {
  const C = useC()
  const [open, setOpen] = useState(false)
  return (
    <span style={{ position: 'relative', display: 'inline-flex' }}>
      <button
        aria-label={`About ${title}`}
        onClick={e => { e.stopPropagation(); setOpen(o => !o) }}
        style={{
          width: 16, height: 16, borderRadius: '50%', border: `1px solid ${C.borderMed}`,
          background: C.white, color: C.sub, fontSize: 10, fontWeight: 700,
          cursor: 'pointer', lineHeight: 1, display: 'grid', placeItems: 'center', padding: 0,
        }}>
        i
      </button>
      {open && (
        <>
          <div onClick={() => setOpen(false)} style={{ position: 'fixed', inset: 0, zIndex: 300 }} />
          <div style={{
            position: 'absolute', top: 22, left: -8, zIndex: 301, width: 280,
            background: C.white, border: `1px solid ${C.border}`, borderRadius: R.sm,
            boxShadow: C.shadow, padding: '12px 14px', cursor: 'default',
          }}>
            <div style={{ fontSize: 12, fontWeight: 800, color: C.text, marginBottom: 5 }}>{title}</div>
            <div style={{ fontSize: 12, color: C.sub, lineHeight: 1.55, whiteSpace: 'pre-line' }}>{body}</div>
          </div>
        </>
      )}
    </span>
  )
}
