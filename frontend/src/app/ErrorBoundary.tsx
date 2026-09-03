import { Component, ReactNode } from 'react'

interface Props { children: ReactNode }
interface State { error: Error | null }

export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  render() {
    if (this.state.error) {
      return (
        <div style={{ fontFamily: 'monospace', padding: 32, background: '#FEF2F2', minHeight: '100vh' }}>
          <div style={{ fontSize: 18, fontWeight: 700, color: '#991B1B', marginBottom: 16 }}>
            ⚠ Application error — React crashed
          </div>
          <div style={{ fontSize: 13, color: '#7F1D1D', marginBottom: 12 }}>
            {this.state.error.message}
          </div>
          <pre style={{ fontSize: 11, color: '#92400E', background: '#FFFBEB', padding: 16, borderRadius: 6, overflow: 'auto', maxHeight: 300 }}>
            {this.state.error.stack}
          </pre>
          <button
            onClick={() => { this.setState({ error: null }); window.location.reload() }}
            style={{ marginTop: 16, padding: '8px 16px', background: '#DC2626', color: '#fff', border: 'none', borderRadius: 6, cursor: 'pointer', fontSize: 13 }}>
            Reload
          </button>
        </div>
      )
    }
    return this.props.children
  }
}
