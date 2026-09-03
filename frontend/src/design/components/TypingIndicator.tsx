import { useC } from '../ThemeContext'

export function TypingIndicator() {
  const C = useC()
  return (
    <div style={{ display: 'flex', gap: 4, alignItems: 'center', padding: '8px 12px' }}>
      {[0, 1, 2].map((i) => (
        <div
          key={i}
          style={{
            width: 6, height: 6, borderRadius: '50%',
            background: C.muted,
            animation: `bounce 1.2s ease-in-out ${i * 0.2}s infinite`
          }}
        />
      ))}
      <style>{`
        @keyframes bounce {
          0%, 80%, 100% { transform: scale(0.8); opacity: 0.5; }
          40% { transform: scale(1.2); opacity: 1; }
        }
      `}</style>
    </div>
  )
}
