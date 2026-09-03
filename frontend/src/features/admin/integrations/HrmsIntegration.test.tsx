import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { HrmsIntegration } from './HrmsIntegration'

vi.mock('../../../api/client', () => ({
  api: {
    get:   vi.fn(),
    post:  vi.fn(),
    put:   vi.fn(),
    patch: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  },
}))

import { api } from '../../../api/client'

const STATUS_UNCONFIGURED = { provider: null, providerName: null, enabled: false, configured: false }
const STATUS_CONNECTED    = { provider: 'acmehr', providerName: 'AcmeHR', enabled: true, configured: true, lastSyncAt: '2026-06-21T09:30:00' }

const PROVIDERS = [
  {
    key: 'acmehr', name: 'AcmeHR',
    fields: [
      { key: 'baseUrl',       label: 'Tenant URL',      type: 'url',      required: true,  secret: false, placeholder: 'https://yourtenant.example.com', options: [] },
      { key: 'companyId',     label: 'Company ID',      type: 'text',     required: true,  secret: false, placeholder: 'Your company ID', options: [] },
      { key: 'apiKey',        label: 'API key',         type: 'password', required: true,  secret: true,  placeholder: 'Enter API key', options: [] },
      { key: 'authType',      label: 'Auth type',       type: 'select',   required: false, secret: false, placeholder: null, options: ['API_KEY', 'BEARER', 'HMAC'] },
      { key: 'webhookSecret', label: 'Webhook secret',  type: 'password', required: false, secret: true,  placeholder: 'For signature validation', options: [] },
    ],
  },
]

const CONFIG_EMPTY = { provider: null, providerName: null, enabled: false, settings: {}, secretsSet: {} }
const CONFIG_SET   = {
  provider: 'acmehr', providerName: 'AcmeHR', enabled: true,
  settings: { baseUrl: 'https://acme.example.com', companyId: 'acme', authType: 'API_KEY' },
  secretsSet: { apiKey: true, webhookSecret: false },
}

const SYNC_RUNS = [
  { id: 1, type: 'DELTA', status: 'SUCCESS', recordsPulled: 12, startedAt: 'Jun 21, 09:30', completedAt: 'Jun 21, 09:30', errorMessage: null },
  { id: 2, type: 'FULL',  status: 'FAILED',  recordsPulled: 0,  startedAt: 'Jun 20, 08:00', completedAt: 'Jun 20, 08:01', errorMessage: 'Connection refused' },
]

const EMPLOYEES = [
  { id: 1, name: 'Priya K.',   email: 'priya@orbit.io',   role: 'PJM',      av: 'PK', color: '#6366F1', hrmsEmpId: 'EMP001', mapped: true  },
  { id: 2, name: 'Amit S.',    email: 'amit@orbit.io',    role: 'HEAD_PJM', av: 'AS', color: '#8B5CF6', hrmsEmpId: null,     mapped: false },
]

const LEAVES = [
  { id: 1, hrmsEmpId: 'EMP001', hrmsLeaveId: 'DBX-L-1001', name: 'Priya K.',
    av: 'PK', color: '#6366F1', leaveType: 'Annual Leave',
    from: 'Jun 23', to: 'Jun 26, 2026', days: 4, status: 'APPROVED', syncedAt: 'Jun 21, 09:30' },
  { id: 2, hrmsEmpId: 'EMP002', hrmsLeaveId: 'DBX-L-1002', name: 'Amit S.',
    av: 'AS', color: '#8B5CF6', leaveType: 'Sick Leave',
    from: 'Jun 15', to: 'Jun 16, 2026', days: 2, status: 'APPROVED', syncedAt: 'Jun 21, 09:30' },
]

const WFH_RECORDS = [
  { id: 1, hrmsEmpId: 'EMP001', hrmsWfhId: 'DBX-WFH-1001', name: 'Priya K.',
    av: 'PK', color: '#6366F1', wfhDate: 'Jun 22, 2026', wfhType: 'FULL_DAY',
    status: 'APPROVED', reason: 'Focus work — design review', syncedAt: 'Jun 21, 09:30' },
  { id: 2, hrmsEmpId: 'EMP005', hrmsWfhId: 'DBX-WFH-1002', name: 'Dev L.',
    av: 'DL', color: '#3B82F6', wfhDate: 'Jun 23, 2026', wfhType: 'HALF_DAY_AM',
    status: 'APPROVED', reason: null, syncedAt: 'Jun 21, 09:30' },
  { id: 3, hrmsEmpId: 'EMP002', hrmsWfhId: 'DBX-WFH-1003', name: 'Amit S.',
    av: 'AS', color: '#8B5CF6', wfhDate: 'Jun 24, 2026', wfhType: 'FULL_DAY',
    status: 'PENDING', reason: 'Team sync from home', syncedAt: 'Jun 21, 09:30' },
]

function mockApi(overrides: Record<string, any> = {}) {
  vi.mocked(api.get).mockImplementation((url: string) => {
    if (url in overrides)             return Promise.resolve({ data: overrides[url] })
    if (url === '/hrms/status')       return Promise.resolve({ data: STATUS_UNCONFIGURED })
    if (url === '/hrms/providers')    return Promise.resolve({ data: PROVIDERS })
    if (url === '/hrms/runs')         return Promise.resolve({ data: SYNC_RUNS })
    if (url === '/hrms/employees')    return Promise.resolve({ data: EMPLOYEES })
    if (url === '/hrms/leaves')       return Promise.resolve({ data: LEAVES })
    if (url === '/hrms/config')       return Promise.resolve({ data: CONFIG_EMPTY })
    if (url.startsWith('/hrms/wfh?from=')) return Promise.resolve({ data: WFH_RECORDS })
    if (url === '/hrms/wfh')          return Promise.resolve({ data: WFH_RECORDS })
    return Promise.resolve({ data: [] })
  })
}

function setup(overrides: Record<string, any> = {}) {
  mockApi(overrides)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter><HrmsIntegration /></MemoryRouter>
    </QueryClientProvider>
  )
}

beforeEach(() => { vi.clearAllMocks() })

// ── Page layout ───────────────────────────────────────────────────────────────

describe('page layout', () => {
  it('renders the HR System sync heading', async () => {
    setup()
    await waitFor(() => expect(screen.getByText('HR System sync')).toBeInTheDocument())
  })

  it('subtitle mentions leave and WFH', async () => {
    setup()
    await waitFor(() => expect(screen.getByText(/Leave.*WFH.*Attendance/)).toBeInTheDocument())
  })

  it('shows the tabs including Settings', async () => {
    setup()
    await waitFor(() => {
      expect(screen.getByText('Sync runs')).toBeInTheDocument()
      expect(screen.getByText('Employee mapping')).toBeInTheDocument()
      expect(screen.getByText('Leave data')).toBeInTheDocument()
      expect(screen.getByText('WFH data')).toBeInTheDocument()
      expect(screen.getByText('Settings')).toBeInTheDocument()
    })
  })

  it('shows unconfigured status badge when no provider is set', async () => {
    setup()
    await waitFor(() => expect(screen.getByText(/No HR system configured/)).toBeInTheDocument())
  })

  it('shows connected status with provider name when configured and enabled', async () => {
    setup({ '/hrms/status': STATUS_CONNECTED })
    await waitFor(() => expect(screen.getByText(/Connected · AcmeHR/)).toBeInTheDocument())
  })

  it('renders Delta sync and Full sync buttons', async () => {
    setup()
    await waitFor(() => {
      expect(screen.getByText('Delta sync')).toBeInTheDocument()
      expect(screen.getByText('Full sync')).toBeInTheDocument()
    })
  })
})

// ── Stat cards ────────────────────────────────────────────────────────────────

describe('stat cards', () => {
  it('shows WFH this week card', async () => {
    setup()
    await waitFor(() => expect(screen.getByText('WFH this week')).toBeInTheDocument())
  })

  it('shows Approved leaves card', async () => {
    setup()
    await waitFor(() => expect(screen.getByText('Approved leaves')).toBeInTheDocument())
  })

  it('shows Mapped employees and Unmapped cards', async () => {
    setup()
    await waitFor(() => {
      expect(screen.getByText('Mapped employees')).toBeInTheDocument()
      expect(screen.getByText('Unmapped')).toBeInTheDocument()
    })
  })
})

// ── Sync runs tab ─────────────────────────────────────────────────────────────

describe('sync runs tab', () => {
  it('renders sync run history', async () => {
    setup()
    await waitFor(() => {
      expect(screen.getAllByText('DELTA').length).toBeGreaterThanOrEqual(1)
      expect(screen.getAllByText('FULL').length).toBeGreaterThanOrEqual(1)
      expect(screen.getAllByText('SUCCESS').length).toBeGreaterThanOrEqual(1)
      expect(screen.getAllByText('FAILED').length).toBeGreaterThanOrEqual(1)
    })
  })

  it('shows record counts', async () => {
    setup()
    await waitFor(() => expect(screen.getByText('12')).toBeInTheDocument())
  })

  it('shows error message for failed runs', async () => {
    setup()
    await waitFor(() => expect(screen.getByText('Connection refused')).toBeInTheDocument())
  })
})

// ── Employee mapping tab ──────────────────────────────────────────────────────

describe('employee mapping tab', () => {
  it('shows all employees with mapping status', async () => {
    setup()
    fireEvent.click(screen.getByText('Employee mapping'))
    await waitFor(() => {
      expect(screen.getByText('Priya K.')).toBeInTheDocument()
      expect(screen.getByText('Amit S.')).toBeInTheDocument()
    })
  })

  it('shows Mapped badge for mapped employees', async () => {
    setup()
    fireEvent.click(screen.getByText('Employee mapping'))
    await waitFor(() => expect(screen.getAllByText('Mapped').length).toBeGreaterThanOrEqual(1))
  })

  it('shows Unmapped badge for unmapped employees', async () => {
    setup()
    fireEvent.click(screen.getByText('Employee mapping'))
    await waitFor(() => expect(screen.getAllByText('Unmapped').length).toBeGreaterThanOrEqual(1))
  })

  it('clicking Map opens inline input', async () => {
    setup()
    fireEvent.click(screen.getByText('Employee mapping'))
    await waitFor(() => screen.getByText('Map'))
    fireEvent.click(screen.getByText('Map'))
    await waitFor(() => expect(screen.getByPlaceholderText('e.g. EMP001')).toBeInTheDocument())
  })

  it('saving a mapping PATCHes hrmsEmpId', async () => {
    vi.mocked(api.patch).mockResolvedValueOnce({ data: { ok: true } })
    setup()
    fireEvent.click(screen.getByText('Employee mapping'))
    await waitFor(() => screen.getByText('Map'))
    fireEvent.click(screen.getByText('Map'))
    await waitFor(() => screen.getByPlaceholderText('e.g. EMP001'))
    fireEvent.change(screen.getByPlaceholderText('e.g. EMP001'), { target: { value: 'EMP099' } })
    fireEvent.click(screen.getByText('Save'))
    await waitFor(() => expect(api.patch).toHaveBeenCalledWith('/hrms/employees/2/emp-id', { hrmsEmpId: 'EMP099' }))
  })

  it('subtitle mentions auto-map by email', async () => {
    setup()
    fireEvent.click(screen.getByText('Employee mapping'))
    await waitFor(() => expect(screen.getByText(/Full sync auto-maps by email/)).toBeInTheDocument())
  })
})

// ── Leave data tab ────────────────────────────────────────────────────────────

describe('leave data tab', () => {
  it('shows leave records', async () => {
    setup()
    fireEvent.click(screen.getByText('Leave data'))
    await waitFor(() => {
      expect(screen.getAllByText('Annual Leave').length).toBeGreaterThanOrEqual(1)
      expect(screen.getAllByText('Sick Leave').length).toBeGreaterThanOrEqual(1)
    })
  })

  it('shows days column', async () => {
    setup()
    fireEvent.click(screen.getByText('Leave data'))
    await waitFor(() => expect(screen.getByText('4d')).toBeInTheDocument())
  })
})

// ── WFH data tab ──────────────────────────────────────────────────────────────

describe('WFH data tab', () => {
  it('renders WFH records', async () => {
    setup()
    fireEvent.click(screen.getByText('WFH data'))
    await waitFor(() => {
      expect(screen.getAllByText('Priya K.').length).toBeGreaterThanOrEqual(1)
      expect(screen.getAllByText('Dev L.').length).toBeGreaterThanOrEqual(1)
      expect(screen.getAllByText('Amit S.').length).toBeGreaterThanOrEqual(1)
    })
  })

  it('shows wfhType as human-readable label', async () => {
    setup()
    fireEvent.click(screen.getByText('WFH data'))
    await waitFor(() => {
      expect(screen.getAllByText('Full day').length).toBeGreaterThanOrEqual(1)
      expect(screen.getByText('Half day AM')).toBeInTheDocument()
    })
  })

  it('shows APPROVED and PENDING status badges', async () => {
    setup()
    fireEvent.click(screen.getByText('WFH data'))
    await waitFor(() => {
      expect(screen.getAllByText('APPROVED').length).toBeGreaterThanOrEqual(1)
      expect(screen.getByText('PENDING')).toBeInTheDocument()
    })
  })

  it('shows reason text when present', async () => {
    setup()
    fireEvent.click(screen.getByText('WFH data'))
    await waitFor(() => expect(screen.getByText('Focus work — design review')).toBeInTheDocument())
  })

  it('shows HR record ID in monospace column', async () => {
    setup()
    fireEvent.click(screen.getByText('WFH data'))
    await waitFor(() => expect(screen.getByText('DBX-WFH-1001')).toBeInTheDocument())
  })

  it('shows empty state when no WFH records exist', async () => {
    setup({ '/hrms/wfh': [], '/hrms/runs': [] })
    fireEvent.click(screen.getByText('WFH data'))
    await waitFor(() => expect(screen.getByText(/No WFH records/)).toBeInTheDocument())
  })
})

// ── Settings tab ──────────────────────────────────────────────────────────────

describe('settings tab', () => {
  it('shows the provider dropdown with registered connectors', async () => {
    setup()
    fireEvent.click(screen.getByText('Settings'))
    await waitFor(() => {
      expect(screen.getByText('Provider')).toBeInTheDocument()
      expect(screen.getByText('Not configured')).toBeInTheDocument()
      expect(screen.getByText('AcmeHR')).toBeInTheDocument()
    })
  })

  it('renders no settings fields until a provider is selected', async () => {
    setup()
    fireEvent.click(screen.getByText('Settings'))
    await waitFor(() => screen.getByText('Provider'))
    expect(screen.queryByText('Tenant URL *')).not.toBeInTheDocument()
  })

  it('selecting a provider renders its descriptor-driven fields', async () => {
    setup()
    fireEvent.click(screen.getByText('Settings'))
    await waitFor(() => screen.getByText('Provider'))
    fireEvent.change(screen.getByDisplayValue('Not configured'), { target: { value: 'acmehr' } })
    await waitFor(() => {
      expect(screen.getByText('Tenant URL *')).toBeInTheDocument()
      expect(screen.getByText('Company ID *')).toBeInTheDocument()
      expect(screen.getByText('API key *')).toBeInTheDocument()
      expect(screen.getByText('Auth type')).toBeInTheDocument()
    })
  })

  it('pre-fills settings from stored config', async () => {
    setup({ '/hrms/config': CONFIG_SET, '/hrms/status': STATUS_CONNECTED })
    fireEvent.click(screen.getByText('Settings'))
    await waitFor(() => expect(screen.getByDisplayValue('https://acme.example.com')).toBeInTheDocument())
  })

  it('masks stored secrets via placeholder', async () => {
    setup({ '/hrms/config': CONFIG_SET, '/hrms/status': STATUS_CONNECTED })
    fireEvent.click(screen.getByText('Settings'))
    await waitFor(() => expect(screen.getByPlaceholderText('•••••• (set — enter to change)')).toBeInTheDocument())
  })

  it('saving PUTs provider, enabled and settings', async () => {
    vi.mocked(api.put).mockResolvedValueOnce({ data: CONFIG_SET })
    setup({ '/hrms/config': CONFIG_SET, '/hrms/status': STATUS_CONNECTED })
    fireEvent.click(screen.getByText('Settings'))
    await waitFor(() => screen.getByText('Save configuration'))
    fireEvent.click(screen.getByText('Save configuration'))
    await waitFor(() => expect(api.put).toHaveBeenCalledWith('/hrms/config', expect.objectContaining({
      provider: 'acmehr',
      enabled: true,
      settings: expect.objectContaining({ baseUrl: 'https://acme.example.com' }),
    })))
  })

  it('test connection POSTs /hrms/test and shows the result', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: { ok: true, message: 'Connected to https://acme.example.com' } })
    setup({ '/hrms/config': CONFIG_SET, '/hrms/status': STATUS_CONNECTED })
    fireEvent.click(screen.getByText('Settings'))
    await waitFor(() => screen.getByText('Test connection'))
    fireEvent.click(screen.getByText('Test connection'))
    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/hrms/test')
      expect(screen.getByText(/Connected to https:\/\/acme.example.com/)).toBeInTheDocument()
    })
  })

  it('shows the generic webhook receiver URL', async () => {
    setup()
    fireEvent.click(screen.getByText('Settings'))
    await waitFor(() => expect(screen.getByText(/\/api\/v1\/hrms\/webhook/)).toBeInTheDocument())
  })
})
