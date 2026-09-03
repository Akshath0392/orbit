import { useState, useRef, useEffect, useCallback } from 'react'
import { useC } from '../../design/ThemeContext'
import { api } from '../../api/client'
import { useStore } from '../../app/store'

type Role = 'user' | 'agent'

interface Message {
  id: string
  role: Role
  text: string
  agentName?: string
  toolCalls?: { name: string; result?: string }[]
  streaming?: boolean
}

let sessionCounter = 0

export function GlobalCopilotPanel() {
  const C = useC()
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState<Message[]>([
    {
      id: 'welcome',
      role: 'agent',
      agentName: 'Orbit Copilot',
      text: 'Hi! I\'m your delivery intelligence assistant. Ask me about project health, CRs, bugs, capacity, or any delivery risk.',
    },
  ])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const sessionId = useRef(`session-${++sessionCounter}-${Date.now()}`)
  const wsRef = useRef<WebSocket | null>(null)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const streamingIdRef = useRef<string | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  // Scroll to bottom on new messages
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  // Focus input when panel opens
  useEffect(() => {
    if (open) setTimeout(() => inputRef.current?.focus(), 120)
  }, [open])

  // Connect WebSocket when panel first opens
  const connectWs = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return

    // Use native WebSocket to /ws endpoint (Spring STOMP over WebSocket)
    // Fallback: poll via REST if WS not available
    try {
      const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      const host  = (import.meta as any).env?.VITE_API_BASE_URL?.replace(/^https?:\/\//, '') || 'localhost:8080'
      const ws = new WebSocket(`${proto}//${host}/ws/websocket`)

      ws.onopen = () => {
        // STOMP CONNECT frame, authenticated with the session JWT.
        const token = useStore.getState().user?.token
        const auth = token ? `Authorization:Bearer ${token}\n` : ''
        ws.send(`CONNECT\naccept-version:1.1,1.0\nheart-beat:10000,10000\n${auth}\n\0`)
      }

      ws.onmessage = (evt) => {
        const body: string = typeof evt.data === 'string' ? evt.data : ''
        if (!body.startsWith('MESSAGE')) return

        // Extract JSON payload after the blank line separating headers from body
        const nlnl = body.indexOf('\n\n')
        if (nlnl === -1) return
        const payload = body.slice(nlnl + 2).replace(/\0$/, '')
        try {
          const data = JSON.parse(payload)
          handleWsEvent(data)
        } catch { /* ignore non-JSON */ }
      }

      ws.onclose = () => { wsRef.current = null }
      wsRef.current = ws

      // After CONNECT, subscribe — we'll do it on CONNECTED response
      const origOnMessage = ws.onmessage
      ws.onmessage = (evt) => {
        if (typeof evt.data === 'string' && evt.data.startsWith('CONNECTED')) {
          // Subscribe to our session topic
          ws.send(`SUBSCRIBE\nid:sub-0\ndestination:/topic/copilot/${sessionId.current}\n\n\0`)
        }
        origOnMessage?.call(ws, evt)
      }
    } catch {
      // WS unavailable — streaming won't work but REST calls still go through
    }
  }, [])

  const handleWsEvent = useCallback((data: any) => {
    const { type, content, name, summary, result } = data

    if (type === 'token') {
      setMessages(prev => prev.map(m =>
        m.id === streamingIdRef.current
          ? { ...m, text: m.text + (content ?? ''), streaming: true }
          : m
      ))
    } else if (type === 'tool_call') {
      setMessages(prev => prev.map(m =>
        m.id === streamingIdRef.current
          ? { ...m, toolCalls: [...(m.toolCalls ?? []), { name: name ?? '' }] }
          : m
      ))
    } else if (type === 'tool_result') {
      setMessages(prev => prev.map(m =>
        m.id === streamingIdRef.current
          ? {
              ...m,
              toolCalls: (m.toolCalls ?? []).map((tc, i, arr) =>
                i === arr.length - 1 ? { ...tc, result: summary ?? result ?? '' } : tc
              ),
            }
          : m
      ))
    } else if (type === 'done') {
      setMessages(prev => prev.map(m =>
        m.id === streamingIdRef.current ? { ...m, streaming: false } : m
      ))
      streamingIdRef.current = null
      setLoading(false)
    }
  }, [])

  const send = async () => {
    const text = input.trim()
    if (!text || loading) return

    connectWs()
    setInput('')
    setLoading(true)

    const userMsg: Message = { id: `u-${Date.now()}`, role: 'user', text }
    const agentMsgId = `a-${Date.now()}`
    const agentMsg: Message = { id: agentMsgId, role: 'agent', agentName: 'Orbit Copilot', text: '', streaming: true }

    streamingIdRef.current = agentMsgId
    setMessages(prev => [...prev, userMsg, agentMsg])

    try {
      await api.post('/copilot/message', {
        sessionId: sessionId.current,
        text,
        portfolioId: useStore.getState().activePortfolioId,
      })
      // Response streams via WS; if WS is unavailable, mark done after 8s timeout
      setTimeout(() => {
        if (streamingIdRef.current === agentMsgId) {
          setMessages(prev => prev.map(m =>
            m.id === agentMsgId && m.streaming
              ? { ...m, streaming: false, text: m.text || 'Message sent — response streaming via WebSocket.' }
              : m
          ))
          streamingIdRef.current = null
          setLoading(false)
        }
      }, 8000)
    } catch {
      setMessages(prev => prev.map(m =>
        m.id === agentMsgId ? { ...m, text: 'Could not reach Orbit — check your connection.', streaming: false } : m
      ))
      streamingIdRef.current = null
      setLoading(false)
    }
  }

  return (
    <>
      {/* Floating toggle button */}
      <button
        onClick={() => setOpen(o => !o)}
        title="Orbit Copilot"
        style={{
          position: 'fixed', bottom: 24, right: 24, zIndex: 1000,
          width: 52, height: 52, borderRadius: '50%', border: 'none',
          background: open ? C.navy : C.indigo, color: '#fff', fontSize: 22,
          cursor: 'pointer', boxShadow: '0 8px 24px rgba(91,124,250,0.35)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          transition: 'background-color 160ms ease, transform 160ms ease, box-shadow 160ms ease',
        }}
        onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.transform = 'scale(1.08)' }}
        onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.transform = 'scale(1)' }}
      >
        {open ? '✕' : '✦'}
      </button>

      {/* Slide-in panel */}
      <div style={{
        position: 'fixed', top: 0, right: 0, bottom: 0, zIndex: 999,
        width: open ? 400 : 0, maxWidth: '100vw',
        background: C.white, borderLeft: `1px solid ${C.border}`,
        display: 'flex', flexDirection: 'column',
        overflow: 'hidden',
        transition: 'width 220ms cubic-bezier(0.4, 0, 0.2, 1)',
        boxShadow: open ? '-8px 0 32px rgba(24,33,47,0.12)' : 'none',
      }}>
        {open && (
          <>
            {/* Header */}
            <div style={{
              display: 'flex', alignItems: 'center', justifyContent: 'space-between',
              padding: '16px 18px', borderBottom: `1px solid ${C.border}`,
              background: C.white, flexShrink: 0,
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                {/* Mini orbit mark */}
                <svg width="28" height="28" viewBox="0 0 28 28" style={{ overflow: 'visible', flexShrink: 0 }}>
                  <circle cx="14" cy="14" r="12" fill="none" stroke="#087f7a" strokeWidth="2" />
                  <ellipse cx="14" cy="14" rx="21" ry="6" fill="none" stroke="#e0a323" strokeWidth="1.5" transform="rotate(-28 14 14)" />
                  <circle cx="14" cy="14" r="3.5" fill="#b83280" />
                </svg>
                <div>
                  <div style={{ fontSize: 14, fontWeight: 800, color: C.text }}>Orbit Copilot</div>
                  <div style={{ fontSize: 11, color: C.sub, fontWeight: 600 }}>Delivery intelligence assistant</div>
                </div>
              </div>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                {loading && (
                  <span style={{ fontSize: 11, color: C.indigo, fontWeight: 700 }}>Thinking…</span>
                )}
                <button onClick={() => setMessages([{ id: 'welcome', role: 'agent', agentName: 'Orbit Copilot', text: 'Hi! Ask me about any delivery risk, CR status, capacity, or escalation.' }])}
                  title="Clear chat"
                  style={{ background: 'none', border: 'none', cursor: 'pointer', color: C.sub, fontSize: 13, padding: 4, borderRadius: 6 }}>
                  ⊘
                </button>
              </div>
            </div>

            {/* Messages */}
            <div style={{ flex: 1, overflowY: 'auto', padding: '14px 16px', display: 'flex', flexDirection: 'column', gap: 12 }}>
              {messages.map(m => (
                <div key={m.id} style={{ display: 'flex', flexDirection: 'column', alignItems: m.role === 'user' ? 'flex-end' : 'flex-start', gap: 4 }}>
                  {m.role === 'agent' && m.agentName && (
                    <span style={{ fontSize: 10, fontWeight: 800, color: C.indigo, textTransform: 'uppercase', letterSpacing: 0.5, paddingLeft: 2 }}>{m.agentName}</span>
                  )}
                  {/* Tool call traces */}
                  {(m.toolCalls ?? []).map((tc, i) => (
                    <div key={i} style={{ fontSize: 11, fontFamily: 'monospace', color: C.amber, background: C.amberPale, border: `1px solid ${C.amber}`, borderRadius: 6, padding: '4px 8px', maxWidth: '90%', wordBreak: 'break-all' }}>
                      ⟳ {tc.name}
                      {tc.result && <span style={{ color: C.sub, display: 'block', marginTop: 2 }}>↳ {tc.result}</span>}
                    </div>
                  ))}
                  {/* Message bubble */}
                  {(m.text || m.streaming) && (
                    <div style={{
                      maxWidth: '86%', padding: '10px 13px', borderRadius: m.role === 'user' ? '12px 12px 2px 12px' : '12px 12px 12px 2px',
                      background: m.role === 'user' ? C.indigoPale : C.canvas,
                      border: `1px solid ${m.role === 'user' ? C.borderMed : C.border}`,
                      fontSize: 13, color: C.text, lineHeight: 1.6, whiteSpace: 'pre-wrap', wordBreak: 'break-word',
                    }}>
                      {m.text}
                      {m.streaming && <span style={{ display: 'inline-block', width: 8, height: 8, borderRadius: '50%', background: C.indigo, marginLeft: 4, animation: 'pulse 1s infinite' }} />}
                    </div>
                  )}
                </div>
              ))}
              <div ref={messagesEndRef} />
            </div>

            {/* Quick prompts */}
            {messages.length <= 1 && (
              <div style={{ padding: '0 16px 10px', display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                {['What needs attention today?', 'Which projects are at risk?', 'Show capacity for this sprint'].map(q => (
                  <button key={q} onClick={() => { setInput(q); setTimeout(() => inputRef.current?.focus(), 50) }}
                    style={{ fontSize: 11, padding: '5px 10px', borderRadius: 20, border: `1px solid ${C.border}`, background: C.canvas, color: C.sub, cursor: 'pointer', fontWeight: 600, transition: 'all 120ms ease' }}
                    onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.background = C.indigoPale; (e.currentTarget as HTMLButtonElement).style.color = C.text }}
                    onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.background = C.canvas; (e.currentTarget as HTMLButtonElement).style.color = C.sub }}
                  >
                    {q} ↗
                  </button>
                ))}
              </div>
            )}

            {/* Input */}
            <div style={{ padding: '12px 14px', borderTop: `1px solid ${C.border}`, background: C.white, display: 'flex', gap: 8, alignItems: 'flex-end', flexShrink: 0 }}>
              <input
                ref={inputRef}
                value={input}
                onChange={e => setInput(e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send() } }}
                placeholder="Ask about delivery, CRs, capacity…"
                disabled={loading}
                style={{
                  flex: 1, minHeight: 38, padding: '8px 12px', fontSize: 13,
                  border: `1px solid ${C.border}`, borderRadius: 8, color: C.text,
                  background: C.white, outline: 'none', resize: 'none',
                  transition: 'border-color 160ms ease',
                  opacity: loading ? 0.7 : 1,
                }}
                onFocus={e => (e.target.style.borderColor = C.indigo)}
                onBlur={e => (e.target.style.borderColor = C.border)}
              />
              <button onClick={send} disabled={loading || !input.trim()} style={{
                width: 38, height: 38, borderRadius: 8, border: 'none',
                background: loading || !input.trim() ? C.border : C.indigo, color: '#fff',
                cursor: loading || !input.trim() ? 'default' : 'pointer', fontSize: 16,
                display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
                transition: 'background-color 160ms ease',
              }}>
                ↑
              </button>
            </div>
          </>
        )}
      </div>

      <style>{`
        @keyframes pulse {
          0%, 100% { opacity: 1; transform: scale(1); }
          50% { opacity: 0.4; transform: scale(0.8); }
        }
      `}</style>
    </>
  )
}
