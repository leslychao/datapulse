# Datapulse — Анализ архитектуры ценообразования

**Статус:** accepted
**Фаза:** C — Pricing (Phase C в delivery plan)
**Зависимости:** canonical truth (Phase A), P&L truth (Phase B)

---

## 1. Текущее состояние

### Что уже определено

| Аспект | Где описано | Что зафиксировано |
|--------|------------|-------------------|
| Pipeline шаги | `functional-capabilities.md` §5 | Eligibility → Signal Assembly → Strategy Evaluation → Constraint Resolution → Guard Pipeline → Decision → Explanation → Action Scheduling |
| Bounded context | `data-architecture.md` | Pricing владеет policies, strategies, constraints, decisions, explanations, action intents |
| Execution lifecycle | `execution-and-reconciliation.md` | PENDING_APPROVAL → ... → SUCCEEDED/FAILED; CAS guards, retry, reconciliation |
| Write contracts | `write-contracts.md` | WB async (upload task + poll), Ozon sync; reconciliation NOT implemented |
| Таблицы PostgreSQL | `data-architecture.md` | `price_policy` (strategy config как JSONB), `price_decision`, `price_action`, `manual_price_lock` |
| Входы decision | `data-architecture.md` | Current state из canonical (PostgreSQL), derived signals из analytics (ClickHouse) через signal assembler |
| Permissions | `non-functional-architecture.md` | pricing manager: policy config, approval, auto-execution; operator: hold/lock |

### Что НЕ определено (и требует проработки)

1. Модель `price_policy` — поля, семантика, lifecycle
2. Типы стратегий ценообразования — какие, как работают, формулы
3. Модель назначения политик на товары — scope, специфичность, разрешение конфликтов
4. Signal assembler — какие сигналы, откуда, как собираются
5. Constraints — типы, композиция, приоритет
6. Guards — типы, блокирующие vs предупреждающие
7. Eligibility — критерии допуска SKU к автоматическому ценообразованию
8. Execution mode — уровни автоматизации, переход между ними
9. Pricing-специфичные особенности WB и Ozon — как стратегия транслируется в write payload
10. Decision и explanation — модель, хранение, аудит

---

## 2. Порядок pipeline

### Согласованный порядок

> Исходная проблема (Constraints и Guards до Strategy) была выявлена при анализе и устранена.
> `functional-capabilities.md` обновлён и содержит корректный порядок.

### Порядок шагов

```
Eligibility → Signal Assembly → Strategy Evaluation → Constraint Resolution → Guard Pipeline → Decision → Explanation → Action Scheduling
```

| Шаг | Вход | Выход | Может прервать? |
|-----|------|-------|-----------------|
| **Eligibility** | SKU + policy assignment | eligible / skip (reason) | Да — SKU без policy, locked, в промо, stale data |
| **Signal Assembly** | eligible SKU | `PricingSignalSet` | Да — missing critical signal (no COGS) |
| **Strategy Evaluation** | signals + strategy config | raw target price | Нет — всегда выдаёт результат |
| **Constraint Resolution** | raw target price + constraints | clamped target price | Нет — корректирует, не блокирует |
| **Guard Pipeline** | clamped price + context | pass / block (reason) | Да — margin breach, frequency, volatility |
| **Decision** | всё выше | CHANGE / SKIP / HOLD | Terminal |
| **Explanation** | decision + все inputs | explanation record | Нет |
| **Action Scheduling** | decision = CHANGE | `price_action` (PENDING_APPROVAL) | Нет |

---

## 3. Стратегии ценообразования

### Контекст

Маркетплейсы WB и Ozon **не предоставляют** через API данные о ценах конкурентов. Стратегии строятся на собственных данных продавца: себестоимость, расходы маркетплейса, скорость продаж, остатки.

### Решение: две стратегии для Phase C

MARKUP как отдельная стратегия **не включается**. TARGET_MARGIN покрывает MARKUP как частный случай: при `commission_source = MANUAL, commission_manual_pct = 0` и аналогичных нулевых cost rates формула TARGET_MARGIN вырождается в наценку от себестоимости. Математически: 150% наценка = 60% маржа (`target_margin = markup / (1 + markup)`). На уровне UI допускается "упрощённый режим ввода" — поле "наценка %", которое конвертируется в target_margin. Но стратегия в системе — одна.

#### 3.1. TARGET_MARGIN — целевая маржинальность (основная)

Самая востребованная стратегия для селлера. «Хочу 25% маржи на этот товар после всех расходов».

**Формула:**

```
effective_cost_rate = commission_pct + logistics_pct + return_adjustment_pct + ad_cost_pct
target_price = COGS / (1 − target_margin_pct − effective_cost_rate)
```

**Пример:**
- COGS = 500 ₽
- target_margin = 25%
- commission = 15%, logistics = 5%, returns = 2%, ads = 3% → effective_cost_rate = 25%
- target_price = 500 / (1 − 0.25 − 0.25) = 500 / 0.50 = 1000 ₽
- Проверка: revenue 1000 − commission 150 − logistics 50 − returns 20 − ads 30 − COGS 500 = 250 ₽ = 25% margin

**Параметры стратегии (strategy_params JSONB):**

| Параметр | Тип | Описание |
|----------|-----|----------|
| `target_margin_pct` | BigDecimal | Целевая маржа (0.25 = 25%) |
| `commission_source` | enum | `AUTO` / `MANUAL` / `AUTO_WITH_MANUAL_FALLBACK` — откуда брать ставку комиссии |
| `commission_manual_pct` | BigDecimal | Ручная ставка комиссии (используется при source = MANUAL или как fallback) |
| `commission_lookback_days` | int | Период для historical avg (default: 30) |
| `commission_min_transactions` | int | Минимум транзакций для reliable historical avg (default: 5) |
| `logistics_source` | enum | `AUTO` / `MANUAL` / `AUTO_WITH_MANUAL_FALLBACK` |
| `logistics_manual_amount` | BigDecimal | Ручная сумма логистики за единицу |
| `logistics_lookback_days` | int | Период для historical avg (default: 30) |
| `logistics_min_transactions` | int | Минимум транзакций (default: 5) |
| `include_return_adjustment` | boolean | Учитывать return rate в расчёте |
| `include_ad_cost` | boolean | Учитывать advertising cost ratio |
| `rounding_step` | BigDecimal | Шаг округления (default: 10) |
| `rounding_direction` | enum | `FLOOR` / `NEAREST` / `CEIL` (default: FLOOR) |

**Commission source — каскадный fallback (для `AUTO_WITH_MANUAL_FALLBACK`, default):**

```
1. Historical per-SKU (fact_finance.commission / fact_finance.revenue за lookback_days, ≥ min_transactions)
     ↓ если недостаточно данных
2. Historical per-category (все SKU категории на этом connection)
     ↓ если недостаточно данных
3. Manual value из commission_manual_pct (seller's estimate)
     ↓ если не задано
4. Decision = SKIP, reason = "Невозможно оценить ставку комиссии"
```

Та же цепочка применяется для logistics, return rate, ad cost ratio.

- `AUTO` — только historical, нет данных → SKIP
- `MANUAL` — всегда ручное значение, игнорировать historical
- `AUTO_WITH_MANUAL_FALLBACK` (default) — historical → manual → SKIP

Тарифная сетка маркетплейса не используется: опубликованный тариф отличается от фактической комиссии (промо-бонусы, acquiring, договорные условия). Поддержка тарифного справочника — непропорциональные усилия для Phase C.

**Источники cost rates:**

| Rate | Historical source (ClickHouse) | Fallback chain | Период |
|------|-------------------------------|----------------|--------|
| Commission % | `fact_finance.marketplace_commission_amount / fact_finance.revenue_amount` по SKU | → по категории → manual → SKIP | Скользящее N дней (default 30) |
| Logistics per unit | `fact_finance.logistics_cost_amount / COUNT(DISTINCT posting_id)` по SKU | → по категории → manual → SKIP | Скользящее N дней |
| Return rate % | `fact_returns / fact_sales` по SKU | → по категории → 0% (ignore) | Скользящее N дней |
| Ad cost ratio | `fact_advertising_costs / fact_finance.revenue_amount` по product | → 0% (ignore) | Скользящее N дней |

#### 3.2. PRICE_CORRIDOR — ценовой коридор (ограничительная)

Не вычисляет цену с нуля, а ограничивает **текущую цену** диапазоном. Если цена вышла за границы коридора — возвращает в диапазон.

**Логика:**

```
if current_price < min_price → target_price = min_price
if current_price > max_price → target_price = max_price
else → target_price = current_price (no change)
```

**Параметры:**

| Параметр | Тип | Описание |
|----------|-----|----------|
| `min_price` | BigDecimal | Минимальная цена |
| `max_price` | BigDecimal | Максимальная цена |

Подходит для товаров, где селлер хочет зафиксировать диапазон и не трогать цену, пока она внутри.

### Стратегии для будущих фаз (Phase G — Intelligence)

| Стратегия | Суть | Зависимости |
|-----------|------|-------------|
| VELOCITY_ADAPTIVE | Высокая скорость продаж → поднять цену, низкая → опустить | Reliable velocity signal, price elasticity model |
| STOCK_BALANCING | Overstock → снижение для расчистки, near stockout → повышение | Reliable days_of_cover, lead time config |
| COMPOSITE | Комбинация нескольких стратегий с весами | Все вышеперечисленные |

**Обоснование отложить:** velocity-adaptive и stock-balancing требуют накопленных исторических данных и настроенных пороговых значений. На Phase C данных может быть недостаточно для надёжных решений. TARGET_MARGIN даёт немедленную ценность без исторических зависимостей.

---

## 4. Модель price_policy

### Рекомендация: Policy = стратегия + ограничения + guards + scope + execution mode

Один `price_policy` — одно правило ценообразования. Привязывается к scope (на что действует).

### Структура таблицы

```
price_policy:
  id                        BIGSERIAL PK
  workspace_id              BIGINT FK → workspace
  name                      VARCHAR         -- "Базовая маржа WB", "Электроника Ozon 20%"
  status                    ENUM            -- DRAFT, ACTIVE, PAUSED, ARCHIVED
  
  -- Стратегия
  strategy_type             ENUM            -- TARGET_MARGIN, PRICE_CORRIDOR
  strategy_params           JSONB           -- параметры стратегии (зависят от типа)
  
  -- Ограничения (hard limits)
  min_margin_pct            DECIMAL         -- минимальная допустимая маржа (nullable)
  max_price_change_pct      DECIMAL         -- макс. изменение цены за одно решение (nullable)
  min_price                 DECIMAL         -- абсолютный минимум цены (nullable)
  max_price                 DECIMAL         -- абсолютный максимум цены (nullable)
  
  -- Guard config
  guard_config              JSONB           -- { frequency_hours: 24, volatility_window_days: 7, ... }
  
  -- Execution mode
  execution_mode            ENUM            -- RECOMMENDATION, SEMI_AUTO, FULL_AUTO
  approval_timeout_hours    INT DEFAULT 72  -- timeout PENDING_APPROVAL → EXPIRED
  
  -- Приоритет (для разрешения конфликтов)
  priority                  INT DEFAULT 0   -- выше = важнее
  
  -- Аудит
  created_by                BIGINT FK → app_user
  created_at                TIMESTAMPTZ
  updated_at                TIMESTAMPTZ
```

### Дизайн-решение: constraints как колонки vs JSONB

**Рекомендация: ключевые constraints — колонки, guard config — JSONB.**

Обоснование:
- `min_margin_pct`, `max_price_change_pct`, `min_price`, `max_price` — фиксированный набор, нужен для SQL-фильтрации и отчётности → **колонки**
- Guard config (частота, волатильность, пороги) — может расширяться → **JSONB**
- Strategy params — зависят от типа стратегии, полиморфные → **JSONB**

### Guard config (JSONB) — структура по умолчанию

```json
{
  "margin_guard_enabled": true,
  "frequency_guard_enabled": true,
  "frequency_limit_hours": 24,
  "volatility_guard_enabled": true,
  "volatility_max_reversals": 3,
  "volatility_window_days": 7,
  "stale_data_guard_enabled": true,
  "stale_data_max_hours": 24,
  "promo_guard_enabled": true
}
```

---

## 5. Модель назначения политик на товары

### Альтернативы

| Вариант | Суть | Плюсы | Минусы |
|---------|------|-------|--------|
| **A. Scope в отдельной таблице** | `price_policy_assignment(policy_id, scope_type, scope_id)` | Гибко, одна policy → N scopes | Лишняя таблица, JOINы |
| **B. Scope внутри policy** | Policy содержит `marketplace_connection_id` + optional category/SKU | Просто, одна policy = один scope | Дублирование policy при разных scopes |
| **C. Hierarchical assignment** | Policy привязана к уровню (connection → category → SKU), SKU получает ближайшую | Интуитивно как CSS specificity | Сложный resolution query |

### Рекомендация: Вариант A (scope в отдельной таблице)

```
price_policy_assignment:
  id                        BIGSERIAL PK
  price_policy_id           BIGINT FK → price_policy
  marketplace_connection_id BIGINT FK → marketplace_connection  -- обязательно
  scope_type                ENUM    -- CONNECTION, CATEGORY, SKU
  category_id               BIGINT  -- nullable, для scope_type = CATEGORY
  marketplace_offer_id      BIGINT  -- nullable, для scope_type = SKU (FK → marketplace_offer)
```

### Разрешение конфликтов: специфичность + приоритет

При нескольких подходящих политиках для SKU:

```
1. SKU-level policy  (scope_type = SKU)     → специфичность = 3 (высшая)
2. Category-level    (scope_type = CATEGORY) → специфичность = 2
3. Connection-level  (scope_type = CONNECTION) → специфичность = 1 (базовая)

Если на одном уровне несколько политик → побеждает policy.priority (выше = важнее).
Если priority одинаковый → policy с наименьшим id (first created wins, deterministic).
```

**Результат:** для каждого marketplace_offer в каждый момент времени существует **ровно одна** эффективная policy (или никакой).

### Пример назначения

```
Policy "Базовая маржа WB 20%"       → CONNECTION-level, WB connection #1
Policy "Электроника WB 15%"          → CATEGORY-level, category "Электроника"
Policy "Бестселлер ABC-123 ценовой коридор" → SKU-level, marketplace_offer #456
```

SKU "ABC-123" в категории "Электроника" на WB → попадает под все три. Побеждает SKU-level (специфичность 3).

SKU "XYZ-789" в категории "Электроника" на WB → попадает под connection + category. Побеждает category (специфичность 2).

SKU "QWE-111" в категории "Одежда" на WB → попадает только под connection. Применяется "Базовая маржа WB 20%".

---

## 6. Signal Assembly

### Архитектурный контекст

Signal assembler собирает входные данные для стратегии из двух источников:
- **Canonical state** (PostgreSQL) — current state: цены, остатки, каталог, COGS
- **Analytics** (ClickHouse) — derived signals: velocity, commission rates, return rates, ad costs

Pricing pipeline **не читает ClickHouse напрямую**. Signal assembler — единственная точка, через которую derived signals попадают в pricing.

### Каталог сигналов

| Signal | Источник | Store | Обязательность | Используется |
|--------|----------|-------|---------------|-------------|
| `current_price` | `canonical_price_snapshot` | PostgreSQL | Обязательный | Все стратегии |
| `cogs` | `cost_profile` (SCD2) | PostgreSQL | Обязательный для TARGET_MARGIN | TARGET_MARGIN |
| `product_status` | `marketplace_offer` | PostgreSQL | Обязательный | Eligibility |
| `available_stock` | `canonical_stock_snapshot` | PostgreSQL | Опциональный | Guard (stock-awareness) |
| `promo_participation` | canonical / promo tables | PostgreSQL | Опциональный | Eligibility / Guard |
| `manual_lock` | `manual_price_lock` | PostgreSQL | Обязательный | Eligibility |
| `avg_commission_pct` | `fact_finance.commission / fact_finance.revenue` | ClickHouse | Опциональный (fallback to manual) | TARGET_MARGIN |
| `avg_logistics_per_unit` | `fact_finance.logistics / COUNT(postings)` | ClickHouse | Опциональный (fallback to manual) | TARGET_MARGIN |
| `return_rate_pct` | `fact_returns / fact_sales` | ClickHouse | Опциональный | TARGET_MARGIN (adjustment) |
| `ad_cost_ratio` | `fact_advertising_costs / revenue` | ClickHouse | Опциональный | TARGET_MARGIN (adjustment) |
| `sales_velocity` | `fact_sales` rolling avg | ClickHouse | Опциональный | Future: VELOCITY_ADAPTIVE |
| `days_of_cover` | `mart_inventory_analysis` | ClickHouse | Опциональный | Future: STOCK_BALANCING |
| `last_price_change_at` | `price_decision` | PostgreSQL | Обязательный | Frequency guard |
| `price_change_history` | `price_decision` (recent) | PostgreSQL | Обязательный | Volatility guard |
| `data_freshness` | `marketplace_sync_state` | PostgreSQL | Обязательный | Stale data guard |

### Контракт Signal Assembler

```
Input:  marketplace_offer_id + price_policy (с strategy type)
Output: PricingSignalSet {
          currentPrice, cogs, productStatus, availableStock,
          promoActive, manualLocked, dataFreshness,
          avgCommissionPct, avgLogisticsPerUnit, returnRatePct, adCostRatio,
          salesVelocity, daysOfCover,
          lastPriceChangeAt, recentPriceChanges[]
        }
```

Signal assembler определяет, какие сигналы нужны для конкретного типа стратегии, и запрашивает только их. Ненужные сигналы = null.

### Fallback при отсутствии derived signal

Cascading fallback chain (для `commission_source = AUTO_WITH_MANUAL_FALLBACK`, default):

| Signal | Fallback 1 | Fallback 2 | Fallback 3 | Fallback 4 |
|--------|-----------|-----------|-----------|-----------|
| Commission % | Historical per-SKU (lookback_days, ≥ min_transactions) | Historical per-category | Manual из strategy_params | SKIP |
| Logistics/unit | Historical per-SKU | Historical per-category | Manual из strategy_params | SKIP |
| Return rate % | Historical per-SKU | Historical per-category | 0% (ignore) | — |
| Ad cost ratio | Historical per-product | 0% (ignore) | — | — |

- `commission_source = AUTO` → только historical, нет данных → SKIP
- `commission_source = MANUAL` → всегда ручное значение из strategy_params
- `commission_source = AUTO_WITH_MANUAL_FALLBACK` (default) → cascading chain выше

Если critical signal (COGS для TARGET_MARGIN) отсутствует — decision = SKIP с объяснением "Себестоимость не задана для SKU". Explanation фиксирует, какой source использован для каждого rate: "комиссия 14.7% (historical avg 30d, 47 транзакций)" или "комиссия 15.0% (manual fallback)".

---

## 7. Constraints (ограничения)

### Назначение

Constraints — **жёсткие ограничения**, которые корректируют target price после вычисления стратегией. Constraints не блокируют — они clamp.

### Типы constraints

| Constraint | Описание | Источник | Композиция |
|------------|----------|----------|------------|
| `min_margin` | Цена не должна давать маржу ниже порога | `price_policy.min_margin_pct` | Floor: price = max(target, margin_floor_price) |
| `max_price_change` | Одно изменение не может превышать ±X% | `price_policy.max_price_change_pct` | Clamp: target clamped to [current × (1−X%), current × (1+X%)] |
| `min_price` | Абсолютный минимум | `price_policy.min_price` | Floor |
| `max_price` | Абсолютный максимум | `price_policy.max_price` | Ceiling |
| `marketplace_min_price` | Минимальная цена маркетплейса | Ozon: `min_price` field | Floor |
| `rounding` | Округление до шага (Phase C, обязательно для доверия к системе) | `strategy_params.rounding_step` + `rounding_direction` | Round (FLOOR default — безопаснее, цена чуть ниже расчётной) |

### Округление цен

Включено в Phase C. Без округления система выдаёт цены вида 1247 ₽ — подрывает доверие seller'а к рекомендациям. Простой step-based rounding покрывает 90% потребности.

| Параметр | Значения | Default |
|----------|----------|---------|
| `rounding_step` | 1, 10, 50, 100 | 10 |
| `rounding_direction` | `FLOOR` / `NEAREST` / `CEIL` | `FLOOR` |

`FLOOR` — default, безопаснее: цена чуть ниже расчётной, margin guard не сработает из-за округления.

Примеры (step=10, FLOOR): 1247 → 1240, 993 → 990, 2518 → 2510.

"Психологические" окончания (990, 1490, 2990) — magnetic pricing. Значительно сложнее (определение ближайшего "магнита" по ценовому диапазону). Отложено на Phase G.

### Порядок применения

```
raw_target (из стратегии)
  → apply min_price floor
  → apply max_price ceiling
  → apply max_price_change clamp (relative to current_price)
  → apply min_margin floor (recalculate price from margin threshold)
  → apply marketplace_min_price floor
  → apply rounding
  → final constrained_target
```

**Принцип:** при конфликте constraints побеждает **наиболее ограничительный** (most restrictive wins). Если min_margin_pct требует цену 1200, а max_price_change_pct ограничивает подъём до 1100 — результат 1100 (max_change ограничивает подъём), но при этом decision explanation фиксирует: "min margin не достигнута из-за ограничения max_price_change".

---

## 8. Guards (блокирующие проверки)

### Назначение

Guards — **блокирующие проверки** после constraint resolution. Guard может заблокировать решение целиком (decision = SKIP). В отличие от constraints, guards не корректируют цену — они отвечают pass/block.

### Типы guards

| Guard | Блокирует когда | Default config | Отключаемый? |
|-------|-----------------|----------------|-------------|
| **Margin guard** | Constrained price даёт margin < `min_margin_pct` | Включён | Да |
| **Frequency guard** | Последнее изменение цены было < N часов назад | 24 часа | Да |
| **Volatility guard** | Направление изменения цены развернулось > N раз за период | 3 разворота / 7 дней | Да |
| **Stale data guard** | Данные canonical model старше N часов | 24 часа | Нет (safety-critical) |
| **Promo guard** | Товар участвует в активной промо-акции маркетплейса | Включён | Да |
| **Manual lock guard** | Оператор поставил ручную блокировку цены | Включён | Нет (respect operator intent) |
| **Stock-out guard** | Остатки = 0 (нет смысла менять цену) | Включён | Да |

### Порядок проверки

Порядок оптимизирован по стоимости (дешёвые проверки первыми):

```
1. Manual lock guard       — O(1), PostgreSQL lookup (cached)
2. Promo guard             — O(1), canonical state
3. Stock-out guard         — O(1), canonical state
4. Stale data guard        — O(1), sync state
5. Frequency guard         — O(1), last decision lookup
6. Volatility guard        — O(N), recent decisions scan
7. Margin guard            — O(1), arithmetic check
```

Short-circuit: первый сработавший guard → decision = SKIP, остальные не проверяются.

### Результат guard

```
GuardResult {
  guard_type: FREQUENCY_GUARD
  passed: false
  reason: "Последнее изменение цены было 4 часа назад, лимит 24 часа"
  blocking: true
}
```

---

## 9. Eligibility (критерии допуска)

### Что проверяет eligibility

Eligibility определяет, **подлежит ли SKU автоматическому ценообразованию вообще** — до загрузки сигналов и вычислений.

| Критерий | Проверка | Skip reason |
|----------|----------|-------------|
| Активная policy | Есть ли ACTIVE policy assignment для этого SKU? | "Нет активной ценовой политики" |
| Товар активен | marketplace_offer.status = ACTIVE | "Товар неактивен на маркетплейсе" |
| Manual lock | Нет записи в `manual_price_lock` | "Ручная блокировка цены" |
| COGS задана (для margin-based) | cost_profile exists and valid | "Себестоимость не задана" |
| Connection active | marketplace_connection.status = ACTIVE | "Подключение к маркетплейсу неактивно" |

Eligibility — быстрый фильтр. Прошедшие eligibility SKU идут в signal assembly. Не прошедшие — SKIP с объяснением, без дальнейшей обработки.

---

## 10. Decision и Explanation

### Модель decision

```
price_decision:
  id                        BIGSERIAL PK
  workspace_id              BIGINT FK
  marketplace_offer_id      BIGINT FK → marketplace_offer
  price_policy_id           BIGINT FK → price_policy
  
  -- Результат
  decision_type             ENUM            -- CHANGE, SKIP, HOLD
  current_price             DECIMAL
  target_price              DECIMAL         -- nullable если SKIP
  price_change_amount       DECIMAL         -- nullable если SKIP
  price_change_pct          DECIMAL         -- nullable если SKIP
  
  -- Стратегия
  strategy_type             ENUM
  strategy_raw_price        DECIMAL         -- цена до constraints
  
  -- Traceability
  signal_snapshot           JSONB           -- snapshot входных сигналов
  constraints_applied       JSONB           -- [{type, value, effect}]
  guards_evaluated          JSONB           -- [{guard, passed, reason}]
  skip_reason               VARCHAR         -- nullable, заполняется при SKIP
  
  -- Explanation
  explanation_summary       TEXT            -- человекочитаемое объяснение
  
  -- Метаданные
  execution_mode            ENUM            -- RECOMMENDATION, SEMI_AUTO, FULL_AUTO
  created_at                TIMESTAMPTZ
```

### Explanation — формат

Explanation строится программно из результатов pipeline:

```
"Стратегия TARGET_MARGIN (целевая маржа 25%):
 • Себестоимость: 500 ₽
 • Расчётная комиссия: 15% (historical avg 30d)
 • Расчётная логистика: 50 ₽/ед (historical avg 30d)
 • Поправка на возвраты: 2%
 • Расчётная цена стратегии: 1000 ₽
 • Ограничение max_price_change (±10%): без эффекта (текущая 950 ₽, изменение +5.3%)
 • Округление до 10 ₽: 1000 ₽
 • Guard margin: passed (расчётная маржа 25.0% ≥ мин. 15%)
 • Guard frequency: passed (последнее изменение 48ч назад, лимит 24ч)
 • Решение: CHANGE 950 ₽ → 1000 ₽ (+50 ₽, +5.3%)"
```

### Decision → Action

- `decision_type = CHANGE` + `execution_mode = SEMI_AUTO` → создаётся `price_action` в состоянии `PENDING_APPROVAL`
- `decision_type = CHANGE` + `execution_mode = FULL_AUTO` → создаётся `price_action` в состоянии `APPROVED`
- `decision_type = CHANGE` + `execution_mode = RECOMMENDATION` → `price_action` НЕ создаётся; решение доступно в UI как рекомендация
- `decision_type = SKIP` → `price_action` НЕ создаётся; решение сохраняется в `price_decision` для аудита
- `decision_type = HOLD` → `price_action` НЕ создаётся; означает "решение не принято из-за недостаточности данных"

---

## 11. Marketplace-specific трансляция

### WB: price + discount модель

WB API принимает `price` (базовая) + `discount` (% скидки продавца). Покупатель видит: `price × (1 − discount / 100)`.

**Рекомендация:** pricing pipeline работает с **конечной ценой для покупателя**. Трансляция в WB-формат — ответственность adapter:

| Вариант | Описание | Рекомендация |
|---------|----------|-------------|
| **A. discount = 0 всегда** | target_price = WB price, discount = 0 | Текущее поведение. Просто, но убирает визуальную скидку |
| **B. Фиксированный discount %** | Seller задаёт discount %, adapter вычисляет base price | Позволяет показывать "скидку" покупателю |
| **C. Сохранение текущего discount** | Adapter читает текущий discount, пересчитывает base price | Сложнее, но сохраняет существующую скидку |

**Рекомендация для Phase C: Вариант A** (discount = 0). Упрощает модель, позволяет сосредоточиться на ценовой логике. Вариант B — расширение на Phase G.

### Ozon: price + old_price модель

Ozon принимает `price` (цена продажи) + `old_price` (зачёркнутая). Если `old_price` > 0 и больше price — покупатель видит "скидку".

**Рекомендация для Phase C: old_price = "0"** (текущее поведение). Управление визуальной скидкой — отдельная capability.

### Ozon: min_price и auto_action

Ozon позволяет задать `min_price` (минимум для автопромо) и `auto_action_enabled` (участие в автопромо Ozon).

**Рекомендация:** `auto_action_enabled = "DISABLED"`, `price_strategy_enabled = "DISABLED"`. Datapulse управляет ценой сам, Ozon не должен автоматически менять цены.

---

## 12. Execution mode и уровни автоматизации

### Три уровня

| Mode | Описание | Кому доступен | Действие |
|------|----------|---------------|----------|
| **RECOMMENDATION** | Система показывает рекомендацию, не создаёт action | Все роли (view) | Decision сохраняется; оператор может вручную создать action |
| **SEMI_AUTO** | Система создаёт action в PENDING_APPROVAL | pricing manager (config), operator (view) | Оператор/pricing manager одобряет или отклоняет |
| **FULL_AUTO** | Система создаёт action в APPROVED, автоматически уходит в execution | pricing manager (config) | Контроль через guards; failed actions → alert |

### Переход между уровнями

```
RECOMMENDATION → SEMI_AUTO: pricing manager включает через policy config
SEMI_AUTO → FULL_AUTO: pricing manager включает; требует подтверждение
FULL_AUTO → SEMI_AUTO: pricing manager переключает обратно (немедленно)
любой → RECOMMENDATION: снижение уровня автоматизации — всегда разрешено
```

### Safety gate для FULL_AUTO

FULL_AUTO доступен только если:
1. Policy была в SEMI_AUTO минимум N дней (configurable, default: 7)
2. Не было FAILED actions за последние N дней
3. Stale data guard НЕ отключён
4. Manual lock guard НЕ отключён
5. pricing manager явно подтверждает включение

---

## 13. Pricing run — когда запускается pipeline

### Триггеры

| Триггер | Описание | Частота |
|---------|----------|---------|
| **Post-sync** | После успешного ETL sync (PRICE_SNAPSHOT / SALES_FACT / FACT_FINANCE) | По расписанию sync (типично 1–4 раза в день) |
| **Manual** | Оператор/pricing manager нажимает "Пересчитать цены" | По требованию |
| **Schedule** | Configurable cron (например, каждый день в 08:00) | По расписанию |
| **Policy change** | При изменении/активации policy | По событию |

### Batch processing

Pricing pipeline обрабатывает **все eligible SKU для connection** за один run. Не per-SKU triggering (дорого, сложно).

```
Pricing Run:
  1. Resolve effective policies for all marketplace_offers in connection
  2. Filter eligible offers (eligibility check)
  3. Batch signal assembly (one ClickHouse query per signal type, not per SKU)
  4. Per-SKU: strategy → constraints → guards → decision → explanation
  5. Batch insert decisions
  6. Batch create actions (for CHANGE decisions with SEMI_AUTO/FULL_AUTO)
```

---

## 14. Принятые решения (resolved)

### D-1: MARKUP не включается (resolved)

**Решение:** Phase C содержит только TARGET_MARGIN + PRICE_CORRIDOR. MARKUP как отдельная стратегия не реализуется.

**Обоснование:** TARGET_MARGIN покрывает MARKUP как вырожденный случай (cost rates = 0). Две стратегии с перекрывающейся функциональностью создают ложный выбор. TARGET_MARGIN по умолчанию учит seller'а мыслить реальной маржой. На уровне UI допускается "упрощённый режим ввода" — поле "наценка %", конвертируемое в target_margin.

### D-2: Commission source — AUTO_WITH_MANUAL_FALLBACK (resolved)

**Решение:** cascading fallback chain — historical per-SKU → historical per-category → manual value → SKIP. Default source: `AUTO_WITH_MANUAL_FALLBACK`.

**Обоснование:** тарифная сетка маркетплейса не используется — опубликованный тариф отличается от фактической комиссии (промо-бонусы, acquiring, договорные условия). Для нового аккаунта seller задаёт manual estimate при создании policy; по мере накопления истории система автоматически переходит на реальные данные. Explanation фиксирует source каждого rate.

### D-3: Округление включено в Phase C (resolved)

**Решение:** step-based rounding включено. Default: `rounding_step = 10`, `rounding_direction = FLOOR`.

**Обоснование:** без округления система выдаёт цены вида 1247 ₽ — подрывает доверие seller'а к рекомендациям. FLOOR безопаснее — цена чуть ниже расчётной, margin guard не сработает из-за округления. "Психологические" окончания (990, 1490) — magnetic pricing, отложено на Phase G.

### D-4: Approval timeout — 72 часа (resolved)

**Решение:** default `approval_timeout_hours = 72`, configurable per policy.

**Обоснование:** 72 часа покрывает weekend (пятница 17:00 → понедельник 17:00). Данные ещё достаточно свежие (3 дня — приемлемое окно). При expiration следующий pricing run создаёт новый decision с актуальными данными — ничего не потеряно. Seller с daily workflow может поставить 24ч, seller с редкими проверками — 7 дней.

### D-5: Отдельные policies per connection (resolved)

**Решение:** policy assignment привязан к `marketplace_connection_id`. 5 WB-кабинетов = 5 отдельных policies. UX: "Clone policy to another connection".

**Обоснование:** расходы реально различаются между connections (разные комиссии, логистика, договорные условия). Workspace-level policies добавляют четвёртый уровень специфичности — overengineering. Изоляция: проблема в одной policy не влияет на другие connections. Дублирование решается UX-действием "Clone". Bulk edit — расширение на Phase G.

### D-6: Хранить все decisions включая SKIP (resolved)

**Решение:** `price_decision` хранит все типы (CHANGE, SKIP, HOLD). Дифференцированный retention.

| Decision type | Retention | Обоснование |
|---------------|-----------|-------------|
| CHANGE (с action) | Бессрочно | Аудит, price journal, history |
| CHANGE (recommendation) | 90 дней | Анализ эффективности рекомендаций |
| SKIP | 30 дней | Debugging, guard hit rates, метрики |

**Обоснование:** SKIP-decisions критичны для объяснимости ("почему система НЕ изменила цену?"), метрик (guard hit rates — обязательная метрика из NFR) и доверия (seller видит, что система оценила все SKU, а не "забыла" про часть). Объём управляем: ~10K SKU × daily run × 30 дней × ~1KB = ~300MB. Партиционирование `price_decision` по `created_at` (monthly).

### D-7: Pricing pipeline в pricing-worker (resolved)

Архитектура определяет `datapulse-pricing-worker` как отдельный runtime entrypoint. Pricing run запускается в нём. API entrypoint может только **инициировать** run (через outbox → RabbitMQ → pricing worker).

---

## 15. Рекомендуемый план реализации Phase C

### Step 1: Модель данных
- DDL для `price_policy` (strategy_type: TARGET_MARGIN / PRICE_CORRIDOR), `price_policy_assignment`, `price_decision`
- Адаптация `price_action` (уже упомянут в data-architecture)
- Партиционирование `price_decision` по `created_at` (monthly) — для retention management
- Liquibase миграции

### Step 2: Policy CRUD
- REST API для создания/редактирования/активации policies
- REST API для назначения policies на scope (CONNECTION / CATEGORY / SKU)
- REST API для клонирования policy на другой connection
- Effective policy resolution (specificity + priority)

### Step 3: Signal Assembler
- PostgreSQL signal reads (current state: prices, COGS, stock, locks, promo, freshness)
- ClickHouse signal reads (derived signals: commission avg, logistics avg, return rate, ad cost ratio)
- Cascading fallback chain (per-SKU → per-category → manual → SKIP) для `AUTO_WITH_MANUAL_FALLBACK`

### Step 4: Strategy Engine
- TARGET_MARGIN implementation (с cascading cost rate resolution)
- PRICE_CORRIDOR implementation
- Strategy registry (strategy_type → strategy bean)

### Step 5: Constraint + Guard Pipeline
- Constraint resolver (ordered application: min_price → max_price → max_change → min_margin → marketplace_min → rounding)
- Step-based rounding (FLOOR / NEAREST / CEIL)
- Guard pipeline (short-circuit evaluation, ordered by cost)
- GuardResult model

### Step 6: Decision Engine + Explainer
- Orchestrator: eligibility → signals → strategy → constraints → guards → decision
- Explanation builder (human-readable, с указанием source каждого rate)
- Batch decision persistence (CHANGE + SKIP + HOLD)

### Step 7: Pricing Run Orchestration
- Post-sync trigger
- Manual trigger (API → outbox → pricing worker)
- Scheduled trigger (configurable cron)
- Batch processing of eligible offers per connection
- Action creation for CHANGE decisions (PENDING_APPROVAL / APPROVED based on execution_mode)
- Approval timeout (72h default, configurable per policy)

### Step 8: Pricing API & UI endpoints
- Decision history (price journal) — все типы decisions
- Current recommendations (RECOMMENDATION mode decisions)
- Policy management screens (CRUD, clone, assign)
- SKIP-decision viewer (debug: "почему НЕ изменена цена?")

### Step 9: Retention management
- Cron job: удаление SKIP-decisions старше 30 дней
- Cron job: удаление CHANGE (recommendation) decisions старше 90 дней
- CHANGE (with action) decisions — бессрочно

---

## Связанные документы

- [Функциональные возможности](functional-capabilities.md) — pipeline, обязательные свойства
- [Архитектура данных](data-architecture.md) — canonical model, signal sources, scope Phase A/B, инварианты
- [Исполнение и сверка](execution-and-reconciliation.md) — action lifecycle после decision
- [Write contracts](provider-contracts/write-contracts.md) — WB/Ozon price write API
- [Нефункциональная архитектура](non-functional-architecture.md) — permissions, audit
