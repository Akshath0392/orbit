import { ErrorState } from '../../design/components/PageState'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useC } from '../../design/ThemeContext'
import { Badge } from '../../design/components/Badge'
import { BurnBar } from '../../design/components/BurnBar'
import { api } from '../../api/client'

export function ClientBacklogPage() {
  const C = useC()
  const navigate = useNavigate()
  const [selClientId, setSelClientId] = useState<number | null>(null)
  const [showDraft, setShowDraft] = useState(false)
  const [draftText, setDraftText] = useState('')
  const [toast, setToast] = useState('')

  const showToast = (msg: string, ms = 3000) => { setToast(msg); setTimeout(() => setToast(''), ms) }

  const { data: clients = [], isLoading, error } = useQuery({
    queryKey: ['clients'],
    queryFn: () => api.get('/clients').then(r => r.data)
  })

  const { data: deps = [] } = useQuery({
    queryKey: ['deps', selClientId],
    queryFn: () => selClientId
      ? api.get(`/clients/${selClientId}/dependencies`).then(r => r.data)
      : [],
    enabled: !!selClientId
  })

  const clientList = clients as any[]
  const selIdx = clientList.findIndex((c: any) => c.id === selClientId)
  const c = selIdx >= 0 ? clientList[selIdx] : clientList[0]

  if (isLoading) return <div style={{ padding: 40, color: C.sub }}>Loading…</div>
  if (error) return <ErrorState error={error} />

  if (clientList.length === 0) {
    return (
      <div style={{ padding: '22px 24px' }}>
        <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>Client backlog</div>
        <div style={{ padding: '40px 0', textAlign: 'center', color: C.muted, fontSize: 13 }}>No data yet</div>
      </div>
    )
  }

  const hCol = c.health >= c.healthGreenThreshold ? C.green : c.health >= c.healthAmberThreshold ? C.amber : C.red
  const hLvl = c.health >= c.healthGreenThreshold ? 'healthy' : c.health >= c.healthAmberThreshold ? 'watch' : 'critical'

  return (
    <div style={{ padding: '22px 24px' }}>
      {toast && (
        <div style={{
          position: 'fixed', bottom: 24, right: 24, zIndex: 9999,
          padding: '10px 18px', borderRadius: 8, background: C.text,
          color: '#fff', fontSize: 13, fontWeight: 500, boxShadow: '0 8px 24px rgba(0,0,0,0.18)'
        }}>
          {toast}
        </div>
      )}
      <div style={{ marginBottom: 18 }}>
        <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>Client backlog</div>
        <div style={{ fontSize: 12, color: C.sub, marginTop: 3 }}>Health cards · governance · dependency tracking</div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: `repeat(${Math.min(clientList.length, 5)},1fr)`, gap: 10, marginBottom: 24 }}>
        {clientList.map((cl: any) => {
          const clCol = cl.health >= cl.healthGreenThreshold ? C.green : cl.health >= cl.healthAmberThreshold ? C.amber : C.red
          return (
            <div
              key={cl.id ?? cl.code}
              onClick={() => setSelClientId(cl.id)}
              style={{
                background: C.white,
                border: `1px solid ${selClientId === cl.id ? C.indigo : C.border}`,
                borderTop: `3px solid ${clCol}`,
                borderRadius: 12, padding: '14px 16px', cursor: 'pointer', transition: 'all .15s'
              }}
            >
              <div style={{ fontSize: 13, fontWeight: 600, color: C.text }}>{cl.name}</div>
              <div style={{ fontSize: 11, color: C.sub, marginTop: 1, marginBottom: 10 }}>{cl.code}</div>
              <div style={{ fontSize: 22, fontWeight: 700, color: clCol, marginBottom: 4 }}>{cl.health}</div>
              <div style={{ fontSize: 10, color: C.muted, marginBottom: 8 }}>Health score / 100</div>
              <BurnBar pct={cl.burn} />
              <div style={{ fontSize: 10, color: C.sub, marginTop: 3 }}>{cl.burn}% budget burned</div>
            </div>
          )
        })}
      </div>

      {c && (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }}>
              <div style={{ fontSize: 11, fontWeight: 600, color: C.sub, letterSpacing: 0.5, textTransform: 'uppercase' }}>{c.name} — backlog summary</div>
              {c.id != null && (
                <span onClick={() => navigate(`/clients/${c.id}`)}
                  style={{ fontSize: 12, fontWeight: 700, color: C.indigo, cursor: 'pointer' }}>Client master page →</span>
              )}
            </div>
            <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: '14px 16px', marginBottom: 12 }}>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 12, marginBottom: 14 }}>
                <div style={{ textAlign: 'center' }}>
                  <div style={{ fontSize: 22, fontWeight: 700, color: C.text }}>{c.crs}</div>
                  <div style={{ fontSize: 11, color: C.sub }}>Open CRs</div>
                </div>
                <div style={{ textAlign: 'center' }}>
                  <div style={{ fontSize: 22, fontWeight: 700, color: c.bugs > 0 ? C.red : C.green }}>{c.bugs}</div>
                  <div style={{ fontSize: 11, color: C.sub }}>P0/P1 bugs</div>
                </div>
                <div style={{ textAlign: 'center' }}>
                  <div style={{ fontSize: 22, fontWeight: 700, color: c.tbc > 0 ? C.amber : C.green }}>{c.tbc}</div>
                  <div style={{ fontSize: 11, color: C.sub }}>TBC dates</div>
                </div>
              </div>
              <div style={{ marginBottom: 12 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                  <span style={{ fontSize: 12, color: C.sub }}>Budget burn</span>
                  <span style={{ fontSize: 12, fontWeight: 600, color: c.burn > 80 ? C.red : c.burn > 60 ? C.amber : C.green }}>{c.burn}%</span>
                </div>
                <BurnBar pct={c.burn} />
              </div>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ fontSize: 12, color: C.sub }}>Account health</div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <div style={{ fontSize: 20, fontWeight: 700, color: hCol }}>{c.health}</div>
                  <Badge level={hLvl} label={hLvl === 'healthy' ? 'Good' : hLvl === 'watch' ? 'Fair' : 'At risk'} />
                </div>
              </div>
            </div>
            <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: '14px 16px' }}>
              <div style={{ fontSize: 12, fontWeight: 600, color: C.text, marginBottom: 10 }}>Dependencies & blockers</div>
              {(deps as any[]).length === 0 && (
                <div style={{ fontSize: 12, color: C.muted }}>No dependencies yet.</div>
              )}
              {(deps as any[]).map((dep: any, i: number) => (
                <div key={dep.id ?? i} style={{ padding: '8px 0', borderTop: i > 0 ? `1px solid ${C.border}` : 'none' }}>
                  <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 8, marginBottom: 2 }}>
                    <div style={{ fontSize: 12, fontWeight: 500, color: C.text, flex: 1 }}>{dep.title}</div>
                    <Badge level={dep.status === 'critical' ? 'critical' : dep.status === 'risk' ? 'risk' : 'info'} label={dep.age ?? dep.depType} />
                  </div>
                  <div style={{ fontSize: 11, color: C.sub }}>{dep.description}</div>
                </div>
              ))}
            </div>
          </div>

          <div>
            <div style={{ fontSize: 11, fontWeight: 600, color: C.sub, letterSpacing: 0.5, textTransform: 'uppercase', marginBottom: 10 }}>Contact & governance</div>
            <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: '14px 16px', marginBottom: 12 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 14 }}>
                <div style={{ width: 44, height: 44, borderRadius: '50%', background: C.indigo, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 15, fontWeight: 700, color: '#fff' }}>
                  {c.contact ? c.contact.split(' ').map((w: string) => w[0]).join('') : '?'}
                </div>
                <div>
                  <div style={{ fontSize: 14, fontWeight: 600, color: C.text }}>{c.contact}</div>
                  <div style={{ fontSize: 12, color: C.sub }}>Account Lead · {c.name}</div>
                </div>
              </div>
              <div style={{ display: 'flex', gap: 7 }}>
                <button
                  onClick={() => setShowDraft(true)}
                  style={{ fontSize: 12, padding: '6px 14px', borderRadius: 7, border: 'none', background: C.indigo, color: '#fff', cursor: 'pointer', fontWeight: 500 }}
                >
                  Draft update
                </button>
                <button
                  onClick={() => showToast('Calendar integration not yet configured — schedule via your calendar app', 3500)}
                  style={{ fontSize: 12, padding: '6px 14px', borderRadius: 7, border: `1px solid ${C.border}`, background: 'transparent', color: C.sub, cursor: 'pointer' }}
                >
                  Schedule call
                </button>
              </div>
            </div>
            <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: '14px 16px' }}>
              <div style={{ fontSize: 12, fontWeight: 600, color: C.text, marginBottom: 10 }}>Upcoming milestones</div>
              {(c.milestones ?? []).length === 0 && (
                <div style={{ fontSize: 12, color: C.muted }}>No milestone data.</div>
              )}
              {(c.milestones ?? []).map((m: any, i: number) => (
                <div key={i} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 0', borderTop: i > 0 ? `1px solid ${C.border}` : 'none' }}>
                  <div style={{ fontSize: 12, color: C.text }}>{m.ms}</div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span style={{ fontSize: 11, color: C.sub }}>{m.date}</span>
                    <Badge level={m.status === 'risk' ? 'risk' : m.status === 'watch' ? 'watch' : 'healthy'} label={m.status === 'risk' ? 'At risk' : m.status === 'watch' ? 'Watch' : 'On track'} />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {showDraft && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.3)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: C.white, borderRadius: 12, padding: 24, width: 480, boxShadow: '0 24px 64px rgba(0,0,0,0.2)' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
              <div style={{ fontSize: 16, fontWeight: 600, color: C.text }}>Draft update for {c?.contact}</div>
              <button onClick={() => setShowDraft(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 18, color: C.muted }}>✕</button>
            </div>
            <textarea
              placeholder={`Hi ${c?.contact},\n\nHere's a quick update on ${c?.name}…`}
              value={draftText}
              onChange={e => setDraftText(e.target.value)}
              rows={8}
              style={{ width: '100%', fontSize: 13, padding: '10px 12px', borderRadius: 8, border: `1px solid ${C.border}`, outline: 'none', resize: 'vertical', color: C.text, boxSizing: 'border-box' as const }}
            />
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 14 }}>
              <button onClick={() => setShowDraft(false)} style={{ fontSize: 12, padding: '7px 16px', borderRadius: 7, border: `1px solid ${C.border}`, background: 'transparent', color: C.sub, cursor: 'pointer' }}>Cancel</button>
              <button
                disabled={!draftText.trim()}
                onClick={() => { navigator.clipboard?.writeText(draftText); setShowDraft(false); showToast('Update copied to clipboard') }}
                style={{ fontSize: 12, padding: '7px 16px', borderRadius: 7, border: 'none', background: C.indigo, color: '#fff', cursor: 'pointer', fontWeight: 500 }}
              >
                Copy to clipboard
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
