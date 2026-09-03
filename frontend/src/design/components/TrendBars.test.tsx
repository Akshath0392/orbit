import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { TrendBars } from './TrendBars'

const points = [
  { label: 'May', value: 12 },
  { label: 'Jun', value: 8 },
  { label: 'Jul', value: 15 },
]

describe('TrendBars', () => {
  it('renders one column per point with value, label, and title', () => {
    render(<TrendBars points={points} />)
    expect(screen.getByText('May')).toBeTruthy()
    expect(screen.getByText('15')).toBeTruthy()
    expect(screen.getByTitle('Jun: 8')).toBeTruthy()
  })

  it('applies formatValue to the printed values', () => {
    render(<TrendBars points={points} formatValue={v => `${v}d`} />)
    expect(screen.getByText('12d')).toBeTruthy()
    // title stays raw
    expect(screen.getByTitle('May: 12')).toBeTruthy()
  })
})
