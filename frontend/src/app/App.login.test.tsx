import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App'
import { useStore } from './store'

vi.mock('../api/client', () => ({
  api: {
    get: vi.fn().mockResolvedValue({ data: {} }),
    post: vi.fn(),
    interceptors: { request: { use: vi.fn() } },
  },
}))

// Import after mock so we get the mocked version
import { api } from '../api/client'

function renderApp(initialPath = '/login') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[initialPath]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

const mockUser = {
  id: 1, name: 'Admin User', email: 'admin@orbit.io',
  role: 'ADMIN', initials: 'AD', avatarColor: '#087f7a',
}

beforeEach(() => {
  useStore.setState({ user: null })
  vi.clearAllMocks()
})

describe('Login form', () => {
  it('renders the login form', () => {
    renderApp()
    expect(screen.getByPlaceholderText('you@company.io')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('••••••••')).toBeInTheDocument()
    expect(screen.getByText('Sign in →')).toBeInTheDocument()
  })

  it('successful login sets user and token in store', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({
      data: { user: mockUser, token: 'test-jwt-token' },
    })

    renderApp()

    fireEvent.change(screen.getByPlaceholderText('you@company.io'), {
      target: { value: 'admin@orbit.io' },
    })
    fireEvent.change(screen.getByPlaceholderText('••••••••'), {
      target: { value: 'gauge123' },
    })
    fireEvent.click(screen.getByText('Sign in →'))

    await waitFor(() => {
      const user = useStore.getState().user
      expect(user?.token).toBe('test-jwt-token')
      expect(user?.email).toBe('admin@orbit.io')
      expect(user?.role).toBe('ADMIN')
    })
  })

  it('calls the login endpoint with email and password', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({
      data: { user: mockUser, token: 'tok' },
    })

    renderApp()

    fireEvent.change(screen.getByPlaceholderText('you@company.io'), {
      target: { value: 'priya@orbit.io' },
    })
    fireEvent.change(screen.getByPlaceholderText('••••••••'), {
      target: { value: 'secret' },
    })
    fireEvent.click(screen.getByText('Sign in →'))

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/auth/login', {
        email: 'priya@orbit.io',
        password: 'secret',
      })
    })
  })

  it('shows error message on failed login', async () => {
    vi.mocked(api.post).mockRejectedValueOnce(new Error('401'))

    renderApp()
    fireEvent.click(screen.getByText('Sign in →'))

    await waitFor(() => {
      expect(screen.getByText('Invalid email or password')).toBeInTheDocument()
    })
  })

  it('does not set user in store on failed login', async () => {
    vi.mocked(api.post).mockRejectedValueOnce(new Error('401'))

    renderApp()
    fireEvent.click(screen.getByText('Sign in →'))

    await waitFor(() => {
      expect(screen.getByText('Invalid email or password')).toBeInTheDocument()
    })
    expect(useStore.getState().user).toBeNull()
  })

  it('unauthenticated access to / redirects to /login', () => {
    useStore.setState({ user: null })
    renderApp('/')
    expect(screen.getByPlaceholderText('you@company.io')).toBeInTheDocument()
  })
})
