import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { useC } from '../../../design/ThemeContext'
import { Card } from '../../../design/components/Card'
import { SectionHead } from '../../../design/components/SectionHead'
import { Badge } from '../../../design/components/Badge'
import { BurnBar } from '../../../design/components/BurnBar'
import { Modal } from '../../../design/components/Modal'
import { Btn } from '../../../design/components/Btn'
import { Input } from '../../../design/components/Input'
import { api } from '../../../api/client'

type ModalType = 'add-portfolio' | 'edit-portfolio' | 'add-project' | 'assign-project' | null

// ── Client multi-select checkbox list ────────────────────────────────────────
function ClientPicker({ clients, selected, onChange }: {
  clients: any[]; selected: number[]; onChange: (ids: number[]) => void
}) {
  const C = useC()
  const toggle = (id: number) =>
    onChange(selected.includes(id) ? selected.filter(x => x !== id) : [...selected, id])
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6, maxHeight: 220, overflowY: 'auto',
      border: `1px solid ${C.border}`, borderRadius: 8, padding: '8px 10px', background: C.white }}>
      {clients.length === 0 && <div style={{ fontSize: 12, color: C.muted }}>No clients found</div>}
      {clients.map((c: any) => (
        <label key={c.id} style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer',
          fontSize: 13, color: C.text, padding: '4px 6px', borderRadius: 6,
          background: selected.includes(c.id) ? C.indigoPale : 'transparent' }}>
          <input type="checkbox" checked={selected.includes(c.id)} onChange={() => toggle(c.id)}
            style={{ accentColor: C.indigo, width: 14, height: 14, flexShrink: 0 }} />
          {c.name}
          {c.code && <span style={{ fontSize: 11, color: C.muted, fontFamily: 'monospace' }}>{c.code}</span>}
        </label>
      ))}
    </div>
  )
}

export function PortfolioSetupPage() {
  const C = useC()
  const qc = useQueryClient()
  const navigate = useNavigate()
  const [modal,            setModal]            = useState<ModalType>(null)
  const [selectedPortfolio, setSelectedPortfolio] = useState<any>(null)
  const [expandedProject,   setExpandedProject]   = useState<number | null>(null)
  const [editingJira,       setEditingJira]       = useState<number | null>(null)
  const [jiraForm,          setJiraForm]          = useState<Record<number, any>>({})
  const [routingField,      setRoutingField]      = useState<Record<number, string>>({})
  const [form,              setForm]              = useState<any>({})

  // ── Data queries ──────────────────────────────────────────────────────────
  const { data: clients = [] } = useQuery({
    queryKey: ['clients-list'],
    queryFn: () => api.get('/clients').then(r => r.data)
  })
  const { data: portfolios = [], isLoading } = useQuery({
    queryKey: ['portfolios'],
    queryFn: () => api.get('/portfolios').then(r => r.data)
  })
  const { data: allProjects = [] } = useQuery({
    queryKey: ['projects'],
    queryFn: () => api.get('/projects').then(r => r.data)
  })
  const { data: portfolioProjects = [] } = useQuery({
    queryKey: ['portfolio-projects', selectedPortfolio?.id],
    queryFn: () => api.get(`/portfolios/${selectedPortfolio.id}/projects`).then(r => r.data),
    enabled: !!selectedPortfolio
  })
  const { data: radarData } = useQuery({
    queryKey: ['radar'],
    queryFn: () => api.get('/dashboard/radar').then(r => r.data)
  })

  // ── Mutations ─────────────────────────────────────────────────────────────
  const createPortfolio = useMutation({
    mutationFn: (body: any) => api.post('/portfolios', body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['portfolios'] }); closeModal() }
  })
  const updatePortfolio = useMutation({
    mutationFn: ({ id, body }: any) => api.put(`/portfolios/${id}`, body),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ['portfolios'] })
      setSelectedPortfolio(res.data)
      closeModal()
    }
  })
  const deletePortfolio = useMutation({
    mutationFn: (id: number) => api.delete(`/portfolios/${id}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['portfolios'] }); setSelectedPortfolio(null) }
  })
  const createProject = useMutation({
    mutationFn: (body: any) => api.post('/admin/projects', body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['portfolio-projects', selectedPortfolio?.id] })
      qc.invalidateQueries({ queryKey: ['projects'] })
      closeModal()
    }
  })
  const assignProject = useMutation({
    mutationFn: ({ portfolioId, projectId }: any) => api.post(`/portfolios/${portfolioId}/projects/${projectId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['portfolio-projects', selectedPortfolio?.id] })
  })
  const removeProject = useMutation({
    mutationFn: ({ portfolioId, projectId }: any) => api.delete(`/portfolios/${portfolioId}/projects/${projectId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['portfolio-projects', selectedPortfolio?.id] })
  })
  const saveJiraConfig = useMutation({
    mutationFn: ({ projectId, data }: any) => api.put(`/projects/${projectId}/jira-config`, data),
    onSuccess: (_r, { projectId }) => {
      qc.invalidateQueries({ queryKey: ['portfolio-projects', selectedPortfolio?.id] })
      qc.invalidateQueries({ queryKey: ['projects'] })
      setEditingJira(null)
      setJiraForm(f => { const n = { ...f }; delete n[projectId]; return n })
    }
  })

  const saveRoutingConfig = useMutation({
    mutationFn: ({ projectId, isSharedProdBugs, clientCodeField }: any) =>
      api.put(`/admin/prod-bug-routing/config/${projectId}`, {
        isSharedProdBugs, clientCodeField: clientCodeField || null
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['portfolio-projects', selectedPortfolio?.id] })
      qc.invalidateQueries({ queryKey: ['prod-bug-routing', 'config'] })
    }
  })

  const closeModal = () => { setModal(null); setForm({}) }

  const openCreate = () => { setForm({ clientIds: [] }); setModal('add-portfolio') }
  const openEdit   = () => {
    setForm({
      name:        selectedPortfolio.name        || '',
      description: selectedPortfolio.description || '',
      clientIds:   selectedPortfolio.clientIds   || [],
    })
    setModal('edit-portfolio')
  }
  const openNewProject = () => {
    setForm({ clientId: selectedPortfolio?.clientIds?.[0] ?? '' })
    setModal('add-project')
  }

  const riskByProjectId = Object.fromEntries(
    ((radarData?.projects ?? []) as any[]).map((p: any) => [p.id, p])
  )
  const assignedIds = new Set((portfolioProjects as any[]).map((p: any) => p.id))
  const unassigned  = (allProjects as any[]).filter((p: any) => !assignedIds.has(p.id))

  // Clients belonging to this portfolio (for the new-project form)
  const portfolioClientIds: number[] = selectedPortfolio?.clientIds ?? []
  const portfolioClients = (clients as any[]).filter((c: any) => portfolioClientIds.includes(c.id))

  if (isLoading) return <div style={{ padding: 40, color: C.sub }}>Loading…</div>

  const inp: React.CSSProperties = {
    fontSize: 12, padding: '7px 10px', borderRadius: 7,
    border: `1px solid ${C.border}`, background: C.white,
    color: C.text, width: '100%', outline: 'none', boxSizing: 'border-box',
  }

  return (
    <div style={{ padding: '22px 24px' }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: 20 }}>
        <div>
          <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>Portfolio setup</div>
          <div style={{ fontSize: 12, color: C.sub, marginTop: 3 }}>
            Group projects into portfolios · one portfolio can span multiple clients
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Btn variant="ghost" onClick={() => navigate('/portfolios/prod-bug-routing')}>
            Prod-bug routing
          </Btn>
          <Btn onClick={openCreate}>+ New portfolio</Btn>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '280px 1fr', gap: 16 }}>

        {/* Portfolio list */}
        <div>
          <SectionHead title="Portfolios" />
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {(portfolios as any[]).length === 0 && (
              <div style={{ padding: '20px 0', textAlign: 'center', color: C.muted, fontSize: 13 }}>No portfolios yet</div>
            )}
            {(portfolios as any[]).map((pf: any) => {
              const names: string[] = pf.clientNames ?? (pf.clientName ? [pf.clientName] : [])
              return (
                <div key={pf.id} onClick={() => setSelectedPortfolio(pf)}
                  style={{
                    padding: '10px 12px', borderRadius: 8, cursor: 'pointer',
                    background: selectedPortfolio?.id === pf.id ? C.indigoPale : C.white,
                    border: `1px solid ${selectedPortfolio?.id === pf.id ? C.indigo : C.border}`,
                  }}>
                  <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 8 }}>
                    <div style={{ fontSize: 13, fontWeight: 600, color: C.text, flex: 1 }}>{pf.name}</div>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, justifyContent: 'flex-end' }}>
                      {names.length === 0
                        ? <Badge level="blue" label="Multi-client" />
                        : names.map(n => <Badge key={n} level="neutral" label={n} />)}
                    </div>
                  </div>
                  {pf.description && <div style={{ fontSize: 11, color: C.sub, marginTop: 3 }}>{pf.description}</div>}
                  {pf.projectCount != null && (
                    <div style={{ fontSize: 11, color: C.muted, marginTop: 3 }}>{pf.projectCount} project{pf.projectCount !== 1 ? 's' : ''}</div>
                  )}
                </div>
              )
            })}
          </div>
        </div>

        {/* Portfolio detail */}
        <div>
          {!selectedPortfolio ? (
            <Card>
              <div style={{ padding: '40px 0', textAlign: 'center', color: C.muted, fontSize: 13 }}>
                Select a portfolio to manage its projects
              </div>
            </Card>
          ) : (
            <Card>
              {/* Header */}
              <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: 16 }}>
                <div>
                  <div style={{ fontSize: 16, fontWeight: 700, color: C.text }}>{selectedPortfolio.name}</div>
                  <div style={{ fontSize: 12, color: C.sub, marginTop: 2 }}>
                    {(() => {
                      const names: string[] = (portfolioProjects as any[]).length > 0
                        ? [...new Set((portfolioProjects as any[]).map((p: any) => p.clientName).filter(Boolean))]
                        : (selectedPortfolio.clientNames ?? [])
                      return names.length > 0 ? names.join(' · ') : 'No clients associated'
                    })()}
                    {selectedPortfolio.description && ` · ${selectedPortfolio.description}`}
                  </div>
                </div>
                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                  <Btn onClick={() => setModal('assign-project')}>+ Assign existing</Btn>
                  <Btn onClick={openNewProject}>+ New project</Btn>
                  <button onClick={openEdit}
                    style={{ fontSize: 12, padding: '6px 12px', borderRadius: 7, border: `1px solid ${C.border}`, background: C.white, color: C.sub, cursor: 'pointer' }}>
                    Edit
                  </button>
                  <button onClick={() => deletePortfolio.mutate(selectedPortfolio.id)}
                    style={{ fontSize: 12, padding: '6px 12px', borderRadius: 7, border: `1px solid ${C.red}`, background: C.redPale, color: C.red, cursor: 'pointer' }}>
                    Delete
                  </button>
                </div>
              </div>

              {/* Projects table */}
              <SectionHead title={`Projects (${(portfolioProjects as any[]).length})`} />
              {(portfolioProjects as any[]).length === 0 ? (
                <div style={{ padding: '20px 0', textAlign: 'center', color: C.muted, fontSize: 13 }}>
                  No projects assigned — click "Assign existing" or "New project"
                </div>
              ) : (
                <table style={{ width: '100%', fontSize: 12, borderCollapse: 'collapse' }}>
                  <thead>
                    <tr style={{ background: C.canvas }}>
                      {['Project', 'Client', 'Jira keys', ''].map(h => (
                        <th key={h} style={{ padding: '8px 12px', textAlign: 'left', fontSize: 10, fontWeight: 600, color: C.sub, letterSpacing: 0.4, borderBottom: `1px solid ${C.border}` }}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {(portfolioProjects as any[]).map((p: any, i: number) => {
                      const risk = riskByProjectId[p.id]
                      const isExpanded = expandedProject === p.id
                      const isEditingJ = editingJira === p.id
                      const jf = jiraForm[p.id] || { jiraProjectKeys: p.jiraProjectKeys || '', jiraJqlOverride: p.jiraJqlOverride || '', jiraCrFilter: p.jiraCrFilter || '', jiraBugFilter: p.jiraBugFilter || '' }
                      return (
                        <>
                          <tr key={p.id} onClick={() => setExpandedProject(isExpanded ? null : p.id)}
                            style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none', cursor: 'pointer', background: isExpanded ? C.indigoPale : C.white }}>
                            <td style={{ padding: '10px 12px', fontWeight: 500, color: C.text }}>
                              <span style={{ marginRight: 6, fontSize: 10, color: C.muted }}>{isExpanded ? '▼' : '▶'}</span>
                              {p.name}
                            </td>
                            <td style={{ padding: '10px 12px', color: C.sub }}>{p.clientName}</td>
                            <td style={{ padding: '10px 12px', fontFamily: 'monospace', fontSize: 11, color: C.indigo }}>
                              {p.jiraProjectKeys || <span style={{ color: C.muted }}>not set</span>}
                            </td>
                            <td style={{ padding: '10px 12px' }} onClick={e => e.stopPropagation()}>
                              <button onClick={() => removeProject.mutate({ portfolioId: selectedPortfolio.id, projectId: p.id })}
                                style={{ fontSize: 11, padding: '3px 9px', borderRadius: 5, cursor: 'pointer', border: `1px solid ${C.border}`, background: 'transparent', color: C.sub }}>
                                Remove
                              </button>
                            </td>
                          </tr>
                          {isExpanded && (
                            <tr key={`${p.id}-deep`}>
                              <td colSpan={4} style={{ padding: 0, background: C.canvas }}>
                                <div style={{ padding: '16px 20px', borderTop: `1px solid ${C.border}` }}>
                                  {risk && (
                                    <div style={{ display: 'flex', gap: 24, marginBottom: 16, padding: '10px 14px', background: C.white, borderRadius: 8, border: `1px solid ${C.border}` }}>
                                      <div style={{ minWidth: 100 }}>
                                        <div style={{ fontSize: 10, color: C.sub, marginBottom: 4 }}>Risk level</div>
                                        <Badge level={risk.risk} label={risk.risk === 'critical' ? 'Critical' : risk.risk === 'watch' ? 'Watch' : 'On track'} />
                                      </div>
                                      {[['Slip risk', risk.prob + '%'], ['Budget burn', risk.burn + '%'], ['Team load', risk.load + '%']].map(([lbl, val]) => (
                                        <div key={lbl} style={{ flex: 1 }}>
                                          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                                            <span style={{ fontSize: 10, color: C.sub }}>{lbl}</span>
                                            <span style={{ fontSize: 10, fontWeight: 600, color: parseInt(val) > 80 ? C.red : parseInt(val) > 60 ? C.amber : C.green }}>{val}</span>
                                          </div>
                                          <BurnBar pct={parseInt(val)} h={4} />
                                        </div>
                                      ))}
                                    </div>
                                  )}
                                  <div style={{ marginBottom: 14 }}>
                                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }}>
                                      <div style={{ fontSize: 11, fontWeight: 600, color: C.text }}>Jira configuration</div>
                                      <div style={{ display: 'flex', gap: 6 }} onClick={e => e.stopPropagation()}>
                                        {isEditingJ ? (
                                          <>
                                            <button onClick={() => saveJiraConfig.mutate({ projectId: p.id, data: jf })}
                                              style={{ fontSize: 11, padding: '4px 12px', borderRadius: 6, cursor: 'pointer', border: 'none', background: C.indigo, color: '#fff', fontWeight: 500 }}>
                                              {saveJiraConfig.isPending ? 'Saving…' : 'Save'}
                                            </button>
                                            <button onClick={() => setEditingJira(null)}
                                              style={{ fontSize: 11, padding: '4px 10px', borderRadius: 6, cursor: 'pointer', border: `1px solid ${C.border}`, background: C.white, color: C.sub }}>
                                              Cancel
                                            </button>
                                          </>
                                        ) : (
                                          <button onClick={() => { setEditingJira(p.id); setJiraForm(f => ({ ...f, [p.id]: jf })) }}
                                            style={{ fontSize: 11, padding: '4px 12px', borderRadius: 6, cursor: 'pointer', border: `1px solid ${C.border}`, background: C.white, color: C.indigo, fontWeight: 500 }}>
                                            Edit
                                          </button>
                                        )}
                                      </div>
                                    </div>
                                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                                      {[['Jira project keys', 'jiraProjectKeys'], ['Delta sync JQL', 'jiraJqlOverride'], ['CR filter', 'jiraCrFilter'], ['Bug filter', 'jiraBugFilter']].map(([lbl, field]) => (
                                        <div key={field}>
                                          <div style={{ fontSize: 10, color: C.sub, marginBottom: 3 }}>{lbl}</div>
                                          {isEditingJ ? (
                                            <textarea value={jf[field] || ''} rows={2}
                                              onChange={e => setJiraForm(f => ({ ...f, [p.id]: { ...jf, [field]: e.target.value } }))}
                                              onClick={e => e.stopPropagation()}
                                              style={{ fontFamily: 'monospace', fontSize: 11, color: C.text, background: C.white, padding: '6px 10px', borderRadius: 6, border: `1px solid ${C.indigo}`, width: '100%', resize: 'vertical', outline: 'none', boxSizing: 'border-box' as const }} />
                                          ) : (
                                            <div style={{ fontFamily: 'monospace', fontSize: 11, color: jf[field] ? C.text : C.muted, background: C.white, padding: '6px 10px', borderRadius: 6, border: `1px solid ${C.border}` }}>
                                              {jf[field] || 'not configured'}
                                            </div>
                                          )}
                                        </div>
                                      ))}
                                    </div>
                                  </div>
                                  {/* Shared prod-bug routing */}
                                  {(() => {
                                    const isShared = !!p.isSharedProdBugs
                                    const fieldValue = routingField[p.id] !== undefined
                                      ? routingField[p.id]
                                      : (p.clientCodeField || '')
                                    const dirty = isShared && fieldValue !== (p.clientCodeField || '')
                                    return (
                                      <div style={{ marginBottom: 14, padding: '10px 14px', background: C.white, borderRadius: 8, border: `1px solid ${C.border}` }} onClick={e => e.stopPropagation()}>
                                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                                          <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontSize: 11, fontWeight: 600, color: C.text }}>
                                            <input
                                              type="checkbox"
                                              checked={isShared}
                                              disabled={saveRoutingConfig.isPending}
                                              onChange={(e) => {
                                                const next = e.target.checked
                                                if (next && !fieldValue.trim()) {
                                                  setRoutingField(f => ({ ...f, [p.id]: fieldValue }))
                                                  return
                                                }
                                                saveRoutingConfig.mutate({
                                                  projectId: p.id,
                                                  isSharedProdBugs: next,
                                                  clientCodeField: next ? fieldValue : null
                                                })
                                              }}
                                              style={{ accentColor: C.indigo, width: 14, height: 14 }}
                                            />
                                            Shared prod-bug pool
                                          </label>
                                          <button
                                            onClick={() => navigate('/portfolios/prod-bug-routing')}
                                            style={{ fontSize: 11, padding: '3px 10px', borderRadius: 5, cursor: 'pointer', border: `1px solid ${C.border}`, background: C.white, color: C.indigo, fontWeight: 500 }}>
                                            Manage codes & quarantine →
                                          </button>
                                        </div>
                                        {(isShared || (routingField[p.id] !== undefined)) && (
                                          <div style={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: 8, alignItems: 'end' }}>
                                            <div>
                                              <div style={{ fontSize: 10, color: C.sub, marginBottom: 3 }}>Jira custom field ID for client code</div>
                                              <input
                                                type="text"
                                                value={fieldValue}
                                                onChange={(e) => setRoutingField(f => ({ ...f, [p.id]: e.target.value }))}
                                                placeholder="e.g. customfield_11683"
                                                style={{ fontFamily: 'monospace', fontSize: 11, color: C.text, background: C.white, padding: '6px 10px', borderRadius: 6, border: `1px solid ${dirty ? C.indigo : C.border}`, width: '100%', outline: 'none', boxSizing: 'border-box' as const }}
                                              />
                                            </div>
                                            <button
                                              onClick={() => saveRoutingConfig.mutate({
                                                projectId: p.id,
                                                isSharedProdBugs: true,
                                                clientCodeField: fieldValue.trim()
                                              })}
                                              disabled={!fieldValue.trim() || saveRoutingConfig.isPending || (isShared && !dirty)}
                                              style={{ fontSize: 11, padding: '6px 14px', borderRadius: 6, cursor: !fieldValue.trim() || (isShared && !dirty) ? 'not-allowed' : 'pointer', border: 'none', background: !fieldValue.trim() || (isShared && !dirty) ? C.canvas : C.indigo, color: !fieldValue.trim() || (isShared && !dirty) ? C.muted : '#fff', fontWeight: 500 }}>
                                              {saveRoutingConfig.isPending ? 'Saving…' : isShared ? 'Update' : 'Enable'}
                                            </button>
                                          </div>
                                        )}
                                        {isShared && (
                                          <div style={{ fontSize: 10, color: C.sub, marginTop: 6 }}>
                                            Every bug synced from this Jira project will be routed to a client based on the value in <code style={{ fontFamily: 'monospace', color: C.text }}>{p.clientCodeField}</code>. Bugs whose code is missing or unknown land in Quarantine.
                                          </div>
                                        )}
                                      </div>
                                    )
                                  })()}
                                  <div style={{ display: 'flex', gap: 8 }}>
                                    <span style={{ fontSize: 10, color: C.sub, alignSelf: 'center' }}>Jump to:</span>
                                    {[{ label: 'CR board', path: '/cr' }, { label: 'Bug triage', path: '/bugs' }, { label: 'Man-days', path: '/mandays' }, { label: 'Radar', path: '/radar' }].map(({ label, path }) => (
                                      <button key={label} onClick={e => { e.stopPropagation(); navigate(path) }}
                                        style={{ fontSize: 11, padding: '4px 10px', borderRadius: 6, cursor: 'pointer', border: `1px solid ${C.border}`, background: C.white, color: C.indigo, fontWeight: 500 }}>
                                        {label} →
                                      </button>
                                    ))}
                                  </div>
                                </div>
                              </td>
                            </tr>
                          )}
                        </>
                      )
                    })}
                  </tbody>
                </table>
              )}
            </Card>
          )}
        </div>
      </div>

      {/* ── Modals ──────────────────────────────────────────────────────────── */}

      {/* Create portfolio */}
      {modal === 'add-portfolio' && (
        <Modal title="New portfolio" onClose={closeModal}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div>
              <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Portfolio name</div>
              <Input value={form.name || ''} onChange={v => setForm({ ...form, name: v })} placeholder="e.g. Collections, Lending" />
            </div>
            <div>
              <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Description (optional)</div>
              <Input value={form.description || ''} onChange={v => setForm({ ...form, description: v })} placeholder="Brief description" />
            </div>
            <div>
              <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>
                Clients <span style={{ fontWeight: 400, color: C.muted }}>(select one or more)</span>
              </div>
              <ClientPicker clients={clients as any[]} selected={form.clientIds || []} onChange={ids => setForm({ ...form, clientIds: ids })} />
            </div>
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 4 }}>
              <Btn onClick={closeModal} style={{ background: C.canvas, color: C.sub, border: `1px solid ${C.border}` }}>Cancel</Btn>
              <Btn onClick={() => createPortfolio.mutate({ name: form.name, description: form.description, clientIds: form.clientIds || [] })}
                disabled={!form.name || createPortfolio.isPending}>
                {createPortfolio.isPending ? 'Creating…' : 'Create portfolio'}
              </Btn>
            </div>
          </div>
        </Modal>
      )}

      {/* Edit portfolio */}
      {modal === 'edit-portfolio' && selectedPortfolio && (
        <Modal title={`Edit · ${selectedPortfolio.name}`} onClose={closeModal}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div>
              <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Portfolio name</div>
              <Input value={form.name || ''} onChange={v => setForm({ ...form, name: v })} placeholder="e.g. Collections, Lending" />
            </div>
            <div>
              <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Description (optional)</div>
              <Input value={form.description || ''} onChange={v => setForm({ ...form, description: v })} placeholder="Brief description" />
            </div>
            <div>
              <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>
                Clients <span style={{ fontWeight: 400, color: C.muted }}>(select one or more)</span>
              </div>
              <ClientPicker clients={clients as any[]} selected={form.clientIds || []} onChange={ids => setForm({ ...form, clientIds: ids })} />
            </div>
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 4 }}>
              <Btn onClick={closeModal} style={{ background: C.canvas, color: C.sub, border: `1px solid ${C.border}` }}>Cancel</Btn>
              <Btn onClick={() => updatePortfolio.mutate({ id: selectedPortfolio.id, body: { name: form.name, description: form.description, clientIds: form.clientIds || [] } })}
                disabled={!form.name || updatePortfolio.isPending}>
                {updatePortfolio.isPending ? 'Saving…' : 'Save changes'}
              </Btn>
            </div>
          </div>
        </Modal>
      )}

      {/* New project (create + assign to this portfolio) */}
      {modal === 'add-project' && selectedPortfolio && (
        <Modal title={`New project in ${selectedPortfolio.name}`} onClose={closeModal}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div>
              <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Project name</div>
              <Input value={form.name || ''} onChange={v => setForm({ ...form, name: v })} placeholder="e.g. CRM Core, Mobile SDK" />
            </div>
            <div>
              <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>
                Client {form.isSharedProdBugs && <span style={{ fontWeight: 400, color: C.muted }}>(optional — routing decides per bug)</span>}
              </div>
              <select value={form.clientId || ''} onChange={e => setForm({ ...form, clientId: e.target.value })} style={inp}>
                <option value="">{form.isSharedProdBugs ? 'No default client' : 'Select client…'}</option>
                {portfolioClients.length > 0
                  ? portfolioClients.map((c: any) => <option key={c.id} value={c.id}>{c.name}</option>)
                  : (clients as any[]).map((c: any) => <option key={c.id} value={c.id}>{c.name}</option>)
                }
              </select>
              {portfolioClients.length === 0 && !form.isSharedProdBugs && (
                <div style={{ fontSize: 11, color: C.amber, marginTop: 4 }}>
                  No clients linked to this portfolio yet — showing all clients. Add clients in Edit.
                </div>
              )}
            </div>
            <div>
              <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Jira project keys <span style={{ fontWeight: 400, color: C.muted }}>(optional, comma-separated)</span></div>
              <Input value={form.jiraProjectKeys || ''} onChange={v => setForm({ ...form, jiraProjectKeys: v })} placeholder="e.g. CRM, NX-CRM" />
            </div>
            <div style={{ padding: '10px 12px', borderRadius: 8, border: `1px solid ${C.border}`, background: C.canvas }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontSize: 13, fontWeight: 500 }}>
                <input type="checkbox" checked={!!form.isSharedProdBugs}
                  onChange={e => setForm({ ...form, isSharedProdBugs: e.target.checked, clientId: e.target.checked ? '' : form.clientId })} />
                Shared prod-bug pool
              </label>
              <div style={{ fontSize: 11, color: C.muted, marginTop: 4, marginLeft: 22 }}>
                Every synced ticket is treated as PROD_BUG and routed to a client by a Jira custom field. Use for cross-client support pools.
              </div>
              {form.isSharedProdBugs && (
                <div style={{ marginTop: 10, marginLeft: 22 }}>
                  <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Client-code custom field</div>
                  <Input value={form.clientCodeField || ''} onChange={v => setForm({ ...form, clientCodeField: v })} placeholder="e.g. customfield_11683" />
                </div>
              )}
            </div>
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 4 }}>
              <Btn onClick={closeModal} style={{ background: C.canvas, color: C.sub, border: `1px solid ${C.border}` }}>Cancel</Btn>
              <Btn onClick={() => createProject.mutate({
                    name: form.name,
                    clientId: form.clientId || null,
                    portfolioId: selectedPortfolio.id,
                    jiraProjectKeys: form.jiraProjectKeys || null,
                    isSharedProdBugs: !!form.isSharedProdBugs,
                    clientCodeField: form.isSharedProdBugs ? (form.clientCodeField || null) : null,
                  })}
                disabled={!form.name || (!form.isSharedProdBugs && !form.clientId) || (form.isSharedProdBugs && !form.clientCodeField) || createProject.isPending}>
                {createProject.isPending ? 'Creating…' : 'Create project'}
              </Btn>
            </div>
          </div>
        </Modal>
      )}

      {/* Assign existing project */}
      {modal === 'assign-project' && selectedPortfolio && (
        <Modal title={`Assign project to ${selectedPortfolio.name}`} onClose={closeModal}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {unassigned.length === 0 ? (
              <div style={{ padding: '20px 0', textAlign: 'center', color: C.muted, fontSize: 13 }}>
                All projects are already assigned to this portfolio
              </div>
            ) : (
              unassigned.map((p: any) => (
                <div key={p.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 12px', borderRadius: 8, border: `1px solid ${C.border}`, background: C.white }}>
                  <div>
                    <div style={{ fontSize: 13, fontWeight: 500, color: C.text }}>{p.name}</div>
                    <div style={{ fontSize: 11, color: C.sub }}>{p.clientName}</div>
                  </div>
                  <Btn onClick={() => assignProject.mutate({ portfolioId: selectedPortfolio.id, projectId: p.id })} style={{ fontSize: 11, padding: '4px 10px' }}>
                    Assign
                  </Btn>
                </div>
              ))
            )}
            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 4 }}>
              <Btn onClick={closeModal} style={{ background: C.canvas, color: C.sub, border: `1px solid ${C.border}` }}>Done</Btn>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}
