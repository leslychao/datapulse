# Промо-аналитика — Implementation Plan

## Обзор

Модуль промо-аналитики обеспечивает полный цикл управления промо-акциями маркетплейсов: загрузка кампаний, анализ товаров в акциях, симуляция участия, принятие решений и пост-анализ эффективности.

## Архитектура

```
ETL Sync (PROMO_SYNC event)
 → OzonPromoEventSource / WbPromoEventSource → raw_* tables
 → PromoOzonMaterializationHandler / PromoWbMaterializationHandler
   → dim_promo_campaign + fact_promo_product
 → MartRefreshService → mart_promo_product_analysis

REST API
 → PromoController
   → PromoQueryService (campaigns, products, monitoring, post-analysis)
   → PromoSimulationService (what-if scenarios)
   → PromoDecisionService (accept/reject + history)
```

## ETL Pipeline

### Event Sources
| Source | Marketplace | Raw Table | Описание |
|--------|-----------|-----------|----------|
| `OzonPromoEventSource` | OZON | `raw_ozon_actions` | Список акций |
| `OzonPromoActionProductsEventSource` | OZON | `raw_ozon_action_products` | Товары в акциях |
| `WbPromoEventSource` | WB | `raw_wb_promotions` | Список промо-акций |
| `WbPromoNomenclaturesEventSource` | WB | `raw_wb_promotion_nomenclatures` | Товары в акциях |

### Materialization
- `PromoOzonMaterializationHandler` — JSONB → `dim_promo_campaign` + `fact_promo_product`
- `PromoWbMaterializationHandler` — аналогично для WB

### Mart
`PromoProductAnalysisMartRepository.refresh(accountId)` — UPSERT `mart_promo_product_analysis`.
Обновляется при событиях: `PROMO_SYNC`, `SALES_FACT`, `FACT_FINANCE`.

## REST API — `PromoController`

**Base path:** `/api/accounts/{accountId}/promo`

### Campaigns
| Endpoint | Auth | Описание |
|----------|------|----------|
| `GET /campaigns` | canRead | Пагинированный список кампаний |
| `GET /campaigns/summary` | canRead | Сводка по кампаниям |
| `GET /campaigns/{campaignId}` | canRead | Детали кампании |

### Products in Campaign
| Endpoint | Auth | Описание |
|----------|------|----------|
| `GET /campaigns/{campaignId}/products` | canRead | Товары в кампании |
| `GET /campaigns/{campaignId}/products/summary` | canRead | Сводка по товарам |

### Analytics
| Endpoint | Auth | Описание |
|----------|------|----------|
| `POST /campaigns/{campaignId}/simulate` | canRead | Симуляция участия |
| `GET /campaigns/{campaignId}/monitoring` | canRead | Real-time мониторинг |
| `GET /campaigns/{campaignId}/post-analysis` | canRead | Пост-анализ |

### Decisions
| Endpoint | Auth | Описание |
|----------|------|----------|
| `GET /campaigns/{campaignId}/decision` | canRead | Текущее решение |
| `POST /campaigns/{campaignId}/decision` | canWrite | Сохранение решения |
| `GET /campaigns/{campaignId}/decision/history` | canRead | История решений |

## Компоненты

### 1. PromoQueryService

**Delegates:**
- `PromoCampaignReadJdbcRepository` — campaigns + summary + detail
- `PromoProductAnalysisReadJdbcRepository` — products + products summary
- `PromoEffectReadJdbcRepository` — monitoring + post-analysis

### 2. PromoSimulationService

**`simulate(accountId, campaignId, request)`:**
- What-if анализ: расчёт PnL при участии/неучастии с заданными ценовыми скидками
- `PromoSimulationRequest` → `PromoSimulationResponse`

### 3. PromoDecisionService

**Entities:**
- `PromoDecisionEntity` — текущее решение (PENDING/PARTICIPATE/DECLINE)
- `PromoDecisionProductEntity` — per-product включение/исключение + custom price
- `PromoDecisionHistoryEntity` — аудит изменений

**`saveDecision(accountId, campaignId, request, initiatedBy)`:**
1. Find or create `PromoDecisionEntity`
2. Save previous decision for history
3. Update decision fields (decision, comment, decidedBy, decidedAt)
4. Delete old product decisions
5. Save new product decisions
6. Create history record

**`decisionHistory(accountId, campaignId)`:**
- Ordered by `createdAt DESC`

## Domain Enums

- `PromoCampaignStatus` — статусы кампаний
- `PromoDecisionType` — PENDING, PARTICIPATE, DECLINE
- `PromoRecommendation` — рекомендации по участию
- `PromoStockRisk` — оценка stock risk при участии

## Notification Integration

Промо-связанные типы уведомлений:
- `PROMO_DEADLINE_APPROACHING` — приближается дедлайн акции
- `PROMO_NEGATIVE_MARGIN` — отрицательная маржа при участии
- `PROMO_STOCK_OUT_RISK` — риск out-of-stock в акции

## Ключевые файлы

| Файл | Модуль | Роль |
|------|--------|------|
| `PromoController.java` | application | REST API (162 строки) |
| `PromoQueryService.java` | core | Campaign/product queries |
| `PromoSimulationService.java` | core | What-if simulation |
| `PromoDecisionService.java` | core | Decision CRUD + history |
| `PromoDecisionEntity.java` | core | Decision JPA entity |
| `PromoDecisionHistoryEntity.java` | core | Audit trail entity |
| `PromoDecisionProductEntity.java` | core | Per-product decision entity |
| `PromoOzonMaterializationHandler.java` | etl | Ozon promo materialization |
| `PromoWbMaterializationHandler.java` | etl | WB promo materialization |
| `PromoProductAnalysisMartJdbcRepository.java` | etl | Analysis mart refresh |
