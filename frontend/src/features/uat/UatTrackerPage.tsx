import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useC } from '../../design/ThemeContext'
import { Badge } from '../../design/components/Badge'
import { THead } from '../../design/components/THead'
import { Tabs } from '../../design/components/Tabs'
import { Pagination } from '../../design/components/Pagination'
import { Modal } from '../../design/components/Modal'
import { Select } from '../../design/components/Select'
import { ErrorState } from '../../design/components/PageState'
import { api } from '../../api/client'

const STATUS_LEVEL: Record<string, string> = { SIGNED_OFF: 'healthy', REJECTED: 'critical', PENDING: 'watch', WAIVED: 'info' }
const STATUS_LABEL: Record<string, string> = { SIGNED_OFF: 'Signed off', REJECTED: 'Rejected', PENDING: 'Pending', WAIVED: 'Waived' }

export function UatTrackerPage() {
  const C = useC()
  const [tab, setTab] = useState('Cycles')
  const [page, setPage] = useState(0)
  const [clientId, setClientId] = useState<string>('')
  const [signOffCycle, setSignOffCycle] = useState<any | null>(null)
  const [signOffStatus, setSignOffStatus] = useState('SIGNED_OFF')
  const [signOffNote, setSignOffNote] = useState('')

  const { data: clientList = [] } = useQuery({
    queryKey: ['clients-list'],
    queryFn: () => api.get('/clients').then(r => r.data as any[]),
  })
  const clientOptions = [
    { v: '', l: 'All clients' },
    ...(clientList as any[]).map((c: any) => ({ v: String(c.id), l: c.name })),
  ]

  const { data: cyclesPage, isLoading, error, refetch } = useQuery({
    queryKey: ['uat-cycles', page, clientId],
    queryFn: () => api.get('/uat/cycles', {
      params: { page, size: 20, clientId: clientId || undefined }
    }).then(r => r.data)
  })
  const cycles: any[] = cyclesPage?.content ?? []
  const totalPages: number = cyclesPage?.totalPages ?? 1

  const { data: signOffSummary } = useQuery({
    queryKey: ['uat-signoff-status', clientId],
    queryFn: () => api.get('/uat/sign-off-status', {
      params: { clientId: clientId || undefined }
    }).then(r => r.data)
  })
  const signed   = signOffSummary?.signedOff ?? 0
  const rejected = signOffSummary?.rejected  ?? 0
  const pending  = signOffSummary?.pending   ?? 0
  const total    = signOffSummary?.total     ?? 0

  if (isLoading) return <div style={{ padding: 40, color: C.sub }}>Loading…</div>
  if (error) return <ErrorState error={error} />

  const doSignOff = async () => {
    if (!signOffCycle) return
    await api.post(`/uat/cycles/${signOffCycle.cycleId}/sign-off`, {
      status: signOffStatus, signedOffBy: 'PJM', notes: signOffNote
    })
    setSignOffCycle(null); setSignOffNote(''); refetch()
  }

  return (
    <div style={{ padding: '22px 24px' }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: 18 }}>
        <div>
          <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>UAT Tracker</div>
          <div style={{ fontSize: 12, color: C.sub, marginTop: 3 }}>Cycle management · Client sign-off tracking</div>
        </div>
        <Select
          options={clientOptions}
          value={clientId}
          onChange={e => { setClientId(e.target.value); setPage(0) }}
          style={{ width: 175 }}
        />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 10, marginBottom: 20 }}>
        {([['In UAT', total, C.indigo], ['Signed off', signed, C.green], ['Pending', pending, C.amber], ['Rejected', rejected, C.red]] as const).map(([l, v, c]) => (
          <div key={String(l)} style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 10, padding: '12px 14px', textAlign: 'center' }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: c }}>{v}</div>
            <div style={{ fontSize: 11, color: C.sub, marginTop: 2 }}>{l}</div>
          </div>
        ))}
      </div>

      <Tabs items={['Cycles', 'Sign-off status', 'Environment health']} active={tab} onChange={(t) => { setTab(t); setPage(0) }} />

      {tab === 'Cycles' && (
        <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden' }}>
          <table style={{ width: '100%', fontSize: 12 }}>
            <THead cols={['Issue', 'Summary', 'Cycle', 'Started', 'Env', 'Sign-off', '']} />
            <tbody>
              {cycles.length === 0 && (
                <tr><td colSpan={7} style={{ padding: '40px 12px', textAlign: 'center', color: C.muted }}>No UAT cycles yet.</td></tr>
              )}
              {cycles.map((c: any, i: number) => (
                <tr key={c.cycleId} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none', background: C.white }}>
                  <td style={{ padding: '10px 12px', color: C.indigo, fontWeight: 600 }}>{c.issueKey}</td>
                  <td style={{ padding: '10px 12px', color: C.text, maxWidth: 200 }}>{c.crSummary}</td>
                  <td style={{ padding: '10px 12px' }}>
                    <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 4, fontWeight: 600, background: c.cycleNumber >= 3 ? C.redPale : C.canvas, color: c.cycleNumber >= 3 ? C.redDeep : C.sub }}>
                      Cycle {c.cycleNumber}
                    </span>
                  </td>
                  <td style={{ padding: '10px 12px', color: C.sub }}>{c.startedAt ? String(c.startedAt).slice(0, 10) : '—'}</td>
                  <td style={{ padding: '10px 12px', fontFamily: 'monospace', fontSize: 11, color: C.sub }}>{c.envSnapshot || '—'}</td>
                  <td style={{ padding: '10px 12px' }}>
                    <Badge level={STATUS_LEVEL[c.signOffStatus] || 'neutral'} label={STATUS_LABEL[c.signOffStatus] || c.signOffStatus} />
                  </td>
                  <td style={{ padding: '10px 12px' }}>
                    {c.signOffStatus === 'PENDING' && (
                      <button onClick={() => setSignOffCycle(c)}
                        style={{ fontSize: 11, padding: '3px 9px', borderRadius: 5, cursor: 'pointer', border: 'none', background: C.indigo, color: '#fff', fontWeight: 500 }}>
                        Sign off
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
        </div>
      )}

      {tab === 'Sign-off status' && (
        cycles.length === 0
          ? <div style={{ padding: '40px 0', color: C.muted, fontSize: 13, textAlign: 'center' }}>No UAT cycles yet.</div>
          : <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              {cycles.map((c: any) => (
                <div key={c.cycleId} style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: '14px 16px', borderLeft: `3px solid ${c.signOffStatus === 'SIGNED_OFF' ? C.green : c.signOffStatus === 'REJECTED' ? C.red : C.amber}` }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 8 }}>
                    <div>
                      <div style={{ fontSize: 13, fontWeight: 600, color: C.text }}>{c.issueKey}</div>
                      <div style={{ fontSize: 11, color: C.sub, marginTop: 2 }}>{c.client} · Cycle {c.cycleNumber}</div>
                    </div>
                    <Badge level={STATUS_LEVEL[c.signOffStatus] || 'neutral'} label={STATUS_LABEL[c.signOffStatus] || c.signOffStatus} />
                  </div>
                  <div style={{ fontSize: 12, color: C.text }}>{c.crSummary}</div>
                </div>
              ))}
            </div>
      )}

      {tab === 'Environment health' && (
        <div style={{ padding: '40px 0', color: C.muted, fontSize: 13, textAlign: 'center' }}>
          Environment health data not yet available. Configure UAT environments in Admin settings.
        </div>
      )}

      {signOffCycle && (
        <Modal title={`Sign off — ${signOffCycle.issueKey}`} onClose={() => setSignOffCycle(null)}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div style={{ padding: '10px 12px', background: C.canvas, borderRadius: 8, fontSize: 12, color: C.sub }}>
              {signOffCycle.crSummary} · Cycle {signOffCycle.cycleNumber}
            </div>
            <div>
              <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Decision</div>
              <Select options={[{ v: 'SIGNED_OFF', l: 'Sign off — UAT passed' }, { v: 'REJECTED', l: 'Reject — issues found' }, { v: 'WAIVED', l: 'Waive — approved with caveats' }]}
                value={signOffStatus} onChange={e => setSignOffStatus(e.target.value)} style={{ width: '100%' }} />
            </div>
            <div>
              <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Notes</div>
              <textarea value={signOffNote} onChange={e => setSignOffNote(e.target.value)}
                placeholder="Client feedback or rejection reasons…" rows={3}
                style={{ fontSize: 12, padding: '7px 10px', borderRadius: 7, border: `1px solid ${C.border}`, width: '100%', outline: 'none', resize: 'vertical', fontFamily: 'inherit' }} />
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <button onClick={() => setSignOffCycle(null)}
                style={{ flex: 1, padding: '9px', borderRadius: 7, border: `1px solid ${C.border}`, background: C.canvas, fontSize: 13, cursor: 'pointer', color: C.sub }}>
                Cancel
              </button>
              <button onClick={doSignOff}
                style={{ flex: 1, padding: '9px', borderRadius: 7, border: 'none', background: signOffStatus === 'REJECTED' ? C.red : C.green, color: '#fff', fontSize: 13, fontWeight: 600, cursor: 'pointer' }}>
                {signOffStatus === 'SIGNED_OFF' ? '✓ Sign off' : signOffStatus === 'REJECTED' ? '✗ Reject' : 'Waive'}
              </button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}
