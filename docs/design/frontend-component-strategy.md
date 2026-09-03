# Frontend Component Strategy

**Since:** 2026-07-08 · **Plan:** [`docs/plan/controlled-release-and-frontend-foundation-plan.md`](../plan/controlled-release-and-frontend-foundation-plan.md)
**Reference implementation:** `src/features/alerts/AlertsPage.tsx`

Three conventions every screen follows: compose from the design system (don't hand-roll layout), be responsive via the shared primitives, and gate unreleased work with feature flags.

---

## 1. Component layers

```
atoms       src/design/components/   Btn, Input, Select, Badge, Card, StatCard,
                                     THead, Pagination, Modal, Tabs, PageState…
composites  src/design/components/   PageHeader, StatGrid, FilterBar, TableWrap, StatusPill
pages       src/features/<screen>/   compose the above; own only their data + domain markup
```

### Composites (use these instead of hand-rolled layout)

| Component | Use for | Replaces |
|---|---|---|
| `PageHeader` | title + subtitle + right-aligned actions | hand-rolled header flex rows (stacks on mobile) |
| `StatGrid` | stat tiles row at the top of a page | `repeat(4,1fr)` grids (auto-wraps on mobile) |
| `FilterBar` | rows of selects/inputs/buttons | ad-hoc `display:flex; gap:8` (wraps on mobile) |
| `TableWrap` | card chrome around a `<table>`, `footer` for `<Pagination>` | white-card divs (adds horizontal scroll on mobile) |
| `StatusPill` | any domain status/severity/RAG chip | per-page `Record<status, color>` maps |
| `SectionHeader` | divider heading between stacked dashboard sections (+ optional `InfoDot`, actions slot) | hand-rolled section titles |
| `InfoDot` | inline ⓘ metric-definition popover | tooltips / wiki links |
| `MatrixTable` | entity-columns × category-rows count matrices (sticky first col, totals, quiet zeros, clickable cells) | bespoke matrix markup |
| `RankTile` | POD benchmark card (mock `.pod-bm`): rank chip + `ScoreRing`, dashed metric rows, `lead` glow, `grid3` single-POD re-flow | — (new, AM dashboard) |
| `ScorecardTile` | client tile (mock `.cli-tile`): 19px name + health chip, 2×2 mini metric boxes, linkish footer | — (new, AM dashboard) |
| `ScoreRing` | mock `.hs-ring` — circle with 4px RAG border, value + tiny caption inside (`sm` 58px / `lg` 74px) | — (new, AM dashboard) |
| `HealthRing` | 0–100 SVG progress-arc ring (use `ScoreRing` for the mock's plain-border rings) | hardcoded `>= 80 ? green` rings |
| `GroupedBars` | mock `.gb-chart`/`.gb-big` — amber/green paired bars, value printed on every bar, optional single series + `scaleMax` | chart libraries (banned) |
| `DonutChart` | mock `.pie-wrap` — 218px conic donut with the dashed-row legend beside it, drillable except 'Others' | chart libraries (banned) |
| `SegmentBar` | proportional multi-segment bar (backlog aging, CSAT splits) | bespoke stacked-bar markup |
| `TrendBars` | per-month labelled value bars, current-month highlight | — (new, DH metric cards) |
| `MetricCard` | delivery-health metric card (headline + RAG + trend + formula + ⓘ + drill; `pending` = awaiting-feed variant) | — (new, client master page) |
| `MilestoneList` | done/upcoming milestone rows | — (new, client master page) |

`MatrixTable` also takes `columnSubs` (per-column second header line — used for SLA % in stage columns).

**Mock fidelity rule (2026-07-14):** widgets implemented from an approved HTML mock must replicate the mock's exact CSS — grid templates, paddings, font sizes, series colours (prod chart: Created = amber, Resolved = green), conditional layouts (single-POD `.bm-grid` re-flow) — not approximate it through generic composite defaults. Extract the mock's class rules verbatim before styling; SLA header tone follows the mock: ≥85% green · ≥70% amber · else red.

### Theme (2026-07-14, V3)

Palette values in `design/theme.ts` follow the approved V3 dark-indigo mock (`resouces/orbit-preview-1.html`): **dark is the default theme** (`darkC` = the mock's `:root` verbatim, accent `#5b7cfa`), light is the derived indigo-light (`lightC`, accent `#4a68e0`). **Green/amber/red are reserved for RAG/status only** — never as accents or decoration; chart series colours come from the sanctioned `PIE_PAL` (no greens). Snapshot/report renders are always light (`?snapshot=1` forces it in index.html and ThemeContext) — print surfaces stay paper-white. `ThemeContext` is the ONLY theme state (localStorage `orbit-theme`) — never re-add theme to the Zustand store. Card radii come from `R` (`lg` 18 / `sm` 13), shadows from `C.shadow`/`C.shadowSm`. Hardcoded hex in pages broke dark mode once already (~279 replaced) — new code uses `C` tokens exclusively; the only sanctioned literals are `#fff` text on coloured fills, rgba shadows, `PIE_PAL`, SVG logo internals, and agentColorMap brand colours.

### Rules

- **No page-local status→color maps.** Extend the `TONE` table in `StatusPill.tsx` when a new domain status appears.
- **No hand-rolled page headers, stat rows, filter rows, or table cards.** If a page needs a variant a composite can't express, extend the composite (add a prop), don't fork it inline.
- **Buttons/inputs/selects are always the atoms** (`Btn`, `Input`, `Select`) — never raw styled elements.
- New repeated pattern across 2+ pages → promote it to a composite in `src/design/components/`.

---

## 2. Responsiveness

- Breakpoints live in `src/design/useBreakpoint.ts`: `mobile` < 640px ≤ `tablet` < 1024px ≤ `desktop`. Branch with `useBreakpoint()` / `useIsMobile()`; never read `window.innerWidth` in a page.
- Composites are responsive by construction — a page that composes them inherits most of its mobile behaviour.
- Page-level responsibilities: collapse multi-column grids (`gridTemplateColumns: mobile ? '1fr' : '1fr 340px'`), reduce page padding (`mobile ? '16px 14px' : '22px 24px'`).
- The Shell handles chrome: desktop keeps the sticky sidebar + inner scroll; mobile gets a top bar + drawer and **natural document scroll** (WebView-friendly; same layout snapshot mode uses). The copilot panel is desktop-only for now.

---

## 3. Controlled release (feature flags)

Managed in the **Features Control Center** (`/flags`, ADMIN only). Backend: `feature_flags` table, `GET /api/v1/feature-flags/effective`.

- Flags are **independent of roles** — role mapping answers "may this role use it", flags answer "is it released, and to whom". Both gates apply.
- Audience: `ALL` → everyone · `PILOT` → listed emails only · `NONE` → hidden. **ADMINs always see everything.**
- **Unknown key = visible.** Only create a flag to hold a feature back; delete the flag to release it permanently.

Key convention and how to gate:

| Key | Gates | How |
|---|---|---|
| `screen.<navId>` | sidebar item + route | automatic — Sidebar filters items, Shell redirects the route to `/radar` |
| `section.<page>.<name>` | a component inside a page | wrap in `<Feature flag="section.alerts.stats">…</Feature>` |

Pilot workflow: ship the code to main with a flag at `NONE` → flip to `PILOT` + pilot emails → widen to `ALL` → delete the flag.

Known trade-off: flags load asynchronously on first paint, so a held-back section can flash briefly before hiding. Accepted for an internal tool.

---

## 4. Migration checklist (apply when touching any page)

- [ ] Header → `PageHeader` (filters go in `actions` via `FilterBar`)
- [ ] Stat tiles → `StatGrid`
- [ ] Table card → `TableWrap` (+ `footer={<Pagination/>}`)
- [ ] Status chips → `StatusPill`; delete the page-local color map
- [ ] Raw buttons/inputs/selects → `Btn`/`Input`/`Select`
- [ ] Multi-column grids collapse on `useIsMobile()`
- [ ] Page padding shrinks on mobile

### Migration status

| Screen | Status |
|---|---|
| Alerts | ✅ reference implementation |
| Features Control Center | ✅ built on composites |
| All others | ⬜ migrate opportunistically when touched |
