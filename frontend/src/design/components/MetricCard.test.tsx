import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { MetricCard } from './MetricCard'

describe('MetricCard', () => {
  it('renders name, value, unit, RAG chip, formula and thresholds', () => {
    render(<MetricCard
      name="Avg bug age" value={18} unit="days" currentLabel="Jul (current)"
      rag="a" formula="Σ age / open bugs"
      thresholds="≤15 green · ≤30 amber · else red"
    />)
    expect(screen.getByText('Avg bug age')).toBeTruthy()
    expect(screen.getByText('18')).toBeTruthy()
    expect(screen.getByText('days')).toBeTruthy()
    expect(screen.getByText('Amber')).toBeTruthy()
    expect(screen.getByText('Σ age / open bugs')).toBeTruthy()
    expect(screen.getByText('≤15 green · ≤30 amber · else red')).toBeTruthy()
  })

  it('renders trend bars with the caption and fires the drill link', () => {
    const onDrill = vi.fn()
    render(<MetricCard
      name="Avg bug age" value={18} rag="g"
      trend={[{ label: 'Jun', value: 20 }, { label: 'Jul', value: 18 }]}
      onDrill={onDrill}
    />)
    expect(screen.getByTitle('Jun: 20')).toBeTruthy()
    expect(screen.getByText('Same formula computed per month · highlighted bar = current month')).toBeTruthy()
    fireEvent.click(screen.getByText('open items'))
    expect(onDrill).toHaveBeenCalled()
  })

  it('pending variant keeps the header but hides value, trend and drill', () => {
    render(<MetricCard
      name="Cycle time" value={9} rag="r" onDrill={() => {}}
      pending="Awaiting status-changelog feed — lights up in Phase C"
    />)
    expect(screen.getByText('Cycle time')).toBeTruthy()
    expect(screen.getByText('Awaiting status-changelog feed — lights up in Phase C')).toBeTruthy()
    expect(screen.queryByText('9')).toBeNull()
    expect(screen.queryByText('open items')).toBeNull()
  })
})
