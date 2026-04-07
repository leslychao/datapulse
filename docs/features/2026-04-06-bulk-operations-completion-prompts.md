# Промты для завершения Bulk Operations & Draft Mode

**Источник:** `docs/features/2026-03-31-bulk-operations-draft-mode.md`
**Дата:** 2026-04-06 (v2 — обновлено с учётом аудита кода)

**Общий контекст:** Phase 1 backend (BulkManualPricingService, REST endpoints, guard config, explanation, audit trail) частично реализован. Frontend draft mode имеет store и toggle, но inline editing, diff cells, formula panel, cost panel — не реализованы. Обнаружены критические баги и рассогласование контракта frontend↔backend.

---

## Промт 1: Фикс контракта Frontend ↔ Backend для Bulk Manual

Контекст фичи: Bulk Operations & Draft Mode (docs/features/2026-03-31-bulk-operations-draft-mode.md), секция "Contract Alignment".

Frontend и backend для bulk manual pricing используют разные имена полей. Запрос с фронта **не сработает** на реальном бэкенде. Нужно привести фронт в соответствие с бэкендом (бэкенд — источник правды).

### Текущее рассогласование

**Request:**
- Frontend: `{ items: [{ offerId, newPrice, originalPrice }] }`
- Backend ожидает: `{ changes: [{ marketplaceOfferId, targetPrice }] }`

**Preview Response:**
- Backend возвращает: `{ summary: { totalRequested, willChange, willSkip, willBlock, avgChangePct, minMarginAfter, maxChangePct }, offers: [{ marketplaceOfferId, skuCode, productName, currentPrice, requestedPrice, effectivePrice, result, constraintsApplied, projectedMarginPct, skipReason, guard }] }`
- Frontend ожидает: `{ items: [...], totalChange, totalSkip, avgDeltaPct, minMargin, maxDeltaPct }` (flat)

**Apply Response:**
- Backend возвращает: `{ pricingRunId, processed, skipped, errored, errors }`
- Frontend ожидает: `{ processed, skipped, errored, errors }` (нет `pricingRunId`)

### Что нужно сделать

**Шаг 1: Обновить frontend types**

Файл: `frontend/src/app/core/models/offer.model.ts`

`DraftPriceChange` — **оставить как есть для клиентского состояния** (offerId, newPrice, originalPrice нужны для UI), но добавить маппинг при отправке на сервер.

Обновить `BulkManualPreviewRequest`:
```typescript
export interface BulkManualPreviewRequest {
  changes: { marketplaceOfferId: number; targetPrice: number }[];
}
```

Обновить `BulkManualPreviewResponse` — nested структура:
```typescript
export interface BulkManualPreviewSummary {
  totalRequested: number;
  willChange: number;
  willSkip: number;
  willBlock: number;
  avgChangePct: number;
  minMarginAfter: number | null;
  maxChangePct: number;
}

export interface BulkManualPreviewOffer {
  marketplaceOfferId: number;
  skuCode: string;
  productName: string;
  currentPrice: number;
  requestedPrice: number;
  effectivePrice: number;
  result: 'CHANGE' | 'SKIP';
  constraintsApplied: { name: string; fromPrice: number; toPrice: number }[];
  projectedMarginPct: number | null;
  skipReason: string | null;
  guard: string | null;
}

export interface BulkManualPreviewResponse {
  summary: BulkManualPreviewSummary;
  offers: BulkManualPreviewOffer[];
}
```

Обновить `BulkActionResponse` — добавить `pricingRunId`:
```typescript
export interface BulkActionResponse {
  pricingRunId: number;
  processed: number;
  skipped: number;
  errored: number;
  errors: string[];
}
```

Удалить старые `BulkManualPreviewItem` (заменён на `BulkManualPreviewOffer`).

**Шаг 2: Обновить API-вызовы**

Файл: `frontend/src/app/core/api/offer-api.service.ts`

`bulkManualPreview` и `bulkManualApply` — убедиться что request body = `BulkManualPreviewRequest` с полем `changes`.

**Шаг 3: Обновить draft-banner.component.ts**

Маппинг `DraftPriceChange[]` → request body:
```typescript
const changes = Array.from(this.gridStore.draftChanges().values())
  .map(d => ({ marketplaceOfferId: d.offerId, targetPrice: d.newPrice }));
```

Передать `{ changes }` вместо `{ items }`.

**Шаг 4: Обновить все места, которые читают preview response**

Заменить `response.items` → `response.offers`, `response.totalChange` → `response.summary.willChange`, и т.д.

### Файлы для изменения:
- `frontend/src/app/core/models/offer.model.ts`
- `frontend/src/app/core/api/offer-api.service.ts`
- `frontend/src/app/features/grid/components/draft-banner.component.ts`

Не трогай бэкенд — он уже корректен. Следуй frontend coding style.

---

## Промт 2: Исправление критических багов в `buildBulkSignals`

Контекст фичи: Bulk Operations & Draft Mode (docs/features/2026-03-31-bulk-operations-draft-mode.md).

В `BulkManualPricingService.buildBulkSignals()` есть три критических бага — сигналы для guard pipeline захардкожены, а не берутся из реальных данных. Из-за этого guard pipeline работает неправильно для bulk-запросов.

### Что сломано

1. **`promoActive = false`** — promo guard всегда pass. По спеке: товар в активном промо → SKIP.
2. **`manualLockActive = false`** — manual lock guard всегда pass. По спеке: товар с ручной блокировкой → SKIP.
3. **`dataFreshnessAt = null`** — StaleDataGuard при `null` возвращает BLOCK("stale_data_unknown"). ВСЕ offers в bulk будут заблокированы stale guard-ом (в тестах цепочка замокана и баг не проявляется).

### Что нужно сделать

**Шаг 1:** В методе `loadOffers()` (или рядом с ним) подгрузить дополнительные данные для сигналов, используя существующие методы `PricingDataReadRepository`:
- `findLockedOfferIds(offerIds)` → Set<Long> заблокированных offer
- `findPromoActiveOfferIds(offerIds)` → Set<Long> offer в активном промо
- `findDataFreshness(connectionId)` → OffsetDateTime последней синхронизации

**Шаг 2:** Передать эти данные в `evaluateOffer()` / `buildAndEvaluateDecision()` и использовать в `buildBulkSignals()` вместо захардкоженных значений.

**Шаг 3:** Обновить `buildBulkSignals`:
```java
private PricingSignalSet buildBulkSignals(EnrichedOfferRow offer, BigDecimal currentPrice,
                                           boolean manualLockActive, boolean promoActive,
                                           OffsetDateTime dataFreshnessAt) {
    return new PricingSignalSet(
            currentPrice, offer.cogs(), offer.status(), null,
            manualLockActive, promoActive,
            null, null, null, null,
            null, null, dataFreshnessAt,
            null, null, null, null,
            null, null, null, null, null);
}
```

**Шаг 4:** Обновить `BulkManualPricingServiceTest` — добавить тесты:
- `should_skip_when_offer_has_manual_lock` — замокать `findLockedOfferIds` чтобы вернуть offer ID, проверить что preview возвращает SKIP с guard = `manual_lock_guard`
- `should_skip_when_offer_in_active_promo` — замокать `findPromoActiveOfferIds`, проверить SKIP с guard = `promo_guard`
- `should_pass_stale_guard_when_data_fresh` — замокать `findDataFreshness` с датой < 24 часов назад, проверить что offer проходит

**Важно:** Не меняй `BULK_GUARD_CONFIG` — frequency, volatility, stock-out должны остаться отключёнными (это по спеке). Меняется только `buildBulkSignals` — он должен передавать реальные данные.

### Файлы для изменения:
- `backend/datapulse-pricing/src/main/java/io/datapulse/pricing/domain/BulkManualPricingService.java`
- `backend/datapulse-pricing/src/test/java/io/datapulse/pricing/domain/BulkManualPricingServiceTest.java`

Не трогай другие файлы. Следуй coding style из правил проекта (Google Java Style, 2 пробела, constructor injection через @RequiredArgsConstructor, AssertJ для assert-ов в тестах).

---

## Промт 3: Bulk Cost Update — добавить формулы операций

Контекст фичи: Bulk Operations & Draft Mode (docs/features/2026-03-31-bulk-operations-draft-mode.md), секция "Bulk Cost Update Panel".

Текущая реализация `POST /api/cost-profiles/bulk-update` принимает только абсолютные значения себестоимости (каждый item содержит `costPrice`). По спеке нужна поддержка формул — одна операция на весь batch.

### Текущее состояние

`BulkUpdateCostProfileRequest` — per-item `costPrice`, без поля `operation`. Нужен отдельный endpoint для формул.

### Что нужно сделать

**Шаг 1:** Создать enum `CostUpdateOperation` в `io.datapulse.etl.domain`:
```java
public enum CostUpdateOperation {
    FIXED,
    INCREASE_PCT,
    DECREASE_PCT,
    MULTIPLY
}
```

**Шаг 2:** Создать request record `BulkFormulaCostRequest`:
```java
public record BulkFormulaCostRequest(
    @NotEmpty @Size(max = 500) List<@NotNull Long> sellerSkuIds,
    @NotNull CostUpdateOperation operation,
    @NotNull @DecimalMin("0.01") BigDecimal value,
    @NotNull LocalDate validFrom
) {}
```

**Шаг 3:** Добавить endpoint в `CostProfileController`:
```
POST /api/cost-profiles/bulk-formula
```
Роли: PRICING_MANAGER, ADMIN, OWNER.

**Шаг 4:** В `CostProfileService` добавить метод `bulkFormula`:
- Загрузить текущие cost_profile для каждого sellerSkuId
- Применить формулу к текущей себестоимости
- Если текущей себестоимости нет и операция не FIXED → пропустить SKU (добавить в skipped)
- Для FIXED — создать новую версию даже если текущей нет
- SCD2: закрыть текущую версию, создать новую

### Файлы:
- Создать: `backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/CostUpdateOperation.java`
- Создать: `backend/datapulse-etl/src/main/java/io/datapulse/etl/api/BulkFormulaCostRequest.java`
- Изменить: `backend/datapulse-etl/src/main/java/io/datapulse/etl/api/CostProfileController.java`
- Изменить: `backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/CostProfileService.java`

Следуй coding style (Google Java Style, 2 пробела).

---

## Промт 4: Frontend — Inline editing цен + Diff cells в Draft Mode

Контекст фичи: Bulk Operations & Draft Mode (docs/features/2026-03-31-bulk-operations-draft-mode.md), секции "Draft Mode" и "Grid в Draft Mode".

**Проблема:** Draft mode имеет toggle, store и banner, но ячейки цен в гриде НЕ редактируемы и нет визуализации изменений. `setDraftPrice()` из `GridStore` нигде не вызывается — draft state никем не наполняется.

### Что нужно сделать

#### 1. Сделать колонку `currentPrice` editable в Draft Mode

В `grid-column-defs.ts` — функция `buildGridColumnDefs` должна принимать callback-и и сигнал `draftMode`:
```typescript
export interface GridColumnCallbacks {
  onLockToggle?: (offerId: number, currentlyLocked: boolean, currentPrice: number | null) => void;
  onDraftPriceChange?: (offerId: number, newPrice: number, originalPrice: number) => void;
  isDraftMode?: () => boolean;
  getDraftChange?: (offerId: number) => DraftPriceChange | undefined;
}
```

Для колонки `currentPrice`:
- `editable`: `true` когда `isDraftMode()` и строка не заблокирована
- `cellEditor`: `'agNumberCellEditor'` с `min: 0.01, precision: 2`
- `onCellValueChanged`: вызывает `onDraftPriceChange(offerId, newValue, oldValue)` → `gridStore.setDraftPrice()`
- `valueSetter`: сохраняет в draft store, НЕ мутирует rowData

#### 2. Diff-визуализация изменённых ячеек

`cellRenderer` для `currentPrice`:
- Если есть draft → зачёркнутая старая цена + новая жирным, жёлтый фон
- Если нет draft → обычное отображение

#### 3. Projected margin колонка

Динамическая колонка `projectedMargin` (видна только в draft mode), вычисляет `(newPrice - costPrice) / newPrice * 100`.

#### 4. Подключить в `grid-page.component.ts`

Передать callback-и, `columnDefs` как `computed()`.

#### 5. Заблокированные строки

Строки с `manualLock = true` или `promoStatus = 'PARTICIPATING'`: non-editable, tooltip с причиной.

### Файлы:
- `frontend/src/app/features/grid/components/grid-column-defs.ts`
- `frontend/src/app/features/grid/grid-page.component.ts`
- `frontend/src/locale/ru.json`

Следуй frontend coding style (OnPush, signals, Tailwind CSS variables, translate pipe).

---

## Промт 5: Frontend — Formula Panel

Контекст фичи: Bulk Operations & Draft Mode (docs/features/2026-03-31-bulk-operations-draft-mode.md), секция "Bulk Formula Panel".

Создать компонент Formula Panel — панель массового изменения цен через формулы.

### Формулы (6 штук):

| Тип | Label | Формула | Параметр |
|-----|-------|---------|----------|
| INCREASE_PCT | Увеличить на % | current × (1 + pct/100) | pct |
| DECREASE_PCT | Уменьшить на % | current × (1 − pct/100) | pct |
| MULTIPLY | Умножить на коэффициент | current × factor | factor |
| FIXED | Установить фиксированную | = fixedPrice | fixedPrice |
| MARKUP_COST | Наценка от себестоимости | costPrice × (1 + pct/100) | pct |
| ROUND | Округлить до шага | round(current, step, direction) | step, direction: FLOOR/NEAREST/CEIL |

### UI: CDK Overlay панель

1. Dropdown «Действие» — выбор формулы
2. Input «Значение»
3. Опция округления (checkbox + step + direction)
4. Секция «Предпросмотр» — client-side computed (count, avg%, min/max цена, мин. маржа)
5. Секция «Заблокированные» — count товаров с manualLock/promo
6. Кнопки «Отмена» / «Применить (N)»

### Логика «Применить»

Для каждого выбранного offer: вычислить newPrice → `gridStore.setDraftPrice(offerId, newPrice, originalPrice)`. Включить draft mode если не включён. Закрыть панель.

### Подключение

В `bulk-actions-bar.component.ts` добавить кнопку «Изменить цену» (иконка `Calculator` из lucide).

### Файлы:
- Создать: `frontend/src/app/features/grid/components/formula-panel.component.ts`
- Изменить: `frontend/src/app/features/grid/components/bulk-actions-bar.component.ts`
- Изменить: `frontend/src/locale/ru.json`

Следуй frontend coding style (standalone, OnPush, signals, Tailwind CSS variables, translate pipe).

---

## Промт 6: Frontend — Apply flow с server-side preview

Контекст фичи: Bulk Operations & Draft Mode (docs/features/2026-03-31-bulk-operations-draft-mode.md), секция "Apply flow".

**Предусловие:** Промт 1 выполнен — контракт frontend↔backend согласован.

Текущее состояние: `draft-banner.component.ts` при нажатии «Применить» сразу вызывает apply без server-side preview. По спеке: preview → confirmation modal → apply.

### Что нужно сделать

1. При нажатии «Применить» → вызвать `offerApi.bulkManualPreview(workspaceId, { changes })` (маппинг `DraftPriceChange[]` → `changes` из промта 1)
2. Loading state на кнопке
3. Показать confirmation modal с данными из `response.summary` (willChange, avgChangePct, minMarginAfter, maxChangePct, willSkip)
4. Count скорректированных constraints: `response.offers.filter(o => o.result === 'CHANGE' && o.effectivePrice !== o.requestedPrice).length`
5. При confirm → `offerApi.bulkManualApply(workspaceId, { changes })`
6. Cleanup: clear draft, toast «N ценовых действий создано», invalidate queries

### Файлы:
- `frontend/src/app/features/grid/components/draft-banner.component.ts`
- `frontend/src/locale/ru.json`

Следуй frontend coding style.

---

## Промт 7: Frontend — Draft доработки: exit confirmation + diff view + per-cell undo + banner stats

Контекст фичи: Bulk Operations & Draft Mode (docs/features/2026-03-31-bulk-operations-draft-mode.md).

### 1. Draft exit confirmation

**Баг:** `toggleDraftMode()` в `grid.store.ts` делает `return` при unsaved changes — модалка `showDraftExitConfirm` никогда не показывается.

Исправить: в `grid-page.component.ts` (или toolbar) перед вызовом `toggleDraftMode()` проверять `hasDraftChanges()` и показывать модалку:
```typescript
onDraftToggle(): void {
  if (this.gridStore.draftMode() && this.gridStore.hasDraftChanges()) {
    this.showDraftExitConfirm.set(true);
  } else {
    this.gridStore.toggleDraftMode();
  }
}
```

### 2. Draft banner — мин. маржа + guards warning

Добавить client-side computed: мин. projected margin из draft changes, count заблокированных guards.

### 3. «Показать diff» кнопка

Фильтр на клиенте — показать только строки с draft changes. Filter pill `[Только изменения ×]`.

### 4. Per-cell undo (правый клик)

AG Grid context menu для ячейки `currentPrice` с draft: использовать i18n ключ `grid.draft.undo_change` (добавить в `ru.json`: `"grid.draft.undo_change": "Отменить изменение"`) → `gridStore.removeDraftPrice(offerId)`. Для AG Grid `getContextMenuItems` используй `TranslateService.instant('grid.draft.undo_change')` как значение `name`.

### Файлы:
- `frontend/src/app/features/grid/components/draft-banner.component.ts`
- `frontend/src/app/features/grid/grid-page.component.ts`
- `frontend/src/app/features/grid/components/grid-column-defs.ts`
- `frontend/src/app/features/grid/components/grid-toolbar.component.ts`
- `frontend/src/app/shared/stores/grid.store.ts`
- `frontend/src/locale/ru.json`

Следуй frontend coding style.

---

## Промт 8: Frontend — Bulk Cost Update Panel

Контекст фичи: Bulk Operations & Draft Mode (docs/features/2026-03-31-bulk-operations-draft-mode.md), секция "Bulk Cost Update Panel".

**Предусловие:** Промт 3 выполнен — бэкенд endpoint `POST /api/cost-profiles/bulk-formula` существует.

### Что нужно создать

CDK Overlay панель с:
1. Radio buttons: FIXED / INCREASE_PCT / DECREASE_PCT / MULTIPLY
2. Input «Значение» (suffix: ₽ / % / × в зависимости от операции)
3. Date picker «Дата начала» (validFrom, default = сегодня)
4. Info-секция: count обновляемых, count без текущей с/с
5. Кнопки «Отмена» / «Обновить (N)»

### API

Добавить в `cost-profile-api.service.ts` метод `bulkFormula(req)`. Типы в `core/models/`.

### Подключение

В `bulk-actions-bar.component.ts` — кнопка «Себестоимость» (иконка `Coins` из lucide).

### Файлы:
- Создать: `frontend/src/app/features/grid/components/cost-update-panel.component.ts`
- Изменить: `frontend/src/app/core/api/cost-profile-api.service.ts`
- Изменить: `frontend/src/app/features/grid/components/bulk-actions-bar.component.ts`
- Изменить: `frontend/src/locale/ru.json`

Следуй frontend coding style (standalone, OnPush, signals, Tailwind CSS variables, translate pipe).

---

## Промт 9: Backend и Frontend тесты

Контекст фичи: Bulk Operations & Draft Mode (docs/features/2026-03-31-bulk-operations-draft-mode.md).

### Backend: Unit-тесты guard pipeline с BULK_GUARD_CONFIG

Создать `BulkGuardConfigTest.java`:
- frequency/volatility/stock-out skipped
- promo blocks when offer in promo
- manual lock blocks when offer locked
- stale data blocks when data too old
- margin guard passes when margin ≥ 0%

Использовать **реальные** guard-классы (не моки) с `PricingGuardChain`.

### Backend: Integration-тест E2E

С Testcontainers (PostgreSQL):
1. Setup: workspace, connection, offers, cost profiles, prices
2. POST preview → verify response structure
3. POST apply → verify `pricing_run` (MANUAL_BULK), `price_decision` (MANUAL_OVERRIDE), `price_action` (APPROVED)
4. Repeat apply → 409 Conflict (idempotency)

### Backend: Integration-тест bulk cost formula SCD2

1. SKU с текущим cost_profile → bulk formula INCREASE_PCT 10% → verify SCD2
2. SKU без cost_profile → FIXED → verify new version created
3. SKU без cost_profile → INCREASE_PCT → verify skipped

### Файлы:
- Создать: `backend/datapulse-pricing/src/test/java/io/datapulse/pricing/domain/guard/BulkGuardConfigTest.java`
- Изменить: `backend/datapulse-pricing/src/test/java/io/datapulse/pricing/domain/BulkManualPricingServiceTest.java`
- Создать: `backend/datapulse-api/src/test/java/io/datapulse/BulkManualPricingIntegrationTest.java`
- Создать: `backend/datapulse-etl/src/test/java/io/datapulse/etl/BulkCostUpdateIntegrationTest.java`

Стиль: AssertJ, `@DisplayName`, `should_<expected>_when_<condition>`.

---

## Порядок выполнения

```
Промт 1 (контракт) ──→ Промт 6 (apply flow)
                   └──→ Промт 4 (diff cells) ──→ Промт 5 (formula panel)
                                              └──→ Промт 7 (draft доработки)

Промт 2 (buildBulkSignals) ── независимо, backend
Промт 3 (bulk cost API) ──→ Промт 8 (cost panel)

Промт 9 (тесты) ── после всех остальных
```

- **Промт 1** — ПЕРВЫЙ, потому что без фикса контракта всё остальное на фронте бессмысленно
- **Промт 2** и **Промт 3** — backend, можно параллельно с фронтом
- **Промт 4** — фундамент для 5, 6, 7 (без inline editing ничего не работает)
- **Промт 9** — после всех остальных
