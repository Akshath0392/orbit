import { useState } from 'react'
import { useC } from '../../../design/ThemeContext'
import { Modal } from '../../../design/components/Modal'
import { SegmentBar } from '../../../design/components/SegmentBar'
import { LoadingState, EmptyState } from '../../../design/components/PageState'
import { AmDrillModal } from './AmDrillModal'
import { AmDrillFilters, useAmCsatDrill } from './useAmApi'

interface AmCsatDrillModalProps {
  portfolioId: number | null
  podName: string
  onClose: () => void
}

// W11 CSAT drill (mock csat page) — per client of the POD, one segment bar per
// work type split Backlog / In Progress / Closed. Despite the name this is a
// pure Jira work-mix view; clicking a CR bar opens the standard CR drill list.
export function AmCsatDrillModal({ portfolioId, podName, onClose }: AmCsatDrillModalProps) {
  const C = useC()
  const { data, isLoading } = useAmCsatDrill(portfolioId, true)
  const [drill, setDrill] = useState<{ title: string; filters: AmDrillFilters } | null>(null)
  const clients: any[] = data?.clients ?? []

  const drillFor = (client: string, workType: string): AmDrillFilters | null => {
    if (workType === 'Launch · CRs') return { portfolioId, clientName: client, type: 'LAUNCH' }
    if (workType === 'BAU · CRs') return { portfolioId, clientName: client, type: 'BAU' }
    return null // UAT / Prod bugs have no CR drill — bar stays informational
  }

  return (
    <Modal title={`Work mix by client — ${podName}`} width={860} onClose={onClose}>
      {isLoading ? <LoadingState /> : clients.length === 0 ? (
        <EmptyState message="No synced work for this POD" icon="✓" />
      ) : (
        <>
          <div style={{ display: 'flex', gap: 16, marginBottom: 16, fontSize: 12, fontWeight: 700, color: C.text, flexWrap: 'wrap' }}>
            <span><Key color={C.borderMed} /> Backlog</span>
            <span><Key color={C.indigo} /> In progress</span>
            <span><Key color={C.green} /> Closed</span>
            <span style={{ color: C.sub, fontWeight: 600 }}>· click a CR bar for the list</span>
          </div>
          {clients.map((cl: any) => (
            <div key={cl.client} style={{ marginBottom: 18 }}>
              <b style={{ fontSize: 14, color: C.text, display: 'block', marginBottom: 8 }}>{cl.client}</b>
              {(cl.groups ?? []).map((g: any) => {
                const filters = drillFor(cl.client, g.workType)
                return (
                  <div key={g.workType} style={{ display: 'grid', gridTemplateColumns: '160px 1fr 46px', gap: 10, alignItems: 'center', padding: '4px 0' }}>
                    <span style={{ fontSize: 12, color: C.sub, fontWeight: 600 }}>{g.workType}</span>
                    <div style={{ cursor: filters ? 'pointer' : 'default' }}
                      onClick={filters ? () => setDrill({ title: `${cl.client} — ${g.workType}`, filters }) : undefined}>
                      <SegmentBar segments={[
                        { label: 'Backlog', value: Number(g.backlog), color: C.borderMed },
                        { label: 'In progress', value: Number(g.inProgress), color: C.indigo },
                        { label: 'Closed', value: Number(g.closed), color: C.green },
                      ]} />
                    </div>
                    <span style={{ fontSize: 12, fontWeight: 700, color: C.text, textAlign: 'right' }}>{g.total}</span>
                  </div>
                )
              })}
            </div>
          ))}
        </>
      )}
      {drill && <AmDrillModal title={drill.title} filters={drill.filters} onClose={() => setDrill(null)} />}
    </Modal>
  )
}

function Key({ color }: { color: string }) {
  return <i style={{ display: 'inline-block', width: 11, height: 11, borderRadius: 3, verticalAlign: -1, marginRight: 5, background: color }} />
}
