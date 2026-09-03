import { useQuery } from '@tanstack/react-query'
import { api } from '../../../api/client'

// AM dashboard aggregations (backend AmDashboardController, lld §5.20-adjacent).
// portfolioId=null → all PODs. type: 'LAUNCH' | 'BAU' | undefined (all).

const params = (portfolioId: number | null, extra: Record<string, unknown> = {}) => ({
  params: { portfolioId: portfolioId ?? undefined, ...extra },
})

export function useAmSummary(portfolioId: number | null, type?: string) {
  return useQuery({
    queryKey: ['am-summary', portfolioId, type ?? 'ALL'],
    queryFn: () => api.get('/am/summary', params(portfolioId, { type })).then(r => r.data),
  })
}

export function useAmStageMatrix(portfolioId: number | null, type?: string) {
  return useQuery({
    queryKey: ['am-stage-matrix', portfolioId, type ?? 'ALL'],
    queryFn: () => api.get('/am/stage-matrix', params(portfolioId, { type })).then(r => r.data),
  })
}

export function useAmOwnerMatrix(portfolioId: number | null, type?: string) {
  return useQuery({
    queryKey: ['am-owner-matrix', portfolioId, type ?? 'ALL'],
    queryFn: () => api.get('/am/owner-matrix', params(portfolioId, { type })).then(r => r.data),
  })
}

export function useAmProdTrend(portfolioId: number | null, months = 12, from?: string, to?: string) {
  return useQuery({
    queryKey: ['am-prod-trend', portfolioId, months, from ?? '', to ?? ''],
    queryFn: () => api.get('/am/prod-trend', params(portfolioId, { months, from, to })).then(r => r.data),
  })
}

export function useAmPodScore() {
  return useQuery({
    queryKey: ['am-pod-score'],
    queryFn: () => api.get('/am/pod-score').then(r => r.data as any[]),
  })
}

export function useAmProdWeekly(portfolioId: number | null, from: string, to: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: ['am-prod-weekly', portfolioId, from, to ?? ''],
    queryFn: () => api.get('/am/prod-weekly', params(portfolioId, { from, to })).then(r => r.data),
    enabled,
  })
}

export function useAmOwnerShare(portfolioId: number | null, type?: string, dim: string = 'assignee') {
  return useQuery({
    queryKey: ['am-owner-share', portfolioId, type ?? 'ALL', dim],
    queryFn: () => api.get('/am/owner-share', params(portfolioId, { type, dim })).then(r => r.data),
  })
}

export function useAmClientOverview(clientId: number | null) {
  return useQuery({
    queryKey: ['am-client-overview', clientId],
    queryFn: () => api.get(`/am/client/${clientId}/overview`).then(r => r.data),
    enabled: clientId != null,
  })
}

export function useAmClientDhMetrics(clientId: number | null, months: number, type?: string) {
  return useQuery({
    queryKey: ['am-client-dh', clientId, months, type ?? 'ALL'],
    queryFn: () => api.get(`/am/client/${clientId}/dh-metrics`, { params: { months, type } }).then(r => r.data),
    enabled: clientId != null,
  })
}

export function useAmClients(portfolioId: number | null) {
  return useQuery({
    queryKey: ['am-clients', portfolioId],
    queryFn: () => api.get('/am/clients', params(portfolioId)).then(r => r.data as any[]),
  })
}

// ── Widget-parity plan (Wave 1) ─────────────────────────────────────────────

export function useAmCsatDrill(portfolioId: number | null, enabled: boolean) {
  return useQuery({
    queryKey: ['am-csat-drill', portfolioId],
    queryFn: () => api.get('/am/csat-drill', params(portfolioId)).then(r => r.data),
    enabled,
  })
}

export interface AmSettings {
  dhSpeedWeight: number
  dhQualityWeight: number
  dhPredWeight: number
  adoptionUrl: string | null
}

export function useAmSettings() {
  return useQuery({
    queryKey: ['am-settings'],
    queryFn: () => api.get('/am/settings').then(r => r.data as AmSettings),
  })
}

// ── Wave 3 (F3 sprint + changelog sync) ─────────────────────────────────────

export function useAmVelocity(portfolioId: number | null) {
  return useQuery({
    queryKey: ['am-velocity', portfolioId],
    queryFn: () => api.get('/am/velocity', params(portfolioId)).then(r => r.data),
  })
}

export function useAmClientMilestones(clientId: number | null) {
  return useQuery({
    queryKey: ['am-client-milestones', clientId],
    queryFn: () => api.get(`/am/client/${clientId}/milestones`).then(r => r.data),
    enabled: clientId != null,
  })
}

export function useJiraBackfillStatus(enabled: boolean) {
  return useQuery({
    queryKey: ['jira-backfill-status'],
    queryFn: () => api.get('/jira-sync/backfill-status').then(r => r.data),
    enabled,
    refetchInterval: q => (q.state.data as any)?.running ? 4000 : false,
  })
}

export function useAccountSprintScope(projectId: number | null) {
  return useQuery({
    queryKey: ['account-sprint-scope', projectId],
    queryFn: () => api.get(`/accounts/${projectId}/sprint-scope`).then(r => r.data),
    enabled: projectId != null,
  })
}

export interface AmDrillFilters {
  portfolioId?: number | null
  clientName?: string
  stage?: string
  owner?: string
  smOwner?: string
  pjmOwner?: string
  type?: string
}

export function useAmCrDrill(filters: AmDrillFilters, page: number, enabled: boolean) {
  return useQuery({
    queryKey: ['am-crs', filters, page],
    queryFn: () => api.get('/am/crs', {
      params: {
        portfolioId: filters.portfolioId ?? undefined,
        clientName: filters.clientName,
        stage: filters.stage,
        owner: filters.owner,
        smOwner: filters.smOwner,
        pjmOwner: filters.pjmOwner,
        type: filters.type,
        page, size: 20,
      },
    }).then(r => r.data),
    enabled,
  })
}
