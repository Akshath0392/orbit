import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useC } from '../../../design/ThemeContext'
import { Badge } from '../../../design/components/Badge'
import { Modal } from '../../../design/components/Modal'
import { Btn } from '../../../design/components/Btn'
import { api } from '../../../api/client'
import { Feature } from '../../../app/featureFlags'

// Chart presentation per role (flag section.charts.config).
// '' = key absent = that chart family's default. Runtime switcher grants the
// role an in-page chart-type toggle next to the charts themselves.
const CHART_OPTIONS: { key: string; label: string; opts: [string, string][] }[] = [
  { key: 'chartType',          label: 'Trend widgets',     opts: [['', 'Default (line)'], ['line', 'Line'], ['bar', 'Bar'], ['stacked', 'Stacked bar']] },
  { key: 'breakdownChartType', label: 'Breakdown charts',  opts: [['', 'Default (bar)'], ['bar', 'Bar'], ['line', 'Line']] },
  { key: 'palette',            label: 'Palette',           opts: [['', 'Default'], ['classic', 'Classic'], ['vibrant', 'Vibrant']] },
  { key: 'runtimeToggle',      label: 'Runtime switcher',  opts: [['', 'Off (default)'], ['on', 'On']] },
]

const ALL_SCREENS = [
  { id: 'radar',    label: 'Orbitter',          section: 'Workspace' },
  { id: 'cockpit',  label: 'My today',          section: 'Workspace' },
  { id: 'cr',       label: 'CR board',          section: 'Workspace' },
  { id: 'bugs',     label: 'Bug triage',        section: 'Workspace' },
  { id: 'uat',      label: 'UAT tracker',       section: 'Workspace' },
  { id: 'mandays',  label: 'Man-days',          section: 'Workspace' },
  { id: 'alerts',   label: 'Alert center',      section: 'Workspace' },
  { id: 'reports',  label: 'Reports',           section: 'Workspace' },
  { id: 'capacity', label: 'Capacity & team',   section: 'Workspace' },
  { id: 'clients',  label: 'Client backlog',    section: 'Workspace' },
  { id: 'integrations', label: 'Integrations',  section: 'System' },
  { id: 'audit',    label: 'Agent audit log',   section: 'System' },
  { id: 'admin',    label: 'Admin console',     section: 'Admin' },
]

const SECTIONS = ['Workspace', 'System', 'Admin']

export function RolesManagementPage() {
  const C = useC()
  const qc = useQueryClient()
  const [modal, setModal] = useState<'edit' | 'add' | null>(null)
  const [selected, setSelected] = useState<any>(null)
  const [screenDraft, setScreenDraft] = useState<string[]>([])
  const [chartDraft, setChartDraft] = useState<Record<string, string>>({})
  const [newRole, setNewRole] = useState({ roleName: '', displayName: '', screenIds: [] as string[] })

  const { data: roles = [], isLoading } = useQuery({
    queryKey: ['admin-roles'],
    queryFn: () => api.get('/admin/roles').then(r => r.data)
  })

  const updateScreens = useMutation({
    mutationFn: ({ roleName, screenIds, displayName }: any) =>
      api.put(`/admin/roles/${roleName}/screens`, { screenIds, displayName }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-roles'] }); qc.invalidateQueries({ queryKey: ['role-screens'] }); setModal(null) }
  })

  // Saved alongside screens on the same Save click; {} clears to defaults.
  const updateChartConfig = useMutation({
    mutationFn: ({ roleName, config }: { roleName: string; config: Record<string, string> }) =>
      api.put(`/admin/roles/${roleName}/chart-config`, config),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-roles'] })
  })

  const createRole = useMutation({
    mutationFn: (body: any) => api.post('/admin/roles', body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-roles'] }); setModal(null); setNewRole({ roleName: '', displayName: '', screenIds: [] }) }
  })

  const openEdit = (r: any) => {
    setSelected(r)
    setScreenDraft([...r.screenIds])
    setChartDraft({ ...(r.chartConfig ?? {}) })
    setModal('edit')
  }

  const setChartOpt = (key: string, value: string) => {
    setChartDraft(prev => {
      const next = { ...prev }
      if (value === '') delete next[key]; else next[key] = value
      return next
    })
  }

  const toggleScreen = (id: string, draft: string[], setDraft: (v: string[]) => void) => {
    setDraft(draft.includes(id) ? draft.filter(s => s !== id) : [...draft, id])
  }

  const roleList = roles as any[]

  if (isLoading) return <div style={{ padding: 40, color: C.sub }}>Loading…</div>

  return (
    <div style={{ padding: '22px 24px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 18 }}>
        <div>
          <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>Roles & permissions</div>
          <div style={{ fontSize: 12, color: C.sub, marginTop: 3 }}>
            Configure which screens each role can access · changes apply on next login
          </div>
        </div>
        <Btn onClick={() => setModal('add')}>+ New role</Btn>
      </div>

      <div style={{ background: C.amberPale, border: `1px solid ${C.amber}40`, borderRadius: 8, padding: '10px 14px', marginBottom: 18, fontSize: 12, color: C.amberDeep }}>
        <strong>Note:</strong> Screen access is managed here. API-level permissions (e.g. who can edit budgets) are enforced in code and require a deploy to change.
      </div>

      {/* Permission matrix */}
      <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'auto', marginBottom: 16 }}>
        <table style={{ width: '100%', fontSize: 11, borderCollapse: 'collapse', minWidth: 700 }}>
          <thead>
            <tr style={{ background: C.canvas }}>
              <th style={{ padding: '10px 14px', textAlign: 'left', fontWeight: 600, color: C.sub, fontSize: 10, letterSpacing: 0.4, borderBottom: `1px solid ${C.border}`, width: 160 }}>SCREEN</th>
              {roleList.map((r: any) => (
                <th key={r.roleName} style={{ padding: '10px 12px', textAlign: 'center', fontWeight: 600, color: C.sub, fontSize: 10, letterSpacing: 0.4, borderBottom: `1px solid ${C.border}`, borderLeft: `1px solid ${C.border}` }}>
                  {r.displayName}
                </th>
              ))}
              <th style={{ padding: '10px 12px', borderBottom: `1px solid ${C.border}`, borderLeft: `1px solid ${C.border}`, width: 60 }} />
            </tr>
          </thead>
          <tbody>
            {SECTIONS.map(section => (
              <>
                <tr key={`sec-${section}`}>
                  <td colSpan={roleList.length + 2} style={{ padding: '6px 14px', background: C.canvas, fontSize: 9, fontWeight: 600, color: C.muted, letterSpacing: 0.7 }}>
                    {section.toUpperCase()}
                  </td>
                </tr>
                {ALL_SCREENS.filter(s => s.section === section).map((screen, si) => (
                  <tr key={screen.id} style={{ borderTop: `1px solid ${C.border}`, background: si % 2 === 0 ? C.white : C.canvas }}>
                    <td style={{ padding: '8px 14px', color: C.text, fontWeight: 400 }}>{screen.label}</td>
                    {roleList.map((r: any) => {
                      const has = (r.screenIds as string[]).includes(screen.id)
                      return (
                        <td key={r.roleName} style={{ padding: '8px 12px', textAlign: 'center', borderLeft: `1px solid ${C.border}` }}>
                          <span style={{ fontSize: 14, color: has ? C.green : C.border }}>
                            {has ? '✓' : '—'}
                          </span>
                        </td>
                      )
                    })}
                    <td style={{ borderLeft: `1px solid ${C.border}` }} />
                  </tr>
                ))}
              </>
            ))}
          </tbody>
        </table>
      </div>

      {/* Role cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12 }}>
        {roleList.map((r: any) => (
          <div key={r.roleName} style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 10, padding: '14px 16px' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }}>
              <div>
                <div style={{ fontSize: 13, fontWeight: 600, color: C.text }}>{r.displayName}</div>
                <div style={{ fontFamily: 'monospace', fontSize: 10, color: C.muted, marginTop: 2 }}>{r.roleName}</div>
              </div>
              <Badge level="neutral" label={`${(r.screenIds as string[]).length} screens`} />
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, marginBottom: 10 }}>
              {(r.screenIds as string[]).map((sid: string) => {
                const s = ALL_SCREENS.find(x => x.id === sid)
                return s ? (
                  <span key={sid} style={{ fontSize: 10, padding: '2px 7px', borderRadius: 10, background: C.indigoPale, color: C.purpleDeep }}>
                    {s.label}
                  </span>
                ) : null
              })}
            </div>
            <button
              onClick={() => openEdit(r)}
              style={{ fontSize: 11, padding: '4px 12px', borderRadius: 6, cursor: 'pointer', border: `1px solid ${C.border}`, background: 'transparent', color: C.indigo, fontWeight: 500 }}
            >
              Edit permissions
            </button>
          </div>
        ))}
      </div>

      {/* Edit role modal */}
      {modal === 'edit' && selected && (
        <Modal title={`Edit permissions — ${selected.displayName}`} onClose={() => setModal(null)}>
          <div style={{ fontSize: 12, color: C.sub, marginBottom: 14 }}>Select which screens this role can access:</div>
          {SECTIONS.map(section => (
            <div key={section} style={{ marginBottom: 14 }}>
              <div style={{ fontSize: 10, fontWeight: 600, color: C.muted, letterSpacing: 0.7, marginBottom: 8 }}>{section.toUpperCase()}</div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                {ALL_SCREENS.filter(s => s.section === section).map(s => {
                  const on = screenDraft.includes(s.id)
                  return (
                    <label key={s.id} style={{ display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer', fontSize: 12, padding: '5px 10px', borderRadius: 6, border: `1px solid ${on ? C.indigo : C.border}`, background: on ? C.indigoPale : C.white, color: on ? C.purpleDeep : C.text }}>
                      <input type="checkbox" checked={on} onChange={() => toggleScreen(s.id, screenDraft, setScreenDraft)} style={{ display: 'none' }} />
                      {on ? '✓ ' : ''}{s.label}
                    </label>
                  )
                })}
              </div>
            </div>
          ))}
          <Feature flag="section.charts.config">
            <div style={{ marginBottom: 14, paddingTop: 12, borderTop: `1px solid ${C.border}` }}>
              <div style={{ fontSize: 10, fontWeight: 600, color: C.muted, letterSpacing: 0.7, marginBottom: 4 }}>
                DASHBOARD CHARTS
              </div>
              <div style={{ fontSize: 11.5, color: C.sub, marginBottom: 10 }}>
                How this role sees the dashboard charts — chart type per family, color palette, and whether users can switch chart types themselves next to the charts.
              </div>
              <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
                {CHART_OPTIONS.map(o => (
                  <label key={o.key} style={{ fontSize: 12, color: C.text, display: 'grid', gap: 4 }}>
                    <span style={{ fontWeight: 500 }}>{o.label}</span>
                    <select value={chartDraft[o.key] ?? ''} onChange={e => setChartOpt(o.key, e.target.value)}
                      style={{ fontSize: 12, padding: '6px 8px', borderRadius: 7, border: `1px solid ${C.border}`, background: C.white, color: C.text }}>
                      {o.opts.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
                    </select>
                  </label>
                ))}
              </div>
            </div>
          </Feature>
          <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 12 }}>
            <Btn onClick={() => setModal(null)} style={{ background: C.canvas, color: C.sub, border: `1px solid ${C.border}` }}>Cancel</Btn>
            <Btn onClick={() => {
              updateChartConfig.mutate({ roleName: selected.roleName, config: chartDraft })
              updateScreens.mutate({ roleName: selected.roleName, screenIds: screenDraft, displayName: selected.displayName })
            }} disabled={updateScreens.isPending || updateChartConfig.isPending}>
              {updateScreens.isPending || updateChartConfig.isPending ? 'Saving…' : 'Save permissions'}
            </Btn>
          </div>
        </Modal>
      )}

      {/* Add role modal */}
      {modal === 'add' && (
        <Modal title="New role" onClose={() => setModal(null)}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div>
              <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Display name</div>
              <input value={newRole.displayName} onChange={e => setNewRole({ ...newRole, displayName: e.target.value })} placeholder="e.g. Senior PJM"
                style={{ fontSize: 12, padding: '7px 10px', borderRadius: 7, border: `1px solid ${C.border}`, width: '100%', outline: 'none' }} />
            </div>
            <div>
              <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Role key (used in system)</div>
              <input value={newRole.roleName} onChange={e => setNewRole({ ...newRole, roleName: e.target.value.toUpperCase().replace(/\s+/g,'_') })} placeholder="e.g. SENIOR_PJM"
                style={{ fontFamily: 'monospace', fontSize: 12, padding: '7px 10px', borderRadius: 7, border: `1px solid ${C.border}`, width: '100%', outline: 'none' }} />
            </div>
            <div>
              <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 8 }}>Screens access</div>
              {SECTIONS.map(section => (
                <div key={section} style={{ marginBottom: 10 }}>
                  <div style={{ fontSize: 10, fontWeight: 600, color: C.muted, letterSpacing: 0.7, marginBottom: 6 }}>{section.toUpperCase()}</div>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 5 }}>
                    {ALL_SCREENS.filter(s => s.section === section).map(s => {
                      const on = newRole.screenIds.includes(s.id)
                      return (
                        <label key={s.id} style={{ display: 'flex', alignItems: 'center', gap: 5, cursor: 'pointer', fontSize: 11, padding: '4px 9px', borderRadius: 6, border: `1px solid ${on ? C.indigo : C.border}`, background: on ? C.indigoPale : C.white, color: on ? C.purpleDeep : C.text }}>
                          <input type="checkbox" checked={on} onChange={() => toggleScreen(s.id, newRole.screenIds, ids => setNewRole({ ...newRole, screenIds: ids }))} style={{ display: 'none' }} />
                          {on ? '✓ ' : ''}{s.label}
                        </label>
                      )
                    })}
                  </div>
                </div>
              ))}
            </div>
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <Btn onClick={() => setModal(null)} style={{ background: C.canvas, color: C.sub, border: `1px solid ${C.border}` }}>Cancel</Btn>
              <Btn onClick={() => createRole.mutate(newRole)} disabled={!newRole.roleName || !newRole.displayName || createRole.isPending}>
                {createRole.isPending ? 'Creating…' : 'Create role'}
              </Btn>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}
