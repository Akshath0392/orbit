import React, { CSSProperties } from 'react'
import { useC } from '../ThemeContext'
import { R } from '../theme'

interface CardProps {
  children: React.ReactNode
  style?: CSSProperties
  onClick?: () => void
}

export function Card({ children, style, onClick }: CardProps) {
  const C = useC()
  return (
    <div
      onClick={onClick}
      style={{
        background: C.white, border: `1px solid ${C.border}`,
        borderRadius: R.lg, padding: '14px 16px',
        boxShadow: C.shadow,
        ...(style || {})
      }}
    >
      {children}
    </div>
  )
}
