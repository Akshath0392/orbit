// Features Control Center — admin CRUD for feature flags (controlled release).
// Key convention: screen.<navId> gates a route + sidebar item, section.<page>.<name>
// gates a component. Unknown keys are visible, so rows exist only for held-back
// features. Pilot workflow: create at NONE → PILOT with emails → ALL → delete.
import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useC } from '../../../design/ThemeContext'
import { PageHeader } from '../../../design/components/PageHeader'
import { TableWrap } from '../../../design/components/TableWrap'
import { THead } from '../../../design/components/THead'
import { StatusPill } from '../../../design/components/StatusPill'
import { Btn } from '../../../design/components/Btn'
import { Input } from '../../../design/components/Input'
import { Select } from '../../../design/components/Select'
import { LoadingState, ErrorState, EmptyState } from '../../../design/components/PageState'
import { Pagination } from '../../../design/components/Pagination'
import { api } from '../../../api/client'

interface FlagRow {
  id: number
  flagKey: string
  description: string | null
  audience: 'ALL' | 'PILOT' | 'NONE'
  pilotEmails: string[]
  updatedBy: string | null
  updatedAt: string | null
}

const AUDIENCES = ['ALL', 'PILOT', 'NONE']
const AUDIENCE_PILL: Record<string, string> = { ALL: 'ACTIVE', PILOT: 'PENDING', NONE: 'BLOCKED' }

interface Draft {
  flagKey: string
  description: string
  audience: string
  pilotEmails: string   // comma-separated in the editor
}

const EMPTY_DRAFT: Draft = { flagKey: '', description: '', audience: 'NONE', pilotEmails: '' }

export function FeatureFlagsPage() {
  const C = useC()
  const qc = useQueryClient()
  const [draft, setDraft] = useState<Draft>(EMPTY_DRAFT)
  const [editingId, setEditingId] = useState<number | 'new' | null>(null)
  const [toast, setToast] = useState('')
  const [page, setPage] = useState(0)

  const { data: flagPage, isLoading, error } = useQuery({
    queryKey: ['admin-feature-flags', page],
    queryFn: () => api.get('/admin/feature-flags', { params: { page, size: 20 } }).then(r => r.data),
  })
  const flags: FlagRow[] = flagPage?.content ?? []
  const totalPages = flagPage?.totalPages ?? 1

  const showToast = (msg: string, ms = 2500) => { setToast(msg); setTimeout(() => setToast(''), ms) }
  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['admin-feature-flags'] })
    qc.invalidateQueries({ queryKey: ['feature-flags'] })
  }

  const startEdit = (f: FlagRow) => {
    setEditingId(f.id)
    setDraft({
      flagKey: f.flagKey,
      description: f.description ?? '',
      audience: f.audience,
      pilotEmails: f.pilotEmails.join(', '),
    })
  }

  const save = () => {
    if (!draft.flagKey.trim()) return
    api.post('/admin/feature-flags', {
      flagKey: draft.flagKey.trim(),
      description: draft.description.trim() || null,
      audience: draft.audience,
      pilotEmails: draft.pilotEmails.split(',').map(e => e.trim()).filter(Boolean),
    })
      .then(() => { showToast('Flag saved'); setEditingId(null); setDraft(EMPTY_DRAFT); refresh() })
      .catch(err => showToast(err?.response?.data?.error ?? 'Failed to save flag'))
  }

  const remove = (f: FlagRow) => {
    api.delete(`/admin/feature-flags/${f.id}`)
      .then(() => { showToast(`Released "${f.flagKey}" to everyone (flag removed)`); refresh() })
      .catch(() => showToast('Failed to delete flag'))
  }

  if (isLoading) return <LoadingState />
  if (error) return <ErrorState error={error} />

  const editorRow = (key: string | number) => (
    <tr key={key} style={{ borderTop: `1px solid ${C.border}`, background: C.indigoPale }}>
      <td style={{ padding: '10px 12px', minWidth: 180 }}>
        {editingId === 'new'
          ? <Input placeholder="screen.uat / section.alerts.stats" value={draft.flagKey} onChange={v => setDraft(d => ({ ...d, flagKey: v }))} />
          : <span style={{ fontSize: 12, fontWeight: 600, color: C.text }}>{draft.flagKey}</span>}
      </td>
      <td style={{ padding: '10px 12px', minWidth: 200 }}>
        <Input placeholder="What is being held back and why" value={draft.description} onChange={v => setDraft(d => ({ ...d, description: v }))} />
      </td>
      <td style={{ padding: '10px 12px' }}>
        <Select options={AUDIENCES} value={draft.audience} onChange={e => setDraft(d => ({ ...d, audience: e.target.value }))} />
      </td>
      <td style={{ padding: '10px 12px', minWidth: 220 }}>
        <Input placeholder="pilot1@x.io, pilot2@x.io" value={draft.pilotEmails} onChange={v => setDraft(d => ({ ...d, pilotEmails: v }))}
          style={{ opacity: draft.audience === 'PILOT' ? 1 : 0.45 }} />
      </td>
      <td style={{ padding: '10px 12px', whiteSpace: 'nowrap' }}>
        <Btn variant="primary" onClick={save} style={{ marginRight: 6 }}>Save</Btn>
        <Btn onClick={() => { setEditingId(null); setDraft(EMPTY_DRAFT) }}>Cancel</Btn>
      </td>
    </tr>
  )

  return (
    <div style={{ padding: '22px 24px' }}>
      {toast && (
        <div style={{
          position: 'fixed', bottom: 24, right: 24, zIndex: 9999,
          padding: '10px 18px', borderRadius: 8, background: C.text,
          color: '#fff', fontSize: 13, fontWeight: 500, boxShadow: '0 8px 24px rgba(0,0,0,0.18)'
        }}>
          {toast}
        </div>
      )}

      <PageHeader
        title="Features Control Center"
        subtitle="Controlled release — hold features back, pilot with a few users, then release to everyone"
        actions={
          <Btn variant="primary" onClick={() => { setEditingId('new'); setDraft(EMPTY_DRAFT) }}>
            + New flag
          </Btn>
        }
      />

      <div style={{ fontSize: 12, color: C.sub, marginBottom: 14, lineHeight: 1.6 }}>
        A feature without a flag is visible to everyone — create a flag only to hold something back.
        <strong> NONE</strong> hides it, <strong>PILOT</strong> shows it to the listed emails,
        <strong> ALL</strong> shows it to everyone. Admins always see everything. Deleting a flag releases the feature.
      </div>

      <TableWrap footer={<Pagination page={page} totalPages={totalPages} onPageChange={setPage} />}>
        <table style={{ width: '100%', fontSize: 12 }}>
          <THead cols={['Flag key', 'Description', 'Audience', 'Pilot users', '']} />
          <tbody>
            {editingId === 'new' && editorRow('new')}
            {flags.length === 0 && editingId !== 'new' && (
              <tr><td colSpan={5}><EmptyState message="No flags — every feature is live for everyone" icon="⚑" /></td></tr>
            )}
            {flags.map(f => editingId === f.id ? editorRow(f.id) : (
              <tr key={f.id} style={{ borderTop: `1px solid ${C.border}` }}>
                <td style={{ padding: '10px 12px', fontWeight: 600, color: C.text, whiteSpace: 'nowrap' }}>{f.flagKey}</td>
                <td style={{ padding: '10px 12px', color: C.sub }}>{f.description || '—'}</td>
                <td style={{ padding: '10px 12px' }}>
                  <StatusPill status={AUDIENCE_PILL[f.audience]} label={f.audience} />
                </td>
                <td style={{ padding: '10px 12px', color: C.sub }}>
                  {f.audience === 'PILOT' ? (f.pilotEmails.join(', ') || '—') : '—'}
                </td>
                <td style={{ padding: '10px 12px', whiteSpace: 'nowrap' }}>
                  <Btn onClick={() => startEdit(f)} style={{ marginRight: 6 }}>Edit</Btn>
                  <Btn variant="danger" onClick={() => remove(f)}>Delete</Btn>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </TableWrap>
    </div>
  )
}
