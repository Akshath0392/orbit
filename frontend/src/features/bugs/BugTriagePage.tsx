import { ErrorState } from '../../design/components/PageState'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useLocation } from 'react-router-dom'
import { useC } from '../../design/ThemeContext'
import { Badge } from '../../design/components/Badge'
import { THead } from '../../design/components/THead'
import { Tabs } from '../../design/components/Tabs'
import { Select } from '../../design/components/Select'
import { Pagination, usePersistedPageSize } from '../../design/components/Pagination'
import { api } from '../../api/client'

export function BugTriagePage() {
  const C = useC()
  const { search: qs } = useLocation()
  const initialSeverity = new URLSearchParams(qs).get('severity') ?? ''
  const initialClientId = new URLSearchParams(qs).get('clientId') ?? ''

  const [severity,  setSeverity]  = useState<string>(initialSeverity)
  const [clientId,  setClientId]  = useState<string>(initialClientId)
  const [slaStatus, setSlaStatus] = useState<string>('')
  const [uatStage,  setUatStage]  = useState<string>('')

  const sevStyle: Record<string, { bg: string; fg: string }> = {
    P0: { bg: C.redPale, fg: C.redDeep },
    P1: { bg: C.amberPale, fg: C.amberDeep },
    P2: { bg: C.amberPale, fg: C.amberDeep },
    P3: { bg: C.canvas, fg: C.sub },
  }
  const slaCol: Record<string, string> = { Breached: C.red, 'At risk': C.amber, 'On track': C.green }
  const uatStageCol: Record<string, string> = { Retesting: C.amber, 'In fix': C.indigo, Raised: C.sub, 'Retest failed': C.red, Fixed: C.green }
  // ?tab=uat|prod makes the tab deep-linkable (DH quality cards drill here)
  const [tab, setTab] = useState(new URLSearchParams(qs).get('tab') === 'uat' ? 'UAT bugs' : 'Production bugs')
  const [prodPage, setProdPage] = useState(0)
  const [uatPage, setUatPage] = useState(0)
  const [prodSize, setProdSize] = usePersistedPageSize('bugs-prod', 10)
  const [uatSize,  setUatSize]  = usePersistedPageSize('bugs-uat',  10)
  const [prodSort, setProdSort] = useState<string>('')
  const [uatSort,  setUatSort]  = useState<string>('')

  // ── Clients dropdown ──────────────────────────────────────────────────────
  const { data: clientList = [] } = useQuery({
    queryKey: ['clients-list'],
    queryFn: () => api.get('/clients').then(r => r.data as any[]),
  })
  const clientOptions = [
    { v: '', l: 'All clients' },
    ...(clientList as any[]).map((c: any) => ({ v: String(c.id), l: c.name })),
  ]

  // ── Reset page on filter change ───────────────────────────────────────────
  const onSeverityChange = (v: string) => { setSeverity(v); setProdPage(0) }
  const onClientChange   = (v: string) => { setClientId(v); setProdPage(0); setUatPage(0) }
  const onSlaChange      = (v: string) => { setSlaStatus(v); setProdPage(0) }
  const onStageChange    = (v: string) => { setUatStage(v); setUatPage(0) }

  // ── Queries with all filters wired ────────────────────────────────────────
  const { data: prodData, isLoading: prodLoading, error: prodError } = useQuery({
    queryKey: ['bugs-prod', prodPage, prodSize, severity, clientId, slaStatus, prodSort],
    queryFn: () => api.get('/bugs/prod', { params: {
      page: prodPage, size: prodSize,
      severity:  severity  || undefined,
      clientId:  clientId  || undefined,
      slaStatus: slaStatus || undefined,
      sort:      prodSort  || undefined,
    }}).then(r => r.data)
  })
  const prodBugs = prodData?.content ?? []
  const prodTotalPages = prodData?.totalPages ?? 1

  const { data: uatData, isLoading: uatLoading } = useQuery({
    queryKey: ['bugs-uat', uatPage, uatSize, clientId, uatStage, uatSort],
    queryFn: () => api.get('/bugs/uat', { params: {
      page: uatPage, size: uatSize,
      clientId: clientId || undefined,
      stage:    uatStage || undefined,
      sort:     uatSort  || undefined,
    }}).then(r => r.data)
  })
  const uatBugs = uatData?.content ?? []
  const uatTotalPages = uatData?.totalPages ?? 1

  const { data: prodSummary } = useQuery({
    queryKey: ['bugs-prod-summary', clientId],
    queryFn: () => api.get('/bugs/prod/summary', { params: { clientId: clientId || undefined } }).then(r => r.data),
    enabled:  tab === 'Production bugs',
  })

  const { data: uatSummary } = useQuery({
    queryKey: ['bugs-uat-summary', clientId],
    queryFn: () => api.get('/bugs/uat/summary', { params: { clientId: clientId || undefined } }).then(r => r.data),
    enabled:  tab === 'UAT bugs',
  })

  // Jira config — used to make "Log bug" a real link to Jira's issue-create form
  const { data: jiraConfig } = useQuery({
    queryKey: ['jira-config'],
    queryFn: () => api.get('/jira-sync/config').then(r => r.data),
  })
  const jiraBase = (jiraConfig?.baseUrl ?? '').replace(/\/$/, '')
  const logBugUrl = jiraBase ? `${jiraBase}/secure/CreateIssue!default.jspa` : ''

  // Renders the 5 KPI tiles for whichever tab is active. Same shape (P0/P1/SLA/Reopened/Unassigned)
  // for both tabs — backend swaps between PROD_BUG and UAT_BUG counts based on the endpoint.
  const renderSummaryTiles = (s: any) => (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5,1fr)', gap: 10, marginBottom: 16 }}>
      {([
        ['P0 open',      s?.p0Open      ?? '—', C.red],
        ['P1 open',      s?.p1Open      ?? '—', C.amber],
        ['SLA breached', s?.slaBreached ?? '—', C.red],
        ['Reopened',     s?.reopened    ?? '—', C.amber],
        ['Unassigned',   s?.unassigned  ?? '—', C.sub],
      ] as const).map(([l, v, c]) => (
        <div key={l} style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 10, padding: '12px 14px', textAlign: 'center' }}>
          <div style={{ fontSize: 22, fontWeight: 700, color: c }}>{v}</div>
          <div style={{ fontSize: 11, color: C.sub, marginTop: 2 }}>{l}</div>
        </div>
      ))}
    </div>
  )

  if (prodError) return <ErrorState error={prodError} />

  return (
    <div style={{ padding: '22px 24px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 18 }}>
        <div>
          <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>Bug triage</div>
          <div style={{ fontSize: 12, color: C.sub, marginTop: 3 }}>SLA-tracked · All clients</div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Select
            options={clientOptions}
            value={clientId}
            onChange={e => onClientChange(e.target.value)}
            style={{ width: 175 }}
          />
          {logBugUrl ? (
            <a
              href={logBugUrl}
              target="_blank"
              rel="noopener noreferrer"
              title={`Opens ${logBugUrl}`}
              style={{
                fontSize: 12, padding: '6px 14px', borderRadius: 7, border: 'none',
                background: C.indigo, color: '#fff', cursor: 'pointer', fontWeight: 500,
                textDecoration: 'none', display: 'inline-flex', alignItems: 'center', gap: 4,
              }}
            >
              + Log bug <span style={{ fontSize: 10 }}>↗</span>
            </a>
          ) : (
            <button
              disabled
              title="Configure Jira base URL in Admin → Integrations to enable"
              style={{
                fontSize: 12, padding: '6px 14px', borderRadius: 7, border: 'none',
                background: C.canvas, color: C.muted, cursor: 'not-allowed', fontWeight: 500,
              }}
            >
              + Log bug
            </button>
          )}
        </div>
      </div>

      <Tabs items={['Production bugs', 'UAT bugs']} active={tab} onChange={(t) => { setTab(t) }} />

      {tab === 'Production bugs' && (
        <>
          {renderSummaryTiles(prodSummary)}
          <div style={{ display: 'flex', gap: 8, margin: '12px 0' }}>
            <Select
              options={[{v:'',l:'All severities'},{v:'P0',l:'P0'},{v:'P1',l:'P1'},{v:'P2',l:'P2'},{v:'P3',l:'P3'}]}
              value={severity}
              onChange={e => onSeverityChange(e.target.value)}
              style={{ width: 140 }}
            />
            <Select
              options={[{v:'',l:'All SLA states'},{v:'Breached',l:'Breached'},{v:'At risk',l:'At risk'},{v:'On track',l:'On track'}]}
              value={slaStatus}
              onChange={e => onSlaChange(e.target.value)}
              style={{ width: 150 }}
            />
            {(severity || slaStatus) && (
              <button onClick={() => { setSeverity(''); setSlaStatus(''); setProdPage(0) }}
                style={{ fontSize: 12, padding: '6px 12px', borderRadius: 7, border: `1px solid ${C.border}`, background: 'transparent', color: C.sub, cursor: 'pointer' }}>
                Clear filters
              </button>
            )}
          </div>
        {prodLoading
          ? <div style={{ padding: 40, color: C.sub }}>Loading…</div>
          : (
            <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden' }}>
              <table style={{ width: '100%', fontSize: 12 }}>
                <THead
                  cols={[
                    { label: 'Key',           sortKey: 'key' },
                    { label: 'Summary',       sortKey: 'summary' },
                    { label: 'Severity',      sortKey: 'sev' },
                    { label: 'SLA status',    sortKey: 'slaS' },
                    'Client',
                    { label: 'Assignee',      sortKey: 'owner' },
                    { label: 'Age',           sortKey: 'age' },
                    { label: 'SLA remaining', sortKey: 'rem' },
                    '',
                  ]}
                  sort={prodSort}
                  onSort={s => { setProdSort(s); setProdPage(0) }}
                />
                <tbody>
                  {prodBugs.length === 0 && (
                    <tr>
                      <td colSpan={9} style={{ padding: '20px', textAlign: 'center', color: C.muted }}>No data yet</td>
                    </tr>
                  )}
                  {prodBugs.map((b: any, i: number) => (
                    <tr key={b.key} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none', background: b.slaS === 'Breached' ? C.redPale : C.white }}>
                      <td style={{ padding: '10px 12px', color: C.indigo, fontWeight: 600 }}>{b.key}</td>
                      <td style={{ padding: '10px 12px', color: C.text, maxWidth: 220 }}>
                        {b.reopen && <span style={{ fontSize: 10, padding: '1px 5px', borderRadius: 3, background: C.amberPale, color: C.amberDeep, marginRight: 5, fontWeight: 500 }}>Reopened</span>}
                        {b.summary}
                      </td>
                      <td style={{ padding: '10px 12px' }}>
                        <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 4, fontWeight: 600, background: (sevStyle[b.sev] || {}).bg, color: (sevStyle[b.sev] || {}).fg }}>{b.sev}</span>
                      </td>
                      <td style={{ padding: '10px 12px', fontWeight: 600, color: slaCol[b.slaS] || C.text }}>{b.slaS}</td>
                      <td style={{ padding: '10px 12px', color: C.sub }}>{b.client}</td>
                      <td style={{ padding: '10px 12px', color: C.text }}>{b.owner}</td>
                      <td style={{ padding: '10px 12px', color: C.sub }}>{b.age}</td>
                      <td style={{ padding: '10px 12px', fontWeight: 600, color: slaCol[b.slaS] || C.text }}>{b.rem}</td>
                      <td style={{ padding: '10px 12px' }}>
                        <button
                          onClick={() => window.open('https://jira.atlassian.net/browse/' + b.key, '_blank')}
                          style={{ fontSize: 11, padding: '3px 9px', borderRadius: 5, cursor: 'pointer', border: `1px solid ${C.border}`, background: 'transparent', color: C.indigo, fontWeight: 500 }}
                        >
                          View in Jira ↗
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <Pagination
                page={prodPage}
                totalPages={prodTotalPages}
                onPageChange={setProdPage}
                pageSize={prodSize}
                onPageSizeChange={(n) => { setProdSize(n); setProdPage(0) }}
              />
            </div>
          )
        }
        </>
      )}

      {tab === 'UAT bugs' && (
        <>
          {renderSummaryTiles(uatSummary)}
          <div style={{ display: 'flex', gap: 8, margin: '12px 0' }}>
            <Select
              options={[
                {v:'',l:'All stages'},
                {v:'Raised',l:'Raised'},
                {v:'In fix',l:'In fix'},
                {v:'Retesting',l:'Retesting'},
                {v:'Retest failed',l:'Retest failed'},
                {v:'Fixed',l:'Fixed'},
              ]}
              value={uatStage}
              onChange={e => onStageChange(e.target.value)}
              style={{ width: 160 }}
            />
            {uatStage && (
              <button onClick={() => { setUatStage(''); setUatPage(0) }}
                style={{ fontSize: 12, padding: '6px 12px', borderRadius: 7, border: `1px solid ${C.border}`, background: 'transparent', color: C.sub, cursor: 'pointer' }}>
                Clear filters
              </button>
            )}
          </div>
        {uatLoading
          ? <div style={{ padding: 40, color: C.sub }}>Loading…</div>
          : (
            <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden' }}>
              <table style={{ width: '100%', fontSize: 12 }}>
                <THead
                  cols={[
                    { label: 'Key',      sortKey: 'key' },
                    { label: 'Summary',  sortKey: 'summary' },
                    { label: 'Severity', sortKey: 'sev' },
                    { label: 'Stage',    sortKey: 'stage' },
                    { label: 'Assignee', sortKey: 'assignee' },
                    { label: 'Cycle',    sortKey: 'cycle' },
                    { label: 'Age',      sortKey: 'age' },
                    'Client',
                    '',
                  ]}
                  sort={uatSort}
                  onSort={s => { setUatSort(s); setUatPage(0) }}
                />
                <tbody>
                  {uatBugs.length === 0 && (
                    <tr>
                      <td colSpan={9} style={{ padding: '20px', textAlign: 'center', color: C.muted }}>No data yet</td>
                    </tr>
                  )}
                  {uatBugs.map((b: any, i: number) => (
                    <tr key={b.key} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none', background: C.white }}>
                      <td style={{ padding: '10px 12px', color: C.indigo, fontWeight: 600 }}>{b.key}</td>
                      <td style={{ padding: '10px 12px', color: C.text, maxWidth: 220 }}>{b.summary}</td>
                      <td style={{ padding: '10px 12px' }}>
                        <span style={{ fontSize: 11, padding: '2px 7px', borderRadius: 4, fontWeight: 600, background: b.sev === 'Critical' ? C.redPale : b.sev === 'High' ? C.amberPale : C.canvas, color: b.sev === 'Critical' ? C.redDeep : b.sev === 'High' ? C.amberDeep : C.sub }}>{b.sev}</span>
                      </td>
                      <td style={{ padding: '10px 12px', fontWeight: 600, color: uatStageCol[b.stage] || C.sub }}>{b.stage}</td>
                      <td style={{ padding: '10px 12px', color: b.assignee === '—' ? C.red : C.text }}>{b.assignee}</td>
                      <td style={{ padding: '10px 12px', color: b.cycle >= 3 ? C.red : C.sub }}>Cycle {b.cycle}</td>
                      <td style={{ padding: '10px 12px', color: C.sub }}>{b.age}</td>
                      <td style={{ padding: '10px 12px', color: C.sub }}>{b.client}</td>
                      <td style={{ padding: '10px 12px' }}>
                        <button
                          onClick={() => window.open('https://jira.atlassian.net/browse/' + b.key, '_blank')}
                          style={{ fontSize: 11, padding: '3px 9px', borderRadius: 5, cursor: 'pointer', border: `1px solid ${C.border}`, background: 'transparent', color: C.indigo, fontWeight: 500 }}
                        >
                          View in Jira ↗
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <Pagination
                page={uatPage}
                totalPages={uatTotalPages}
                onPageChange={setUatPage}
                pageSize={uatSize}
                onPageSizeChange={(n) => { setUatSize(n); setUatPage(0) }}
              />
            </div>
          )
        }
        </>
      )}
    </div>
  )
}
