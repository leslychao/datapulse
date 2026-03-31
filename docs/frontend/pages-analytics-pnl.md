# Pages: Analytics & P&L

**Фаза:** B — Trust Analytics
**Модуль Activity Bar:** Аналитика (иконка: `bar-chart-3` из Lucide)
**Зависимости:** [analytics-pnl.md](../modules/analytics-pnl.md), [frontend-design-direction.md](frontend-design-direction.md)

---

## Содержание

- [Навигация внутри модуля](#навигация-внутри-модуля)
- [Общие паттерны аналитических экранов](#общие-паттерны-аналитических-экранов)
- [1. P&L Summary](#1-pnl-summary)
- [2. P&L by Product](#2-pnl-by-product)
- [3. P&L by Posting](#3-pnl-by-posting)
- [4. Posting Detail](#4-posting-detail)
- [5. P&L Trend](#5-pnl-trend)
- [6. Inventory Overview](#6-inventory-overview)
- [7. Inventory by Product](#7-inventory-by-product)
- [8. Stock History](#8-stock-history)
- [9. Returns Summary](#9-returns-summary)
- [10. Returns by Product](#10-returns-by-product)
- [11. Returns Trend](#11-returns-trend)
- [12. Data Quality Status](#12-data-quality-status)
- [13. Reconciliation](#13-reconciliation)
- [User Flow Scenarios](#user-flow-scenarios)
- [Edge Cases](#edge-cases)

---

## Навигация внутри модуля

При клике на иконку «Аналитика» в Activity Bar открывается модуль с tab-based навигацией. Tabs соответствуют подразделам:

| Tab label | Route suffix | Default |
|-----------|-------------|---------|
| P&L | `/analytics/pnl/summary` | **Да** (при открытии модуля) |
| Остатки | `/analytics/inventory/overview` | |
| Возвраты | `/analytics/returns/summary` | |
| Качество данных | `/analytics/data-quality/status` | |

Внутри каждого tab подразделы выбираются через **sub-navigation bar** — горизонтальную полоску с текстовыми ссылками под tab strip:

```
[Сводка]  [По товарам]  [По отправкам]  [Тренд]
```

Активный подраздел — подчёркнут `--accent-primary` (2px bottom border).

---

## Общие паттерны аналитических экранов

### Фильтры

Все аналитические экраны используют **общий filter bar** (горизонтальная полоска над контентом):

| Фильтр | Тип | Обязательный | Описание |
|--------|-----|-------------|----------|
| **Подключение** | Dropdown (single-select) | Нет | Marketplace connection. Пустое = все подключения. Label: «Подключение» |
| **Период** | Month picker | Да (default: текущий месяц) | Формат: «Март 2026». Label: «Период» |

Дополнительные фильтры описаны per screen.

### Форматирование чисел

| Тип | Формат | Пример | Font |
|-----|--------|--------|------|
| Денежные | Space thousands, ₽ suffix | `1 290 ₽` | JetBrains Mono |
| Процент | Одна десятая, запятая | `18,3%` | JetBrains Mono |
| Количество | Space thousands | `1 234` | JetBrains Mono |
| Дельта (+) | `↑ 8,2%` | Цвет: `--finance-positive` (#059669) | JetBrains Mono |
| Дельта (−) | `↓ 2,1%` | Цвет: `--finance-negative` (#DC2626) | JetBrains Mono |
| Дельта (0) | `→ 0,0%` | Цвет: `--finance-zero` (#6B7280) | JetBrains Mono |
| Дата | `28 мар 2026` | — | Inter |

### Финансовое цветовое кодирование

| Значение | Цвет | Token |
|----------|------|-------|
| Положительное (прибыль, выручка) | #059669 | `--finance-positive` |
| Отрицательное (убыток, штрафы) | #DC2626 | `--finance-negative` |
| Ноль | #6B7280 | `--finance-zero` |

### Graceful degradation: ClickHouse недоступен

Все аналитические экраны читают из ClickHouse. При недоступности ClickHouse:

1. **Баннер** (persistent, yellow, 32px, над контентом): «Аналитические данные временно недоступны. Обновите страницу через несколько минут.»
2. **Контент:** последние закешированные данные (TanStack Query cache) показываются с пометкой «Данные могут быть неактуальны» (серый italic-текст под filter bar).
3. **Если кеша нет:** пустое состояние с сообщением: «Не удалось загрузить данные. Проверьте подключение.» + кнопка [Обновить].
4. **KPI cards:** показывают `—` (em-dash) вместо числа. Дельта скрыта.
5. **Графики:** пустая область с сообщением по центру.

---

## 1. P&L Summary

### 1.1. Назначение

Сводная панель прибыльности по выбранному подключению и периоду. Главный «приборный щиток» модуля — первое, что видит пользователь при открытии раздела «Аналитика». Даёт моментальный ответ на вопрос: «Сколько я заработал в этом месяце?»

### 1.2. Route

```
/workspace/:id/analytics/pnl/summary
```

### 1.3. Entry point

- Activity Bar → иконка «Аналитика» (default landing page модуля)
- Sub-nav: **Сводка** (активный)
- Breadcrumb: `Аналитика > P&L > Сводка`

### 1.4. Layout

```
┌─────────────────────────────────────────────────────────────────┐
│ Filter bar: [Подключение ▾]  [Период: Март 2026 ▾]             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐           │
│  │ Выручка  │ │ Расходы  │ │ COGS     │ │ Реклама  │           │
│  │ 1 290 ₽  │ │ −420 ₽   │ │ −310 ₽   │ │ −80 ₽    │           │
│  │ ↑ 12,3%  │ │ ↑ 5,1%   │ │ → 0,0%   │ │ ↓ 3,2%   │           │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘           │
│                                                                 │
│  ┌──────────┐ ┌──────────────────────┐                          │
│  │ P&L      │ │ Reconciliation       │                          │
│  │ 480 ₽    │ │ Residual: 12 ₽       │                          │
│  │ ↑ 8,7%   │ │ Ratio: 0,9%          │                          │
│  └──────────┘ └──────────────────────┘                          │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │              P&L Trend (mini-chart)                      │    │
│  │  ─── Выручка  ─── Расходы  ─── P&L                      │    │
│  │                                                          │    │
│  │    📈 Area chart, 30 дней, granularity = daily           │    │
│  │                                                          │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  Структура расходов (Donut chart)                        │    │
│  │                                                          │    │
│  │  Комиссия: 35%  Логистика: 28%  Хранение: 15%           │    │
│  │  Штрафы: 8%  Прочие: 14%                                │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

Main Area разделена на три логические зоны:
1. **KPI strip** (верхняя) — 6 карточек в одну строку
2. **Mini trend chart** (средняя) — area chart за текущий период
3. **Cost breakdown** (нижняя) — donut chart структуры расходов

### 1.5. Permissions

| Роль | Доступ |
|------|--------|
| OWNER | Полный |
| ADMIN | Полный |
| ANALYST | Полный |
| OPERATOR | Полный (read-only by nature) |

### 1.6. Data sources

| Компонент | API endpoint | ClickHouse source |
|-----------|-------------|-------------------|
| KPI cards | `GET /api/analytics/pnl/summary?connectionId=...&from=...&to=...` | `mart_product_pnl` (aggregated) |
| Mini trend | `GET /api/analytics/pnl/trend?connectionId=...&from=...&to=...&granularity=DAILY` | `fact_finance` (daily on-the-fly) |
| Cost breakdown | Included in summary response | `mart_product_pnl` (aggregated) |

### 1.7. Screen states

| State | Условие | Отображение |
|-------|---------|-------------|
| **Loading** | Первая загрузка | Skeleton: 6 серых прямоугольников (KPI) + серая область chart |
| **Data loaded** | Данные получены | KPI cards + charts |
| **Empty (no data)** | Нет данных за период | KPI cards с `—`, charts пустые. Сообщение: «Нет финансовых данных за выбранный период» |
| **No connections** | Нет подключений | Full-area: «Подключите маркетплейс, чтобы увидеть аналитику» + [Перейти в настройки] |
| **ClickHouse down** | API 503 | Yellow banner + cached data (см. §Graceful degradation) |
| **Partial data** | COGS=0 или advertising=0 | KPI card «COGS» показывает «0 ₽» с tooltip: «Себестоимость не задана». KPI card «Реклама» показывает «0 ₽» с tooltip: «Рекламные данные не подключены» |
| **Refreshing** | Background refetch | 2px progress bar сверху, данные видны |

### 1.8. Wireframe (ASCII)

См. §1.4 Layout.

### 1.9. Interactive elements

| Элемент | Действие | Результат |
|---------|----------|-----------|
| KPI card «Выручка» | Click | Навигация → P&L by Product, sort by revenue desc |
| KPI card «P&L» | Click | Навигация → P&L by Product, sort by full_pnl desc |
| KPI card «Reconciliation Residual» | Click | Навигация → Reconciliation dashboard (admin only, иначе no-op) |
| Mini trend chart | Click on data point | Tooltip с точными значениями за день |
| Mini trend chart | Link «Подробнее →» | Навигация → P&L Trend (полный экран) |
| Cost breakdown donut | Hover segment | Tooltip: название категории + сумма + процент |
| Filter «Подключение» | Change | Re-fetch summary для выбранного connection |
| Filter «Период» | Change | Re-fetch summary за новый период |

### 1.10. Tables

Нет таблиц на этом экране.

### 1.11. Detail panels

Нет detail panel на этом экране.

### 1.12. Forms

Нет форм на этом экране.

### 1.13. Modals

Нет модалок на этом экране.

### 1.14. Charts

**Mini Trend Chart:**

| Параметр | Значение |
|----------|----------|
| Тип | Area chart (ngx-echarts) |
| Серии | 3: Выручка (#059669), Расходы (#DC2626), P&L (#2563EB) |
| Ось X | Дни текущего периода (формат: «28 мар») |
| Ось Y | Сумма в ₽ (auto-scale, space thousands) |
| Фильтры | Наследует connection + period от filter bar |
| Interactivity | Hover → tooltip с точными значениями. Click → tooltip pinned |
| Legend | Горизонтальная, под chart. Кликабельная (toggle series visibility) |
| Высота | 200px |

**Cost Breakdown Chart:**

| Параметр | Значение |
|----------|----------|
| Тип | Donut chart (ngx-echarts) |
| Сегменты | marketplace_commission, logistics_cost, storage_cost, penalties, acceptance_cost, marketing_cost, other_marketplace_charges. Мелкие (<3%) → «Прочие» |
| Центр | Общая сумма расходов |
| Interactivity | Hover → tooltip: название + сумма + процент |
| Palette | Оттенки серого и синего (нейтральные, не яркие — это расходы) |
| Высота | 240px |

### 1.15. Real-time updates

- WebSocket topic: `/topic/analytics.pnl.summary.{connectionId}` — при завершении materialization worker отправляет event.
- При получении event → TanStack Query invalidation → silent background refetch → KPI cards обновляются без перезагрузки страницы.
- Status Bar: «Данные обновлены N мин назад».

### 1.16. Keyboard shortcuts

| Shortcut | Действие |
|----------|----------|
| `1`–`4` (number keys) | Переключение sub-nav: 1=Сводка, 2=По товарам, 3=По отправкам, 4=Тренд |
| `Ctrl+F` | Focus на filter bar |
| `R` | Обновить данные (manual refetch) |

---

## 2. P&L by Product

### 2.1. Назначение

Таблица P&L с разбивкой по товарам за выбранный период. Центральная операционная таблица модуля. Позволяет ответить: «Какие товары приносят прибыль, а какие убыточны?», «Где самые большие расходы?»

### 2.2. Route

```
/workspace/:id/analytics/pnl/by-product
```

### 2.3. Entry point

- Sub-nav: **По товарам**
- Клик на KPI card «Выручка» / «P&L» из P&L Summary
- Breadcrumb: `Аналитика > P&L > По товарам`

### 2.4. Layout

```
┌─────────────────────────────────────────────────────────────────────────┐
│ Filter bar: [Подключение ▾] [Период ▾] [🔍 Поиск по SKU/названию]     │
├─────────────────────────────────────────────────────────────────────────┤
│ Toolbar: [Columns ⊞] [Export ↓]  Showing 1–50 of 1 234  [◀ 1 ▶]      │
├─────────────────────────────────────────────────────────────────────────┤
│ ☑│ SKU      │ Товар        │ Выручка  │ Комиссия │ Эквайр.│ Логист.│…│
│──┼──────────┼──────────────┼──────────┼──────────┼────────┼────────┼─│
│ ☐│ АРТ-001  │ Футболка бел │ 12 500 ₽ │ −2 125 ₽ │ −375 ₽ │ −890 ₽ │…│
│ ☐│ АРТ-002  │ Джинсы синие │  8 900 ₽ │ −1 513 ₽ │ −267 ₽ │ −650 ₽ │…│
│ ☐│ АРТ-003  │ Носки (3 пар │  3 200 ₽ │   −544 ₽ │  −96 ₽ │ −320 ₽ │…│
│  │          │              │          │          │        │        │ │
│  │   ⋮      │       ⋮      │    ⋮     │    ⋮     │   ⋮    │   ⋮    │ │
├─────────────────────────────────────────────────────────────────────────┤
│ Page: [◀] 1 2 3 ... 25 [▶]   Per page: [50 ▾]                         │
└─────────────────────────────────────────────────────────────────────────┘
```

Main Area целиком занята AG Grid. При клике на строку → Detail Panel (right) с расшифровкой P&L по товару.

### 2.5. Permissions

| Роль | Доступ |
|------|--------|
| Все роли | Read-only |

### 2.6. Data sources

| Компонент | API endpoint | ClickHouse source |
|-----------|-------------|-------------------|
| Таблица | `GET /api/analytics/pnl/by-product?connectionId=...&period=YYYYMM&search=...&page=0&size=50&sort=full_pnl,desc` | `mart_product_pnl` JOIN `dim_product` |

### 2.7. Screen states

| State | Условие | Отображение |
|-------|---------|-------------|
| **Loading** | Первая загрузка | AG Grid skeleton (серые строки) |
| **Data loaded** | Данные получены | Заполненная таблица |
| **Empty (no data)** | `totalElements = 0` | Centered: «Нет данных за выбранный период.» + [Очистить фильтры] |
| **Empty (no cost profile)** | Все `cogs_status = NO_COST_PROFILE` | Данные показаны, но persistent info banner: «Себестоимость не задана. Колонки COGS и P&L могут быть неточными.» + [Задать себестоимость] |
| **Filtered empty** | Поиск не дал результатов | «Нет товаров, соответствующих запросу.» + [Очистить поиск] |
| **ClickHouse down** | API 503 | Yellow banner + cached table (если есть) |
| **Refreshing** | Background refetch | 2px progress bar, таблица видна |

### 2.8. Wireframe (ASCII)

См. §2.4 Layout.

### 2.9. Interactive elements

| Элемент | Действие | Результат |
|---------|----------|-----------|
| Строка таблицы | Single click | Detail Panel (right) с расшифровкой P&L по товару |
| Строка таблицы | Double click | Навигация → P&L by Posting с filter `sellerSkuId` |
| Column header | Click | Sort (asc/desc toggle) |
| Column header | Drag border | Resize column |
| Filter «Поиск» | Input text | Debounced search (300ms) по `sku_code` + `product_name` |
| Toolbar «Columns» | Click | Column configuration dropdown (toggle/reorder columns) |
| Toolbar «Export» | Click | Export CSV (server-side, all pages, respects filters) |
| Checkbox column | Click | Toggle row selection (для bulk export) |
| `cogs_status` badge | Hover | Tooltip: «Себестоимость не задана» / «Нет данных о продажах» |

### 2.10. Tables

**Columns (полный перечень):**

| # | Column key | Заголовок (RU) | Тип | Align | Default visible | Frozen | Sort | Описание |
|---|-----------|---------------|-----|-------|----------------|--------|------|----------|
| 1 | `checkbox` | ☑ | Checkbox | Center | Да | Да | Нет | Selection |
| 2 | `sku_code` | Артикул | Text | Left | Да | Да | Да | seller_sku.sku_code |
| 3 | `product_name` | Товар | Text | Left | Да | Нет | Да | marketplace_offer.name (ellipsis, tooltip full) |
| 4 | `source_platform` | МП | Badge | Center | Да | Нет | Да | `WB` / `Ozon` (badge style) |
| 5 | `revenue_amount` | Выручка | Money | Right | Да | Нет | Да | SUM revenue |
| 6 | `marketplace_commission_amount` | Комиссия МП | Money | Right | Да | Нет | Да | Signed, typically <0 |
| 7 | `acquiring_commission_amount` | Эквайринг | Money | Right | Нет | Нет | Да | |
| 8 | `logistics_cost_amount` | Логистика | Money | Right | Да | Нет | Да | |
| 9 | `storage_cost_amount` | Хранение | Money | Right | Нет | Нет | Да | |
| 10 | `penalties_amount` | Штрафы | Money | Right | Нет | Нет | Да | |
| 11 | `acceptance_cost_amount` | Приёмка | Money | Right | Нет | Нет | Да | |
| 12 | `marketing_cost_amount` | Маркетинг | Money | Right | Нет | Нет | Да | |
| 13 | `other_marketplace_charges_amount` | Прочие | Money | Right | Нет | Нет | Да | |
| 14 | `compensation_amount` | Компенсации | Money | Right | Нет | Нет | Да | Typically >0 |
| 15 | `refund_amount` | Возвраты | Money | Right | Да | Нет | Да | Signed (<0 = debit) |
| 16 | `net_cogs` | COGS | Money | Right | Да | Нет | Да | net_cogs after refund netting |
| 17 | `advertising_cost` | Реклама | Money | Right | Да | Нет | Да | 0 в Phase B core |
| 18 | `marketplace_pnl` | P&L (МП) | Money | Right | Нет | Нет | Да | Before COGS & ads |
| 19 | `full_pnl` | P&L | Money | Right | Да | Нет | Да | **Primary P&L.** Color-coded: positive=green, negative=red, zero=gray |
| 20 | `cogs_status` | Статус COGS | Badge | Center | Да | Нет | Да | OK=green, NO_COST_PROFILE=yellow, NO_SALES=gray |

**Column type formatting:**

- **Money:** JetBrains Mono, right-aligned, space thousands, `₽` suffix. Negative = `--finance-negative` color. Positive = `--finance-positive` color (only for revenue, compensation, full_pnl). Costs shown as negative numbers with red color.
- **Badge:** pill-shaped, semantic color. `WB` = purple bg, `Ozon` = blue bg.

**Row height:** 32px (compact).
**Pagination:** Server-side, 50/100/200 per page.
**Default sort:** `full_pnl` DESC.

### 2.11. Detail panels

**P&L Product Detail Panel** (opens on row click):

```
┌─ Футболка белая (АРТ-001) ──────── [×]  │
│                                          │
│  Платформа:  WB                          │
│  Период:     Март 2026                   │
│                                          │
│  ─── P&L расшифровка ───                 │
│                                          │
│  Выручка              12 500 ₽           │
│  ─────────────────────────────           │
│  Комиссия МП          −2 125 ₽           │
│  Эквайринг              −375 ₽           │
│  Логистика              −890 ₽           │
│  Хранение               −210 ₽           │
│  Штрафы                    0 ₽           │
│  Приёмка                −120 ₽           │
│  Маркетинг                 0 ₽           │
│  Прочие                  −45 ₽           │
│  Компенсации              80 ₽           │
│  Возвраты               −650 ₽           │
│  ─────────────────────────────           │
│  P&L (МП)              8 165 ₽           │
│  COGS                 −3 200 ₽           │
│  Реклама                −450 ₽           │
│  ═════════════════════════════           │
│  P&L итого             4 515 ₽  ✓        │
│                                          │
│  Статус COGS: OK ●                       │
│                                          │
│  [Перейти к отправкам →]                 │
│                                          │
└──────────────────────────────────────────┘
```

**Width:** 400px default, resizable (320–50% viewport).
**Tabs within panel:** single tab «P&L» (Phase B). Future: «Ценовая история», «Остатки».
**Link «Перейти к отправкам»:** навигация → P&L by Posting с filter `sellerSkuId`.

### 2.12. Forms

Нет форм на этом экране (read-only analytics).

### 2.13. Modals

Нет модалок.

### 2.14. Charts

Нет charts в основной таблице. Mini sparkline в ячейке (Phase C) — TBD.

### 2.15. Real-time updates

- WebSocket: `/topic/analytics.pnl.product.{connectionId}` — при обновлении mart_product_pnl.
- При event → invalidate TanStack Query → background re-fetch текущей страницы.
- Обновлённые строки — кратковременная подсветка фона (`--bg-active`, 1 pulse, 1s).

### 2.16. Keyboard shortcuts

| Shortcut | Действие |
|----------|----------|
| `↑ / ↓` | Навигация по строкам |
| `Enter` | Открыть Detail Panel для выбранной строки |
| `Escape` | Закрыть Detail Panel |
| `Ctrl+F` | Focus на поле поиска |
| `Space` | Toggle checkbox на текущей строке |
| `Ctrl+E` | Export (если есть выделенные строки — export selection, иначе — all) |

---

## 3. P&L by Posting

### 3.1. Назначение

Таблица P&L на уровне отдельных отправок (posting). Позволяет глубоко разобрать: «Что именно произошло с конкретной отправкой? Какие расходы были? Совпадает ли выплата с суммой компонентов?»

Posting-level — максимальная детализация до перехода к отдельным финансовым записям (fact_finance entries).

### 3.2. Route

```
/workspace/:id/analytics/pnl/by-posting
```

### 3.3. Entry point

- Sub-nav: **По отправкам**
- Double-click на строке в P&L by Product
- Link «Перейти к отправкам» из Detail Panel товара
- Breadcrumb: `Аналитика > P&L > По отправкам`

### 3.4. Layout

```
┌──────────────────────────────────────────────────────────────────────────┐
│ Filter bar: [Подключение ▾] [Дата от ▾] [Дата до ▾] [SKU: _________]   │
├──────────────────────────────────────────────────────────────────────────┤
│ Toolbar: [Columns ⊞] [Export ↓]  Showing 1–50 of 5 678   [◀ 1 ▶]      │
├──────────────────────────────────────────────────────────────────────────┤
│ ☑│ Отправка    │ Дата       │ Выручка │ Комиссия│ Логист.│… │Выплата│Res│
│──┼─────────────┼────────────┼─────────┼─────────┼────────┼──┼───────┼───│
│ ☐│ FBS-12345   │ 28 мар 2026│ 1 290 ₽ │  −219 ₽ │ −120 ₽ │  │ 890 ₽ │12₽│
│ ☐│ FBS-12346   │ 27 мар 2026│ 2 500 ₽ │  −425 ₽ │ −180 ₽ │  │1 780₽ │ 0₽│
│  │    ⋮        │    ⋮       │    ⋮    │    ⋮    │   ⋮    │  │  ⋮    │ ⋮ │
└──────────────────────────────────────────────────────────────────────────┘
```

### 3.5. Permissions

| Роль | Доступ |
|------|--------|
| Все роли | Read-only |

### 3.6. Data sources

| Компонент | API endpoint | ClickHouse source |
|-----------|-------------|-------------------|
| Таблица | `GET /api/analytics/pnl/by-posting?connectionId=...&from=...&to=...&sellerSkuId=...&page=0&size=50&sort=finance_date,desc` | `mart_posting_pnl` JOIN `dim_product` |

### 3.7. Screen states

| State | Условие | Отображение |
|-------|---------|-------------|
| **Loading** | Первая загрузка | AG Grid skeleton |
| **Data loaded** | Данные получены | Заполненная таблица |
| **Empty** | Нет отправок | «Нет отправок за выбранный период.» |
| **Filtered by SKU** | Пришли с P&L by Product | Filter bar показывает active pill: `SKU: АРТ-001 ×` |
| **ClickHouse down** | API 503 | Yellow banner + cached data |

### 3.8. Wireframe (ASCII)

См. §3.4 Layout.

### 3.9. Interactive elements

| Элемент | Действие | Результат |
|---------|----------|-----------|
| Строка таблицы | Single click | Detail Panel (right) с posting P&L breakdown |
| Строка таблицы | Double click | Навигация → Posting Detail (drill-down к entries) |
| Column header | Click | Sort (asc/desc toggle) |
| Reconciliation residual cell | Hover (если ≠ 0) | Tooltip: «Разница между выплатой и суммой компонентов: {amount} ({ratio}%)» |
| Filter pill `SKU: АРТ-001 ×` | Click × | Сбросить SKU-фильтр |

### 3.10. Tables

**Columns (полный перечень):**

| # | Column key | Заголовок (RU) | Тип | Align | Default visible | Frozen | Sort |
|---|-----------|---------------|-----|-------|----------------|--------|------|
| 1 | `checkbox` | ☑ | Checkbox | Center | Да | Да | Нет |
| 2 | `posting_id` | Отправка | Text (mono) | Left | Да | Да | Да |
| 3 | `sku_code` | Артикул | Text | Left | Да | Нет | Да |
| 4 | `product_name` | Товар | Text | Left | Да | Нет | Да |
| 5 | `source_platform` | МП | Badge | Center | Да | Нет | Да |
| 6 | `finance_date` | Дата | Date | Left | Да | Нет | Да |
| 7 | `revenue_amount` | Выручка | Money | Right | Да | Нет | Да |
| 8 | `marketplace_commission_amount` | Комиссия | Money | Right | Да | Нет | Да |
| 9 | `acquiring_commission_amount` | Эквайринг | Money | Right | Нет | Нет | Да |
| 10 | `logistics_cost_amount` | Логистика | Money | Right | Да | Нет | Да |
| 11 | `storage_cost_amount` | Хранение | Money | Right | Нет | Нет | Да |
| 12 | `penalties_amount` | Штрафы | Money | Right | Нет | Нет | Да |
| 13 | `acceptance_cost_amount` | Приёмка | Money | Right | Нет | Нет | Да |
| 14 | `marketing_cost_amount` | Маркетинг | Money | Right | Нет | Нет | Да |
| 15 | `other_marketplace_charges_amount` | Прочие | Money | Right | Нет | Нет | Да |
| 16 | `compensation_amount` | Компенсации | Money | Right | Нет | Нет | Да |
| 17 | `refund_amount` | Возвраты | Money | Right | Да | Нет | Да |
| 18 | `net_payout` | Выплата | Money | Right | Да | Нет | Да |
| 19 | `gross_cogs` | COGS (gross) | Money | Right | Нет | Нет | Да |
| 20 | `net_cogs` | COGS | Money | Right | Да | Нет | Да |
| 21 | `cogs_status` | Статус COGS | Badge | Center | Нет | Нет | Да |
| 22 | `reconciliation_residual` | Residual | Money | Right | Да | Нет | Да |

**Reconciliation residual cell formatting:**
- `0 ₽` → gray text
- `≠ 0` → orange text (`--status-warning`) + warning icon (small `alert-triangle`)
- Tooltip при hover: «Разница между выплатой и суммой компонентов»

**Default sort:** `finance_date` DESC.
**Pagination:** Server-side, 50/100/200.

### 3.11. Detail panels

**Posting P&L Detail Panel** (opens on row click):

```
┌─ Отправка FBS-12345 ──────────── [×]    │
│                                          │
│  МП:         WB                          │
│  Дата:       28 мар 2026                 │
│  Артикул:    АРТ-001                     │
│  Товар:      Футболка белая              │
│                                          │
│  ─── P&L posting ───                     │
│                                          │
│  Выручка              1 290 ₽            │
│  Комиссия МП           −219 ₽            │
│  Эквайринг              −39 ₽            │
│  Логистика             −120 ₽            │
│  ... (all 11 measures) ...               │
│  ─────────────────────────────           │
│  Выплата (net payout)    890 ₽           │
│                                          │
│  COGS                   −310 ₽           │
│  Reconciliation:          12 ₽  ⚠        │
│                                          │
│  [Показать записи →]                     │
│                                          │
└──────────────────────────────────────────┘
```

**Link «Показать записи»:** навигация → Posting Detail (drill-down к fact_finance entries).

### 3.12. Forms

Нет форм.

### 3.13. Modals

Нет модалок.

### 3.14. Charts

Нет charts.

### 3.15. Real-time updates

- WebSocket: `/topic/analytics.pnl.posting.{connectionId}` — при обновлении mart_posting_pnl.
- Background re-fetch текущей страницы.

### 3.16. Keyboard shortcuts

| Shortcut | Действие |
|----------|----------|
| `↑ / ↓` | Навигация по строкам |
| `Enter` | Открыть Detail Panel |
| `Shift+Enter` | Drill-down → Posting Detail |
| `Escape` | Закрыть Detail Panel |
| `Ctrl+F` | Focus на filter bar |

---

## 4. Posting Detail

### 4.1. Назначение

Drill-down до уровня отдельных финансовых записей (fact_finance entries) для одной отправки. Позволяет увидеть: «Из каких именно операций маркетплейса сложился P&L этой отправки?» Каждая строка — одна canonical_finance_entry. Provenance link позволяет дойти до raw-данных от маркетплейса.

### 4.2. Route

```
/workspace/:id/analytics/pnl/posting/:postingId
```

### 4.3. Entry point

- Double-click на строке в P&L by Posting
- Link «Показать записи» из Posting Detail Panel
- Breadcrumb: `Аналитика > P&L > По отправкам > FBS-12345`

### 4.4. Layout

```
┌─────────────────────────────────────────────────────────────────────┐
│ Breadcrumb: Аналитика > P&L > По отправкам > FBS-12345             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Posting: FBS-12345    МП: WB    Дата: 28 мар 2026                  │
│  Артикул: АРТ-001      Товар: Футболка белая                        │
│                                                                     │
│  ─── P&L итого ───                                                  │
│  Выручка: 1 290 ₽   Расходы: −400 ₽   Выплата: 890 ₽              │
│  COGS: −310 ₽   Residual: 12 ₽                                     │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│ Тип операции   │ Выручка │ Комиссия│ Логист. │ … │ Дата       │ 🔗 │
│────────────────┼─────────┼─────────┼─────────┼───┼────────────┼────│
│ SALE_ACCRUAL   │ 1 290 ₽ │  −219 ₽ │  −120 ₽ │   │ 28 мар 2026│ 🔗 │
│ MARKETPLACE_   │       0 │   −15 ₽ │       0 │   │ 28 мар 2026│ 🔗 │
│ COMMISSION     │         │         │         │   │            │    │
│ ACQUIRING      │       0 │       0 │       0 │   │ 28 мар 2026│ 🔗 │
│ (acq: −39 ₽)  │         │         │         │   │            │    │
│────────────────┼─────────┼─────────┼─────────┼───┼────────────┼────│
│ Σ ИТОГО        │ 1 290 ₽ │  −234 ₽ │  −120 ₽ │   │            │    │
└─────────────────────────────────────────────────────────────────────┘
```

Верхняя часть — summary header (key-value pairs). Нижняя — таблица entries. Нет пагинации (обычно 1-5 entries per posting).

### 4.5. Permissions

| Роль | Доступ |
|------|--------|
| Все роли | Таблица entries |
| ADMIN, OWNER | Provenance link (🔗 → raw S3 data) |

### 4.6. Data sources

| Компонент | API endpoint | Source |
|-----------|-------------|-------|
| Summary header | Same as table (computed client-side from entries) | — |
| Entries table | `GET /api/analytics/pnl/posting/{postingId}/details` | `fact_finance` WHERE `posting_id = :postingId` (ClickHouse) |
| Provenance link | `GET /api/analytics/provenance/entry/{entryId}/raw` | canonical_finance_entry (PostgreSQL) → S3 |

### 4.7. Screen states

| State | Условие | Отображение |
|-------|---------|-------------|
| **Loading** | Загрузка | Skeleton header + skeleton table |
| **Data loaded** | Данные получены | Summary + table |
| **Not found** | `postingId` не найден | «Отправка не найдена.» + [Вернуться к списку] |
| **ClickHouse down** | API 503 | Yellow banner + cached data |
| **Raw expired** | Provenance → 404 | Toast: «Исходные данные удалены (retention)» |

### 4.8. Wireframe (ASCII)

См. §4.4 Layout.

### 4.9. Interactive elements

| Элемент | Действие | Результат |
|---------|----------|-----------|
| 🔗 (provenance link) | Click | Для ADMIN/OWNER: загружает presigned S3 URL → opens raw JSON в новой вкладке. Для прочих ролей: скрыт |
| Entry row | Click | Подсвечивает строку, показывает mini-detail inline (below row, accordion-style): полный набор measure columns |
| Back button (breadcrumb) | Click | Навигация → P&L by Posting |
| Summary header «Выплата» value | Hover | Tooltip: «Чистая выплата маркетплейса за отправку» |
| Summary header «Residual» value | Hover | Tooltip: «Разница между выплатой и суммой классифицированных компонентов. Не входит в P&L.» |

### 4.10. Tables

**Columns:**

| # | Column key | Заголовок (RU) | Тип | Align | Описание |
|---|-----------|---------------|-----|-------|----------|
| 1 | `entry_type` | Тип операции | Badge/Text | Left | Canonical entry type. Human-readable mapping: `SALE_ACCRUAL` → «Начисление продажи», `RETURN_REVERSAL` → «Возврат», `MARKETPLACE_COMMISSION` → «Комиссия МП», etc. |
| 2 | `revenue_amount` | Выручка | Money | Right | |
| 3 | `marketplace_commission_amount` | Комиссия | Money | Right | |
| 4 | `acquiring_commission_amount` | Эквайринг | Money | Right | |
| 5 | `logistics_cost_amount` | Логистика | Money | Right | |
| 6 | `storage_cost_amount` | Хранение | Money | Right | |
| 7 | `penalties_amount` | Штрафы | Money | Right | |
| 8 | `acceptance_cost_amount` | Приёмка | Money | Right | |
| 9 | `marketing_cost_amount` | Маркетинг | Money | Right | |
| 10 | `other_marketplace_charges_amount` | Прочие | Money | Right | |
| 11 | `compensation_amount` | Компенсации | Money | Right | |
| 12 | `refund_amount` | Возвраты | Money | Right | |
| 13 | `net_payout` | Выплата | Money | Right | |
| 14 | `finance_date` | Дата | Date | Left | «28 мар 2026» |
| 15 | `provenance` | 🔗 | Icon button | Center | Provenance drill-down (ADMIN/OWNER only) |

**Footer row:** `Σ ИТОГО` — sum of all entries per measure column. Bold font. Background: `--bg-secondary`.

**Zero cells:** display `—` (em-dash) instead of `0 ₽` for readability (most cells in composite row are zero).

**No pagination** (typically 1-5 rows per posting).

### 4.11. Detail panels

Нет отдельного Detail Panel — используется accordion-expand при клике на строку.

### 4.12. Forms

Нет форм.

### 4.13. Modals

**Raw Data Preview Modal** (если ADMIN/OWNER кликнул provenance и raw доступен):

Не требуется для Phase B — provenance link открывает raw JSON в новой вкладке браузера.

### 4.14. Charts

Нет charts.

### 4.15. Real-time updates

Нет real-time updates — экран открывается для конкретного posting (snapshot view).

### 4.16. Keyboard shortcuts

| Shortcut | Действие |
|----------|----------|
| `↑ / ↓` | Навигация по entry rows |
| `Enter` | Expand/collapse entry detail |
| `Escape` | Back → P&L by Posting |
| `Backspace` | Back → P&L by Posting |

---

## 5. P&L Trend

### 5.1. Назначение

Визуализация динамики P&L во времени. Позволяет ответить: «Как менялась прибыль и расходы? Есть ли сезонные паттерны? Когда произошёл спад?»

### 5.2. Route

```
/workspace/:id/analytics/pnl/trend
```

### 5.3. Entry point

- Sub-nav: **Тренд**
- Link «Подробнее →» из mini trend chart на P&L Summary
- Breadcrumb: `Аналитика > P&L > Тренд`

### 5.4. Layout

```
┌─────────────────────────────────────────────────────────────────┐
│ Filter bar: [Подключение ▾] [Дата от ▾] [Дата до ▾]            │
│             Гранулярность: [День] [Неделя] [Месяц]              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                                                         │    │
│  │  ──── Выручка   ──── Расходы   ──── P&L                │    │
│  │                                                         │    │
│  │        ╱╲                                               │    │
│  │   ╱╲  ╱  ╲     ╱╲                                      │    │
│  │  ╱  ╲╱    ╲   ╱  ╲    ╱╲                               │    │
│  │ ╱         ╲  ╱    ╲  ╱  ╲                              │    │
│  │╱           ╲╱      ╲╱    ╲                             │    │
│  │                                                         │    │
│  │  Янв    Фев    Мар    Апр    Май    Июн                 │    │
│  │                                                         │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  Таблица summary (под chart):                            │    │
│  │  Период │ Выручка │ Расходы │ COGS │ Реклама │ P&L      │    │
│  │  Янв 26 │ 45 000 ₽│−15 200₽ │−8 000│   −500₽ │ 21 300₽  │    │
│  │  Фев 26 │ 52 000 ₽│−17 800₽ │−9 200│   −600₽ │ 24 400₽  │    │
│  │  Мар 26 │ 48 500 ₽│−16 100₽ │−8 500│   −550₽ │ 23 350₽  │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

### 5.5. Permissions

| Роль | Доступ |
|------|--------|
| Все роли | Read-only |

### 5.6. Data sources

| Компонент | API endpoint | ClickHouse source |
|-----------|-------------|-------------------|
| Chart + Table | `GET /api/analytics/pnl/trend?connectionId=...&from=...&to=...&granularity=MONTHLY` | `mart_product_pnl` (monthly); `fact_finance` (daily on-the-fly) |

### 5.7. Screen states

| State | Условие | Отображение |
|-------|---------|-------------|
| **Loading** | Первая загрузка | Skeleton: серая прямоугольная область (chart) + skeleton table |
| **Data loaded** | Данные получены | Chart + summary table |
| **Empty** | Нет данных за период | Chart area: «Нет данных за выбранный период» |
| **Single point** | Только один период (напр. первый месяц) | Chart с одной точкой + подсказка: «Недостаточно данных для отображения тренда» |
| **ClickHouse down** | API 503 | Yellow banner + cached chart |

### 5.8. Wireframe (ASCII)

См. §5.4 Layout.

### 5.9. Interactive elements

| Элемент | Действие | Результат |
|---------|----------|-----------|
| Granularity switcher | Click [День]/[Неделя]/[Месяц] | Re-fetch с новой гранулярностью. Active button: `--accent-primary` bg |
| Chart data point | Hover | Tooltip: дата + точные значения всех серий |
| Chart data point | Click | Pinned tooltip. Если granularity=month → можно double-click → navigation to P&L by Product за этот месяц |
| Legend item | Click | Toggle visibility серии |
| Chart area | Drag (brush select) | Zoom in на выбранный диапазон дат. Button «Сбросить zoom» появляется |
| Summary table row | Click | Highlight corresponding point on chart |

### 5.10. Tables

**Summary table under chart:**

| # | Column key | Заголовок (RU) | Тип | Align |
|---|-----------|---------------|-----|-------|
| 1 | `period` | Период | Text | Left | Format: «Янв 2026» (monthly), «28 мар» (daily), «Нед. 12» (weekly) |
| 2 | `revenue_amount` | Выручка | Money | Right |
| 3 | `total_costs` | Расходы | Money | Right | Sum of all cost measures (displayed as abs value with −) |
| 4 | `net_cogs` | COGS | Money | Right |
| 5 | `advertising_cost` | Реклама | Money | Right |
| 6 | `full_pnl` | P&L | Money | Right | Color-coded |

**No pagination** — summary table shows all periods in range (max ~12 rows for monthly, ~31 for daily).

### 5.11. Detail panels

Нет.

### 5.12. Forms

Нет.

### 5.13. Modals

Нет.

### 5.14. Charts

**P&L Trend Line Chart:**

| Параметр | Значение |
|----------|----------|
| Тип | Line chart (ngx-echarts) |
| Серии | 3: Выручка (line, #059669, filled area), Расходы (line, #DC2626, dashed), P&L (line, #2563EB, bold) |
| Ось X | Период (format зависит от granularity) |
| Ось Y | Сумма в ₽ (auto-scale, space thousands) |
| Grid lines | Horizontal only, subtle (`--border-subtle`) |
| Tooltip | Shared tooltip (all series at hover point) |
| Legend | Горизонтальная, кликабельная (toggle series) |
| Zoom | Brush select horizontal → zoom in. Reset button |
| DataZoom | Slider at bottom for large datasets (>90 daily points) |
| Высота | 400px |
| Responsiveness | Chart fills available width, re-renders on container resize |

### 5.15. Real-time updates

- WebSocket: при обновлении materializer → invalidate trend query.
- Chart re-renders smoothly (animation transition on data update).

### 5.16. Keyboard shortcuts

| Shortcut | Действие |
|----------|----------|
| `D` | Granularity → День |
| `W` | Granularity → Неделя |
| `M` | Granularity → Месяц |
| `Ctrl+F` | Focus на filter bar |
| `R` | Обновить данные |

---

## 6. Inventory Overview

### 6.1. Назначение

Сводная панель состояния остатков. Позволяет моментально увидеть: «Есть ли товары, которые скоро закончатся? Сколько денег заморожено в переизбыточных остатках?»

### 6.2. Route

```
/workspace/:id/analytics/inventory/overview
```

### 6.3. Entry point

- Activity Bar → Аналитика → Tab «Остатки» (default sub-view)
- Sub-nav: **Обзор** (активный)
- Breadcrumb: `Аналитика > Остатки > Обзор`

### 6.4. Layout

```
┌─────────────────────────────────────────────────────────────────┐
│ Filter bar: [Подключение ▾]                                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐             │
│  │ Всего SKU    │ │ Stock-out    │ │ Замороженный │             │
│  │     234      │ │ Критичных:12 │ │   капитал    │             │
│  │              │ │   ⚠ CRITICAL │ │  450 200 ₽   │             │
│  └──────────────┘ └──────────────┘ └──────────────┘             │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Распределение по stock-out риску (bar chart)             │   │
│  │                                                           │   │
│  │  CRITICAL ████████████   12                               │   │
│  │  WARNING  ████████████████████   28                       │   │
│  │  NORMAL   ████████████████████████████████████████  194   │   │
│  │                                                           │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Топ-10 товаров с критическим риском (mini-table)         │   │
│  │                                                           │   │
│  │  SKU      │ Товар          │ Остаток│ Дней покрытия│Риск  │   │
│  │  АРТ-005  │ Кроссовки бел. │     3  │          1,2 │ 🔴   │   │
│  │  АРТ-012  │ Рюкзак чёрн.  │     0  │          0,0 │ 🔴   │   │
│  │  ...      │                │        │              │      │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### 6.5. Permissions

| Роль | Доступ |
|------|--------|
| Все роли | Read-only |

### 6.6. Data sources

| Компонент | API endpoint | ClickHouse source |
|-----------|-------------|-------------------|
| KPI + distribution + top-10 | `GET /api/analytics/inventory/overview?connectionId=...` | `mart_inventory_analysis` (latest analysis_date) |

### 6.7. Screen states

| State | Условие | Отображение |
|-------|---------|-------------|
| **Loading** | Первая загрузка | Skeleton KPI cards + skeleton chart + skeleton table |
| **Data loaded** | Данные получены | KPI + bar chart + top-10 table |
| **No inventory data** | Нет fact_inventory_snapshot | «Данные об остатках ещё не загружены.» + [Проверить синхронизацию] |
| **All normal** | Нет CRITICAL/WARNING | KPI «Stock-out критичных: 0» (green badge ✓). Bar chart показывает только NORMAL |
| **ClickHouse down** | API 503 | Yellow banner + cached data |

### 6.8. Wireframe (ASCII)

См. §6.4 Layout.

### 6.9. Interactive elements

| Элемент | Действие | Результат |
|---------|----------|-----------|
| KPI «Stock-out критичных» | Click | Навигация → Inventory by Product с filter `stock_out_risk=CRITICAL` |
| KPI «Замороженный капитал» | Click | Навигация → Inventory by Product с sort `frozen_capital,desc` |
| Bar chart segment | Click | Навигация → Inventory by Product с filter по выбранному risk level |
| Top-10 table row | Click | Навигация → Stock History для этого product |

### 6.10. Tables

**Top-10 mini-table:**

| # | Column | Заголовок (RU) | Тип | Align |
|---|--------|---------------|-----|-------|
| 1 | `sku_code` | Артикул | Text | Left |
| 2 | `product_name` | Товар | Text | Left |
| 3 | `available` | Остаток | Number | Right |
| 4 | `days_of_cover` | Дней покрытия | Number (1 decimal) | Right |
| 5 | `stock_out_risk` | Риск | Risk badge | Center |

**Risk badge colors:**
- CRITICAL: red dot + «Критичный»
- WARNING: yellow dot + «Внимание»
- NORMAL: green dot + «Норма»

### 6.11. Detail panels

Нет на этом экране.

### 6.12. Forms

Нет.

### 6.13. Modals

Нет.

### 6.14. Charts

**Stock-out Risk Distribution:**

| Параметр | Значение |
|----------|----------|
| Тип | Horizontal bar chart (ngx-echarts) |
| Bars | 3: CRITICAL (#DC2626), WARNING (#D97706), NORMAL (#059669) |
| Ось X | Количество SKU |
| Ось Y | Risk level (category) |
| Labels | Value label at end of each bar |
| Interactivity | Click → navigate to Inventory by Product filtered |
| Высота | 120px |

### 6.15. Real-time updates

- WebSocket: `/topic/analytics.inventory.{connectionId}` — при обновлении mart_inventory_analysis.
- KPI cards + chart refresh upon event.

### 6.16. Keyboard shortcuts

| Shortcut | Действие |
|----------|----------|
| `Ctrl+F` | Focus на filter bar |
| `R` | Обновить данные |

---

## 7. Inventory by Product

### 7.1. Назначение

Полная таблица остатков по товарам с аналитикой: дни покрытия, риск stock-out, замороженный капитал, рекомендация по пополнению. Рабочая таблица для менеджера по закупкам.

### 7.2. Route

```
/workspace/:id/analytics/inventory/by-product
```

### 7.3. Entry point

- Sub-nav: **По товарам**
- Click на KPI cards / bar chart segments из Inventory Overview
- Breadcrumb: `Аналитика > Остатки > По товарам`

### 7.4. Layout

```
┌──────────────────────────────────────────────────────────────────────────┐
│ Filter bar: [Подключение ▾] [Риск: Все ▾] [🔍 Поиск по SKU/названию]   │
├──────────────────────────────────────────────────────────────────────────┤
│ Toolbar: [Columns ⊞] [Export ↓]   Showing 1–50 of 234    [◀ 1 ▶]      │
├──────────────────────────────────────────────────────────────────────────┤
│ ☑│ SKU      │ Товар         │ Остаток│ Дней покр.│ Риск  │ Заморож.│Реком│
│──┼──────────┼───────────────┼────────┼───────────┼───────┼─────────┼────│
│ ☐│ АРТ-005  │ Кроссовки бел.│      3 │       1,2 │ 🔴    │       0 │  45│
│ ☐│ АРТ-012  │ Рюкзак чёрн.  │      0 │       0,0 │ 🔴    │       0 │  60│
│ ☐│ АРТ-001  │ Футболка белая│    120 │      28,5 │ 🟡    │  12 300₽│   0│
│ ☐│ АРТ-003  │ Носки (3 пары)│    450 │      95,0 │ 🟢    │  35 200₽│   0│
│  │    ⋮     │       ⋮       │    ⋮   │       ⋮   │  ⋮    │    ⋮    │  ⋮ │
└──────────────────────────────────────────────────────────────────────────┘
```

### 7.5. Permissions

| Роль | Доступ |
|------|--------|
| Все роли | Read-only |

### 7.6. Data sources

| Компонент | API endpoint | ClickHouse source |
|-----------|-------------|-------------------|
| Таблица | `GET /api/analytics/inventory/by-product?connectionId=...&stockOutRisk=...&search=...&page=0&size=50` | `mart_inventory_analysis` JOIN `dim_product` |

### 7.7. Screen states

| State | Условие | Отображение |
|-------|---------|-------------|
| **Loading** | Первая загрузка | AG Grid skeleton |
| **Data loaded** | Данные получены | Заполненная таблица |
| **Empty** | Нет данных | «Нет данных об остатках.» |
| **Filtered by risk** | Пришли с overview (risk filter active) | Filter pill: `Риск: Критичный ×` |
| **No cost profile** | `frozen_capital = NULL` для ряда товаров | Ячейка показывает `—` (em-dash), tooltip: «Себестоимость не задана — невозможно рассчитать» |
| **ClickHouse down** | API 503 | Yellow banner + cached data |

### 7.8. Wireframe (ASCII)

См. §7.4 Layout.

### 7.9. Interactive elements

| Элемент | Действие | Результат |
|---------|----------|-----------|
| Строка таблицы | Click | Detail Panel (right) с inventory detail per product |
| Строка таблицы | Double click | Навигация → Stock History для этого product |
| Column header | Click | Sort |
| Filter «Риск» | Change | Filter by stock_out_risk level |

### 7.10. Tables

**Columns:**

| # | Column key | Заголовок (RU) | Тип | Align | Default visible | Sort |
|---|-----------|---------------|-----|-------|----------------|------|
| 1 | `checkbox` | ☑ | Checkbox | Center | Да | Нет |
| 2 | `sku_code` | Артикул | Text | Left | Да | Да |
| 3 | `product_name` | Товар | Text | Left | Да | Да |
| 4 | `source_platform` | МП | Badge | Center | Да | Да |
| 5 | `available` | Остаток | Number | Right | Да | Да |
| 6 | `reserved` | Резерв | Number | Right | Нет | Да |
| 7 | `days_of_cover` | Дней покрытия | Number (1 dec) | Right | Да | Да |
| 8 | `stock_out_risk` | Риск | Risk badge | Center | Да | Да |
| 9 | `frozen_capital` | Заморож. капитал | Money | Right | Да | Да |
| 10 | `recommended_replenishment` | Пополнение | Number | Right | Да | Да |
| 11 | `avg_daily_sales_14d` | Продажи/день | Number (1 dec) | Right | Нет | Да |
| 12 | `cost_price` | Себестоимость | Money | Right | Нет | Да |

**Default sort:** `stock_out_risk` (CRITICAL first), then `days_of_cover` ASC.

### 7.11. Detail panels

**Inventory Product Detail Panel:**

```
┌─ Кроссовки белые (АРТ-005) ──── [×]     │
│                                          │
│  МП:            WB                       │
│  Остаток:       3                        │
│  Резерв:        0                        │
│  Дней покрытия: 1,2                      │
│  Риск:          🔴 Критичный             │
│                                          │
│  Средние продажи/день (14д): 2,5         │
│  Себестоимость:  1 200 ₽                 │
│  Заморож. капитал: 0 ₽                   │
│  Рекомен. пополнение: 45 шт.            │
│                                          │
│  [История остатков →]                    │
│                                          │
└──────────────────────────────────────────┘
```

### 7.12. Forms

Нет.

### 7.13. Modals

Нет.

### 7.14. Charts

Нет.

### 7.15. Real-time updates

- WebSocket: `/topic/analytics.inventory.{connectionId}` → background re-fetch.

### 7.16. Keyboard shortcuts

| Shortcut | Действие |
|----------|----------|
| `↑ / ↓` | Навигация по строкам |
| `Enter` | Detail Panel |
| `Shift+Enter` | Drill-down → Stock History |
| `Escape` | Закрыть Detail Panel |
| `Ctrl+F` | Focus на поле поиска |

---

## 8. Stock History

### 8.1. Назначение

График изменения остатков по конкретному товару за период. Позволяет увидеть: «Как менялись остатки? Есть ли тренд к обнулению? Когда последний раз пополняли?»

### 8.2. Route

```
/workspace/:id/analytics/inventory/stock-history
```

Query params: `?productId=...&from=...&to=...`

### 8.3. Entry point

- Double-click на строке в Inventory by Product
- Link «История остатков» из Detail Panel
- Breadcrumb: `Аналитика > Остатки > История остатков`

### 8.4. Layout

```
┌─────────────────────────────────────────────────────────────────┐
│ Filter bar: [Товар: АРТ-005 Кроссовки ▾] [Дата от ▾] [Дата до]│
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                                                         │    │
│  │  ──── Доступно   ──── Резерв                            │    │
│  │                                                         │    │
│  │  120─┐                                                  │    │
│  │      │                                                  │    │
│  │   80─┤        ╱╲                                        │    │
│  │      │       ╱  ╲                                       │    │
│  │   40─┤      ╱    ╲                                      │    │
│  │      │─────╱      ╲─────────                            │    │
│  │    0─┤              ╲─── ← stock-out zone               │    │
│  │      └──────────────────────────────────────            │    │
│  │      1 мар   8 мар  15 мар  22 мар  29 мар             │    │
│  │                                                         │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  Текущий остаток: 3 шт.   Риск: 🔴 Критичный                   │
│  Дней покрытия: 1,2   Рекомен. пополнение: 45 шт.              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 8.5. Permissions

| Роль | Доступ |
|------|--------|
| Все роли | Read-only |

### 8.6. Data sources

| Компонент | API endpoint | ClickHouse source |
|-----------|-------------|-------------------|
| Chart | `GET /api/analytics/inventory/stock-history?productId=...&from=...&to=...` | `fact_inventory_snapshot` |

### 8.7. Screen states

| State | Условие | Отображение |
|-------|---------|-------------|
| **Loading** | Загрузка | Skeleton chart area |
| **Data loaded** | Данные получены | Line chart + summary strip |
| **No product selected** | `productId` пуст | «Выберите товар для просмотра истории остатков.» Dropdown «Товар» подсвечен |
| **No history** | Нет snapshots для product | «Нет исторических данных по остаткам.» |
| **ClickHouse down** | API 503 | Yellow banner + cached chart |

### 8.8. Wireframe (ASCII)

См. §8.4 Layout.

### 8.9. Interactive elements

| Элемент | Действие | Результат |
|---------|----------|-----------|
| Product selector | Change | Re-fetch history для другого товара |
| Date range | Change | Re-fetch за новый период |
| Chart data point | Hover | Tooltip: дата + available + reserved |
| Chart area | Drag brush | Zoom into date range |

### 8.10. Tables

Нет таблиц.

### 8.11. Detail panels

Нет.

### 8.12. Forms

Нет.

### 8.13. Modals

Нет.

### 8.14. Charts

**Stock History Line Chart:**

| Параметр | Значение |
|----------|----------|
| Тип | Step line chart (ngx-echarts, `step: 'end'`) |
| Серии | 2: Доступно (#2563EB, area fill), Резерв (#D97706, dashed line) |
| Ось X | Даты (format: «28 мар») |
| Ось Y | Количество (integer, auto-scale) |
| Highlight zones | Красная горизонтальная зона (полупрозрачная) при `available < lead_time × avg_daily_sales` — зона stock-out риска |
| Tooltip | Shared: дата + available + reserved |
| Zoom | Brush select + DataZoom slider для длинных периодов |
| Высота | 360px |

### 8.15. Real-time updates

- При новом inventory sync → chart обновляется (новая точка данных).

### 8.16. Keyboard shortcuts

| Shortcut | Действие |
|----------|----------|
| `Ctrl+F` | Focus на filter bar |
| `R` | Обновить данные |
| `Escape` | Back → Inventory by Product |

---

## 9. Returns Summary

### 9.1. Назначение

Сводная панель по возвратам. Позволяет моментально увидеть: «Какой процент возвратов? Сколько денег потеряно? Какая самая частая причина?»

### 9.2. Route

```
/workspace/:id/analytics/returns/summary
```

### 9.3. Entry point

- Activity Bar → Аналитика → Tab «Возвраты» (default sub-view)
- Sub-nav: **Сводка** (активный)
- Breadcrumb: `Аналитика > Возвраты > Сводка`

### 9.4. Layout

```
┌─────────────────────────────────────────────────────────────────┐
│ Filter bar: [Подключение ▾] [Период ▾]                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐             │
│  │ Возврат %    │ │ Сумма        │ │ Главная      │             │
│  │    4,2%      │ │ возвратов    │ │ причина      │             │
│  │  ↑ 0,8%     │ │ −45 200 ₽    │ │ Брак         │             │
│  └──────────────┘ └──────────────┘ └──────────────┘             │
│                                                                 │
│  ┌──────────────────────────┐ ┌─────────────────────────────┐   │
│  │ Причины возвратов        │ │ Штрафы за период            │   │
│  │ (horizontal bar chart)   │ │                             │   │
│  │                          │ │ Итого: −12 500 ₽            │   │
│  │ Брак       ██████  45%   │ │                             │   │
│  │ Не подошло ████    30%   │ │ По типам:                   │   │
│  │ Повреждение ██     15%   │ │ Дефект:       −5 200 ₽      │   │
│  │ Прочее      █      10%   │ │ Задержка:     −3 800 ₽      │   │
│  │                          │ │ Отмена:       −2 100 ₽      │   │
│  │                          │ │ Прочие:       −1 400 ₽      │   │
│  └──────────────────────────┘ └─────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 9.5. Permissions

| Роль | Доступ |
|------|--------|
| Все роли | Read-only |

### 9.6. Data sources

| Компонент | API endpoint | ClickHouse source |
|-----------|-------------|-------------------|
| KPI + charts | `GET /api/analytics/returns/summary?connectionId=...&period=YYYYMM` | `mart_returns_analysis` (aggregated) |

### 9.7. Screen states

| State | Условие | Отображение |
|-------|---------|-------------|
| **Loading** | Первая загрузка | Skeleton KPI + skeleton charts |
| **Data loaded** | Данные получены | KPI cards + charts |
| **No returns** | Нет возвратов за период | KPI «Возврат %» = «0,0%» (green). Charts пустые с сообщением: «Возвратов за период не было» |
| **No data** | Нет fact_returns | «Данные о возвратах ещё не загружены» |
| **ClickHouse down** | API 503 | Yellow banner + cached data |

### 9.8. Wireframe (ASCII)

См. §9.4 Layout.

### 9.9. Interactive elements

| Элемент | Действие | Результат |
|---------|----------|-----------|
| KPI «Возврат %» | Click | Навигация → Returns Trend |
| KPI «Сумма возвратов» | Click | Навигация → Returns by Product, sort by `return_amount` desc |
| Bar chart segment | Click | Навигация → Returns by Product, filter by `return_reason` |

### 9.10. Tables

Нет полной таблицы на этом экране.

### 9.11. Detail panels

Нет.

### 9.12. Forms

Нет.

### 9.13. Modals

Нет.

### 9.14. Charts

**Return Reasons Bar Chart:**

| Параметр | Значение |
|----------|----------|
| Тип | Horizontal bar chart (ngx-echarts) |
| Bars | Dynamic (по причинам из данных). Цвет: градации `--status-error` (красные оттенки) |
| Ось X | Процент или количество |
| Ось Y | Причина возврата |
| Labels | Value + percent at end of each bar |
| Высота | 200px |

### 9.15. Real-time updates

- WebSocket: `/topic/analytics.returns.{connectionId}` → refresh.

### 9.16. Keyboard shortcuts

| Shortcut | Действие |
|----------|----------|
| `Ctrl+F` | Focus на filter bar |
| `R` | Обновить данные |

---

## 10. Returns by Product

### 10.1. Назначение

Полная таблица возвратов с разбивкой по товарам. Позволяет ответить: «Какие товары чаще всего возвращают? Какой финансовый ущерб от возвратов по каждому SKU?»

### 10.2. Route

```
/workspace/:id/analytics/returns/by-product
```

### 10.3. Entry point

- Sub-nav: **По товарам**
- Клики из Returns Summary (KPI cards, bar chart)
- Breadcrumb: `Аналитика > Возвраты > По товарам`

### 10.4. Layout

```
┌──────────────────────────────────────────────────────────────────────────┐
│ Filter bar: [Подключение ▾] [Период ▾] [🔍 Поиск по SKU/названию]      │
├──────────────────────────────────────────────────────────────────────────┤
│ Toolbar: [Columns ⊞] [Export ↓]   Showing 1–50 of 189    [◀ 1 ▶]      │
├──────────────────────────────────────────────────────────────────────────┤
│ ☑│ SKU      │ Товар        │Возвратов│Единиц│ %    │Сумма   │Штрафы│Прич│
│──┼──────────┼──────────────┼────────┼──────┼──────┼────────┼──────┼────│
│ ☐│ АРТ-005  │ Кроссовки б. │     12 │   14 │  8,2%│−5 200₽ │−800₽ │Брак│
│ ☐│ АРТ-008  │ Шапка шерст. │      8 │    8 │  6,1%│−2 400₽ │−200₽ │Не п│
│ ☐│ АРТ-001  │ Футболка бел │      5 │    6 │  2,3%│−1 800₽ │   0₽ │Разм│
│  │    ⋮     │      ⋮       │   ⋮    │   ⋮  │  ⋮   │   ⋮    │  ⋮   │  ⋮ │
└──────────────────────────────────────────────────────────────────────────┘
```

### 10.5. Permissions

| Роль | Доступ |
|------|--------|
| Все роли | Read-only |

### 10.6. Data sources

| Компонент | API endpoint | ClickHouse source |
|-----------|-------------|-------------------|
| Таблица | `GET /api/analytics/returns/by-product?connectionId=...&period=YYYYMM&search=...&page=0&size=50` | `mart_returns_analysis` JOIN `dim_product` |

### 10.7. Screen states

| State | Условие | Отображение |
|-------|---------|-------------|
| **Loading** | Первая загрузка | AG Grid skeleton |
| **Data loaded** | Данные получены | Заполненная таблица |
| **Empty** | Нет возвратов | «Нет возвратов за выбранный период.» |
| **ClickHouse down** | API 503 | Yellow banner + cached data |

### 10.8. Wireframe (ASCII)

См. §10.4 Layout.

### 10.9. Interactive elements

| Элемент | Действие | Результат |
|---------|----------|-----------|
| Строка таблицы | Click | Detail Panel (right) с детальной информацией по возвратам товара |
| Column header | Click | Sort |
| Column «%» | Hover | Tooltip: «Процент возвратов = возвращённые единицы / проданные единицы × 100» |
| Column «Сумма» | Hover | Tooltip: «Финансовый impact возвратов (реверс выручки)» |

### 10.10. Tables

**Columns:**

| # | Column key | Заголовок (RU) | Тип | Align | Default visible | Sort |
|---|-----------|---------------|-----|-------|----------------|------|
| 1 | `checkbox` | ☑ | Checkbox | Center | Да | Нет |
| 2 | `sku_code` | Артикул | Text | Left | Да | Да |
| 3 | `product_name` | Товар | Text | Left | Да | Да |
| 4 | `source_platform` | МП | Badge | Center | Да | Да |
| 5 | `return_count` | Возвратов | Number | Right | Да | Да |
| 6 | `return_quantity` | Единиц | Number | Right | Да | Да |
| 7 | `return_rate_pct` | Возврат % | Percent | Right | Да | Да |
| 8 | `financial_refund_amount` | Сумма возвратов | Money | Right | Да | Да |
| 9 | `penalties_amount` | Штрафы | Money | Right | Да | Да |
| 10 | `top_return_reason` | Причина | Text | Left | Да | Да |
| 11 | `sale_count` | Продаж | Number | Right | Нет | Да |
| 12 | `sale_quantity` | Продано ед. | Number | Right | Нет | Да |

**Formatting notes:**
- `financial_refund_amount`: stored as signed (<0). Display as absolute value with `−` prefix and red color. E.g., `−5 200 ₽`.
- `return_rate_pct`: color-coded. >10% = red, 5-10% = yellow, <5% = default.

**Default sort:** `return_rate_pct` DESC.

### 10.11. Detail panels

**Returns Product Detail Panel:**

```
┌─ Кроссовки белые (АРТ-005) ──── [×]     │
│                                          │
│  Период:       Март 2026                 │
│  МП:           WB                        │
│                                          │
│  ─── Статистика возвратов ───            │
│                                          │
│  Возвратов (шт):          14             │
│  Продано (шт):           171             │
│  Возврат %:              8,2%            │
│                                          │
│  ─── Финансовый impact ───               │
│                                          │
│  Реверс выручки:    −5 200 ₽             │
│  Штрафы:              −800 ₽             │
│  Итого ущерб:       −6 000 ₽             │
│                                          │
│  ─── Причины ───                         │
│                                          │
│  Брак: 8 (57%)                           │
│  Не подошёл размер: 4 (29%)              │
│  Повреждение при доставке: 2 (14%)       │
│                                          │
└──────────────────────────────────────────┘
```

### 10.12. Forms

Нет.

### 10.13. Modals

Нет.

### 10.14. Charts

Нет (chart-heavy view — Returns Trend).

### 10.15. Real-time updates

- WebSocket: `/topic/analytics.returns.{connectionId}` → background re-fetch.

### 10.16. Keyboard shortcuts

| Shortcut | Действие |
|----------|----------|
| `↑ / ↓` | Навигация по строкам |
| `Enter` | Detail Panel |
| `Escape` | Закрыть Detail Panel |
| `Ctrl+F` | Focus на поле поиска |
| `Ctrl+E` | Export |

---

## 11. Returns Trend

### 11.1. Назначение

График динамики возвратов во времени. Позволяет увидеть: «Растёт ли процент возвратов? Был ли всплеск возвратов после акции?»

### 11.2. Route

```
/workspace/:id/analytics/returns/trend
```

### 11.3. Entry point

- Sub-nav: **Тренд**
- Click на KPI «Возврат %» в Returns Summary
- Breadcrumb: `Аналитика > Возвраты > Тренд`

### 11.4. Layout

```
┌─────────────────────────────────────────────────────────────────┐
│ Filter bar: [Подключение ▾] [Дата от ▾] [Дата до ▾]            │
│             Гранулярность: [День] [Неделя] [Месяц]              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                                                         │    │
│  │  ──── Возврат %   ──── Возвратов (шт)                   │    │
│  │                                                         │    │
│  │  8%─┐                              ╱╲                   │    │
│  │     │                             ╱  ╲                  │    │
│  │  4%─┤    ╱╲        ╱╲            ╱    ╲                 │    │
│  │     │───╱──╲──────╱──╲──────────╱──────╲───             │    │
│  │  0%─┤                                                   │    │
│  │     └───────────────────────────────────────             │    │
│  │     Янв     Фев     Мар     Апр     Май                 │    │
│  │                                                         │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 11.5. Permissions

| Роль | Доступ |
|------|--------|
| Все роли | Read-only |

### 11.6. Data sources

| Компонент | API endpoint | ClickHouse source |
|-----------|-------------|-------------------|
| Chart | `GET /api/analytics/returns/trend?connectionId=...&from=...&to=...&granularity=MONTHLY` | `mart_returns_analysis` |

### 11.7. Screen states

| State | Условие | Отображение |
|-------|---------|-------------|
| **Loading** | Загрузка | Skeleton chart |
| **Data loaded** | Данные получены | Line chart |
| **Empty** | Нет возвратов | «Нет данных о возвратах за выбранный период» |
| **ClickHouse down** | API 503 | Yellow banner + cached chart |

### 11.8. Wireframe (ASCII)

См. §11.4 Layout.

### 11.9. Interactive elements

| Элемент | Действие | Результат |
|---------|----------|-----------|
| Granularity switcher | Click | Re-fetch с новой гранулярностью |
| Data point | Hover | Tooltip: period + return_rate_pct + return_quantity |
| Data point | Click | Pinned tooltip. Double-click → navigation to Returns by Product за этот период |
| Legend | Click | Toggle series |
| Chart brush | Drag | Zoom into date range |

### 11.10. Tables

Нет.

### 11.11. Detail panels

Нет.

### 11.12. Forms

Нет.

### 11.13. Modals

Нет.

### 11.14. Charts

**Returns Trend Chart:**

| Параметр | Значение |
|----------|----------|
| Тип | Dual-axis line chart (ngx-echarts) |
| Серия 1 (left axis) | Возврат % (line, #DC2626). Ось: «Возврат, %» |
| Серия 2 (right axis) | Возвратов шт (bar, #6B7280, opacity 0.3). Ось: «Количество» |
| Ось X | Период |
| Warning line | Horizontal dashed line at 5% (configurable threshold) с label «Порог» |
| Tooltip | Shared: период + rate + quantity |
| Zoom | Brush select |
| Высота | 400px |

### 11.15. Real-time updates

- WebSocket: при обновлении returns data → chart re-renders.

### 11.16. Keyboard shortcuts

| Shortcut | Действие |
|----------|----------|
| `D` | Granularity → День |
| `W` | Granularity → Неделя |
| `M` | Granularity → Месяц |
| `R` | Обновить данные |

---

## 12. Data Quality Status

### 12.1. Назначение

Операционная панель здоровья данных. Позволяет ответить: «Все ли данные свежие? Есть ли проблемы с синхронизацией? Не заблокирована ли автоматизация?»

Критически важный экран для доверия к аналитике. Если данные несвежие или синхронизация сломалась, пользователь должен узнать об этом здесь, а не гадать, почему P&L выглядит странно.

### 12.2. Route

```
/workspace/:id/analytics/data-quality/status
```

### 12.3. Entry point

- Activity Bar → Аналитика → Tab «Качество данных» (default sub-view)
- Sub-nav: **Статус** (активный)
- Persistent yellow banner на любом экране → click → Data Quality Status
- Breadcrumb: `Аналитика > Качество данных > Статус`

### 12.4. Layout

```
┌──────────────────────────────────────────────────────────────────────┐
│ Filter bar: [Подключение ▾]                                         │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌─ Automation Blocker Status ─────────────────────────────────┐    │
│  │                                                              │    │
│  │  WB (Основной):     ✅ Активна    Последний sync: 12 мин назад│   │
│  │  Ozon (Основной):   ⚠ БЛОКИРОВАНА — stale data (26ч)        │    │
│  │                                                              │    │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  ┌─ Синхронизация по доменам ──────────────────────────────────┐    │
│  │                                                              │    │
│  │  WB (Основной)                                               │    │
│  │  ┌──────────┬────────────┬────────────┬──────────┐           │    │
│  │  │ Домен    │ Посл. sync │ Статус     │ Записей  │           │    │
│  │  ├──────────┼────────────┼────────────┼──────────┤           │    │
│  │  │ Финансы  │ 12 мин наз │ ● Свежие   │  5 678   │           │    │
│  │  │ Заказы   │ 3 ч назад  │ ● Свежие   │ 12 345   │           │    │
│  │  │ Остатки  │ 1 ч назад  │ ● Свежие   │  2 340   │           │    │
│  │  │ Реклама  │ 2 дня назад│ ● Устарев. │    890   │           │    │
│  │  └──────────┴────────────┴────────────┴──────────┘           │    │
│  │                                                              │    │
│  │  Ozon (Основной)                                             │    │
│  │  ┌──────────┬────────────┬────────────┬──────────┐           │    │
│  │  │ Домен    │ Посл. sync │ Статус     │ Записей  │           │    │
│  │  ├──────────┼────────────┼────────────┼──────────┤           │    │
│  │  │ Финансы  │ 26 ч назад │ 🔴 Просроч.│  3 456   │           │    │
│  │  │ Заказы   │ 26 ч назад │ 🔴 Просроч.│  8 901   │           │    │
│  │  │ Остатки  │ 26 ч назад │ 🔴 Просроч.│  1 200   │           │    │
│  │  └──────────┴────────────┴────────────┴──────────┘           │    │
│  │                                                              │    │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### 12.5. Permissions

| Роль | Доступ |
|------|--------|
| Все роли | Read-only |

### 12.6. Data sources

| Компонент | API endpoint | Source |
|-----------|-------------|-------|
| All | `GET /api/analytics/data-quality/status?connectionId=...` | PostgreSQL (`marketplace_sync_state`, `alert_event`) |

NB: этот экран читает из PostgreSQL (не ClickHouse) — sync state и automation blocker status хранятся в PostgreSQL.

### 12.7. Screen states

| State | Условие | Отображение |
|-------|---------|-------------|
| **Loading** | Загрузка | Skeleton cards + skeleton tables |
| **All healthy** | Все домены fresh, нет blockers | Green banner: «Все данные актуальны ✓». Automation blockers: «Все подключения активны ✓» |
| **Stale data** | Один или более доменов stale | Affected domains highlighted with red status dot. Automation blocker section: orange warning |
| **No connections** | Нет подключений | «Подключите маркетплейс для мониторинга данных.» + [Настройки] |
| **API error** | HTTP 500 | Toast: «Не удалось загрузить статус данных.» + [Повторить] |

### 12.8. Wireframe (ASCII)

См. §12.4 Layout.

### 12.9. Interactive elements

| Элемент | Действие | Результат |
|---------|----------|-----------|
| Connection row (Automation Blocker) | Click | Expand details: причина блокировки, timestamp, recommended action |
| Domain row (Sync table) | Click | Expand: last 5 sync attempts (timestamp, status, error message if failed) |
| Status dot | Hover | Tooltip: «Последний sync: {timestamp}. Порог: {threshold}» |
| «БЛОКИРОВАНА» badge | Hover | Tooltip: «Ценообразование приостановлено для этого подключения из-за устаревших данных» |

### 12.10. Tables

**Sync Status per Connection (per domain):**

| # | Column | Заголовок (RU) | Тип | Описание |
|---|--------|---------------|-----|----------|
| 1 | `domain` | Домен | Text | Finance / Orders / Stock / Catalog / Advertising |
| 2 | `last_success_at` | Посл. синхронизация | Relative time | «12 мин назад», «26 ч назад» |
| 3 | `status` | Статус | Status dot | ● Свежие (green) / ● Устаревшие (yellow) / 🔴 Просрочены (red) |
| 4 | `record_count` | Записей | Number | Count of records in last sync batch |

**Freshness thresholds (from analytics-pnl.md §Data quality controls):**
- Finance: >24h = stale
- State (catalog, stocks): >48h = stale
- Advertising: >72h = stale (warning-only, не блокирует automation)

**Domain labels (RU):**

| Internal domain | UI label |
|----------------|----------|
| `finance` | Финансы |
| `orders` | Заказы |
| `stock` | Остатки |
| `catalog` | Каталог |
| `advertising` | Реклама |

### 12.11. Detail panels

Нет detail panel (информация показана inline с expand).

### 12.12. Forms

Нет.

### 12.13. Modals

Нет.

### 12.14. Charts

Нет (operational dashboard, не аналитический chart).

### 12.15. Real-time updates

- WebSocket: `/topic/sync.status.{connectionId}` — при каждом sync event (start, success, failure).
- Status dots обновляются в реальном времени. Relative time тикает (re-renders every 30s).
- При снятии automation blocker → banner обновляется без refresh.

### 12.16. Keyboard shortcuts

| Shortcut | Действие |
|----------|----------|
| `Ctrl+F` | Focus на filter bar |
| `R` | Обновить данные |

---

## 13. Reconciliation

### 13.1. Назначение

Панель reconciliation — диагностика точности маппинга финансовых данных. Показывает reconciliation residual per connection: расхождение между выплатой маркетплейса и суммой классифицированных компонентов.

Экран для ADMIN/OWNER — инструмент контроля качества данных, а не повседневный операционный экран. Позволяет ответить: «Насколько точно мы классифицируем финансовые операции маркетплейса? Есть ли аномалии?»

### 13.2. Route

```
/workspace/:id/analytics/data-quality/reconciliation
```

### 13.3. Entry point

- Sub-nav: **Reconciliation** (visible only for ADMIN/OWNER)
- Click на KPI «Reconciliation Residual» из P&L Summary (ADMIN/OWNER only)
- Breadcrumb: `Аналитика > Качество данных > Reconciliation`

### 13.4. Layout

```
┌──────────────────────────────────────────────────────────────────────────┐
│ Filter bar: [Подключение ▾] [Период ▾]                                  │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌─ Reconciliation Overview ───────────────────────────────────────┐    │
│  │                                                                  │    │
│  │  WB (Основной)                                                   │    │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐           │    │
│  │  │ Residual     │  │ Residual %   │  │ Baseline     │           │    │
│  │  │  +4 560 ₽    │  │    3,8%      │  │    4,0%      │           │    │
│  │  │  ● В норме   │  │  (ожид: ~4%) │  │ (30д rolling)│           │    │
│  │  └──────────────┘  └──────────────┘  └──────────────┘           │    │
│  │                                                                  │    │
│  │  Ozon (Основной)                                                 │    │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐           │    │
│  │  │ Residual     │  │ Residual %   │  │ Baseline     │           │    │
│  │  │     +12 ₽    │  │    0,01%     │  │    0,02%     │           │    │
│  │  │  ● В норме   │  │  (ожид: ~0%) │  │ (30д rolling)│           │    │
│  │  └──────────────┘  └──────────────┘  └──────────────┘           │    │
│  │                                                                  │    │
│  └──────────────────────────────────────────────────────────────────┘    │
│                                                                          │
│  ┌─ Residual Trend ────────────────────────────────────────────────┐    │
│  │                                                                  │    │
│  │  ──── WB residual %   ──── Ozon residual %   ---- Baseline      │    │
│  │                                                                  │    │
│  │  5%─┐    ╱╲                                                     │    │
│  │     │───╱──╲────────────── baseline WB                          │    │
│  │  0%─┤═══════════════════════════════ baseline Ozon              │    │
│  │     └───────────────────────────────────────                    │    │
│  │     Янв     Фев     Мар                                        │    │
│  │                                                                  │    │
│  └──────────────────────────────────────────────────────────────────┘    │
│                                                                          │
│  ┌─ Posting-level Residual Distribution ───────────────────────────┐    │
│  │                                                                  │    │
│  │  Histogram: distribution of |residual| per posting               │    │
│  │                                                                  │    │
│  └──────────────────────────────────────────────────────────────────┘    │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### 13.5. Permissions

| Роль | Доступ |
|------|--------|
| **ADMIN** | Полный |
| **OWNER** | Полный |
| ANALYST | **Нет доступа** (sub-nav item скрыт) |
| OPERATOR | **Нет доступа** |

При попытке прямого перехода по URL без соответствующей роли → redirect на Data Quality Status с toast: «Недостаточно прав для доступа к reconciliation.»

### 13.6. Data sources

| Компонент | API endpoint | Source |
|-----------|-------------|-------|
| Overview KPIs + trend | `GET /api/analytics/data-quality/reconciliation?connectionId=...&period=YYYYMM` | `mart_posting_pnl` (aggregated), `mart_product_pnl` |
| Posting distribution | Included in reconciliation response | `mart_posting_pnl` |

### 13.7. Screen states

| State | Условие | Отображение |
|-------|---------|-------------|
| **Loading** | Загрузка | Skeleton cards + skeleton charts |
| **Data loaded** | Данные получены | KPI per connection + charts |
| **Within baseline** | residual_ratio within 2σ of baseline | Green status: «● В норме» |
| **Anomaly detected** | residual_ratio deviates > 2σ | Red status: «● Аномалия обнаружена». Info banner: «Отклонение от baseline. Возможные причины: новые типы операций МП, ошибки маппинга.» |
| **Insufficient data** | < 100 operations (min sample) | Yellow status: «Недостаточно данных для оценки (< 100 операций)» |
| **Calibration period** | First 30 days | Info banner: «Идёт калибровка baseline (первые 30 дней). Alerts в режиме наблюдения.» |
| **ClickHouse down** | API 503 | Yellow banner + cached data |

### 13.8. Wireframe (ASCII)

См. §13.4 Layout.

### 13.9. Interactive elements

| Элемент | Действие | Результат |
|---------|----------|-----------|
| Connection KPI cards | Click | Scroll to trend chart for that connection |
| Trend chart point | Hover | Tooltip: period + exact residual % + baseline % |
| Trend chart point | Click | Navigation → P&L by Posting filtered by that period (to investigate postings with high residual) |
| Histogram bar | Click | Filters P&L by Posting to show postings within that residual range |
| «Аномалия» badge | Hover | Tooltip: «Текущий: {current}%. Baseline: {baseline}%. Отклонение: {deviation}σ. Порог: 2σ.» |

### 13.10. Tables

Нет отдельных таблиц (drill-down → P&L by Posting).

### 13.11. Detail panels

Нет.

### 13.12. Forms

Нет.

### 13.13. Modals

Нет.

### 13.14. Charts

**Residual Trend Chart:**

| Параметр | Значение |
|----------|----------|
| Тип | Line chart (ngx-echarts) |
| Серии | Per connection: residual_ratio % (solid line). Baseline per connection: horizontal dashed line |
| Ось X | Период (monthly) |
| Ось Y | Residual ratio, % |
| Color coding | WB line: #7C3AED (purple), Ozon line: #2563EB (blue). Baselines: same color, dashed |
| Anomaly markers | Red dots on data points exceeding 2σ threshold |
| Tooltip | Period + connection + current ratio + baseline + deviation |
| Высота | 280px |

**Posting Residual Distribution (Histogram):**

| Параметр | Значение |
|----------|----------|
| Тип | Bar chart / histogram (ngx-echarts) |
| Ось X | |residual| buckets: [0, 1₽), [1, 10₽), [10, 100₽), [100, 1000₽), [1000+₽) |
| Ось Y | Count of postings |
| Color | Neutral gray bars, red for buckets >100₽ |
| Per connection | Stacked or side-by-side bars (toggleable) |
| Interactivity | Click on bar → navigate to P&L by Posting with residual range filter |
| Высота | 200px |

### 13.15. Real-time updates

- При обновлении materialization → re-fetch reconciliation data.
- Anomaly detection runs on backend; frontend shows pre-computed status.

### 13.16. Keyboard shortcuts

| Shortcut | Действие |
|----------|----------|
| `Ctrl+F` | Focus на filter bar |
| `R` | Обновить данные |

---

## User Flow Scenarios

### Scenario 1: Ежедневная проверка прибыльности

**Пользователь:** оператор / аналитик.
**Цель:** за 2 минуты понять, как идут дела в этом месяце.

1. Открывает Datapulse → автоматически landing на P&L Summary.
2. Видит 6 KPI cards: выручка выросла ↑12%, P&L положительный.
3. Смотрит mini trend chart — стабильный рост последние 10 дней.
4. Замечает, что «COGS» = `0 ₽` с tooltip «Себестоимость не задана».
5. Понимает, что нужно задать себестоимость. Запоминает — идёт дальше (Phase B: COGS setup через отдельный экран).
6. Кликает на «P&L» card → переходит в P&L by Product.
7. Сортирует по `full_pnl` ASC — видит убыточные товары внизу.
8. Кликает на убыточный товар → Detail Panel показывает: высокая логистика (−890 ₽ при выручке 1 290 ₽).
9. Делает вывод: нужно оптимизировать логистику для этого SKU.

**Затронутые экраны:** P&L Summary → P&L by Product → Detail Panel.
**Время:** ~2 мин.

### Scenario 2: Расследование аномального residual

**Пользователь:** ADMIN / OWNER.
**Цель:** понять, почему reconciliation residual вырос.

1. В Status Bar замечает жёлтый индикатор.
2. Переходит в Data Quality Status → видит: «Ozon — ● Аномалия reconciliation».
3. Переходит в Reconciliation → видит: Ozon residual = 2,1% (baseline = 0,02%). Красный dot.
4. Наводит мышь на аномалию → tooltip: «Отклонение: 5,2σ. Порог: 2σ».
5. Кликает на точку графика → переходит в P&L by Posting с фильтром по периоду.
6. Сортирует по `reconciliation_residual` DESC.
7. Находит posting с residual = 1 200 ₽.
8. Кликает → Detail Panel → кликает «Показать записи».
9. В Posting Detail видит entry type `OTHER` → маркетплейс добавил новый тип операции.
10. Кликает provenance link (🔗) → видит raw JSON от Ozon.
11. Определяет новый тип → создаёт задачу на добавление маппинга в normalizer.

**Затронутые экраны:** Data Quality Status → Reconciliation → P&L by Posting → Posting Detail (provenance).
**Время:** ~5-7 мин.

### Scenario 3: Анализ возвратов для принятия решения о снятии товара

**Пользователь:** аналитик.
**Цель:** определить, какие товары нужно снять с продажи из-за высокого процента возвратов.

1. Открывает Returns Summary → видит: общий return rate = 4,2%, главная причина «Брак».
2. Кликает на «Сумма возвратов» → переходит в Returns by Product.
3. Сортирует по `return_rate_pct` DESC.
4. Видит: «Кроссовки белые» — 8,2% возвратов. Кликает → Detail Panel.
5. В Detail Panel видит: 57% возвратов по причине «Брак». Финансовый ущерб: −6 000 ₽.
6. Переходит в Returns Trend → проверяет, что тренд стабильно высокий (не разовый всплеск).
7. Делает вывод: нужно связаться с поставщиком или снять товар.
8. Экспортирует таблицу Returns by Product в CSV для отчёта.

**Затронутые экраны:** Returns Summary → Returns by Product → Detail Panel → Returns Trend.
**Время:** ~3-4 мин.

---

## Edge Cases

### ClickHouse unavailable (full outage)

- **Affected screens:** все 13 аналитических экранов (кроме Data Quality Status, который читает из PostgreSQL).
- **Поведение:**
  1. Persistent yellow banner на всех affected screens.
  2. TanStack Query cache используется для показа последних известных данных.
  3. Cache TTL = `staleTime` в TanStack Query config (default: 5 min for analytics). После expiration → пустое состояние с кнопкой retry.
  4. Data Quality Status продолжает работать (PostgreSQL) и показывает: sync status может быть «healthy», но ClickHouse unavailable → отдельный статус-индикатор.
- **Status Bar:** красный dot + «ClickHouse недоступен».

### No cost_profile (COGS unavailable)

- **Affected screens:** P&L Summary, P&L by Product, P&L by Posting.
- **Поведение:**
  1. `cogs_status = NO_COST_PROFILE` → COGS columns show `0 ₽`.
  2. `full_pnl` column shows `marketplace_pnl` (without COGS deduction). Visual: value shown, but with `*` asterisk.
  3. Persistent info banner (blue, not yellow): «Себестоимость не задана для {N} товаров. P&L показан без учёта COGS.» + [Задать себестоимость].
  4. KPI card «COGS» → `0 ₽` with tooltip.

### No advertising data (Phase B core)

- **Affected screens:** P&L Summary, P&L by Product.
- **Поведение:**
  1. `advertising_cost = 0` → shown as `0 ₽` with tooltip: «Рекламные данные не подключены».
  2. Это **не ошибка** — Phase B core поведение.
  3. При подключении advertising (Phase B extended) → values appear, tooltip снимается.
  4. Info banner (dismissible): «Рекламные расходы не подключены. P&L показан без учёта рекламы.»

### Empty workspace (no connections, no data)

- **Affected screens:** все.
- **Поведение:**
  1. Full-area empty state: «Подключите маркетплейс, чтобы увидеть аналитику.»
  2. Illustration-free (per design direction).
  3. CTA button: [Перейти в настройки →].
  4. Activity Bar visible but module icon shows gray dot (no data).

### Mixed marketplace data (one stale, one fresh)

- **Поведение:**
  1. Filter «Подключение» = ALL → data shown for all connections. Stale connection data marked with ⚠ icon in table rows.
  2. Filter set to specific connection → only that connection's data shown.
  3. Automation blocker is per-connection — stale Ozon doesn't block WB pricing.
  4. Status Bar shows per-connection freshness.

### Large datasets (>100k postings)

- **Поведение:**
  1. Server-side pagination prevents browser overload.
  2. Export: async (>10,000 rows). Toast: «Экспорт подготавливается...» → notification when ready.
  3. P&L Trend daily granularity for >90 days → DataZoom slider enabled.
  4. AG Grid virtual scrolling for visible rows only.

### Reconciliation — calibration period (first 30 days)

- **Поведение:**
  1. Reconciliation screen shows info banner: «Идёт калибровка baseline (первые 30 дней). Alerts в режиме наблюдения.»
  2. KPI cards show current residual values but baseline = «Калибровка...» instead of a percentage.
  3. No anomaly alerts generated during calibration.
  4. After 30 days → baseline computed from rolling history. Info banner disappears.

---

## Related documents

- [frontend-design-direction.md](frontend-design-direction.md) — design system, patterns, components
- [analytics-pnl.md](../modules/analytics-pnl.md) — P&L formula, star schema, REST API, data quality
- [etl-pipeline.md](../modules/etl-pipeline.md) — ETL canonical model, materialization
- [audit-alerting.md](../modules/audit-alerting.md) — automation blocker, alert events, notifications
