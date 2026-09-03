import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { DonutChart } from './DonutChart'

const data = [
  { label: 'ACME', value: 6 },
  { label: 'ZENITH', value: 3 },
  { label: 'Others', value: 1 },
]

describe('DonutChart', () => {
  it('renders hole value/label and legend rows with counts and percentages', () => {
    render(<DonutChart data={data} holeValue={10} holeLabel="Open bugs" />)
    expect(screen.getByText('10')).toBeTruthy()
    expect(screen.getByText('Open bugs')).toBeTruthy()
    expect(screen.getByText('ACME')).toBeTruthy()
    expect(screen.getByText('· 30%', { exact: false })).toBeTruthy()
  })

  it('fires onSliceClick on legend rows but never for Others', () => {
    const onSlice = vi.fn()
    render(<DonutChart data={data} holeValue={10} holeLabel="Open bugs" onSliceClick={onSlice} />)
    fireEvent.click(screen.getByText('ACME'))
    expect(onSlice).toHaveBeenCalledWith('ACME')
    fireEvent.click(screen.getByText('Others'))
    expect(onSlice).toHaveBeenCalledTimes(1)
  })

  it('avoids NaN percentages when total is zero and shows the note', () => {
    render(<DonutChart data={[{ label: 'ACME', value: 0 }]} holeValue={0} holeLabel="Open bugs" note="click a name" />)
    expect(screen.queryByText(/NaN/)).toBeNull()
    expect(screen.getByText('click a name')).toBeTruthy()
  })
})
