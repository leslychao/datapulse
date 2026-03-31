# Page Spec: Mismatch Monitor

**Route:** `/workspace/:id/mismatches`
**Module:** Seller Operations
**Phase:** D (Execution reconciliation), E (Seller Operations UI)
**Activity Bar icon:** `lucide:triangle-alert`

---

## Table of contents

- [Overview](#overview)
- [Screen 1 — Mismatch Dashboard](#screen-1--mismatch-dashboard)
- [Screen 2 — Mismatch List](#screen-2--mismatch-list)
- [Screen 3 — Mismatch Detail Panel](#screen-3--mismatch-detail-panel)
- [Screen 4 — Real-time Updates](#screen-4--real-time-updates)
- [Badge reference](#badge-reference)
- [User flows](#user-flows)
- [Edge cases](#edge-cases)
- [Related documents](#related-documents)

---

## Overview

Mismatch Monitor — экран для наблюдения и устранения расхождений между связанными data domains (цена, остатки, статус участия в промо, финансы). Это основной инструмент для операторов и менеджеров, чтобы быстро находить проблемы после синхронизации и reconciliation, понимать их причину и принимать решения.

Экран организован как **dashboard + table + detail panel** — стандартный паттерн Datapulse (см. [frontend-design-direction.md](frontend-design-direction.md) §Shell layout).

### Screen composition

```
┌──────────────────────────────────────────────────────────────────────┐
│  Top Bar: ... / Seller Operations / Mismatch Monitor                 │
├────┬─────────────────────────────────────────────────────────────────┤
│    │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐       │
│ A  │  │ Активные │  │Критичных │  │ Среднее  │  │ Авто-    │       │
│ c  │  │   12     │  │    3     │  │ время    │  │ решено   │       │
│ t  │  │ ↑4 за 7д │  │ ↓1 за 7д│  │  4.2ч    │  │ сегодня  │       │
│ i  │  └──────────┘  └──────────┘  └──────────┘  │    8     │       │
│ v  │                                             └──────────┘       │
│ i  │  ┌─────────────────────┐  ┌──────────────────────────┐        │
│ t  │  │ По типам (donut)    │  │ Динамика (stacked bar)   │        │
│ y  │  │                     │  │                          │        │
│    │  └─────────────────────┘  └──────────────────────────┘        │
│ B  │                                                                │
│ a  │  ┌─ Filter bar ──────────────────────────────────────┐        │
│ r  │  │ [Подключение ×] [Тип ×] [Статус ×] [Период ×]    │        │
│    │  └───────────────────────────────────────────────────┘        │
│    │  ┌─ Mismatch List ──────────────────────────────────┐        │
│    │  │ □  Товар           Тип    Ожид.  Факт.  Обнар.  │  Panel │
│    │  │ □  Футболка синяя  Цена   1200   1500   12м     │  (C)   │
│    │  │ □  Кроссовки NB    Остат. 50     32     2ч      │        │
│    │  │ ...                                              │        │
│    │  └──────────────────────────────────────────────────┘        │
├────┴─────────────────────────────────────────────────────────────────┤
│  Status Bar: ● WB synced 12 мин назад · ● 3 stale endpoints         │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Screen 1 — Mismatch Dashboard

### 1. Назначение

Верхняя часть страницы с агрегированными метриками и визуализацией распределения расхождений. Даёт оператору мгновенное понимание текущей ситуации: сколько проблем, какой тяжести, какая динамика.

### 2. Route

`/workspace/:id/mismatches` — dashboard является верхней частью единого экрана, не отдельным route.

### 3. Layout / Wireframe

```
┌─ KPI Cards ──────────────────────────────────────────────────────────┐
│                                                                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  │ Активные     │  │ Критичных    │  │ Среднее время│  │ Авто-решено  │
│  │ расхождения  │  │              │  │ без решения  │  │ сегодня      │
│  │              │  │              │  │              │  │              │
│  │    12        │  │     3        │  │   4,2 ч      │  │     8        │
│  │  ↑4 за 7д   │  │  ↓1 за 7д   │  │  ↑0,8ч       │  │  ↑3 vs вчера │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘
│                                                                       │
│  ┌─ Distribution (donut) ─────┐  ┌─ Timeline (stacked bar) ─────────┐
│  │                             │  │                                   │
│  │       ╭───╮                 │  │  ██ Новые   ░░ Решённые          │
│  │      ╱ 42% ╲   Цена: 5     │  │                                   │
│  │     │ PRICE │   Остаток: 4  │  │  ▐█░▐█░▐█░▐██▐██▐█░▐██▐█░...    │
│  │      ╲ 33% ╱   Статус: 2   │  │  1   5   10  15  20  25  30      │
│  │       ╰───╯    Финансы: 1   │  │                (дней назад)       │
│  │                             │  │                                   │
│  └─────────────────────────────┘  └───────────────────────────────────┘
└───────────────────────────────────────────────────────────────────────┘
```

KPI cards — горизонтальная полоса из 4 карточек (max 4, без прокрутки). Ниже — два чарта side-by-side с соотношением ~40/60.

### 4. Data model / API

**Endpoint:** `GET /api/workspace/{workspaceId}/mismatches/summary`

**Response:**

```json
{
  "totalActive": 12,
  "totalActiveDelta7d": 4,
  "criticalCount": 3,
  "criticalDelta7d": -1,
  "avgHoursUnresolved": 4.2,
  "avgHoursUnresolvedDelta7d": 0.8,
  "autoResolvedToday": 8,
  "autoResolvedYesterday": 5,
  "distributionByType": [
    { "type": "PRICE", "count": 5 },
    { "type": "STOCK", "count": 4 },
    { "type": "PROMO", "count": 2 },
    { "type": "FINANCE", "count": 1 }
  ],
  "timeline": [
    { "date": "2026-03-01", "newCount": 3, "resolvedCount": 2 },
    { "date": "2026-03-02", "newCount": 1, "resolvedCount": 4 }
  ]
}
```

`timeline` — массив за последние 30 дней. Каждый день — количество обнаруженных и решённых (включая auto-resolved).

### 5. Content blocks

| Block | Тип | Данные | Формат |
|-------|-----|--------|--------|
| Активные расхождения | KPI card | `totalActive` | Число, `--text-2xl`, JetBrains Mono. Delta: `↑N за 7д` / `↓N за 7д`, цвет по знаку (↑ = `--status-error`, ↓ = `--status-success`) |
| Критичных | KPI card | `criticalCount` | Число, `--text-2xl`. Красный акцент (`--status-error`) если > 0. Delta аналогично |
| Среднее время без решения | KPI card | `avgHoursUnresolved` | Часы (одна десятичная), `--text-2xl`. Юнит «ч» рядом. Delta: ↑ = хуже (красный), ↓ = лучше (зелёный) |
| Авто-решено сегодня | KPI card | `autoResolvedToday` | Число, `--text-2xl`. Delta: `↑N vs вчера` |
| Распределение по типам | Donut chart | `distributionByType` | Apache ECharts donut. Цвета по типу (см. §Badge reference). Легенда справа от чарта, вертикальный список |
| Динамика за 30 дней | Stacked bar chart | `timeline` | Apache ECharts stacked bar. Новые — `--status-error` (приглушённый). Решённые — `--status-success` (приглушённый). Ось X: даты (dd.MM). Ось Y: количество. Tooltip при hover |

### 6. Filters

Dashboard KPI и чарты **не** фильтруются отдельно. Они реагируют на фильтры, применённые к Mismatch List (единый filter state).

Исключение: при первой загрузке без фильтров — показывают данные по всему workspace.

### 7. Sorting

Не применимо (агрегированные данные).

### 8. Pagination

Не применимо.

### 9. Actions / Buttons

Нет action buttons на dashboard. Клик по сегменту donut chart → устанавливает фильтр по типу в Mismatch List и скроллит к таблице. Клик по столбцу timeline → устанавливает фильтр по дате.

### 10. Status / Badge mapping

KPI card «Критичных» получает визуальный акцент:
- `criticalCount > 0` → левая граница карточки `--status-error` (2px), число красное
- `criticalCount == 0` → стандартный стиль, число `--status-success`

### 11. Loading states

Skeleton: 4 серых прямоугольника в линию (KPI cards) + 2 серых прямоугольника (charts). Shimmer-эффект.

Частичная загрузка: KPI cards загружаются первыми (один запрос), чарты рендерятся после парсинга `distributionByType` и `timeline` из того же ответа.

### 12. Empty states

| Условие | Отображение |
|---------|-------------|
| `totalActive == 0`, есть данные | KPI cards показывают «0». Donut заменяется на пустой круг с текстом «Нет активных расхождений». Timeline показывает только resolved bars |
| Нет данных (workspace без подключений) | Весь dashboard заменяется на: «Нет данных о расхождениях. Подключите маркетплейс в настройках.» + ссылка [Настройки подключений] |
| Первый день (timeline пустой) | Timeline показывает один столбец за текущий день |

### 13. Error states

| Ошибка | Отображение |
|--------|-------------|
| API 5xx | Toast (error): «Не удалось загрузить сводку расхождений. Повторите позже.» Dashboard показывает последние кешированные данные (TanStack Query staleTime) или skeleton |
| API timeout | Toast (error): «Сервер не отвечает.» + [Повторить]. Skeleton остаётся |
| Частичная ошибка (charts ok, KPI fail) | Невозможно — один endpoint. Весь блок или загружен, или нет |

### 14. Permissions / Roles

| Роль | Доступ |
|------|--------|
| VIEWER+ | Полный read-only доступ к dashboard |

Dashboard не содержит write-операций. Любая роль от VIEWER и выше видит все метрики.

### 15. Keyboard shortcuts

| Shortcut | Действие |
|----------|----------|
| `Ctrl+K` | Global command palette (стандартный) |
| `Tab` → `Enter` | Фокус на KPI card → переход к фильтру таблицы по этой метрике |

### 16. Responsive behavior

| Viewport | Поведение |
|----------|-----------|
| ≥ 1440px | 4 KPI cards в строку + 2 чарта side-by-side |
| 1280–1440px | 4 KPI cards в строку (сужаются) + 2 чарта side-by-side (сужаются) |
| < 1280px | Не поддерживается (стандарт Datapulse) |

---

## Screen 2 — Mismatch List

### 1. Назначение

Основная рабочая таблица со списком расхождений. Оператор фильтрует, сортирует, кликает по строке для детального разбора. Таблица расположена под dashboard на том же route.

### 2. Route

`/workspace/:id/mismatches` — нижняя часть единого экрана. Scroll позиция сохраняется при открытии/закрытии detail panel.

### 3. Layout / Wireframe

```
┌─ Filter Bar ────────────────────────────────────────────────────────┐
│ [Подключение: Мой WB ×]  [Тип: Цена ×]  [Статус ×]  [Период ×]    │
│ [+ Добавить фильтр]                                   [⊘ Сбросить] │
└─────────────────────────────────────────────────────────────────────┘

┌─ Grid Toolbar ──────────────────────────────────────────────────────┐
│ Показано 1–50 из 142 │ [Столбцы ▾]  [Экспорт]  50▾  ◀  1/3  ▶    │
└─────────────────────────────────────────────────────────────────────┘

┌─ Table Header ──────────────────────────────────────────────────────┐
│ □  Товар              Тип ↕  Ожидаемое   Фактическое  Обнаружено↓  │
│    (name + sku)              значение    значение     Статус        │
│                                                       Решение       │
├─────────────────────────────────────────────────────────────────────┤
│ □  Футболка синяя      ● Цена   1 200 ₽    1 500 ₽    12 мин назад │
│    ABC-001                                             ● Активно    │
│                                                                     │
│ □  Кроссовки NB        ● Остаток  50        32         2 ч назад   │
│    NB-RUN-42                                           ● Активно    │
│                                                                     │
│ □  Платье летнее       ● Статус  PARTICIP.  DECLINED   вчера       │
│    DRS-001                                             ● Решено     │
│                                                        Авто-решено  │
│ ...                                                                 │
├─────────────────────────────────────────────────────────────────────┤
│ □  Bulk: 3 выбрано  [Игнорировать выбранные]  [Экспорт выбранные]  │
└─────────────────────────────────────────────────────────────────────┘
```

### 4. Data model / API

**Endpoint:** `GET /api/workspace/{workspaceId}/mismatches`

**Query parameters:**

| Parameter | Type | Default | Описание |
|-----------|------|---------|----------|
| `page` | int | 0 | Номер страницы (0-based) |
| `size` | int | 50 | Размер страницы (50 / 100 / 200) |
| `sort` | string | `detected_at` | Колонка сортировки |
| `direction` | string | `DESC` | ASC / DESC |
| `type` | string[] | — | Фильтр: PRICE, STOCK, PROMO, FINANCE |
| `connectionId` | long[] | — | Фильтр по подключению |
| `status` | string[] | — | Фильтр: ACTIVE, ACKNOWLEDGED, RESOLVED, AUTO_RESOLVED, IGNORED |
| `severity` | string[] | — | Фильтр: WARNING, CRITICAL |
| `from` | ISO date | — | Начало периода (по `detected_at`) |
| `to` | ISO date | — | Конец периода |
| `offerId` | long | — | Фильтр по конкретному offer |
| `query` | string | — | Поиск по имени товара / SKU (ILIKE) |

**Response** (формат из seller-operations.md §Mismatch Monitor REST API):

```json
{
  "content": [
    {
      "mismatchId": 77,
      "type": "PRICE",
      "severity": "CRITICAL",
      "offerId": 12345,
      "offerName": "Футболка синяя",
      "skuCode": "ABC-001",
      "marketplaceType": "WB",
      "connectionName": "Мой кабинет WB",
      "expectedValue": "1200.00",
      "actualValue": "1500.00",
      "deltaPct": 25.0,
      "status": "ACTIVE",
      "resolution": null,
      "resolvedAt": null,
      "detectedAt": "2026-03-30T15:00:00Z",
      "relatedActionId": 456
    }
  ],
  "page": 0,
  "size": 50,
  "totalElements": 142,
  "totalPages": 3
}
```

### 5. Columns

| # | Column | Field | Width | Align | Font | Описание |
|---|--------|-------|-------|-------|------|----------|
| 1 | ☐ | — | 32px | center | — | Checkbox для bulk actions |
| 2 | Товар | `offerName` + `skuCode` | 240px min | left | Inter (`offerName`, `--text-sm`), JetBrains Mono (`skuCode`, `--text-xs`, `--text-secondary`) | Двухстрочная ячейка: имя товара + артикул под ним |
| 3 | Маркетплейс | `marketplaceType` | 80px | center | — | Иконка маркетплейса (WB / Ozon logo micro) |
| 4 | Тип | `type` | 100px | left | — | Badge с цветом по типу (см. §Badge reference) |
| 5 | Ожидаемое | `expectedValue` | 120px | right | JetBrains Mono | Для PRICE — сумма с ₽. Для STOCK — число. Для PROMO/STATUS — текст |
| 6 | Фактическое | `actualValue` | 120px | right | JetBrains Mono | Аналогично. **Выделение diff**: если ≠ expected → `--status-error` фон ячейки (light tint) |
| 7 | Δ% | `deltaPct` | 70px | right | JetBrains Mono | Процент расхождения с sign. `+25,0%` красным, `−3,0%` красным. `null` для non-numeric → `—` |
| 8 | Обнаружено | `detectedAt` | 120px | left | Inter, `--text-xs` | Relative time: «12 мин назад», «вчера». Tooltip: абсолютное время |
| 9 | Статус | `status` | 130px | left | — | Status badge (см. §Badge reference) |
| 10 | Решение | `resolution` | 140px | left | Inter, `--text-xs` | Resolution type: «Авто-решено», «Повторная установка», «Исследовано», «Внешняя причина», «Принято». `null` → `—` |
| 11 | Подключение | `connectionName` | 160px | left | Inter, `--text-sm` | Название подключения. По умолчанию скрыт, включается через «Столбцы» |
| 12 | Серьёзность | `severity` | 90px | center | — | WARNING: `⚠` жёлтый. CRITICAL: `⚠` красный. По умолчанию скрыт |

**Frozen columns:** checkbox + Товар (всегда видны при горизонтальном скролле).

**Default visible:** 1–10. Columns 11–12 скрыты, доступны через «Столбцы».

### 6. Filters

| Filter | UI element | Поведение |
|--------|------------|-----------|
| Подключение | Dropdown multi-select | Список подключений workspace. При выборе → `connectionId` param |
| Тип расхождения | Dropdown multi-select | PRICE / STOCK / PROMO / FINANCE. Pill с цветным badge |
| Статус | Dropdown multi-select | Активно / Подтверждено / Решено / Авто-решено / Проигнорировано. Default: «Активно» pre-selected |
| Серьёзность | Dropdown multi-select | WARNING / CRITICAL |
| Период | Date range picker | From–To по `detectedAt`. Пресеты: Сегодня, 7 дней, 30 дней, Произвольный |
| Поиск | Text input (debounce 300ms) | По имени товара или SKU code. `query` param |

**Filter pill format:** `[Тип: Цена, Остаток ×]` — compact pills в filter bar (стандарт из frontend-design-direction.md §Filter bar).

**Default filter state:** при первом заходе — `status=ACTIVE` pre-applied. Сбрасывается через «Сбросить все».

**Filter persistence:** фильтры сохраняются в URL query params + localStorage per user. При возврате на страницу — восстанавливаются.

### 7. Sorting

| Column | Sortable | Default | Server/Client |
|--------|----------|---------|---------------|
| `offerName` | ✓ | — | Server |
| `type` | ✓ | — | Server |
| `expectedValue` | ✓ | — | Server |
| `actualValue` | ✓ | — | Server |
| `deltaPct` | ✓ | — | Server |
| `detectedAt` | ✓ | DESC (default) | Server |
| `status` | ✓ | — | Server |
| `severity` | ✓ | — | Server |

**Sort whitelist** (backend):

```java
Map<String, String> MISMATCH_SORT_WHITELIST = Map.of(
    "offerName",     "mo.name",
    "type",          "ae.mismatch_type",
    "expectedValue", "ae.expected_value",
    "actualValue",   "ae.actual_value",
    "deltaPct",      "ae.delta_pct",
    "detectedAt",    "ae.detected_at",
    "status",        "ae.status",
    "severity",      "ae.severity"
);
```

**Multi-column sort:** не поддерживается. Один активный sort column.

### 8. Pagination

Server-side. Standard Datapulse pattern:

- Page size selector: `50` / `100` / `200`
- Counter: «Показано 1–50 из 142»
- Navigation: `◀` prev / `▶` next + input field для номера страницы
- При смене фильтров → reset to page 0

### 9. Actions / Buttons

**Grid toolbar:**

| Button | Style | Описание | Roles |
|--------|-------|----------|-------|
| Столбцы | Ghost icon | Dropdown panel с checkbox list колонок | VIEWER+ |
| Экспорт | Ghost icon + text | CSV export всех отфильтрованных строк (server-side streaming) | VIEWER+ |

**Row-level actions (click row → opens detail panel):**

Single row click → открывает Detail Panel справа (Screen 3).

**Bulk actions (bottom bar при выборе ≥ 1 строки):**

```
┌────────────────────────────────────────────────────────────────────┐
│ 3 расхождения выбрано  [Игнорировать выбранные]  [Экспорт]     × │
└────────────────────────────────────────────────────────────────────┘
```

| Bulk action | Описание | Roles | Confirmation |
|-------------|----------|-------|------------|
| Игнорировать выбранные | Bulk resolve с `resolution = IGNORED` | PRICING_MANAGER+ | Modal: «Отметить N расхождений как проигнорированные?» + textarea `reason` (обязательно) + [Подтвердить] / [Отмена] |
| Экспорт | CSV export выбранных строк | VIEWER+ | Нет |

**Context menu (right-click):**

| Item | Описание | Roles |
|------|----------|-------|
| Открыть в деталях | Открывает Detail Panel | VIEWER+ |
| Перейти к товару | Переход: `/workspace/:id/grid?offerId=N` (Operational Grid с фокусом) | VIEWER+ |
| Копировать SKU | Копирует `skuCode` в буфер | VIEWER+ |
| Игнорировать | Resolve как IGNORED (с модалом причины) | PRICING_MANAGER+ |

### 10. Status / Badge mapping

**Mismatch status (column «Статус»):**

| Status | Badge | Dot color | Label | CSS token |
|--------|-------|-----------|-------|-----------|
| `ACTIVE` | Red dot pill | `--status-error` (#DC2626) | Активно | `bg-red-50 text-red-700 border-red-200` |
| `ACKNOWLEDGED` | Yellow dot pill | `--status-warning` (#D97706) | Подтверждено | `bg-amber-50 text-amber-700 border-amber-200` |
| `RESOLVED` | Green dot pill | `--status-success` (#059669) | Решено | `bg-green-50 text-green-700 border-green-200` |
| `AUTO_RESOLVED` | Green dot pill | `--status-success` (#059669) | Авто-решено | `bg-green-50 text-green-700 border-green-200` |
| `IGNORED` | Gray dot pill | `--status-neutral` (#6B7280) | Проигнорировано | `bg-gray-50 text-gray-500 border-gray-200` |

**Mismatch type (column «Тип»):**

| Type | Badge | Color | Label |
|------|-------|-------|-------|
| `PRICE` | Blue filled pill | `--status-info` (#2563EB) | Цена |
| `STOCK` | Amber filled pill | `--status-warning` (#D97706) | Остаток |
| `PROMO` | Purple filled pill | `#7C3AED` | Промо |
| `FINANCE` | Indigo filled pill | `#4338CA` | Финансы |

**Severity indicator:**

| Severity | Визуал | Описание |
|----------|--------|----------|
| `WARNING` | Жёлтый `⚠` icon перед type badge | Стандартная серьёзность |
| `CRITICAL` | Красный `⚠` icon перед type badge + red left border (2px) на всей строке | Повышенная серьёзность. Строка выделяется визуально |

### 11. Loading states

| State | Визуал |
|-------|--------|
| Первичная загрузка | AG Grid skeleton: серые shimmer-строки (8 строк), toolbar активен |
| Рефреш данных (смена фильтра, пагинация) | Top-edge progress bar (2px, `--accent-primary`), текущие строки остаются видимыми |
| Экспорт в процессе | Toast (info): «Подготовка экспорта...» (non-blocking) |

### 12. Empty states

| Условие | Message | Action |
|---------|---------|--------|
| Есть фильтры, 0 результатов | «Нет расхождений, соответствующих фильтрам.» | [Сбросить фильтры] |
| Нет фильтров, 0 результатов | «Расхождений не обнаружено. Система работает штатно.» | — (позитивный empty state, зелёная галочка) |
| Workspace без подключений | «Нет данных о расхождениях. Подключите маркетплейс.» | [Перейти к настройкам] |

### 13. Error states

| Ошибка | Отображение |
|--------|-------------|
| API 5xx | Toast (error): «Не удалось загрузить список расхождений.» + [Повторить]. Grid показывает кешированные данные или skeleton |
| API 403 | Toast (error): «Недостаточно прав для просмотра расхождений.» |
| API 400 (bad filter) | Toast (warning): «Некорректные параметры фильтра. Проверьте фильтры.» Фильтр, вызвавший ошибку, подсвечивается красной рамкой |
| Export timeout | Toast (error): «Экспорт не удался. Попробуйте сузить фильтры.» |

### 14. Permissions / Roles

| Действие | Минимальная роль |
|----------|------------------|
| Просмотр списка | VIEWER |
| Фильтрация, сортировка | VIEWER |
| Экспорт CSV | VIEWER |
| Клик по строке → Detail Panel | VIEWER |
| Bulk «Игнорировать» | PRICING_MANAGER |

### 15. Keyboard shortcuts

| Shortcut | Действие |
|----------|----------|
| `↑` / `↓` | Навигация по строкам таблицы |
| `Enter` | Открыть Detail Panel для выбранной строки |
| `Escape` | Закрыть Detail Panel |
| `Space` | Toggle checkbox на выбранной строке |
| `Ctrl+F` | Focus на search input в filter bar |
| `Ctrl+A` | Select all (на текущей странице) |
| `Ctrl+Shift+E` | Экспорт текущего view |

### 16. Responsive behavior

| Viewport | Поведение |
|----------|-----------|
| ≥ 1440px | Таблица на полную ширину, Detail Panel push-layout (400px) |
| 1280–1440px | Таблица сужается, Detail Panel overlay |
| < 1280px | Не поддерживается |

AG Grid column auto-sizing: при открытии Detail Panel колонки сжимаются пропорционально. Frozen columns не сжимаются.

---

## Screen 3 — Mismatch Detail Panel

### 1. Назначение

Правая боковая панель с полной информацией о конкретном расхождении: что случилось, хронология событий, сравнение ожидаемого и фактического значений, связанные действия, кнопки для решения проблемы.

### 2. Route

Не отдельный route. Открывается по клику на строку в Mismatch List. URL обновляется: `/workspace/:id/mismatches?selected=77` (query param `selected` = `mismatchId`). При обновлении страницы — panel открывается автоматически.

### 3. Layout / Wireframe

```
┌─ Detail Panel ─────────────────────────────────┐
│  ✕  Расхождение #77                            │
│                                                 │
│  ┌─ Header ──────────────────────────────────┐ │
│  │ Футболка синяя                            │ │
│  │ ABC-001 · WB · Мой кабинет WB            │ │
│  │ ● Цена   ⚠ CRITICAL   ● Активно          │ │
│  │ Обнаружено: 30 мар 2026, 15:00            │ │
│  └────────────────────────────────────────────┘ │
│                                                 │
│  ┌─ Tabs ────────────────────────────────────┐ │
│  │ [Сравнение] [Хронология] [Связь]          │ │
│  └────────────────────────────────────────────┘ │
│                                                 │
│  ══ Tab: Сравнение ═══════════════════════════  │
│                                                 │
│  ┌─ Expected vs Actual ──────────────────────┐ │
│  │           Ожидаемое      Фактическое      │ │
│  │ Цена      1 200 ₽      ▸ 1 500 ₽  (+25%) │ │
│  │ Источник  price_action   marketplace_read  │ │
│  │ Дата      30 мар, 10:01  30 мар, 15:00    │ │
│  └────────────────────────────────────────────┘ │
│                                                 │
│  Пороговые значения:                            │
│  CRITICAL > 5% │ Текущее: 25,0%                │
│                                                 │
│  ══ Actions ══════════════════════════════════  │
│                                                 │
│  [Повторить ↻]  [Игнорировать]  [Эскалировать] │
│  [Подтвердить ✓]                                │
│                                                 │
└─────────────────────────────────────────────────┘
```

### 4. Data model / API

**Endpoint:** `GET /api/workspace/{workspaceId}/mismatches/{mismatchId}`

**Response:**

```json
{
  "mismatchId": 77,
  "type": "PRICE",
  "severity": "CRITICAL",
  "status": "ACTIVE",
  "offer": {
    "offerId": 12345,
    "offerName": "Футболка синяя",
    "skuCode": "ABC-001",
    "marketplaceType": "WB",
    "connectionName": "Мой кабинет WB"
  },
  "expectedValue": "1200.00",
  "actualValue": "1500.00",
  "deltaPct": 25.0,
  "expectedSource": "price_action #456 (SUCCEEDED)",
  "actualSource": "marketplace_read (sync 30 мар, 15:00)",
  "detectedAt": "2026-03-30T15:00:00Z",
  "acknowledgedAt": null,
  "acknowledgedBy": null,
  "resolvedAt": null,
  "resolvedBy": null,
  "resolution": null,
  "resolutionNote": null,
  "relatedActionId": 456,
  "relatedAction": {
    "actionId": 456,
    "status": "SUCCEEDED",
    "targetPrice": 1200.00,
    "executedAt": "2026-03-30T10:01:00Z",
    "reconciliationSource": "IMMEDIATE"
  },
  "thresholds": {
    "warningPct": 1.0,
    "criticalPct": 5.0
  },
  "timeline": [
    {
      "eventType": "DETECTED",
      "timestamp": "2026-03-30T15:00:00Z",
      "description": "Расхождение обнаружено после sync",
      "actor": "system"
    },
    {
      "eventType": "RELATED_ACTION",
      "timestamp": "2026-03-30T10:01:00Z",
      "description": "price_action #456: установка цены 1 200 ₽ → SUCCEEDED",
      "actor": "system"
    }
  ]
}
```

**Action endpoints:**

| Action | Method | Path | Body |
|--------|--------|------|------|
| Подтвердить (acknowledge) | POST | `/api/workspace/{id}/mismatches/{mismatchId}/acknowledge` | — |
| Решить (resolve) | POST | `/api/workspace/{id}/mismatches/{mismatchId}/resolve` | `{ "resolution": "REPRICED" \| "ACCEPTED" \| "INVESTIGATED" \| "EXTERNAL" \| "IGNORED", "note": "..." }` |
| Повторить (retry related action) | POST | `/api/workspace/{id}/actions/{actionId}/retry` | `{ "retryReason": "mismatch investigation" }` |
| Эскалировать (create alert) | POST | `/api/workspace/{id}/alerts` | `{ "sourceType": "MISMATCH", "sourceId": 77, "severity": "CRITICAL", "message": "..." }` |

### 5. Content blocks

**Header:**

| Field | Формат |
|-------|--------|
| Название товара | `--text-lg`, `--text-primary`, font-weight 600 |
| SKU · Маркетплейс · Подключение | `--text-sm`, `--text-secondary`. SKU в JetBrains Mono. Маркетплейс — micro icon + text |
| Type badge + Severity badge + Status badge | Inline, horizontal. Badges из §Badge reference |
| Обнаружено | `--text-xs`, `--text-secondary`. Абсолютная дата + время |

**Tabs:**

| Tab | Содержание |
|-----|------------|
| Сравнение | Expected vs Actual side-by-side с diff highlighting |
| Хронология | Timeline событий по этому расхождению |
| Связь | Связанный price_action (если есть) с деталями |

**Tab: Сравнение**

Side-by-side таблица:

```
┌─────────────────────────────────────────────────┐
│               Ожидаемое          Фактическое    │
│ ─────────────────────────────────────────────── │
│ Значение      1 200 ₽           1 500 ₽        │
│                                  ▲ +25,0%       │
│ ─────────────────────────────────────────────── │
│ Источник      price_action #456  sync WB        │
│               SUCCEEDED          30 мар, 15:00  │
│ ─────────────────────────────────────────────── │
│ Дата          30 мар, 10:01     30 мар, 15:00   │
└─────────────────────────────────────────────────┘
```

Diff highlighting: ячейка «Фактическое → Значение» получает красный tint фон (`bg-red-50`), если `deltaPct` превышает threshold. Ячейка «Ожидаемое → Значение» — нейтральный фон.

Для non-numeric types (PROMO, STATUS): текстовые значения вместо чисел. Diff — выделение строки целиком если значения различаются.

Блок «Пороговые значения» под таблицей:

```
Настроенные пороги:
  WARNING: > 1%    CRITICAL: > 5%
  Текущее расхождение: 25,0% → CRITICAL
```

**Tab: Хронология**

Вертикальная timeline (сверху — новейшие):

```
┌─ Timeline ──────────────────────────────────────┐
│                                                  │
│  ● 30 мар, 15:00 — Расхождение обнаружено       │
│  │  Sync обнаружил price mismatch                │
│  │  Ожидалось: 1 200 ₽ | Факт: 1 500 ₽         │
│  │                                               │
│  ○ 30 мар, 10:01 — Связанный action              │
│  │  price_action #456: 1 200 ₽ → SUCCEEDED       │
│  │  Reconciliation: IMMEDIATE                    │
│  │                                               │
│  ○ 30 мар, 10:00 — Решение принято               │
│  │  price_decision: CHANGE 1 500 → 1 200         │
│  │  Политика: «Маржа 25% WB»                    │
│                                                  │
└──────────────────────────────────────────────────┘
```

Timeline dots:
- `●` filled (red) — событие обнаружения
- `●` filled (green) — событие решения
- `●` filled (yellow) — acknowledge
- `○` outlined (gray) — информационное событие

**Tab: Связь**

Если `relatedActionId` != null:

```
┌─ Связанный action ──────────────────────────────┐
│                                                  │
│  Action #456                                     │
│  Статус: ● SUCCEEDED                             │
│  Целевая цена: 1 200 ₽                          │
│  Режим: LIVE                                     │
│  Выполнен: 30 мар, 10:01                        │
│  Reconciliation: IMMEDIATE                       │
│                                                  │
│  [Открыть action →]                              │
│                                                  │
└──────────────────────────────────────────────────┘
```

Ссылка «Открыть action →» ведёт к экрану Execution (future page spec). Пока — открывает в новой вкладке (route TBD).

Если `relatedActionId == null`:

```
  Нет связанного action. Расхождение обнаружено
  вне контекста ценового действия.
```

### 6. Filters

Не применимо (detail view).

### 7. Sorting

Timeline сортируется по `timestamp` DESC (новейшие сверху). Не настраивается.

### 8. Pagination

Timeline: max 50 событий без пагинации (ожидаемый объём < 20 для одного mismatch).

### 9. Actions / Buttons

**Primary actions (нижняя часть panel):**

| Button | Style | Condition | Roles | API call | Feedback |
|--------|-------|-----------|-------|----------|----------|
| Подтвердить | Secondary | `status == ACTIVE` | OPERATOR+ | `POST .../acknowledge` | Toast: «Расхождение подтверждено». Status → ACKNOWLEDGED |
| Повторить ↻ | Primary | `relatedActionId != null && status IN (ACTIVE, ACKNOWLEDGED)` | OPERATOR+ | `POST /api/.../actions/{actionId}/retry` | Toast: «Повторная попытка запущена» |
| Игнорировать | Ghost | `status IN (ACTIVE, ACKNOWLEDGED)` | PRICING_MANAGER+ | `POST .../resolve { resolution: "IGNORED", note }` | Modal с полем «Причина» (required) → Toast: «Расхождение проигнорировано» |
| Эскалировать | Danger outline | `status IN (ACTIVE, ACKNOWLEDGED)` | OPERATOR+ | `POST /api/.../alerts` | Toast: «Алерт создан» |
| Решить ✓ | Primary | `status IN (ACTIVE, ACKNOWLEDGED)` | OPERATOR+ | `POST .../resolve` | Modal: dropdown `resolution` + textarea `note` → Toast: «Расхождение решено» |

**Resolve modal:**

```
┌─ Решить расхождение ────────────────────────────┐
│                                                  │
│  Тип решения: *                                  │
│  ┌──────────────────────────────────────────┐   │
│  │ ▾ Выберите тип решения                   │   │
│  │   • Принято (расхождение ожидаемо)       │   │
│  │   • Повторная установка цены             │   │
│  │   • Исследовано (причина найдена)        │   │
│  │   • Внешняя причина (сторона МП)         │   │
│  │   • Проигнорировано                      │   │
│  └──────────────────────────────────────────┘   │
│                                                  │
│  Комментарий: *                                  │
│  ┌──────────────────────────────────────────┐   │
│  │                                          │   │
│  │                                          │   │
│  └──────────────────────────────────────────┘   │
│                                                  │
│  [Отмена]                          [Решить ✓]   │
│                                                  │
└──────────────────────────────────────────────────┘
```

Resolution type mapping:

| UI label | API value | Описание |
|----------|-----------|----------|
| Принято (расхождение ожидаемо) | `ACCEPTED` | МП ещё не применил цену, задержка |
| Повторная установка цены | `REPRICED` | Запущен manual pricing run |
| Исследовано (причина найдена) | `INVESTIGATED` | Причина задокументирована |
| Внешняя причина (сторона МП) | `EXTERNAL` | Исправление невозможно |
| Проигнорировано | `IGNORED` | Оператор решил проигнорировать |

**Button visibility by status:**

| Status | Подтвердить | Повторить | Игнорировать | Эскалировать | Решить |
|--------|-------------|-----------|--------------|--------------|--------|
| ACTIVE | ✓ | ✓ (if action) | ✓ | ✓ | ✓ |
| ACKNOWLEDGED | — | ✓ (if action) | ✓ | ✓ | ✓ |
| RESOLVED | — | — | — | — | — |
| AUTO_RESOLVED | — | — | — | — | — |
| IGNORED | — | — | — | — | — |

Для терминальных статусов — action buttons скрыты. Panel переходит в read-only mode с блоком результата:

```
┌─ Результат ─────────────────────────────────────┐
│ ● Решено · 31 мар, 09:15 · Иванов А.            │
│ Тип: Повторная установка цены                    │
│ Комментарий: «Запустил ручную переоценку...»     │
└──────────────────────────────────────────────────┘
```

### 10. Status / Badge mapping

Аналогично Screen 2 — используются те же badge styles для status, type и severity.

Дополнительно в header panel:

- Если `severity == CRITICAL` → красная полоса (4px) вверху panel
- Если `severity == WARNING` → жёлтая полоса (4px) вверху panel

### 11. Loading states

| State | Визуал |
|-------|--------|
| Panel открывается, данные грузятся | Skeleton: header block + 3 tab-placeholder lines + shimmer. Panel slide-in animation (200ms ease, стандарт) |
| Action в процессе | Кнопка, вызвавшая action → spinner внутри кнопки, остальные кнопки disabled |
| Panel refresh после action | Subtle fade + re-render content. Tabs сохраняют позицию |

### 12. Empty states

| Условие | Отображение |
|---------|-------------|
| Panel открыт, но mismatch не найден (deleted/stale) | «Расхождение не найдено. Возможно, оно было авто-решено.» + [Закрыть panel] |
| Timeline пустая | «Нет событий.» (маловероятно — detection event всегда присутствует) |
| Нет связанного action | Tab «Связь»: «Это расхождение обнаружено вне контекста ценового действия.» |

### 13. Error states

| Ошибка | Отображение |
|--------|-------------|
| GET mismatch 404 | Panel content: «Расхождение не найдено.» + [Закрыть] |
| GET mismatch 5xx | Panel content: «Не удалось загрузить данные.» + [Повторить] |
| POST action 409 (CAS conflict) | Toast (warning): «Расхождение уже обработано другим пользователем.» Panel re-fetches данные, показывая обновлённый статус |
| POST action 403 | Toast (error): «Недостаточно прав для этого действия.» |
| POST action 5xx | Toast (error): «Не удалось выполнить действие.» + [Повторить] |
| Retry related action — action already retried | Toast (warning): «Действие уже запущено повторно.» (409 от Execution API) |

### 14. Permissions / Roles

| Действие | Минимальная роль |
|----------|------------------|
| Просмотр Detail Panel | VIEWER |
| Подтвердить (acknowledge) | OPERATOR |
| Повторить (retry) | OPERATOR |
| Эскалировать (create alert) | OPERATOR |
| Игнорировать | PRICING_MANAGER |
| Решить (resolve с любым типом) | OPERATOR |

Кнопки, недоступные текущей роли, скрыты (не disabled). Пользователь не видит actions, на которые нет прав.

### 15. Keyboard shortcuts

| Shortcut | Действие |
|----------|----------|
| `Escape` | Закрыть Detail Panel |
| `Tab` | Перемещение между action buttons |
| `Ctrl+Enter` | Submit в модальном окне (Resolve / Ignore) |
| `←` / `→` | Переключение между tabs (Сравнение / Хронология / Связь) |

### 16. Responsive behavior

| Viewport | Поведение |
|----------|-----------|
| ≥ 1440px | Push layout: panel (400px) сдвигает таблицу. Drag handle для resize (min 320px, max 50%) |
| 1280–1440px | Overlay layout: panel поверх таблицы (shadow `--shadow-md`) |
| < 1280px | Не поддерживается |

---

## Screen 4 — Real-time Updates

### 1. Назначение

WebSocket-канал для push-обновлений: появление новых расхождений, изменение статуса, auto-resolve. Пользователь видит изменения в реальном времени без ручного рефреша.

### 2. Route

Не отдельный route. WebSocket соединение устанавливается при входе на `/workspace/:id/mismatches` и поддерживается пока страница открыта.

### 3. Layout / Wireframe

Визуальные эффекты real-time обновлений:

```
┌─ New mismatch detected ─────────────────────────┐
│                                                  │
│  ┌─ Toast (top-right) ─────────────────────┐    │
│  │ ⚠ Новое CRITICAL расхождение            │    │
│  │ Футболка синяя · Цена · +25%            │    │
│  │ [Показать]                     ✕        │    │
│  └──────────────────────────────────────────┘    │
│                                                  │
│  ┌─ Table row pulse ───────────────────────┐    │
│  │ ███ Новая строка с background pulse ███ │    │
│  │ (bg-yellow-50 → transparent, 2s ease)   │    │
│  └──────────────────────────────────────────┘    │
│                                                  │
│  ┌─ KPI card counter increment ────────────┐    │
│  │ Активные: 12 → 13 (number animate)     │    │
│  └──────────────────────────────────────────┘    │
│                                                  │
└──────────────────────────────────────────────────┘
```

### 4. Data model / API

**WebSocket endpoint:** STOMP over WebSocket

**STOMP destination:** `/topic/workspace/{workspaceId}/mismatches`

**Message types:**

| Event type | Payload | Описание |
|------------|---------|----------|
| `MISMATCH_DETECTED` | `{ mismatchId, type, severity, offerName, skuCode, expectedValue, actualValue, deltaPct, detectedAt }` | Новое расхождение обнаружено |
| `MISMATCH_RESOLVED` | `{ mismatchId, resolution, resolvedBy, resolvedAt }` | Расхождение решено (manual или auto) |
| `MISMATCH_ACKNOWLEDGED` | `{ mismatchId, acknowledgedBy, acknowledgedAt }` | Расхождение подтверждено |
| `MISMATCH_STATUS_CHANGED` | `{ mismatchId, oldStatus, newStatus, actor }` | Статус изменился (generic fallback) |

**STOMP subscribe:** `stompClient.subscribe('/topic/workspace/{workspaceId}/mismatches', callback)`

**Auth:** STOMP connect с JWT token в `Authorization` header. WorkspaceContext из token + workspace ID validation на server side.

### 5. Content blocks

**Toast для CRITICAL mismatches:**

```
┌────────────────────────────────────────────┐
│ ⚠  Новое CRITICAL расхождение              │
│ Футболка синяя (ABC-001)                   │
│ Цена: ожидалось 1 200 ₽, факт 1 500 ₽    │
│                              [Показать]  ✕ │
└────────────────────────────────────────────┘
```

Toast parameters:
- Style: error variant (red left border, `--status-error`)
- Duration: 8 seconds (error toast standard)
- Manual dismiss: ✕ button
- Action: [Показать] → scroll to row + select + open Detail Panel
- Стacking: max 3 toasts simultaneously. Older → auto-dismiss

**Toast для WARNING mismatches:**

Не показывается как toast. Только обновление таблицы.

**Toast для resolved mismatches:**

```
┌────────────────────────────────────────────┐
│ ✓  Расхождение решено                      │
│ Футболка синяя · Авто-решено               │
│                                         ✕  │
└────────────────────────────────────────────┘
```

Toast parameters:
- Style: success variant (green left border)
- Duration: 3 seconds
- Manual dismiss: ✕ button

### 6. Filters

WebSocket messages приходят для всех mismatches workspace. Фильтрация — на клиенте:

- Если мисмэтч соответствует текущим фильтрам → вставляется в таблицу
- Если не соответствует → обновляет только dashboard KPI (counter increment) без вставки строки
- Если Detail Panel открыт и пришло обновление для этого mismatch → panel обновляется in-place

### 7. Sorting

Новая строка вставляется в позицию, соответствующую текущей сортировке. Если `sort=detectedAt DESC` (default) → новая строка вставляется в начало.

### 8. Pagination

При получении нового мисмэтча:
- Если пользователь на первой странице → строка вставляется в начало, последняя строка уходит на следующую страницу. Counter обновляется: «Показано 1–50 из 143»
- Если пользователь не на первой странице → counter обновляется, но видимые строки не сдвигаются (предотвращение дезориентации)

### 9. Actions / Buttons

Toast button [Показать]:
1. Scroll page to Mismatch List
2. Highlight строку с `mismatchId` (background pulse animation)
3. Select строку
4. Open Detail Panel

### 10. Status / Badge mapping

При обновлении статуса мисмэтча в таблице:
- Badge обновляется с micro-animation: crossfade (150ms) от старого к новому
- Строка получает background pulse (1 раз, 2s ease-out):
  - RESOLVED / AUTO_RESOLVED → зелёный pulse (`bg-green-50` → transparent)
  - ACKNOWLEDGED → жёлтый pulse (`bg-amber-50` → transparent)
  - Новый ACTIVE → нейтральный pulse (`bg-blue-50` → transparent)

### 11. Loading states

| State | Визуал |
|-------|--------|
| WebSocket connecting | Status Bar indicator: «● Подключение...» (жёлтая точка) |
| WebSocket connected | Status Bar indicator: «● Онлайн» (зелёная точка). Невидим (дефолт) |
| WebSocket disconnected | Persistent banner (top of Main Area): «⚠ Соединение потеряно. Переподключение...» (`--status-warning` background). Auto-retry: exponential backoff 1s, 2s, 4s, 8s, max 30s |
| WebSocket reconnected | Banner auto-dismiss. Toast (info): «Соединение восстановлено». Full data refresh (stale data catch-up) |

### 12. Empty states

Не применимо (WebSocket — это event stream, нет «пустого» состояния).

### 13. Error states

| Ошибка | Отображение |
|--------|-------------|
| WebSocket auth failure (401) | Banner (red): «Ошибка аутентификации. Перезайдите в систему.» + [Войти заново] |
| WebSocket disconnect (network) | Banner (yellow): «Соединение потеряно. Данные могут быть неактуальны. Переподключение...» Auto-retry |
| Server-side error (STOMP ERROR frame) | `log.warn` в console. Banner если persistent: «Ошибка real-time канала. Данные обновляются вручную.» |
| Message parse error (bad payload) | Silent ignore + `log.error`. Не показывать пользователю |

### 14. Permissions / Roles

| Действие | Минимальная роль |
|----------|------------------|
| Получать WebSocket events | VIEWER |
| Видеть CRITICAL toast | VIEWER |
| Клик [Показать] в toast | VIEWER |

WebSocket подписка доступна всем ролям с доступом к workspace. Server-side STOMP interceptor проверяет workspace membership.

### 15. Keyboard shortcuts

Нет специфичных shortcuts для real-time updates. Toast [Показать] активируется по `Enter` при фокусе.

### 16. Responsive behavior

WebSocket connection не зависит от viewport. Toast positioning: top-right corner, поверх всех элементов (z-index: overlay level).

---

## Badge reference

### Mismatch status badges

| Status | Dot | Label | Dot CSS | Background | Text | Border |
|--------|-----|-------|---------|------------|------|--------|
| `ACTIVE` | ● 6px | Активно | `bg-[#DC2626]` | `bg-red-50` | `text-red-700` | `border border-red-200` |
| `ACKNOWLEDGED` | ● 6px | Подтверждено | `bg-[#D97706]` | `bg-amber-50` | `text-amber-700` | `border border-amber-200` |
| `RESOLVED` | ● 6px | Решено | `bg-[#059669]` | `bg-green-50` | `text-green-700` | `border border-green-200` |
| `AUTO_RESOLVED` | ● 6px | Авто-решено | `bg-[#059669]` | `bg-green-50` | `text-green-700` | `border border-green-200` |
| `IGNORED` | ● 6px | Проигнорировано | `bg-[#6B7280]` | `bg-gray-50` | `text-gray-500` | `border border-gray-200` |

### Mismatch type badges

| Type | Label | Background | Text |
|------|-------|------------|------|
| `PRICE` | Цена | `bg-blue-100` | `text-blue-700` |
| `STOCK` | Остаток | `bg-amber-100` | `text-amber-700` |
| `PROMO` | Промо | `bg-purple-100` | `text-purple-700` |
| `FINANCE` | Финансы | `bg-indigo-100` | `text-indigo-700` |

Badge shape: pill (border-radius `--radius-md`, 6px). Height: 20px. Font: `--text-xs` (11px), font-weight 500.

### Severity indicators

| Severity | Icon | Color | Описание |
|----------|------|-------|----------|
| `WARNING` | `lucide:alert-triangle` 14px | `--status-warning` (#D97706) | Стандартная серьёзность |
| `CRITICAL` | `lucide:alert-triangle` 14px | `--status-error` (#DC2626) | Повышенная серьёзность. + Red left border (2px) на строке таблицы |

### Resolution type labels

| API value | UI label | Описание |
|-----------|----------|----------|
| `ACCEPTED` | Принято | Расхождение ожидаемо |
| `REPRICED` | Повторная установка | Запущен manual pricing run |
| `INVESTIGATED` | Исследовано | Причина найдена |
| `EXTERNAL` | Внешняя причина | Сторона маркетплейса |
| `IGNORED` | Проигнорировано | Оператор решил игнорировать |
| `AUTO_RESOLVED` | Авто-решено | Расхождение исчезло при следующем sync |

---

## User flows

### UF-1: Расследование ценового расхождения

**Персона:** Оператор (OPERATOR)
**Триггер:** Toast notification о CRITICAL расхождении

```
1. Оператор видит toast: «Новое CRITICAL расхождение: Футболка синяя, Цена, +25%»
2. Клик [Показать] → страница скроллится к таблице, строка подсвечена
3. Клик по строке → Detail Panel открывается справа
4. Tab «Сравнение»: видит Expected 1 200 ₽ vs Actual 1 500 ₽ (+25%)
   Источник: price_action #456 (SUCCEEDED) vs marketplace_read (sync)
5. Tab «Связь»: видит action #456 — SUCCEEDED, reconciliation IMMEDIATE
   Значит action был выполнен успешно, но маркетплейс вернул другую цену
6. Tab «Хронология»: видит последовательность событий
7. Решение: кликает [Повторить ↻] — повторный запрос на установку цены
8. Toast: «Повторная попытка запущена»
9. Через 1–2 минуты приходит WebSocket event MISMATCH_RESOLVED
   → строка обновляется, статус → Авто-решено (зелёный pulse)
```

**Альтернативный путь:** Если повтор не помог, оператор кликает [Эскалировать] для создания алерта.

### UF-2: Массовое игнорирование ожидаемых расхождений

**Персона:** Pricing Manager (PRICING_MANAGER)
**Триггер:** После масштабной переоценки — десятки расхождений из-за задержки применения цен маркетплейсом

```
1. Менеджер открывает /workspace/:id/mismatches
2. Dashboard: видит Активных — 47 (↑42 за 7д). Критичных — 2
3. Устанавливает фильтр: Тип = Цена, Статус = Активно
4. Таблица показывает 45 ценовых расхождений
5. Проверяет первые 3 — все ожидаемые (action SUCCEEDED 2 часа назад,
   маркетплейс ещё не обновил read API)
6. Ctrl+A → выбирает все 45 строк на странице
7. Bulk action bar: «45 расхождений выбрано [Игнорировать выбранные]»
8. Клик [Игнорировать выбранные] → Modal:
   «Отметить 45 расхождений как проигнорированные?»
   Причина: «Ожидаемая задержка применения цен WB после массовой переоценки»
   [Подтвердить]
9. Toast: «45 расхождений проигнорировано»
10. Таблица: строки обновляются, статус → Проигнорировано (серый)
11. Dashboard: Активных → 4 (оставшиеся 2 CRITICAL + 2 non-price)
```

### UF-3: Мониторинг расхождений в реальном времени после sync

**Персона:** Оператор (OPERATOR)
**Триггер:** Плановая синхронизация данных WB, оператор наблюдает за результатом

```
1. Оператор открывает /workspace/:id/mismatches
2. Dashboard: Активных — 3, всё спокойно
3. Status Bar: «● WB synced 1 мин назад»
4. Sync завершается, mismatch checker запускается
5. WebSocket events начинают приходить:
   — MISMATCH_DETECTED (PRICE, WARNING) → новая строка в таблице (blue pulse)
   — MISMATCH_DETECTED (PRICE, CRITICAL) → toast + строка (yellow pulse)
   — MISMATCH_DETECTED (STOCK, WARNING) → строка добавлена
   — MISMATCH_RESOLVED (AUTO_RESOLVED) → существующее расхождение решилось (green pulse)
6. Dashboard KPI обновляются в реальном времени:
   Активных: 3 → 4 → 5 → 6 (counter increment animation)
   Donut chart перерисовывается с новым распределением
7. Оператор видит CRITICAL toast, кликает [Показать]
8. Разбирает расхождение в Detail Panel
9. Через 5 минут — ещё пачка AUTO_RESOLVED events
   (маркетплейс применил цены с задержкой)
10. Dashboard: Активных: 6 → 3 → 1
11. Оператор доволен — система работает штатно
```

---

## Edge cases

### EC-1: Массовый всплеск расхождений после sync

**Сценарий:** Sync обнаруживает 500+ расхождений одновременно (например, после сбоя API или массовой переоценки маркетплейсом).

**Проблемы:**
- 500 WebSocket events подряд → flood of toasts
- Dashboard KPI — быстро меняющиеся числа
- Таблица — массовая вставка строк

**Решение:**
- **Toast throttling:** max 3 toast notifications за 10 секунд. Если больше → один summary toast: «Обнаружено N новых расхождений за последнюю минуту. [Обновить]»
- **Dashboard debounce:** KPI обновляются не чаще раза в 2 секунды. Промежуточные значения буферизируются, отображается последнее
- **Таблица batch insert:** WebSocket events буферизируются на 1 секунду, вставляются пакетом. Анимация pulse — только для первых 5 строк в batch, остальные вставляются без анимации
- **Counter animation:** при delta > 10 — число обновляется мгновенно (без count-up animation), чтобы не выглядело «зависшим»

### EC-2: Расхождение для удалённого/архивированного offer

**Сценарий:** Mismatch ссылается на `offerId`, который уже `ARCHIVED` или удалён из маркетплейса.

**Проблемы:**
- Offer detail может быть недоступен
- Ссылки «Перейти к товару» ведут в никуда
- Action retry невозможен

**Решение:**
- Таблица: строка отображается нормально. Если offer archived → значок «📦 Архив» рядом с именем
- Detail Panel: header показывает `(архивный товар)` под именем. Tab «Связь» → «Товар архивирован. Действия недоступны.»
- Buttons: «Повторить ↻» и «Эскалировать» скрыты. Доступны только «Игнорировать» и «Решить» (с resolution = EXTERNAL)
- Context menu «Перейти к товару» → disabled с tooltip «Товар архивирован»

### EC-3: Concurrent resolution (два оператора решают одновременно)

**Сценарий:** Оператор A открывает Detail Panel для mismatch #77. Оператор B в это же время resolves mismatch #77 через bulk action.

**Проблемы:**
- Оператор A нажимает [Решить], но mismatch уже resolved
- CAS conflict на backend

**Решение:**
- Backend: CAS guard на `status`. `POST .../resolve` с expected `status IN (ACTIVE, ACKNOWLEDGED)`. Если текущий статус уже RESOLVED → HTTP 409 с текущим состоянием
- Frontend (оператор A):
  1. Получает WebSocket event `MISMATCH_RESOLVED` → panel обновляется in-place, buttons исчезают, показывается результат оператора B
  2. Если WebSocket event пришёл после нажатия кнопки, но до ответа API → API вернёт 409
  3. Toast (warning): «Расхождение уже решено другим пользователем (Оператор B)»
  4. Panel re-fetch → показывает обновлённые данные
- Нет потери данных, нет двойного resolve

### EC-4: WebSocket reconnect — stale data catch-up

**Сценарий:** WebSocket отключается на 5 минут (сетевая проблема). За это время 10 mismatches появились и 3 resolved.

**Решение:**
- При WebSocket reconnect → full data refresh:
  1. Re-fetch `GET /api/.../mismatches/summary` (dashboard KPIs)
  2. Re-fetch `GET /api/.../mismatches` (current page с текущими фильтрами)
  3. Если Detail Panel открыт → re-fetch `GET /api/.../mismatches/{id}`
- TanStack Query `staleTime: 30s` — при reconnect данные считаются stale, refetch автоматический
- Banner «Данные обновлены» (info toast, 3s) после catch-up

### EC-5: Mismatch для offer без related action (non-reconciliation mismatch)

**Сценарий:** Stock inconsistency обнаружен между `canonical_stock_current` и `fact_inventory_snapshot`. Это не результат price action — нет `relatedActionId`.

**Решение:**
- Detail Panel: tab «Связь» показывает «Нет связанного действия. Расхождение обнаружено при сравнении данных sync.»
- Button «Повторить ↻» скрыт (нечего retry'ить)
- Остальные buttons доступны: Подтвердить, Игнорировать, Эскалировать, Решить
- Tab «Сравнение» показывает значения из двух источников данных (canonical vs analytics)

---

## Related documents

- [Frontend Design Direction](frontend-design-direction.md) — design system, component patterns, colors
- [Seller Operations](../modules/seller-operations.md) — Mismatch Monitor section, REST API contracts
- [Execution](../modules/execution.md) — reconciliation flow, action state machine
- [Tenancy & IAM](../modules/tenancy-iam.md) — role permissions matrix
- [Audit & Alerting](../modules/audit-alerting.md) — alert creation, WebSocket STOMP architecture
