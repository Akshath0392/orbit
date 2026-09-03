import { useState } from 'react'
import { useC } from '../../design/ThemeContext'
import { Tabs } from '../../design/components/Tabs'
import { PortfolioSetupPage } from './portfolios/PortfolioSetupPage'
import { ClientManagementPage } from './clients/ClientManagementPage'
import { ProjectManagementPage } from './projects/ProjectManagementPage'
import { UserManagementPage } from './users/UserManagementPage'
import { RolesManagementPage } from './roles/RolesManagementPage'
import { SlaRulesPage } from './sla/SlaRulesPage'
import { LifecycleMappingPage } from './lifecycle/LifecycleMappingPage'
import { ReportSchedulesPage } from './schedules/ReportSchedulesPage'
import { AlertRulesPage } from './alert-rules/AlertRulesPage'
import { PhaseDeliveriesPage } from './alert-rules/PhaseDeliveriesPage'
import { TeamRoleLabelsPage } from './team-roles/TeamRoleLabelsPage'

const TABS = ['Clients', 'Projects', 'Portfolio setup', 'Users', 'Roles & permissions', 'Team role labels', 'SLA rules', 'Lifecycle mapping', 'Report schedules', 'Notification rules', 'Phase deliveries']

export function AdminConsolePage() {
  const C = useC()
  const [tab, setTab] = useState('Clients')

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div style={{ padding: '18px 24px 0', borderBottom: `1px solid ${C.border}`, background: C.white }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 14 }}>
          <div style={{ width: 28, height: 28, borderRadius: 7, background: C.indigoPale, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 14 }}>
            ⚙
          </div>
          <div>
            <div style={{ fontSize: 18, fontWeight: 700, color: C.text, letterSpacing: -0.3 }}>Admin console</div>
            <div style={{ fontSize: 11, color: C.sub }}>Workspace configuration · onboarding · access control</div>
          </div>
        </div>
        <Tabs items={TABS} active={tab} onChange={setTab} />
      </div>

      <div style={{ flex: 1, overflowY: 'auto' }}>
        {tab === 'Clients'             && <ClientManagementPage />}
        {tab === 'Projects'            && <ProjectManagementPage />}
        {tab === 'Portfolio setup'     && <PortfolioSetupPage />}
        {tab === 'Users'               && <UserManagementPage />}
        {tab === 'Roles & permissions' && <RolesManagementPage />}
        {tab === 'Team role labels'    && <TeamRoleLabelsPage />}
        {tab === 'SLA rules'           && <SlaRulesPage />}
        {tab === 'Lifecycle mapping'    && <LifecycleMappingPage />}
        {tab === 'Report schedules'    && <ReportSchedulesPage />}
        {tab === 'Notification rules'  && <AlertRulesPage />}
        {tab === 'Phase deliveries'    && <PhaseDeliveriesPage />}
      </div>
    </div>
  )
}
