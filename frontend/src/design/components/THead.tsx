import { useC } from '../ThemeContext'

interface SortableCol { label: string; sortKey?: string }
interface THeadProps {
  cols: (string | SortableCol)[]
  sort?: string                       // "<key>,asc" | "<key>,desc"
  onSort?: (sort: string) => void
}

export function THead({ cols, sort, onSort }: THeadProps) {
  const C = useC()
  const [curKey, curDir] = (sort ?? '').split(',')

  const cycleSort = (key: string) => {
    if (!onSort) return
    if (curKey !== key) onSort(`${key},asc`)
    else if (curDir === 'asc') onSort(`${key},desc`)
    else onSort('')   // third click clears
  }

  return (
    <thead>
      <tr style={{ background: C.canvas }}>
        {cols.map((col) => {
          const label = typeof col === 'string' ? col : col.label
          const key   = typeof col === 'string' ? undefined : col.sortKey
          const sortable = !!key && !!onSort
          const isActive = sortable && curKey === key
          const indicator = !sortable ? '' : isActive ? (curDir === 'desc' ? ' ▼' : ' ▲') : ' ↕'
          return (
            <th
              key={label}
              onClick={sortable ? () => cycleSort(key!) : undefined}
              style={{
                padding: '8px 12px', textAlign: 'left',
                fontSize: 11, fontWeight: 600, color: isActive ? C.indigo : C.sub,
                letterSpacing: 0.4, textTransform: 'uppercase',
                borderBottom: `1px solid ${C.border}`, whiteSpace: 'nowrap',
                cursor: sortable ? 'pointer' : 'default',
                userSelect: 'none',
              }}
            >
              {label}<span style={{ fontSize: 9, opacity: isActive ? 1 : 0.4, marginLeft: 2 }}>{indicator}</span>
            </th>
          )
        })}
      </tr>
    </thead>
  )
}
