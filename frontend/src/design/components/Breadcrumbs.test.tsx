import { describe, it, expect } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom'
import { Breadcrumbs } from './Breadcrumbs'
import { ThemeProvider } from '../ThemeContext'

const LocationProbe = () => <div data-testid="loc">{useLocation().pathname}</div>

const renderTrail = () => render(
  <ThemeProvider>
    <MemoryRouter initialEntries={['/accounts/1']}>
      <LocationProbe />
      <Routes>
        <Route path="*" element={
          <Breadcrumbs items={[
            { label: 'Orbitter', to: '/radar' },
            { label: 'Collections POD' },
            { label: 'Atlas Bank' },
          ]} />
        } />
      </Routes>
    </MemoryRouter>
  </ThemeProvider>
)

describe('Breadcrumbs', () => {
  it('renders every crumb with separators', () => {
    renderTrail()
    expect(screen.getByText('Orbitter')).toBeTruthy()
    expect(screen.getByText('Collections POD')).toBeTruthy()
    expect(screen.getByText('Atlas Bank')).toBeTruthy()
    expect(screen.getAllByText('／')).toHaveLength(2)
  })

  it('navigates on linked crumbs and keeps the last crumb plain', () => {
    renderTrail()
    // last crumb is the current page — plain text, not a button
    expect(screen.getByText('Atlas Bank').tagName).not.toBe('BUTTON')
    // middle crumb has no `to` — also not clickable
    expect(screen.getByText('Collections POD').tagName).not.toBe('BUTTON')
    // first crumb navigates via its explicit route
    fireEvent.click(screen.getByText('Orbitter'))
    expect(screen.getByTestId('loc').textContent).toBe('/radar')
  })
})
