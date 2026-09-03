import { useC } from '../ThemeContext'

export type ChartGlyph = 'line' | 'bars' | 'stacked'
export interface ChartTypeOption<T extends string = string> {
  value: T
  label: string
  glyph: ChartGlyph
}

// The 14×12 glyphs share the segment's currentColor so active/idle tinting
// is pure CSS.
function Glyph({ kind }: { kind: ChartGlyph }) {
  if (kind === 'line') {
    return (
      <svg width={14} height={12} viewBox="0 0 14 12" aria-hidden>
        <path d="M1 9.5 L5 4.5 L8.5 7 L13 1.5" fill="none" stroke="currentColor"
              strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    )
  }
  if (kind === 'bars') {
    return (
      <svg width={14} height={12} viewBox="0 0 14 12" aria-hidden>
        <rect x={1}  y={5} width={3} height={7} rx={0.8} fill="currentColor" />
        <rect x={5.5} y={1} width={3} height={11} rx={0.8} fill="currentColor" />
        <rect x={10} y={7} width={3} height={5} rx={0.8} fill="currentColor" />
      </svg>
    )
  }
  return (
    <svg width={14} height={12} viewBox="0 0 14 12" aria-hidden>
      <rect x={2} y={6.5} width={4} height={5.5} rx={0.8} fill="currentColor" />
      <rect x={2} y={1} width={4} height={4.5} rx={0.8} fill="currentColor" opacity={0.45} />
      <rect x={8} y={4.5} width={4} height={7.5} rx={0.8} fill="currentColor" />
      <rect x={8} y={0} width={4} height={3.5} rx={0.8} fill="currentColor" opacity={0.45} />
    </svg>
  )
}

// Runtime chart-type switcher: compact segmented control rendered near a
// chart when the role's chart config grants runtimeToggle. Purely
// presentational — the caller owns the value (Shell persists it via the
// store), so this stays reusable across chart families.
export function ChartTypeToggle<T extends string>({ value, options, onChange, ariaLabel }: {
  value: T
  options: readonly ChartTypeOption<T>[]
  onChange: (v: T) => void
  ariaLabel: string
}) {
  const C = useC()
  return (
    <div role="group" aria-label={ariaLabel} style={{
      display: 'inline-flex', alignItems: 'stretch',
      border: `1px solid ${C.border}`, borderRadius: 8,
      background: C.white, overflow: 'hidden', flexShrink: 0,
    }}>
      {options.map((o, i) => {
        const active = o.value === value
        return (
          <button key={o.value} type="button" title={o.label} aria-pressed={active}
            onClick={() => onChange(o.value)}
            style={{
              display: 'inline-flex', alignItems: 'center', gap: 5,
              padding: '4px 9px', fontSize: 11, fontWeight: 700,
              border: 'none', cursor: 'pointer',
              borderLeft: i > 0 ? `1px solid ${C.border}` : 'none',
              background: active ? C.mintFaint : 'transparent',
              color: active ? C.tealDeep : C.muted,
              transition: 'background .15s, color .15s',
            }}>
            <Glyph kind={o.glyph} />
            <span style={{ whiteSpace: 'nowrap' }}>{o.label}</span>
          </button>
        )
      })}
    </div>
  )
}
