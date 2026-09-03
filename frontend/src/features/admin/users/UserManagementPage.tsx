import { ErrorState } from '../../../design/components/PageState'
import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useC } from '../../../design/ThemeContext'
import { Badge } from '../../../design/components/Badge'
import { THead } from '../../../design/components/THead'
import { Tabs } from '../../../design/components/Tabs'
import { Select } from '../../../design/components/Select'
import { Pagination } from '../../../design/components/Pagination'
import { Btn } from '../../../design/components/Btn'
import { api } from '../../../api/client'

const PAGE_SIZE = 10
const roleLevel: Record<string, string> = {
  ADMIN: 'critical', PJM: 'blue', HEAD_PJM: 'purple',
  LEADERSHIP: 'teal', ENGINEERING: 'healthy'
}
const roleLabel: Record<string, string> = {
  ADMIN: 'Admin', PJM: 'PJM', HEAD_PJM: 'Head of PJM',
  LEADERSHIP: 'Leadership', ENGINEERING: 'Engineering'
}

export function UserManagementPage() {
  const C = useC()
  const qc = useQueryClient()
  const [tab, setTab]           = useState('Users')
  const [showInvite, setShowInvite] = useState(false)
  const [page, setPage]         = useState(0)
  const [inviteForm, setInviteForm] = useState({ name: '', email: '', role: 'PM' })
  const [bulkText, setBulkText] = useState('')
  const [bulkResult, setBulkResult] = useState<any>(null)
  const [jiraSyncResult, setJiraSyncResult] = useState<any>(null)
  const [editUser, setEditUser] = useState<any>(null)
  const [newRole, setNewRole] = useState('')
  const [toast, setToast] = useState('')
  const fileRef = useRef<HTMLInputElement>(null)

  const { data: appUsers = [], isLoading, error } = useQuery({
    queryKey: ['app-users'],
    queryFn: () => api.get('/admin/users').then(r => r.data)
  })

  const { data: roleList = [] } = useQuery({
    queryKey: ['admin-roles'],
    queryFn: () => api.get('/admin/roles').then(r => r.data)
  })

  const inviteUser = useMutation({
    mutationFn: (data: any) => api.post('/admin/users/bulk', [data]),
    onSuccess: (r) => {
      qc.invalidateQueries({ queryKey: ['app-users'] })
      const created = (r.data?.results as any[] | undefined)?.find(x => x.status === 'created')
      if (created?.tempPassword) {
        window.alert(`Account created for ${created.email}.\nTemporary password (shown once): ${created.tempPassword}\n\nShare it securely; the user should reset it on first login.`)
      }
      setShowInvite(false); setInviteForm({ name: '', email: '', role: 'PM' })
    }
  })

  const bulkImport = useMutation({
    mutationFn: (rows: any[]) => api.post('/admin/users/bulk', rows),
    onSuccess: (r) => { qc.invalidateQueries({ queryKey: ['app-users'] }); setBulkResult(r.data) }
  })

  const jiraSync = useMutation({
    mutationFn: () => api.post('/admin/users/sync-jira'),
    onSuccess: (r) => setJiraSyncResult(r.data)
  })

  const parseBulkCSV = () => {
    const lines = bulkText.trim().split('\n').filter(Boolean)
    const rows = lines.map(line => {
      const [name, email, role] = line.split(',').map(s => s.trim())
      return { name, email, role: role?.toUpperCase().replace(/\s+/g,'_') || 'PJM' }
    }).filter(r => r.email)
    if (rows.length > 0) bulkImport.mutate(rows)
  }

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = ev => setBulkText(ev.target?.result as string || '')
    reader.readAsText(file)
  }

  const users = appUsers as any[]
  const roles = roleList as any[]
  const totalPages = Math.ceil(users.length / PAGE_SIZE)
  const paged = users.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)

  if (isLoading) return <div style={{ padding: 40, color: C.sub }}>Loading…</div>
  if (error) return <ErrorState error={error} />

  return (
    <div style={{ padding: '22px 24px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 18 }}>
        <div>
          <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>User management</div>
          <div style={{ fontSize: 12, color: C.sub, marginTop: 3 }}>Internal team · role-based access control</div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Btn onClick={() => setShowInvite(true)}>+ Invite user</Btn>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5,1fr)', gap: 10, marginBottom: 20 }}>
        {[
          ['Total', users.length, C.text],
          ['PJMs', users.filter((u: any) => u.role === 'PJM').length, C.indigo],
          ['Head of PJM', users.filter((u: any) => u.role === 'HEAD_PJM').length, C.purple],
          ['Leadership', users.filter((u: any) => u.role === 'LEADERSHIP').length, C.teal],
          ['Admins', users.filter((u: any) => u.role === 'ADMIN').length, C.red],
        ].map(([l, v, c]) => (
          <div key={String(l)} style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 10, padding: '12px 14px', textAlign: 'center' }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: String(c) }}>{v}</div>
            <div style={{ fontSize: 11, color: C.sub, marginTop: 2 }}>{l}</div>
          </div>
        ))}
      </div>

      <Tabs items={['Users', 'Bulk import', 'Jira sync']} active={tab} onChange={t => { setTab(t); setBulkResult(null); setJiraSyncResult(null) }} />

      {/* ── Users table ── */}
      {tab === 'Users' && (
        <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden' }}>
          <table style={{ width: '100%', fontSize: 12 }}>
            <THead cols={['User', 'Email', 'Role', 'Status', '']} />
            <tbody>
              {paged.length === 0 && (
                <tr><td colSpan={5} style={{ padding: '20px', textAlign: 'center', color: C.muted }}>No users yet</td></tr>
              )}
              {paged.map((u: any, i: number) => (
                <tr key={u.id ?? i} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none' }}>
                  <td style={{ padding: '10px 12px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <div style={{ width: 30, height: 30, borderRadius: '50%', background: u.color || C.indigo, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 10, fontWeight: 700, color: '#fff' }}>{u.av}</div>
                      <span style={{ fontSize: 12, fontWeight: 500, color: C.text }}>{u.name}</span>
                    </div>
                  </td>
                  <td style={{ padding: '10px 12px', color: C.sub }}>{u.email}</td>
                  <td style={{ padding: '10px 12px' }}>
                    <Badge level={roleLevel[u.role] || 'neutral'} label={roleLabel[u.role] || u.role} />
                  </td>
                  <td style={{ padding: '10px 12px' }}><Badge level="healthy" label="Active" /></td>
                  <td style={{ padding: '10px 12px' }}>
                    <button onClick={() => { setEditUser(u); setNewRole(u.role) }} style={{ fontSize: 11, padding: '3px 9px', borderRadius: 5, cursor: 'pointer', border: `1px solid ${C.border}`, background: 'transparent', color: C.sub }}>Edit role</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
        </div>
      )}

      {/* ── Bulk import ── */}
      {tab === 'Bulk import' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: '18px 20px' }}>
            <div style={{ fontSize: 13, fontWeight: 600, color: C.text, marginBottom: 6 }}>Bulk import users</div>
            <div style={{ fontSize: 12, color: C.sub, marginBottom: 14 }}>
              Upload a CSV or paste rows below. Format: <code style={{ background: C.canvas, padding: '1px 5px', borderRadius: 4 }}>Full Name, email@domain.com, ROLE</code>
              <br />Valid roles: PM, ENGINEERING, LEADERSHIP, CSM, REVENUE, ADMIN · Each user gets a unique temporary password, shown once in the results below.
            </div>

            <div style={{ display: 'flex', gap: 10, marginBottom: 12 }}>
              <input ref={fileRef} type="file" accept=".csv,.txt" onChange={handleFileUpload} style={{ display: 'none' }} />
              <button onClick={() => fileRef.current?.click()} style={{ fontSize: 12, padding: '7px 14px', borderRadius: 7, border: `1px solid ${C.border}`, background: C.canvas, color: C.sub, cursor: 'pointer' }}>
                Upload CSV file
              </button>
              <span style={{ fontSize: 12, color: C.muted, alignSelf: 'center' }}>or paste below</span>
            </div>

            <textarea
              value={bulkText}
              onChange={e => setBulkText(e.target.value)}
              rows={8}
              placeholder={'Priya Kulkarni, priya@company.com, PJM\nAmit Sharma, amit@company.com, HEAD_PJM\nRajesh Nair, rajesh@company.com, LEADERSHIP'}
              style={{
                width: '100%', fontFamily: 'monospace', fontSize: 12, padding: '10px 12px',
                borderRadius: 8, border: `1px solid ${C.border}`, outline: 'none',
                resize: 'vertical', color: C.text, boxSizing: 'border-box' as const
              }}
            />

            <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
              <Btn onClick={parseBulkCSV} disabled={!bulkText.trim() || bulkImport.isPending}>
                {bulkImport.isPending ? 'Importing…' : 'Import users'}
              </Btn>
              {bulkText && <button onClick={() => setBulkText('')} style={{ fontSize: 12, padding: '6px 12px', borderRadius: 7, border: `1px solid ${C.border}`, background: 'transparent', color: C.sub, cursor: 'pointer' }}>Clear</button>}
            </div>
          </div>

          {bulkResult && (
            <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: '16px 20px' }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: C.text, marginBottom: 10 }}>
                Import complete — {bulkResult.processed} rows processed
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
                {(bulkResult.results as any[]).map((r: any, i: number) => (
                  <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12 }}>
                    <span style={{ color: r.status === 'created' ? C.green : C.muted }}>{r.status === 'created' ? '✓' : '—'}</span>
                    <span style={{ color: C.text }}>{r.email}</span>
                    <Badge level={r.status === 'created' ? 'healthy' : 'neutral'} label={r.status === 'created' ? 'Created' : 'Skipped (exists)'} />
                    {r.tempPassword && (
                      <span style={{ fontSize: 11, color: C.sub }}>temp pw: <code style={{ background: C.canvas, padding: '1px 5px', borderRadius: 4 }}>{r.tempPassword}</code></span>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {/* ── Jira sync ── */}
      {tab === 'Jira sync' && (
        <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: '20px' }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: C.text, marginBottom: 6 }}>Sync users from Jira</div>
          <div style={{ fontSize: 12, color: C.sub, marginBottom: 16 }}>
            Pull users from your Jira instance and create accounts in Orbit. Existing users (matched by email) are skipped.
            Requires <code style={{ background: C.canvas, padding: '1px 5px', borderRadius: 4 }}>JIRA_BASE_URL</code> and <code style={{ background: C.canvas, padding: '1px 5px', borderRadius: 4 }}>JIRA_API_TOKEN</code> env vars.
          </div>

          {jiraSyncResult ? (
            <div style={{ padding: '14px 16px', borderRadius: 8, background: jiraSyncResult.status === 'NOT_CONFIGURED' ? C.amberPale : C.greenPale, border: `1px solid ${jiraSyncResult.status === 'NOT_CONFIGURED' ? C.amber + '40' : C.green + '40'}`, fontSize: 12, color: jiraSyncResult.status === 'NOT_CONFIGURED' ? C.amberDeep : C.greenDeep, marginBottom: 14 }}>
              {jiraSyncResult.message}
            </div>
          ) : null}

          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, marginBottom: 20 }}>
            {[['JIRA_BASE_URL','Your Jira instance URL'], ['JIRA_API_TOKEN','Jira API token'], ['JIRA_EMAIL','Your Jira login email']].map(([k, v]) => (
              <div key={k} style={{ padding: '10px 14px', borderRadius: 8, background: C.canvas, border: `1px solid ${C.border}`, fontSize: 12 }}>
                <div style={{ fontFamily: 'monospace', fontSize: 11, color: C.indigo, marginBottom: 2 }}>{k}</div>
                <div style={{ color: C.sub }}>{v}</div>
              </div>
            ))}
          </div>

          <Btn onClick={() => jiraSync.mutate()} disabled={jiraSync.isPending}>
            {jiraSync.isPending ? 'Syncing…' : 'Sync from Jira'}
          </Btn>
        </div>
      )}

      {toast && (
        <div style={{
          position: 'fixed', bottom: 24, right: 24, zIndex: 9999,
          padding: '10px 18px', borderRadius: 8, background: C.navy,
          color: '#fff', fontSize: 13, fontWeight: 500, boxShadow: '0 8px 24px rgba(0,0,0,0.18)'
        }}>
          {toast}
        </div>
      )}

      {/* Edit role modal */}
      {editUser && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,22,41,.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: C.white, borderRadius: 14, width: 380, boxShadow: '0 20px 60px rgba(0,0,0,.3)' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '16px 20px', borderBottom: `1px solid ${C.border}` }}>
              <div style={{ fontSize: 15, fontWeight: 600, color: C.text }}>Edit role — {editUser.name}</div>
              <button onClick={() => setEditUser(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 18, color: C.muted }}>✕</button>
            </div>
            <div style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div>
                <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>New role</div>
                <select
                  value={newRole}
                  onChange={e => setNewRole(e.target.value)}
                  style={{ fontSize: 12, padding: '7px 10px', borderRadius: 7, border: `1px solid ${C.border}`, outline: 'none', width: '100%' }}
                >
                  {(roles.length > 0
                    ? roles.map((r: any) => ({ value: r.roleName, label: r.displayName }))
                    : ['PJM','HEAD_PJM','LEADERSHIP','ENG_MANAGER','ADMIN'].map(r => ({ value: r, label: r }))
                  ).map((o: any) => <option key={o.value} value={o.value}>{o.label}</option>)}
                </select>
              </div>
              <button
                disabled={!newRole}
                onClick={() => {
                  api.put(`/admin/users/${editUser.id}/role`, { role: newRole })
                    .then(() => { qc.invalidateQueries({ queryKey: ['app-users'] }); setEditUser(null); setToast(`Role updated to ${newRole}`); setTimeout(() => setToast(''), 3000) })
                    .catch((e) => { setEditUser(null); setToast(e?.response?.data?.error || 'Role update failed'); setTimeout(() => setToast(''), 3500) })
                }}
                style={{ padding: '9px', borderRadius: 7, border: 'none', background: C.indigo, color: '#fff', fontSize: 13, fontWeight: 600, cursor: 'pointer' }}
              >
                Save role
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Invite modal */}
      {showInvite && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,22,41,.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: C.white, borderRadius: 14, width: 420, boxShadow: '0 20px 60px rgba(0,0,0,.3)' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '16px 20px', borderBottom: `1px solid ${C.border}` }}>
              <div style={{ fontSize: 15, fontWeight: 600, color: C.text }}>Invite user</div>
              <button onClick={() => setShowInvite(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 18, color: C.muted }}>✕</button>
            </div>
            <div style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div>
                <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Full name</div>
                <input value={inviteForm.name} onChange={e => setInviteForm({ ...inviteForm, name: e.target.value })} placeholder="e.g. Priya Kulkarni"
                  style={{ fontSize: 12, padding: '7px 10px', borderRadius: 7, border: `1px solid ${C.border}`, outline: 'none', width: '100%' }} />
              </div>
              <div>
                <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Work email</div>
                <input value={inviteForm.email} onChange={e => setInviteForm({ ...inviteForm, email: e.target.value })} placeholder="name@orbit.io"
                  style={{ fontSize: 12, padding: '7px 10px', borderRadius: 7, border: `1px solid ${C.border}`, outline: 'none', width: '100%' }} />
              </div>
              <div>
                <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Role</div>
                <select value={inviteForm.role} onChange={e => setInviteForm({ ...inviteForm, role: e.target.value })}
                  style={{ fontSize: 12, padding: '7px 10px', borderRadius: 7, border: `1px solid ${C.border}`, outline: 'none', width: '100%' }}>
                  {roles.length > 0
                    ? roles.map((r: any) => <option key={r.roleName} value={r.roleName}>{r.displayName}</option>)
                    : ['PM','ENGINEERING','CSM','REVENUE','LEADERSHIP','ADMIN'].map(r => <option key={r} value={r}>{r}</option>)
                  }
                </select>
              </div>
              <div style={{ fontSize: 11, color: C.sub }}>A unique temporary password is generated and shown once on creation (user should reset on first login)</div>
              <button
                onClick={() => inviteUser.mutate({ name: inviteForm.name, email: inviteForm.email, role: inviteForm.role })}
                disabled={!inviteForm.email || inviteUser.isPending}
                style={{ padding: '9px', borderRadius: 7, border: 'none', background: C.indigo, color: '#fff', fontSize: 13, fontWeight: 600, cursor: 'pointer' }}
              >
                {inviteUser.isPending ? 'Creating…' : 'Create account'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
