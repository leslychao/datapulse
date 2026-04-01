# Модуль: Pricing

**Фаза:** C — Pricing
**Зависимости:** [ETL Pipeline](etl-pipeline.md) (canonical truth), [Analytics & P&L](analytics-pnl.md) (P&L truth, derived signals)
**Runtime:** datapulse-pricing-worker, datapulse-api (policy CRUD)

---

## Назначение

Объяснимый repricing с полным audit trail. Pipeline: eligibility → signal assembly → strategy evaluation → constraint resolution → guard pipeline → decision → explanation → action scheduling.

## Pipeline

```
Eligibility → Signal Assembly → Strategy Evaluation → Constraint Resolution → Guard Pipeline → Decision → Explanation → Action Scheduling
```

| Шаг | Вход | Выход | Может прервать? |
|-----|------|-------|-----------------|
| **Eligibility** | SKU + policy assignment | eligible / skip (reason) | Да |
| **Signal Assembly** | eligible SKU | `PricingSignalSet` | Да (missing critical signal) |
| **Strategy Evaluation** | signals + strategy config | raw target price | Нет |
| **Constraint Resolution** | raw target price + constraints | clamped target price | Нет |
| **Guard Pipeline** | clamped price + context | pass / block (reason) | Да |
| **Decision** | всё выше | CHANGE / SKIP / HOLD | Terminal |
| **Explanation** | decision + все inputs | explanation record | Нет |
| **Action Scheduling** | decision = CHANGE | `price_action` (PENDING_APPROVAL) | Да (active action exists) |

## Стратегии ценообразования

### TARGET_MARGIN — целевая маржинальность (основная)

**Формула:**

```
effective_cost_rate = commission_pct + logistics_pct + return_adjustment_pct + ad_cost_pct
target_price = COGS / (1 − target_margin_pct − effective_cost_rate)
```

**Параметры стратегии (strategy_params JSONB):**

| Параметр | Тип | Описание |
|----------|-----|----------|
| `target_margin_pct` | BigDecimal | Целевая маржа (0.25 = 25%) |
| `commission_source` | enum | `AUTO` / `MANUAL` / `AUTO_WITH_MANUAL_FALLBACK` |
| `commission_manual_pct` | BigDecimal | Ручная ставка комиссии |
| `commission_lookback_days` | int | Период для historical avg (default: 30) |
| `commission_min_transactions` | int | Минимум транзакций (default: 5) |
| `logistics_source` | enum | `AUTO` / `MANUAL` / `AUTO_WITH_MANUAL_FALLBACK` |
| `logistics_manual_amount` | BigDecimal | Ручная сумма логистики за единицу |
| `include_return_adjustment` | boolean | Учитывать return rate |
| `include_ad_cost` | boolean | Учитывать advertising cost ratio |
| `rounding_step` | BigDecimal | Шаг округления (default: 10) |
| `rounding_direction` | enum | `FLOOR` / `NEAREST` / `CEIL` (default: FLOOR) |

**Commission source — каскадный fallback (AUTO_WITH_MANUAL_FALLBACK, default):**

```
1. Historical per-SKU (lookback_days, ≥ min_transactions)
     ↓ недостаточно данных
2. Historical per-category
     ↓ недостаточно данных
3. Manual value из commission_manual_pct
     ↓ не задано
4. Decision = SKIP
```

MARKUP как отдельная стратегия не включается. TARGET_MARGIN покрывает MARKUP как частный случай при cost rates = 0.

### Validation contract для strategy_params

`strategy_params` десериализуется в typed record с Jakarta Validation constraints при создании/обновлении `price_policy`. Невалидные params → 400 Bad Request.

#### TARGET_MARGIN constraints

| Параметр | Constraint | Обоснование |
|----------|-----------|-------------|
| `target_margin_pct` | [0.01, 0.80] | > 0.80 при любых cost rates → отрицательный знаменатель |
| `commission_manual_pct` | [0.01, 0.50] | Комиссия МП не превышает 50% |
| `commission_lookback_days` | [7, 365] | < 7 — недостаточно данных; > 365 — irrelevant |
| `commission_min_transactions` | [1, 100] | Минимальная выборка для статистической значимости |
| `rounding_step` | [1, 100] | Шаг округления в рублях |

#### PRICE_CORRIDOR constraints

| Параметр | Constraint | Обоснование |
|----------|-----------|-------------|
| `min_price` | > 0, nullable | Абсолютный floor |
| `max_price` | > min_price, nullable | Абсолютный ceiling |

#### Runtime safety guard

Pricing strategy evaluator проверяет `denominator > 0` перед делением. При `denominator ≤ 0` → `decision = SKIP` с reason «target margin + effective cost rate ≥ 100%». Это defense-in-depth: validation ловит при создании policy, runtime guard — при execution (на случай если cost rates изменились после создания policy).

### PRICE_CORRIDOR — ценовой коридор (ограничительная)

```
if current_price < min_price → target_price = min_price
if current_price > max_price → target_price = max_price
else → target_price = current_price (no change)
```

### Стратегии Phase G+ (будущее)

Все будущие стратегии расширяют тот же pipeline (eligibility → signal assembly → strategy evaluation → constraint resolution → guard pipeline → decision → explanation). Новые стратегии добавляются как значения `strategy_type` enum. Constraint resolution и guard pipeline — shared для всех стратегий.

| Стратегия | Фаза | Требования | Описание |
|-----------|------|------------|----------|
| `VELOCITY_ADAPTIVE` | G | Historical sales velocity (fact_sales, ≥ 30 дней) | Цена адаптируется к скорости продаж: замедление → снижение, ускорение → повышение |
| `STOCK_BALANCING` | G | Inventory intelligence (fact_inventory_snapshot, lead time) | Цена балансирует остатки: overstock → снижение, near-stockout → повышение |
| `COMPETITOR_ANCHOR` | G+ | Competitor data source, matching subsystem, trust scoring | Цена привязана к конкурентному ориентиру с margin floor |
| `COMPOSITE` | G+ | Несколько signal sources | Взвешенная комбинация нескольких стратегий |

#### COMPETITOR_ANCHOR — архитектурные prerequisites

Для реализации необходимы компоненты, которых нет в текущей архитектуре:

1. **Competitor data source** — внешний поставщик конкурентных цен (SaaS API, scraping service, manual upload). Не является частью Datapulse core. Интеграция через adapter boundary (аналогично marketplace adapters).

2. **Canonical competitor model** — новые PostgreSQL-таблицы:

| Таблица | Назначение |
|---------|------------|
| `competitor_observation` | Наблюдение: marketplace_offer_id, competitor_source, competitor_price, confidence_score, observed_at, freshness_status |
| `competitor_match` | Матчинг: marketplace_offer_id, competitor_listing_id, match_method (AUTO/MANUAL), trust_level (TRUSTED/CANDIDATE/REJECTED) |

3. **Signal assembly extension** — новые signals: `competitor_price`, `competitor_match_trust`, `competitor_freshness`.

4. **Competitor-specific guards** — `competitor_freshness_guard`, `competitor_trust_guard`, `competitor_match_missing_guard`.

5. **Strategy formula (примерная):**

```
anchor_price = competitor_price × position_factor
target_price = MAX(anchor_price, margin_floor_price)
margin_floor_price = COGS / (1 − min_margin_pct − effective_cost_rate)
```

`position_factor` — configurable: 1.0 = match, 0.95 = на 5% дешевле, 1.05 = на 5% дороже.

**Design decision: отложить до подтверждённой потребности.** COMPETITOR_ANCHOR не включается в scope без: подтверждённого бизнес-кейса, надёжного источника данных о конкурентах, бюджета на competitor data integration. Текущая архитектура (signal assembly + strategy extensibility + constraint/guard pipeline) **не блокирует** добавление в будущем.

## Модель price_policy

```
price_policy:
  id                        BIGSERIAL PK
  workspace_id              BIGINT FK → workspace
  name                      VARCHAR
  status                    ENUM (DRAFT, ACTIVE, PAUSED, ARCHIVED)
  strategy_type             ENUM (TARGET_MARGIN, PRICE_CORRIDOR)
  strategy_params           JSONB
  min_margin_pct            DECIMAL (nullable)
  max_price_change_pct      DECIMAL (nullable)
  min_price                 DECIMAL (nullable)
  max_price                 DECIMAL (nullable)
  guard_config              JSONB
  execution_mode            ENUM (RECOMMENDATION, SEMI_AUTO, FULL_AUTO, SIMULATED)
  approval_timeout_hours    INT DEFAULT 72
  priority                  INT DEFAULT 0
  version                   INT NOT NULL DEFAULT 1
  created_by                BIGINT FK → app_user
  created_at                TIMESTAMPTZ
  updated_at                TIMESTAMPTZ
```

Key constraints — колонки (SQL-фильтрация). Guard config, strategy params — JSONB (расширяемость).

### Policy versioning

`version` инкрементируется атомарно при UPDATE полей, влияющих на pricing logic: `strategy_type`, `strategy_params`, `min_margin_pct`, `max_price_change_pct`, `min_price`, `max_price`, `guard_config`, `execution_mode`.

```sql
UPDATE price_policy
SET strategy_params = :params, ..., version = version + 1, updated_at = NOW()
WHERE id = :id
```

Изменения `name`, `status`, `priority` **не** инкрементируют version — это метаданные, не pricing logic. Полная версионная история policy прослеживается через `policy_snapshot` в `price_decision` (см. §Decision и Explanation).

## Назначение политик на товары

```
price_policy_assignment:
  id                        BIGSERIAL PK
  price_policy_id           BIGINT FK → price_policy
  marketplace_connection_id BIGINT FK → marketplace_connection
  scope_type                ENUM (CONNECTION, CATEGORY, SKU)
  category_id               BIGINT (nullable)
  marketplace_offer_id      BIGINT (nullable, FK → marketplace_offer)

  UNIQUE (price_policy_id, marketplace_connection_id, scope_type, COALESCE(category_id, 0), COALESCE(marketplace_offer_id, 0))
  -- одна запись per policy × scope target; предотвращает дублирующие назначения
```

## Manual price lock

Оператор может зафиксировать цену конкретного товара, исключив его из автоматического ценообразования. Lock → товар не проходит eligibility check → pricing pipeline пропускает его.

```
manual_price_lock:
  id                        BIGSERIAL PK
  workspace_id              BIGINT FK → workspace                    NOT NULL
  marketplace_offer_id      BIGINT FK → marketplace_offer            NOT NULL
  locked_price              DECIMAL NOT NULL                         -- зафиксированная цена
  reason                    TEXT                                     -- причина фиксации (nullable)
  locked_by                 BIGINT FK → app_user                     NOT NULL
  locked_at                 TIMESTAMPTZ NOT NULL DEFAULT now()
  expires_at                TIMESTAMPTZ                              -- auto-unlock (nullable — бессрочно)
  unlocked_at               TIMESTAMPTZ                              -- manual unlock (nullable)
  unlocked_by               BIGINT FK → app_user                     (nullable)

  UNIQUE (marketplace_offer_id) WHERE unlocked_at IS NULL           -- один active lock per offer
```

**Eligibility check:** `SELECT EXISTS(SELECT 1 FROM manual_price_lock WHERE marketplace_offer_id = ? AND unlocked_at IS NULL AND (expires_at IS NULL OR expires_at > now()))`. Если TRUE → decision = SKIP, reason = `MANUAL_LOCK`.

**Expiration:** scheduled job (hourly) проверяет `WHERE expires_at < now() AND unlocked_at IS NULL` → UPDATE `unlocked_at = now()`, `unlocked_by = NULL` (system unlock).

**Audit:** создание/удаление lock записывается в `audit_log` (action_type = `lock.create`, `lock.remove`).

### Разрешение конфликтов: специфичность + приоритет

```
1. SKU-level    (scope_type = SKU)        → специфичность = 3 (высшая)
2. Category-level (scope_type = CATEGORY) → специфичность = 2
3. Connection-level (scope_type = CONNECTION) → специфичность = 1

Одинаковый уровень → policy.priority (выше = важнее).
Одинаковый priority → наименьший id (first created wins).
```

## Signal Assembly

Signal assembler — единственная точка, через которую derived signals попадают в pricing.

| Signal | Источник | Store | Используется |
|--------|----------|-------|-------------|
| `current_price` | `canonical_price_current` | PostgreSQL | Все стратегии |
| `cogs` | `cost_profile` (SCD2) | PostgreSQL | TARGET_MARGIN |
| `product_status` | `marketplace_offer` | PostgreSQL | Eligibility |
| `available_stock` | `canonical_stock_current` | PostgreSQL | Guard |
| `manual_lock` | `manual_price_lock` | PostgreSQL | Eligibility |
| `avg_commission_pct` | fact_finance | ClickHouse | TARGET_MARGIN |
| `avg_logistics_per_unit` | fact_finance | ClickHouse | TARGET_MARGIN |
| `return_rate_pct` | fact_returns / fact_sales | ClickHouse | TARGET_MARGIN |
| `ad_cost_ratio` | fact_advertising.spend / fact_finance.revenue (30-day rolling, per product) | ClickHouse | TARGET_MARGIN |
| `last_price_change_at` | `price_decision` | PostgreSQL | Frequency guard |
| `price_change_history` | `price_decision` | PostgreSQL | Volatility guard |
| `data_freshness` | `marketplace_sync_state` | PostgreSQL | Stale data guard |

### Signal: `ad_cost_ratio` — specification

Доля рекламных расходов в выручке по продукту. Используется в формуле TARGET_MARGIN: `effective_cost_rate = commission_pct + logistics_pct + return_adjustment_pct + ad_cost_pct`.

```
ad_cost_ratio = SUM(fact_advertising.spend) / NULLIF(SUM(fact_finance.revenue_amount), 0)
  WHERE fact_advertising.marketplace_sku = offer.marketplace_sku
    AND fact_advertising.ad_date >= today() - 30
    AND fact_finance.finance_date >= today() - 30
    AND fact_finance.attribution_level = 'POSTING'
```

| Параметр | Значение |
|----------|----------|
| Time window | 30 дней (rolling) |
| Grain | Per `seller_sku_id` (агрегируется across campaigns) |
| Revenue source | `fact_finance.revenue_amount` WHERE `attribution_level = 'POSTING'` |
| Spend source | `fact_advertising.spend` (joined через `marketplace_sku`) |
| Fallback (no advertising data) | `ad_cost_ratio = 0` |
| Fallback (no revenue) | `ad_cost_ratio = NULL` → signal INSUFFICIENT_DATA |

**Interaction with `include_ad_cost`:**
- `include_ad_cost = false` (default) → `ad_cost_pct = 0` в формуле, signal не участвует
- `include_ad_cost = true` → `ad_cost_pct = ad_cost_ratio`
- Рекомендация: переключать на `true` после подтверждения что advertising data стабильно загружается ≥ 14 дней

## Constraints (ограничения)

Жёсткие ограничения, которые корректируют target price. Не блокируют — clamp.

| Constraint | Описание | Композиция |
|------------|----------|------------|
| `min_margin` | Цена не ниже margin floor | Floor |
| `max_price_change` | Макс. ±X% за одно решение | Clamp |
| `min_price` / `max_price` | Абсолютные границы | Floor / Ceiling |
| `marketplace_min_price` | Минимум маркетплейса | Floor |
| `rounding` | Округление до шага | Round (FLOOR default) |

Порядок: min_price → max_price → max_price_change → min_margin → marketplace_min → rounding.

При конфликте — наиболее ограничительный побеждает.

## Guards (блокирующие проверки)

| Guard | Блокирует когда | Default | Отключаемый? |
|-------|-----------------|---------|-------------|
| **Margin guard** | margin < min_margin_pct | Включён | Да |
| **Frequency guard** | Изменение < N часов назад | 24 часа | Да |
| **Volatility guard** | > N разворотов за период | 3 / 7 дней | Да |
| **Stale data guard** | Данные старше N часов | 24 часа | Нет (safety) |
| **Promo guard** | Товар в активном промо ([Promotions](promotions.md) — source of truth) | Включён | Да |
| **Manual lock guard** | Ручная блокировка | Включён | Нет |
| **Stock-out guard** | Остатки = 0 | Включён | Да |

Порядок оптимизирован по стоимости (дешёвые первыми). Short-circuit: первый сработавший → SKIP.

### guard_config JSONB structure

```json
{
  "margin_guard_enabled": true,
  "frequency_guard_enabled": true,
  "frequency_guard_hours": 24,
  "volatility_guard_enabled": true,
  "volatility_guard_reversals": 3,
  "volatility_guard_period_days": 7,
  "promo_guard_enabled": true,
  "stock_out_guard_enabled": true,
  "stale_data_guard_hours": 24
}
```

Все поля optional — при отсутствии используются defaults. `stale_data_guard` и `manual_lock_guard` не имеют `_enabled` флага — всегда активны (safety).

**Примечание:** помимо per-SKU stale data guard, analytics automation blocker ([Analytics & P&L](analytics-pnl.md#automation-blocker)) может заблокировать весь pricing run для account/marketplace при stale finance data (> 24h) или residual anomaly.

## Eligibility (критерии допуска)

| Критерий | Skip reason |
|----------|-------------|
| Активная policy | "Нет активной ценовой политики" |
| Товар активен | "Товар неактивен на маркетплейсе" |
| Нет manual lock | "Ручная блокировка цены" |
| COGS задана (margin-based) | "Себестоимость не задана" |
| Connection active | "Подключение неактивно" |

## Decision и Explanation

### Модель decision

```sql
price_decision:
  id                        BIGSERIAL PK
  workspace_id              BIGINT FK → workspace              NOT NULL
  pricing_run_id            BIGINT FK → pricing_run            NOT NULL
  marketplace_offer_id      BIGINT FK → marketplace_offer      NOT NULL
  price_policy_id           BIGINT FK → price_policy           NOT NULL
  policy_version            INT NOT NULL                       -- snapshot price_policy.version
  policy_snapshot           JSONB NOT NULL                     -- full policy config at decision time
  decision_type             VARCHAR(20) NOT NULL               -- CHANGE, SKIP, HOLD
  current_price             DECIMAL                            -- marketplace price at decision time
  target_price              DECIMAL                            -- computed target (nullable — null for SKIP/HOLD)
  price_change_amount       DECIMAL                            -- target - current (nullable)
  price_change_pct          DECIMAL                            -- (target - current) / current × 100 (nullable)
  strategy_type             VARCHAR(30) NOT NULL               -- TARGET_MARGIN, PRICE_CORRIDOR (redundant with snapshot for SQL filtering)
  strategy_raw_price        DECIMAL                            -- raw price before constraints (nullable)
  signal_snapshot           JSONB                              -- all assembled signals at decision time
  constraints_applied       JSONB                              -- ordered list: [{ name, from_price, to_price }]
  guards_evaluated          JSONB                              -- ordered list: [{ name, passed: bool, details }]
  skip_reason               VARCHAR(255)                       -- human-readable skip reason (nullable — only for SKIP/HOLD)
  explanation_summary       TEXT                               -- structured explanation (see §Explanation summary format)
  execution_mode            VARCHAR(20) NOT NULL               -- LIVE, SIMULATED (derived: policy SIMULATED → SIMULATED, иначе LIVE)
  created_at                TIMESTAMPTZ NOT NULL DEFAULT now()

  INDEX idx_pd_workspace_created (workspace_id, created_at DESC)
  INDEX idx_pd_offer_latest (marketplace_offer_id, created_at DESC)
  INDEX idx_pd_run (pricing_run_id)
  INDEX idx_pd_policy_version (price_policy_id, policy_version)
```

`policy_version` — snapshot `price_policy.version` на момент решения. Indexed INT для аналитических запросов ("distribution of decisions by policy version").

`policy_snapshot` — полный слепок policy config на момент решения:

```json
{
  "policy_id": 42,
  "version": 3,
  "name": "Маржа 25% WB",
  "strategy_type": "TARGET_MARGIN",
  "strategy_params": { "target_margin_pct": 0.25, "commission_source": "AUTO_WITH_MANUAL_FALLBACK", "rounding_step": 10 },
  "min_margin_pct": 0.15,
  "max_price_change_pct": 0.10,
  "min_price": null,
  "max_price": null,
  "guard_config": { "frequency_guard_hours": 24, "volatility_guard_reversals": 3 },
  "execution_mode": "SEMI_AUTO"
}
```

`strategy_type` и `execution_mode` redundant с `policy_snapshot`, но сохраняются top-level для SQL-фильтрации и индексирования (JSONB-фильтрация дороже). Controlled redundancy: snapshot для audit, top-level поля для operational queries.

### Decision → Action

- CHANGE + SEMI_AUTO → `price_action` PENDING_APPROVAL (`execution_mode = LIVE`)
- CHANGE + FULL_AUTO → `price_action` APPROVED (`execution_mode = LIVE`)
- CHANGE + SIMULATED → `price_action` APPROVED (`execution_mode = SIMULATED`)
- CHANGE + RECOMMENDATION → НЕ создаёт action; рекомендация в UI
- SKIP → сохраняется для аудита
- HOLD → недостаточность данных

**Mapping `price_policy.execution_mode` → `price_decision.execution_mode`:** policy SIMULATED → decision SIMULATED, все остальные (RECOMMENDATION, SEMI_AUTO, FULL_AUTO) → decision LIVE. `price_decision.execution_mode` — derived поле для SQL-фильтрации; source of truth — `policy_snapshot.execution_mode` внутри decision.

### Action scheduling — обработка конфликтов с active action

При создании `price_action` возможен конфликт с partial unique index (active action уже существует для того же offer в том же `execution_mode`). Обработка per-offer, не per-batch:

| Сценарий | Действие |
|----------|----------|
| Active action в pre-execution (PENDING_APPROVAL, APPROVED, ON_HOLD, SCHEDULED) | Immediate supersede → SUPERSEDED; новый action создаётся. В одной транзакции |
| Active action в in-flight (EXECUTING, RETRY_SCHEDULED, RECONCILIATION_PENDING) | Создаётся `deferred_action` ([Execution](execution.md) §Deferred supersede). Decision сохраняется. Alert: «action creation deferred, in-flight action {id} in progress» |
| Unique constraint violation (race condition) | Catch per-offer. Decision сохраняется со skip_reason «active action in progress». Оффер пропускается, batch продолжается. `log.warn` + alert |

**Инвариант:** конфликт при создании action для одного оффера НЕ прерывает pricing run для остальных офферов в batch.

### Retention

| Decision type | Retention |
|---------------|-----------|
| CHANGE (с action) | Бессрочно |
| CHANGE (recommendation) | 90 дней |
| SKIP | 30 дней |

Партиционирование `price_decision` по `created_at` (monthly).

### Explanation summary format

`explanation_summary` — human-readable TEXT, генерируемый шагом Explanation по фиксированному шаблону. Состоит из секций, разделённых newline. Каждая секция начинается с label в квадратных скобках.

**CHANGE:**

```
[Решение] CHANGE: 4 500 → 3 890 (−13.6%)
[Политика] «Маржа 25% WB» (TARGET_MARGIN, v3)
[Стратегия] target_margin=25.0%, effective_cost_rate=38.2% (commission=15.0%, logistics=8.2%, returns=5.0%, ads=10.0%) → raw=3 842
[Ограничения] rounding FLOOR step=10: 3 842 → 3 840; min_price 3 890: 3 840 → 3 890
[Guards] Все пройдены
[Режим] SEMI_AUTO → action PENDING_APPROVAL
```

**SKIP:**

```
[Решение] SKIP
[Причина] Данные старше 24 часов (last sync: 2026-03-30 08:15)
[Guard] stale_data_guard: last_success_at=2026-03-30 08:15, threshold=24h
```

**HOLD:**

```
[Решение] HOLD
[Причина] Себестоимость не задана
[Политика] «Маржа 25% WB» (TARGET_MARGIN, v3) — requires COGS
```

**Секции:**

| Секция | Когда присутствует | Содержание |
|--------|-------------------|------------|
| `[Решение]` | Всегда | `{decision_type}`: price change summary или skip/hold |
| `[Политика]` | Всегда кроме eligibility SKIP | Имя, тип стратегии, version |
| `[Стратегия]` | Только для CHANGE | Ключевые параметры формулы и raw result |
| `[Ограничения]` | Только если constraints изменили цену | Каждое ограничение: name, from → to |
| `[Guards]` | Всегда для eligible SKUs | «Все пройдены» или сработавший guard с деталями |
| `[Причина]` | Только для SKIP/HOLD | Human-readable skip_reason |
| `[Режим]` | Только для CHANGE | execution_mode и результирующий action status |

**Правила формата:** числа без trailing zeros; разделитель тысяч (пробел) для цен ≥ 10 000; проценты — один знак после точки; знак изменения — `+` для повышения, `−` (минус) для понижения; policy version — `v{N}`.

## Execution mode и уровни автоматизации

| Mode | Описание | Действие |
|------|----------|----------|
| **RECOMMENDATION** | Показывает рекомендацию | Decision сохраняется; action НЕ создаётся. Рекомендация отображается в UI для ручного решения оператора |
| **SEMI_AUTO** | Создаёт action PENDING_APPROVAL | Оператор одобряет или отклоняет |
| **FULL_AUTO** | Создаёт action APPROVED | Контроль через guards; failed → alert |
| **SIMULATED** | Симулированное исполнение | Action создаётся APPROVED с `execution_mode = SIMULATED`. Реальная запись на маркетплейс не выполняется; результат пишется в `simulated_offer_state` |

### Safety gate для FULL_AUTO

1. Policy была в SEMI_AUTO минимум N дней (default: 7)
2. Не было FAILED actions за последние N дней
3. Stale data guard НЕ отключён
4. Manual lock guard НЕ отключён
5. Pricing manager явно подтверждает

## Marketplace-specific трансляция

### WB: Phase C — discount = 0 always

Pricing pipeline работает с конечной ценой для покупателя. WB adapter: target_price = WB price, discount = 0.

### Ozon: Phase C — old_price = "0"

`auto_action_enabled = "DISABLED"`, `price_strategy_enabled = "DISABLED"`. Datapulse управляет ценой сам.

## Pricing run

### Триггеры

| Триггер | Механизм | Частота |
|---------|----------|---------|
| Post-sync | RabbitMQ event `ETL_SYNC_COMPLETED` (outbox, [ETL Pipeline](etl-pipeline.md#post-sync-outbox-events)) | После успешного ETL sync (1-4 раза в день) |
| Manual | REST API `POST /api/workspaces/{workspaceId}/pricing/runs` → outbox → RabbitMQ | По требованию |
| Schedule | Spring `@Scheduled` cron → outbox → RabbitMQ | Configurable cron |
| Policy change | `@TransactionalEventListener(AFTER_COMMIT)` → outbox → RabbitMQ | При изменении/активации policy |
| **Manual bulk** | REST API `POST /api/workspaces/{workspaceId}/pricing/bulk-manual/apply` → synchronous run | По требованию (ad-hoc). Strategy = `MANUAL_OVERRIDE` (user-provided price). Guards: frequency, volatility, stock-out **не применяются**. См. [Bulk Operations & Draft Mode](../features/2026-03-31-bulk-operations-draft-mode.md) |

#### Post-sync trigger flow

```
ETL ingest-worker: sync COMPLETED
  → INSERT outbox_event (ETL_SYNC_COMPLETED, connection_id, completed_domains[])
  → outbox poller → RabbitMQ exchange datapulse.etl.events

pricing-worker queue receives ETL_SYNC_COMPLETED:
  1. Check: FINANCE ∈ completed_domains? (no → skip pricing run)
  2. Check: active policies exist for connection_id? (no → skip)
  3. Check: no IN_PROGRESS pricing run for connection_id? (yes → skip, idempotent)
  4. INSERT pricing_run (PENDING) → INSERT outbox_event (PRICING_RUN_EXECUTE)
  5. pricing-worker picks up → executes batch processing
```

**Идемпотентность:** pricing worker хранит `source_job_execution_id` в `pricing_run`. При повторной доставке `ETL_SYNC_COMPLETED` проверяется `EXISTS pricing_run WHERE source_job_execution_id = ?` — дубликат игнорируется.

**Инвариант:** pricing run для connection X не запускается, пока для того же connection X есть ETL `job_execution` в статусе `IN_PROGRESS`. Post-sync trigger гарантирует это by design (event приходит после completion). Manual и scheduled триггеры проверяют отсутствие активного sync перед запуском. Если есть active ETL → pricing run откладывается (ожидает `ETL_SYNC_COMPLETED`).

### pricing_run model

```sql
pricing_run:
  id                      BIGSERIAL PK
  workspace_id            BIGINT FK → workspace              NOT NULL
  connection_id           BIGINT FK → marketplace_connection  NOT NULL
  trigger_type            VARCHAR(30) NOT NULL                -- POST_SYNC, MANUAL, SCHEDULED, POLICY_CHANGE, MANUAL_BULK
  request_hash            VARCHAR(64)                         -- SHA-256 дедупликации для MANUAL_BULK (nullable)
  requested_offers_count  INT                                 -- для MANUAL_BULK: сколько offers в запросе (nullable)
  source_job_execution_id BIGINT FK → job_execution           (nullable — only for POST_SYNC)
  status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING'
  total_offers            INT
  eligible_count          INT
  change_count            INT
  skip_count              INT
  hold_count              INT
  started_at              TIMESTAMPTZ
  completed_at            TIMESTAMPTZ
  error_details           JSONB
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
```

**Lifecycle:**

```
PENDING → IN_PROGRESS → COMPLETED
                      → COMPLETED_WITH_ERRORS
                      → FAILED
```

| Переход | Guard |
|---------|-------|
| PENDING → IN_PROGRESS | CAS: `WHERE id = ? AND status = 'PENDING'` |
| IN_PROGRESS → COMPLETED | Все offers обработаны, 0 errors |
| IN_PROGRESS → COMPLETED_WITH_ERRORS | Часть offers обработана с ошибками |
| IN_PROGRESS → FAILED | Infrastructure error (DB, ClickHouse unavailable) |

### Batch processing

```
1. Resolve effective policies for all marketplace_offers in connection
2. Filter eligible offers
3. Batch signal assembly (one ClickHouse query per signal type)
4. Per-SKU: strategy → constraints → guards → decision → explanation
5. Batch insert decisions
6. Batch create actions (for CHANGE decisions with SEMI_AUTO/FULL_AUTO)
```

## Impact preview (Phase E)

Перед активацией или изменением `price_policy` оператор может запросить preview: «что произойдёт, если эта policy будет применена?»

### Механика

```
1. Resolve offers, попадающие под assignments policy
2. Прогнать per-offer pricing pipeline (eligibility → signals → strategy → constraints → guards) в dry-run mode
3. Не создавать decisions/actions — только preview-результат
4. Вернуть aggregated summary + per-offer breakdown
```

### Preview result

| Поле | Описание |
|------|----------|
| `total_offers` | Количество offers в scope policy |
| `eligible_count` | Прошли eligibility |
| `change_count` | Decision = CHANGE |
| `skip_count` | Decision = SKIP (с breakdown по skip reasons) |
| `hold_count` | Decision = HOLD |
| `avg_price_change_pct` | Средний % изменения для CHANGE decisions |
| `max_price_change_pct` | Максимальный % изменения |
| `min_margin_after` | Минимальная margin после изменения (worst case) |
| `offers_breakdown` | Per-offer detail (paginated): current_price, target_price, change %, decision_type, skip_reason |

### Ограничения

- Preview выполняется synchronously в рамках API request (не через outbox/worker).
- Timeout: 30s. Для policies с scope > 10 000 offers — async preview через polling.
- Preview НЕ является гарантией: между preview и реальным pricing run данные могут измениться.

### Фаза: E (Seller Operations)

Impact preview — часть операционного cockpit. UI интегрируется в policy creation/editing flow.

## REST API

### Policies

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| POST | `/api/workspaces/{workspaceId}/pricing/policies` | PRICING_MANAGER, ADMIN, OWNER | Создать policy. Body: `{ name, strategyType, strategyParams, minMarginPct, maxPriceChangePct, minPrice, maxPrice, guardConfig, executionMode, priority }`. Status = DRAFT. Response: `201` |
| GET | `/api/workspaces/{workspaceId}/pricing/policies` | Any role | Paginated. Список policies workspace. Filters: `?status=ACTIVE&strategyType=...` |
| GET | `/api/workspaces/{workspaceId}/pricing/policies/{policyId}` | Any role | Детали policy |
| PUT | `/api/workspaces/{workspaceId}/pricing/policies/{policyId}` | PRICING_MANAGER, ADMIN, OWNER | Обновить policy (инкрементирует version). Body: все изменяемые поля |
| POST | `/api/workspaces/{workspaceId}/pricing/policies/{policyId}/activate` | PRICING_MANAGER, ADMIN, OWNER | DRAFT/PAUSED → ACTIVE |
| POST | `/api/workspaces/{workspaceId}/pricing/policies/{policyId}/pause` | PRICING_MANAGER, ADMIN, OWNER | ACTIVE → PAUSED |
| POST | `/api/workspaces/{workspaceId}/pricing/policies/{policyId}/archive` | PRICING_MANAGER, ADMIN, OWNER | → ARCHIVED |

### Policy assignments

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| GET | `/api/workspaces/{workspaceId}/pricing/policies/{policyId}/assignments` | Any role | Список assignments |
| POST | `/api/workspaces/{workspaceId}/pricing/policies/{policyId}/assignments` | PRICING_MANAGER, ADMIN, OWNER | Добавить assignment. Body: `{ connectionId, scopeType, categoryId?, marketplaceOfferId? }` |
| DELETE | `/api/workspaces/{workspaceId}/pricing/policies/{policyId}/assignments/{assignmentId}` | PRICING_MANAGER, ADMIN, OWNER | Удалить assignment |

### Manual price lock

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| POST | `/api/workspaces/{workspaceId}/pricing/locks` | OPERATOR, PRICING_MANAGER, ADMIN, OWNER | Создать lock. Body: `{ marketplaceOfferId, lockedPrice, reason, expiresAt? }` |
| GET | `/api/workspaces/{workspaceId}/pricing/locks` | Any role | Active locks. Filter: `?marketplaceOfferId=...` |
| DELETE | `/api/workspaces/{workspaceId}/pricing/locks/{lockId}` | OPERATOR, PRICING_MANAGER, ADMIN, OWNER | Unlock (manual) |

### Pricing runs

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| POST | `/api/workspaces/{workspaceId}/pricing/runs` | PRICING_MANAGER, ADMIN, OWNER | Trigger manual pricing run. Body: `{ connectionId }` |
| GET | `/api/workspaces/{workspaceId}/pricing/runs` | Any role | Paginated. Filters: `?connectionId=...&status=...&from=...&to=...` |
| GET | `/api/workspaces/{workspaceId}/pricing/runs/{runId}` | Any role | Детали run: status, counts, timing |

### Decisions

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| GET | `/api/workspaces/{workspaceId}/pricing/decisions` | Any role | Paginated. Filters: `?connectionId=...&marketplaceOfferId=...&decisionType=...&pricingRunId=...&from=...&to=...` |
| GET | `/api/workspaces/{workspaceId}/pricing/decisions/{decisionId}` | Any role | Полные детали decision: signal_snapshot, constraints_applied, guards_evaluated, explanation_summary |

### Impact preview

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| POST | `/api/workspaces/{workspaceId}/pricing/policies/{policyId}/preview` | PRICING_MANAGER, ADMIN, OWNER | Dry-run preview. Response: aggregated summary + paginated per-offer breakdown |

### Bulk manual price operations

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| POST | `/api/workspaces/{workspaceId}/pricing/bulk-manual/preview` | PRICING_MANAGER, ADMIN, OWNER | Dry-run: constraints + guards per-offer. Body: `{ changes: [{ marketplaceOfferId, targetPrice }] }`. Response: per-offer result + summary. Max 500 offers. Timeout 30s |
| POST | `/api/workspaces/{workspaceId}/pricing/bulk-manual/apply` | PRICING_MANAGER, ADMIN, OWNER | Создаёт pricing_run (MANUAL_BULK) + decisions (MANUAL_OVERRIDE) + actions (APPROVED). Body: тот же формат. Idempotency: `request_hash` (SHA-256). Детали: [Bulk Operations & Draft Mode](../features/2026-03-31-bulk-operations-draft-mode.md) |

### Bulk cost update

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| POST | `/api/cost-profiles/bulk-update` | PRICING_MANAGER, ADMIN, OWNER | Массовое обновление себестоимости (SCD2). Body: `{ sellerSkuIds, operation, value, validFrom }`. Max 500 SKUs. Детали: [Bulk Operations & Draft Mode](../features/2026-03-31-bulk-operations-draft-mode.md) |

## Связанные модули

- [Analytics & P&L](analytics-pnl.md) — derived signals через signal assembler
- [ETL Pipeline](etl-pipeline.md) — canonical state для decision-grade reads
- [Execution](execution.md) — action lifecycle после decision
- [Promotions](promotions.md) — Promo guard читает canonical participation_status; shared signal assembler
- [Seller Operations](seller-operations.md) — price journal, recommendations UI, impact preview UI
- Детальные write-контракты: [Write Contracts](../provider-api-specs/write-contracts.md)
- [Bulk Operations & Draft Mode](../features/2026-03-31-bulk-operations-draft-mode.md) — Phase E: bulk manual pricing, draft mode, bulk cost update
