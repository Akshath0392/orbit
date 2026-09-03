// Snapshot mode is enabled by the Playwright sidecar passing `?snapshot=1` on
// the URL. Pages and the shell consult this helper to:
//   - hide chrome (sidebar, copilot panel) so the artifact is just the page,
//   - read pre-selected portfolio / lens / project from the URL,
//   - set `data-snapshot-ready="true"` once data is loaded so the sidecar can capture.
export interface SnapshotParams {
  enabled: boolean
  portfolioId: number | null
  lens: string | null   // overrides activePersona for this render
  projectId: number | null
}

export function readSnapshotParams(): SnapshotParams {
  if (typeof window === 'undefined') {
    return { enabled: false, portfolioId: null, lens: null, projectId: null }
  }
  const sp = new URLSearchParams(window.location.search)
  const enabled = sp.get('snapshot') === '1'
  if (!enabled) return { enabled: false, portfolioId: null, lens: null, projectId: null }
  const num = (k: string) => {
    const v = sp.get(k)
    if (!v) return null
    const n = Number(v)
    return Number.isFinite(n) ? n : null
  }
  return {
    enabled: true,
    portfolioId: num('portfolio'),
    lens:       sp.get('lens'),
    projectId:  num('project'),
  }
}

export const SNAPSHOT_READY_ATTR = 'data-snapshot-ready'
