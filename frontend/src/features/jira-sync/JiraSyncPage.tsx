import { ErrorState } from '../../design/components/PageState'
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useC } from '../../design/ThemeContext'
import { Badge } from '../../design/components/Badge'
import { StatCard } from '../../design/components/StatCard'
import { THead } from '../../design/components/THead'
import { Tabs } from '../../design/components/Tabs'
import { Pagination } from '../../design/components/Pagination'
import { api } from '../../api/client'
import { useStore } from '../../app/store'
import { useJiraBackfillStatus } from '../radar/am/useAmApi'
import { fmtDate } from '../../lib/datetime'

const PAGE_SIZE = 10

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

// Custom-field id mappings (widget-parity plan F2) — same jira_config row as
// the connection settings; blank mapping keeps the dependent feature dark.
const MAPPED_FIELDS: [string, string, string][] = [
  ['slaField', 'SLA (JSM)', 'e.g. customfield_10020 — SLA status/remaining'],
  ['storyPointsField', 'Story points', 'e.g. customfield_10016 — velocity & sprint metrics'],
  ['sprintField', 'Sprint', 'e.g. customfield_10007 — sprint tags & velocity'],
  ['smField', 'Solutioning Manager', 'user picker field — SM owner donut'],
  ['pjmField', 'Project Manager (PjM)', 'user picker field — PjM owner donut'],
  ['developerField', 'Developer', 'user picker field — developer attribution'],
]

export function CustomFieldMappingCard({ config, canEdit }: { config: any; canEdit: boolean }) {
  const C = useC()
  const qc = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState<Record<string, string>>({})

  const save = useMutation({
    mutationFn: () => api.put('/jira-sync/config', form),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['jira-config'] })
      setEditing(false)
    },
  })

  return (
    <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: '18px 20px', marginBottom: 16 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
        <div>
          <div style={{ fontSize: 14, fontWeight: 600, color: C.text }}>Custom field mappings</div>
          <div style={{ fontSize: 11, color: C.sub, marginTop: 2 }}>
            Jira custom-field ids for SLA, story points, Sprint, SM, PjM and Developer — blank keeps the dependent widget dark
          </div>
        </div>
        {canEdit && !editing && (
          <button
            onClick={() => {
              setForm(Object.fromEntries(MAPPED_FIELDS.map(([k]) => [k, config?.[k] ?? ''])))
              setEditing(true)
            }}
            style={{ fontSize: 11, padding: '5px 14px', borderRadius: 6, cursor: 'pointer', border: `1px solid ${C.border}`, background: C.white, color: C.indigo, fontWeight: 500 }}>
            Edit
          </button>
        )}
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: 12 }}>
        {MAPPED_FIELDS.map(([key, label, hint]) => (
          <div key={key}>
            <div style={{ fontSize: 11, color: C.sub, marginBottom: 4, fontWeight: 500 }}>{label}</div>
            {editing ? (
              <input value={form[key] ?? ''} onChange={e => setForm({ ...form, [key]: e.target.value })}
                placeholder="customfield_…"
                style={{ fontSize: 12, padding: '7px 10px', borderRadius: 6, border: `1px solid ${C.border}`, background: C.white, color: C.text, outline: 'none', width: '100%', fontFamily: 'monospace', boxSizing: 'border-box' as const }} />
            ) : (
              <div style={{ fontFamily: 'monospace', fontSize: 11, color: config?.[key] ? C.text : C.muted, background: C.canvas, padding: '7px 10px', borderRadius: 6, border: `1px solid ${C.border}` }}>
                {config?.[key] || 'not mapped'}
              </div>
            )}
            <div style={{ fontSize: 10, color: C.muted, marginTop: 3 }}>{hint}</div>
          </div>
        ))}
      </div>
      {editing && (
        <div style={{ display: 'flex', gap: 8, marginTop: 14 }}>
          <button onClick={() => save.mutate()} disabled={save.isPending}
            style={{ fontSize: 12, padding: '7px 16px', borderRadius: 6, border: 'none', background: C.indigo, color: '#fff', cursor: 'pointer', fontWeight: 500 }}>
            {save.isPending ? 'Saving…' : 'Save mappings'}
          </button>
          <button onClick={() => setEditing(false)}
            style={{ fontSize: 12, padding: '7px 14px', borderRadius: 6, border: `1px solid ${C.border}`, background: C.white, color: C.sub, cursor: 'pointer' }}>
            Cancel
          </button>
        </div>
      )}
      {canEdit && <ChangelogBackfillRow C={C} />}
    </div>
  )
}

// F3 — one-click changelog history backfill (status/sprint/SP transitions →
// cycle time, reopen counting, committed-SP reconstruction). Resumable; runs
// in slices of 200 issues, so re-trigger until pending reaches 0.
function ChangelogBackfillRow({ C }: { C: any }) {
  const qc = useQueryClient()
  const { data: status } = useJiraBackfillStatus(true)
  const trigger = useMutation({
    mutationFn: () => api.post('/jira-sync/backfill-changelog', {}),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['jira-backfill-status'] })
      qc.invalidateQueries({ queryKey: ['sync-runs'] })
    },
  })
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 16, paddingTop: 14, borderTop: `1px solid ${C.border}` }}>
      <button onClick={() => trigger.mutate()} disabled={status?.running || trigger.isPending}
        style={{ fontSize: 12, padding: '7px 16px', borderRadius: 6, border: 'none',
          background: status?.running ? C.muted : C.indigo, color: '#fff',
          cursor: status?.running ? 'not-allowed' : 'pointer', fontWeight: 500 }}>
        {status?.running ? '⟳ Backfilling…' : 'Backfill changelog history'}
      </button>
      <span style={{ fontSize: 12, color: C.sub }}>
        {status?.pendingIssues != null && `${status.pendingIssues} issues pending`}
        {status?.running && ` · ${status.processedThisRun} processed this run`}
        {status?.lastError && <span style={{ color: C.red }}> · {status.lastError}</span>}
      </span>
      <span style={{ fontSize: 11, color: C.muted }}>
        Feeds cycle time, reopen counts and sprint history (runs in 200-issue slices — re-trigger until 0 pending)
      </span>
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

export function JiraSyncPage() {
  const C = useC()
  const user = useStore(s => s.user)
  const role = user?.role ?? 'PJM'
  const isPjmOnly = role === 'PJM'

  const [tab, setTab] = useState(isPjmOnly ? 'Project config' : 'Sync runs')
  const [page, setPage] = useState(0)

  const allTabs = ['Sync runs', 'Field mapping', 'JQL config', 'Project config', 'Connection & Webhook']
  const tabs = isPjmOnly ? ['Project config'] : allTabs

  const { data: runsPage, isLoading, error } = useQuery({
    queryKey: ['sync-runs', page],
    queryFn: () => api.get(`/jira-sync/runs?page=${page}&size=${PAGE_SIZE}`).then(r => r.data),
    enabled: !isPjmOnly
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

  // /jira-sync/runs is a server-paged envelope {content, page, size, totalPages, totalElements}
  const paged = (runsPage?.content ?? []) as any[]
  const totalPages = runsPage?.totalPages ?? 0
  const displayMaps = (fieldMappings as any[]).length > 0 ? fieldMappings as any[] : FIELD_MAPS

  if (isLoading) return <div style={{ padding: 40, color: C.sub }}>Loading…</div>
  if (error) return <ErrorState error={error} />

  const canEdit = ['ADMIN', 'HEAD_PJM', 'PJM'].includes(role)
  const canEditConfig = role === 'ADMIN'

  return (
    <div style={{ padding: '22px 24px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 18 }}>
        <div>
          <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>Jira sync health</div>
          <div style={{ fontSize: 12, color: C.sub, marginTop: 3 }}>
            {isPjmOnly ? 'Configure JQL and project keys for your projects' : 'Connection · webhook · delta sync · field mapping · JQL config'}
          </div>
        </div>
        {!isPjmOnly && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' as const }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '6px 12px', background: C.greenPale, borderRadius: 8, fontSize: 12, color: C.greenDeep, fontWeight: 500 }}>
              <span style={{ width: 7, height: 7, borderRadius: '50%', background: C.green }} />
              Healthy · synced 4m ago
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
            <THead cols={['Time', 'Type', 'Issues processed', 'Status', 'Duration', '']} />
            <tbody>
              {paged.length === 0 && (
                <tr><td colSpan={6} style={{ padding: '20px', textAlign: 'center', color: C.muted }}>No data yet</td></tr>
              )}
              {paged.map((r: any, i: number) => (
                <tr key={i} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none', background: r.status === 'Failed' ? C.redPale : C.white }}>
                  <td style={{ padding: '10px 12px', fontWeight: 500, color: C.text }}>{r.time}</td>
                  <td style={{ padding: '10px 12px' }}><Badge level={r.type === 'Full' ? 'blue' : r.type === 'Webhook' ? 'teal' : 'neutral'} label={r.type} /></td>
                  <td style={{ padding: '10px 12px', color: C.sub }}>{r.issues}</td>
                  <td style={{ padding: '10px 12px' }}><Badge level={r.status === 'Success' ? 'healthy' : 'critical'} label={r.status} /></td>
                  <td style={{ padding: '10px 12px', color: C.sub }}>{r.dur}</td>
                  <td style={{ padding: '10px 12px' }}><button style={{ fontSize: 11, padding: '3px 9px', borderRadius: 5, cursor: 'pointer', border: `1px solid ${C.border}`, background: 'transparent', color: C.sub }}>View log</button></td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
        </div>
      )}

      {tab === 'Field mapping' && (
        <>
        <CustomFieldMappingCard config={jiraConfig} canEdit={canEditConfig} />
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
    </div>
  )
}
