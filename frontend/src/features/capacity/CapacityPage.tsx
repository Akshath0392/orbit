import { ErrorState } from '../../design/components/PageState'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useC } from '../../design/ThemeContext'
import { Badge } from '../../design/components/Badge'
import { BurnBar } from '../../design/components/BurnBar'
import { StatCard } from '../../design/components/StatCard'
import { THead } from '../../design/components/THead'
import { Tabs } from '../../design/components/Tabs'
import { Select } from '../../design/components/Select'
import { Pagination } from '../../design/components/Pagination'
import { api } from '../../api/client'

const PAGE_SIZE = 10

export function CapacityPage() {
  const C = useC()
  const navigate = useNavigate()
  const [tab, setTab] = useState('Team load')
  const [page, setPage] = useState(0)
  const [toast, setToast] = useState('')
  const [teamFilter, setTeamFilter] = useState('')

  const showToast = (msg: string, ms = 3000) => { setToast(msg); setTimeout(() => setToast(''), ms) }

  const { data: config } = useQuery({
    queryKey: ['capacity-config'],
    queryFn: () => api.get('/capacity/config').then(r => r.data),
    staleTime: 600_000,
  })
  const overloadT = config?.overloadThreshold ?? 85
  const busyT     = config?.busyThreshold     ?? 70
  const utilCol = (v: number) => v > overloadT ? C.red : v > busyT ? C.amber : C.green

  const { data: teams = [] } = useQuery({
    queryKey: ['capacity-teams'],
    queryFn: () => api.get('/capacity/teams').then(r => r.data),
    staleTime: 60_000,
  })

  const { data: team = [], isLoading, error } = useQuery({
    queryKey: ['team', teamFilter],
    queryFn: () => api.get('/capacity/team', { params: teamFilter ? { team: teamFilter } : {} }).then(r => r.data)
  })

  const { data: leaves = [] } = useQuery({
    queryKey: ['leaves'],
    queryFn: () => api.get('/capacity/leaves').then(r => r.data)
  })

  const { data: assigns = [] } = useQuery({
    queryKey: ['assignments'],
    queryFn: () => api.get('/capacity/assignments').then(r => r.data)
  })

  const devs = team as any[]
  const totalPages = Math.ceil(devs.length / PAGE_SIZE)
  const paged = devs.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)

  const available = devs.filter((d: any) => !d.onLeave && d.util <  busyT).length
  const busy      = devs.filter((d: any) => !d.onLeave && d.util >= busyT && d.util <= overloadT).length
  const overloaded= devs.filter((d: any) => !d.onLeave && d.util >  overloadT).length
  const onLeaveToday = devs.filter((d: any) => d.onLeave).length

  if (isLoading) return <div style={{ padding: 40, color: C.sub }}>Loading…</div>
  if (error) return <ErrorState error={error} />

  return (
    <div style={{ padding: '22px 24px' }}>
      {toast && (
        <div style={{
          position: 'fixed', bottom: 24, right: 24, zIndex: 9999,
          padding: '10px 18px', borderRadius: 8, background: C.text,
          color: '#fff', fontSize: 13, fontWeight: 500, boxShadow: '0 8px 24px rgba(0,0,0,0.18)'
        }}>
          {toast}
        </div>
      )}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 18 }}>
        <div>
          <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>Capacity & team</div>
          <div style={{ fontSize: 12, color: C.sub, marginTop: 3 }}>Utilization · leave · assignments</div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <select
            value={teamFilter}
            onChange={e => { setTeamFilter(e.target.value); setPage(0) }}
            style={{ fontSize: 12, padding: '6px 10px', borderRadius: 7, border: `1px solid ${C.border}`, background: C.white, color: C.text }}
          >
            <option value="">All teams</option>
            {teams.map((t: string) => <option key={t} value={t}>{t}</option>)}
          </select>
          <button
            onClick={() => showToast('Assignment management via HR sync — configure it under Integrations → HR System', 4000)}
            style={{ fontSize: 12, padding: '6px 14px', borderRadius: 7, border: 'none', background: C.indigo, color: '#fff', cursor: 'pointer', fontWeight: 500 }}
          >
            + Add assignment
          </button>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 10, marginBottom: 20 }}>
        <StatCard label="Available (<70%)" value={String(available)} color={C.green} sub="" icon="✓" />
        <StatCard label="Busy (70–85%)" value={String(busy)} color={C.amber} sub="" icon="⚡" />
        <StatCard label="Overloaded (>85%)" value={String(overloaded)} color={C.red} sub="" icon="⚠" />
        <StatCard label="On leave today" value={String(onLeaveToday)} sub="" icon="🏖" />
      </div>

      <Tabs items={['Team load', 'Leave calendar', 'Assignments']} active={tab} onChange={(t) => { setTab(t); setPage(0) }} />

      {tab === 'Team load' && (
        <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden' }}>
          <table style={{ width: '100%', fontSize: 12 }}>
            <THead cols={['Developer', 'Team', 'Utilization', 'Status', 'Active tasks', 'Leave upcoming', '']} />
            <tbody>
              {paged.length === 0 && (
                <tr>
                  <td colSpan={7} style={{ padding: '20px', textAlign: 'center', color: C.muted }}>No data yet</td>
                </tr>
              )}
              {paged.map((d: any, i: number) => (
                <tr key={d.id ?? d.name} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none', background: C.white }}>
                  <td style={{ padding: '10px 12px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <div style={{ width: 28, height: 28, borderRadius: '50%', background: d.color, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 10, fontWeight: 700, color: '#fff', flexShrink: 0 }}>{d.av}</div>
                      <span style={{ fontSize: 12, fontWeight: 500, color: C.text }}>{d.name}</span>
                    </div>
                  </td>
                  <td style={{ padding: '10px 12px', color: C.sub }}>{d.team}</td>
                  <td style={{ padding: '10px 12px', width: 160 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <div style={{ flex: 1 }}><BurnBar pct={d.onLeave ? 0 : d.util} h={5} /></div>
                      <span style={{ fontSize: 12, fontWeight: 600, color: d.onLeave ? C.muted : utilCol(d.util), minWidth: 36 }}>{d.onLeave ? '—' : d.util + '%'}</span>
                    </div>
                  </td>
                  <td style={{ padding: '10px 12px' }}>
                    {d.onLeave
                      ? <Badge level="blue" label="On leave" />
                      : <Badge level={d.util > 85 ? 'critical' : d.util > 70 ? 'risk' : 'healthy'} label={d.util > 85 ? 'Overloaded' : d.util > 70 ? 'Busy' : 'Available'} />}
                  </td>
                  <td style={{ padding: '10px 12px', color: C.sub }}>{d.tasks} tasks</td>
                  <td style={{ padding: '10px 12px', color: d.leave ? C.amber : C.muted, fontSize: 11 }}>{d.leave || '—'}</td>
                  <td style={{ padding: '10px 12px' }}>
                    <button
                      onClick={() => navigate('/cr')}
                      style={{ fontSize: 11, padding: '3px 9px', borderRadius: 5, cursor: 'pointer', border: `1px solid ${C.border}`, background: 'transparent', color: C.sub }}
                    >
                      View CRs →
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
        </div>
      )}

      {tab === 'Leave calendar' && (
        <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: '16px 18px' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
            <div style={{ fontSize: 13, fontWeight: 500, color: C.text }}>Upcoming & active leaves</div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 11, color: C.sub }}>
              <span style={{ width: 6, height: 6, borderRadius: '50%', background: C.green, display: 'inline-block' }} />
              Synced from HR system
            </div>
          </div>
          {(leaves as any[]).length === 0 && (
            <div style={{ color: C.muted, fontSize: 13, padding: '20px 0', textAlign: 'center' }}>
              No upcoming leave records. <a href="/integrations" style={{ color: C.indigo }}>Sync from your HR system →</a>
            </div>
          )}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {(leaves as any[]).map((l: any, i: number) => (
              <div key={l.id ?? i} style={{
                display: 'flex', alignItems: 'center', gap: 12,
                padding: '12px 14px', borderRadius: 10,
                background: l.status === 'PENDING' ? C.amberPale : C.indigoPale,
                border: `1px solid ${l.status === 'PENDING' ? C.amber + '40' : C.indigo + '20'}`
              }}>
                <div style={{ width: 36, height: 36, borderRadius: '50%', background: l.color || C.indigo, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 700, color: '#fff' }}>
                  {l.av}
                </div>
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 13, fontWeight: 500, color: C.text }}>{l.dev}</div>
                  <div style={{ fontSize: 11, color: C.sub, marginTop: 1 }}>
                    {l.from} → {l.to} · {l.days} working days · {l.leaveType}
                  </div>
                </div>
                <Badge level={l.status === 'APPROVED' ? 'healthy' : l.status === 'PENDING' ? 'watch' : 'neutral'} label={l.status} />
                <button
                  onClick={() => showToast(`${l.dev} on leave ${l.from} → ${l.to} · ${l.days} days`, 4000)}
                  style={{ fontSize: 11, padding: '3px 9px', borderRadius: 5, cursor: 'pointer', border: `1px solid ${C.border}`, background: C.white, color: C.indigo, fontWeight: 500 }}
                >
                  View impact
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {tab === 'Assignments' && (
        <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden' }}>
          <table style={{ width: '100%', fontSize: 12 }}>
            <THead cols={['Developer', 'Project', 'Allocation', 'Start', 'End', 'Man-days', '']} />
            <tbody>
              {(assigns as any[]).length === 0 && (
                <tr>
                  <td colSpan={7} style={{ padding: '20px', textAlign: 'center', color: C.muted }}>No data yet</td>
                </tr>
              )}
              {(assigns as any[]).map((a: any, i: number) => (
                <tr key={i} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none', background: C.white }}>
                  <td style={{ padding: '10px 12px', fontWeight: 500, color: C.text }}>{a.dev}</td>
                  <td style={{ padding: '10px 12px', color: C.sub }}>{a.proj}</td>
                  <td style={{ padding: '10px 12px', width: 130 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <div style={{ width: 60 }}><BurnBar pct={a.alloc} h={4} /></div>
                      <span style={{ fontSize: 11, color: C.sub }}>{a.alloc}%</span>
                    </div>
                  </td>
                  <td style={{ padding: '10px 12px', color: C.sub }}>{a.start}</td>
                  <td style={{ padding: '10px 12px', color: C.sub }}>{a.end}</td>
                  <td style={{ padding: '10px 12px', fontWeight: 500, color: C.text }}>{a.md} MD</td>
                  <td style={{ padding: '10px 12px' }}>
                    <button
                      onClick={() => showToast('Edit assignments via HR sync')}
                      style={{ fontSize: 11, padding: '3px 9px', borderRadius: 5, cursor: 'pointer', border: `1px solid ${C.border}`, background: 'transparent', color: C.sub }}
                    >
                      Edit
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
