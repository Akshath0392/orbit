import { useC } from '../ThemeContext'

interface Props { message?: string; icon?: string }

export function LoadingState() {
  const C = useC()
  return (
    <div style={{ padding: '60px 24px', textAlign: 'center', color: C.muted, fontSize: 13 }}>
      <div style={{ fontSize: 20, marginBottom: 10 }}>⟳</div>
      Loading…
    </div>
  )
}

export function EmptyState({ message = 'No data yet', icon = '◫' }: Props) {
  const C = useC()
  return (
    <div style={{ padding: '60px 24px', textAlign: 'center', color: C.muted, fontSize: 13 }}>
      <div style={{ fontSize: 28, marginBottom: 12, opacity: 0.4 }}>{icon}</div>
      <div style={{ fontWeight: 500, color: C.sub }}>{message}</div>
      <div style={{ marginTop: 4, fontSize: 12 }}>Data will appear here once records are added.</div>
    </div>
  )
}

export function ErrorState({ error }: { error: any }) {
  const C = useC()
  const status = error?.response?.status
  if (status === 403) {
    return (
      <div style={{ padding: '60px 24px', textAlign: 'center', color: C.muted, fontSize: 13 }}>
        <div style={{ fontSize: 28, marginBottom: 12, opacity: 0.4 }}>🔒</div>
        <div style={{ fontWeight: 500, color: C.sub }}>Access restricted</div>
        <div style={{ marginTop: 4, fontSize: 12 }}>Your role does not have permission to view this page.</div>
      </div>
    )
  }
  return (
    <div style={{ padding: '60px 24px', textAlign: 'center', color: C.muted, fontSize: 13 }}>
      <div style={{ fontSize: 28, marginBottom: 12, opacity: 0.4 }}>⚠</div>
      <div style={{ fontWeight: 500, color: C.sub }}>Could not load data</div>
      <div style={{ marginTop: 4, fontSize: 12 }}>{error?.message ?? 'Unknown error'}</div>
    </div>
  )
}
