import { render } from '@testing-library/react'
import { Badge } from './Badge'
import { C } from '../tokens'

// jsdom normalises hex colours to rgb() in CSSStyleDeclaration — convert for comparison
function rgb(hex: string) {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  return `rgb(${r}, ${g}, ${b})`
}

describe('Badge', () => {
  it.each([
    ['critical', C.redPale,    C.redDeep],
    ['risk',     C.amberPale,  C.amberDeep],
    ['watch',    C.amberPale,  C.amberDeep],
    ['healthy',  C.greenPale,  C.greenDeep],
    ['info',     C.bluePale,   C.blueDeep],
    ['blue',     C.bluePale,   C.blueDeep],
    ['purple',   C.purplePale, C.purpleDeep],
    ['teal',     C.tealPale,   C.tealDeep],
    ['neutral',  C.canvas,     C.sub],
  ])('%s level uses correct colours', (level, expectedBg, expectedFg) => {
    const { container } = render(<Badge level={level} label={level} />)
    const span = container.querySelector('span') as HTMLElement
    expect(span.style.background).toBe(rgb(expectedBg))
    expect(span.style.color).toBe(rgb(expectedFg))
  })

  it('unknown level falls back to neutral colours', () => {
    const { container } = render(<Badge level="nonexistent" label="x" />)
    const span = container.querySelector('span') as HTMLElement
    expect(span.style.background).toBe(rgb(C.canvas))
    expect(span.style.color).toBe(rgb(C.sub))
  })

  it('renders the label text', () => {
    const { getByText } = render(<Badge level="healthy" label="On Track" />)
    expect(getByText('On Track')).toBeInTheDocument()
  })

  it('dot colour matches the level indicator', () => {
    const { container } = render(<Badge level="critical" label="Critical" />)
    const dot = container.querySelector('span > span') as HTMLElement
    expect(dot.style.background).toBe(rgb(C.red))
  })
})
