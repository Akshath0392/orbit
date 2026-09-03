import { createContext, useContext, useState, useEffect, ReactNode } from 'react'
import { lightC, darkC, Colors } from './theme'
import { readSnapshotParams } from '../app/snapshotMode'

type Theme = 'light' | 'dark'

interface ThemeCtx {
  theme: Theme
  toggleTheme: () => void
  C: Colors
}

// Default to lightC so components work without a provider (e.g. in unit tests)
const ThemeContext = createContext<ThemeCtx>({
  theme: 'light',
  toggleTheme: () => {},
  C: lightC,
})

export function ThemeProvider({ children }: { children: ReactNode }) {
  // Snapshot/report renders are always light — print-friendly regardless of
  // the requesting user's theme (mock's paper-white report rule).
  const snapshotRender = readSnapshotParams().enabled
  const [theme, setTheme] = useState<Theme>(() => {
    if (snapshotRender) return 'light'
    try { return (localStorage.getItem('orbit-theme') as Theme) || 'dark' } catch { return 'dark' }
  })

  useEffect(() => {
    if (!snapshotRender) { try { localStorage.setItem('orbit-theme', theme) } catch {} }
    document.documentElement.setAttribute('data-theme', theme)
  }, [theme, snapshotRender])

  const toggleTheme = () => { if (!snapshotRender) setTheme(t => t === 'light' ? 'dark' : 'light') }
  const C = theme === 'dark' ? darkC : lightC

  return (
    <ThemeContext.Provider value={{ theme, toggleTheme, C }}>
      {children}
    </ThemeContext.Provider>
  )
}

export const useC = (): Colors => useContext(ThemeContext).C
export const useTheme = () => {
  const { theme, toggleTheme } = useContext(ThemeContext)
  return { theme, toggleTheme }
}
