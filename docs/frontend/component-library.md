# Datapulse — Shared Component Library

Каталог всех переиспользуемых UI-компонентов платформы Datapulse. Единственный источник правды для реализации компонентов.

Визуальный язык, принципы, color tokens и type scale определены в [frontend-design-direction.md](frontend-design-direction.md). Данный документ **расширяет** его имплементационными деталями: полные props, states, behavior, accessibility для каждого компонента.

---

## Design Tokens

### Цвета

Определены в `frontend-design-direction.md` §Color system. Ниже — полная таблица для имплементации.

#### Фон и поверхности

| Token | Value | Использование |
|-------|-------|---------------|
| `--bg-primary` | `#FFFFFF` | Основной фон контента |
| `--bg-secondary` | `#F9FAFB` | Sidebar, вторичные панели, чередующиеся строки |
| `--bg-tertiary` | `#F3F4F6` | Hover states, заголовки панелей |
| `--bg-active` | `#EFF6FF` | Выбранная строка, активный tab |
| `--bg-overlay` | `rgba(0,0,0,0.4)` | Backdrop для модальных окон |
| `--bg-toast` | `#FFFFFF` | Фон toast-уведомления |

#### Границы

| Token | Value | Использование |
|-------|-------|---------------|
| `--border-default` | `#E5E7EB` | Границы, разделители (1px) |
| `--border-subtle` | `#F3F4F6` | Внутренние разделители ячеек |
| `--border-focus` | `#2563EB` | Focus ring (2px) |

#### Текст

| Token | Value | Использование |
|-------|-------|---------------|
| `--text-primary` | `#111827` | Основной текст, заголовки |
| `--text-secondary` | `#6B7280` | Метки, метаданные, timestamps |
| `--text-tertiary` | `#9CA3AF` | Placeholder, disabled |
| `--text-inverse` | `#FFFFFF` | Текст на залитых кнопках |
| `--text-link` | `#2563EB` | Ссылки |

#### Акцент

| Token | Value | Использование |
|-------|-------|---------------|
| `--accent-primary` | `#2563EB` | Primary actions, links, active states |
| `--accent-primary-hover` | `#1D4ED8` | Button hover |
| `--accent-subtle` | `#EFF6FF` | Акцентный фон (выбранный tab, активный фильтр) |

#### Семантические

| Token | Value | Использование |
|-------|-------|---------------|
| `--status-success` | `#059669` | Synced, confirmed, succeeded |
| `--status-success-bg` | `#ECFDF5` | Фон success badge |
| `--status-warning` | `#D97706` | Pending, stale, attention |
| `--status-warning-bg` | `#FFFBEB` | Фон warning badge |
| `--status-error` | `#DC2626` | Failed, loss, critical |
| `--status-error-bg` | `#FEF2F2` | Фон error badge |
| `--status-info` | `#2563EB` | Informational |
| `--status-info-bg` | `#EFF6FF` | Фон info badge |
| `--status-neutral` | `#6B7280` | Skipped, archived, inactive |
| `--status-neutral-bg` | `#F3F4F6` | Фон neutral badge |

#### Финансовые

| Token | Value | Использование |
|-------|-------|---------------|
| `--finance-positive` | `#059669` | Profit, positive margin |
| `--finance-negative` | `#DC2626` | Loss, negative margin |
| `--finance-zero` | `#6B7280` | Zero, break-even |

#### Маркетплейсы

| Token | Value | Использование |
|-------|-------|---------------|
| `--mp-wb` | `#6B21A8` | Wildberries purple |
| `--mp-wb-bg` | `#F5F3FF` | Wildberries badge background |
| `--mp-ozon` | `#005BFF` | Ozon blue |
| `--mp-ozon-bg` | `#EFF6FF` | Ozon badge background |

### Spacing

| Token | Value |
|-------|-------|
| `--space-0` | `0px` |
| `--space-0.5` | `2px` |
| `--space-1` | `4px` |
| `--space-1.5` | `6px` |
| `--space-2` | `8px` |
| `--space-3` | `12px` |
| `--space-4` | `16px` |
| `--space-5` | `20px` |
| `--space-6` | `24px` |
| `--space-8` | `32px` |
| `--space-10` | `40px` |
| `--space-12` | `48px` |

### Typography

| Token | Size | Weight | Line height | Font | Использование |
|-------|------|--------|-------------|------|---------------|
| `--text-xs` | 11px | 400 | 1.2 | Inter | Timestamps, meta, status bar |
| `--text-sm` | 13px | 400 | 1.4 | Inter | Table cells, secondary labels |
| `--text-base` | 14px | 400 | 1.4 | Inter | Body text, form inputs |
| `--text-lg` | 16px | 600 | 1.2 | Inter | Section headings, tab labels |
| `--text-xl` | 20px | 600 | 1.2 | Inter | Page titles, KPI values |
| `--text-2xl` | 24px | 700 | 1.2 | Inter | Hero numbers (KPI cards only) |
| `--text-mono-sm` | 13px | 400 | 1.0 | JetBrains Mono | Grid numbers, prices |
| `--text-mono-base` | 14px | 400 | 1.0 | JetBrains Mono | Form number inputs |
| `--text-mono-lg` | 16px | 600 | 1.0 | JetBrains Mono | KPI card values |
| `--text-mono-2xl` | 24px | 700 | 1.0 | JetBrains Mono | Hero KPI numbers |

### Border Radius

| Token | Value | Использование |
|-------|-------|---------------|
| `--radius-sm` | `4px` | Badges, small chips |
| `--radius-md` | `6px` | Buttons, inputs, cards |
| `--radius-lg` | `8px` | Modals, command palette |
| `--radius-full` | `9999px` | Status dots, avatars |

### Shadows

| Token | Value | Использование |
|-------|-------|---------------|
| `--shadow-sm` | `0 1px 2px rgba(0,0,0,0.05)` | Subtle elevation |
| `--shadow-md` | `0 4px 12px rgba(0,0,0,0.08)` | Dropdowns, command palette |
| `--shadow-lg` | `0 8px 24px rgba(0,0,0,0.12)` | Modals |

### Animation

| Token | Value | Использование |
|-------|-------|---------------|
| `--transition-fast` | `150ms ease` | Hover, focus states |
| `--transition-normal` | `200ms ease` | Panel open, tab switch |
| `--transition-slow` | `300ms ease` | Modal open/close |
| `--duration-toast` | `300ms` | Toast slide-in |
| `--duration-panel` | `200ms` | Detail panel slide |

---

## Icon Mapping

Все иконки — [Lucide](https://lucide.dev/), подключены через `lucide-angular`.

### Activity Bar — модули

| Модуль | Icon | Lucide name |
|--------|------|-------------|
| Seller Operations (Operational Grid) | Сетка | `layout-grid` |
| Analytics & P&L | График | `bar-chart-3` |
| Pricing | Ценник | `tag` |
| Promotions | Рупор | `megaphone` |
| Audit & Alerts | Щит | `shield-alert` |
| Settings (нижняя секция) | Шестерёнка | `settings` |

### Общие действия

| Действие | Icon |
|----------|------|
| Search / Command palette | `search` |
| Filter | `filter` |
| Add / Create | `plus` |
| Edit | `pencil` |
| Delete | `trash-2` |
| Close | `x` |
| Collapse / Expand | `chevron-down` / `chevron-up` |
| External link | `external-link` |
| Export | `download` |
| Import (upload) | `upload` |
| Refresh / Retry | `refresh-cw` |
| Approve | `check` |
| Reject | `x-circle` |
| Hold | `pause` |
| Resume | `play` |
| Lock | `lock` |
| Unlock | `unlock` |
| Info | `info` |
| Warning | `alert-triangle` |
| Error | `alert-circle` |
| Success | `check-circle` |
| Notification bell | `bell` |
| User | `user` |
| Workspace | `building-2` |
| Connection | `plug` |
| Copy | `copy` |
| Pin tab | `pin` |
| Columns config | `columns-3` |
| Density toggle | `align-justify` |
| Sort ascending | `arrow-up` |
| Sort descending | `arrow-down` |
| Trend up | `trending-up` |
| Trend down | `trending-down` |
| Trend flat | `minus` |
| Calendar | `calendar` |
| Clock | `clock` |

---

## Status Color Mapping

Полная таблица маппинга всех status enum'ов системы на цвета и метки (русский UI).

### price_action.status

| Status | Color token | Dot | Метка RU |
|--------|------------|-----|----------|
| `PENDING_APPROVAL` | `--status-info` | blue | Ожидает одобрения |
| `APPROVED` | `--status-info` | blue | Одобрен |
| `ON_HOLD` | `--status-warning` | amber | Приостановлен |
| `SCHEDULED` | `--status-info` | blue | Запланирован |
| `EXECUTING` | `--status-warning` | amber | Выполняется |
| `RECONCILIATION_PENDING` | `--status-warning` | amber | Проверка |
| `RETRY_SCHEDULED` | `--status-warning` | amber | Повтор |
| `SUCCEEDED` | `--status-success` | green | Выполнен |
| `FAILED` | `--status-error` | red | Ошибка |
| `EXPIRED` | `--status-neutral` | gray | Истёк |
| `CANCELLED` | `--status-neutral` | gray | Отменён |
| `SUPERSEDED` | `--status-neutral` | gray | Заменён |

### promo_action.status

| Status | Color token | Dot | Метка RU |
|--------|------------|-----|----------|
| `PENDING_APPROVAL` | `--status-info` | blue | Ожидает одобрения |
| `APPROVED` | `--status-info` | blue | Одобрен |
| `EXECUTING` | `--status-warning` | amber | Выполняется |
| `SUCCEEDED` | `--status-success` | green | Выполнен |
| `FAILED` | `--status-error` | red | Ошибка |
| `EXPIRED` | `--status-neutral` | gray | Истёк |
| `CANCELLED` | `--status-neutral` | gray | Отменён |

### price_decision.decision_type

| Type | Color token | Метка RU |
|------|------------|----------|
| `CHANGE` | `--status-info` | Изменение |
| `SKIP` | `--status-neutral` | Пропуск |
| `HOLD` | `--status-warning` | Ожидание |

### promo_decision.decision_type

| Type | Color token | Метка RU |
|------|------------|----------|
| `PARTICIPATE` | `--status-success` | Участие |
| `DECLINE` | `--status-error` | Отказ |
| `PENDING_REVIEW` | `--status-warning` | На рассмотрении |

### promo_evaluation.evaluation_result

| Result | Color token | Метка RU |
|--------|------------|----------|
| `PROFITABLE` | `--status-success` | Выгодно |
| `MARGINAL` | `--status-warning` | Пограничный |
| `UNPROFITABLE` | `--status-error` | Невыгодно |
| `INSUFFICIENT_STOCK` | `--status-error` | Мало остатков |
| `INSUFFICIENT_DATA` | `--status-neutral` | Мало данных |

### canonical_promo_product.participation_status

| Status | Color token | Метка RU |
|--------|------------|----------|
| `ELIGIBLE` | `--status-info` | Доступен |
| `PARTICIPATING` | `--status-success` | Участвует |
| `DECLINED` | `--status-neutral` | Отклонён |
| `REMOVED` | `--status-neutral` | Удалён |
| `BANNED` | `--status-error` | Заблокирован |
| `AUTO_DECLINED` | `--status-neutral` | Авто-отклонён |

### canonical_promo_campaign.status

| Status | Color token | Метка RU |
|--------|------------|----------|
| `UPCOMING` | `--status-info` | Предстоящая |
| `ACTIVE` | `--status-success` | Активная |
| `FROZEN` | `--status-warning` | Заморожена |
| `ENDED` | `--status-neutral` | Завершена |

### marketplace_offer.status

| Status | Color token | Метка RU |
|--------|------------|----------|
| `ACTIVE` | `--status-success` | Активен |
| `ARCHIVED` | `--status-neutral` | В архиве |
| `BLOCKED` | `--status-error` | Заблокирован |

### marketplace_connection.status

| Status | Color token | Метка RU |
|--------|------------|----------|
| `PENDING_VALIDATION` | `--status-warning` | Проверка |
| `ACTIVE` | `--status-success` | Активно |
| `AUTH_FAILED` | `--status-error` | Ошибка авторизации |
| `DISABLED` | `--status-neutral` | Отключено |
| `ARCHIVED` | `--status-neutral` | В архиве |

### marketplace_sync_state.status

| Status | Color token | Метка RU |
|--------|------------|----------|
| `IDLE` | `--status-success` | Готово |
| `SYNCING` | `--status-info` | Синхронизация |
| `ERROR` | `--status-error` | Ошибка |

### stock_out_risk

| Level | Color token | Метка RU |
|-------|------------|----------|
| `CRITICAL` | `--status-error` | Критический |
| `WARNING` | `--status-warning` | Внимание |
| `NORMAL` | `--status-success` | Норма |

### data_freshness

| Level | Color token | Метка RU |
|-------|------------|----------|
| `FRESH` | `--status-success` | Свежие |
| `STALE` | `--status-warning` | Устарели |

### alert_event.status

| Status | Color token | Метка RU |
|--------|------------|----------|
| `OPEN` | `--status-error` | Открыт |
| `ACKNOWLEDGED` | `--status-warning` | Принят |
| `RESOLVED` | `--status-success` | Решён |
| `AUTO_RESOLVED` | `--status-success` | Решён автоматически |

### alert_event.severity

| Severity | Color token | Icon | Метка RU |
|----------|------------|------|----------|
| `INFO` | `--status-info` | `info` | Информация |
| `WARNING` | `--status-warning` | `alert-triangle` | Предупреждение |
| `CRITICAL` | `--status-error` | `alert-circle` | Критический |

### working_queue_assignment.status

| Status | Color token | Метка RU |
|--------|------------|----------|
| `PENDING` | `--status-warning` | Ожидает |
| `IN_PROGRESS` | `--status-info` | В работе |
| `DONE` | `--status-success` | Выполнен |
| `DISMISSED` | `--status-neutral` | Отклонён |

### price_policy.status / promo_policy.status

| Status | Color token | Метка RU |
|--------|------------|----------|
| `DRAFT` | `--status-neutral` | Черновик |
| `ACTIVE` | `--status-success` | Активна |
| `PAUSED` | `--status-warning` | Приостановлена |
| `ARCHIVED` | `--status-neutral` | В архиве |

### execution_mode

| Mode | Color token | Метка RU |
|------|------------|----------|
| `RECOMMENDATION` | `--status-neutral` | Рекомендация |
| `SEMI_AUTO` | `--status-info` | Полуавтомат |
| `FULL_AUTO` | `--status-success` | Автомат |
| `SIMULATED` | `--status-warning` | Симуляция |

### pricing_run.status

| Status | Color token | Метка RU |
|--------|------------|----------|
| `PENDING` | `--status-neutral` | Ожидание |
| `IN_PROGRESS` | `--status-info` | Выполняется |
| `COMPLETED` | `--status-success` | Завершён |
| `COMPLETED_WITH_ERRORS` | `--status-warning` | Завершён с ошибками |
| `FAILED` | `--status-error` | Ошибка |

### mismatch type

| Type | Icon | Метка RU |
|------|------|----------|
| `PRICE` | `tag` | Цена |
| `STOCK` | `package` | Остатки |
| `PROMO` | `megaphone` | Промо |
| `FINANCE` | `wallet` | Финансы |

### mismatch severity

| Severity | Color token | Метка RU |
|----------|------------|----------|
| `WARNING` | `--status-warning` | Предупреждение |
| `CRITICAL` | `--status-error` | Критическое |

### workspace.status

| Status | Color token | Метка RU |
|--------|------------|----------|
| `ACTIVE` | `--status-success` | Активно |
| `SUSPENDED` | `--status-warning` | Приостановлено |
| `ARCHIVED` | `--status-neutral` | В архиве |

### workspace_invitation.status

| Status | Color token | Метка RU |
|--------|------------|----------|
| `PENDING` | `--status-warning` | Ожидает |
| `ACCEPTED` | `--status-success` | Принято |
| `EXPIRED` | `--status-neutral` | Истекло |
| `CANCELLED` | `--status-neutral` | Отменено |

### cogs_status

| Status | Color token | Метка RU |
|--------|------------|----------|
| `OK` | `--status-success` | ОК |
| `NO_COST_PROFILE` | `--status-warning` | Нет себестоимости |
| `NO_SALES` | `--status-neutral` | Нет продаж |

---

## 1. Layout Components

### AppShell

**Angular:** `AppShellComponent`
**Phase:** A
**Purpose:** Корневой layout приложения. Содержит все persistent зоны: Activity Bar, Top Bar, Main Area, optional Detail Panel, Status Bar.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `detailPanelOpen` | `boolean` | `false` | Открыта ли Detail Panel |
| `detailPanelWidth` | `number` | `400` | Ширина Detail Panel (px) |
| `bottomPanelOpen` | `boolean` | `false` | Открыта ли Bottom Panel |

**Visual spec:**
- Минимальный viewport: 1280×720
- Activity Bar: fixed left, 48px wide, `--bg-secondary`, border-right 1px `--border-default`
- Top Bar: fixed top, height 40px, `--bg-primary`, border-bottom 1px `--border-default`
- Status Bar: fixed bottom, height 24px, `--bg-secondary`, border-top 1px `--border-default`, `--text-xs`
- Main Area: fills available space, `--bg-primary`
- Detail Panel: right side, resizable, `--bg-primary`, border-left 1px `--border-default`

**Layout diagram:**

```
┌────────────────────────────────────────────────────────┐
│  TopBar (40px)                                         │
├──────┬──────────────────────────────┬──────────────────┤
│  AB  │  Main Area                   │  Detail Panel    │
│ 48px │  (tab strip + content)       │  (optional)      │
│      │                              │  400px default   │
│      ├──────────────────────────────┤                  │
│      │  Bottom Panel (optional)     │                  │
├──────┴──────────────────────────────┴──────────────────┤
│  StatusBar (24px)                                      │
└────────────────────────────────────────────────────────┘
```

**Behavior:**
- Detail Panel pushes Main Area left (не overlay) при viewport ≥ 1440px; overlay при 1280–1440px
- Bottom Panel expands upward from above Status Bar
- `< 1280px` — показать сообщение «Datapulse рассчитан на экраны от 1280px»

**Accessibility:**
- Landmark roles: `<nav>` для Activity Bar, `<main>` для Main Area, `<aside>` для Detail Panel
- Skip navigation link at top

---

### SplitLayout

**Angular:** `SplitLayoutComponent`
**Phase:** E
**Purpose:** Resizable left/right split для страниц с master-detail layout (Working Queues, Settings).

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `leftWidth` | `number` | `320` | Начальная ширина left panel (px) |
| `minLeftWidth` | `number` | `240` | Минимум left panel |
| `maxLeftWidth` | `number` | `480` | Максимум left panel |
| `resizable` | `boolean` | `true` | Drag handle виден |

**Visual spec:**
- Drag handle: 4px wide, `--border-default`, cursor `col-resize`
- Left panel: `--bg-secondary`
- Right panel: `--bg-primary`

**Behavior:**
- Drag handle между панелями для resize
- Double-click drag handle — сброс к `leftWidth`

**Accessibility:**
- `role="separator"` с `aria-orientation="vertical"` на drag handle
- Arrow keys для resize (1px step, shift+arrow — 10px)

---

### DetailPanel

**Angular:** `DetailPanelComponent`
**Phase:** E
**Purpose:** Slide-in правая панель для контекстных деталей выбранной entity (offer, action, campaign).

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `isOpen` | `boolean` | `false` | Состояние видимости |
| `width` | `number` | `400` | Ширина (px) |
| `minWidth` | `number` | `300` | Минимальная ширина |
| `maxWidth` | `number` | `600` | Максимальная ширина |
| `title` | `string` | `''` | Заголовок панели |
| `subtitle` | `string \| null` | `null` | Подзаголовок (например, SKU код) |

**Outputs:**

| Output | Type | Описание |
|--------|------|----------|
| `closed` | `EventEmitter<void>` | Панель закрыта |
| `widthChanged` | `EventEmitter<number>` | Ширина изменена drag-ом |

**Visual spec:**
- Header: 48px height, `--bg-secondary`, `--text-lg` title, close button (×) и collapse button справа
- Body: scrollable area, padding `--space-4`
- Left border: 1px `--border-default`
- Drag handle left edge: 4px, cursor `col-resize`

**States:**
- Default (open): slide-in `--transition-normal`
- Closed: width 0, hidden
- Resizing: drag handle highlighted `--accent-primary`

**Behavior:**
- `Escape` closes panel
- Slide-in animation from right
- Pushes main content at ≥ 1440px, overlays at 1280–1440px

**Accessibility:**
- `role="complementary"`, `aria-label="Панель деталей"`
- Focus trap when overlay mode
- Close button: `aria-label="Закрыть панель"`

**Usage:** Operational Grid row click, Pricing decision detail, Promo campaign detail.

---

### TabStrip

**Angular:** `TabStripComponent`
**Phase:** A
**Purpose:** Полоса вкладок поверх Main Area. Аналог editor tabs в Cursor.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `tabs` | `Tab[]` | `[]` | Массив вкладок |
| `activeTabId` | `string` | `''` | ID активной вкладки |

Где `Tab`:
```typescript
interface Tab {
  id: string;
  label: string;
  icon?: string;        // Lucide icon name
  pinned?: boolean;
  closeable?: boolean;
  dirty?: boolean;       // unsaved changes indicator
}
```

**Outputs:**

| Output | Type | Описание |
|--------|------|----------|
| `tabSelected` | `EventEmitter<string>` | Tab ID |
| `tabClosed` | `EventEmitter<string>` | Tab ID |
| `tabReordered` | `EventEmitter<{id: string, newIndex: number}>` | Drag reorder |

**Visual spec:**
- Container: height 32px, `--bg-secondary`, border-bottom 1px `--border-default`
- Tab: padding `0 --space-3`, `--text-sm`, max-width 200px, ellipsis overflow
- Active tab: `--bg-primary`, border-bottom 2px `--accent-primary`, `--text-primary`
- Inactive tab: `--bg-secondary`, `--text-secondary`
- Hover: `--bg-tertiary`
- Pinned tabs: left-aligned, no close button, `pin` icon before label
- Close button: `x` icon, 16×16, visible on hover, `--text-tertiary`
- Dirty dot: 6px filled circle `--accent-primary` before label
- Overflow: chevron arrows left/right when tabs exceed width

**Behavior:**
- Click — activate tab
- Middle-click — close tab (if closeable)
- Ctrl+click — close tab
- Drag — reorder (CDK DragDrop). Pinned tabs cannot be reordered past unpinned.
- Right-click — context menu: Закрыть, Закрыть остальные, Закрыть все, Закрепить/Открепить
- Overflow scroll — horizontal, smooth, chevron buttons at edges

**Accessibility:**
- `role="tablist"` container, `role="tab"` per item
- `aria-selected` on active
- Arrow keys for navigation
- `Delete` key closes selected closeable tab

---

### PageLayout

**Angular:** `PageLayoutComponent`
**Phase:** B
**Purpose:** Стандартный шаблон страницы: optional KPI strip → optional FilterBar → main content area.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `showKpiStrip` | `boolean` | `false` | Показывать KPI strip |
| `showFilterBar` | `boolean` | `false` | Показывать FilterBar |
| `title` | `string` | `''` | Заголовок страницы (breadcrumbs handle navigation) |

**Visual spec:**
- KPI strip: flex row, gap `--space-3`, padding `--space-4` horizontal, `--space-3` vertical, border-bottom 1px `--border-default`
- FilterBar: below KPI (or top if no KPI), border-bottom 1px `--border-default`
- Content: fills remaining space, overflow auto

**Content projection slots:**
- `[kpiStrip]` — KPI cards row
- `[filterBar]` — FilterBar
- `[toolbar]` — Grid toolbar (columns, density, export)
- `[content]` — Main content area (grid, form, etc.)

---

## 2. Data Display

### DataGrid

**Angular:** `DataGridComponent` (AG Grid wrapper)
**Phase:** A
**Purpose:** Центральный операционный компонент. Обёртка AG Grid Community с preset конфигурацией в стиле Datapulse.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `columnDefs` | `ColDef[]` | `[]` | AG Grid column definitions |
| `rowData` | `any[]` | `[]` | Данные строк |
| `totalRows` | `number` | `0` | Общее количество записей (server-side pagination) |
| `pageSize` | `number` | `50` | Размер страницы |
| `currentPage` | `number` | `0` | Текущая страница (0-based) |
| `loading` | `boolean` | `false` | Показывать loading overlay |
| `density` | `'compact' \| 'comfortable'` | `'compact'` | Плотность отображения |
| `selectable` | `boolean` | `true` | Показывать checkbox-колонку |
| `frozenColumns` | `string[]` | `[]` | Имена frozen-колонок (всегда видны при горизонтальном скролле) |

**Outputs:**

| Output | Type | Описание |
|--------|------|----------|
| `rowClicked` | `EventEmitter<any>` | Строка кликнута → Detail Panel |
| `cellDoubleClicked` | `EventEmitter<{row: any, colId: string}>` | Ячейка double-clicked → inline edit |
| `selectionChanged` | `EventEmitter<any[]>` | Изменение selection |
| `sortChanged` | `EventEmitter<{column: string, direction: 'asc' \| 'desc'}>` | Сортировка изменена |
| `pageChanged` | `EventEmitter<{page: number, size: number}>` | Пагинация изменена |
| `exportRequested` | `EventEmitter<'all' \| 'selection'>` | Export |

**Visual spec:**
- Row height: 32px (compact), 40px (comfortable)
- Header: 36px height, `--bg-secondary`, `--text-sm` semi-bold, `--text-secondary`
- Cell: `--text-sm`, padding `0 --space-2`
- Text columns: left-aligned
- Number columns: right-aligned, `font-family: JetBrains Mono`
- Hover row: `--bg-tertiary`
- Selected row: `--bg-active`, left border 2px `--accent-primary`
- Checkbox column: width 40px, frozen
- Grid borders: 1px `--border-subtle` between cells, 1px `--border-default` between header and body
- Alternating rows: not used (too noisy at compact density)

**Toolbar (content projection `[toolbar]`):**
Standard toolbar includes: density toggle, column config button, export button. FilterBar is separate component above grid.

**Behavior:**
- Click row → `rowClicked` event → open Detail Panel
- Double-click editable cell → inline edit mode
- Shift+click for range selection, Ctrl+click for toggle
- Arrow keys navigate rows, Enter opens Detail Panel
- Space toggles checkbox on selected row
- Column headers: click to sort (asc → desc → none), drag to resize, drag to reorder
- Server-side pagination: grid does not hold all data

**Accessibility:**
- AG Grid built-in a11y: `role="grid"`, `role="row"`, `role="gridcell"`
- Screen reader announces sort state, selection state
- Keyboard navigation fully supported by AG Grid

**Usage:** Operational Grid, Working Queues items, Price Journal, Promo Journal, Mismatch Monitor, P&L by-product, P&L by-posting, Inventory by-product, Returns by-product.

---

### KpiCard

**Angular:** `KpiCardComponent`
**Phase:** B
**Purpose:** Компактная карточка метрики в KPI strip. Показывает значение, метку и опциональный trend delta.

**Variants:** `currency`, `percent`, `count`, `duration`

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `label` | `string` | — | Метка (RU) |
| `value` | `number \| null` | — | Значение |
| `variant` | `'currency' \| 'percent' \| 'count' \| 'duration'` | `'count'` | Тип форматирования |
| `trend` | `number \| null` | `null` | Дельта (%). Positive = зелёная стрелка вверх, negative = красная вниз |
| `trendLabel` | `string \| null` | `null` | Подпись trend (напр. «за 30 дней») |
| `loading` | `boolean` | `false` | Skeleton |

**Visual spec:**
- Size: min-width 160px, height 80px, padding `--space-3`
- Background: `--bg-primary`, border 1px `--border-default`, radius `--radius-md`
- Label: `--text-sm`, `--text-secondary`, top
- Value: `--text-mono-2xl` (currency/count), `--text-2xl` (percent/duration), `--text-primary`, middle
- Trend line: `--text-xs`, bottom. Arrow icon + percent + optional label
  - Positive: `trending-up` icon + value in `--finance-positive`
  - Negative: `trending-down` icon + value in `--finance-negative`
  - Zero: `minus` icon + value in `--finance-zero`
- Null value: `—` (em dash), `--text-tertiary`

**Formatting rules:**
- `currency`: `1 290 ₽` (space thousands, 0 decimals for >100₽, 2 decimals for <100₽)
- `percent`: `18,3%` (comma decimal, 1 fractional digit)
- `count`: `1 234` (space thousands, no decimals)
- `duration`: `18,5 д.` (comma decimal, suffix «д.» for days)

**States:**
- Default: data shown
- Loading: skeleton shimmer block matching layout
- Null: em dash for value, no trend

**Accessibility:**
- `role="status"`, `aria-label="{label}: {formatted value}"`

**Usage:** Top of Operational Grid, P&L summary, Inventory overview, Pricing run summary.

---

### StatusBadge

**Angular:** `StatusBadgeComponent`
**Phase:** A
**Purpose:** Inline pill-shaped badge с цветной точкой и текстовой меткой. Универсальный компонент для всех status enum'ов системы.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `status` | `string` | — | Значение enum (например, `'SUCCEEDED'`, `'ACTIVE'`) |
| `domain` | `string` | — | Имя enum-домена (например, `'priceAction'`, `'offer'`) для lookup цвета и метки |

Маппинг `(domain, status) → (color, label)` определяется через конфигурационный сервис `StatusConfigService`, загружаемый из единого реестра (см. §Status Color Mapping выше).

**Visual spec:**
- Dot: 6px circle, fill по color token домена
- Label: `--text-xs` (11px), `--text-primary`, margin-left `--space-1`
- Container: inline-flex, align center, no background, no border
- Pill variant (with background): padding `--space-0.5 --space-2`, border-radius `--radius-sm`, background по `*-bg` token

**States:**
- Default: dot + label visible

**Accessibility:**
- `role="status"`, `aria-label="{label}"` — не только цвет, но и текст

**Usage:** Operational Grid status columns, Detail Panel, Working Queues, all list views.

---

### MarketplaceBadge

**Angular:** `MarketplaceBadgeComponent`
**Phase:** A
**Purpose:** Компактный badge маркетплейса (WB / Ozon).

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `marketplace` | `'WB' \| 'OZON'` | — | Тип маркетплейса |
| `size` | `'sm' \| 'md'` | `'sm'` | Размер |

**Visual spec:**
- `sm`: height 18px, padding `0 --space-1.5`, `--text-xs` bold
- `md`: height 22px, padding `0 --space-2`, `--text-sm` bold
- WB: background `--mp-wb-bg`, text `--mp-wb`, border-radius `--radius-sm`
- Ozon: background `--mp-ozon-bg`, text `--mp-ozon`, border-radius `--radius-sm`

**Accessibility:**
- `aria-label="Wildberries"` / `aria-label="Ozon"`

---

### MoneyDisplay

**Angular:** `MoneyDisplayComponent`
**Phase:** A
**Purpose:** Форматированное отображение денежных значений с цветовой семантикой.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `value` | `number \| null` | — | Значение |
| `currency` | `string` | `'₽'` | Символ валюты |
| `colorize` | `boolean` | `false` | Красить по знаку |
| `decimals` | `number` | `2` | Количество знаков после запятой |
| `compact` | `boolean` | `false` | Сокращённый формат для больших чисел (1,2M) |

**Visual spec:**
- Font: `JetBrains Mono`, `--text-mono-sm` (grid cell) или `--text-mono-base` (detail panel)
- Thousands separator: space (русский стандарт)
- Decimal separator: comma
- Currency suffix: ` ₽` (пробел + символ)
- Colorized: positive → `--finance-positive`, negative → `--finance-negative`, zero → `--finance-zero`
- Null: `—` в `--text-tertiary`
- Compact: `1,2 млн ₽`, `432 тыс ₽`

**Formatting examples:**
- `1290.50` → `1 290,50 ₽`
- `-500.00` → `−500,00 ₽` (typographic minus `−`, red)
- `0.00` → `0,00 ₽` (gray)
- `null` → `—`

**Accessibility:**
- `aria-label="1 290 рублей 50 копеек"` (screen reader-friendly)

---

### PercentDisplay

**Angular:** `PercentDisplayComponent`
**Phase:** A
**Purpose:** Форматированное отображение процентов с опциональной стрелкой тренда.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `value` | `number \| null` | — | Значение (в процентах, например 18.3) |
| `showTrend` | `boolean` | `false` | Показывать стрелку |
| `colorize` | `boolean` | `false` | Красить по знаку |
| `decimals` | `number` | `1` | Знаков после запятой |

**Visual spec:**
- Font: `JetBrains Mono`, `--text-mono-sm`
- Format: `18,3%` (comma decimal)
- Trend: `↑ 8,2%` зелёный, `↓ 2,1%` красный, `→ 0,0%` серый
- Null: `—`

---

### DateDisplay

**Angular:** `DateDisplayComponent`
**Phase:** A
**Purpose:** Форматированная дата в русской локали.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `value` | `string \| Date \| null` | — | ISO date/datetime |
| `format` | `'date' \| 'datetime' \| 'relative'` | `'date'` | Режим |
| `relativeThreshold` | `number` | `24` | Часы, до которых показывать relative |

**Formatting:**
- `date`: `28 мар 2026` (русский короткий месяц)
- `datetime`: `28 мар, 14:32` (24-hour, no seconds)
- `relative`: `12 мин назад`, `3 ч назад`, `вчера`. Свыше `relativeThreshold` → absolute date
- Null: `—`

**Dependencies:** `date-fns` с `locale/ru`.

---

### SparklineChart

**Angular:** `SparklineChartComponent`
**Phase:** E
**Purpose:** Мини inline-график в ячейке грида. 7-дневный тренд цены или остатков.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `data` | `number[]` | `[]` | Массив значений (7 точек) |
| `color` | `string` | `'--accent-primary'` | CSS token цвета линии |
| `height` | `number` | `24` | Высота (px) |
| `width` | `number` | `80` | Ширина (px) |
| `showArea` | `boolean` | `true` | Показывать заливку под линией |

**Visual spec:**
- SVG inline, no axes/labels
- Line: 1.5px stroke
- Area fill: 10% opacity of line color
- No animation (static for grid performance)

**Accessibility:**
- `aria-label="Тренд за 7 дней: от {min} до {max}"`
- `role="img"`

---

### ExplanationBlock

**Angular:** `ExplanationBlockComponent`
**Phase:** C
**Purpose:** Structured display для pricing/promo decision explanation. Парсит формат `[Секция] Содержание`.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `explanationText` | `string` | `''` | Raw explanation_summary текст |
| `decisionType` | `string` | — | CHANGE / SKIP / HOLD для цветовой акцентуации |

**Visual spec:**
- Container: padding `--space-3`, background `--bg-secondary`, border-radius `--radius-md`
- Section label (`[Решение]`, `[Политика]`, etc.): `--text-sm`, semi-bold, `--text-secondary`
- Section value: `--text-sm`, `--text-primary`
- CHANGE: `[Решение]` label accent `--accent-primary`
- SKIP: `[Решение]` label accent `--status-neutral`
- HOLD: `[Решение]` label accent `--status-warning`
- Numbers in text: `JetBrains Mono` span
- Price change: highlight span with background `--accent-subtle`
- Line spacing: `--space-1` between sections

**Accessibility:**
- Semantic `<dl>` with `<dt>` for labels and `<dd>` for values

**Usage:** Detail Panel → Price Journal entry, Decision detail view.

---

### ProgressIndicator

**Angular:** `ProgressIndicatorComponent`
**Phase:** A (onboarding), D (action lifecycle)
**Purpose:** Step indicator для визардов и action lifecycle.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `steps` | `ProgressStep[]` | `[]` | Шаги |
| `currentStep` | `number` | `0` | Текущий шаг (0-based) |
| `orientation` | `'horizontal' \| 'vertical'` | `'horizontal'` | Ориентация |

Где `ProgressStep`:
```typescript
interface ProgressStep {
  label: string;
  description?: string;
  status: 'pending' | 'active' | 'completed' | 'error';
}
```

**Visual spec:**
- Circle: 24px diameter
  - Pending: border 2px `--border-default`, empty
  - Active: fill `--accent-primary`, white number
  - Completed: fill `--status-success`, white checkmark icon
  - Error: fill `--status-error`, white × icon
- Connector line: 2px, between circles
  - Before current: `--status-success`
  - After current: `--border-default`
- Label: `--text-sm`, below circle (horizontal) or right (vertical)

**Accessibility:**
- `role="progressbar"` with `aria-valuenow`, `aria-valuemin`, `aria-valuemax`
- Step labels announced

---

### EmptyState

**Angular:** `EmptyStateComponent`
**Phase:** A
**Purpose:** Placeholder когда нет данных. Variants для разных контекстов.

**Variants:** `no-data`, `no-results`, `no-selection`, `first-time`

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `variant` | `string` | `'no-data'` | Тип пустого состояния |
| `title` | `string` | — | Заголовок |
| `description` | `string \| null` | `null` | Описание |
| `icon` | `string` | `'inbox'` | Lucide icon |
| `actionLabel` | `string \| null` | `null` | Текст CTA-кнопки |

**Outputs:**

| Output | Type | Описание |
|--------|------|----------|
| `actionClicked` | `EventEmitter<void>` | CTA кнопка нажата |

**Visual spec:**
- Centered in container, max-width 400px
- Icon: 48×48, `--text-tertiary`, top
- Title: `--text-lg`, `--text-primary`, margin-top `--space-4`
- Description: `--text-base`, `--text-secondary`, margin-top `--space-2`
- CTA button: Secondary variant, margin-top `--space-4`
- No illustrations (design direction: minimal, no decorative elements)

**Preset variants:**
- `no-data`: icon `inbox`, title «Нет данных»
- `no-results`: icon `search`, title «Ничего не найдено», description «Попробуйте изменить фильтры», action «Сбросить фильтры»
- `no-selection`: icon `mouse-pointer`, title «Выберите элемент»
- `first-time`: icon `rocket`, title «Здесь пока пусто», description «Настройте подключение к маркетплейсу», action «Перейти в настройки»

---

## 3. Forms & Input

### TextInput

**Angular:** `TextInputComponent`
**Phase:** A
**Purpose:** Универсальное текстовое поле с label, validation, disabled state.

**Variants:** `text`, `password`, `textarea`, `monospace`

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `label` | `string` | — | Метка над полем |
| `placeholder` | `string` | `''` | Placeholder text |
| `value` | `string` | `''` | Текущее значение |
| `variant` | `'text' \| 'password' \| 'textarea' \| 'monospace'` | `'text'` | Тип |
| `error` | `string \| null` | `null` | Текст ошибки валидации |
| `disabled` | `boolean` | `false` | Disabled state |
| `required` | `boolean` | `false` | Показать * у label |
| `hint` | `string \| null` | `null` | Подсказка под полем |
| `maxLength` | `number \| null` | `null` | Максимальная длина |
| `rows` | `number` | `3` | Строки для textarea |

**Visual spec:**
- Label: `--text-sm`, `--text-secondary`, margin-bottom `--space-1`
- Required marker: ` *` в `--status-error`
- Input height: 32px (compact), `--text-base`
- Border: 1px `--border-default`, radius `--radius-md`
- Focus: border 2px `--border-focus`, no shadow
- Error: border `--status-error`, error text below in `--text-xs`, `--status-error`
- Disabled: `--bg-secondary`, `--text-tertiary`, cursor not-allowed
- Monospace: `font-family: JetBrains Mono` (для API токенов)
- Textarea: min-height from `rows`, resizable vertical
- Padding: `--space-2` horizontal, `--space-1` vertical

**States:** default, focus, error, disabled, readonly

**Behavior:**
- Validation errors appear on blur, not on keystroke
- Password: toggle visibility icon (eye/eye-off)

**Accessibility:**
- `<label>` with `for` attribute linked to input
- `aria-invalid="true"` when error
- `aria-describedby` pointing to error/hint text

---

### NumberInput

**Angular:** `NumberInputComponent`
**Phase:** A
**Purpose:** Числовое поле с правым выравниванием, monospace шрифтом, optional currency suffix.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `label` | `string` | — | Метка |
| `value` | `number \| null` | `null` | Значение |
| `min` | `number \| null` | `null` | Минимум |
| `max` | `number \| null` | `null` | Максимум |
| `step` | `number` | `1` | Шаг |
| `decimals` | `number` | `2` | Знаков после запятой |
| `suffix` | `string \| null` | `null` | Суффикс (₽, %, шт.) |
| `showStepButtons` | `boolean` | `false` | Кнопки ±step |
| `error` | `string \| null` | `null` | Текст ошибки |
| `disabled` | `boolean` | `false` | Disabled |

**Visual spec:**
- Same dimensions as TextInput (32px height)
- Font: `JetBrains Mono`, right-aligned
- Suffix: `--text-secondary`, inside input right padding
- Step buttons: small ▲▼ on right edge, visible on hover/focus

**Behavior:**
- Arrow Up/Down: increment/decrement by step
- Shift+Arrow: increment by step×10
- Comma and dot both accepted as decimal separator
- Formats on blur (adds trailing zeros to match `decimals`)

---

### SelectDropdown

**Angular:** `SelectDropdownComponent`
**Phase:** A
**Purpose:** Single-select dropdown с поиском.

**Variants:** `simple`, `grouped`, `with-badges`

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `label` | `string` | — | Метка |
| `options` | `SelectOption[]` | `[]` | Список опций |
| `value` | `any \| null` | `null` | Выбранное значение |
| `placeholder` | `string` | `'Выберите...'` | Placeholder |
| `searchable` | `boolean` | `false` | Показывать поиск (auto-enabled при >10 опций) |
| `disabled` | `boolean` | `false` | Disabled |
| `error` | `string \| null` | `null` | Ошибка |

Где `SelectOption`:
```typescript
interface SelectOption {
  value: any;
  label: string;
  group?: string;
  badge?: { text: string; color: string };
  disabled?: boolean;
}
```

**Visual spec:**
- Trigger: same as TextInput (32px), chevron-down icon right
- Dropdown: `--bg-primary`, border 1px `--border-default`, shadow `--shadow-md`, max-height 300px, scrollable
- Option: height 32px, padding `--space-2`, `--text-sm`
- Option hover: `--bg-tertiary`
- Selected option: `--bg-active`, checkmark icon left
- Search input: sticky top in dropdown, border-bottom `--border-default`
- Group header: `--text-xs`, `--text-tertiary`, uppercase, padding top `--space-2`
- Badge variant: `MarketplaceBadge` or `StatusBadge` right-aligned in option

**Behavior:**
- Click trigger → open dropdown
- Type to search (debounced 150ms, filters options by label)
- Arrow keys navigate, Enter selects, Escape closes
- Click outside closes

**Accessibility:**
- `role="listbox"` for dropdown, `role="option"` for items
- `aria-expanded` on trigger
- `aria-activedescendant` tracks highlighted option

---

### MultiSelect

**Angular:** `MultiSelectComponent`
**Phase:** E
**Purpose:** Multi-select с chips и поиском.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `label` | `string` | — | Метка |
| `options` | `SelectOption[]` | `[]` | Список опций |
| `value` | `any[]` | `[]` | Выбранные значения |
| `placeholder` | `string` | `'Выберите...'` | Placeholder |
| `maxVisible` | `number` | `3` | Макс. видимых chips (остальные `+N`) |

**Visual spec:**
- Selected values shown as chips inside trigger area
- Chip: height 22px, `--bg-tertiary`, `--text-sm`, radius `--radius-sm`, × to remove
- `+N` counter: same style chip but `--accent-subtle` background

**Behavior:**
- Dropdown stays open after selection (multi-select)
- Backspace removes last chip
- Clear all button when ≥1 selected

---

### DatePicker

**Angular:** `DatePickerComponent`
**Phase:** B
**Purpose:** Выбор даты. Русская локаль, неделя начинается с понедельника.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `label` | `string` | — | Метка |
| `value` | `string \| null` | `null` | ISO date string |
| `min` | `string \| null` | `null` | Минимальная дата |
| `max` | `string \| null` | `null` | Максимальная дата |
| `disabled` | `boolean` | `false` | Disabled |

**Visual spec:**
- Trigger: TextInput style с calendar icon right
- Calendar popup: 280px wide, shadow `--shadow-md`
- Header: month/year navigation with chevron arrows
- Day names: Пн Вт Ср Чт Пт Сб Вс (Russian short)
- Day cells: 32×32, `--text-sm`
- Today: ring `--accent-primary`
- Selected: fill `--accent-primary`, white text
- Out-of-range: `--text-tertiary`, not clickable

**Behavior:**
- Arrow keys navigate days, Enter selects
- Month/year navigation via header arrows
- Type date directly in input (auto-parse DD.MM.YYYY)

---

### DateRangePicker

**Angular:** `DateRangePickerComponent`
**Phase:** B
**Purpose:** Выбор диапазона дат с пресетами.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `label` | `string` | — | Метка |
| `from` | `string \| null` | `null` | Начальная дата |
| `to` | `string \| null` | `null` | Конечная дата |
| `presets` | `DatePreset[]` | default presets | Пресеты |

**Default presets:**
- Сегодня
- Вчера
- Последние 7 дней
- Последние 30 дней
- Текущий месяц
- Прошлый месяц
- Текущий квартал
- Произвольный период

**Visual spec:**
- Two-field layout: «От» / «До» with calendar icon
- Preset chips row above fields
- Active preset: `--accent-subtle` background
- Calendar popup: two months side-by-side
- Range highlight: `--accent-subtle` background between from/to

---

### ToggleSwitch

**Angular:** `ToggleSwitchComponent`
**Phase:** A
**Purpose:** On/off toggle с label.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `label` | `string` | — | Метка |
| `checked` | `boolean` | `false` | Состояние |
| `disabled` | `boolean` | `false` | Disabled |

**Visual spec:**
- Track: 36×20px, radius `--radius-full`
- Off: `--border-default` border, `--bg-secondary` fill
- On: `--accent-primary` fill
- Thumb: 16px circle, white, shadow `--shadow-sm`
- Label: `--text-base`, right of toggle, gap `--space-2`
- Disabled: 50% opacity

**Behavior:**
- Click toggles
- Space/Enter toggles when focused

**Accessibility:**
- `role="switch"`, `aria-checked`

---

### RadioGroup

**Angular:** `RadioGroupComponent`
**Phase:** A

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `label` | `string` | — | Group label |
| `options` | `{value: any, label: string, description?: string}[]` | `[]` | Опции |
| `value` | `any` | — | Выбранное значение |
| `orientation` | `'vertical' \| 'horizontal'` | `'vertical'` | Layout |
| `disabled` | `boolean` | `false` | Disabled |

**Visual spec:**
- Radio circle: 16px, border 2px `--border-default`, selected inner dot 8px `--accent-primary`
- Option label: `--text-base`, gap `--space-2` от radio
- Description: `--text-sm`, `--text-secondary`
- Gap between options: `--space-2` (horizontal), `--space-3` (vertical)

---

### FilterBar

**Angular:** `FilterBarComponent`
**Phase:** E
**Purpose:** Горизонтальная полоса фильтров над гридом. Filters как компактные pills.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `filters` | `ActiveFilter[]` | `[]` | Активные фильтры |
| `availableFields` | `FilterField[]` | `[]` | Доступные поля для фильтрации |

Где:
```typescript
interface ActiveFilter {
  field: string;
  operator: string;
  value: any;
  label: string;  // human-readable display
}

interface FilterField {
  field: string;
  label: string;
  type: 'enum' | 'text' | 'number-range' | 'boolean' | 'date-range';
  options?: { value: any; label: string }[];  // for enum type
}
```

**Outputs:**

| Output | Type | Описание |
|--------|------|----------|
| `filterAdded` | `EventEmitter<ActiveFilter>` | Фильтр добавлен |
| `filterRemoved` | `EventEmitter<string>` | Field name — фильтр удалён |
| `filterChanged` | `EventEmitter<ActiveFilter>` | Фильтр изменён |
| `allCleared` | `EventEmitter<void>` | Все фильтры сброшены |

**Visual spec:**
- Container: height auto (wrap), padding `--space-2 --space-4`, gap `--space-2`
- Filter pill: height 28px, padding `0 --space-2`, `--bg-tertiary`, radius `--radius-sm`
  - Content: «Field: Value» in `--text-sm`
  - Close ×: 14px, `--text-tertiary`, hover `--text-primary`
- «+ Добавить фильтр» button: ghost style, `--text-secondary`, `filter` icon
- «Сбросить все» link: `--text-xs`, `--text-secondary`, visible when ≥1 active filter

**Behavior:**
- Click pill → edit dropdown for that filter (inline, not modal)
- Click × on pill → remove
- «+ Добавить фильтр» → dropdown with available fields grouped by type
- Filters persisted per tab / saved view

**Accessibility:**
- Toolbar pattern (`role="toolbar"`)
- Each pill: `role="button"`, `aria-label="Фильтр: {field} — {value}. Нажмите для редактирования"`

---

### SearchInput

**Angular:** `SearchInputComponent`
**Phase:** A
**Purpose:** Поле поиска с иконкой, debounce, shortcut hint.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `placeholder` | `string` | `'Поиск...'` | Placeholder |
| `value` | `string` | `''` | Текущее значение |
| `debounceMs` | `number` | `300` | Debounce delay |
| `shortcutHint` | `string \| null` | `'Ctrl+K'` | Подсказка shortcut |
| `autoFocus` | `boolean` | `false` | Auto-focus при появлении |

**Visual spec:**
- Search icon (`search`) left, `--text-tertiary`
- Shortcut hint right: `--text-xs`, `--text-tertiary`, `--bg-tertiary` background, padding `--space-0.5 --space-1`, radius `--radius-sm`
- Clear ×: appears when value not empty
- Height: 32px, same border/focus as TextInput

---

### InlineEdit

**Angular:** `InlineEditComponent`
**Phase:** E
**Purpose:** Click-to-edit для ячеек грида. Modes: text, number, toggle.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `value` | `any` | — | Текущее значение |
| `mode` | `'text' \| 'number' \| 'toggle'` | `'text'` | Тип |
| `editing` | `boolean` | `false` | В режиме редактирования |
| `suffix` | `string \| null` | `null` | Суффикс для number (₽) |

**Outputs:**

| Output | Type | Описание |
|--------|------|----------|
| `saved` | `EventEmitter<any>` | Новое значение сохранено |
| `cancelled` | `EventEmitter<void>` | Редактирование отменено |

**Visual spec:**
- Display mode: plain text, pencil icon appears on hover (right side, `--text-tertiary`)
- Edit mode: input replaces text, blue focus ring, same cell dimensions
- Toggle mode: `ToggleSwitch` inline

**Behavior:**
- Double-click → edit mode
- Enter / blur → save (optimistic update)
- Escape → cancel (revert)
- On error → rollback value, show toast error

**Usage:** Operational Grid: `cost_price` (number), `manual_lock` (toggle).

---

### FileUpload

**Angular:** `FileUploadComponent`
**Phase:** E
**Purpose:** Drag-and-drop zone + browse button для CSV import.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `accept` | `string` | `'.csv'` | Допустимые типы файлов |
| `maxSizeMb` | `number` | `10` | Максимальный размер файла |
| `label` | `string` | `'Перетащите файл сюда'` | Текст в зоне |

**Visual spec:**
- Zone: dashed border 2px `--border-default`, radius `--radius-lg`, padding `--space-8`
- Icon: `upload`, 32px, `--text-tertiary`, centered
- Label: `--text-base`, `--text-secondary`
- Sub-label: `--text-sm`, `--text-tertiary`, «CSV, до {maxSizeMb} МБ»
- Browse button: secondary variant, below label
- Drag-over: border color `--accent-primary`, background `--accent-subtle`

---

## 4. Feedback & Overlay

### ToastNotification

**Angular:** `ToastNotificationComponent` (managed by `ToastService`)
**Phase:** A
**Purpose:** Уведомление в правом нижнем углу. Severity-based стилизация.

**Props/Inputs (internal, created by service):**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `severity` | `'success' \| 'info' \| 'warning' \| 'error'` | — | Тип |
| `title` | `string` | — | Заголовок |
| `message` | `string \| null` | `null` | Тело |
| `actionLabel` | `string \| null` | `null` | Текст action link |
| `duration` | `number` | severity-based | Автоматическое закрытие (ms) |

**Auto-dismiss timing:**
- Success: 3000ms
- Info: 3000ms
- Warning: 5000ms
- Error: 8000ms (manual dismiss also available)

**Visual spec:**
- Position: fixed bottom-right, offset `--space-4` from edges
- Stack: max 3 visible, stacked upward with `--space-2` gap
- Width: 360px
- Background: `--bg-toast`
- Left border: 3px solid, color by severity token
- Shadow: `--shadow-md`
- Padding: `--space-3`
- Icon: left side, 20px, color by severity (check-circle/info/alert-triangle/alert-circle)
- Title: `--text-sm` semi-bold, `--text-primary`
- Message: `--text-sm`, `--text-secondary`
- Close ×: top-right, `--text-tertiary`
- Action link: `--text-sm`, `--accent-primary`, underline

**Behavior:**
- Slide-in from right (`--duration-toast`)
- Auto-dismiss with progress bar (2px bottom, decreasing)
- Hover pauses auto-dismiss timer
- Click action → callback, dismiss toast
- New toasts push older ones up; oldest dismissed if >3

**Accessibility:**
- `role="alert"` for error/warning, `role="status"` for success/info
- `aria-live="polite"` (success/info) or `aria-live="assertive"` (error/warning)

---

### ConfirmationModal

**Angular:** `ConfirmationModalComponent`
**Phase:** A
**Purpose:** Модальное подтверждение действия. Normal и destructive варианты.

**Variants:** `normal`, `destructive`, `type-to-confirm`

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `title` | `string` | — | Заголовок |
| `message` | `string` | — | Тело сообщения |
| `variant` | `'normal' \| 'destructive' \| 'type-to-confirm'` | `'normal'` | Вариант |
| `confirmLabel` | `string` | `'Подтвердить'` | Текст кнопки подтверждения |
| `cancelLabel` | `string` | `'Отменить'` | Текст кнопки отмены |
| `confirmValue` | `string \| null` | `null` | Значение для type-to-confirm |

**Outputs:**

| Output | Type | Описание |
|--------|------|----------|
| `confirmed` | `EventEmitter<void>` | Подтверждено |
| `cancelled` | `EventEmitter<void>` | Отменено |

**Visual spec:**
- Backdrop: `--bg-overlay`, z-index 1000
- Modal: centered, max-width 480px, `--bg-primary`, radius `--radius-lg`, shadow `--shadow-lg`
- Title: `--text-lg`, `--text-primary`, padding `--space-4` top/sides
- Message: `--text-base`, `--text-secondary`, padding `--space-3` sides
- Buttons: row, right-aligned, gap `--space-2`, padding `--space-4`
- Normal confirm: Primary button
- Destructive confirm: Danger button (`--status-error` fill, white text)
- Type-to-confirm: TextInput appears above buttons, confirm disabled until typed value matches `confirmValue`

**Behavior:**
- Escape closes (cancel)
- Click backdrop closes (cancel)
- Focus trapped inside modal
- Enter on confirm button triggers confirm (only when enabled)
- Fade-in `--transition-slow`

**Accessibility:**
- `role="dialog"`, `aria-modal="true"`, `aria-labelledby` → title
- Focus moves to first focusable element on open
- Focus returns to trigger element on close

---

### FormModal

**Angular:** `FormModalComponent`
**Phase:** A
**Purpose:** Модал с формой внутри. Submit/cancel buttons. Loading state при submit.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `title` | `string` | — | Заголовок |
| `submitLabel` | `string` | `'Сохранить'` | Текст submit |
| `cancelLabel` | `string` | `'Отменить'` | Текст cancel |
| `loading` | `boolean` | `false` | Submit in progress |
| `submitDisabled` | `boolean` | `false` | Submit недоступен |

**Visual spec:**
- Same modal chrome as ConfirmationModal
- Max-width: 560px
- Body: content projection slot for form
- Footer: submit (Primary button) + cancel (Secondary), right-aligned
- Loading: submit button shows spinner, disabled

---

### LoadingSpinner

**Angular:** `LoadingSpinnerComponent`
**Phase:** A

**Variants:** `page`, `inline`, `button`

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `variant` | `'page' \| 'inline' \| 'button'` | `'inline'` | Размер/расположение |
| `label` | `string \| null` | `null` | Текст под спиннером |

**Visual spec:**
- Page: centered in container, 32px spinner, optional label below
- Inline: 16px spinner, inline with text
- Button: 14px spinner, replaces button icon/text
- Animation: CSS rotate, `--accent-primary` partial circle

---

### LoadingSkeleton

**Angular:** `LoadingSkeletonComponent`
**Phase:** A
**Purpose:** Gray shimmer blocks, matching content layout для initial load.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `variant` | `'text' \| 'card' \| 'row' \| 'grid'` | `'text'` | Preset layout |
| `lines` | `number` | `3` | Строк для text variant |
| `rows` | `number` | `5` | Строк для grid variant |

**Visual spec:**
- Background: `--bg-tertiary`
- Shimmer: gradient animation left-to-right, 1.5s duration, infinite
- Radius: `--radius-sm`
- Spacing: matches real content spacing

---

### ErrorState

**Angular:** `ErrorStateComponent`
**Phase:** A
**Purpose:** Экран ошибки. Variants: full-page, inline, banner.

**Variants:** `page`, `inline`, `banner`

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `variant` | `'page' \| 'inline' \| 'banner'` | `'inline'` | Тип |
| `title` | `string` | `'Произошла ошибка'` | Заголовок |
| `message` | `string \| null` | `null` | Описание |
| `retryable` | `boolean` | `true` | Показывать кнопку retry |

**Outputs:**

| Output | Type | Описание |
|--------|------|----------|
| `retryClicked` | `EventEmitter<void>` | Retry |

**Visual spec:**
- Page: centered, `alert-circle` icon 48px in `--status-error`, title `--text-lg`, «Повторить» Primary button
- Inline: compact, icon 24px, single line, «Повторить» ghost button
- Banner: full-width strip, `--status-error-bg`, icon + text + «Повторить» link, height 40px

---

### ConnectionLostBanner

**Angular:** `ConnectionLostBannerComponent`
**Phase:** A
**Purpose:** Persistent full-width banner при потере WebSocket-соединения.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `reconnecting` | `boolean` | `true` | Идёт ли переподключение |

**Visual spec:**
- Position: below TopBar, full-width, z-index 500
- Height: 32px
- Background: `--status-warning-bg`
- Left icon: `wifi-off`, 16px, `--status-warning`
- Text: `--text-sm`, `--text-primary`, «Соединение потеряно. Переподключение...»
- No dismiss button (auto-dismisses when reconnected)

---

### AutomationBlockerBanner

**Angular:** `AutomationBlockerBannerComponent`
**Phase:** B
**Purpose:** Full-width banner когда автоматизация заблокирована из-за stale данных.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `connectionName` | `string` | — | Имя подключения |
| `reason` | `string` | — | Причина блокировки |
| `hoursStale` | `number` | — | Часов с последней синхронизации |

**Outputs:**

| Output | Type | Описание |
|--------|------|----------|
| `dismissed` | `EventEmitter<void>` | Закрыт |
| `detailsClicked` | `EventEmitter<void>` | «Подробнее» нажато |

**Visual spec:**
- Position: below TopBar (or below ConnectionLostBanner), full-width
- Height: 36px, padding `--space-2 --space-4`
- Background: `--status-warning-bg`
- Left icon: `alert-triangle`, 16px, `--status-warning`
- Text: `--text-sm`, `--text-primary`
- Template: «Автоматизация приостановлена: {connectionName} — данные устарели на {hoursStale} ч. {reason}»
- «Подробнее» link: `--accent-primary`
- Close ×: right side

---

## 5. Navigation

### ActivityBar

**Angular:** `ActivityBarComponent`
**Phase:** A
**Purpose:** Вертикальная панель навигации (левый край). Иконки модулей.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `modules` | `ActivityBarItem[]` | `[]` | Модули |
| `activeModuleId` | `string` | `''` | ID активного модуля |
| `bottomItems` | `ActivityBarItem[]` | `[]` | Нижние items (Settings) |

Где `ActivityBarItem`:
```typescript
interface ActivityBarItem {
  id: string;
  icon: string;      // Lucide icon name
  label: string;     // Tooltip text (RU)
  badge?: number;     // Notification count
}
```

**Visual spec:**
- Width: 48px
- Background: `--bg-secondary`
- Border-right: 1px `--border-default`
- Icon container: 40×40px, centered, radius `--radius-md`
- Icon: 20px, `--text-secondary`
- Active: left accent bar 3px `--accent-primary`, icon `--text-primary`, background `--bg-active`
- Hover: `--bg-tertiary`
- Badge: 16px circle, `--status-error` fill, white text `--text-xs`, top-right of icon
- Separator: horizontal line `--border-default` between main and bottom items
- Tooltip: on hover, `--shadow-sm`, `--bg-primary`, `--text-sm`, appears right of bar, delay 300ms

**Behavior:**
- Click icon → navigate to module
- Module items top-aligned, settings bottom-aligned

**Accessibility:**
- `<nav aria-label="Основная навигация">`
- Each item: `role="link"`, `aria-label="{label}"`, `aria-current="page"` for active

---

### Breadcrumbs

**Angular:** `BreadcrumbsComponent`
**Phase:** A
**Purpose:** Навигационные крошки в TopBar.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `segments` | `BreadcrumbSegment[]` | `[]` | Сегменты пути |

Где `BreadcrumbSegment`:
```typescript
interface BreadcrumbSegment {
  label: string;
  routerLink?: string | any[];
  icon?: string;
}
```

**Visual spec:**
- Separator: `chevron-right` icon, 12px, `--text-tertiary`
- Segment: `--text-sm`, `--text-secondary`, clickable
- Last segment: `--text-primary`, not clickable
- Max depth: 3 levels
- Overflow: first segment truncated with ellipsis

**Accessibility:**
- `<nav aria-label="Навигационная цепочка">`
- `<ol>` list with `<li>` items, `aria-current="page"` on last

---

### CommandPalette

**Angular:** `CommandPaletteComponent`
**Phase:** E
**Purpose:** Ctrl+K floating search с grouped results. Аналог Cursor Command Palette.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `isOpen` | `boolean` | `false` | Видимость |

**Visual spec:**
- Position: centered, top 20% of viewport
- Width: 560px, max-height 400px
- Background: `--bg-primary`, radius `--radius-lg`, shadow `--shadow-lg`
- Backdrop: `--bg-overlay`
- Search input: height 48px, `--text-lg`, no border, focus auto
- Results grouped by type: «Товары», «Представления», «Команды»
- Group header: `--text-xs`, `--text-tertiary`, uppercase
- Result item: height 36px, icon left, label + subtitle, `--text-sm`
- Highlighted item: `--bg-tertiary`
- Shortcut hints: right-aligned, `--text-xs`, `--text-tertiary`, monospace

**Behavior:**
- `Ctrl+K` toggles open/close
- Type → debounce 200ms → search
- Arrow keys navigate results
- Enter activates selected result
- Escape closes
- Results: entities (offers, products), saved views, commands (export, manual sync, etc.)

**Accessibility:**
- `role="combobox"` on input
- `role="listbox"` on results
- `aria-activedescendant` for highlighted

---

### SidebarNav

**Angular:** `SidebarNavComponent`
**Phase:** A
**Purpose:** Navigation list для Settings-style pages.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `sections` | `NavSection[]` | `[]` | Секции навигации |
| `activeItemId` | `string` | `''` | Активный item |

Где `NavSection`:
```typescript
interface NavSection {
  title?: string;
  items: { id: string; label: string; icon?: string; badge?: number }[];
}
```

**Visual spec:**
- Width: 240px, `--bg-secondary`, border-right 1px `--border-default`
- Section title: `--text-xs`, `--text-tertiary`, uppercase, margin-top `--space-4`
- Item: height 32px, padding `--space-2 --space-3`, `--text-sm`
- Active: `--bg-active`, `--text-primary`, left border 2px `--accent-primary`
- Hover: `--bg-tertiary`

---

### PaginationBar

**Angular:** `PaginationBarComponent`
**Phase:** A
**Purpose:** Полоса пагинации под гридом.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `currentPage` | `number` | `0` | Текущая страница (0-based) |
| `pageSize` | `number` | `50` | Размер страницы |
| `totalItems` | `number` | `0` | Общее количество |
| `pageSizeOptions` | `number[]` | `[50, 100, 200]` | Варианты размера |

**Outputs:**

| Output | Type | Описание |
|--------|------|----------|
| `pageChanged` | `EventEmitter<{page: number, size: number}>` | Страница/размер изменены |

**Visual spec:**
- Height: 36px, border-top 1px `--border-default`, padding `--space-2 --space-4`
- Left: «Показано 1–50 из 1 234» in `--text-sm`, `--text-secondary`
- Center: page numbers (1 2 3 ... 25), current page `--accent-primary` fill, others ghost
- Right: «Строк на странице» dropdown (50 / 100 / 200), `--text-sm`
- Prev/Next: chevron icons, disabled at boundaries

---

## 6. Domain-Specific Components

### StateMachineVisualizer

**Angular:** `StateMachineVisualizerComponent`
**Phase:** D
**Purpose:** Горизонтальный pipeline состояний для action lifecycle. Current state highlighted, completed checkmarked.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `states` | `StateMachineState[]` | `[]` | Все состояния pipeline |
| `currentState` | `string` | — | Текущее состояние |
| `terminalStates` | `string[]` | `[]` | Терминальные состояния |
| `branchStates` | `{from: string, to: string}[]` | `[]` | Ответвления (ON_HOLD, RETRY) |

Где `StateMachineState`:
```typescript
interface StateMachineState {
  id: string;
  label: string;
  status: 'completed' | 'current' | 'pending' | 'error' | 'skipped';
}
```

**Visual spec:**
- Main pipeline: horizontal row of circles (24px) connected by lines
- Completed: `--status-success` fill, white checkmark
- Current: `--accent-primary` fill, white text, pulsing ring animation
- Pending: border `--border-default`, no fill
- Error (terminal): `--status-error` fill, white × icon
- Skipped (terminal): `--status-neutral` fill, white `minus` icon
- Branch states: shown below main line, connected with downward connector
- Labels: `--text-xs` below each circle

**Usage:** Detail Panel → Action lifecycle (price_action, promo_action).

---

### PriceChangeIndicator

**Angular:** `PriceChangeIndicatorComponent`
**Phase:** C
**Purpose:** Визуализация изменения цены: old → new, delta %, color-coded.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `oldPrice` | `number` | — | Старая цена |
| `newPrice` | `number` | — | Новая цена |
| `currency` | `string` | `'₽'` | Валюта |

**Visual spec:**
- Layout: `{old} → {new} ({delta%})`
- Old price: `--text-mono-sm`, `--text-secondary`, strikethrough
- Arrow: `→`, `--text-tertiary`
- New price: `--text-mono-sm`, `--text-primary`, bold
- Delta: `--text-xs`
  - Decrease: `−15,3%` in `--finance-negative`
  - Increase: `+8,2%` in `--finance-positive`
  - No change: `0,0%` in `--finance-zero`

---

### StockRiskBadge

**Angular:** `StockRiskBadgeComponent`
**Phase:** B
**Purpose:** Stock-out risk indicator с цветовой кодировкой.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `risk` | `'CRITICAL' \| 'WARNING' \| 'NORMAL' \| null` | `null` | Уровень риска |
| `daysOfCover` | `number \| null` | `null` | Дней до stock-out (для tooltip) |

**Visual spec:**
- Same as StatusBadge, but with domain `'stockRisk'`
- CRITICAL: `--status-error`, icon `alert-circle`
- WARNING: `--status-warning`, icon `alert-triangle`
- NORMAL: `--status-success`, no icon (dot only)
- Tooltip: «Запас на {daysOfCover} дней» или «Нет данных о скорости продаж»

---

### SyncFreshnessDot

**Angular:** `SyncFreshnessDotComponent`
**Phase:** A
**Purpose:** Цветная точка свежести данных с tooltip о времени последней синхронизации.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `lastSyncAt` | `string \| null` | `null` | ISO datetime последней синхронизации |
| `thresholdHours` | `number` | `24` | Порог для stale (часов) |

**Visual spec:**
- Dot: 8px circle
- Fresh (within threshold): `--status-success`
- Approaching stale (50-100% of threshold): `--status-warning`
- Stale (over threshold): `--status-error`
- No sync data: `--status-neutral`
- Tooltip: «Синхронизация: {relative time}» / «Данные устарели ({hours} ч)» / «Нет данных»

**Usage:** Status Bar per connection, Column headers in grid.

---

### PolicyScopeChip

**Angular:** `PolicyScopeChipComponent`
**Phase:** C
**Purpose:** Визуальное отображение scope назначения политики.

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `scopeType` | `'CONNECTION' \| 'CATEGORY' \| 'SKU'` | — | Тип scope |
| `connectionName` | `string` | — | Имя подключения |
| `categoryName` | `string \| null` | `null` | Имя категории |
| `skuCode` | `string \| null` | `null` | SKU код |
| `marketplace` | `'WB' \| 'OZON'` | — | Маркетплейс |

**Visual spec:**
- Chip container: inline-flex, `--bg-tertiary`, padding `--space-1 --space-2`, radius `--radius-sm`
- MarketplaceBadge (sm) + scope text in `--text-xs`
- CONNECTION: «{connectionName}»
- CATEGORY: «{connectionName} → {categoryName}»
- SKU: «{connectionName} → {skuCode}»
- Scope icon: `plug` (connection), `folder` (category), `package` (SKU)

---

## Buttons

Определены в `frontend-design-direction.md` §Buttons. Полная спецификация для Angular:

**Angular:** `ButtonComponent`
**Phase:** A

**Props/Inputs:**

| Input | Type | Default | Описание |
|-------|------|---------|----------|
| `variant` | `'primary' \| 'secondary' \| 'ghost' \| 'danger'` | `'primary'` | Стиль |
| `size` | `'sm' \| 'md'` | `'md'` | Размер |
| `icon` | `string \| null` | `null` | Lucide icon (left) |
| `iconOnly` | `boolean` | `false` | Только иконка |
| `loading` | `boolean` | `false` | Спиннер вместо контента |
| `disabled` | `boolean` | `false` | Disabled |

**Visual spec:**

| Variant | Background | Text | Border | Hover bg |
|---------|-----------|------|--------|----------|
| Primary | `--accent-primary` | `--text-inverse` | none | `--accent-primary-hover` |
| Secondary | `--bg-primary` | `--text-primary` | 1px `--border-default` | `--bg-tertiary` |
| Ghost | transparent | `--text-secondary` | none | `--bg-tertiary` |
| Danger | `--status-error` | `--text-inverse` | none | `#B91C1C` |

| Size | Height | Padding H | Font | Icon size |
|------|--------|-----------|------|-----------|
| sm | 24px | `--space-2` | `--text-xs` | 14px |
| md | 28px | `--space-3` | `--text-sm` | 16px |

- Icon-only: square (md: 28×28, sm: 24×24), no padding
- Border-radius: `--radius-md`
- No shadows, no gradients
- Disabled: 50% opacity, cursor not-allowed
- Loading: spinner replaces text, button width maintained

**Accessibility:**
- `aria-label` required for icon-only buttons
- `aria-busy="true"` when loading
- `aria-disabled="true"` when disabled

---

## Phasing Summary

| Phase | Components introduced |
|-------|---------------------|
| **A — Foundation** | AppShell, ActivityBar, TopBar, StatusBar, TabStrip, Breadcrumbs, SidebarNav, Button, TextInput, NumberInput, SelectDropdown, RadioGroup, ToggleSwitch, SearchInput, StatusBadge, MarketplaceBadge, MoneyDisplay, PercentDisplay, DateDisplay, SyncFreshnessDot, EmptyState, LoadingSpinner, LoadingSkeleton, ErrorState, ConnectionLostBanner, ConfirmationModal, FormModal, ToastNotification, PaginationBar, ProgressIndicator |
| **B — Trust Analytics** | DataGrid (AG Grid wrapper), KpiCard, PageLayout, DatePicker, DateRangePicker, StockRiskBadge, AutomationBlockerBanner |
| **C — Pricing** | ExplanationBlock, PriceChangeIndicator, PolicyScopeChip |
| **D — Execution** | StateMachineVisualizer |
| **E — Seller Operations** | DetailPanel, SplitLayout, FilterBar, MultiSelect, InlineEdit, FileUpload, SparklineChart, CommandPalette, DataGrid column config panel, DataGrid export |
| **F — Promotions & Simulation** | (reuses existing components; no new shared components) |

---

## Related documents

- [Frontend Design Direction](frontend-design-direction.md) — visual principles, color system, type scale, interaction patterns
- [Seller Operations](../modules/seller-operations.md) — Operational Grid columns, Saved Views, Working Queues
- [Analytics & P&L](../modules/analytics-pnl.md) — P&L formula, inventory intelligence
- [Pricing](../modules/pricing.md) — decision pipeline, explanation format, policies
- [Execution](../modules/execution.md) — action state machine, reconciliation
- [Promotions](../modules/promotions.md) — promo evaluation, participation lifecycle
- [Audit & Alerting](../modules/audit-alerting.md) — alert lifecycle, notification delivery
- [Integration](../modules/integration.md) — connection statuses, sync states
- [Tenancy & IAM](../modules/tenancy-iam.md) — workspace lifecycle, user roles
