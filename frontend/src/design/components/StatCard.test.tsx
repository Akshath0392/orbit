import { render, screen } from '@testing-library/react'
import { StatCard } from './StatCard'
import { C } from '../tokens'

// jsdom normalises hex to rgb()
function rgb(hex: string) {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  return `rgb(${r}, ${g}, ${b})`
}

describe('StatCard', () => {
  it('renders label and value', () => {
    render(<StatCard label="Projects at risk" value={5} />)
    expect(screen.getByText('Projects at risk')).toBeInTheDocument()
    expect(screen.getByText('5')).toBeInTheDocument()
  })

  it('renders sub text when provided', () => {
    render(<StatCard label="Budget alerts" value={3} sub="across all projects" />)
    expect(screen.getByText('across all projects')).toBeInTheDocument()
  })

  it('omits sub text when prop is absent', () => {
    render(<StatCard label="Team overload" value={2} />)
    expect(screen.queryByRole('generic', { name: /sub/i })).not.toBeInTheDocument()
  })

  it('renders icon when provided', () => {
    render(<StatCard label="Alerts" value={7} icon="⚡" />)
    expect(screen.getByText('⚡')).toBeInTheDocument()
  })

  it('omits icon element when prop is absent', () => {
    const { container } = render(<StatCard label="Alerts" value={7} />)
    expect(container.querySelector('span')).not.toBeInTheDocument()
  })

  it('uses custom color on the value when provided', () => {
    const { container } = render(<StatCard label="Risk" value={9} color={C.red} />)
    const valueEl = container.querySelector('div > div:nth-child(2)') as HTMLElement
    expect(valueEl.style.color).toBe(rgb(C.red))
  })

  it('falls back to C.text color on the value when no color prop', () => {
    const { container } = render(<StatCard label="Risk" value={9} />)
    const valueEl = container.querySelector('div > div:nth-child(2)') as HTMLElement
    expect(valueEl.style.color).toBe(rgb(C.text))
  })

  it('accepts string values', () => {
    render(<StatCard label="Last sync" value="4m ago" />)
    expect(screen.getByText('4m ago')).toBeInTheDocument()
  })
})
