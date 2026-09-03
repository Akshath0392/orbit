import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TopBar } from './TopBar'
import { useStore } from '../app/store'

vi.mock('../api/client', () => ({
  api: {
    get: vi.fn().mockResolvedValue({ data: {} }),
    post: vi.fn(),
    interceptors: { request: { use: vi.fn() } },
  },
}))

function renderTopBar() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <TopBar />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

const baseUser = {
  id: 1, name: 'Test User', email: 'test@orbit.io',
  initials: 'TU', avatarColor: '#087f7a', token: 'tok',
}

// Open the avatar dropdown (initials button) so its menu items render.
function openMenu() {
  fireEvent.click(screen.getByText('TU'))
}

beforeEach(() => {
  useStore.setState({ user: null, collapsed: false, roleScreens: {} })
})

describe('TopBar avatar menu RBAC', () => {
  it('ADMIN sees Admin console + Agents + Integrations in the avatar menu', () => {
    useStore.setState({ user: { ...baseUser, role: 'ADMIN' } })
    renderTopBar()
    openMenu()
    expect(screen.getByText('Admin console')).toBeInTheDocument()
    expect(screen.getByText('Agents')).toBeInTheDocument()
    expect(screen.getByText('Integrations')).toBeInTheDocument()
  })

  it('PM does NOT see Admin console but DOES see Integrations', () => {
    useStore.setState({ user: { ...baseUser, role: 'PM' } })
    renderTopBar()
    openMenu()
    expect(screen.queryByText('Admin console')).not.toBeInTheDocument()
    expect(screen.getByText('Integrations')).toBeInTheDocument()
  })

  it('PJM does NOT see Admin console', () => {
    useStore.setState({ user: { ...baseUser, role: 'PJM' } })
    renderTopBar()
    openMenu()
    expect(screen.queryByText('Admin console')).not.toBeInTheDocument()
  })

  it('LEADERSHIP sees no admin/system links (menu shows only sign out)', () => {
    useStore.setState({ user: { ...baseUser, role: 'LEADERSHIP' } })
    renderTopBar()
    openMenu()
    expect(screen.queryByText('Admin console')).not.toBeInTheDocument()
    expect(screen.queryByText('Integrations')).not.toBeInTheDocument()
    expect(screen.queryByText('Agent audit log')).not.toBeInTheDocument()
    expect(screen.getByText('Sign out')).toBeInTheDocument()
  })

  it('ENGINEERING does NOT see Integrations', () => {
    useStore.setState({ user: { ...baseUser, role: 'ENGINEERING' } })
    renderTopBar()
    openMenu()
    expect(screen.queryByText('Integrations')).not.toBeInTheDocument()
  })

  it('menu is closed by default (Sign out hidden until avatar clicked)', () => {
    useStore.setState({ user: { ...baseUser, role: 'ADMIN' } })
    renderTopBar()
    expect(screen.queryByText('Sign out')).not.toBeInTheDocument()
  })
})

describe('TopBar alerts bell gating', () => {
  it('ADMIN sees the alerts bell', () => {
    useStore.setState({ user: { ...baseUser, role: 'ADMIN' } })
    renderTopBar()
    expect(screen.getByLabelText('Alert center')).toBeInTheDocument()
  })

  it('LEADERSHIP (no alerts access) does not see the bell', () => {
    useStore.setState({ user: { ...baseUser, role: 'LEADERSHIP' } })
    renderTopBar()
    expect(screen.queryByLabelText('Alert center')).not.toBeInTheDocument()
  })
})
