import { useState, useEffect } from 'react'
import { Routes, Route, Navigate, useNavigate } from 'react-router-dom'
import { ThemeProvider, useC } from '../design/ThemeContext'
import { Shell } from '../layout/Shell'
import { useStore } from './store'
import { api } from '../api/client'
import { SlackLinkConfirmPage } from '../features/slack-link/SlackLinkConfirmPage'
import { readSnapshotParams } from './snapshotMode'

function LoginScreen({ onLogin }: { onLogin: (user: any, token: string) => void }) {
  const C = useC()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()

  useEffect(() => {
    const search = new URLSearchParams(window.location.search)
    // SSO delivers the token in the URL fragment (not the query string) so it is
    // not sent to the server or leaked via logs/Referer.
    const hash = new URLSearchParams(
      window.location.hash.startsWith('#') ? window.location.hash.slice(1) : ''
    )
    const token = hash.get('token')
    if (token) {
      onLogin({
        id:          Number(hash.get('id')),
        name:        hash.get('name') || '',
        email:       hash.get('email') || '',
        role:        hash.get('role') || 'PM',
        initials:    hash.get('initials') || '',
        avatarColor: hash.get('avatarColor') || '#5b7cfa',
      }, token)
      // Scrub the token from the address bar / history.
      window.history.replaceState(null, '', window.location.pathname)
      navigate('/radar', { replace: true })
      return
    }
    const ssoError = search.get('error')
    if (ssoError) setError(`Google sign-in failed: ${ssoError}`)
  }, [])

  const submit = async () => {
    setLoading(true); setError('')
    try {
      const res = await api.post('/auth/login', { email, password })
      onLogin(res.data.user, res.data.token)
      navigate('/radar')
    } catch {
      setError('Invalid email or password')
    } finally {
      setLoading(false)
    }
  }

  const INK   = C.text
  const TEAL  = C.indigo
  const LINE  = C.border
  const MUTED = C.muted
  const SOFT  = C.mintFaint

  return (
    <div style={{
      minHeight: '100vh', background: C.canvas,
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      padding: 24, fontFamily: 'Inter, ui-sans-serif, system-ui, sans-serif',
    }}>
      {/* Background orbital decoration */}
      <div style={{ position: 'fixed', inset: 0, overflow: 'hidden', pointerEvents: 'none', zIndex: 0 }}>
        <svg width="600" height="600" style={{ position: 'absolute', top: -100, right: -150, opacity: 0.06 }}>
          <circle cx="300" cy="300" r="280" fill="none" stroke={TEAL} strokeWidth="2" />
          <ellipse cx="300" cy="300" rx="460" ry="130" fill="none" stroke="#e0a323" strokeWidth="2" transform="rotate(-28 300 300)" />
          <circle cx="300" cy="300" r="35" fill="#b83280" opacity="0.5" />
        </svg>
        <svg width="400" height="400" style={{ position: 'absolute', bottom: -80, left: -100, opacity: 0.05 }}>
          <circle cx="200" cy="200" r="190" fill="none" stroke={TEAL} strokeWidth="2" />
          <ellipse cx="200" cy="200" rx="300" ry="85" fill="none" stroke="#e0a323" strokeWidth="2" transform="rotate(-28 200 200)" />
        </svg>
      </div>

      <div style={{ width: '100%', maxWidth: 420, position: 'relative', zIndex: 1 }}>
        {/* Logo */}
        <div style={{ textAlign: 'center', marginBottom: 28 }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 14, marginBottom: 8 }}>
            {/* Orbit logo mark */}
            <svg width="52" height="52" viewBox="0 0 52 52" style={{ overflow: 'visible' }}>
              <circle cx="26" cy="26" r="24" fill="none" stroke={TEAL} strokeWidth="2.5" />
              <ellipse cx="26" cy="26" rx="39" ry="11" fill="none" stroke="#e0a323" strokeWidth="2.5" transform="rotate(-28 26 26)" />
              <circle cx="26" cy="26" r="6" fill="#b83280" />
            </svg>
            <div style={{ textAlign: 'left' }}>
              <div style={{ fontSize: 42, fontWeight: 900, color: INK, letterSpacing: -1, lineHeight: 1 }}>orbit</div>
              <div style={{ fontSize: 11, fontWeight: 800, color: TEAL, textTransform: 'uppercase', letterSpacing: 1 }}>DELIVERY COMMAND CENTER</div>
            </div>
          </div>
        </div>

        {/* Card */}
        <div style={{
          background: C.white, borderRadius: 14, padding: '32px 32px 28px',
          boxShadow: '0 18px 60px rgba(0,0,0,0.18), 0 4px 16px rgba(0,0,0,0.08)',
          border: `1px solid ${LINE}`,
        }}>
          <div style={{ marginBottom: 24 }}>
            <div style={{ fontSize: 22, fontWeight: 800, color: INK, marginBottom: 3 }}>Sign in to workspace</div>
            <div style={{ fontSize: 13, color: MUTED, fontWeight: 600 }}>Delivery Command Center</div>
          </div>

          {/* Email */}
          <div style={{ marginBottom: 14 }}>
            <label style={{ display: 'block', fontSize: 12, fontWeight: 700, color: INK, marginBottom: 6 }}>Work email</label>
            <input
              type="email" value={email} onChange={e => setEmail(e.target.value)}
              placeholder="you@company.io"
              style={{
                width: '100%', minHeight: 40, padding: '0 12px', fontSize: 14,
                border: `1px solid ${LINE}`, borderRadius: 8, color: INK,
                background: C.white, outline: 'none', boxSizing: 'border-box',
                transition: 'border-color 160ms ease',
              }}
              onFocus={e => (e.target.style.borderColor = TEAL)}
              onBlur={e => (e.target.style.borderColor = LINE)}
            />
          </div>

          {/* Password */}
          <div style={{ marginBottom: 20 }}>
            <label style={{ display: 'block', fontSize: 12, fontWeight: 700, color: INK, marginBottom: 6 }}>Password</label>
            <input
              type="password" value={password} onChange={e => setPassword(e.target.value)}
              placeholder="••••••••"
              onKeyDown={e => e.key === 'Enter' && submit()}
              style={{
                width: '100%', minHeight: 40, padding: '0 12px', fontSize: 14,
                border: `1px solid ${LINE}`, borderRadius: 8, color: INK,
                background: C.white, outline: 'none', boxSizing: 'border-box',
                transition: 'border-color 160ms ease',
              }}
              onFocus={e => (e.target.style.borderColor = TEAL)}
              onBlur={e => (e.target.style.borderColor = LINE)}
            />
          </div>

          {error && (
            <div style={{ fontSize: 13, color: C.red, background: C.redPale, border: `1px solid ${C.red}`, borderRadius: 8, padding: '10px 12px', marginBottom: 16, fontWeight: 600 }}>
              {error}
            </div>
          )}

          {/* Sign in button */}
          <button onClick={submit} disabled={loading} style={{
            width: '100%', minHeight: 42, border: 'none', borderRadius: 8,
            background: loading ? MUTED : TEAL, color: '#fff',
            fontSize: 14, fontWeight: 800, cursor: loading ? 'not-allowed' : 'pointer',
            transition: 'background-color 160ms ease, transform 160ms ease',
            marginBottom: 16,
          }}>
            {loading ? 'Signing in…' : 'Sign in →'}
          </button>

          {/* Divider */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 14 }}>
            <div style={{ flex: 1, height: 1, background: LINE }} />
            <span style={{ fontSize: 12, color: MUTED, fontWeight: 700 }}>or continue with</span>
            <div style={{ flex: 1, height: 1, background: LINE }} />
          </div>

          {/* SSO buttons */}
          <div style={{ marginBottom: 20 }}>
            <button
              onClick={() => { window.location.href = '/api/v1/auth/google' }}
              style={{
                width: '100%', minHeight: 38, border: `1px solid ${LINE}`, borderRadius: 8,
                background: C.mint, fontSize: 13, color: INK, cursor: 'pointer', fontWeight: 700,
                transition: 'background-color 160ms ease',
              }}
              onMouseEnter={e => (e.currentTarget.style.background = C.indigoPale)}
              onMouseLeave={e => (e.currentTarget.style.background = C.mint)}>
              Continue with Google SSO
            </button>
          </div>

          {/* Role hint */}
          <div style={{ padding: '12px 14px', background: SOFT, borderRadius: 8, border: `1px solid ${LINE}` }}>
            <div style={{ fontSize: 11, fontWeight: 800, color: TEAL, textTransform: 'uppercase', letterSpacing: 0.5, marginBottom: 4 }}>Access</div>
            <div style={{ fontSize: 12, color: MUTED, lineHeight: 1.5 }}>
              Roles: Admin · PM · Leadership · Engineering · CSM · Revenue
            </div>
          </div>
        </div>

      </div>
    </div>
  )
}

function RequireAuth({ children }: { children: JSX.Element }) {
  const user = useStore(s => s.user)
  // Snapshot mode: the Playwright sidecar loads with `?snapshot=1&token=<jwt>`.
  // There's no zustand `user` (fresh localStorage in headless), but the URL JWT
  // is itself the auth — api/client attaches it on every request. Skip the
  // login bounce so the page can render for the screenshot.
  if (!user && !readSnapshotParams().enabled) return <Navigate to="/login" replace />
  return children
}

function AppRoutes() {
  const { setUser } = useStore()
  const handleLogin = (u: any, token: string) => setUser({ ...u, token })

  return (
    <Routes>
      <Route path="/login" element={<LoginScreen onLogin={handleLogin} />} />
      <Route path="/slack/link" element={<SlackLinkConfirmPage />} />
      <Route path="/*" element={
        <RequireAuth>
          <Shell />
        </RequireAuth>
      } />
    </Routes>
  )
}

export default function App() {
  return (
    <ThemeProvider>
      <AppRoutes />
    </ThemeProvider>
  )
}
