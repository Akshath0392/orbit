import { useC } from '../ThemeContext'

interface PaginationProps {
  page: number
  totalPages: number
  onPageChange: (page: number) => void
  pageSize?: number
  onPageSizeChange?: (size: number) => void
  pageSizeOptions?: number[]
}

const DEFAULT_SIZES = [10, 25, 50, 100]

export function Pagination({
  page, totalPages, onPageChange,
  pageSize, onPageSizeChange,
  pageSizeOptions = DEFAULT_SIZES,
}: PaginationProps) {
  const C = useC()
  const showSizeSelector = pageSize != null && onPageSizeChange != null
  // Render even at 1 page when a size selector is requested — otherwise the
  // user can't increase page size to load more.
  if (totalPages <= 1 && !showSizeSelector) return null
  return (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'flex-end',
      gap: 10, padding: '10px 16px', borderTop: `1px solid ${C.border}`
    }}>
      {showSizeSelector && (
        <>
          <label style={{ fontSize: 11, color: C.muted }}>
            Rows
            <select
              aria-label="Rows per page"
              value={pageSize}
              onChange={e => onPageSizeChange!(Number(e.target.value))}
              style={{
                marginLeft: 6,
                fontSize: 12, padding: '3px 7px', borderRadius: 6,
                border: `1px solid ${C.border}`, background: C.white, color: C.text
              }}
            >
              {pageSizeOptions.map(s => <option key={s} value={s}>{s}</option>)}
            </select>
          </label>
        </>
      )}
      <button
        onClick={() => onPageChange(page - 1)}
        disabled={page === 0}
        style={{
          fontSize: 12, padding: '5px 12px', borderRadius: 7, cursor: page === 0 ? 'not-allowed' : 'pointer',
          border: `1px solid ${C.border}`, background: 'transparent',
          color: page === 0 ? C.muted : C.sub, fontWeight: 500
        }}
      >
        ← Prev
      </button>
      <span style={{ fontSize: 12, color: C.sub }}>
        Page {page + 1} of {Math.max(totalPages, 1)}
      </span>
      <button
        onClick={() => onPageChange(page + 1)}
        disabled={page >= totalPages - 1}
        style={{
          fontSize: 12, padding: '5px 12px', borderRadius: 7,
          cursor: page >= totalPages - 1 ? 'not-allowed' : 'pointer',
          border: `1px solid ${C.border}`, background: 'transparent',
          color: page >= totalPages - 1 ? C.muted : C.sub, fontWeight: 500
        }}
      >
        Next →
      </button>
    </div>
  )
}

// Persist last-used size per table key. Consumers wire:
//   const [size, setSize] = usePersistedPageSize('bugs-prod', 10)
//   <Pagination ... pageSize={size} onPageSizeChange={s => { setSize(s); setPage(0) }} />
import { useState } from 'react'

export function usePersistedPageSize(tableKey: string, defaultSize = 10): [number, (n: number) => void] {
  const storageKey = `orbit:page-size:${tableKey}`
  const [size, setSize] = useState<number>(() => {
    if (typeof window === 'undefined') return defaultSize
    const raw = window.localStorage.getItem(storageKey)
    const n = raw ? Number(raw) : NaN
    return Number.isFinite(n) && n > 0 ? n : defaultSize
  })
  const set = (n: number) => {
    setSize(n)
    try { window.localStorage.setItem(storageKey, String(n)) } catch { /* quota / private mode — ignore */ }
  }
  return [size, set]
}
