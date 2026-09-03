// Display-timezone-aware date rendering.
//
// The backend emits ISO-8601 with offset for converted endpoints, but legacy
// fields still serialize LocalDateTime naively (no offset) in the *server*
// zone — parsing those with `new Date()` would silently reinterpret them in
// the browser zone. parseServerDate() handles both; the fmt* helpers render
// in the configured display zone (GET /app/config when available, default
// Asia/Kolkata).
import { api } from '../api/client'

let displayZone = 'Asia/Kolkata'
let serverZone = 'UTC'
let initialized = false

export function configureDatetime(cfg: { displayTimezone?: string; serverTimezone?: string }) {
  if (cfg.displayTimezone) displayZone = cfg.displayTimezone
  if (cfg.serverTimezone) serverZone = cfg.serverTimezone
}

/**
 * Fetch /app/config once per session; any failure (including a backend
 * without the endpoint — it 404s) keeps the defaults above.
 */
export async function initDatetime(): Promise<void> {
  if (initialized) return
  initialized = true
  try {
    const r = await api.get('/app/config')
    configureDatetime(r.data ?? {})
  } catch {
    /* endpoint absent or unreachable — defaults stay in effect */
  }
}

const HAS_OFFSET = /(Z|[+-]\d{2}:?\d{2})$/
const NAIVE = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?/

/**
 * Backend timestamp → Date. Offset-aware ISO parses directly; naive
 * `yyyy-MM-dd(T| )HH:mm[:ss]` strings are interpreted in the server zone.
 */
export function parseServerDate(value: string | number | Date | null | undefined): Date | null {
  if (value == null || value === '') return null
  if (value instanceof Date) return value
  if (typeof value === 'number') return new Date(value)
  const s = value.trim()
  const naive = !HAS_OFFSET.test(s) && s.match(NAIVE)
  if (naive) {
    const [, y, mo, d, h, mi, sec] = naive
    const utcGuess = Date.UTC(+y, +mo - 1, +d, +h, +mi, +(sec ?? '0'))
    return new Date(utcGuess - zoneOffsetMs(serverZone, utcGuess))
  }
  const parsed = new Date(s)
  return isNaN(parsed.getTime()) ? null : parsed
}

/** UTC offset of `zone` at the given instant, in ms (+05:30 → 19_800_000). */
function zoneOffsetMs(zone: string, utcMs: number): number {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-US', {
      timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
    }).formatToParts(new Date(utcMs)).map(p => [p.type, p.value]),
  )
  const asUtc = Date.UTC(+parts.year, +parts.month - 1, +parts.day,
    parts.hour === '24' ? 0 : +parts.hour, +parts.minute, +parts.second)
  return asUtc - utcMs
}

type DateInput = string | number | Date | null | undefined

function fmt(value: DateInput, options: Intl.DateTimeFormatOptions, empty: string): string {
  const d = parseServerDate(value)
  if (!d) return empty
  return new Intl.DateTimeFormat('en-GB', { ...options, timeZone: displayZone }).format(d)
}

/** "25 Jul 2026" */
export function fmtDate(value: DateInput, empty = '—'): string {
  return fmt(value, { day: '2-digit', month: 'short', year: 'numeric' }, empty)
}

/** "14:30" */
export function fmtTime(value: DateInput, empty = '—'): string {
  return fmt(value, { hour: '2-digit', minute: '2-digit', hour12: false }, empty)
}

/** "25 Jul 14:30" */
export function fmtDateTime(value: DateInput, empty = '—'): string {
  return fmt(value, { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false }, empty)
}

/** "25 Jul 2026, 14:30" */
export function fmtDateTimeFull(value: DateInput, empty = '—'): string {
  return fmt(value, {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit', hour12: false,
  }, empty)
}

/** "just now" / "12m ago" / "3h ago", falling back to fmtDateTime beyond a day. */
export function relTime(value: DateInput, empty = '—'): string {
  const d = parseServerDate(value)
  if (!d) return empty
  const mins = Math.floor((Date.now() - d.getTime()) / 60_000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins}m ago`
  if (mins < 24 * 60) return `${Math.floor(mins / 60)}h ago`
  return fmtDateTime(d)
}
