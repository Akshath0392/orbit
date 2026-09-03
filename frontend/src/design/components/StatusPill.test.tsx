import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { StatusPill } from './StatusPill'

describe('StatusPill', () => {
  it('renders the status as its label by default', () => {
    render(<StatusPill status="OPEN" />)
    expect(screen.getByText('OPEN')).toBeTruthy()
  })

  it('renders a custom label when given', () => {
    render(<StatusPill status="critical" label="Critical" />)
    expect(screen.getByText('Critical')).toBeTruthy()
  })

  it('falls back to the neutral tone for unknown statuses', () => {
    render(<StatusPill status="SOMETHING_NEW" />)
    expect(screen.getByText('SOMETHING_NEW')).toBeTruthy()
  })
})
