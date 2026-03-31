# Settings Module — Frontend Specification

**Module route:** `/workspace/:id/settings`
**Activity Bar position:** Bottom (separated from operational modules)
**Icon:** `lucide:settings` (gear icon)

---

## Table of Contents

- [S-0. Settings Layout](#s-0-settings-layout)
- [S-1. Общие (General)](#s-1-общие-general)
- [S-2. Подключения (Connections)](#s-2-подключения-connections)
- [S-3. Детали подключения (Connection Detail)](#s-3-детали-подключения-connection-detail)
- [S-4. Себестоимость (Cost Profiles)](#s-4-себестоимость-cost-profiles)
- [S-5. Команда (Team Members)](#s-5-команда-team-members)
- [S-6. Приглашения (Invitations)](#s-6-приглашения-invitations)
- [S-7. Правила алертов (Alert Rules)](#s-7-правила-алертов-alert-rules)
- [S-8. Журнал аудита (Audit Log)](#s-8-журнал-аудита-audit-log)
- [User Flows](#user-flows)
- [Permission Matrix (Summary)](#permission-matrix-summary)

---

## Specification Point Legend

Each screen is described using 16 standardized points:

| # | Point | Description |
|---|-------|-------------|
| 1 | Route & title | URL pattern, breadcrumb, page title |
| 2 | Purpose | What user accomplishes on this screen |
| 3 | Layout & wireframe | Visual structure (ASCII wireframe) |
| 4 | Data sources | API endpoints consumed |
| 5 | Data model | Table columns / form fields with types |
| 6 | Components | UI components used (from design system) |
| 7 | Interactions | Click, hover, keyboard, inline edit behaviors |
| 8 | Form specifications | Full form specs (fields, types, validation, defaults) |
| 9 | Validation rules | Client-side and server-side validation |
| 10 | Permissions | Who can view, who can edit, per-field restrictions |
| 11 | Loading states | Skeleton, spinner, progress bar behaviors |
| 12 | Empty states | Message, illustration, call-to-action |
| 13 | Error states | Validation, API, permission-denied handling |
| 14 | Destructive action confirmations | Modal text, type-to-confirm, consequences |
| 15 | Keyboard shortcuts | Screen-specific shortcuts |
| 16 | Phase & priority | Delivery phase (A/B/E/G), dependencies |

---

## S-0. Settings Layout

### 1. Route & title

- **Route:** `/workspace/:id/settings`
- **Default redirect:** `/workspace/:id/settings/general`
- **Breadcrumb:** `Настройки > {Section Name}`
- **Page title:** `Настройки`

### 2. Purpose

Settings shell — frame for all settings sub-sections. Provides sidebar navigation to switch between settings screens. Not a standalone screen with its own content.

### 3. Layout & wireframe

```
┌─────────────────────────────────────────────────────────────┐
│  Top Bar: ... · Настройки                                    │
├────┬──────────┬─────────────────────────────────────────────┤
│    │ Settings │                                             │
│ A  │ Sidebar  │        Section Content Area                 │
│ c  │ (200px)  │                                             │
│ t  │          │        (router-outlet for child routes)     │
│ i  │ ● Общие  │                                             │
│ v  │   Подклю │                                             │
│ i  │   Себест │                                             │
│ t  │   Команд │                                             │
│ y  │   Пригла │                                             │
│    │   Правил │                                             │
│ B  │   Журнал │                                             │
│ a  │          │                                             │
│ r  │          │                                             │
├────┴──────────┴─────────────────────────────────────────────┤
│  Status Bar                                                  │
└─────────────────────────────────────────────────────────────┘
```

**Settings Sidebar** (left, 200px, `--bg-secondary` background):

| Section | Icon | Label | Route suffix |
|---------|------|-------|--------------|
| General | `lucide:building-2` | Общие | `/general` |
| Connections | `lucide:plug` | Подключения | `/connections` |
| Cost Profiles | `lucide:calculator` | Себестоимость | `/cost-profiles` |
| Team | `lucide:users` | Команда | `/team` |
| Invitations | `lucide:mail-plus` | Приглашения | `/invitations` |
| Alert Rules | `lucide:bell-ring` | Правила алертов | `/alert-rules` |
| Audit Log | `lucide:scroll-text` | Журнал аудита | `/audit` |

- Active section: `--bg-active` background + left accent bar (2px `--accent-primary`).
- Sidebar items are clickable rows, 36px height, `--text-sm` (13px).
- Sidebar is not collapsible within Settings.
- Sidebar border-right: 1px `--border-default`.

### 4. Data sources

No direct data fetching — sidebar is static. Section content loaded by child routes.

### 5. Data model

N/A — layout only.

### 6. Components

- **Settings Sidebar:** Custom component, vertical nav list.
- **Router outlet:** Angular `<router-outlet>` for section content.
- **Breadcrumb:** Top Bar breadcrumb updates per section.

### 7. Interactions

- Click sidebar item → navigate to section route.
- Active section highlighted visually.
- Browser back/forward navigates between sections normally.

### 8. Form specifications

N/A — layout only.

### 9. Validation rules

N/A.

### 10. Permissions

Settings icon visible in Activity Bar for **all roles**. Individual sections enforce permissions internally (see each section). Sidebar shows all sections to all roles — permission-denied handled at section level if needed.

### 11. Loading states

Sidebar renders immediately (static). Section content shows own loading state.

### 12. Empty states

N/A.

### 13. Error states

N/A.

### 14. Destructive action confirmations

N/A.

### 15. Keyboard shortcuts

| Shortcut | Action |
|----------|--------|
| `↑ / ↓` | Navigate sidebar items (when sidebar focused) |
| `Enter` | Activate focused sidebar item |

### 16. Phase & priority

**Phase A** — Settings shell is part of Foundation. All sections except Cost Profiles (Phase B) and Alert Rules (Phase B/E) ship with the shell.

---

## S-1. Общие (General)

### 1. Route & title

- **Route:** `/workspace/:id/settings/general`
- **Breadcrumb:** `Настройки > Общие`
- **Page title:** `Общие настройки`

### 2. Purpose

View and edit basic workspace information: name, slug, tenant info. ADMIN+ can rename workspace.

### 3. Layout & wireframe

```
┌──────────────────────────────────────────────┐
│  Общие настройки                              │
│                                              │
│  ┌─ Workspace ─────────────────────────────┐ │
│  │                                         │ │
│  │  Название workspace                     │ │
│  │  ┌──────────────────────────┐           │ │
│  │  │ Мой магазин WB           │ [Сохр.]  │ │
│  │  └──────────────────────────┘           │ │
│  │                                         │ │
│  │  Slug (URL-идентификатор)               │ │
│  │  moy-magazin-wb-a3f2         (readonly) │ │
│  │                                         │ │
│  │  Создан: 28 мар 2026                    │ │
│  │  Статус: ● Активен                      │ │
│  └─────────────────────────────────────────┘ │
│                                              │
│  ┌─ Организация ───────────────────────────┐ │
│  │                                         │ │
│  │  Название: ООО "Ромашка"     (readonly) │ │
│  │  Slug: ooo-romashka-x9k1     (readonly) │ │
│  └─────────────────────────────────────────┘ │
└──────────────────────────────────────────────┘
```

### 4. Data sources

| Action | Method | Endpoint | Notes |
|--------|--------|----------|-------|
| Load workspace | GET | `/api/workspaces/{workspaceId}` | Returns `{ id, name, slug, status, createdAt, tenantId }` |
| Load tenant | GET | `/api/tenants/{tenantId}` | Returns `{ id, name, slug }`. OWNER/ADMIN only |
| Update workspace name | PUT | `/api/workspaces/{workspaceId}` | Body: `{ name }` |

### 5. Data model

| Field | Type | Editable | Display |
|-------|------|----------|---------|
| Workspace name | `string` | Yes (ADMIN+) | Text input, 32px height |
| Workspace slug | `string` | No | Read-only text, `--text-secondary`, monospace |
| Created at | `timestamp` | No | Date format: `28 мар 2026` |
| Status | `enum` | No | Status badge (green: Активен, yellow: Приостановлен, gray: Архивирован) |
| Tenant name | `string` | No | Read-only text |
| Tenant slug | `string` | No | Read-only text, monospace |

### 6. Components

- **Section card** — bordered container (`1px --border-default`, `--radius-md`), with section header.
- **Text input** — for workspace name (standard form input, 32px).
- **Primary button** — "Сохранить" (28px, `--accent-primary`).
- **Status badge** — pill with semantic dot.
- **Read-only field** — label + value pair, `--text-secondary` label, `--text-primary` value.

### 7. Interactions

- Edit workspace name → type in input → click "Сохранить" or press `Enter`.
- Save button disabled until name differs from current value.
- Save button disabled if name is empty or whitespace-only.
- After save: success toast "Сохранено" (3s).
- For VIEWER/ANALYST/OPERATOR/PRICING_MANAGER: name field rendered as read-only text (no input).

### 8. Form specifications

| Field | Label | Type | Required | Min | Max | Default |
|-------|-------|------|----------|-----|-----|---------|
| name | Название workspace | text input | Yes | 1 char (trimmed) | 255 chars | Current name |

### 9. Validation rules

| Rule | Trigger | Message |
|------|---------|---------|
| Name required | Blur / submit | "Название обязательно" |
| Name too long | Input / blur | "Максимум 255 символов" |
| Name whitespace-only | Blur / submit | "Название не может состоять только из пробелов" |

### 10. Permissions

| Role | View | Edit name |
|------|------|-----------|
| OWNER | ✓ | ✓ |
| ADMIN | ✓ | ✓ |
| PRICING_MANAGER | ✓ (workspace section only, tenant hidden) | — |
| OPERATOR | ✓ (workspace section only, tenant hidden) | — |
| ANALYST | ✓ (workspace section only, tenant hidden) | — |
| VIEWER | ✓ (workspace section only, tenant hidden) | — |

Tenant section (`Организация`) visible only to OWNER and ADMIN (GET `/api/tenants/{tenantId}` requires OWNER/ADMIN).

### 11. Loading states

- Workspace data: skeleton shimmer for name and slug fields (2 placeholder lines).
- Tenant data: skeleton shimmer for tenant section.
- Save button shows spinner icon during API call, text changes to "Сохранение...".

### 12. Empty states

N/A — workspace always has a name.

### 13. Error states

| Situation | Handling |
|-----------|----------|
| Save failed (server error) | Error toast: "Не удалось сохранить. Попробуйте ещё раз." with [Повторить] action |
| Save failed (409 conflict) | Error toast: "Название уже используется." |
| Permission denied on save | Error toast: "Нет прав для редактирования." Input reverts to read-only |

### 14. Destructive action confirmations

N/A — no destructive actions on this screen. Workspace archive/suspend are separate admin operations (not on settings UI in Phase A).

### 15. Keyboard shortcuts

| Shortcut | Action |
|----------|--------|
| `Enter` (in name input) | Submit form |
| `Escape` (in name input) | Revert to original value |

### 16. Phase & priority

**Phase A** — Foundation. Simple read/write screen with minimal complexity.

---

## S-2. Подключения (Connections)

### 1. Route & title

- **Route:** `/workspace/:id/settings/connections`
- **Breadcrumb:** `Настройки > Подключения`
- **Page title:** `Подключения`

### 2. Purpose

View all marketplace connections, their health status, and last sync times. Add new connections. Navigate to connection detail for management.

### 3. Layout & wireframe

```
┌──────────────────────────────────────────────────────┐
│  Подключения                      [+ Добавить подключение] │
│                                                      │
│  ┌──────────────────────────────────────────────────┐│
│  │ Название      │ Маркетплейс │ Статус   │Послед. ││
│  │               │             │          │синхр.  ││
│  ├───────────────┼─────────────┼──────────┼────────┤│
│  │ Мой WB        │ [WB]        │ ● Актив. │12мин   ││
│  │ Основной Ozon │ [Ozon]      │ ● Ошибка │2ч назад││
│  │ WB Второй     │ [WB]        │ ○ Откл.  │—       ││
│  └──────────────────────────────────────────────────┘│
│                                                      │
│  Showing 1–3 of 3                                    │
└──────────────────────────────────────────────────────┘
```

### 4. Data sources

| Action | Method | Endpoint | Notes |
|--------|--------|----------|-------|
| Load connections | GET | `/api/connections` | Returns `[{ id, marketplaceType, name, status, lastCheckAt, lastSuccessAt, lastErrorCode }]` |

### 5. Data model

| Column | Source field | Type | Alignment | Sort | Width |
|--------|-------------|------|-----------|------|-------|
| Название | `name` | text | left | ✓ (default, A→Z) | flex |
| Маркетплейс | `marketplaceType` | badge | center | ✓ | 120px |
| Статус | `status` | status badge | center | ✓ | 120px |
| Последняя синхр. | `lastSuccessAt` | relative time | right | ✓ | 140px |

**Status badge mapping:**

| Backend status | Badge label | Color |
|----------------|-------------|-------|
| `PENDING_VALIDATION` | Проверка... | `--status-info` (blue) |
| `ACTIVE` | Активно | `--status-success` (green) |
| `AUTH_FAILED` | Ошибка авторизации | `--status-error` (red) |
| `DISABLED` | Отключено | `--status-neutral` (gray) |
| `ARCHIVED` | Архив | `--status-neutral` (gray) |

**Marketplace badge mapping:**

| Value | Badge text | Badge style |
|-------|-----------|-------------|
| `WB` | WB | Purple background (`#7B2FBE` bg, white text) |
| `OZON` | Ozon | Blue background (`#005BFF` bg, white text) |

### 6. Components

- **Data table** (not AG Grid — simple HTML table for small datasets, max ~10 connections).
- **Primary button** — "Добавить подключение".
- **Status badges** — pill-shaped, semantic colors.
- **Marketplace badges** — provider-branded mini pills.
- **Relative time** — `date-fns/locale/ru` formatDistanceToNow.

### 7. Interactions

| Trigger | Action |
|---------|--------|
| Click row | Navigate to `/workspace/:id/settings/connections/{connectionId}` |
| Click "Добавить подключение" | Open "Add Connection" form (inline section below table, or replaces table) |
| Hover row | `--bg-tertiary` highlight |

### 8. Form specifications

**Add Connection form** (shown inline or as a panel below the table when "Добавить подключение" clicked):

| Field | Label | Type | Required | Notes |
|-------|-------|------|----------|-------|
| marketplaceType | Маркетплейс | Select dropdown | Yes | Options: `WB`, `Ozon` |
| name | Название подключения | Text input | Yes | Max 255 chars |
| credentials | — | Conditional fields | Yes | See below |

**WB credential fields** (shown when marketplaceType = WB):

| Field | Label | Type | Required | Placeholder |
|-------|-------|------|----------|-------------|
| apiToken | API-токен | Textarea (monospace, 3 rows) | Yes | "Вставьте API-токен Wildberries" |

**Ozon credential fields** (shown when marketplaceType = Ozon):

| Field | Label | Type | Required | Placeholder |
|-------|-------|------|----------|-------------|
| clientId | Client-Id | Text input (monospace) | Yes | "Например: 1943980" |
| apiKey | Api-Key | Text input (monospace, password toggle) | Yes | "Вставьте Api-Key" |

**Form buttons:**

| Button | Type | Label |
|--------|------|-------|
| Submit | Primary | Подключить |
| Cancel | Secondary | Отмена |

### 9. Validation rules

| Rule | Trigger | Message |
|------|---------|---------|
| Marketplace required | Submit | "Выберите маркетплейс" |
| Name required | Blur / submit | "Введите название подключения" |
| Name max length | Input | "Максимум 255 символов" |
| WB: API token required | Blur / submit | "Введите API-токен" |
| WB: API token non-blank | Blur / submit | "API-токен не может быть пустым" |
| Ozon: Client-Id required | Blur / submit | "Введите Client-Id" |
| Ozon: Api-Key required | Blur / submit | "Введите Api-Key" |

### 10. Permissions

| Role | View list | Add connection |
|------|-----------|----------------|
| OWNER | ✓ | ✓ |
| ADMIN | ✓ | ✓ |
| PRICING_MANAGER | ✓ | — |
| OPERATOR | ✓ | — |
| ANALYST | ✓ | — |
| VIEWER | ✓ | — |

"Добавить подключение" button hidden for roles below ADMIN.

### 11. Loading states

- Table: skeleton rows (3 placeholder rows with shimmer animation).
- Add form submit: button shows spinner, text "Подключение...". Form fields disabled during submission.

### 12. Empty states

**No connections yet:**

```
┌──────────────────────────────────────────────┐
│                                              │
│  Нет подключений                             │
│                                              │
│  Подключите маркетплейс, чтобы начать        │
│  загрузку данных.                            │
│                                              │
│  [+ Добавить подключение]                    │
│                                              │
└──────────────────────────────────────────────┘
```

### 13. Error states

| Situation | Handling |
|-----------|----------|
| Failed to load connections | Error toast + "Не удалось загрузить подключения. [Повторить]" |
| Create connection failed (server) | Error toast: "Не удалось создать подключение. Попробуйте ещё раз." |
| Create connection: validation failed (AUTH_FAILED) | After creation, status shows "Ошибка авторизации". Inline warning below form: "Учётные данные не прошли проверку. Проверьте токен и попробуйте ещё раз." |
| Duplicate connection (same marketplace + external_account_id) | Error toast: "Подключение для этого кабинета уже существует." |

### 14. Destructive action confirmations

N/A on this screen (deletion is on Connection Detail).

### 15. Keyboard shortcuts

| Shortcut | Action |
|----------|--------|
| `Enter` (in form) | Submit form |
| `Escape` (in form) | Close/cancel form |

### 16. Phase & priority

**Phase A** — Foundation. Critical path for onboarding.

---

## S-3. Детали подключения (Connection Detail)

### 1. Route & title

- **Route:** `/workspace/:id/settings/connections/:connectionId`
- **Breadcrumb:** `Настройки > Подключения > {Connection Name}`
- **Page title:** `{Connection Name}`

### 2. Purpose

Full management of a single marketplace connection: view info, update credentials, monitor sync health per domain, trigger manual sync, review API call log, disable/delete connection.

### 3. Layout & wireframe

```
┌──────────────────────────────────────────────────────────┐
│  ← Подключения    Мой WB  [WB]  ● Активно               │
│                                                          │
│  ┌─ Информация ──────────────────────────────────────┐   │
│  │  Название: [Мой WB              ] [Сохранить]     │   │
│  │  Маркетплейс: Wildberries         Создан: 15 мар  │   │
│  │  Статус: ● Активно                                │   │
│  └───────────────────────────────────────────────────┘   │
│                                                          │
│  ┌─ Учётные данные ──────────────────────────────────┐   │
│  │  Тип: API-токен WB                                │   │
│  │  Последнее обновление: 15 мар 2026                │   │
│  │                                                   │   │
│  │  [Обновить учётные данные]                        │   │
│  └───────────────────────────────────────────────────┘   │
│                                                          │
│  ┌─ Синхронизация ───────────────────────────────────┐   │
│  │                                                   │   │
│  │  Домен      │ Статус  │ Последняя  │ Следующая │  │   │
│  │  ───────────┼─────────┼────────────┼───────────│  │   │
│  │  Каталог    │ ● Готов │ 12 мин     │ через 2ч  │ [⟳]│
│  │  Цены       │ ● Готов │ 12 мин     │ через 2ч  │ [⟳]│
│  │  Остатки    │ ● Готов │ 15 мин     │ через 2ч  │ [⟳]│
│  │  Заказы     │ ● Готов │ 1ч назад   │ через 5ч  │ [⟳]│
│  │  Финансы    │ ⚠ Ошибка│ 2 дн назад │ через 1ч  │ [⟳]│
│  │  Промо      │ ● Готов │ 3ч назад   │ через 5ч  │ [⟳]│
│  │                                                   │   │
│  │  [Синхронизировать всё]                           │   │
│  └───────────────────────────────────────────────────┘   │
│                                                          │
│  ┌─ Журнал вызовов ─────────────────────────────────┐   │
│  │  Endpoint          │ Статус │ Время │ Дата       │   │
│  │  ──────────────────┼────────┼───────┼────────────│   │
│  │  /v2/get/cards/list│  200   │ 342ms │ 14:32      │   │
│  │  /v5/supplier/repo.│  429   │ 12ms  │ 14:30      │   │
│  │  /v2/list/goods/fi.│  200   │ 187ms │ 14:28      │   │
│  │                                                   │   │
│  │  Showing 1–20 of 1,234          [← Prev] [Next →]│   │
│  └───────────────────────────────────────────────────┘   │
│                                                          │
│  ┌─ Опасная зона ────────────────────────────────────┐   │
│  │                                                   │   │
│  │  [Отключить подключение]    [Удалить подключение] │   │
│  └───────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────┘
```

### 4. Data sources

| Action | Method | Endpoint | Notes |
|--------|--------|----------|-------|
| Load connection | GET | `/api/connections/{connectionId}` | Includes sync state per domain |
| Update name | PUT | `/api/connections/{connectionId}` | Body: `{ name }` |
| Load sync state | GET | `/api/connections/{connectionId}/sync-state` | `[{ dataDomain, lastSyncAt, lastSuccessAt, nextScheduledAt, status }]` |
| Manual sync (single domain) | POST | `/api/connections/{connectionId}/sync` | Body: `{ domains: ["CATALOG"] }` |
| Manual sync (all) | POST | `/api/connections/{connectionId}/sync` | Body: `{}` or `{ domains: [] }` |
| Update credentials | PUT | `/api/connections/{connectionId}/credentials` | Body: `{ credentials: {...} }` |
| Load call log | GET | `/api/connections/{connectionId}/call-log` | Paginated. Filters: `from`, `to`, `endpoint`, `httpStatus` |
| Disable connection | POST | `/api/connections/{connectionId}/disable` | Status → DISABLED |
| Enable connection | POST | `/api/connections/{connectionId}/enable` | Triggers re-validation |
| Delete connection | DELETE | `/api/connections/{connectionId}` | Status → ARCHIVED (OWNER only) |

### 5. Data model

**Info section fields:**

| Field | Source | Editable | Display |
|-------|--------|----------|---------|
| name | `connection.name` | Yes (ADMIN+) | Text input |
| marketplaceType | `connection.marketplaceType` | No | Marketplace badge (WB/Ozon) |
| status | `connection.status` | No | Status badge |
| createdAt | `connection.createdAt` | No | Date format |
| lastErrorCode | `connection.lastErrorCode` | No | Shown only if AUTH_FAILED; red inline text |

**Sync state table columns:**

| Column | Source field | Type | Width |
|--------|-------------|------|-------|
| Домен | `dataDomain` (mapped to Russian) | text | 120px |
| Статус | `status` | status badge | 100px |
| Последняя синхр. | `lastSuccessAt` | relative time | 120px |
| Следующая синхр. | `nextScheduledAt` | relative time | 120px |
| Actions | — | icon button (⟳) | 48px |

**Domain name mapping:**

| Backend value | Display (Russian) |
|---------------|-------------------|
| `CATALOG` | Каталог |
| `PRICES` | Цены |
| `STOCKS` | Остатки |
| `ORDERS` | Заказы |
| `SALES` | Продажи |
| `RETURNS` | Возвраты |
| `FINANCE` | Финансы |
| `PROMO` | Промо |
| `ADVERTISING` | Реклама |

**Sync status mapping:**

| Backend value | Badge | Color |
|---------------|-------|-------|
| `IDLE` | Готов | `--status-success` |
| `SYNCING` | Синхронизация... | `--status-info` |
| `ERROR` | Ошибка | `--status-error` |

**Call log table columns:**

| Column | Source field | Type | Alignment | Sort | Width |
|--------|-------------|------|-----------|------|-------|
| Endpoint | `endpoint` | text (truncated, tooltip for full) | left | — | flex |
| Метод | `httpMethod` | text (monospace, uppercase) | center | — | 60px |
| Статус | `httpStatus` | number, color-coded | center | ✓ | 80px |
| Время | `durationMs` | `{n} мс` (monospace) | right | ✓ | 80px |
| Дата | `createdAt` | timestamp `28 мар, 14:32` | right | ✓ (default, desc) | 120px |

**HTTP status color coding:**

| Range | Color |
|-------|-------|
| 2xx | `--status-success` |
| 3xx | `--status-info` |
| 4xx | `--status-warning` |
| 5xx | `--status-error` |

### 6. Components

- **Back link** — `← Подключения`, navigates to connections list.
- **Section cards** — bordered containers for Info, Credentials, Sync, Call Log, Danger Zone.
- **Text input** — for connection name (inline edit).
- **Primary button** — "Сохранить" for name, "Синхронизировать всё".
- **Secondary button** — "Обновить учётные данные".
- **Ghost icon button** — sync trigger per domain (⟳ `lucide:refresh-cw`).
- **Danger button** — "Удалить подключение".
- **Data table** — for sync state and call log.
- **Pagination** — for call log (server-side, 20 rows per page).
- **Status badges**, **Marketplace badges** — as defined in S-2.

### 7. Interactions

| Trigger | Action |
|---------|--------|
| Edit name → Save | PUT connection, success toast |
| Click "Обновить учётные данные" | Expand credential form below button (same fields as creation, pre-populated type) |
| Submit credential form | PUT credentials, async re-validation begins. Info toast: "Учётные данные обновлены. Идёт проверка..." |
| Click ⟳ (per domain) | POST sync for that domain. Button shows spinner. Toast: "Синхронизация {domain} запущена" |
| Click "Синхронизировать всё" | POST sync (all domains). Button shows spinner. Toast: "Полная синхронизация запущена" |
| Click "Отключить подключение" | Confirmation modal → POST disable. Toast: "Подключение отключено" |
| Click "Включить" (shown when DISABLED) | POST enable. Toast: "Подключение включено. Идёт проверка..." |
| Click "Удалить подключение" | Destructive confirmation modal (type-to-confirm) → DELETE. Redirect to connections list |

### 8. Form specifications

**Credential update form** (same structure as creation, without marketplace selector):

For WB: single `apiToken` textarea field.
For Ozon: `clientId` + `apiKey` fields.

Fields are blank on open (never pre-populate existing credentials for security).

Buttons: "Сохранить" (Primary), "Отмена" (Secondary).

### 9. Validation rules

Same as S-2 form validation for credentials. Additionally:

| Rule | Trigger | Message |
|------|---------|---------|
| Name required | Blur | "Название обязательно" |
| Sync already in progress | Click ⟳ | Toast warning: "Синхронизация уже выполняется" (409 from backend) |

### 10. Permissions

| Action | OWNER | ADMIN | PM | OP | AN | VW |
|--------|-------|-------|----|----|----|----|
| View connection detail | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Edit name | ✓ | ✓ | — | — | — | — |
| Update credentials | ✓ | ✓ | — | — | — | — |
| Trigger manual sync | ✓ | ✓ | — | — | — | — |
| View call log | ✓ | ✓ | — | — | — | — |
| Disable/Enable | ✓ | ✓ | — | — | — | — |
| Delete (archive) | ✓ | — | — | — | — | — |

Call log section and Danger Zone hidden for roles below ADMIN.

### 11. Loading states

- Connection info: skeleton for all fields.
- Sync state table: skeleton rows.
- Call log: skeleton rows.
- Manual sync button: spinner icon replaces ⟳, button disabled.
- Credential save: spinner on button, fields disabled.

### 12. Empty states

**Call log empty:**
```
Нет вызовов API. История появится после первой синхронизации.
```

**Sync state shows no domains (edge case — fresh connection, PENDING_VALIDATION):**
```
Домены синхронизации появятся после проверки учётных данных.
```

### 13. Error states

| Situation | Handling |
|-----------|----------|
| Connection not found (404) | Full area: "Подключение не найдено." + [Вернуться к списку] |
| Credential update failed (invalid) | Connection status badge turns to "Ошибка авторизации". Toast: "Учётные данные не прошли проверку." |
| Manual sync conflict (409) | Toast warning: "Синхронизация уже выполняется для этого подключения." |
| Delete failed | Error toast: "Не удалось удалить подключение." |

### 14. Destructive action confirmations

**Disable connection:**

```
┌─ Отключить подключение? ────────────────────────┐
│                                                  │
│  Подключение «{name}» будет отключено.           │
│  Синхронизация данных прекратится.                │
│  Подключение можно включить обратно.             │
│                                                  │
│                    [Отмена]  [Отключить]          │
└──────────────────────────────────────────────────┘
```

Button "Отключить" — secondary style (not danger, since reversible).

**Delete connection (type-to-confirm):**

```
┌─ Удалить подключение? ──────────────────────────┐
│                                                  │
│  ⚠ Это действие необратимо.                      │
│                                                  │
│  Подключение «{name}» будет удалено.             │
│  Синхронизация прекратится. Загруженные данные    │
│  сохранятся, но новые поступать не будут.        │
│                                                  │
│  Введите название подключения для подтверждения: │
│  ┌───────────────────────────────┐               │
│  │                               │               │
│  └───────────────────────────────┘               │
│                                                  │
│                    [Отмена]  [Удалить]            │
└──────────────────────────────────────────────────┘
```

"Удалить" button — danger style, enabled only when input matches connection name exactly.

### 15. Keyboard shortcuts

| Shortcut | Action |
|----------|--------|
| `Escape` | Close credential form / close modal |
| `Backspace` (in back link area) | Navigate back to connections list |

### 16. Phase & priority

**Phase A** — Foundation. Core setup flow.

---

## S-4. Себестоимость (Cost Profiles)

### 1. Route & title

- **Route:** `/workspace/:id/settings/cost-profiles`
- **Breadcrumb:** `Настройки > Себестоимость`
- **Page title:** `Себестоимость`

### 2. Purpose

Manage per-SKU cost prices (COGS). View, inline-edit, bulk import via CSV, and export. SCD2 versioning — each change creates a new historical version.

### 3. Layout & wireframe

```
┌──────────────────────────────────────────────────────────────┐
│  Себестоимость                                                │
│                                                              │
│  [🔍 Поиск по SKU или названию...]                           │
│                                                              │
│  [+ Добавить]  [↑ Импорт CSV]  [↓ Экспорт CSV]              │
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ SKU          │ Название товара │ Себестоим. │ Обновлено │ │
│  ├──────────────┼─────────────────┼────────────┼───────────┤ │
│  │ ART-001      │ Футболка белая  │ 450 ₽    ✎│ 15 мар    │ │
│  │ ART-002      │ Худи чёрное     │ 1 200 ₽  ✎│ 10 мар    │ │
│  │ ART-003      │ Шорты синие     │   — (не з.)│ —         │ │
│  │ ART-004      │ Кепка           │ 320 ₽    ✎│ 28 фев    │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                              │
│  Showing 1–50 of 234                   [← Prev] [Next →]    │
└──────────────────────────────────────────────────────────────┘
```

### 4. Data sources

| Action | Method | Endpoint | Notes |
|--------|--------|----------|-------|
| Load cost profiles | GET | `/api/cost-profiles?search=...&page=0&size=50` | Paginated. Search by SKU code or product name |
| Update single cost | POST | `/api/cost-profiles` | Body: `{ sellerSkuId, costPrice, currency: "RUB", validFrom }` |
| CSV import | POST | `/api/cost-profiles/bulk-import` | Multipart file upload |
| CSV export | GET | `/api/cost-profiles/export` | Downloads CSV file |
| View history | GET | `/api/cost-profiles/{sellerSkuId}/history` | SCD2 versions |

Note: Cost profiles endpoint returns joined data — seller_sku.sku_code + product_master.name + cost_profile fields.

### 5. Data model

**Table columns:**

| Column | Source field | Type | Alignment | Editable | Width |
|--------|-------------|------|-----------|----------|-------|
| SKU | `skuCode` | text (monospace) | left | No | 140px |
| Название товара | `productName` | text | left | No | flex |
| Себестоимость | `costPrice` | currency (monospace) | right | Yes (inline) | 120px |
| Обновлено | `updatedAt` or `validFrom` | date | right | No | 100px |

### 6. Components

- **Data table** — AG Grid (for inline editing, pagination, sorting, search).
- **Search input** — filter bar above table (`🔍` icon, 32px height, debounced 300ms).
- **Primary button** — "Добавить".
- **Secondary buttons** — "Импорт CSV", "Экспорт CSV".
- **Inline number input** — for cost_price editing (right-aligned, monospace, `₽` suffix).
- **Upload dialog** — modal for CSV import.

### 7. Interactions

| Trigger | Action |
|---------|--------|
| Double-click cost cell | Cell enters edit mode (number input, value selected). Save on blur or Enter. Cancel on Escape |
| Type in search | Debounce 300ms → GET with `search` param, reset pagination to page 0 |
| Click "Добавить" | Open inline form row at top of table (SKU selector + cost input + date) or modal |
| Click "Импорт CSV" | Open upload modal |
| Click "Экспорт CSV" | Trigger download. Toast: "Подготовка экспорта..." → download starts |
| Edit cost → Save | POST cost-profiles. Success: cell shows green flash. Toast: "Себестоимость обновлена." Failure: cell reverts, error toast |

### 8. Form specifications

**Add single cost profile (modal or inline):**

| Field | Label | Type | Required | Validation | Notes |
|-------|-------|------|----------|------------|-------|
| sellerSkuId | SKU | Autocomplete dropdown | Yes | Must exist in system | Search by sku_code or product name |
| costPrice | Себестоимость | Number input (₽) | Yes | > 0 | Right-aligned, monospace, 2 decimal places |
| validFrom | Действует с | Date picker | Yes | ≤ today | Default: today |

**CSV Import upload modal:**

```
┌─ Импорт себестоимости ──────────────────────────┐
│                                                  │
│  Загрузите CSV-файл с себестоимостью.            │
│                                                  │
│  Формат: sku_code, cost_price, currency,         │
│          valid_from                               │
│                                                  │
│  ┌──────────────────────────────────────┐        │
│  │                                      │        │
│  │   Перетащите файл сюда               │        │
│  │   или [Выберите файл]                │        │
│  │                                      │        │
│  │   CSV, до 5 МБ, до 10 000 строк     │        │
│  └──────────────────────────────────────┘        │
│                                                  │
│  [Скачать шаблон CSV]                            │
│                                                  │
│                        [Отмена]  [Импортировать]  │
└──────────────────────────────────────────────────┘
```

**Post-import result:**

```
┌─ Результат импорта ─────────────────────────────┐
│                                                  │
│  ✓ Импортировано: 180                            │
│  ⚠ Пропущено: 3                                 │
│  ✕ Ошибки: 2                                    │
│                                                  │
│  Ошибки:                                         │
│  • Строка 45: SKU "XYZ-999" не найден            │
│  • Строка 78: Себестоимость отрицательная         │
│                                                  │
│                                        [Закрыть] │
└──────────────────────────────────────────────────┘
```

### 9. Validation rules

| Rule | Trigger | Message |
|------|---------|---------|
| Cost price > 0 | Blur / submit | "Себестоимость должна быть положительной" |
| Cost price numeric | Input | "Введите число" |
| Cost price max 2 decimals | Blur | "Максимум 2 знака после запятой" |
| SKU required (add form) | Submit | "Выберите SKU" |
| SKU not found (add form) | Submit / server | "SKU не найден в системе" |
| CSV: file too large | Upload | "Максимальный размер файла: 5 МБ" |
| CSV: too many rows | Server response | "Максимум 10 000 строк в файле" |
| CSV: invalid format | Server response | "Неверный формат CSV. Проверьте структуру файла." |

### 10. Permissions

| Action | OWNER | ADMIN | PM | OP | AN | VW |
|--------|-------|-------|----|----|----|----|
| View cost profiles | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Edit cost (inline) | ✓ | ✓ | ✓ | — | — | — |
| Add cost profile | ✓ | ✓ | ✓ | — | — | — |
| CSV import | ✓ | ✓ | ✓ | — | — | — |
| CSV export | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |

Action buttons hidden for roles without permission. Inline edit cells show as read-only for OP/AN/VW.

### 11. Loading states

- Table: AG Grid built-in skeleton rows.
- Search: no separate loading — table refreshes.
- CSV import: progress bar in modal during upload + processing.
- Export: toast "Подготовка экспорта..." → download triggers.

### 12. Empty states

**No cost profiles and no products synced:**
```
Нет данных о себестоимости.
Себестоимость можно задать после загрузки каталога товаров.
```

**No cost profiles but products exist:**
```
Себестоимость не задана ни для одного SKU.
Добавьте вручную или импортируйте CSV.
[+ Добавить]  [↑ Импорт CSV]
```

**Search returned nothing:**
```
Нет SKU, соответствующих запросу «{query}».  [Сбросить поиск]
```

### 13. Error states

| Situation | Handling |
|-----------|----------|
| Inline edit failed | Cell reverts to old value, red flash. Error toast: "Не удалось сохранить. [Повторить]" |
| CSV import: partial failure | Import result modal shows imported + skipped + errors with row numbers |
| CSV import: total failure | Error toast: "Импорт не удался. Проверьте формат файла." |
| Export failed | Error toast: "Не удалось подготовить экспорт. [Повторить]" |

### 14. Destructive action confirmations

N/A — cost profile updates are non-destructive (SCD2 versioning preserves history). No delete operation.

### 15. Keyboard shortcuts

| Shortcut | Action |
|----------|--------|
| `Enter` (in cell edit) | Save edit, move to next row same column |
| `Escape` (in cell edit) | Cancel edit, revert value |
| `Tab` (in cell edit) | Save edit, move to next editable cell |
| `Ctrl+F` | Focus search input |

### 16. Phase & priority

**Phase B** — Cost profiles are needed for P&L calculations. Depends on canonical catalog being populated (Phase A ETL).

---

## S-5. Команда (Team Members)

### 1. Route & title

- **Route:** `/workspace/:id/settings/team`
- **Breadcrumb:** `Настройки > Команда`
- **Page title:** `Команда`

### 2. Purpose

View workspace members, change roles, remove members. Available to all roles for viewing (team awareness), edit operations restricted to ADMIN+.

### 3. Layout & wireframe

```
┌──────────────────────────────────────────────────────────┐
│  Команда                                                  │
│                                                          │
│  ┌────────────────────────────────────────────────────┐  │
│  │ Имя          │ Email             │ Роль    │Дата   │  │
│  ├──────────────┼───────────────────┼─────────┼───────┤  │
│  │ Виктор Ким   │ v@example.com     │[OWNER ▾]│15 мар │  │
│  │ Анна Петрова │ anna@example.com  │[ADMIN ▾]│20 мар │  │
│  │ Иван Сидоров │ ivan@example.com  │[ANALYST]│25 мар │  │
│  └────────────────────────────────────────────────────┘  │
│                                                          │
│  3 участника                                             │
└──────────────────────────────────────────────────────────┘
```

### 4. Data sources

| Action | Method | Endpoint | Notes |
|--------|--------|----------|-------|
| Load members | GET | `/api/workspaces/{workspaceId}/members` | Returns `[{ userId, email, name, role, status, createdAt }]` |
| Change role | PUT | `/api/workspaces/{workspaceId}/members/{userId}/role` | Body: `{ role }` |
| Remove member | DELETE | `/api/workspaces/{workspaceId}/members/{userId}` | Sets status = INACTIVE |

### 5. Data model

| Column | Source field | Type | Alignment | Editable | Width |
|--------|-------------|------|-----------|----------|-------|
| Имя | `name` | text | left | No | flex |
| Email | `email` | text (`--text-secondary`) | left | No | 200px |
| Роль | `role` | dropdown / badge | center | Yes (ADMIN+) | 160px |
| Присоединился | `createdAt` | date | right | No | 100px |
| Actions | — | icon button (remove) | center | — | 48px |

**Role badge mapping:**

| Role | Display | Color |
|------|---------|-------|
| `OWNER` | Владелец | `--accent-primary` bg, white text |
| `ADMIN` | Админ | `--status-info` bg, white text |
| `PRICING_MANAGER` | Менеджер цен | `--status-success` bg, white text |
| `OPERATOR` | Оператор | `--status-warning` bg, white text |
| `ANALYST` | Аналитик | `--status-neutral` bg, white text |
| `VIEWER` | Наблюдатель | `--bg-tertiary` bg, `--text-secondary` text |

### 6. Components

- **Data table** — simple HTML table (small dataset, max ~20 members).
- **Dropdown select** — for role change (inline in table cell, replaces badge on click).
- **Icon button** — remove member (`lucide:user-minus`, ghost style, danger on hover).
- **Role badge** — pill-shaped with role-specific colors.

### 7. Interactions

| Trigger | Action |
|---------|--------|
| Click role badge (ADMIN+ viewing non-OWNER, non-self) | Badge becomes dropdown with available roles |
| Select new role from dropdown | PUT role. Success: badge updates. Toast: "Роль изменена." |
| Click remove button | Confirmation modal → DELETE. Toast: "Участник удалён." |
| Hover row | `--bg-tertiary` background |

**Role dropdown rules:**
- ADMIN can assign: PRICING_MANAGER, OPERATOR, ANALYST, VIEWER (not ADMIN, not OWNER).
- OWNER can assign: ADMIN, PRICING_MANAGER, OPERATOR, ANALYST, VIEWER (not OWNER — ownership transfer is separate).
- Cannot change own role.
- Cannot change OWNER's role (OWNER row has no dropdown).

### 8. Form specifications

**Role change dropdown:**

| Option | Available to ADMIN | Available to OWNER |
|--------|--------------------|--------------------|
| Админ | — | ✓ |
| Менеджер цен | ✓ | ✓ |
| Оператор | ✓ | ✓ |
| Аналитик | ✓ | ✓ |
| Наблюдатель | ✓ | ✓ |

### 9. Validation rules

| Rule | Source | Message |
|------|--------|---------|
| Cannot change OWNER role | Client-side (dropdown hidden) | N/A — no UI to trigger |
| Cannot change own role | Client-side (dropdown hidden for self) | N/A |
| Cannot remove OWNER | Client-side (remove button hidden) | N/A |
| Cannot remove self | Client-side (remove button hidden for self) | N/A |
| Last ADMIN cannot change own role to non-ADMIN | Server-side (403) | Error toast: "Невозможно изменить роль: в workspace должен быть хотя бы один администратор." |

### 10. Permissions

| Action | OWNER | ADMIN | PM | OP | AN | VW |
|--------|-------|-------|----|----|----|----|
| View members | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Change role | ✓ | ✓ (restricted set) | — | — | — | — |
| Remove member | ✓ | ✓ | — | — | — | — |

Role dropdown and remove button hidden for roles below ADMIN.

### 11. Loading states

- Table: skeleton rows (3 placeholder rows).
- Role change: dropdown shows spinner next to selected option.
- Remove: button shows spinner during API call.

### 12. Empty states

N/A — workspace always has at least one member (OWNER).

### 13. Error states

| Situation | Handling |
|-----------|----------|
| Role change failed | Dropdown reverts to original. Error toast: "Не удалось изменить роль." |
| Remove failed | Error toast: "Не удалось удалить участника." |
| Cannot remove OWNER (server 403) | Error toast: "Невозможно удалить владельца workspace." |
| Attempt to change to OWNER role via API manipulation | Server 400. N/A in UI (option not shown) |

### 14. Destructive action confirmations

**Remove member:**

```
┌─ Удалить участника? ────────────────────────────┐
│                                                  │
│  {Name} ({email}) будет удалён из workspace.     │
│  Доступ к данным будет прекращён.                │
│                                                  │
│  Участника можно пригласить повторно.            │
│                                                  │
│                      [Отмена]  [Удалить]         │
└──────────────────────────────────────────────────┘
```

"Удалить" — danger button.

### 15. Keyboard shortcuts

| Shortcut | Action |
|----------|--------|
| `Escape` | Close role dropdown / close confirmation modal |

### 16. Phase & priority

**Phase A** — Foundation. Required for multi-user workspace operation.

---

## S-6. Приглашения (Invitations)

### 1. Route & title

- **Route:** `/workspace/:id/settings/invitations`
- **Breadcrumb:** `Настройки > Приглашения`
- **Page title:** `Приглашения`

### 2. Purpose

Send invitations to join workspace, track invitation status, resend or cancel pending invitations.

### 3. Layout & wireframe

```
┌──────────────────────────────────────────────────────────────┐
│  Приглашения                                                  │
│                                                              │
│  ┌─ Отправить приглашение ─────────────────────────────────┐ │
│  │  Email: [________________]  Роль: [Аналитик ▾]          │ │
│  │                                         [Отправить]     │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ Email            │ Роль     │ Статус   │ Отправ. │      │ │
│  ├──────────────────┼──────────┼──────────┼─────────┼──────┤ │
│  │ new@example.com  │ Аналитик │ ● Ожид.  │ 30 мар  │ ⟳ ✕ │ │
│  │ old@example.com  │ Оператор │ ✓ Принято│ 15 мар  │     │ │
│  │ exp@example.com  │ Админ    │ ○ Истекло│ 01 мар  │     │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                              │
│  3 приглашения                                               │
└──────────────────────────────────────────────────────────────┘
```

### 4. Data sources

| Action | Method | Endpoint | Notes |
|--------|--------|----------|-------|
| Load invitations | GET | `/api/workspaces/{workspaceId}/invitations` | Returns `[{ id, email, role, status, createdAt, expiresAt }]` |
| Send invitation | POST | `/api/workspaces/{workspaceId}/invitations` | Body: `{ email, role }` |
| Cancel invitation | DELETE | `/api/workspaces/{workspaceId}/invitations/{invitationId}` | Status → CANCELLED |
| Resend invitation | POST | `/api/workspaces/{workspaceId}/invitations/{invitationId}/resend` | New token, updated expires_at |

### 5. Data model

| Column | Source field | Type | Alignment | Width |
|--------|-------------|------|-----------|-------|
| Email | `email` | text | left | flex |
| Роль | `role` | role badge | center | 120px |
| Статус | `status` | status badge | center | 110px |
| Отправлено | `createdAt` | date | right | 100px |
| Истекает | `expiresAt` | date / relative | right | 100px |
| Actions | — | icon buttons | center | 80px |

**Status badge mapping:**

| Backend value | Badge label | Color | Actions available |
|---------------|-------------|-------|-------------------|
| `PENDING` | Ожидает | `--status-warning` (yellow) | Resend (⟳), Cancel (✕) |
| `ACCEPTED` | Принято | `--status-success` (green) | None |
| `EXPIRED` | Истекло | `--status-neutral` (gray) | None |
| `CANCELLED` | Отменено | `--status-neutral` (gray) | None |

### 6. Components

- **Inline form** — at top of screen for sending invitations (always visible for ADMIN+).
- **Email input** — standard text input with email validation.
- **Role dropdown** — select with role options.
- **Primary button** — "Отправить".
- **Data table** — simple table for invitation list.
- **Ghost icon buttons** — Resend (⟳ `lucide:refresh-cw`), Cancel (✕ `lucide:x`).
- **Status/role badges** — pill-shaped.

### 7. Interactions

| Trigger | Action |
|---------|--------|
| Fill email + select role → "Отправить" | POST invitation. Success toast: "Приглашение отправлено на {email}". Email field clears, role resets to default |
| Click ⟳ (resend) on PENDING row | POST resend. Toast: "Приглашение повторно отправлено на {email}" |
| Click ✕ (cancel) on PENDING row | Confirmation → DELETE. Toast: "Приглашение отменено." |

### 8. Form specifications

**Send invitation form:**

| Field | Label | Type | Required | Validation | Default |
|-------|-------|------|----------|------------|---------|
| email | Email | Text input (email type) | Yes | Valid email format | Empty |
| role | Роль | Select dropdown | Yes | Must be valid role | ANALYST |

**Role dropdown options (depends on current user role):**

| Option | Available to ADMIN | Available to OWNER |
|--------|--------------------|--------------------|
| Админ | — | ✓ |
| Менеджер цен | ✓ | ✓ |
| Оператор | ✓ | ✓ |
| Аналитик | ✓ | ✓ |
| Наблюдатель | ✓ | ✓ |

OWNER role cannot be assigned via invitation — ownership transfer is a separate flow.

### 9. Validation rules

| Rule | Trigger | Message |
|------|---------|---------|
| Email required | Blur / submit | "Введите email" |
| Email format | Blur | "Введите корректный email" |
| Role required | Submit | "Выберите роль" |
| Duplicate pending invitation | Server 409 (actually updates existing) | Toast info: "Приглашение для {email} обновлено." |
| Email already a member | Server 400 | Error toast: "Пользователь {email} уже является участником workspace." |
| Invitation limit exceeded | Server 429 | Error toast: "Слишком много приглашений. Попробуйте позже." |

### 10. Permissions

| Action | OWNER | ADMIN | PM | OP | AN | VW |
|--------|-------|-------|----|----|----|----|
| View invitations | ✓ | ✓ | — | — | — | — |
| Send invitation | ✓ | ✓ | — | — | — | — |
| Cancel invitation | ✓ | ✓ | — | — | — | — |
| Resend invitation | ✓ | ✓ | — | — | — | — |

Entire section hidden in sidebar for roles below ADMIN. If a non-ADMIN navigates directly to URL, show permission-denied message.

### 11. Loading states

- Table: skeleton rows.
- Send invitation: button spinner + "Отправка..." text.
- Resend: ⟳ icon becomes spinner.
- Cancel: ✕ icon becomes spinner.

### 12. Empty states

**No invitations ever sent:**
```
Приглашений пока нет.
Отправьте приглашение, чтобы добавить участника в workspace.
```

### 13. Error states

| Situation | Handling |
|-----------|----------|
| Send failed (server error) | Error toast: "Не удалось отправить приглашение. [Повторить]" |
| Resend failed | Error toast: "Не удалось повторно отправить приглашение." |
| Cancel failed | Error toast: "Не удалось отменить приглашение." |
| User already member | Error toast: "Пользователь {email} уже является участником workspace." |
| Access denied (navigated directly, non-ADMIN) | Full area: "У вас нет доступа к этому разделу." |

### 14. Destructive action confirmations

**Cancel invitation:**

```
┌─ Отменить приглашение? ─────────────────────────┐
│                                                  │
│  Приглашение для {email} будет отменено.          │
│  Ссылка в письме перестанет работать.             │
│                                                  │
│                    [Не отменять]  [Отменить]      │
└──────────────────────────────────────────────────┘
```

"Отменить" — danger button.

### 15. Keyboard shortcuts

| Shortcut | Action |
|----------|--------|
| `Enter` (in email input) | Submit form |
| `Tab` (from email) | Focus role dropdown |
| `Escape` | Close confirmation modal |

### 16. Phase & priority

**Phase A** — Foundation. Required for team onboarding.

---

## S-7. Правила алертов (Alert Rules)

### 1. Route & title

- **Route:** `/workspace/:id/settings/alert-rules`
- **Breadcrumb:** `Настройки > Правила алертов`
- **Page title:** `Правила алертов`

### 2. Purpose

View and configure business alert rules: adjust thresholds, enable/disable rules, set severity, control automation blocking. Default rules are seeded on workspace creation.

### 3. Layout & wireframe

```
┌──────────────────────────────────────────────────────────────┐
│  Правила алертов                                              │
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ Название          │ Тип        │ Критич.│ Статус │ Посл.│ │
│  ├───────────────────┼────────────┼────────┼────────┼──────┤ │
│  │ Устаревшие данные │ STALE_DATA │ ●КРИТ. │ ✓ Вкл  │15мар │ │
│  │ Пропуск синхр.    │ MISSING..  │ ⚠ВНИМН │ ✓ Вкл  │14мар │ │
│  │ Аномалия остатков │ RESIDUAL.. │ ●КРИТ. │ ✓ Вкл  │ —    │ │
│  │ Всплеск метрик    │ SPIKE_D..  │ ⚠ВНИМН │ ○ Выкл │ —    │ │
│  │ Расхождения       │ MISMATCH   │ ⚠ВНИМН │ ✓ Вкл  │12мар │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                              │
│  Нажмите на правило для настройки                            │
└──────────────────────────────────────────────────────────────┘
```

**Rule detail / edit panel (opens on row click — replaces table or appears below):**

```
┌─ Устаревшие данные (STALE_DATA) ────────────────────────────┐
│                                                              │
│  Описание: Алерт срабатывает, когда данные по финансам       │
│  не обновлялись дольше заданного порога.                      │
│                                                              │
│  Критичность: [● Критический ▾]                              │
│                                                              │
│  Блокирует автоматизацию: [✓]                                │
│  (Если включено, ценообразование и промо-автоматизация       │
│   будут приостановлены при срабатывании этого правила)        │
│                                                              │
│  Параметры:                                                  │
│  ┌─────────────────────────────────────────────────────┐     │
│  │  Порог устаревания финансов (часы): [24        ]    │     │
│  │  Порог устаревания каталога (часы):  [48        ]    │     │
│  └─────────────────────────────────────────────────────┘     │
│                                                              │
│  Статус: [✓ Включено]                                        │
│                                                              │
│  Последнее срабатывание: 15 мар 2026, 10:32                  │
│                                                              │
│                               [Отмена]  [Сохранить]          │
└──────────────────────────────────────────────────────────────┘
```

### 4. Data sources

| Action | Method | Endpoint | Notes |
|--------|--------|----------|-------|
| Load rules | GET | `/api/alert-rules` | Returns `[{ id, ruleType, targetEntityType, config, enabled, severity, blocksAutomation, createdAt, updatedAt }]` |
| Update rule | PUT | `/api/alert-rules/{id}` | Body: `{ config, enabled, severity, blocksAutomation }` |

Note: Phase B — no rule creation via UI. Default rules are system-seeded. Phase G+ — custom rule creation.

### 5. Data model

**Table columns:**

| Column | Source field | Type | Alignment | Width |
|--------|-------------|------|-----------|-------|
| Название | derived from `ruleType` | text | left | flex |
| Тип | `ruleType` | text (monospace, `--text-secondary`) | left | 130px |
| Критичность | `severity` | severity badge | center | 100px |
| Статус | `enabled` | toggle badge | center | 80px |
| Последнее срабатывание | `lastTriggeredAt` | relative time | right | 110px |

**Rule type display mapping:**

| Backend value | Display name (Russian) | Description |
|---------------|------------------------|-------------|
| `STALE_DATA` | Устаревшие данные | Данные не обновлялись дольше порога |
| `MISSING_SYNC` | Пропуск синхронизации | Ожидаемая синхронизация не произошла |
| `RESIDUAL_ANOMALY` | Аномалия reconciliation | Отклонение reconciliation residual от baseline |
| `SPIKE_DETECTION` | Всплеск метрик | Резкое изменение ключевых показателей |
| `MISMATCH` | Расхождения данных | Несоответствие между связанными доменами данных |

**Severity badge mapping:**

| Value | Badge label | Color |
|-------|-------------|-------|
| `INFO` | Инфо | `--status-info` (blue) |
| `WARNING` | Внимание | `--status-warning` (yellow) |
| `CRITICAL` | Критический | `--status-error` (red) |

**Config fields per rule_type:**

| rule_type | Config field | Label | Type | Default |
|-----------|-------------|-------|------|---------|
| `STALE_DATA` | `finance_stale_hours` | Порог устаревания финансов (часы) | Number input | 24 |
| `STALE_DATA` | `state_stale_hours` | Порог устаревания каталога/цен/остатков (часы) | Number input | 48 |
| `MISSING_SYNC` | `expected_interval_minutes` | Ожидаемый интервал синхронизации (мин) | Number input | 60 |
| `MISSING_SYNC` | `tolerance_factor` | Множитель допуска | Number input | 2.0 |
| `RESIDUAL_ANOMALY` | `sigma_threshold` | Порог отклонения (σ) | Number input | 2.0 |
| `RESIDUAL_ANOMALY` | `min_absolute_threshold` | Минимальная сумма аномалии (₽) | Number input | 500 |
| `SPIKE_DETECTION` | `spike_ratio_threshold` | Порог всплеска (множитель) | Number input | 3.0 |
| `SPIKE_DETECTION` | `min_baseline_days` | Минимум дней для baseline | Number input | 7 |
| `MISMATCH` | `max_orphan_count` | Максимум расхождений без алерта | Number input | 5 |

### 6. Components

- **Data table** — simple table for rules list.
- **Severity badge** — colored pill.
- **Toggle badge** — green "Вкл" / gray "Выкл".
- **Edit form** — inline below table or as expandable section on row click.
- **Dropdown** — for severity selection.
- **Checkbox** — for `blocksAutomation` and `enabled`.
- **Number inputs** — for threshold configuration (32px, monospace for numbers).
- **Primary/Secondary buttons** — "Сохранить" / "Отмена".

### 7. Interactions

| Trigger | Action |
|---------|--------|
| Click row | Expand/show rule detail form below table (accordion style). Previous detail collapses |
| Toggle enabled | Immediate PUT update (optimistic toggle). Toast: "Правило {включено/выключено}." |
| Change severity | Part of form, saved on "Сохранить" |
| Change thresholds | Part of form, saved on "Сохранить" |
| Toggle blocksAutomation | Part of form, saved on "Сохранить". Warning inline when enabling: "Внимание: при срабатывании этого правила автоматическое ценообразование и промо будут приостановлены для затронутого подключения." |
| Click "Сохранить" | PUT rule. Success toast: "Правило обновлено." |
| Click "Отмена" | Collapse detail, revert changes |

### 8. Form specifications

See "Config fields per rule_type" table in section 5 above. All config fields are number inputs.

**Common fields across all rules:**

| Field | Label | Type | Notes |
|-------|-------|------|-------|
| severity | Критичность | Select dropdown | INFO / WARNING / CRITICAL |
| enabled | Включено | Checkbox | Default: true |
| blocksAutomation | Блокирует автоматизацию | Checkbox | Default: depends on rule_type |

### 9. Validation rules

| Rule | Trigger | Message |
|------|---------|---------|
| Threshold must be positive | Blur | "Значение должно быть положительным" |
| Threshold must be numeric | Input | "Введите число" |
| Sigma threshold 0.5–10.0 range | Blur | "Допустимый диапазон: 0,5 — 10" |
| Spike ratio 1.0–100.0 | Blur | "Допустимый диапазон: 1 — 100" |
| Min baseline days 1–90 | Blur | "Допустимый диапазон: 1 — 90 дней" |

### 10. Permissions

| Action | OWNER | ADMIN | PM | OP | AN | VW |
|--------|-------|-------|----|----|----|----|
| View rules | ✓ | ✓ | ✓ | ✓ | — | — |
| Edit rules | ✓ | ✓ | — | — | — | — |

Sidebar item visible to OPERATOR+ (they can view alert rules for context). Edit controls (form fields, save button) hidden for non-ADMIN roles — they see read-only detail.

### 11. Loading states

- Table: skeleton rows (5 rows).
- Save: button spinner + "Сохранение...".
- Toggle: optimistic toggle (immediate visual switch), revert on failure.

### 12. Empty states

N/A — default rules are seeded on workspace creation. Table always has 5 rows.

### 13. Error states

| Situation | Handling |
|-----------|----------|
| Load failed | Error toast: "Не удалось загрузить правила алертов. [Повторить]" |
| Save failed | Error toast: "Не удалось сохранить. [Повторить]". Form stays open |
| Toggle failed | Revert toggle state. Error toast: "Не удалось изменить статус правила." |

### 14. Destructive action confirmations

N/A — no rule deletion in Phase B. Toggle disable is non-destructive (reversible).

When enabling `blocksAutomation`, inline warning text suffices (no modal):

> ⚠ При срабатывании этого правила автоматическое ценообразование и промо будут приостановлены для затронутого подключения.

### 15. Keyboard shortcuts

| Shortcut | Action |
|----------|--------|
| `Enter` (in form) | Submit form |
| `Escape` | Collapse detail form |

### 16. Phase & priority

**Phase B** (configurable alert rules). Phase E (Alert Rules CRUD API). Default seeded rules appear as read-only in Phase A (alert events functional without config UI).

---

## S-8. Журнал аудита (Audit Log)

### 1. Route & title

- **Route:** `/workspace/:id/settings/audit`
- **Breadcrumb:** `Настройки > Журнал аудита`
- **Page title:** `Журнал аудита`

### 2. Purpose

Read-only view of all workspace actions: who did what, when, to which entity. Filterable by actor, action type, entity type, and date range. ADMIN+ only.

### 3. Layout & wireframe

```
┌──────────────────────────────────────────────────────────────┐
│  Журнал аудита                                                │
│                                                              │
│  [Пользователь ▾] [Действие ▾] [Тип сущности ▾]            │
│  [Период: 01 мар — 31 мар ▾]                    [⊘ Сбросить]│
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ Время       │ Пользователь │ Действие       │ Объект   │ │
│  ├─────────────┼──────────────┼────────────────┼──────────┤ │
│  │ 31 мар 14:30│ Виктор Ким   │ member.invite  │ invite#45│▸│
│  │ 31 мар 12:15│ Система      │ connection.sync│ conn#2   │▸│
│  │ 30 мар 18:00│ Анна Петрова │ policy.update  │ policy#1 │▸│
│  │ 30 мар 10:22│ Виктор Ким   │ credential.rot │ conn#1   │▸│
│  └─────────────────────────────────────────────────────────┘ │
│                                                              │
│  Showing 1–50 of 1,234                   [← Prev] [Next →]  │
└──────────────────────────────────────────────────────────────┘
```

**Expanded row (click ▸ or row click → inline expand):**

```
│ 31 мар 14:30│ Виктор Ким   │ member.invite  │ invite#45│▾│
│  ┌─ Детали ────────────────────────────────────────────────│
│  │  ID записи: 1234                                       │
│  │  Пользователь: Виктор Ким (v@example.com)              │
│  │  Тип актора: USER                                      │
│  │  Действие: member.invite                                │
│  │  Тип сущности: workspace_invitation                     │
│  │  ID сущности: 45                                        │
│  │  Результат: SUCCESS                                     │
│  │  IP: 192.168.1.42                                       │
│  │                                                         │
│  │  Подробности:                                           │
│  │  ┌──────────────────────────────────────────┐           │
│  │  │ {                                        │           │
│  │  │   "email": "new@example.com",            │           │
│  │  │   "role": "ANALYST"                      │           │
│  │  │ }                                        │           │
│  │  └──────────────────────────────────────────┘           │
│  └─────────────────────────────────────────────────────────│
```

### 4. Data sources

| Action | Method | Endpoint | Notes |
|--------|--------|----------|-------|
| Load audit log | GET | `/api/audit-log?actionType=...&entityType=...&userId=...&from=...&to=...&page=0&size=50` | Paginated, server-side filtering |

### 5. Data model

**Table columns:**

| Column | Source field | Type | Alignment | Sort | Width |
|--------|-------------|------|-----------|------|-------|
| Время | `createdAt` | timestamp `31 мар, 14:30` | left | ✓ (default, desc) | 120px |
| Пользователь | `actorUserId` → resolved name, or "Система" / "Планировщик" | text | left | ✓ | 150px |
| Действие | `actionType` | text (monospace, dot-separated) | left | ✓ | 160px |
| Объект | `entityType` + `entityId` | text | left | — | 120px |
| Результат | `outcome` | badge | center | ✓ | 90px |
| Expand | — | chevron icon (▸/▾) | center | — | 32px |

**Outcome badge mapping:**

| Value | Badge label | Color |
|-------|-------------|-------|
| `SUCCESS` | Успех | `--status-success` |
| `DENIED` | Отказ | `--status-error` |
| `FAILED` | Ошибка | `--status-warning` |

**Actor type display:**

| Value | Display |
|-------|---------|
| `USER` | User's name (from resolved userId) |
| `SYSTEM` | Система |
| `SCHEDULER` | Планировщик |

**Expanded detail fields:**

| Field | Source | Display |
|-------|--------|---------|
| ID записи | `id` | Read-only |
| Пользователь | resolved name + email | Read-only |
| Тип актора | `actorType` | Read-only |
| Действие | `actionType` | Read-only, monospace |
| Тип сущности | `entityType` | Read-only |
| ID сущности | `entityId` | Read-only |
| Результат | `outcome` | Badge |
| IP | `ipAddress` | Read-only, monospace (nullable — show "—" if null) |
| Подробности | `details` | JSON viewer (syntax-highlighted, read-only, collapsible) |

### 6. Components

- **Filter bar** — horizontal, filter pills pattern (from design system).
- **Filter: Пользователь** — dropdown with workspace members + "Система" + "Планировщик".
- **Filter: Действие** — dropdown with action_type prefixes grouped by module (connection.*, member.*, policy.*, etc.).
- **Filter: Тип сущности** — dropdown with entity types (marketplace_connection, workspace_invitation, price_policy, etc.).
- **Filter: Период** — date range picker (from — to).
- **Data table** — with expandable rows (accordion style).
- **JSON viewer** — for `details` field. Read-only, syntax-highlighted (monospace, indented).
- **Pagination** — server-side, 50 rows per page.

### 7. Interactions

| Trigger | Action |
|---------|--------|
| Click row or ▸ chevron | Expand row to show full detail (accordion — others collapse) |
| Click ▾ chevron (expanded) | Collapse detail |
| Change filter | Re-fetch with filter params, reset to page 0 |
| Click "Сбросить" | Clear all filters, reload default view |

### 8. Form specifications

N/A — read-only screen. Filters are dropdowns and date pickers, not forms.

**Filter options:**

| Filter | Type | Options |
|--------|------|---------|
| Пользователь | Searchable dropdown | Dynamic: workspace members list + "Система", "Планировщик" |
| Действие | Grouped dropdown | Groups: Подключения (connection.*), Учётные данные (credential.*), Участники (member.*), Workspace (workspace.*), Политики (policy.*), Действия (action.*), Промо (promo.*), Алерты (alert.*) |
| Тип сущности | Dropdown | marketplace_connection, workspace_invitation, workspace_member, workspace, price_policy, price_action, app_user |
| Период | Date range picker | From date — To date. Default: last 30 days |

### 9. Validation rules

| Rule | Trigger | Message |
|------|---------|---------|
| Date range: `from` ≤ `to` | Date picker change | "Дата начала не может быть позже даты окончания" |
| Date range: max 12 months | Date picker change | "Максимальный период: 12 месяцев" |

### 10. Permissions

| Action | OWNER | ADMIN | PM | OP | AN | VW |
|--------|-------|-------|----|----|----|----|
| View audit log | ✓ | ✓ | — | — | — | — |

Section hidden in sidebar for roles below ADMIN. Direct URL navigation → permission-denied message.

### 11. Loading states

- Table: skeleton rows (5–10 rows with shimmer).
- Filter change: subtle top-edge progress bar (2px `--accent-primary`), table content stays visible.
- Expand row: instant (details loaded with list response).

### 12. Empty states

**No audit entries match filters:**
```
Нет записей, соответствующих выбранным фильтрам.  [Сбросить фильтры]
```

**No audit entries at all (fresh workspace):**
```
Журнал аудита пуст. Действия пользователей и системы
будут записываться автоматически.
```

### 13. Error states

| Situation | Handling |
|-----------|----------|
| Load failed | Error toast: "Не удалось загрузить журнал аудита. [Повторить]" |
| Access denied | Full area: "У вас нет доступа к журналу аудита." |

### 14. Destructive action confirmations

N/A — completely read-only screen.

### 15. Keyboard shortcuts

| Shortcut | Action |
|----------|--------|
| `↑ / ↓` | Navigate rows |
| `Enter` | Expand/collapse selected row |
| `Escape` | Collapse expanded row |
| `Ctrl+F` | Focus filter bar |

### 16. Phase & priority

**Phase A** — audit_log write + read API are Foundation. Full-featured UI with filters — Phase E (alongside notification UI).

---

## User Flows

### UF-1: Добавление подключения с проверкой учётных данных

**Actor:** ADMIN or OWNER
**Entry point:** Settings → Подключения → "Добавить подключение"

```
1. User navigates to Settings → Подключения
2. Clicks [+ Добавить подключение]
3. Add Connection form appears inline
4. Selects marketplace: "Wildberries"
   → WB credential fields appear (apiToken textarea)
5. Enters connection name: "Мой кабинет WB"
6. Pastes API token into apiToken field
7. Clicks [Подключить]
   → Button shows spinner, text "Подключение..."
   → POST /api/connections { marketplaceType: "WB", name: "Мой кабинет WB", credentials: { apiToken: "..." } }
8a. SUCCESS (201):
   → Toast: "Подключение создано"
   → New row appears in table: name "Мой кабинет WB", status "Проверка..." (PENDING_VALIDATION)
   → Form closes
   → Background: async credential validation runs
   → Within seconds: WebSocket push updates status
   → Status badge changes to "Активно" (green) if valid
   → Status bar updates with sync status
9a. VALIDATION SUCCESS:
   → Status → ACTIVE
   → Sync state domains appear (CATALOG, PRICES, etc.)
   → Initial FULL_SYNC begins automatically
   → Toast: "Первая синхронизация запущена"
8b. VALIDATION FAILURE:
   → Status → "Ошибка авторизации" (red badge)
   → User clicks row → Connection Detail
   → Sees "Ошибка авторизации" status + lastErrorCode
   → Clicks [Обновить учётные данные]
   → Enters correct token → Save
   → Re-validation → status updates
```

**Edge cases:**
- Invalid token format (empty/whitespace) → client-side validation stops submit.
- Network error during creation → error toast with retry action.
- Duplicate connection (same external_account_id) → server 409, toast: "Подключение для этого кабинета уже существует."

### UF-2: Приглашение участника в команду

**Actor:** ADMIN or OWNER
**Entry point:** Settings → Приглашения

```
1. User navigates to Settings → Приглашения
2. Invitation form is visible at top
3. Types email: "newuser@company.ru"
4. Selects role: "Аналитик" (from dropdown)
5. Clicks [Отправить]
   → Button shows spinner
   → POST /api/workspaces/{id}/invitations { email: "newuser@company.ru", role: "ANALYST" }
6a. SUCCESS (201):
   → Toast: "Приглашение отправлено на newuser@company.ru"
   → Email field clears, role resets to default
   → New row in table: email, role "Аналитик", status "Ожидает" (yellow)
7. If invitation not accepted within 7 days:
   → Status automatically changes to "Истекло" (gray)
8. To resend: click ⟳ on PENDING row
   → POST .../resend
   → Toast: "Приглашение повторно отправлено"
   → New token generated, expires_at reset
```

**Edge cases:**
- Email already a member → server 400, toast: "Пользователь уже является участником."
- Duplicate pending invitation → server response indicates update (not error), toast: "Приглашение обновлено."
- Invalid email format → client-side validation: "Введите корректный email."
- ADMIN tries to invite as ADMIN role → option not in dropdown (only OWNER can assign ADMIN).

### UF-3: Управление себестоимостью через CSV

**Actor:** ADMIN, OWNER, or PRICING_MANAGER
**Entry point:** Settings → Себестоимость

```
1. User navigates to Settings → Себестоимость
2. Table shows existing cost profiles (or empty state)
3. Clicks [↑ Импорт CSV]
   → Upload modal opens
4. Downloads template: clicks [Скачать шаблон CSV]
   → CSV file downloads with headers: sku_code,cost_price,currency,valid_from
5. Fills template with data (e.g., 200 SKUs)
6. Drags file into drop zone (or clicks "Выберите файл")
   → File name appears, file size shown
7. Clicks [Импортировать]
   → Progress bar appears in modal
   → POST /api/cost-profiles/bulk-import (multipart)
8a. SUCCESS (partial):
   → Result modal:
     ✓ Импортировано: 195
     ⚠ Пропущено: 3 (duplicates with same valid_from + cost)
     ✕ Ошибки: 2
       • Строка 45: SKU "NONEXIST-001" не найден
       • Строка 112: Себестоимость "-50" — должна быть положительной
   → User clicks [Закрыть]
   → Table refreshes with updated data
8b. TOTAL SUCCESS:
   → Result modal: ✓ Импортировано: 200
   → Table refreshes
9. To verify: user searches for specific SKU in search bar
10. To export current state: clicks [↓ Экспорт CSV]
    → Toast: "Подготовка экспорта..."
    → CSV downloads with all current cost profiles
```

**Edge cases:**
- File > 5 MB → client-side check: "Максимальный размер файла: 5 МБ."
- File > 10,000 rows → server 400: "Максимум 10 000 строк в файле."
- Invalid CSV structure → server error, toast: "Неверный формат CSV."
- All rows invalid → result modal: 0 imported, N errors listed.
- Empty file → toast: "Файл пуст."

---

## Permission Matrix (Summary)

Cross-reference of all Settings sections by role:

| Section | Route suffix | OWNER | ADMIN | PM | OP | AN | VW |
|---------|-------------|-------|-------|----|----|----|----|
| Общие | `/general` | View+Edit | View+Edit | View | View | View | View |
| Подключения | `/connections` | View+Add | View+Add | View | View | View | View |
| Детали подкл. | `/connections/:id` | Full | Full (no delete) | View only | View only | View only | View only |
| Себестоимость | `/cost-profiles` | Full | Full | Full | View+Export | View+Export | View+Export |
| Команда | `/team` | View+Edit | View+Edit (restricted) | View | View | View | View |
| Приглашения | `/invitations` | Full | Full (restricted roles) | Hidden | Hidden | Hidden | Hidden |
| Правила алертов | `/alert-rules` | View+Edit | View+Edit | View | View | Hidden | Hidden |
| Журнал аудита | `/audit` | Full | Full | Hidden | Hidden | Hidden | Hidden |

**Legend:**
- **Full** — all actions available
- **View+Edit** — can view and modify
- **View+Add** — can view list and create new
- **View+Export** — can view and export (no edit)
- **View** — read-only access
- **Hidden** — sidebar item not shown, direct URL shows permission-denied
- **restricted** — subset of role options available (see individual section specs)

---

## Related documents

- [Frontend Design Direction](frontend-design-direction.md) — design system, component patterns
- [Tenancy & IAM](../modules/tenancy-iam.md) — workspace, members, invitations, roles
- [Integration](../modules/integration.md) — connections, credentials, sync, call log
- [ETL Pipeline](../modules/etl-pipeline.md) — cost profiles, job executions
- [Audit & Alerting](../modules/audit-alerting.md) — alert rules, audit log, notifications
