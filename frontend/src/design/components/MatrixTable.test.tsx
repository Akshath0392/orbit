import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { MatrixTable, MatrixRow } from './MatrixTable'

const rows: MatrixRow[] = [
  { label: 'In Progress', sublabel: 'SLA 50% · avg 34d', total: 3, cells: { ACME: { count: 2, avgAgingDays: 40 }, ZENITH: { count: 1 } } },
  { label: 'Hold', total: 1, cells: { ZENITH: { count: 1 } } },
]

describe('MatrixTable', () => {
  it('renders counts, quiet dots for zeros, and totals', () => {
    render(<MatrixTable columns={['ACME', 'ZENITH']} rows={rows} rowHeader="Stage" />)
    expect(screen.getByText('In Progress')).toBeTruthy()
    expect(screen.getByText('SLA 50% · avg 34d')).toBeTruthy()
    // grand total = 4
    expect(screen.getByText('4')).toBeTruthy()
    // Hold row has no ACME cell → a quiet dot exists
    expect(screen.getAllByText('·').length).toBeGreaterThan(0)
  })

  it('fires cell and column click handlers', () => {
    const onCell = vi.fn()
    const onCol = vi.fn()
    render(<MatrixTable columns={['ACME', 'ZENITH']} rows={rows} rowHeader="Stage" onCellClick={onCell} onColumnClick={onCol} />)
    fireEvent.click(screen.getAllByText('2')[0]) // cell value; totals row repeats the number
    expect(onCell).toHaveBeenCalledWith('In Progress', 'ACME')
    fireEvent.click(screen.getByText('ACME'))
    expect(onCol).toHaveBeenCalledWith('ACME')
  })

  it('renders columnSubs on a second header line, skipping nulls', () => {
    render(<MatrixTable columns={['ACME', 'ZENITH']} columnSubs={['62% SLA', null]} rows={rows} rowHeader="Stage" />)
    expect(screen.getByText('62% SLA')).toBeTruthy()
    // null entry renders nothing extra in the ZENITH header
    expect(screen.getByText('ZENITH').textContent).toBe('ZENITH')
  })
})
