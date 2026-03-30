# Datapulse Frontend Design Direction

## Core direction

Datapulse frontend follows a **Cursor IDE-inspired** design language:
**light theme only** (MVP), minimalistic, dense, professional, desktop-first, editor-like.
Dark theme is not planned for initial release and may be added later.

The goal is not to copy Cursor literally, but to achieve the same **feeling**:
a high-quality operational workspace with compact controls, clean typography,
subtle borders, strong layout hierarchy, contextual side panels, and low visual noise.

The user opens Datapulse and works in it all day — like a developer works in an IDE.

## Product purpose

The UI must prioritize operational usability for data-heavy workflows:
- Dense data grids with filtering, sorting, and inline editing
- Contextual detail panels with drill-down and explainability
- Tabbed workspaces for parallel data views
- Task-oriented queues and journals
- Summary KPI strips with drill-down into underlying data
- Status-driven action pipelines

Specific screens and their content will be defined per module. This document
describes the abstract visual language and interaction patterns that all screens share.

The product should feel like a serious daily work tool, not a decorative dashboard.

---

## UX principles

1. Dense information layout over oversized cards
2. Desktop-first workflows over mobile-first simplification
3. One main operational workspace over scattered screens
4. Clear status semantics over visual effects
5. Explainability visible near decisions
6. Context panels over navigation chaos
7. Tables/grids first for operational work
8. Subtle visual hierarchy, low noise
9. Fast repeatable daily workflows
10. Consistent state language across pricing, execution and reconciliation

## Explicit anti-goals

Do not build:
- glossy marketing SaaS visuals
- oversized dashboard cards as the primary UI pattern
- whitespace-heavy layouts
- decorative animations
- chart-first UX for operational tasks
- hidden critical state behind hover-only interactions
- mobile-first compromises that damage dense desktop workflows
- skeleton screens on every component (only on initial load of primary content)

---

## Shell layout (application frame)

Datapulse uses a Cursor-inspired shell with four persistent zones:

```
┌─────────────────────────────────────────────────────────────┐
│  Top Bar: workspace switcher · breadcrumbs · search · user  │
├────┬────────────────────────────────────────────┬───────────┤
│    │                                            │           │
│ A  │              Main Area (B)                 │  Detail   │
│ c  │                                            │  Panel    │
│ t  │  tabs · grid / table / form / chart        │  (C)     │
│ i  │                                            │           │
│ v  │                                            │  slide-in │
│ i  │                                            │  from     │
│ t  │                                            │  right    │
│ y  │                                            │           │
│    ├────────────────────────────────────────────┤           │
│ B  │  Bottom Panel (optional)                   │           │
│ a  │  notifications · bulk actions bar          │           │
│ r  │                                            │           │
├────┴────────────────────────────────────────────┴───────────┤
│  Status Bar: data freshness · sync status · active account  │
└─────────────────────────────────────────────────────────────┘
```

### Zone descriptions

| Zone | Cursor analogy | Datapulse purpose |
|------|---------------|-------------------|
| **Top Bar** | Title bar + menu | Workspace switcher, breadcrumbs, global search (Ctrl+K), user menu |
| **Activity Bar** | Left icon bar | Module navigation: one icon per top-level module |
| **Main Area** | Editor area + tabs | Active view content — grid, tables, forms. Multiple tabs for open views |
| **Detail Panel** | Side panel (right) | Contextual detail for selected entity: breakdown, explanation, history. Slide-in, resizable, closeable |
| **Bottom Panel** | Terminal / output | Bulk action bar, notification feed (collapsible) |
| **Status Bar** | Status bar | Data freshness indicators, last sync times, active workspace/account, connection health |

### Layout behavior

- Activity Bar is always visible (~48px wide, icons only, tooltip on hover).
- Main Area fills available space. No fixed sidebar with permanent navigation tree — use Activity Bar + tabs instead.
- Detail Panel opens on demand (row click, "explain" action) and pushes Main Area left (does not overlay). Default width ~400px, resizable with drag handle.
- Bottom Panel collapses to zero height when inactive. Expands for bulk operations or notification review.
- Status Bar is always visible (~24px), compact, one-line.
- Minimum supported viewport: 1280×720.

---

## Navigation model

### Primary navigation: Activity Bar

Vertical icon bar (left edge). Each icon = a top-level module.
Exact module list will be defined when page structure is finalized.

- Each module gets a distinct icon. 4–6 modules expected.
- Active module is highlighted with a vertical accent bar (left edge of icon).
- Settings/admin module always at the bottom of the bar, separated from operational modules.

### Secondary navigation: Tabs

Within a module, open views appear as tabs in the Main Area (like editor tabs in Cursor).
- Tabs are closeable, reorderable (drag).
- "Pinned" tabs stick to the left.
- Tab overflow → horizontal scroll with chevron arrows.
- Double-click a tab to rename a custom view.
- Each module defines its own default and user-created tabs.

### Tertiary navigation: Breadcrumbs

Displayed in Top Bar below workspace switcher. Shows current path:
`Module > View > Entity`

Breadcrumb segments are clickable. Depth: typically 2–3 levels.

### Quick access: Command Palette (Ctrl+K)

Global search and action palette. Matches:
- Domain entities by name, code, or identifier
- Saved views and workspaces
- System commands and actions

Visually: centered floating input with dropdown results (identical to Cursor's Ctrl+K).
Specific searchable entity types are defined per module.

---

## Authentication & onboarding

### Login

Datapulse delegates authentication to Keycloak (OAuth2). The login screen is Keycloak-hosted, styled to match Datapulse branding (logo, colors, typography). Datapulse itself has no custom login form.

Flow: user opens Datapulse → redirect to Keycloak login → authenticate → redirect back → land on workspace.

### Workspace selector (post-login)

After login, if the user has access to multiple workspaces, a **workspace selector** screen is shown before entering the shell.

- Each workspace card: name, summary stats (connection count, entity count).
- Single workspace → skip selector, go directly to shell.
- Last used workspace remembered (localStorage) → pre-selected on next login.

### Workspace switcher (in-app)

Dropdown in Top Bar (left corner, next to logo). Shows current workspace name.
Click → dropdown with workspace list + "Manage workspaces" link.
Switching workspace = full context reload (clear tabs, reset filters, load new data).

### First-run onboarding

New workspace with no data → guided setup flow instead of an empty workspace.

- Steps are displayed in the Main Area (no modal wizard). Activity Bar is visible but grayed out until setup completes.
- Validation happens inline (not on submit). Credential validation → immediate feedback.
- After initial setup, user can skip remaining steps and complete configuration later in Settings.
- Specific onboarding steps are defined per feature.

---

## Color system

### Light theme (primary)

Foundation: neutral grays with minimal accent.

| Token | Value | Usage |
|-------|-------|-------|
| `--bg-primary` | `#FFFFFF` | Main content background |
| `--bg-secondary` | `#F9FAFB` | Sidebar, secondary panels, alternate rows |
| `--bg-tertiary` | `#F3F4F6` | Hover states, panel headers |
| `--bg-active` | `#EFF6FF` | Selected row, active tab |
| `--border-default` | `#E5E7EB` | Borders, separators (1px) |
| `--border-subtle` | `#F3F4F6` | Inner cell dividers |
| `--text-primary` | `#111827` | Main text, headings |
| `--text-secondary` | `#6B7280` | Labels, meta info, timestamps |
| `--text-tertiary` | `#9CA3AF` | Placeholders, disabled |
| `--accent-primary` | `#2563EB` | Primary actions, links, active states |
| `--accent-primary-hover` | `#1D4ED8` | Button hover |
| `--accent-subtle` | `#EFF6FF` | Accent background (selected tab, active filter) |

### Semantic colors (both themes)

| Token | Value | Usage |
|-------|-------|-------|
| `--status-success` | `#059669` | Confirmed, synced, profitable |
| `--status-warning` | `#D97706` | Pending, stale, attention needed |
| `--status-error` | `#DC2626` | Failed, loss, critical alert |
| `--status-info` | `#2563EB` | Informational badges |
| `--status-neutral` | `#6B7280` | Skipped, archived, inactive |

### Financial colors

| Token | Value | Usage |
|-------|-------|-------|
| `--finance-positive` | `#059669` | Profit, positive margin, revenue |
| `--finance-negative` | `#DC2626` | Loss, negative margin, penalties |
| `--finance-zero` | `#6B7280` | Zero values, break-even |

### Dark theme — out of scope (MVP)

Dark theme is **not implemented** in MVP. Only light theme is supported.
Design tokens are structured as CSS custom properties on `:root`, so adding
a dark theme later is a token-swap operation without component changes.
When implemented: follow Cursor's dark palette — dark grays (#1E1E1E base),
not pure black. Same semantic colors with adjusted brightness for dark backgrounds.

---

## Typography

### Font stack

| Role | Font | Fallback |
|------|------|----------|
| **UI chrome** | Inter | system-ui, -apple-system, sans-serif |
| **Data / numbers** | JetBrains Mono | "Cascadia Code", "Fira Code", monospace |

Inter for all interface text: labels, buttons, headings, descriptions.
JetBrains Mono for tabular data, prices, quantities, percentages, codes, barcodes — anywhere monospace alignment matters.

### Type scale

| Token | Size | Weight | Usage |
|-------|------|--------|-------|
| `--text-xs` | 11px | 400 | Timestamps, meta, status bar |
| `--text-sm` | 13px | 400 | Table cells, secondary labels, breadcrumbs |
| `--text-base` | 14px | 400 | Body text, form inputs, primary labels |
| `--text-lg` | 16px | 600 | Section headings, tab labels |
| `--text-xl` | 20px | 600 | Page titles, KPI values |
| `--text-2xl` | 24px | 700 | Hero numbers (only in summary KPI cards) |

Default body: 14px. Grid cells: 13px. This matches Cursor's density.

Line-height: 1.4 for body, 1.2 for headings, 1.0 for single-line cells.

---

## Component patterns

### Data grid (primary component)

The operational grid is the central UI component — equivalent to the code editor in Cursor.

**Structure:**
- Toolbar: filters, sort, view controls, bulk actions, density toggle
- Column headers: sortable (click), resizable (drag), reorderable (drag)
- Rows: 32px default height (compact), 40px comfortable
- Cells: left-aligned text, right-aligned numbers
- Selection: checkbox column (first), shift+click for range, ctrl+click for toggle
- Frozen columns: checkbox + primary identifier always visible on horizontal scroll

**Row behavior:**
- Hover: `--bg-tertiary` background
- Selected: `--bg-active` background + left accent border (2px `--accent-primary`)
- Click on row → opens Detail Panel (right) with full entity context
- Double-click on editable cell → inline edit

**Column types:**
- Text: product name, barcode, category (left-aligned)
- Number: price, margin, stock, velocity (right-aligned, monospace, with sign coloring)
- Status: badge with semantic color + short label ("Profitable", "Pending", "Failed")
- Sparkline: 7-day trend mini-chart inline in cell (optional per column)
- Action: icon button in cell (context-dependent actions)

**Pagination:**
- Server-side pagination. 50 / 100 / 200 rows per page selector.
- "Showing 1–50 of 1,234" counter in toolbar.
- Page navigation: prev/next + page number input.

### Filter bar

Horizontal bar above grid. Filters appear as compact pills:

```
[Field: Value ×] [Field: Operator Value ×] [Status: Active ×]  [+ Add filter]  [⊘ Clear all]
```

- Each pill shows field name + operator + value. Click to edit inline dropdown.
- "Add filter" opens a dropdown with all available fields.
- Active filters are persisted per tab / saved view.
- Complex filters (range, multi-select) use a dropdown panel, not a modal.

### Detail Panel (right side panel)

Opens when user clicks a grid row or triggers "explain" / "detail" action.

**Layout:**
- Header: entity name + close button (×) + collapse button
- Tab strip within panel: context-dependent tabs (defined per entity type)
- Content: dense key-value pairs, mini tables, explanation blocks

**Sizing:**
- Default width: 400px. Resizable with drag handle. Min: 320px, Max: 50% of viewport.
- Panel pushes main content (no overlay). Like Cursor's side panel behavior.

### Status badges

Small, inline, pill-shaped. 

| Style | When |
|-------|------|
| Green dot + "Synced" | Data is fresh |
| Yellow dot + "Stale" | Data older than threshold |
| Red dot + "Failed" | Sync failed, action failed |
| Blue dot + "Pending" | Awaiting approval, in progress |
| Gray dot + "Skipped" | Not applicable, archived |

Dot is 6px circle. Label is `--text-xs` (11px). No large banners for status.

### Buttons

Follow Cursor's button hierarchy:

| Type | Style | When |
|------|-------|------|
| **Primary** | Filled `--accent-primary`, white text, 28px height | Main action per context (Save, Approve, Apply) |
| **Secondary** | Border `--border-default`, `--text-primary`, 28px height | Alternative actions (Cancel, Export) |
| **Ghost** | No border, `--text-secondary`, hover shows `--bg-tertiary` | Toolbar actions, less important actions |
| **Danger** | Filled `--status-error`, white text | Destructive actions (Delete, Reject) |
| **Icon button** | 24×24, icon only, ghost style | Compact actions in grid cells, toolbars |

All buttons: border-radius 6px. No shadows. No gradients.

### Form inputs

- Height: 32px (compact).
- Border: 1px `--border-default`. Focus: 2px `--accent-primary` ring (like Cursor).
- No floating labels. Label above input, `--text-sm`, `--text-secondary`.
- Number inputs: right-aligned, monospace font.
- Dropdowns: native feel, flat list with search for long lists.

### Cards (limited use)

Cards are used **only** for summary KPIs at the top of analytical views — not as a primary layout pattern.

```
┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  KPI Label   │  │  KPI Label   │  │  KPI Label   │  │  KPI Label   │
│  Value       │  │  Value       │  │  Value       │  │  Value       │
│  ↑ Δ trend   │  │  ↓ Δ trend   │  │  → Δ trend   │  │  ↑ Δ trend   │
└──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘
```

Max 4-6 KPI cards in a row. Below them — immediately the table/grid. No card-based layouts for operational data.

### Modals (rare)

Modals are used sparingly — only for:
- Destructive confirmations
- Multi-step creation wizards
- Bulk action confirmations

Everything else: inline editing, panels, dropdowns.

### Column configuration

Grid toolbar has a "Columns" button (icon: vertical bars). Opens a dropdown panel:

```
┌─ Columns ────────────────────┐
│ 🔍 Search columns...         │
│                               │
│ ☑ Primary column    (frozen) │
│ ☑ Column A                   │
│ ☑ Column B                   │
│ ☐ Column C                   │
│ ☐ Column D                   │
│ ☑ Status                     │
│                               │
│ [Reset to default]            │
└───────────────────────────────┘
```

- Checkbox toggles column visibility. Drag handle (left of checkbox) reorders columns.
- "Frozen" label on columns that cannot be hidden (primary identifier column).
- Search input at top for grids with many columns.
- Column configuration is part of Saved View state — switching view restores column set.
- "Reset to default" restores the module's default column set.

### Export

Export is available from grid toolbar ("Export" button) and context menu ("Export selection").

| Scope | Trigger | Behavior |
|-------|---------|----------|
| Current view (all pages) | Toolbar → Export | Exports full filtered dataset (server-side), not just visible page |
| Selected rows | Context menu → Export selection | Exports only checked rows |

Format: **CSV** (default, MVP). Excel (.xlsx) may be added later.

Flow:
1. Click Export → toast: "Preparing export..." (info, non-blocking).
2. Server generates file → browser download starts automatically.
3. Toast updates: "Export complete — 1,234 rows" with link to re-download.

Export respects current filters, sorting, and visible columns.
Large exports (>10,000 rows) → async: "Export is being prepared. You'll be notified when ready."

---

## Interaction patterns

### Keyboard-first

| Shortcut | Action |
|----------|--------|
| `Ctrl+K` | Command palette |
| `↑ / ↓` | Navigate grid rows |
| `Enter` | Open detail panel for selected row |
| `Escape` | Close detail panel / close modal |
| `Ctrl+S` | Save current view |
| `Ctrl+F` | Focus filter bar |
| `Tab` | Move between interactive elements |
| `Space` | Toggle checkbox on selected row |

### Inline editing

Editable cells switch to edit mode on double-click.
No modal forms for single-field edits. Save on blur or Enter. Cancel on Escape.

### Context menu (right-click)

On grid rows: Copy, Open in new tab, Export selection, and context-dependent domain actions.

### Bulk actions

When multiple rows are selected (checkboxes), a bottom bar slides up:

```
┌──────────────────────────────────────────────────────────┐
│ 12 items selected  [Approve all] [Reject all] [Export]  ×│
└──────────────────────────────────────────────────────────┘
```

### Drag interactions

- Column resize: drag column border
- Column reorder: drag column header
- Panel resize: drag panel edge
- Tab reorder: drag tab

No drag-and-drop for data manipulation (too error-prone for financial data).

---

## Data display conventions

### Numbers

- Currency: `1,290₽` (thousands separator, ₽ suffix, monospace)
- Percentage: `18.3%` (one decimal, monospace)
- Quantities: `1,234` (thousands separator, no decimals, monospace)
- Deltas: `↑ 8.2%` green / `↓ 2.1%` red / `→ 0.0%` gray

### Dates and times

- Relative for recency: "12 min ago", "3 hours ago", "yesterday"
- Absolute for historical: "Mar 28, 2026" (short month, no leading zeros)
- Timestamps: "Mar 28, 14:32" (24h format, no seconds)
- Never show raw ISO timestamps in UI

### Empty states

- Empty grid: centered illustration-free message. "No items match your filters." + [Clear filters] button.
- Empty panel: "Select a row to view details."
- No data yet: "No data synced yet. Check your connection settings." + [Go to Settings] link.

Tone: helpful, direct. No "Oops!" or playful copy.

### Loading states

- Initial page load: full-area skeleton (gray shimmer blocks matching expected layout).
- Data refresh: subtle top-edge progress bar (2px, `--accent-primary`), content stays visible.
- Row action in progress: spinner icon replaces action icon in that cell.
- No full-page spinners after initial load.

### Error states

| Situation | Pattern | Example |
|-----------|---------|---------|
| Form validation | Inline red text below field + red border | "API token is required" under empty token input |
| API error (user-recoverable) | Toast (error variant) with retry action | "Failed to save view. [Retry]" |
| API error (server-side) | Toast (error variant) with description | "Server error. Try again later." |
| Connection lost (WebSocket) | Persistent top banner (yellow) | "Connection lost. Reconnecting..." → auto-dismiss on reconnect |
| Connection lost (no internet) | Persistent top banner (red) | "No internet connection. Changes will not be saved." |
| Permission denied | Toast (error variant) | "You don't have permission to approve actions." |
| Stale data blocking action | Inline message near action button | "Cannot proceed: source data is stale." |
| 404 / not found | Full main area message | "Workspace not found." + [Go to workspace selector] |

Rules:
- Validation errors appear immediately on blur, not only on submit.
- Toast errors auto-dismiss after 8 seconds (longer than success toasts). Error toasts have manual dismiss button.
- Persistent banners (connection loss, automation blockers) do not auto-dismiss — they stay until condition resolves.
- Never show raw HTTP codes or stack traces. Map errors to human-readable messages.

### Confirmation & feedback

| Action type | Feedback | Style |
|-------------|----------|-------|
| Non-destructive save | Toast: "Saved" | Success toast, auto-dismiss 3s |
| Approve / confirm | Toast: "Approved" | Success toast, 3s |
| Reject / decline | Toast: "Rejected" | Neutral toast, 3s |
| Bulk action | Toast: "N items processed" | Success toast, 3s |
| Destructive action | Confirmation modal → Toast: "Deleted" | Danger modal → success toast |
| High-impact destructive | Confirmation modal (type-to-confirm) | Danger modal: type entity name |
| Async operation | Toast: "Started..." → completion notification | Info toast → browser download or notification |

Rules:
- Success toasts: 3 seconds, auto-dismiss, green left border.
- Info toasts: 3 seconds, auto-dismiss, blue left border.
- Error toasts: 8 seconds, manual dismiss available, red left border.
- Destructive actions always require confirmation modal.
- High-impact destructive actions (disconnect, delete workspace) require type-to-confirm.
- No confirmation for non-destructive saves (inline editing, filter changes).

---

## Data freshness and sync status

### Status Bar indicators

Always visible at the bottom. Compact, one-line:

```
● Source A synced 12 min ago   ● Source B synced 3 min ago   ● 2 stale endpoints
```

- Green dot: synced within threshold (default 1h)
- Yellow dot: approaching stale threshold
- Red dot: stale or failed

### Grid-level freshness

Column header shows freshness icon if source data is older than threshold:
- ⚠ icon next to column header
- Tooltip: "Data last updated N hours ago"

### Automation blockers

When data staleness blocks automation, a non-dismissible banner appears above the grid:

```
⚠ Automation paused: [source] data is N hours stale. Manual actions are still available.
```

Banner style: yellow background, `--text-primary` text, compact (32px height).

---

## Notification and alerts

### In-app notifications

Notification bell icon in Top Bar with unread count badge.
Dropdown panel (not page) shows recent notifications:
- Failed actions
- Completed syncs
- Anomaly alerts
- Approval requests

Each notification: icon + title + time + dismiss.

### WebSocket real-time updates

- Grid rows update in place (no full reload) when data changes.
- New items appear in lists/queues without page refresh.
- Status bar sync times update live.
- Subtle flash animation (background color pulse once) on updated rows — only when row is visible.

---

## Responsive behavior

### Desktop-first tiers

| Viewport | Behavior |
|----------|----------|
| ≥ 1440px | Full layout: Activity Bar + Main + Detail Panel side-by-side |
| 1280–1440px | Detail Panel overlays instead of pushing. Slightly narrower Activity Bar |
| < 1280px | Not officially supported. Show message: "Datapulse is designed for desktop screens (1280px+)" |

### No mobile version

Mobile access is explicitly out of scope. No responsive breakpoints below 1280px.
Future consideration: mobile read-only notification view (separate app, not this design).

---

## Technology choices (frontend stack)

| Concern | Choice | Rationale |
|---------|--------|-----------|
| Framework | **React 18+** | Component model, ecosystem, team familiarity |
| Language | **TypeScript** (strict) | Type safety for complex data models |
| Build | **Vite** | Fast dev server, modern bundling |
| State management | **TanStack Query** (server state) + **Zustand** (client state) | Server-state-first architecture, minimal boilerplate |
| Grid component | **TanStack Table** or **AG Grid Community** | Virtual scrolling, column control, sorting, filtering |
| Routing | **React Router v6** | Standard, supports nested layouts |
| Styling | **Tailwind CSS** | Utility-first matches dense UI, design tokens via CSS variables |
| Icons | **Lucide React** | Clean, minimal line icons (similar to VS Code Codicons style) |
| Charts | **Recharts** or **Lightweight Charts** (for sparklines) | Simple API, works with React |
| WebSocket | **STOMP over SockJS** (Spring-compatible) | Matches backend stack |
| Forms | **React Hook Form** + **Zod** | Validation, performance |
| Date formatting | **date-fns** | Tree-shakeable, locale support |
| Notifications | **Sonner** (toast) | Minimal, non-intrusive toast notifications |

### Not using

- CSS-in-JS (styled-components, emotion) — unnecessary complexity for utility-first approach
- Redux — too heavy for this use case, TanStack Query handles server state
- Material UI, Ant Design, Chakra — opinionated design systems that conflict with custom Cursor-like aesthetic
- Next.js — no SSR needed, this is a SPA operational tool

---

## Design token structure

All visual tokens are CSS custom properties on `:root`. Theme switching (future dark mode) = swap token values.

```
:root {
  /* Spacing */
  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-6: 24px;
  --space-8: 32px;

  /* Radii */
  --radius-sm: 4px;
  --radius-md: 6px;
  --radius-lg: 8px;

  /* Shadows (minimal — Cursor-like) */
  --shadow-sm: 0 1px 2px rgba(0,0,0,0.05);
  --shadow-md: 0 4px 12px rgba(0,0,0,0.08);

  /* Transitions */
  --transition-fast: 150ms ease;
  --transition-normal: 200ms ease;
}
```

No elevation system. No 5-level shadow scale. Shadows used only on:
- Command palette overlay
- Dropdown menus
- Modals

Everything else: 1px borders for separation. Background shade differences for hierarchy.

---

## Accessibility baseline

- WCAG 2.1 AA contrast ratios for all text.
- All interactive elements keyboard-focusable with visible focus ring.
- `aria-label` on icon-only buttons.
- Grid rows navigable with arrow keys.
- Screen reader support for status badges (text alternative, not just color).
- No information conveyed by color alone (always color + text/icon).

---

## Localization (i18n)

### Language: Russian only (MVP)

MVP ships with Russian-only UI. All labels, messages, tooltips, empty states, error messages — in Russian.

English localization is not planned for initial release but the architecture should not prevent it:
- All user-facing strings extracted into a single locale file (not hardcoded in components).
- Use `react-i18next` for string externalization from day one, even if only `ru` locale exists.
- Keys in English, values in Russian: `{ "grid.empty": "Нет данных, соответствующих фильтрам." }`.

### Number and date formatting

| Format | Convention | Example |
|--------|-----------|---------|
| Thousands separator | Space (Russian standard) | `1 290 ₽` |
| Decimal separator | Comma | `18,3%` |
| Currency symbol | ₽ suffix with space | `1 290 ₽` |
| Dates | Russian short month | `28 мар 2026` |
| Relative time | Russian | `12 мин назад`, `вчера` |

Use `date-fns/locale/ru` for date formatting. Number formatting via `Intl.NumberFormat('ru-RU')`.

### Domain terms — no translation

Marketplace-specific and domain terms stay in original language where standard:
- SKU, P&L, COGS, FBO, FBS — English abbreviations (industry standard)
- WB, Ozon — as-is
- "Wildberries", "Ozon" — not translated

---

## Related documents

- [Seller Operations module](../modules/seller-operations.md) — grid, views, queues, journals
- [Analytics & P&L module](../modules/analytics-pnl.md) — P&L formula, star schema, drill-down
- [Pricing module](../modules/pricing.md) — decision pipeline, explanation, policies
- [Execution module](../modules/execution.md) — action lifecycle, reconciliation
- [Project Vision & Scope](../project-vision-and-scope.md) — delivery phases, constraints
