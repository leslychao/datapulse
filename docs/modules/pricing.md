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

### Frontend validation (policy form)

Frontend дублирует constraints бэкенда + добавляет cross-field проверки для UX:

| Проверка | Тип | Описание |
|----------|-----|----------|
| `name` required, maxLength 255 | Field | Название обязательно |
| `targetMarginPct` [1, 80] required | Field | Только для TARGET_MARGIN |
| `commissionManualPct` [1, 50] | Field | Показывается при source ≠ AUTO |
| `corridorMinPrice` OR `corridorMaxPrice` required | Cross-field | Хотя бы одно значение для PRICE_CORRIDOR |
| `corridorMaxPrice > corridorMinPrice` | Cross-field | Если оба заданы |
| `maxPrice > minPrice` (constraints) | Cross-field | Если оба заданы |
| Validation summary | UX | При submit с ошибками — блок со списком всех проблем |
| Tooltips на каждом поле | UX | Через `title` attr + `?` иконка, ключи `pricing.form.tooltip.*` |

### PRICE_CORRIDOR — ценовой коридор (ограничительная)

```
if current_price < min_price → target_price = min_price
if current_price > max_price → target_price = max_price
else → target_price = current_price (no change)
```

### Расширяемость: Strategy + Registry

Pricing pipeline использует паттерн **Strategy + Registry** для подключения стратегий:

- **Интерфейс** `PricingStrategy`: метод `evaluate(PricingSignalSet signals, StrategyParams params) → BigDecimal rawTargetPrice`. Дискриминатор: `strategyType() → StrategyType`.
- **Registry** `PricingStrategyRegistry`: Spring auto-discovery через `List<PricingStrategy>` injection в конструкторе. Index по `strategyType`. Lookup: `registry.get(policy.strategyType)`.
- **Добавление новой стратегии:** создать `@Component` класс, реализующий `PricingStrategy`. Registry подхватит автоматически. Правки в pipeline, constraint resolution и guard pipeline не требуются.

Constraint resolution и guard pipeline — shared для всех стратегий, не зависят от `strategy_type`.

### VELOCITY_ADAPTIVE — адаптация к скорости продаж

**Реализовано.** Стратегия адаптирует цену к динамике продаж на основе сравнения short-window и long-window velocity.

**Формула:**

```
velocity_ratio = salesVelocityShort / salesVelocityLong

if ratio < deceleration_threshold:
  adjustment = -deceleration_discount_pct × (threshold - ratio) / threshold
elif ratio > acceleration_threshold:
  adjustment = +acceleration_markup_pct × (ratio - threshold) / threshold
else:
  HOLD (velocity stable)

target_price = current_price × (1 + adjustment)
```

**Сигналы:** `salesVelocityShort` (7d), `salesVelocityLong` (30d) из `fact_sales` (CH).

**Параметры:** `decelerationThreshold` (0.70), `accelerationThreshold` (1.30), `decelerationDiscountPct` (0.05), `accelerationMarkupPct` (0.03), `minBaselineSales` (10), `velocityWindowShortDays` (7), `velocityWindowLongDays` (30).

### STOCK_BALANCING — ценовое управление остатками

**Реализовано.** Стратегия управляет ценой на основе days_of_cover из `mart_inventory_analysis`.

**Формула:**

```
if daysOfCover < criticalDaysOfCover:
  adjustment = +stockoutMarkupPct (защита от stockout)
elif daysOfCover > overstockDaysOfCover:
  overshoot = (daysOfCover - threshold) / threshold
  adjustment = -min(overshoot × discountFactor, maxDiscountPct)
else:
  HOLD (stock normal)

target_price = current_price × (1 + adjustment)
```

**Сигналы:** `daysOfCover`, `frozenCapital`, `stockOutRisk` из `mart_inventory_analysis` (CH).

**Параметры:** `criticalDaysOfCover` (7), `overstockDaysOfCover` (60), `stockoutMarkupPct` (0.05), `overstockDiscountFactor` (0.10), `maxDiscountPct` (0.20), `leadTimeDays` (14).

### COMPOSITE — взвешенная комбинация стратегий

**Реализовано.** Позволяет комбинировать несколько стратегий с весами. Финальная target price — средневзвешенная raw prices успешных компонентов.

**Алгоритм:**
1. Для каждого компонента: вызвать `strategy.calculate(signals, params)`.
2. Отфильтровать SKIP-компоненты, перенормировать веса.
3. `weighted_target = Σ(rawTargetPrice_i × weight_i)`.

**Ограничения:** вложенный `COMPOSITE` запрещён (no recursion). `MANUAL_OVERRIDE` как компонент запрещён.

### COMPETITOR_ANCHOR — конкурентное ценообразование

**Реализовано (Manual Upload MVP).** Привязка цены к конкурентному ориентиру с margin floor.

**Формула:**

```
anchor_price = competitor_price × position_factor
if useMarginFloor AND cogs available:
  margin_floor_price = cogs / (1 − min_margin_pct − effective_cost_rate)
  target_price = MAX(anchor_price, margin_floor_price)
else:
  target_price = anchor_price
```

**Данные о конкурентах:** ручной ввод + CSV upload через REST API. Хранение в `competitor_match` + `competitor_observation` (PostgreSQL).

**Параметры:** `positionFactor` (1.0 = match), `minMarginPct` (0.10), `aggregation` (MIN/MEDIAN/AVG), `useMarginFloor` (true).

## Competitor Price Model

### Таблицы

| Таблица | Назначение |
|---------|------------|
| `competitor_match` | Привязка товара к конкуренту: marketplace_offer_id, competitor_name, match_method (MANUAL/AUTO), trust_level (TRUSTED/CANDIDATE/REJECTED) |
| `competitor_observation` | Наблюдение цены: competitor_match_id, competitor_price, currency, observed_at |

### Signal Assembly

Competitor signals собираются в `PricingSignalCollector` через LATERAL JOIN: для каждого offer берётся минимальная цена среди TRUSTED matches с последним observation.

### Competitor Guards

| Guard | Блокирует когда | Порядок |
|-------|-----------------|---------|
| `CompetitorFreshnessGuard` | Данные о конкурентах старше `competitorFreshnessHours` (default 72) | 25 |
| `CompetitorTrustGuard` | `trust_level = CANDIDATE` (не подтверждён) | 26 |

### REST API (Competitor Management)

| Method | Path | Описание |
|--------|------|----------|
| GET | `/api/workspaces/{wsId}/competitors/matches` | Список привязок |
| POST | `/api/workspaces/{wsId}/competitors/matches` | Создать привязку |
| DELETE | `/api/workspaces/{wsId}/competitors/matches/{id}` | Удалить привязку |
| POST | `/api/workspaces/{wsId}/competitors/matches/{id}/observations` | Добавить наблюдение цены |
| GET | `/api/workspaces/{wsId}/competitors/matches/{id}/observations` | История наблюдений |
| POST | `/api/workspaces/{wsId}/competitors/bulk-upload` | CSV upload (multipart) |

## AI Pricing Features

### Pricing Advisor (§6.1)

**Что:** LLM-генерированный совет по ценообразованию для конкретного товара. On-demand, кэш 24h.

**API:** `POST /api/workspaces/{wsId}/pricing/advisor/{offerId}` → `{ advice, generatedAt, cachedUntil, error }`.

**Вход LLM:** последний `price_decision` с `explanation_summary`, `signal_snapshot`, P&L данные, inventory, competitor data.

**System prompt:** Pricing advisor для селлеров маркетплейсов. Формат: [Рекомендация] + [Обоснование] + [Риски]. 2-3 предложения, конкретные цифры.

**Кэширование:** Caffeine, ключ = `offerId:lastDecisionId`, TTL 24h.

**Fallback (LLM unavailable):** `{ advice: null, error: "pricing.advisor.unavailable" }`.

**Frontend:** в Detail Panel → секция «AI-совет» (collapsible, lazy-load on expand).

### Impact Simulation Narrative (§6.2)

**Что:** текстовое описание результата Impact Preview. Генерируется async при вызове preview endpoint.

**API:** дополнительные поля в response `POST /api/pricing/policies/{policyId}/preview`:
- `narrative: string | null`
- `narrativeStatus: PENDING | READY | UNAVAILABLE`

**Вход LLM:** summary (totalOffers, changeCount, avgPriceChangePct, minMarginAfter) + top-5 biggest changes.

**Timeout:** 5 секунд. Если LLM не успевает — `narrativeStatus = UNAVAILABLE`.

### Proactive Price Insights (§6.3)

**Что:** автоматические инсайты, генерируемые ежедневно (`@Scheduled`). Хранятся в `pricing_insight` (PostgreSQL).

**Типы инсайтов:**

| Тип | Детекция | Severity |
|-----|----------|----------|
| `PRICE_INCREASE_CANDIDATE` | margin > 40% AND velocity growing | INFO |
| `OVERSTOCK_LIQUIDATION` | days_of_cover > 90 AND frozen_capital > threshold | WARNING |
| `HIGH_DRR_ALERT` | ad_cost_ratio > 30% | WARNING |
| `COMPETITOR_UNDERCUT` | competitor_price < current_price × 0.9 | CRITICAL |

**Таблица:**

```
pricing_insight:
  id              BIGSERIAL PK
  workspace_id    BIGINT FK → workspace
  insight_type    VARCHAR(50)
  title           VARCHAR(500)
  body            TEXT
  severity        ENUM (INFO, WARNING, CRITICAL)
  acknowledged    BOOLEAN DEFAULT false
  created_at      TIMESTAMPTZ
```

**API:**

| Method | Path | Описание |
|--------|------|----------|
| GET | `/api/workspaces/{wsId}/pricing/insights` | Список инсайтов. Filter: `?type=...&acknowledged=false` |
| GET | `/api/workspaces/{wsId}/pricing/insights/count` | Количество непрочитанных |
| POST | `/api/workspaces/{wsId}/pricing/insights/{id}/acknowledge` | Отметить как прочитанный |

**Notification:** `InsightCreatedEvent` публикуется через `ApplicationEventPublisher`.

**Frontend:** новый tab «Инсайты» в pricing section. Карточки с severity icon, title, body, type badge, кнопка «Прочитано».

### Расширяемость: Strategy + Registry

Pricing pipeline использует паттерн **Strategy + Registry** для подключения стратегий:

- **Интерфейс** `PricingStrategy`: метод `evaluate(PricingSignalSet signals, StrategyParams params) → BigDecimal rawTargetPrice`. Дискриминатор: `strategyType() → StrategyType`.
- **Registry** `PricingStrategyRegistry`: Spring auto-discovery через `List<PricingStrategy>` injection в конструкторе. Index по `strategyType`. Lookup: `registry.get(policy.strategyType)`.
- **Добавление новой стратегии:** создать `@Component` класс, реализующий `PricingStrategy`. Registry подхватит автоматически. Правки в pipeline, constraint resolution и guard pipeline не требуются.

Constraint resolution и guard pipeline — shared для всех стратегий, не зависят от `strategy_type`.

## Модель price_policy

```
price_policy:
  id                        BIGSERIAL PK
  workspace_id              BIGINT FK → workspace
  name                      VARCHAR
  status                    ENUM (DRAFT, ACTIVE, PAUSED, ARCHIVED)
  strategy_type             ENUM (TARGET_MARGIN, PRICE_CORRIDOR, VELOCITY_ADAPTIVE, STOCK_BALANCING, COMPOSITE, COMPETITOR_ANCHOR, MANUAL_OVERRIDE)
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
  last_preview_version      INT NOT NULL DEFAULT 0          -- version при последнем impact preview (для mandatory preview gate)
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
| `cogs` | `cost_profile` (SCD2) | PostgreSQL | TARGET_MARGIN, COMPETITOR_ANCHOR |
| `product_status` | `marketplace_offer` | PostgreSQL | Eligibility |
| `available_stock` | `canonical_stock_current` | PostgreSQL | Guard |
| `manual_lock` | `manual_price_lock` | PostgreSQL | Eligibility |
| `marketplace_min_price` | `canonical_price_current.min_price` | PostgreSQL | Marketplace min price constraint |
| `avg_commission_pct` | `mart_posting_pnl` | ClickHouse | TARGET_MARGIN, COMPETITOR_ANCHOR |
| `avg_logistics_per_unit` | `mart_posting_pnl` | ClickHouse | TARGET_MARGIN |
| `return_rate_pct` | `fact_returns` / `fact_sales` | ClickHouse | TARGET_MARGIN |
| `ad_cost_ratio` | `fact_advertising` / `fact_finance` (30-day rolling) | ClickHouse | TARGET_MARGIN, Ad cost guard |
| `sales_velocity_short` | `fact_sales` (7-day window) | ClickHouse | VELOCITY_ADAPTIVE |
| `sales_velocity_long` | `fact_sales` (30-day window) | ClickHouse | VELOCITY_ADAPTIVE |
| `days_of_cover` | `mart_inventory_analysis` | ClickHouse | STOCK_BALANCING, VELOCITY_ADAPTIVE |
| `frozen_capital` | `mart_inventory_analysis` | ClickHouse | STOCK_BALANCING |
| `stock_out_risk` | `mart_inventory_analysis` | ClickHouse | STOCK_BALANCING |
| `competitor_price` | `competitor_observation` (min among TRUSTED) | PostgreSQL | COMPETITOR_ANCHOR |
| `competitor_trust_level` | `competitor_match.trust_level` | PostgreSQL | Competitor trust guard |
| `competitor_freshness_at` | `competitor_observation.observed_at` | PostgreSQL | Competitor freshness guard |
| `last_price_change_at` | `price_decision` | PostgreSQL | Frequency guard |
| `price_reversals_in_period` | `price_decision` | PostgreSQL | Volatility guard |
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

### Signal criticality и ClickHouse fallback

| Signal | Источник | Criticality | Fallback при недоступности источника |
|--------|----------|-------------|--------------------------------------|
| `current_price` | PostgreSQL | **CRITICAL** | Нет fallback — без current_price pipeline невозможен |
| `cogs` | PostgreSQL | **CRITICAL** (TARGET_MARGIN) | Нет — decision = HOLD, reason «Себестоимость не задана» |
| `product_status` | PostgreSQL | **CRITICAL** | Нет — eligibility невозможна |
| `available_stock` | PostgreSQL | OPTIONAL | `NULL` → stock-out guard пропускается (PASS) |
| `manual_lock` | PostgreSQL | **CRITICAL** | Нет — eligibility невозможна |
| `avg_commission_pct` | ClickHouse | **REQUIRED** | Cascade fallback: per-SKU → per-category → manual → SKIP (см. commission source) |
| `avg_logistics_per_unit` | ClickHouse | **REQUIRED** | Cascade: per-SKU → per-category → manual. Все уровни пусты и `logistics_source ≠ MANUAL` → HOLD |
| `return_rate_pct` | ClickHouse | OPTIONAL | `0` (conservative: не увеличивает effective_cost_rate) |
| `ad_cost_ratio` | ClickHouse | OPTIONAL | `0` при `include_ad_cost = false`; `NULL` → HOLD при `include_ad_cost = true` и отсутствии revenue |
| `last_price_change_at` | PostgreSQL | OPTIONAL | `NULL` → frequency guard пропускается (первое изменение) |
| `price_change_history` | PostgreSQL | OPTIONAL | Пустой список → volatility guard пропускается |
| `data_freshness` | PostgreSQL | **CRITICAL** | Нет — stale data guard не может быть вычислен → conservative BLOCK |

**Классификация:**
- **CRITICAL** — без сигнала pipeline не может работать. PostgreSQL-only. При недоступности PostgreSQL → pricing_run = FAILED.
- **REQUIRED** — необходим для стратегии, но имеет cascade fallback. ClickHouse-зависимые сигналы.
- **OPTIONAL** — имеет safe default (0, NULL → guard skip). Недоступность не блокирует decision.

**ClickHouse unavailability — поведение:**

- **Timeout per query:** 5s (configurable: `datapulse.pricing.clickhouse-query-timeout`). При timeout → signal = `UNAVAILABLE`.
- **REQUIRED signal = UNAVAILABLE:** если cascade fallback исчерпан → per-SKU decision = HOLD с reason `pricing.signal.unavailable` и `args: { signal: "{signal_name}" }`.
- **OPTIONAL signal = UNAVAILABLE:** используется safe default (см. таблицу).
- **Все ClickHouse queries fail (полная недоступность):** pricing_run продолжает обработку offers с PostgreSQL-only signals. TARGET_MARGIN offers → HOLD (commission/logistics unavailable без manual fallback). PRICE_CORRIDOR offers → нормальная обработка (не зависит от ClickHouse).
- **Run status:** COMPLETED_WITH_ERRORS (не FAILED), если хотя бы часть offers обработана. FAILED — только при полном infrastructure failure (PostgreSQL down).

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

| Guard | Блокирует когда | Default | Отключаемый? | Order |
|-------|-----------------|---------|-------------|-------|
| **Manual lock guard** | Ручная блокировка | Включён | Нет | 10 |
| **Promo guard** | Товар в активном/frozen промо | Включён | Да | 11 |
| **Stock-out guard** | Остатки = 0 | Включён | Да | 12 |
| **Stale data guard** | Данные старше N часов | 24 часа | Нет (safety) | 15 |
| **Frequency guard** | Изменение < N часов назад | 24 часа | Да | 20 |
| **Volatility guard** | > N разворотов за период | 3 / 7 дней | Да | 21 |
| **Margin guard** | margin < min_margin_pct | Включён | Да | 22 |
| **Ad cost guard** | DRR > threshold при снижении цены | Отключён | Да | 23 |
| **Competitor freshness guard** | Данные о конкурентах старше N часов | 72 часа | Да | 25 |
| **Competitor trust guard** | trust_level = CANDIDATE (не подтверждён) | Отключён | Да | 26 |

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
  "stale_data_guard_hours": 24,
  "ad_cost_guard_enabled": false,
  "ad_cost_drr_threshold_pct": 0.15,
  "competitor_freshness_guard_enabled": false,
  "competitor_freshness_hours": 72,
  "competitor_trust_guard_enabled": false
}
```

Все поля optional — при отсутствии используются defaults. `stale_data_guard` и `manual_lock_guard` не имеют `_enabled` флага — всегда активны (safety).

**Примечание:** помимо per-SKU stale data guard, analytics automation blocker ([Analytics & P&L](analytics-pnl.md#automation-blocker)) может заблокировать весь pricing run для account/marketplace при stale finance data (> 24h) или residual anomaly.

### Алгоритмы guards

#### Margin guard

```
projected_margin = (target_price − COGS − target_price × effective_cost_rate) / target_price
if projected_margin < policy.min_margin_pct → BLOCK
  reason: "Projected margin {projected_margin}% ниже порога {min_margin_pct}%"
```

Guard проверяет **projected margin после изменения цены** (от target_price), не текущую маржу. Если `min_margin_pct` не задан на policy (NULL) → guard пропускается. `effective_cost_rate` берётся из signal assembly (commission + logistics + returns + ads).

#### Frequency guard

```
last_change_at = SELECT MAX(created_at) FROM price_decision
  WHERE marketplace_offer_id = :offerId
    AND decision_type = 'CHANGE'
    AND execution_mode = 'LIVE'

hours_since = HOURS_BETWEEN(last_change_at, now())
if hours_since < guard_config.frequency_guard_hours → BLOCK
  reason: "Последнее изменение {hours_since}ч назад, порог {frequency_guard_hours}ч"
```

Считает время с последнего **CHANGE decision** в LIVE mode (SIMULATED не учитываются). Если `last_change_at IS NULL` (никогда не менялась) → PASS.

#### Volatility guard

**Разворот (reversal)** — смена знака `price_change_amount` между двумя consecutive CHANGE decisions для одного SKU.

```
changes = SELECT price_change_amount FROM price_decision
  WHERE marketplace_offer_id = :offerId
    AND decision_type = 'CHANGE'
    AND execution_mode = 'LIVE'
    AND created_at >= now() - :volatility_guard_period_days
  ORDER BY created_at ASC

reversals = 0
for i in 1..len(changes)-1:
  if sign(changes[i]) ≠ sign(changes[i-1]):
    reversals++

if reversals >= guard_config.volatility_guard_reversals → BLOCK
  reason: "{reversals} разворотов за {period_days} дней, порог {volatility_guard_reversals}"
```

Пример: цены +5%, −3%, +2%, −1% за 7 дней = 3 разворота → при threshold 3 → BLOCK.

#### Stale data guard

```
last_sync = SELECT last_success_at FROM marketplace_sync_state
  WHERE connection_id = :connectionId
    AND sync_domain = 'FINANCE'

hours_since = HOURS_BETWEEN(last_sync, now())
if last_sync IS NULL → BLOCK (sync никогда не завершался)
if hours_since > guard_config.stale_data_guard_hours → BLOCK
  reason: "Финансовые данные устарели: {hours_since}ч, порог {stale_data_guard_hours}ч"
```

Проверяет freshness **финансовых данных** для connection товара. Domain = `FINANCE`, потому что pricing decisions зависят от комиссии, логистики, returns. `last_success_at IS NULL` → conservative BLOCK.

#### Stock-out guard

```
stock = signal_set.available_stock
if stock IS NULL → PASS (данные о стоках не синхронизированы — не блокируем; stale data guard покроет freshness)
if stock = 0 → BLOCK
  reason: "Остатки = 0"
if stock > 0 → PASS
```

`NULL` stock → PASS, а не BLOCK: отсутствие данных о стоках не должно блокировать pricing. Если данные о стоках критически устарели, это покрывается stale data guard на уровне sync domain `STOCK`.

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

### Action scheduling — нормальный flow

При `decision_type = CHANGE` и **отсутствии** active action для offer:

1. INSERT `price_action` с начальным статусом и snapshot `approval_timeout_hours` из policy:
   - SEMI_AUTO → `PENDING_APPROVAL`, `approval_timeout_hours = policy.approval_timeout_hours`
   - FULL_AUTO → `APPROVED`
   - SIMULATED → `APPROVED`, `execution_mode = SIMULATED`
   - RECOMMENDATION → action **не создаётся**; decision сохраняется для UI-отображения
2. Для APPROVED actions → INSERT `outbox_event` (`PRICE_ACTION_EXECUTE`)
3. Для PENDING_APPROVAL → оператор видит action в UI, может approve / reject до `created_at + approval_timeout_hours`

### Approval expiration

PENDING_APPROVAL actions хранят snapshot `approval_timeout_hours` из policy. Expiration: `created_at + approval_timeout_hours < NOW()`.

**Expiration job:** Execution worker, hourly `@Scheduled` + `@SchedulerLock(name = "approvalExpiration")` (детали: [Execution §Expiration mechanism](execution.md#expiration-mechanism)):

```sql
UPDATE price_action
SET status = 'EXPIRED', updated_at = now()
WHERE status = 'PENDING_APPROVAL'
  AND created_at + (approval_timeout_hours || ' hours')::interval < now()
RETURNING id, marketplace_offer_id, workspace_id
```

**Status transition:** `PENDING_APPROVAL → EXPIRED` (terminal). Expired action не исполняется.

**Notifications:**
- За 4 часа до истечения → push notification оператору: `pricing.approval.expiring_soon`, `args: { count, hours_remaining: 4 }`.
- При истечении → notification: `pricing.approval.expired`, `args: { count }`.

**Поведение после expiration:**
- Decision остаётся в истории (audit trail)
- Следующий pricing run для этого offer создаст новый decision и, при conditions = CHANGE, новый action
- Метрика: `pricing_approval_expired_total` counter — рост сигнализирует о перегрузке оператора или нерелевантных recommendations
- Alert: `pricing.approval.expiration_rate_high` при > 20% expired / total PENDING_APPROVAL за сутки

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

### Safety gate — enforcement

**При переключении на FULL_AUTO** (REST API `PUT /api/.../policies/{id}` с `executionMode = FULL_AUTO`):

1. Бэкенд валидирует все 5 условий. При нарушении → 400 Bad Request с `messageKey = pricing.policy.full_auto_gate_failed` и списком несоблюдённых условий в `args.violations[]`.
2. Условие «Pricing manager явно подтверждает» реализуется через обязательный флаг `confirmFullAuto: true` в request body. Отсутствие флага → 400 с reason `pricing.policy.full_auto_confirm_required`.

**Runtime re-check** при каждом pricing run для FULL_AUTO policy:

- Перепроверяются условия 2–4 (no FAILED actions за N дней, stale guard enabled, lock guard enabled).
- При нарушении: decision создаётся, но action **не создаётся** → downgrade до RECOMMENDATION с `skip_reason = pricing.safety_gate.runtime_violation`, `args: { violations: [...] }`.
- Alert: `pricing.full_auto.safety_gate_runtime_violation` (ops notification).

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
| Manual | REST API `POST /api/workspaces/{workspaceId}/pricing/runs/trigger` → derives connections from active policy assignments → outbox → RabbitMQ. Skips connections with run already in progress | По требованию |
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
  status                  VARCHAR(30) NOT NULL DEFAULT 'PENDING'  -- PENDING, IN_PROGRESS, COMPLETED, COMPLETED_WITH_ERRORS, FAILED, PAUSED, CANCELLED
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
                      → PAUSED → IN_PROGRESS (resume)
                               → CANCELLED (cancel)
```

| Переход | Guard |
|---------|-------|
| PENDING → IN_PROGRESS | CAS: `WHERE id = ? AND status = 'PENDING'` |
| IN_PROGRESS → COMPLETED | Все offers обработаны, 0 errors |
| IN_PROGRESS → COMPLETED_WITH_ERRORS | Часть offers обработана с ошибками |
| IN_PROGRESS → FAILED | Infrastructure error (DB, ClickHouse unavailable) |
| IN_PROGRESS → PAUSED | Blast radius circuit breaker сработал (только FULL_AUTO, см. §Aggregate blast radius) |
| PAUSED → IN_PROGRESS | Pricing manager resume: `POST .../runs/{id}/resume` |
| PAUSED → CANCELLED | Pricing manager cancel: `POST .../runs/{id}/cancel`. ON_HOLD actions → CANCELLED |

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

## Aggregate blast radius protection

Per-SKU `max_price_change_pct` ограничивает одно изменение. Aggregate circuit breaker защищает от массового ущерба при ошибке в strategy_params — даже если каждое отдельное изменение в пределах per-SKU лимита, совокупный эффект может быть критичным.

### Circuit breaker (FULL_AUTO only)

Pricing run в FULL_AUTO mode отслеживает aggregate metrics по ходу batch processing:

| Метрика | Описание | Default threshold | Configurable |
|---------|----------|-------------------|-------------|
| `change_ratio` | Доля offers с \|price_change_pct\| > 5% среди eligible | 0.30 (30%) | `datapulse.pricing.blast-radius.change-ratio-threshold` |
| `max_abs_change_pct` | Максимальный \|price_change_pct\| среди CHANGE decisions в run | 25% | `datapulse.pricing.blast-radius.max-abs-change-pct` |

**При превышении любого threshold:**

1. Pricing run переходит в `PAUSED` (между IN_PROGRESS и terminal)
2. Уже созданные APPROVED actions → `ON_HOLD` (execution не начинается)
3. Оставшиеся offers в batch → не обрабатываются
4. Alert: `pricing.run.blast_radius_breached`, `args: { runId, metric, value, threshold }`
5. Pricing manager может:
   - **Resume** (`POST /api/.../pricing/runs/{runId}/resume`) → run продолжает, ON_HOLD actions → APPROVED
   - **Cancel** (`POST /api/.../pricing/runs/{runId}/cancel`) → run = CANCELLED, ON_HOLD actions → CANCELLED

**Scope:** только FULL_AUTO. SEMI_AUTO защищён approval flow. RECOMMENDATION и SIMULATED — non-destructive.

### Mandatory impact preview

Для policies с `scope_type = CONNECTION` и `execution_mode = FULL_AUTO`:

- При активации policy (DRAFT/PAUSED → ACTIVE) бэкенд проверяет: impact preview выполнялся для текущей `version` policy. Если нет → 400 Bad Request, `messageKey = pricing.policy.preview_required_for_full_auto`.
- Tracking: `price_policy.last_preview_version INT DEFAULT 0`. Impact preview endpoint обновляет `last_preview_version = policy.version`. При `version` increment (policy edit) → preview считается устаревшим.
- Для `scope_type = CATEGORY` и `SKU` — preview рекомендуется, но не обязателен (blast radius ограничен scope).

## REST API

### Policies

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| POST | `/api/workspaces/{workspaceId}/pricing/policies` | PRICING_MANAGER, ADMIN, OWNER | Создать policy. Body: `{ name, strategyType, strategyParams, minMarginPct, maxPriceChangePct, minPrice, maxPrice, guardConfig, executionMode, approvalTimeoutHours?, priority }`. Status = DRAFT. `approvalTimeoutHours` — optional, default 72 для SEMI_AUTO, 0 для остальных. Response: `201` |
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
| POST | `/api/workspaces/{workspaceId}/pricing/runs/trigger` | PRICING_MANAGER, ADMIN, OWNER | Trigger manual pricing runs for all connections with active policies. No body. Returns `List<PricingRunResponse>`. Skips connections with run already in progress |
| POST | `/api/workspaces/{workspaceId}/pricing/runs` | PRICING_MANAGER, ADMIN, OWNER | Trigger manual pricing run for specific connection. Body: `{ connectionId }`. Legacy endpoint |
| GET | `/api/workspaces/{workspaceId}/pricing/runs` | Any role | Paginated. Filters: `?connectionId=...&status=...&from=...&to=...` |
| GET | `/api/workspaces/{workspaceId}/pricing/runs/{runId}` | Any role | Детали run: status, counts, timing |
| POST | `/api/workspaces/{workspaceId}/pricing/runs/{runId}/resume` | PRICING_MANAGER, ADMIN, OWNER | Resume PAUSED run (blast radius override). PAUSED → IN_PROGRESS, ON_HOLD actions → APPROVED |
| POST | `/api/workspaces/{workspaceId}/pricing/runs/{runId}/cancel` | PRICING_MANAGER, ADMIN, OWNER | Cancel PAUSED run. PAUSED → CANCELLED, ON_HOLD actions → CANCELLED |

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

### AI Pricing

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| POST | `/api/workspaces/{workspaceId}/pricing/advisor/{offerId}` | PRICING_MANAGER, ADMIN, OWNER | AI-совет по ценообразованию для offer. Response: `{ advice, generatedAt, cachedUntil, error }`. Cache: Caffeine, TTL 24h |
| GET | `/api/workspaces/{workspaceId}/pricing/insights` | Все роли | Список проактивных AI-инсайтов. Фильтры: `insightType`, `acknowledged`. Response: `Page<PricingInsightResponse>` |
| GET | `/api/workspaces/{workspaceId}/pricing/insights/count` | Все роли | Количество непрочитанных инсайтов. Response: `{ count }` |
| POST | `/api/workspaces/{workspaceId}/pricing/insights/{insightId}/acknowledge` | Все роли | Отметить инсайт как прочитанный |

### Competitors

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| POST | `/api/workspaces/{workspaceId}/competitors/match` | PRICING_MANAGER, ADMIN, OWNER | Создать привязку конкурента к offer |
| GET | `/api/workspaces/{workspaceId}/competitors/matches` | Все роли | Список привязок конкурентов для workspace |
| POST | `/api/workspaces/{workspaceId}/competitors/observations` | PRICING_MANAGER, ADMIN, OWNER | Добавить наблюдение цены конкурента |
| POST | `/api/workspaces/{workspaceId}/competitors/upload-csv` | PRICING_MANAGER, ADMIN, OWNER | Массовая загрузка наблюдений из CSV |

## Связанные модули

- [Analytics & P&L](analytics-pnl.md) — derived signals через signal assembler
- [ETL Pipeline](etl-pipeline.md) — canonical state для decision-grade reads
- [Execution](execution.md) — action lifecycle после decision
- [Promotions](promotions.md) — Promo guard читает canonical participation_status; shared signal assembler
- [Seller Operations](seller-operations.md) — price journal, recommendations UI, impact preview UI
- Детальные write-контракты: [Write Contracts](../provider-api-specs/write-contracts.md)
- [Bulk Operations & Draft Mode](../features/2026-03-31-bulk-operations-draft-mode.md) — Phase E: bulk manual pricing, draft mode, bulk cost update
