import { useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useC } from '../../../design/ThemeContext'
import type { Colors } from '../../../design/theme'
import { Badge } from '../../../design/components/Badge'
import { StatCard } from '../../../design/components/StatCard'
import { THead } from '../../../design/components/THead'
import { Tabs } from '../../../design/components/Tabs'
import { Pagination } from '../../../design/components/Pagination'
import { api } from '../../../api/client'

const PAGE_SIZE = 15

const WFH_TYPE_LABEL: Record<string, string> = {
  FULL_DAY: 'Full day', HALF_DAY_AM: 'Half day AM', HALF_DAY_PM: 'Half day PM',
}

const ATT_STATUS_COLOR = (C: Colors): Record<string, { bg: string; fg: string }> => ({
  Present:   { bg: C.greenPale,  fg: C.green },
  Absent:    { bg: C.redPale,    fg: C.red },
  Late:      { bg: C.amberPale,  fg: C.amber },
  'Half-day':{ bg: C.bluePale,   fg: C.blue },
  WFH:       { bg: C.tealPale,   fg: C.teal },
  Holiday:   { bg: C.purplePale, fg: C.purple },
})

interface ProviderField {
  key: string
  label: string
  type: string
  required: boolean
  secret: boolean
  placeholder?: string | null
  options: string[]
}

interface Provider {
  key: string
  name: string
  fields: ProviderField[]
}

export function HrmsIntegration() {
  const C  = useC()
  const qc = useQueryClient()
  const [tab,        setTab]       = useState('Sync runs')
  const [page,       setPage]      = useState(0)
  const [editingEmp, setEditingEmp] = useState<number | null>(null)
  const [empIdDraft, setEmpIdDraft] = useState('')

  // Config form state
  const [providerDraft, setProviderDraft] = useState<string | null>(null)
  const [settingsDraft, setSettingsDraft] = useState<Record<string, string>>({})
  const [enabledDraft,  setEnabledDraft]  = useState(false)
  const [cfgLoaded,  setCfgLoaded]  = useState(false)
  const [savingCfg,  setSavingCfg]  = useState(false)
  const [testingCfg, setTestingCfg] = useState(false)
  const [cfgMsg,     setCfgMsg]     = useState('')

  const { data: status } = useQuery({
    queryKey: ['hrms-status'],
    queryFn: () => api.get('/hrms/status').then(r => r.data),
    refetchInterval: 30_000,
  })
  const { data: providers = [] } = useQuery({
    queryKey: ['hrms-providers'],
    queryFn: () => api.get('/hrms/providers').then(r => r.data),
  })
  const { data: runs = [] } = useQuery({
    queryKey: ['hrms-runs'],
    queryFn: () => api.get('/hrms/runs').then(r => r.data),
    enabled: tab === 'Sync runs',
  })
  const { data: employees = [] } = useQuery({
    queryKey: ['hrms-employees'],
    queryFn: () => api.get('/hrms/employees').then(r => r.data),
    enabled: tab === 'Employee mapping',
  })
  const { data: leaveData = [] } = useQuery({
    queryKey: ['hrms-leaves'],
    queryFn: () => api.get('/hrms/leaves').then(r => r.data),
    enabled: tab === 'Leave data',
  })
  const { data: wfhData = [] } = useQuery({
    queryKey: ['hrms-wfh'],
    queryFn: () => api.get('/hrms/wfh').then(r => r.data),
    enabled: tab === 'WFH data',
  })
  const { data: balanceData = [] } = useQuery({
    queryKey: ['hrms-balances'],
    queryFn: () => api.get('/hrms/balances').then(r => r.data),
    enabled: tab === 'Leave balances',
  })
  const { data: attendanceData = [] } = useQuery({
    queryKey: ['hrms-attendance'],
    queryFn: () => api.get('/hrms/attendance').then(r => r.data),
    enabled: tab === 'Attendance',
  })
  const { data: wfhWeek = [] } = useQuery({
    queryKey: ['hrms-wfh-week'],
    queryFn: () => {
      const today = new Date()
      const from  = today.toISOString().split('T')[0]
      const to    = new Date(today.getTime() + 7 * 86400000).toISOString().split('T')[0]
      return api.get(`/hrms/wfh?from=${from}&to=${to}`).then(r => r.data)
    },
  })
  const { data: config, refetch: refetchConfig } = useQuery({
    queryKey: ['hrms-config'],
    queryFn: () => api.get('/hrms/config').then(r => r.data),
    enabled: tab === 'Settings',
  })

  useEffect(() => {
    if (config && !cfgLoaded) {
      setProviderDraft(config.provider ?? null)
      setSettingsDraft(config.settings ?? {})
      setEnabledDraft(!!config.enabled)
      setCfgLoaded(true)
    }
  }, [config, cfgLoaded])

  const triggerSync = useMutation({
    mutationFn: (type: string) => api.post(`/hrms/sync?type=${type}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['hrms-runs'] })
      qc.invalidateQueries({ queryKey: ['hrms-wfh-week'] })
      qc.invalidateQueries({ queryKey: ['hrms-status'] })
    },
  })
  const saveEmpId = useMutation({
    mutationFn: ({ id, hrmsEmpId }: any) => api.patch(`/hrms/employees/${id}/emp-id`, { hrmsEmpId }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['hrms-employees'] }); setEditingEmp(null) },
  })

  const providerList  = providers as Provider[]
  const activeFields  = providerList.find(p => p.key === providerDraft)?.fields ?? []
  const secretsSet    = (config?.secretsSet ?? {}) as Record<string, boolean>

  const selectProvider = (key: string) => {
    setProviderDraft(key || null)
    setSettingsDraft(key === config?.provider ? (config?.settings ?? {}) : {})
    setCfgMsg('')
  }

  const saveConfig = async () => {
    setSavingCfg(true); setCfgMsg('')
    try {
      await api.put('/hrms/config', { provider: providerDraft ?? '', enabled: enabledDraft, settings: settingsDraft })
      qc.invalidateQueries({ queryKey: ['hrms-status'] })
      setCfgLoaded(false)
      refetchConfig()
      setCfgMsg('Configuration saved.')
    } catch { setCfgMsg('Save failed — check values and try again.') }
    finally { setSavingCfg(false) }
  }

  const testConnection = async () => {
    setTestingCfg(true); setCfgMsg('')
    try {
      const res = await api.post('/hrms/test')
      setCfgMsg(res.data.ok ? `✓ ${res.data.message ?? 'Connection OK'}` : `✗ ${res.data.error ?? 'Connection failed'}`)
    } catch { setCfgMsg('✗ Connection test failed') }
    finally { setTestingCfg(false) }
  }

  const runList      = runs       as any[]
  const empList      = employees  as any[]
  const leavList     = leaveData  as any[]
  const wfhList      = wfhData    as any[]
  const balList      = balanceData as any[]
  const attList      = attendanceData as any[]
  const wfhWeekList  = wfhWeek   as any[]

  const pagedRuns    = runList.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)
  const totalRunPgs  = Math.ceil(runList.length / PAGE_SIZE)
  const mapped       = empList.filter((e: any) => e.mapped).length
  const unmapped     = empList.length - mapped
  const approvedLv   = leavList.filter((l: any) => l.status === 'APPROVED').length
  const wfhThisWeek  = wfhWeekList.filter((w: any) => ['APPROVED','PENDING'].includes(w.status)).length
  const connected    = status?.configured && status?.enabled
  const providerName = status?.providerName

  const inp: React.CSSProperties = {
    fontSize: 13, padding: '7px 10px', borderRadius: 7,
    border: `1px solid ${C.border}`, outline: 'none',
    width: '100%', boxSizing: 'border-box', color: C.text, background: C.white,
  }

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 18 }}>
        <div>
          <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>HR System sync</div>
          <div style={{ fontSize: 12, color: C.sub, marginTop: 3 }}>Leave · WFH · Attendance · Leave balances · Employee mapping</div>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <div style={{
            display: 'flex', alignItems: 'center', gap: 6,
            padding: '6px 12px', borderRadius: 8, fontSize: 12, fontWeight: 500,
            background: connected ? C.greenPale : C.amberPale,
            color: connected ? C.green : C.amber,
          }}>
            <span style={{ width: 7, height: 7, borderRadius: '50%', background: connected ? C.green : C.amber }} />
            {connected
              ? `Connected · ${providerName} · synced ${status?.lastSyncAt?.slice(11,16) ?? '—'}`
              : providerName
                ? `${providerName} · not enabled or missing credentials`
                : 'No HR system configured'}
          </div>
          <button onClick={() => triggerSync.mutate('DELTA')} disabled={triggerSync.isPending}
            style={{ fontSize: 12, padding: '6px 14px', borderRadius: 7, border: `1px solid ${C.border}`, background: C.white, color: C.sub, cursor: 'pointer' }}>
            {triggerSync.isPending ? 'Syncing…' : 'Delta sync'}
          </button>
          <button onClick={() => triggerSync.mutate('FULL')} disabled={triggerSync.isPending}
            style={{ fontSize: 12, padding: '6px 14px', borderRadius: 7, border: 'none', background: C.indigo, color: '#fff', cursor: 'pointer', fontWeight: 500 }}>
            Full sync
          </button>
        </div>
      </div>

      {/* Stat cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 10, marginBottom: 20 }}>
        <StatCard label="Mapped employees" value={`${mapped}/${empList.length || '—'}`} color={mapped === empList.length && empList.length > 0 ? C.green : C.amber} sub="HR employee ID linked" icon="👤" />
        <StatCard label="Unmapped"         value={String(unmapped || '—')} color={unmapped > 0 ? C.red : C.green} sub="Need HR employee ID" icon="⚠" />
        <StatCard label="Approved leaves"  value={String(approvedLv || '—')} color={C.blue} sub="upcoming & active" icon="✓" />
        <StatCard label="WFH this week"    value={String(wfhThisWeek || '—')} color={C.teal} sub="approved + pending" icon="🏠" />
      </div>

      <Tabs
        items={['Sync runs','Employee mapping','Leave data','WFH data','Leave balances','Attendance','Settings']}
        active={tab}
        onChange={t => { setTab(t); setPage(0) }}
      />

      {/* ── Sync runs ── */}
      {tab === 'Sync runs' && (
        <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden' }}>
          <table style={{ width: '100%', fontSize: 12 }}>
            <THead cols={['Started','Type','Records','Status','Completed','Error']} />
            <tbody>
              {pagedRuns.length === 0 && <tr><td colSpan={6} style={{ padding: 20, textAlign: 'center', color: C.muted }}>No sync runs yet</td></tr>}
              {pagedRuns.map((r: any, i: number) => (
                <tr key={r.id} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none', background: r.status === 'FAILED' ? C.redPale : C.white }}>
                  <td style={{ padding: '10px 12px', fontWeight: 500, color: C.text }}>{r.startedAt}</td>
                  <td style={{ padding: '10px 12px' }}><Badge level={r.type === 'FULL' ? 'blue' : 'neutral'} label={r.type} /></td>
                  <td style={{ padding: '10px 12px', color: C.sub }}>{r.recordsPulled}</td>
                  <td style={{ padding: '10px 12px' }}><Badge level={r.status === 'SUCCESS' ? 'healthy' : r.status === 'IN_PROGRESS' ? 'info' : 'critical'} label={r.status} /></td>
                  <td style={{ padding: '10px 12px', color: C.sub }}>{r.completedAt}</td>
                  <td style={{ padding: '10px 12px', color: r.errorMessage ? C.red : C.muted, fontSize: 11 }}>{r.errorMessage || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={page} totalPages={totalRunPgs} onPageChange={setPage} />
        </div>
      )}

      {/* ── Employee mapping ── */}
      {tab === 'Employee mapping' && (
        <div>
          <div style={{ fontSize: 12, color: C.sub, marginBottom: 14 }}>
            Map each Orbit user to their HR system employee ID. A Full sync auto-maps by email match.
          </div>
          <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden' }}>
            <table style={{ width: '100%', fontSize: 12 }}>
              <THead cols={['User','Role','HR employee ID','Status','']} />
              <tbody>
                {empList.length === 0 && <tr><td colSpan={5} style={{ padding: 20, textAlign: 'center', color: C.muted }}>No users found</td></tr>}
                {empList.map((u: any, i: number) => (
                  <tr key={u.id} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none', background: C.white }}>
                    <td style={{ padding: '10px 12px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <div style={{ width: 28, height: 28, borderRadius: '50%', background: u.color, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 10, fontWeight: 700, color: '#fff' }}>{u.av}</div>
                        <div>
                          <div style={{ fontWeight: 500, color: C.text }}>{u.name}</div>
                          <div style={{ fontSize: 11, color: C.sub }}>{u.email}</div>
                        </div>
                      </div>
                    </td>
                    <td style={{ padding: '10px 12px', color: C.sub }}>{u.role}</td>
                    <td style={{ padding: '10px 12px' }}>
                      {editingEmp === u.id
                        ? <input autoFocus value={empIdDraft} onChange={e => setEmpIdDraft(e.target.value)} placeholder="e.g. EMP001"
                            style={{ fontFamily: 'monospace', fontSize: 12, padding: '5px 8px', borderRadius: 6, border: `1px solid ${C.indigo}`, outline: 'none', width: 120, color: C.text }} />
                        : <span style={{ fontFamily: 'monospace', fontSize: 12, color: u.hrmsEmpId ? C.indigo : C.muted }}>{u.hrmsEmpId || 'not set'}</span>
                      }
                    </td>
                    <td style={{ padding: '10px 12px' }}><Badge level={u.mapped ? 'healthy' : 'risk'} label={u.mapped ? 'Mapped' : 'Unmapped'} /></td>
                    <td style={{ padding: '10px 12px' }}>
                      {editingEmp === u.id
                        ? <div style={{ display: 'flex', gap: 5 }}>
                            <button onClick={() => saveEmpId.mutate({ id: u.id, hrmsEmpId: empIdDraft })} style={{ fontSize: 11, padding: '3px 10px', borderRadius: 5, cursor: 'pointer', border: 'none', background: C.indigo, color: '#fff', fontWeight: 500 }}>Save</button>
                            <button onClick={() => setEditingEmp(null)} style={{ fontSize: 11, padding: '3px 8px', borderRadius: 5, cursor: 'pointer', border: `1px solid ${C.border}`, background: 'transparent', color: C.sub }}>✕</button>
                          </div>
                        : <button onClick={() => { setEditingEmp(u.id); setEmpIdDraft(u.hrmsEmpId || '') }}
                            style={{ fontSize: 11, padding: '3px 9px', borderRadius: 5, cursor: 'pointer', border: `1px solid ${C.border}`, background: 'transparent', color: C.indigo, fontWeight: 500 }}>
                            {u.hrmsEmpId ? 'Edit' : 'Map'}
                          </button>
                      }
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* ── Leave data ── */}
      {tab === 'Leave data' && (
        <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden' }}>
          <table style={{ width: '100%', fontSize: 12 }}>
            <THead cols={['Employee','Leave type','From','To','Days','Status','HR record ID','Synced']} />
            <tbody>
              {leavList.length === 0 && <tr><td colSpan={8} style={{ padding: 20, textAlign: 'center', color: C.muted }}>No leave records. Run a sync to pull from your HR system.</td></tr>}
              {leavList.map((l: any, i: number) => (
                <tr key={l.id} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none', background: C.white }}>
                  <td style={{ padding: '10px 12px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
                      <div style={{ width: 24, height: 24, borderRadius: '50%', background: l.color, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 9, fontWeight: 700, color: '#fff' }}>{l.av}</div>
                      <span style={{ fontWeight: 500, color: C.text }}>{l.name}</span>
                    </div>
                  </td>
                  <td style={{ padding: '10px 12px', color: C.sub }}>{l.leaveType}</td>
                  <td style={{ padding: '10px 12px', color: C.text }}>{l.from}</td>
                  <td style={{ padding: '10px 12px', color: C.text }}>{l.to}</td>
                  <td style={{ padding: '10px 12px', fontWeight: 500, color: C.text }}>{l.days}d</td>
                  <td style={{ padding: '10px 12px' }}><Badge level={l.status==='APPROVED'?'healthy':l.status==='PENDING'?'watch':l.status==='CANCELLED'?'neutral':'critical'} label={l.status} /></td>
                  <td style={{ padding: '10px 12px', fontFamily: 'monospace', fontSize: 11, color: C.muted }}>{l.hrmsLeaveId}</td>
                  <td style={{ padding: '10px 12px', fontSize: 11, color: C.muted }}>{l.syncedAt}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* ── WFH data ── */}
      {tab === 'WFH data' && (
        <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden' }}>
          <table style={{ width: '100%', fontSize: 12 }}>
            <THead cols={['Employee','Date','Type','Status','Reason','HR record ID','Synced']} />
            <tbody>
              {wfhList.length === 0 && <tr><td colSpan={7} style={{ padding: 20, textAlign: 'center', color: C.muted }}>No WFH records. Run a sync to pull from your HR system.</td></tr>}
              {wfhList.map((w: any, i: number) => (
                <tr key={w.id} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none', background: C.white }}>
                  <td style={{ padding: '10px 12px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
                      <div style={{ width: 24, height: 24, borderRadius: '50%', background: w.color, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 9, fontWeight: 700, color: '#fff' }}>{w.av}</div>
                      <span style={{ fontWeight: 500, color: C.text }}>{w.name}</span>
                    </div>
                  </td>
                  <td style={{ padding: '10px 12px', fontWeight: 500, color: C.text }}>{w.wfhDate}</td>
                  <td style={{ padding: '10px 12px' }}>
                    <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 10, fontWeight: 500, background: w.wfhType === 'FULL_DAY' ? C.tealPale : C.indigoPale, color: w.wfhType === 'FULL_DAY' ? C.teal : C.indigo }}>
                      {WFH_TYPE_LABEL[w.wfhType] ?? w.wfhType}
                    </span>
                  </td>
                  <td style={{ padding: '10px 12px' }}><Badge level={w.status==='APPROVED'?'healthy':w.status==='PENDING'?'watch':w.status==='CANCELLED'?'neutral':'critical'} label={w.status} /></td>
                  <td style={{ padding: '10px 12px', color: C.sub, fontSize: 11 }}>{w.reason || '—'}</td>
                  <td style={{ padding: '10px 12px', fontFamily: 'monospace', fontSize: 11, color: C.muted }}>{w.hrmsWfhId}</td>
                  <td style={{ padding: '10px 12px', fontSize: 11, color: C.muted }}>{w.syncedAt}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* ── Leave balances ── */}
      {tab === 'Leave balances' && (
        <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden' }}>
          <table style={{ width: '100%', fontSize: 12 }}>
            <THead cols={['Employee','Leave type','Total','Taken','Pending','Remaining','Synced']} />
            <tbody>
              {balList.length === 0 && <tr><td colSpan={7} style={{ padding: 20, textAlign: 'center', color: C.muted }}>No balance records. Run a sync to pull from your HR system.</td></tr>}
              {balList.map((b: any, i: number) => (
                <tr key={b.id} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none', background: C.white }}>
                  <td style={{ padding: '10px 12px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
                      <div style={{ width: 24, height: 24, borderRadius: '50%', background: b.color, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 9, fontWeight: 700, color: '#fff' }}>{b.av}</div>
                      <span style={{ fontWeight: 500, color: C.text }}>{b.name}</span>
                    </div>
                  </td>
                  <td style={{ padding: '10px 12px', color: C.sub }}>{b.leaveType}</td>
                  <td style={{ padding: '10px 12px', fontWeight: 500, color: C.text }}>{b.totalDays}d</td>
                  <td style={{ padding: '10px 12px', color: C.red }}>{b.takenDays}d</td>
                  <td style={{ padding: '10px 12px', color: C.amber }}>{b.pendingDays}d</td>
                  <td style={{ padding: '10px 12px', fontWeight: 700, color: Number(b.remainingDays) > 5 ? C.green : C.amber }}>{b.remainingDays}d</td>
                  <td style={{ padding: '10px 12px', fontSize: 11, color: C.muted }}>{b.syncedAt}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* ── Attendance ── */}
      {tab === 'Attendance' && (
        <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden' }}>
          <table style={{ width: '100%', fontSize: 12 }}>
            <THead cols={['Employee','Date','Check-in','Check-out','Hours','Status']} />
            <tbody>
              {attList.length === 0 && <tr><td colSpan={6} style={{ padding: 20, textAlign: 'center', color: C.muted }}>No attendance records. Run a sync to pull from your HR system.</td></tr>}
              {attList.map((a: any, i: number) => {
                const sc = ATT_STATUS_COLOR(C)[a.status] ?? { bg: C.canvas, fg: C.sub }
                return (
                  <tr key={a.id} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none', background: C.white }}>
                    <td style={{ padding: '10px 12px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
                        <div style={{ width: 24, height: 24, borderRadius: '50%', background: a.color, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 9, fontWeight: 700, color: '#fff' }}>{a.av}</div>
                        <span style={{ fontWeight: 500, color: C.text }}>{a.name}</span>
                      </div>
                    </td>
                    <td style={{ padding: '10px 12px', fontWeight: 500, color: C.text }}>{a.date}</td>
                    <td style={{ padding: '10px 12px', fontFamily: 'monospace', color: C.teal }}>{a.checkIn}</td>
                    <td style={{ padding: '10px 12px', fontFamily: 'monospace', color: C.sub }}>{a.checkOut}</td>
                    <td style={{ padding: '10px 12px', fontWeight: 500, color: C.text }}>{a.workingHours ? `${a.workingHours}h` : '—'}</td>
                    <td style={{ padding: '10px 12px' }}>
                      <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 10, fontWeight: 600, background: sc.bg, color: sc.fg }}>{a.status || '—'}</span>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* ── Settings ── */}
      {tab === 'Settings' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16, maxWidth: 640 }}>
          <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: '20px 22px' }}>
            <div style={{ fontSize: 14, fontWeight: 700, color: C.text, marginBottom: 4 }}>HR system provider</div>
            <div style={{ fontSize: 12, color: C.sub, marginBottom: 18, lineHeight: 1.6 }}>
              Pick your HRMS provider and fill in its credentials — the form below adapts to
              the selected provider. Everything is stored in the database; Orbit works fine
              with no HR system configured (capacity views simply show no leave data).
            </div>
            {config && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 13 }}>
                <div>
                  <label style={{ display: 'block', fontSize: 12, fontWeight: 600, marginBottom: 5, color: C.text }}>Provider</label>
                  <select value={providerDraft ?? ''} onChange={e => selectProvider(e.target.value)} style={inp}>
                    <option value="">Not configured</option>
                    {providerList.map(p => <option key={p.key} value={p.key}>{p.name}</option>)}
                  </select>
                </div>
                {activeFields.map(f => (
                  <div key={f.key}>
                    <label style={{ display: 'block', fontSize: 12, fontWeight: 600, marginBottom: 5, color: C.text }}>
                      {f.label}{f.required ? ' *' : ''}
                    </label>
                    {f.type === 'select' ? (
                      <select value={settingsDraft[f.key] ?? f.options[0] ?? ''}
                        onChange={e => setSettingsDraft(d => ({ ...d, [f.key]: e.target.value }))} style={inp}>
                        {f.options.map(o => <option key={o} value={o}>{o}</option>)}
                      </select>
                    ) : (
                      <input
                        type={f.type === 'password' ? 'password' : f.type === 'number' ? 'number' : 'text'}
                        placeholder={f.secret && secretsSet[f.key]
                          ? '•••••• (set — enter to change)'
                          : f.placeholder ?? ''}
                        value={settingsDraft[f.key] ?? ''}
                        onChange={e => setSettingsDraft(d => ({ ...d, [f.key]: e.target.value }))}
                        style={inp} />
                    )}
                  </div>
                ))}
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <input type="checkbox" id="hrms-enabled" checked={enabledDraft}
                    onChange={e => setEnabledDraft(e.target.checked)} />
                  <label htmlFor="hrms-enabled" style={{ fontSize: 13, color: C.text }}>Enable live sync</label>
                </div>
                {cfgMsg && <div style={{ fontSize: 12, color: cfgMsg.includes('failed') || cfgMsg.startsWith('✗') ? C.red : C.green, fontWeight: 600 }}>{cfgMsg}</div>}
                <div style={{ display: 'flex', gap: 8 }}>
                  <button onClick={saveConfig} disabled={savingCfg}
                    style={{ padding: '9px 20px', borderRadius: 8, border: 'none', background: C.teal, color: '#fff', fontSize: 13, fontWeight: 700, cursor: 'pointer' }}>
                    {savingCfg ? 'Saving…' : 'Save configuration'}
                  </button>
                  <button onClick={testConnection} disabled={testingCfg || !providerDraft}
                    style={{ padding: '9px 16px', borderRadius: 8, border: `1px solid ${C.border}`, background: 'transparent', color: C.sub, fontSize: 13, fontWeight: 600, cursor: 'pointer' }}>
                    {testingCfg ? 'Testing…' : 'Test connection'}
                  </button>
                </div>
              </div>
            )}
          </div>

          <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: '16px 20px' }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: C.text, marginBottom: 10 }}>Webhook receiver</div>
            <div style={{ fontSize: 12, color: C.sub, lineHeight: 1.6, marginBottom: 10 }}>
              Configure this URL in your HR system's admin portal to push leave / WFH /
              employee events. Requests must be HMAC-SHA256 signed with the webhook secret.
            </div>
            <div style={{ fontFamily: 'monospace', fontSize: 12, padding: '8px 12px', background: C.canvas, borderRadius: 7, color: C.indigo, userSelect: 'all' }}>
              POST {window.location.origin.replace(':3000',':8080')}/api/v1/hrms/webhook
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
