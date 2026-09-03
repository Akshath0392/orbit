import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useC } from '../../../design/ThemeContext'
import { Badge } from '../../../design/components/Badge'
import { BurnBar } from '../../../design/components/BurnBar'
import { Modal } from '../../../design/components/Modal'
import { Btn } from '../../../design/components/Btn'
import { Input } from '../../../design/components/Input'
import { api } from '../../../api/client'

const EMPTY_FORM = {
  name: '', code: '', contactName: '', healthGreenThreshold: 80, healthAmberThreshold: 60,
  csatLaunch: '', csatBau: '', engagementScore: '',
}

export function ClientManagementPage() {
  const C = useC()
  const qc = useQueryClient()
  const [modal, setModal] = useState<'add' | 'edit' | null>(null)
  const [editing, setEditing] = useState<any>(null)
  const [form, setForm] = useState<any>(EMPTY_FORM)

  const { data: clientList = [], isLoading } = useQuery({
    queryKey: ['admin-clients'],
    queryFn: () => api.get('/clients').then(r => r.data)
  })

  const save = useMutation({
    mutationFn: (data: any) => editing
      ? api.put(`/admin/clients/${editing.id}`, data)
      : api.post('/admin/clients', data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-clients'] })
      qc.invalidateQueries({ queryKey: ['clients-list'] })
      setModal(null); setEditing(null); setForm(EMPTY_FORM)
    }
  })

  const deactivate = useMutation({
    mutationFn: (id: number) => api.delete(`/admin/clients/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-clients'] })
  })

  const openEdit = (c: any) => {
    setEditing(c)
    setForm({
      name: c.name, code: c.code, contactName: c.contact || '',
      healthGreenThreshold: c.healthGreenThreshold ?? 80,
      healthAmberThreshold: c.healthAmberThreshold ?? 60,
      csatLaunch: c.csatLaunch ?? '', csatBau: c.csatBau ?? '',
      engagementScore: c.engagementScore ?? '',
    })
    setModal('edit')
  }

  // Blank CSAT/engagement inputs save as null (widget shows "—", never 0)
  const toPayload = (f: any) => ({
    ...f,
    csatLaunch: f.csatLaunch === '' ? null : Number(f.csatLaunch),
    csatBau: f.csatBau === '' ? null : Number(f.csatBau),
    engagementScore: f.engagementScore === '' ? null : Number(f.engagementScore),
  })

  const clients = clientList as any[]

  if (isLoading) return <div style={{ padding: 40, color: C.sub }}>Loading…</div>

  return (
    <div style={{ padding: '22px 24px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 18 }}>
        <div>
          <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>Client management</div>
          <div style={{ fontSize: 12, color: C.sub, marginTop: 3 }}>Configure clients, health thresholds, and account contacts</div>
        </div>
        <Btn onClick={() => { setEditing(null); setForm(EMPTY_FORM); setModal('add') }}>+ Add client</Btn>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 10, marginBottom: 20 }}>
        {[
          ['Total clients', clients.length, C.text],
          ['Critical health', clients.filter((c: any) => c.health < (c.healthAmberThreshold ?? 60)).length, C.red],
          ['Watch', clients.filter((c: any) => c.health >= (c.healthAmberThreshold ?? 60) && c.health < (c.healthGreenThreshold ?? 80)).length, C.amber],
          ['Healthy', clients.filter((c: any) => c.health >= (c.healthGreenThreshold ?? 80)).length, C.green],
        ].map(([l, v, col]) => (
          <div key={String(l)} style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 10, padding: '12px 14px', textAlign: 'center' }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: String(col) }}>{v}</div>
            <div style={{ fontSize: 11, color: C.sub, marginTop: 2 }}>{l}</div>
          </div>
        ))}
      </div>

      <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, overflow: 'hidden' }}>
        <table style={{ width: '100%', fontSize: 12, borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ background: C.canvas }}>
              {['Client', 'Code', 'Health', 'Budget burn', 'Open CRs', 'Open bugs', 'Contact', 'Thresholds', ''].map(h => (
                <th key={h} style={{ padding: '8px 12px', textAlign: 'left', fontSize: 10, fontWeight: 600, color: C.sub, letterSpacing: 0.4, borderBottom: `1px solid ${C.border}` }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {clients.length === 0 && (
              <tr><td colSpan={9} style={{ padding: '30px', textAlign: 'center', color: C.muted }}>No clients yet. Add your first client.</td></tr>
            )}
            {clients.map((c: any, i: number) => {
              const level = c.health >= (c.healthGreenThreshold ?? 80) ? 'healthy' : c.health >= (c.healthAmberThreshold ?? 60) ? 'watch' : 'critical'
              return (
                <tr key={c.id} style={{ borderTop: i > 0 ? `1px solid ${C.border}` : 'none', background: C.white }}>
                  <td style={{ padding: '10px 12px', fontWeight: 600, color: C.text }}>{c.name}</td>
                  <td style={{ padding: '10px 12px', fontFamily: 'monospace', fontSize: 11, color: C.indigo }}>{c.code}</td>
                  <td style={{ padding: '10px 12px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <div style={{ width: 60 }}><BurnBar pct={c.health} h={4} /></div>
                      <Badge level={level} label={`${c.health}%`} />
                    </div>
                  </td>
                  <td style={{ padding: '10px 12px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <div style={{ width: 50 }}><BurnBar pct={c.burn} h={4} /></div>
                      <span style={{ fontSize: 11, color: C.sub }}>{c.burn}%</span>
                    </div>
                  </td>
                  <td style={{ padding: '10px 12px', color: C.sub }}>{c.crs}</td>
                  <td style={{ padding: '10px 12px', color: c.bugs > 0 ? C.red : C.sub }}>{c.bugs}</td>
                  <td style={{ padding: '10px 12px', color: C.sub }}>{c.contact || '—'}</td>
                  <td style={{ padding: '10px 12px', fontSize: 11, color: C.sub }}>
                    🟢 ≥{c.healthGreenThreshold ?? 80} · 🟡 ≥{c.healthAmberThreshold ?? 60}
                  </td>
                  <td style={{ padding: '10px 12px' }}>
                    <div style={{ display: 'flex', gap: 5 }}>
                      <button onClick={() => openEdit(c)} style={{ fontSize: 11, padding: '3px 9px', borderRadius: 5, cursor: 'pointer', border: `1px solid ${C.border}`, background: 'transparent', color: C.indigo, fontWeight: 500 }}>Edit</button>
                      <button onClick={() => { if (confirm(`Deactivate ${c.name}?`)) deactivate.mutate(c.id) }} style={{ fontSize: 11, padding: '3px 9px', borderRadius: 5, cursor: 'pointer', border: `1px solid ${C.border}`, background: 'transparent', color: C.sub }}>Deactivate</button>
                    </div>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {(modal === 'add' || modal === 'edit') && (
        <Modal title={modal === 'edit' ? `Edit ${editing?.name}` : 'Add client'} onClose={() => { setModal(null); setEditing(null) }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: 10 }}>
              <div>
                <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Client name</div>
                <Input value={form.name} onChange={v => setForm({ ...form, name: v })} placeholder="e.g. Nexus Corp" />
              </div>
              <div>
                <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Code</div>
                <Input value={form.code} onChange={v => setForm({ ...form, code: v })} placeholder="ACME" />
              </div>
            </div>
            <div>
              <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Account contact</div>
              <Input value={form.contactName} onChange={v => setForm({ ...form, contactName: v })} placeholder="Contact name" />
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
              <div>
                <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Healthy threshold (≥)</div>
                <input
                  type="number" value={form.healthGreenThreshold} min={0} max={100}
                  onChange={e => setForm({ ...form, healthGreenThreshold: parseInt(e.target.value) })}
                  style={{ fontSize: 12, padding: '7px 10px', borderRadius: 7, border: `1px solid ${C.border}`, width: '100%', outline: 'none' }}
                />
              </div>
              <div>
                <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>Watch threshold (≥)</div>
                <input
                  type="number" value={form.healthAmberThreshold} min={0} max={100}
                  onChange={e => setForm({ ...form, healthAmberThreshold: parseInt(e.target.value) })}
                  style={{ fontSize: 12, padding: '7px 10px', borderRadius: 7, border: `1px solid ${C.border}`, width: '100%', outline: 'none' }}
                />
              </div>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 10 }}>
              {([
                ['CSAT — Launch (1–10)', 'csatLaunch', 1, 10, 0.1],
                ['CSAT — BAU (1–10)', 'csatBau', 1, 10, 0.1],
                ['Engagement (0–100)', 'engagementScore', 0, 100, 1],
              ] as const).map(([label, key, min, max, step]) => (
                <div key={key}>
                  <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 5 }}>{label}</div>
                  <input
                    type="number" value={form[key]} min={min} max={max} step={step} placeholder="—"
                    onChange={e => setForm({ ...form, [key]: e.target.value })}
                    style={{ fontSize: 12, padding: '7px 10px', borderRadius: 7, border: `1px solid ${C.border}`, width: '100%', outline: 'none' }}
                  />
                </div>
              ))}
            </div>
            <div style={{ fontSize: 11, color: C.muted }}>
              CSAT is the interim admin-entered source (widget-parity plan F1) — leave blank for "—".
              A survey feed later replaces these fields.
            </div>
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 4 }}>
              <Btn onClick={() => { setModal(null); setEditing(null) }} style={{ background: C.canvas, color: C.sub, border: `1px solid ${C.border}` }}>Cancel</Btn>
              <Btn onClick={() => save.mutate(toPayload(form))} disabled={!form.name || !form.code || save.isPending}>
                {save.isPending ? 'Saving…' : modal === 'edit' ? 'Save changes' : 'Create client'}
              </Btn>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}
