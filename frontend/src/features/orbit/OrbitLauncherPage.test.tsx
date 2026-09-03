import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { OrbitLauncherPage } from './OrbitLauncherPage'
import { useStore } from '../../app/store'

vi.mock('../../api/client', () => ({
  api: {
    get: vi.fn().mockResolvedValue({ data: {} }),
    post: vi.fn(),
    interceptors: { request: { use: vi.fn() } },
  },
}))

function renderLauncher() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <OrbitLauncherPage />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

const baseUser = {
  id: 1, name: 'Test User', email: 'test@orbit.io',
  initials: 'TU', avatarColor: '#087f7a', token: 'tok',
}

beforeEach(() => {
  useStore.setState({ user: null, collapsed: false, roleScreens: {} })
})

describe('OrbitLauncher product-tile RBAC', () => {
  it('ADMIN sees the full product set as tiles', () => {
    useStore.setState({ user: { ...baseUser, role: 'ADMIN' } })
    renderLauncher()
    expect(screen.getByText('Orbitter')).toBeInTheDocument()
    expect(screen.getByText('CR dashboard')).toBeInTheDocument()
    expect(screen.getByText('Alert center')).toBeInTheDocument()
    expect(screen.getByText('Client backlog')).toBeInTheDocument()
  })

  it('LEADERSHIP only sees the Orbitter tile', () => {
    useStore.setState({ user: { ...baseUser, role: 'LEADERSHIP' } })
    renderLauncher()
    expect(screen.getByText('Orbitter')).toBeInTheDocument()
    expect(screen.queryByText('CR dashboard')).not.toBeInTheDocument()
    expect(screen.queryByText('Alert center')).not.toBeInTheDocument()
  })

  it('ENGINEERING sees Capacity and Man-days but not CR dashboard', () => {
    useStore.setState({ user: { ...baseUser, role: 'ENGINEERING' } })
    renderLauncher()
    expect(screen.getByText('Capacity')).toBeInTheDocument()
    expect(screen.getByText('Man-days')).toBeInTheDocument()
    expect(screen.queryByText('CR dashboard')).not.toBeInTheDocument()
  })

  it('null user falls back to PM product access', () => {
    useStore.setState({ user: null })
    renderLauncher()
    expect(screen.getByText('Orbitter')).toBeInTheDocument()
    expect(screen.getByText('CR dashboard')).toBeInTheDocument()
  })

  it('admin-only screens never appear as product tiles', () => {
    useStore.setState({ user: { ...baseUser, role: 'ADMIN' } })
    renderLauncher()
    expect(screen.queryByText('Admin console')).not.toBeInTheDocument()
    expect(screen.queryByText('Features Control Center')).not.toBeInTheDocument()
  })
})
