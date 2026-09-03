import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Feature } from './featureFlags'

vi.mock('../api/client', () => ({
  api: {
    get: vi.fn(() => Promise.resolve({
      data: { 'screen.uat': false, 'screen.cr': true },
    })),
  },
}))

function wrap(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
}

describe('Feature flag gating', () => {
  it('shows children for a flag the backend reports as on', async () => {
    wrap(<Feature flag="screen.cr"><div>cr-content</div></Feature>)
    await waitFor(() => expect(screen.getByText('cr-content')).toBeTruthy())
  })

  it('hides children for a flag the backend reports as off', async () => {
    wrap(<Feature flag="screen.uat"><div>uat-content</div></Feature>)
    await waitFor(() => expect(screen.queryByText('uat-content')).toBeNull())
  })

  it('renders the fallback when the flag is off', async () => {
    wrap(
      <Feature flag="screen.uat" fallback={<div>held-back</div>}>
        <div>uat-content</div>
      </Feature>
    )
    await waitFor(() => expect(screen.getByText('held-back')).toBeTruthy())
  })

  it('defaults unknown flags to visible', async () => {
    wrap(<Feature flag="section.never.registered"><div>always-on</div></Feature>)
    await waitFor(() => expect(screen.getByText('always-on')).toBeTruthy())
  })
})
