import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export interface User {
  id: number
  name: string
  email: string
  role: string
  initials: string
  avatarColor: string
  token: string
}

interface AppStore {
  user: User | null
  setUser: (u: User | null) => void
  collapsed: boolean
  toggleSidebar: () => void
  selectedProjectId: number | null
  setSelectedProjectId: (id: number | null) => void
  roleScreens: Record<string, string[]>
  setRoleScreens: (screens: Record<string, string[]>) => void
  // roleName → chart_config JSON from /admin/roles; not persisted,
  // refetched with roleScreens on every Shell mount.
  roleChartConfigs: Record<string, Record<string, string>>
  setRoleChartConfigs: (configs: Record<string, Record<string, string>>) => void
  // User's in-page chart-type choices, layered over the role config by Shell
  // only when the role has runtimeToggle=on. Persisted per-browser (like
  // `collapsed`); stored as plain strings and re-validated against the
  // chartConfig vocabularies on read, so stale values are inert.
  chartTypeOverride: string | null
  setChartTypeOverride: (t: string | null) => void
  breakdownChartTypeOverride: string | null
  setBreakdownChartTypeOverride: (t: string | null) => void
  activePersona: string
  setActivePersona: (p: string) => void
  activePortfolioId: number | null
  setActivePortfolioId: (id: number | null) => void
}

export const useStore = create<AppStore>()(
  persist(
    (set) => ({
      user: null,
      setUser: (user) => set({ user }),
      collapsed: false,
      toggleSidebar: () => set((s) => ({ collapsed: !s.collapsed })),
      selectedProjectId: null,
      setSelectedProjectId: (id) => set({ selectedProjectId: id }),
      roleScreens: {},
      setRoleScreens: (screens) => set({ roleScreens: screens }),
      roleChartConfigs: {},
      setRoleChartConfigs: (roleChartConfigs) => set({ roleChartConfigs }),
      chartTypeOverride: null,
      setChartTypeOverride: (chartTypeOverride) => set({ chartTypeOverride }),
      breakdownChartTypeOverride: null,
      setBreakdownChartTypeOverride: (breakdownChartTypeOverride) => set({ breakdownChartTypeOverride }),
      activePersona: '',
      setActivePersona: (activePersona) => set({ activePersona }),
      activePortfolioId: null,
      setActivePortfolioId: (activePortfolioId) => set({ activePortfolioId }),
    }),
    {
      name: 'orbit-session',
      // theme deliberately NOT here — ThemeContext owns it (localStorage 'orbit-theme')
      partialize: (s) => ({
        user: s.user, collapsed: s.collapsed,
        chartTypeOverride: s.chartTypeOverride,
        breakdownChartTypeOverride: s.breakdownChartTypeOverride,
      }),
    }
  )
)
