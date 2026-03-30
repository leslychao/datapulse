# Аналитические модули — Implementation Plan

## Обзор

DataPulse включает несколько аналитических модулей, каждый со своим REST API, query service и JDBC-репозиторием. Все модули следуют единому паттерну: Controller (application) → QueryService (core) → ReadJdbcRepository (core).

## Модули

### 1. Sales Funnel (Воронка продаж)

**REST — `SalesFunnelController` (`/api/accounts/{accountId}/sales-funnel`):**
| Endpoint | Описание |
|----------|----------|
| `GET /summary` | Сводка воронки |
| `GET /dashboard` | Dashboard с KPI, stages, insights, data quality |
| `GET /series` | Временные ряды |
| `GET /drill-down?dimension=` | Drill-down по измерению (пагинация) |

**Dashboard построение (`SalesFunnelQueryService`):**
1. `aggregatePeriod(current)` — агрегат текущего периода
2. `aggregatePeriod(prior)` — агрегат сравнительного периода (если задан)
3. `buildStages(current, prior)` — этапы воронки с conversion rates
4. `buildKpis(current, prior)` — KPI с period-over-period comparison
5. `buildInsights(current, prior)` — автоматические инсайты (пороги, аномалии)
6. `buildDataQualityCodes(current)` — предупреждения о качестве данных

**Домен:**
- `SalesFunnelStageKind` — виды этапов
- `SalesFunnelInsightSeverity` — критичность инсайтов
- `SalesFunnelInsightCode` — коды инсайтов
- `SalesFunnelDrillDownDimension` — измерения для drill-down
- `SalesFunnelDataQualityCode` — коды качества данных

### 2. Ads Analytics (Аналитика рекламы)

**REST — `AdsAnalyticsController` (`/api/accounts/{accountId}/ads-analytics`):**
| Endpoint | Описание |
|----------|----------|
| `GET /` | Дневная статистика |
| `GET /summary` | Сводка |

**Данные:** `fact_advertising_costs` + `dim_product` + allocation в mart.

### 3. Supply Analytics (Аналитика поставок)

**REST — `SupplyAnalyticsController` (`/api/accounts/{accountId}/supply-analytics`):**
| Endpoint | Описание |
|----------|----------|
| `GET /` | Список поставок |
| `GET /summary` | Сводка |

**Данные:** `fact_supply` (из `raw_wb_incomes`).

### 4. Inventory Health (Здоровье остатков)

**REST — `InventoryHealthController` (`/api/accounts/{accountId}/inventory-health`):**
| Endpoint | Описание |
|----------|----------|
| `GET /` | Список товаров с остатками |
| `GET /summary` | Сводка здоровья |

**Связь с алертами:** `LOW_STOCK_ALERT` через `SellerAlertEpisodeService`.

### 5. Inventory Snapshots (Снимки остатков)

**REST — `InventorySnapshotController` (`/api/accounts/{accountId}/inventory-snapshots`):**
| Endpoint | Описание |
|----------|----------|
| `GET /` | Снимки остатков |
| `GET /summary` | Сводка |

**Entity:** `FactInventorySnapshotEntity` — JPA entity в core.

### 6. Commission Audit (Аудит комиссий)

**REST — `CommissionAuditController` (`/api/accounts/{accountId}/commission-audit`):**
| Endpoint | Описание |
|----------|----------|
| `GET /` | Список комиссий |
| `GET /summary` | Сводка аудита |

**Данные:** `fact_commission` + тарифные справочники (`dim_tariff_wb`, `dim_tariff_ozon`).

### 7. Unit Economics (Юнит-экономика)

**REST — `UnitEconomicsController` (`/api/unit-economics`):**
| Endpoint | Описание |
|----------|----------|
| `POST /calculate` | Расчёт юнит-экономики |

**Service:** `UnitEconomicsCalculationService` — калькулятор без привязки к историческим данным. Принимает параметры (`UnitEconomicsRequest`), возвращает расчёт (`UnitEconomicsResponse`).

### 8. Product Content (Контент товаров)

**REST — `ProductContentController` (`/api/accounts/{accountId}/product-content`):**
| Endpoint | Описание |
|----------|----------|
| `GET /` | Список товаров с атрибутами |
| `GET /summary` | Сводка контента |

### 9. Consolidated PnL

**REST — `ConsolidatedPnlController` (`/api/consolidated/order-pnl`):**
| Endpoint | Описание |
|----------|----------|
| `GET /summary` | PnL summary по нескольким accountIds |

**Service:** `ConsolidatedPnlQueryService` — агрегация по массиву `accountIds` с проверкой доступа к каждому.

### 10. CSV Export

**REST — `AnalyticsExportController` (`/api/accounts/{accountId}/exports`):**
| Endpoint | Описание |
|----------|----------|
| `GET /order-pnl` | Стриминг CSV с данными PnL |

**Service:** `CsvExportService` — `StreamingResponseBody` для потоковой записи CSV из `OrderPnlReadJdbcRepository`.

## Общий паттерн реализации

```
Controller (@PreAuthorize + @GetMapping)
 → QueryService (@Validated, @NotNull validations)
   → ReadJdbcRepository (NamedParameterJdbcTemplate, SORTABLE_COLUMNS whitelist)
     → SQL query with filters + pagination
     → RowMapper → Domain DTO
```

**Server-side sort:**
1. `SORTABLE_COLUMNS: Map<String, String>` — DTO field → SQL column
2. `buildOrderByClause(Sort)` — safe clause with fallback
3. Предотвращает SQL injection через whitelist

## Ключевые файлы

| Файл | Модуль | Роль |
|------|--------|------|
| `SalesFunnelController.java` | application | Sales funnel REST |
| `SalesFunnelQueryService.java` | core | Dashboard logic (404 строки) |
| `AdsAnalyticsController.java` | application | Ads REST |
| `SupplyAnalyticsController.java` | application | Supply REST |
| `InventoryHealthController.java` | application | Inventory health REST |
| `CommissionAuditController.java` | application | Commission audit REST |
| `UnitEconomicsController.java` | application | Calculator REST |
| `ConsolidatedPnlController.java` | application | Multi-account PnL |
| `AnalyticsExportController.java` | application | CSV export |
