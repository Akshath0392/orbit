import { CSSProperties, ReactNode, useState } from 'react'
import { useC } from '../ThemeContext'
import { R } from '../theme'

// Plain-language hover bubble: a one-line explainer that appears below an
// element on hover. Distinct from the ⓘ modal (InfoDot) — this is per-KPI /
// per-chip help so a reviewer can tell what a single number or legend entry
// means without leaving the chart. Uses the app's surface tokens (like the
// chart hover tooltip) so it reads correctly in both themes: white card on
// light, dark card on dark. Opaque + high z-index so it floats cleanly over
// legend chips instead of showing through them.
export function TipBubble({ tip, show }: { tip: string; show: boolean }) {
  const C = useC()
  if (!show) return null
  return (
    <span role="tooltip" style={{
      position: 'absolute', top: 'calc(100% + 8px)', left: '50%', transform: 'translateX(-50%)',
      width: 250, maxWidth: '80vw', background: C.white, color: C.text,
      border: `1px solid ${C.borderMed}`, borderRadius: R.sm, padding: '10px 13px',
      fontSize: 11.8, fontWeight: 550, lineHeight: 1.55, textAlign: 'left',
      textTransform: 'none', letterSpacing: 0, whiteSpace: 'normal',
      boxShadow: C.shadow, zIndex: 60, pointerEvents: 'none',
    }}>{tip}</span>
  )
}

// Wraps any element (chips, badges) and shows a TipBubble on hover/focus.
// KPI cards render their own bubble inline (they can't wrap themselves), so
// this is for the surrounding chips. No tip → renders children untouched.
// While shown, the wrapper is lifted above sibling chips so the bubble is
// never painted under the next chip in the legend row.
export function HoverTip({ tip, children, style }:
  { tip?: string; children: ReactNode; style?: CSSProperties }) {
  const [show, setShow] = useState(false)
  if (!tip) return <>{children}</>
  return (
    <span
      onMouseEnter={() => setShow(true)} onMouseLeave={() => setShow(false)}
      onFocus={() => setShow(true)} onBlur={() => setShow(false)}
      style={{ position: 'relative', display: 'inline-flex', zIndex: show ? 60 : undefined, ...style }}
    >
      {children}
      <TipBubble tip={tip} show={show} />
    </span>
  )
}
