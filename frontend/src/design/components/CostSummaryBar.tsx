import { useC } from '../ThemeContext'

interface CostSummaryBarProps {
  tokens: number
  costUsd: number
  period?: string
}

export function CostSummaryBar({ tokens, costUsd, period = 'this week' }: CostSummaryBarProps) {
  const C = useC()
  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 16,
      padding: '8px 16px', background: C.canvas,
      border: `1px solid ${C.border}`, borderRadius: 8,
      fontSize: 12
    }}>
      <span style={{ color: C.sub }}>AI cost {period}:</span>
      <span style={{ fontWeight: 600, color: C.text }}>{tokens.toLocaleString()} tokens</span>
      <span style={{ color: C.muted }}>·</span>
      <span style={{ fontWeight: 600, color: C.green }}>${costUsd.toFixed(2)} USD</span>
    </div>
  )
}
