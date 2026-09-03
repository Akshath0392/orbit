import { ReactNode, useEffect, useMemo } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { useC } from '../design/ThemeContext'
import {
  ChartConfigContext, ChartConfigValue, DEFAULT_CHART_CONFIG, resolveChartConfig,
  pickChartValue, TREND_CHART_TYPES, BREAKDOWN_CHART_TYPES,
} from '../design/chartConfig'
import { useStore } from '../app/store'
import { api } from '../api/client'
import { readSnapshotParams } from '../app/snapshotMode'
import { useFlags, flagOn } from '../app/featureFlags'
import { TopBar } from './TopBar'
import { OrbitLauncherPage } from '../features/orbit/OrbitLauncherPage'
import { RadarPage } from '../features/radar/RadarPage'
import { AccountDetailPage } from '../features/accounts/AccountDetailPage'
import { AccountReportPage } from '../features/accounts/AccountReportPage'
import { GlobalCopilotPanel } from '../features/copilot/GlobalCopilotPanel'
import { CrBoardPage } from '../features/cr-board/CrBoardPage'
import { BugTriagePage } from '../features/bugs/BugTriagePage'
import { ManDaysPage } from '../features/man-days/ManDaysPage'
import { AlertsPage } from '../features/alerts/AlertsPage'
import { ReportsPage } from '../features/reports/ReportsPage'
import { CapacityPage } from '../features/capacity/CapacityPage'
import { ClientBacklogPage } from '../features/clients/ClientBacklogPage'
import { ClientMasterPage } from '../features/clients/ClientMasterPage'
import { AgentAuditPage } from '../features/agent-audit/AgentAuditPage'
import { SlaRulesPage } from '../features/admin/sla/SlaRulesPage'
import { LifecycleMappingPage } from '../features/admin/lifecycle/LifecycleMappingPage'
import { UserManagementPage } from '../features/admin/users/UserManagementPage'
import { UatTrackerPage } from '../features/uat/UatTrackerPage'
import { ReportSchedulesPage } from '../features/admin/schedules/ReportSchedulesPage'
import { PortfolioSetupPage } from '../features/admin/portfolios/PortfolioSetupPage'
import { ProdBugRoutingPage } from '../features/admin/prod-bug-routing/ProdBugRoutingPage'
import { AdminConsolePage } from '../features/admin/AdminConsolePage'
import { AgentBuilderPage } from '../features/admin/agents/AgentBuilderPage'
import { AgentDetailPage } from '../features/admin/agents/AgentDetailPage'
import { IntegrationsPage } from '../features/admin/integrations/IntegrationsPage'
import { SnapshotViewerPage } from '../features/snapshots/SnapshotViewerPage'
import { FeatureFlagsPage } from '../features/admin/flags/FeatureFlagsPage'
import { initDatetime } from '../lib/datetime'

export function Shell() {
  const C = useC()
  const { setRoleScreens, setRoleChartConfigs, roleChartConfigs, user,
          chartTypeOverride, setChartTypeOverride,
          breakdownChartTypeOverride, setBreakdownChartTypeOverride } = useStore()
  const snapshot = readSnapshotParams()
  const flags = useFlags()

  useEffect(() => {
    api.get('/admin/roles').then(r => {
      const map: Record<string, string[]> = {}
      const chartMap: Record<string, Record<string, string>> = {}
      for (const role of r.data) {
        map[role.roleName] = role.screenIds
        chartMap[role.roleName] = role.chartConfig ?? {}
      }
      setRoleScreens(map)
      setRoleChartConfigs(chartMap)
    }).catch(() => {/* keep hardcoded ROLE_ACCESS fallback in nav.tsx */})
    initDatetime() // display-timezone config; defaults apply until it resolves
  }, [])

  // Per-role chart presentation. Flag off → hardcoded current behavior.
  // Snapshot renders also get defaults: snapshot JWTs are ADMIN-elevated and
  // ADMIN sees every flag true, so without this guard a report render would
  // pick up whatever role config exists — reports must stay deterministic.
  const chartConfig = useMemo((): ChartConfigValue => {
    if (snapshot.enabled || !flagOn(flags, 'section.charts.config')) return DEFAULT_CHART_CONFIG
    const resolved = resolveChartConfig(user ? roleChartConfigs[user.role] : null)
    if (!resolved.runtimeToggle) return resolved
    // Granted roles: layer the user's persisted in-page choice over the role
    // default and expose the setters — their presence is what makes the
    // charts render the switcher. Persisted values are re-validated so a
    // stale localStorage entry can never produce an unknown chart type.
    return {
      ...resolved,
      chartType: pickChartValue(chartTypeOverride, TREND_CHART_TYPES) ?? resolved.chartType,
      breakdownChartType: pickChartValue(breakdownChartTypeOverride, BREAKDOWN_CHART_TYPES) ?? resolved.breakdownChartType,
      setChartType: setChartTypeOverride,
      setBreakdownChartType: setBreakdownChartTypeOverride,
    }
  }, [snapshot.enabled, flags, roleChartConfigs, user, chartTypeOverride, breakdownChartTypeOverride,
      setChartTypeOverride, setBreakdownChartTypeOverride])

  // Controlled release: a screen whose screen.<id> flag is off is unreachable
  // even by direct URL, not just hidden from the nav.
  const gate = (id: string, el: ReactNode) =>
    flagOn(flags, `screen.${id}`) ? el : <Navigate to="/radar" replace />

  const chrome = !snapshot.enabled

  return (
    <ChartConfigContext.Provider value={chartConfig}>
    <div style={{
      display: 'flex', flexDirection: 'column',
      fontFamily: "'Inter', system-ui, sans-serif",
      background: C.canvas,
      // In snapshot mode the document must grow with the content: Playwright's
      // fullPage screenshot only captures document scroll height, not inner
      // scroll containers, so the 100vh clamp would clip anything below the fold.
      ...(snapshot.enabled
        ? { minHeight: '100vh' }
        : { height: '100vh', overflow: 'hidden' }),
    }}>
      {chrome && <TopBar />}
      <div style={{ flex: 1, minWidth: 0, position: 'relative', ...(snapshot.enabled ? {} : { overflowY: 'auto' }) }}>
        <Routes>
          <Route path="/"          element={<Navigate to="/radar" replace />} />
          <Route path="/orbit"     element={<OrbitLauncherPage />} />
          <Route path="/radar"     element={<RadarPage />} />
          <Route path="/accounts/:projectId" element={<AccountDetailPage />} />
          <Route path="/accounts/:projectId/report" element={<AccountReportPage />} />
          <Route path="/cr"        element={gate('cr', <CrBoardPage />)} />
          <Route path="/bugs"      element={gate('bugs', <BugTriagePage />)} />
          <Route path="/mandays"   element={gate('mandays', <ManDaysPage />)} />
          <Route path="/alerts"    element={gate('alerts', <AlertsPage />)} />
          <Route path="/reports"   element={gate('reports', <ReportsPage />)} />
          <Route path="/capacity"  element={gate('capacity', <CapacityPage />)} />
          <Route path="/clients"   element={gate('clients', <ClientBacklogPage />)} />
          <Route path="/clients/:clientId" element={gate('clients', <ClientMasterPage />)} />
          <Route path="/audit"     element={gate('audit', <AgentAuditPage />)} />
          <Route path="/sla"       element={gate('sla', <SlaRulesPage />)} />
          <Route path="/lifecycle" element={gate('lifecycle', <LifecycleMappingPage />)} />
          <Route path="/users"     element={gate('users', <UserManagementPage />)} />
          <Route path="/uat"       element={gate('uat', <UatTrackerPage />)} />
          <Route path="/schedules"  element={gate('schedules', <ReportSchedulesPage />)} />
          <Route path="/portfolios" element={gate('portfolios', <PortfolioSetupPage />)} />
          <Route path="/portfolios/prod-bug-routing" element={gate('portfolios', <ProdBugRoutingPage />)} />
          <Route path="/admin"      element={gate('admin', <AdminConsolePage />)} />
          <Route path="/agent-builder" element={gate('agent-builder', <AgentBuilderPage />)} />
          <Route path="/admin/agents/:id" element={gate('agent-builder', <AgentDetailPage />)} />
          <Route path="/agent-logs"    element={<Navigate to="/agent-builder" replace />} />
          <Route path="/integrations"  element={gate('integrations', <IntegrationsPage />)} />
          <Route path="/flags"         element={<FeatureFlagsPage />} />
          <Route path="/snapshots/:id" element={<SnapshotViewerPage />} />
          <Route path="*"          element={<Navigate to="/radar" replace />} />
        </Routes>
      </div>
      {chrome && <GlobalCopilotPanel />}
    </div>
    </ChartConfigContext.Provider>
  )
}
