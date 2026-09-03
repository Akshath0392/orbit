import { useState } from 'react'
import { useC } from '../../../design/ThemeContext'
import { Modal } from '../../../design/components/Modal'
import { TableWrap } from '../../../design/components/TableWrap'
import { THead } from '../../../design/components/THead'
import { Pagination } from '../../../design/components/Pagination'
import { StatusPill } from '../../../design/components/StatusPill'
import { LoadingState, EmptyState } from '../../../design/components/PageState'
import { AmDrillFilters, useAmCrDrill } from './useAmApi'

interface AmDrillModalProps {
  title: string
  filters: AmDrillFilters
  onClose: () => void
}

// Paginated CR drill list — every matrix/tile click lands here (mock's issues
// view). Paginated per locked convention #2 even though the mock scrolls all rows.
export function AmDrillModal({ title, filters, onClose }: AmDrillModalProps) {
  const C = useC()
  const [page, setPage] = useState(0)
  const { data, isLoading } = useAmCrDrill(filters, page, true)
  const rows: any[] = data?.content ?? []

  return (
    <Modal title={title} onClose={onClose} width={860}>
      {isLoading ? <LoadingState /> : (
        <TableWrap footer={<Pagination page={page} totalPages={data?.totalPages ?? 1} onPageChange={setPage} />}>
          <table style={{ width: '100%', fontSize: 12 }}>
            <THead cols={['CR key', 'Client', 'Description', 'Status', 'Stage', 'Owner', 'Type', 'Aging']} />
            <tbody>
              {rows.length === 0 && (
                <tr><td colSpan={8}><EmptyState message="No open CRs match this cell" icon="✓" /></td></tr>
              )}
              {rows.map((r: any) => (
                <tr key={r.key} style={{ borderTop: `1px solid ${C.border}` }}>
                  <td style={{ padding: '9px 10px', fontWeight: 700, color: C.indigo, whiteSpace: 'nowrap' }}>{r.key}</td>
                  <td style={{ padding: '9px 10px', color: C.text, whiteSpace: 'nowrap' }}>{r.client ?? '—'}</td>
                  <td style={{ padding: '9px 10px', color: C.sub, maxWidth: 280, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={r.summary}>{r.summary}</td>
                  <td style={{ padding: '9px 10px', whiteSpace: 'nowrap' }}><StatusPill status={r.status ?? ''} /></td>
                  <td style={{ padding: '9px 10px', color: C.sub, whiteSpace: 'nowrap' }}>{r.stage ?? '—'}</td>
                  <td style={{ padding: '9px 10px', color: C.sub, whiteSpace: 'nowrap' }}>{r.owner ?? '—'}</td>
                  <td style={{ padding: '9px 10px', color: C.sub, textTransform: 'capitalize' }}>{r.type ?? '—'}</td>
                  <td style={{ padding: '9px 10px', fontWeight: 700, color: r.agingDays > 60 ? C.red : r.agingDays > 30 ? C.amber : C.text, whiteSpace: 'nowrap' }}>
                    {r.agingDays}d
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </TableWrap>
      )}
    </Modal>
  )
}
