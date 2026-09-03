import { useC } from '../ThemeContext'

type Tone = 'red' | 'amber' | 'green' | 'blue' | 'purple' | 'neutral'

// Single source of truth for domain status → colour tone. Pages must not keep
// their own per-status colour maps — extend this table instead.
const TONE: Record<string, Tone> = {
  // RAG / health
  RED: 'red', AMBER: 'amber', GREEN: 'green',
  Red: 'red', Amber: 'amber', Green: 'green',
  // severity
  critical: 'red', risk: 'amber', info: 'blue',
  P0: 'red', P1: 'amber', P2: 'blue', P3: 'neutral',
  // alert lifecycle
  OPEN: 'red', ACKNOWLEDGED: 'amber', MITIGATED: 'green', DISMISSED: 'neutral',
  // generic lifecycle
  ACTIVE: 'green', PAUSED: 'amber', BLOCKED: 'red', DONE: 'green', CLOSED: 'neutral',
  PENDING: 'amber', RUNNING: 'blue', FAILED: 'red', COMPLETED: 'green',
  // Jira sync runs
  Success: 'green', Failed: 'red', Running: 'blue', Skipped: 'neutral',
}

export function StatusPill({ status, label }: { status: string; label?: string }) {
  const C = useC()
  const palette: Record<Tone, { bg: string; fg: string }> = {
    red:     { bg: C.redPale,    fg: C.redDeep },
    amber:   { bg: C.amberPale,  fg: C.amberDeep },
    green:   { bg: C.greenPale,  fg: C.greenDeep },
    blue:    { bg: C.bluePale,   fg: C.blueDeep },
    purple:  { bg: C.purplePale, fg: C.purpleDeep },
    neutral: { bg: C.canvas,     fg: C.sub },
  }
  const { bg, fg } = palette[TONE[status] ?? 'neutral']
  return (
    <span style={{
      background: bg, color: fg, padding: '2px 8px', borderRadius: 10,
      fontSize: 10, fontWeight: 700, whiteSpace: 'nowrap', display: 'inline-block',
    }}>
      {label ?? status}
    </span>
  )
}
