import ReactDOM from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import App from './app/App'
import { ErrorBoundary } from './app/ErrorBoundary'

console.log('[Orbit] main.tsx loading')

const qc = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error: any) => {
        const status = error?.response?.status
        if (status >= 400 && status < 500) return false
        return failureCount < 2
      },
      staleTime: 30_000,
    },
  },
})

console.log('[Orbit] QueryClient created, mounting React...')

const rootEl = document.getElementById('root')
console.log('[Orbit] root element:', rootEl)

ReactDOM.createRoot(rootEl!).render(
  <BrowserRouter>
    <ErrorBoundary>
      <QueryClientProvider client={qc}>
        <App />
      </QueryClientProvider>
    </ErrorBoundary>
  </BrowserRouter>
)

console.log('[Orbit] render() called')
