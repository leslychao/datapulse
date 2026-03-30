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
- Seller Operations Grid
- Saved Views
- Working Queues
- Journals (Price, Promo)
- Mismatch Monitor
- Explanation panels
- Pricing recommendations and decisions
- P&L drill-down
- Execution / reconciliation states

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
| **Activity Bar** | Left icon bar | Module navigation: Operations, Analytics, Pricing, Execution, Settings |
| **Main Area** | Editor area + tabs | Active view content — grid, tables, forms. Multiple tabs for open views |
| **Detail Panel** | Side panel (right) | Contextual detail: SKU explanation, decision breakdown, P&L drill-down. Slide-in, resizable, closeable |
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

| Icon | Module | Primary screens |
|------|--------|----------------|
| Grid icon | **Operations** | Operational Grid, Saved Views, Working Queues |
| Chart icon | **Analytics** | P&L overview, Inventory, Returns & Penalties |
| Tag icon | **Pricing** | Policies, Decisions, Price Journal |
| Play icon | **Execution** | Actions, Reconciliation, Failed queue |
| Gear icon | **Settings** | Accounts, Connections, Team, Billing |

Active module is highlighted with a vertical accent bar (left edge of icon).

### Secondary navigation: Tabs

Within a module, open views appear as tabs in the Main Area (like editor tabs in Cursor).
- Tabs are closeable, reorderable (drag).
- "Pinned" tabs stick to the left.
- Tab overflow → horizontal scroll with chevron arrows.
- Double-click a tab to rename a custom view.

Examples:
- Operations module → tabs: "All SKUs", "Low margin", "Price review queue", "WB Ozon comparison"
- Analytics module → tabs: "P&L March 2026", "Inventory overview", "Returns deep-dive"

### Tertiary navigation: Breadcrumbs

Displayed in Top Bar below workspace switcher. Shows current path:
`Operations > Saved View: Low margin > SKU #28491`

Breadcrumb segments are clickable.

### Quick access: Command Palette (Ctrl+K)

Global search and action palette. Matches:
- SKU by name, barcode, article
- Saved views by name
- Working queue items
- Commands: "Run pricing", "Export view", "Switch workspace"

Visually: centered floating input with dropdown results (identical to Cursor's Ctrl+K).

---

## Authentication & onboarding

### Login

Datapulse delegates authentication to Keycloak (OAuth2). The login screen is Keycloak-hosted, styled to match Datapulse branding (logo, colors, typography). Datapulse itself has no custom login form.

Flow: user opens Datapulse → redirect to Keycloak login → authenticate → redirect back → land on workspace.

### Workspace selector (post-login)

After login, if the user has access to multiple workspaces, a **workspace selector** screen is shown before entering the shell.

```
┌─────────────────────────────────────────┐
│              DataPulse                  │
│                                         │
│  Select workspace                       │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ 🏢  My Brand (WB + Ozon)       │    │
│  │     3 connections · 1,204 SKUs  │    │
│  └─────────────────────────────────┘    │
│  ┌─────────────────────────────────┐    │
│  │ 🏢  Partner Store (Ozon)       │    │
│  │     1 connection · 312 SKUs     │    │
│  └─────────────────────────────────┘    │
│                                         │
│  [+ Create workspace]                   │
└─────────────────────────────────────────┘
```

- Each workspace card: name, marketplace icons, connection count, SKU count.
- Single workspace → skip selector, go directly to shell.
- Last used workspace remembered (localStorage) → pre-selected on next login.

### Workspace switcher (in-app)

Dropdown in Top Bar (left corner, next to logo). Shows current workspace name.
Click → dropdown with workspace list + "Manage workspaces" link.
Switching workspace = full context reload (clear tabs, reset filters, load new data).

### First-run onboarding (SC-1)

New workspace with no connections → guided setup instead of empty grid:

```
Step 1: "Connect your first marketplace"
        [Wildberries]  [Ozon]

Step 2: "Enter API credentials"
        Token: [________________]  [Validate]
        ✓ Connection successful · 1,204 products found

Step 3: "Initial sync started"
        Progress: ████████░░ 80% — catalog synced, prices syncing...
        "This may take a few minutes. We'll notify you when ready."

→ Redirect to Operations Grid when first sync completes.
```

- Steps are displayed in the Main Area (no modal wizard). Activity Bar is visible but grayed out until setup completes.
- Validation happens inline (not on submit). Token validation → immediate feedback.
- After first connection, user can skip remaining steps and add more connections later in Settings.

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
| `--text-2xl` | 24px | 700 | Hero numbers (P&L total, only in summary cards) |

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
- Frozen columns: checkbox + SKU name always visible on horizontal scroll

**Row behavior:**
- Hover: `--bg-tertiary` background
- Selected: `--bg-active` background + left accent border (2px `--accent-primary`)
- Click on row → opens Detail Panel (right) with full SKU context
- Double-click on editable cell → inline edit (price override, COGS input)

**Column types:**
- Text: product name, barcode, category (left-aligned)
- Number: price, margin, stock, velocity (right-aligned, monospace, with sign coloring)
- Status: badge with semantic color + short label ("Profitable", "Pending", "Failed")
- Sparkline: 7-day trend mini-chart inline in cell (optional per column)
- Action: icon button in cell (lock price, view explanation)

**Pagination:**
- Server-side pagination. 50 / 100 / 200 rows per page selector.
- "Showing 1–50 of 1,234" counter in toolbar.
- Page navigation: prev/next + page number input.

### Filter bar

Horizontal bar above grid. Filters appear as compact pills:

```
[Marketplace: WB ×] [Margin: < 10% ×] [Status: Active ×]  [+ Add filter]  [⊘ Clear all]
```

- Each pill shows field name + operator + value. Click to edit inline dropdown.
- "Add filter" opens a dropdown with all available fields.
- Active filters are persisted per tab / saved view.
- Complex filters (range, multi-select) use a dropdown panel, not a modal.

### Detail Panel (right side panel)

Opens when user clicks a grid row or triggers "explain" / "detail" action.

**Layout:**
- Header: entity name + close button (×) + collapse button
- Tab strip within panel: "Overview", "P&L", "Pricing", "History"
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
│  Revenue     │  │  Profit      │  │  Margin      │  │  Active SKUs │
│  ₽ 2,340,120 │  │  ₽ 312,450   │  │  13.4%       │  │  1,234       │
│  ↑ 8.2%      │  │  ↓ 2.1%      │  │  → 0.0%      │  │  ↑ 12        │
└──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘
```

Max 4-6 KPI cards in a row. Below them — immediately the table/grid. No card-based layouts for operational data.

### Modals (rare)

Modals are used sparingly — only for:
- Destructive confirmations ("Delete policy?")
- Multi-step creation wizards (Create pricing policy)
- Bulk action confirmations

Everything else: inline editing, panels, dropdowns.

### Column configuration

Grid toolbar has a "Columns" button (icon: vertical bars). Opens a dropdown panel:

```
┌─ Columns ────────────────────┐
│ 🔍 Search columns...         │
│                               │
│ ☑ SKU Name          (frozen) │
│ ☑ Price                      │
│ ☑ Margin                     │
│ ☑ Stock                      │
│ ☐ Velocity                   │
│ ☐ Category                   │
│ ☑ Status                     │
│ ☐ COGS                       │
│ ☐ Days of cover              │
│ ☐ Return rate                │
│                               │
│ [Reset to default]            │
└───────────────────────────────┘
```

- Checkbox toggles column visibility. Drag handle (left of checkbox) reorders columns.
- "Frozen" label on columns that cannot be hidden (SKU Name).
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

## Key screens

### Operations → Operational Grid

The default landing screen. Seller's daily workspace.

```
┌─ Top Bar ───────────────────────────────────────────────────┐
│  [DataPulse]  Operations > All SKUs         🔍 Ctrl+K  [V] │
├─ Activity Bar ──────────────────────────────────────────────┤
│    │ Tab: [All SKUs] [Low margin*] [Price review] [+]       │
│ □  │ Toolbar: [Marketplace ▾] [Category ▾] [+Filter] [⊘]  │
│ □  │ ┌─Grid──────────────────────────────────────────┐      │
│ ■  │ │ ☐ │ SKU Name      │ Price  │ Margin │ Stock │ │      │
│ □  │ │ ☐ │ Widget A      │ 1,290₽ │ 18.3%  │   42  │ │      │
│ □  │ │ ■ │ Widget B      │   890₽ │  4.1%  │  120  │→│ Detail│
│    │ │ ☐ │ Widget C      │ 2,450₽ │ 22.7%  │    8  │ │ Panel │
│    │ └───────────────────────────────────────────────┘      │
│    │ Showing 1-50 of 1,234   [< Prev] [1] [2] ... [Next >] │
├────┴────────────────────────────────────────────────────────┤
│  Status: WB synced 12 min ago · Ozon synced 3 min ago      │
└─────────────────────────────────────────────────────────────┘
```

### Analytics → P&L Overview

Summary KPIs + drill-down table.

```
┌─ KPI Strip ─────────────────────────────────────────────────┐
│ [Revenue ₽2.3M ↑8%] [Profit ₽312K ↓2%] [Margin 13.4%] ... │
├─ Filter Bar ────────────────────────────────────────────────┤
│ [Period: Mar 2026 ×] [Marketplace: All] [Category ▾]       │
├─ P&L Table ─────────────────────────────────────────────────┤
│ SKU       │ Revenue │ Comission │ Logistics │ COGS │ Profit │
│ Widget A  │  45,200 │   -4,520  │   -2,100  │  -28K│ +10.5K │
│ Widget B  │  12,800 │   -1,920  │     -890  │   -9K│   -990 │
│ ...       │         │           │           │      │        │
├─ clicked row ─────────────────────────────── Detail Panel ──┤
│                                             │ P&L Breakdown │
│                                             │ revenue  45200│
│                                             │ - comm   4520 │
│                                             │ - logi   2100 │
│                                             │ - COGS  28000 │
│                                             │ = profit 10580│
│                                             │ residual   +22│
└─────────────────────────────────────────────────────────────┘
```

### Pricing → Decision Explanation

When operator reviews a pricing decision, the Detail Panel shows full explainability:

```
Detail Panel (right)
┌─────────────────────────────────────┐
│ Pricing Decision #4821        [×]   │
│ Widget A · WB · Mar 28, 2026        │
├─────────────────────────────────────┤
│ Decision: CHANGE  890₽ → 1,050₽    │
│ Strategy: TARGET_MARGIN (25%)       │
├─ Signals ───────────────────────────┤
│ COGS              640₽              │
│ Commission avg    12.3%             │
│ Logistics avg     82₽               │
│ Return rate       4.2%              │
│ Ad cost ratio     3.1%              │
├─ Constraints ───────────────────────┤
│ ✓ min_margin      passed            │
│ ✓ max_change      passed (18%)      │
│ ✓ min_price       passed            │
│ ⚡ rounding        1,047 → 1,050    │
├─ Guards ────────────────────────────┤
│ ✓ margin          25.1% ≥ 15%      │
│ ✓ frequency       48h since last    │
│ ✓ stale data      fresh (3h ago)    │
│ ✓ stock           42 units          │
├─────────────────────────────────────┤
│ [Approve]  [Reject]  [Hold]        │
└─────────────────────────────────────┘
```

### Operations → Working Queues

Working queues are filtered, prioritized subsets of the operational grid — task-oriented views for daily routines.

```
┌─ Tab: [All SKUs] [Low margin] [▸ Price review queue ◂] [+]──┐
│                                                               │
│  Queue: Price review         12 items · 3 assigned to you     │
│  [Auto-assign next]  [Mark all reviewed]                      │
│                                                               │
│  ┌─Grid────────────────────────────────────────────────────┐  │
│  │ Priority │ SKU Name    │ Issue         │ Assigned │ Age  │  │
│  │ !!!      │ Widget B    │ Margin < 5%   │ You      │ 2d   │  │
│  │ !!       │ Widget F    │ Price stale   │ —        │ 1d   │  │
│  │ !        │ Widget K    │ Needs COGS    │ Anna     │ 4h   │  │
│  └─────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────┘
```

Visual differences from Saved Views:
- Queue header shows item count and personal assignment count.
- Priority column with urgency indicators (!, !!, !!!).
- "Assigned" column — who is working on this item.
- Age column — how long item has been in queue.
- Queue-specific actions in toolbar: "Auto-assign next", "Mark all reviewed".

Saved Views are passive filters. Working Queues are active task lists with assignment and completion tracking.

### Operations → Price Journal

Chronological log of all price changes — decisions, actions, and outcomes.

```
┌─ Tab: [All SKUs] [▸ Price Journal ◂] [+]────────────────────┐
│                                                               │
│  Filter: [Period: Last 7 days ×] [SKU ▾] [Status ▾]         │
│                                                               │
│  ┌─Table───────────────────────────────────────────────────┐  │
│  │ Time         │ SKU       │ Was    │ Now    │ Δ     │ St │  │
│  │ Mar 28 14:32 │ Widget A  │  890₽  │ 1,050₽ │ +18%  │ ✓  │  │
│  │ Mar 28 14:32 │ Widget B  │ 1,290₽ │ 1,190₽ │  −8%  │ ✓  │  │
│  │ Mar 27 09:15 │ Widget C  │ 2,450₽ │ 2,450₽ │   0%  │ ⊘  │  │
│  │ Mar 26 11:00 │ Widget A  │  850₽  │   890₽ │  +5%  │ ✓  │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                               │
│  Detail Panel → decision explanation, action lifecycle, effect│
└───────────────────────────────────────────────────────────────┘
```

- "Was / Now / Δ" — old price, new price, change percentage (colored).
- Status column: ✓ SUCCEEDED, ⊘ SKIPPED, ● PENDING, ✕ FAILED.
- Click row → Detail Panel shows full decision explanation + action attempt history.
- Promo Journal follows the same pattern (promo name, dates, effect metrics instead of price deltas).

### Operations → Mismatch Monitor

Displays data quality issues — discrepancies between domains.

```
┌─ Tab: [All SKUs] [▸ Mismatch Monitor ◂] [+]─────────────────┐
│                                                               │
│  Summary: 5 active mismatches · 2 critical                    │
│                                                               │
│  ┌─Table───────────────────────────────────────────────────┐  │
│  │ Sev  │ Type              │ SKU      │ Expected │ Actual │  │
│  │ 🔴   │ Price mismatch    │ Widget B │  1,050₽  │  890₽  │  │
│  │ 🔴   │ Stock mismatch    │ Widget F │     42   │    0   │  │
│  │ 🟡   │ Stale finance     │ —        │ < 24h    │ 36h    │  │
│  │ 🟡   │ Residual spike    │ Widget A │ < 3%     │ 8.2%   │  │
│  │ ⚪   │ Missing COGS      │ Widget K │ set      │ empty  │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                               │
│  Detail Panel → evidence, source records, suggested action    │
└───────────────────────────────────────────────────────────────┘
```

- Severity: critical (red), warning (yellow), info (gray).
- Each mismatch row shows: what was expected vs what is actual.
- Detail Panel shows evidence trail — source records from both sides of the mismatch.
- Mismatches auto-resolve when underlying data is corrected (re-sync).

### Execution → Action Queue

Operator's view of the action lifecycle — all price actions across statuses.

```
┌─ Tab: [▸ Action Queue ◂] [Failed] [Reconciliation] [+]──────┐
│                                                               │
│  Filter: [Status: All ▾] [Marketplace ▾] [Period ▾]         │
│                                                               │
│  ┌─Table───────────────────────────────────────────────────┐  │
│  │ ID    │ SKU       │ Target  │ Status           │ Age    │  │
│  │ #4821 │ Widget A  │ 1,050₽  │ ● PENDING_APPR   │ 2h     │  │
│  │ #4820 │ Widget B  │ 1,190₽  │ ✓ SUCCEEDED      │ 1d     │  │
│  │ #4818 │ Widget F  │   990₽  │ ✕ FAILED (3/3)   │ 3d     │  │
│  │ #4815 │ Widget C  │ 2,200₽  │ ↻ RETRY (2/3)    │ 6h     │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                               │
│  [Approve selected]  [Reject selected]                        │
└───────────────────────────────────────────────────────────────┘
```

- Status column shows state + attempt count for retries/failures: "RETRY (2/3)", "FAILED (3/3)".
- Tabs pre-filter by status group: "Failed" tab = only FAILED actions, "Reconciliation" = RECONCILIATION_PENDING.
- Detail Panel for a row shows: full attempt history (timestamps, responses, errors), action timeline visualization.

### Execution → Action Detail (Detail Panel)

```
Detail Panel (right)
┌─────────────────────────────────────┐
│ Action #4818                  [×]   │
│ Widget F · Ozon · Target: 990₽     │
├─ Timeline ──────────────────────────┤
│ Mar 26 11:00  Created (pricing run) │
│ Mar 26 11:02  Approved (auto)       │
│ Mar 26 11:05  Attempt 1 → 429      │
│ Mar 26 11:10  Attempt 2 → 503      │
│ Mar 26 11:20  Attempt 3 → 503      │
│ Mar 26 11:20  FAILED (max attempts) │
├─ Last error ────────────────────────┤
│ HTTP 503 Service Unavailable        │
│ Ozon API returned maintenance page  │
├─────────────────────────────────────┤
│ [Retry]  [Cancel]                   │
└─────────────────────────────────────┘
```

### Settings → Connections

```
┌─ Settings ──────────────────────────────────────────────────┐
│  Sidebar          │  Content                                │
│                   │                                         │
│  Connections ■    │  Marketplace Connections                 │
│  Team             │                                         │
│  Workspace        │  ┌─────────────────────────────────┐    │
│  Billing          │  │ WB Main    ● Connected          │    │
│                   │  │ Last sync: 12 min ago            │    │
│                   │  │ 842 SKUs · 3 endpoints active    │    │
│                   │  │ [Edit] [Sync now] [Disconnect]   │    │
│                   │  └─────────────────────────────────┘    │
│                   │  ┌─────────────────────────────────┐    │
│                   │  │ Ozon Store ● Connected          │    │
│                   │  │ Last sync: 3 min ago             │    │
│                   │  │ 362 SKUs · 5 endpoints active    │    │
│                   │  │ [Edit] [Sync now] [Disconnect]   │    │
│                   │  └─────────────────────────────────┘    │
│                   │                                         │
│                   │  [+ Add connection]                     │
└───────────────────┴─────────────────────────────────────────┘
```

Settings uses a different layout than operational modules: left sidebar navigation (vertical text links) + content area. No tabs, no grid. This matches how Cursor's Settings screen differs from the editor.

### Settings → Team

```
│  Team members (4)                                │
│                                                   │
│  ┌─Table──────────────────────────────────────┐  │
│  │ Name       │ Email              │ Role     │  │
│  │ Виталий К. │ v@example.com      │ Owner    │  │
│  │ Анна С.    │ anna@example.com   │ Admin    │  │
│  │ Иван П.    │ ivan@example.com   │ Operator │  │
│  │ Мария Д.   │ maria@example.com  │ Analyst  │  │
│  └────────────────────────────────────────────┘  │
│                                                   │
│  Pending invitations (1)                          │
│  ┌────────────────────────────────────────────┐  │
│  │ new@example.com │ Operator │ Exp: Apr 5 │ ×│  │
│  └────────────────────────────────────────────┘  │
│                                                   │
│  [+ Invite member]                                │
```

- Role displayed as badge. Role change via inline dropdown (admin only).
- Invite: email + role selector → sends invitation email.
- Pending invitations shown separately with expiration date and cancel button.

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

Editable cells (COGS, manual price lock, notes) switch to edit mode on double-click.
No modal forms for single-field edits. Save on blur or Enter. Cancel on Escape.

### Context menu (right-click)

On grid rows: Copy, Open in new tab, Lock price, Add to queue, Export selection.

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
| Stale data blocking action | Inline message near action button | "Cannot run pricing: stock data is stale." |
| 404 / not found | Full main area message | "Workspace not found." + [Go to workspace selector] |

Rules:
- Validation errors appear immediately on blur, not only on submit.
- Toast errors auto-dismiss after 8 seconds (longer than success toasts). Error toasts have manual dismiss button.
- Persistent banners (connection loss, automation blockers) do not auto-dismiss — they stay until condition resolves.
- Never show raw HTTP codes or stack traces. Map errors to human-readable messages.

### Confirmation & feedback

| Action | Feedback | Style |
|--------|----------|-------|
| Save (view, policy, COGS) | Toast: "View saved" | Success toast, auto-dismiss 3s |
| Approve action | Toast: "Action #4821 approved" | Success toast, 3s |
| Reject action | Toast: "Action #4821 rejected" | Neutral toast, 3s |
| Bulk approve | Toast: "12 actions approved" | Success toast, 3s |
| Delete (policy, connection) | Confirmation modal → Toast: "Policy deleted" | Danger modal → success toast |
| Disconnect marketplace | Confirmation modal (explicit type-to-confirm) | Danger modal: "Type 'WB Main' to confirm" |
| Invite sent | Toast: "Invitation sent to anna@example.com" | Success toast, 3s |
| Sync triggered | Toast: "Sync started for WB Main" | Info toast, 3s |
| Export started | Toast: "Exporting 1,234 rows..." → file download | Info toast → browser download |

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
● WB synced 12 min ago   ● Ozon synced 3 min ago   ● 2 stale endpoints
```

- Green dot: synced within threshold (default 1h)
- Yellow dot: approaching stale threshold
- Red dot: stale or failed

### Grid-level freshness

Column header shows freshness icon if source data is older than threshold:
- ⚠ icon next to column header
- Tooltip: "Stock data last updated 6 hours ago"

### Automation blockers

When data staleness blocks automation (pricing, execution), a non-dismissible banner appears above the grid:

```
⚠ Automated pricing paused: WB stock data is 8 hours stale. Manual actions are still available.
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

- Grid rows update in place (no full reload) when sync completes.
- New pricing decisions appear in working queue without page refresh.
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
