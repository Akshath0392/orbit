import { ErrorState } from '../../../design/components/PageState'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useC } from '../../../design/ThemeContext'
import { Badge } from '../../../design/components/Badge'
import { THead } from '../../../design/components/THead'
import { Pagination } from '../../../design/components/Pagination'
import { api } from '../../../api/client'

const PAGE_SIZE = 10
const CATEGORIES = ['backlog', 'in-progress', 'qa', 'uat', 'blocked', 'ready', 'released', 'closed']

// Canonical sync vocabulary — the value is what the backend keys
// stage resolution on; the label is display-only.
const TYPE_OPTIONS = [
  { value: 'CR', label: 'CR' },
  { value: 'PROD_BUG', label: 'Prod Bug' },
  { value: 'UAT_BUG', label: 'UAT Bug' },
  { value: 'TASK', label: 'Task' },
  { value: 'ALL', label: 'All' },
]
const typeLabel = (v: string) => TYPE_OPTIONS.find(o => o.value === v)?.label ?? v

type StageRow = { id: number; name: string; displayOrder: number; category: string; mappingCount: number; issueCount: number }

const stageDot = (category: string, C: any) =>
  category === 'backlog' ? C.amber
  : category === 'blocked' ? C.red
  : category === 'released' || category === 'closed' ? C.green
  : C.indigo

export function LifecycleMappingPage() {
  const C = useC()
  const [showModal, setShowModal] = useState(false)
  const [newJira, setNewJira] = useState('')
  const [newType, setNewType] = useState('CR')
  const [newStage, setNewStage] = useState('')
  const [savingNew, setSavingNew] = useState(false)
  const [editItem, setEditItem] = useState<any>(null)
  const [page, setPage] = useState(0)

  const { data: mappings = [], isLoading, error, refetch } = useQuery({
    queryKey: ['lifecycle-mappings'],
    queryFn: () => api.get('/admin/lifecycle-mappings').then(r => r.data)
  })
  const { data: stagesData, refetch: refetchStages } = useQuery({
    queryKey: ['admin-stages'],
    queryFn: () => api.get('/admin/stages').then(r => r.data)
  })
  const stages: StageRow[] = Array.isArray(stagesData) ? stagesData : []
  const stageNames = stages.map(s => s.name)

  const [discoverResult, setDiscoverResult] = useState<{ created: number; alreadyExisted: number; backfilled: number } | null>(null)
  const [discovering, setDiscovering] = useState(false)

  const createMapping = (body: any) => {
    setSavingNew(true)
    return api.post('/admin/lifecycle-mappings', body)
      .then(() => { refetch(); refetchStages(); setShowModal(false); setNewJira(''); setNewType('CR'); setNewStage('') })
      .finally(() => setSavingNew(false))
  }
  const updateMapping = (item: any, body: any) =>
    api.delete(`/admin/lifecycle-mappings/${item.id}`)
      .then(() => api.post('/admin/lifecycle-mappings', body))
      .then(() => { refetch(); refetchStages(); setEditItem(null) })

  const autoDiscover = async () => {
    setDiscovering(true)
    setDiscoverResult(null)
    try {
      const res = await api.post('/admin/lifecycle-mappings/auto-discover')
      setDiscoverResult(res.data)
      refetch()
      refetchStages()
    } finally {
      setDiscovering(false)
    }
  }

  const mapList = mappings as any[]
  const totalPages = Math.ceil(mapList.length / PAGE_SIZE)
  const paged = mapList.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)

  if (isLoading) return <div style={{ padding: 40, color: C.sub }}>Loading…</div>
  if (error) return <ErrorState error={error} />

  return (
    <div style={{ padding: '22px 24px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 18 }}>
        <div>
          <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>Lifecycle mapping</div>
          <div style={{ fontSize: 12, color: C.sub, marginTop: 3 }}>Map Jira statuses → delivery stages</div>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          {discoverResult && (
            <span style={{ fontSize: 11, color: C.greenDeep, background: C.greenPale, padding: '4px 10px', borderRadius: 6 }}>
              {discoverResult.created} created · {discoverResult.backfilled} issues backfilled
            </span>
          )}
          <button
            onClick={autoDiscover}
            disabled={discovering}
            style={{ fontSize: 12, padding: '6px 14px', borderRadius: 7, border: `1px solid ${C.border}`, background: C.white, color: C.indigo, cursor: 'pointer', fontWeight: 500 }}
          >
            {discovering ? '⟳ Discovering…' : '↻ Auto-discover from Jira'}
          </button>
          <button onClick={() => setShowModal(true)} style={{ fontSize: 12, padding: '6px 14px', borderRadius: 7, border: 'none', background: C.indigo, color: '#fff', cursor: 'pointer', fontWeight: 500 }}>+ Add mapping</button>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 260px', gap: 16 }}>
        <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden' }}>
          <table style={{ width: '100%', fontSize: 12 }}>
            <THead cols={['Jira status', 'Issue type', 'Stage', 'Last updated', '']} />
            <tbody>
              {paged.length === 0 && (
                <tr>
                  <td colSpan={5} style={{ padding: '20px', textAlign: 'center', color: C.muted }}>No data yet</td>
                </tr>
              )}
              {paged.map((m: any, i: number) => (
                <tr key={m.id ?? i} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none', background: C.white }}>
                  <td style={{ padding: '10px 12px', fontFamily: 'monospace', fontSize: 11, fontWeight: 600, color: C.text }}>{m.jira}</td>
                  <td style={{ padding: '10px 12px' }}><Badge level={m.type === 'CR' ? 'blue' : 'risk'} label={typeLabel(m.type)} /></td>
                  <td style={{ padding: '10px 12px', fontWeight: 500, color: C.indigo }}>{m.akki}</td>
                  <td style={{ padding: '10px 12px', color: C.muted, fontSize: 11 }}>{m.updatedAt ?? 'Jun 1 · Admin'}</td>
                  <td style={{ padding: '10px 12px' }}>
                    <button onClick={() => setEditItem(m)} style={{ fontSize: 11, padding: '3px 9px', borderRadius: 5, cursor: 'pointer', border: `1px solid ${C.border}`, background: 'transparent', color: C.sub }}>Edit</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
        </div>

        <StagesPanel stages={stages} onChanged={() => { refetchStages(); refetch() }} />
      </div>

      {showModal && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,22,41,.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: C.white, borderRadius: 14, width: 440, boxShadow: '0 20px 60px rgba(0,0,0,.3)' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '16px 20px', borderBottom: `1px solid ${C.border}` }}>
              <div style={{ fontSize: 15, fontWeight: 600, color: C.text }}>Add lifecycle mapping</div>
              <button onClick={() => setShowModal(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 18, color: C.muted }}>✕</button>
            </div>
            <div style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div>
                <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Jira status name (exact match)</div>
                <input
                  value={newJira}
                  onChange={e => setNewJira(e.target.value)}
                  placeholder="e.g. Code Review"
                  style={{ fontSize: 12, padding: '7px 10px', borderRadius: 7, border: `1px solid ${C.border}`, outline: 'none', width: '100%', boxSizing: 'border-box' as const }}
                />
              </div>
              <div>
                <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Issue type</div>
                <select
                  value={newType}
                  onChange={e => setNewType(e.target.value)}
                  style={{ fontSize: 12, padding: '7px 10px', borderRadius: 7, border: `1px solid ${C.border}`, outline: 'none', width: '100%' }}
                >
                  {TYPE_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
                </select>
              </div>
              <div>
                <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Maps to stage</div>
                <select
                  value={newStage || stageNames[0] || ''}
                  onChange={e => setNewStage(e.target.value)}
                  style={{ fontSize: 12, padding: '7px 10px', borderRadius: 7, border: `1px solid ${C.border}`, outline: 'none', width: '100%' }}
                >
                  {stageNames.map(s => <option key={s} value={s}>{s}</option>)}
                </select>
              </div>
              <button
                disabled={!newJira.trim() || savingNew}
                onClick={() => createMapping({ jira: newJira.trim(), type: newType, akki: newStage || stageNames[0] })}
                style={{ padding: '9px', borderRadius: 7, border: 'none', background: C.indigo, color: '#fff', fontSize: 13, fontWeight: 600, cursor: 'pointer', opacity: !newJira.trim() ? 0.6 : 1 }}
              >
                {savingNew ? 'Saving…' : 'Save mapping'}
              </button>
            </div>
          </div>
        </div>
      )}

      {editItem && (
        <EditMappingModal
          item={editItem}
          stageNames={stageNames}
          onClose={() => setEditItem(null)}
          onSave={(body: any) => updateMapping(editItem, body)}
        />
      )}
    </div>
  )
}

function StagesPanel({ stages, onChanged }: { stages: StageRow[]; onChanged: () => void }) {
  const C = useC()
  const [addName, setAddName] = useState('')
  const [addCategory, setAddCategory] = useState('in-progress')
  const [busy, setBusy] = useState(false)
  const [editId, setEditId] = useState<number | null>(null)
  const [editName, setEditName] = useState('')
  const [editCategory, setEditCategory] = useState('in-progress')
  const [panelError, setPanelError] = useState<string | null>(null)

  const run = async (fn: () => Promise<any>) => {
    setBusy(true)
    setPanelError(null)
    try {
      await fn()
      onChanged()
    } catch (e: any) {
      setPanelError(e?.response?.data?.error ?? 'Something went wrong')
    } finally {
      setBusy(false)
    }
  }

  const addStage = () => run(() =>
    api.post('/admin/stages', { name: addName.trim(), category: addCategory })
      .then(() => { setAddName(''); setAddCategory('in-progress') }))

  const saveEdit = (s: StageRow) => run(() =>
    api.patch(`/admin/stages/${s.id}`, { name: editName.trim(), category: editCategory })
      .then(() => setEditId(null)))

  const deleteStage = (s: StageRow) => run(() => api.delete(`/admin/stages/${s.id}`))

  const selectStyle = { fontSize: 11, padding: '5px 6px', borderRadius: 6, border: `1px solid ${C.border}`, outline: 'none', background: C.white, color: C.text }

  return (
    <div>
      <div style={{ fontSize: 11, fontWeight: 600, color: C.sub, letterSpacing: 0.5, textTransform: 'uppercase', marginBottom: 8 }}>Stages</div>
      <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: '12px 14px' }}>
        {stages.map((s, i) => {
          const inUse = s.mappingCount > 0 || s.issueCount > 0
          const editing = editId === s.id
          return (
            <div key={s.id} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '6px 0', borderTop: i > 0 ? `1px solid ${C.border}` : 'none' }}>
              <span style={{ width: 7, height: 7, borderRadius: '50%', flexShrink: 0, background: stageDot(s.category, C) }} />
              {editing ? (
                <>
                  <input
                    value={editName}
                    onChange={e => setEditName(e.target.value)}
                    style={{ fontSize: 12, padding: '4px 6px', borderRadius: 6, border: `1px solid ${C.border}`, outline: 'none', minWidth: 0, flex: 1 }}
                    autoFocus
                  />
                  <select value={editCategory} onChange={e => setEditCategory(e.target.value)} style={selectStyle}>
                    {CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
                  </select>
                  <button
                    onClick={() => saveEdit(s)}
                    disabled={busy || !editName.trim()}
                    title="Save"
                    style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 13, color: C.greenDeep, padding: 0 }}
                  >✓</button>
                  <button
                    onClick={() => setEditId(null)}
                    title="Cancel"
                    style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 13, color: C.muted, padding: 0 }}
                  >✕</button>
                </>
              ) : (
                <>
                  <span style={{ fontSize: 12, color: C.text, flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={`${s.mappingCount} mappings · ${s.issueCount} issues`}>{s.name}</span>
                  <button
                    onClick={() => { setEditId(s.id); setEditName(s.name); setEditCategory(s.category); setPanelError(null) }}
                    title="Rename / recategorize"
                    style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 12, color: C.sub, padding: 0 }}
                  >✎</button>
                  <button
                    onClick={() => deleteStage(s)}
                    disabled={busy || inUse}
                    title={inUse ? `In use by ${s.mappingCount} mapping(s) · ${s.issueCount} issue(s) — remap first` : 'Delete stage'}
                    style={{ background: 'none', border: 'none', cursor: inUse ? 'not-allowed' : 'pointer', fontSize: 12, color: inUse ? C.border : C.red, padding: 0 }}
                  >✕</button>
                </>
              )}
            </div>
          )
        })}
        {stages.length === 0 && <div style={{ fontSize: 12, color: C.muted, padding: '4px 0' }}>No stages yet</div>}

        <div style={{ borderTop: `1px solid ${C.border}`, marginTop: 8, paddingTop: 10, display: 'flex', flexDirection: 'column', gap: 6 }}>
          <input
            value={addName}
            onChange={e => setAddName(e.target.value)}
            placeholder="New stage name"
            style={{ fontSize: 12, padding: '6px 8px', borderRadius: 6, border: `1px solid ${C.border}`, outline: 'none', boxSizing: 'border-box' as const, width: '100%' }}
          />
          <div style={{ display: 'flex', gap: 6 }}>
            <select value={addCategory} onChange={e => setAddCategory(e.target.value)} style={{ ...selectStyle, flex: 1 }}>
              {CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
            <button
              onClick={addStage}
              disabled={busy || !addName.trim()}
              style={{ fontSize: 12, padding: '5px 12px', borderRadius: 6, border: 'none', background: C.indigo, color: '#fff', cursor: 'pointer', fontWeight: 500, opacity: !addName.trim() ? 0.6 : 1 }}
            >+ Add</button>
          </div>
          {panelError && <div style={{ fontSize: 11, color: C.red }}>{panelError}</div>}
        </div>
      </div>
    </div>
  )
}

function EditMappingModal({ item, stageNames, onClose, onSave }: { item: any; stageNames: string[]; onClose: () => void; onSave: (body: any) => void }) {
  const C = useC()
  const [jiraStatus, setJiraStatus] = useState(item.jira ?? '')
  const [issueType, setIssueType] = useState(item.type ?? 'CR')
  const [stage, setStage] = useState(item.akki ?? stageNames[0] ?? '')
  const [saving, setSaving] = useState(false)

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,22,41,.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
      <div style={{ background: C.white, borderRadius: 14, width: 440, boxShadow: '0 20px 60px rgba(0,0,0,.3)' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '16px 20px', borderBottom: `1px solid ${C.border}` }}>
          <div style={{ fontSize: 15, fontWeight: 600, color: C.text }}>Edit lifecycle mapping</div>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 18, color: C.muted }}>✕</button>
        </div>
        <div style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div>
            <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Jira status name (exact match)</div>
            <input
              value={jiraStatus}
              onChange={e => setJiraStatus(e.target.value)}
              placeholder="e.g. Code Review"
              style={{ fontSize: 12, padding: '7px 10px', borderRadius: 7, border: `1px solid ${C.border}`, outline: 'none', width: '100%' }}
            />
          </div>
          <div>
            <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Issue type</div>
            <select
              value={issueType}
              onChange={e => setIssueType(e.target.value)}
              style={{ fontSize: 12, padding: '7px 10px', borderRadius: 7, border: `1px solid ${C.border}`, outline: 'none', width: '100%' }}
            >
              {TYPE_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </div>
          <div>
            <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Maps to stage</div>
            <select
              value={stage}
              onChange={e => setStage(e.target.value)}
              style={{ fontSize: 12, padding: '7px 10px', borderRadius: 7, border: `1px solid ${C.border}`, outline: 'none', width: '100%' }}
            >
              {stageNames.map(s => <option key={s} value={s}>{s}</option>)}
            </select>
          </div>
          <button
            disabled={!jiraStatus.trim() || saving}
            onClick={() => { setSaving(true); onSave({ jira: jiraStatus, type: issueType, akki: stage }) }}
            style={{ padding: '9px', borderRadius: 7, border: 'none', background: C.indigo, color: '#fff', fontSize: 13, fontWeight: 600, cursor: 'pointer' }}
          >
            {saving ? 'Saving…' : 'Update mapping'}
          </button>
        </div>
      </div>
    </div>
  )
}
