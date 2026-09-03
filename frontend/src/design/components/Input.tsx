import { CSSProperties } from 'react'
import { useC } from '../ThemeContext'

interface InputProps {
  placeholder?: string
  value?: string
  onChange?: (value: string) => void
  type?: string
  style?: CSSProperties
  defaultValue?: string
}

export function Input({ placeholder, value, onChange, type = 'text', style, defaultValue }: InputProps) {
  const C = useC()
  return (
    <input
      type={type}
      placeholder={placeholder}
      value={value !== undefined ? value : undefined}
      defaultValue={defaultValue}
      onChange={onChange ? (e) => onChange(e.target.value) : undefined}
      autoCapitalize="none"
      autoCorrect="off"
      autoComplete="off"
      spellCheck={false}
      style={{
        fontSize: 12, padding: '7px 10px', borderRadius: 7,
        border: `1px solid ${C.border}`, background: C.white,
        color: C.text, outline: 'none', width: '100%',
        ...(style || {})
      }}
    />
  )
}
