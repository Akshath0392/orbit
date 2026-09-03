import { useQuery } from '@tanstack/react-query'
import { api } from '../../api/client'

export interface TeamRoleLabel {
  roleKey: string
  label: string
}

// Neutral fallbacks (V93 seed) shown until the query resolves.
export const DEFAULT_TEAM_ROLE_LABELS: Record<string, string> = {
  internal_pm: 'Project Manager',
  internal_am: 'Account Manager',
  internal_sol: 'Delivery Manager',
  internal_em: 'Engineering Manager',
  internal_tech_lead: 'Tech Lead',
  internal_qa_lead: 'QA Lead',
  internal_support_mgr: 'Support Manager',
}

export function useTeamRoleLabels() {
  return useQuery({
    queryKey: ['team-role-labels'],
    queryFn: () => api.get('/admin/team-role-labels').then(r => r.data as TeamRoleLabel[]),
  })
}
