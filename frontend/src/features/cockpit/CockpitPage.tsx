import { ErrorState } from '../../design/components/PageState'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useC } from '../../design/ThemeContext'
import { Badge } from '../../design/components/Badge'
import { StatCard } from '../../design/components/StatCard'
import { SectionHead } from '../../design/components/SectionHead'
import { api } from '../../api/client'
import { fmtTime } from '../../lib/datetime'

const tagLevel: Record<string, string> = { 'SLA breached': 'critical', 'Hold aging': 'risk', 'Budget risk': 'risk', 'UAT blocker': 'info' }

type MsgBtn = { l: string; p?: boolean }
type CopilotMsgType = { from: string; agent?: string; text?: string; tool?: string; tools?: string[]; btns?: MsgBtn[] }

// Copilot panel is WS-driven — empty initial state until streaming is live.
const INITIAL_MSGS: CopilotMsgType[] = []

function CopilotMsg({ m, onAction }: { m: CopilotMsgType; onAction?: (label: string) => void }) {
  const C = useC()
  if (m.from === 'user') {
    return (
      <div style={{
        alignSelf: 'flex-end', maxWidth: '86%',
        background: C.indigoPale, borderRadius: '10px 10px 2px 10px',
        padding: '8px 12px', fontSize: 12, color: C.purpleDeep, lineHeight: 1.5
      }}>
        {m.text}
      </div>
    )
  }
  return (
    <div style={{
      background: C.white, border: `1px solid ${C.border}`,
      borderRadius: '10px 10px 10px 2px', padding: '10px 12px', fontSize: 12, lineHeight: 1.5
    }}>
      <div style={{ fontSize: 10, fontWeight: 600, color: C.green, marginBottom: 4, letterSpacing: 0.3 }}>{m.agent}</div>
      {(m.tools || m.tool) && (
        <div style={{ marginBottom: 6 }}>
          {(m.tools || (m.tool ? [m.tool] : [])).map((t, i) => (
            <div key={i} style={{
              fontFamily: 'monospace', fontSize: 10, color: C.sub,
              background: C.canvas, borderLeft: `2px solid ${C.amber}`,
              padding: '2px 7px', margin: '2px 0', borderRadius: '0 3px 3px 0'
            }}>
              {t}
            </div>
          ))}
        </div>
      )}
      <div style={{ color: C.text, whiteSpace: 'pre-line' }}>{m.text}</div>
      {m.btns && (
        <div style={{ display: 'flex', gap: 5, marginTop: 8, flexWrap: 'wrap' }}>
          {m.btns.map((b, i) => (
            <button key={i} onClick={() => onAction?.(b.l)} style={{
              fontSize: 11, padding: '4px 9px', borderRadius: 5, cursor: 'pointer',
              border: `1px solid ${b.p ? C.indigo : C.border}`,
              background: b.p ? C.indigo : 'none',
              color: b.p ? '#fff' : C.sub, fontWeight: b.p ? 600 : 400
            }}>
              {b.l}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

export function CockpitPage() {
  const C = useC()
  const sevBorder: Record<string, string> = { critical: C.red, warn: C.amber, info: C.indigo }
  const [input, setInput] = useState('')
  const [messages, setMessages] = useState<CopilotMsgType[]>(INITIAL_MSGS)
  const [editingStandup, setEditingStandup] = useState(false)
  const [draftLines, setDraftLines] = useState<string[]>([])
  const [toast, setToast] = useState('')

  const showToast = (msg: string, ms = 3000) => { setToast(msg); setTimeout(() => setToast(''), ms) }

  const { data: cockpitData, isLoading, error } = useQuery({
    queryKey: ['cockpit'],
    queryFn: () => api.get('/dashboard/cockpit').then(r => r.data)
  })
  const actions = cockpitData?.actions ?? []
  const standup = cockpitData?.standupDraft
  const stats = cockpitData?.stats ?? {}

  const sendMessage = () => {
    if (!input.trim()) return
    const text = input
    setMessages(ms => [...ms, { from: 'user', text }])
    setInput('')
    setTimeout(() => {
      setMessages(ms => [...ms, { from: 'agent', agent: 'CopilotAgent', text: 'Processing your request… (live copilot streaming coming soon)' }])
    }, 600)
  }

  if (isLoading) return <div style={{ padding: 40, color: C.sub }}>Loading…</div>
  if (error) return <ErrorState error={error} />

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 296px', height: '100%', minHeight: 760 }}>
      {toast && (
        <div style={{
          position: 'fixed', bottom: 24, right: 24, zIndex: 9999,
          padding: '10px 18px', borderRadius: 8, background: C.text,
          color: '#fff', fontSize: 13, fontWeight: 500, boxShadow: '0 8px 24px rgba(0,0,0,0.18)'
        }}>
          {toast}
        </div>
      )}
      <div style={{ padding: '22px 24px', overflowY: 'auto', borderRight: `1px solid ${C.border}` }}>
        <div style={{ marginBottom: 16 }}>
          <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>
            {cockpitData?.greeting ?? 'Welcome'} 👋
          </div>
          <div style={{ fontSize: 12, color: C.sub, marginTop: 3 }}>
            {cockpitData?.date ?? ''} · {actions.length} actions need attention
          </div>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 10, marginBottom: 18 }}>
          <StatCard label="Open CRs" value={stats.openCrs ?? '—'} sub={stats.openCrsSub ?? ''} />
          <StatCard label="SLA breached" value={stats.slaBreached ?? '—'} color={C.red} sub={stats.slaBreachedSub ?? ''} />
          <StatCard label="Avg burn" value={stats.avgBurn ?? '—'} color={C.amber} sub={stats.avgBurnSub ?? ''} />
          <StatCard label="Avg utilization" value={stats.avgUtil ?? '—'} color={C.green} sub={stats.avgUtilSub ?? ''} />
        </div>
        <SectionHead title="Actions for today — by urgency" action="View all" />
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 18 }}>
          {actions.length === 0 && (
            <div style={{ padding: '20px 0', color: C.muted, fontSize: 13 }}>No actions for today.</div>
          )}
          {actions.map((a: any) => (
            <div key={a.id} style={{
              background: C.white, border: `1px solid ${C.border}`,
              borderLeft: `3px solid ${sevBorder[a.severity] || C.indigo}`,
              borderRadius: '0 10px 10px 0', padding: '12px 14px'
            }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 8, marginBottom: 5 }}>
                <div style={{ fontSize: 13, fontWeight: 600, color: C.text, lineHeight: 1.4, flex: 1 }}>{a.title}</div>
                <Badge level={tagLevel[a.tag] || 'info'} label={a.tag} />
              </div>
              <div style={{ fontSize: 12, color: C.sub, lineHeight: 1.5, marginBottom: 8 }}>{a.body}</div>
            </div>
          ))}
        </div>

        {standup ? (
          <div style={{ background: C.canvas, border: `1px solid ${C.border}`, borderRadius: 10, padding: '12px 14px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8 }}>
              <span style={{ width: 7, height: 7, borderRadius: '50%', background: C.green }} />
              <span style={{ fontSize: 11, fontWeight: 600, color: C.green, letterSpacing: 0.3 }}>
                {standup.agentLabel ?? 'STANDUP AGENT — DRAFT READY'}
              </span>
              <span style={{ marginLeft: 'auto', fontSize: 10, color: C.muted }}>
                {standup.autoPostAt ? fmtTime(standup.autoPostAt, '') : ''}
                {standup.projectCount != null ? ` · ${standup.projectCount} projects` : ''}
              </span>
            </div>
            {editingStandup ? (
              <>
                {draftLines.map((l, i) => (
                  <textarea
                    key={i}
                    value={l}
                    rows={2}
                    onChange={e => setDraftLines(ls => ls.map((x, j) => j === i ? e.target.value : x))}
                    style={{ fontSize: 12, padding: '6px 8px', borderRadius: 6, border: `1px solid ${C.border}`, width: '100%', resize: 'vertical', outline: 'none', color: C.text, boxSizing: 'border-box' as const, marginBottom: 4 }}
                  />
                ))}
                <div style={{ display: 'flex', gap: 7, marginTop: 6 }}>
                  <button onClick={() => setEditingStandup(false)} style={{ fontSize: 11, padding: '4px 12px', borderRadius: 6, border: 'none', background: C.indigo, color: '#fff', cursor: 'pointer', fontWeight: 500 }}>Save draft</button>
                  <button onClick={() => { setEditingStandup(false); setDraftLines([]) }} style={{ fontSize: 11, padding: '4px 10px', borderRadius: 6, border: `1px solid ${C.border}`, background: 'transparent', color: C.sub, cursor: 'pointer' }}>Cancel</button>
                </div>
              </>
            ) : (
              <>
                {(standup.lines ?? []).map((l: string, i: number) => (
                  <div key={i} style={{ fontSize: 12, color: C.text, lineHeight: 1.6, padding: '4px 0', borderTop: i > 0 ? `1px solid ${C.border}` : 'none' }}>
                    {l}
                  </div>
                ))}
              </>
            )}
            <div style={{ display: 'flex', gap: 7, marginTop: 10 }}>
              <button
                onClick={() => showToast('Standup will be posted via EscalationAgent after approval — configure in Alert Center', 4000)}
                style={{ fontSize: 11, padding: '5px 12px', borderRadius: 6, border: 'none', background: C.indigo, color: '#fff', cursor: 'pointer', fontWeight: 500 }}
              >
                Post to Slack
              </button>
              <button
                onClick={() => { setEditingStandup(true); setDraftLines(standup?.lines ?? []) }}
                style={{ fontSize: 11, padding: '5px 12px', borderRadius: 6, cursor: 'pointer', border: `1px solid ${C.border}`, background: 'transparent', color: C.sub }}
              >
                Edit draft
              </button>
            </div>
          </div>
        ) : (
          <div style={{ background: C.canvas, border: `1px solid ${C.border}`, borderRadius: 10, padding: '12px 14px' }}>
            <div style={{ fontSize: 12, color: C.muted }}>No standup draft available.</div>
          </div>
        )}
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', background: C.canvas }}>
        <div style={{ padding: '12px 14px', borderBottom: `1px solid ${C.border}`, display: 'flex', alignItems: 'center', gap: 7, background: C.white }}>
          <span style={{ width: 8, height: 8, borderRadius: '50%', background: C.green }} />
          <span style={{ fontSize: 13, fontWeight: 600, color: C.text }}>Copilot</span>
          <span style={{ marginLeft: 'auto', fontSize: 10, color: C.muted }}>DeliveryAgent active</span>
        </div>
        <div style={{ flex: 1, overflowY: 'auto', padding: '12px', display: 'flex', flexDirection: 'column', gap: 8 }}>
          {messages.map((m, i) => (
            <CopilotMsg key={i} m={m} onAction={(label) => showToast(`"${label}" — action received`)} />
          ))}
        </div>
        <div style={{ padding: '10px', borderTop: `1px solid ${C.border}`, display: 'flex', gap: 7 }}>
          <input
            placeholder="Ask anything about your projects…"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage() } }}
            style={{
              flex: 1, fontSize: 12, padding: '7px 10px', borderRadius: 7,
              border: `1px solid ${C.border}`, background: C.white, color: C.text, outline: 'none'
            }}
          />
          <button
            onClick={sendMessage}
            style={{ padding: '7px 14px', borderRadius: 7, border: 'none', background: C.indigo, color: '#fff', fontSize: 12, cursor: 'pointer', fontWeight: 500 }}
          >
            Ask
          </button>
        </div>
      </div>
    </div>
  )
}
