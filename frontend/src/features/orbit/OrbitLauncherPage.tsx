import { useNavigate } from 'react-router-dom'
import { useC } from '../../design/ThemeContext'
import { useFlags } from '../../app/featureFlags'
import { PRODUCT_NAV, useAllowedScreenIds, visibleNav, type NavItem } from '../../layout/nav'

// The "Orbit" launcher (/orbit): the hub from which every product page is
// reachable now that the sidebar is gone. Radar leads as a hero tile; the rest
// render as gated tiles (RBAC × feature flags — same gate the sidebar used).
export function OrbitLauncherPage() {
  const C = useC()
  const navigate = useNavigate()
  const flags = useFlags()
  const allowed = useAllowedScreenIds()

  const items = visibleNav(PRODUCT_NAV, allowed, flags)
  const hero = items.find(i => i.id === 'radar')
  const rest = items.filter(i => i.id !== 'radar')

  const Tile = ({ n, big = false }: { n: NavItem; big?: boolean }) => (
    <button onClick={() => navigate(`/${n.id}`)}
      style={{
        display: 'flex', alignItems: 'flex-start', gap: 14, textAlign: 'left',
        background: big ? C.indigoPale : C.white, border: `1px solid ${big ? C.indigo : C.border}`,
        borderRadius: 16, padding: big ? '22px 24px' : '18px 20px', cursor: 'pointer',
        transition: 'transform 160ms ease, border-color 160ms ease, box-shadow 160ms ease',
        gridColumn: big ? '1 / -1' : 'auto',
      }}
      onMouseEnter={e => { const el = e.currentTarget as HTMLElement; el.style.transform = 'translateY(-2px)'; el.style.borderColor = C.indigo; el.style.boxShadow = '0 10px 28px rgba(0,0,0,0.10)' }}
      onMouseLeave={e => { const el = e.currentTarget as HTMLElement; el.style.transform = 'none'; el.style.borderColor = big ? C.indigo : C.border; el.style.boxShadow = 'none' }}>
      <span style={{
        width: big ? 46 : 40, height: big ? 46 : 40, flexShrink: 0, display: 'grid', placeItems: 'center',
        borderRadius: 12, background: C.mint, color: C.indigo, fontSize: big ? 22 : 18, fontWeight: 700,
      }}>{n.sym}</span>
      <div style={{ minWidth: 0 }}>
        <div style={{ fontSize: big ? 18 : 15, fontWeight: 750, color: C.text, letterSpacing: -0.3 }}>{n.label}</div>
        {n.desc && <div style={{ fontSize: 12.5, color: C.sub, fontWeight: 500, marginTop: 3 }}>{n.desc}</div>}
      </div>
    </button>
  )

  return (
    <div style={{ maxWidth: 1340, margin: '0 auto', padding: '32px 24px 80px' }}>
      <div style={{ marginBottom: 24 }}>
        <div style={{ fontSize: 11.5, fontWeight: 800, letterSpacing: 1, color: C.indigo, textTransform: 'uppercase' }}>Orbit</div>
        <h1 style={{ margin: '6px 0 0', fontSize: 30, fontWeight: 820, letterSpacing: -0.8, color: C.text }}>Where would you like to go?</h1>
        <p style={{ margin: '8px 0 0', fontSize: 14, color: C.sub, fontWeight: 500 }}>Every delivery workspace, one hop away.</p>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: 16 }}>
        {hero && <Tile n={hero} big />}
        {rest.map(n => <Tile key={n.id} n={n} />)}
      </div>
    </div>
  )
}
