// Per-role chart presentation config, resolved from role_screen_config's
// chart_config JSON and gated by the section.charts.config flag. Design-layer
// only — the app layer (Shell) does the resolving and mounts the provider;
// components read via useChartConfig() and get defaults without a provider
// (unit tests, snapshot renders, flag off).
import { createContext, useContext } from 'react'

// Single vocabulary source: resolve, Shell's override validation, and the
// ChartTypeToggle options all read these.
export const TREND_CHART_TYPES = ['line', 'bar', 'stacked'] as const
export const BREAKDOWN_CHART_TYPES = ['bar', 'line'] as const
export type TrendChartType = (typeof TREND_CHART_TYPES)[number]
export type BreakdownChartType = (typeof BREAKDOWN_CHART_TYPES)[number]

export interface ChartConfig {
  chartType: TrendChartType              // trend widgets; default line
  breakdownChartType: BreakdownChartType // breakdown charts; default bar
  // null = each chart family's native palette — so flag-off renders are
  // pixel-identical. 'classic' / 'vibrant' are explicit admin choices.
  palette: 'classic' | 'vibrant' | null
  // Role grant for the in-page switcher. The grant alone doesn't render
  // anything — Shell only attaches the setters when it is true.
  runtimeToggle: boolean
}

// Setters present ⇒ the charts render a ChartTypeToggle wired to them; absent
// (default config, snapshot mode, flag off, role without the grant) ⇒ no
// switcher, so default renders are preserved by construction.
export interface ChartConfigValue extends ChartConfig {
  setChartType?: (t: TrendChartType) => void
  setBreakdownChartType?: (t: BreakdownChartType) => void
}

export const DEFAULT_CHART_CONFIG: ChartConfig = {
  chartType: 'line',
  breakdownChartType: 'bar',
  palette: null,
  runtimeToggle: false,
}

export const pickChartValue = <T extends string>(v: string | undefined | null, allowed: readonly T[]): T | undefined =>
  allowed.includes(v as T) ? (v as T) : undefined

/** Validate a role's raw chart_config JSON into a ChartConfig (unknown values → defaults). */
export function resolveChartConfig(raw?: Record<string, string> | null): ChartConfig {
  return {
    chartType: pickChartValue(raw?.chartType, TREND_CHART_TYPES) ?? DEFAULT_CHART_CONFIG.chartType,
    breakdownChartType: pickChartValue(raw?.breakdownChartType, BREAKDOWN_CHART_TYPES) ?? DEFAULT_CHART_CONFIG.breakdownChartType,
    palette: pickChartValue(raw?.palette, ['classic', 'vibrant'] as const) ?? DEFAULT_CHART_CONFIG.palette,
    runtimeToggle: raw?.runtimeToggle === 'on',
  }
}

export const ChartConfigContext = createContext<ChartConfigValue>(DEFAULT_CHART_CONFIG)
export const useChartConfig = (): ChartConfigValue => useContext(ChartConfigContext)
