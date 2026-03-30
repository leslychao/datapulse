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
| **Action Scheduling** | decision = CHANGE | `price_action` (PENDING_APPROVAL) | Нет |

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

### Стратегии Phase G (будущее)

VELOCITY_ADAPTIVE, STOCK_BALANCING, COMPOSITE — требуют накопленных исторических данных.

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
  execution_mode            ENUM (RECOMMENDATION, SEMI_AUTO, FULL_AUTO)
  approval_timeout_hours    INT DEFAULT 72
  priority                  INT DEFAULT 0
  created_by                BIGINT FK → app_user
  created_at                TIMESTAMPTZ
  updated_at                TIMESTAMPTZ
```

Key constraints — колонки (SQL-фильтрация). Guard config, strategy params — JSONB (расширяемость).

## Назначение политик на товары

```
price_policy_assignment:
  id                        BIGSERIAL PK
  price_policy_id           BIGINT FK → price_policy
  marketplace_connection_id BIGINT FK → marketplace_connection
  scope_type                ENUM (CONNECTION, CATEGORY, SKU)
  category_id               BIGINT (nullable)
  marketplace_offer_id      BIGINT (nullable, FK → marketplace_offer)
```

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
| `current_price` | `canonical_price_snapshot` | PostgreSQL | Все стратегии |
| `cogs` | `cost_profile` (SCD2) | PostgreSQL | TARGET_MARGIN |
| `product_status` | `marketplace_offer` | PostgreSQL | Eligibility |
| `available_stock` | `canonical_stock_snapshot` | PostgreSQL | Guard |
| `manual_lock` | `manual_price_lock` | PostgreSQL | Eligibility |
| `avg_commission_pct` | fact_finance | ClickHouse | TARGET_MARGIN |
| `avg_logistics_per_unit` | fact_finance | ClickHouse | TARGET_MARGIN |
| `return_rate_pct` | fact_returns / fact_sales | ClickHouse | TARGET_MARGIN |
| `ad_cost_ratio` | fact_advertising_costs / revenue | ClickHouse | TARGET_MARGIN |
| `last_price_change_at` | `price_decision` | PostgreSQL | Frequency guard |
| `price_change_history` | `price_decision` | PostgreSQL | Volatility guard |
| `data_freshness` | `marketplace_sync_state` | PostgreSQL | Stale data guard |

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
| **Promo guard** | Товар в активном промо | Включён | Да |
| **Manual lock guard** | Ручная блокировка | Включён | Нет |
| **Stock-out guard** | Остатки = 0 | Включён | Да |

Порядок оптимизирован по стоимости (дешёвые первыми). Short-circuit: первый сработавший → SKIP.

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

```
price_decision:
  id, workspace_id, marketplace_offer_id, price_policy_id
  decision_type             ENUM (CHANGE, SKIP, HOLD)
  current_price, target_price, price_change_amount, price_change_pct
  strategy_type, strategy_raw_price
  signal_snapshot           JSONB
  constraints_applied       JSONB
  guards_evaluated          JSONB
  skip_reason               VARCHAR
  explanation_summary       TEXT
  execution_mode            ENUM
  created_at                TIMESTAMPTZ
```

### Decision → Action

- CHANGE + SEMI_AUTO → `price_action` PENDING_APPROVAL
- CHANGE + FULL_AUTO → `price_action` APPROVED
- CHANGE + RECOMMENDATION → НЕ создаёт action; рекомендация в UI
- SKIP → сохраняется для аудита
- HOLD → недостаточность данных

### Retention

| Decision type | Retention |
|---------------|-----------|
| CHANGE (с action) | Бессрочно |
| CHANGE (recommendation) | 90 дней |
| SKIP | 30 дней |

Партиционирование `price_decision` по `created_at` (monthly).

## Execution mode и уровни автоматизации

| Mode | Описание | Действие |
|------|----------|----------|
| **RECOMMENDATION** | Показывает рекомендацию | Decision сохраняется; оператор вручную создаёт action |
| **SEMI_AUTO** | Создаёт action PENDING_APPROVAL | Оператор одобряет или отклоняет |
| **FULL_AUTO** | Создаёт action APPROVED | Контроль через guards; failed → alert |

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

| Триггер | Частота |
|---------|---------|
| Post-sync | После успешного ETL sync (1-4 раза в день) |
| Manual | По требованию |
| Schedule | Configurable cron |
| Policy change | При изменении/активации policy |

**Инвариант:** pricing run для connection X не запускается, пока для того же connection X есть ETL `job_execution` в статусе `IN_PROGRESS`. Post-sync trigger создаёт pricing run только после успешного завершения ETL sync. Manual и scheduled триггеры проверяют отсутствие активного sync перед запуском. Если есть active ETL → pricing run отложен (запланирован after sync completion).

### Batch processing

```
1. Resolve effective policies for all marketplace_offers in connection
2. Filter eligible offers
3. Batch signal assembly (one ClickHouse query per signal type)
4. Per-SKU: strategy → constraints → guards → decision → explanation
5. Batch insert decisions
6. Batch create actions (for CHANGE decisions with SEMI_AUTO/FULL_AUTO)
```

## Связанные модули

- [Analytics & P&L](analytics-pnl.md) — derived signals через signal assembler
- [ETL Pipeline](etl-pipeline.md) — canonical state для decision-grade reads
- [Execution](execution.md) — action lifecycle после decision
- [Seller Operations](seller-operations.md) — price journal, recommendations UI
- Детальные write-контракты: [Write Contracts](../provider-api-specs/write-contracts.md)
