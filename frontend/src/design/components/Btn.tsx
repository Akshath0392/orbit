import React, { CSSProperties } from 'react'
import { useC } from '../ThemeContext'

type BtnVariant = 'primary' | 'danger' | 'ghost' | 'success' | 'warn'
type BtnSize = 'sm' | 'md'

interface BtnProps {
  children: React.ReactNode
  variant?: BtnVariant
  size?: BtnSize
  onClick?: () => void
  style?: CSSProperties
  type?: 'button' | 'submit' | 'reset'
  disabled?: boolean
}

export function Btn({ children, variant = 'ghost', size = 'sm', onClick, style, type = 'button', disabled }: BtnProps) {
  const C = useC()
  // mock .btn / .btn.sm — 11px (sm 9px) radius, 650 weight, line-2 border
  const base: CSSProperties = {
    cursor: 'pointer', border: 'none', fontWeight: 650,
    display: 'inline-flex', alignItems: 'center', gap: 7,
    ...(size === 'sm'
      ? { fontSize: 12, padding: '7px 12px', borderRadius: 9 }
      : { fontSize: 13, padding: '10px 16px', borderRadius: 11 })
  }
  const variants: Record<BtnVariant, CSSProperties> = {
    primary: { ...base, background: C.indigo, color: '#fff' },
    danger:  { ...base, background: C.red, color: '#fff' },
    ghost:   { ...base, background: C.white, color: C.sub, border: `1px solid ${C.borderMed}` },
    success: { ...base, background: C.greenPale, color: C.greenDeep, border: '1px solid #A7F3D0' },
    warn:    { ...base, background: C.amberPale, color: C.amberDeep, border: '1px solid #FDE68A' },
  }
  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      style={{ ...variants[variant], ...(style || {}) }}
    >
      {children}
    </button>
  )
}
