import { ErrorState } from '../../design/components/PageState'
import { useState, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useC } from '../../design/ThemeContext'
import { Badge } from '../../design/components/Badge'
import { BurnBar } from '../../design/components/BurnBar'
import { StatCard } from '../../design/components/StatCard'
import { THead } from '../../design/components/THead'
import { Pagination } from '../../design/components/Pagination'
import { api } from '../../api/client'

const PAGE_SIZE = 10
const W = 560, H = 80, PAD_LEFT = 30, PAD_RIGHT = 10, PAD_TOP = 8, PAD_BOT = 18

function ForecastChart({ points, purchased, color }: { points: any[]; purchased: number; color: string }) {
  const C = useC()
  if (!points.length) return null
  const maxY = purchased * 1.05
  const toX  = (i: number) => PAD_LEFT + (i / (points.length - 1)) * (W - PAD_LEFT - PAD_RIGHT)
  const toY  = (v: number) => PAD_TOP + (1 - v / maxY) * (H - PAD_TOP - PAD_BOT)
  const todayX = PAD_LEFT

  const upper = points.map((pt: any, i: number) => `${toX(i)},${toY(pt.yhatUpper80)}`).join(' ')
  const lower = [...points].reverse().map((pt: any, i: number) => `${toX(points.length - 1 - i)},${toY(pt.yhatLower80)}`).join(' ')
  const line  = points.map((pt: any, i: number) => `${toX(i)},${toY(pt.yhat)}`).join(' ')
  const pctLine = toY(purchased * 0.8)

  return (
    <svg width="100%" viewBox={`0 0 ${W} ${H}`} style={{ display: 'block' }}>
      <polygon points={`${upper} ${lower}`} fill={color + '18'} />
      <line x1={PAD_LEFT} y1={PAD_TOP} x2={PAD_LEFT} y2={H - PAD_BOT} stroke={C.border} strokeWidth="0.5" />
      <line x1={PAD_LEFT} y1={H - PAD_BOT} x2={W - PAD_RIGHT} y2={H - PAD_BOT} stroke={C.border} strokeWidth="0.5" />
      <line x1={PAD_LEFT} y1={pctLine} x2={W - PAD_RIGHT} y2={pctLine} stroke={C.amber} strokeWidth="0.8" strokeDasharray="3 2" />
      <text x={PAD_LEFT + 2} y={pctLine - 2} style={{ fontSize: 8, fill: C.amber, fontFamily: 'Inter,sans-serif' }}>80% threshold</text>
      <polyline fill="none" stroke={color} strokeWidth="1.8" points={line} />
      <line x1={todayX} y1={PAD_TOP} x2={todayX} y2={H - PAD_BOT} stroke={C.indigo} strokeWidth="0.8" strokeDasharray="2 2" />
      <text x={todayX + 2} y={PAD_TOP + 6} style={{ fontSize: 8, fill: C.indigo, fontFamily: 'Inter,sans-serif' }}>Today</text>
      {points.filter((_: any, i: number) => i % 5 === 0).map((pt: any, i: number) => {
        const idx = i * 5
        const label = pt.ds.slice(5)
        return <text key={idx} x={toX(idx)} y={H - 2} style={{ fontSize: 8, fill: C.muted, fontFamily: 'Inter,sans-serif', textAnchor: 'middle' }}>{label}</text>
      })}
      <text x={PAD_LEFT - 2} y={toY(purchased) + 3} style={{ fontSize: 8, fill: C.muted, fontFamily: 'Inter,sans-serif', textAnchor: 'end' }}>{purchased} MD</text>
      <text x={PAD_LEFT - 2} y={toY(0) + 3} style={{ fontSize: 8, fill: C.muted, fontFamily: 'Inter,sans-serif', textAnchor: 'end' }}>0</text>
    </svg>
  )
}

export function ManDaysPage() {
  const C = useC()
  const [selIdx, setSelIdx] = useState(0)
  const [page, setPage] = useState(0)
  const [selectedProjectId, setSelectedProjectId] = useState<number | null>(null)
  const [toast, setToast] = useState('')

  const { data: mdList = [], isLoading, error } = useQuery({
    queryKey: ['man-days'],
    queryFn: () => api.get('/man-days').then(r => r.data)
  })

  const { data: forecast } = useQuery({
    queryKey: ['forecast', selectedProjectId],
    queryFn: () => selectedProjectId
      ? api.get('/man-days/forecast', { params: { projectId: selectedProjectId } }).then(r => r.data)
      : null,
    enabled: !!selectedProjectId
  })

  useEffect(() => {
    const list = mdList as any[]
    if (list.length > 0 && !selectedProjectId) setSelectedProjectId(list[0].id ?? null)
  }, [mdList])

  const totalPages = Math.ceil((mdList as any[]).length / PAGE_SIZE)
  const paged = (mdList as any[]).slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)
  const globalSelIdx = page * PAGE_SIZE + selIdx

  const p = (mdList as any[])[globalSelIdx] || null
  const pct = p ? Math.round(p.burned / p.total * 100) : 0
  const sc = p ? (p.st === 'critical' ? C.red : p.st === 'warn' ? C.amber : C.green) : C.muted

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
          <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>Man-day consumption</div>
          <div style={{ fontSize: 12, color: C.sub, marginTop: 3 }}>Budget burn across all projects</div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button
            onClick={() => {
              if (!selectedProjectId) {
                setToast('Select a project first, then export')
                setTimeout(() => setToast(''), 3000)
              } else {
                window.open(`/api/v1/man-days?format=excel&projectId=${selectedProjectId}`, '_blank')
              }
            }}
            style={{ fontSize: 12, padding: '6px 14px', borderRadius: 7, border: 'none', background: C.indigo, color: '#fff', cursor: 'pointer', fontWeight: 500 }}
          >
            ↓ Export Excel
          </button>
        </div>
      </div>

      <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden', marginBottom: 20 }}>
        <table style={{ width: '100%', fontSize: 12 }}>
          <THead cols={['Project', 'Client', 'Purchased', 'Burned', 'Remaining', 'Progress', 'Burn rate', 'Forecast', 'Status']} />
          <tbody>
            {paged.length === 0 && (
              <tr>
                <td colSpan={9} style={{ padding: '20px', textAlign: 'center', color: C.muted }}>No data yet</td>
              </tr>
            )}
            {paged.map((proj: any, i: number) => {
              const pc = Math.round(proj.burned / proj.total * 100)
              const sc2 = proj.st === 'critical' ? C.red : proj.st === 'warn' ? C.amber : C.green
              const gIdx = page * PAGE_SIZE + i
              return (
                <tr
                  key={proj.id ?? i}
                  onClick={() => {
                    setSelIdx(i)
                    setSelectedProjectId(proj.id ?? null)
                  }}
                  style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none', cursor: 'pointer', background: selIdx === i ? C.indigoPale : C.white }}
                >
                  <td style={{ padding: '10px 12px', fontWeight: 600, color: C.text }}>{proj.name}</td>
                  <td style={{ padding: '10px 12px', color: C.sub }}>{proj.client}</td>
                  <td style={{ padding: '10px 12px', color: C.sub }}>{proj.total} MD</td>
                  <td style={{ padding: '10px 12px', fontWeight: 600, color: sc2 }}>{proj.burned} MD</td>
                  <td style={{ padding: '10px 12px', color: C.text }}>{proj.total - proj.burned} MD</td>
                  <td style={{ padding: '10px 12px', width: 110 }}>
                    <BurnBar pct={pc} />
                    <span style={{ fontSize: 10, color: C.sub, marginTop: 2, display: 'block' }}>{pc}%</span>
                  </td>
                  <td style={{ padding: '10px 12px', color: C.sub }}>{proj.rate} MD/d {proj.tr === 'up' ? '↑' : proj.tr === 'down' ? '↓' : '→'}</td>
                  <td style={{ padding: '10px 12px', color: sc2, fontWeight: 500 }}>{proj.exh}</td>
                  <td style={{ padding: '10px 12px' }}>
                    <Badge level={proj.st === 'critical' ? 'critical' : proj.st === 'warn' ? 'risk' : 'healthy'} label={proj.st === 'critical' ? 'Critical' : proj.st === 'warn' ? 'At risk' : 'On track'} />
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
        <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
      </div>

      {p && (
        <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: '16px 18px' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
            <div style={{ fontSize: 14, fontWeight: 600, color: C.text }}>{p.name} — burn detail ({p.client})</div>
            <Badge level={p.st === 'critical' ? 'critical' : p.st === 'warn' ? 'risk' : 'healthy'} label={p.st === 'critical' ? 'Critical' : p.st === 'warn' ? 'At risk' : 'On track'} />
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 10, marginBottom: 16 }}>
            <StatCard label="Purchased" value={`${p.total} MD`} sub="Contract budget" />
            <StatCard label="Burned" value={`${p.burned} MD`} color={sc} sub={`${pct}% of budget`} />
            <StatCard label="Remaining" value={`${p.total - p.burned} MD`} color={sc} sub={`~${Math.round((p.total - p.burned) / p.rate)} days left`} />
            <StatCard label="Forecast exhaustion" value={p.exh} color={sc} sub={`${p.rate} MD/day`} />
          </div>
          <div style={{ background: C.canvas, borderRadius: 8, padding: '14px 16px', marginBottom: 12 }}>
            {forecast?.forecast ? (
              <ForecastChart points={forecast.forecast} purchased={forecast.purchased ?? p.total} color={sc} />
            ) : (
              <div style={{ height: 80, display: 'flex', alignItems: 'center', justifyContent: 'center', color: C.muted, fontSize: 12 }}>
                Select a project to load forecast chart
              </div>
            )}
          </div>
          {forecast?.interpretation && (
            <div style={{ padding: '10px 12px', background: C.amberPale, borderRadius: 8, fontSize: 12, color: C.amberDeep, lineHeight: 1.6, borderLeft: `3px solid ${C.amber}`, marginBottom: 10 }}>
              ⚠ ManDayForecastAgent: {forecast.interpretation}
            </div>
          )}
          {!forecast?.interpretation && (
            <div style={{ padding: '10px 12px', background: C.amberPale, borderRadius: 8, fontSize: 12, color: C.amberDeep, lineHeight: 1.6, borderLeft: `3px solid ${C.amber}` }}>
              ⚠ ManDayForecastAgent: At {p.rate} MD/day, budget exhausts <strong>{p.exh}</strong>.{p.tr === 'up' ? ' Burn rate accelerated 29% over last 2 weeks.' : ''} Recommend budget review or scope reduction.
            </div>
          )}
          {forecast?.proposedActions && (forecast.proposedActions as string[]).length > 0 && (
            <div style={{ marginTop: 10 }}>
              <div style={{ fontSize: 11, fontWeight: 600, color: C.sub, letterSpacing: 0.3, textTransform: 'uppercase', marginBottom: 6 }}>Proposed actions</div>
              {(forecast.proposedActions as string[]).map((action: string, i: number) => (
                <div key={i} style={{ fontSize: 12, color: C.text, padding: '4px 0', borderTop: i > 0 ? `1px solid ${C.border}` : 'none' }}>• {action}</div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
