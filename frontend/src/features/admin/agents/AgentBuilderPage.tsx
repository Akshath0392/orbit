import React, { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useC } from '../../../design/ThemeContext'
import type { Colors } from '../../../design/theme'
import { Badge } from '../../../design/components/Badge'
import { Tabs } from '../../../design/components/Tabs'
import { Pagination } from '../../../design/components/Pagination'
import { api } from '../../../api/client'
import { AgentRunHistory } from './AgentRunHistory'
import { relTime } from '../../../lib/datetime'

const AGENT_TYPES   = ['INTELLIGENCE', 'REMINDER', 'ESCALATION', 'COMMUNICATION', 'REPORTING']
const TRIGGER_TYPES = ['CRON', 'WEBHOOK', 'THRESHOLD', 'MANUAL', 'AGENT_OUTPUT']
const CHANNELS      = ['IN_APP', 'SLACK', 'EMAIL', 'JIRA']

const TYPE_BADGE: Record<string, any> = {
  INTELLIGENCE: 'info', REMINDER: 'watch', ESCALATION: 'critical',
  COMMUNICATION: 'teal', REPORTING: 'purple',
}

const RUN_COLOR = (C: Colors): Record<string, string> => ({
  COMPLETED: C.green, FAILED: C.red, RUNNING: C.blue,
})

const STEP_COLOR = (C: Colors): Record<string, string> => ({
  EXECUTED: C.green, APPROVED: C.green,
  AWAITING_HITL: C.amber, REJECTED: C.red, ERROR: C.red,
})

const STEP_ICON: Record<string, string> = {
  EXECUTED: '✓', APPROVED: '✓', AWAITING_HITL: '⏸', REJECTED: '✗', ERROR: '✗',
}

function triggerLabel(a: any) {
  if (!a.triggerConfig) return a.triggerType
  try {
    const cfg = JSON.parse(a.triggerConfig)
    if (cfg.cron)   return `Cron: ${cfg.cron}`
    if (cfg.metric) return `${cfg.metric} > ${cfg.gt}`
    if (cfg.events) return `Webhook: ${cfg.events.join(', ')}`
  } catch { /* ignore */ }
  return a.triggerType
}

function fmtTime(iso: string) {
  return relTime(iso)
}

export function AgentBuilderPage() {
  const C = useC()
  const qc = useQueryClient()

  // shared
  const [tab, setTab] = useState('Agents')
  const [toast, setToast] = useState('')
  const [toastOk, setToastOk] = useState(true)

  // Agents tab
  const [page, setPage] = useState(0)
  const [showModal, setShowModal] = useState(false)
  const [editAgent, setEditAgent] = useState<any>(null)
  const [runResult, setRunResult] = useState<Record<number, any>>({})
  const [running, setRunning] = useState<number | null>(null)
  const [expandedHistory, setExpandedHistory] = useState<number | null>(null)

  // Logs tab
  const [agentFilter, setAgentFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [logsPage, setLogsPage] = useState(0)
  const [expandedRunId, setExpandedRunId] = useState<number | null>(null)

  // HITL
  const [rejectingStep, setRejectingStep] = useState<number | null>(null)
  const [rejectReason, setRejectReason] = useState('')
  const [actionLoading, setActionLoading] = useState<number | null>(null)

  const showToast = (msg: string, ok = true) => {
    setToast(msg); setToastOk(ok); setTimeout(() => setToast(''), 3500)
  }

  // ── Queries ──────────────────────────────────────────────────────────────────

  const { data: agents = [], isLoading } = useQuery({
    queryKey: ['agent-definitions'],
    queryFn: () => api.get('/admin/agents').then(r => r.data),
  })

  const { data: tools = [] } = useQuery({
    queryKey: ['agent-tools'],
    queryFn: () => api.get('/admin/agents/tools').then(r => r.data),
  })

  const { data: runsPage, isLoading: runsLoading } = useQuery({
    queryKey: ['all-agent-runs', agentFilter, statusFilter, logsPage],
    queryFn: () => api.get('/admin/agents/runs', {
      params: { agentId: agentFilter || undefined, status: statusFilter || undefined, page: logsPage, size: 20 },
    }).then(r => r.data),
    enabled: tab === 'Execution logs',
  })

  const { data: pendingHitl = [], isLoading: hitlLoading } = useQuery({
    queryKey: ['pending-hitl'],
    queryFn: () => api.get('/admin/agents/runs/pending-hitl').then(r => r.data),
    refetchInterval: 30000,
  })

  const { data: expandedSteps = [] } = useQuery({
    queryKey: ['run-steps', expandedRunId],
    queryFn: () => api.get(`/admin/agents/runs/${expandedRunId}/steps`).then(r => r.data),
    enabled: expandedRunId !== null,
  })

  const agentList  = agents as any[]
  const toolList   = tools as any[]
  const runs       = (runsPage?.content ?? []) as any[]
  const totalPages = runsPage?.totalPages ?? 0
  const pending    = pendingHitl as any[]

  const PAGE_SIZE  = 10
  const paged      = agentList.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)
  const agentPages = Math.ceil(agentList.length / PAGE_SIZE)

  const pendingCount = pending.length

  // ── Agent actions ─────────────────────────────────────────────────────────

  const toggle = async (a: any) => {
    await api.patch(`/admin/agents/${a.id}/toggle`)
    qc.invalidateQueries({ queryKey: ['agent-definitions'] })
  }

  const deleteAgent = async (a: any) => {
    if (a.systemAgent) { showToast('System agents cannot be deleted', false); return }
    await api.delete(`/admin/agents/${a.id}`)
    qc.invalidateQueries({ queryKey: ['agent-definitions'] })
    showToast('Agent deleted')
  }

  const testRun = async (a: any) => {
    setRunning(a.id)
    try {
      const res = await api.post(`/admin/agents/${a.id}/test-run`)
      setRunResult(prev => ({ ...prev, [a.id]: res.data }))
    } catch { showToast('Test run failed', false) }
    finally { setRunning(null) }
  }

  // ── HITL actions ──────────────────────────────────────────────────────────

  const approve = async (runId: number, stepId: number, editedArgs?: any) => {
    setActionLoading(stepId)
    try {
      await api.post(`/admin/agents/runs/${runId}/steps/${stepId}/approve`,
        editedArgs ? { editedArgs } : {})
      qc.invalidateQueries({ queryKey: ['run-steps', runId] })
      qc.invalidateQueries({ queryKey: ['all-agent-runs'] })
      qc.invalidateQueries({ queryKey: ['pending-hitl'] })
      showToast('Tool approved and executed')
    } catch (e: any) {
      showToast(e.response?.data?.error ?? 'Approval failed', false)
    } finally { setActionLoading(null) }
  }

  const reject = async (runId: number, stepId: number) => {
    if (!rejectReason.trim()) return
    setActionLoading(stepId)
    try {
      await api.post(`/admin/agents/runs/${runId}/steps/${stepId}/reject`, { reason: rejectReason })
      qc.invalidateQueries({ queryKey: ['run-steps', runId] })
      qc.invalidateQueries({ queryKey: ['all-agent-runs'] })
      qc.invalidateQueries({ queryKey: ['pending-hitl'] })
      setRejectingStep(null); setRejectReason('')
      showToast('Step rejected')
    } catch (e: any) {
      showToast(e.response?.data?.error ?? 'Rejection failed', false)
    } finally { setActionLoading(null) }
  }

  if (isLoading) return <div style={{ padding: 40, color: C.sub }}>Loading…</div>

  const tabItems = [
    'Agents',
    'Execution logs',
    `Pending approvals${pendingCount > 0 ? ` (${pendingCount})` : ''}`,
  ]

  return (
    <div style={{ padding: '22px 24px' }}>
      {toast && (
        <div style={{ position: 'fixed', bottom: 24, right: 24, zIndex: 9999, padding: '10px 18px', borderRadius: 8, background: toastOk ? C.text : C.red, color: '#fff', fontSize: 13, fontWeight: 500, boxShadow: '0 8px 24px rgba(0,0,0,.18)' }}>
          {toast}
        </div>
      )}

      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 18 }}>
        <div>
          <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>Agents</div>
          <div style={{ fontSize: 12, color: C.sub, marginTop: 3 }}>
            {agentList.length} agents · {agentList.filter((a: any) => a.enabled).length} enabled
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          {pendingCount > 0 && tab !== 'Pending approvals' && (
            <button
              onClick={() => setTab('Pending approvals')}
              style={{ fontSize: 12, padding: '7px 14px', borderRadius: 7, border: 'none', background: C.amberPale, color: C.amberDeep, cursor: 'pointer', fontWeight: 600 }}
            >
              ⏸ {pendingCount} pending approval{pendingCount > 1 ? 's' : ''}
            </button>
          )}
          {tab === 'Agents' && (
            <button
              onClick={() => { setEditAgent(null); setShowModal(true) }}
              style={{ fontSize: 12, padding: '7px 16px', borderRadius: 7, border: 'none', background: C.indigo, color: '#fff', cursor: 'pointer', fontWeight: 600 }}
            >
              + New agent
            </button>
          )}
        </div>
      </div>

      <Tabs
        items={tabItems}
        active={tab}
        onChange={t => setTab(t.replace(/ \(\d+\)$/, ''))}
      />

      {/* ── Agents tab ──────────────────────────────────────────────────────── */}
      {tab === 'Agents' && (
        <div style={{ marginTop: 20 }}>
          {/* Tool reference bar */}
          {toolList.length > 0 && (
            <div style={{ background: C.navyMid, borderRadius: 10, padding: '10px 14px', marginBottom: 18, display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              <span style={{ fontSize: 11, color: C.muted, marginRight: 4, alignSelf: 'center' }}>Available tools:</span>
              {toolList.map((t: any) => (
                <span key={t.id} style={{ fontSize: 10, padding: '2px 8px', borderRadius: 20, background: t.requiresHitl ? C.amberPale : C.greenPale, color: t.requiresHitl ? C.amberDeep : C.greenDeep, fontWeight: 500 }}>
                  {t.id}{t.requiresHitl ? ' ⊙' : ''}
                </span>
              ))}
              <span style={{ fontSize: 10, color: C.muted, alignSelf: 'center', marginLeft: 4 }}>⊙ = HITL required</span>
            </div>
          )}

          <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden', marginBottom: 12 }}>
            <table style={{ width: '100%', fontSize: 12, borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ background: C.canvas }}>
                  {['Agent', 'Type', 'Trigger', 'Tools', 'Channel', 'HITL', 'Status', 'Actions'].map(h => (
                    <th key={h} style={{ padding: '8px 12px', textAlign: 'left', fontSize: 10, fontWeight: 600, color: C.sub, letterSpacing: 0.4, borderBottom: `1px solid ${C.border}` }}>
                      {h !== 'Actions' ? h : ''}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {paged.length === 0 && (
                  <tr><td colSpan={8} style={{ padding: 32, textAlign: 'center', color: C.muted }}>No agents yet — create one above</td></tr>
                )}
                {paged.map((a: any, i: number) => (
                  <React.Fragment key={a.id}>
                    <tr style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none', background: C.white }}>
                      <td style={{ padding: '10px 12px' }}>
                        <Link to={`/admin/agents/${a.id}`} style={{ fontWeight: 600, color: C.indigo, textDecoration: 'none' }}>{a.name}</Link>
                        {a.systemAgent && <span style={{ fontSize: 10, color: C.muted, marginLeft: 6 }}>system</span>}
                        {a.description && <div style={{ fontSize: 11, color: C.sub, marginTop: 2 }}>{a.description}</div>}
                      </td>
                      <td style={{ padding: '10px 12px' }}>
                        <Badge level={TYPE_BADGE[a.agentType] ?? 'neutral'} label={a.agentType} />
                      </td>
                      <td style={{ padding: '10px 12px', color: C.sub, fontSize: 11 }}>{triggerLabel(a)}</td>
                      <td style={{ padding: '10px 12px' }}>
                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 3 }}>
                          {(a.tools ?? []).map((t: string) => (
                            <span key={t} style={{ fontSize: 10, padding: '1px 6px', borderRadius: 4, background: C.canvas, border: `1px solid ${C.border}`, color: C.sub }}>{t}</span>
                          ))}
                        </div>
                      </td>
                      <td style={{ padding: '10px 12px', color: C.sub, fontSize: 11 }}>{a.outputChannel ?? '—'}</td>
                      <td style={{ padding: '10px 12px' }}>
                        <span style={{ fontSize: 11, color: a.requiresHitl ? C.amber : C.green, fontWeight: 600 }}>
                          {a.requiresHitl ? 'Yes' : 'No'}
                        </span>
                      </td>
                      <td style={{ padding: '10px 12px' }}>
                        <button
                          onClick={() => toggle(a)}
                          style={{ fontSize: 11, padding: '3px 10px', borderRadius: 20, border: 'none', cursor: 'pointer', fontWeight: 600, background: a.enabled ? C.greenPale : C.canvas, color: a.enabled ? C.greenDeep : C.muted }}
                        >
                          {a.enabled ? '● Enabled' : '○ Disabled'}
                        </button>
                      </td>
                      <td style={{ padding: '10px 12px' }}>
                        <div style={{ display: 'flex', gap: 5 }}>
                          <button
                            onClick={() => testRun(a)}
                            disabled={running === a.id}
                            style={{ fontSize: 11, padding: '3px 9px', borderRadius: 5, cursor: 'pointer', border: `1px solid ${C.indigo}`, background: 'transparent', color: C.indigo }}
                          >
                            {running === a.id ? '⟳' : '▶ Test'}
                          </button>
                          <button
                            onClick={() => { setEditAgent(a); setShowModal(true) }}
                            style={{ fontSize: 11, padding: '3px 9px', borderRadius: 5, cursor: 'pointer', border: `1px solid ${C.border}`, background: 'transparent', color: C.sub }}
                          >
                            Edit
                          </button>
                          <button
                            onClick={() => setExpandedHistory(expandedHistory === a.id ? null : a.id)}
                            style={{ fontSize: 11, padding: '3px 9px', borderRadius: 5, cursor: 'pointer', border: `1px solid ${C.border}`, background: expandedHistory === a.id ? C.indigoPale : 'transparent', color: expandedHistory === a.id ? C.indigo : C.sub }}
                          >
                            {expandedHistory === a.id ? '▲ Runs' : '▾ Runs'}
                          </button>
                          {!a.systemAgent && (
                            <button
                              onClick={() => deleteAgent(a)}
                              style={{ fontSize: 11, padding: '3px 9px', borderRadius: 5, cursor: 'pointer', border: `1px solid ${C.border}`, background: 'transparent', color: C.red }}
                            >
                              ✕
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                    {runResult[a.id] && (
                      <tr key={`run-${a.id}`}>
                        <td colSpan={8} style={{ padding: '10px 14px', background: C.canvas, borderTop: `1px solid ${C.border}` }}>
                          <RunStepsPanel run={runResult[a.id]} C={C} />
                        </td>
                      </tr>
                    )}
                    {expandedHistory === a.id && (
                      <tr key={`hist-${a.id}`}>
                        <td colSpan={8} style={{ padding: '10px 14px', background: C.canvas, borderTop: `1px solid ${C.border}` }}>
                          <div style={{ fontSize: 11, fontWeight: 600, color: C.sub, marginBottom: 8, letterSpacing: 0.4, textTransform: 'uppercase' }}>
                            Execution history — {a.name}
                          </div>
                          <AgentRunHistory
                            agentId={a.id}
                            agentName={a.name}
                            allowHitlActions
                            onHitlActioned={() => qc.invalidateQueries({ queryKey: ['agent-definitions'] })}
                          />
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                ))}
              </tbody>
            </table>
            <Pagination page={page} totalPages={agentPages} onPageChange={setPage} />
          </div>
        </div>
      )}

      {/* ── Execution logs tab ──────────────────────────────────────────────── */}
      {tab === 'Execution logs' && (
        <div style={{ marginTop: 20 }}>
          <div style={{ display: 'flex', gap: 10, marginBottom: 16 }}>
            <select
              value={agentFilter}
              onChange={e => { setAgentFilter(e.target.value); setLogsPage(0) }}
              style={selStyle(C)}
            >
              <option value="">All agents</option>
              {agentList.map((a: any) => (
                <option key={a.id} value={a.id}>{a.name}</option>
              ))}
            </select>
            <select
              value={statusFilter}
              onChange={e => { setStatusFilter(e.target.value); setLogsPage(0) }}
              style={selStyle(C)}
            >
              <option value="">All statuses</option>
              {['COMPLETED', 'FAILED', 'RUNNING'].map(s => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </div>

          {runsLoading ? (
            <div style={{ padding: 32, textAlign: 'center', color: C.muted }}>Loading…</div>
          ) : (
            <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
                <thead>
                  <tr style={{ background: C.canvas }}>
                    {['Run', 'Agent', 'Trigger', 'Status', '⏸', 'Duration', 'Started', ''].map(h => (
                      <th key={h} style={{ padding: '8px 14px', textAlign: 'left', fontSize: 10, fontWeight: 600, color: C.sub, letterSpacing: 0.4, borderBottom: `1px solid ${C.border}` }}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {runs.length === 0 && (
                    <tr><td colSpan={8} style={{ padding: 32, textAlign: 'center', color: C.muted }}>No runs found</td></tr>
                  )}
                  {runs.map((r: any, i: number) => {
                    const isExpanded = expandedRunId === r.id
                    const stColor = RUN_COLOR(C)[r.status] || C.muted
                    return [
                      <tr
                        key={r.id}
                        onClick={() => setExpandedRunId(isExpanded ? null : r.id)}
                        style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none', cursor: 'pointer', background: isExpanded ? C.indigoPale : C.white }}
                      >
                        <td style={{ padding: '10px 14px', fontFamily: 'monospace', fontSize: 11, color: C.sub }}>#{r.id}</td>
                        <td style={{ padding: '10px 14px' }}>
                          <div style={{ fontWeight: 600, color: C.text }}>{r.agentName ?? `Agent #${r.agentId}`}</div>
                          {r.agentType && <div style={{ fontSize: 10, color: C.muted }}>{r.agentType}</div>}
                        </td>
                        <td style={{ padding: '10px 14px', color: C.sub }}>{r.triggeredBy}</td>
                        <td style={{ padding: '10px 14px' }}>
                          <span style={{ color: stColor, fontWeight: 600 }}>{r.status}</span>
                        </td>
                        <td style={{ padding: '10px 14px' }}>
                          {r.pendingHitl > 0 && (
                            <span style={{ background: C.amberPale, color: C.amberDeep, padding: '2px 7px', borderRadius: 10, fontSize: 10, fontWeight: 700 }}>
                              ⏸ {r.pendingHitl}
                            </span>
                          )}
                        </td>
                        <td style={{ padding: '10px 14px', color: C.muted, fontSize: 11 }}>
                          {r.durationMs ? `${r.durationMs}ms` : '—'}
                        </td>
                        <td style={{ padding: '10px 14px', color: C.muted, fontSize: 11 }}>{fmtTime(r.startedAt)}</td>
                        <td style={{ padding: '10px 14px', color: C.indigo, fontSize: 11 }}>
                          {isExpanded ? '▲' : '▶'}
                        </td>
                      </tr>,
                      isExpanded && (
                        <tr key={`steps-${r.id}`}>
                          <td colSpan={8} style={{ padding: '12px 16px', background: C.canvas, borderTop: `1px solid ${C.border}` }}>
                            <LogStepDetail
                              steps={expandedSteps as any[]}
                              runId={r.id}
                              rejectingStep={rejectingStep}
                              rejectReason={rejectReason}
                              actionLoading={actionLoading}
                              onApprove={approve}
                              onStartReject={(id: number) => { setRejectingStep(id); setRejectReason('') }}
                              onRejectReasonChange={setRejectReason}
                              onConfirmReject={reject}
                              onCancelReject={() => { setRejectingStep(null); setRejectReason('') }}
                              C={C}
                            />
                          </td>
                        </tr>
                      ),
                    ]
                  })}
                </tbody>
              </table>
              <Pagination page={logsPage} totalPages={totalPages} onPageChange={setLogsPage} />
            </div>
          )}
        </div>
      )}

      {/* ── Pending approvals tab ────────────────────────────────────────────── */}
      {tab === 'Pending approvals' && (
        <div style={{ marginTop: 20 }}>
          {hitlLoading ? (
            <div style={{ padding: 32, textAlign: 'center', color: C.muted }}>Loading…</div>
          ) : pending.length === 0 ? (
            <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: 40, textAlign: 'center' }}>
              <div style={{ fontSize: 24, marginBottom: 8 }}>✓</div>
              <div style={{ fontSize: 14, fontWeight: 600, color: C.text, marginBottom: 4 }}>No pending approvals</div>
              <div style={{ fontSize: 12, color: C.muted }}>All HITL tool calls have been approved or rejected.</div>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {pending.map((item: any) => {
                const isRejecting = rejectingStep === item.id
                return (
                  <div key={item.id} style={{ background: C.white, border: `2px solid ${C.amber}`, borderRadius: 12, overflow: 'hidden' }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px 18px', background: C.amberPale, borderBottom: `1px solid ${C.amber}` }}>
                      <div>
                        <div style={{ fontSize: 13, fontWeight: 700, color: C.text }}>⏸ {item.tool}</div>
                        <div style={{ fontSize: 11, color: C.sub, marginTop: 2 }}>
                          {item.agentName ?? `Agent #${item.agentId}`} · Run #{item.runId} · {fmtTime(item.calledAt)}
                        </div>
                      </div>
                      <span style={{ fontSize: 10, padding: '3px 8px', borderRadius: 10, background: C.amberPale, color: C.amberDeep, fontWeight: 600 }}>
                        AWAITING APPROVAL
                      </span>
                    </div>
                    <div style={{ padding: '12px 18px' }}>
                      {item.args && item.args !== '{}' ? (
                        <div style={{ marginBottom: 12 }}>
                          <div style={{ fontSize: 10, fontWeight: 600, color: C.sub, letterSpacing: 0.4, textTransform: 'uppercase', marginBottom: 6 }}>Proposed action</div>
                          <pre style={{ margin: 0, fontSize: 11, fontFamily: 'monospace', background: C.canvas, padding: '8px 12px', borderRadius: 6, color: C.text, overflowX: 'auto', whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                            {JSON.stringify(JSON.parse(item.args), null, 2)}
                          </pre>
                        </div>
                      ) : (
                        <div style={{ fontSize: 12, color: C.muted, marginBottom: 12 }}>No args — tool will execute with default context.</div>
                      )}
                      {!isRejecting ? (
                        <div style={{ display: 'flex', gap: 8 }}>
                          <button
                            onClick={() => approve(item.runId, item.id)}
                            disabled={actionLoading === item.id}
                            style={{ fontSize: 12, padding: '7px 18px', borderRadius: 7, border: 'none', background: C.green, color: '#fff', cursor: 'pointer', fontWeight: 600 }}
                          >
                            {actionLoading === item.id ? '⟳ Executing…' : '✓ Approve & execute'}
                          </button>
                          <button
                            onClick={() => { setRejectingStep(item.id); setRejectReason('') }}
                            style={{ fontSize: 12, padding: '7px 16px', borderRadius: 7, border: `1px solid ${C.red}`, background: 'transparent', color: C.red, cursor: 'pointer', fontWeight: 600 }}
                          >
                            ✗ Reject
                          </button>
                        </div>
                      ) : (
                        <div>
                          <div style={{ fontSize: 11, fontWeight: 600, color: C.red, marginBottom: 6 }}>Reason for rejection (required)</div>
                          <textarea
                            value={rejectReason}
                            onChange={e => setRejectReason(e.target.value)}
                            placeholder="e.g. Wrong recipient — let me update the contact first"
                            rows={2}
                            style={{ width: '100%', fontSize: 12, padding: '8px 10px', borderRadius: 6, border: `1px solid ${C.red}`, outline: 'none', resize: 'vertical', boxSizing: 'border-box' as const }}
                          />
                          <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
                            <button
                              onClick={() => reject(item.runId, item.id)}
                              disabled={!rejectReason.trim() || actionLoading === item.id}
                              style={{ fontSize: 12, padding: '7px 16px', borderRadius: 7, border: 'none', background: C.red, color: '#fff', cursor: 'pointer', fontWeight: 600 }}
                            >
                              {actionLoading === item.id ? '⟳' : 'Confirm rejection'}
                            </button>
                            <button
                              onClick={() => { setRejectingStep(null); setRejectReason('') }}
                              style={{ fontSize: 12, padding: '7px 12px', borderRadius: 7, border: `1px solid ${C.border}`, background: 'transparent', color: C.sub, cursor: 'pointer' }}
                            >
                              Cancel
                            </button>
                          </div>
                        </div>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      )}

      {showModal && (
        <AgentFormModal
          agent={editAgent}
          toolList={toolList}
          onClose={() => { setShowModal(false); setEditAgent(null) }}
          onSaved={() => {
            qc.invalidateQueries({ queryKey: ['agent-definitions'] })
            setShowModal(false); setEditAgent(null)
            showToast(editAgent ? 'Agent updated' : 'Agent created')
          }}
        />
      )}
    </div>
  )
}

// ── Log step detail (used in Execution logs row expansion) ─────────────────────

function LogStepDetail({ steps, runId, rejectingStep, rejectReason, actionLoading,
  onApprove, onStartReject, onRejectReasonChange, onConfirmReject, onCancelReject, C }: any) {
  if (steps.length === 0) return (
    <div style={{ fontSize: 12, color: C.muted }}>No tool calls recorded for this run.</div>
  )
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      {steps.map((s: any) => {
        const color = STEP_COLOR(C)[s.status] || C.muted
        const icon  = STEP_ICON[s.status]  || '·'
        const isHitl = s.status === 'AWAITING_HITL'
        const isRejecting = rejectingStep === s.id

        let resultPreview = ''
        if (s.result) {
          try {
            const p = JSON.parse(s.result)
            resultPreview = Object.entries(p).slice(0, 4).map(([k, v]) => `${k}: ${v}`).join(' · ')
          } catch { resultPreview = String(s.result).slice(0, 120) }
        }

        return (
          <div key={s.id} style={{ borderRadius: 6, border: `1px solid ${isHitl ? C.amber : C.border}`, overflow: 'hidden' }}>
            <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8, padding: '7px 10px', background: isHitl ? C.amberPale : C.white }}>
              <span style={{ fontSize: 12, color, flexShrink: 0, marginTop: 1 }}>{icon}</span>
              <div style={{ flex: 1, minWidth: 0 }}>
                <span style={{ fontSize: 11, fontWeight: 700, color, fontFamily: 'monospace' }}>{s.tool}</span>
                {isHitl && <span style={{ fontSize: 11, color: C.amberDeep, marginLeft: 8 }}>— awaiting approval before this tool can execute</span>}
                {s.status === 'REJECTED' && s.hitlNote && <span style={{ fontSize: 11, color: C.red, marginLeft: 8 }}>Rejected: {s.hitlNote}</span>}
                {resultPreview && !isHitl && <span style={{ fontSize: 10, color: C.sub, marginLeft: 8 }}>{resultPreview}</span>}
              </div>
              <span style={{ fontSize: 10, color: C.muted, flexShrink: 0 }}>{fmtTime(s.calledAt)}</span>
            </div>
            {isHitl && !isRejecting && (
              <div style={{ display: 'flex', gap: 8, padding: '8px 10px', background: C.amberPale, borderTop: `1px solid ${C.amber}` }}>
                <button onClick={() => onApprove(runId, s.id)} disabled={actionLoading === s.id}
                  style={{ fontSize: 11, padding: '4px 12px', borderRadius: 5, border: 'none', background: C.green, color: '#fff', cursor: 'pointer', fontWeight: 600 }}>
                  {actionLoading === s.id ? '⟳' : '✓ Approve'}
                </button>
                <button onClick={() => onStartReject(s.id)}
                  style={{ fontSize: 11, padding: '4px 12px', borderRadius: 5, border: `1px solid ${C.red}`, background: 'transparent', color: C.red, cursor: 'pointer', fontWeight: 600 }}>
                  ✗ Reject
                </button>
              </div>
            )}
            {isRejecting && (
              <div style={{ padding: '8px 10px', background: C.redPale, borderTop: `1px solid ${C.red}` }}>
                <textarea value={rejectReason} onChange={e => onRejectReasonChange(e.target.value)}
                  placeholder="Reason for rejection (required)" rows={2}
                  style={{ width: '100%', fontSize: 12, padding: '6px 8px', borderRadius: 5, border: `1px solid ${C.red}`, outline: 'none', resize: 'vertical', boxSizing: 'border-box' as const }} />
                <div style={{ display: 'flex', gap: 6, marginTop: 6 }}>
                  <button onClick={() => onConfirmReject(runId, s.id)} disabled={!rejectReason.trim() || actionLoading === s.id}
                    style={{ fontSize: 11, padding: '4px 12px', borderRadius: 5, border: 'none', background: C.red, color: '#fff', cursor: 'pointer', fontWeight: 600 }}>
                    {actionLoading === s.id ? '⟳' : 'Confirm rejection'}
                  </button>
                  <button onClick={onCancelReject}
                    style={{ fontSize: 11, padding: '4px 10px', borderRadius: 5, border: `1px solid ${C.border}`, background: 'transparent', color: C.sub, cursor: 'pointer' }}>
                    Cancel
                  </button>
                </div>
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}

// ── Test-run steps panel (shown inline per agent on the Agents tab) ────────────

function RunStepsPanel({ run, C }: { run: any; C: any }) {
  const steps: any[] = run.steps ?? []
  const hitlBlocked = steps.filter((s: any) => s.status === 'AWAITING_HITL')

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 8 }}>
        <span style={{ fontSize: 11, fontWeight: 700, color: run.status === 'COMPLETED' ? C.greenDeep : C.red }}>
          {run.status === 'COMPLETED' ? '✓' : '✗'} Run #{run.runId} · {run.status} · {run.durationMs}ms
        </span>
        {hitlBlocked.length > 0 && (
          <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 10, background: C.amberPale, color: C.amberDeep, fontWeight: 600 }}>
            ⏸ {hitlBlocked.length} tool{hitlBlocked.length > 1 ? 's' : ''} awaiting HITL approval
          </span>
        )}
        {run.errorMessage && <span style={{ fontSize: 11, color: C.red }}>{run.errorMessage}</span>}
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        {steps.map((s: any, i: number) => {
          const isHitl  = s.status === 'AWAITING_HITL'
          const isError = s.status === 'ERROR'
          const color   = isHitl ? C.amberDeep : isError ? C.red : C.greenDeep
          const bg      = isHitl ? C.amberPale : isError ? C.redPale : C.greenPale
          const icon    = isHitl ? '⏸' : isError ? '✗' : '✓'

          let resultPreview = ''
          if (s.result) {
            try {
              const parsed = typeof s.result === 'string' ? JSON.parse(s.result) : s.result
              resultPreview = Object.entries(parsed).slice(0, 3).map(([k, v]) => `${k}: ${v}`).join(' · ')
            } catch { resultPreview = String(s.result).slice(0, 80) }
          }

          return (
            <div key={i} style={{ display: 'flex', alignItems: 'flex-start', gap: 8, padding: '5px 8px', borderRadius: 6, background: bg }}>
              <span style={{ fontSize: 12, color, flexShrink: 0, marginTop: 1 }}>{icon}</span>
              <div style={{ flex: 1, minWidth: 0 }}>
                <span style={{ fontSize: 11, fontWeight: 600, color, fontFamily: 'monospace' }}>{s.tool}</span>
                {isHitl && <span style={{ fontSize: 11, color: C.amberDeep, marginLeft: 8 }}>— blocked, waiting for human approval before sending</span>}
                {resultPreview && !isHitl && <span style={{ fontSize: 10, color: C.sub, marginLeft: 8 }}>{resultPreview}</span>}
              </div>
            </div>
          )
        })}
        {steps.length === 0 && <div style={{ fontSize: 11, color: C.muted }}>No tools were configured for this agent.</div>}
      </div>
    </div>
  )
}

// ── Agent create/edit modal ───────────────────────────────────────────────────

function AgentFormModal({ agent, toolList, onClose, onSaved }: { agent: any; toolList: any[]; onClose: () => void; onSaved: () => void }) {
  const C = useC()
  const [name,           setName]           = useState(agent?.name ?? '')
  const [description,    setDescription]    = useState(agent?.description ?? '')
  const [agentType,      setAgentType]      = useState(agent?.agentType ?? 'INTELLIGENCE')
  const [triggerType,    setTriggerType]    = useState(agent?.triggerType ?? 'MANUAL')
  const [triggerConfig,  setTriggerConfig]  = useState(agent?.triggerConfig ?? '{}')
  const [promptTemplate, setPromptTemplate] = useState(agent?.promptTemplate ?? '')
  const [selectedTools,  setSelectedTools]  = useState<string[]>(agent?.tools ?? [])
  const [outputChannel,  setOutputChannel]  = useState(agent?.outputChannel || 'IN_APP')
  const [channelConfig,  setChannelConfig]  = useState(agent?.channelConfig ?? '{}')
  const [requiresHitl,   setRequiresHitl]   = useState(agent?.requiresHitl ?? true)
  const [saving, setSaving] = useState(false)
  const [error,  setError]  = useState('')

  const toggleTool = (id: string) =>
    setSelectedTools(prev => prev.includes(id) ? prev.filter(t => t !== id) : [...prev, id])

  const save = async () => {
    if (!name.trim()) { setError('Name is required'); return }
    setSaving(true); setError('')
    try {
      const body = { name, description, agentType, triggerType, triggerConfig, promptTemplate, tools: selectedTools, outputChannel, channelConfig, requiresHitl }
      if (agent?.id) await api.put(`/admin/agents/${agent.id}`, body)
      else           await api.post('/admin/agents', body)
      onSaved()
    } catch (e: any) {
      setError(e.response?.data?.message ?? 'Save failed')
    } finally { setSaving(false) }
  }

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,22,41,.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
      <div style={{ background: C.white, borderRadius: 14, width: 580, maxHeight: '90vh', overflowY: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,.3)' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '16px 20px', borderBottom: `1px solid ${C.border}`, position: 'sticky', top: 0, background: C.white, zIndex: 1 }}>
          <div style={{ fontSize: 15, fontWeight: 600, color: C.text }}>{agent ? 'Edit agent' : 'New agent'}</div>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 18, color: C.muted }}>✕</button>
        </div>
        <div style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 14 }}>
          <Field label="Name">
            <input value={name} onChange={e => setName(e.target.value)} placeholder="e.g. Dev Reminder Agent" style={inputStyle(C)} />
          </Field>
          <Field label="Description">
            <input value={description} onChange={e => setDescription(e.target.value)} placeholder="What this agent does" style={inputStyle(C)} />
          </Field>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Field label="Agent type">
              <select value={agentType} onChange={e => setAgentType(e.target.value)} style={inputStyle(C)}>
                {AGENT_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
              </select>
            </Field>
            <Field label="Trigger">
              <select value={triggerType} onChange={e => setTriggerType(e.target.value)} style={inputStyle(C)}>
                {TRIGGER_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
              </select>
            </Field>
          </div>
          <Field label={`Trigger config (JSON)${triggerType === 'CRON' ? ' — e.g. {"cron":"0 9 * * MON-FRI"}' : triggerType === 'THRESHOLD' ? ' — e.g. {"metric":"hold_days","gt":5}' : ''}`}>
            <input value={triggerConfig} onChange={e => setTriggerConfig(e.target.value)} placeholder="{}"
              style={{ ...inputStyle(C), fontFamily: 'monospace', fontSize: 11 }} />
          </Field>
          <Field label="Tools">
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, padding: '8px 10px', border: `1px solid ${C.border}`, borderRadius: 7, background: C.canvas, minHeight: 40 }}>
              {toolList.map((t: any) => {
                const sel = selectedTools.includes(t.id)
                return (
                  <button key={t.id} onClick={() => toggleTool(t.id)}
                    title={t.description + (t.requiresHitl ? ' (HITL)' : '')}
                    style={{ fontSize: 10, padding: '3px 8px', borderRadius: 4, cursor: 'pointer', border: `1px solid ${sel ? C.indigo : C.border}`, background: sel ? C.indigoPale : C.white, color: sel ? C.indigo : C.sub, fontWeight: sel ? 600 : 400 }}>
                    {t.id}{t.requiresHitl ? ' ⊙' : ''}
                  </button>
                )
              })}
              {toolList.length === 0 && <span style={{ fontSize: 11, color: C.muted }}>Loading tools…</span>}
            </div>
          </Field>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Field label="Output channel">
              <select value={outputChannel} onChange={e => setOutputChannel(e.target.value)} style={inputStyle(C)}>
                {CHANNELS.map(c => <option key={c} value={c}>{c}</option>)}
              </select>
            </Field>
            <Field label="Channel config (JSON)">
              <input value={channelConfig} onChange={e => setChannelConfig(e.target.value)} placeholder="{}"
                style={{ ...inputStyle(C), fontFamily: 'monospace', fontSize: 11 }} />
            </Field>
          </div>
          <Field label="Prompt template (optional — use {{variable}} for placeholders)">
            <textarea value={promptTemplate} onChange={e => setPromptTemplate(e.target.value)} rows={4}
              placeholder="You are an Orbit delivery agent. Project: {{project_name}}. Overdue CRs: {{overdue_crs}}. Summarise and remind the team."
              style={{ ...inputStyle(C), resize: 'vertical', fontFamily: 'monospace', fontSize: 11 }} />
          </Field>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <input type="checkbox" id="hitl" checked={requiresHitl} onChange={e => setRequiresHitl(e.target.checked)} />
            <label htmlFor="hitl" style={{ fontSize: 12, color: C.text, cursor: 'pointer' }}>
              Require HITL approval before executing external actions
            </label>
          </div>
          {error && <div style={{ fontSize: 12, color: C.red }}>{error}</div>}
          <button onClick={save} disabled={saving || !name.trim()}
            style={{ padding: '9px', borderRadius: 7, border: 'none', background: C.indigo, color: '#fff', fontSize: 13, fontWeight: 600, cursor: 'pointer', opacity: !name.trim() ? 0.6 : 1 }}>
            {saving ? 'Saving…' : agent ? 'Update agent' : 'Create agent'}
          </button>
        </div>
      </div>
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  const C = useC()
  return (
    <div>
      <div style={{ fontSize: 11, fontWeight: 500, color: C.sub, marginBottom: 5 }}>{label}</div>
      {children}
    </div>
  )
}

function inputStyle(C: any) {
  return { fontSize: 12, padding: '7px 10px', borderRadius: 7, border: `1px solid ${C.border}`, outline: 'none', width: '100%', boxSizing: 'border-box' as const, background: C.white }
}

function selStyle(C: any) {
  return { fontSize: 12, padding: '7px 10px', borderRadius: 7, border: `1px solid ${C.border}`, background: C.white, color: C.text, minWidth: 160 }
}
