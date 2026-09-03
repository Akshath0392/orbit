import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useC } from '../../../design/ThemeContext'
import type { Colors } from '../../../design/theme'
import { api } from '../../../api/client'

const PHASES = ['FSD', 'DEV', 'QA', 'UAT', 'PROD']

const STATUS_COLORS = (C: Colors): Record<string, string> => ({
  NOT_STARTED:    C.muted,
  IN_PROGRESS:    C.blue,
  ON_TRACK:       C.green,
  DELAYED_SELF:   C.amber,
  DELAYED_SYSTEM: C.red,
  COMPLETED:      C.teal,
})

export function PhaseDeliveriesPage() {
  const C = useC()
  const qc = useQueryClient()
  const [selectedProject, setSelectedProject] = useState<any>(null)
  const [toast, setToast] = useState('')
  const [toastOk, setToastOk] = useState(true)
  const [addingPhase, setAddingPhase] = useState(false)
  const [newPhase, setNewPhase] = useState<any>({ phase: 'FSD', assigneeEmail: '', assigneeName: '', startDate: '', endDate: '' })
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editForm, setEditForm] = useState<any>({})

  const showToast = (msg: string, ok = true) => {
    setToast(msg); setToastOk(ok); setTimeout(() => setToast(''), 3000)
  }

  const { data: projects = [] } = useQuery({
    queryKey: ['projects-list'],
    queryFn: () => api.get('/admin/projects').then(r => r.data),
  })

  const { data: phases = [] } = useQuery({
    queryKey: ['phase-statuses', selectedProject?.id],
    queryFn: () => selectedProject
      ? api.get(`/admin/phase-statuses/project/${selectedProject.id}`).then(r => r.data)
      : Promise.resolve([]),
    enabled: !!selectedProject,
  })

  const createPhase = async () => {
    if (!selectedProject) return
    try {
      await api.post('/admin/phase-statuses', { ...newPhase, projectId: selectedProject.id })
      qc.invalidateQueries({ queryKey: ['phase-statuses'] })
      setAddingPhase(false)
      setNewPhase({ phase: 'FSD', assigneeEmail: '', assigneeName: '', startDate: '', endDate: '' })
      showToast('Phase added')
    } catch { showToast('Failed to add phase', false) }
  }

  const saveEdit = async (id: number) => {
    try {
      await api.put(`/admin/phase-statuses/${id}`, editForm)
      qc.invalidateQueries({ queryKey: ['phase-statuses'] })
      setEditingId(null)
      showToast('Phase updated')
    } catch { showToast('Failed to update', false) }
  }

  const deletePhase = async (id: number) => {
    try {
      await api.delete(`/admin/phase-statuses/${id}`)
      qc.invalidateQueries({ queryKey: ['phase-statuses'] })
      showToast('Phase removed')
    } catch { showToast('Failed to delete', false) }
  }

  return (
    <div style={{ padding: '22px 24px' }}>
      {toast && (
        <div style={{ position: 'fixed', bottom: 24, right: 24, zIndex: 9999, padding: '10px 18px', borderRadius: 8, background: toastOk ? C.greenDeep : C.red, color: '#fff', fontSize: 13, fontWeight: 500 }}>
          {toast}
        </div>
      )}

      <div style={{ marginBottom: 20 }}>
        <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>Phase deliveries</div>
        <div style={{ fontSize: 12, color: C.sub, marginTop: 3 }}>Track delivery phase dates and assignees per project — drives T-2, T-1, D-Day notifications</div>
      </div>

      {/* Project selector */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 20 }}>
        <div style={{ fontSize: 12, fontWeight: 600, color: C.sub }}>Project</div>
        <select
          value={selectedProject?.id || ''}
          onChange={e => {
            const p = (projects as any[]).find((x: any) => String(x.id) === e.target.value)
            setSelectedProject(p || null)
          }}
          style={{ fontSize: 12, padding: '7px 10px', borderRadius: 7, border: `1px solid ${C.border}`, background: C.white, color: C.text, minWidth: 240 }}
        >
          <option value="">— Select a project —</option>
          {(projects as any[]).map((p: any) => (
            <option key={p.id} value={p.id}>{p.name}{p.clientName ? ` · ${p.clientName}` : ''}</option>
          ))}
        </select>
      </div>

      {selectedProject && (
        <>
          {/* Summary row */}
          <div style={{ display: 'flex', gap: 12, marginBottom: 20 }}>
            {PHASES.map(ph => {
              const ps = (phases as any[]).find((p: any) => p.phase === ph)
              const color = ps ? STATUS_COLORS(C)[ps.status] || C.muted : C.borderMed
              return (
                <div key={ph} style={{ flex: 1, background: C.white, border: `1px solid ${C.border}`, borderRadius: 8, padding: '10px 12px', borderTop: `3px solid ${color}` }}>
                  <div style={{ fontSize: 11, fontWeight: 700, color: C.sub, letterSpacing: 0.5, marginBottom: 4 }}>{ph}</div>
                  {ps ? (
                    <>
                      <div style={{ fontSize: 10, color: color, fontWeight: 600, marginBottom: 2 }}>{ps.status.replace('_', ' ')}</div>
                      <div style={{ fontSize: 10, color: C.muted }}>{ps.endDate || 'No date'}</div>
                    </>
                  ) : (
                    <div style={{ fontSize: 10, color: C.muted }}>Not configured</div>
                  )}
                </div>
              )
            })}
          </div>

          {/* Phase table */}
          <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 10, overflow: 'hidden' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 16px', borderBottom: `1px solid ${C.border}` }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: C.text }}>Phase schedule — {selectedProject.name}</div>
              <button onClick={() => setAddingPhase(true)} style={{ fontSize: 12, padding: '5px 12px', borderRadius: 6, border: 'none', background: C.indigo, color: '#fff', cursor: 'pointer', fontWeight: 600 }}>+ Add phase</button>
            </div>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
              <thead>
                <tr style={{ background: C.canvas }}>
                  {['Phase', 'Start date', 'End date', 'Assignee', 'Status', 'Delay note', ''].map(h => (
                    <th key={h} style={{ padding: '8px 14px', textAlign: 'left', fontSize: 10, fontWeight: 600, color: C.sub, letterSpacing: 0.4, borderBottom: `1px solid ${C.border}` }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {(phases as any[]).length === 0 && (
                  <tr><td colSpan={7} style={{ padding: 24, textAlign: 'center', color: C.muted }}>No phases configured — click "+ Add phase" to start</td></tr>
                )}
                {(phases as any[]).map((ps: any, i: number) => {
                  const editing = editingId === ps.id
                  const color = STATUS_COLORS(C)[ps.status] || C.muted
                  return (
                    <tr key={ps.id} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none' }}>
                      <td style={{ padding: '9px 14px' }}>
                        <span style={{ background: C.indigoPale, color: C.indigo, padding: '3px 8px', borderRadius: 4, fontSize: 11, fontWeight: 700 }}>{ps.phase}</span>
                      </td>
                      <td style={{ padding: '9px 14px' }}>
                        {editing ? <input type="date" value={editForm.startDate || ''} onChange={e => setEditForm((p: any) => ({ ...p, startDate: e.target.value }))} style={inp(C)} /> : <span style={{ color: C.text }}>{ps.startDate || '—'}</span>}
                      </td>
                      <td style={{ padding: '9px 14px' }}>
                        {editing ? <input type="date" value={editForm.endDate || ''} onChange={e => setEditForm((p: any) => ({ ...p, endDate: e.target.value }))} style={inp(C)} /> : <span style={{ color: C.text, fontWeight: 500 }}>{ps.endDate || '—'}</span>}
                      </td>
                      <td style={{ padding: '9px 14px' }}>
                        {editing ? (
                          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                            <input value={editForm.assigneeName || ''} onChange={e => setEditForm((p: any) => ({ ...p, assigneeName: e.target.value }))} style={inp(C)} placeholder="Name" />
                            <input value={editForm.assigneeEmail || ''} onChange={e => setEditForm((p: any) => ({ ...p, assigneeEmail: e.target.value }))} style={inp(C)} placeholder="email@company.com" />
                          </div>
                        ) : (
                          <div>
                            <div style={{ color: C.text, fontWeight: 500 }}>{ps.assigneeName || '—'}</div>
                            {ps.assigneeEmail && <div style={{ fontSize: 10, color: C.muted }}>{ps.assigneeEmail}</div>}
                          </div>
                        )}
                      </td>
                      <td style={{ padding: '9px 14px' }}>
                        <span style={{ background: color + '20', color: color, padding: '3px 7px', borderRadius: 4, fontSize: 10, fontWeight: 600 }}>{ps.status.replace(/_/g, ' ')}</span>
                      </td>
                      <td style={{ padding: '9px 14px', color: C.sub, fontSize: 11 }}>{ps.delayNote || '—'}</td>
                      <td style={{ padding: '9px 14px' }}>
                        <div style={{ display: 'flex', gap: 6 }}>
                          {editing ? (
                            <>
                              <button onClick={() => saveEdit(ps.id)} style={btnStyle(C, true)}>Save</button>
                              <button onClick={() => setEditingId(null)} style={btnStyle(C, false)}>Cancel</button>
                            </>
                          ) : (
                            <>
                              <button onClick={() => { setEditingId(ps.id); setEditForm({ startDate: ps.startDate || '', endDate: ps.endDate || '', assigneeName: ps.assigneeName || '', assigneeEmail: ps.assigneeEmail || '' }) }} style={btnStyle(C, false)}>Edit</button>
                              <button onClick={() => deletePhase(ps.id)} style={{ ...btnStyle(C, false), color: C.red, borderColor: C.red }}>✕</button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  )
                })}

                {/* Add phase row */}
                {addingPhase && (
                  <tr style={{ borderTop: `1px solid ${C.border}`, background: C.canvas }}>
                    <td style={{ padding: '9px 14px' }}>
                      <select value={newPhase.phase} onChange={e => setNewPhase((p: any) => ({ ...p, phase: e.target.value }))} style={inp(C)}>
                        {PHASES.map(ph => <option key={ph} value={ph}>{ph}</option>)}
                      </select>
                    </td>
                    <td style={{ padding: '9px 14px' }}><input type="date" value={newPhase.startDate} onChange={e => setNewPhase((p: any) => ({ ...p, startDate: e.target.value }))} style={inp(C)} /></td>
                    <td style={{ padding: '9px 14px' }}><input type="date" value={newPhase.endDate} onChange={e => setNewPhase((p: any) => ({ ...p, endDate: e.target.value }))} style={inp(C)} /></td>
                    <td style={{ padding: '9px 14px' }}>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                        <input value={newPhase.assigneeName} onChange={e => setNewPhase((p: any) => ({ ...p, assigneeName: e.target.value }))} style={inp(C)} placeholder="Name" />
                        <input value={newPhase.assigneeEmail} onChange={e => setNewPhase((p: any) => ({ ...p, assigneeEmail: e.target.value }))} style={inp(C)} placeholder="email@company.com" />
                      </div>
                    </td>
                    <td style={{ padding: '9px 14px', color: C.muted }}>NOT_STARTED</td>
                    <td style={{ padding: '9px 14px' }}>—</td>
                    <td style={{ padding: '9px 14px' }}>
                      <div style={{ display: 'flex', gap: 6 }}>
                        <button onClick={createPhase} style={btnStyle(C, true)}>Add</button>
                        <button onClick={() => setAddingPhase(false)} style={btnStyle(C, false)}>Cancel</button>
                      </div>
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </>
      )}

      {!selectedProject && (
        <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 10, padding: 40, textAlign: 'center', color: C.muted }}>
          Select a project above to view and manage its delivery phase schedule
        </div>
      )}
    </div>
  )
}

function inp(C: any) {
  return { fontSize: 12, padding: '5px 8px', borderRadius: 6, border: `1px solid ${C.border}`, outline: 'none', width: '100%', boxSizing: 'border-box' as const }
}

function btnStyle(C: any, primary: boolean) {
  return {
    fontSize: 11, padding: '4px 10px', borderRadius: 5, cursor: 'pointer', fontWeight: 500,
    border: primary ? 'none' : `1px solid ${C.border}`,
    background: primary ? C.indigo : 'transparent',
    color: primary ? '#fff' : C.sub,
  } as React.CSSProperties
}
