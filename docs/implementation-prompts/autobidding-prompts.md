# Autobidding — Implementation Prompts

**Инструкция:** открывай новый Cursor chat для каждого блока (Chat 1, Chat 2, ...).
В начале чата скопируй весь блок целиком (от заголовка `## Chat N` до следующего `## Chat`).
После завершения блока переходи к следующему чату.

**Правила для каждого чата:**
- Agent mode (не Ask mode)
- Перед каждым промптом внутри блока скажи: "Продолжай, промпт N"
- Если что-то сломалось — исправь в этом же чате, не переходи дальше
- После последнего промпта в блоке: проверь что проект компилируется (`mvn compile` backend, `ng build` frontend)

---

## Chat 1 — Maven module + migrations + entities + enums (промпты 1–3)

### Контекст для чата

Реализую модуль автобиддинга (автоматическое управление рекламными ставками на маркетплейсах) для проекта Datapulse.

Ключевые документы для чтения:
- `docs/modules/autobidding.md` — бизнес-спецификация (главный источник правды)
- `docs/provider-api-specs/wb-advertising-bidding-contracts.md` — контракты WB API
- `docs/provider-api-specs/ozon-advertising-bidding-contracts.md` — контракты Ozon API

Существующие аналоги для reference:
- Модуль `datapulse-pricing` — аналогичная архитектура (strategy + registry, guard chain, execution lifecycle)
- `backend/pom.xml` — родительский POM со всеми модулями
- `backend/datapulse-api/src/main/resources/db/changelog/db.changelog-master.yaml` — Liquibase master changelog (последняя миграция: `0030-alert-event-resolved-by.sql`)
- `backend/datapulse-platform/src/main/java/io/datapulse/platform/persistence/BaseEntity.java` — базовая entity

### Промпт 1 — Maven module + Liquibase migrations

Создай новый Maven module `datapulse-bidding` по аналогии с `datapulse-pricing` (`backend/datapulse-pricing/pom.xml`).

1. Создай `backend/datapulse-bidding/pom.xml` с зависимостями: `datapulse-common`, `datapulse-platform`, `datapulse-integration`, spring-boot-starter-data-jpa, spring-boot-starter-web, lombok, mapstruct. Посмотри `datapulse-pricing/pom.xml` для точного формата.

2. Добавь `<module>datapulse-bidding</module>` в `backend/pom.xml` (после datapulse-audit-alerting, перед datapulse-api).

3. Добавь зависимость `datapulse-bidding` в `backend/datapulse-api/pom.xml` (по аналогии с datapulse-pricing).

4. Создай Liquibase migration `backend/datapulse-api/src/main/resources/db/changelog/changes/0031-bidding-tables.sql` с таблицами:

```sql
--liquibase formatted sql

--changeset datapulse:0031-bidding-tables

CREATE TABLE bid_policy (
    id bigserial PRIMARY KEY,
    workspace_id bigint NOT NULL REFERENCES workspace(id),
    name varchar(255) NOT NULL,
    strategy_type varchar(50) NOT NULL,
    execution_mode varchar(30) NOT NULL DEFAULT 'RECOMMENDATION',
    status varchar(30) NOT NULL DEFAULT 'DRAFT',
    config jsonb NOT NULL DEFAULT '{}',
    created_by bigint REFERENCES app_user(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE bid_policy_assignment (
    id bigserial PRIMARY KEY,
    bid_policy_id bigint NOT NULL REFERENCES bid_policy(id) ON DELETE CASCADE,
    workspace_id bigint NOT NULL REFERENCES workspace(id),
    marketplace_offer_id bigint REFERENCES marketplace_offer(id),
    campaign_external_id varchar(100),
    assignment_scope varchar(30) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_bid_assignment_offer
    ON bid_policy_assignment(marketplace_offer_id)
    WHERE marketplace_offer_id IS NOT NULL;

CREATE TABLE bidding_run (
    id bigserial PRIMARY KEY,
    workspace_id bigint NOT NULL REFERENCES workspace(id),
    bid_policy_id bigint NOT NULL REFERENCES bid_policy(id),
    status varchar(30) NOT NULL DEFAULT 'RUNNING',
    total_eligible int NOT NULL DEFAULT 0,
    total_decisions int NOT NULL DEFAULT 0,
    total_bid_up int NOT NULL DEFAULT 0,
    total_bid_down int NOT NULL DEFAULT 0,
    total_hold int NOT NULL DEFAULT 0,
    total_pause int NOT NULL DEFAULT 0,
    started_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz,
    error_message text
);

CREATE TABLE bid_decision (
    id bigserial PRIMARY KEY,
    bidding_run_id bigint NOT NULL REFERENCES bidding_run(id),
    workspace_id bigint NOT NULL REFERENCES workspace(id),
    marketplace_offer_id bigint NOT NULL REFERENCES marketplace_offer(id),
    bid_policy_id bigint NOT NULL REFERENCES bid_policy(id),
    strategy_type varchar(50) NOT NULL,
    decision_type varchar(30) NOT NULL,
    current_bid int,
    target_bid int,
    signal_snapshot jsonb,
    guards_applied jsonb,
    explanation_summary text,
    execution_mode varchar(30) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_bid_decision_run ON bid_decision(bidding_run_id);
CREATE INDEX idx_bid_decision_offer ON bid_decision(workspace_id, marketplace_offer_id, created_at DESC);

CREATE TABLE manual_bid_lock (
    id bigserial PRIMARY KEY,
    workspace_id bigint NOT NULL REFERENCES workspace(id),
    marketplace_offer_id bigint NOT NULL REFERENCES marketplace_offer(id),
    locked_bid int,
    reason varchar(500),
    locked_by bigint REFERENCES app_user(id),
    expires_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_manual_bid_lock_offer
    ON manual_bid_lock(workspace_id, marketplace_offer_id)
    WHERE expires_at IS NULL OR expires_at > now();

--rollback DROP TABLE manual_bid_lock;
--rollback DROP TABLE bid_decision;
--rollback DROP TABLE bidding_run;
--rollback DROP TABLE bid_policy_assignment;
--rollback DROP TABLE bid_policy;
```

5. Добавь include в `db.changelog-master.yaml` после последнего entry.

6. Создай пустую структуру пакетов в `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/`:
   - `api/`
   - `domain/`
   - `domain/guard/`
   - `domain/strategy/`
   - `persistence/`
   - `config/`
   - `scheduling/`

### Промпт 2 — Enums + domain records

В модуле `datapulse-bidding`, пакет `io.datapulse.bidding.domain`, создай enums и records.

Прочитай `docs/modules/autobidding.md` §7–§9 для полного описания.

Enums:

1. `BiddingStrategyType` — ECONOMY_HOLD, MINIMAL_PRESENCE. Зарезервированные (с комментарием): LAUNCH, GROWTH, LIQUIDATION, FULL_AUTO.

2. `BidDecisionType` — BID_UP, BID_DOWN, HOLD, PAUSE, RESUME, SET_MINIMUM, EMERGENCY_CUT.

3. `BidPolicyStatus` — DRAFT, ACTIVE, PAUSED, ARCHIVED.

4. `ExecutionMode` — RECOMMENDATION, SEMI_AUTO, FULL_AUTO.

5. `BiddingRunStatus` — RUNNING, COMPLETED, FAILED, PAUSED.

6. `AssignmentScope` — PRODUCT, CAMPAIGN.

Records (value objects):

7. `BiddingSignalSet` — record с полями:
   - Integer currentBid (копейки)
   - BigDecimal drrPct, cpoPct, roas
   - long impressions, clicks, adOrders
   - BigDecimal adSpend (рубли)
   - BigDecimal marginPct
   - Integer stockDays
   - Integer competitiveBid, leadersBid, minBid (копейки)
   - BidDecisionType previousDecisionType (nullable)
   - Integer daysSinceLastChange (nullable)
   - String campaignStatus

8. `BiddingStrategyResult` — record (BidDecisionType decisionType, Integer targetBid, String explanation).

9. `BiddingGuardResult` — record:
   - boolean allowed
   - String guardName
   - String messageKey
   - Map<String, Object> args
   
   Static factory methods: `allow(String guardName)`, `block(String guardName, String messageKey, Map<String, Object> args)`.

10. `BiddingGuardContext` — record (long marketplaceOfferId, long workspaceId, BiddingSignalSet signals, BidDecisionType proposedDecision, Integer targetBid, Integer currentBid, JsonNode policyConfig).

### Промпт 3 — JPA entities + repositories

В `io.datapulse.bidding.persistence` создай JPA entities. Следуй стилю из `datapulse-pricing/persistence/`.

Каждая entity: наследует `BaseEntity` (`io.datapulse.platform.persistence.BaseEntity`), аннотации `@Getter @Setter` на классе, без `@Data`, без `@Builder`.

Entities:

1. `BidPolicyEntity` — table `bid_policy`:
   - workspaceId (long), name (String), strategyType (String), executionMode (String), status (String), config (String, @Column(columnDefinition = "jsonb")), createdBy (Long, nullable)

2. `BidPolicyAssignmentEntity` — table `bid_policy_assignment`:
   - bidPolicyId (long), workspaceId (long), marketplaceOfferId (Long, nullable), campaignExternalId (String, nullable), assignmentScope (String)

3. `BiddingRunEntity` — table `bidding_run`:
   - workspaceId (long), bidPolicyId (long), status (String), totalEligible (int), totalDecisions (int), totalBidUp (int), totalBidDown (int), totalHold (int), totalPause (int), startedAt (Instant), completedAt (Instant, nullable), errorMessage (String, nullable)

4. `BidDecisionEntity` — table `bid_decision`:
   - biddingRunId (long), workspaceId (long), marketplaceOfferId (long), bidPolicyId (long), strategyType (String), decisionType (String), currentBid (Integer, nullable), targetBid (Integer, nullable), signalSnapshot (String, columnDefinition jsonb), guardsApplied (String, columnDefinition jsonb), explanationSummary (String, nullable), executionMode (String)

5. `ManualBidLockEntity` — table `manual_bid_lock`:
   - workspaceId (long), marketplaceOfferId (long), lockedBid (Integer, nullable), reason (String, nullable), lockedBy (Long, nullable), expiresAt (Instant, nullable)

Spring Data JPA repositories (interface extends JpaRepository):

6. `BidPolicyRepository` — findByWorkspaceIdAndStatus, findByWorkspaceId(long workspaceId, Pageable)
7. `BidPolicyAssignmentRepository` — findByBidPolicyId, findByMarketplaceOfferId, existsByMarketplaceOfferId, deleteByBidPolicyIdAndMarketplaceOfferId
8. `BiddingRunRepository` — findByBidPolicyId(long, Pageable), findByWorkspaceId(long, Pageable)
9. `BidDecisionRepository` — findByBiddingRunId, findFirstByWorkspaceIdAndMarketplaceOfferIdOrderByCreatedAtDesc
10. `ManualBidLockRepository` — findByWorkspaceIdAndMarketplaceOfferId, deleteByWorkspaceIdAndMarketplaceOfferId

**Чеклист после Chat 1:**
- [ ] `backend/datapulse-bidding/pom.xml` создан
- [ ] Модуль добавлен в parent pom и datapulse-api pom
- [ ] Liquibase migration 0031 создана и добавлена в master changelog
- [ ] 6 enum-ов в `domain/`
- [ ] 4 record-а (value objects) в `domain/`
- [ ] 5 JPA entities в `persistence/`
- [ ] 5 JPA repositories в `persistence/`
- [ ] `mvn compile` проходит

---

## Chat 2 — Config + Strategy + Guards (промпты 4–6)

### Контекст для чата

Продолжаю реализацию модуля автобиддинга. Chat 1 создал Maven module `datapulse-bidding`, Liquibase миграции, enums, records и JPA entities/repositories.

Reference:
- `docs/modules/autobidding.md` §7–§9 — стратегии и guards
- `backend/datapulse-pricing/src/main/java/io/datapulse/pricing/domain/strategy/` — аналогичный Strategy+Registry
- `backend/datapulse-pricing/src/main/java/io/datapulse/pricing/domain/guard/` — аналогичный Guard chain
- `backend/datapulse-platform/src/main/java/io/datapulse/platform/outbox/OutboxEventType.java` — текущие event types
- `backend/datapulse-integration/src/main/java/io/datapulse/integration/domain/ratelimit/RateLimitGroup.java` — rate limit groups
- `backend/datapulse-api/src/main/java/io/datapulse/api/config/RabbitTopologyConfig.java` — RabbitMQ topology
- `backend/datapulse-common/src/main/java/io/datapulse/common/error/MessageCodes.java` — message codes

### Промпт 4 — ConfigurationProperties + OutboxEventType + RabbitMQ

1. Создай `BiddingProperties` в `io.datapulse.bidding.config`:
   - `@ConfigurationProperties(prefix = "datapulse.bidding")`
   - `@Validated @Getter @RequiredArgsConstructor` с final полями
   - Поля: int defaultLookbackDays (default 7), int maxBidUpRatioPct (default 50), int minDecisionIntervalHours (default 4), int staleDataThresholdHours (default 48), int lowStockThresholdDays (default 7), boolean enabled (default true)
   - Не забудь `@EnableConfigurationProperties(BiddingProperties.class)` в `@Configuration` классе модуля

2. Добавь в `OutboxEventType` (`backend/datapulse-platform/.../OutboxEventType.java`) три новых значения:
   ```
   BIDDING_RUN_EXECUTE("bidding.run", "bidding.run"),
   BID_ACTION_EXECUTE("bid.execution", "bid.execution"),
   BID_ACTION_RETRY("bid.execution.wait", "bid.execution.wait")
   ```
   Посмотри существующие entries для формата (exchange, routingKey в конструкторе).

3. Добавь RabbitMQ topology в `RabbitTopologyConfig` (`backend/datapulse-api/.../RabbitTopologyConfig.java`):
   - Exchange: `bidding.run` (direct), queue: `bidding.run`, binding key `bidding.run`
   - Exchange: `bid.execution` (direct), queue: `bid.execution`, binding key `bid.execution`
   - Exchange: `bid.execution.wait` (direct), queue: `bid.execution.wait` с DLX → `bid.execution`, TTL 60000ms
   Посмотри как сделано для pricing (PRICE_ACTION_EXECUTE, PRICE_ACTION_RETRY) и сделай аналогично.

4. Добавь properties в `application.yml` (или `application-local.yml`):
   ```yaml
   datapulse:
     bidding:
       default-lookback-days: 7
       max-bid-up-ratio-pct: 50
       min-decision-interval-hours: 4
       stale-data-threshold-hours: 48
       low-stock-threshold-days: 7
       enabled: true
   ```

### Промпт 5 — BiddingStrategy interface + Registry + EconomyHoldStrategy

В `io.datapulse.bidding.domain.strategy` создай Strategy + Registry по паттерну из `datapulse-pricing/domain/strategy/`.

1. `BiddingStrategy` — interface:
   ```java
   BiddingStrategyResult evaluate(BiddingSignalSet signals, JsonNode policyConfig);
   BiddingStrategyType strategyType();
   ```

2. `BiddingStrategyRegistry` — @Service:
   - Constructor injection: `List<BiddingStrategy>`
   - Индексирует в `Map<BiddingStrategyType, BiddingStrategy>`
   - Method: `BiddingStrategy resolve(BiddingStrategyType type)` — throws IllegalArgumentException если не найдена

3. `EconomyHoldStrategy` — @Component, implements BiddingStrategy:
   - strategyType() → ECONOMY_HOLD
   - evaluate() — бизнес-логика по `docs/modules/autobidding.md` §8.1:

   **Параметры из policyConfig (JsonNode):**
   - targetDrrPct (BigDecimal) — целевой ДРР
   - tolerancePct (BigDecimal, default 10) — допустимое отклонение от цели
   - stepUpPct (BigDecimal, default 10) — шаг повышения ставки в %
   - stepDownPct (BigDecimal, default 15) — шаг понижения ставки в %
   - maxBidKopecks (Integer, nullable) — потолок ставки
   - minRoas (BigDecimal, default 1.0) — минимальный ROAS для повышения

   **Логика:**
   - upperBound = targetDrr × (1 + tolerance/100)
   - lowerBound = targetDrr × (1 − tolerance/100)
   - Если signals.drrPct > upperBound → BID_DOWN, targetBid = currentBid × (1 − stepDown/100), но не ниже signals.minBid (если известен)
   - Если signals.drrPct < lowerBound И signals.roas > minRoas → BID_UP, targetBid = currentBid × (1 + stepUp/100), но не выше maxBidKopecks (если задан)
   - Иначе → HOLD, targetBid = currentBid
   - Если currentBid == null → HOLD (нет данных)
   - Если drrPct == null → HOLD (нет данных)
   - targetBid всегда округлять до целого (Math.round), минимум 50 копеек (WB minimum)
   - explanation: "DRR {drrPct}% vs target {targetDrr}%±{tolerance}%. Decision: {type}. Bid: {current} → {target} kopecks"

### Промпт 6 — BiddingGuard interface + chain + 5 MVP guards

В `io.datapulse.bidding.domain.guard` создай Guard chain. Посмотри как сделано в `datapulse-pricing/domain/guard/` для reference.

1. `BiddingGuard` — interface:
   ```java
   BiddingGuardResult evaluate(BiddingGuardContext context);
   String guardName();
   int order();
   ```

2. `BiddingGuardChain` — @Service:
   - Constructor injection: `List<BiddingGuard>`, сортирует по order() при инициализации
   - Method: `BiddingGuardResult evaluate(BiddingGuardContext context)` — выполняет guards последовательно, возвращает первый block или allow

3. Пять guards:

   **ManualBidLockGuard** (order=10):
   - Проверяет ManualBidLockRepository: есть ли активный lock (не expired) для данного offer
   - Block reason: `bidding.guard.manual_lock.blocked`

   **CampaignInactiveGuard** (order=20):
   - Проверяет signals.campaignStatus: WB status должен быть "9" (active), Ozon — "RUNNING"
   - Для MVP: block если campaignStatus == null или != "9" (WB-specific, потом расширим)
   - Block reason: `bidding.guard.campaign_inactive.blocked`

   **StaleAdvertisingDataGuard** (order=30):
   - Inject BiddingProperties
   - Block если daysSinceLastChange из signals > staleDataThresholdHours / 24 (или если данных нет — null check)
   - Фактически: проверяем, были ли свежие данные по рекламе. Если signals.impressions == 0 И signals.clicks == 0 И signals.adOrders == 0 → данные возможно устарели
   - Block reason: `bidding.guard.stale_data.blocked`, args: {hours: staleDataThresholdHours}

   **StockOutGuard** (order=40):
   - Block BID_UP/SET_MINIMUM если signals.stockDays != null И signals.stockDays == 0
   - Только блокирует повышение — HOLD и BID_DOWN разрешены
   - Block reason: `bidding.guard.stock_out.blocked`

   **EconomyGuard** (order=50):
   - Block BID_UP если signals.marginPct != null И signals.marginPct <= 0 (нет маржи)
   - Block reason: `bidding.guard.economy.blocked`

4. Добавь MessageCodes в `backend/datapulse-common/.../MessageCodes.java`:
   ```java
   // --- Bidding guards ---
   public static final String BIDDING_GUARD_MANUAL_LOCK = "bidding.guard.manual_lock.blocked";
   public static final String BIDDING_GUARD_CAMPAIGN_INACTIVE = "bidding.guard.campaign_inactive.blocked";
   public static final String BIDDING_GUARD_STALE_DATA = "bidding.guard.stale_data.blocked";
   public static final String BIDDING_GUARD_STOCK_OUT = "bidding.guard.stock_out.blocked";
   public static final String BIDDING_GUARD_ECONOMY = "bidding.guard.economy.blocked";
   ```

**Чеклист после Chat 2:**
- [ ] BiddingProperties создан + @Configuration с @EnableConfigurationProperties
- [ ] OutboxEventType дополнен 3 значениями
- [ ] RabbitTopologyConfig дополнен bidding exchanges/queues
- [ ] application.yml дополнен datapulse.bidding.*
- [ ] BiddingStrategy interface + BiddingStrategyRegistry
- [ ] EconomyHoldStrategy с полной бизнес-логикой
- [ ] BiddingGuard interface + BiddingGuardChain
- [ ] 5 guards (ManualBidLock, CampaignInactive, StaleData, StockOut, Economy)
- [ ] MessageCodes дополнен bidding keys
- [ ] `mvn compile` проходит

---

## Chat 3 — Signal assembly + pipeline (промпты 7–10)

### Контекст для чата

Продолжаю реализацию модуля автобиддинга.

Готово: Maven module, миграции, enums, records, JPA entities/repos, ConfigProperties, OutboxEventType, RabbitMQ topology, Strategy+Registry (EconomyHold), Guard chain (5 guards), MessageCodes.

Сейчас: signal assembly (сбор метрик для принятия решений) и основной pipeline (BiddingRunService).

Reference:
- `docs/modules/autobidding.md` §7 — описание сигналов
- `backend/datapulse-pricing/src/main/java/io/datapulse/pricing/domain/PricingRunService.java` — аналогичный pipeline
- `backend/datapulse-pricing/src/main/java/io/datapulse/pricing/persistence/` — JDBC repositories для reference
- ClickHouse таблицы: `mart_advertising_product` (ДРР, расходы, клики), `mart_product_pnl` (маржа), `mart_inventory_analysis` (остатки)

### Промпт 7 — BiddingDataReadRepository (PostgreSQL)

Создай `BiddingDataReadRepository` в `io.datapulse.bidding.persistence`:
- @Repository, @RequiredArgsConstructor
- Inject: NamedParameterJdbcTemplate
- SQL в private static final String text blocks

Methods:

1. `Optional<BidPolicyAssignmentRow> findActiveAssignment(long marketplaceOfferId)` — из bid_policy_assignment JOIN bid_policy WHERE bid_policy.status = 'ACTIVE'

2. `Optional<ManualBidLockRow> findActiveLock(long workspaceId, long marketplaceOfferId)` — из manual_bid_lock WHERE (expires_at IS NULL OR expires_at > now())

3. `Optional<BidDecisionRow> findLastDecision(long workspaceId, long marketplaceOfferId)` — из bid_decision ORDER BY created_at DESC LIMIT 1

4. `List<EligibleProductRow> findEligibleProducts(long workspaceId, long bidPolicyId)` — из bid_policy_assignment bpa JOIN marketplace_offer mo ON mo.id = bpa.marketplace_offer_id WHERE bpa.bid_policy_id = :bidPolicyId AND bpa.workspace_id = :workspaceId. Вернуть: marketplaceOfferId, externalId (nmId), connectionId

5. `Optional<CampaignInfoRow> findCampaignInfo(long marketplaceOfferId)` — из canonical_advertising_campaign WHERE marketplace_offer_id = :id ORDER BY updated_at DESC LIMIT 1. Вернуть: campaignExternalId, status, currentBid (если есть поле), marketplaceType

Создай Row records в persistence/:
- `BidPolicyAssignmentRow(long id, long bidPolicyId, String strategyType, String executionMode, String config)`
- `ManualBidLockRow(long id, Integer lockedBid, String reason, Instant expiresAt)`
- `BidDecisionRow(long id, String decisionType, Integer targetBid, Instant createdAt)`
- `EligibleProductRow(long marketplaceOfferId, String externalId, long connectionId)`
- `CampaignInfoRow(String campaignExternalId, String status, Integer currentBid, String marketplaceType)`

RowMapper-ы как лямбды в каждом методе.

### Промпт 8 — BiddingClickHouseReadRepository

Создай `BiddingClickHouseReadRepository` в `io.datapulse.bidding.persistence`:
- @Repository, @RequiredArgsConstructor
- Inject: ClickHouse-specific JdbcTemplate (посмотри как подключается в pricing или analytics module — там должен быть отдельный DataSource/JdbcTemplate с qualifier)
- SQL text blocks

Methods:

1. `Optional<AdvertisingMetricsRow> findAdvertisingMetrics(long workspaceId, long marketplaceOfferId, int lookbackDays)`:
   SQL against ClickHouse (mart_advertising_product or fact_advertising — посмотри реальные таблицы в проекте):
   - SUM/AVG за последние lookbackDays дней
   - Возврат: drrPct (avg), cpoPct (avg), roas (avg), totalSpend (sum), impressions (sum), clicks (sum), adOrders (sum)
   - Если данных нет → Optional.empty()

2. `Optional<MarginMetricsRow> findMarginMetrics(long workspaceId, long marketplaceOfferId, int lookbackDays)`:
   SQL against mart_product_pnl:
   - AVG margin_pct за lookback period
   - Возврат: marginPct

3. `Optional<StockMetricsRow> findStockMetrics(long workspaceId, long marketplaceOfferId)`:
   SQL against mart_inventory_analysis (или canonical_inventory):
   - Текущий days_of_cover (количество дней остатков)
   - Возврат: stockDays

Row records:
- `AdvertisingMetricsRow(BigDecimal drrPct, BigDecimal cpoPct, BigDecimal roas, BigDecimal totalSpend, long impressions, long clicks, long adOrders)`
- `MarginMetricsRow(BigDecimal marginPct)`
- `StockMetricsRow(int stockDays)`

Если ClickHouse таблицы имеют другие названия — посмотри существующие CH repositories в проекте и используй правильные имена.

### Промпт 9 — BiddingSignalCollector

Создай `BiddingSignalCollector` в `io.datapulse.bidding.domain`:
- @Service, @RequiredArgsConstructor, @Slf4j
- Inject: BiddingDataReadRepository, BiddingClickHouseReadRepository, BiddingProperties

Method `BiddingSignalSet collect(long workspaceId, long marketplaceOfferId, int lookbackDays)`:

1. Читает campaign info из PG (currentBid, campaignStatus)
2. Читает advertising metrics из CH (drrPct, cpoPct, roas, spend, impressions, clicks, adOrders)
3. Читает margin metrics из CH (marginPct)
4. Читает stock metrics из CH (stockDays)
5. Читает last decision из PG (previousDecisionType, daysSinceLastChange)
6. Собирает всё в BiddingSignalSet, null-safe:
   - Если campaign info absent → currentBid = null, campaignStatus = null
   - Если advertising metrics absent → все рекламные метрики = null
   - Если margin absent → marginPct = null
   - daysSinceLastChange = если есть last decision → ChronoUnit.DAYS.between(lastDecision.createdAt, Instant.now())

Method `boolean hasMinimumData(BiddingSignalSet signals)`:
- Возвращает true если currentBid != null (остальное — optional, стратегия сама решит)

### Промпт 10 — BiddingRunService (main pipeline)

Создай `BiddingRunService` в `io.datapulse.bidding.domain`:
- @Service, @RequiredArgsConstructor, @Slf4j
- Inject: BiddingStrategyRegistry, BiddingGuardChain, BiddingSignalCollector, BiddingDataReadRepository, BidDecisionRepository, BiddingRunRepository, BidPolicyRepository, BiddingProperties, ObjectMapper (для JsonNode parsing)

Method `@Transactional void executeRun(long workspaceId, long bidPolicyId)`:

1. Load BidPolicyEntity, verify status = "ACTIVE", else log.warn и return
2. Parse config JSON: `objectMapper.readTree(policy.getConfig())`
3. Create BiddingRunEntity (status = RUNNING, startedAt = now)
4. Load eligible products: `readRepo.findEligibleProducts(workspaceId, bidPolicyId)`
5. Resolve strategy: `registry.resolve(BiddingStrategyType.valueOf(policy.getStrategyType()))`

6. For each eligible product:
   a. Collect signals: `signalCollector.collect(workspaceId, product.marketplaceOfferId(), properties.getDefaultLookbackDays())`
   b. Check minimum data: если !hasMinimumData → skip, increment totalHold
   c. Evaluate strategy: `strategy.evaluate(signals, configNode)` → result
   d. Если result.decisionType == HOLD → save decision, increment totalHold, continue
   e. Build guard context, evaluate guard chain
   f. Если guard blocks → save decision с decisionType=HOLD + guard info в guardsApplied, increment totalHold
   g. Если guard allows → save decision с result данными, increment counter по типу (totalBidUp/Down/Pause)

7. Blast radius check: если totalBidUp > totalEligible × maxBidUpRatioPct / 100 → set run.status = "PAUSED", log.warn
8. Else: set run.status = "COMPLETED"
9. Set run.completedAt = now, save run

10. Log: "Bidding run completed: runId={}, policy={}, eligible={}, bidUp={}, bidDown={}, hold={}, status={}"

Error handling: catch Exception → set run.status = "FAILED", run.errorMessage, log.error

Save BidDecisionEntity helper method:
- private void saveDecision(biddingRunId, workspaceId, product, policy, result, guardResult, executionMode)
- Сохраняет signalSnapshot и guardsApplied как JSON strings через objectMapper

**Чеклист после Chat 3:**
- [ ] BiddingDataReadRepository с 5 methods + Row records
- [ ] BiddingClickHouseReadRepository с 3 methods + Row records
- [ ] BiddingSignalCollector — collect() + hasMinimumData()
- [ ] BiddingRunService — executeRun() с полным pipeline
- [ ] `mvn compile` проходит

---

## Chat 4 — Trigger + CRUD + API + i18n (промпты 11–14)

### Контекст для чата

Продолжаю реализацию модуля автобиддинга.

Готово: Maven module, миграции, entities/repos, enums, records, ConfigProperties, OutboxEventType, RabbitMQ, Strategy+Registry (EconomyHold), Guards (5 штук), Signal collector, BiddingRunService (pipeline).

Сейчас: consumer для запуска run, trigger из pricing, scheduler, CRUD сервисы, REST API, i18n.

Reference:
- `backend/datapulse-api/src/main/java/io/datapulse/api/` — consumers, config
- `backend/datapulse-pricing/src/main/java/io/datapulse/pricing/api/` — REST controllers для reference
- `backend/datapulse-pricing/src/main/java/io/datapulse/pricing/scheduling/` — scheduler для reference
- `backend/datapulse-common/src/main/java/io/datapulse/common/error/MessageCodes.java`
- `frontend/src/locale/ru.json` — переводы

### Промпт 11 — BiddingRunConsumer + trigger + scheduler

1. Создай `BiddingRunConsumer` в `backend/datapulse-api` (посмотри где живут другие consumers — скорее всего в пакете `io.datapulse.api` или отдельном sub-package):
   - @Component, @RequiredArgsConstructor, @Slf4j
   - @RabbitListener(queues = "bidding.run")
   - Принимает message payload с workspaceId и bidPolicyId
   - Вызывает biddingRunService.executeRun(workspaceId, bidPolicyId)
   - Error handling: catch + log.error, не rethrow

2. Создай trigger integration: после завершения pricing run, если workspace имеет active bid policies → создать outbox events для bidding. Найди где заканчивается PricingRunService (или PricingRunConsumer) и добавь listener (ApplicationEvent / @EventListener) или прямой вызов. Конкретный подход:
   - Создай `BiddingPostPricingRunListener` (@Component, @EventListener или @TransactionalEventListener)
   - Слушает event завершения pricing run
   - Загружает все active bid policies для workspace
   - Для каждой создаёт outbox event BIDDING_RUN_EXECUTE

3. Создай `BiddingRunScheduler` в `io.datapulse.bidding.scheduling`:
   - @Slf4j, @Component, @RequiredArgsConstructor
   - @Scheduled(cron = "${datapulse.bidding.run-cron:0 0 */6 * * *}") — каждые 6 часов
   - @SchedulerLock(name = "biddingRunScheduler", lockAtMostFor = "PT30M")
   - Загружает все workspaces с active bid policies
   - Для каждой policy → создаёт outbox event BIDDING_RUN_EXECUTE
   - Error handling: try-catch на уровне метода

### Промпт 12 — BidPolicy CRUD services

Создай сервисный слой в `io.datapulse.bidding.domain`:

1. `BidPolicyService` (@Service, @RequiredArgsConstructor, @Slf4j):
   - `@Transactional BidPolicyEntity createPolicy(long workspaceId, String name, String strategyType, String executionMode, String configJson, Long createdBy)` — создаёт с status=DRAFT
   - `@Transactional BidPolicyEntity updatePolicy(long id, String name, String executionMode, String configJson)` — обновляет. Verify status != ARCHIVED
   - `@Transactional void activatePolicy(long id)` — DRAFT/PAUSED → ACTIVE. Throw BadRequestException если ARCHIVED
   - `@Transactional void pausePolicy(long id)` — ACTIVE → PAUSED
   - `@Transactional void archivePolicy(long id)` — any → ARCHIVED
   - `@Transactional(readOnly = true) BidPolicyEntity getPolicy(long id)` — findById, orElseThrow NotFoundException
   - `@Transactional(readOnly = true) Page<BidPolicyEntity> listPolicies(long workspaceId, Pageable pageable)`

2. `BidPolicyAssignmentService` (@Service, @RequiredArgsConstructor):
   - `@Transactional BidPolicyAssignmentEntity assign(long bidPolicyId, long workspaceId, Long marketplaceOfferId, String campaignExternalId, String scope)` — проверяет: если marketplaceOfferId != null и existsByMarketplaceOfferId → throw BadRequestException("bidding.assignment.conflict")
   - `@Transactional void unassign(long assignmentId)` — deleteById
   - `@Transactional List<BidPolicyAssignmentEntity> bulkAssign(long bidPolicyId, long workspaceId, List<Long> marketplaceOfferIds, String scope)` — для каждого: проверка + assign
   - `@Transactional(readOnly = true) Page<BidPolicyAssignmentEntity> listAssignments(long bidPolicyId, Pageable pageable)` — findByBidPolicyId

3. `ManualBidLockService` (@Service, @RequiredArgsConstructor):
   - `@Transactional ManualBidLockEntity createLock(long workspaceId, long marketplaceOfferId, Integer lockedBid, String reason, Long lockedBy, Instant expiresAt)` — upsert: если lock уже есть → update; если нет → create
   - `@Transactional void removeLock(long lockId)` — deleteById
   - `@Transactional(readOnly = true) Optional<ManualBidLockEntity> findActiveLock(long workspaceId, long marketplaceOfferId)`

### Промпт 13 — REST API controllers + DTOs + Mapper

Создай REST API в `io.datapulse.bidding.api`. Посмотри `datapulse-pricing/api/` для стиля.

1. Request/Response DTOs (все — records):

   `CreateBidPolicyRequest(String name, String strategyType, String executionMode, JsonNode config)`
   `UpdateBidPolicyRequest(String name, String executionMode, JsonNode config)`
   `BidPolicySummaryResponse(long id, String name, String strategyType, String executionMode, String status, int assignmentCount, Instant createdAt, Instant updatedAt)`
   `BidPolicyDetailResponse(long id, String name, String strategyType, String executionMode, String status, JsonNode config, int assignmentCount, Long createdBy, Instant createdAt, Instant updatedAt)`
   `CreateAssignmentRequest(Long marketplaceOfferId, String campaignExternalId, String scope)`
   `BulkAssignRequest(List<Long> marketplaceOfferIds, String scope)`
   `AssignmentResponse(long id, long bidPolicyId, Long marketplaceOfferId, String campaignExternalId, String scope, Instant createdAt)`
   `CreateManualBidLockRequest(long marketplaceOfferId, Integer lockedBid, String reason, Instant expiresAt)`
   `ManualBidLockResponse(long id, long marketplaceOfferId, Integer lockedBid, String reason, Long lockedBy, Instant expiresAt, Instant createdAt)`
   `BidDecisionSummaryResponse(long id, long marketplaceOfferId, String strategyType, String decisionType, Integer currentBid, Integer targetBid, String explanationSummary, String executionMode, Instant createdAt)`
   `BidDecisionDetailResponse(long id, long biddingRunId, long marketplaceOfferId, String strategyType, String decisionType, Integer currentBid, Integer targetBid, JsonNode signalSnapshot, JsonNode guardsApplied, String explanationSummary, String executionMode, Instant createdAt)`
   `BidDecisionFilter(Long workspaceId, Long bidPolicyId, Long marketplaceOfferId, String decisionType, LocalDate dateFrom, LocalDate dateTo)`
   `BiddingRunSummaryResponse(long id, long bidPolicyId, String status, int totalEligible, int totalDecisions, int totalBidUp, int totalBidDown, int totalHold, int totalPause, Instant startedAt, Instant completedAt)`
   `TriggerBiddingRunRequest(long bidPolicyId)`

2. `BidPolicyMapper` — MapStruct (@Mapper(componentModel = "spring")):
   - `BidPolicySummaryResponse toSummary(BidPolicyEntity entity, int assignmentCount)`
   - `BidPolicyDetailResponse toDetail(BidPolicyEntity entity, int assignmentCount)`
   - `AssignmentResponse toResponse(BidPolicyAssignmentEntity entity)`
   - `ManualBidLockResponse toResponse(ManualBidLockEntity entity)`
   - `BidDecisionSummaryResponse toSummary(BidDecisionEntity entity)`
   - `BiddingRunSummaryResponse toSummary(BiddingRunEntity entity)`

3. Controllers:

   `BidPolicyController` — @RestController, @RequestMapping(value = "/api/v1/bid-policies", produces = APPLICATION_JSON_VALUE), @RequiredArgsConstructor:
   - GET / → listPolicies(@RequestParam long workspaceId, Pageable) → Page<BidPolicySummaryResponse>
   - POST / → createPolicy(@Valid @RequestBody CreateBidPolicyRequest, @RequestParam long workspaceId) → @ResponseStatus(CREATED) BidPolicyDetailResponse
   - GET /{id} → getPolicy(@PathVariable("id") long id) → BidPolicyDetailResponse
   - PUT /{id} → updatePolicy(@PathVariable("id") long id, @Valid @RequestBody UpdateBidPolicyRequest) → BidPolicyDetailResponse
   - POST /{id}/activate → activatePolicy → void
   - POST /{id}/pause → pausePolicy → void
   - POST /{id}/archive → archivePolicy → void

   `BidPolicyAssignmentController` — /api/v1/bid-policies/{policyId}/assignments:
   - GET / → listAssignments(Pageable) → Page<AssignmentResponse>
   - POST / → assign(@Valid @RequestBody CreateAssignmentRequest) → @ResponseStatus(CREATED) AssignmentResponse
   - POST /bulk → bulkAssign(@Valid @RequestBody BulkAssignRequest) → List<AssignmentResponse>
   - DELETE /{assignmentId} → unassign → @ResponseStatus(NO_CONTENT) void

   `ManualBidLockController` — /api/v1/manual-bid-locks:
   - POST / → createLock(@Valid @RequestBody, @RequestParam long workspaceId) → @ResponseStatus(CREATED) ManualBidLockResponse
   - DELETE /{id} → removeLock → @ResponseStatus(NO_CONTENT) void

   `BidDecisionController` — /api/v1/bid-decisions:
   - GET / → listDecisions(BidDecisionFilter, Pageable) → Page<BidDecisionSummaryResponse>
   - GET /{id} → getDecision(@PathVariable("id") long id) → BidDecisionDetailResponse

   `BiddingRunController` — /api/v1/bidding-runs:
   - GET / → listRuns(@RequestParam long workspaceId, Pageable) → Page<BiddingRunSummaryResponse>
   - POST /trigger → triggerRun(@Valid @RequestBody TriggerBiddingRunRequest, @RequestParam long workspaceId) → void

   Security: @PreAuthorize("@accessService.canRead(...)") на GET, @PreAuthorize("@accessService.canWrite(...)") на POST/PUT/DELETE. Посмотри как сделано в pricing controllers.

### Промпт 14 — MessageCodes + i18n

1. Дополни `MessageCodes.java` (если ещё не добавлены):
   ```java
   // --- Bidding ---
   public static final String BIDDING_POLICY_NOT_FOUND = "bidding.policy.not_found";
   public static final String BIDDING_POLICY_ALREADY_ACTIVE = "bidding.policy.already_active";
   public static final String BIDDING_POLICY_ALREADY_PAUSED = "bidding.policy.already_paused";
   public static final String BIDDING_POLICY_ARCHIVED = "bidding.policy.archived";
   public static final String BIDDING_ASSIGNMENT_CONFLICT = "bidding.assignment.conflict";
   public static final String BIDDING_ASSIGNMENT_NOT_FOUND = "bidding.assignment.not_found";
   public static final String BIDDING_RUN_COMPLETED = "bidding.run.completed";
   public static final String BIDDING_RUN_FAILED = "bidding.run.failed";
   public static final String BIDDING_RUN_PAUSED = "bidding.run.paused";
   ```

2. Добавь переводы в `frontend/src/locale/ru.json`:
   ```json
   "bidding.policy.not_found": "Стратегия автобиддинга не найдена",
   "bidding.policy.already_active": "Стратегия уже активна",
   "bidding.policy.already_paused": "Стратегия уже на паузе",
   "bidding.policy.archived": "Стратегия архивирована",
   "bidding.assignment.conflict": "Товар уже назначен на другую стратегию",
   "bidding.assignment.not_found": "Назначение не найдено",
   "bidding.guard.manual_lock.blocked": "Ставка зафиксирована вручную",
   "bidding.guard.campaign_inactive.blocked": "Рекламная кампания неактивна",
   "bidding.guard.stale_data.blocked": "Данные рекламы устарели (более {{hours}} ч)",
   "bidding.guard.stock_out.blocked": "Товар отсутствует на складе",
   "bidding.guard.economy.blocked": "Маржинальность отрицательная",
   "bidding.run.completed": "Прогон автобиддинга завершён",
   "bidding.run.failed": "Прогон автобиддинга завершился с ошибкой",
   "bidding.run.paused": "Прогон автобиддинга приостановлен (blast radius)",
   "bidding.strategies.title": "Стратегии автобиддинга",
   "bidding.strategies.create": "Создать стратегию",
   "bidding.strategy.economy_hold": "Удержание ДРР",
   "bidding.strategy.minimal_presence": "Минимальное присутствие",
   "bidding.decisions.title": "Журнал решений",
   "bidding.decision.bid_up": "Повысить ставку",
   "bidding.decision.bid_down": "Снизить ставку",
   "bidding.decision.hold": "Удержать",
   "bidding.decision.pause": "Пауза",
   "bidding.decision.resume": "Возобновить",
   "bidding.decision.set_minimum": "Установить минимум",
   "bidding.decision.emergency_cut": "Экстренное снижение",
   "bidding.mode.recommendation": "Рекомендации",
   "bidding.mode.semi_auto": "Полуавтомат",
   "bidding.mode.full_auto": "Полный автомат",
   "bidding.status.draft": "Черновик",
   "bidding.status.active": "Активна",
   "bidding.status.paused": "На паузе",
   "bidding.status.archived": "В архиве",
   "bidding.grid.view_name": "Реклама",
   "bidding.detail_panel.tab": "Автобиддинг",
   "bidding.lock.create": "Зафиксировать ставку",
   "bidding.lock.remove": "Снять фиксацию",
   "bidding.run.trigger": "Запустить вручную"
   ```

**Чеклист после Chat 4:**
- [ ] BiddingRunConsumer обрабатывает сообщения из queue
- [ ] Trigger из pricing run (listener/event)
- [ ] BiddingRunScheduler с @Scheduled + @SchedulerLock
- [ ] BidPolicyService — CRUD + lifecycle (activate/pause/archive)
- [ ] BidPolicyAssignmentService — assign/unassign/bulk
- [ ] ManualBidLockService — lock/unlock
- [ ] 13 request/response DTO records
- [ ] BidPolicyMapper (MapStruct)
- [ ] 5 REST controllers
- [ ] MessageCodes дополнен bidding keys
- [ ] ru.json дополнен ~30 переводами
- [ ] `mvn compile` проходит

---

## Chat 5 — Execution layer (промпты 15–18)

### Контекст для чата

Продолжаю реализацию модуля автобиддинга.

Готово: весь backend pipeline (module, migrations, entities, strategy, guards, signals, BiddingRunService, CRUD services, REST API, consumers, scheduler, i18n).

Сейчас: execution layer — bid_action таблицы, action executor, approval flow, consumers.

Reference:
- `docs/modules/autobidding.md` §10–§11 — execution lifecycle
- `backend/datapulse-execution/` — аналогичный execution module для pricing
- `backend/datapulse-api/` — consumers

### Промпт 15 — Bid action migration + entities

1. Создай Liquibase migration `0032-bid-action-tables.sql`:

```sql
--liquibase formatted sql

--changeset datapulse:0032-bid-action-tables

CREATE TABLE bid_action (
    id bigserial PRIMARY KEY,
    bid_decision_id bigint NOT NULL REFERENCES bid_decision(id),
    workspace_id bigint NOT NULL REFERENCES workspace(id),
    marketplace_offer_id bigint NOT NULL REFERENCES marketplace_offer(id),
    connection_id bigint NOT NULL REFERENCES marketplace_connection(id),
    campaign_external_id varchar(100) NOT NULL,
    nm_id varchar(100),
    marketplace_type varchar(20) NOT NULL,
    target_bid int NOT NULL,
    previous_bid int,
    status varchar(30) NOT NULL DEFAULT 'PENDING_APPROVAL',
    execution_mode varchar(30) NOT NULL,
    approved_at timestamptz,
    scheduled_at timestamptz,
    executed_at timestamptz,
    reconciled_at timestamptz,
    retry_count int NOT NULL DEFAULT 0,
    max_retries int NOT NULL DEFAULT 3,
    error_message text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_bid_action_status ON bid_action(workspace_id, status);
CREATE INDEX idx_bid_action_offer ON bid_action(marketplace_offer_id, created_at DESC);

CREATE TABLE bid_action_attempt (
    id bigserial PRIMARY KEY,
    bid_action_id bigint NOT NULL REFERENCES bid_action(id),
    attempt_number int NOT NULL,
    request_summary jsonb,
    response_summary jsonb,
    reconciliation_read jsonb,
    status varchar(20) NOT NULL,
    error_code varchar(100),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_bid_action_attempt_action ON bid_action_attempt(bid_action_id);

--rollback DROP TABLE bid_action_attempt;
--rollback DROP TABLE bid_action;
```

Добавь в db.changelog-master.yaml.

2. Создай enum `BidActionStatus` в `io.datapulse.bidding.domain`:
   PENDING_APPROVAL, APPROVED, SCHEDULED, EXECUTING, RECONCILIATION_PENDING, SUCCEEDED, FAILED, RETRY_SCHEDULED, EXPIRED, SUPERSEDED, CANCELLED, ON_HOLD

3. Создай enum `AttemptStatus` в `io.datapulse.bidding.domain`:
   SUCCESS, FAILURE, TIMEOUT

4. Создай JPA entities в persistence/:
   - `BidActionEntity` — все поля из DDL
   - `BidActionAttemptEntity` — все поля из DDL

5. Создай JPA repositories:
   - `BidActionRepository` — findByWorkspaceIdAndStatus(long, String, Pageable), findByMarketplaceOfferIdAndStatusIn(long, List<String>)
   - `BidActionAttemptRepository` — findByBidActionIdOrderByAttemptNumberDesc

### Промпт 16 — BiddingActionScheduler + approval flow

1. Создай `BiddingActionScheduler` в `io.datapulse.bidding.domain`:
   - @Service, @RequiredArgsConstructor, @Slf4j
   - Inject: BidDecisionRepository, BidActionRepository, OutboxEventRepository (или OutboxService — посмотри как pricing creates outbox events)

   Method `@Transactional void scheduleActions(long biddingRunId)`:
   a. Load BidDecisionEntities for run where decisionType IN ('BID_UP', 'BID_DOWN', 'SET_MINIMUM', 'EMERGENCY_CUT')
   b. Для каждого decision:
      - Supersede previous pending/scheduled actions для того же offer: find by marketplaceOfferId and status IN (PENDING_APPROVAL, APPROVED, SCHEDULED, ON_HOLD) → set status = SUPERSEDED
      - Create BidActionEntity:
        - Если executionMode = RECOMMENDATION → status = ON_HOLD
        - Если executionMode = SEMI_AUTO → status = PENDING_APPROVAL
        - Если executionMode = FULL_AUTO → status = APPROVED, создай outbox event BID_ACTION_EXECUTE
      - Заполни connection_id, campaign_external_id, nm_id из eligible product data (join from decision → assignment → campaign info)
   c. Log summary

2. Вызови scheduleActions() из BiddingRunService — после завершения run (status = COMPLETED), вызвать `actionScheduler.scheduleActions(run.getId())`.

3. Создай `BidActionApprovalService` в `io.datapulse.bidding.domain`:
   - `@Transactional void approve(long actionId)` — PENDING_APPROVAL → APPROVED, set approvedAt, create outbox event BID_ACTION_EXECUTE
   - `@Transactional void reject(long actionId)` — PENDING_APPROVAL → CANCELLED
   - `@Transactional void bulkApprove(List<Long> actionIds)` — для каждого: approve
   - `@Transactional void bulkReject(List<Long> actionIds)` — для каждого: reject

4. Добавь endpoints в новый `BidActionController` (в api/):
   - GET /api/v1/bid-actions/pending?workspaceId={id} → Page<BidActionSummaryResponse>
   - POST /api/v1/bid-actions/{id}/approve → void
   - POST /api/v1/bid-actions/{id}/reject → void
   - POST /api/v1/bid-actions/bulk-approve → void (body: List<Long> actionIds)
   - POST /api/v1/bid-actions/bulk-reject → void (body: List<Long> actionIds)
   DTOs: `BidActionSummaryResponse(long id, long marketplaceOfferId, String marketplaceType, String decisionType, Integer previousBid, Integer targetBid, String status, String executionMode, Instant createdAt)`

### Промпт 17 — BidActionGateway interface + BidActionExecutor

1. Создай `BidActionGateway` interface в `io.datapulse.bidding.domain`:
   ```java
   BidActionGatewayResult execute(BidActionEntity action, String apiToken);
   BidActionGatewayResult reconcile(BidActionEntity action, String apiToken);
   String marketplaceType();
   ```

2. `BidActionGatewayResult` record:
   - boolean success
   - Integer appliedBid (nullable — bid value confirmed by marketplace)
   - String errorCode (nullable)
   - String errorMessage (nullable)
   - String rawResponse (nullable)

3. `BidActionGatewayRegistry` — @Service:
   - Constructor injection: `List<BidActionGateway>`
   - Map<String, BidActionGateway> по marketplaceType
   - Method: `BidActionGateway resolve(String marketplaceType)`

4. `BidActionExecutor` — @Service, @RequiredArgsConstructor, @Slf4j:
   - Inject: BidActionRepository, BidActionAttemptRepository, BidActionGatewayRegistry, OutboxService (для retry), credential resolver (посмотри как pricing resolves API tokens for marketplace)

   Method `@Transactional void execute(long bidActionId)`:
   a. Load BidActionEntity, verify status IN (APPROVED, SCHEDULED, RETRY_SCHEDULED)
   b. CAS: set status = EXECUTING, save
   c. Resolve API token (from connection credentials)
   d. Resolve gateway by marketplaceType
   e. Call gateway.execute(action, token)
   f. Create BidActionAttemptEntity: attempt_number = retry_count + 1, request/response summary, status
   g. On success: CAS → RECONCILIATION_PENDING, set executedAt. (Reconciliation: для MVP — сразу SUCCEEDED, reconciliation добавим позже)
   h. On failure:
      - if retryCount < maxRetries → CAS → RETRY_SCHEDULED, increment retryCount, create outbox BID_ACTION_RETRY
      - else → CAS → FAILED, set errorMessage

### Промпт 18 — Consumers for bid action execution

1. `BidActionExecuteConsumer` в datapulse-api:
   - @Component, @RequiredArgsConstructor, @Slf4j
   - @RabbitListener(queues = "bid.execution")
   - Payload: bidActionId (long)
   - Calls bidActionExecutor.execute(bidActionId)
   - Error handling: catch + log.error

2. `BidActionRetryConsumer` в datapulse-api:
   - @Component, @RequiredArgsConstructor, @Slf4j
   - @RabbitListener(queues = "bid.execution.wait") — DLX delayed queue
   - Payload: bidActionId
   - Calls bidActionExecutor.execute(bidActionId)

**Чеклист после Chat 5:**
- [ ] Liquibase migration 0032 (bid_action + bid_action_attempt)
- [ ] BidActionStatus + AttemptStatus enums
- [ ] BidActionEntity + BidActionAttemptEntity + repositories
- [ ] BiddingActionScheduler — создаёт actions из decisions
- [ ] BidActionApprovalService — approve/reject/bulk
- [ ] BidActionController + DTOs
- [ ] BidActionGateway interface + GatewayResult + Registry
- [ ] BidActionExecutor — execute flow с retry
- [ ] BidActionExecuteConsumer + BidActionRetryConsumer
- [ ] `mvn compile` проходит

---

## Chat 6 — WB/Ozon adapters (промпты 19–23)

### Контекст для чата

Продолжаю реализацию модуля автобиддинга.

Готово: весь backend pipeline + execution layer (actions, executor, consumers, approval flow).

Сейчас: реализация marketplace-specific adapters для WB и Ozon.

Reference:
- `docs/provider-api-specs/wb-advertising-bidding-contracts.md` — WB API контракты
- `docs/provider-api-specs/ozon-advertising-bidding-contracts.md` — Ozon API контракты
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/wb/WbAdvertisingReadAdapter.java` — WB read adapter для reference
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/ozon/OzonAdvertisingReadAdapter.java` — Ozon read adapter
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/ozon/OzonPerformanceTokenService.java` — Ozon OAuth2 token management
- `backend/datapulse-integration/src/main/java/io/datapulse/integration/domain/ratelimit/RateLimitGroup.java` — rate limits

### Промпт 19 — WB Bid Command Adapter (write)

В `io.datapulse.bidding.adapter.wb` создай WB bid write adapter:

1. Добавь в `RateLimitGroup`:
   ```java
   WB_ADVERT_BIDS(5.0, 1, Duration.ofSeconds(1))
   ```
   Посмотри формат существующих entries.

2. `WbBidCommandAdapter` — @Service, @RequiredArgsConstructor, @Slf4j, implements BidActionGateway:
   - Inject: WebClient (или MarketplaceRateLimiter — посмотри как WbAdvertisingReadAdapter делает HTTP вызовы)

   execute(action, token):
   - PATCH https://advert-api.wildberries.ru/adv/v1/save-bids
   - Header: Authorization: {token}
   - Content-Type: application/json
   - Body по контракту из wb-advertising-bidding-contracts.md §2.1:
     ```json
     [
       {
         "advertId": <campaignExternalId as long>,
         "type": 6,
         "cpm": <targetBid>,
         "param": <nmId as int>,
         "instrument": 4
       }
     ]
     ```
   - Bid unit: canonical (копейки) = WB (копейки). No conversion.
   - Success: 200 с пустым телом → return success
   - Errors: 400, 422, 429, 500 → handle appropriately

   reconcile(action, token):
   - Для MVP: return success с appliedBid = targetBid (skip reconciliation read-back)
   - TODO: implement read-back through /adv/v0/params endpoint

   marketplaceType(): "WB"

3. DTOs в `adapter/wb/dto/`:
   - `WbSaveBidsRequestItem(long advertId, int type, int cpm, int param, int instrument)`

### Промпт 20 — WB Bid Read Adapter (recommendations)

В `io.datapulse.bidding.adapter.wb`:

1. Добавь в RateLimitGroup (если ещё нет):
   ```
   WB_ADVERT_BIDS_RECOMMENDED(5.0, 1, Duration.ofMinutes(1))
   ```

2. `WbBidReadAdapter` — @Service, @RequiredArgsConstructor, @Slf4j:

   Method `Optional<WbBidRecommendationResponse> getRecommendedBids(long campaignId, String token)`:
   - GET https://advert-api.wildberries.ru/adv/v0/params?id={campaignId}
   - Из ответа извлечь nms[].bids.competitiveBid и nms[].bids.leadersBid
   - Rate limit: WB_ADVERT_BIDS_RECOMMENDED

3. DTOs:
   - `WbCampaignParamsResponse` — @JsonIgnoreProperties(ignoreUnknown = true)
   - Вложенные: nms list → каждый nm имеет bids object → competitiveBid, leadersBid, topBid (копейки)

### Промпт 21 — Ozon Bid Command Adapter (write)

В `io.datapulse.bidding.adapter.ozon`:

1. Добавь в RateLimitGroup:
   ```
   OZON_PERFORMANCE_BIDS(1.0, 1, Duration.ofSeconds(1))
   ```

2. `OzonBidCommandAdapter` — @Service, @RequiredArgsConstructor, @Slf4j, implements BidActionGateway:
   - Inject: WebClient, OzonPerformanceTokenService (из datapulse-etl — добавь зависимость если нужно, или создай interface в domain)

   execute(action, token):
   - POST https://api-performance.ozon.ru/api/client/campaign/products/set-bids
   - Header: Authorization: Bearer {performanceAccessToken}
   - Body по контракту из ozon-advertising-bidding-contracts.md §2.1:
     ```json
     {
       "sku_bids": [
         { "sku": <nmId as long>, "bid": <targetBidInRubles as string> }
       ]
     }
     ```
   - **Bid unit conversion:** canonical (копейки) → Ozon (рубли): `BigDecimal.valueOf(action.getTargetBid()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).toPlainString()`
   - OAuth2: get token from OzonPerformanceTokenService. Evict on 401.
   - Success: 200 → return success
   - Errors: 400, 401 (token refresh), 429, 500

   reconcile(action, token): MVP — return success (skip read-back)

   marketplaceType(): "OZON"

3. DTOs: `OzonSetBidsRequest`, `OzonSetBidsSkuBid(long sku, String bid)`

### Промпт 22 — Ozon Bid Read Adapter (recommendations)

В `io.datapulse.bidding.adapter.ozon`:

1. `OzonBidReadAdapter` — @Service, @RequiredArgsConstructor, @Slf4j:

   Method `Optional<OzonRecommendedBidsResponse> getRecommendedBids(List<Long> skuIds, String accessToken)`:
   - POST https://api-performance.ozon.ru/api/client/campaign/products/recommended-bids
   - Body: `{ "sku_list": [<skuIds>] }`
   - Response: per-SKU recommended bid in rubles → convert to kopecks for canonical
   - Rate limit: OZON_PERFORMANCE_BIDS

2. DTOs: `OzonRecommendedBidsRequest`, `OzonRecommendedBidsResponse` — @JsonIgnoreProperties(ignoreUnknown = true)

### Промпт 23 — SimulatedBidActionGateway

Создай `SimulatedBidActionGateway` в `io.datapulse.bidding.adapter`:
- @Slf4j, @Component
- Implements BidActionGateway
- execute(): log.info("Simulated bid change: offerId={}, targetBid={}", ...), return success с appliedBid = targetBid
- reconcile(): return success
- marketplaceType(): "SIMULATED"

В `BidActionExecutor` добавь логику: если action.executionMode = "RECOMMENDATION" → использовать SimulatedBidActionGateway вместо real gateway. Либо в BidActionGatewayRegistry добавь fallback logic.

**Чеклист после Chat 6:**
- [ ] RateLimitGroup дополнен: WB_ADVERT_BIDS, WB_ADVERT_BIDS_RECOMMENDED, OZON_PERFORMANCE_BIDS
- [ ] WbBidCommandAdapter — PATCH save-bids
- [ ] WbBidReadAdapter — GET campaign params (recommendations)
- [ ] OzonBidCommandAdapter — POST set-bids с bid conversion kopecks→rubles
- [ ] OzonBidReadAdapter — POST recommended-bids
- [ ] SimulatedBidActionGateway для RECOMMENDATION mode
- [ ] DTOs для всех adapters
- [ ] `mvn compile` проходит

---

## Chat 7 — Frontend: models + routes + pages (промпты 24–28)

### Контекст для чата

Продолжаю реализацию модуля автобиддинга. Весь backend готов.

Сейчас: frontend — TypeScript models, API service, routes, основные pages.

Reference:
- `frontend/src/app/core/models/pricing.model.ts` — аналогичные models для reference
- `frontend/src/app/core/api/pricing-api.service.ts` — аналогичный API service
- `frontend/src/app/features/pricing/` — аналогичные страницы для reference
- `frontend/src/app/app.routes.ts` — root routes
- `frontend/src/app/shared/shell/activity-bar/activity-bar.component.ts` — navigation
- `frontend/src/locale/ru.json` — translations (bidding keys уже добавлены в Chat 4)

### Промпт 24 — TypeScript models + API service

1. Создай `frontend/src/app/core/models/bidding.model.ts`:
   ```typescript
   export type BiddingStrategyType = 'ECONOMY_HOLD' | 'MINIMAL_PRESENCE';
   export type BidDecisionType = 'BID_UP' | 'BID_DOWN' | 'HOLD' | 'PAUSE' | 'RESUME' | 'SET_MINIMUM' | 'EMERGENCY_CUT';
   export type BidPolicyStatus = 'DRAFT' | 'ACTIVE' | 'PAUSED' | 'ARCHIVED';
   export type ExecutionMode = 'RECOMMENDATION' | 'SEMI_AUTO' | 'FULL_AUTO';
   export type BidActionStatus = 'PENDING_APPROVAL' | 'APPROVED' | 'SCHEDULED' | 'EXECUTING' | 'RECONCILIATION_PENDING' | 'SUCCEEDED' | 'FAILED' | 'RETRY_SCHEDULED' | 'EXPIRED' | 'SUPERSEDED' | 'CANCELLED' | 'ON_HOLD';
   export type BiddingRunStatus = 'RUNNING' | 'COMPLETED' | 'FAILED' | 'PAUSED';

   export interface BidPolicySummary { id: number; name: string; strategyType: string; executionMode: string; status: string; assignmentCount: number; createdAt: string; updatedAt: string; }
   export interface BidPolicyDetail extends BidPolicySummary { config: Record<string, unknown>; createdBy: number | null; }
   export interface CreateBidPolicyRequest { name: string; strategyType: string; executionMode: string; config: Record<string, unknown>; }
   export interface UpdateBidPolicyRequest { name: string; executionMode: string; config: Record<string, unknown>; }
   export interface BidPolicyAssignment { id: number; bidPolicyId: number; marketplaceOfferId: number | null; campaignExternalId: string | null; scope: string; createdAt: string; }
   export interface CreateAssignmentRequest { marketplaceOfferId?: number; campaignExternalId?: string; scope: string; }
   export interface BidDecisionSummary { id: number; marketplaceOfferId: number; strategyType: string; decisionType: string; currentBid: number | null; targetBid: number | null; explanationSummary: string | null; executionMode: string; createdAt: string; }
   export interface BidDecisionDetail extends BidDecisionSummary { biddingRunId: number; signalSnapshot: Record<string, unknown> | null; guardsApplied: Record<string, unknown> | null; }
   export interface BiddingRunSummary { id: number; bidPolicyId: number; status: string; totalEligible: number; totalDecisions: number; totalBidUp: number; totalBidDown: number; totalHold: number; totalPause: number; startedAt: string; completedAt: string | null; }
   export interface ManualBidLock { id: number; marketplaceOfferId: number; lockedBid: number | null; reason: string | null; lockedBy: number | null; expiresAt: string | null; createdAt: string; }
   export interface CreateManualBidLockRequest { marketplaceOfferId: number; lockedBid?: number; reason?: string; expiresAt?: string; }
   export interface BidActionSummary { id: number; marketplaceOfferId: number; marketplaceType: string; decisionType: string; previousBid: number | null; targetBid: number; status: string; executionMode: string; createdAt: string; }
   ```

   Добавь re-export в `frontend/src/app/core/models/index.ts`.

2. Создай `frontend/src/app/core/api/bidding-api.service.ts`:
   - @Injectable({ providedIn: 'root' })
   - По паттерну из `pricing-api.service.ts`
   - Methods (все return Observable<T>):
     - listPolicies(workspaceId: number): Observable<Page<BidPolicySummary>>
     - createPolicy(workspaceId: number, req: CreateBidPolicyRequest): Observable<BidPolicyDetail>
     - getPolicy(id: number): Observable<BidPolicyDetail>
     - updatePolicy(id: number, req: UpdateBidPolicyRequest): Observable<BidPolicyDetail>
     - activatePolicy(id: number): Observable<void>
     - pausePolicy(id: number): Observable<void>
     - archivePolicy(id: number): Observable<void>
     - listAssignments(policyId: number, page: number, size: number): Observable<Page<BidPolicyAssignment>>
     - createAssignment(policyId: number, req: CreateAssignmentRequest): Observable<BidPolicyAssignment>
     - bulkAssign(policyId: number, offerIds: number[], scope: string): Observable<BidPolicyAssignment[]>
     - deleteAssignment(policyId: number, assignmentId: number): Observable<void>
     - listDecisions(params: Record<string, unknown>): Observable<Page<BidDecisionSummary>>
     - getDecision(id: number): Observable<BidDecisionDetail>
     - listRuns(workspaceId: number, page: number, size: number): Observable<Page<BiddingRunSummary>>
     - triggerRun(workspaceId: number, bidPolicyId: number): Observable<void>
     - createLock(workspaceId: number, req: CreateManualBidLockRequest): Observable<ManualBidLock>
     - deleteLock(id: number): Observable<void>
     - listPendingActions(workspaceId: number, page: number, size: number): Observable<Page<BidActionSummary>>
     - approveAction(id: number): Observable<void>
     - rejectAction(id: number): Observable<void>
     - bulkApproveActions(ids: number[]): Observable<void>
     - bulkRejectActions(ids: number[]): Observable<void>

### Промпт 25 — Routes + navigation

1. Создай `frontend/src/app/features/bidding/bidding.routes.ts`:
   ```typescript
   const routes: Routes = [
     { path: '', redirectTo: 'strategies', pathMatch: 'full' },
     {
       path: 'strategies',
       loadComponent: () => import('./strategies/bid-strategies-page.component').then(m => m.BidStrategiesPageComponent),
       data: { breadcrumb: 'Стратегии' },
     },
     {
       path: 'strategies/:id',
       loadComponent: () => import('./strategies/bid-strategy-detail-page.component').then(m => m.BidStrategyDetailPageComponent),
       data: { breadcrumb: 'Детали стратегии' },
     },
     {
       path: 'decisions',
       loadComponent: () => import('./decisions/bid-decisions-page.component').then(m => m.BidDecisionsPageComponent),
       data: { breadcrumb: 'Журнал решений' },
     },
   ];
   export default routes;
   ```

2. Добавь route в `app.routes.ts`:
   ```typescript
   {
     path: 'bidding',
     loadChildren: () => import('./features/bidding/bidding.routes'),
     data: { breadcrumb: 'Автобиддинг' },
   }
   ```

3. Добавь пункт навигации в `activity-bar.component.ts`:
   - Иконка: `Zap` или `Bot` из lucide-angular
   - Label: 'Автобиддинг'
   - routerLink: '/bidding'
   - Посмотри как добавлены другие пункты (pricing, analytics, settings) и добавь аналогично

### Промпт 26 — Bid Strategies List page

Создай `frontend/src/app/features/bidding/strategies/bid-strategies-page.component.ts`:
- standalone, OnPush, selector: dp-bid-strategies-page
- Посмотри `frontend/src/app/features/pricing/` для UI patterns и component structure

Содержимое:
- Заголовок "Стратегии автобиддинга" + кнопка "Создать стратегию"
- TanStack Query: injectQuery для listPolicies(workspaceId)
- Таблица (обычный HTML table, НЕ AG Grid): name, strategyType (badge), executionMode (badge), status (badge с цветом), assignmentCount, createdAt
- Status badge colours: DRAFT → gray, ACTIVE → green, PAUSED → yellow, ARCHIVED → gray
- Row click → navigate to strategy detail
- Inline actions dropdown (три точки): "Активировать", "Приостановить", "Архивировать" (mutations с toast)
- Кнопка "Создать" → modal:
  - Поля: name (input), strategyType (select: ECONOMY_HOLD, MINIMAL_PRESENCE), executionMode (select: RECOMMENDATION, SEMI_AUTO, FULL_AUTO)
  - Config section: targetDrrPct, tolerancePct, stepUpPct, stepDownPct, maxBidKopecks, minRoas (все optional с defaults)
  - Submit → createPolicy mutation → refetch list → close modal + toast
- Empty state: "Нет стратегий. Создайте первую стратегию для автоматического управления ставками."
- Loading state: spinner
- Стилизация: Tailwind + CSS variables

### Промпт 27 — Bid Strategy Detail page

Создай `frontend/src/app/features/bidding/strategies/bid-strategy-detail-page.component.ts`:
- standalone, OnPush, selector: dp-bid-strategy-detail-page
- Route param `id` через `input()` (withComponentInputBinding)

Секции:

1. **Header**: name, status badge, action buttons (Activate/Pause/Archive)
2. **Policy info card**: strategyType, executionMode, config params (key-value table)
   - Edit button → modal с UpdateBidPolicyRequest
3. **Assignments table** (TanStack Query: listAssignments):
   - Columns: marketplace_offer_id, scope, createdAt, delete action
   - "Назначить товар" button → simple modal: input marketplaceOfferId + scope select → createAssignment
   - Empty: "Нет назначенных товаров"
4. **Run history** (TanStack Query: listRuns filtered by policyId):
   - Compact table: status badge, totalDecisions, bidUp/bidDown/hold, startedAt, completedAt
   - "Запустить вручную" button → triggerRun mutation
5. **Pending actions** (если executionMode = SEMI_AUTO):
   - TanStack Query: listPendingActions
   - Compact list: product, currentBid → targetBid, approve/reject buttons
   - "Одобрить все" / "Отклонить все" bulk actions

### Промпт 28 — Bid Decisions Journal page

Создай `frontend/src/app/features/bidding/decisions/bid-decisions-page.component.ts`:
- standalone, OnPush, selector: dp-bid-decisions-page

Содержимое:
- Заголовок "Журнал решений"
- Фильтры (signal-based):
  - dateFrom / dateTo (date inputs)
  - decisionType (multi-select: all BidDecisionType values)
  - search by marketplaceOfferId
- TanStack Query: listDecisions с фильтрами как query params
- Table: createdAt, marketplaceOfferId, strategyType badge, decisionType badge (с цветом: BID_UP→green, BID_DOWN→red, HOLD→gray, PAUSE→yellow), currentBid → targetBid, explanationSummary (truncated)
- Click на row → expandable section:
  - Full explanation
  - Signal snapshot: key-value pairs (currentBid, drrPct, marginPct, stockDays, etc.)
  - Guards applied: list of {guardName, allowed/blocked, reason}
- Pagination
- Empty state: "Нет решений за выбранный период"

**Чеклист после Chat 7:**
- [ ] bidding.model.ts с types + interfaces
- [ ] bidding-api.service.ts с ~20 methods
- [ ] bidding.routes.ts с 3 routes (lazy-loaded)
- [ ] Route добавлен в app.routes.ts
- [ ] Пункт навигации в activity-bar
- [ ] BidStrategiesPageComponent — list + create modal
- [ ] BidStrategyDetailPageComponent — detail + assignments + runs + approvals
- [ ] BidDecisionsPageComponent — journal с фильтрами
- [ ] `ng build` проходит

---

## Chat 8 — Frontend: grid + detail panel + shared components (промпты 29–33)

### Контекст для чата

Продолжаю реализацию модуля автобиддинга. Backend полностью готов. Frontend pages (strategies, detail, decisions) сделаны.

Сейчас: интеграция bidding в операционный грид, detail panel товара, shared components.

Reference:
- `frontend/src/app/features/grid/components/grid-column-defs.ts` — определения колонок грида
- `frontend/src/app/features/grid/components/view-tabs.component.ts` — views/tabs
- `frontend/src/app/features/grid/components/offer-detail/` — tabs detail panel
- `frontend/src/app/features/grid/components/bulk-actions-bar.component.ts` — bulk actions

### Промпт 29 — Grid columns for bidding

Расширь операционный грид:

1. В `grid-column-defs.ts` добавь колонки:
   - `bidStatus` — cellRenderer: badge (active strategy name or "—")
   - `currentBid` — число, formatted как рубли (value / 100, append " ₽")
   - `lastBidDecision` — cellRenderer: decisionType badge
   - `bidDrrPct` — число с 1 decimal + "%"

2. В view-tabs добавь новый view "Реклама":
   - Посмотри как определены другие views (Основное, Цены, Промо и т.д.)
   - Колонки: sku, name, bidStatus, currentBid, bidDrrPct, lastBidDecision + существующие advertising колонки

3. Если grid data загружается из backend (offer-api или grid-specific endpoint), нужно будет расширить backend DTO дополнительными полями для bidding. Но это зависит от текущей реализации — посмотри `offer-api.service.ts` и определи, нужно ли расширять backend response или данные берутся из разных sources.

### Промпт 30 — Detail Panel — вкладка "Автобиддинг"

Создай `frontend/src/app/features/grid/components/offer-detail/offer-bidding-tab.component.ts`:
- standalone, OnPush, selector: dp-offer-bidding-tab
- Input: offerId (number, required)

Секции:

1. **Текущая стратегия**: если назначена — name, type badge, mode badge, status badge. Link на detail page.
   Если не назначена — "Стратегия не назначена" + кнопка "Назначить"

2. **Текущая ставка**: значение в рублях (bid / 100), дата последнего изменения

3. **Manual lock**: если есть — alert badge "Ставка зафиксирована", reason, expiry.
   Quick actions: "Зафиксировать ставку" / "Снять фиксацию" buttons

4. **Последнее решение**: type badge, currentBid → targetBid, explanation, timestamp

5. **История решений**: последние 5 решений (compact list: date, type badge, bid change)

TanStack queries для data fetching. Toast для actions.

Добавь tab "Автобиддинг" в `offer-detail-page.component.ts` (или wherever tabs are defined) — посмотри как добавлены другие tabs (Overview, Price Journal, Stock, etc.).

### Промпт 31 — Shared components (badges)

Создай shared components:

1. `frontend/src/app/shared/components/bid-decision-badge.component.ts`:
   - standalone, OnPush, selector: dp-bid-decision-badge
   - Input: decisionType (string)
   - Template: small badge with icon + text
     - BID_UP → green background, arrow-up icon, "Повысить"
     - BID_DOWN → red background, arrow-down icon, "Снизить"
     - HOLD → gray background, minus icon, "Удержать"
     - PAUSE → yellow background, pause icon, "Пауза"
     - SET_MINIMUM → blue background, minimize icon, "Минимум"
   - Uses Lucide icons (ArrowUp, ArrowDown, Minus, Pause, Minimize2)
   - Translates text via translate pipe

2. `frontend/src/app/shared/components/bid-policy-status-badge.component.ts`:
   - standalone, OnPush, selector: dp-bid-policy-status-badge
   - Input: status (string)
   - DRAFT → gray, ACTIVE → green, PAUSED → yellow, ARCHIVED → gray muted

### Промпт 32 — i18n additions (if needed)

Проверь `frontend/src/locale/ru.json` — если в промптах 26–31 использовались новые ключи, которых ещё нет, добавь их. Также добавь:
```json
"bidding.detail.current_strategy": "Текущая стратегия",
"bidding.detail.no_strategy": "Стратегия не назначена",
"bidding.detail.current_bid": "Текущая ставка",
"bidding.detail.last_decision": "Последнее решение",
"bidding.detail.decision_history": "История решений",
"bidding.detail.manual_lock_active": "Ставка зафиксирована",
"bidding.detail.assign_strategy": "Назначить стратегию",
"bidding.assignment.product": "Товар",
"bidding.assignment.scope": "Область",
"bidding.assignment.created_at": "Назначен",
"bidding.run.total_eligible": "Всего товаров",
"bidding.run.started_at": "Начат",
"bidding.run.completed_at": "Завершён",
"bidding.empty.strategies": "Нет стратегий. Создайте первую стратегию для автоматического управления ставками.",
"bidding.empty.assignments": "Нет назначенных товаров",
"bidding.empty.decisions": "Нет решений за выбранный период",
"bidding.action.approve": "Одобрить",
"bidding.action.reject": "Отклонить",
"bidding.action.approve_all": "Одобрить все",
"bidding.action.reject_all": "Отклонить все",
"bidding.toast.policy_created": "Стратегия создана",
"bidding.toast.policy_activated": "Стратегия активирована",
"bidding.toast.policy_paused": "Стратегия приостановлена",
"bidding.toast.policy_archived": "Стратегия архивирована",
"bidding.toast.assignment_created": "Товар назначен",
"bidding.toast.assignment_deleted": "Назначение удалено",
"bidding.toast.lock_created": "Ставка зафиксирована",
"bidding.toast.lock_removed": "Фиксация снята",
"bidding.toast.run_triggered": "Прогон запущен",
"bidding.toast.action_approved": "Действие одобрено",
"bidding.toast.action_rejected": "Действие отклонено",
"bidding.toast.error": "Не удалось выполнить операцию"
```

### Промпт 33 — Strategy config forms

Создай `frontend/src/app/features/bidding/strategies/strategy-config-form.component.ts`:
- standalone, OnPush, selector: dp-strategy-config-form
- Input: strategyType (signal), initialConfig (signal, optional)
- Output: configChanged (EventEmitter<Record<string, unknown>>)

Содержимое зависит от strategyType:

**ECONOMY_HOLD:**
- targetDrrPct: number input (required, label: "Целевой ДРР, %")
- tolerancePct: number input (default 10, label: "Допуск, %")
- stepUpPct: number input (default 10, label: "Шаг повышения, %")
- stepDownPct: number input (default 15, label: "Шаг понижения, %")
- maxBidKopecks: number input (optional, label: "Потолок ставки, коп.")
- minRoas: number input (default 1.0, label: "Минимальный ROAS")

**MINIMAL_PRESENCE:**
- Нет дополнительных настроек. Показать текст: "Стратегия минимального присутствия автоматически устанавливает минимальную допустимую ставку."

Emit configChanged при изменении любого поля (debounced).

Используй этот компонент в create/edit modals (промпты 26, 27).

**Чеклист после Chat 8:**
- [ ] Grid columns для bidding (bidStatus, currentBid, lastBidDecision, bidDrrPct)
- [ ] Новый view "Реклама" в view tabs
- [ ] OfferBiddingTabComponent — вкладка в detail panel
- [ ] BidDecisionBadgeComponent — shared badge
- [ ] BidPolicyStatusBadgeComponent — shared badge
- [ ] StrategyConfigFormComponent — dynamic form per strategy type
- [ ] i18n дополнен
- [ ] `ng build` проходит

---

## Chat 9 — Extras: MinimalPresence + remaining guards + bulk + alerts + docs (промпты 34–40)

### Контекст для чата

Продолжаю реализацию модуля автобиддинга. Backend pipeline, execution layer, adapters, frontend pages/grid/panel — всё готово.

Сейчас: вторая стратегия, оставшиеся guards, массовые операции, алерты, обновление docs.

Reference:
- `docs/modules/autobidding.md` §8.2, §9
- `backend/datapulse-audit-alerting/` — для alert checkers

### Промпт 34 — MinimalPresenceStrategy

В `io.datapulse.bidding.domain.strategy` создай `MinimalPresenceStrategy` (@Component):
- implements BiddingStrategy
- strategyType() → MINIMAL_PRESENCE

Логика по autobidding.md §8.2:
- targetBid = signals.minBid (минимальная допустимая ставка)
- Если currentBid > minBid → BID_DOWN, target = minBid
- Если currentBid < minBid → BID_UP, target = minBid
- Если currentBid == minBid → HOLD
- Если minBid == null → HOLD (нет данных о минимальной ставке)
- Если stockDays != null && stockDays == 0 → PAUSE
- explanation: "Minimal presence: current={current}, min={min}. Decision: {type}"

### Промпт 35 — Remaining guards (3 штуки)

Добавь в `io.datapulse.bidding.domain.guard`:

1. `LowStockGuard` (order=45):
   - Block BID_UP если signals.stockDays != null && signals.stockDays > 0 && signals.stockDays < properties.getLowStockThresholdDays()
   - MessageCode: `bidding.guard.low_stock.blocked`, args: {days: stockDays, threshold: lowStockThresholdDays}

2. `FrequencyGuard` (order=55):
   - Block если signals.daysSinceLastChange != null && signals.daysSinceLastChange < (properties.getMinDecisionIntervalHours() / 24)
   - Фактически: если решение было менее minDecisionIntervalHours часов назад → block
   - MessageCode: `bidding.guard.frequency.blocked`, args: {hours: minDecisionIntervalHours}

3. `DrrCeilingGuard` (order=60):
   - Block BID_UP если signals.drrPct != null && signals.drrPct > drrCeiling (из policyConfig JsonNode, default 30)
   - MessageCode: `bidding.guard.drr_ceiling.blocked`, args: {drr: drrPct, ceiling: drrCeiling}

Добавь MessageCodes:
```java
public static final String BIDDING_GUARD_LOW_STOCK = "bidding.guard.low_stock.blocked";
public static final String BIDDING_GUARD_FREQUENCY = "bidding.guard.frequency.blocked";
public static final String BIDDING_GUARD_DRR_CEILING = "bidding.guard.drr_ceiling.blocked";
```

Добавь переводы в ru.json:
```json
"bidding.guard.low_stock.blocked": "Низкие остатки ({{days}} дн., порог: {{threshold}} дн.)",
"bidding.guard.frequency.blocked": "Решение было менее {{hours}} ч назад",
"bidding.guard.drr_ceiling.blocked": "ДРР {{drr}}% превышает потолок {{ceiling}}%"
```

### Промпт 36 — Bulk operations (backend)

Расширь backend для массовых операций из грида:

1. В `BidPolicyAssignmentController` добавь:
   - POST /api/v1/bid-policies/{policyId}/assignments/bulk-unassign — body: List<Long> marketplaceOfferIds → void

2. В `ManualBidLockController` добавь:
   - POST /api/v1/manual-bid-locks/bulk → body: List<CreateManualBidLockRequest> → List<ManualBidLockResponse>
   - POST /api/v1/manual-bid-locks/bulk-unlock → body: List<Long> lockIds → void

3. В services добавь соответствующие методы:
   - BidPolicyAssignmentService.bulkUnassign(long bidPolicyId, List<Long> marketplaceOfferIds)
   - ManualBidLockService.bulkCreateLock(long workspaceId, List<CreateManualBidLockRequest>)
   - ManualBidLockService.bulkRemoveLock(List<Long> lockIds)

### Промпт 37 — Bulk operations (frontend)

В grid bulk-actions-bar (или аналогичном компоненте):

1. Добавь bulk action buttons для bidding (показываются при выделении строк):
   - "Назначить стратегию" → modal с select policy → bulkAssign API call
   - "Зафиксировать ставку" → modal с bid input + reason → bulk lock API call
   - "Снять фиксацию" → confirmation → bulk unlock API call

2. Добавь methods в bidding-api.service.ts:
   - bulkUnassign(policyId: number, offerIds: number[]): Observable<void>
   - bulkCreateLocks(workspaceId: number, requests: CreateManualBidLockRequest[]): Observable<ManualBidLock[]>
   - bulkRemoveLocks(lockIds: number[]): Observable<void>

3. Toast notifications: "Стратегия назначена на {{count}} товаров", "Ставка зафиксирована для {{count}} товаров"

### Промпт 38 — Alert checkers for bidding

В `datapulse-audit-alerting` создай alert checkers (посмотри существующие AlertChecker implementations для паттерна):

1. `AutobidHighDrrClusterChecker`:
   - Если > 20% товаров под autobidding имеют DRR > targetDrr × 1.5 за последние 7 дней → alert
   - SQL: join bid_policy_assignment с mart_advertising_product

2. `AutobidSpendSpikeChecker`:
   - Если суммарный ad spend за сегодня > 2× среднего за 7 дней для товаров под autobidding → alert

Добавь alert types в AlertType enum (если есть) и переводы в ru.json.

### Промпт 39 — WebSocket notifications

Добавь WebSocket push для bidding events:

1. В BiddingRunService при завершении run → publish WebSocket event:
   `{ type: "BIDDING_RUN_COMPLETED", workspaceId, runId, stats: { bidUp, bidDown, hold, paused } }`

2. При blast radius PAUSED:
   `{ type: "BIDDING_RUN_PAUSED", workspaceId, runId }`

Посмотри как pricing module или audit module отправляет WebSocket events и сделай аналогично.

Frontend: в notification store обработай новые event types, покажи toast.

### Промпт 40 — Documentation updates

1. Обнови `docs/data-model.md`:
   - Добавь bidding таблицы (bid_policy, bid_policy_assignment, bidding_run, bid_decision, bid_action, bid_action_attempt, manual_bid_lock)
   - Добавь outbox events: BIDDING_RUN_EXECUTE, BID_ACTION_EXECUTE, BID_ACTION_RETRY

2. Обнови `docs/modules/autobidding.md`:
   - Измени статус на "implemented — MVP"
   - Добавь секцию "Implementation notes" с ссылками на ключевые классы

**Чеклист после Chat 9:**
- [ ] MinimalPresenceStrategy
- [ ] 3 дополнительных guard (LowStock, Frequency, DrrCeiling) + MessageCodes + i18n
- [ ] Bulk operations backend (bulk-unassign, bulk locks)
- [ ] Bulk operations frontend (grid actions)
- [ ] 2 alert checkers
- [ ] WebSocket notifications
- [ ] docs/data-model.md обновлён
- [ ] docs/modules/autobidding.md обновлён
- [ ] `mvn compile` + `ng build` проходят

---

## Chat 10 — Tests + stabilization (промпты 41–45)

### Контекст для чата

Реализация модуля автобиддинга завершена. Весь код написан. Сейчас: тесты и финальная стабилизация.

Reference:
- `backend/datapulse-pricing/src/test/` — тесты pricing модуля для reference
- Правила: coding-style.mdc §27, §42

### Промпт 41 — Unit tests: strategies + guards

Создай unit tests в `backend/datapulse-bidding/src/test/java/io/datapulse/bidding/domain/`:

1. `EconomyHoldStrategyTest`:
   - @ExtendWith(MockitoExtension.class)
   - @Nested groups: BidUp, BidDown, Hold, EdgeCases
   - Tests:
     - should_bidUp_when_drrBelowLowerBound_and_roasAboveMin
     - should_bidDown_when_drrAboveUpperBound
     - should_hold_when_drrWithinTolerance
     - should_respectMaxBid_when_bidUpExceedsMax
     - should_useMinBid_when_bidDownBelowMin
     - should_hold_when_currentBidIsNull
     - should_hold_when_drrIsNull

2. `MinimalPresenceStrategyTest`:
   - should_bidDown_when_currentBidAboveMinBid
   - should_bidUp_when_currentBidBelowMinBid
   - should_hold_when_currentBidEqualsMinBid
   - should_pause_when_outOfStock
   - should_hold_when_minBidIsNull

3. Guard tests (один test class на guard, по 2-3 cases):
   - ManualBidLockGuardTest: should_block_when_lockExists, should_allow_when_noLock
   - CampaignInactiveGuardTest: should_block_when_statusNotActive, should_allow_when_statusActive
   - StaleAdvertisingDataGuardTest: should_block_when_noRecentData, should_allow_when_dataFresh
   - StockOutGuardTest: should_block_bidUp_when_outOfStock, should_allow_bidDown_when_outOfStock
   - EconomyGuardTest: should_block_bidUp_when_negativeMargina, should_allow_when_positiveMargina
   - LowStockGuardTest: should_block_bidUp_when_lowStock, should_allow_when_enoughStock
   - FrequencyGuardTest: should_block_when_recentDecision, should_allow_when_enoughTimePassed
   - DrrCeilingGuardTest: should_block_bidUp_when_drrAboveCeiling, should_allow_when_drrBelowCeiling

AssertJ assertions. Mockito mocks for repositories.

### Промпт 42 — Integration test: BiddingRunService

Создай `BiddingRunServiceIntegrationTest` в `backend/datapulse-bidding/src/test/`:
- @SpringJUnitConfig + @Testcontainers (PostgreSQL)
- Посмотри как сделаны integration tests в pricing module
- @TestConfiguration с mock BiddingClickHouseReadRepository (return fixed signal values)

Setup: insert через JPA repos — workspace, marketplace_offer, bid_policy (ACTIVE, ECONOMY_HOLD, config), bid_policy_assignment

Tests:
1. should_createDecisions_when_runExecuted — full pipeline, verify BidDecisionEntity created with correct type
2. should_holdDecision_when_guardBlocks — setup stale data → decision type = HOLD with guard info
3. should_pauseRun_when_blastRadiusExceeded — setup 100% BID_UP → run status = PAUSED
4. should_skipProduct_when_manualLockExists — insert manual_bid_lock → verify product skipped

### Промпт 43 — Adapter tests (WireMock)

Создай adapter tests:

1. `WbBidCommandAdapterTest`:
   - WireMock stub: PATCH /adv/v1/save-bids → 200 empty body
   - Test: execute → success, verify request body format (advertId, cpm, param, instrument)
   - WireMock stub: 429 → verify appropriate error handling
   - WireMock stub: 400 → verify failure result

2. `OzonBidCommandAdapterTest`:
   - WireMock stub: POST token endpoint → return mock access_token
   - WireMock stub: POST /api/client/campaign/products/set-bids → 200
   - Test: execute → success, verify bid conversion (kopecks ÷ 100 = rubles in request)
   - Test: 401 → verify token eviction

### Промпт 44 — Frontend compilation fixes

1. Запусти `ng build` и исправь все ошибки компиляции
2. Проверь:
   - Все imports используют path aliases (@core/, @shared/)
   - Все components: standalone: true, changeDetection: OnPush
   - Все TanStack queries имеют queryKey
   - Все mutations имеют onError с toast
   - Все @for имеют track
   - Нет any/as any
3. Fix all lint errors

### Промпт 45 — End-to-end smoke + final fixes

Финальная проверка:

1. `mvn compile -pl datapulse-bidding` — компиляция модуля
2. `mvn compile` — компиляция всего backend
3. Проверь Liquibase: migrations 0031 и 0032 корректно ссылаются на существующие таблицы
4. Проверь RabbitTopologyConfig: все exchanges/queues/bindings для bidding определены
5. Проверь OutboxEventType: 3 новых значения маппят на правильные exchanges
6. Проверь что все REST endpoints зарегистрированы (контроллеры сканируются компонент-сканом datapulse-api)
7. `ng build` — frontend компиляция
8. Исправь все обнаруженные проблемы

**Чеклист после Chat 10:**
- [ ] Unit tests: 2 strategy tests + 8 guard tests (~25 test cases)
- [ ] Integration test: BiddingRunService (4 scenarios)
- [ ] Adapter tests: WB + Ozon (WireMock, 5 test cases)
- [ ] Frontend compiles without errors
- [ ] Backend compiles without errors
- [ ] All lint errors fixed
- [ ] Migrations valid
- [ ] RabbitMQ topology complete
- [ ] Outbox events mapped correctly

---

## Summary

| Chat | Промпты | Тема | Ожидаемое время |
|------|---------|------|-----------------|
| 1 | 1–3 | Maven module + migrations + entities + enums | 15–20 min |
| 2 | 4–6 | Config + Strategy + Guards | 15–20 min |
| 3 | 7–10 | Signal assembly + pipeline | 20–25 min |
| 4 | 11–14 | Trigger + CRUD + API + i18n | 20–25 min |
| 5 | 15–18 | Execution layer | 20–25 min |
| 6 | 19–23 | WB/Ozon adapters | 15–20 min |
| 7 | 24–28 | Frontend: models + routes + pages | 25–30 min |
| 8 | 29–33 | Frontend: grid + panel + shared | 20–25 min |
| 9 | 34–40 | Extras: strategies + guards + bulk + alerts + docs | 20–25 min |
| 10 | 41–45 | Tests + stabilization | 25–30 min |

**Total: 10 чатов, 45 промптов, ~3–4 часа чистого времени**

После завершения: +1–2 промпта на эмпирическую верификацию API (когда будут доступны реальные токены маркетплейсов).
