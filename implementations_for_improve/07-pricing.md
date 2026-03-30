# Ценообразование — Implementation Plan

## Обзор

Подсистема ценообразования позволяет: (1) задавать ценовые политики с целевой и минимальной маржой, (2) просматривать текущие цены с наложением себестоимости, (3) применять обновления цен через API маркетплейсов, (4) отслеживать историю изменений.

## Компоненты

### 1. Pricing Policy — `PricingPolicyService`

**Entity — `PricingPolicyEntity`:**
- `accountId`, `sourcePlatform`, `dimProductId`
- `targetMarginPercent` (DECIMAL 5,2) — целевая маржа
- `floorMarginPercent` (DECIMAL 5,2) — минимальная допустимая маржа

**REST — `PricingPolicyController` (`/api/accounts/{accountId}/pricing-policy`):**
| Endpoint | Auth | Описание |
|----------|------|----------|
| `GET /` | canRead | Список политик аккаунта |
| `POST /` | canWrite | Создание (201) |
| `PUT /{policyId}` | canWrite | Обновление |
| `DELETE /{policyId}` | canWrite | Удаление (204) |

**Валидации:**
- `floorMarginPercent ≤ targetMarginPercent` — floor не превышает target
- Уникальность `(accountId, sourcePlatform, dimProductId)` — один policy per product per platform
- При update: exclude current policyId из duplicate check

### 2. Price Control Query — `PriceControlQueryService`

**REST — `PriceControlController` (`/api/accounts/{accountId}/price-control`):**
| Endpoint | Auth | Описание |
|----------|------|----------|
| `GET /` | canRead | Поиск товаров с ценами |
| `POST /apply` | canWrite | Применение новой цены |
| `GET /history` | canRead | История изменений |

**Search (`PriceControlReadJdbcRepository`):**
- JOIN `dim_product` + `custom_expense_entry` (cost_per_unit) + `pricing_policy`
- Server-side фильтрация и пагинация

### 3. Price Update — `PriceUpdateService`

**`applyPrice(accountId, request, initiatedByProfileId)`:**

1. **Валидация товара:**
   - `DimProductRepository.findByIdAndAccountId()` — существование
   - `sourceProductId` не пуст
   - WB: `sourceProductId` должен быть числовым

2. **Adapter resolution:**
   - `List<MarketplacePriceAdapter>` — inject all implementations
   - `adapter.supports(marketplace)` — find matching

3. **Previous price:**
   - `PriceUpdateLogRepository.findFirstByDimProductIdAndStatusOrderByCreatedAtDesc(dimProductId, SUCCESS)`

4. **Create pending log:**
   - `PriceUpdateLogEntity` со статусом `PENDING`

5. **Вызов маркетплейса:**
   - `adapter.updatePrice(accountId, sourceProductId, offerId, newPrice)` → `PriceUpdateResult`

6. **Обработка результата:**
   - `result.success()` → `completeLog(SUCCESS)`
   - `result.pending()` → `scheduleAsyncPoll()` (CompletableFuture)
   - Exception → `completeLog(FAILED, ADAPTER_ERROR, message)`

**Async poll (для WB):**
- WB price update async → poll через 3s, затем ещё через 4s
- `adapter.pollResult(accountId, asyncReference)` → SUCCESS/FAILED/PENDING

**`PriceUpdateLogEntity`:**
| Колонка | Тип | Назначение |
|---------|-----|-----------|
| id | BIGSERIAL | PK |
| account_id | BIGINT | Аккаунт |
| dim_product_id | BIGINT | Товар |
| source_platform | VARCHAR | Маркетплейс |
| source_product_id | VARCHAR | ID на маркетплейсе |
| previous_price | DECIMAL | Предыдущая цена |
| new_price | DECIMAL | Новая цена |
| status | VARCHAR | PENDING/SUCCESS/FAILED |
| error_code | VARCHAR | Код ошибки |
| error_message | VARCHAR | Текст ошибки |
| initiated_by | BIGINT | Profile ID инициатора |
| created_at | TIMESTAMPTZ | Время создания |
| completed_at | TIMESTAMPTZ | Время завершения |

### 4. Marketplace Price Adapters

**Interface — `MarketplacePriceAdapter`:**
- `supports(MarketplaceType)` → boolean
- `updatePrice(accountId, sourceProductId, offerId, newPrice)` → `PriceUpdateResult`
- `pollResult(accountId, asyncReference)` → `PriceUpdateResult`

**Implementations (в datapulse-marketplaces):**
- `OzonPriceCommandAdapter` — `MarketplaceCommandClient.post(OZON, CMD_OZON_UPDATE_PRICES, ...)`
- `WbPriceCommandAdapter` — `MarketplaceCommandClient.post(WB, CMD_WB_UPDATE_PRICES, ...)` + async poll через `CMD_WB_PRICE_UPLOAD_DETAILS`

## Ключевые файлы

| Файл | Модуль | Роль |
|------|--------|------|
| `PricingPolicyController.java` | application | Policy CRUD REST |
| `PriceControlController.java` | application | Price control REST |
| `PricingPolicyService.java` | core | Policy business logic |
| `PriceUpdateService.java` | core | Price update orchestration |
| `PriceControlQueryService.java` | core | Price search delegate |
| `PricingPolicyEntity.java` | core | Policy JPA entity |
| `PriceUpdateLogEntity.java` | core | Update log JPA entity |
| `OzonPriceCommandAdapter.java` | marketplaces | Ozon price API |
| `WbPriceCommandAdapter.java` | marketplaces | WB price API + async |
