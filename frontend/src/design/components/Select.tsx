import { CSSProperties, ChangeEvent } from 'react'
import { useC } from '../ThemeContext'

type SelectOption = string | { v: string; l: string }

interface SelectProps {
  options: SelectOption[]
  value?: string
  onChange?: (e: ChangeEvent<HTMLSelectElement>) => void
  style?: CSSProperties
}

export function Select({ options, value, onChange, style }: SelectProps) {
  const C = useC()
  return (
    <select
      value={value || ''}
      onChange={onChange}
      style={{
        // mock select.filter — 13px/600, 11px radius, line-2 border
        fontSize: 13, fontWeight: 600, padding: '10px 13px', borderRadius: 11,
        border: `1px solid ${C.borderMed}`, background: C.white,
        color: C.sub, outline: 'none',
        ...(style || {})
      }}
    >
      {options.map((o) => {
        const val = typeof o === 'string' ? o : o.v
        const lbl = typeof o === 'string' ? o : o.l
        return <option key={val} value={val}>{lbl}</option>
      })}
    </select>
  )
}
