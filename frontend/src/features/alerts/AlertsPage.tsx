// Reference implementation of the composite component strategy — see
// docs/design/frontend-component-strategy.md. Page-level layout comes from
// PageHeader/StatGrid/FilterBar/TableWrap, status colours from StatusPill,
// and the two-column layout stacks on mobile via useIsMobile.
import { LoadingState, ErrorState } from '../../design/components/PageState'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useC } from '../../design/ThemeContext'
import { useIsMobile } from '../../design/useBreakpoint'
import { PageHeader } from '../../design/components/PageHeader'
import { FilterBar } from '../../design/components/FilterBar'
import { StatGrid } from '../../design/components/StatGrid'
import { StatusPill } from '../../design/components/StatusPill'
import { TableWrap } from '../../design/components/TableWrap'
import { Select } from '../../design/components/Select'
import { Btn } from '../../design/components/Btn'
import { Input } from '../../design/components/Input'
import { THead } from '../../design/components/THead'
import { Pagination } from '../../design/components/Pagination'
import { Feature } from '../../app/featureFlags'
import { api } from '../../api/client'
import { useAlertTypes } from '../../api/hooks'

export function AlertsPage() {
  const C = useC()
  const navigate = useNavigate()
  const mobile = useIsMobile()
  const sevCol: Record<string, string> = { critical: C.red, risk: C.amber, info: C.indigo }
  const [selId, setSelId] = useState<number | null>(null)
  const [page, setPage] = useState(0)
  const [sevFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [typeFilter, setTypeFilter] = useState('')
  const [assignForm, setAssignForm] = useState({ owner: '', date: '' })
  const [showAssign, setShowAssign] = useState(false)
  const [toast, setToast] = useState('')
  const [mitigation, setMitigation] = useState('')

  const { data: alertTypes = [] } = useAlertTypes()

  const { data: alertPage, isLoading, error, refetch } = useQuery({
    queryKey: ['alerts', page, sevFilter, statusFilter, typeFilter],
    queryFn: () => api.get('/alerts', {
      params: {
        page, size: 20,
        severity: sevFilter    || undefined,
        status:   statusFilter || undefined,
        type:     typeFilter   || undefined,
      }
    }).then(r => r.data)
  })

  const showToast = (msg: string, ms = 2500) => { setToast(msg); setTimeout(() => setToast(''), ms) }
  const saveNote = (id: number) => {
    if (!mitigation.trim()) return
    api.post(`/alerts/${id}/note`, { note: mitigation })
      .then(() => { showToast('Mitigation note saved'); setMitigation('') })
      .catch(() => showToast('Failed to save note'))
  }
  const alertList = alertPage?.content ?? []
  const totalPages = alertPage?.totalPages ?? 1
  const selAlert = alertList.find((a: any) => a.id === selId)

  const ack = (id: number) => api.post(`/alerts/${id}/acknowledge`).then(() => refetch())
  const dismiss = (id: number, reason: string) => api.post(`/alerts/${id}/dismiss`, { reason }).then(() => { refetch(); setSelId(null) })
  const assign = (id: number, ownerId: string, followUpDate: string) =>
    api.post(`/alerts/${id}/assign`, { ownerId, followUpDate }).then(() => refetch())

  const criticalOpen = alertList.filter((a: any) => a.sev === 'critical' && a.status === 'OPEN').length
  const highOpen = alertList.filter((a: any) => a.sev === 'risk' && a.status === 'OPEN').length
  const acknowledged = alertList.filter((a: any) => a.status === 'ACKNOWLEDGED').length
  const dismissed = alertList.filter((a: any) => a.status === 'DISMISSED').length

  if (isLoading) return <LoadingState />
  if (error) return <ErrorState error={error} />

  return (
    <div style={{ padding: mobile ? '16px 14px' : '22px 24px' }}>
      {toast && (
        <div style={{
          position: 'fixed', bottom: 24, right: 24, zIndex: 9999,
          padding: '10px 18px', borderRadius: 8, background: C.text,
          color: '#fff', fontSize: 13, fontWeight: 500, boxShadow: '0 8px 24px rgba(0,0,0,0.18)'
        }}>
          {toast}
        </div>
      )}

      <PageHeader
        title="Alert center"
        subtitle="Cross-project risk signals · AI-detected"
        actions={
          <FilterBar>
            <Select
              value={statusFilter}
              onChange={e => { setStatusFilter(e.target.value); setPage(0) }}
              options={[{ v: '', l: 'All status' }, ...['OPEN','ACKNOWLEDGED','MITIGATED','DISMISSED'].map(s => ({ v: s, l: s }))]}
              style={{ width: 140 }}
            />
            <Select
              value={typeFilter}
              onChange={e => { setTypeFilter(e.target.value); setPage(0) }}
              options={[{ v: '', l: 'All types' }, ...alertTypes.map((t: string) => ({ v: t, l: t }))]}
              style={{ width: 160 }}
            />
          </FilterBar>
        }
      />

      <Feature flag="section.alerts.stats">
        <StatGrid items={[
          { label: 'Critical open', value: criticalOpen, color: C.red },
          { label: 'High open', value: highOpen, color: C.amber },
          { label: 'Acknowledged', value: acknowledged, color: C.indigo },
          { label: 'Dismissed', value: dismissed, color: C.muted },
        ]} />
      </Feature>

      <div style={{ display: 'grid', gridTemplateColumns: mobile ? '1fr' : '1fr 340px', gap: 12 }}>
        <TableWrap footer={<Pagination page={page} totalPages={totalPages} onPageChange={setPage} />}>
          <table style={{ width: '100%', fontSize: 12 }}>
            <THead cols={['', 'Alert', 'Phase', 'Client', 'Status', 'Detected', '']} />
            <tbody>
              {alertList.length === 0 && (
                <tr>
                  <td colSpan={7} style={{ padding: '20px', textAlign: 'center', color: C.muted }}>No data yet</td>
                </tr>
              )}
              {alertList.map((a: any, i: number) => (
                <tr
                  key={a.id}
                  onClick={() => setSelId(selId === a.id ? null : a.id)}
                  style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none', cursor: 'pointer', background: selId === a.id ? C.indigoPale : C.white }}
                >
                  <td style={{ padding: '10px 10px', width: 10 }}>
                    <span style={{ display: 'block', width: 8, height: 8, borderRadius: '50%', background: sevCol[a.sev] || C.muted }} />
                  </td>
                  <td style={{ padding: '10px 12px' }}>
                    <div style={{ fontSize: 12, fontWeight: 600, color: C.text, marginBottom: 2 }}>{a.title}</div>
                    <div style={{ fontSize: 11, color: C.sub }}>{a.type} · {a.agent}</div>
                  </td>
                  <td style={{ padding: '10px 12px' }}>
                    {a.phase
                      ? <StatusPill status="RUNNING" label={a.phase} />
                      : <span style={{ color: C.muted }}>—</span>
                    }
                    {a.daysOverdue > 0 && <span style={{ marginLeft: 6, color: C.red, fontSize: 10, fontWeight: 600 }}>{a.daysOverdue}d late</span>}
                  </td>
                  <td style={{ padding: '10px 12px', color: C.sub, whiteSpace: 'nowrap' }}>{a.client}</td>
                  <td style={{ padding: '10px 12px' }}><StatusPill status={a.status} /></td>
                  <td style={{ padding: '10px 12px', color: C.muted, whiteSpace: 'nowrap', fontSize: 11 }}>{a.time}</td>
                  <td style={{ padding: '10px 12px' }}>
                    <Btn onClick={() => setSelId(selId === a.id ? null : a.id)}>Actions</Btn>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </TableWrap>

        {selAlert ? (
          <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: '16px 18px' }}>
            <div style={{ fontSize: 14, fontWeight: 600, color: C.text, marginBottom: 6 }}>{selAlert.title}</div>
            <div style={{ display: 'flex', gap: 6, marginBottom: 12 }}>
              <StatusPill status={selAlert.sev} label={selAlert.sev.charAt(0).toUpperCase() + selAlert.sev.slice(1)} />
              <StatusPill status={selAlert.status} />
            </div>
            <div style={{ fontSize: 12, color: C.sub, lineHeight: 1.6, marginBottom: 14, padding: '10px 12px', background: C.canvas, borderRadius: 8 }}>{selAlert.detail}</div>
            <div style={{ fontSize: 11, color: C.muted, marginBottom: 12 }}>Detected by <strong>{selAlert.agent}</strong> · {selAlert.time}</div>
            {selAlert.owner && (
              <div style={{ fontSize: 12, color: C.sub, marginBottom: 12 }}>Acknowledged by <strong>{selAlert.owner}</strong></div>
            )}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
              <Btn variant="success" onClick={() => ack(selAlert.id)}>✓ Acknowledge</Btn>
              <Btn variant="warn" onClick={() => setShowAssign(s => !s)}>⚑ Assign owner</Btn>
              {showAssign && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6, padding: '10px', background: C.canvas, borderRadius: 7, border: `1px solid ${C.border}` }}>
                  <Input
                    placeholder="Owner email or name"
                    value={assignForm.owner}
                    onChange={v => setAssignForm(f => ({ ...f, owner: v }))}
                  />
                  <Input
                    type="date"
                    value={assignForm.date}
                    onChange={v => setAssignForm(f => ({ ...f, date: v }))}
                  />
                  <Btn
                    variant="primary"
                    disabled={!assignForm.owner || !assignForm.date}
                    onClick={() => { assign(selAlert.id, assignForm.owner, assignForm.date); setShowAssign(false); setAssignForm({ owner: '', date: '' }) }}
                  >
                    Confirm assignment
                  </Btn>
                </div>
              )}
              <Btn variant="primary" onClick={() => navigate('/cockpit')}>Draft escalation ↗</Btn>
              <Btn onClick={() => dismiss(selAlert.id, 'Manually dismissed')}>Dismiss with reason</Btn>
            </div>
            <div style={{ marginTop: 14 }}>
              <div style={{ fontSize: 11, fontWeight: 500, color: C.sub, marginBottom: 5 }}>Mitigation note</div>
              <textarea
                placeholder="Describe actions taken or planned…"
                value={mitigation}
                onChange={e => setMitigation(e.target.value)}
                style={{ width: '100%', fontSize: 12, padding: '8px 10px', borderRadius: 7, border: `1px solid ${C.border}`, outline: 'none', resize: 'vertical', minHeight: 60, color: C.text }}
              />
              <Btn
                variant="primary"
                disabled={!mitigation.trim()}
                onClick={() => saveNote(selAlert.id)}
                style={{ marginTop: 6, opacity: mitigation.trim() ? 1 : 0.5 }}
              >
                Save note
              </Btn>
            </div>
          </div>
        ) : (
          <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, display: 'flex', alignItems: 'center', justifyContent: 'center', color: C.muted, fontSize: 13, minHeight: mobile ? 80 : undefined }}>
            Select an alert to see details and actions
          </div>
        )}
      </div>
    </div>
  )
}
