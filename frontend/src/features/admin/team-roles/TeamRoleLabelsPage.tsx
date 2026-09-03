import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useC } from '../../../design/ThemeContext'
import { ErrorState } from '../../../design/components/PageState'
import { api } from '../../../api/client'
import { useTeamRoleLabels } from '../../accounts/useTeamRoleLabels'

// Display labels for the fixed project_team internal-role slots (V93).
// Renaming here re-labels the Internal Team table on every account page.
export function TeamRoleLabelsPage() {
  const C = useC()
  const qc = useQueryClient()
  const { data: labels = [], isLoading, error } = useTeamRoleLabels()
  const [draft, setDraft] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState(false)

  if (isLoading) return <div style={{ padding: 40, color: C.sub }}>Loading…</div>
  if (error) return <ErrorState error={error} />

  const changed = labels.filter(l => draft[l.roleKey] != null && draft[l.roleKey].trim() !== '' && draft[l.roleKey].trim() !== l.label)
  const save = async () => {
    setSaving(true)
    try {
      for (const l of changed) {
        await api.put(`/admin/team-role-labels/${l.roleKey}`, { label: draft[l.roleKey].trim() })
      }
      setDraft({})
      qc.invalidateQueries({ queryKey: ['team-role-labels'] })
    } finally { setSaving(false) }
  }

  return (
    <div style={{ padding: '22px 24px' }}>
      <div style={{ marginBottom: 18 }}>
        <div style={{ fontSize: 20, fontWeight: 700, color: C.text, letterSpacing: -0.4 }}>Team role labels</div>
        <div style={{ fontSize: 12, color: C.sub, marginTop: 3 }}>
          Rename the internal-team roles shown on account pages — the underlying role slots stay the same.
        </div>
      </div>
      <div style={{ background: C.white, border: `1px solid ${C.border}`, borderRadius: 12, padding: 18, maxWidth: 520 }}>
        {labels.map((l, i) => (
          <div key={l.roleKey} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '9px 0', borderTop: i > 0 ? `1px solid ${C.border}` : 'none' }}>
            <span style={{ flex: 1, fontFamily: 'monospace', fontSize: 11, color: C.muted }}>{l.roleKey}</span>
            <input value={draft[l.roleKey] ?? l.label}
              onChange={e => setDraft(d => ({ ...d, [l.roleKey]: e.target.value }))}
              style={{ flex: 1.4, fontSize: 12, padding: '6px 10px', borderRadius: 6, border: `1px solid ${C.border}`, outline: 'none', color: C.text, background: C.white }} />
          </div>
        ))}
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 14 }}>
          <button onClick={save} disabled={saving || changed.length === 0}
            style={{ fontSize: 12, padding: '7px 16px', borderRadius: 7, border: 'none', background: C.indigo, color: '#fff', fontWeight: 600, cursor: 'pointer', opacity: changed.length === 0 ? 0.5 : 1 }}>
            {saving ? 'Saving…' : `Save${changed.length ? ` (${changed.length})` : ''}`}
          </button>
        </div>
      </div>
    </div>
  )
}
