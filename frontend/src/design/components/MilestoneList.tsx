import { useC } from '../ThemeContext'

export interface Milestone { title: string; sub?: string }
export interface MilestoneListProps {
  done: Milestone[]
  upcoming: Milestone[]
}

// Done/upcoming milestone rows (mock acctMilestones render).
export function MilestoneList({ done, upcoming }: MilestoneListProps) {
  const C = useC()
  const group = (header: string, items: Milestone[], bg: string, fg: string, mark: string) => (
    <div>
      <div style={{ fontSize: 10, fontWeight: 800, color: C.muted, textTransform: 'uppercase', letterSpacing: 0.5, marginBottom: 6 }}>
        {header}
      </div>
      {items.length === 0
        ? <div style={{ fontSize: 12, color: C.muted }}>— none derived yet</div>
        : items.map(m => (
          <div key={m.title} style={{ display: 'flex', gap: 8, alignItems: 'flex-start', marginBottom: 8 }}>
            <span style={{
              width: 18, height: 18, borderRadius: '50%', background: bg, color: fg,
              fontSize: 10, display: 'grid', placeItems: 'center', flexShrink: 0, marginTop: 1,
            }}>{mark}</span>
            <div>
              <div style={{ fontSize: 13, fontWeight: 700, color: C.text }}>{m.title}</div>
              {m.sub && <div style={{ fontSize: 11, color: C.muted }}>{m.sub}</div>}
            </div>
          </div>
        ))}
    </div>
  )
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      {group('Recently achieved', done, C.greenPale, C.green, '✓')}
      {group('Upcoming targets', upcoming, C.indigoPale, C.indigo, '○')}
    </div>
  )
}
