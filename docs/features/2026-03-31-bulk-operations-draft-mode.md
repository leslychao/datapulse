# Feature: Bulk Operations & Draft Mode

**Статус:** PARTIALLY IMPLEMENTED
**Дата создания:** 2026-03-31
**Дата обновления:** 2026-04-06
**Автор:** Виталий Ким
**Целевая фаза:** E — Seller Operations

---

## Business Context

### Проблема

Селлеры маркетплейсов при необходимости массового изменения цен (пересмотр ассортимента, реакция на изменение тарифов, сезонная корректировка) выгружают данные в Excel, вносят изменения формулами и загружают обратно. Цикл занимает 20-30 минут, не имеет audit trail, не защищён safety-guards и создаёт разрыв между «системой аналитики» и «местом где реально работаешь».

Причины ухода в Excel:
- **Формулы на диапазон:** Excel позволяет `=B2*1.05` для 500 строк. Web UI — только по одной ячейке
- **Staging (черновик):** в Excel можно менять, смотреть, думать. В web нажал Save — применилось
- **Условные формулы:** «если остаток < 10, цена +5%» — в web это нужно делать руками per-SKU
- **Визуальная верификация:** человек видит всю картину в таблице, подкрашивает, сортирует, проверяет глазами

### Бизнес-ценность

**Killer-фича:** если массовое изменение цен в web-интерфейсе быстрее, удобнее и безопаснее, чем в Excel — селлер не уйдёт из системы. DataPulse становится **единственным рабочим инструментом**, а не «аналитикой, из которой экспортируют в Excel».

**Measurable outcomes:**
- Время массового пересмотра цен: с 20-30 минут (Excel) до 30-60 секунд (web)
- Audit trail: каждое bulk-изменение проходит через pricing pipeline с explanation
- Safety: guard pipeline защищает от ошибок, которых нет в Excel (margin floor, promo guard, stale data)
- Нулевой export/import цикл — данные не покидают систему

### Ключевой принцип

Bulk operations — **не обход** pricing pipeline, а его расширение. Каждое изменение проходит через constraints и guards. Каждое решение имеет explanation. Каждое действие — audit trail. Разница с policy-based pricing: trigger = ручной ad-hoc, не scheduled/event-driven.

---

## User Stories

### US-1: Массовое изменение цены формулой

**Как** менеджер по ценообразованию,
**я хочу** выделить 50 товаров в гриде и применить формулу «+5% к текущей цене» ко всем сразу,
**чтобы** не менять цену каждого товара вручную.

### US-2: Массовое изменение с условием

**Как** менеджер по ценообразованию,
**я хочу** применить формулу «наценка 30% от себестоимости» только к выбранным товарам,
**чтобы** выровнять маржу по категории за одну операцию.

### US-3: Черновик изменений (Draft Mode)

**Как** селлер,
**я хочу** вносить изменения цен в гриде как черновик — видеть projected margin, старую и новую цену — и применить все изменения одной кнопкой,
**чтобы** проверить результат глазами перед отправкой на маркетплейс.

### US-4: Предварительный просмотр последствий

**Как** менеджер по ценообразованию,
**я хочу** перед применением bulk-изменений видеть summary: сколько товаров затронуто, средний % изменения, минимальная маржа, сколько заблокировано guards,
**чтобы** принять осознанное решение.

### US-5: Массовое изменение себестоимости

**Как** селлер,
**я хочу** обновить себестоимость сразу для 30 товаров (например, после изменения закупочной цены у поставщика),
**чтобы** не редактировать каждый cost_profile по одному.

### US-6: Отмена черновика

**Как** селлер,
**я хочу** отменить все несохранённые изменения в draft mode одной кнопкой,
**чтобы** начать сначала, если допустил ошибку.

---

## Acceptance Criteria

### Backend
- [x] REST endpoints: `POST /api/workspaces/{workspaceId}/pricing/bulk-manual/preview` и `/apply` — работают
- [x] `BulkManualPricingService`: constraint resolution + guard pipeline для list of (offerId, targetPrice)
- [x] Guard pipeline: frequency/volatility/stock-out отключены для MANUAL_BULK через `BULK_GUARD_CONFIG`
- [x] Explanation: `[Источник] Ручное массовое изменение (MANUAL_BULK run #N)` — реализован
- [x] Audit trail: `pricing_run.trigger_type = MANUAL_BULK`, `request_hash`, `requested_offers_count`
- [x] Permissions: PRICING_MANAGER, ADMIN, OWNER — настроены на контроллере
- [ ] **BUG:** `buildBulkSignals()` — `manualLockActive`, `promoActive`, `dataFreshnessAt` захардкожены (false/false/null). Guards promo и manual_lock не работают; stale data guard блокирует всё
- [x] `BULK_GUARD_CONFIG.minMarginPct = null` (0%) — решение принято: guard защищает только от отрицательной маржи (см. §9)
- [ ] Bulk cost update: `POST /api/cost-profiles/bulk-update` — endpoint есть, но поддерживает только абсолютные значения (FIXED). Формулы (INCREASE_PCT, DECREASE_PCT, MULTIPLY) не реализованы

### Frontend
- [x] Draft mode toggle в toolbar — кнопка «Черновик» есть
- [x] `GridStore.draftChanges` Map и `setDraftPrice()` / `removeDraftPrice()` — определены
- [x] `beforeunload` host listener в grid-page — есть
- [ ] **BUG:** `setDraftPrice()` нигде не вызывается — draft changes не наполняются
- [ ] **BUG:** Draft exit confirmation: `showDraftExitConfirm` signal есть, но `toggleDraftMode()` делает `return` при unsaved changes — модалка не показывается
- [ ] **BUG:** Frontend↔Backend контракт рассогласован (см. секцию «Contract Alignment» ниже)
- [ ] Grid diff cells: strikethrough old price, yellow bg, projected margin — не реализовано
- [ ] Inline price edit в Draft Mode — не реализовано
- [ ] Formula Panel (6 формул, client-side preview) — не реализовано
- [ ] Bulk Cost Update Panel (UI) — не реализовано
- [ ] Per-cell undo (right-click → «Отменить изменение») — не реализовано
- [ ] Draft banner: мин. маржа, guards warning, server-side preview перед apply — не реализовано
- [ ] «Показать diff» filter view — не реализовано
- [ ] Bulk actions bar: кнопки «Изменить цену» и «Себестоимость» — не реализовано

---

## Scope

### В scope

- Bulk formula panel (применение формулы к выбранным строкам)
- Draft mode с inline editing в гриде
- Draft diff visualization (старая → новая цена, projected margin)
- Draft summary banner с агрегатами и guard-preview
- Apply draft → pricing pipeline → decisions + actions
- Discard draft
- Bulk cost_price update (массовое изменение себестоимости)
- Новый `trigger_type = MANUAL_BULK` в pricing_run
- REST API для bulk operations
- Explanation format для manual bulk decisions

### Вне scope

- Clipboard paste из Excel (Level 3 — исключён)
- Bulk formula DSL / скриптовый язык (формулы — фиксированный набор, не произвольные выражения)
- Conditional formulas с произвольными условиями (условия = то что уже отфильтровано в гриде)
- Undo после apply (actions управляются через Execution lifecycle: cancel, supersede)
- Draft persistence на сервере (draft — клиентское состояние, при закрытии вкладки теряется)

---

## Architectural Impact

**Статус:** resolved

### Затронутые модули

| Модуль | Тип изменения | Описание |
|--------|---------------|----------|
| [Pricing](../modules/pricing.md) | Расширение | Новый trigger_type `MANUAL_BULK`; новый endpoint `POST /api/workspaces/{workspaceId}/pricing/bulk-manual`; новый strategy_type контекст `MANUAL_OVERRIDE`; explanation extension |
| [Seller Operations](../modules/seller-operations.md) | Расширение | Bulk Actions Bar: formula panel, cost update panel. Draft mode section |
| [Execution](../modules/execution.md) | Без изменений | Actions от manual bulk идут через тот же lifecycle |
| Frontend: [pages-operational-grid.md](../frontend/pages-operational-grid.md) | Расширение | Draft mode toggle, inline edit, diff cells, draft banner, formula panel UI, bulk cost update UI |

### Новые модули

Не требуются.

### Изменения в data model

#### Изменения существующих таблиц

| Таблица | Изменение |
|---------|-----------|
| `pricing_run.trigger_type` | Новое значение: `MANUAL_BULK` |

Новые таблицы **не нужны**. Draft state хранится клиентски. Pricing decisions и actions используют existing tables.

### Архитектурные решения

| Решение | Варианты | Выбор | Обоснование |
|---------|----------|-------|-------------|
| Где хранить draft state | A: Сервер (PostgreSQL — draft_price_change table)<br>B: Клиент (React state, Map<offerId, DraftChange>) | **B** | Draft — это unsaved user intent, не business state. Серверный draft создаёт complexity (lifecycle, cleanup, multi-tab conflicts). Клиентский draft прост: теряется при закрытии — это OK, потому что draft = 30-60 секунд работы, не часы. Browser `beforeunload` предупредит о несохранённых изменениях |
| Как проходить pipeline для manual bulk | A: Полный pricing run (signal assembly, strategy, constraints, guards)<br>B: Упрощённый path (только constraints + guards, без strategy) | **B** | Manual bulk указывает конкретную target_price — strategy evaluation не нужна. Constraints (min_margin, max_price_change, min/max_price, rounding) и guards (stale data, promo, manual lock, stock-out) — обязательны. Strategy = `MANUAL_OVERRIDE` (user-provided price) |
| Как создавать actions | A: Все actions сразу (batch insert)<br>B: Pricing run с per-offer processing | **A** (batch, но через pricing_run) | Manual bulk создаёт `pricing_run` (trigger_type = MANUAL_BULK) → per-offer: constraint resolution → guard pipeline → decision → action. Batch insert decisions + actions. Тот же pattern что и policy-based runs, но strategy step заменён на user-provided price |
| Execution mode для manual bulk actions | A: Всегда APPROVED (user уже подтвердил)<br>B: Наследовать от active policy offer-а<br>C: Configurable per-bulk-request | **A** | Manual bulk — осознанное ручное действие. Пользователь нажал «Применить» = approval. Двойное подтверждение (сначала Apply draft, потом Approve action) — плохой UX. Guard pipeline обеспечивает safety |
| Preview перед apply | A: Client-side preview (projected margin из текущих данных)<br>B: Server-side dry-run | **A + B** | Client-side preview показывает projected margin в real-time (instant feedback, computed из cost_price + new_price). Server-side dry-run при нажатии «Применить» → backend прогоняет constraints + guards → возвращает preview → пользователь подтверждает финально |

---

## Детальный дизайн

### 1. Bulk Formula Panel

Доступ: Bulk Actions Bar → кнопка «Изменить цену» (visible когда ≥ 1 строка выбрана).

#### Формулы

| # | Русский label | Формула | Параметры | Пример |
|---|---------------|---------|-----------|--------|
| 1 | Увеличить на % | `new_price = current_price × (1 + pct/100)` | `pct: decimal` | +5% → 1 000 → 1 050 |
| 2 | Уменьшить на % | `new_price = current_price × (1 − pct/100)` | `pct: decimal` | −10% → 1 000 → 900 |
| 3 | Умножить на коэффициент | `new_price = current_price × factor` | `factor: decimal` | ×1.15 → 1 000 → 1 150 |
| 4 | Установить фиксированную | `new_price = fixed_price` | `fixed_price: decimal` | 999 ₽ → все = 999 |
| 5 | Наценка от себестоимости | `new_price = cost_price × (1 + markup_pct/100)` | `markup_pct: decimal` | markup 30% при с/с 600 → 780 |
| 6 | Округлить до шага | `new_price = round(current_price, step, direction)` | `step: int, direction: FLOOR/NEAREST/CEIL` | step=100, FLOOR → 1 450 → 1 400 |

**Формула 5 (наценка от с/с):** если `cost_price = NULL` для товара → товар исключается из bulk с причиной «Себестоимость не задана». Count исключённых отображается в preview.

#### UI: Formula Panel (dropdown)

```
┌─ Массовое изменение цены ───────────────────────────┐
│                                                      │
│ Действие:    [Увеличить на %          ▾]            │
│                                                      │
│ Значение:    [5,0          ] %                       │
│                                                      │
│ Округление:  ☑ Округлить до [10 ₽] шаг [FLOOR ▾]   │
│                                                      │
│ ─────────────────────────────────────────────────── │
│ Предпросмотр (47 товаров)                            │
│                                                      │
│ Ср. изменение:    +5,0%                              │
│ Мин. новая цена:  525 ₽ (маржа: 12,4%)             │
│ Макс. новая цена: 15 750 ₽ (маржа: 64,2%)          │
│ Мин. маржа после: 12,4%                             │
│                                                      │
│ ─────────────────────────────────────────────────── │
│ ⚠ 3 товара заблокированы:                           │
│   • 1 — ручная блокировка цены                      │
│   • 1 — товар в активном промо                      │
│   • 1 — себестоимость не задана                     │
│   [Показать заблокированные]                         │
│                                                      │
│ [Отмена]                    [Применить (44)]         │
└──────────────────────────────────────────────────────┘
```

**Предпросмотр:** computed client-side. Быстрый (instant) feedback при изменении формулы/значения. Данные `current_price` и `cost_price` уже в grid state.

**«Применить»** → server-side dry-run → confirmation modal (см. §6) → batch create.

#### Bulk Cost Update Panel

Доступ: Bulk Actions Bar → кнопка «Себестоимость» (visible когда ≥ 1 строка выбрана).

```
┌─ Массовое изменение себестоимости ──────────────────┐
│                                                      │
│ Действие:    [Установить фиксированную    ▾]        │
│              ● Установить фиксированную              │
│              ○ Увеличить на %                        │
│              ○ Уменьшить на %                        │
│              ○ Умножить на коэффициент               │
│                                                      │
│ Значение:    [650          ] ₽                       │
│                                                      │
│ Дата начала: [01.04.2026   ] (SCD2 valid_from)      │
│                                                      │
│ ─────────────────────────────────────────────────── │
│ 47 товаров будет обновлено.                          │
│ ⚠ 5 товаров без текущей себестоимости —             │
│   будет создана первая версия.                       │
│                                                      │
│ [Отмена]                    [Обновить (47)]          │
└──────────────────────────────────────────────────────┘
```

**API:** `POST /api/cost-profiles/bulk-update`

```json
{
  "sellerSkuIds": [1, 2, 3, ...],
  "operation": "FIXED",
  "value": 650.00,
  "validFrom": "2026-04-01"
}
```

`operation` enum: `FIXED`, `INCREASE_PCT`, `DECREASE_PCT`, `MULTIPLY`.

Каждый SKU обрабатывается как отдельный SCD2 UPSERT (закрытие текущей версии + INSERT новой). Атомарность: per-SKU, не all-or-nothing (partial success допускается).

Response: `{ updated: 47, skipped: 0, errors: [] }`.

### 2. Draft Mode

#### Активация

Toggle button в Toolbar grid-а: «Черновик» (outline button → filled button when active).

```
┌─ Toolbar ──────────────────────────────────────────────────────────────────────┐
│ [Filters...] [+ Фильтр] [⊘]  │  [✎ Черновик] [≡ Колонки] [⫶ Плотн.] [↓ Экс.]│
└────────────────────────────────────────────────────────────────────────────────┘
```

При активации:
1. Кнопка «Черновик» → filled style с акцентным цветом
2. Draft banner появляется над grid-ом
3. Колонка `current_price` становится editable (double-click или single-click)
4. Grid получает дополнительную колонку `projected_margin` (live computed)

При деактивации:
- Если есть unsaved changes → confirmation: «Отменить 23 изменения?» → Да / Нет
- Если нет changes → просто отключается

#### Draft Banner

```
┌───────────────────────────────────────────────────────────────────────────────┐
│ ✎ Черновик: 23 изменения                                                     │
│ Ср. изменение: +3,7%  │  Мин. маржа: 14,2%  │  ⚠ 2 заблокированы guards    │
│                                                                               │
│ [Показать diff]  [Сбросить все]                         [Применить (21)]      │
└───────────────────────────────────────────────────────────────────────────────┘
```

| Элемент | Описание |
|---------|----------|
| Counter | `{N} изменений` — live counter |
| Ср. изменение | Средний % изменения по всем draft entries |
| Мин. маржа | Минимальная projected margin среди изменённых товаров |
| Guards warning | `{N} заблокированы guards` — count товаров, которые не пройдут guards (client-side pre-check: manual_lock, promo_status, stock = 0) |
| «Показать diff» | Переключает grid в diff-view (см. ниже) |
| «Сбросить все» | Очищает все draft entries. Confirmation: нет (быстрая операция) |
| «Применить» | Server-side dry-run → confirmation → batch create. `(N)` = count без заблокированных |

#### Grid в Draft Mode — ячейки с изменениями

```
│ ☐ │ ABC-001 │ Футболка │ WB │ 1̶ ̶5̶0̶0̶ → 1 575 ₽ │ 60% → 58,1% │ ... │
│ ☐ │ DEF-002 │ Кроссовк │ OZ │ 3̶ ̶8̶9̶0̶ → 4 085 ₽ │ 25% → 27,3% │ ... │
│ ☐ │ GHI-003 │ Кепка    │ WB │ 800 ₽             │ 40,0%       │ ... │
│ ☐ │ JKL-004 │ Шапка 🔒 │ WB │ 1 200 ₽  ⚠       │ 35,0%       │ ... │
```

| Визуал | Описание |
|--------|----------|
| Зачёркнутая старая цена + новая | Ячейка с background `--bg-warning-subtle` (светло-жёлтый). Старая цена зачёркнута (`text-decoration: line-through`, `--text-secondary`). Новая цена — `--text-primary`, bold |
| Projected margin | Колонка `margin_pct` показывает `старая% → новая%`. Если новая маржа < 10% — красный цвет |
| Неизменённые строки | Обычный стиль, ячейка цены read-only |
| Заблокированные (guard pre-check) | Иконка ⚠ в ячейке цены. Tooltip: «Товар заблокирован: ручная блокировка цены». Ячейка non-editable |

#### Inline edit в Draft Mode

| Свойство | Значение |
|----------|----------|
| Trigger | Double-click на ячейку `current_price` в Draft Mode |
| Edit control | Number input, right-aligned, monospace, `₽` suffix. Pre-filled с current_price |
| Save to draft | Enter или Blur → записывает в клиентский `draftChanges` Map |
| Cancel | Escape → возврат к текущему значению (не draft значению!) |
| Undo per-cell | Right-click на изменённую ячейку → «Отменить изменение» → remove из draftChanges |
| Validation | > 0, decimal с 2 знаками |

#### «Показать diff» view

При нажатии «Показать diff» в draft banner — grid фильтруется, показывая **только изменённые строки**. Toolbar filter pill: `[Только изменения ×]`. Закрытие pill → возврат к полному grid.

Дополнительные колонки в diff view:

| Колонка | Описание |
|---------|----------|
| Текущая цена | `current_price` (read-only) |
| Новая цена | `draft_price` (editable) |
| Δ цены | `new - old` с знаком и `₽` |
| Δ% | `(new - old) / old × 100` с знаком и `%` |
| Текущая маржа | computed из current_price и cost_price |
| Новая маржа | computed из draft_price и cost_price |
| Guard status | ✓ пройдёт / ⚠ заблокирован (причина) |

### 3. Apply flow (Draft → Server)

#### Шаг 1: Client → Server dry-run

```
POST /api/workspaces/{workspaceId}/pricing/bulk-manual/preview
```

Request:

```json
{
  "changes": [
    { "marketplaceOfferId": 12345, "targetPrice": 1575.00 },
    { "marketplaceOfferId": 67890, "targetPrice": 4085.00 }
  ]
}
```

Backend выполняет per-offer:
1. Resolve current canonical_price_current
2. Constraint resolution: min_margin → max_price_change → min_price → max_price → marketplace_min → rounding
3. Guard pipeline: stale_data → margin → frequency → volatility → promo → manual_lock → stock_out
4. Decision: CHANGE (clamped price) / SKIP (guard blocked)

Response:

```json
{
  "summary": {
    "totalRequested": 47,
    "willChange": 41,
    "willSkip": 4,
    "willBlock": 2,
    "avgChangePct": 4.8,
    "minMarginAfter": 14.2,
    "maxChangePct": 12.5
  },
  "offers": [
    {
      "marketplaceOfferId": 12345,
      "skuCode": "ABC-001",
      "productName": "Футболка синяя",
      "currentPrice": 1500.00,
      "requestedPrice": 1575.00,
      "effectivePrice": 1570.00,
      "result": "CHANGE",
      "constraintsApplied": [
        { "name": "rounding", "fromPrice": 1575.00, "toPrice": 1570.00 }
      ],
      "projectedMarginPct": 58.6
    },
    {
      "marketplaceOfferId": 99999,
      "skuCode": "JKL-004",
      "result": "SKIP",
      "skipReason": "pricing.guard.manual_lock.blocked",
      "guard": "manual_lock_guard"
    }
  ]
}
```

`effectivePrice` может отличаться от `requestedPrice` — constraints clamped цену.

#### Шаг 2: Confirmation modal

```
┌─ Применить массовое изменение цен ─────────────────────────┐
│                                                              │
│ 41 товар будет изменён                                       │
│                                                              │
│ Среднее изменение:  +4,8%                                    │
│ Минимальная маржа:  14,2%                                    │
│ Макс. изменение:    +12,5%                                   │
│                                                              │
│ ⚠ 4 товара пропущены (guards)                               │
│ ⚠ 2 товара скорректированы constraints                      │
│   (запрошенная цена отличается от итоговой)                  │
│                                                              │
│ Действия будут созданы со статусом APPROVED                  │
│ и отправлены на исполнение.                                  │
│                                                              │
│ [Отмена]                              [Применить (41)]       │
└──────────────────────────────────────────────────────────────┘
```

#### Шаг 3: Apply

```
POST /api/workspaces/{workspaceId}/pricing/bulk-manual/apply
```

Request body — тот же формат, что и preview. Backend:

1. INSERT `pricing_run` (trigger_type = `MANUAL_BULK`, status = `PENDING`)
2. CAS `PENDING → IN_PROGRESS`
3. Per-offer: constraint resolution → guard pipeline → INSERT `price_decision` → INSERT `price_action` (status = `APPROVED`)
4. INSERT `outbox_event` per action (PRICE_ACTION_EXECUTE)
5. CAS `IN_PROGRESS → COMPLETED`
6. Response: `{ pricingRunId, created: 41, skipped: 6, errors: [] }`

**Идемпотентность:** `pricing_run` с trigger_type = `MANUAL_BULK` содержит `request_hash` (SHA-256 от sorted list of offerId+targetPrice). Повторный request с тем же hash → 409 Conflict.

#### Шаг 4: Client cleanup

После успешного apply:
1. Draft state очищается
2. Draft mode деактивируется
3. Toast: «41 ценовое действие создано»
4. Grid обновляется (re-fetch): `lastActionStatus` → APPROVED/SCHEDULED
5. WebSocket push обновит статусы по мере исполнения

### 4. pricing_run extension

Существующая модель `pricing_run` расширяется новым `trigger_type`:

| Триггер | Механизм | Частота |
|---------|----------|---------|
| Post-sync | RabbitMQ event `ETL_SYNC_COMPLETED` | После ETL sync |
| Manual | REST API `POST /api/pricing/runs` | По требованию |
| Schedule | Spring `@Scheduled` cron | Configurable |
| Policy change | `@TransactionalEventListener` | При изменении policy |
| **Manual bulk** | REST API `POST /api/workspaces/{workspaceId}/pricing/bulk-manual/apply` | По требованию (ad-hoc) |

**Отличия MANUAL_BULK от MANUAL:**

| Аспект | MANUAL | MANUAL_BULK |
|--------|--------|-------------|
| Scope | Все offers в connection (через policies) | Конкретный список offer_ids |
| Strategy | Policy strategy (TARGET_MARGIN, etc.) | `MANUAL_OVERRIDE` (user-provided price) |
| Execution mode | Policy execution_mode | Всегда APPROVED (user confirmed) |
| Constraints | Полный pipeline | Полный pipeline |
| Guards | Полный pipeline | Полный pipeline |
| Signal assembly | Полный (ClickHouse queries) | Partial (только cost_price для margin check, current_price для delta) |

**pricing_run DDL extension:**

```sql
pricing_run:
  ...
  trigger_type            VARCHAR(30) NOT NULL   -- POST_SYNC, MANUAL, SCHEDULED, POLICY_CHANGE, MANUAL_BULK
  request_hash            VARCHAR(64)            -- SHA-256 дедупликации для MANUAL_BULK (nullable)
  requested_offers_count  INT                    -- для MANUAL_BULK: сколько offers в запросе (nullable)
  ...
```

### 5. price_decision для MANUAL_BULK

`price_decision` для manual bulk использует existing schema, но с особенностями:

| Поле | Значение для MANUAL_BULK |
|------|--------------------------|
| `strategy_type` | `MANUAL_OVERRIDE` |
| `policy_snapshot` | `null` (нет policy) |
| `price_policy_id` | `null` (нет policy) |
| `policy_version` | `0` |
| `strategy_raw_price` | user-provided target_price (до constraints) |
| `signal_snapshot` | `{ "current_price": ..., "cost_price": ..., "source": "MANUAL_BULK" }` |
| `execution_mode` | `LIVE` |

**Explanation format:**

```
[Решение] CHANGE: 1 500 → 1 570 (+ 4,7%)
[Источник] Ручное массовое изменение (MANUAL_BULK run #123)
[Запрос] Целевая цена: 1 575 ₽
[Ограничения] rounding FLOOR step=10: 1 575 → 1 570
[Guards] Все пройдены
[Режим] APPROVED → action APPROVED
```

Новая секция `[Источник]` — заменяет `[Политика]` и `[Стратегия]` для MANUAL_BULK. Включает run ID для traceability.

### 6. Guard pipeline для MANUAL_BULK

Все guards из standard pipeline применяются. Особенности:

| Guard | Поведение для MANUAL_BULK |
|-------|--------------------------|
| Stale data guard | Применяется. Ручное изменение на основе stale data — опасно |
| Margin guard | Применяется. Constraint `min_margin` clamp'ает цену |
| Frequency guard | **Не применяется.** Manual bulk — осознанное действие, frequency ограничение для автоматики |
| Volatility guard | **Не применяется.** Аналогично frequency |
| Promo guard | Применяется. Товар в активном промо → SKIP |
| Manual lock guard | Применяется. Locked товар → SKIP |
| Stock-out guard | **Не применяется.** Оператор может осознанно поменять цену на out-of-stock товар |

**Обоснование исключений:** frequency и volatility guards защищают от «бота, который слишком часто меняет цены» — для ручного массового действия это не релевантно. Stock-out guard для автоматики предотвращает бессмысленные изменения, но оператор может иметь причину (подготовка к поступлению).

### 7. REST API

#### Bulk manual price — preview

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| POST | `/api/workspaces/{workspaceId}/pricing/bulk-manual/preview` | PRICING_MANAGER, ADMIN, OWNER | Dry-run: constraints + guards. Response: per-offer result + summary |

Request body:

```json
{
  "changes": [
    { "marketplaceOfferId": 12345, "targetPrice": 1575.00 },
    { "marketplaceOfferId": 67890, "targetPrice": 4085.00 }
  ]
}
```

**Limits:** max 500 offers per request. > 500 → 400 Bad Request с сообщением «Максимум 500 товаров за одну операцию. Разбейте на несколько batch-ей.»

**Timeout:** 30s (synchronous, аналогично Impact Preview).

#### Bulk manual price — apply

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| POST | `/api/workspaces/{workspaceId}/pricing/bulk-manual/apply` | PRICING_MANAGER, ADMIN, OWNER | Создаёт pricing_run + decisions + actions. Response: `{ pricingRunId, created, skipped, errors[] }` |

Request body — тот же формат, что и preview.

**Idempotency:** `request_hash` = SHA-256(sorted JSON of changes). Повторный request с тем же hash и pricing_run в non-terminal status → 409 Conflict.

#### Bulk cost update

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| POST | `/api/cost-profiles/bulk-update` | PRICING_MANAGER, ADMIN, OWNER | Массовое обновление себестоимости (SCD2). Response: `{ updated, skipped, errors[] }` |

Request body:

```json
{
  "sellerSkuIds": [1, 2, 3],
  "operation": "FIXED",
  "value": 650.00,
  "validFrom": "2026-04-01"
}
```

`operation` enum: `FIXED` (установить), `INCREASE_PCT` (увеличить на %), `DECREASE_PCT` (уменьшить на %), `MULTIPLY` (умножить на коэффициент).

**Limits:** max 500 SKUs per request.

### 8. Contract Alignment (Frontend ↔ Backend)

**Статус:** РАССОГЛАСОВАН — требуется фикс на фронтенде.

Backend является источником правды (Java records с Jakarta Validation). Frontend types должны быть приведены в соответствие.

#### Request body

| Аспект | Backend (canonical) | Frontend (текущее, НЕВЕРНОЕ) |
|--------|--------------------|-----------------------------|
| Корневое поле | `changes: PriceChange[]` | `items: DraftPriceChange[]` |
| ID оффера | `marketplaceOfferId: Long` | `offerId: number` |
| Новая цена | `targetPrice: BigDecimal` | `newPrice: number` |
| Старая цена | — (не передаётся) | `originalPrice: number` |

**Фикс:** переименовать frontend types и API-вызовы:
- `BulkManualPreviewRequest.items` → `changes`
- `DraftPriceChange.offerId` → `marketplaceOfferId`
- `DraftPriceChange.newPrice` → `targetPrice`
- `originalPrice` — оставить в клиентском `DraftPriceChange` для UI, но не отправлять на сервер

#### Preview response

| Аспект | Backend | Frontend (текущее, НЕВЕРНОЕ) |
|--------|---------|------------------------------|
| Структура | `{ summary: Summary, offers: OfferPreview[] }` | `{ items, totalChange, totalSkip, ... }` (flat) |
| Счётчики | `summary.totalRequested`, `willChange`, `willSkip`, `willBlock` | `totalChange`, `totalSkip` (нет `totalRequested`, `willBlock`) |
| Процент | `summary.avgChangePct`, `maxChangePct` | `avgDeltaPct`, `maxDeltaPct` |
| Маржа | `summary.minMarginAfter` | `minMargin` |
| Offers | `offers[].marketplaceOfferId`, `effectivePrice`, `result`, `constraintsApplied[]` | `items[].offerId`, `targetPrice`, `status` |

**Фикс:** обновить `BulkManualPreviewResponse` на фронте — nested structure `summary` + `offers`.

#### Apply response

| Аспект | Backend | Frontend |
|--------|---------|----------|
| Run ID | `pricingRunId: Long` | Нет |
| Счётчики | `processed`, `skipped`, `errored` | `processed`, `skipped`, `errored` ✅ |
| Ошибки | `errors: String[]` | `errors: string[]` ✅ |

**Фикс:** добавить `pricingRunId` в `BulkActionResponse`.

### 9. Решение по `minMarginPct` для MANUAL_BULK

**Статус:** RESOLVED

`BULK_GUARD_CONFIG` задаёт margin guard = enabled, `minMarginPct = null` → интерпретируется как 0%.

**Выбор: A — оставить null (0%).**

Margin guard при 0% предотвращает отрицательную маржу (продажу в убыток). Manual bulk = осознанное ручное действие, оператор сам отвечает за целевую маржу. Двойная защита: клиентский preview показывает projected margin, серверный constraint `min_margin` может clamp если настроен в policy.

---

## Technical Breakdown (TBD)

**Статус:** частично реализовано (см. AC выше)

### Предусловия

- [ ] Phase C (Pricing) развёрнут: pricing pipeline, constraint resolution, guard pipeline
- [ ] Phase D (Execution) развёрнут: action lifecycle, executor worker
- [ ] Phase E (Seller Operations) grid развёрнут: AG Grid, Bulk Actions Bar, inline editing

### Задачи

| # | Задача | Зависимости | Оценка | Приоритет |
|---|--------|-------------|--------|-----------|
| 1 | Backend: `BulkManualPricingService` — constraint resolution + guards для list of (offerId, targetPrice) | — | M | must |
| 2 | Backend: `POST /api/workspaces/{workspaceId}/pricing/bulk-manual/preview` endpoint | #1 | S | must |
| 3 | Backend: `POST /api/workspaces/{workspaceId}/pricing/bulk-manual/apply` endpoint + pricing_run (MANUAL_BULK) + batch decisions/actions | #1 | L | must |
| 4 | Backend: Explanation builder extension — `[Источник]` секция, MANUAL_OVERRIDE strategy_type | #3 | S | must |
| 5 | Backend: Guard pipeline — skip frequency/volatility/stock-out для MANUAL_BULK context | #1 | S | must |
| 6 | Backend: `POST /api/cost-profiles/bulk-update` endpoint | — | M | must |
| 7 | Liquibase: `pricing_run.trigger_type` — добавить `MANUAL_BULK`, `request_hash`, `requested_offers_count` | — | S | must |
| 8 | Frontend: Draft mode toggle + draftChanges React state | — | M | must |
| 9 | Frontend: Draft banner (counter, avg %, min margin, guards warning) | #8 | M | must |
| 10 | Frontend: Grid diff cells (strikethrough old, yellow bg, projected margin) | #8 | M | must |
| 11 | Frontend: Inline price edit в Draft Mode | #8 | S | must |
| 12 | Frontend: «Показать diff» filter view | #8, #10 | S | should |
| 13 | Frontend: Formula Panel UI (dropdown, формулы, client-side preview) | — | L | must |
| 14 | Frontend: Apply flow — preview API call → confirmation modal → apply API call → cleanup | #2, #3, #9 | M | must |
| 15 | Frontend: Bulk cost update panel UI | #6 | M | must |
| 16 | Frontend: `beforeunload` warning при наличии draft changes | #8 | S | must |
| 17 | Frontend: Per-cell undo (right-click → «Отменить изменение») | #8 | S | should |
| 18 | Unit-тесты: BulkManualPricingService — constraints, guards, MANUAL_OVERRIDE decisions | #1 | L | must |
| 19 | Unit-тесты: Guard pipeline MANUAL_BULK context (frequency/volatility/stock-out skipped) | #5 | M | must |
| 20 | Integration-тесты: end-to-end bulk manual → pricing_run → decisions → actions | #3 | L | must |
| 21 | Integration-тесты: bulk cost update SCD2 correctness | #6 | M | must |

### Порядок реализации

```
Phase 1 — Backend foundation:
  #7 (migration) → #1 (service) → #5 (guards) → #4 (explanation)
                                → #2 (preview API)
                                → #3 (apply API)
  #6 (cost bulk API) — параллельно

Phase 2 — Frontend Draft Mode:
  #8 (draft state) → #10 (diff cells) → #9 (banner) → #11 (inline edit) → #16 (beforeunload)
                                                     → #12 (diff view)
                                                     → #17 (per-cell undo)

Phase 3 — Frontend Bulk Operations:
  #13 (formula panel) → #14 (apply flow)
  #15 (cost panel) — параллельно

Phase 4 — Tests:
  #18, #19, #20, #21 — параллельно с implementation
```

### Риски реализации

| Риск | Вероятность | Impact | Митигация |
|------|-------------|--------|-----------|
| Bulk apply для 500 offers → timeout | LOW | UX degradation | Preview = synchronous (30s timeout). Apply = synchronous, но constraints + guards per-offer — O(N) без ClickHouse queries (signals минимальны для MANUAL_OVERRIDE). 500 offers × ~5ms/offer = ~2.5s. Acceptable |
| Draft state теряется при F5 / закрытии | MED | Потеря work-in-progress | `beforeunload` warning. Draft = 30-60 секунд работы, не часы. Серверный persistence — overengineering для ad-hoc операции |
| Conflicting actions: user A applies bulk, user B тоже | LOW | Двойное изменение | Existing action supersede logic: если active action уже есть → SUPERSEDED. pricing_run idempotency через request_hash |
| Frequency guard disabled → bot-like behavior | LOW | Маркетплейс пессимизирует | Bulk manual — инициирован человеком, не автоматикой. Audit trail (trigger_type = MANUAL_BULK) доказывает. Rate: 500 offers / request × max N requests/day — не bot-like. Мониторинг: alert при >5 MANUAL_BULK runs/day per workspace |

### Definition of Done

- [ ] Код написан и прошёл code review
- [ ] Unit-тесты: BulkManualPricingService, guard context, explanation builder (AssertJ + Mockito)
- [ ] Integration-тесты: end-to-end bulk manual flow (Testcontainers)
- [ ] Архитектурные документы обновлены: pricing.md (trigger_type, API), seller-operations.md (bulk actions, draft mode), pages-operational-grid.md (UI spec)
- [ ] Liquibase миграция создана
- [ ] API endpoints задокументированы
- [ ] Frontend: Draft Mode работает в AG Grid
- [ ] Frontend: Formula Panel + Apply flow + Confirmation modal
- [ ] Frontend: Bulk cost update panel
- [ ] `beforeunload` warning при наличии unsaved draft changes

---

## References

- [Pricing](../modules/pricing.md) — pricing_run, constraints, guards, explanation format
- [Execution](../modules/execution.md) — action lifecycle, supersede logic
- [Seller Operations](../modules/seller-operations.md) — Bulk Actions Bar, grid, inline editing
- [Operational Grid](../frontend/pages-operational-grid.md) — grid UI spec, columns, inline edit, bulk actions
- [ETL Pipeline](../modules/etl-pipeline.md) — cost_profile SCD2 lifecycle
