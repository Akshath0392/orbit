import React, { useCallback, useEffect, useRef, useState } from 'react'
import { useParams, useNavigate, Link, useSearchParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useC } from '../../../design/ThemeContext'
import type { Colors } from '../../../design/theme'
import { Badge } from '../../../design/components/Badge'
import { Tabs } from '../../../design/components/Tabs'
import { Pagination } from '../../../design/components/Pagination'
import { api } from '../../../api/client'
import { useStore } from '../../../app/store'
import { fmtDateTimeFull, fmtTime } from '../../../lib/datetime'

const TAB_NAMES = ['Overview', 'Runs', 'Live', 'Test'] as const
type TabName = typeof TAB_NAMES[number]

const TYPE_BADGE: Record<string, any> = {
  INTELLIGENCE: 'info', REMINDER: 'watch', ESCALATION: 'critical',
  COMMUNICATION: 'teal', REPORTING: 'purple',
}

const STEP_STATUS_COLOR = (C: Colors): Record<string, string> => ({
  STARTED: C.blue, COMPLETED: C.green,
  AWAITING_HITL: C.amber, FAILED: C.red,
})

interface LiveLogLine {
  ts: string
  status: string
  toolName: string
  stepId?: number | null
  message?: string | null
}

export function AgentDetailPage() {
  const C = useC()
  const { id } = useParams<{ id: string }>()
  const agentId = Number(id)
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [search, setSearch] = useSearchParams()

  const tab: TabName = (TAB_NAMES.includes(search.get('tab') as TabName)
    ? (search.get('tab') as TabName)
    : 'Overview')
  const setTab = (t: TabName) => setSearch({ tab: t })

  const { data: agent, isLoading } = useQuery({
    queryKey: ['agent-definition', agentId],
    queryFn: () => api.get('/admin/agents').then(r => (r.data as any[]).find((a: any) => a.id === agentId)),
  })

  if (isLoading) return <div style={{ padding: 40, color: C.sub }}>Loading…</div>
  if (!agent) return (
    <div style={{ padding: 40 }}>
      <div style={{ color: C.sub, marginBottom: 12 }}>Agent #{id} not found.</div>
      <Link to="/agent-builder" style={{ color: C.indigo }}>← Back to Agents</Link>
    </div>
  )

  return (
    <div style={{ padding: '22px 24px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 6 }}>
        <Link to="/agent-builder" style={{ fontSize: 12, color: C.sub, textDecoration: 'none' }}>← Agents</Link>
        <span style={{ color: C.muted }}>/</span>
        <span style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>{agent.name}</span>
        <Badge level={TYPE_BADGE[agent.agentType] ?? 'neutral'} label={agent.agentType} />
        {agent.systemAgent && (
          <span style={{ fontSize: 10, padding: '2px 7px', borderRadius: 4, background: C.canvas, color: C.muted, fontWeight: 600 }}>SYSTEM</span>
        )}
        <span style={{ fontSize: 11, color: agent.enabled ? C.green : C.muted, fontWeight: 600 }}>
          {agent.enabled ? '● Enabled' : '○ Disabled'}
        </span>
      </div>
      <div style={{ fontSize: 12, color: C.sub, marginBottom: 16 }}>
        {agent.description || 'No description.'}
      </div>

      <Tabs items={[...TAB_NAMES]} active={tab} onChange={(t) => setTab(t as TabName)} />

      {tab === 'Overview' && <OverviewTab agent={agent} C={C} />}
      {tab === 'Runs' && <RunsTab agentId={agentId} C={C} />}
      {tab === 'Live' && <LiveTab agentId={agentId} C={C} />}
      {tab === 'Test' && (
        <TestTab
          agentId={agentId}
          C={C}
          onStarted={(runId) => {
            qc.invalidateQueries({ queryKey: ['agent-runs', agentId] })
            setSearch({ tab: 'Live', runId: String(runId) })
            navigate(`/admin/agents/${agentId}?tab=Live&runId=${runId}`)
          }}
        />
      )}
    </div>
  )
}

// ── Overview ─────────────────────────────────────────────────────────────────

function OverviewTab({ agent, C }: { agent: any; C: any }) {
  const row = (label: string, value: React.ReactNode) => (
    <tr>
      <td style={{ padding: '8px 14px', color: C.sub, fontSize: 11, width: 180, verticalAlign: 'top' }}>{label}</td>
      <td style={{ padding: '8px 14px', color: C.text, fontSize: 12 }}>{value ?? '—'}</td>
    </tr>
  )
  return (
    <div style={{ marginTop: 20, background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden', maxWidth: 880 }}>
      <table style={{ width: '100%', borderCollapse: 'collapse' }}>
        <tbody>
          {row('Type', agent.agentType)}
          {row('Trigger', `${agent.triggerType}${agent.triggerConfig ? ' — ' + agent.triggerConfig : ''}`)}
          {row('Tools', (agent.tools ?? []).join(', '))}
          {row('Output channel', agent.outputChannel)}
          {row('HITL required', agent.requiresHitl ? 'Yes' : 'No')}
          {row('Slack exposed', agent.slackExposed ? 'Yes' : 'No')}
          {row('Project scope', agent.projectId ?? '(global)')}
          {row('Prompt template', agent.promptTemplate
            ? <pre style={{ whiteSpace: 'pre-wrap', fontFamily: 'ui-monospace, monospace', fontSize: 11, color: C.sub, margin: 0 }}>{agent.promptTemplate}</pre>
            : null)}
        </tbody>
      </table>
    </div>
  )
}

// ── Runs ─────────────────────────────────────────────────────────────────────

function RunsTab({ agentId, C }: { agentId: number; C: any }) {
  const [page, setPage] = useState(0)
  const [expanded, setExpanded] = useState<number | null>(null)

  const { data: runsPage, isLoading } = useQuery({
    queryKey: ['agent-runs', agentId, page],
    queryFn: () => api.get(`/admin/agents/${agentId}/runs`, { params: { page, size: 20 } }).then(r => r.data),
  })

  const runs = (runsPage?.content ?? []) as any[]
  const totalPages = runsPage?.totalPages ?? 0

  return (
    <div style={{ marginTop: 20, background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden' }}>
      <table style={{ width: '100%', fontSize: 12, borderCollapse: 'collapse' }}>
        <thead>
          <tr style={{ background: C.canvas }}>
            {['Started', 'Triggered by', 'Source', 'Status', 'Duration', 'Summary'].map(h => (
              <th key={h} style={{ padding: '8px 12px', textAlign: 'left', fontSize: 10, fontWeight: 600, color: C.sub, letterSpacing: 0.4, borderBottom: `1px solid ${C.border}` }}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {isLoading && <tr><td colSpan={6} style={{ padding: 28, textAlign: 'center', color: C.muted }}>Loading…</td></tr>}
          {!isLoading && runs.length === 0 && <tr><td colSpan={6} style={{ padding: 28, textAlign: 'center', color: C.muted }}>No runs yet.</td></tr>}
          {runs.map(r => (
            <React.Fragment key={r.id}>
              <tr style={{ borderTop: `1px solid ${C.border}`, cursor: 'pointer' }} onClick={() => setExpanded(expanded === r.id ? null : r.id)}>
                <td style={{ padding: '10px 12px', color: C.sub }}>{fmtDateTimeFull(r.startedAt)}</td>
                <td style={{ padding: '10px 12px', color: C.sub }}>{r.triggeredBy ?? '—'}</td>
                <td style={{ padding: '10px 12px' }}>
                  <span style={{ fontSize: 10, padding: '2px 6px', borderRadius: 4, background: C.canvas, color: C.sub, fontWeight: 600 }}>{r.invocationSource ?? 'SCHEDULED'}</span>
                </td>
                <td style={{ padding: '10px 12px' }}>
                  <Badge level={r.status === 'COMPLETED' ? 'healthy' : r.status === 'FAILED' ? 'critical' : 'info'} label={r.status} />
                </td>
                <td style={{ padding: '10px 12px', color: C.sub }}>{r.durationMs ? `${r.durationMs}ms` : '—'}</td>
                <td style={{ padding: '10px 12px', color: C.sub }}>{r.outputSummary ?? '—'}</td>
              </tr>
              {expanded === r.id && <ExpandedSteps agentId={agentId} runId={r.id} C={C} />}
            </React.Fragment>
          ))}
        </tbody>
      </table>
      <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
    </div>
  )
}

function ExpandedSteps({ agentId, runId, C }: { agentId: number; runId: number; C: any }) {
  const { data: steps = [] } = useQuery({
    queryKey: ['agent-run-steps', agentId, runId],
    queryFn: () => api.get(`/admin/agents/${agentId}/runs/${runId}/steps`).then(r => r.data),
  })
  const stepList = steps as any[]
  return (
    <tr><td colSpan={6} style={{ padding: '10px 18px', background: C.canvas, borderTop: `1px solid ${C.border}` }}>
      {stepList.length === 0 && <div style={{ color: C.muted, fontSize: 11 }}>No steps recorded.</div>}
      {stepList.map(s => (
        <div key={s.id} style={{ fontSize: 11, color: C.sub, marginBottom: 4 }}>
          <span style={{ fontFamily: 'ui-monospace, monospace' }}>{s.toolName}</span>
          {' · '}
          <span style={{ color: STEP_STATUS_COLOR(C)[s.hitlOutcome ?? 'COMPLETED'] ?? C.sub, fontWeight: 600 }}>{s.hitlOutcome ?? 'EXECUTED'}</span>
        </div>
      ))}
    </td></tr>
  )
}

// ── Live (STOMP-streamed tail) ───────────────────────────────────────────────

function LiveTab({ agentId, C }: { agentId: number; C: any }) {
  const [search] = useSearchParams()
  const runId = search.get('runId')
  const [lines, setLines] = useState<LiveLogLine[]>([])
  const wsRef = useRef<WebSocket | null>(null)

  useEffect(() => {
    if (!runId) return
    setLines([])
    const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host  = (import.meta as any).env?.VITE_API_BASE_URL?.replace(/^https?:\/\//, '') || 'localhost:8080'
    let ws: WebSocket
    try {
      ws = new WebSocket(`${proto}//${host}/ws/websocket`)
    } catch {
      return
    }
    wsRef.current = ws
    ws.onopen = () => {
      const token = useStore.getState().user?.token
      const auth = token ? `Authorization:Bearer ${token}\n` : ''
      ws.send(`CONNECT\naccept-version:1.1,1.0\nheart-beat:10000,10000\n${auth}\n\0`)
    }
    ws.onmessage = (evt) => {
      const body: string = typeof evt.data === 'string' ? evt.data : ''
      if (body.startsWith('CONNECTED')) {
        ws.send(`SUBSCRIBE\nid:sub-run-${runId}\ndestination:/topic/agent-runs/${runId}\n\n\0`)
        return
      }
      if (!body.startsWith('MESSAGE')) return
      const nlnl = body.indexOf('\n\n')
      if (nlnl < 0) return
      const payload = body.slice(nlnl + 2).replace(/\0$/, '')
      try {
        const data = JSON.parse(payload) as LiveLogLine
        setLines(prev => [...prev, data])
      } catch { /* ignore */ }
    }
    return () => {
      try { ws.send(`UNSUBSCRIBE\nid:sub-run-${runId}\n\n\0`) } catch { /* ignore */ }
      try { ws.close() } catch { /* ignore */ }
      wsRef.current = null
    }
  }, [runId, agentId])

  if (!runId) return (
    <div style={{ marginTop: 24, padding: 28, background: C.canvas, borderRadius: 12, color: C.sub, fontSize: 13 }}>
      No active run. Trigger one from the <strong>Test</strong> tab, or open this tab via a "Test" submission to tail a run live.
    </div>
  )

  return (
    <div style={{ marginTop: 20, background: C.navy, borderRadius: 12, padding: 16, fontFamily: 'ui-monospace, monospace', fontSize: 12, color: '#E2E8F0', minHeight: 240 }}>
      <div style={{ color: '#94A3B8', marginBottom: 8 }}>tail /topic/agent-runs/{runId} — {lines.length} event{lines.length === 1 ? '' : 's'}</div>
      {lines.length === 0 && <div style={{ color: '#64748B' }}>Waiting for events…</div>}
      {lines.map((l, i) => (
        <div key={i} style={{ marginBottom: 3, lineHeight: 1.5 }}>
          <span style={{ color: '#64748B' }}>{fmtTime(l.ts)}</span>
          {' '}
          <span style={{ color: STEP_STATUS_COLOR(C)[l.status] ?? '#94A3B8', fontWeight: 600 }}>{l.status}</span>
          {' '}
          <span style={{ color: '#CBD5E1' }}>{l.toolName}</span>
          {l.message && <span style={{ color: '#94A3B8' }}>{' — '}{l.message}</span>}
        </div>
      ))}
    </div>
  )
}

// ── Test ─────────────────────────────────────────────────────────────────────

function TestTab({ agentId, C, onStarted }: { agentId: number; C: any; onStarted: (runId: number) => void }) {
  const [argsText, setArgsText] = useState('{}')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  const submit = async () => {
    let parsed: any
    try { parsed = argsText.trim() ? JSON.parse(argsText) : {} }
    catch (e: any) { setError('Invalid JSON: ' + e.message); return }
    setError(''); setSubmitting(true)
    try {
      const res = await api.post(`/admin/agents/${agentId}/test-run`, parsed)
      const runId = res.data?.runId ?? res.data?.id
      if (runId) onStarted(Number(runId))
      else setError('Test run accepted but no runId returned.')
    } catch (e: any) {
      setError(e.response?.data?.error ?? e.message ?? 'Test run failed')
    } finally { setSubmitting(false) }
  }

  return (
    <div style={{ marginTop: 20, maxWidth: 720 }}>
      <div style={{ fontSize: 12, color: C.sub, marginBottom: 8 }}>
        Args JSON — passed through to the agent as the <code>inputContext</code> map.
      </div>
      <textarea
        value={argsText}
        onChange={e => setArgsText(e.target.value)}
        spellCheck={false}
        rows={10}
        style={{ width: '100%', fontFamily: 'ui-monospace, monospace', fontSize: 12, padding: 12, border: `1px solid ${C.border}`, borderRadius: 8, color: C.text, background: C.white }}
      />
      {error && <div style={{ color: C.red, fontSize: 12, marginTop: 8 }}>{error}</div>}
      <div style={{ marginTop: 12 }}>
        <button
          onClick={submit}
          disabled={submitting}
          style={{ fontSize: 12, padding: '8px 18px', borderRadius: 7, border: 'none', background: C.indigo, color: '#fff', cursor: submitting ? 'wait' : 'pointer', fontWeight: 600 }}
        >
          {submitting ? 'Starting…' : '▶ Run test'}
        </button>
        <span style={{ marginLeft: 12, fontSize: 11, color: C.sub }}>
          On success you'll switch to the Live tab and tail the run.
        </span>
      </div>
    </div>
  )
}
