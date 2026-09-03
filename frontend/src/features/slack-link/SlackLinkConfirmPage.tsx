import { useEffect, useState } from 'react'
import { useSearchParams, Link } from 'react-router-dom'
import { api } from '../../api/client'
import { useC } from '../../design/ThemeContext'

type Status = 'pending' | 'success' | 'error'

export function SlackLinkConfirmPage() {
  const C = useC()
  const [params] = useSearchParams()
  const token = params.get('token')
  const [status, setStatus] = useState<Status>('pending')
  const [message, setMessage] = useState('Linking your Slack account…')
  const [email, setEmail] = useState<string | null>(null)

  useEffect(() => {
    if (!token) {
      setStatus('error')
      setMessage('Missing token in URL.')
      return
    }
    api.post('/slack/link/confirm', { token })
      .then(res => {
        setStatus('success')
        setEmail(res.data?.email || null)
        setMessage('Your Slack account is now linked to Orbit.')
      })
      .catch(err => {
        setStatus('error')
        const code = err?.response?.data?.error
        setMessage(
          code === 'invalid_or_expired_token' ? 'This link has expired or already been used. Run `/orbit-link <your-email>` in Slack to request a new one.' :
          code === 'user_not_found' ? 'The email on this link does not match an Orbit account.' :
          'Unable to link your Slack account. Please try again.'
        )
      })
  }, [token])

  const colour = status === 'success' ? C.green : status === 'error' ? C.red : C.indigo
  const icon   = status === 'success' ? '✓' : status === 'error' ? '✕' : '…'

  return (
    <div style={{
      minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: C.canvas, padding: 24
    }}>
      <div style={{
        background: C.white, border: `1px solid ${C.border}`, borderRadius: 12,
        padding: '32px 40px', width: '100%', maxWidth: 420, textAlign: 'center'
      }}>
        <div style={{
          width: 56, height: 56, borderRadius: '50%', background: colour,
          color: '#fff', fontSize: 28, fontWeight: 600,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          margin: '0 auto 20px'
        }}>{icon}</div>
        <div style={{ fontSize: 18, fontWeight: 600, color: C.text, marginBottom: 8 }}>
          {status === 'success' ? 'Slack linked' : status === 'error' ? 'Link failed' : 'Linking…'}
        </div>
        <div style={{ fontSize: 13, color: C.sub, lineHeight: 1.5, marginBottom: 20 }}>
          {message}
        </div>
        {email && (
          <div style={{ fontSize: 12, color: C.sub, marginBottom: 20 }}>
            Linked as <code style={{ background: C.canvas, padding: '2px 6px', borderRadius: 4 }}>{email}</code>
          </div>
        )}
        {status === 'success' && (
          <div style={{ fontSize: 12, color: C.sub }}>
            You can close this tab and use Orbit commands in Slack — try <code style={{ background: C.canvas, padding: '2px 6px', borderRadius: 4 }}>/orbit alerts critical</code>.
          </div>
        )}
        {status !== 'pending' && (
          <div style={{ marginTop: 24 }}>
            <Link to="/" style={{ fontSize: 12, color: C.indigo, textDecoration: 'none' }}>
              ← Back to Orbit
            </Link>
          </div>
        )}
      </div>
    </div>
  )
}
