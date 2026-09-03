import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { SegmentBar } from './SegmentBar'

const segments = [
  { label: 'Promoter', value: 8, color: '#1e9e6a' },
  { label: 'Passive', value: 3, color: '#c5871b' },
  { label: 'Detractor', value: 0, color: '#cf4436' },
]

describe('SegmentBar', () => {
  it('renders nonzero segments with counts and skips zero-value segments', () => {
    render(<SegmentBar segments={segments} />)
    expect(screen.getByTitle('Promoter: 8')).toBeTruthy()
    expect(screen.getByText('3')).toBeTruthy()
    expect(screen.queryByTitle('Detractor: 0')).toBeNull()
  })

  it('renders a single muted track with no text when all values are zero', () => {
    const { container } = render(<SegmentBar segments={[{ label: 'A', value: 0, color: '#000' }]} />)
    expect(container.textContent).toBe('')
    expect(container.querySelectorAll('div').length).toBe(1)
  })

  it('fires onSegmentClick with the segment label', () => {
    const onSeg = vi.fn()
    render(<SegmentBar segments={segments} onSegmentClick={onSeg} />)
    fireEvent.click(screen.getByTitle('Passive: 3'))
    expect(onSeg).toHaveBeenCalledWith('Passive')
  })
})
