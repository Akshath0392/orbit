import { render, screen, fireEvent } from '@testing-library/react'
import { Input } from './Input'

describe('Input', () => {
  it('onChange receives a string value, not a synthetic event', () => {
    const onChange = vi.fn()
    render(<Input placeholder="type here" value="" onChange={onChange} />)
    fireEvent.change(screen.getByPlaceholderText('type here'), {
      target: { value: 'hello' },
    })
    expect(onChange).toHaveBeenCalledWith('hello')
    expect(onChange).not.toHaveBeenCalledWith(expect.objectContaining({ target: expect.anything() }))
  })

  it('does not call onChange when prop is omitted', () => {
    const onChange = vi.fn()
    render(<Input placeholder="readonly" />)
    fireEvent.change(screen.getByPlaceholderText('readonly'), {
      target: { value: 'test' },
    })
    expect(onChange).not.toHaveBeenCalled()
  })

  it('renders with correct type attribute', () => {
    render(<Input type="password" placeholder="pw" />)
    expect(screen.getByPlaceholderText('pw')).toHaveAttribute('type', 'password')
  })

  it('defaults to type text', () => {
    render(<Input placeholder="txt" />)
    expect(screen.getByPlaceholderText('txt')).toHaveAttribute('type', 'text')
  })

  it('forwards controlled value', () => {
    render(<Input value="prefilled" onChange={vi.fn()} placeholder="v" />)
    expect(screen.getByPlaceholderText('v')).toHaveValue('prefilled')
  })
})
