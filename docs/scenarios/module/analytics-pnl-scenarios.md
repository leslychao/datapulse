# Analytics & P&L — Module Scenarios

## Роль модуля

Analytics & P&L отвечает за вычисление unit economics и P&L, построение star schema в ClickHouse (facts, dimensions, marts), advertising allocation, data quality controls, и inventory intelligence. Является основным read-слоем для Pricing (signals) и Seller Operations (grid enrichment).

## Сценарии

### ANA-01: P&L computation — happy path

- **Назначение:** Расчёт P&L на уровне SKU/day/connection.
- **Trigger:** Materialization после `FINANCE_SYNC_COMPLETED`.
- **Main path:** `fact_finance` (CH) + `dim_product` + `cost_profile` (SCD2 COGS) → compute 13 P&L components: revenue_amount, marketplace_commission, acquiring_commission, logistics_cost, storage_cost, penalties, acceptance_cost, marketing_cost, other_marketplace_charges, advertising_cost (pro-rata allocation), refund_amount, compensation (+), COGS (SCD2) → net_profit. Примечание: `last_mile` — подкатегория `logistics_cost`, не отдельный P&L component. `promo_discount` не существует как P&L component (скидки уже отражены в `revenue_amount`).
- **Dependencies:** Finance entries materialized. COGS available (`cost_profile`). `dim_product` linked.
- **Failure risks:** Missing COGS → P&L incomplete (COGS=0, margin inflated). Missing finance entries → undercount.
- **Uniqueness:** Core business output. 13-компонентная формула — уникальная бизнес-логика.

### ANA-02: Advertising cost allocation

- **Назначение:** Распределение рекламных затрат по SKU.
- **Trigger:** Materialization после `AD_SYNC_COMPLETED`.
- **Main path:** Direct attribution (ad_cost linked to specific SKU via campaign→product mapping) → allocate directly. Unattributed costs → pro-rata allocation по revenue share.
- **Dependencies:** Advertising data synced. Campaign→product mapping. Revenue per SKU computed.
- **Failure risks:** Missing campaign mapping → all costs go to pro-rata (less accurate). Ozon Performance API partial → undercount ad spend.
- **Uniqueness:** Dual allocation strategy (direct + pro-rata) — уникальная business logic.

### ANA-03: COGS lookup (SCD2 point-in-time)

- **Назначение:** Определение COGS для finance entry на дату операции.
- **Trigger:** P&L computation для конкретного entry.
- **Main path:** `entry_date` → lookup `cost_profile` WHERE `valid_from <= entry_date` AND (`valid_to` IS NULL OR `valid_to > entry_date`) → COGS found.
- **Dependencies:** `cost_profile` history (ETL-15). `seller_sku` mapping.
- **Failure risks:** No `cost_profile` for date range → COGS=0 → margin inflated. Backdated entries → may use wrong COGS version.
- **Uniqueness:** Point-in-time lookup — отдельная временная семантика (не current value, а historical).

### ANA-04: Reconciliation residual computation

- **Назначение:** Вычисление разницы между суммой компонентов P&L и фактическим net payout от маркетплейса.
- **Trigger:** P&L materialization.
- **Main path:** sum(all P&L components) vs net_payout (`ppvz_for_pay` WB, `operation.amount` Ozon) → residual = |difference|. Store in mart.
- **Dependencies:** `net_payout` field populated. All P&L components computed.
- **Failure risks:** Systematic residual > threshold → unknown fee or miscategorization. Alert: `RESIDUAL_ANOMALY`.
- **Uniqueness:** Cross-check mechanism — единственный способ обнаружить «скрытые» удержания маркетплейса.

### ANA-05: Residual anomaly detection

- **Назначение:** Обнаружение аномальных residuals, требующих расследования.
- **Trigger:** Scheduled checker (daily).
- **Main path:** Query residual distribution per connection → if mean residual > threshold OR sudden spike → create `alert_event` (`RESIDUAL_ANOMALY`).
- **Dependencies:** Reconciliation residual computed (ANA-04). Alert rules configured.
- **Failure risks:** Threshold too tight → noise (many false alerts). Too loose → real anomalies missed.
- **Uniqueness:** Alert-генерирующий сценарий — другой business outcome (alert, не data).

### ANA-06: Stale data detection

- **Назначение:** Обнаружение устаревших аналитических данных.
- **Trigger:** Scheduled checker (every 30 min).
- **Main path:** Check mart freshness timestamps → if data older than threshold → create `alert_event` (`STALE_DATA`). If `blocks_automation = true` in `alert_rule` → pricing pipeline paused for connection.
- **Dependencies:** Materialization timestamps. Alert rules. Automation blocker integration.
- **Failure risks:** Stale due to CH outage → expected, already handled. Stale due to missed sync → need sync investigation.
- **Uniqueness:** Cross-cutting safety scenario — влияет на Pricing (automation block) и Seller Ops (warning banner).

### ANA-07: Spike detection

- **Назначение:** Обнаружение внезапных скачков в ключевых метриках (revenue, returns, commission).
- **Trigger:** Scheduled checker (daily).
- **Main path:** Compare current period vs rolling average → if deviation > threshold → `alert_event` (`METRIC_SPIKE`).
- **Dependencies:** Historical data sufficient for baseline. Threshold configuration.
- **Failure risks:** Legitimate spikes (sales event, holiday) → false positives.
- **Uniqueness:** Statistical anomaly detection — другая detection logic (deviation vs threshold).

### ANA-08: Full re-materialization (ClickHouse rebuild)

- **Назначение:** Полная перестройка всех ClickHouse tables из canonical.
- **Trigger:** Daily scheduled (night window). Manual trigger. Post-disaster recovery.
- **Main path:** Truncate CH tables (or use ReplacingMergeTree overwrite) → read all canonical data → batch INSERT → verify row counts.
- **Dependencies:** Canonical layer consistent. ClickHouse available. Sufficient time window.
- **Failure risks:** Long runtime → overlap with morning operations. Partial failure → some tables stale.
- **Uniqueness:** Bulk operation — полностью другой scope (всё vs incremental). Recovery/consistency mechanism.

### ANA-09: Inventory intelligence (days of cover, velocity)

- **Назначение:** Расчёт операционных метрик по остаткам.
- **Trigger:** Materialization после `STOCKS_SYNC_COMPLETED`.
- **Main path:** `current_stock / avg_daily_sales` → days_of_cover. Sales trend → velocity. Store in mart.
- **Dependencies:** Stock snapshots. Sales history (orders). Sufficient history for avg computation.
- **Failure risks:** New SKU → insufficient history → velocity = NULL.
- **Uniqueness:** Derived metric — другой computation, другой data dependency (stocks + orders).

### ANA-10: Returns & penalties analysis

- **Назначение:** Аналитика по возвратам и штрафам для понимания unit economics.
- **Trigger:** Materialization после `FINANCE_SYNC_COMPLETED`.
- **Main path:** Filter finance entries by type (RETURN, PENALTY) → aggregate by SKU, period → store in mart. Return rate = returns / sales.
- **Dependencies:** Finance entries with correct `entry_type` classification.
- **Failure risks:** Misclassified `entry_type` → incorrect return rate.
- **Uniqueness:** Specific P&L component analysis — другой slice, другая бизнес-метрика.

### ANA-11: Incomplete dimension linkage

- **Назначение:** Факт (finance, order) не может быть привязан к dimension (product, brand, category).
- **Trigger:** Materialization: fact row references dimension key that doesn't exist in dim table.
- **Main path:** Fact row saved с dim key = `UNKNOWN` placeholder in `dim_product`. Aggregations work but drill-down incomplete.
- **Dependencies:** `dim_product` populated before facts (dependency ordering).
- **Failure risks:** Systematic missing dims → aggregation accuracy questionable.
- **Uniqueness:** Star schema integrity issue — другая persistence semantics (placeholder vs reject).

### ANA-12: Data provenance trace

- **Назначение:** Отследить путь данных от mart → fact → canonical → raw → provider response.
- **Trigger:** User investigation (manual, through API or logs).
- **Main path:** mart row → `fact_finance` (`job_execution_id`) → `job_execution` → `job_items` (`s3_key`) → raw S3 object → original provider response.
- **Dependencies:** `job_execution_id` propagated through pipeline. S3 raw preserved (retention policy).
- **Failure risks:** Raw expired (retention) → trace ends at canonical. `job_execution_id` missing → broken link.
- **Uniqueness:** Observability/audit scenario — не data flow, а investigation flow. Read-only.

### ANA-13: COGS revenue-ratio netting (T-4 invariant)

- **Назначение:** Нетирование COGS пропорционально возвращённой выручке.
- **Trigger:** P&L materialization (mart computation).
- **Main path:** `gross_cogs = fact_sales.quantity × fact_product_cost.cost_price`. `refund_ratio = SUM(refund_amount) / NULLIF(SUM(revenue_amount), 0)`. `net_cogs = gross_cogs × GREATEST(0, 1 − COALESCE(refund_ratio, 0))`. Инвариант: если posting полностью возвращён → refund_ratio = 1 → net_cogs = 0.
- **Dependencies:** fact_sales (quantity). fact_product_cost (SCD2). fact_finance (refund_amount, revenue_amount).
- **Failure risks:** Missing fact_sales → COGS = 0, cogs_status = NO_SALES. Missing cost_profile → COGS = 0, cogs_status = NO_COST_PROFILE.
- **Uniqueness:** Revenue-ratio netting вместо quantity-based (Ozon fact_returns не содержит posting_number для join). Единая формула для обоих маркетплейсов.

### ANA-14: Attribution taxonomy (POSTING / PRODUCT / ACCOUNT)

- **Назначение:** Классификация каждой строки fact_finance по уровню attribution.
- **Trigger:** Materialization canonical_finance_entry → fact_finance.
- **Main path:** Materializer вычисляет: IF posting_id IS NOT NULL OR order_id IS NOT NULL → POSTING. ELIF seller_sku_id IS NOT NULL → PRODUCT. ELSE → ACCOUNT. Exhaustive classification — каждая строка ровно в одной категории.
- **Dependencies:** canonical_finance_entry fields: posting_id, order_id, seller_sku_id.
- **Failure risks:** Новый тип операции МП с неожиданной комбинацией fields → неверная attribution. Mitigation: exhaustive enum, unrecognized → ACCOUNT + log.warn.
- **Uniqueness:** Определяет, в какой mart попадает строка (mart_posting_pnl vs mart_product_pnl).

