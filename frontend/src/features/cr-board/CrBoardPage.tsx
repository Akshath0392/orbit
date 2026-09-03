import { ErrorState } from '../../design/components/PageState'
import { useState, useEffect, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useLocation } from 'react-router-dom'
import { useC } from '../../design/ThemeContext'
import { Badge } from '../../design/components/Badge'
import { Breadcrumbs } from '../../design/components/Breadcrumbs'
import { THead } from '../../design/components/THead'
import { Pagination, usePersistedPageSize } from '../../design/components/Pagination'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { api } from '../../api/client'

type SortDir = 'asc' | 'desc'
type SortCol = 'issueKey' | 'stage' | 'priority' | 'owner' | 'age' | 'client'

export function CrBoardPage() {
  const C = useC()
  const { search: qs } = useLocation()

  // ── Filters & sort — mock filter set: stage/client/pod/sm/pjm/type, all
  // URL-seeded so widget drills deep-link here ──────────────────────────────
  const qp = new URLSearchParams(qs)
  const [clientId,  setClientId]  = useState<string>(() => qp.get('clientId') ?? '')
  const [stage,     setStage]     = useState<string>(() => qp.get('stage') ?? '')
  const [pod,       setPod]       = useState<string>(() => qp.get('pod') ?? '')
  const [sm,        setSm]        = useState<string>(() => qp.get('sm') ?? '')
  const [pjm,       setPjm]       = useState<string>(() => qp.get('pjm') ?? '')
  const [type,      setType]      = useState<string>(() => qp.get('type') ?? '')
  const [search,    setSearch]    = useState<string>('')
  const debouncedSearch = useDebouncedValue(search, 350)
  const [sort,      setSort]      = useState<SortCol>('issueKey')
  const [direction, setDirection] = useState<SortDir>('asc')
  const [page,      setPage]      = useState(0)
  const [pageSize,  setPageSize]  = usePersistedPageSize('cr-board', 20)

  // Reset to first page when search settles (debounce already throttles).
  useEffect(() => { setPage(0) }, [debouncedSearch])

  // ── Detail panel ───────────────────────────────────────────────────────────
  const [selKey, setSelKey] = useState<string | null>(null)
  const [showNote, setShowNote] = useState(false)
  const [noteText, setNoteText] = useState('')
  const [savingNote, setSavingNote] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [toast, setToast] = useState('')
  const showToast = (msg: string) => { setToast(msg); setTimeout(() => setToast(''), 3500) }

  async function downloadCsv() {
    setExporting(true)
    try {
      const res = await api.get('/cr/export', {
        params: {
          clientId:    clientId  || undefined,
          portfolioId: pod       || undefined,
          stage:       stage     || undefined,
          sm:          sm        || undefined,
          pjm:         pjm       || undefined,
          type:        type      || undefined,
          search:      debouncedSearch || undefined,
          sort, direction
        },
        responseType: 'blob'
      })
      const url = URL.createObjectURL(new Blob([res.data], { type: 'text/csv' }))
      const a = document.createElement('a')
      a.href = url
      a.download = `cr-export${stage ? `-${stage}` : ''}${clientId ? `-client${clientId}` : ''}.csv`
      a.click()
      URL.revokeObjectURL(url)
    } catch {
      showToast('Export failed — please try again')
    } finally {
      setExporting(false)
    }
  }

  // ── Data fetching ──────────────────────────────────────────────────────────
  const { data: clients = [] } = useQuery({
    queryKey: ['clients-list'],
    queryFn: () => api.get('/clients').then(r => r.data)
  })

  const { data: stageData = {} } = useQuery({
    queryKey: ['cr-stages', clientId],
    queryFn: () => api.get('/cr/stage-summary', {
      params: { clientId: clientId || undefined }
    }).then(r => r.data)
  })

  const { data: crPage, isLoading, error } = useQuery({
    queryKey: ['crs', page, pageSize, stage, debouncedSearch, clientId, pod, sm, pjm, type, sort, direction],
    queryFn: () => api.get('/cr', {
      params: {
        page, size: pageSize,
        stage:       stage     || undefined,
        search:      debouncedSearch || undefined,
        clientId:    clientId  || undefined,
        portfolioId: pod       || undefined,
        sm:          sm        || undefined,
        pjm:         pjm       || undefined,
        type:        type      || undefined,
        sort, direction
      }
    }).then(r => r.data)
  })

  const { data: filterOptions } = useQuery({
    queryKey: ['cr-filter-options'],
    queryFn: () => api.get('/cr/filter-options').then(r => r.data)
  })
  const { data: portfolios = [] } = useQuery({
    queryKey: ['portfolios-list'],
    queryFn: () => api.get('/portfolios').then(r => r.data)
  })

  const { data: jiraConfig } = useQuery({
    queryKey: ['jira-config'],
    queryFn: () => api.get('/jira-sync/config').then(r => r.data)
  })

  // Fetch CR detail when a row is selected
  const { data: selDetail } = useQuery({
    queryKey: ['cr-detail', selKey],
    queryFn: () => api.get(`/cr/${selKey}`).then(r => r.data),
    enabled: !!selKey
  })

  const sel = { fontSize: 12, padding: '6px 10px', borderRadius: 7, border: `1px solid ${C.border}`, background: C.white, color: C.text, outline: 'none' } as const
  const crs = crPage?.content ?? []
  const totalPages = crPage?.totalPages ?? 1
  const stageMap = stageData as Record<string, number>
  const clientList = clients as any[]
  const scopedClientName = clientId ? clientList.find((c: any) => String(c.id) === clientId)?.name ?? null : null

  const jiraBase = jiraConfig?.baseUrl || 'https://jira.atlassian.net'
  const openJira = (key: string) => window.open(`${jiraBase}/browse/${key}`, '_blank')

  // ── Sort helpers ───────────────────────────────────────────────────────────
  function handleSort(col: SortCol) {
    if (sort === col) setDirection(d => d === 'asc' ? 'desc' : 'asc')
    else { setSort(col); setDirection('asc') }
    setPage(0)
  }

  function SortIcon({ col }: { col: SortCol }) {
    if (sort !== col) return <span style={{ color: C.muted, marginLeft: 3 }}>⇅</span>
    return <span style={{ color: C.indigo, marginLeft: 3 }}>{direction === 'asc' ? '↑' : '↓'}</span>
  }

  function SortHeader({ col, label }: { col: SortCol; label: string }) {
    return (
      <span
        onClick={() => handleSort(col)}
        style={{ cursor: 'pointer', userSelect: 'none', display: 'inline-flex', alignItems: 'center' }}
      >
        {label}<SortIcon col={col} />
      </span>
    )
  }

  // ── Stage pill colours ─────────────────────────────────────────────────────
  const stagePill = (s: string): { bg: string; fg: string } => {
    const lo = s.toLowerCase()
    if (lo.includes('dev') || lo === 'in progress') return { bg: C.bluePale,   fg: C.blueDeep   }
    if (lo === 'hold')                               return { bg: C.redPale,    fg: C.redDeep    }
    if (lo.includes('brd') || lo.includes('awaited') || lo === 'backlog' || lo === 'to do')
                                                     return { bg: C.amberPale,  fg: C.amberDeep  }
    if (lo.includes('qa') || lo.includes('uat'))     return { bg: C.purplePale, fg: C.purpleDeep }
    if (lo === 'ready for prod' || lo.includes('ready for production')) return { bg: C.tealPale, fg: C.tealDeep }
    if (lo === 'released' || lo.includes('released to production'))     return { bg: C.greenPale, fg: C.greenDeep }
    if (lo === 'closed')                             return { bg: C.canvas,     fg: C.sub        }
    return { bg: C.canvas, fg: C.sub }
  }

  if (isLoading && crs.length === 0) return <div style={{ padding: 40, color: C.sub }}>Loading…</div>
  if (error) return <ErrorState error={error} />

  return (
    <div style={{ padding: '22px 24px' }}>
      {toast && (
        <div style={{ position: 'fixed', bottom: 24, right: 24, zIndex: 9999, padding: '10px 18px', borderRadius: 8, background: C.text, color: '#fff', fontSize: 13, fontWeight: 500, boxShadow: '0 8px 24px rgba(0,0,0,0.18)' }}>
          {toast}
        </div>
      )}

      {/* ── Header ─────────────────────────────────────────────────────────── */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 18 }}>
        <div>
          {scopedClientName && (
            <div style={{ marginBottom: 6 }}>
              <Breadcrumbs items={[{ label: 'Orbitter', to: '/radar' }, { label: scopedClientName }, { label: 'CRs' }]} />
            </div>
          )}
          <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>
            CR delivery board{scopedClientName ? ` — ${scopedClientName}` : ''}
          </div>
          <div style={{ fontSize: 12, color: C.sub, marginTop: 3 }}>
            {crPage?.totalElements ?? '—'} CRs · Jira synced
            {scopedClientName && (
              <button onClick={() => { setClientId(''); setStage(''); setPage(0) }}
                style={{ marginLeft: 8, background: 'none', border: 'none', padding: 0, color: C.indigo, cursor: 'pointer', fontSize: 12, fontWeight: 600 }}>
                clear client filter ✕
              </button>
            )}
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          {/* Mock filter set: client · POD · SM · PjM · type (+stage pills below) */}
          <select aria-label="Client filter" value={clientId}
            onChange={e => { setClientId(e.target.value); setStage(''); setPage(0) }} style={sel}>
            <option value="">All clients</option>
            {clientList.map((c: any) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
          <select aria-label="POD filter" value={pod}
            onChange={e => { setPod(e.target.value); setPage(0) }} style={sel}>
            <option value="">All PODs</option>
            {(Array.isArray(portfolios) ? portfolios : []).map((p: any) => <option key={p.id} value={p.id}>{p.name}</option>)}
          </select>
          <select aria-label="SM filter" value={sm}
            onChange={e => { setSm(e.target.value); setPage(0) }} style={sel}>
            <option value="">All SMs</option>
            {(filterOptions?.sms ?? []).map((s: string) => <option key={s} value={s}>{s}</option>)}
          </select>
          <select aria-label="PjM filter" value={pjm}
            onChange={e => { setPjm(e.target.value); setPage(0) }} style={sel}>
            <option value="">All PjMs</option>
            {(filterOptions?.pjms ?? []).map((s: string) => <option key={s} value={s}>{s}</option>)}
          </select>
          <select aria-label="Type filter" value={type}
            onChange={e => { setType(e.target.value); setPage(0) }} style={sel}>
            <option value="">All types</option>
            <option value="LAUNCH">Launch</option>
            <option value="BAU">BAU</option>
          </select>
          {/* Search */}
          <input
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Search key or summary…"
            style={{ fontSize: 12, padding: '6px 10px', borderRadius: 7, border: `1px solid ${C.border}`, background: C.white, color: C.text, outline: 'none', width: 180 }}
          />
          {/* CSV export */}
          <button
            onClick={downloadCsv}
            disabled={exporting}
            title={`Export ${crPage?.totalElements ?? ''} CRs as CSV`}
            style={{ fontSize: 12, padding: '6px 12px', borderRadius: 7, border: `1px solid ${C.border}`, background: C.white, color: exporting ? C.muted : C.indigo, cursor: exporting ? 'not-allowed' : 'pointer', fontWeight: 500, display: 'flex', alignItems: 'center', gap: 5 }}
          >
            {exporting ? '⟳ Exporting…' : '↓ CSV'}
          </button>
        </div>
      </div>

      {/* ── Stage tiles (dynamic) ───────────────────────────────────────────── */}
      <div style={{ display: 'flex', gap: 6, marginBottom: 18, overflowX: 'auto', paddingBottom: 4 }}>
        {/* All tile */}
        <div
          onClick={() => { setStage(''); setPage(0) }}
          style={{
            flexShrink: 0, background: stage === '' ? C.indigoPale : C.white,
            border: `1px solid ${stage === '' ? C.indigo : C.border}`,
            borderRadius: 10, padding: '10px 14px', cursor: 'pointer', textAlign: 'center', minWidth: 60, transition: 'all .15s'
          }}
        >
          <div style={{ fontSize: 20, fontWeight: 700, color: stage === '' ? C.indigo : C.text }}>
            {crPage?.totalElements ?? '—'}
          </div>
          <div style={{ fontSize: 10, color: C.sub, marginTop: 1 }}>All</div>
        </div>

        {/* Dynamic stage tiles — only non-zero */}
        {Object.entries(stageMap)
          .filter(([, cnt]) => cnt > 0)
          .map(([st, cnt]) => {
            const active = stage === st
            const pill = stagePill(st)
            return (
              <div
                key={st}
                onClick={() => { setStage(active ? '' : st); setPage(0) }}
                style={{
                  flexShrink: 0, background: active ? C.indigoPale : C.white,
                  border: `1px solid ${active ? C.indigo : C.border}`,
                  borderRadius: 10, padding: '10px 12px', cursor: 'pointer', textAlign: 'center', minWidth: 84, transition: 'all .15s'
                }}
              >
                <div style={{ fontSize: 20, fontWeight: 700, color: active ? C.indigo : pill.fg }}>{cnt}</div>
                <div style={{ fontSize: 10, color: C.sub, marginTop: 1, whiteSpace: 'nowrap' }}>{st}</div>
              </div>
            )
          })}
      </div>

      {/* ── CR table ────────────────────────────────────────────────────────── */}
      <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden', marginBottom: 12 }}>
        <table style={{ width: '100%', fontSize: 12, borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ background: C.canvas }}>
              {/* mock column order: CR · Client · Description · Status · Stage · POD · SM · PjM · Type · Aging */}
              {[
                { col: 'issueKey' as SortCol, label: 'CR' },
                { col: 'client' as SortCol, label: 'Client' },
                { col: null, label: 'Description' },
                { col: null, label: 'Status' },
                { col: 'stage' as SortCol, label: 'Stage' },
                { col: null, label: 'POD' },
                { col: null, label: 'SM' },
                { col: null, label: 'PjM' },
                { col: null, label: 'Type' },
                { col: 'age' as SortCol, label: 'Aging' },
              ].map(({ col, label }) => (
                <th key={label} style={{ padding: '8px 12px', textAlign: 'left', fontSize: 10, fontWeight: 600, color: C.sub, letterSpacing: 0.4, borderBottom: `1px solid ${C.border}` }}>
                  {col ? <SortHeader col={col} label={label} /> : label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {crs.length === 0 && (
              <tr>
                <td colSpan={10} style={{ padding: '32px', textAlign: 'center', color: C.muted }}>
                  {debouncedSearch ? `No CRs matching "${debouncedSearch}"` : 'No CRs found'}
                </td>
              </tr>
            )}
            {crs.map((cr: any, i: number) => {
              const pill = stagePill(cr.stage)
              return (
                <tr
                  key={cr.key}
                  onClick={() => { setSelKey(selKey === cr.key ? null : cr.key); setShowNote(false); setNoteText('') }}
                  style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none', cursor: 'pointer', background: selKey === cr.key ? C.indigoPale : C.white }}
                >
                  {/* Key — clickable link */}
                  <td style={{ padding: '10px 12px' }}>
                    <span
                      onClick={(e) => { e.stopPropagation(); openJira(cr.key) }}
                      style={{ color: C.indigo, fontWeight: 700, cursor: 'pointer', textDecoration: 'underline', textUnderlineOffset: 2 }}
                    >
                      {cr.key} ↗
                    </span>
                  </td>
                  <td style={{ padding: '10px 12px', color: C.sub }}>{cr.client}</td>
                  <td style={{ padding: '10px 12px', color: C.text, maxWidth: 220 }}>{cr.summary}</td>
                  <td style={{ padding: '10px 12px', fontSize: 11, color: C.sub }}>{cr.jiraStatus}</td>
                  <td style={{ padding: '10px 12px' }}>
                    <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 4, background: pill.bg, color: pill.fg, fontWeight: 500, whiteSpace: 'nowrap' }}>
                      {cr.stage}
                    </span>
                  </td>
                  <td style={{ padding: '10px 12px', color: C.sub }}>{cr.pod}</td>
                  <td style={{ padding: '10px 12px', color: C.sub }}>{cr.sm}</td>
                  <td style={{ padding: '10px 12px', color: C.sub }}>{cr.pjm}</td>
                  <td style={{ padding: '10px 12px', fontSize: 11, color: C.sub }}>{cr.type}</td>
                  <td style={{ padding: '10px 12px', color: C.sub }}>{cr.age}</td>
                </tr>
              )
            })}
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

      {/* ── Detail panel ────────────────────────────────────────────────────── */}
      {selKey && selDetail && (
        <div style={{ background: C.white, border: `1px solid ${C.indigo}`, borderLeft: `3px solid ${C.indigo}`, borderRadius: '0 12px 12px 0', padding: '16px 18px' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <span
                onClick={() => openJira(selDetail.key)}
                style={{ fontSize: 14, fontWeight: 700, color: C.indigo, cursor: 'pointer', textDecoration: 'underline', textUnderlineOffset: 2 }}
              >
                {selDetail.key} ↗
              </span>
              <span style={{ fontSize: 14, fontWeight: 600, color: C.text }}>{selDetail.summary}</span>
            </div>
            <div style={{ display: 'flex', gap: 7 }}>
              <button
                onClick={() => setShowNote(s => !s)}
                style={{ fontSize: 11, padding: '4px 10px', borderRadius: 6, cursor: 'pointer', border: `1px solid ${C.border}`, background: 'transparent', color: C.sub }}
              >
                {showNote ? 'Cancel note' : '+ Note'}
              </button>
              <button
                onClick={() => { setSelKey(null); setShowNote(false); setNoteText('') }}
                style={{ fontSize: 11, padding: '4px 10px', borderRadius: 6, cursor: 'pointer', border: `1px solid ${C.border}`, background: 'transparent', color: C.sub }}
              >
                ✕ Close
              </button>
            </div>
          </div>

          {/* Meta chips */}
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 14 }}>
            <Badge level={selDetail.stage?.toLowerCase().includes('hold') ? 'critical' : selDetail.stage?.toLowerCase().includes('released') || selDetail.stage?.toLowerCase().includes('closed') ? 'healthy' : 'info'} label={selDetail.stage} />
            {selDetail.jiraStatus !== selDetail.stage && (
              <span style={{ fontSize: 11, color: C.sub, padding: '2px 8px', borderRadius: 4, background: C.canvas, border: `1px solid ${C.border}` }}>
                Jira: {selDetail.jiraStatus}
              </span>
            )}
            <span style={{ fontSize: 11, color: C.sub }}>Priority: {selDetail.pri}</span>
            <span style={{ fontSize: 11, color: selDetail.owner === '—' ? C.red : C.sub }}>
              Assignee: {selDetail.owner}
            </span>
            <span style={{ fontSize: 11, color: C.sub }}>{selDetail.age} old</span>
          </div>

          {/* Milestones */}
          {(selDetail.milestones ?? []).length > 0 ? (
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 14 }}>
              {(selDetail.milestones as any[]).map((m: any) => (
                <div key={m.type} style={{ background: C.canvas, borderRadius: 8, padding: '8px 12px', minWidth: 90 }}>
                  <div style={{ fontSize: 10, color: C.sub, marginBottom: 3 }}>{m.type}</div>
                  <div style={{ fontSize: 12, fontWeight: 600, color: m.isTbc ? C.muted : m.status === 'AT_RISK' ? C.amber : C.green }}>
                    {m.isTbc ? 'TBC' : (m.targetDate ?? m.status ?? 'Set')}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div style={{ fontSize: 12, color: C.muted, marginBottom: 14 }}>No milestone data — configure in Jira.</div>
          )}

          {/* Notes */}
          {(selDetail.notes ?? []).length > 0 && (
            <div style={{ marginBottom: 14 }}>
              <div style={{ fontSize: 11, fontWeight: 600, color: C.sub, marginBottom: 8 }}>Notes</div>
              {(selDetail.notes as any[]).map((n: any, i: number) => (
                <div key={n.id} style={{ padding: '8px 10px', background: C.canvas, borderRadius: 7, marginBottom: 6, fontSize: 12, color: C.text, lineHeight: 1.5 }}>
                  <div style={{ fontSize: 10, color: C.muted, marginBottom: 3 }}>{n.by} · {new Date(n.at).toLocaleDateString()}</div>
                  {n.text}
                </div>
              ))}
            </div>
          )}

          {/* Add note inline */}
          {showNote && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
              <textarea
                placeholder="Add a note on this CR…"
                value={noteText}
                onChange={e => setNoteText(e.target.value)}
                rows={3}
                style={{ fontSize: 12, padding: '8px 10px', borderRadius: 7, border: `1px solid ${C.border}`, outline: 'none', resize: 'vertical', color: C.text, background: C.white, width: '100%', boxSizing: 'border-box' as const }}
              />
              <div style={{ display: 'flex', gap: 7 }}>
                <button
                  disabled={!noteText.trim() || savingNote}
                  onClick={() => {
                    setSavingNote(true)
                    api.post(`/cr/${selDetail.key}/notes`, { text: noteText, isClientSafe: false })
                      .then(() => { setNoteText(''); setShowNote(false) })
                      .finally(() => setSavingNote(false))
                  }}
                  style={{ fontSize: 12, padding: '6px 14px', borderRadius: 6, border: 'none', background: C.indigo, color: '#fff', cursor: 'pointer', fontWeight: 500 }}
                >
                  {savingNote ? 'Saving…' : 'Save note'}
                </button>
                <button onClick={() => { setShowNote(false); setNoteText('') }} style={{ fontSize: 12, padding: '6px 12px', borderRadius: 6, border: `1px solid ${C.border}`, background: 'transparent', color: C.sub, cursor: 'pointer' }}>
                  Cancel
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
