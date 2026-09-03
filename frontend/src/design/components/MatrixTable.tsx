import { useC } from '../ThemeContext'
import { TableWrap } from './TableWrap'

export interface MatrixCell { count: number; avgAgingDays?: number }
export interface MatrixRow {
  label: string
  sublabel?: string          // e.g. "SLA 62% · avg 34d"
  cells: Record<string, MatrixCell>
  total: number
}

interface MatrixTableProps {
  columns: string[]                       // e.g. stage names
  columnSubs?: (React.ReactNode | null)[] // aligned by index with columns; second line in the <th>
  columnTotals?: Record<string, number>
  rows: MatrixRow[]                       // e.g. clients, volume-sorted
  rowHeader: string                       // first-column heading
  onCellClick?: (row: string, col: string) => void
  onColumnClick?: (col: string) => void
  onRowClick?: (row: string) => void
}

// Mock AM stage matrix table: uppercase mint-2 header with SLA sub-line,
// centered clickable counts, quiet '·' dots for zeros, Total row + column on
// the mint-2 tint. Wide matrices scroll via TableWrap; first column sticky.
export function MatrixTable({ columns, columnSubs, columnTotals, rows, rowHeader, onCellClick, onColumnClick, onRowClick }: MatrixTableProps) {
  const C = useC()
  const grand = rows.reduce((s, r) => s + r.total, 0)
  const th: React.CSSProperties = {
    padding: '12px 15px', background: C.mintFaint, color: C.muted, fontWeight: 750,
    fontSize: 11, textTransform: 'uppercase', letterSpacing: 0.4,
    borderBottom: `1px solid ${C.border}`, whiteSpace: 'nowrap',
  }
  const td: React.CSSProperties = {
    padding: '12px 15px', borderBottom: `1px solid ${C.border}`, color: C.sub,
    textAlign: 'center', whiteSpace: 'nowrap',
  }
  const stickyFirst: React.CSSProperties = {
    position: 'sticky', left: 0, background: C.white, zIndex: 1, textAlign: 'left', minWidth: 110,
  }
  return (
    <TableWrap>
      <table style={{ width: '100%', fontSize: 13, borderCollapse: 'collapse' }}>
        <thead>
          <tr>
            <th style={{ ...th, ...stickyFirst, background: C.mintFaint }}>{rowHeader}</th>
            {columns.map((col, i) => (
              <th key={col}
                onClick={onColumnClick ? () => onColumnClick(col) : undefined}
                style={{ ...th, textAlign: 'center', cursor: onColumnClick ? 'pointer' : 'default' }}>
                {col}
                {columnTotals && <div style={{ fontSize: 10, color: C.muted, fontWeight: 700 }}>{columnTotals[col] ?? 0}</div>}
                {columnSubs?.[i] != null && (
                  <div style={{ fontWeight: 700, fontSize: 9.5, marginTop: 2, textTransform: 'none', letterSpacing: 0 }}>{columnSubs[i]}</div>
                )}
              </th>
            ))}
            <th style={{ ...th, textAlign: 'center' }}>Total</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(row => (
            <tr key={row.label}>
              <td onClick={onRowClick ? () => onRowClick(row.label) : undefined}
                style={{ ...td, ...stickyFirst, cursor: onRowClick ? 'pointer' : 'default' }}>
                <b style={{ fontWeight: 700, color: onRowClick ? C.tealDeep : C.text }}>{row.label}</b>
                {row.sublabel && <div style={{ fontSize: 10, color: C.muted, marginTop: 1 }}>{row.sublabel}</div>}
              </td>
              {columns.map(col => {
                const cell = row.cells[col]
                return (
                  <td key={col}
                    onClick={cell && onCellClick ? () => onCellClick(row.label, col) : undefined}
                    title={cell?.avgAgingDays != null ? `avg ${cell.avgAgingDays}d aging` : undefined}
                    style={{ ...td, cursor: cell && onCellClick ? 'pointer' : 'default' }}>
                    {cell
                      ? <b style={{ fontWeight: 700, color: C.text }}>{cell.count}</b>
                      : <span style={{ color: C.borderMed }}>·</span>}
                  </td>
                )
              })}
              <td style={{ ...td, background: C.mintFaint }}><b style={{ fontWeight: 700, color: C.text }}>{row.total}</b></td>
            </tr>
          ))}
          <tr style={{ background: C.mintFaint }}>
            <td style={{ ...td, ...stickyFirst, background: C.mintFaint, borderBottom: 'none' }}><b style={{ color: C.text }}>Total</b></td>
            {columns.map(col => (
              <td key={col} style={{ ...td, borderBottom: 'none' }}>
                <b style={{ color: C.text }}>{rows.reduce((s, r) => s + (r.cells[col]?.count ?? 0), 0)}</b>
              </td>
            ))}
            <td style={{ ...td, borderBottom: 'none' }}><b style={{ color: C.text }}>{grand}</b></td>
          </tr>
        </tbody>
      </table>
    </TableWrap>
  )
}
