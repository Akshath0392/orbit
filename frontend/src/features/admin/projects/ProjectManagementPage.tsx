import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useC } from '../../../design/ThemeContext'
import { Modal } from '../../../design/components/Modal'
import { Btn } from '../../../design/components/Btn'
import { Input } from '../../../design/components/Input'
import { api } from '../../../api/client'

const EMPTY_FORM = { name: '', clientId: '', portfolioId: '', jiraProjectKeys: '', isSharedProdBugs: false, clientCodeField: '' }

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>{label}</div>
      {children}
    </div>
  )
}

function SelectInput({ value, onChange, children }: { value: string; onChange: (v: string) => void; children: React.ReactNode }) {
  const C = useC()
  return (
    <select
      value={value}
      onChange={e => onChange(e.target.value)}
      style={{
        fontSize: 12, padding: '7px 10px', borderRadius: 7,
        border: `1px solid ${C.border}`, background: C.white,
        color: value ? C.text : C.muted, width: '100%', outline: 'none'
      }}
    >
      {children}
    </select>
  )
}

export function ProjectManagementPage() {
  const C = useC()
  const qc = useQueryClient()
  const [modal, setModal] = useState<'add' | 'edit' | null>(null)
  const [editing, setEditing] = useState<any>(null)
  const [form, setForm] = useState<any>(EMPTY_FORM)

  const { data: projects = [], isLoading } = useQuery({
    queryKey: ['admin-projects'],
    queryFn: () => api.get('/projects').then(r => r.data)
  })

  const { data: clients = [] } = useQuery({
    queryKey: ['clients-list'],
    queryFn: () => api.get('/clients').then(r => r.data)
  })

  const { data: portfolios = [] } = useQuery({
    queryKey: ['portfolios'],
    queryFn: () => api.get('/portfolios').then(r => r.data)
  })

  // Filter portfolios to match selected client
  const filteredPortfolios = (portfolios as any[]).filter(
    (pf: any) => !form.clientId || String(pf.clientId) === String(form.clientId)
  )

  const save = useMutation({
    mutationFn: (data: any) => editing
      ? api.put(`/admin/projects/${editing.id}`, data)
      : api.post('/admin/projects', data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-projects'] })
      qc.invalidateQueries({ queryKey: ['projects'] })
      setModal(null); setEditing(null); setForm(EMPTY_FORM)
    }
  })

  const deactivate = useMutation({
    mutationFn: (id: number) => api.delete(`/admin/projects/${id}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-projects'] })
      qc.invalidateQueries({ queryKey: ['projects'] })
    }
  })

  const openAdd = () => {
    setEditing(null); setForm(EMPTY_FORM); setModal('add')
  }

  const openEdit = (p: any) => {
    setEditing(p)
    setForm({
      name:             p.name,
      clientId:         String(p.clientId || ''),
      portfolioId:      String(p.portfolioId || ''),
      jiraProjectKeys:  p.jiraProjectKeys || '',
      isSharedProdBugs: !!p.isSharedProdBugs,
      clientCodeField:  p.clientCodeField || '',
    })
    setModal('edit')
  }

  const submit = () => save.mutate({
    name:             form.name,
    clientId:         form.clientId   || undefined,
    portfolioId:      form.portfolioId || undefined,
    jiraProjectKeys:  form.jiraProjectKeys || undefined,
    isSharedProdBugs: !!form.isSharedProdBugs,
    clientCodeField:  form.isSharedProdBugs ? (form.clientCodeField || null) : null,
  })

  const list = projects as any[]

  if (isLoading) return <div style={{ padding: 40, color: C.sub }}>Loading…</div>

  return (
    <div style={{ padding: '22px 24px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 18 }}>
        <div>
          <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>Projects</div>
          <div style={{ fontSize: 12, color: C.sub, marginTop: 3 }}>
            Create and manage projects · assign Jira keys · link to clients and portfolios
          </div>
        </div>
        <Btn onClick={openAdd}>+ New project</Btn>
      </div>

      <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden' }}>
        <table style={{ width: '100%', fontSize: 12, borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ background: C.canvas }}>
              {['Project', 'Client', 'Portfolio', 'Jira keys', ''].map(h => (
                <th key={h} style={{
                  padding: '8px 12px', textAlign: 'left', fontSize: 10,
                  fontWeight: 600, color: C.sub, letterSpacing: 0.4,
                  borderBottom: `1px solid ${C.border}`
                }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {list.length === 0 && (
              <tr>
                <td colSpan={5} style={{ padding: '36px', textAlign: 'center', color: C.muted }}>
                  No projects yet. Click <strong>+ New project</strong> to create one.
                </td>
              </tr>
            )}
            {list.map((p: any, i: number) => (
              <tr key={p.id} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none' }}>
                <td style={{ padding: '10px 12px', fontWeight: 600, color: C.text }}>{p.name}</td>
                <td style={{ padding: '10px 12px', color: C.sub }}>{p.clientName || '—'}</td>
                <td style={{ padding: '10px 12px', color: C.sub }}>{p.portfolioName || <span style={{ color: C.muted }}>—</span>}</td>
                <td style={{ padding: '10px 12px', fontFamily: 'monospace', fontSize: 11, color: p.jiraProjectKeys ? C.indigo : C.muted }}>
                  {p.jiraProjectKeys || 'not set'}
                </td>
                <td style={{ padding: '10px 12px' }}>
                  <div style={{ display: 'flex', gap: 5 }}>
                    <button
                      onClick={() => openEdit(p)}
                      style={{ fontSize: 11, padding: '3px 9px', borderRadius: 5, cursor: 'pointer', border: `1px solid ${C.border}`, background: 'transparent', color: C.indigo, fontWeight: 500 }}
                    >
                      Edit
                    </button>
                    <button
                      onClick={() => { if (confirm(`Deactivate ${p.name}?`)) deactivate.mutate(p.id) }}
                      style={{ fontSize: 11, padding: '3px 9px', borderRadius: 5, cursor: 'pointer', border: `1px solid ${C.border}`, background: 'transparent', color: C.sub }}
                    >
                      Deactivate
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {(modal === 'add' || modal === 'edit') && (
        <Modal
          title={modal === 'edit' ? `Edit ${editing?.name}` : 'New project'}
          onClose={() => { setModal(null); setEditing(null) }}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <Field label="Project name *">
              <Input
                value={form.name}
                onChange={v => setForm({ ...form, name: v })}
                placeholder="e.g. CRM Core, Collections 2.0"
              />
            </Field>

            <Field label={`Client ${form.isSharedProdBugs ? '(optional — routing decides per bug)' : '*'}`}>
              <SelectInput
                value={form.clientId}
                onChange={v => setForm({ ...form, clientId: v, portfolioId: '' })}
              >
                <option value="">{form.isSharedProdBugs ? 'No default client' : 'Select client…'}</option>
                {(clients as any[]).map((c: any) => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </SelectInput>
            </Field>

            <div style={{ background: C.canvas, border: `1px solid ${C.border}`, borderRadius: 8, padding: '10px 12px' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12, fontWeight: 500, cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={!!form.isSharedProdBugs}
                  onChange={e => setForm({ ...form, isSharedProdBugs: e.target.checked, clientId: e.target.checked ? '' : form.clientId })}
                />
                Shared prod-bug pool
              </label>
              <div style={{ fontSize: 11, color: C.muted, marginTop: 4, marginLeft: 22 }}>
                Bugs are routed to a client per-ticket via a Jira custom field. Client above becomes optional.
              </div>
              {form.isSharedProdBugs && (
                <div style={{ marginTop: 10, marginLeft: 22 }}>
                  <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Client-code custom field *</div>
                  <Input
                    value={form.clientCodeField || ''}
                    onChange={v => setForm({ ...form, clientCodeField: v })}
                    placeholder="e.g. customfield_11683"
                  />
                </div>
              )}
            </div>

            <Field label="Portfolio (optional)">
              <SelectInput
                value={form.portfolioId}
                onChange={v => setForm({ ...form, portfolioId: v })}
              >
                <option value="">No portfolio</option>
                {filteredPortfolios.map((pf: any) => (
                  <option key={pf.id} value={pf.id}>{pf.name}</option>
                ))}
              </SelectInput>
              {form.clientId && filteredPortfolios.length === 0 && (
                <div style={{ fontSize: 11, color: C.muted, marginTop: 4 }}>
                  No portfolios for this client — create one in Portfolio setup first.
                </div>
              )}
            </Field>

            <Field label="Jira project keys (comma-separated)">
              <Input
                value={form.jiraProjectKeys}
                onChange={v => setForm({ ...form, jiraProjectKeys: v })}
                placeholder="e.g. NX, CRM"
              />
            </Field>

            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 4 }}>
              <Btn
                onClick={() => { setModal(null); setEditing(null) }}
                style={{ background: C.canvas, color: C.sub, border: `1px solid ${C.border}` }}
              >
                Cancel
              </Btn>
              <Btn
                onClick={submit}
                disabled={
                  !form.name
                  || (!form.isSharedProdBugs && !form.clientId)
                  || (form.isSharedProdBugs && !form.clientCodeField)
                  || save.isPending
                }
              >
                {save.isPending ? 'Saving…' : modal === 'edit' ? 'Save changes' : 'Create project'}
              </Btn>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}
