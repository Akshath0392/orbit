import { useEffect, useRef, useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useTheme, useC } from '../design/ThemeContext'
import { useStore } from '../app/store'
import { useFlags, flagOn } from '../app/featureFlags'
import { ADMIN_NAV, ROLE_LABEL, OrbitMark, useAllowedScreenIds, visibleNav } from './nav'

// Topbar-only app chrome (replaces the retired Sidebar and MobileTopBar).
// Brand → /orbit launcher; alerts bell → /alerts (gated); avatar → admin dropdown.
export function TopBar() {
  const C = useC()
  const { theme, toggleTheme } = useTheme()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const flags = useFlags()
  const user = useStore(s => s.user)
  const setUser = useStore(s => s.setUser)
  const allowed = useAllowedScreenIds()
  const [menuOpen, setMenuOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  const adminItems = visibleNav(ADMIN_NAV, allowed, flags)
  const alertsOn = allowed.includes('alerts') && flagOn(flags, 'screen.alerts')

  // Close the menu on route change, outside click, or Escape.
  useEffect(() => { setMenuOpen(false) }, [pathname])
  useEffect(() => {
    if (!menuOpen) return
    const onClick = (e: MouseEvent) => { if (menuRef.current && !menuRef.current.contains(e.target as Node)) setMenuOpen(false) }
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setMenuOpen(false) }
    document.addEventListener('mousedown', onClick)
    document.addEventListener('keydown', onKey)
    return () => { document.removeEventListener('mousedown', onClick); document.removeEventListener('keydown', onKey) }
  }, [menuOpen])

  const firstLast = user
    ? `${user.name.split(' ')[0]} ${(user.name.split(' ').slice(-1)[0] ?? '')[0] ?? ''}.`.trim()
    : 'User'

  return (
    <div style={{
      position: 'sticky', top: 0, zIndex: 200,
      background: C.white, borderBottom: `1px solid ${C.border}`,
      backdropFilter: 'blur(8px)',
    }}>
      <div style={{
        maxWidth: 1340, margin: '0 auto', padding: '10px 24px',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12,
      }}>
        {/* Brand → Orbit launcher */}
        <button onClick={() => navigate('/orbit')} aria-label="Orbit launcher"
          style={{ display: 'flex', alignItems: 'center', gap: 11, background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}>
          <OrbitMark size={32} />
          <span style={{ fontSize: 20, fontWeight: 800, color: C.text, letterSpacing: -0.5 }}>orbit</span>
        </button>

        {/* Right cluster */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          {alertsOn && (
            <button onClick={() => navigate('/alerts')} title="Alert center" aria-label="Alert center"
              style={{ width: 38, height: 38, display: 'grid', placeItems: 'center', background: 'none',
                border: `1px solid ${C.border}`, borderRadius: 10, cursor: 'pointer', color: C.sub }}
              onMouseEnter={e => { (e.currentTarget as HTMLElement).style.color = C.indigo; (e.currentTarget as HTMLElement).style.borderColor = C.indigo }}
              onMouseLeave={e => { (e.currentTarget as HTMLElement).style.color = C.sub; (e.currentTarget as HTMLElement).style.borderColor = C.border }}>
              <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" /><path d="M13.7 21a2 2 0 0 1-3.4 0" />
              </svg>
            </button>
          )}

          {/* Avatar + dropdown */}
          <div ref={menuRef} style={{ position: 'relative' }}>
            <button onClick={() => setMenuOpen(o => !o)} aria-haspopup="menu" aria-expanded={menuOpen} title={user?.name || 'Account'}
              style={{ width: 36, height: 36, borderRadius: '50%', border: 'none', cursor: 'pointer',
                background: user?.avatarColor || C.indigo, color: '#fff', fontSize: 12, fontWeight: 800 }}>
              {user?.initials || 'U'}
            </button>

            {menuOpen && (
              <div role="menu" style={{
                position: 'absolute', top: 46, right: 0, minWidth: 232,
                background: C.white, border: `1px solid ${C.border}`, borderRadius: 12,
                boxShadow: '0 12px 40px rgba(0,0,0,0.18)', padding: 6, zIndex: 300,
              }}>
                {/* Identity header */}
                <div style={{ padding: '8px 10px 10px', borderBottom: `1px solid ${C.border}`, marginBottom: 6 }}>
                  <div style={{ fontSize: 13, fontWeight: 700, color: C.text, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{firstLast}</div>
                  <div style={{ fontSize: 11, color: C.sub, fontWeight: 600 }}>{ROLE_LABEL[user?.role ?? ''] ?? user?.role ?? 'PM'}</div>
                </div>

                {/* Admin + system links (gated) */}
                {adminItems.map(n => (
                  <button key={n.id} role="menuitem" onClick={() => { setMenuOpen(false); navigate(`/${n.id}`) }}
                    style={{ display: 'flex', alignItems: 'center', gap: 10, width: '100%', textAlign: 'left',
                      background: 'none', border: 'none', cursor: 'pointer', padding: '8px 10px', borderRadius: 8,
                      color: C.text, fontSize: 13, fontWeight: 500 }}
                    onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = C.indigoPale}
                    onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = 'transparent'}>
                    <span style={{ width: 20, textAlign: 'center', color: C.indigo }}>{n.sym}</span>{n.label}
                  </button>
                ))}

                <div style={{ borderTop: `1px solid ${C.border}`, marginTop: 6, paddingTop: 6, display: 'flex', gap: 6 }}>
                  <button role="menuitem" onClick={toggleTheme}
                    style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6,
                      background: 'none', border: `1px solid ${C.border}`, borderRadius: 8, cursor: 'pointer',
                      padding: '8px 10px', color: C.sub, fontSize: 12, fontWeight: 600 }}>
                    {theme === 'dark' ? '☀ Light' : '◑ Dark'}
                  </button>
                  <button role="menuitem" onClick={() => { setMenuOpen(false); setUser(null) }}
                    style={{ flex: 1, background: 'none', border: `1px solid ${C.border}`, borderRadius: 8, cursor: 'pointer',
                      padding: '8px 10px', color: C.red, fontSize: 12, fontWeight: 700 }}>
                    Sign out
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
