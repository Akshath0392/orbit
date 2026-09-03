import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { GroupedBars } from './GroupedBars'

describe('GroupedBars', () => {
  it('renders one group per label with values printed on every bar', () => {
    render(<GroupedBars
      labels={['May', 'Jun']}
      seriesA={{ name: 'Created', values: [4, 6] }}
      seriesB={{ name: 'Resolved', values: [3, 5] }}
    />)
    expect(screen.getByTitle('Jun: Created 6 · Resolved 5')).toBeTruthy()
    // mock .gv — values always printed
    expect(screen.getByText('4')).toBeTruthy()
    expect(screen.getByText('3')).toBeTruthy()
    expect(screen.getByText('6')).toBeTruthy()
    expect(screen.getByText('5')).toBeTruthy()
  })

  it('fires onGroupClick with the group index', () => {
    const onGroup = vi.fn()
    render(<GroupedBars
      labels={['May', 'Jun']}
      seriesA={{ name: 'Created', values: [4, 6] }}
      seriesB={{ name: 'Resolved', values: [3, 5] }}
      onGroupClick={onGroup}
    />)
    fireEvent.click(screen.getByTitle('May: Created 4 · Resolved 3'))
    expect(onGroup).toHaveBeenCalledWith(0)
  })

  it('supports a single series (week-on-week blocks)', () => {
    render(<GroupedBars
      labels={['W1', 'W2']}
      seriesA={{ name: 'Created', values: [2, 7], color: '#d99a2b' }}
    />)
    expect(screen.getByTitle('W2: Created 7')).toBeTruthy()
    expect(screen.getByText('7')).toBeTruthy()
  })
})
