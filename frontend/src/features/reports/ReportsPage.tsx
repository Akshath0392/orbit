import { ErrorState } from '../../design/components/PageState'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useC } from '../../design/ThemeContext'
import { THead } from '../../design/components/THead'
import { Select } from '../../design/components/Select'
import { Pagination, usePersistedPageSize } from '../../design/components/Pagination'
import { api } from '../../api/client'
import { useClients, useReportTemplates } from '../../api/hooks'

const todayIso = () => new Date().toISOString().slice(0, 10)
const daysAgoIso = (n: number) => { const d = new Date(); d.setDate(d.getDate() - n); return d.toISOString().slice(0, 10) }

export function ReportsPage() {
  const C = useC()
  const [showModal, setShowModal] = useState(false)
  const [selId, setSelId] = useState<number | null>(null)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = usePersistedPageSize('reports', 10)
  const [previewId, setPreviewId] = useState<number | null>(null)
  const [toast, setToast] = useState('')
  const [modalType, setModalType] = useState('Weekly delivery')
  const [modalClientId, setModalClientId] = useState<number | null>(null)
  const [modalFrom, setModalFrom] = useState(daysAgoIso(7))
  const [modalTo,   setModalTo]   = useState(todayIso())

  const { data: clients = [] } = useClients()
  const { data: templates = [] } = useReportTemplates()
  const { data: reportStats } = useQuery({
    queryKey: ['reports-stats'],
    queryFn: () => api.get('/reports/stats').then(r => r.data),
    staleTime: 60_000,
  })
  const { data: schedulesCount } = useQuery({
    queryKey: ['report-schedules-active'],
    queryFn: () => api.get('/report-schedules/count', { params: { active: true } }).then(r => r.data?.count ?? 0),
    staleTime: 60_000,
  })

  const { data: reportPage, isLoading, error, refetch } = useQuery({
    queryKey: ['reports', page, pageSize],
    queryFn: () => api.get('/reports', { params: { page, size: pageSize } }).then(r => r.data)
  })
  const reportList = reportPage?.content ?? []
  const totalPages = reportPage?.totalPages ?? 1
  const selReport = reportList.find((r: any) => r.id === selId)

  const { data: preview } = useQuery({
    queryKey: ['report-preview', previewId],
    queryFn: () => previewId ? api.get(`/reports/${previewId}/preview`).then(r => r.data) : null,
    enabled: !!previewId
  })

  const generate = (type: string, clientId?: number) =>
    api.post('/reports/generate', { type, clientId }).then(() => { refetch(); setShowModal(false) })

  if (isLoading) return <div style={{ padding: 40, color: C.sub }}>Loading…</div>
  if (error) return <ErrorState error={error} />

  return (
    <div style={{ padding: '22px 24px' }}>
      {toast && (
        <div style={{
          position: 'fixed', bottom: 24, right: 24, zIndex: 9999,
          padding: '10px 18px', borderRadius: 8, background: C.navy,
          color: '#fff', fontSize: 13, fontWeight: 500, boxShadow: '0 8px 24px rgba(0,0,0,0.18)'
        }}>
          {toast}
        </div>
      )}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 18 }}>
        <div>
          <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>Reports</div>
          <div style={{ fontSize: 12, color: C.sub, marginTop: 3 }}>AI-drafted delivery reports · auto-scheduled</div>
        </div>
        <button onClick={() => setShowModal(true)} style={{ fontSize: 12, padding: '6px 14px', borderRadius: 7, border: 'none', background: C.indigo, color: '#fff', cursor: 'pointer', fontWeight: 500 }}>+ Generate report</button>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 10, marginBottom: 20 }}>
        {[
          ['Report types', String(reportStats?.reportTypes ?? templates.length ?? '—'), C.indigo, '📋'],
          ['Auto-scheduled', String(schedulesCount ?? '—'), C.green, '⏰'],
          ['Generated this week', String(reportStats?.generatedThisWeek ?? '—'), C.amber, '📊'],
        ].map(([l, v, c, icon]) => (
          <div key={String(l)} style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 10, padding: '14px 16px' }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: String(c) }}>{v}</div>
            <div style={{ fontSize: 12, color: C.sub, marginTop: 2 }}>{icon} {l}</div>
          </div>
        ))}
      </div>

      <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden', marginBottom: 16 }}>
        <table style={{ width: '100%', fontSize: 12 }}>
          <THead cols={['Report type', 'Client', 'Generated', 'By', 'Status', '']} />
          <tbody>
            {reportList.length === 0 && (
              <tr>
                <td colSpan={6} style={{ padding: '20px', textAlign: 'center', color: C.muted }}>No data yet</td>
              </tr>
            )}
            {reportList.map((r: any, i: number) => (
              <tr
                key={r.id}
                onClick={() => { setSelId(selId === r.id ? null : r.id); setPreviewId(r.id) }}
                style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none', cursor: 'pointer', background: selId === r.id ? C.indigoPale : C.white }}
              >
                <td style={{ padding: '10px 12px', fontWeight: 500, color: C.text }}>{r.type}</td>
                <td style={{ padding: '10px 12px', color: C.sub }}>{r.client}</td>
                <td style={{ padding: '10px 12px', color: C.sub }}>{r.generatedAt}</td>
                <td style={{ padding: '10px 12px', color: C.text }}>{r.generatedBy}</td>
                <td style={{ padding: '10px 12px', color: C.sub }}>{r.status}</td>
                <td style={{ padding: '10px 12px' }}>
                  <div style={{ display: 'flex', gap: 6 }}>
                    <button
                      onClick={(e) => { e.stopPropagation(); setSelId(r.id); setPreviewId(r.id) }}
                      style={{ fontSize: 11, padding: '3px 8px', borderRadius: 5, cursor: 'pointer', border: `1px solid ${C.border}`, background: 'transparent', color: C.sub }}
                    >
                      Preview
                    </button>
                    <button
                      onClick={(e) => { e.stopPropagation(); window.open(`/api/v1/reports/${r.id}/download?format=WORD`) }}
                      style={{ fontSize: 11, padding: '3px 8px', borderRadius: 5, cursor: 'pointer', border: `1px solid ${C.border}`, background: 'transparent', color: C.indigo, fontWeight: 500 }}
                    >
                      .docx
                    </button>
                    <button
                      onClick={(e) => { e.stopPropagation(); window.open(`/api/v1/reports/${r.id}/download?format=PDF`) }}
                      style={{ fontSize: 11, padding: '3px 8px', borderRadius: 5, cursor: 'pointer', border: `1px solid ${C.border}`, background: 'transparent', color: C.red, fontWeight: 500 }}
                    >
                      .pdf
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <Pagination
          page={page}
          totalPages={totalPages}
          onPageChange={setPage}
          pageSize={pageSize}
          onPageSizeChange={(n) => { setPageSize(n); setPage(0) }}
        />
      </div>

      {selReport && (
        <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: '16px 18px' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
            <div style={{ fontSize: 14, fontWeight: 600, color: C.text }}>{selReport.type} — {selReport.client}</div>
            <div style={{ display: 'flex', gap: 7 }}>
              <button
                onClick={() => window.open(`/api/v1/reports/${selReport.id}/download?format=WORD`)}
                style={{ fontSize: 12, padding: '5px 12px', borderRadius: 7, border: 'none', background: C.indigo, color: '#fff', cursor: 'pointer', fontWeight: 500 }}
              >
                ↓ .docx
              </button>
              <button
                onClick={() => window.open(`/api/v1/reports/${selReport.id}/download?format=PDF`)}
                style={{ fontSize: 12, padding: '5px 12px', borderRadius: 7, border: `1px solid ${C.border}`, background: 'transparent', color: C.sub, cursor: 'pointer' }}
              >
                ↓ .pdf
              </button>
              <button
                onClick={() => { setToast('Email delivery available via Report Schedules in Admin'); setTimeout(() => setToast(''), 3500) }}
                style={{ fontSize: 12, padding: '5px 12px', borderRadius: 7, border: `1px solid ${C.border}`, background: 'transparent', color: C.sub, cursor: 'pointer' }}
              >
                Send via email
              </button>
            </div>
          </div>
          {preview ? (
            (preview.sections ?? []).map((section: any) => (
              <div key={section.title} style={{ marginBottom: 10, padding: '10px 12px', background: C.canvas, borderRadius: 8 }}>
                <div style={{ fontSize: 12, fontWeight: 600, color: C.text, marginBottom: 4 }}>{section.title}</div>
                <div style={{ fontSize: 12, color: C.sub, lineHeight: 1.6 }}>{section.body}</div>
              </div>
            ))
          ) : (
            <div style={{ padding: '10px 12px', background: C.canvas, borderRadius: 8, fontSize: 12, color: C.muted }}>Loading preview…</div>
          )}
          <div style={{ padding: '10px 12px', background: C.indigoPale, borderRadius: 8, fontSize: 12, color: C.purpleDeep }}>
            ✏ Drafted by {selReport.draftedBy ?? selReport.generatedBy ?? 'agent'} · Add PJM notes before exporting
          </div>
        </div>
      )}

      {showModal && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,22,41,.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: C.white, borderRadius: 14, width: 500, boxShadow: '0 20px 60px rgba(0,0,0,.3)' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '16px 20px', borderBottom: `1px solid ${C.border}` }}>
              <div style={{ fontSize: 15, fontWeight: 600, color: C.text }}>Generate new report</div>
              <button onClick={() => setShowModal(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 18, color: C.muted }}>✕</button>
            </div>
            <div style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div>
                <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Report type</div>
                <Select
                  options={(templates.length ? templates : [{ id: 'Weekly delivery', name: 'Weekly delivery' }]).map(t => t.name)}
                  value={modalType}
                  onChange={e => setModalType((e.target as HTMLSelectElement).value)}
                  style={{ width: '100%' }}
                />
              </div>
              <div>
                <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Client / scope</div>
                <select
                  value={modalClientId ?? ''}
                  onChange={e => setModalClientId(e.target.value ? Number(e.target.value) : null)}
                  style={{ width: '100%', fontSize: 12, padding: '7px 10px', borderRadius: 7, border: `1px solid ${C.border}`, outline: 'none' }}
                >
                  <option value="">All clients</option>
                  {clients.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
              </div>
              <div>
                <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Date range</div>
                <div style={{ display: 'flex', gap: 8 }}>
                  <input type="date" value={modalFrom} onChange={e => setModalFrom(e.target.value)} style={{ flex: 1, fontSize: 12, padding: '7px 10px', borderRadius: 7, border: `1px solid ${C.border}`, outline: 'none' }} />
                  <input type="date" value={modalTo}   onChange={e => setModalTo(e.target.value)}   style={{ flex: 1, fontSize: 12, padding: '7px 10px', borderRadius: 7, border: `1px solid ${C.border}`, outline: 'none' }} />
                </div>
              </div>
              <div>
                <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>PJM notes (optional)</div>
                <textarea placeholder="Any context to include in the report…" style={{ width: '100%', fontSize: 12, padding: '8px 10px', borderRadius: 7, border: `1px solid ${C.border}`, outline: 'none', resize: 'vertical', minHeight: 72 }} />
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <input type="checkbox" id="csf" defaultChecked />
                <label htmlFor="csf" style={{ fontSize: 12, color: C.sub }}>Apply client-safe filter (hide internal capacity details)</label>
              </div>
              <button
                onClick={() => generate(modalType, modalClientId ?? undefined)}
                style={{ padding: '9px', borderRadius: 7, border: 'none', background: C.indigo, color: '#fff', fontSize: 13, fontWeight: 600, cursor: 'pointer' }}
              >
                Generate with ReportDraftingAgent ↗
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
