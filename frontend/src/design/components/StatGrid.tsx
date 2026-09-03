import { StatCard } from './StatCard'

export interface StatGridItem {
  label: string
  value: string | number
  sub?: string
  color?: string
  icon?: string
}

// The standard "N stat tiles across the top of a page" row. auto-fit makes it
// wrap on narrow screens instead of squashing, so it needs no breakpoint logic.
export function StatGrid({ items, minWidth = 150 }: { items: StatGridItem[]; minWidth?: number }) {
  return (
    <div style={{
      display: 'grid',
      gap: 10,
      marginBottom: 20,
      gridTemplateColumns: `repeat(auto-fit, minmax(${minWidth}px, 1fr))`,
    }}>
      {items.map(it => <StatCard key={it.label} {...it} />)}
    </div>
  )
}
