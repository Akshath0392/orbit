import axios from 'axios'
import { useStore } from '../app/store'

export const api = axios.create({ baseURL: '/api/v1' })

// Snapshot mode: when the page is loaded headlessly by the Playwright sidecar
// (`?snapshot=1&token=<jwt>`), use the URL-supplied JWT instead of the store.
// This avoids having to seed zustand's persisted localStorage shape from the sidecar.
function snapshotToken(): string | null {
  if (typeof window === 'undefined') return null
  const sp = new URLSearchParams(window.location.search)
  if (sp.get('snapshot') !== '1') return null
  return sp.get('token')
}

api.interceptors.request.use((config) => {
  const token = snapshotToken() ?? useStore.getState().user?.token
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (res) => res,
  (err) => {
    // In snapshot mode the user isn't logged in via the store, so don't clobber it on 401.
    if (err.response?.status === 401 && !snapshotToken()) {
      useStore.getState().setUser(null)
    }
    return Promise.reject(err)
  }
)
