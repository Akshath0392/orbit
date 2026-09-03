import { ErrorState } from '../../design/components/PageState'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useC } from '../../design/ThemeContext'
import { Badge } from '../../design/components/Badge'
import { THead } from '../../design/components/THead'
import { Pagination } from '../../design/components/Pagination'
import { CostSummaryBar } from '../../design/components/CostSummaryBar'
import { agentColorMap } from '../../design/agentColorMap'
import { api } from '../../api/client'
import { useAgents } from '../../api/hooks'

const outcomeLvl: Record<string, string> = { APPROVED: 'healthy', EDITED: 'watch', REJECTED: 'risk' }
const OUTCOMES = ['APPROVED', 'EDITED', 'REJECTED']

export function AgentAuditPage() {
  const C = useC()
  const [selId, setSelId] = useState<number | null>(null)
  const [page, setPage] = useState(0)
  const [agentFilter, setAgentFilter] = useState('')
  const [outcomeFilter, setOutcomeFilter] = useState('')

  const { data: agents = [] } = useAgents()

  const { data: decisionsPage, isLoading, error } = useQuery({
    queryKey: ['decisions', page, agentFilter, outcomeFilter],
    queryFn: () => api.get('/agent/decisions', { params: {
      page, size: 10,
      ...(agentFilter   ? { agentName: agentFilter } : {}),
      ...(outcomeFilter ? { outcome: outcomeFilter } : {}),
    } }).then(r => r.data)
  })
  const decisions = decisionsPage?.content ?? []
  const totalPages = decisionsPage?.totalPages ?? 1
  const selD = decisions.find((d: any) => d.id === selId)

  const { data: costSummary } = useQuery({
    queryKey: ['cost-summary'],
    queryFn: () => api.get('/agent/cost-summary?period=week').then(r => r.data)
  })

  const totalTokens = costSummary?.tokensTotal ?? 0
  const estimatedCostUsd = costSummary?.estimatedCostUsd ?? 0  // server is authoritative; no client fallback

  const approvedCount = decisions.filter((d: any) => d.outcome === 'APPROVED').length
  const editedCount = decisions.filter((d: any) => d.outcome === 'EDITED').length
  const rejectedCount = decisions.filter((d: any) => d.outcome === 'REJECTED').length

  if (isLoading) return <div style={{ padding: 40, color: C.sub }}>Loading…</div>
  if (error) return <ErrorState error={error} />

  return (
    <div style={{ padding: '22px 24px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 18 }}>
        <div>
          <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>Agent audit log</div>
          <div style={{ fontSize: 12, color: C.sub, marginTop: 3 }}>All agent proposals · HITL decisions · token usage</div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <select
            value={agentFilter}
            onChange={e => { setAgentFilter(e.target.value); setPage(0) }}
            style={{ width: 220, fontSize: 12, padding: '6px 10px', borderRadius: 7, border: `1px solid ${C.border}`, background: C.white, color: C.text }}
          >
            <option value="">All agents</option>
            {agents.map(a => <option key={a.id} value={a.name}>{a.name}</option>)}
          </select>
          <select
            value={outcomeFilter}
            onChange={e => { setOutcomeFilter(e.target.value); setPage(0) }}
            style={{ width: 140, fontSize: 12, padding: '6px 10px', borderRadius: 7, border: `1px solid ${C.border}`, background: C.white, color: C.text }}
          >
            <option value="">All outcomes</option>
            {OUTCOMES.map(o => <option key={o} value={o}>{o}</option>)}
          </select>
        </div>
      </div>

      <div style={{ marginBottom: 16 }}>
        <CostSummaryBar tokens={totalTokens} costUsd={estimatedCostUsd} period="this week" />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 10, marginBottom: 20 }}>
        {[
          ['Total proposals', decisions.length, C.text],
          ['Approved', approvedCount, C.green],
          ['Edited', editedCount, C.amber],
          ['Rejected', rejectedCount, C.red],
        ].map(([l, v, c]) => (
          <div key={String(l)} style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 10, padding: '12px 14px', textAlign: 'center' }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: String(c) }}>{v}</div>
            <div style={{ fontSize: 11, color: C.sub, marginTop: 2 }}>{l}</div>
          </div>
        ))}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 360px', gap: 12 }}>
        <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden' }}>
          <table style={{ width: '100%', fontSize: 12 }}>
            <THead cols={['Agent', 'Trigger', 'Proposal', 'Outcome', 'By', 'When']} />
            <tbody>
              {decisions.length === 0 && (
                <tr>
                  <td colSpan={6} style={{ padding: '20px', textAlign: 'center', color: C.muted }}>No data yet</td>
                </tr>
              )}
              {decisions.map((d: any, i: number) => (
                <tr
                  key={d.id}
                  onClick={() => setSelId(selId === d.id ? null : d.id)}
                  style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none', cursor: 'pointer', background: selId === d.id ? C.indigoPale : C.white }}
                >
                  <td style={{ padding: '10px 12px' }}>
                    <span style={{
                      fontSize: 10, padding: '2px 7px', borderRadius: 4, fontWeight: 600,
                      background: (agentColorMap[d.agent] || C.indigo) + '18',
                      color: agentColorMap[d.agent] || C.indigo
                    }}>
                      {d.agent.replace('Agent', '').replace('Intelligence', 'Intel.')}
                    </span>
                  </td>
                  <td style={{ padding: '10px 12px', color: C.sub, fontSize: 11, maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={String(d.trigger ?? '')}>{d.trigger}</td>
                  <td style={{ padding: '10px 12px', color: C.text, maxWidth: 280, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={String(d.proposal ?? '')}>{d.proposal}</td>
                  <td style={{ padding: '10px 12px' }}><Badge level={outcomeLvl[d.outcome]} label={d.outcome} /></td>
                  <td style={{ padding: '10px 12px', color: C.sub }}>{d.by}</td>
                  <td style={{ padding: '10px 12px', color: C.muted, whiteSpace: 'nowrap', fontSize: 11 }}>{d.at}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
        </div>

        {selD ? (
          <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: '16px 18px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
              <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 4, fontWeight: 600, background: (agentColorMap[selD.agent] || C.indigo) + '18', color: agentColorMap[selD.agent] || C.indigo }}>{selD.agent}</span>
              <Badge level={outcomeLvl[selD.outcome]} label={selD.outcome} />
            </div>
            <div style={{ fontSize: 11, fontWeight: 500, color: C.sub, marginBottom: 4 }}>Trigger event</div>
            <div style={{ fontSize: 12, color: C.text, marginBottom: 12, padding: '8px 10px', background: C.canvas, borderRadius: 7, lineHeight: 1.5 }}>{selD.trigger}</div>
            <div style={{ fontSize: 11, fontWeight: 500, color: C.sub, marginBottom: 4 }}>Full proposal</div>
            <div style={{ fontSize: 12, color: C.text, lineHeight: 1.6, marginBottom: 12, padding: '8px 10px', background: C.canvas, borderRadius: 7 }}>{selD.proposal}</div>
            {selD.outcomeNote && (
              <>
                <div style={{ fontSize: 11, fontWeight: 500, color: C.sub, marginBottom: 4 }}>Outcome note</div>
                <div style={{ fontSize: 12, color: C.text, lineHeight: 1.5, marginBottom: 12, padding: '8px 10px', background: C.redPale, borderRadius: 7, borderLeft: `3px solid ${C.red}` }}>{selD.outcomeNote}</div>
              </>
            )}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 10 }}>
              <div style={{ background: C.canvas, borderRadius: 8, padding: '8px 12px' }}>
                <div style={{ fontSize: 10, color: C.sub }}>Decided by</div>
                <div style={{ fontSize: 13, fontWeight: 600, color: C.text, marginTop: 2 }}>{selD.by}</div>
              </div>
              <div style={{ background: C.canvas, borderRadius: 8, padding: '8px 12px' }}>
                <div style={{ fontSize: 10, color: C.sub }}>Tokens used</div>
                <div style={{ fontSize: 13, fontWeight: 600, color: C.text, marginTop: 2 }}>{(selD.tokensUsed ?? selD.tokens ?? 0).toLocaleString()}</div>
              </div>
            </div>
            <div style={{ fontSize: 11, color: C.muted }}>Logged at {selD.at}</div>
          </div>
        ) : (
          <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: 200, color: C.muted, fontSize: 13 }}>
            Select a decision to inspect
          </div>
        )}
      </div>
    </div>
  )
}
