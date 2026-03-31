# Страница: Working Queues (Рабочие очереди)

**Фаза:** E — Seller Operations
**Route:** `/workspace/:id/queues`
**Activity Bar icon:** CheckSquare (lucide) — второй уровень навигации внутри модуля Seller Operations
**Breadcrumbs:** `Seller Operations > Очереди`

---

## Обзор

Рабочие очереди — основной инструмент ежедневной операционной работы. Экран объединяет задачи,
требующие внимания оператора: одобрение ценовых действий, обработка ошибок, расхождений, критических
остатков, отсутствующей себестоимости и свежих решений.

Интерфейс организован как **master-detail** со списком очередей слева и содержимым выбранной очереди
справа. Паттерн аналогичен почтовому клиенту: сайдбар с папками + основная область с элементами.

### Спецификация экрана — 16 обязательных пунктов

Каждый экран описывается по единому шаблону:

| # | Пункт | Содержание |
|---|-------|-----------|
| 1 | URL и навигация | Route, breadcrumbs, Activity Bar |
| 2 | Layout и wireframe | ASCII-wireframe, зоны, размеры |
| 3 | Заголовок и контекст | Название, подзаголовок, контекстная информация |
| 4 | Компоненты и зоны | Перечень визуальных областей |
| 5 | Данные и колонки | Источники данных, колонки грида |
| 6 | Фильтрация | Доступные фильтры |
| 7 | Сортировка | Сортируемые колонки, дефолт |
| 8 | Пагинация | Размеры страниц, тип пагинации |
| 9 | Действия и кнопки | Интерактивные элементы, кнопки |
| 10 | Пустые состояния | Сообщения при отсутствии данных |
| 11 | Загрузка | Паттерны загрузки |
| 12 | Ошибки | Обработка ошибок |
| 13 | Клавиатурные сокращения | Горячие клавиши |
| 14 | Права доступа | Роли, ограничения |
| 15 | API-контракты | Используемые endpoints |
| 16 | Realtime / WebSocket | Живые обновления |

---

## Системные очереди

Системные очереди создаются автоматически при создании workspace. Не могут быть удалены или переименованы.
Их `auto_criteria` фиксирован.

| # | Русское название | `queue_type` | `auto_criteria` | Источник |
|---|------------------|-------------|----------------|----------|
| 1 | Ожидают одобрения | DECISION | `price_action.status = 'PENDING_APPROVAL'` | [Execution](../modules/execution.md) |
| 2 | Ошибки выполнения | ATTENTION | `price_action.status IN ('FAILED')` OR `promo_action.status = 'FAILED'` | [Execution](../modules/execution.md) |
| 3 | Расхождения | ATTENTION | `alert_event.rule_type = 'MISMATCH' AND status = 'ACTIVE'` | [Seller Operations → Mismatch Monitor](../modules/seller-operations.md#mismatch-monitor) |
| 4 | Нет себестоимости | ATTENTION | `marketplace_offer.status = 'ACTIVE' AND cost_profile IS NULL` | [Pricing](../modules/pricing.md) — eligibility skip |
| 5 | Критичные остатки | ATTENTION | `mart_inventory_analysis.stock_out_risk = 'CRITICAL'` | [Analytics & P&L](../modules/analytics-pnl.md) (ClickHouse) |
| 6 | Свежие решения | PROCESSING | `price_decision.created_at > NOW() - interval '24 hours' AND decision_type = 'CHANGE'` | [Pricing](../modules/pricing.md) |

### Маппинг entity_type по очередям

| Очередь | `entity_type` | `entity_id` → | Описание |
|---------|--------------|---------------|----------|
| Ожидают одобрения | `price_action` | `price_action.id` | Action ожидает ручного approve |
| Ошибки выполнения | `price_action` / `promo_action` | `.id` | Терминальный FAILED |
| Расхождения | `alert_event` | `alert_event.id` | Mismatch alert |
| Нет себестоимости | `marketplace_offer` | `marketplace_offer.id` | Offer без cost_profile |
| Критичные остатки | `marketplace_offer` | `marketplace_offer.id` | Offer с CRITICAL stock risk |
| Свежие решения | `price_decision` | `price_decision.id` | Решение CHANGE за последние 24ч |

---

## Экран 1: Queue List (Список очередей + содержимое)

### 1. URL и навигация

- **Route:** `/workspace/:id/queues`
- **Route с выбранной очередью:** `/workspace/:id/queues/:queueId`
- **Breadcrumbs:** `Seller Operations > Очереди` → при выборе очереди: `Seller Operations > Очереди > {Название очереди}`
- **Activity Bar:** иконка модуля Seller Operations (активна)
- **Tab:** «Очереди» — tab в Main Area. Pinned по умолчанию.
- При первом входе — выбрана первая системная очередь с `totalActiveCount > 0`. Если все пусты — выбрана «Ожидают одобрения».

### 2. Layout и wireframe

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  Top Bar: workspace switcher · Seller Operations > Очереди > {queue} · 🔍 · 👤│
├────┬──────────────┬──────────────────────────────────────────┬───────────────┤
│    │ Queue        │                                          │               │
│ A  │ Sidebar      │         Queue Items Grid (B)             │  Detail       │
│ c  │ (200px)      │                                          │  Panel (C)    │
│ t  │              │  toolbar: filters · sort · bulk actions  │               │
│ i  │ ─ SYSTEM ──  │  ─────────────────────────────────────── │  (opens on    │
│ v  │ ● Ожидают    │  ☐ │ SKU    │ Товар     │ Статус │ ...  │   row click)  │
│ i  │   одобрения  │  ☐ │ABC-001 │ Футболка  │PENDING │ ...  │               │
│ t  │   12         │  ☐ │DEF-002 │ Штаны     │PENDING │ ...  │  400px,       │
│ y  │ ● Ошибки     │  ☐ │GHI-003 │ Куртка    │PENDING │ ...  │  resizable    │
│    │   выполнения │                                          │               │
│ B  │   3          │  ─────────────────────────────────────── │               │
│ a  │ ● Расхожде-  │  Showing 1–50 of 12 · [◀] 1 [▶]        │               │
│ r  │   ния  0 ✓   │                                          │               │
│    │ ● Нет себе-  ├──────────────────────────────────────────┤               │
│    │   стоимости  │  Bulk bar: 3 selected [Одобрить все]     │               │
│    │   5          │  [Отклонить все] [Экспорт]           ×   │               │
│    │ ● Критичные  │                                          │               │
│    │   остатки 2  │                                          │               │
│    │ ● Свежие     │                                          │               │
│    │   решения 8  │                                          │               │
│    │              │                                          │               │
│    │ ─ CUSTOM ──  │                                          │               │
│    │ ○ Мой фильтр │                                          │               │
│    │   14         │                                          │               │
│    │              │                                          │               │
│    │ [+ Очередь]  │                                          │               │
│    │              │                                          │               │
├────┴──────────────┴──────────────────────────────────────────┴───────────────┤
│  Status Bar: ● WB synced 12 мин назад  ● Ozon synced 3 мин назад            │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 3. Заголовок и контекст

- **Sidebar header:** «Очереди» (`--text-lg`, 16px, weight 600)
- Разделитель «СИСТЕМНЫЕ» (`--text-xs`, 11px, `--text-tertiary`, uppercase tracking)
- Разделитель «ПОЛЬЗОВАТЕЛЬСКИЕ» — аналогично
- В Main Area: название выбранной очереди (`--text-lg`) + badge с количеством `totalActiveCount`

### 4. Компоненты и зоны

| Зона | Компонент | Размер | Описание |
|------|-----------|--------|----------|
| Queue Sidebar | Список очередей | 200px fixed width | Системные + пользовательские очереди |
| Queue Toolbar | Filter bar + actions | 40px height | Фильтры, сортировка, bulk actions |
| Queue Items Grid | AG Grid | Оставшееся пространство | Содержимое выбранной очереди |
| Detail Panel | Side panel | 400px default, resizable | Открывается по клику на строку |
| Bulk Actions Bar | Bottom bar | 48px height, slide-up | При множественном выборе |

**Queue Sidebar — элемент очереди:**

```
┌────────────────────────────┐
│ ● Ожидают одобрения    12  │  ← selected: --bg-active, left accent bar (2px blue)
├────────────────────────────┤
│   Ошибки выполнения    3   │  ← unselected: hover → --bg-tertiary
├────────────────────────────┤
│   Расхождения         ✓    │  ← 0 items: зелёная галочка вместо числа
└────────────────────────────┘
```

- Иконка слева: `●` (6px filled circle) — цвет по `queue_type`:
  - ATTENTION: `--status-error` (#DC2626)
  - DECISION: `--status-warning` (#D97706)
  - PROCESSING: `--status-info` (#2563EB)
- Название очереди: `--text-sm` (13px), `--text-primary`
- Count badge: `--text-sm`, monospace, `--text-secondary`. При 0 → зелёная галочка (✓) в `--status-success`
- Selected: `--bg-active` (#EFF6FF), left border 2px `--accent-primary`
- Hover (не selected): `--bg-tertiary` (#F3F4F6)
- Row height: 36px
- Custom queue icon: `○` (6px outlined circle), `--text-tertiary`
- Кнопка «+ Очередь» внизу sidebar: ghost button, full width, `--text-secondary`

### 5. Данные и колонки

Колонки грида **зависят от типа очереди** — каждая очередь показывает контекстно-релевантные данные.
Базовый набор колонок наследуется от Operational Grid ([seller-operations.md §Grid columns](../modules/seller-operations.md#grid-columns)),
с дополнительными queue-specific колонками.

**Общие колонки (все очереди):**

| Column | Отображение | Align | Font |
|--------|------------|-------|------|
| Checkbox (☐) | Selection | center | — |
| `sku_code` | Артикул | left | mono |
| `product_name` | Товар | left | Inter |
| `marketplace_type` | МП | left | Inter |
| `current_price` | Текущая цена | right | mono |
| `cost_price` | Себестоимость | right | mono |
| `margin_pct` | Маржа % | right | mono |

**Queue-specific колонки:**

| Очередь | Дополнительные колонки | Описание |
|---------|----------------------|----------|
| Ожидают одобрения | `target_price`, `price_change_pct`, `execution_mode`, `policy_name`, `explanation_summary`, `created_at` | Целевая цена, % изменения, политика, объяснение |
| Ошибки выполнения | `last_action_status`, `last_error`, `attempt_count`, `max_attempts`, `last_attempt_at` | Детали ошибки, количество попыток |
| Расхождения | `mismatch_type`, `expected_value`, `actual_value`, `delta_pct`, `severity`, `detected_at` | Тип расхождения, значения, серьёзность |
| Нет себестоимости | `available_stock`, `revenue_30d`, `velocity_14d`, `active_policy` | Контекст для приоритизации задания COGS |
| Критичные остатки | `available_stock`, `days_of_cover`, `velocity_14d`, `stock_risk` | Метрики остатков |
| Свежие решения | `decision_type`, `target_price`, `price_change_pct`, `action_status`, `policy_name`, `explanation_summary`, `created_at` | Результат pricing run |

**entitySummary в queue items response:**

Queue items API возвращает `entitySummary` — denormalized snippet, структура зависит от `entity_type`:

```json
// entity_type = "price_action" (Ожидают одобрения)
{
  "offerName": "Футболка синяя",
  "skuCode": "ABC-001",
  "marketplaceType": "WB",
  "currentPrice": 1500.00,
  "targetPrice": 1200.00,
  "priceChangePct": -20.0,
  "executionMode": "LIVE",
  "policyName": "Маржа 25% WB",
  "explanationSummary": "[Решение] CHANGE: 1 500 → 1 200 (−20.0%)...",
  "actionStatus": "PENDING_APPROVAL",
  "createdAt": "2026-03-30T10:00:00Z"
}
```

```json
// entity_type = "price_action" (Ошибки выполнения)
{
  "offerName": "Штаны чёрные",
  "skuCode": "DEF-002",
  "marketplaceType": "OZON",
  "actionStatus": "FAILED",
  "lastError": "HTTP 429 rate limit",
  "attemptCount": 3,
  "maxAttempts": 3,
  "lastAttemptAt": "2026-03-30T12:15:00Z"
}
```

```json
// entity_type = "alert_event" (Расхождения)
{
  "offerName": "Куртка зимняя",
  "skuCode": "GHI-003",
  "mismatchType": "PRICE",
  "expectedValue": "1200.00",
  "actualValue": "1500.00",
  "deltaPct": 25.0,
  "severity": "WARNING",
  "detectedAt": "2026-03-30T15:00:00Z"
}
```

```json
// entity_type = "marketplace_offer" (Нет себестоимости / Критичные остатки)
{
  "offerName": "Рубашка белая",
  "skuCode": "JKL-004",
  "marketplaceType": "WB",
  "currentPrice": 2500.00,
  "availableStock": 3,
  "daysOfCover": 1.2,
  "stockRisk": "CRITICAL",
  "velocity14d": 2.5,
  "revenue30d": 75000.00
}
```

```json
// entity_type = "price_decision" (Свежие решения)
{
  "offerName": "Футболка красная",
  "skuCode": "MNO-005",
  "decisionType": "CHANGE",
  "currentPrice": 1800.00,
  "targetPrice": 1590.00,
  "priceChangePct": -11.7,
  "policyName": "Маржа 25% WB",
  "actionStatus": "SUCCEEDED",
  "explanationSummary": "[Решение] CHANGE: 1 800 → 1 590 (−11.7%)...",
  "createdAt": "2026-03-31T08:00:00Z"
}
```

### 6. Фильтрация

**Sidebar-level:** выбор очереди в sidebar = основной фильтр. Очередь определяет `auto_criteria`.

**Grid-level фильтры (filter bar над гридом):**

| Фильтр | Тип | Доступность |
|--------|-----|-------------|
| Маркетплейс | Multi-select (WB, Ozon) | Все очереди |
| Подключение | Multi-select по `connection_id` | Все очереди |
| Артикул / Название | Text search (debounce 300ms) | Все очереди |
| Статус задачи | Multi-select (PENDING, IN_PROGRESS, DONE, DISMISSED) | Все очереди |
| Назначен мне | Toggle boolean (`assignedToMe=true`) | Все очереди |
| Серьёзность | Multi-select (WARNING, CRITICAL) | Расхождения |
| Тип расхождения | Multi-select (PRICE, STOCK, PROMO, FINANCE) | Расхождения |
| Решение | Multi-select (CHANGE, SKIP, HOLD) | Свежие решения |
| Статус action | Multi-select (SUCCEEDED, FAILED, PENDING_APPROVAL, ...) | Свежие решения, Ошибки |

Фильтры отображаются как compact pills (как описано в [frontend-design-direction.md §Filter bar](frontend-design-direction.md#filter-bar)).
Active filter pills: `[Маркетплейс: WB ×] [Назначен мне ×] [+ Фильтр] [⊘ Сбросить]`.

### 7. Сортировка

**Default sort per queue:**

| Очередь | Default sort | Direction |
|---------|-------------|-----------|
| Ожидают одобрения | `created_at` | ASC (старые первыми — они ближе к expiration) |
| Ошибки выполнения | `updated_at` | DESC (последние ошибки первыми) |
| Расхождения | `severity` → `detected_at` | CRITICAL first, затем DESC |
| Нет себестоимости | `revenue_30d` | DESC (приоритет — высокая выручка) |
| Критичные остатки | `days_of_cover` | ASC (самые критичные первыми) |
| Свежие решения | `created_at` | DESC (новейшие первыми) |

Все видимые колонки — сортируемые (click на header). Whitelist аналогичен Operational Grid.

### 8. Пагинация

- Server-side pagination.
- Размеры страниц: **20** (default) / 50 / 100. Меньший default чем в Operational Grid, т.к. queue items требуют больше внимания per-item.
- Counter: `Показано 1–20 из 12` (в формате `{from}–{to} из {total}`).
- Навигация: `[◀ Назад]` `[Вперёд ▶]` + input номера страницы.
- API: `?page=0&size=20`

### 9. Действия и кнопки

**Toolbar (всегда видна):**

| Кнопка | Тип | Расположение | Условие |
|--------|-----|-------------|---------|
| Фильтры | Filter pills | Левая часть toolbar | Всегда |
| Columns | Icon button (vertical bars) | Правая часть | Всегда |
| Экспорт | Ghost button | Правая часть | Всегда |
| Density toggle | Icon button | Правая часть | Всегда |

**Per-row action buttons (в колонке Actions, rightmost):**

| Очередь | Кнопка 1 | Кнопка 2 | Кнопка 3 |
|---------|----------|----------|----------|
| Ожидают одобрения | ✓ Одобрить (Primary icon 16px) | ✗ Отклонить (Danger icon 16px) | ⊙ Приостановить (Ghost icon) |
| Ошибки выполнения | ↻ Повторить (Primary icon) | ✗ Отменить (Danger icon) | — |
| Расхождения | ✓ Подтвердить (Primary icon) | → Перейти к монитору (Ghost icon) | — |
| Нет себестоимости | ₽ Задать цену (Primary icon) | — | — |
| Критичные остатки | → Открыть деталь (Ghost icon) | — | — |
| Свежие решения | → Открыть деталь (Ghost icon) | — | — |

**Bulk Actions Bar (появляется при выборе ≥1 строки):**

```
┌────────────────────────────────────────────────────────────────────────────┐
│ Выбрано 3                                                                  │
│ [Одобрить все] [Отклонить все] [Экспорт]                              ×   │
└────────────────────────────────────────────────────────────────────────────┘
```

| Очередь | Bulk Actions |
|---------|-------------|
| Ожидают одобрения | `[Одобрить все]` (Primary), `[Отклонить все]` (Danger), `[Экспорт]` (Secondary) |
| Ошибки выполнения | `[Повторить все]` (Primary), `[Отменить все]` (Danger), `[Экспорт]` (Secondary) |
| Расхождения | `[Подтвердить все]` (Primary), `[Экспорт]` (Secondary) |
| Нет себестоимости | `[Экспорт]` (Secondary) |
| Критичные остатки | `[Экспорт]` (Secondary) |
| Свежие решения | `[Экспорт]` (Secondary) |

Bulk Approve / Reject → модальное окно подтверждения:

```
┌─────────────────────────────────────────────────┐
│  Одобрить 3 действия?                        ×  │
│                                                  │
│  Будет одобрено 3 ценовых изменения:             │
│  • ABC-001: 1 500 → 1 200 (−20,0%)              │
│  • DEF-002: 2 300 → 2 100 (−8,7%)               │
│  • GHI-003: 890 → 950 (+6,7%)                   │
│                                                  │
│              [Отмена]   [Одобрить 3 действия]    │
└─────────────────────────────────────────────────┘
```

Bulk Reject требует причину (текстовое поле, обязательное):

```
┌─────────────────────────────────────────────────┐
│  Отклонить 3 действия?                       ×  │
│                                                  │
│  Причина отклонения *                            │
│  ┌───────────────────────────────────────────┐   │
│  │ Цены ниже минимальной маржи               │   │
│  └───────────────────────────────────────────┘   │
│                                                  │
│              [Отмена]   [Отклонить 3 действия]   │
└─────────────────────────────────────────────────┘
```

### 10. Пустые состояния

| Ситуация | Сообщение | Действие |
|----------|-----------|----------|
| Очередь пуста (totalActiveCount = 0) | «Всё обработано — нет задач в очереди.» | Sidebar показывает ✓ вместо числа |
| Фильтры отсекают всё | «Нет элементов, соответствующих фильтрам.» | `[Сбросить фильтры]` |
| Нет custom queues | Секция «ПОЛЬЗОВАТЕЛЬСКИЕ» отсутствует, кнопка `[+ Очередь]` всё равно видна | — |
| Workspace без данных | «Нет подключений к маркетплейсам. Очереди появятся после настройки.» | `[Перейти к настройкам]` |

**Пустая очередь — визуал:**

```
┌──────────────────────────────────────────┐
│                                          │
│              ✓                            │
│    Всё обработано — нет задач            │
│    в очереди.                            │
│                                          │
└──────────────────────────────────────────┘
```

Иконка `✓` (Check circle, lucide): 48px, `--status-success`. Текст: `--text-secondary`, `--text-base` (14px), centered.

### 11. Загрузка

| Ситуация | Паттерн |
|----------|---------|
| Initial page load | Full-area skeleton: sidebar (6 shimmer rows) + grid area (shimmer table) |
| Queue switch (sidebar click) | Grid skeleton (keep sidebar active), 2px top-edge progress bar |
| Data refresh (background) | 2px progress bar top-edge, data stays visible |
| Row action in progress | Spinner (16px) replaces action icon in that row |
| Bulk action in progress | Bulk bar: text changes to «Обработка... (2/3)», spinner, кнопки disabled |

### 12. Ошибки

| Ситуация | Паттерн | Пример |
|----------|---------|--------|
| Queue list load failed | Toast (error), retry button | «Не удалось загрузить очереди. [Повторить]» |
| Queue items load failed | Inline error в grid area | «Ошибка загрузки. [Повторить]» |
| Approve failed (server) | Toast (error, 8s) | «Не удалось одобрить действие. [Повторить]» |
| Approve — CAS conflict (409) | Toast (warning, 8s) | «Действие уже обработано другим пользователем. Обновляем...» + auto-refresh |
| Bulk approve partial failure | Toast (warning) | «Обработано 2 из 3. 1 действие уже обработано.» |
| Permission denied (403) | Toast (error) | «Недостаточно прав для одобрения действий.» |
| Stale data blocking action | Inline message near action button | «Данные устарели. Действие недоступно.» |

**CAS conflict handling (важный сценарий):**

Когда оператор A нажимает «Одобрить», а оператор B уже одобрил тот же action:
1. Backend возвращает HTTP 409 с текущим статусом action
2. Frontend показывает toast: «Действие уже обработано другим пользователем»
3. Строка автоматически обновляется через WebSocket или refetch
4. Item исчезает из очереди (если условия `auto_criteria` больше не матчат)

### 13. Клавиатурные сокращения

| Shortcut | Действие | Контекст |
|----------|----------|----------|
| `↑ / ↓` | Navigate queue items | Grid focused |
| `← / →` | Navigate between sidebar and grid | Any |
| `Enter` | Открыть Detail Panel для выбранной строки | Grid row focused |
| `Escape` | Закрыть Detail Panel | Detail Panel open |
| `Space` | Toggle checkbox на текущей строке | Grid row focused |
| `Ctrl+A` | Select all visible rows | Grid focused |
| `Ctrl+Enter` | Одобрить выбранные / Quick action | Row(s) selected in approval queue |
| `Ctrl+Shift+Enter` | Отклонить выбранные | Row(s) selected in approval queue |
| `Ctrl+K` | Command Palette | Global |
| `Ctrl+F` | Focus filter bar | Global |
| `1-6` | Переключить на системную очередь (#) | Sidebar focused |

### 14. Права доступа

**Permission matrix:**

| Действие | VIEWER | OPERATOR | PRICING_MANAGER | ADMIN | OWNER |
|----------|--------|----------|-----------------|-------|-------|
| Просмотр очередей | ✓ | ✓ | ✓ | ✓ | ✓ |
| Просмотр queue items | ✓ | ✓ | ✓ | ✓ | ✓ |
| Claim item (взять в работу) | — | ✓ | ✓ | ✓ | ✓ |
| Mark done / dismiss | — | ✓ | ✓ | ✓ | ✓ |
| Approve price action | — | — | ✓ | ✓ | ✓ |
| Reject price action | — | — | ✓ | ✓ | ✓ |
| Hold price action | — | ✓ | ✓ | ✓ | ✓ |
| Cancel action | — | ✓ | ✓ | ✓ | ✓ |
| Retry failed action | — | — | ✓ | ✓ | ✓ |
| Manual reconciliation | — | — | — | ✓ | ✓ |
| Acknowledge mismatch | — | ✓ | ✓ | ✓ | ✓ |
| Resolve mismatch | — | ✓ | ✓ | ✓ | ✓ |
| Set cost_price (inline) | — | ✓ | ✓ | ✓ | ✓ |
| Create custom queue | — | ✓ | ✓ | ✓ | ✓ |
| Edit/delete custom queue | — | ✓ (own) | ✓ | ✓ | ✓ |
| Export | ✓ | ✓ | ✓ | ✓ | ✓ |

**UI behaviour при недостаточных правах:**
- Action buttons не отображаются (не disabled, а отсутствуют)
- Bulk action bar показывает только доступные действия
- При прямом API-вызове с недостаточными правами → 403 → toast «Недостаточно прав»

### 15. API-контракты

**Queue list:**

```
GET /api/workspace/{workspaceId}/queues

Response: [
  {
    "queueId": 1,
    "name": "Ожидают одобрения",
    "queueType": "DECISION",
    "isSystem": true,
    "pendingCount": 12,
    "inProgressCount": 0,
    "totalActiveCount": 12
  },
  ...
]
```

**Queue items:**

```
GET /api/workspace/{workspaceId}/queues/{queueId}/items
    ?status=PENDING
    &assignedToMe=false
    &page=0
    &size=20
    &sort=created_at
    &direction=ASC

Response: Spring Page<QueueItemResponse>
```

**Queue item actions:**

| Endpoint | Method | Body | Описание |
|----------|--------|------|----------|
| `.../queues/{queueId}/items/{itemId}/claim` | POST | — | Взять item в работу |
| `.../queues/{queueId}/items/{itemId}/done` | POST | `{ note? }` | Отметить как обработанный |
| `.../queues/{queueId}/items/{itemId}/dismiss` | POST | `{ note? }` | Отклонить item |

**Delegated action endpoints (из [Execution](../modules/execution.md) и [Pricing](../modules/pricing.md)):**

| Endpoint | Method | Body | Queue context |
|----------|--------|------|--------------|
| `/api/workspace/{wId}/actions/{actionId}/approve` | POST | — | Ожидают одобрения |
| `/api/workspace/{wId}/actions/{actionId}/reject` | POST | `{ cancelReason }` | Ожидают одобрения |
| `/api/workspace/{wId}/actions/{actionId}/hold` | POST | `{ holdReason }` | Ожидают одобрения |
| `/api/workspace/{wId}/actions/{actionId}/cancel` | POST | `{ cancelReason }` | Ошибки выполнения |
| `/api/workspace/{wId}/actions/{actionId}/retry` | POST | `{ retryReason }` | Ошибки выполнения |
| `/api/workspace/{wId}/actions/bulk-approve` | POST | `{ actionIds: [...] }` | Ожидают одобрения (bulk) |
| `/api/workspace/{wId}/mismatches/{mismatchId}/acknowledge` | POST | — | Расхождения |
| `/api/workspace/{wId}/mismatches/{mismatchId}/resolve` | POST | `{ resolution, note }` | Расхождения |
| `PUT /api/cost-profiles` | PUT | `{ marketplaceOfferId, costPrice }` | Нет себестоимости |

### 16. Realtime / WebSocket

| Событие | STOMP destination | Действие в UI |
|---------|-------------------|---------------|
| Queue count changed | `/topic/workspace.{id}.queues` | Обновить badge count в sidebar |
| Queue item added | `/topic/workspace.{id}.queues.{queueId}` | Добавить строку в grid (с flash-анимацией) |
| Queue item resolved | `/topic/workspace.{id}.queues.{queueId}` | Удалить строку из grid (fade-out 200ms) |
| Action status changed | `/topic/workspace.{id}.actions` | Обновить строку in-place |
| Mismatch auto-resolved | `/topic/workspace.{id}.mismatches` | Удалить строку, обновить count |

**Optimistic updates:**
- При нажатии «Одобрить» → строка сразу показывает spinner вместо кнопок
- Если succeed → строка плавно исчезает (200ms fade-out) + toast «Одобрено»
- Если fail → spinner убирается, кнопки возвращаются, toast с ошибкой

**Concurrent user conflict:**
- WebSocket сообщение о том, что item обработан другим пользователем → строка мягко исчезает
- Если пользователь успел нажать действие → CAS conflict → toast (см. §12 Ошибки)

---

## Экран 2: Queue Detail / Items View (правая часть)

Items view — это правая сторона двухпанельного layout. Не отдельная страница, а часть Queue List.
Подробная спецификация per-queue.

### 1. URL и навигация

- **Route:** `/workspace/:id/queues/:queueId` — `queueId` меняется при клике в sidebar
- Переход не вызывает full page reload — только refetch данных grid
- Deep link: URL с `queueId` сразу открывает нужную очередь

### 2. Layout и wireframe

Items view занимает всё пространство справа от sidebar (200px). Wireframe — часть основного layout из Экрана 1.

```
┌──────────────────────────────────────────────────────┐
│ Toolbar: [marketplace_type ×] [+ Фильтр] [⊘]        │
│          [Columns ▦] [Export ↓] [⊞ compact/comfort]  │
├──────────────────────────────────────────────────────┤
│ ☐ │ Артикул  │ Товар        │ Целевая │ Δ%    │ ··· │
│───│──────────│──────────────│─────────│───────│─────│
│ ☐ │ ABC-001  │ Футболка     │ 1 200₽  │ −20,0%│ [✓] │
│ ☐ │ DEF-002  │ Штаны        │ 2 100₽  │ −8,7% │ [✓] │
│ ☐ │ GHI-003  │ Куртка       │   950₽  │ +6,7% │ [✓] │
│   │          │              │         │       │     │
├──────────────────────────────────────────────────────┤
│ Показано 1–3 из 3  [◀] 1 [▶]   Размер: [20▼]       │
└──────────────────────────────────────────────────────┘
```

### 3. Заголовок и контекст

Header Items View (над toolbar):

```
┌──────────────────────────────────────────────────────┐
│ ● Ожидают одобрения                               12 │
│   Ценовые действия, ожидающие вашего подтверждения    │
└──────────────────────────────────────────────────────┘
```

- Queue name: `--text-lg` (16px, weight 600)
- Queue type dot: 8px, цвет по `queue_type`
- Count: `--text-lg`, monospace, `--text-secondary`
- Description: `--text-sm`, `--text-secondary`

**Descriptions per queue:**

| Очередь | Подзаголовок |
|---------|-------------|
| Ожидают одобрения | Ценовые действия, ожидающие вашего подтверждения |
| Ошибки выполнения | Действия, завершившиеся с ошибкой |
| Расхождения | Обнаруженные расхождения между ожидаемыми и фактическими данными |
| Нет себестоимости | Товары без заданной себестоимости — не участвуют в авторасчёте цен |
| Критичные остатки | Товары с критически низким уровнем запасов |
| Свежие решения | Ценовые решения за последние 24 часа |

### 4. Компоненты и зоны

| Компонент | Описание |
|-----------|----------|
| Queue header | Название + count + description |
| Filter bar | Горизонтальные filter pills |
| AG Grid | Виртуализированный грид с колонками |
| Pagination footer | Страницы + size selector |
| Row action buttons | Inline icon buttons per row |
| Bulk bar (conditional) | Slide-up при selection |

### 5. Данные и колонки

Колонки описаны в Экране 1, §5. Здесь — column configuration details.

**Column configuration per queue (defaults, user может кастомизировать):**

**«Ожидают одобрения» — default columns:**

| # | Column ID | Label | Width | Frozen |
|---|-----------|-------|-------|--------|
| 0 | checkbox | ☐ | 40px | Yes |
| 1 | sku_code | Артикул | 120px | Yes |
| 2 | product_name | Товар | 200px flex | No |
| 3 | marketplace_type | МП | 60px | No |
| 4 | current_price | Текущая | 100px | No |
| 5 | target_price | Целевая | 100px | No |
| 6 | price_change_pct | Δ% | 80px | No |
| 7 | margin_pct | Маржа % | 80px | No |
| 8 | policy_name | Политика | 160px | No |
| 9 | execution_mode | Режим | 80px | No |
| 10 | created_at | Создано | 120px | No |
| 11 | actions | Действия | 120px | No |

**Колонка `price_change_pct`:** числовая, monospace, подсвечивается:
- Снижение цены (< 0): `--finance-negative` (#DC2626), со стрелкой `↓`
- Повышение цены (> 0): `--finance-positive` (#059669), со стрелкой `↑`

**Колонка `execution_mode`:** badge:
- LIVE: без badge (default, не выделяется)
- SIMULATED: gray badge «Симуляция»

### 6. Фильтрация

Описана в Экране 1, §6. Фильтры сохраняются per queue (localStorage key: `queue_{queueId}_filters`).
При переключении очереди — фильтры сбрасываются до defaults.

### 7. Сортировка

Описана в Экране 1, §7. Текущая сортировка отображается как стрелка ▲/▼ в header колонки.

### 8. Пагинация

Описана в Экране 1, §8.

### 9. Действия и кнопки

**Row click behavior:**
- Single click на строку → открывает Detail Panel (правая панель, push layout)
- Detail Panel показывает полные данные offer (тот же, что в Operational Grid — см. [seller-operations.md §Offer Detail](../modules/seller-operations.md#offer-detail))
- Additional tabs в Detail Panel для queue context:
  - Tab «Объяснение» (для Ожидают одобрения / Свежие решения): полный `explanation_summary`, `constraints_applied`, `guards_evaluated`
  - Tab «Ошибки» (для Ошибки выполнения): `attempts[]` с provider_request/response_summary
  - Tab «Расхождение» (для Расхождения): expected vs actual, severity, timeline

**Context menu (right-click на строку):**

| Действие | Shortcut | Доступность |
|----------|----------|-------------|
| Открыть в Detail Panel | Enter | Всегда |
| Открыть в новой вкладке | Ctrl+Enter | Всегда |
| Скопировать артикул | Ctrl+C | Всегда |
| Одобрить | — | Ожидают одобрения |
| Отклонить | — | Ожидают одобрения |
| Повторить | — | Ошибки выполнения |
| Экспорт выбранного | — | ≥1 selected |

### 10. Пустые состояния

Описаны в Экране 1, §10.

### 11. Загрузка

Описана в Экране 1, §11.

### 12. Ошибки

Описаны в Экране 1, §12.

### 13. Клавиатурные сокращения

Описаны в Экране 1, §13.

### 14. Права доступа

Описаны в Экране 1, §14.

### 15. API-контракты

Описаны в Экране 1, §15.

### 16. Realtime / WebSocket

Описаны в Экране 1, §16.

---

## Экран 3: Create/Edit Custom Queue (Модальное окно)

### 1. URL и навигация

- **Route:** не меняется (модал поверх текущей страницы)
- **Trigger:** кнопка `[+ Очередь]` в sidebar или context menu на custom queue → «Редактировать»
- **Модал:** centered overlay, `--shadow-md`, max-width 640px

### 2. Layout и wireframe

```
┌─────────────────────────────────────────────────────────────┐
│  Создать очередь                                         ×  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Название *                                                 │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ Высокая маржа WB                                    │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                             │
│  Тип очереди                                                │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ Требует внимания (ATTENTION)                    ▼   │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                             │
│  ─── Критерии автоматического наполнения ───────────────    │
│                                                             │
│  Правило 1                                                  │
│  ┌──────────────┐ ┌────────┐ ┌──────────────────┐ [×]      │
│  │ marketplace  ▼│ │ равно ▼│ │ WB               ▼│         │
│  └──────────────┘ └────────┘ └──────────────────┘          │
│                                                             │
│  Правило 2                                                  │
│  ┌──────────────┐ ┌────────┐ ┌──────────────────┐ [×]      │
│  │ margin_pct   ▼│ │ ≥     ▼│ │ 25               │         │
│  └──────────────┘ └────────┘ └──────────────────┘          │
│                                                             │
│  [+ Добавить правило]                                       │
│                                                             │
│  ─── Предпросмотр ─────────────────────────────────────    │
│  Найдено товаров: 47                                        │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│                              [Отмена]   [Создать очередь]   │
└─────────────────────────────────────────────────────────────┘
```

### 3. Заголовок и контекст

- **Create mode:** «Создать очередь»
- **Edit mode:** «Редактировать очередь» + текущее название
- Font: `--text-lg` (16px, weight 600)
- Close button: `×` в правом верхнем углу

### 4. Компоненты и зоны

| Компонент | Описание |
|-----------|----------|
| Header | Заголовок + close button |
| Name input | Текстовое поле, обязательное |
| Queue type selector | Dropdown: ATTENTION, DECISION, PROCESSING |
| Criteria builder | Visual rule builder (AND logic) |
| Preview counter | Количество matching entities |
| Footer | Cancel + Submit buttons |

### 5. Данные и колонки

**Поля формы:**

| Поле | Тип | Обязательное | Validation |
|------|-----|-------------|-----------|
| `name` | Text input (32px height) | Да | 1–200 символов, unique per workspace |
| `queue_type` | Dropdown | Да | ATTENTION / DECISION / PROCESSING |
| `auto_criteria` | Rule builder | Нет (пустые criteria → manual-only queue) | Valid JSON rules |

**Queue type options (русские labels):**

| Value | Label | Описание |
|-------|-------|----------|
| ATTENTION | Требует внимания | Проблемы, требующие реакции |
| DECISION | Ожидает решения | Элементы, ожидающие approve/decline |
| PROCESSING | В обработке | Информационная очередь (мониторинг) |

### 6. Фильтрация

Не применимо (это форма, не grid).

### 7. Сортировка

Не применимо.

### 8. Пагинация

Не применимо.

### 9. Действия и кнопки

**Auto criteria builder — визуальный конструктор правил:**

Каждое правило = тройка: `[Поле] [Оператор] [Значение]`

**Доступные поля для criteria:**

| Field ID | Label (русский) | Type | Operators | Possible values |
|----------|-----------------|------|-----------|----------------|
| `connection_id` | Подключение | Select | `eq`, `in` | Dropdown connections |
| `marketplace_type` | Маркетплейс | Enum | `eq`, `in` | WB, OZON |
| `category` | Категория | Select | `eq`, `in` | Dropdown categories |
| `brand` | Бренд | Text | `eq`, `in` | Text/multi-select |
| `stock_risk` | Риск остатков | Enum | `eq`, `in` | CRITICAL, WARNING, NORMAL |
| `has_active_policy` | Есть ценовая политика | Boolean | `eq` | true, false |
| `margin_pct` | Маржа % | Number | `gt`, `lt`, `gte`, `lte` | Numeric input |
| `status` | Статус товара | Enum | `eq`, `in` | ACTIVE, ARCHIVED, BLOCKED |
| `has_manual_lock` | Ручная блокировка | Boolean | `eq` | true, false |
| `last_action_status` | Статус действия | Enum | `eq`, `in` | FAILED, PENDING_APPROVAL, SUCCEEDED, ... |
| `promo_status` | Промо-статус | Enum | `eq`, `in` | PARTICIPATING, ELIGIBLE |

**Operators — локализация:**

| Operator | Label |
|----------|-------|
| `eq` | равно |
| `neq` | не равно |
| `in` | одно из |
| `gt` | больше |
| `lt` | меньше |
| `gte` | ≥ |
| `lte` | ≤ |

**Rule builder interactions:**

1. Нажать `[+ Добавить правило]` → появляется новая строка с тремя пустыми dropdown
2. Выбрать поле → operator dropdown обновляется (только совместимые операторы)
3. Выбрать оператор → value input обновляется (dropdown для enum, input для number/text)
4. Удалить правило: `[×]` иконка справа от строки
5. Правила объединяются через AND (все должны совпадать)
6. **Preview** обновляется при каждом изменении (debounce 500ms) — lightweight count query

**Кнопки формы:**

| Кнопка | Тип | Условие |
|--------|-----|---------|
| Отмена | Secondary | Всегда |
| Создать очередь / Сохранить | Primary | Когда форма валидна |

**Edit mode — дополнительные кнопки:**

| Кнопка | Тип | Описание |
|--------|-----|----------|
| Удалить очередь | Danger (ghost, внизу формы) | Показывает confirmation modal |

### 10. Пустые состояния

| Ситуация | Сообщение |
|----------|-----------|
| Нет правил (criteria пусты) | Inline hint: «Без критериев очередь будет пустой — элементы можно будет добавлять только вручную» |
| Preview = 0 | «Подходящих товаров: 0. Попробуйте изменить критерии.» |

### 11. Загрузка

| Ситуация | Паттерн |
|----------|---------|
| Preview counting | Spinner (16px) рядом с «Найдено товаров: ...» |
| Save in progress | Submit button disabled + spinner inside |
| Categories/connections loading | Shimmer в dropdown lists |

### 12. Ошибки

| Ситуация | Паттерн |
|----------|---------|
| Duplicate name | Inline error under field: «Очередь с таким названием уже существует» |
| Empty name | Inline error: «Введите название очереди» |
| Invalid criteria | Inline error under rule: «Выберите значение» |
| Save failed | Toast (error): «Не удалось сохранить очередь. [Повторить]» |

**Validation timing:** inline, on blur + on submit.

### 13. Клавиатурные сокращения

| Shortcut | Действие |
|----------|----------|
| `Escape` | Закрыть модал (если нет несохранённых изменений — сразу; если есть — confirmation) |
| `Ctrl+Enter` | Submit форму |
| `Tab` | Навигация между полями |

### 14. Права доступа

| Действие | Роли |
|----------|------|
| Create custom queue | OPERATOR, PRICING_MANAGER, ADMIN, OWNER |
| Edit own custom queue | OPERATOR (own), PRICING_MANAGER, ADMIN, OWNER |
| Edit any custom queue | ADMIN, OWNER |
| Delete custom queue | OPERATOR (own), PRICING_MANAGER, ADMIN, OWNER |

VIEWER не видит кнопку `[+ Очередь]` и не имеет пункта «Редактировать» в context menu.

### 15. API-контракты

**Create:**

```
POST /api/workspace/{workspaceId}/queues

Body:
{
  "name": "Высокая маржа WB",
  "queueType": "ATTENTION",
  "autoCriteria": {
    "entity_type": "marketplace_offer",
    "match_rules": [
      { "field": "marketplace_type", "op": "eq", "value": "WB" },
      { "field": "margin_pct", "op": "gte", "value": 25.0 }
    ]
  }
}

Response: 201 Created
{
  "queueId": 7,
  "name": "Высокая маржа WB",
  "queueType": "ATTENTION",
  "pendingCount": 47,
  "inProgressCount": 0,
  "totalActiveCount": 47
}
```

**Update:**

```
PUT /api/workspace/{workspaceId}/queues/{queueId}

Body: { "name": "...", "queueType": "...", "autoCriteria": { ... } }

Response: 200 OK { ... }
```

**Delete:**

```
DELETE /api/workspace/{workspaceId}/queues/{queueId}

Response: 204 No Content
```

**Preview count (для real-time preview в форме):**

```
POST /api/workspace/{workspaceId}/queues/preview-count

Body: { "autoCriteria": { ... } }

Response: { "matchCount": 47 }
```

### 16. Realtime / WebSocket

Не применимо (модальное окно). Preview count обновляется через REST-вызовы с debounce.

---

## Экран 4: Queue Actions (Действия из контекста очереди)

Действия выполняются inline из grid (кнопки в строках) или через bulk actions bar.
Не являются отдельной страницей — это интерактивные элементы внутри Queue Items View.

### 1. URL и навигация

URL не меняется. Действия выполняются через API-вызовы, состояние обновляется in-place.

### 2. Layout и wireframe

**Approve flow (Ожидают одобрения):**

```
Строка до:
│ ABC-001 │ Футболка │ 1 200₽ │ −20,0% │ [✓ Одобрить] [✗ Отклонить] │

Нажатие [✓ Одобрить]:
│ ABC-001 │ Футболка │ 1 200₽ │ −20,0% │ [⟳ Обработка...]            │

После success (200ms → fade-out):
│ (строка исчезает из очереди, count обновляется: 12 → 11)              │
```

**Reject flow (Ожидают одобрения):**

```
Нажатие [✗ Отклонить] → inline expand или mini-modal:
┌────────────────────────────────────────┐
│ Причина отклонения *                   │
│ ┌──────────────────────────────────┐   │
│ │                                  │   │
│ └──────────────────────────────────┘   │
│           [Отмена] [Отклонить]         │
└────────────────────────────────────────┘
```

**Retry flow (Ошибки выполнения):**

```
Нажатие [↻ Повторить]:
│ DEF-002 │ Штаны │ FAILED │ HTTP 429 │ [⟳ Повтор...]              │

После success → toast: «Повтор запущен» → строка обновляется, status меняется
```

**Set cost_price flow (Нет себестоимости):**

```
Нажатие [₽ Задать цену] → inline edit в ячейке cost_price:
│ JKL-004 │ Рубашка │ —       │ ┌──────────┐ │ [✓] [✗]  │
│         │         │         │ │ 850      ₽│ │          │
│         │         │         │ └──────────┘ │          │

Enter или [✓] → save → строка обновляется, исчезает из очереди
Escape или [✗] → cancel
```

**Mismatch acknowledge flow (Расхождения):**

```
Нажатие [✓ Подтвердить]:
│ GHI-003 │ PRICE │ 1 200 → 1 500 │ [⟳]                           │

После success → toast: «Расхождение подтверждено» → status: ACKNOWLEDGED
Строка остаётся (с обновлённым status badge), уходит из очереди при RESOLVED
```

**Mismatch resolve flow (через Detail Panel):**

```
Detail Panel → Tab «Расхождение»:
┌────────────────────────────────────┐
│ Расхождение #77                    │
│ Тип: Цена                          │
│ Ожидалось: 1 200₽                  │
│ Фактически: 1 500₽                 │
│ Дельта: +25,0%                     │
│ Статус: ACKNOWLEDGED               │
│                                    │
│ Способ решения *                   │
│ ┌──────────────────────────────┐   │
│ │ Перевыставлена цена     ▼   │   │
│ └──────────────────────────────┘   │
│ Комментарий                        │
│ ┌──────────────────────────────┐   │
│ │ Запущен manual pricing run   │   │
│ └──────────────────────────────┘   │
│                                    │
│        [Отменить] [Решено]         │
└────────────────────────────────────┘
```

Resolution type dropdown:

| Value | Label |
|-------|-------|
| ACCEPTED | Ожидаемое расхождение |
| REPRICED | Перевыставлена цена |
| INVESTIGATED | Причина найдена |
| EXTERNAL | Внешняя причина (маркетплейс) |

### 3. Заголовок и контекст

Контекст действия определяется очередью (§3 в Экране 2).

### 4. Компоненты и зоны

| Компонент | Описание |
|-----------|----------|
| Row action buttons | Icon buttons 24×24 в Actions column |
| Inline reject form | Expandable inline form (причина) |
| Inline cost_price editor | Number input в ячейке |
| Confirmation modal | Для bulk actions |
| Detail Panel resolution form | Для mismatch resolve |

### 5. Данные и колонки

Данные обновляются optimistically — UI отражает действие до ответа сервера.

**Approve response mapping:**

```
POST /api/workspace/{wId}/actions/{actionId}/approve
→ 200 OK
→ Queue item status: PENDING → DONE (auto-resolution by auto_criteria)
→ price_action.status: PENDING_APPROVAL → APPROVED → SCHEDULED (в одной транзакции)
→ UI: строка исчезает из очереди
```

### 6. Фильтрация

Не применимо.

### 7. Сортировка

Не применимо.

### 8. Пагинация

Не применимо.

### 9. Действия и кнопки

**Summary всех действий per queue:**

| Очередь | Per-row actions | Bulk actions | Detail Panel actions |
|---------|----------------|-------------|---------------------|
| Ожидают одобрения | Одобрить, Отклонить, Приостановить | Одобрить все, Отклонить все | Полный explanation, Одобрить/Отклонить/Hold |
| Ошибки выполнения | Повторить, Отменить | Повторить все, Отменить все | Attempt history, Повторить/Отменить |
| Расхождения | Подтвердить, Перейти к монитору | Подтвердить все | Resolve (форма с resolution type + note) |
| Нет себестоимости | Задать цену (inline) | — | Inline edit cost_price, history |
| Критичные остатки | Открыть деталь | — | Full offer detail + inventory tab |
| Свежие решения | Открыть деталь | — | Full decision explanation |

**Confirmation patterns per action:**

| Действие | Confirmation? | Type |
|----------|--------------|------|
| Approve (single) | Нет (instant) | — |
| Reject (single) | Да (inline form: причина, обязательно) | Inline |
| Hold (single) | Да (inline form: причина, опционально) | Inline |
| Retry (single) | Нет (instant) | — |
| Cancel (single) | Да (inline form: причина, обязательно) | Inline |
| Approve (bulk) | Да (modal с перечнем) | Modal |
| Reject (bulk) | Да (modal с причиной) | Modal |
| Set cost_price | Нет (save on Enter/blur) | Inline edit |
| Mismatch resolve | Да (form в Detail Panel) | Panel form |

### 10. Пустые состояния

Не применимо (действия выполняются из непустой очереди).

### 11. Загрузка

| Ситуация | Паттерн |
|----------|---------|
| Action in progress | Spinner (16px) replaces action buttons в строке |
| Bulk in progress | Bulk bar: «Обработка... (N/M)», progress indicator, кнопки disabled |
| Cost price saving | Input border → `--accent-primary` (saving indicator), затем green flash → restore |

### 12. Ошибки

| Ситуация | Паттерн |
|----------|---------|
| Action failed (500) | Toast (error): «Не удалось выполнить действие. [Повторить]» |
| CAS conflict (409) | Toast (warning): «Элемент уже обработан другим пользователем.» + auto-refresh row |
| Validation error (400) | Inline error under field: «Причина обязательна» |
| Permission denied (403) | Toast (error): «Недостаточно прав для этого действия.» |
| Cost price validation | Inline: «Себестоимость должна быть больше 0» |
| Concurrent bulk partial | Toast: «Обработано N из M. K элементов уже обработаны.» |

### 13. Клавиатурные сокращения

| Shortcut | Действие | Контекст |
|----------|----------|----------|
| `Ctrl+Enter` | Одобрить выбранные строки | Ожидают одобрения, ≥1 selected |
| `Ctrl+Shift+Enter` | Отклонить выбранные | Ожидают одобрения, ≥1 selected |
| `Enter` | Confirm inline action (cost_price save, reject reason submit) | Inline form focused |
| `Escape` | Cancel inline action | Inline form focused |

### 14. Права доступа

Описаны в Экране 1, §14 (permission matrix).

Дополнительные правила:
- VIEWER видит grid, но все action buttons скрыты
- OPERATOR видит Hold/Cancel/Acknowledge/Resolve/SetCost, но НЕ Approve/Reject/Retry
- PRICING_MANAGER+ видит все actions

### 15. API-контракты

Все endpoints описаны в Экране 1, §15 (Delegated action endpoints).

**Bulk endpoints (дополнительно к seller-operations.md):**

```
POST /api/workspace/{wId}/actions/bulk-approve
Body: { "actionIds": [1, 2, 3] }
Response: {
  "processed": 2,
  "failed": 1,
  "results": [
    { "actionId": 1, "status": "APPROVED" },
    { "actionId": 2, "status": "APPROVED" },
    { "actionId": 3, "error": "CAS_CONFLICT", "currentStatus": "CANCELLED" }
  ]
}
```

```
POST /api/workspace/{wId}/actions/bulk-reject
Body: { "actionIds": [1, 2, 3], "cancelReason": "Ниже минимальной маржи" }
Response: { "processed": 3, "failed": 0, "results": [...] }
```

### 16. Realtime / WebSocket

| Событие | Реакция в UI |
|---------|-------------|
| Action approved (by another user) | Строка fade-out из очереди, toast если была selected |
| Action status changed | Badge обновляется in-place |
| New item added to queue | Строка появляется сверху с flash-анимацией (highlight `--bg-active` 1s → normal) |
| Mismatch auto-resolved | Строка fade-out, count уменьшается |

---

## Пользовательские сценарии (User Flows)

### UF-1: Обработка очереди одобрений (ежедневный workflow)

**Актор:** PRICING_MANAGER
**Цель:** Рассмотреть и одобрить/отклонить ценовые действия

```
1. Логин → workspace → Activity Bar: Seller Operations → Tab: Очереди
2. Sidebar: «Ожидают одобрения» (12) — выбрана по умолчанию (наибольший count)
3. Грид показывает 12 pending actions, отсортированных по created_at ASC (старые первыми)
4. Клик на первую строку → Detail Panel открывается справа
5. Detail Panel → tab «Объяснение»:
   - [Решение] CHANGE: 1 500 → 1 200 (−20,0%)
   - [Политика] «Маржа 25% WB» (TARGET_MARGIN, v3)
   - [Стратегия] target_margin=25.0%, effective_cost_rate=38.2%...
   - [Guards] Все пройдены
6. Оператор оценивает решение → нажимает [✓ Одобрить] в строке
7. Строка исчезает (fade-out), count: 12 → 11, toast: «Одобрено»
8. Автоматический focus на следующую строку

Alternative flow — bulk approve:
4a. Оператор видит что все 5 первых actions от одной policy, все CHANGE −5-10%
4b. Shift+Click: выбирает 5 строк → Bulk bar: «Выбрано 5 [Одобрить все] [Отклонить все]»
4c. [Одобрить все] → Modal: «Одобрить 5 действий?» + краткий перечень
4d. Подтверждение → Bulk bar: «Обработка... (3/5)» → toast: «5 действий одобрено»
4e. Все 5 строк исчезают, count: 12 → 7

Alternative flow — reject:
6a. Оператор видит слишком агрессивное снижение → нажимает [✗ Отклонить]
6b. Inline form: «Причина отклонения *» → вводит «Снижение больше допустимого»
6c. [Отклонить] → строка исчезает, toast: «Отклонено»
```

### UF-2: Создание пользовательской очереди

**Актор:** OPERATOR
**Цель:** Создать очередь для мониторинга товаров WB с высокой маржой

```
1. Sidebar → [+ Очередь]
2. Modal: «Создать очередь»
3. Название: «WB маржа > 30%»
4. Тип: «В обработке» (PROCESSING)
5. [+ Добавить правило]:
   - Правило 1: [Маркетплейс] [равно] [WB]
   - Правило 2: [Маржа %] [≥] [30]
6. Preview: «Найдено товаров: 47» (обновляется в реальном времени)
7. [Создать очередь]
8. Modal закрывается → новая очередь появляется в секции «ПОЛЬЗОВАТЕЛЬСКИЕ» с count 47
9. Автоматический переход на новую очередь → грид показывает 47 items
```

### UF-3: Обработка ошибок выполнения

**Актор:** PRICING_MANAGER
**Цель:** Разобраться с failed actions и решить: повторить или отменить

```
1. Sidebar: «Ошибки выполнения» (3) — красная точка
2. Грид: 3 failed actions
3. Клик на первую строку → Detail Panel → tab «Ошибки»:
   - Attempt 1: HTTP 429 rate limit (10:15)
   - Attempt 2: HTTP 429 rate limit (10:20)
   - Attempt 3: HTTP 429 rate limit (10:30) — terminal
4. Оператор оценивает: rate limit — transient, стоит повторить позже
5. [↻ Повторить] → toast: «Повтор запущен» → строка обновляется, status → IN_PROGRESS
6. Через 30s WebSocket: action SUCCEEDED → строка исчезает, count: 3 → 2

Клик на вторую строку:
7. Detail Panel: «HTTP 400: price below marketplace minimum»
8. Оператор решает: нужно пересмотреть policy constraints
9. [✗ Отменить] → inline form: «Причина: цена ниже минимума маркетплейса»
10. Строка исчезает, count: 2 → 1
```

---

## Edge Cases

### EC-1: Пустая очередь (0 items)

**Поведение:**
- Sidebar: badge заменяется на ✓ (зелёная галочка, `--status-success`)
- Grid area: empty state — иконка ✓ (48px) + «Всё обработано — нет задач в очереди.»
- Toolbar visible, но фильтры disabled (нечего фильтровать)
- Если есть custom queue с 0 items — аналогичное поведение

### EC-2: Concurrent approval (конфликт при одновременной работе)

**Сценарий:** Оператор A и Оператор B видят одну и ту же очередь approval.

```
t=0: A и B видят item #101 (status: PENDING_APPROVAL)
t=1: A нажимает [Одобрить] → POST /actions/101/approve → 200 OK
t=2: WebSocket: item #101 status changed → B получает update
t=3: B нажимает [Одобрить] на item #101 (уже в APPROVED)
     → POST /actions/101/approve → 409 Conflict
     → Response: { "currentStatus": "APPROVED", "approvedBy": "Оператор A" }
t=4: B видит toast: «Действие уже одобрено пользователем Оператор A»
     Строка обновляется in-place или исчезает из очереди
```

**Защита:**
- WebSocket обновления уменьшают window для конфликта
- CAS на backend гарантирует single-winner
- UI gracefully handles 409: toast + auto-refresh

### EC-3: Large queue (>1000 items)

**Поведение:**
- AG Grid виртуализация — рендерит только видимые строки
- Server-side pagination, 20 items per page
- Badge в sidebar: число с разделителем тысяч: `1 234`
- Bulk select: «Выбрать все» выбирает только текущую страницу
  - Для bulk approve всех: «Выбрать все N на всех страницах» (link под bulk bar)
  - При > 100 items: confirmation modal: «Вы собираетесь одобрить {N} действий. Это может занять некоторое время.»

### EC-4: Queue item entity deleted

**Сценарий:** Item в очереди ссылается на entity, которая была удалена (offer archived, action superseded).

**Поведение:**
- Auto-population job при следующем цикле (5 min) помечает assignment как DONE (condition no longer matches)
- Если пользователь видит строку до auto-resolution: `entitySummary` показывает последние известные данные
- При попытке выполнить действие → 404 или 409 → toast: «Элемент больше не актуален» → строка исчезает

### EC-5: WebSocket disconnect

**Поведение:**
- Status Bar: persistent yellow banner «Соединение потеряно. Переподключение...»
- Queue counts и items НЕ обновляются в реальном времени
- Actions работают (REST), но без optimistic UI updates — показывать loading state дольше
- При reconnect: full refetch текущей queue, обновление counts в sidebar

### EC-6: ClickHouse unavailable (graceful degradation)

**Поведение:**
- Очереди с PostgreSQL-only criteria работают нормально
- «Критичные остатки» (ClickHouse-dependent): sidebar показывает «—» вместо count
- Grid для «Критичные остатки»: «Данные временно недоступны. ClickHouse-метрики не загружены.»
- Колонки `days_of_cover`, `stock_risk`, `velocity_14d`, `revenue_30d` в других очередях: `null` → «—» (dash)

---

## Permission Matrix — сводная таблица

| Действие | VIEWER | OPERATOR | PRICING_MANAGER | ADMIN | OWNER |
|----------|:------:|:--------:|:---------------:|:-----:|:-----:|
| **Чтение** | | | | | |
| Просмотр списка очередей | ✓ | ✓ | ✓ | ✓ | ✓ |
| Просмотр содержимого очереди | ✓ | ✓ | ✓ | ✓ | ✓ |
| Открыть Detail Panel | ✓ | ✓ | ✓ | ✓ | ✓ |
| Экспорт CSV | ✓ | ✓ | ✓ | ✓ | ✓ |
| **Queue Management** | | | | | |
| Claim item (взять в работу) | — | ✓ | ✓ | ✓ | ✓ |
| Mark done / dismiss | — | ✓ | ✓ | ✓ | ✓ |
| Create custom queue | — | ✓ | ✓ | ✓ | ✓ |
| Edit own custom queue | — | ✓ | ✓ | ✓ | ✓ |
| Edit any custom queue | — | — | — | ✓ | ✓ |
| Delete own custom queue | — | ✓ | ✓ | ✓ | ✓ |
| Delete any custom queue | — | — | — | ✓ | ✓ |
| **Price Actions** | | | | | |
| Approve | — | — | ✓ | ✓ | ✓ |
| Reject | — | — | ✓ | ✓ | ✓ |
| Hold | — | ✓ | ✓ | ✓ | ✓ |
| Resume (from hold) | — | ✓ | ✓ | ✓ | ✓ |
| Cancel | — | ✓ | ✓ | ✓ | ✓ |
| Retry failed | — | — | ✓ | ✓ | ✓ |
| Manual reconciliation | — | — | — | ✓ | ✓ |
| Bulk approve | — | — | ✓ | ✓ | ✓ |
| Bulk reject | — | — | ✓ | ✓ | ✓ |
| **Mismatches** | | | | | |
| Acknowledge | — | ✓ | ✓ | ✓ | ✓ |
| Resolve | — | ✓ | ✓ | ✓ | ✓ |
| **Data Entry** | | | | | |
| Set cost_price (inline) | — | ✓ | ✓ | ✓ | ✓ |

---

## Технические заметки

### AG Grid configuration

```typescript
// Queue items grid — AG Grid config
{
  rowModelType: 'serverSide',
  pagination: true,
  paginationPageSize: 20,
  paginationPageSizeSelector: [20, 50, 100],
  rowSelection: 'multiple',
  suppressRowClickSelection: true, // selection only via checkbox
  animateRows: true, // for row add/remove animations
  getRowId: (params) => params.data.itemId.toString(),
  rowHeight: 32, // compact mode
  headerHeight: 32,
}
```

### State management (SignalStore)

```
QueueStore (SignalStore):
  - queues: QueueSummary[]           // sidebar data
  - selectedQueueId: number | null
  - items: QueueItem[]               // current queue items
  - pagination: { page, size, total }
  - filters: QueueFilters
  - selectedItemIds: Set<number>
  - loading: boolean
  - error: string | null
```

### WebSocket subscriptions

```
On queue page mount:
  subscribe('/topic/workspace.{id}.queues')           // queue counts
  subscribe('/topic/workspace.{id}.queues.{queueId}') // current queue items

On queue switch:
  unsubscribe previous queueId topic
  subscribe new queueId topic
```

---

## Связанные документы

- [Frontend Design Direction](frontend-design-direction.md) — design system, components, patterns
- [Seller Operations](../modules/seller-operations.md) — working queues schema, REST API, grid columns
- [Execution](../modules/execution.md) — action states, approval flow, CAS guards
- [Pricing](../modules/pricing.md) — decisions, policies, explanation format
- [Promotions](../modules/promotions.md) — promo decisions, promo actions
- [Audit & Alerting](../modules/audit-alerting.md) — alert events, WebSocket infrastructure
