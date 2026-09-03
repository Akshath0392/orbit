import { C } from './tokens'

export const agentColorMap: Record<string, string> = {
  "DeliveryIntelligenceAgent": C.indigo,
  "ManDayForecastAgent":       C.teal,
  "StandupAgent":              C.green,
  "EscalationAgent":           C.red,
  "ReportDraftingAgent":       C.purple,
  "AlertEngine":               C.amber,
  "SyncMonitor":               C.blue,
  "Auto-detected":             C.muted,
}
