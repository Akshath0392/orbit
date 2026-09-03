import { ErrorState } from '../../../design/components/PageState'
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useC } from '../../../design/ThemeContext'
import { Badge } from '../../../design/components/Badge'
import { StatCard } from '../../../design/components/StatCard'
import { THead } from '../../../design/components/THead'
import { Tabs } from '../../../design/components/Tabs'
import { Pagination } from '../../../design/components/Pagination'
import { Modal } from '../../../design/components/Modal'
import { StatusPill } from '../../../design/components/StatusPill'
import { api } from '../../../api/client'
import { useStore } from '../../../app/store'
import { CustomFieldMappingCard } from '../../jira-sync/JiraSyncPage'
import { fmtDate, fmtDateTimeFull, parseServerDate } from '../../../lib/datetime'

const PAGE_SIZE = 10

// "2h ago" — offset-aware; naive strings parse in the server zone.
export function relTime(naiveIso?: string | null): string {
  const t = parseServerDate(naiveIso)?.getTime()
  if (t == null || Number.isNaN(t)) return ''
  const s = Math.max(0, Math.floor((Date.now() - t) / 1000))
  if (s < 60) return 'just now'
  if (s < 3600) return `${Math.floor(s / 60)}m ago`
  if (s < 86400) return `${Math.floor(s / 3600)}h ago`
  return `${Math.floor(s / 86400)}d ago`
}

function ScopePills({ scope, current }: { scope?: string[] | null; current?: string | null }) {
  const C = useC()
  if (!scope?.length && !current) return <span style={{ color: C.muted }}>—</span>
  const shown = (scope ?? []).slice(0, 3)
  const extra = (scope?.length ?? 0) - shown.length
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap' as const, gap: 4, alignItems: 'center' }}>
      {shown.map(p => (
        <span key={p} style={{
          fontSize: 10, padding: '2px 8px', borderRadius: 10, whiteSpace: 'nowrap' as const,
          background: p === current ? C.bluePale : C.canvas,
          color: p === current ? C.blueDeep : C.sub,
          fontWeight: p === current ? 700 : 400,
        }}>{p}</span>
      ))}
      {extra > 0 && <span style={{ fontSize: 10, color: C.muted }}>+{extra}</span>}
      {current && (
        <span style={{
          fontSize: 10, padding: '2px 8px', borderRadius: 10, background: C.bluePale,
          color: C.blueDeep, fontWeight: 700, whiteSpace: 'nowrap' as const,
        }}>⟳ {current}</span>
      )}
    </div>
  )
}

function RunProgress({ run }: { run: any }) {
  const C = useC()
  if (run.totalExpected == null) return <span style={{ color: C.sub }}>{run.issues}</span>
  const done = run.processedSoFar ?? 0
  const pct = run.totalExpected > 0 ? Math.min(100, Math.round((done / run.totalExpected) * 100)) : 100
  return (
    <div style={{ minWidth: 110 }}>
      <div style={{ fontSize: 11, color: C.sub, marginBottom: 3 }}>
        {done.toLocaleString()}/{run.totalExpected.toLocaleString()}
        {run.pending != null && run.pending > 0 ? ` · ${run.pending.toLocaleString()} pending` : ''}
      </div>
      <div style={{ background: C.border, borderRadius: 4, overflow: 'hidden', height: 5, width: '100%' }}>
        <div style={{ width: `${pct}%`, height: '100%', background: C.indigo, borderRadius: 4, transition: 'width 400ms ease' }} />
      </div>
    </div>
  )
}

const FIELD_MAPS = [
  { jf: 'issuetype.name', to: 'issueType', it: 'All', notes: 'Admin-configured CR/Bug/UAT mapping', ok: true },
  { jf: 'customfield_10201', to: 'milestone.BRD.targetDate', it: 'CR', notes: 'Per-client custom field ID', ok: true },
  { jf: 'customfield_10202', to: 'milestone.FSD.targetDate', it: 'CR', notes: 'Per-client custom field ID', ok: true },
  { jf: 'changelog.transitions', to: 'IssueTransition[]', it: 'All', notes: 'Full history for SLA calc', ok: true },
  { jf: 'priority.name', to: 'severity', it: 'Bug', notes: 'P0=Critical, P1=High, P2=Med, P3=Low', ok: true },
  { jf: 'fixVersions[0].name', to: 'fixVersion', it: 'All', notes: 'Release target date', ok: true },
  { jf: 'customfield_10890', to: 'holdReason', it: 'CR', notes: 'Hold reason text — field not found in Jira', ok: false },
]

function JqlField({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  const C = useC()
  return (
    <div style={{ marginBottom: 10 }}>
      <div style={{ fontSize: 10, color: C.sub, marginBottom: 4, fontWeight: 500 }}>{label}</div>
      <textarea
        value={value}
        onChange={e => onChange(e.target.value)}
        rows={2}
        style={{
          fontFamily: 'monospace', fontSize: 11, color: C.text,
          background: C.white, padding: '7px 10px', borderRadius: 6,
          border: `1px solid ${C.border}`, width: '100%', resize: 'vertical',
          outline: 'none', boxSizing: 'border-box'
        }}
      />
    </div>
  )
}

type SyncResult = { status: string; issuesProcessed: number; durationMs: number; error?: string }

function ProjectConfigCard({ project, canEdit, role }: { project: any; canEdit: boolean; role: string }) {
  const C = useC()
  const qc = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState({
    jiraProjectKeys: project.jiraProjectKeys || '',
    jiraJqlOverride: project.jiraJqlOverride || '',
    jiraCrFilter:    project.jiraCrFilter || '',
    jiraBugFilter:   project.jiraBugFilter || '',
  })
  const [syncResult, setSyncResult] = useState<SyncResult | null>(null)
  const [syncing, setSyncing] = useState(false)

  const canFullSync = ['ADMIN', 'HEAD_PJM'].includes(role)

  const save = useMutation({
    mutationFn: () => api.put(`/projects/${project.id}/jira-config`, form),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['projects'] }); setEditing(false) }
  })

  const triggerSync = async (type: 'delta' | 'full') => {
    setSyncing(true)
    setSyncResult(null)
    try {
      const res = await api.post(`/projects/${project.id}/sync`, { type })
      setSyncResult(res.data)
    } catch (e: any) {
      setSyncResult({ status: 'Failed', issuesProcessed: 0, durationMs: 0, error: e?.response?.data?.error ?? 'Request failed' })
    } finally {
      setSyncing(false)
    }
  }

  return (
    <div style={{ marginBottom: 14, padding: '14px 16px', background: C.canvas, borderRadius: 8, border: `1px solid ${C.border}` }}>
      {/* Header row */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
        <div>
          <div style={{ fontSize: 13, fontWeight: 600, color: C.text }}>{project.name}</div>
          <div style={{ fontSize: 11, color: C.sub, marginTop: 1 }}>
            {project.clientName}{project.portfolioName ? ` · ${project.portfolioName}` : ''}
          </div>
        </div>
        {canEdit && (
          <div style={{ display: 'flex', gap: 6 }}>
            {editing ? (
              <>
                <button onClick={() => save.mutate()} disabled={save.isPending}
                  style={{ fontSize: 11, padding: '4px 12px', borderRadius: 6, cursor: 'pointer', border: 'none', background: C.indigo, color: '#fff', fontWeight: 500 }}>
                  {save.isPending ? 'Saving…' : 'Save'}
                </button>
                <button onClick={() => setEditing(false)}
                  style={{ fontSize: 11, padding: '4px 10px', borderRadius: 6, cursor: 'pointer', border: `1px solid ${C.border}`, background: C.white, color: C.sub }}>
                  Cancel
                </button>
              </>
            ) : (
              <button onClick={() => setEditing(true)}
                style={{ fontSize: 11, padding: '4px 12px', borderRadius: 6, cursor: 'pointer', border: `1px solid ${C.border}`, background: C.white, color: C.indigo, fontWeight: 500 }}>
                Edit config
              </button>
            )}
          </div>
        )}
      </div>

      {/* Jira config fields */}
      {editing ? (
        <>
          <div style={{ marginBottom: 10 }}>
            <div style={{ fontSize: 10, color: C.sub, marginBottom: 4, fontWeight: 500 }}>Jira project keys (comma-separated)</div>
            <input value={form.jiraProjectKeys} onChange={e => setForm({ ...form, jiraProjectKeys: e.target.value })}
              placeholder="e.g. NX, CRM"
              style={{ fontFamily: 'monospace', fontSize: 11, color: C.text, background: C.white, padding: '7px 10px', borderRadius: 6, border: `1px solid ${C.border}`, width: '100%', outline: 'none', boxSizing: 'border-box' as const }} />
          </div>
          <JqlField label="Delta sync JQL" value={form.jiraJqlOverride} onChange={v => setForm({ ...form, jiraJqlOverride: v })} />
          <JqlField label="CR filter" value={form.jiraCrFilter} onChange={v => setForm({ ...form, jiraCrFilter: v })} />
          <JqlField label="Bug filter" value={form.jiraBugFilter} onChange={v => setForm({ ...form, jiraBugFilter: v })} />
        </>
      ) : (
        [
          ['Jira project keys', project.jiraProjectKeys],
          ['Delta sync JQL',    project.jiraJqlOverride],
          ['CR filter',         project.jiraCrFilter],
          ['Bug filter',        project.jiraBugFilter],
        ].map(([lbl, val]) => (
          <div key={lbl} style={{ marginBottom: 8 }}>
            <div style={{ fontSize: 10, color: C.sub, marginBottom: 3 }}>{lbl}</div>
            <div style={{ fontFamily: 'monospace', fontSize: 11, color: val ? C.text : C.muted, background: C.white, padding: '6px 10px', borderRadius: 6, border: `1px solid ${C.border}` }}>
              {val || 'not configured'}
            </div>
          </div>
        ))
      )}

      {/* On-demand sync strip */}
      <div style={{ marginTop: 12, paddingTop: 12, borderTop: `1px solid ${C.border}`, display: 'flex', alignItems: 'center', gap: 8 }}>
        <span style={{ fontSize: 11, color: C.sub, flexShrink: 0 }}>Sync now:</span>
        <button
          onClick={() => triggerSync('delta')}
          disabled={syncing}
          style={{ fontSize: 11, padding: '4px 12px', borderRadius: 6, cursor: syncing ? 'not-allowed' : 'pointer', border: `1px solid ${C.border}`, background: C.white, color: syncing ? C.muted : C.indigo, fontWeight: 500 }}
        >
          {syncing ? '⟳ Syncing…' : '↻ Delta'}
        </button>
        {canFullSync && (
          <button
            onClick={() => triggerSync('full')}
            disabled={syncing}
            style={{ fontSize: 11, padding: '4px 12px', borderRadius: 6, cursor: syncing ? 'not-allowed' : 'pointer', border: `1px solid ${C.border}`, background: C.white, color: syncing ? C.muted : C.sub, fontWeight: 500 }}
          >
            Full
          </button>
        )}

        {/* Inline result */}
        {syncResult && (
          <span style={{
            fontSize: 11, padding: '3px 10px', borderRadius: 20, fontWeight: 500,
            background: syncResult.status === 'Success' ? C.greenPale : C.redPale,
            color: syncResult.status === 'Success' ? C.greenDeep : C.redDeep,
          }}>
            {syncResult.status === 'Success'
              ? `✓ ${syncResult.issuesProcessed} issue${syncResult.issuesProcessed !== 1 ? 's' : ''} · ${(syncResult.durationMs / 1000).toFixed(1)}s`
              : `✗ ${syncResult.error ?? 'Failed'}`
            }
          </span>
        )}
      </div>
    </div>
  )
}

function FieldRow({ label, value }: { label: string; value: string }) {
  const C = useC()
  return (
    <div style={{ marginBottom: 14 }}>
      <div style={{ fontSize: 11, color: C.sub, marginBottom: 5, fontWeight: 500 }}>{label}</div>
      <input
        readOnly
        value={value}
        style={{
          fontSize: 12, padding: '7px 10px', borderRadius: 6,
          border: `1px solid ${C.border}`, background: C.canvas,
          color: C.text, outline: 'none', width: '100%',
          fontFamily: label.toLowerCase().includes('url') || label.toLowerCase().includes('token') ? 'monospace' : 'inherit',
          boxSizing: 'border-box' as const
        }}
      />
    </div>
  )
}

function ConnectionAndWebhookTab({ config, canEdit }: { config: any; canEdit: boolean }) {
  const C = useC()
  const qc = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [showToken, setShowToken] = useState(false)
  const [form, setForm] = useState({
    baseUrl:       config?.baseUrl       ?? '',
    email:         config?.email         ?? '',
    apiToken:      '',
    webhookSecret: '',
  })

  const save = useMutation({
    mutationFn: () => api.put('/jira-sync/config', {
      baseUrl:       form.baseUrl,
      email:         form.email,
      apiToken:      form.apiToken      || undefined,
      webhookSecret: form.webhookSecret || undefined,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['jira-config'] })
      setEditing(false)
      setShowToken(false)
      setForm(f => ({ ...f, apiToken: '', webhookSecret: '' }))
    }
  })

  const infoRows: [string, string][] = [
    ['Webhook URL (paste into Jira)', config?.webhookUrl ?? ''],
    ['Events',      config?.webhookEvents     ?? ''],
    ['Validation',  config?.webhookValidation ?? ''],
    ['Retry policy',config?.webhookRetry      ?? ''],
  ]

  return (
    <div>
      {/* ── Jira connection ─────────────────────────────────────────── */}
      <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: '18px 20px', marginBottom: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
          <div>
            <div style={{ fontSize: 14, fontWeight: 600, color: C.text }}>Jira connection</div>
            <div style={{ fontSize: 11, color: C.sub, marginTop: 2 }}>
              Credentials used for delta sync and API calls to your Jira workspace
            </div>
          </div>
          {canEdit && !editing && (
            <button
              onClick={() => { setEditing(true); setForm({ baseUrl: config?.baseUrl ?? '', email: config?.email ?? '', apiToken: '', webhookSecret: '' }) }}
              style={{ fontSize: 11, padding: '5px 14px', borderRadius: 6, cursor: 'pointer', border: `1px solid ${C.border}`, background: C.white, color: C.indigo, fontWeight: 500 }}
            >
              Edit
            </button>
          )}
        </div>

        {editing ? (
          <>
            <div style={{ marginBottom: 14 }}>
              <div style={{ fontSize: 11, color: C.sub, marginBottom: 5, fontWeight: 500 }}>Jira base URL</div>
              <input
                value={form.baseUrl}
                onChange={e => setForm({ ...form, baseUrl: e.target.value })}
                placeholder="https://your-org.atlassian.net"
                style={{ fontSize: 12, padding: '7px 10px', borderRadius: 6, border: `1px solid ${C.border}`, background: C.white, color: C.text, outline: 'none', width: '100%', fontFamily: 'monospace', boxSizing: 'border-box' as const }}
              />
            </div>
            <div style={{ marginBottom: 14 }}>
              <div style={{ fontSize: 11, color: C.sub, marginBottom: 5, fontWeight: 500 }}>Jira account email</div>
              <input
                type="email"
                value={form.email}
                onChange={e => setForm({ ...form, email: e.target.value })}
                placeholder="admin@your-org.com"
                style={{ fontSize: 12, padding: '7px 10px', borderRadius: 6, border: `1px solid ${C.border}`, background: C.white, color: C.text, outline: 'none', width: '100%', boxSizing: 'border-box' as const }}
              />
            </div>
            <div style={{ marginBottom: 14 }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 5 }}>
                <div style={{ fontSize: 11, color: C.sub, fontWeight: 500 }}>
                  API token {config?.apiTokenSet ? <span style={{ color: C.green, marginLeft: 4 }}>● set</span> : <span style={{ color: C.amber, marginLeft: 4 }}>● not set</span>}
                </div>
                <button onClick={() => setShowToken(s => !s)} style={{ fontSize: 10, color: C.indigo, background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}>
                  {showToken ? 'Hide' : config?.apiTokenSet ? 'Replace token' : 'Set token'}
                </button>
              </div>
              {showToken && (
                <input
                  type="password"
                  value={form.apiToken}
                  onChange={e => setForm({ ...form, apiToken: e.target.value })}
                  placeholder="Paste new API token"
                  autoComplete="new-password"
                  style={{ fontSize: 12, padding: '7px 10px', borderRadius: 6, border: `1px solid ${C.border}`, background: C.white, color: C.text, outline: 'none', width: '100%', boxSizing: 'border-box' as const }}
                />
              )}
              {!showToken && config?.apiTokenSet && (
                <div style={{ fontSize: 12, color: C.muted, padding: '7px 10px', background: C.canvas, borderRadius: 6, border: `1px solid ${C.border}` }}>
                  •••••••••••••••
                </div>
              )}
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <button
                onClick={() => save.mutate()}
                disabled={save.isPending}
                style={{ fontSize: 12, padding: '7px 16px', borderRadius: 6, border: 'none', background: C.indigo, color: '#fff', cursor: 'pointer', fontWeight: 500 }}
              >
                {save.isPending ? 'Saving…' : 'Save connection'}
              </button>
              <button
                onClick={() => { setEditing(false); setShowToken(false) }}
                style={{ fontSize: 12, padding: '7px 14px', borderRadius: 6, border: `1px solid ${C.border}`, background: C.white, color: C.sub, cursor: 'pointer' }}
              >
                Cancel
              </button>
            </div>
          </>
        ) : (
          <>
            <FieldRow label="Jira base URL" value={config?.baseUrl || 'Not configured'} />
            <FieldRow label="Jira account email" value={config?.email || 'Not configured'} />
            <div style={{ marginBottom: 14 }}>
              <div style={{ fontSize: 11, color: C.sub, marginBottom: 5, fontWeight: 500 }}>
                API token
                {config?.apiTokenSet
                  ? <span style={{ color: C.green, marginLeft: 6, fontSize: 10, fontWeight: 400 }}>● set</span>
                  : <span style={{ color: C.amber, marginLeft: 6, fontSize: 10, fontWeight: 400 }}>● not set</span>
                }
              </div>
              <div style={{ fontSize: 12, color: C.muted, padding: '7px 10px', background: C.canvas, borderRadius: 6, border: `1px solid ${C.border}` }}>
                {config?.apiTokenSet ? '•••••••••••••••' : 'No token configured'}
              </div>
            </div>
            {config?.updatedBy && (
              <div style={{ fontSize: 11, color: C.muted }}>
                Last updated by {config.updatedBy}{config.updatedAt ? ` · ${fmtDate(config.updatedAt)}` : ''}
              </div>
            )}
          </>
        )}
      </div>

      {/* ── Webhook configuration ────────────────────────────────────── */}
      <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: '18px 20px' }}>
        <div style={{ marginBottom: 14 }}>
          <div style={{ fontSize: 14, fontWeight: 600, color: C.text }}>Webhook endpoint</div>
          <div style={{ fontSize: 11, color: C.sub, marginTop: 2 }}>
            Register this URL in your Jira project settings under System → WebHooks
          </div>
        </div>

        {/* Webhook URL with copy button */}
        <div style={{ marginBottom: 16 }}>
          <div style={{ fontSize: 11, color: C.sub, marginBottom: 5, fontWeight: 500 }}>Webhook URL</div>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <div style={{ flex: 1, fontFamily: 'monospace', fontSize: 12, color: C.text, background: C.canvas, padding: '8px 12px', borderRadius: 6, border: `1px solid ${C.border}` }}>
              {config?.webhookUrl ?? 'https://orbit.internal/api/jira/webhook'}
            </div>
            <button
              onClick={() => navigator.clipboard?.writeText(config?.webhookUrl ?? '')}
              style={{ fontSize: 11, padding: '7px 12px', borderRadius: 6, border: `1px solid ${C.border}`, background: C.white, color: C.sub, cursor: 'pointer', whiteSpace: 'nowrap' as const }}
            >
              Copy
            </button>
          </div>
        </div>

        {/* Webhook secret with update option */}
        <div style={{ marginBottom: 16 }}>
          <div style={{ fontSize: 11, color: C.sub, marginBottom: 5, fontWeight: 500 }}>Webhook secret</div>
          {editing ? (
            <input
              type="password"
              value={form.webhookSecret}
              onChange={e => setForm({ ...form, webhookSecret: e.target.value })}
              placeholder="Enter new webhook secret"
              autoComplete="new-password"
              style={{ fontSize: 12, padding: '7px 10px', borderRadius: 6, border: `1px solid ${C.border}`, background: C.white, color: C.text, outline: 'none', width: '100%', boxSizing: 'border-box' as const }}
            />
          ) : (
            <div style={{ fontSize: 12, color: C.muted, padding: '7px 10px', background: C.canvas, borderRadius: 6, border: `1px solid ${C.border}` }}>
              {config?.webhookSecret || '•••••••• (not configured)'}
            </div>
          )}
        </div>

        {/* Info rows */}
        {infoRows.map(([k, v]) => v ? (
          <div key={k} style={{ display: 'flex', gap: 12, padding: '8px 0', borderTop: `1px solid ${C.border}` }}>
            <div style={{ fontSize: 12, fontWeight: 500, color: C.sub, width: 110, flexShrink: 0 }}>{k}</div>
            <div style={{ fontSize: 12, color: C.text }}>{v}</div>
          </div>
        ) : null)}
      </div>
    </div>
  )
}

export function JiraIntegration() {
  const C = useC()
  const user = useStore(s => s.user)
  const role = user?.role ?? 'PJM'
  const isPjmOnly = role === 'PJM'

  const [tab, setTab] = useState(isPjmOnly ? 'Project config' : 'Sync runs')
  const [page, setPage] = useState(0)
  const [logRun, setLogRun] = useState<any>(null)

  const allTabs = ['Sync runs', 'Field mapping', 'JQL config', 'Project config', 'Connection & Webhook']
  const tabs = isPjmOnly ? ['Project config'] : allTabs

  const { data: runsPage, isLoading, error } = useQuery({
    queryKey: ['sync-runs', page],
    queryFn: () => api.get(`/jira-sync/runs?page=${page}&size=${PAGE_SIZE}`).then(r => r.data),
    enabled: !isPjmOnly,
    // Keep progress bars moving while a sync is running
    refetchInterval: q => (q.state.data?.content ?? []).some((r: any) => r.status === 'Running') ? 5000 : false,
  })

  const { data: fieldMappings = [] } = useQuery({
    queryKey: ['field-mappings'],
    queryFn: () => api.get('/jira-sync/field-mappings').then(r => r.data),
    enabled: !isPjmOnly
  })

  const { data: jiraConfig } = useQuery({
    queryKey: ['jira-config'],
    queryFn: () => api.get('/jira-sync/config').then(r => r.data),
    enabled: !isPjmOnly
  })

  const { data: syncStats } = useQuery({
    queryKey: ['sync-stats'],
    queryFn: () => api.get('/jira-sync/stats').then(r => r.data),
    enabled: !isPjmOnly
  })

  const { data: projects = [] } = useQuery({
    queryKey: ['projects'],
    queryFn: () => api.get('/projects').then(r => r.data)
  })

  const qc = useQueryClient()
  const [globalSyncing, setGlobalSyncing] = useState(false)
  const [globalSyncResult, setGlobalSyncResult] = useState<{ status: string; issuesProcessed: number; durationMs: number; error?: string } | null>(null)

  const triggerGlobalSync = async (type: 'delta' | 'full') => {
    setGlobalSyncing(true)
    setGlobalSyncResult(null)
    try {
      const res = await api.post('/jira-sync/trigger', { type })
      setGlobalSyncResult(res.data)
      qc.invalidateQueries({ queryKey: ['sync-runs'] })
      qc.invalidateQueries({ queryKey: ['sync-stats'] })
    } catch (e: any) {
      setGlobalSyncResult({ status: 'Failed', issuesProcessed: 0, durationMs: 0, error: e?.response?.data?.error ?? 'Request failed' })
    } finally {
      setGlobalSyncing(false)
    }
  }

  const paged = (runsPage?.content ?? []) as any[]
  const totalPages = runsPage?.totalPages ?? 0
  const latestRun = page === 0 ? paged[0] : undefined
  const displayMaps = (fieldMappings as any[]).length > 0 ? fieldMappings as any[] : FIELD_MAPS

  if (isLoading) return <div style={{ padding: 40, color: C.sub }}>Loading…</div>
  if (error) return <ErrorState error={error} />

  const canEdit = ['ADMIN', 'HEAD_PJM', 'PJM'].includes(role)
  const canEditConfig = role === 'ADMIN'

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 18 }}>
        <div>
          <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>Jira sync health</div>
          <div style={{ fontSize: 12, color: C.sub, marginTop: 3 }}>
            {isPjmOnly ? 'Configure JQL and project keys for your projects' : 'Connection · webhook · delta sync · field mapping · JQL config'}
          </div>
        </div>
        {!isPjmOnly && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' as const }}>
            <div style={{
              display: 'flex', alignItems: 'center', gap: 6, padding: '6px 12px', borderRadius: 8, fontSize: 12, fontWeight: 500,
              background: latestRun?.status === 'Failed' ? C.redPale : C.greenPale,
              color: latestRun?.status === 'Failed' ? C.redDeep : C.greenDeep,
            }}>
              <span style={{ width: 7, height: 7, borderRadius: '50%', background: latestRun?.status === 'Failed' ? C.red : C.green }} />
              {latestRun
                ? `${latestRun.status === 'Failed' ? 'Last sync failed' : 'Healthy'} · synced ${relTime(latestRun.startedAt)}`
                : 'No syncs yet'}
            </div>
            <button
              onClick={() => triggerGlobalSync('delta')}
              disabled={globalSyncing}
              style={{ fontSize: 12, padding: '6px 14px', borderRadius: 7, border: `1px solid ${C.border}`, background: C.white, color: globalSyncing ? C.muted : C.sub, cursor: globalSyncing ? 'not-allowed' : 'pointer' }}
            >
              {globalSyncing ? '⟳ Syncing…' : 'Force delta sync'}
            </button>
            <button
              onClick={() => triggerGlobalSync('full')}
              disabled={globalSyncing}
              style={{ fontSize: 12, padding: '6px 14px', borderRadius: 7, border: 'none', background: globalSyncing ? C.muted : C.indigo, color: '#fff', cursor: globalSyncing ? 'not-allowed' : 'pointer', fontWeight: 500 }}
            >
              {globalSyncing ? '⟳ Syncing…' : 'Force full sync'}
            </button>
            {globalSyncResult && (
              <span style={{
                fontSize: 11, padding: '4px 12px', borderRadius: 20, fontWeight: 500,
                background: globalSyncResult.status === 'Success' ? C.greenPale : C.redPale,
                color: globalSyncResult.status === 'Success' ? C.greenDeep : C.redDeep,
              }}>
                {globalSyncResult.status === 'Success'
                  ? `✓ ${globalSyncResult.issuesProcessed} issues · ${(globalSyncResult.durationMs / 1000).toFixed(1)}s`
                  : `✗ ${globalSyncResult.error ?? 'Failed'}`}
              </span>
            )}
          </div>
        )}
      </div>

      {!isPjmOnly && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 10, marginBottom: 20 }}>
          <StatCard label="Issues synced" value={syncStats?.issuesSynced ?? '—'} sub={syncStats?.issuesSyncedSub ?? ''} icon="🔄" />
          <StatCard label="Webhooks today" value={syncStats?.webhooksToday ?? '—'} color={C.green} sub={syncStats?.webhooksSub ?? ''} icon="⚡" />
          <StatCard label="Delta syncs today" value={syncStats?.deltaSyncs ?? '—'} sub={syncStats?.deltaSyncsSub ?? ''} icon="⏱" />
          <StatCard label="Last full sync" value={syncStats?.lastFullSync ?? '—'} sub={syncStats?.lastFullSyncSub ?? ''} icon="✓" />
        </div>
      )}

      <Tabs items={tabs} active={tab} onChange={(t) => { setTab(t); setPage(0) }} />

      {tab === 'Sync runs' && (
        <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden' }}>
          <table style={{ width: '100%', fontSize: 12 }}>
            <THead cols={['Started', 'Type', 'Progress', 'Status', 'Scope', 'Duration', '']} />
            <tbody>
              {paged.length === 0 && (
                <tr><td colSpan={7} style={{ padding: '20px', textAlign: 'center', color: C.muted }}>No data yet</td></tr>
              )}
              {paged.map((r: any, i: number) => (
                <tr key={r.id ?? i} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none', background: r.status === 'Failed' ? C.redPale : C.white }}>
                  <td style={{ padding: '10px 12px' }}>
                    <div style={{ fontWeight: 500, color: C.text }}>{r.time}</div>
                    <div style={{ fontSize: 10, color: C.muted }}>{relTime(r.startedAt)}{r.triggeredBy ? ` · by ${r.triggeredBy}` : ''}</div>
                  </td>
                  <td style={{ padding: '10px 12px' }}><Badge level={r.type === 'Full' ? 'blue' : r.type === 'Webhook' ? 'teal' : 'neutral'} label={r.type} /></td>
                  <td style={{ padding: '10px 12px', color: C.sub }}><RunProgress run={r} /></td>
                  <td style={{ padding: '10px 12px' }}><StatusPill status={r.status} /></td>
                  <td style={{ padding: '10px 12px', maxWidth: 260 }}>
                    <ScopePills scope={r.projectScope ?? (r.projectName ? [r.projectName] : null)} current={r.currentProject} />
                  </td>
                  <td style={{ padding: '10px 12px', color: C.sub }}>{r.dur}</td>
                  <td style={{ padding: '10px 12px' }}><button onClick={() => setLogRun(r)} style={{ fontSize: 11, padding: '3px 9px', borderRadius: 5, cursor: 'pointer', border: `1px solid ${C.border}`, background: 'transparent', color: C.indigo, fontWeight: 500 }}>View log</button></td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
        </div>
      )}

      {tab === 'Field mapping' && (
        <>
        <CustomFieldMappingCard config={jiraConfig} canEdit={role === 'ADMIN'} />
        <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden' }}>
          <table style={{ width: '100%', fontSize: 12 }}>
            <THead cols={['Jira field', 'Maps to', 'Issue type', 'Notes', 'Status']} />
            <tbody>
              {displayMaps.map((m: any, i: number) => (
                <tr key={i} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none', background: m.ok ? C.white : C.redPale }}>
                  <td style={{ padding: '10px 12px', fontFamily: 'monospace', fontSize: 11, color: C.indigo }}>{m.jf}</td>
                  <td style={{ padding: '10px 12px', fontFamily: 'monospace', fontSize: 11, color: C.teal }}>{m.to}</td>
                  <td style={{ padding: '10px 12px', color: C.sub }}>{m.it}</td>
                  <td style={{ padding: '10px 12px', color: C.sub, fontSize: 11 }}>{m.notes}</td>
                  <td style={{ padding: '10px 12px' }}><Badge level={m.ok ? 'healthy' : 'critical'} label={m.ok ? 'Mapped' : 'Not mapped'} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        </>
      )}

      {tab === 'JQL config' && (
        <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: '16px 18px' }}>
          <div style={{ fontSize: 12, color: C.sub, marginBottom: 16 }}>
            Read-only view of all project JQL. Edit individual project configs in the <strong>Project config</strong> tab.
          </div>
          {(projects as any[]).map((p: any, i: number) => (
            <div key={i} style={{ marginBottom: 14, padding: '12px 14px', background: C.canvas, borderRadius: 8 }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: C.text, marginBottom: 10 }}>
                {p.name}
                <span style={{ fontSize: 11, fontWeight: 400, color: C.sub, marginLeft: 8 }}>
                  {p.clientName}{p.portfolioName ? ` · ${p.portfolioName}` : ''}
                </span>
              </div>
              {[['Delta sync JQL', p.jiraJqlOverride], ['CR filter', p.jiraCrFilter], ['Bug filter', p.jiraBugFilter]].map(([lbl, val]) => (
                <div key={lbl} style={{ marginBottom: 8 }}>
                  <div style={{ fontSize: 10, color: C.sub, marginBottom: 3 }}>{lbl}</div>
                  <div style={{ fontFamily: 'monospace', fontSize: 11, color: val ? C.text : C.muted, background: C.white, padding: '6px 10px', borderRadius: 6, border: `1px solid ${C.border}` }}>
                    {val || 'not configured'}
                  </div>
                </div>
              ))}
            </div>
          ))}
        </div>
      )}

      {tab === 'Project config' && (
        <div>
          <div style={{ fontSize: 12, color: C.sub, marginBottom: 16 }}>
            Configure Jira project keys and JQL filters per project.
            {canEdit ? ' Changes take effect on the next sync.' : ''}
          </div>
          {(projects as any[]).length === 0 && (
            <div style={{ padding: '40px 0', textAlign: 'center', color: C.muted, fontSize: 13 }}>No projects found</div>
          )}
          {(projects as any[]).map((p: any) => (
            <ProjectConfigCard key={p.id} project={p} canEdit={canEdit} role={role} />
          ))}
        </div>
      )}

      {tab === 'Connection & Webhook' && (
        <ConnectionAndWebhookTab config={jiraConfig} canEdit={canEditConfig} />
      )}

      {logRun && (
        <Modal title={`Sync run #${logRun.id ?? ''}`} onClose={() => setLogRun(null)}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10, fontSize: 12 }}>
            <div style={{ display: 'grid', gridTemplateColumns: '120px 1fr', gap: 8 }}>
              <div style={{ color: C.sub }}>Type</div>
              <div><Badge level={logRun.type === 'Full' ? 'blue' : logRun.type === 'Webhook' ? 'teal' : 'neutral'} label={logRun.type} /></div>
              <div style={{ color: C.sub }}>Status</div>
              <div><StatusPill status={logRun.status} /></div>
              <div style={{ color: C.sub }}>Triggered by</div>
              <div>{logRun.triggeredBy || '—'}</div>
              <div style={{ color: C.sub }}>Scope</div>
              <div><ScopePills scope={logRun.projectScope ?? (logRun.projectName ? [logRun.projectName] : null)} current={logRun.currentProject} /></div>
              <div style={{ color: C.sub }}>Progress</div>
              <div><RunProgress run={logRun} /></div>
              <div style={{ color: C.sub }}>Started</div>
              <div style={{ fontFamily: 'monospace' }}>{fmtDateTimeFull(logRun.startedAt)}</div>
              <div style={{ color: C.sub }}>Completed</div>
              <div style={{ fontFamily: 'monospace' }}>{fmtDateTimeFull(logRun.completedAt)}</div>
              <div style={{ color: C.sub }}>Duration</div>
              <div>{logRun.durationMs != null ? `${(logRun.durationMs / 1000).toFixed(2)}s` : '—'}</div>
              <div style={{ color: C.sub }}>Issues processed</div>
              <div>{logRun.issues}</div>
            </div>
            <div style={{ marginTop: 6 }}>
              <div style={{ fontWeight: 500, marginBottom: 4 }}>Error message</div>
              <pre style={{
                background: C.canvas, border: `1px solid ${C.border}`, borderRadius: 6,
                padding: '10px 12px', fontSize: 11, whiteSpace: 'pre-wrap', margin: 0,
                color: logRun.errorMessage ? C.redDeep : C.muted, maxHeight: 300, overflow: 'auto'
              }}>{logRun.errorMessage || 'No errors recorded.'}</pre>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}
