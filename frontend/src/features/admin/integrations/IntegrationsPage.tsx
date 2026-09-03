import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useC } from '../../../design/ThemeContext'
import { Tabs } from '../../../design/components/Tabs'
import { api } from '../../../api/client'
import { JiraIntegration } from './JiraIntegration'
import { HrmsIntegration } from './HrmsIntegration'

export function IntegrationsPage() {
  const C = useC()
  const qc = useQueryClient()
  const [tab, setTab] = useState('Jira')
  const [toast, setToast] = useState('')
  const [toastOk, setToastOk] = useState(true)
  const showToast = (msg: string, ok = true) => { setToast(msg); setToastOk(ok); setTimeout(() => setToast(''), 3500) }

  // ── Slack config ───────────────────────────────────────────────────────────
  const { data: slackCfg, isLoading: loadingSlack } = useQuery({
    queryKey: ['slack-config'],
    queryFn: () => api.get('/admin/integrations/slack').then(r => r.data).catch(() => null)
  })

  const { data: channels = [] } = useQuery({
    queryKey: ['slack-channels'],
    queryFn: () => api.get('/admin/integrations/slack/channels').then(r => r.data)
  })

  const { data: projects = [] } = useQuery({
    queryKey: ['projects-list'],
    queryFn: () => api.get('/admin/projects').then(r => r.data)
  })

  const [workspaceName,  setWorkspaceName]  = useState('')
  const [botToken,       setBotToken]       = useState('')
  const [signingSecret,  setSigningSecret]  = useState('')
  const [defaultChannel, setDefaultChannel] = useState('')
  const [savingSlack,    setSavingSlack]    = useState(false)
  const [testing,        setTesting]        = useState(false)
  const [slackEditing,   setSlackEditing]   = useState(false)

  // Pre-populate form when opening edit mode
  const openEdit = () => {
    if (slackCfg?.configured) {
      setWorkspaceName(slackCfg.workspaceName || '')
      setDefaultChannel(slackCfg.defaultChannel || '')
    }
    setBotToken('')
    setSigningSecret('')
    setSlackEditing(true)
  }

  const [editChannels, setEditChannels] = useState<Record<number, string>>({})

  const saveSlack = async () => {
    setSavingSlack(true)
    try {
      await api.put('/admin/integrations/slack', { workspaceName, botToken, signingSecret, defaultChannel })
      qc.invalidateQueries({ queryKey: ['slack-config'] })
      setSlackEditing(false); setBotToken('')
      showToast('Slack config saved')
    } catch { showToast('Save failed', false) }
    finally { setSavingSlack(false) }
  }

  const testSlack = async () => {
    setTesting(true)
    try {
      const res = await api.post('/admin/integrations/slack/test')
      showToast(res.data.ok ? '✓ Test message sent to Slack' : `✗ ${res.data.error}`, res.data.ok)
    } catch { showToast('Test failed', false) }
    finally { setTesting(false) }
  }

  const saveChannel = async (projectId: number) => {
    const channelId = editChannels[projectId]
    if (!channelId?.trim()) return
    try {
      await api.put(`/admin/integrations/slack/channels/${projectId}`, { channelId: channelId.trim(), channelName: channelId.trim() })
      qc.invalidateQueries({ queryKey: ['slack-channels'] })
      setEditChannels(prev => { const n = {...prev}; delete n[projectId]; return n })
      showToast('Channel mapping saved')
    } catch { showToast('Save failed', false) }
  }

  const removeChannel = async (projectId: number) => {
    await api.delete(`/admin/integrations/slack/channels/${projectId}`)
    qc.invalidateQueries({ queryKey: ['slack-channels'] })
    showToast('Channel mapping removed')
  }

  const channelMap = Object.fromEntries((channels as any[]).map((c: any) => [c.projectId, c]))
  const projectList = projects as any[]

  return (
    <div style={{ padding: '22px 24px' }}>
      {toast && (
        <div style={{ position: 'fixed', bottom: 24, right: 24, zIndex: 9999, padding: '10px 18px', borderRadius: 8, background: toastOk ? C.greenDeep : C.red, color: '#fff', fontSize: 13, fontWeight: 500, boxShadow: '0 8px 24px rgba(0,0,0,.18)' }}>
          {toast}
        </div>
      )}

      <div style={{ marginBottom: 20 }}>
        <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>Integrations</div>
        <div style={{ fontSize: 12, color: C.sub, marginTop: 3 }}>Jira · HR System · Slack</div>
      </div>

      <Tabs items={['Jira', 'HR System', 'Slack']} active={tab} onChange={setTab} />

      {tab === 'Jira' && <JiraIntegration />}
      {tab === 'HR System' && <HrmsIntegration />}
      {tab === 'Slack' && (
        <div>
          {/* ── Slack ──────────────────────────────────────────────────────────── */}
          <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, marginBottom: 20 }}>
            {/* Header */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '14px 18px', borderBottom: `1px solid ${C.border}` }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <span style={{ fontSize: 20 }}>💬</span>
                <div>
                  <div style={{ fontSize: 14, fontWeight: 600, color: C.text }}>Slack</div>
                  <div style={{ fontSize: 11, color: C.sub }}>Post agent messages to project channels</div>
                </div>
              </div>
              <div style={{ display: 'flex', gap: 8 }}>
                {slackCfg?.configured && !slackEditing && (
                  <>
                    <button onClick={testSlack} disabled={testing}
                      style={{ fontSize: 12, padding: '5px 12px', borderRadius: 6, border: `1px solid ${C.border}`, background: 'transparent', color: C.sub, cursor: 'pointer' }}>
                      {testing ? '⟳ Testing…' : 'Send test'}
                    </button>
                    <button onClick={openEdit}
                      style={{ fontSize: 12, padding: '5px 12px', borderRadius: 6, border: `1px solid ${C.border}`, background: 'transparent', color: C.indigo, cursor: 'pointer' }}>
                      Edit
                    </button>
                  </>
                )}
              </div>
            </div>

            <div style={{ padding: '16px 18px' }}>
              {loadingSlack ? (
                <div style={{ fontSize: 12, color: C.sub }}>Loading…</div>
              ) : slackCfg?.configured && !slackEditing ? (
                /* Config summary */
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 16 }}>
                  <Stat label="Workspace" value={slackCfg.workspaceName || '—'} />
                  <Stat label="Bot token" value={slackCfg.botToken || '—'} />
                  <Stat label="Default channel" value={slackCfg.defaultChannel || '—'} />
                </div>
              ) : (
                /* Config form */
                <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                    <FormField label="Workspace name">
                      <input value={workspaceName} onChange={e => setWorkspaceName(e.target.value)}
                        placeholder="e.g. acme-workspace" style={inp(C)} />
                    </FormField>
                    <FormField label="Default channel">
                      <input value={defaultChannel} onChange={e => setDefaultChannel(e.target.value)}
                        placeholder="e.g. C0123ABCD or #general" style={inp(C)} />
                    </FormField>
                  </div>
                  <FormField label="Bot token (xoxb-…)">
                    <input type="password" value={botToken} onChange={e => setBotToken(e.target.value)}
                      placeholder={slackCfg ? 'Enter new token to update' : 'xoxb-your-token-here'} style={inp(C)} />
                  </FormField>
                  <FormField label="Signing secret (optional — for webhook verification)">
                    <input type="password" value={signingSecret} onChange={e => setSigningSecret(e.target.value)}
                      placeholder="Your Slack signing secret" style={inp(C)} />
                  </FormField>
                  <div style={{ display: 'flex', gap: 8 }}>
                    <button onClick={saveSlack} disabled={savingSlack || (!botToken.trim() && !slackCfg)}
                      style={{ fontSize: 12, padding: '7px 16px', borderRadius: 7, border: 'none', background: C.indigo, color: '#fff', cursor: 'pointer', fontWeight: 600 }}>
                      {savingSlack ? 'Saving…' : 'Save'}
                    </button>
                    {slackCfg?.configured && (
                      <button onClick={() => setSlackEditing(false)}
                        style={{ fontSize: 12, padding: '7px 12px', borderRadius: 7, border: `1px solid ${C.border}`, background: 'transparent', color: C.sub, cursor: 'pointer' }}>
                        Cancel
                      </button>
                    )}
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* ── Per-project channel mapping ────────────────────────────────────── */}
          <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, marginBottom: 20 }}>
            <div style={{ padding: '14px 18px', borderBottom: `1px solid ${C.border}` }}>
              <div style={{ fontSize: 14, fontWeight: 600, color: C.text }}>Project → channel mapping</div>
              <div style={{ fontSize: 11, color: C.sub, marginTop: 2 }}>Agents use the project channel when posting to Slack</div>
            </div>
            <table style={{ width: '100%', fontSize: 12, borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ background: C.canvas }}>
                  {['Project', 'Slack channel ID', ''].map(h => (
                    <th key={h} style={{ padding: '8px 14px', textAlign: 'left', fontSize: 10, fontWeight: 600, color: C.sub, letterSpacing: 0.4, borderBottom: `1px solid ${C.border}` }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {projectList.length === 0 && (
                  <tr><td colSpan={3} style={{ padding: 20, textAlign: 'center', color: C.muted }}>No projects configured</td></tr>
                )}
                {projectList.map((p: any, i: number) => {
                  const mapped = channelMap[p.id]
                  const editing = editChannels[p.id] !== undefined
                  return (
                    <tr key={p.id} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none' }}>
                      <td style={{ padding: '10px 14px', fontWeight: 500, color: C.text }}>
                        {p.name}
                        {p.clientName && <span style={{ fontSize: 11, color: C.sub, marginLeft: 6 }}>{p.clientName}</span>}
                      </td>
                      <td style={{ padding: '10px 14px' }}>
                        {editing ? (
                          <input
                            autoFocus
                            value={editChannels[p.id]}
                            onChange={e => setEditChannels(prev => ({ ...prev, [p.id]: e.target.value }))}
                            placeholder="C0123ABCD"
                            style={{ ...inp(C), width: 200 }}
                          />
                        ) : mapped ? (
                          <span style={{ fontFamily: 'monospace', fontSize: 11, color: C.indigo }}>{mapped.channelId}</span>
                        ) : (
                          <span style={{ fontSize: 11, color: C.muted }}>Not configured</span>
                        )}
                      </td>
                      <td style={{ padding: '10px 14px' }}>
                        <div style={{ display: 'flex', gap: 6 }}>
                          {editing ? (
                            <>
                              <button onClick={() => saveChannel(p.id)}
                                style={{ fontSize: 11, padding: '3px 9px', borderRadius: 5, border: 'none', background: C.indigo, color: '#fff', cursor: 'pointer' }}>Save</button>
                              <button onClick={() => setEditChannels(prev => { const n = {...prev}; delete n[p.id]; return n })}
                                style={{ fontSize: 11, padding: '3px 9px', borderRadius: 5, border: `1px solid ${C.border}`, background: 'transparent', color: C.sub, cursor: 'pointer' }}>Cancel</button>
                            </>
                          ) : (
                            <>
                              <button onClick={() => setEditChannels(prev => ({ ...prev, [p.id]: mapped?.channelId ?? '' }))}
                                style={{ fontSize: 11, padding: '3px 9px', borderRadius: 5, border: `1px solid ${C.border}`, background: 'transparent', color: C.sub, cursor: 'pointer' }}>
                                {mapped ? 'Edit' : '+ Set'}
                              </button>
                              {mapped && (
                                <button onClick={() => removeChannel(p.id)}
                                  style={{ fontSize: 11, padding: '3px 9px', borderRadius: 5, border: `1px solid ${C.border}`, background: 'transparent', color: C.red, cursor: 'pointer' }}>✕</button>
                              )}
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          {/* ── Email (stub) ──────────────────────────────────────────────────── */}
          <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, opacity: 0.6 }}>
            <div style={{ padding: '14px 18px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <span style={{ fontSize: 20 }}>✉️</span>
                <div>
                  <div style={{ fontSize: 14, fontWeight: 600, color: C.text }}>Email (SMTP)</div>
                  <div style={{ fontSize: 11, color: C.sub }}>Coming soon — configure SMTP for email delivery</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  const C = useC()
  return (
    <div>
      <div style={{ fontSize: 10, color: C.muted, fontWeight: 600, letterSpacing: 0.4, textTransform: 'uppercase', marginBottom: 4 }}>{label}</div>
      <div style={{ fontSize: 13, fontWeight: 500, color: C.text, fontFamily: value.startsWith('xox') || value.startsWith('C0') ? 'monospace' : 'inherit' }}>{value}</div>
    </div>
  )
}

function FormField({ label, children }: { label: string; children: React.ReactNode }) {
  const C = useC()
  return (
    <div>
      <div style={{ fontSize: 11, fontWeight: 500, color: C.sub, marginBottom: 5 }}>{label}</div>
      {children}
    </div>
  )
}

function inp(C: any) {
  return { fontSize: 12, padding: '7px 10px', borderRadius: 7, border: `1px solid ${C.border}`, outline: 'none', width: '100%', boxSizing: 'border-box' as const }
}
