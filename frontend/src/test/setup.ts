import '@testing-library/jest-dom/vitest'

// Zustand's persist middleware calls localStorage — jsdom needs a working implementation
const storageMock = (() => {
  let store: Record<string, string> = {}
  return {
    getItem:    (key: string) => store[key] ?? null,
    setItem:    (key: string, value: string) => { store[key] = String(value) },
    removeItem: (key: string) => { delete store[key] },
    clear:      () => { store = {} },
    get length() { return Object.keys(store).length },
    key:        (i: number) => Object.keys(store)[i] ?? null,
  }
})()

Object.defineProperty(window, 'localStorage', { value: storageMock, writable: true })
