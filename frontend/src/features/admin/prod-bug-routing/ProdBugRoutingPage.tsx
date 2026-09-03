import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useC } from '../../../design/ThemeContext'
import { Card } from '../../../design/components/Card'
import { Btn } from '../../../design/components/Btn'
import { Input } from '../../../design/components/Input'
import { Badge } from '../../../design/components/Badge'
import { Modal } from '../../../design/components/Modal'
import { Tabs } from '../../../design/components/Tabs'
import { Pagination } from '../../../design/components/Pagination'
import { ErrorState } from '../../../design/components/PageState'
import { api } from '../../../api/client'
import { fmtDateTimeFull } from '../../../lib/datetime'

/**
 * Admin surface for the shared prod-bug routing feature (V80). Lives under
 * Portfolio Setup so admins configure the shared-project flag, the client
 * code assignments, and resolve quarantined bugs from one place.
 */

const QUARANTINE_PAGE_SIZE = 20

type ConfigRow = {
  projectId: number
  projectName: string
  isSharedProdBugs: boolean
  clientCodeField: string | null
  quarantinedOpen: number
}
type ClientRow = { clientId: number; clientName: string; code: string | null; hasCode: boolean }
type QuarantineRow = {
  id: number
  jiraKey: string
  jiraSummary: string | null
  rawClientCode: string | null
  reason: 'MISSING_CODE' | 'UNKNOWN_CODE'
  seenAt: string
  lastSeenAt: string
}

export function ProdBugRoutingPage() {
  const C = useC()
  const qc = useQueryClient()
  const [tab, setTab] = useState<'config' | 'clients' | 'quarantine'>('config')
  const [quarantinePage, setQuarantinePage] = useState(0)
  const [editingClient, setEditingClient] = useState<ClientRow | null>(null)
  const [codeInput, setCodeInput] = useState('')
  const [resolving, setResolving] = useState<QuarantineRow | null>(null)
  const [resolveNote, setResolveNote] = useState('')
  const [resolveCode, setResolveCode] = useState('')
  const [banner, setBanner] = useState<{ kind: 'ok' | 'err'; msg: string } | null>(null)
  const [addingPool, setAddingPool] = useState(false)
  const [poolProjectId, setPoolProjectId] = useState<number | ''>('')
  const [poolFieldId, setPoolFieldId] = useState('')

  const {
    data: config = [], isLoading: cfgLoading, error: cfgError
  } = useQuery<ConfigRow[]>({
    queryKey: ['prod-bug-routing', 'config'],
    queryFn: () => api.get('/admin/prod-bug-routing/config').then(r => r.data)
  })
  const {
    data: clients = [], isLoading: clientsLoading, error: clientsError
  } = useQuery<ClientRow[]>({
    queryKey: ['prod-bug-routing', 'clients'],
    queryFn: () => api.get('/admin/prod-bug-routing/clients').then(r => r.data)
  })
  const { data: allProjects = [] } = useQuery<any[]>({
    queryKey: ['projects', 'for-routing'],
    queryFn: () => api.get('/projects').then(r => r.data)
  })
  const {
    data: quarantine, isLoading: quarantineLoading, error: quarantineError
  } = useQuery<{ content: QuarantineRow[]; totalElements: number; totalPages: number }>({
    queryKey: ['prod-bug-routing', 'quarantine', quarantinePage],
    queryFn: () => api.get(`/admin/prod-bug-routing/quarantine?page=${quarantinePage}&size=${QUARANTINE_PAGE_SIZE}`)
      .then(r => r.data)
  })

  const setClientCode = useMutation({
    mutationFn: (v: { clientId: number; code: string }) =>
      api.post(`/admin/prod-bug-routing/clients/${v.clientId}/code`, { code: v.code }).then(r => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['prod-bug-routing', 'clients'] })
      setEditingClient(null); setCodeInput('')
      setBanner({ kind: 'ok', msg: 'Code saved' })
    },
    onError: (e: any) => setBanner({ kind: 'err', msg: e?.response?.data?.error || 'Failed to save code' })
  })

  const resolveQuarantine = useMutation({
    mutationFn: (v: { id: number; note: string; assignClientCode?: string }) => {
      const body: any = { note: v.note }
      if (v.assignClientCode) body.assignClientCode = v.assignClientCode
      return api.post(`/admin/prod-bug-routing/quarantine/${v.id}/resolve`, body).then(r => r.data)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['prod-bug-routing', 'quarantine'] })
      qc.invalidateQueries({ queryKey: ['prod-bug-routing', 'config'] })
      setResolving(null); setResolveNote(''); setResolveCode('')
      setBanner({ kind: 'ok', msg: 'Quarantine resolved' })
    },
    onError: (e: any) => setBanner({ kind: 'err', msg: e?.response?.data?.error || 'Failed to resolve' })
  })

  const addPool = useMutation({
    mutationFn: (v: { projectId: number; clientCodeField: string }) =>
      api.put(`/admin/prod-bug-routing/config/${v.projectId}`, {
        isSharedProdBugs: true, clientCodeField: v.clientCodeField
      }).then(r => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['prod-bug-routing', 'config'] })
      setAddingPool(false); setPoolProjectId(''); setPoolFieldId('')
      setBanner({ kind: 'ok', msg: 'Shared prod-bug pool enabled' })
    },
    onError: (e: any) => setBanner({ kind: 'err', msg: e?.response?.data?.error || 'Failed to enable' })
  })

  const backfill = useMutation({
    mutationFn: (projectId: number) =>
      api.post(`/admin/prod-bug-routing/backfill/${projectId}`).then(r => r.data),
    onSuccess: (data: any) => {
      qc.invalidateQueries({ queryKey: ['prod-bug-routing'] })
      setBanner({ kind: 'ok', msg: `Backfill done · ${data.issuesProcessed ?? 0} issues re-routed in ${data.durationMs ?? 0}ms` })
    },
    onError: (e: any) => setBanner({ kind: 'err', msg: e?.response?.data?.error || 'Backfill failed' })
  })

  return (
    <div style={{ padding: '22px 24px' }}>
      <div style={{ marginBottom: 18 }}>
        <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>
          Prod-bug routing
        </div>
        <div style={{ fontSize: 12, color: C.sub, marginTop: 3 }}>
          Fan out bugs from the shared Jira project to the right client via a Jira custom-field code.
        </div>
      </div>

      {banner && (
        <div style={{
          marginBottom: 12, fontSize: 12,
          background: banner.kind === 'ok' ? C.greenPale : C.redPale,
          color: banner.kind === 'ok' ? C.greenDeep : C.redDeep,
          padding: '8px 12px', borderRadius: 8,
          display: 'flex', justifyContent: 'space-between', alignItems: 'center'
        }}>
          {banner.msg}
          <button onClick={() => setBanner(null)} style={{ background: 'transparent', border: 'none', color: 'inherit', cursor: 'pointer', fontSize: 14 }}>×</button>
        </div>
      )}

      {(() => {
        const quarantineLabel = `Quarantine${quarantine?.totalElements ? ` (${quarantine.totalElements})` : ''}`
        const items = ['Shared pools', 'Client codes', quarantineLabel]
        const active = tab === 'config' ? 'Shared pools' : tab === 'clients' ? 'Client codes' : quarantineLabel
        return (
          <Tabs
            items={items}
            active={active}
            onChange={(t) => setTab(t === 'Shared pools' ? 'config' : t === 'Client codes' ? 'clients' : 'quarantine')}
          />
        )
      })()}

      <div style={{ marginTop: 16 }}>
        {tab === 'config' && (
          <ConfigTab
            rows={config} loading={cfgLoading} error={cfgError}
            onBackfill={(id) => backfill.mutate(id)}
            backfilling={backfill.isPending}
            onAddPool={() => setAddingPool(true)}
          />
        )}
        {tab === 'clients' && (
          <ClientsTab
            rows={clients} loading={clientsLoading} error={clientsError}
            onEdit={(row) => { setEditingClient(row); setCodeInput(row.code ?? '') }}
          />
        )}
        {tab === 'quarantine' && (
          <QuarantineTab
            data={quarantine} loading={quarantineLoading} error={quarantineError}
            page={quarantinePage} onPageChange={setQuarantinePage}
            onResolve={(row) => { setResolving(row); setResolveNote(''); setResolveCode(row.rawClientCode ?? '') }}
          />
        )}
      </div>

      {addingPool && (() => {
        const eligible = (allProjects as any[]).filter(p => !p.isSharedProdBugs)
        const configuredIds = new Set(config.map(c => c.projectId))
        const remaining = eligible.filter(p => !configuredIds.has(p.id))
        return (
          <Modal
            onClose={() => { setAddingPool(false); setPoolProjectId(''); setPoolFieldId('') }}
            title="Add shared prod-bug pool"
          >
            <div style={{ fontSize: 12, color: C.sub, marginBottom: 12 }}>
              Pick an existing Orbit project (must already be wired to the shared Jira project keys)
              and tell us which Jira custom field carries the client code.
            </div>
            <div style={{ fontSize: 12, color: C.sub, marginBottom: 4 }}>Project</div>
            <select
              value={poolProjectId}
              onChange={(e) => setPoolProjectId(e.target.value ? Number(e.target.value) : '')}
              style={{ width: '100%', fontSize: 13, padding: '7px 10px', borderRadius: 7, border: `1px solid ${C.border}`, background: C.white, color: C.text, marginBottom: 12 }}
            >
              <option value="">Select a project…</option>
              {remaining.map(p => (
                <option key={p.id} value={p.id}>{p.name}{p.clientName ? ` — ${p.clientName}` : ''}</option>
              ))}
            </select>
            <div style={{ fontSize: 12, color: C.sub, marginBottom: 4 }}>Jira custom field ID for client code</div>
            <Input
              value={poolFieldId}
              onChange={setPoolFieldId}
              placeholder="e.g. customfield_11683"
            />
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <Btn onClick={() => { setAddingPool(false); setPoolProjectId(''); setPoolFieldId('') }} variant="ghost">Cancel</Btn>
              <Btn
                onClick={() => addPool.mutate({
                  projectId: Number(poolProjectId),
                  clientCodeField: poolFieldId.trim()
                })}
                disabled={!poolProjectId || !poolFieldId.trim() || addPool.isPending}
              >
                {addPool.isPending ? 'Enabling…' : 'Enable routing'}
              </Btn>
            </div>
          </Modal>
        )
      })()}

      {editingClient && (
        <Modal
          onClose={() => setEditingClient(null)}
          title={`Set code for ${editingClient.clientName}`}
        >
          <div style={{ fontSize: 12, color: C.sub, marginBottom: 12 }}>
            Codes are stored uppercase. They must exactly match the value Jira reporters put in the
            custom field for their bug to be routed to this client.
          </div>
          <Input
            value={codeInput}
            onChange={(v) => setCodeInput(v.toUpperCase())}
            placeholder="e.g. ACME"
          />
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
            <Btn onClick={() => setEditingClient(null)} variant="ghost">Cancel</Btn>
            <Btn
              onClick={() => setClientCode.mutate({ clientId: editingClient.clientId, code: codeInput })}
              disabled={setClientCode.isPending || !codeInput.trim()}
            >
              {setClientCode.isPending ? 'Saving…' : 'Save'}
            </Btn>
          </div>
        </Modal>
      )}

      {resolving && (
        <Modal
          onClose={() => setResolving(null)}
          title={`Resolve ${resolving.jiraKey}`}
        >
          <div style={{ fontSize: 13, color: C.text, marginBottom: 4 }}>{resolving.jiraSummary || '—'}</div>
          <div style={{ fontSize: 12, color: C.sub, marginBottom: 12 }}>
            Reason: <b>{resolving.reason.replace('_', ' ').toLowerCase()}</b>
            {resolving.rawClientCode && <> · Jira code: <code>{resolving.rawClientCode}</code></>}
          </div>
          <div style={{ fontSize: 12, color: C.sub, marginBottom: 4 }}>Assign to client code (optional)</div>
          <Input
            value={resolveCode}
            onChange={(v) => setResolveCode(v.toUpperCase())}
            placeholder="Leave blank to just close the quarantine row"
          />
          <div style={{ fontSize: 12, color: C.sub, margin: '12px 0 4px' }}>Note</div>
          <Input
            value={resolveNote}
            onChange={setResolveNote}
            placeholder="What did you do to fix this?"
          />
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
            <Btn onClick={() => setResolving(null)} variant="ghost">Cancel</Btn>
            <Btn
              onClick={() => resolveQuarantine.mutate({
                id: resolving.id, note: resolveNote,
                assignClientCode: resolveCode.trim() || undefined
              })}
              disabled={resolveQuarantine.isPending}
            >
              {resolveQuarantine.isPending ? 'Resolving…' : 'Resolve'}
            </Btn>
          </div>
        </Modal>
      )}
    </div>
  )
}

// ── Tabs ───────────────────────────────────────────────────────────

function ConfigTab({ rows, loading, error, onBackfill, backfilling, onAddPool }: {
  rows: ConfigRow[]; loading: boolean; error: unknown;
  onBackfill: (projectId: number) => void; backfilling: boolean;
  onAddPool: () => void
}) {
  const C = useC()
  if (loading) return <div style={{ padding: 20, color: C.sub }}>Loading…</div>
  if (error) return <ErrorState error={error} />
  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 10 }}>
        <Btn onClick={onAddPool} variant="primary">+ Add shared pool</Btn>
      </div>
      {rows.length === 0 ? (
        <Card>
          <div style={{ padding: 20, fontSize: 13, color: C.sub }}>
            No shared prod-bug pools configured yet. Click <b>+ Add shared pool</b> above to
            mark an existing Orbit project as the shared Jira bug pool, or toggle
            <b> Shared prod-bug pool</b> inside the project's row on Portfolio Setup.
          </div>
        </Card>
      ) : (
        <Card>
          <table style={{ width: '100%', fontSize: 13, borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ textAlign: 'left', color: C.sub, fontSize: 11, textTransform: 'uppercase' }}>
                <th style={{ padding: '10px 14px' }}>Project</th>
                <th style={{ padding: '10px 14px' }}>Client-code field</th>
                <th style={{ padding: '10px 14px' }}>Open quarantine</th>
                <th style={{ padding: '10px 14px', textAlign: 'right' }}></th>
              </tr>
            </thead>
            <tbody>
              {rows.map(r => (
                <tr key={r.projectId} style={{ borderTop: `1px solid ${C.border}` }}>
                  <td style={{ padding: '12px 14px', color: C.text, fontWeight: 500 }}>{r.projectName}</td>
                  <td style={{ padding: '12px 14px', fontFamily: 'monospace', color: C.text }}>{r.clientCodeField ?? '—'}</td>
                  <td style={{ padding: '12px 14px' }}>
                    {r.quarantinedOpen > 0
                      ? <Badge level="risk" label={String(r.quarantinedOpen)} />
                      : <span style={{ color: C.sub }}>0</span>}
                  </td>
                  <td style={{ padding: '12px 14px', textAlign: 'right' }}>
                    <Btn
                      onClick={() => onBackfill(r.projectId)}
                      disabled={backfilling}
                      variant="ghost"
                    >
                      {backfilling ? 'Backfilling…' : 'Backfill'}
                    </Btn>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
    </>
  )
}

function ClientsTab({ rows, loading, error, onEdit }: {
  rows: ClientRow[]; loading: boolean; error: unknown;
  onEdit: (row: ClientRow) => void
}) {
  const C = useC()
  const [filter, setFilter] = useState('')
  if (loading) return <div style={{ padding: 20, color: C.sub }}>Loading…</div>
  if (error) return <ErrorState error={error} />
  const filtered = rows.filter(r =>
    r.clientName.toLowerCase().includes(filter.toLowerCase()) ||
    (r.code ?? '').toLowerCase().includes(filter.toLowerCase()))
  const missing = rows.filter(r => !r.hasCode).length
  return (
    <div>
      <div style={{ display: 'flex', gap: 10, alignItems: 'center', marginBottom: 10 }}>
        <Input value={filter} onChange={setFilter} placeholder="Filter by name or code" />
        {missing > 0 && (
          <Badge level="risk" label={`${missing} client${missing === 1 ? '' : 's'} without a code`} />
        )}
      </div>
      <Card>
        <table style={{ width: '100%', fontSize: 13, borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ textAlign: 'left', color: C.sub, fontSize: 11, textTransform: 'uppercase' }}>
              <th style={{ padding: '10px 14px' }}>Client</th>
              <th style={{ padding: '10px 14px' }}>Code</th>
              <th style={{ padding: '10px 14px', textAlign: 'right' }}></th>
            </tr>
          </thead>
          <tbody>
            {filtered.map(r => (
              <tr key={r.clientId} style={{ borderTop: `1px solid ${C.border}` }}>
                <td style={{ padding: '12px 14px', color: C.text }}>{r.clientName}</td>
                <td style={{ padding: '12px 14px', fontFamily: 'monospace', color: r.hasCode ? C.text : C.sub }}>
                  {r.code ?? <em style={{ fontStyle: 'italic' }}>not set</em>}
                </td>
                <td style={{ padding: '12px 14px', textAlign: 'right' }}>
                  <Btn onClick={() => onEdit(r)} variant="ghost">
                    {r.hasCode ? 'Change' : 'Set code'}
                  </Btn>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </div>
  )
}

function QuarantineTab({ data, loading, error, page, onPageChange, onResolve }: {
  data: { content: QuarantineRow[]; totalElements: number; totalPages: number } | undefined;
  loading: boolean; error: unknown; page: number; onPageChange: (n: number) => void;
  onResolve: (row: QuarantineRow) => void
}) {
  const C = useC()
  if (loading) return <div style={{ padding: 20, color: C.sub }}>Loading…</div>
  if (error) return <ErrorState error={error} />
  if (!data || data.content.length === 0) {
    return (
      <Card>
        <div style={{ padding: 20, fontSize: 13, color: C.sub }}>
          Nothing quarantined right now — every prod bug so far has routed cleanly.
        </div>
      </Card>
    )
  }
  return (
    <>
      <Card>
        <table style={{ width: '100%', fontSize: 13, borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ textAlign: 'left', color: C.sub, fontSize: 11, textTransform: 'uppercase' }}>
              <th style={{ padding: '10px 14px' }}>Jira key</th>
              <th style={{ padding: '10px 14px' }}>Summary</th>
              <th style={{ padding: '10px 14px' }}>Reason</th>
              <th style={{ padding: '10px 14px' }}>Raw code</th>
              <th style={{ padding: '10px 14px' }}>Last seen</th>
              <th style={{ padding: '10px 14px', textAlign: 'right' }}></th>
            </tr>
          </thead>
          <tbody>
            {data.content.map(r => (
              <tr key={r.id} style={{ borderTop: `1px solid ${C.border}` }}>
                <td style={{ padding: '12px 14px', fontFamily: 'monospace', color: C.indigo }}>{r.jiraKey}</td>
                <td style={{ padding: '12px 14px', color: C.text, maxWidth: 380, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {r.jiraSummary ?? '—'}
                </td>
                <td style={{ padding: '12px 14px' }}>
                  <Badge
                    level={r.reason === 'MISSING_CODE' ? 'risk' : 'critical'}
                    label={r.reason.replace('_', ' ').toLowerCase()}
                  />
                </td>
                <td style={{ padding: '12px 14px', fontFamily: 'monospace', color: C.sub }}>{r.rawClientCode ?? '—'}</td>
                <td style={{ padding: '12px 14px', color: C.sub, fontSize: 12 }}>
                  {fmtDateTimeFull(r.lastSeenAt)}
                </td>
                <td style={{ padding: '12px 14px', textAlign: 'right' }}>
                  <Btn onClick={() => onResolve(r)} variant="ghost">Resolve</Btn>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
      {data.totalPages > 1 && (
        <div style={{ marginTop: 12 }}>
          <Pagination page={page} totalPages={data.totalPages} onPageChange={onPageChange} />
        </div>
      )}
    </>
  )
}
