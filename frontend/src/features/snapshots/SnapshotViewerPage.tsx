import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api } from '../../api/client'
import { useC } from '../../design/ThemeContext'

type Status = {
  id: number
  state: 'PENDING' | 'RUNNING' | 'READY' | 'FAILED'
  etaSeconds?: number
  downloadPng?: string
  downloadPdf?: string
  error?: string
}

// Stable progress tracker for snapshot agent runs. Polls /status every 1.5s
// while the row is PENDING/RUNNING (backing off to 5s after 20s) so the same
// link the user gets in Slack doubles as "is it ready yet?" — no chat.update,
// no separate Slack status helper.
export function SnapshotViewerPage() {
  const { id } = useParams<{ id: string }>()
  const C = useC()
  const [status, setStatus] = useState<Status | null>(null)
  const [err, setErr] = useState<string | null>(null)
  const started = useRef(Date.now())

  useEffect(() => {
    if (!id) return
    let cancelled = false
    let timer: number | undefined
    const tick = async () => {
      try {
        const r = await api.get<Status>(`/snapshots/${id}/status`)
        if (cancelled) return
        setStatus(r.data)
        if (r.data.state === 'PENDING' || r.data.state === 'RUNNING') {
          const elapsed = Date.now() - started.current
          const delay = elapsed > 20_000 ? 5000 : 1500
          timer = window.setTimeout(tick, delay)
        }
      } catch (e: any) {
        if (cancelled) return
        if (e?.response?.status === 404) setErr('Snapshot not found.')
        else if (e?.response?.status === 403) setErr('You do not have access to this snapshot.')
        else setErr(e?.message || 'Failed to load status')
      }
    }
    tick()
    return () => { cancelled = true; if (timer) clearTimeout(timer) }
  }, [id])

  const box = {
    maxWidth: 720, margin: '60px auto', padding: 28,
    background: C.white, border: `1px solid ${C.border}`, borderRadius: 10,
    boxShadow: '0 4px 18px rgba(91,124,250,0.10)',
    fontFamily: 'Inter, ui-sans-serif, system-ui, sans-serif', color: C.text,
  } as const

  if (err) {
    return (
      <div style={box}>
        <h2 style={{ margin: 0, color: C.red }}>Snapshot unavailable</h2>
        <p style={{ color: C.sub }}>{err}</p>
      </div>
    )
  }

  if (!status) {
    return (
      <div style={box}>
        <p style={{ color: C.sub, margin: 0 }}>Loading snapshot status…</p>
      </div>
    )
  }

  if (status.state === 'FAILED') {
    return (
      <div style={box}>
        <h2 style={{ margin: 0, color: C.red }}>Snapshot failed</h2>
        <p style={{ color: C.sub }}>{status.error ?? 'Unknown error'}</p>
        <p style={{ color: C.sub, fontSize: 13 }}>
          Try running <code>/orbit snapshot</code> again. If it keeps failing, ping #orbit-help.
        </p>
      </div>
    )
  }

  if (status.state === 'READY') {
    const download = async (kind: 'png' | 'pdf') => {
      try {
        // Use axios so the JWT interceptor attaches Authorization — a plain
        // <a download> link would bypass the interceptor and get a 401 from
        // @PreAuthorize("isAuthenticated()") on the controller.
        const resp = await api.get<Blob>(`/snapshots/${id}/${kind}`, { responseType: 'blob' })
        const url = window.URL.createObjectURL(resp.data)
        const a = document.createElement('a')
        a.href = url
        a.download = `orbit-snapshot-${id}.${kind}`
        document.body.appendChild(a)
        a.click()
        a.remove()
        window.URL.revokeObjectURL(url)
      } catch (e: any) {
        const code = e?.response?.status
        const msg = code === 410 ? 'This snapshot has expired.'
          : code === 409 ? 'Snapshot is not ready yet — try again in a moment.'
          : code === 403 ? 'You do not have access to this snapshot.'
          : (e?.message || 'Download failed.')
        setErr(msg)
      }
    }
    return (
      <div style={box}>
        <p style={{ margin: 0, color: C.teal, fontSize: 12, fontWeight: 800, textTransform: 'uppercase' }}>Snapshot ready</p>
        <h2 style={{ margin: '6px 0 18px' }}>Your Orbit snapshot is ready to download</h2>
        <div style={{ display: 'flex', gap: 12 }}>
          {status.downloadPng && (
            <button onClick={() => download('png')} style={btn(C, true)}>Download PNG</button>
          )}
          {status.downloadPdf && (
            <button onClick={() => download('pdf')} style={btn(C, false)}>Download PDF</button>
          )}
        </div>
        <p style={{ color: C.sub, fontSize: 12, marginTop: 18 }}>
          Snapshots expire after 7 days. Re-run <code>/orbit snapshot</code> in Slack to refresh.
        </p>
      </div>
    )
  }

  // PENDING / RUNNING
  const elapsed = Math.floor((Date.now() - started.current) / 1000)
  return (
    <div style={box}>
      <p style={{ margin: 0, color: C.teal, fontSize: 12, fontWeight: 800, textTransform: 'uppercase' }}>{status.state}</p>
      <h2 style={{ margin: '6px 0 18px' }}>Generating your snapshot…</h2>
      <Spinner color={C.teal} />
      <p style={{ color: C.sub, fontSize: 13, marginTop: 14 }}>
        {status.etaSeconds != null
          ? `Estimated time remaining: ${status.etaSeconds}s`
          : `Elapsed: ${elapsed}s`}
      </p>
      <p style={{ color: C.sub, fontSize: 12 }}>
        This page refreshes automatically. Keep it open or come back to the same link later.
      </p>
    </div>
  )
}

function btn(C: any, primary: boolean): React.CSSProperties {
  return {
    minHeight: 42, padding: '0 18px', borderRadius: 8,
    background: primary ? C.teal : C.white,
    color: primary ? '#fff' : C.text,
    border: primary ? 'none' : `1px solid ${C.border}`,
    fontWeight: 800, fontSize: 14, textDecoration: 'none', cursor: 'pointer',
    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
    fontFamily: 'inherit',
  }
}

function Spinner({ color }: { color: string }) {
  return (
    <div style={{
      width: 28, height: 28, border: `3px solid ${color}33`,
      borderTopColor: color, borderRadius: '50%',
      animation: 'orbit-spin 0.9s linear infinite',
    }}>
      <style>{`@keyframes orbit-spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  )
}
