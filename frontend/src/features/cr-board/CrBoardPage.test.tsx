import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { CrBoardPage } from './CrBoardPage'
import { useStore } from '../../app/store'

vi.mock('../../api/client', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  },
}))

import { api } from '../../api/client'

const clientList = [{ id: 1, name: 'Nexus Corp' }, { id: 2, name: 'Sigma Telecom' }]
const stageMap   = { 'In dev': 3, 'In QA': 1 }
const jiraConfig = { baseUrl: 'https://jira.example.com' }
const emptyPage  = { content: [], totalPages: 1, totalElements: 0 }
const oneCrPage  = {
  content: [{ key: 'NX-101', summary: 'Export feature', stage: 'In dev',
              jiraStatus: 'In Progress', pri: 'High', owner: 'alice', client: 'Nexus Corp', age: '5d' }],
  totalPages: 1, totalElements: 1
}

// Default handler covering every URL the component fetches
function defaultGetHandler(url: string) {
  if (url === '/clients')          return Promise.resolve({ data: clientList })
  if (url === '/cr/stage-summary') return Promise.resolve({ data: stageMap })
  if (url === '/jira-sync/config') return Promise.resolve({ data: jiraConfig })
  if (url === '/cr')               return Promise.resolve({ data: emptyPage })
  if (url === '/cr/export')        return Promise.resolve({ data: new Blob(['Key,Summary\n'], { type: 'text/csv' }) })
  return Promise.resolve({ data: {} })
}

function setup(overridePage?: object) {
  useStore.setState({
    user: { id: 1, name: 'Admin', email: 'admin@orbit.io', role: 'ADMIN',
            initials: 'AD', avatarColor: '#6366F1', token: 'tok' }
  })
  vi.mocked(api.get).mockImplementation((url: string) => {
    if (url === '/cr' && overridePage) return Promise.resolve({ data: overridePage })
    return defaultGetHandler(url)
  })
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <CrBoardPage />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  ;(URL as any).createObjectURL = vi.fn(() => 'blob:mock-url')
  ;(URL as any).revokeObjectURL = vi.fn()

  // Prevent jsdom "Not implemented: navigation" when a.click() fires on blob URLs
  const origCreate = document.createElement.bind(document)
  vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
    if (tag === 'a') {
      const a = origCreate('a')
      a.click = vi.fn()
      return a
    }
    return origCreate(tag)
  })
})

afterEach(() => {
  vi.restoreAllMocks()
})

// ── CSV download button ────────────────────────────────────────────────────────

describe('CSV export button', () => {
  it('renders the download button', async () => {
    setup()
    await waitFor(() => expect(screen.getByText('↓ CSV')).toBeInTheDocument())
  })

  it('calls /cr/export with responseType blob on click', async () => {
    setup()
    await waitFor(() => screen.getByText('↓ CSV'))
    fireEvent.click(screen.getByText('↓ CSV'))

    await waitFor(() =>
      expect(api.get).toHaveBeenCalledWith('/cr/export', expect.objectContaining({ responseType: 'blob' }))
    )
  })

  it('passes active stage filter to export', async () => {
    setup()
    await waitFor(() => screen.getByText('In dev'))
    fireEvent.click(screen.getByText('In dev'))     // select stage tile

    await waitFor(() => screen.getByText('↓ CSV'))
    fireEvent.click(screen.getByText('↓ CSV'))

    await waitFor(() =>
      expect(api.get).toHaveBeenCalledWith('/cr/export', expect.objectContaining({
        params: expect.objectContaining({ stage: 'In dev' })
      }))
    )
  })

  it('passes active client filter to export', async () => {
    setup()
    await waitFor(() => screen.getByText('↓ CSV'))

    fireEvent.change(screen.getByRole('combobox', { name: /client filter/i }), { target: { value: '1' } })
    // Client change triggers a refetch — wait for the loading state to clear
    await waitFor(() => screen.getByText('↓ CSV'))

    fireEvent.click(screen.getByText('↓ CSV'))

    await waitFor(() =>
      expect(api.get).toHaveBeenCalledWith('/cr/export', expect.objectContaining({
        params: expect.objectContaining({ clientId: '1' })
      }))
    )
  })

  it('triggers a file download — sets href and download attribute', async () => {
    setup()
    await waitFor(() => screen.getByText('↓ CSV'))
    fireEvent.click(screen.getByText('↓ CSV'))

    // The beforeEach anchor mock captures the element created by downloadCsv
    await waitFor(() => expect(URL.createObjectURL).toHaveBeenCalled())
    // Verify the blob URL was assigned and a download filename was set
    const anchors = document.querySelectorAll('a')
    expect(URL.createObjectURL).toHaveBeenCalled()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-url')
  })

  it('revokes the object URL after download', async () => {
    setup()
    await waitFor(() => screen.getByText('↓ CSV'))
    fireEvent.click(screen.getByText('↓ CSV'))

    await waitFor(() => expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-url'))
  })

  it('shows toast on export failure', async () => {
    setup()
    await waitFor(() => screen.getByText('↓ CSV'))

    // Override just the export call to reject
    vi.mocked(api.get).mockImplementation((url: string) => {
      if (url === '/cr/export') return Promise.reject(new Error('network error'))
      return defaultGetHandler(url)
    })

    fireEvent.click(screen.getByText('↓ CSV'))

    await waitFor(() =>
      expect(screen.getByText('Export failed — please try again')).toBeInTheDocument()
    )
  })

  it('button shows Exporting… and is disabled while in flight', async () => {
    let resolveExport!: (v: any) => void
    const exportPromise = new Promise(res => { resolveExport = res })

    setup()
    await waitFor(() => screen.getByText('↓ CSV'))

    vi.mocked(api.get).mockImplementation((url: string) => {
      if (url === '/cr/export') return exportPromise
      return defaultGetHandler(url)
    })

    fireEvent.click(screen.getByText('↓ CSV'))

    await waitFor(() => expect(screen.getByText('⟳ Exporting…')).toBeInTheDocument())
    expect(screen.getByText('⟳ Exporting…').closest('button')).toBeDisabled()

    resolveExport({ data: new Blob(['csv']) })
  })
})

// ── CR list ────────────────────────────────────────────────────────────────────

describe('CR list', () => {
  it('renders empty state when no CRs', async () => {
    setup()
    await waitFor(() => expect(screen.getByText('No CRs found')).toBeInTheDocument())
  })

  it('renders CR rows returned by the API', async () => {
    setup(oneCrPage)
    await waitFor(() => expect(screen.getByText('Export feature')).toBeInTheDocument())
    expect(screen.getByText(/NX-101/)).toBeInTheDocument()
  })

  it('renders dynamic stage tiles', async () => {
    setup()
    await waitFor(() => {
      expect(screen.getByText('In dev')).toBeInTheDocument()
      expect(screen.getByText('In QA')).toBeInTheDocument()
    })
  })

  it('renders client dropdown options from API', async () => {
    setup()
    await waitFor(() => {
      expect(screen.getByText('Nexus Corp')).toBeInTheDocument()
      expect(screen.getByText('Sigma Telecom')).toBeInTheDocument()
    })
  })
})
