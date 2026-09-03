import React from 'react'
import { useC } from '../ThemeContext'

interface ModalProps {
  title: string
  children: React.ReactNode
  onClose: () => void
  width?: number
}

export function Modal({ title, children, onClose, width = 520 }: ModalProps) {
  const C = useC()
  return (
    <div style={{
      position: 'fixed', inset: 0, background: 'rgba(15,22,41,.55)',
      display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000
    }}>
      <div style={{
        background: C.white, borderRadius: 14, width,
        maxHeight: '88vh', overflow: 'auto',
        boxShadow: '0 20px 60px rgba(0,0,0,.3)'
      }}>
        <div style={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          padding: '16px 20px', borderBottom: `1px solid ${C.border}`
        }}>
          <div style={{ fontSize: 15, fontWeight: 600, color: C.text }}>{title}</div>
          <button
            onClick={onClose}
            style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 18, color: C.muted }}
          >
            ✕
          </button>
        </div>
        <div style={{ padding: '20px' }}>{children}</div>
      </div>
    </div>
  )
}
