import { useEffect, useState } from 'react'

export type Breakpoint = 'mobile' | 'tablet' | 'desktop'

// Width thresholds shared by every responsive component. Pages should branch on
// useBreakpoint()/useIsMobile() rather than reading window.innerWidth directly.
export const BP = { mobile: 640, tablet: 1024 }

function current(): Breakpoint {
  if (typeof window === 'undefined') return 'desktop'
  const w = window.innerWidth
  if (w < BP.mobile) return 'mobile'
  if (w < BP.tablet) return 'tablet'
  return 'desktop'
}

export function useBreakpoint(): Breakpoint {
  const [bp, setBp] = useState<Breakpoint>(current)
  useEffect(() => {
    const onResize = () => setBp(current())
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])
  return bp
}

export function useIsMobile(): boolean {
  return useBreakpoint() === 'mobile'
}
