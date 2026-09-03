import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MilestoneList } from './MilestoneList'

describe('MilestoneList', () => {
  it('renders group headers, done and upcoming rows with markers', () => {
    render(<MilestoneList
      done={[{ title: 'UAT sign-off', sub: 'Closed 2026-06-30' }]}
      upcoming={[{ title: 'Go-live', sub: 'Due 2026-07-20' }]}
    />)
    expect(screen.getByText('Recently achieved')).toBeTruthy()
    expect(screen.getByText('Upcoming targets')).toBeTruthy()
    expect(screen.getByText('UAT sign-off')).toBeTruthy()
    expect(screen.getByText('Closed 2026-06-30')).toBeTruthy()
    expect(screen.getByText('✓')).toBeTruthy()
    expect(screen.getByText('○')).toBeTruthy()
  })

  it('shows the empty placeholder for empty groups', () => {
    render(<MilestoneList done={[]} upcoming={[{ title: 'Go-live' }]} />)
    expect(screen.getByText('— none derived yet')).toBeTruthy()
    expect(screen.getByText('Go-live')).toBeTruthy()
  })
})
