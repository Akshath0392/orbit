// Feature flags for controlled rollout — deliberately independent of roles.
// Key convention: screen.<navId> gates a route + sidebar item;
//                 section.<page>.<name> gates a component inside a page.
// Unknown keys are ON, so only held-back features need a flag row (managed in
// the Features Control Center, /flags). ADMINs receive true for every flag.
import { ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'

export type FlagMap = Record<string, boolean>

export function useFlags(): FlagMap {
  const { data } = useQuery({
    queryKey: ['feature-flags'],
    queryFn: () => api.get('/feature-flags/effective').then(r => r.data),
    staleTime: 60_000,
  })
  return data ?? {}
}

export function flagOn(flags: FlagMap, key: string): boolean {
  return flags[key] !== false
}

export function useFlag(key: string): boolean {
  return flagOn(useFlags(), key)
}

export function Feature({ flag, children, fallback = null }: {
  flag: string
  children: ReactNode
  fallback?: ReactNode
}) {
  const on = useFlag(flag)
  return <>{on ? children : fallback}</>
}
