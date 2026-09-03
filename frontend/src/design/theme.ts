// Orbit palette — dark-first indigo design language, adopted 2026-07-14 from
// the approved V3 interactive mock (resouces/orbit-preview-1.html :root tokens).
// Token KEYS are stable API — pages reference C.<key>, never hex.
// "indigo" is the primary accent. Green/amber/red are reserved for RAG/status
// only — never use them as accents or decoration.
//
// darkC carries the mock's exact values and is the DEFAULT theme; lightC is
// the derived indigo-light used by the toggle and by snapshot/report renders
// (print surfaces always render light).

// Light — derived from the dark mock: white surfaces, same indigo accent
// family deepened for contrast on white.
export const lightC = {
  navy:       "#1b2333",   // ink — brand-dark surfaces & headings
  navyMid:    "#242e40",
  navyLight:  "#303c52",
  indigo:     "#4a68e0",   // primary accent (mock --teal-2, readable on white)
  indigoHov:  "#3d57c4",
  indigoPale: "#e8edfc",
  indigoFaint:"#eef1fa",   // metric mini-boxes (mock --teal-soft-2)
  mint:       "#eef1fb",   // soft section tint
  mintFaint:  "#f5f7fd",
  canvas:     "#f6f7fa",   // --bg
  white:      "#ffffff",   // --surface
  border:     "#e6e9ee",   // --line
  borderMed:  "#dbe0e8",   // --line-2
  text:       "#1b2333",   // --ink
  sub:        "#5b6472",   // --ink-2
  muted:      "#8a929f",   // --ink-3
  red:        "#cf4436",
  redPale:    "#fbeeec",
  redDeep:    "#b8362a",   // --red-ink
  amber:      "#c5871b",
  amberPale:  "#faf3e3",
  amberDeep:  "#a06d10",   // --amber-ink
  green:      "#1e9e6a",
  greenPale:  "#e9f6f0",
  greenDeep:  "#17835a",   // --green-ink
  purple:     "#7d5bbf",
  purplePale: "#f1ecfa",
  purpleDeep: "#5f3fa0",
  blue:       "#3667e0",
  bluePale:   "#ecf1fc",
  blueDeep:   "#274eb2",
  teal:       "#4a68e0",   // historical alias of the accent — same as indigo
  tealPale:   "#e8edfc",
  tealDeep:   "#3d57c4",
  shadow:     "0 1px 2px rgba(20,25,40,.05), 0 4px 16px rgba(20,25,40,.06)",
  shadowSm:   "0 1px 2px rgba(20,25,40,.06)",
}

// Colors is a plain string map — not tied to literal hex values so dark theme can use different values
export type Colors = { [K in keyof typeof lightC]: string }

// Dark — the V3 mock's :root values verbatim (default theme).
export const darkC: Colors = {
  navy:       "#0b0e13",
  navyMid:    "#131822",
  navyLight:  "#1c2430",
  indigo:     "#5b7cfa",   // --teal (accent; var name is historical in the mock too)
  indigoHov:  "#4a68e0",   // --teal-2
  indigoPale: "#1e2843",   // --teal-soft
  indigoFaint:"#1a2233",   // --teal-soft-2 (metric mini-boxes)
  mint:       "#1b2334",   // --mint
  mintFaint:  "#161d2a",   // --mint-2
  canvas:     "#0e1116",   // --bg
  white:      "#161b23",   // --surface
  border:     "#252c37",   // --line
  borderMed:  "#323b49",   // --line-2
  text:       "#e8ecf2",   // --ink
  sub:        "#aab4c2",   // --ink-2
  muted:      "#8791a0",   // --ink-3
  red:        "#e0574a",
  redPale:    "#331713",   // mock .hs-r / .t-r chip background
  redDeep:    "#ef8177",   // --red-ink
  amber:      "#d99a2b",
  amberPale:  "#2e2410",   // mock .hs-a / .t-a chip background
  amberDeep:  "#e6b054",   // --amber-ink
  green:      "#27a56f",
  greenPale:  "#123324",   // mock .hs-g / .t-g chip background
  greenDeep:  "#4fc890",   // --green-ink
  purple:     "#9a6ff0",
  purplePale: "#241a3d",
  purpleDeep: "#c3a8f7",
  blue:       "#58a6ff",
  bluePale:   "#14213a",
  blueDeep:   "#9ccaff",
  teal:       "#5b7cfa",   // alias of the accent
  tealPale:   "#1e2843",
  tealDeep:   "#8fa6ff",   // --teal-ink
  shadow:     "0 1px 2px rgba(0,0,0,.3), 0 4px 16px rgba(0,0,0,.35)",
  shadowSm:   "0 1px 2px rgba(0,0,0,.35)",
}

// Corner radii from the mock (--radius / --radius-sm). Cards & panels use lg,
// inner elements (inputs, chips, nested tiles) use sm.
export const R = { lg: 18, sm: 13 } as const

// Categorical palette for donut/owner-share charts (mock PIE_PAL) — contains
// no greens by design (green = RAG only). Works on both themes.
export const PIE_PAL = [
  "#5b7cfa", "#58a6ff", "#d99a2b", "#e0574a", "#9a6ff0",
  "#c76fd1", "#3fa7b8", "#a8834a", "#e888a0", "#7c8ba8",
] as const
