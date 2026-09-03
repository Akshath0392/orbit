import { useC } from '../ThemeContext'

interface AiSuggestionChipProps {
  suggestion: string
  onAccept?: () => void
  onOverride?: () => void
}

export function AiSuggestionChip({ suggestion, onAccept, onOverride }: AiSuggestionChipProps) {
  const C = useC()
  return (
    <div style={{
      display: 'inline-flex', alignItems: 'center', gap: 8,
      padding: '4px 10px', borderRadius: 20,
      background: C.indigoPale, border: `1px solid #C7D2FE`,
      fontSize: 11, color: C.purpleDeep
    }}>
      <span style={{ fontWeight: 600 }}>✦ AI:</span>
      <span>{suggestion}</span>
      {onAccept && (
        <button
          onClick={onAccept}
          style={{
            fontSize: 10, padding: '1px 7px', borderRadius: 10, cursor: 'pointer',
            border: 'none', background: C.indigo, color: '#fff', fontWeight: 600
          }}
        >
          Accept
        </button>
      )}
      {onOverride && (
        <button
          onClick={onOverride}
          style={{
            fontSize: 10, padding: '1px 7px', borderRadius: 10, cursor: 'pointer',
            border: `1px solid ${C.border}`, background: 'transparent',
            color: C.sub, fontWeight: 500
          }}
        >
          Override
        </button>
      )}
    </div>
  )
}
