import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { IntegrationsPage } from './IntegrationsPage'
import { useStore } from '../../../app/store'

vi.mock('./JiraIntegration', () => ({ JiraIntegration: () => <div>Jira Integration Content</div> }))
vi.mock('./HrmsIntegration', () => ({ HrmsIntegration: () => <div>HR System Integration Content</div> }))

vi.mock('../../../api/client', () => ({
  api: {
    get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  },
}))

import { api } from '../../../api/client'

const SLACK_CFG = { id: 1, workspaceName: 'acme', botToken: '***1234', defaultChannel: '#orbit-alerts', enabled: true, configured: true }
const PROJECTS  = [{ id: 1, name: 'CRM Core', clientName: 'Nexus Corp' }, { id: 2, name: 'Collections 2.0', clientName: 'Sigma' }]
const CHANNELS  = [{ projectId: 1, projectName: 'CRM Core', channelId: 'C0123ABC', channelName: 'C0123ABC' }]

function setup(slackData: any = SLACK_CFG) {
  useStore.setState({
    user: { id: 1, name: 'Admin', email: 'admin@orbit.io', role: 'ADMIN', initials: 'AD', avatarColor: '#6366F1', token: 'tok' }
  })
  vi.mocked(api.get).mockImplementation((url: string) => {
    if (url === '/admin/integrations/slack')          return Promise.resolve({ data: slackData })
    if (url === '/admin/integrations/slack/channels') return Promise.resolve({ data: CHANNELS })
    if (url === '/admin/projects')                    return Promise.resolve({ data: PROJECTS })
    return Promise.resolve({ data: [] })
  })
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter><IntegrationsPage /></MemoryRouter>
    </QueryClientProvider>
  )
}

beforeEach(() => { vi.clearAllMocks() })

// ── Integration tabs ───────────────────────────────────────────────────────────

describe('Integration tabs', () => {
  it('renders three tabs: Jira, HR System, Slack', async () => {
    setup()
    await waitFor(() => {
      expect(screen.getByText('Jira')).toBeInTheDocument()
      expect(screen.getByText('HR System')).toBeInTheDocument()
      expect(screen.getByText('Slack')).toBeInTheDocument()
    })
  })

  it('shows Jira content on Jira tab (default)', async () => {
    setup()
    await waitFor(() => expect(screen.getByText('Jira Integration Content')).toBeInTheDocument())
  })

  it('shows HR System content on HR System tab click', async () => {
    setup()
    await waitFor(() => screen.getByText('HR System'))
    fireEvent.click(screen.getByText('HR System'))
    await waitFor(() => expect(screen.getByText('HR System Integration Content')).toBeInTheDocument())
  })

  it('shows Slack content on Slack tab click', async () => {
    setup()
    await waitFor(() => screen.getByText('Slack'))
    fireEvent.click(screen.getByText('Slack'))
    await waitFor(() => expect(screen.getByText('Post agent messages to project channels')).toBeInTheDocument())
  })

  it('page heading says Integrations', async () => {
    setup()
    await waitFor(() => expect(screen.getByText('Integrations')).toBeInTheDocument())
  })
})

// ── Page structure ─────────────────────────────────────────────────────────────

describe('IntegrationsPage layout', () => {
  it('renders page heading', async () => {
    setup()
    await waitFor(() => expect(screen.getByText('Integrations')).toBeInTheDocument())
  })

  it('renders Slack section when on Slack tab', async () => {
    setup()
    await waitFor(() => screen.getByText('Slack'))
    fireEvent.click(screen.getByText('Slack'))
    await waitFor(() => expect(screen.getByText('Post agent messages to project channels')).toBeInTheDocument())
  })

  it('renders Email stub section when on Slack tab', async () => {
    setup()
    await waitFor(() => screen.getByText('Slack'))
    fireEvent.click(screen.getByText('Slack'))
    await waitFor(() => expect(screen.getByText('Email (SMTP)')).toBeInTheDocument())
  })
})

// ── Slack config display ───────────────────────────────────────────────────────

describe('Slack config display', () => {
  function setupSlackTab(slackData: any = SLACK_CFG) {
    const result = setup(slackData)
    return result
  }

  async function switchToSlack() {
    await waitFor(() => screen.getByText('Slack'))
    fireEvent.click(screen.getByText('Slack'))
  }

  it('shows workspace name when configured', async () => {
    setupSlackTab()
    await switchToSlack()
    await waitFor(() => expect(screen.getByText('acme')).toBeInTheDocument())
  })

  it('shows masked token', async () => {
    setupSlackTab()
    await switchToSlack()
    await waitFor(() => expect(screen.getByText('***1234')).toBeInTheDocument())
  })

  it('shows default channel', async () => {
    setupSlackTab()
    await switchToSlack()
    await waitFor(() => expect(screen.getByText('#orbit-alerts')).toBeInTheDocument())
  })

  it('shows Edit and Send test buttons when configured', async () => {
    setupSlackTab()
    await switchToSlack()
    // Multiple Edit buttons exist (Slack config + channel rows) — check Send test is unique
    await waitFor(() => {
      expect(screen.getAllByText('Edit').length).toBeGreaterThanOrEqual(1)
      expect(screen.getByText('Send test')).toBeInTheDocument()
    })
  })

  it('shows config form when not configured', async () => {
    // Return null so slackCfg is falsy — triggers the form view
    setupSlackTab(null)
    await switchToSlack()
    await waitFor(() => expect(screen.getByPlaceholderText('xoxb-your-token-here')).toBeInTheDocument())
  })
})

// ── Edit flow ─────────────────────────────────────────────────────────────────

describe('Edit Slack config', () => {
  async function setupAndSwitchToSlack(slackData: any = SLACK_CFG) {
    setup(slackData)
    await waitFor(() => screen.getByText('Slack'))
    fireEvent.click(screen.getByText('Slack'))
    await waitFor(() => screen.getByText('Send test'))
  }

  // The Slack config Edit button is adjacent to Send test; click it via getAllByText[0]
  // (Slack config Edit renders before channel-row Edit buttons in DOM order)

  it('shows config form after clicking Edit', async () => {
    await setupAndSwitchToSlack()
    // Slack config Edit is the button next to Send test — it's the first Edit in DOM
    fireEvent.click(screen.getAllByText('Edit')[0])
    await waitFor(() => expect(screen.getByPlaceholderText('Enter new token to update')).toBeInTheDocument())
  })

  it('calls PUT /admin/integrations/slack on save', async () => {
    vi.mocked(api.put).mockResolvedValueOnce({ data: { ok: true } })
    await setupAndSwitchToSlack()
    fireEvent.click(screen.getAllByText('Edit')[0])

    await waitFor(() => screen.getByPlaceholderText('Enter new token to update'))
    fireEvent.change(screen.getByPlaceholderText('Enter new token to update'), { target: { value: 'xoxb-newtoken' } })
    fireEvent.click(screen.getByText('Save'))

    await waitFor(() => expect(api.put).toHaveBeenCalledWith(
      '/admin/integrations/slack',
      expect.objectContaining({ botToken: 'xoxb-newtoken' })
    ))
  })

  it('shows success toast after save', async () => {
    vi.mocked(api.put).mockResolvedValueOnce({ data: { ok: true } })
    await setupAndSwitchToSlack()
    fireEvent.click(screen.getAllByText('Edit')[0])

    await waitFor(() => screen.getByPlaceholderText('Enter new token to update'))
    fireEvent.change(screen.getByPlaceholderText('Enter new token to update'), { target: { value: 'xoxb-t' } })
    fireEvent.click(screen.getByText('Save'))
    await waitFor(() => expect(screen.getByText('Slack config saved')).toBeInTheDocument())
  })

  it('Cancel button restores read view', async () => {
    await setupAndSwitchToSlack()
    fireEvent.click(screen.getAllByText('Edit')[0])
    await waitFor(() => screen.getByText('Cancel'))
    fireEvent.click(screen.getByText('Cancel'))
    await waitFor(() => expect(screen.getByText('Send test')).toBeInTheDocument())
  })
})

// ── Test send ─────────────────────────────────────────────────────────────────

describe('Send test message', () => {
  async function setupAndSwitchToSlack() {
    setup()
    await waitFor(() => screen.getByText('Slack'))
    fireEvent.click(screen.getByText('Slack'))
    await waitFor(() => screen.getByText('Send test'))
  }

  it('calls POST /admin/integrations/slack/test', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: { ok: true, channel: '#orbit-alerts' } })
    await setupAndSwitchToSlack()
    fireEvent.click(screen.getByText('Send test'))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/admin/integrations/slack/test'))
  })

  it('shows success toast when test passes', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: { ok: true, channel: '#orbit-alerts' } })
    await setupAndSwitchToSlack()
    fireEvent.click(screen.getByText('Send test'))
    await waitFor(() => expect(screen.getByText('✓ Test message sent to Slack')).toBeInTheDocument())
  })

  it('shows error toast when test fails', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: { ok: false, error: 'invalid_auth' } })
    await setupAndSwitchToSlack()
    fireEvent.click(screen.getByText('Send test'))
    await waitFor(() => expect(screen.getByText('✗ invalid_auth')).toBeInTheDocument())
  })
})

// ── Channel mapping table ─────────────────────────────────────────────────────

describe('Project channel mapping', () => {
  async function setupAndSwitchToSlack() {
    setup()
    await waitFor(() => screen.getByText('Slack'))
    fireEvent.click(screen.getByText('Slack'))
    await waitFor(() => screen.getByText('Send test'))
  }

  it('renders project names from API', async () => {
    await setupAndSwitchToSlack()
    await waitFor(() => {
      expect(screen.getByText('CRM Core')).toBeInTheDocument()
      expect(screen.getByText('Collections 2.0')).toBeInTheDocument()
    })
  })

  it('shows mapped channel ID for configured project', async () => {
    await setupAndSwitchToSlack()
    await waitFor(() => expect(screen.getByText('C0123ABC')).toBeInTheDocument())
  })

  it('shows "Not configured" for unmapped projects', async () => {
    await setupAndSwitchToSlack()
    await waitFor(() => expect(screen.getByText('Not configured')).toBeInTheDocument())
  })

  it('shows Edit button for mapped project', async () => {
    await setupAndSwitchToSlack()
    await waitFor(() => screen.getByText('C0123ABC'))
    // CRM Core row has 'Edit' for existing mapping
    expect(screen.getAllByText('Edit').length).toBeGreaterThanOrEqual(1)
  })

  it('shows + Set button for unmapped project', async () => {
    await setupAndSwitchToSlack()
    await waitFor(() => screen.getByText('Not configured'))
    expect(screen.getByText('+ Set')).toBeInTheDocument()
  })

  it('calls PUT on save after entering channel ID', async () => {
    vi.mocked(api.put).mockResolvedValueOnce({ data: { ok: true } })
    await setupAndSwitchToSlack()
    await waitFor(() => screen.getByText('+ Set'))
    fireEvent.click(screen.getByText('+ Set'))

    const input = screen.getByPlaceholderText('C0123ABCD')
    fireEvent.change(input, { target: { value: 'C0NEW123' } })
    fireEvent.click(screen.getByText('Save'))

    await waitFor(() => expect(api.put).toHaveBeenCalledWith(
      '/admin/integrations/slack/channels/2',
      expect.objectContaining({ channelId: 'C0NEW123' })
    ))
  })

  it('calls DELETE when ✕ is clicked on a mapped channel', async () => {
    vi.mocked(api.delete).mockResolvedValueOnce({ data: {} })
    await setupAndSwitchToSlack()
    await waitFor(() => screen.getByText('C0123ABC'))
    fireEvent.click(screen.getByText('✕'))
    await waitFor(() => expect(api.delete).toHaveBeenCalledWith('/admin/integrations/slack/channels/1'))
  })

  it('shows success toast after saving channel mapping', async () => {
    vi.mocked(api.put).mockResolvedValueOnce({ data: { ok: true } })
    await setupAndSwitchToSlack()
    await waitFor(() => screen.getByText('+ Set'))
    fireEvent.click(screen.getByText('+ Set'))
    fireEvent.change(screen.getByPlaceholderText('C0123ABCD'), { target: { value: 'C0NEW' } })
    fireEvent.click(screen.getByText('Save'))
    await waitFor(() => expect(screen.getByText('Channel mapping saved')).toBeInTheDocument())
  })
})
