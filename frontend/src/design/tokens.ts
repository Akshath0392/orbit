// Static light-mode tokens exported as C — used directly by tests and as the
// ThemeContext default value. Runtime components should use useC() from
// ThemeContext so they respond to theme changes.
export { lightC as C } from './theme'
export type { Colors } from './theme'
