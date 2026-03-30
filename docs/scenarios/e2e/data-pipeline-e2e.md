# Data Pipeline E2E Scenarios

## Бизнес-контекст

Полный цикл данных: от подключения маркетплейса до доступности данных в аналитике и операционном grid. Проходит через Integration → ETL → Analytics → Seller Operations.

## Сценарии

### E2E-DP-01: First connection — from creation to data availability

- **Business goal:** Новый маркетплейс подключен, первые данные доступны пользователю.
- **Участвующие модули:** Tenancy (workspace) → Integration → ETL → Analytics → Seller Ops.
- **Основной поток:**
  1. **Integration:** ADMIN creates connection → credentials stored in Vault → async validation → ACTIVE.
  2. **ETL:** First FULL_SYNC triggered automatically → all domains: catalog, prices, stocks, orders, finance.
  3. **ETL:** Raw pages stored in S3 → normalized → canonical UPSERT in PostgreSQL.
  4. **ETL:** Post-sync outbox event: `ETL_SYNC_COMPLETED` (completed_domains: [CATALOG, PRICES, STOCKS, ORDERS, FINANCE]).
  5. **Analytics:** Materialization triggered → facts, dims written to ClickHouse.
  6. **Seller Ops:** Grid now shows catalog items with prices, stocks, and analytics enrichment.
- **Ключевые зависимости:** Vault available. Provider API reachable. Valid credentials. ClickHouse available. All data domains synced successfully.
- **Failure paths:**
  - Credentials invalid → AUTH_FAILED → no sync. User must update credentials.
  - Partial sync failure (some domains fail) → COMPLETED_WITH_ERRORS → partial data. Next sync fills gaps.
  - ClickHouse down → canonical data available but analytics empty. Warning banner in grid.
  - Large catalog (100k+ SKUs) → long sync time → user waits.
- **Почему обязательный:** Onboarding flow. Если не работает, система не может использоваться вообще.

### E2E-DP-02: Incremental sync cycle — daily data freshness

- **Business goal:** Данные обновляются автоматически с заданной периодичностью.
- **Участвующие модули:** Integration (scheduler) → ETL → Analytics → Pricing (trigger) → Seller Ops.
- **Основной поток:**
  1. **Integration:** Scheduler triggers sync per connection per domain (based on configured intervals).
  2. **ETL:** Incremental sync: cursor-based extraction → new/changed data → canonical UPSERT (IS DISTINCT FROM) → outbox events.
  3. **Analytics:** Incremental materialization to ClickHouse.
  4. **Pricing:** `ETL_SYNC_COMPLETED` → pricing worker checks FINANCE ∈ completed_domains → pricing run triggered.
  5. **Seller Ops:** Grid reflects updated data.
- **Ключевые зависимости:** Cursor state correct. Provider API available. Data dependency ordering (catalog before finance).
- **Failure paths:**
  - Provider timeout → retry. Max retries → FAILED job → next scheduled sync attempts again.
  - No-churn: identical data → no downstream effects (efficient).
  - Stale data → stale_data_guard in Pricing blocks decisions → alert.
- **Почему обязательный:** Core operational cycle. Определяет data freshness, которая влияет на pricing accuracy.

### E2E-DP-03: Finance ingestion → P&L computation

- **Business goal:** Финансовые данные маркетплейса превращаются в P&L на уровне SKU.
- **Участвующие модули:** ETL (finance sync) → Analytics (P&L) → Seller Ops (P&L view).
- **Основной поток:**
  1. **ETL:** FINANCE_SYNC → extract finance operations → normalize (sign convention: positive=credit, negative=debit) → canonical_finance_entry (UPSERT) → ETL_SYNC_COMPLETED (FINANCE ∈ completed_domains).
  2. **Analytics:** Materialization → fact_finance (ClickHouse) → P&L computation: revenue, commission, logistics, COGS (SCD2 lookup), advertising (direct + pro-rata) → net_profit.
  3. **Analytics:** Reconciliation residual = |sum(components) - net_payout|. If > threshold → RESIDUAL_ANOMALY alert.
  4. **Seller Ops:** P&L data visible in grid enrichment columns and drill-down.
- **Ключевые зависимости:** Finance API contracts (WB rrd, Ozon operations). COGS available. Sign convention correct. SKU mapping (seller_sku_id).
- **Failure paths:**
  - SKU lookup miss → finance entry saved with seller_sku_id=NULL → P&L incomplete at SKU level.
  - Late-arriving finance entries → P&L for past periods re-computed.
  - Sign convention error → P&L fundamentally wrong. Mitigation: validation checks.
  - High reconciliation residual → alert → manual investigation.
- **Почему обязательный:** P&L — основной business output. Ошибки в P&L → неверные pricing decisions.

### E2E-DP-04: Data replay / full re-sync recovery

- **Business goal:** Восстановление данных после обнаружения ошибки.
- **Участвующие модули:** Integration → ETL → Analytics.
- **Основной поток:**
  1. **Trigger:** Admin detects data issue (missing records, wrong values) → decides to re-sync.
  2. **Integration:** Reset cursor for affected domain.
  3. **ETL:** Full sync → reload all data → canonical UPSERT handles duplicates (IS DISTINCT FROM).
  4. **Analytics:** Full re-materialization → ClickHouse rebuilt from canonical.
  5. **Verification:** Compare record counts, spot-check values.
- **Ключевые зависимости:** Canonical idempotency. Provider API supports full data fetch. ClickHouse re-materialization.
- **Failure paths:**
  - Re-sync introduces new errors (provider changed API) → worse state. Mitigation: compare raw before and after.
  - Long re-sync for large datasets → operational impact (resource contention).
- **Почему обязательный:** Recovery mechanism. Без replay capability ошибки в данных неисправимы.

### E2E-DP-05: Cross-domain data dependency chain

- **Business goal:** Все data domains синхронизированы в правильном порядке для обеспечения referential integrity.
- **Участвующие модули:** ETL (orchestration) → Analytics.
- **Основной поток:**
  1. **ETL:** CATALOG_SYNC → creates/updates seller_sku, canonical_offer.
  2. **ETL:** PRICES_SYNC → updates canonical_price_current (references canonical_offer).
  3. **ETL:** STOCKS_SYNC → updates canonical_stock_current (references canonical_offer).
  4. **ETL:** FINANCE_SYNC → creates canonical_finance_entry (references seller_sku).
  5. **ETL:** PROMO_SYNC → creates canonical_promo_campaign/product (references canonical_offer).
  6. **ETL:** After all domain syncs → single `ETL_SYNC_COMPLETED` outbox event (completed_domains: list of successfully synced domains).
  7. **Analytics:** Materialization respects dependencies: dims before facts.
- **Ключевые зависимости:** ETL event dependency graph. Domain ordering enforced by scheduler.
- **Failure paths:**
  - Catalog sync fails → downstream domains reference non-existent offers/SKUs → nullable FK, soft failure.
  - Domain sync ordering violated (finance before catalog) → SKU lookup misses.
  - Partial success: some domains OK, others failed → inconsistent state until next cycle.
- **Почему обязательный:** Referential integrity chain. Нарушение порядка → cascade of soft failures.
