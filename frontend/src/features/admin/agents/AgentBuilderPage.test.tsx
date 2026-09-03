import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AgentBuilderPage } from './AgentBuilderPage'
import { useStore } from '../../../app/store'

vi.mock('../../../api/client', () => ({
  api: {
    get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  },
}))

import { api } from '../../../api/client'

// ── Shared fixtures ────────────────────────────────────────────────────────────

const AGENTS = [
  { id: 1, name: 'DeliveryIntelligenceAgent', description: 'Analyses Jira events', agentType: 'INTELLIGENCE',
    triggerType: 'WEBHOOK', triggerConfig: '{"events":["issue_updated"]}',
    promptTemplate: 'Analyse project {{project_name}}', channelConfig: '{}',
    tools: ['orbit.get_cr_summary', 'memory.read'], outputChannel: 'IN_APP',
    requiresHitl: true, enabled: true, systemAgent: true, createdBy: 'system' },
  { id: 2, name: 'StandupAgent', description: 'Posts standup to Slack', agentType: 'COMMUNICATION',
    triggerType: 'CRON', triggerConfig: '{"cron":"0 9 * * MON-FRI"}',
    promptTemplate: '', channelConfig: '{}',
    tools: ['orbit.get_cr_summary', 'slack.send_channel'], outputChannel: 'SLACK',
    requiresHitl: false, enabled: true, systemAgent: false, createdBy: 'admin@orbit.io' },
  { id: 3, name: 'MyCustomAgent', description: 'Custom reminder', agentType: 'REMINDER',
    triggerType: 'CRON', triggerConfig: '{"cron":"0 9 * * MON-FRI"}',
    promptTemplate: '', channelConfig: '{}',
    tools: ['slack.send_channel'], outputChannel: 'SLACK',
    requiresHitl: true, enabled: false, systemAgent: false, createdBy: 'admin@orbit.io' },
]

const TOOLS = [
  { id: 'orbit.get_cr_summary', description: 'Get CR summary', requiresHitl: false },
  { id: 'slack.send_channel',   description: 'Post to Slack',   requiresHitl: false },
  { id: 'memory.read',          description: 'Read memory',      requiresHitl: false },
  { id: 'email.send',           description: 'Send email',       requiresHitl: true  },
]

const RUNS_PAGE = {
  content: [
    { id: 8, agentId: 2, agentName: 'StandupAgent', agentType: 'COMMUNICATION',
      triggeredBy: 'MANUAL_TEST', status: 'COMPLETED', durationMs: 896,
      pendingHitl: 0, startedAt: new Date(Date.now() - 120000).toISOString() },
    { id: 5, agentId: 1, agentName: 'EscalationAgent', agentType: 'ESCALATION',
      triggeredBy: 'THRESHOLD', status: 'COMPLETED', durationMs: 210,
      pendingHitl: 1, startedAt: new Date(Date.now() - 3600000).toISOString() },
    { id: 3, agentId: 2, agentName: 'StandupAgent', agentType: 'COMMUNICATION',
      triggeredBy: 'CRON', status: 'FAILED', durationMs: 12,
      pendingHitl: 0, startedAt: new Date(Date.now() - 7200000).toISOString() },
  ],
  totalPages: 1, totalElements: 3,
}

const STEPS_EXECUTED = [
  { id: 10, runId: 8, tool: 'orbit.get_cr_summary', status: 'EXECUTED', hitlRequired: false,
    hitlOutcome: null, args: null, result: '{"totalCrs":659,"onHold":14}', calledAt: new Date().toISOString() },
  { id: 11, runId: 8, tool: 'slack.send_channel',   status: 'EXECUTED', hitlRequired: false,
    hitlOutcome: null, args: null, result: '{"ok":true,"ts":"178.001"}',    calledAt: new Date().toISOString() },
]

const STEPS_WITH_HITL = [
  { id: 20, runId: 5, tool: 'orbit.get_stakeholder_contacts', status: 'EXECUTED',     hitlRequired: false,
    hitlOutcome: null,            args: null, result: '{"contacts":["alex.doe@example.com"]}', calledAt: new Date().toISOString() },
  { id: 21, runId: 5, tool: 'email.send',                     status: 'AWAITING_HITL', hitlRequired: true,
    hitlOutcome: 'AWAITING_HITL', args: '{"to":"alex.doe@example.com","subject":"Escalation"}',
    result: '{"status":"awaiting_hitl"}', calledAt: new Date().toISOString() },
]

const PENDING_HITL = [
  { id: 21, runId: 5, tool: 'email.send', status: 'AWAITING_HITL', hitlRequired: true,
    hitlOutcome: 'AWAITING_HITL', args: '{"to":"alex.doe@example.com","subject":"Escalation: NX-884"}',
    agentId: 1, agentName: 'EscalationAgent', agentType: 'ESCALATION',
    triggeredBy: 'THRESHOLD', calledAt: new Date(Date.now() - 7200000).toISOString() },
]

// Successful test-run response
const RUN_ALL_EXECUTED = {
  runId: 99, status: 'COMPLETED', durationMs: 48,
  steps: [
    { tool: 'orbit.get_cr_summary', status: 'EXECUTED',      hitlRequired: false, result: '{"totalCrs":50,"onHold":3}' },
    { tool: 'slack.send_channel',   status: 'EXECUTED',      hitlRequired: false, result: '{"sent":true,"channel":"#orbit"}' },
  ],
}

const RUN_WITH_HITL = {
  runId: 100, status: 'COMPLETED', durationMs: 12,
  steps: [
    { tool: 'orbit.get_cr_summary', status: 'EXECUTED',      hitlRequired: false, result: '{"totalCrs":50}' },
    { tool: 'email.send',           status: 'AWAITING_HITL', hitlRequired: true,  result: '{"status":"awaiting_hitl"}' },
  ],
}

const RUN_FAILED = {
  runId: 101, status: 'FAILED', durationMs: 5, errorMessage: 'DB connection failed', steps: [],
}

// ── Test setup ─────────────────────────────────────────────────────────────────

function setup() {
  useStore.setState({
    user: { id: 1, name: 'Admin', email: 'admin@orbit.io', role: 'ADMIN',
            initials: 'AD', avatarColor: '#6366F1', token: 'tok' }
  })
  vi.mocked(api.get).mockImplementation((url: string) => {
    if (url === '/admin/agents')                     return Promise.resolve({ data: AGENTS })
    if (url === '/admin/agents/tools')               return Promise.resolve({ data: TOOLS })
    if (url === '/admin/agents/runs/pending-hitl')   return Promise.resolve({ data: PENDING_HITL })
    if (url === '/admin/agents/runs')                return Promise.resolve({ data: RUNS_PAGE })
    if (url.includes('/runs/8/steps'))               return Promise.resolve({ data: STEPS_EXECUTED })
    if (url.includes('/runs/5/steps'))               return Promise.resolve({ data: STEPS_WITH_HITL })
    return Promise.resolve({ data: { content: [], totalPages: 0 } })
  })
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter><AgentBuilderPage /></MemoryRouter>
    </QueryClientProvider>
  )
}

/** Navigate to the Execution logs tab after initial load. */
async function goToLogsTab() {
  setup()
  // Wait for page to load using the subtitle (avoids ambiguity with the "Agents" tab button)
  await waitFor(() => expect(screen.getByText(/3 agents/)).toBeInTheDocument())
  fireEvent.click(screen.getByText('Execution logs'))
  await waitFor(() => expect(screen.getByText('All agents')).toBeInTheDocument())
}

/** Navigate to the Pending approvals tab after initial load. */
async function goToPendingTab() {
  setup()
  await waitFor(() => expect(screen.getByText(/3 agents/)).toBeInTheDocument())
  fireEvent.click(screen.getByText(/Pending approvals/))
  await waitFor(() => expect(screen.getByText('AWAITING APPROVAL')).toBeInTheDocument())
}

beforeEach(() => { vi.clearAllMocks() })

// ── Page structure ─────────────────────────────────────────────────────────────

describe('page structure', () => {
  it('renders the page heading', async () => {
    setup()
    // "Agents" appears as both the heading and the tab button — check at least one exists
    await waitFor(() => expect(screen.getAllByText('Agents').length).toBeGreaterThanOrEqual(1))
  })

  it('shows agent count in subtitle', async () => {
    setup()
    await waitFor(() => expect(screen.getByText(/3 agents/)).toBeInTheDocument())
  })

  it('shows enabled count in subtitle', async () => {
    setup()
    await waitFor(() => expect(screen.getByText(/2 enabled/)).toBeInTheDocument())
  })

  it('renders three tabs: Agents, Execution logs, Pending approvals', async () => {
    setup()
    await waitFor(() => {
      // "Agents" appears as both heading and tab — getAllByText confirms both are present
      expect(screen.getAllByText('Agents').length).toBeGreaterThanOrEqual(2)
      expect(screen.getByText('Execution logs')).toBeInTheDocument()
      expect(screen.getByText(/Pending approvals/)).toBeInTheDocument()
    })
  })

  it('shows pending count in Pending approvals tab label', async () => {
    setup()
    await waitFor(() => expect(screen.getByText(/Pending approvals \(1\)/)).toBeInTheDocument())
  })

  it('shows pending approvals banner button when not on that tab', async () => {
    setup()
    await waitFor(() => expect(screen.getByText(/1 pending approval/)).toBeInTheDocument())
  })

  it('pending approvals banner switches to Pending approvals tab', async () => {
    setup()
    await waitFor(() => screen.getByText(/1 pending approval/))
    fireEvent.click(screen.getByText(/1 pending approval/))
    await waitFor(() => expect(screen.getByText('AWAITING APPROVAL')).toBeInTheDocument())
  })
})

// ── Agents tab — layout ────────────────────────────────────────────────────────

describe('Agents tab — layout', () => {
  it('renders New agent button on Agents tab', async () => {
    setup()
    await waitFor(() => expect(screen.getByText('+ New agent')).toBeInTheDocument())
  })

  it('renders available tools in reference bar', async () => {
    setup()
    await waitFor(() => {
      expect(screen.getAllByText('orbit.get_cr_summary').length).toBeGreaterThanOrEqual(1)
      expect(screen.getAllByText(/slack\.send_channel/).length).toBeGreaterThanOrEqual(1)
    })
  })

  it('marks HITL tools with ⊙ symbol in reference bar', async () => {
    setup()
    await waitFor(() => expect(screen.getByText(/email\.send.*⊙/)).toBeInTheDocument())
  })

  it('New agent button is hidden on Execution logs tab', async () => {
    await goToLogsTab()
    expect(screen.queryByText('+ New agent')).not.toBeInTheDocument()
  })
})

// ── Agents tab — table ─────────────────────────────────────────────────────────

describe('Agents tab — table', () => {
  it('renders all agent names', async () => {
    setup()
    await waitFor(() => {
      expect(screen.getByText('DeliveryIntelligenceAgent')).toBeInTheDocument()
      expect(screen.getByText('StandupAgent')).toBeInTheDocument()
      expect(screen.getByText('MyCustomAgent')).toBeInTheDocument()
    })
  })

  it('shows "system" label for system agents', async () => {
    setup()
    await waitFor(() => expect(screen.getByText('system')).toBeInTheDocument())
  })

  it('shows delete button only for non-system agents', async () => {
    setup()
    await waitFor(() => screen.getByText('DeliveryIntelligenceAgent'))
    const rows = screen.getAllByRole('row')
    const sysRow = rows.find(r => within(r).queryByText('DeliveryIntelligenceAgent'))
    expect(within(sysRow!).queryByText('✕')).not.toBeInTheDocument()
    const customRow = rows.find(r => within(r).queryByText('MyCustomAgent'))
    expect(within(customRow!).getByText('✕')).toBeInTheDocument()
  })

  it('renders enabled/disabled badges', async () => {
    setup()
    await waitFor(() => {
      expect(screen.getAllByText('● Enabled').length).toBeGreaterThanOrEqual(1)
      expect(screen.getByText('○ Disabled')).toBeInTheDocument()
    })
  })
})

// ── Agents tab — enable/disable toggle ────────────────────────────────────────

describe('Agents tab — toggle enable/disable', () => {
  it('calls PATCH toggle on enabled button click', async () => {
    vi.mocked(api.patch).mockResolvedValueOnce({ data: { enabled: false } })
    setup()
    await waitFor(() => screen.getAllByText('● Enabled'))
    fireEvent.click(screen.getAllByText('● Enabled')[0])
    await waitFor(() => expect(api.patch).toHaveBeenCalledWith('/admin/agents/1/toggle'))
  })
})

// ── Agents tab — test run button ───────────────────────────────────────────────

describe('Agents tab — test run button', () => {
  it('calls POST /{id}/test-run on ▶ Test click', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: RUN_ALL_EXECUTED })
    setup()
    await waitFor(() => screen.getAllByText('▶ Test'))
    fireEvent.click(screen.getAllByText('▶ Test')[0])
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/admin/agents/1/test-run'))
  })

  it('shows ⟳ spinner while run is in flight', async () => {
    let resolve: any
    vi.mocked(api.post).mockReturnValueOnce(new Promise(r => { resolve = r }))
    setup()
    await waitFor(() => screen.getAllByText('▶ Test'))
    fireEvent.click(screen.getAllByText('▶ Test')[0])
    await waitFor(() => expect(screen.getByText('⟳')).toBeInTheDocument())
    resolve({ data: RUN_ALL_EXECUTED })
  })

  it('shows toast on test run network failure', async () => {
    vi.mocked(api.post).mockRejectedValueOnce(new Error('500'))
    setup()
    await waitFor(() => screen.getAllByText('▶ Test'))
    fireEvent.click(screen.getAllByText('▶ Test')[0])
    await waitFor(() => expect(screen.getByText('Test run failed')).toBeInTheDocument())
  })
})

// ── Agents tab — RunStepsPanel ─────────────────────────────────────────────────

describe('Agents tab — RunStepsPanel', () => {
  async function runTest(agentIdx = 0, data = RUN_ALL_EXECUTED) {
    vi.mocked(api.post).mockResolvedValueOnce({ data })
    setup()
    await waitFor(() => screen.getAllByText('▶ Test'))
    fireEvent.click(screen.getAllByText('▶ Test')[agentIdx])
    await waitFor(() => screen.getByText(/Run #/))
  }

  it('shows run ID and COMPLETED status after successful run', async () => {
    await runTest()
    expect(screen.getByText(/Run #99/)).toBeInTheDocument()
    expect(screen.getByText(/COMPLETED/)).toBeInTheDocument()
  })

  it('shows duration in ms', async () => {
    await runTest()
    expect(screen.getByText(/48ms/)).toBeInTheDocument()
  })

  it('shows ✓ icon for EXECUTED steps', async () => {
    await runTest()
    const panel = screen.getByText(/Run #99/).closest('td')!
    expect(within(panel).getAllByText('✓').length).toBe(2)
  })

  it('renders tool name for each step', async () => {
    await runTest()
    const panel = screen.getByText(/Run #99/).closest('td')!
    expect(within(panel).getByText('orbit.get_cr_summary')).toBeInTheDocument()
    expect(within(panel).getByText('slack.send_channel')).toBeInTheDocument()
  })

  it('shows result preview for executed tools', async () => {
    await runTest()
    await waitFor(() => expect(screen.getByText(/totalCrs: 50/)).toBeInTheDocument())
  })

  it('shows HITL badge with blocked count', async () => {
    await runTest(0, RUN_WITH_HITL)
    expect(screen.getByText(/1 tool.*awaiting HITL/i)).toBeInTheDocument()
  })

  it('shows ⏸ icon for HITL-blocked tools', async () => {
    await runTest(0, RUN_WITH_HITL)
    const panel = screen.getByText(/Run #100/).closest('td')!
    expect(within(panel).getByText('⏸')).toBeInTheDocument()
  })

  it('shows blocked message next to HITL tool name', async () => {
    await runTest(0, RUN_WITH_HITL)
    const panel = screen.getByText(/Run #100/).closest('td')!
    expect(within(panel).getByText('email.send')).toBeInTheDocument()
    expect(within(panel).getByText(/blocked.*waiting for human approval/i)).toBeInTheDocument()
  })

  it('shows ✗ and error message for FAILED run', async () => {
    await runTest(0, RUN_FAILED)
    expect(screen.getByText(/FAILED/)).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText(/DB connection failed/)).toBeInTheDocument())
  })

  it('shows empty steps message when run has no tool calls', async () => {
    await runTest(0, RUN_FAILED)
    await waitFor(() =>
      expect(screen.getByText(/No tools were configured/)).toBeInTheDocument()
    )
  })
})

// ── Agents tab — New agent modal ───────────────────────────────────────────────

describe('Agents tab — New agent modal', () => {
  it('opens modal on + New agent click', async () => {
    setup()
    await waitFor(() => screen.getByText('+ New agent'))
    fireEvent.click(screen.getByText('+ New agent'))
    expect(screen.getByText('New agent')).toBeInTheDocument()
  })

  it('modal has name input', async () => {
    setup()
    await waitFor(() => screen.getByText('+ New agent'))
    fireEvent.click(screen.getByText('+ New agent'))
    expect(screen.getByPlaceholderText('e.g. Dev Reminder Agent')).toBeInTheDocument()
  })

  it('Save button disabled when name empty', async () => {
    setup()
    await waitFor(() => screen.getByText('+ New agent'))
    fireEvent.click(screen.getByText('+ New agent'))
    expect(screen.getByText('Create agent')).toBeDisabled()
  })

  it('Save button enables after name typed', async () => {
    setup()
    await waitFor(() => screen.getByText('+ New agent'))
    fireEvent.click(screen.getByText('+ New agent'))
    fireEvent.change(screen.getByPlaceholderText('e.g. Dev Reminder Agent'), { target: { value: 'My New Agent' } })
    expect(screen.getByText('Create agent')).not.toBeDisabled()
  })

  it('calls POST /admin/agents on save', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: { id: 10 } })
    setup()
    await waitFor(() => screen.getByText('+ New agent'))
    fireEvent.click(screen.getByText('+ New agent'))
    fireEvent.change(screen.getByPlaceholderText('e.g. Dev Reminder Agent'), { target: { value: 'My New Agent' } })
    fireEvent.click(screen.getByText('Create agent'))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith(
      '/admin/agents', expect.objectContaining({ name: 'My New Agent' })
    ))
  })

  it('closes modal on ✕ click', async () => {
    setup()
    await waitFor(() => screen.getByText('+ New agent'))
    fireEvent.click(screen.getByText('+ New agent'))
    const allX = screen.getAllByRole('button', { name: '✕' })
    fireEvent.click(allX[allX.length - 1])
    await waitFor(() => expect(screen.queryByText('New agent')).not.toBeInTheDocument())
  })
})

// ── Agents tab — Edit agent modal ─────────────────────────────────────────────

describe('Agents tab — Edit agent modal', () => {
  it('opens with pre-populated name', async () => {
    setup()
    await waitFor(() => screen.getAllByText('Edit'))
    fireEvent.click(screen.getAllByText('Edit')[0])
    expect(screen.getByDisplayValue('DeliveryIntelligenceAgent')).toBeInTheDocument()
  })

  it('pre-populates promptTemplate field', async () => {
    setup()
    await waitFor(() => screen.getAllByText('Edit'))
    fireEvent.click(screen.getAllByText('Edit')[0])
    expect(screen.getByDisplayValue('Analyse project {{project_name}}')).toBeInTheDocument()
  })

  it('shows Update agent button for edit mode', async () => {
    setup()
    await waitFor(() => screen.getAllByText('Edit'))
    fireEvent.click(screen.getAllByText('Edit')[0])
    expect(screen.getByText('Update agent')).toBeInTheDocument()
  })

  it('calls PUT /admin/agents/{id} on save', async () => {
    vi.mocked(api.put).mockResolvedValueOnce({ data: { ok: true } })
    setup()
    await waitFor(() => screen.getAllByText('Edit'))
    fireEvent.click(screen.getAllByText('Edit')[0])
    await waitFor(() => screen.getByText('Update agent'))
    fireEvent.click(screen.getByText('Update agent'))
    await waitFor(() => expect(api.put).toHaveBeenCalledWith(
      '/admin/agents/1', expect.objectContaining({ name: 'DeliveryIntelligenceAgent' })
    ))
  })
})

// ── Execution logs tab — layout ───────────────────────────────────────────────

describe('Execution logs tab — layout', () => {
  it('shows the runs table after switching to Execution logs', async () => {
    await goToLogsTab()
    expect(screen.getByText('All agents')).toBeInTheDocument()
    expect(screen.getByText('All statuses')).toBeInTheDocument()
  })

  it('renders all run rows', async () => {
    await goToLogsTab()
    await waitFor(() => {
      expect(screen.getAllByText('StandupAgent').length).toBeGreaterThanOrEqual(1)
      expect(screen.getByText('EscalationAgent')).toBeInTheDocument()
    })
  })

  it('shows triggered-by values', async () => {
    await goToLogsTab()
    await waitFor(() => {
      expect(screen.getByText('MANUAL_TEST')).toBeInTheDocument()
      expect(screen.getByText('THRESHOLD')).toBeInTheDocument()
      expect(screen.getByText('CRON')).toBeInTheDocument()
    })
  })

  it('shows COMPLETED and FAILED statuses', async () => {
    await goToLogsTab()
    await waitFor(() => {
      expect(screen.getAllByText('COMPLETED').length).toBeGreaterThanOrEqual(1)
      expect(screen.getByText('FAILED')).toBeInTheDocument()
    })
  })

  it('shows ⏸ badge on runs with pending HITL', async () => {
    await goToLogsTab()
    await waitFor(() => expect(screen.getByText('⏸ 1')).toBeInTheDocument())
  })
})

// ── Execution logs tab — step expansion ───────────────────────────────────────

describe('Execution logs tab — step expansion', () => {
  async function expandRun8() {
    await goToLogsTab()
    await waitFor(() => screen.getByText('MANUAL_TEST'))
    const rows = screen.getAllByRole('row')
    const run8Row = rows.find(r => within(r).queryByText('MANUAL_TEST'))!
    fireEvent.click(run8Row)
    await waitFor(() => screen.getByText('orbit.get_cr_summary'))
  }

  it('clicking a row expands its step detail', async () => {
    await expandRun8()
    expect(screen.getByText('orbit.get_cr_summary')).toBeInTheDocument()
    expect(screen.getByText('slack.send_channel')).toBeInTheDocument()
  })

  it('shows result preview for executed steps', async () => {
    await expandRun8()
    await waitFor(() => expect(screen.getByText(/totalCrs: 659/)).toBeInTheDocument())
  })

  it('clicking an expanded row again collapses it', async () => {
    await goToLogsTab()
    await waitFor(() => screen.getByText('MANUAL_TEST'))
    const rows = screen.getAllByRole('row')
    const row = rows.find(r => within(r).queryByText('MANUAL_TEST'))!
    fireEvent.click(row)
    await waitFor(() => screen.getByText('orbit.get_cr_summary'))
    fireEvent.click(row)
    await waitFor(() => expect(screen.queryByText('orbit.get_cr_summary')).not.toBeInTheDocument())
  })
})

// ── Execution logs tab — HITL steps ───────────────────────────────────────────

describe('Execution logs tab — HITL steps', () => {
  async function expandRun5() {
    await goToLogsTab()
    await waitFor(() => screen.getByText('THRESHOLD'))
    const rows = screen.getAllByRole('row')
    const run5Row = rows.find(r => within(r).queryByText('THRESHOLD'))!
    fireEvent.click(run5Row)
    await waitFor(() => screen.getByText('email.send'))
  }

  it('shows ⏸ for AWAITING_HITL steps', async () => {
    await expandRun5()
    // ⏸ appears in the step icon
    const stepRow = screen.getByText('email.send').closest('div')!
    expect(within(stepRow.parentElement!).getAllByText('⏸').length).toBeGreaterThanOrEqual(1)
  })

  it('shows awaiting approval message for HITL steps', async () => {
    await expandRun5()
    await waitFor(() =>
      expect(screen.getByText(/awaiting approval before this tool can execute/i)).toBeInTheDocument()
    )
  })

  it('shows Approve and Reject buttons for HITL steps', async () => {
    await expandRun5()
    await waitFor(() => {
      expect(screen.getByText(/✓ Approve/i)).toBeInTheDocument()
      expect(screen.getByText(/✗ Reject/i)).toBeInTheDocument()
    })
  })

  it('calls POST approve on Approve click', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: { ok: true, result: { ok: true, ts: '178.1' } } })
    await expandRun5()
    await waitFor(() => screen.getByText(/✓ Approve/i))
    fireEvent.click(screen.getByText(/✓ Approve/i))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith(
      '/admin/agents/runs/5/steps/21/approve', {}
    ))
  })

  it('shows rejection textarea after clicking Reject', async () => {
    await expandRun5()
    fireEvent.click(screen.getByText(/✗ Reject/i))
    await waitFor(() => expect(screen.getByPlaceholderText(/Reason for rejection/i)).toBeInTheDocument())
  })

  it('calls POST reject with reason on confirm', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: { ok: true } })
    await expandRun5()
    fireEvent.click(screen.getByText(/✗ Reject/i))
    await waitFor(() => screen.getByPlaceholderText(/Reason for rejection/i))
    fireEvent.change(screen.getByPlaceholderText(/Reason for rejection/i), {
      target: { value: 'Wrong channel mapping' }
    })
    fireEvent.click(screen.getByText(/Confirm rejection/i))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith(
      '/admin/agents/runs/5/steps/21/reject',
      expect.objectContaining({ reason: 'Wrong channel mapping' })
    ))
  })
})

// ── Pending approvals tab ─────────────────────────────────────────────────────

describe('Pending approvals tab', () => {
  it('shows agent name and tool name for pending items', async () => {
    await goToPendingTab()
    expect(screen.getByText(/EscalationAgent/)).toBeInTheDocument()
    expect(screen.getByText(/⏸ email\.send/)).toBeInTheDocument()
  })

  it('shows AWAITING APPROVAL badge', async () => {
    await goToPendingTab()
    expect(screen.getByText('AWAITING APPROVAL')).toBeInTheDocument()
  })

  it('shows the proposed args JSON', async () => {
    await goToPendingTab()
    await waitFor(() => expect(screen.getByText(/alex\.doe@example\.com/)).toBeInTheDocument())
  })

  it('shows Approve & execute button', async () => {
    await goToPendingTab()
    expect(screen.getByText(/Approve & execute/i)).toBeInTheDocument()
  })

  it('calls POST approve when Approve & execute clicked', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: { ok: true, result: { ok: true } } })
    await goToPendingTab()
    fireEvent.click(screen.getByText(/Approve & execute/i))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith(
      '/admin/agents/runs/5/steps/21/approve', {}
    ))
  })

  it('shows rejection textarea after clicking Reject', async () => {
    await goToPendingTab()
    fireEvent.click(screen.getByText(/✗ Reject/))
    await waitFor(() => expect(screen.getByPlaceholderText(/Wrong recipient/i)).toBeInTheDocument())
  })

  it('Confirm rejection button disabled until reason typed', async () => {
    await goToPendingTab()
    fireEvent.click(screen.getByText(/✗ Reject/))
    await waitFor(() => screen.getByText(/Confirm rejection/i))
    expect(screen.getByText(/Confirm rejection/i)).toBeDisabled()
  })

  it('enables Confirm rejection after typing reason', async () => {
    await goToPendingTab()
    fireEvent.click(screen.getByText(/✗ Reject/))
    await waitFor(() => screen.getByPlaceholderText(/Wrong recipient/i))
    fireEvent.change(screen.getByPlaceholderText(/Wrong recipient/i), {
      target: { value: 'Needs review first' }
    })
    expect(screen.getByText(/Confirm rejection/i)).not.toBeDisabled()
  })

  it('calls POST reject with reason on confirm', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: { ok: true } })
    await goToPendingTab()
    fireEvent.click(screen.getByText(/✗ Reject/))
    await waitFor(() => screen.getByPlaceholderText(/Wrong recipient/i))
    fireEvent.change(screen.getByPlaceholderText(/Wrong recipient/i), {
      target: { value: 'Channel not configured yet' }
    })
    fireEvent.click(screen.getByText(/Confirm rejection/i))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith(
      '/admin/agents/runs/5/steps/21/reject',
      expect.objectContaining({ reason: 'Channel not configured yet' })
    ))
  })

  it('Cancel restores Approve & execute button', async () => {
    await goToPendingTab()
    fireEvent.click(screen.getByText(/✗ Reject/))
    await waitFor(() => screen.getByText('Cancel'))
    fireEvent.click(screen.getByText('Cancel'))
    await waitFor(() => expect(screen.getByText(/Approve & execute/i)).toBeInTheDocument())
  })

  it('shows empty state when no pending approvals', async () => {
    vi.mocked(api.get).mockImplementation((url: string) => {
      if (url === '/admin/agents')                   return Promise.resolve({ data: AGENTS })
      if (url === '/admin/agents/tools')             return Promise.resolve({ data: TOOLS })
      if (url === '/admin/agents/runs/pending-hitl') return Promise.resolve({ data: [] })
      return Promise.resolve({ data: { content: [], totalPages: 0 } })
    })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={client}><MemoryRouter><AgentBuilderPage /></MemoryRouter></QueryClientProvider>)
    await waitFor(() => screen.getByText(/Pending approvals/))
    fireEvent.click(screen.getByText(/Pending approvals/))
    await waitFor(() => expect(screen.getByText(/No pending approvals/i)).toBeInTheDocument())
  })
})
