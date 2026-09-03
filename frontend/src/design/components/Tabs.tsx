import { useC } from '../ThemeContext'

interface TabsProps {
  items: string[]
  active: string
  onChange: (tab: string) => void
}

export function Tabs({ items, active, onChange }: TabsProps) {
  const C = useC()
  return (
    <div style={{ display: 'flex', borderBottom: `1px solid ${C.border}`, marginBottom: 16 }}>
      {items.map((t) => {
        const isActive = active === t
        return (
          <button
            key={t}
            onClick={() => onChange(t)}
            style={{
              padding: '8px 16px', fontSize: 12,
              fontWeight: isActive ? 700 : 400,
              color: isActive ? C.teal : C.sub,
              background: 'transparent', border: 'none', cursor: 'pointer',
              borderBottom: isActive ? `2px solid ${C.teal}` : '2px solid transparent',
              marginBottom: -1,
              transition: 'color 160ms ease, border-color 160ms ease',
              transform: 'none',
            }}
            onMouseEnter={e => { if (!isActive) (e.currentTarget as HTMLElement).style.color = C.text }}
            onMouseLeave={e => { if (!isActive) (e.currentTarget as HTMLElement).style.color = C.sub }}
          >
            {t}
          </button>
        )
      })}
    </div>
  )
}
