import { ReactNode } from 'react'
import { useC } from '../ThemeContext'
import { RagTone } from './ScoreRing'

export interface ScorecardMini {
  label: string
  value: ReactNode
  onClick?: () => void
}

interface ScorecardTileProps {
  title: string
  health?: { value: number; tone: RagTone } | null   // null → "—" chip (feed pending)
  subtitle?: ReactNode                               // "{pod} · {users} users" line
  minis?: ScorecardMini[]                            // 2×2 metric boxes (mock .cli-mini)
  links?: { label: string; onClick: () => void }[]
  onClick?: () => void
}

// Client scorecard tile — mock .cli-tile: 19px name + .hs-chip health chip,
// pod/users line, 2×2 .cli-mini metric boxes (20px values), linkish footer.
export function ScorecardTile({ title, health, subtitle, minis = [], links = [], onClick }: ScorecardTileProps) {
  const C = useC()
  const chipBg = health ? (health.tone === 'g' ? C.greenPale : health.tone === 'a' ? C.amberPale : C.redPale) : C.mint
  const chipColor = health ? (health.tone === 'g' ? C.greenDeep : health.tone === 'a' ? C.amberDeep : C.redDeep) : C.muted
  return (
    <div onClick={onClick} style={{
      background: C.white, border: `1px solid ${C.border}`, borderRadius: 16,
      padding: '22px 24px', boxShadow: C.shadowSm, minWidth: 0,
      cursor: onClick ? 'pointer' : 'default',
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 2 }}>
        <b style={{ fontSize: 19, letterSpacing: -0.4, color: C.text, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{title}</b>
        <span title="Delivery Health Score" style={{
          minWidth: 44, height: 30, borderRadius: 10, display: 'inline-flex', alignItems: 'center',
          justifyContent: 'center', fontWeight: 800, fontSize: 16, padding: '0 7px', flexShrink: 0,
          background: chipBg, color: chipColor,
        }}>{health ? health.value : '—'}</span>
      </div>
      {subtitle && <div style={{ fontSize: 12.5, color: C.sub, fontWeight: 600, marginBottom: 14 }}>{subtitle}</div>}
      {minis.length > 0 && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 10 }}>
          {minis.map(m => (
            <div key={m.label}
              onClick={m.onClick ? e => { e.stopPropagation(); m.onClick!() } : undefined}
              style={{ background: C.indigoFaint, borderRadius: 12, padding: '14px 6px', textAlign: 'center', cursor: m.onClick ? 'pointer' : undefined }}>
              <span style={{ display: 'block', fontSize: 10.5, fontWeight: 700, textTransform: 'uppercase', letterSpacing: 0.4, color: C.muted, marginBottom: 4 }}>{m.label}</span>
              <b style={{ fontSize: 20, letterSpacing: -0.5, color: C.text }}>{m.value}</b>
            </div>
          ))}
        </div>
      )}
      {links.length > 0 && (
        <div style={{ display: 'flex', gap: 12, marginTop: 10 }}>
          {links.map(l => (
            <span key={l.label}
              onClick={e => { e.stopPropagation(); l.onClick() }}
              style={{ fontSize: 11, color: C.tealDeep, fontWeight: 700, cursor: 'pointer' }}>
              {l.label}
            </span>
          ))}
        </div>
      )}
    </div>
  )
}
