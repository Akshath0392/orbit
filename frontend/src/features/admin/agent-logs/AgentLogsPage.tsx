import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useC } from '../../../design/ThemeContext'
import type { Colors } from '../../../design/theme'
import { Tabs } from '../../../design/components/Tabs'
import { Pagination } from '../../../design/components/Pagination'
import { api } from '../../../api/client'
import { AgentRunHistory } from '../agents/AgentRunHistory'
import { relTime } from '../../../lib/datetime'

const STATUS_COLOR = (C: Colors): Record<string, string> => ({
  COMPLETED: C.green,
  FAILED:    C.red,
  RUNNING:   C.blue,
})

const STEP_COLOR = (C: Colors): Record<string, string> => ({
  EXECUTED:     C.green,
  APPROVED:     C.green,
  AWAITING_HITL:C.amber,
  REJECTED:     C.red,
  ERROR:        C.red,
})

const STEP_ICON: Record<string, string> = {
  EXECUTED:     '✓',
  APPROVED:     '✓',
  AWAITING_HITL:'⏸',
  REJECTED:     '✗',
  ERROR:        '✗',
}

export function AgentLogsPage() {
  const C = useC()
  const qc = useQueryClient()
  const [tab, setTab] = useState('Execution logs')
  const [agentFilter, setAgentFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [page, setPage] = useState(0)
  const [expandedRunId, setExpandedRunId] = useState<number | null>(null)
  const [rejectingStep, setRejectingStep] = useState<number | null>(null)
  const [rejectReason, setRejectReason] = useState('')
  const [actionLoading, setActionLoading] = useState<number | null>(null)
  const [toast, setToast] = useState('')
  const [toastOk, setToastOk] = useState(true)

  const showToast = (msg: string, ok = true) => {
    setToast(msg); setToastOk(ok); setTimeout(() => setToast(''), 3000)
  }

  const { data: agents = [] } = useQuery({
    queryKey: ['agent-definitions'],
    queryFn: () => api.get('/admin/agents').then(r => r.data),
  })

  const { data: runsPage, isLoading: runsLoading } = useQuery({
    queryKey: ['all-agent-runs', agentFilter, statusFilter, page],
    queryFn: () => api.get('/admin/agents/runs', {
      params: {
        agentId: agentFilter || undefined,
        status:  statusFilter || undefined,
        page, size: 20,
      }
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

  const runs = (runsPage?.content ?? []) as any[]
  const totalPages = runsPage?.totalPages ?? 0
  const pending = pendingHitl as any[]

  return (
    <div style={{ padding: '22px 24px' }}>
      {toast && (
        <div style={{ position: 'fixed', bottom: 24, right: 24, zIndex: 9999, padding: '10px 18px', borderRadius: 8, background: toastOk ? C.greenDeep : C.red, color: '#fff', fontSize: 13, fontWeight: 500, boxShadow: '0 8px 24px rgba(0,0,0,.18)' }}>
          {toast}
        </div>
      )}

      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
        <div>
          <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>Agent logs</div>
          <div style={{ fontSize: 12, color: C.sub, marginTop: 3 }}>Execution history and HITL approval queue</div>
        </div>
        {pending.length > 0 && tab !== 'Pending approvals' && (
          <button
            onClick={() => setTab('Pending approvals')}
            style={{ fontSize: 12, padding: '7px 14px', borderRadius: 7, border: 'none', background: C.amberPale, color: C.amberDeep, cursor: 'pointer', fontWeight: 600 }}
          >
            ⏸ {pending.length} pending approval{pending.length > 1 ? 's' : ''}
          </button>
        )}
      </div>

      <Tabs
        items={[
          'Execution logs',
          `Pending approvals${pending.length > 0 ? ` (${pending.length})` : ''}`,
        ]}
        active={tab}
        onChange={t => setTab(t.replace(/ \(\d+\)$/, ''))}
      />

      {/* ── Execution logs tab ─────────────────────────────────────────────── */}
      {tab === 'Execution logs' && (
        <div style={{ marginTop: 20 }}>
          {/* Filters */}
          <div style={{ display: 'flex', gap: 10, marginBottom: 16 }}>
            <select
              value={agentFilter}
              onChange={e => { setAgentFilter(e.target.value); setPage(0) }}
              style={selStyle(C)}
            >
              <option value="">All agents</option>
              {(agents as any[]).map((a: any) => (
                <option key={a.id} value={a.id}>{a.name}</option>
              ))}
            </select>
            <select
              value={statusFilter}
              onChange={e => { setStatusFilter(e.target.value); setPage(0) }}
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
                    const stColor = STATUS_COLOR(C)[r.status] || C.muted
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
                        <td style={{ padding: '10px 14px', color: C.sub }}>
                          {r.triggeredBy}
                          {r.invocationSource?.startsWith('SLACK_') && (
                            <span title={`Originated from Slack (${r.invocationSource})`} style={{ marginLeft: 6, padding: '1px 6px', background: C.indigoPale, color: C.indigo, borderRadius: 8, fontSize: 9, fontWeight: 700, verticalAlign: 'middle' }}>
                              SLACK
                            </span>
                          )}
                        </td>
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
                        <td style={{ padding: '10px 14px', color: C.muted, fontSize: 11 }}>
                          {fmtTime(r.startedAt)}
                        </td>
                        <td style={{ padding: '10px 14px', color: C.indigo, fontSize: 11 }}>
                          {isExpanded ? '▲' : '▶'}
                        </td>
                      </tr>,
                      isExpanded && (
                        <tr key={`steps-${r.id}`}>
                          <td colSpan={8} style={{ padding: '12px 16px', background: C.canvas, borderTop: `1px solid ${C.border}` }}>
                            <StepDetail
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
                      )
                    ]
                  })}
                </tbody>
              </table>
              <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
            </div>
          )}
        </div>
      )}

      {/* ── Pending approvals tab ──────────────────────────────────────────── */}
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
                  <div key={item.id} style={{ background: C.white, border: `2px solid ${C.amberPale}`, borderRadius: 12, overflow: 'hidden' }}>
                    {/* Header */}
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px 18px', background: C.amberPale, borderBottom: `1px solid ${C.amberPale}` }}>
                      <div>
                        <div style={{ fontSize: 13, fontWeight: 700, color: C.text }}>
                          ⏸ {item.tool}
                        </div>
                        <div style={{ fontSize: 11, color: C.sub, marginTop: 2 }}>
                          {item.agentName ?? `Agent #${item.agentId}`} · Run #{item.runId} · {fmtTime(item.calledAt)}
                        </div>
                      </div>
                      <span style={{ fontSize: 10, padding: '3px 8px', borderRadius: 10, background: C.amberPale, color: C.amberDeep, fontWeight: 600 }}>
                        AWAITING APPROVAL
                      </span>
                    </div>

                    {/* Args preview */}
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
                            style={{ width: '100%', fontSize: 12, padding: '8px 10px', borderRadius: 6, border: `1px solid ${C.redPale}`, outline: 'none', resize: 'vertical', boxSizing: 'border-box' as const }}
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
    </div>
  )
}

// ── Step detail (used in Execution logs expansion) ─────────────────────────────

function StepDetail({ steps, runId, rejectingStep, rejectReason, actionLoading,
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
          <div key={s.id} style={{ borderRadius: 6, border: `1px solid ${isHitl ? C.amberPale : C.border}`, overflow: 'hidden' }}>
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
              <div style={{ display: 'flex', gap: 8, padding: '8px 10px', background: C.amberPale, borderTop: `1px solid ${C.amberPale}` }}>
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
              <div style={{ padding: '8px 10px', background: C.redPale, borderTop: `1px solid ${C.redPale}` }}>
                <textarea value={rejectReason} onChange={e => onRejectReasonChange(e.target.value)}
                  placeholder="Reason for rejection (required)" rows={2}
                  style={{ width: '100%', fontSize: 12, padding: '6px 8px', borderRadius: 5, border: `1px solid ${C.redPale}`, outline: 'none', resize: 'vertical', boxSizing: 'border-box' as const }} />
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

function selStyle(C: any) {
  return { fontSize: 12, padding: '7px 10px', borderRadius: 7, border: `1px solid ${C.border}`, background: C.white, color: C.text, minWidth: 160 }
}

function fmtTime(iso: string) {
  return relTime(iso)
}
