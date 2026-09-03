import { useQuery } from '@tanstack/react-query'
import { api } from './client'

export interface ClientLite { id: number; name: string }
export interface AgentLite  { id: number; name: string }

export function useClients() {
  return useQuery<ClientLite[]>({
    queryKey: ['clients'],
    queryFn: () => api.get('/clients').then(r => r.data),
    staleTime: 60_000,
  })
}

export function useAgents() {
  return useQuery<AgentLite[]>({
    queryKey: ['agents'],
    queryFn: () => api.get('/admin/agents').then(r => r.data),
    staleTime: 60_000,
  })
}

export function useReportTemplates() {
  return useQuery<{ id: string; name: string }[]>({
    queryKey: ['report-templates'],
    queryFn: () => api.get('/reports/templates').then(r => r.data),
    staleTime: 300_000,
  })
}

export function useAlertTypes() {
  return useQuery<string[]>({
    queryKey: ['alert-types'],
    queryFn: () => api.get('/alerts/types').then(r => r.data),
    staleTime: 60_000,
  })
}

export function useCapacityConfig() {
  return useQuery<{ overloadThreshold: number; busyThreshold: number; manDayWarningPct: number; uatCycleWarnThreshold: number }>({
    queryKey: ['capacity-config'],
    queryFn: () => api.get('/capacity/config').then(r => r.data),
    staleTime: 600_000,
  })
}

export interface Role { roleName: string; displayName: string; screenIds: string[] }

export function useRoles() {
  return useQuery<Role[]>({
    queryKey: ['admin-roles'],
    queryFn: () => api.get('/admin/roles').then(r => r.data),
    staleTime: 300_000,
  })
}

export function useCapacityTeams() {
  return useQuery<string[]>({
    queryKey: ['capacity-teams'],
    queryFn: () => api.get('/capacity/teams').then(r => r.data),
    staleTime: 60_000,
  })
}
