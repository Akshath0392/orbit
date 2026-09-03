// Single source of truth for the app-shell navigation model, shared by the
// TopBar (avatar → admin dropdown) and the Orbit launcher (product tiles).
// Screen visibility is RBAC (role → allowed screenIds) × controlled-release
// feature flags (`screen.<navId>`), exactly as the retired Sidebar gated it
// (see docs/CLAUDE.md rule 14). Lifted verbatim from the old Sidebar so
// behavior is identical.
import { useStore } from '../app/store'
import { flagOn, type FlagMap } from '../app/featureFlags'

export interface NavItem {
  id: string
  sym: string
  label: string
  desc?: string
  badge?: string | null
}

// Product screens — reachable from the /orbit launcher.
export const PRODUCT_NAV: NavItem[] = [
  { id: 'radar',    sym: '◈', label: 'Orbitter',       desc: 'Persona home — portfolio radar',   badge: null },
  { id: 'cr',       sym: '◇', label: 'CR dashboard',   desc: 'Change requests by stage & SLA',    badge: null },
  { id: 'bugs',     sym: '!', label: 'Bug Triage',     desc: 'Production defects & prod SLA',      badge: null },
  { id: 'uat',      sym: '✓', label: 'UAT tracker',    desc: 'Cycles, defects & sign-off',        badge: null },
  { id: 'mandays',  sym: '◷', label: 'Man-days',       desc: 'Sold vs consumed, burn & runway',   badge: null },
  { id: 'alerts',   sym: '⚑', label: 'Alert center',   desc: 'Delivery-risk alerts',              badge: null },
  { id: 'reports',  sym: '⊟', label: 'Reports',        desc: 'Draft, export & schedule reports',  badge: null },
  { id: 'capacity', sym: '◫', label: 'Capacity',       desc: 'Team load & availability',          badge: null },
  { id: 'clients',  sym: '▤', label: 'Client backlog', desc: 'Accounts, backlog & governance',    badge: null },
]

// System + admin screens — reachable from the avatar dropdown in the TopBar.
export const ADMIN_NAV: NavItem[] = [
  { id: 'integrations', sym: '⇄', label: 'Integrations',            badge: null },
  { id: 'audit',        sym: '◍', label: 'Agent audit log',         badge: null },
  { id: 'admin',        sym: '⚙', label: 'Admin console',           badge: null },
  { id: 'agent-builder',sym: '⬡', label: 'Agents',                  badge: null },
  { id: 'flags',        sym: '⚐', label: 'Features Control Center', badge: null },
]

export const ROLE_ACCESS: Record<string, string[]> = {
  ADMIN:       ['radar','cr','bugs','uat','mandays','alerts','reports','capacity','clients','integrations','audit','admin','agent-builder','flags'],
  PM:          ['radar','cr','bugs','uat','mandays','alerts','reports','capacity','clients','integrations','audit','agent-builder'],
  PJM:         ['radar','cr','bugs','uat','mandays','alerts','reports','capacity','clients','integrations','audit','agent-builder'],
  LEADERSHIP:  ['radar'],
  ENGINEERING: ['radar','capacity','mandays','agent-builder'],
  CSM:         ['radar','clients','alerts','reports'],
  REVENUE:     ['radar','mandays','reports','capacity'],
}

export const ROLE_LABEL: Record<string, string> = {
  ADMIN: 'Admin', PM: 'Project Management', PJM: 'Project Management',
  LEADERSHIP: 'Leadership', ENGINEERING: 'Engineering',
  CSM: 'Account Management', REVENUE: 'Revenue',
}

// Role → allowed screen ids: dynamic `/admin/roles` map wins, then the
// hardcoded fallback, then PM. (Was Sidebar.tsx:69.)
export function useAllowedScreenIds(): string[] {
  const user = useStore(s => s.user)
  const roleScreens = useStore(s => s.roleScreens)
  const role = user?.role ?? 'PM'
  return (Object.keys(roleScreens).length > 0 ? roleScreens[role] : null)
    ?? ROLE_ACCESS[role] ?? ROLE_ACCESS['PM']
}

// Role gate (may this role use it) AND release gate (is it rolled out).
// (Was Sidebar.tsx:117.)
export function visibleNav(items: NavItem[], allowed: string[], flags: FlagMap): NavItem[] {
  return items.filter(n => allowed.includes(n.id) && flagOn(flags, `screen.${n.id}`))
}

// Orbit orbital logo mark — matches the static HTML brand-mark.
export function OrbitMark({ size = 40 }: { size?: number }) {
  const cx = size / 2
  const cy = size / 2
  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden="true" style={{ flexShrink: 0, overflow: 'visible' }}>
      <circle cx={cx} cy={cy} r={size / 2 - 2} fill="none" stroke="#087f7a" strokeWidth="2" />
      <ellipse cx={cx} cy={cy} rx={size * 0.76} ry={size * 0.21} fill="none" stroke="#e0a323" strokeWidth="2" transform={`rotate(-28 ${cx} ${cy})`} />
      <circle cx={cx} cy={cy} r={size * 0.12} fill="#b83280" />
    </svg>
  )
}
