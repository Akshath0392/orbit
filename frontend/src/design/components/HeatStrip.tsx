interface HeatStripProps {
  data: number[]
}

const HEAT_COLORS = ['#10B981', '#34D399', '#FCD34D', '#F59E0B', '#F97316', '#EF4444']

export function HeatStrip({ data }: HeatStripProps) {
  return (
    <div style={{ display: 'flex', gap: 2, alignItems: 'flex-end', height: 20 }}>
      {(data || []).map((v, i) => {
        const h = 4 + Math.round(v / 100 * 16)
        const ci = Math.min(5, Math.floor(v / 20))
        return (
          <div
            key={i}
            style={{
              width: 3, height: h, borderRadius: 2,
              background: HEAT_COLORS[ci], opacity: 0.85
            }}
          />
        )
      })}
    </div>
  )
}
