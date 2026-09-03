import { render } from '@testing-library/react'
import { BurnBar } from './BurnBar'
import { C } from '../tokens'

// jsdom normalises hex to rgb() in CSSStyleDeclaration
function rgb(hex: string) {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  return `rgb(${r}, ${g}, ${b})`
}

// BurnBar renders: container > track > fill
// container.firstElementChild = track, track.firstElementChild = fill
function getFill(container: HTMLElement) {
  const track = container.firstElementChild as HTMLElement
  return track.firstElementChild as HTMLElement
}

describe('BurnBar', () => {
  it('renders fill width equal to pct', () => {
    const { container } = render(<BurnBar pct={50} />)
    expect(getFill(container).style.width).toBe('50%')
  })

  it('clamps fill width to 100% when pct > 100', () => {
    const { container } = render(<BurnBar pct={130} />)
    expect(getFill(container).style.width).toBe('100%')
  })

  it('uses green colour when pct ≤ 60', () => {
    const { container } = render(<BurnBar pct={40} />)
    expect(getFill(container).style.background).toBe(rgb(C.green))
  })

  it('uses green colour at exactly pct=60 (boundary — threshold is > 60)', () => {
    const { container } = render(<BurnBar pct={60} />)
    expect(getFill(container).style.background).toBe(rgb(C.green))
  })

  it('uses amber colour when pct is 61–80', () => {
    const { container } = render(<BurnBar pct={70} />)
    expect(getFill(container).style.background).toBe(rgb(C.amber))
  })

  it('uses red colour when pct > 80', () => {
    const { container } = render(<BurnBar pct={85} />)
    expect(getFill(container).style.background).toBe(rgb(C.red))
  })

  it('uses red colour exactly at 81', () => {
    const { container } = render(<BurnBar pct={81} />)
    expect(getFill(container).style.background).toBe(rgb(C.red))
  })

  it('respects custom height prop', () => {
    const { container } = render(<BurnBar pct={50} h={12} />)
    const track = container.firstElementChild as HTMLElement
    expect(track.style.height).toBe('12px')
  })
})
