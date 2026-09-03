import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useC } from '../../../design/ThemeContext'
import type { Colors } from '../../../design/theme'
import { api } from '../../../api/client'
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

interface Props {
  agentId: number
  agentName?: string
  /** If true, shows approve/reject buttons on AWAITING_HITL steps */
  allowHitlActions?: boolean
  onHitlActioned?: () => void
}

export function AgentRunHistory({ agentId, agentName, allowHitlActions, onHitlActioned }: Props) {
  const C = useC()
  const qc = useQueryClient()
  const [expandedRunId, setExpandedRunId] = useState<number | null>(null)
  const [rejectingStep, setRejectingStep] = useState<number | null>(null)
  const [rejectReason, setRejectReason] = useState('')
  const [actionLoading, setActionLoading] = useState<number | null>(null)
  const [toast, setToast] = useState('')

  const showToast = (msg: string) => { setToast(msg); setTimeout(() => setToast(''), 3000) }

  const { data, isLoading } = useQuery({
    queryKey: ['agent-runs', agentId],
    queryFn: () => api.get(`/admin/agents/${agentId}/runs?size=10`).then(r => r.data),
  })

  const { data: steps = [] } = useQuery({
    queryKey: ['run-steps', expandedRunId],
    queryFn: () => api.get(`/admin/agents/runs/${expandedRunId}/steps`).then(r => r.data),
    enabled: expandedRunId !== null,
  })

  const approve = async (runId: number, stepId: number) => {
    setActionLoading(stepId)
    try {
      await api.post(`/admin/agents/runs/${runId}/steps/${stepId}/approve`, {})
      qc.invalidateQueries({ queryKey: ['run-steps', expandedRunId] })
      qc.invalidateQueries({ queryKey: ['agent-runs', agentId] })
      qc.invalidateQueries({ queryKey: ['pending-hitl'] })
      showToast('Tool approved and executed')
      onHitlActioned?.()
    } catch (e: any) {
      showToast(e.response?.data?.error ?? 'Approval failed')
    } finally { setActionLoading(null) }
  }

  const reject = async (runId: number, stepId: number) => {
    if (!rejectReason.trim()) return
    setActionLoading(stepId)
    try {
      await api.post(`/admin/agents/runs/${runId}/steps/${stepId}/reject`, { reason: rejectReason })
      qc.invalidateQueries({ queryKey: ['run-steps', expandedRunId] })
      qc.invalidateQueries({ queryKey: ['agent-runs', agentId] })
      qc.invalidateQueries({ queryKey: ['pending-hitl'] })
      setRejectingStep(null); setRejectReason('')
      showToast('Step rejected')
      onHitlActioned?.()
    } catch (e: any) {
      showToast(e.response?.data?.error ?? 'Rejection failed')
    } finally { setActionLoading(null) }
  }

  const runs = (data?.content ?? []) as any[]

  if (isLoading) return (
    <div style={{ padding: '10px 0', fontSize: 12, color: C.muted }}>Loading runs…</div>
  )

  if (runs.length === 0) return (
    <div style={{ padding: '10px 0', fontSize: 12, color: C.muted }}>No runs yet</div>
  )

  return (
    <div style={{ marginTop: 8 }}>
      {toast && (
        <div style={{ position: 'fixed', bottom: 24, right: 24, zIndex: 9999, padding: '10px 18px', borderRadius: 8, background: C.text, color: '#fff', fontSize: 13, fontWeight: 500 }}>
          {toast}
        </div>
      )}

      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
        <thead>
          <tr style={{ background: C.canvas }}>
            {['Run #', 'Trigger', 'Status', '⏸', 'Duration', 'Started', ''].map(h => (
              <th key={h} style={{ padding: '6px 10px', textAlign: 'left', fontSize: 10, fontWeight: 600, color: C.sub, letterSpacing: 0.4, borderBottom: `1px solid ${C.border}` }}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {runs.map((r: any, i: number) => {
            const isExpanded = expandedRunId === r.id
            const stColor = STATUS_COLOR(C)[r.status] || C.muted
            return [
              <tr
                key={r.id}
                onClick={() => setExpandedRunId(isExpanded ? null : r.id)}
                style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none', cursor: 'pointer', background: isExpanded ? C.indigoPale : C.white }}
              >
                <td style={{ padding: '8px 10px', color: C.sub, fontFamily: 'monospace', fontSize: 11 }}>#{r.id}</td>
                <td style={{ padding: '8px 10px', color: C.sub }}>{r.triggeredBy}</td>
                <td style={{ padding: '8px 10px' }}>
                  <span style={{ color: stColor, fontWeight: 600, fontSize: 11 }}>{r.status}</span>
                </td>
                <td style={{ padding: '8px 10px' }}>
                  {r.pendingHitl > 0 && (
                    <span style={{ background: C.amberPale, color: C.amberDeep, padding: '2px 6px', borderRadius: 4, fontSize: 10, fontWeight: 600 }}>
                      {r.pendingHitl}
                    </span>
                  )}
                </td>
                <td style={{ padding: '8px 10px', color: C.muted, fontSize: 11 }}>{r.durationMs ? `${r.durationMs}ms` : '—'}</td>
                <td style={{ padding: '8px 10px', color: C.muted, fontSize: 11 }}>{fmtTime(r.startedAt)}</td>
                <td style={{ padding: '8px 10px', color: C.indigo, fontSize: 11 }}>{isExpanded ? '▲' : '▶'}</td>
              </tr>,
              isExpanded && (
                <tr key={`steps-${r.id}`}>
                  <td colSpan={7} style={{ padding: '10px 14px', background: C.canvas, borderTop: `1px solid ${C.border}` }}>
                    <StepsList
                      steps={steps as any[]}
                      runId={r.id}
                      allowHitlActions={allowHitlActions}
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
    </div>
  )
}

function StepsList({ steps, runId, allowHitlActions, rejectingStep, rejectReason, actionLoading,
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

        let resultPreview = ''
        if (s.result) {
          try {
            const parsed = JSON.parse(s.result)
            resultPreview = Object.entries(parsed).slice(0, 3)
              .map(([k, v]) => `${k}: ${v}`).join(' · ')
          } catch { resultPreview = String(s.result).slice(0, 100) }
        }

        const isRejecting = rejectingStep === s.id

        return (
          <div key={s.id} style={{ borderRadius: 6, overflow: 'hidden', border: `1px solid ${isHitl ? C.amber : C.border}` }}>
            {/* Step header */}
            <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8, padding: '7px 10px', background: isHitl ? C.amberPale : C.white }}>
              <span style={{ fontSize: 13, color, flexShrink: 0, marginTop: 1 }}>{icon}</span>
              <div style={{ flex: 1, minWidth: 0 }}>
                <span style={{ fontSize: 11, fontWeight: 700, color, fontFamily: 'monospace' }}>{s.tool}</span>
                {isHitl && <span style={{ fontSize: 11, color: C.amberDeep, marginLeft: 8, fontWeight: 500 }}>— awaiting approval before this tool can execute</span>}
                {s.status === 'REJECTED' && s.hitlNote && <span style={{ fontSize: 11, color: C.red, marginLeft: 8 }}>Rejected: {s.hitlNote}</span>}
                {resultPreview && !isHitl && <span style={{ fontSize: 10, color: C.sub, marginLeft: 8 }}>{resultPreview}</span>}
              </div>
              <span style={{ fontSize: 10, color: C.muted, flexShrink: 0 }}>{fmtTime(s.calledAt)}</span>
            </div>

            {/* HITL action row */}
            {isHitl && allowHitlActions && !isRejecting && (
              <div style={{ display: 'flex', gap: 8, padding: '8px 10px', background: C.amberPale, borderTop: `1px solid ${C.amber}` }}>
                {s.args && s.args !== '{}' && (
                  <div style={{ flex: 1, fontSize: 11, color: C.sub, fontFamily: 'monospace', background: C.canvas, padding: '4px 8px', borderRadius: 4 }}>
                    Args: {s.args}
                  </div>
                )}
                <button
                  onClick={() => onApprove(runId, s.id)}
                  disabled={actionLoading === s.id}
                  style={{ fontSize: 11, padding: '4px 12px', borderRadius: 5, border: 'none', background: C.green, color: '#fff', cursor: 'pointer', fontWeight: 600, flexShrink: 0 }}
                >
                  {actionLoading === s.id ? '⟳' : '✓ Approve'}
                </button>
                <button
                  onClick={() => onStartReject(s.id)}
                  style={{ fontSize: 11, padding: '4px 12px', borderRadius: 5, border: `1px solid ${C.red}`, background: 'transparent', color: C.red, cursor: 'pointer', fontWeight: 600, flexShrink: 0 }}
                >
                  ✗ Reject
                </button>
              </div>
            )}

            {/* Rejection form */}
            {isRejecting && (
              <div style={{ padding: '8px 10px', background: C.redPale, borderTop: `1px solid ${C.red}` }}>
                <div style={{ fontSize: 11, fontWeight: 600, color: C.red, marginBottom: 6 }}>Reason for rejection (required)</div>
                <textarea
                  value={rejectReason}
                  onChange={e => onRejectReasonChange(e.target.value)}
                  placeholder="e.g. Wrong recipient — let me verify first"
                  rows={2}
                  style={{ width: '100%', fontSize: 12, padding: '6px 8px', borderRadius: 5, border: `1px solid ${C.red}`, outline: 'none', resize: 'vertical', boxSizing: 'border-box' }}
                />
                <div style={{ display: 'flex', gap: 6, marginTop: 6 }}>
                  <button
                    onClick={() => onConfirmReject(runId, s.id)}
                    disabled={!rejectReason.trim() || actionLoading === s.id}
                    style={{ fontSize: 11, padding: '4px 12px', borderRadius: 5, border: 'none', background: C.red, color: '#fff', cursor: 'pointer', fontWeight: 600 }}
                  >
                    {actionLoading === s.id ? '⟳' : 'Confirm rejection'}
                  </button>
                  <button
                    onClick={onCancelReject}
                    style={{ fontSize: 11, padding: '4px 10px', borderRadius: 5, border: `1px solid ${C.border}`, background: 'transparent', color: C.sub, cursor: 'pointer' }}
                  >
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

function fmtTime(iso: string) {
  return relTime(iso)
}
