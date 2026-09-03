import { useC } from '../ThemeContext'

type BadgeLevel = 'critical' | 'risk' | 'watch' | 'healthy' | 'info' | 'blue' | 'purple' | 'teal' | 'neutral'

interface BadgeProps {
  level: BadgeLevel | string
  label: string
}

export function Badge({ level, label }: BadgeProps) {
  const C = useC()
  const m: Record<string, { bg: string; fg: string; d: string }> = {
    critical: { bg: C.redPale,    fg: C.redDeep,    d: C.red    },
    risk:     { bg: C.amberPale,  fg: C.amberDeep,  d: C.amber  },
    watch:    { bg: C.amberPale,  fg: C.amberDeep,  d: C.amber  },
    healthy:  { bg: C.greenPale,  fg: C.greenDeep,  d: C.green  },
    info:     { bg: C.bluePale,   fg: C.blueDeep,   d: C.blue   },
    blue:     { bg: C.bluePale,   fg: C.blueDeep,   d: C.blue   },
    purple:   { bg: C.purplePale, fg: C.purpleDeep, d: C.purple },
    teal:     { bg: C.tealPale,   fg: C.tealDeep,   d: C.teal   },
    neutral:  { bg: C.canvas,     fg: C.sub,        d: C.muted  },
  }
  const color = m[level] || { bg: C.canvas, fg: C.sub, d: C.muted }
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 5,
      padding: '2px 9px', borderRadius: 20,
      background: color.bg, color: color.fg,
      fontSize: 11, fontWeight: 600, letterSpacing: 0.3, whiteSpace: 'nowrap',
      transition: 'background-color 160ms ease, color 160ms ease',
    }}>
      <span style={{ width: 6, height: 6, borderRadius: '50%', background: color.d, flexShrink: 0 }} />
      {label}
    </span>
  )
}
