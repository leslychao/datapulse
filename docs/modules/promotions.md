# Модуль: Promotions

**Фаза:** D — Execution (promo evaluation + execution), E — Seller Operations (promo analytics in grid)
**Зависимости:** [ETL Pipeline](etl-pipeline.md) (promo discovery), [Analytics & P&L](analytics-pnl.md) (margin signals), [Pricing](pricing.md) (promo guard coordination), [Integration](integration.md) (write-адаптеры)
**Runtime:** datapulse-api (policy CRUD, manual decisions), datapulse-pricing-worker (evaluation pipeline), datapulse-executor-worker (promo action execution)

---

## Назначение

Управление участием товаров в промо-акциях маркетплейсов (WB, Ozon). Оценка экономической целесообразности, принятие решений об участии, контролируемое исполнение и анализ результатов. Промо-акции — инициатива маркетплейса; Datapulse решает, участвовать ли и с какими товарами.

## Ключевое отличие от Pricing

| Аспект | Pricing | Promotions |
|--------|---------|------------|
| Инициатор | Селлер (через policy) | Маркетплейс (через акцию) |
| Цена | Вычисляется стратегией | Диктуется или ограничивается маркетплейсом (`action_price`, `max_action_price`) |
| Temporal | Постоянный repricing cycle | Фиксированный период акции (`starts_at` → `ends_at`) с `freeze_at` |
| Decision | CHANGE / SKIP / HOLD | PARTICIPATE / DECLINE / PENDING_REVIEW |
| Write API | Price update endpoint | Promo activate/deactivate endpoint |

## Lifecycle

```
Discovery (ETL) → Canonical Promo Model → Evaluation Pipeline → Decision (activate / deactivate / decline) → Action Scheduling → Execution → Monitoring → Post-mortem
                                                    ↑ re-evaluation PARTICIPATING товаров (deactivation loop)
```

### Стадии

| Стадия | Ответственный модуль | Описание |
|--------|---------------------|----------|
| Discovery | [ETL Pipeline](etl-pipeline.md) | `PROMO_SYNC`: загрузка акций и eligible/participating products |
| Canonical Model | [ETL Pipeline](etl-pipeline.md#promo-canonical-entities) | `canonical_promo_campaign` / `canonical_promo_product` в PostgreSQL (source of truth — `PROMO_SYNC`) |
| Evaluation | **Promotions** | Оценка маржи, остатков, целесообразности участия |
| Decision | **Promotions** | PARTICIPATE / DECLINE / PENDING_REVIEW |
| Deactivation | **Promotions** | Re-evaluation PARTICIPATING товаров; DEACTIVATE при невыгодности |
| Action Scheduling | **Promotions** | Создание `promo_action` для activate/deactivate |
| Execution | **Promotions** (через [Integration](integration.md) adapters) | Write API вызов к маркетплейсу |
| Monitoring | [Seller Operations](seller-operations.md#promo-journal) | Promo Journal (данные из `canonical_promo_campaign`, `canonical_promo_product`, `promo_action`), working queues |
| Post-mortem | [Analytics & P&L](analytics-pnl.md) | `mart_promo_product_analysis` |

## Модель данных

### Таблицы PostgreSQL

| Таблица | Ответственность |
|---------|-----------------|
| `canonical_promo_campaign` | Каноническая акция маркетплейса (владелец — [ETL Pipeline](etl-pipeline.md#promo-canonical-entities)) |
| `canonical_promo_product` | Статус товара в акции (владелец — [ETL Pipeline](etl-pipeline.md#promo-canonical-entities); Promotions обновляет participation / decision fields) |
| `promo_policy` | Правила автоматического участия |
| `promo_policy_assignment` | Назначение политик на подключения/категории/товары |
| `promo_evaluation_run` | Batch-запуск оценки промо-участия |
| `promo_evaluation` | Результат оценки товар × акция |
| `promo_decision` | Решение об участии |
| `promo_action` | Действие (activate/deactivate) с lifecycle |
| `promo_action_attempt` | Per-attempt tracking для forensics |

### canonical_promo_campaign (owned by [ETL Pipeline](etl-pipeline.md))

Каноническое представление акции маркетплейса. Source of truth — `PROMO_SYNC` из ETL. Полная DDL — в [ETL Pipeline → Promo canonical entities](etl-pipeline.md#promo-canonical-entities).

ETL записывает данные из marketplace API. Promotions module читает для evaluation pipeline и обновляет `is_participating` после execution.

`status` обновляется ETL при каждом `PROMO_SYNC`. Lifecycle: UPCOMING → ACTIVE → FROZEN (если `freeze_at` задан и прошёл) → ENDED. **Между sync'ами `status` не обновляется** — это materialized field, не computed. Поэтому Promotions pipeline проверяет `freeze_at` напрямую (`freeze_at IS NULL OR freeze_at > NOW()`), а не полагается на `status ≠ FROZEN`.

**Stale campaign detection:** если кампания не возвращается в `PROMO_SYNC` (маркетплейс удалил акцию), её строка в PostgreSQL не обновляется — `synced_at` остаётся старым.

Stale detection выполняется **в рамках ETL** (post-sync cleanup), т.к. `canonical_promo_campaign` — ETL-owned таблица. ETL при каждом `PROMO_SYNC` проверяет:

```sql
SELECT * FROM canonical_promo_campaign
WHERE connection_id = :connectionId
  AND status IN ('UPCOMING', 'ACTIVE')
  AND synced_at < NOW() - INTERVAL '48 hours'
```

ETL переводит stale campaigns → `status = ENDED` и публикует `ETL_PROMO_CAMPAIGN_STALE` event.

**Promotions реагирует** на этот event (через listener): PENDING_APPROVAL / APPROVED promo_actions для stale campaign → EXPIRED. Alert `PROMO_CAMPAIGN_STALE` создаётся в Audit & Alerting.

Такое разделение сохраняет write boundary: ETL пишет в свою таблицу, Promotions пишет в `promo_action`.

**Dedup key:** `(connection_id, external_promo_id)` — unique.

### canonical_promo_product (owned by [ETL Pipeline](etl-pipeline.md))

Связь товар × акция. Обновляется из `PROMO_SYNC` (ETL) и из действий Datapulse (Promotions execution). Полная DDL — в [ETL Pipeline → Promo canonical entities](etl-pipeline.md#promo-canonical-entities).

`participation_status` обновляется из sync и из действий Datapulse:
- `ELIGIBLE` — товар может участвовать, но не участвует (Ozon candidates / WB inAction=false)
- `PARTICIPATING` — товар участвует (Ozon action_products / WB inAction=true)
- `DECLINED` — Datapulse решил не участвовать
- `REMOVED` — Datapulse удалил из акции
- `BANNED` — маркетплейс заблокировал участие (Ozon banned_products)
- `AUTO_DECLINED` — автоматически отклонён по promo_policy

### Write boundary: canonical_promo_product

ETL и Promotions записывают в одну таблицу. Поля разделены по ответственности; conflict resolution через conditional UPSERT.

**ETL пишет (при каждом PROMO_SYNC):**

Все поля canonical_promo_product, кроме `participation_decision_source` (только Promotions). Для `participation_status` ETL использует conditional UPSERT:

```sql
participation_status = CASE
  WHEN EXCLUDED.participation_status IN ('PARTICIPATING', 'BANNED')
    THEN EXCLUDED.participation_status
  WHEN canonical_promo_product.participation_decision_source IN ('AUTO', 'MANUAL')
    AND canonical_promo_product.participation_status IN ('DECLINED', 'REMOVED', 'AUTO_DECLINED')
    AND EXCLUDED.participation_status = 'ELIGIBLE'
    THEN canonical_promo_product.participation_status
  ELSE EXCLUDED.participation_status
END
```

**Правила merge:**

| DB status | Provider status (sync) | Результат | Обоснование |
|-----------|----------------------|-----------|-------------|
| ELIGIBLE | ELIGIBLE | ELIGIBLE | Обычный sync |
| ELIGIBLE | PARTICIPATING | PARTICIPATING | Marketplace подтвердил участие |
| ELIGIBLE | BANNED | BANNED | Marketplace заблокировал |
| DECLINED / REMOVED / AUTO_DECLINED | ELIGIBLE | **Preserve** DB value | Решение Datapulse сохраняется — маркетплейс не знает о нашем decline |
| DECLINED / REMOVED / AUTO_DECLINED | PARTICIPATING | PARTICIPATING | Marketplace override (участие активировано вне Datapulse — возможно вручную в ЛК) |
| DECLINED / REMOVED / AUTO_DECLINED | BANNED | BANNED | Marketplace заблокировал — приоритет выше Datapulse |
| PARTICIPATING | ELIGIBLE | ELIGIBLE | Marketplace отменил участие (deactivation confirmed) |
| PARTICIPATING | BANNED | BANNED | Marketplace заблокировал |

**Promotions пишет (после execution / evaluation):**

| Поле | Когда | Значение |
|------|-------|----------|
| `participation_status` | promo_action SUCCEEDED (activate) | `PARTICIPATING` |
| `participation_status` | promo_action SUCCEEDED (deactivate) | `REMOVED` |
| `participation_status` | Auto-decline по promo_policy | `AUTO_DECLINED` |
| `participation_status` | Manual decline | `DECLINED` |
| `participation_decision_source` | При любом обновлении Promotions | `AUTO` / `MANUAL` |

**Concurrency:** Promotions использует точечный UPDATE с CAS (`WHERE id = ? AND participation_status = ?`), не UPSERT. ETL использует conditional UPSERT. Оба не конфликтуют при нормальном timing (Promotions обновляет между sync'ами). При overlap: ETL conditional UPSERT сохраняет Datapulse decisions (CASE logic выше).

**Isolation level assumption:** корректность write boundary зависит от PostgreSQL `READ COMMITTED` (default). При concurrent UPDATE + UPSERT на одну строку: PostgreSQL перечитывает строку после блокировки (re-evaluation of `WHERE` clause). CAS `WHERE participation_status = 'ELIGIBLE'` вернёт 0 rows, если ETL уже обновил status → Promotions обнаружит conflict, залогирует warning. Аналогично, ETL UPSERT re-evaluate'ит CASE expression после блокировки → видит актуальный status, установленный Promotions. `SERIALIZABLE` не требуется.

### promo_policy

Правила автоматического участия в акциях. Аналог `price_policy` для промо.

```
promo_policy:
  id                          BIGSERIAL PK
  workspace_id                BIGINT FK → workspace
  name                        VARCHAR
  status                      ENUM (DRAFT, ACTIVE, PAUSED, ARCHIVED)
  participation_mode          ENUM (RECOMMENDATION, SEMI_AUTO, FULL_AUTO, SIMULATED)
  min_margin_pct              DECIMAL NOT NULL
  min_stock_days_of_cover     INT DEFAULT 7
  max_promo_discount_pct      DECIMAL (nullable)
  auto_participate_categories JSONB (nullable — whitelist категорий)
  auto_decline_categories     JSONB (nullable — blacklist категорий)
  evaluation_config           JSONB
  version                     INT NOT NULL DEFAULT 1
  created_by                  BIGINT FK → app_user
  created_at                  TIMESTAMPTZ
  updated_at                  TIMESTAMPTZ
```

### Policy versioning

Семантика `version` аналогична [Pricing → Policy versioning](pricing.md#policy-versioning). Инкрементируется при UPDATE полей, влияющих на evaluation logic: `participation_mode`, `min_margin_pct`, `min_stock_days_of_cover`, `max_promo_discount_pct`, `auto_participate_categories`, `auto_decline_categories`, `evaluation_config`.

**participation_mode:**

| Mode | Описание |
|------|----------|
| `RECOMMENDATION` | Показывает рекомендацию; оператор решает вручную |
| `SEMI_AUTO` | Создаёт action PENDING_APPROVAL; оператор одобряет |
| `FULL_AUTO` | Создаёт action APPROVED; контроль через guards |
| `SIMULATED` | Создаёт action APPROVED с `execution_mode = SIMULATED`; реальный API не вызывается |

**Mapping participation_mode → execution_mode:** policy SIMULATED → action `execution_mode = SIMULATED`. Все остальные (RECOMMENDATION, SEMI_AUTO, FULL_AUTO) → action `execution_mode = LIVE`. Аналогично [Pricing → execution_mode mapping](pricing.md#decision-и-explanation).

### promo_policy_assignment

Назначение промо-политик на подключения/категории/товары. Структура аналогична `price_policy_assignment`.

```
promo_policy_assignment:
  id                          BIGSERIAL PK
  promo_policy_id             BIGINT FK → promo_policy
  marketplace_connection_id   BIGINT FK → marketplace_connection
  scope_type                  ENUM (CONNECTION, CATEGORY, SKU)
  category_id                 BIGINT (nullable)
  marketplace_offer_id        BIGINT (nullable, FK → marketplace_offer)

  UNIQUE (promo_policy_id, marketplace_connection_id, scope_type, COALESCE(category_id, 0), COALESCE(marketplace_offer_id, 0))
  -- одна запись per policy × scope target; предотвращает дублирующие назначения
```

Разрешение конфликтов: специфичность + приоритет (идентично pricing).

### promo_evaluation_run

Batch-запуск оценки промо-участия. Аналог `pricing_run` для promo evaluation pipeline.

```
promo_evaluation_run:
  id                          BIGSERIAL PK
  workspace_id                BIGINT FK → workspace                     NOT NULL
  connection_id               BIGINT FK → marketplace_connection        NOT NULL
  trigger_type                VARCHAR(30) NOT NULL                      -- POST_SYNC, MANUAL, POLICY_CHANGE
  source_job_execution_id     BIGINT FK → job_execution                 (nullable — only for POST_SYNC)
  status                      VARCHAR(20) NOT NULL DEFAULT 'PENDING'    -- PENDING, IN_PROGRESS, COMPLETED, COMPLETED_WITH_ERRORS, FAILED
  total_products              INT
  eligible_count              INT
  participate_count           INT
  decline_count               INT
  pending_review_count        INT
  deactivate_count            INT
  started_at                  TIMESTAMPTZ
  completed_at                TIMESTAMPTZ
  error_details               JSONB
  created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
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
| IN_PROGRESS → COMPLETED | Все products обработаны, 0 errors |
| IN_PROGRESS → COMPLETED_WITH_ERRORS | Часть products обработана с ошибками |
| IN_PROGRESS → FAILED | Infrastructure error (DB, ClickHouse unavailable) |

**Идемпотентность:** `source_job_execution_id` — dedup key для post-sync triggers. При повторной доставке `PROMO_EVALUATION_EXECUTE`: `EXISTS promo_evaluation_run WHERE source_job_execution_id = ?` → дубликат игнорируется.

### promo_evaluation

Результат оценки целесообразности участия товара в акции.

```
promo_evaluation:
  id                          BIGSERIAL PK
  workspace_id                BIGINT FK → workspace                     NOT NULL
  promo_evaluation_run_id     BIGINT FK → promo_evaluation_run          NOT NULL
  canonical_promo_product_id  BIGINT FK → canonical_promo_product
  promo_policy_id             BIGINT FK → promo_policy
  evaluated_at                TIMESTAMPTZ
  current_participation_status VARCHAR(30) NOT NULL                     -- participation_status на момент оценки (ELIGIBLE / PARTICIPATING)
  promo_price                 DECIMAL
  regular_price               DECIMAL
  discount_pct                DECIMAL
  cogs                        DECIMAL (nullable)
  margin_at_promo_price       DECIMAL (nullable)
  margin_at_regular_price     DECIMAL (nullable)
  margin_delta_pct            DECIMAL (nullable)
  effective_cost_rate          DECIMAL (nullable)
  stock_available             INT
  expected_promo_duration_days INT
  avg_daily_velocity          DECIMAL (nullable)
  stock_days_of_cover         DECIMAL (nullable)
  stock_sufficient            BOOLEAN
  evaluation_result           ENUM (PROFITABLE, MARGINAL, UNPROFITABLE, INSUFFICIENT_STOCK, INSUFFICIENT_DATA)
  signal_snapshot             JSONB
  skip_reason                 VARCHAR (nullable)
  created_at                  TIMESTAMPTZ
```

### promo_decision

```
promo_decision:
  id                          BIGSERIAL PK
  workspace_id                BIGINT FK → workspace
  canonical_promo_product_id  BIGINT FK → canonical_promo_product
  promo_evaluation_id         BIGINT FK → promo_evaluation (nullable)
  policy_version              INT NOT NULL
  policy_snapshot             JSONB NOT NULL
  decision_type               ENUM (PARTICIPATE, DECLINE, DEACTIVATE, PENDING_REVIEW)
  participation_mode          ENUM (RECOMMENDATION, SEMI_AUTO, FULL_AUTO, SIMULATED)
  execution_mode              VARCHAR(20) NOT NULL                     -- LIVE, SIMULATED (derived: policy SIMULATED → SIMULATED, иначе LIVE)
  target_promo_price          DECIMAL (nullable)
  explanation_summary         TEXT
  decided_by                  BIGINT FK → app_user (nullable — NULL для auto)
  created_at                  TIMESTAMPTZ
```

**decision_type семантика:**

| decision_type | Контекст | Описание |
|---------------|----------|----------|
| `PARTICIPATE` | ELIGIBLE product | Решение войти в акцию → ACTIVATE action |
| `DECLINE` | ELIGIBLE product | Решение не входить в акцию → no action (update canonical status) |
| `DEACTIVATE` | PARTICIPATING product | Решение выйти из акции → DEACTIVATE action |
| `PENDING_REVIEW` | ELIGIBLE / PARTICIPATING | Требует ручного подтверждения → action PENDING_APPROVAL |

Для manual actions (`decided_by ≠ NULL`): decision создаётся **всегда** (не nullable). `policy_version` / `policy_snapshot` хранят snapshot effective policy на момент ручного решения (или пустой snapshot с `policy_id = NULL`, если policy не назначена). `promo_evaluation_id` = NULL для manual actions без предшествующей оценки.

`policy_version` и `policy_snapshot` — аналогично [Pricing → price_decision](pricing.md#модель-decision). Snapshot содержит: `policy_id`, `version`, `name`, `participation_mode`, `min_margin_pct`, `min_stock_days_of_cover`, `max_promo_discount_pct`, `auto_participate_categories`, `auto_decline_categories`, `evaluation_config`.

### promo_action

Действие по изменению участия в акции. Использует упрощённый lifecycle (не полную state machine Execution, т.к. promo writes проще price writes).

```
promo_action:
  id                          BIGSERIAL PK
  workspace_id                BIGINT FK → workspace
  promo_decision_id           BIGINT FK → promo_decision               NOT NULL
  canonical_promo_campaign_id BIGINT FK → canonical_promo_campaign
  marketplace_offer_id        BIGINT FK → marketplace_offer
  action_type                 ENUM (ACTIVATE, DEACTIVATE)
  target_promo_price          DECIMAL (nullable — NULL для DEACTIVATE; обязателен для ACTIVATE)
  status                      ENUM (PENDING_APPROVAL, APPROVED, EXECUTING, SUCCEEDED, FAILED, EXPIRED, CANCELLED)
  attempt_count               INT DEFAULT 0
  last_error                  TEXT (nullable)
  execution_mode              ENUM (LIVE, SIMULATED)
  freeze_at_snapshot          TIMESTAMPTZ (nullable)                   -- snapshot canonical_promo_campaign.freeze_at at creation time
  cancel_reason               TEXT (nullable — обязателен при manual cancel)
  created_at                  TIMESTAMPTZ
  updated_at                  TIMESTAMPTZ
```

### promo_action_attempt

Per-attempt tracking для forensics и аудита. Упрощённая версия `price_action_attempt`.

```
promo_action_attempt:
  id                          BIGSERIAL PK
  promo_action_id             BIGINT FK → promo_action                 NOT NULL
  attempt_number              INT NOT NULL
  started_at                  TIMESTAMPTZ NOT NULL
  completed_at                TIMESTAMPTZ (nullable)
  outcome                     ENUM (SUCCESS, RETRIABLE_FAILURE, NON_RETRIABLE_FAILURE)
  error_message               TEXT (nullable)
  provider_request_summary    JSONB (nullable)                         -- endpoint, action_type, offer_id, promo_price
  provider_response_summary   JSONB (nullable)                         -- http_status, accepted_ids, rejected: [{ id, reason }]
  created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
```

**provider_request_summary** — ключевые поля: endpoint, action_type, marketplace_offer_id, target_promo_price, campaign_external_id.

**provider_response_summary** — ключевые поля: http_status, accepted_product_ids, rejected `[{ product_id, reason }]`.

**Упрощения по сравнению с price_action:**
- Нет RECONCILIATION_PENDING — promo activate/deactivate подтверждается re-read promo products при следующем `PROMO_SYNC`
- Нет RETRY_SCHEDULED как отдельного состояния — retry in-process (max 2 attempts, immediate backoff внутри consumer)
- Нет SUPERSEDED — promo actions не конкурируют (одна акция = одно решение per product per campaign)
- Нет ON_HOLD — promo actions time-sensitive (freeze_at deadline)

**Reconciliation через sync:** после promo_action SUCCEEDED, следующий `PROMO_SYNC` верифицирует фактическое participation_status. Расхождение → alert в Promo Journal.

### DB constraint

Partial unique indexes гарантируют не более одного active action per product per campaign per execution_mode:

```sql
CREATE UNIQUE INDEX idx_promo_action_active_live
ON promo_action (canonical_promo_campaign_id, marketplace_offer_id)
WHERE execution_mode = 'LIVE'
  AND status NOT IN ('SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELLED');

CREATE UNIQUE INDEX idx_promo_action_active_simulated
ON promo_action (canonical_promo_campaign_id, marketplace_offer_id)
WHERE execution_mode = 'SIMULATED'
  AND status NOT IN ('SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELLED');
```

При попытке создать дублирующий action → unique constraint violation → action не создаётся, evaluation log warning.

### CAS guards

Все переходы promo_action через CAS SQL (аналогично [Execution → CAS guards](execution.md#cas-compare-and-swap-guards)):

```sql
UPDATE promo_action
SET status = :newStatus, updated_at = NOW()
WHERE id = :id AND status = :expectedStatus
```

CAS return 0 rows → conflict detected → `log.warn`. API-инициированные переходы при conflict → HTTP 409.

### Аудит ручных операций

Каждое ручное действие **обязательно** записывается в `audit_log` (схема и механизм записи — [Audit & Alerting](audit-alerting.md) §Audit). Action types:

| Действие | audit_log.action_type |
|----------|-----------------------|
| Manual participate | `promo.participate` |
| Manual decline | `promo.decline` |
| Manual deactivate | `promo.deactivate` |
| Approve pending action | `promo_action.approve` |
| Reject pending action | `promo_action.reject` |
| Cancel action | `promo_action.cancel` |

При CAS conflict (concurrent transition) — запись с `outcome = CAS_CONFLICT` всё равно создаётся. API возвращает HTTP 409 с текущим статусом action'а.

### Таблица переходов

| Transition | Когда | Инициатор |
|------------|-------|-----------|
| `PENDING_APPROVAL → APPROVED` | Manual approval | operator |
| `PENDING_APPROVAL → EXPIRED` | `freeze_at` deadline passed (scheduled job) | system |
| `PENDING_APPROVAL → CANCELLED` | Manual cancel | operator |
| `APPROVED → EXECUTING` | Worker claim (outbox consumer) | system |
| `APPROVED → EXPIRED` | `freeze_at` deadline passed (scheduled expiration job) | system |
| `APPROVED → CANCELLED` | Manual cancel | operator |
| `EXECUTING → SUCCEEDED` | Provider response confirms activation/deactivation | system |
| `EXECUTING → FAILED` | Non-retriable error OR max attempts exhausted | system |
| `CANCELLED` | Terminal | — |
| `SUCCEEDED` | Terminal | — |
| `FAILED` | Terminal | — |
| `EXPIRED` | Terminal | — |

### Retry — in-process

Retry выполняется **in-process** (внутри consumer), не через outbox:

```
EXECUTING → rateLimiter.acquire(connectionId, group).get(timeout)
  → ожидание токена (CompletableFuture-based, cancellable)
  → provider call
  → if failed (retriable: 429, 503, connect timeout):
      attempt_count++ → immediate backoff (2s × attempt) → retry (через rate limiter снова)
  → if attempt_count >= 2 → CAS: EXECUTING → FAILED
```

Read timeout (запрос отправлен, ответ не получен) → FAILED + reconciliation через следующий PROMO_SYNC (не retry, т.к. activate может быть уже применён).

**Rate limiter interaction:** promo consumer **обязан** вызывать `rateLimiter.acquire(connectionId, group).get(timeout)` перед каждым API call, включая retry attempts. Bucket (`WB_PROMO` / `OZON_PROMO`) разделяется с ETL (PROMO_SYNC) и другими promo actions. Ожидание токена может быть значительным для медленных groups — consumer блокирован на `acquire().get()`. При `prefetchCount=1` это означает: пока один promo action ждёт токен, другие messages в очереди не обрабатываются. Это **допустимо**: promo rate limits высокие (WB: 10/6s, Ozon: 20/60s), wait обычно < 1s; time-critical actions имеют отдельный `freeze_at` deadline с stuck-state detector.

**Обоснование in-process retry:** promo writes идемпотентны (activate already-participating = no-op), max 2 попытки с малым backoff. Outbox-based retry оправдан для price_action (больше attempts, сложнее reconciliation), но избыточен для промо.

### Stuck-state detector

Scheduled job (совмещается с price_action stuck-state detector из [Execution](execution.md)):

```sql
SELECT * FROM promo_action
WHERE status IN ('EXECUTING', 'APPROVED')
  AND updated_at + state_ttl(status) < NOW()
```

| Застрявшее состояние | Default TTL | Эскалация |
|---------------------|-------------|-----------|
| `EXECUTING` | 5 min | → `FAILED` + alert (in-process retry не вернул результат) |
| `APPROVED` | 5 min | → `FAILED` + alert (outbox delivery failure) |

`PENDING_APPROVAL` не входит в stuck-detector — у него отдельный expiration через `freeze_at`.

## Evaluation Pipeline

### Триггеры

| Триггер | Описание |
|---------|----------|
| Post-PROMO_SYNC | После успешного PROMO_SYNC — оценка новых/изменённых акций |
| Manual | По требованию оператора |
| Policy change | При активации/изменении promo_policy |
| New promo detected | Когда sync обнаруживает новую акцию |

### Post-sync promo evaluation trigger

Аналогично [Pricing → Post-sync trigger](pricing.md#pricing-run), promo evaluation триггерится через `ETL_SYNC_COMPLETED` с промежуточным outbox event:

```
pricing-worker queue receives ETL_SYNC_COMPLETED:
  1. Check: PROMO ∈ completed_domains? (no → skip promo evaluation)
  2. Check: active promo_policies exist for connection_id? (no → skip)
  3. Check: no IN_PROGRESS promo_evaluation_run for connection_id? (yes → skip, idempotent)
  4. INSERT promo_evaluation_run (status: PENDING, trigger_type: POST_SYNC, source_job_execution_id)
  5. INSERT outbox_event (type: PROMO_EVALUATION_EXECUTE, payload: { connection_id, promo_evaluation_run_id })
  6. Outbox poller → RabbitMQ exchange promo.evaluation
  7. pricing-worker consumer picks up → CAS promo_evaluation_run PENDING → IN_PROGRESS → executes promo evaluation batch → CAS IN_PROGRESS → COMPLETED/COMPLETED_WITH_ERRORS/FAILED
```

Двухступенчатый подход (ETL_SYNC_COMPLETED → outbox → evaluation) аналогичен pricing (ETL_SYNC_COMPLETED → outbox(PRICING_RUN_EXECUTE) → pricing run). Обеспечивает reliability: при crash между шагами 5 и 7 outbox poller повторит delivery.

**Идемпотентность:** pricing worker отслеживает `source_job_execution_id` в `promo_evaluation_run`. Повторная доставка `ETL_SYNC_COMPLETED` или `PROMO_EVALUATION_EXECUTE` — дубликат игнорируется (run уже существует или IN_PROGRESS).

### Pipeline

```
1. Resolve effective promo_policy per marketplace_offer
2. Filter actionable canonical_promo_product rows:
   - canonical_promo_campaign.status IN (UPCOMING, ACTIVE)
   - freeze_at guard: (canonical_promo_campaign.freeze_at IS NULL OR canonical_promo_campaign.freeze_at > NOW())
   - canonical_promo_product.participation_status IN (ELIGIBLE, PARTICIPATING)
   - canonical_promo_product NOT BANNED
3. Signal assembly (batch):
   - COGS из cost_profile (PostgreSQL)
   - avg_commission_pct, avg_logistics_per_unit из ClickHouse
   - current stock из canonical_stock_current
   - avg_daily_velocity из fact_sales
4. Per-product evaluation:
   - Margin at promo_price = (promo_price − COGS − effective_costs) / promo_price
   - Stock check: stock / avg_daily_velocity ≥ promo_duration_days
   - Category filter: whitelist/blacklist
5. Decision:
   - PROFITABLE + stock sufficient → PARTICIPATE
   - MARGINAL (margin > 0 but < min_margin) → PENDING_REVIEW
   - UNPROFITABLE → DECLINE
   - INSUFFICIENT_STOCK → DECLINE
   - INSUFFICIENT_DATA → PENDING_REVIEW
6. Action scheduling (for SEMI_AUTO / FULL_AUTO)
```

### Margin evaluation

```
effective_cost_rate = avg_commission_pct + avg_logistics_per_unit / promo_price + return_adjustment_pct
margin_at_promo_price = (promo_price − COGS) / promo_price − effective_cost_rate
```

Сигналы идентичны [Pricing → Signal Assembly](pricing.md#signal-assembly). Promotions использует тот же signal assembler.

### Evaluation result classification

| Result | Условие | Default action |
|--------|---------|----------------|
| `PROFITABLE` | `margin_at_promo_price ≥ min_margin_pct` AND stock sufficient | PARTICIPATE |
| `MARGINAL` | `0 < margin_at_promo_price < min_margin_pct` AND stock sufficient | PENDING_REVIEW |
| `UNPROFITABLE` | `margin_at_promo_price ≤ 0` | DECLINE |
| `INSUFFICIENT_STOCK` | `stock_days_of_cover < min_stock_days_of_cover` | DECLINE |
| `INSUFFICIENT_DATA` | COGS не задан или critical signal missing | PENDING_REVIEW |

### Evaluation → Decision mapping

| evaluation_result | RECOMMENDATION | SEMI_AUTO | FULL_AUTO | SIMULATED |
|-------------------|---------------|-----------|-----------|-----------|
| PROFITABLE | Рекомендация «участвовать» | promo_action PENDING_APPROVAL | promo_action APPROVED | promo_action APPROVED (simulated) |
| MARGINAL | Рекомендация «на усмотрение» | promo_action PENDING_APPROVAL | promo_action PENDING_APPROVAL (safety) | promo_action APPROVED (simulated) |
| UNPROFITABLE | Рекомендация «отказаться» | Decline (no action) | Decline (no action) | Decline (no action) |
| INSUFFICIENT_STOCK | Рекомендация «отказаться» | Decline (no action) | Decline (no action) | Decline (no action) |
| INSUFFICIENT_DATA | Рекомендация «проверить данные» | promo_action PENDING_APPROVAL | promo_action PENDING_APPROVAL (safety) | promo_action APPROVED (simulated) |

SIMULATED mode: все actions создаются с `execution_mode = SIMULATED`. MARGINAL и INSUFFICIENT_DATA в SIMULATED создают simulated actions (не PENDING_APPROVAL) — цель: ответить «что было бы, если бы мы участвовали?», не защитить от пограничных решений.

### Deactivation logic

Evaluation pipeline обрабатывает товары со статусом `PARTICIPATING` наряду с `ELIGIBLE`. Для уже участвующих товаров решение зависит от текущей оценки: если участие перестало быть выгодным — создаётся `DEACTIVATE` action.

**Evaluation → Decision mapping для PARTICIPATING товаров:**

| evaluation_result | RECOMMENDATION | SEMI_AUTO | FULL_AUTO | SIMULATED |
|-------------------|---------------|-----------|-----------|-----------|
| PROFITABLE | Нет действия (участие продолжается) | Нет действия | Нет действия | Нет действия |
| MARGINAL | Рекомендация «рассмотреть выход» | Нет действия (сохраняем participation — borderline) | Нет действия (сохраняем) | Нет действия |
| UNPROFITABLE | Рекомендация «выйти из акции» | promo_action DEACTIVATE PENDING_APPROVAL | promo_action DEACTIVATE APPROVED | promo_action DEACTIVATE APPROVED (simulated) |
| INSUFFICIENT_STOCK | Рекомендация «выйти — мало остатков» | promo_action DEACTIVATE PENDING_APPROVAL | promo_action DEACTIVATE PENDING_APPROVAL (safety — stock-out risk требует подтверждения) | promo_action DEACTIVATE APPROVED (simulated) |
| INSUFFICIENT_DATA | Рекомендация «проверить данные» | Нет действия (preserve participation — данные неполные) | Нет действия | Нет действия |

**Ключевые отличия от ELIGIBLE → ACTIVATE:**

- **MARGINAL PARTICIPATING** — не деактивируется. Товар уже участвует; порог для выхода выше, чем для входа (hysteresis). Выход из акции с маржинальным результатом хуже, чем невход с маржинальным результатом.
- **INSUFFICIENT_STOCK + FULL_AUTO** → `PENDING_APPROVAL` (не APPROVED), потому что stock-out risk при участии в акции может привести к штрафам маркетплейса. Оператор должен подтвердить.
- **INSUFFICIENT_DATA PARTICIPATING** — сохраняем participation. Выход из акции на основании неполных данных рискованнее, чем неучастие.

**Temporal constraint:** DEACTIVATE actions подчиняются тем же `freeze_at` ограничениям: после freeze нельзя менять участие. Если кампания FROZEN — DEACTIVATE actions не создаются (pipeline filter: `status ≠ FROZEN`).

### Batch processing

```
1. Load all active/upcoming canonical_promo_campaign rows for connection
2. Load canonical_promo_product rows with ELIGIBLE/PARTICIPATING status
3. Batch signal assembly (one ClickHouse query per signal type)
4. Per-product: evaluate → decide → schedule action
5. Batch insert evaluations, decisions, actions
6. Update promo_evaluation_run counters and status
```

**Per-product error isolation:** ошибка оценки одного товара (деление на ноль, null signal, unique constraint violation при создании action) **НЕ прерывает** evaluation run для остальных товаров в batch. Проблемный товар пропускается: evaluation сохраняется с `evaluation_result = INSUFFICIENT_DATA` и `skip_reason`, `promo_evaluation_run` завершается как `COMPLETED_WITH_ERRORS`. Аналогично [Pricing → Action scheduling — обработка конфликтов](pricing.md#action-scheduling--обработка-конфликтов-с-active-action).

**Stable-state dedup:** для PARTICIPATING товаров, где предыдущая evaluation дала `PROFITABLE` и текущие signals не изменились существенно (delta margin < 1 pp), evaluation сохраняется с `skip_reason = 'stable_state'` и decision/action **не создаётся**. Это предотвращает накопление бесполезных evaluation → decision → «нет действия» цепочек при ежедневном PROMO_SYNC. Для ELIGIBLE товаров dedup не применяется — каждый evaluation run проверяет, не стоит ли войти в акцию.

Критерий стабильности: `|current_margin - previous_margin| < 0.01 AND evaluation_result = previous_evaluation_result`. Проверяется по последней `promo_evaluation` для того же `canonical_promo_product_id`. Если evaluation_result изменился (например, PROFITABLE → MARGINAL из-за роста COGS) — полная evaluation с decision.

## Execution

### Write contracts (provider-specific)

**Ozon:**

| Действие | Endpoint | Метод |
|----------|----------|-------|
| Activate products | `/v1/actions/products/activate` | POST |
| Deactivate products | `/v1/actions/products/deactivate` | POST |

Activate request: `{ action_id, products: [{ product_id, action_price, stock }] }`
Deactivate request: `{ action_id, product_ids: [...] }`
Response: `{ result: { product_ids: [...], rejected: [{ product_id, reason }] } }`

**Batch coordination:** Ozon API принимает массивы products. Executor-worker при обработке `promo.execution` queue группирует `PROMO_ACTION_EXECUTE` события по `(connection_id, canonical_promo_campaign_id, action_type)` в micro-batch (configurable window: `datapulse.promo.execution.batch-window-ms`, default 500ms; max batch size: 50). Один API-вызов → per-product response mapping → CAS каждого promo_action индивидуально (SUCCEEDED / FAILED на основании accepted/rejected per product). Rejected products → `promo_action_attempt` с `provider_response_summary.rejected[].reason`.

Batching значительно снижает нагрузку на rate limit: `OZON_PROMO: 20 req/60s` — 50 products per call × 20 calls/min = 1000 products/min вместо 20 products/min при per-product calls.

WB batch behaviour определится после верификации API.

**WB:**

| Действие | Endpoint | Метод |
|----------|----------|-------|
| Upload products to promo | `/api/v1/calendar/promotions/upload` | POST |

Статус: **требует исследования**. WB promo write API менее документирован; формат запроса и поведение при ошибках нуждаются в эмпирической верификации.

### Action lifecycle

```
PENDING_APPROVAL → APPROVED → EXECUTING → SUCCEEDED
                                 ↓
                               FAILED (→ retry: max 2 attempts)
```

Упрощения обоснованы:
- Promo activate/deactivate — идемпотентные операции
- Provider response явно сообщает о rejected products с причинами
- Reconciliation через следующий PROMO_SYNC (не через отдельный re-read)

### Outbox integration

Promo actions используют тот же `outbox_event` что и [Execution](execution.md). Авторитетная DDL — [Data Model](../data-model.md) §outbox_event:

| outbox_event.type | Описание |
|-------------------|----------|
| `PROMO_ACTION_EXECUTE` | Исполнение promo_action |

**Runtime:** promo actions исполняются `datapulse-executor-worker` (тем же runtime, что и price actions). Обоснование: shared infrastructure (outbox poller, retry, rate limit coordination через Integration module), единая blast radius isolation, общие marketplace adapters.

RabbitMQ topology:

```
Exchanges (direct):
  promo.execution       ← promo action dispatch

Queues:
  promo.execution       ← datapulse-executor-worker consumer (отдельная queue от price.execution)
```

Consumer обрабатывает activate/deactivate через соответствующие marketplace adapters. Отдельная queue `promo.execution` от `price.execution` обеспечивает:
- Независимый prefetch и concurrency tuning
- Изоляцию backpressure (burst promo deadlines не блокирует price actions)
- Отдельные DLQ для диагностики

### Temporal constraints

| Constraint | Описание |
|------------|----------|
| `freeze_at` deadline | Promo action APPROVED должен быть executed до `freeze_at`. После freeze — EXPIRED |
| Upcoming-only activation | Активация наиболее эффективна до `starts_at` (товар участвует с начала акции) |
| Active-period changes | Некоторые акции позволяют менять участие после старта; зависит от `promo_type` |

**Scheduled expiration job:** promo_actions в pre-execution состояниях с истёкшим `freeze_at` → EXPIRED:

```sql
UPDATE promo_action pa
SET status = 'EXPIRED', updated_at = NOW()
FROM canonical_promo_campaign pc
WHERE pa.canonical_promo_campaign_id = pc.id
  AND pa.status IN ('PENDING_APPROVAL', 'APPROVED')
  AND pa.freeze_at_snapshot < NOW()
```

`APPROVED` включён в expiration: при задержке outbox delivery или backpressure в queue, APPROVED action может не дойти до EXECUTING до freeze deadline. Без expiration — action останется APPROVED навсегда (stuck-state detector переведёт в FAILED через 5 мин, но expiration по freeze_at семантически корректнее).

Expiration использует `freeze_at_snapshot` (снимок при создании action), а не `canonical_promo_campaign.freeze_at` — защита от изменения deadline при следующем sync.

### Idempotency

| Provider | Механизм |
|----------|----------|
| Ozon | Activate already-participating product → no-op (response includes in product_ids) |
| Ozon | Deactivate non-participating product → rejected with reason |
| WB | Requires investigation |

## Координация с Pricing

### Promo guard

[Pricing](pricing.md) содержит **Promo guard** — блокирует repricing для товаров, участвующих в активной акции.

Промо-модуль — **source of truth** для participation status. Pricing Promo guard читает:

```sql
SELECT EXISTS (
  SELECT 1 FROM canonical_promo_product pp
  JOIN canonical_promo_campaign pc ON pp.canonical_promo_campaign_id = pc.id
  WHERE pp.marketplace_offer_id = :offerId
    AND pp.participation_status = 'PARTICIPATING'
    AND pc.status IN ('ACTIVE', 'UPCOMING', 'FROZEN')
)
```

`FROZEN` включён в фильтр: после freeze маркетплейс фиксирует промо-цену, изменение regular price через Pricing API бессмысленно (маркетплейс его проигнорирует до окончания акции) и может вызвать расхождение canonical state. Guard снимается только при `ENDED`.

### Price restoration after promo

После завершения акции (`canonical_promo_campaign.status = ENDED`), товар возвращается к обычной ценовой политике. Pricing pipeline при следующем run видит, что Promo guard больше не блокирует → repricing resume.

Специальный «restore price» action не нужен: маркетплейс автоматически восстанавливает regular price после окончания акции.

### min_price coordination (Ozon)

Ozon price write API принимает `min_price` — минимальная цена для промо-акций. Datapulse может использовать это как safety net:

- Pricing module устанавливает `min_price` = floor price (из constraints)
- Promotions module проверяет, что `action_price ≥ min_price`
- Если `action_price < min_price` → evaluation_result = UNPROFITABLE (even if margin positive, violates floor)

## Симулированное участие

Поддерживает `execution_mode = SIMULATED` (аналогично [Execution](execution.md#симулированное-исполнение-phase-f)):

- Evaluation и decision pipeline работают полностью
- promo_action создаётся с `execution_mode = SIMULATED`
- Simulated gateway не вызывает реальный API
- canonical_promo_product.participation_status не меняется (shadow evaluation only)

Позволяет оценить: «если бы мы включили auto-participate, какие товары попали бы в акцию и какова была бы маржа?»

**Enforcement (canonical isolation):**

Executor-worker при обработке `PROMO_ACTION_EXECUTE` проверяет `promo_action.execution_mode`:

- `LIVE` → marketplace adapter call → CAS-update `canonical_promo_product.participation_status`
- `SIMULATED` → simulated gateway (логирует intent, записывает `promo_action_attempt` с `outcome = SUCCESS`) → **пропускает** CAS-update canonical. Результат виден только в `promo_action` / `promo_action_attempt` / `promo_decision`.

Partial unique index `idx_promo_action_active_simulated` изолирует simulated actions от live — один active simulated и один active live action могут сосуществовать для одного product × campaign. Это позволяет запускать simulation параллельно с live execution без конфликтов.

## REST API

### Promo campaigns

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| GET | `/api/workspaces/{workspaceId}/promo/campaigns` | Any role | Paginated. Filters: `?sourcePlatform=...&status=ACTIVE&from=...&to=...` |
| GET | `/api/workspaces/{workspaceId}/promo/campaigns/{campaignId}` | Any role | Детали campaign + product stats (total, eligible, participating, declined) |
| GET | `/api/workspaces/{workspaceId}/promo/campaigns/{campaignId}/products` | Any role | Products in campaign. Paginated. Filters: `?participationStatus=...&search=...` |

### Promo policies

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| POST | `/api/workspaces/{workspaceId}/promo/policies` | PRICING_MANAGER, ADMIN, OWNER | Создать promo policy. Status = DRAFT. Response: `201` |
| GET | `/api/workspaces/{workspaceId}/promo/policies` | Any role | Список promo policies |
| GET | `/api/workspaces/{workspaceId}/promo/policies/{policyId}` | Any role | Детали policy |
| PUT | `/api/workspaces/{workspaceId}/promo/policies/{policyId}` | PRICING_MANAGER, ADMIN, OWNER | Обновить policy (инкрементирует version) |
| POST | `/api/workspaces/{workspaceId}/promo/policies/{policyId}/activate` | PRICING_MANAGER, ADMIN, OWNER | DRAFT/PAUSED → ACTIVE |
| POST | `/api/workspaces/{workspaceId}/promo/policies/{policyId}/pause` | PRICING_MANAGER, ADMIN, OWNER | ACTIVE → PAUSED |
| POST | `/api/workspaces/{workspaceId}/promo/policies/{policyId}/archive` | PRICING_MANAGER, ADMIN, OWNER | → ARCHIVED |

### Policy assignments

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| GET | `/api/workspaces/{workspaceId}/promo/policies/{policyId}/assignments` | Any role | Список assignments |
| POST | `/api/workspaces/{workspaceId}/promo/policies/{policyId}/assignments` | PRICING_MANAGER, ADMIN, OWNER | Добавить assignment |
| DELETE | `/api/workspaces/{workspaceId}/promo/policies/{policyId}/assignments/{assignmentId}` | PRICING_MANAGER, ADMIN, OWNER | Удалить assignment |

### Evaluation runs

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| GET | `/api/workspaces/{workspaceId}/promo/evaluation-runs` | Any role | Paginated. Filters: `?sourcePlatform=...&status=...&from=...&to=...` |
| GET | `/api/workspaces/{workspaceId}/promo/evaluation-runs/{runId}` | Any role | Детали run: status, counts, timing |
| POST | `/api/workspaces/{workspaceId}/promo/evaluation-runs` | PRICING_MANAGER, ADMIN, OWNER | Trigger manual promo evaluation. Body: `{ sourcePlatform }` |

### Evaluations & decisions

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| GET | `/api/workspaces/{workspaceId}/promo/evaluations` | Any role | Paginated. Filters: `?campaignId=...&marketplaceOfferId=...&evaluationResult=...&runId=...` |
| GET | `/api/workspaces/{workspaceId}/promo/decisions` | Any role | Paginated. Filters: `?campaignId=...&decisionType=...&from=...&to=...` |

### Manual promo actions

**Audit-complete flow:** каждое ручное действие создаёт полную цепочку `promo_decision` → `promo_action` (для participate/deactivate) или только `promo_decision` (для decline). `promo_decision.promo_evaluation_id = NULL` (без предшествующей оценки), `decided_by = current_user`, `policy_snapshot` содержит snapshot effective policy (или пустой JSON если policy не назначена).

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| POST | `/api/workspaces/{workspaceId}/promo/products/{promoProductId}/participate` | PRICING_MANAGER, ADMIN, OWNER | Manual participate. Body: `{ targetPromoPrice? }`. Creates promo_decision (PARTICIPATE) + promo_action APPROVED |
| POST | `/api/workspaces/{workspaceId}/promo/products/{promoProductId}/decline` | PRICING_MANAGER, ADMIN, OWNER | Manual decline. Body: `{ reason? }`. Creates promo_decision (DECLINE), updates canonical status |
| POST | `/api/workspaces/{workspaceId}/promo/products/{promoProductId}/deactivate` | PRICING_MANAGER, ADMIN, OWNER | Manual deactivate (PARTICIPATING product). Body: `{ reason? }`. Creates promo_decision (DEACTIVATE) + promo_action DEACTIVATE APPROVED |
| POST | `/api/workspaces/{workspaceId}/promo/actions/{actionId}/approve` | PRICING_MANAGER, ADMIN, OWNER | Approve PENDING_APPROVAL promo_action |
| POST | `/api/workspaces/{workspaceId}/promo/actions/{actionId}/reject` | PRICING_MANAGER, ADMIN, OWNER | Reject PENDING_APPROVAL. Body: `{ reason }` |
| POST | `/api/workspaces/{workspaceId}/promo/actions/{actionId}/cancel` | PRICING_MANAGER, ADMIN, OWNER | Cancel. Body: `{ cancelReason }` |
| POST | `/api/workspaces/{workspaceId}/promo/actions/bulk-approve` | PRICING_MANAGER, ADMIN, OWNER | Bulk approve. Body: `{ actionIds: [...] }`. Response: `{ succeeded: [...], failed: [{ actionId, reason }] }` |
| POST | `/api/workspaces/{workspaceId}/promo/actions/bulk-reject` | PRICING_MANAGER, ADMIN, OWNER | Bulk reject. Body: `{ actionIds: [...], reason }`. Response: `{ succeeded: [...], failed: [{ actionId, reason }] }` |
| GET | `/api/workspaces/{workspaceId}/promo/actions` | Any role | Paginated. Filters: `?campaignId=...&status=...&actionType=...` |

## Аналитика (ClickHouse)

Владение аналитическими артефактами:

| Артефакт | Фаза | Описание |
|----------|------|----------|
| `dim_promo_campaign` | F | Промо-кампании: даты, тип, marketplace |
| `fact_promo_product` | F | Товары в промо: participation status, promo_price, regular_price |
| `mart_promo_product_analysis` | G | Эффективность промо: revenue uplift, margin impact, stock-out events |

Materialization: `PROMO_SYNC` → `dim_promo_campaign` + `fact_promo_product` (уже реализовано в ETL).

### dim_promo_campaign (ClickHouse DDL)

Grain: одна строка per промо-кампания (connection_id × promo_campaign_id).

| Column | Type | Source | Notes |
|--------|------|--------|-------|
| `promo_campaign_id` | UInt64 | canonical_promo_campaign.id | PK (тот же id, что в PostgreSQL canonical) |
| `connection_id` | UInt32 | canonical_promo_campaign.connection_id | FK |
| `source_platform` | LowCardinality(String) | canonical_promo_campaign.source_platform | `'ozon'` / `'wb'` |
| `external_promo_id` | String | canonical_promo_campaign.external_promo_id | Provider-specific |
| `name` | String | canonical_promo_campaign.promo_name | |
| `promo_type` | LowCardinality(String) | canonical_promo_campaign.promo_type | |
| `status` | LowCardinality(String) | canonical_promo_campaign.status | |
| `starts_at` | DateTime | canonical_promo_campaign.date_from | Семантика старта акции |
| `ends_at` | DateTime | canonical_promo_campaign.date_to | Семантика окончания |
| `freeze_at` | Nullable(DateTime) | canonical_promo_campaign.freeze_at | Ozon-specific |
| `ver` | UInt64 | — | Materialization timestamp |

```sql
ENGINE = ReplacingMergeTree(ver)
PARTITION BY source_platform
ORDER BY (connection_id, promo_campaign_id)
```

### fact_promo_product (ClickHouse DDL)

Grain: canonical_promo_campaign × marketplace_offer.

| Column | Type | Source | Notes |
|--------|------|--------|-------|
| `promo_product_id` | UInt64 | canonical_promo_product.id | PK |
| `promo_campaign_id` | UInt64 | canonical_promo_product.canonical_promo_campaign_id | FK dim_promo_campaign |
| `connection_id` | UInt32 | via canonical_promo_campaign | FK marketplace_connection |
| `source_platform` | LowCardinality(String) | — | `'ozon'` / `'wb'` |
| `product_id` | UInt64 | canonical_promo_product.marketplace_offer_id | FK dim_product |
| `seller_sku_id` | UInt64 | via marketplace_offer | FK seller_sku |
| `participation_status` | LowCardinality(String) | canonical_promo_product.participation_status | |
| `participation_decision_source` | LowCardinality(Nullable(String)) | canonical_promo_product.participation_decision_source | `'AUTO'` / `'MANUAL'` / `NULL` (no Datapulse decision yet) |
| `regular_price` | Decimal(18,2) | canonical_promo_product.current_price | Regular на момент sync |
| `promo_price` | Nullable(Decimal(18,2)) | canonical_promo_product.required_price | Цена участия (action_price и т.п.) |
| `discount_pct` | Nullable(Decimal(18,2)) | computed | |
| `ver` | UInt64 | — | Materialization timestamp |

```sql
ENGINE = ReplacingMergeTree(ver)
PARTITION BY source_platform
ORDER BY (connection_id, promo_campaign_id, promo_product_id)
```

### mart_promo_product_analysis (Phase G)

Grain: `promo_campaign × product` (один ряд per product per campaign). Post-mortem анализ после завершения акции.

| Column | Type | Source | Notes |
|--------|------|--------|-------|
| `promo_campaign_id` | UInt64 | dim_promo_campaign | PK (part) |
| `connection_id` | UInt32 | dim_promo_campaign | FK |
| `source_platform` | LowCardinality(String) | dim_promo_campaign | `'ozon'` / `'wb'` |
| `product_id` | UInt64 | fact_promo_product.product_id | PK (part), FK dim_product |
| `seller_sku_id` | UInt64 | via dim_product | FK seller_sku |
| `participation_status` | LowCardinality(String) | fact_promo_product | Final status |
| `decision_type` | LowCardinality(Nullable(String)) | promo_decision (latest per product × campaign) | PARTICIPATE / DECLINE / DEACTIVATE / PENDING_REVIEW / NULL (no evaluation) |
| `participation_decision_source` | LowCardinality(Nullable(String)) | fact_promo_product | AUTO / MANUAL / NULL |
| `promo_price` | Nullable(Decimal(18,2)) | fact_promo_product | Цена в промо (NULL если не участвовал) |
| `regular_price` | Decimal(18,2) | fact_promo_product | Обычная цена |
| `discount_pct` | Nullable(Decimal(18,2)) | computed | `(regular − promo) / regular × 100` |
| `revenue_during_promo` | Nullable(Decimal(18,2)) | fact_finance | Выручка за период акции (NULL если не участвовал) |
| `revenue_before_promo` | Nullable(Decimal(18,2)) | fact_finance | Выручка за эквивалентный период до (NULL если period < 7 дней) |
| `revenue_uplift_pct` | Nullable(Decimal(18,2)) | computed | `(during − before) / NULLIF(before, 0) × 100` |
| `margin_during_promo` | Nullable(Decimal(18,2)) | fact_finance + fact_product_cost | Маржа за период акции |
| `margin_before_promo` | Nullable(Decimal(18,2)) | fact_finance + fact_product_cost | Маржа за аналогичный период до |
| `margin_delta` | Nullable(Decimal(18,2)) | computed | Абсолютное изменение маржи |
| `units_sold_during_promo` | UInt32 | fact_sales | Количество продаж |
| `stock_out_during_promo` | UInt8 | fact_inventory_snapshot | `1` если stock-out был |
| `ver` | UInt64 | — | Materialization timestamp |

```sql
ENGINE = ReplacingMergeTree(ver)
PARTITION BY source_platform
ORDER BY (connection_id, promo_campaign_id, product_id)
```

**Refresh trigger:** materialization после `canonical_promo_campaign.status = ENDED`. Полный пересчёт per campaign при daily re-materialization (Phase G).

**«Before» period:** эквивалентный по длительности период непосредственно до `date_from` кампании. Если период до акции < 7 дней → `revenue_before_promo = NULL` (недостаточно данных для сравнения).

Dependencies: `fact_finance` (revenue, margin), `fact_sales` (units), `fact_inventory_snapshot` (stock-out), `fact_product_cost` (COGS), `dim_promo_campaign` + `fact_promo_product`, `promo_decision` (PostgreSQL — latest decision per product × campaign for `decision_type`).

## Модель данных — retention

| Таблица | Retention |
|---------|-----------|
| `canonical_promo_campaign` | Бессрочно (historical reference; владелец — ETL) |
| `canonical_promo_product` | Бессрочно (владелец — ETL) |
| `promo_evaluation_run` | 180 дней (после ends_at последней связанной акции) |
| `promo_evaluation` | 180 дней (после ends_at акции) |
| `promo_decision` | 180 дней |
| `promo_action` | Бессрочно (audit) |
| `promo_action_attempt` | Бессрочно (audit, привязана к promo_action) |

## Фазовое разделение

| Компонент | Фаза |
|-----------|------|
| `canonical_promo_campaign`, `canonical_promo_product` (canonical model, ETL-owned) | **F** |
| Evaluation pipeline (margin, stock check) | **F** |
| Decision flow (RECOMMENDATION mode) | **F** |
| Promo execution (SEMI_AUTO, FULL_AUTO) | **F** |
| Ozon activate/deactivate write adapter | **F** |
| WB promo write adapter | **F** (requires API investigation) |
| Promo guard integration с Pricing | **F** (guard уже существует в C, canonical source — F) |
| `mart_promo_product_analysis` | **G** |
| Advanced analytics (uplift modeling, optimal pricing) | **G** |

## Design decisions

### P-1: Promo canonical model в PostgreSQL — RESOLVED

Промо-данные из ETL (`PROMO_SYNC`) материализуются в ClickHouse (`dim_promo_campaign`, `fact_promo_product`) для аналитики. **Единственный** набор canonical таблиц в PostgreSQL — `canonical_promo_campaign` и `canonical_promo_product`, владелец схемы и первичная запись из sync — [ETL Pipeline](etl-pipeline.md#promo-canonical-entities). Модуль Promotions не дублирует campaign/product таблицы; читает canonical слой, обновляет поля участия / решений (`is_participating`, `participation_status`, `participation_decision_source` и т.д.) и ведёт собственные сущности (`promo_policy`, `promo_evaluation`, `promo_decision`, `promo_action`).

Обоснование: decision pipeline, promo guard, action lifecycle опираются на тот же canonical PostgreSQL state, что и ETL. ClickHouse — read-only для аналитики (архитектурный инвариант).

### P-2: Отдельный action lifecycle vs reuse Execution — RESOLVED

Promo actions используют **упрощённый lifecycle** (без RECONCILIATION_PENDING, без RETRY_SCHEDULED как отдельного состояния, без SUPERSEDED). Обоснование:

1. Promo write APIs возвращают explicit success/rejected per product — не нужен deferred reconciliation
2. Promo activate идемпотентен — retry проще (immediate, max 2)
3. Freeze deadline создаёт жёсткий temporal constraint — сложный retry flow контрпродуктивен
4. Verification через следующий PROMO_SYNC — eventual reconciliation без отдельного механизма

Общий `outbox_event` переиспользуется (инфраструктура shared). Action state machine — собственная.

### P-3: Promo policy scope — RESOLVED

`promo_policy` назначается через `promo_policy_assignment` (аналогично pricing). Одна active promo_policy per marketplace_offer (resolved через specificity + priority).

Допускается scenario: promo_policy отсутствует → товар не оценивается автоматически → участие только через ручное решение оператора. Это default для новых connections.

### P-4: WB promo write — OPEN

WB promo write API (`POST /api/v1/calendar/promotions/upload`) требует эмпирической верификации:
- Точный формат запроса (JSON body vs multipart)
- Поведение при невалидных SKU
- Ограничения по timing (можно ли менять участие после старта акции)
- Rate limits для write endpoint

**Блокер Phase F:** WB promo write доступен только с Promotion-scoped токеном.

**Graceful degradation (до разблокировки):**

Пока WB promo write недоступен, pipeline для WB работает в **recommendation-only режиме**:

1. **Evaluation pipeline** работает полностью — оценка маржи, остатков, classification (PROFITABLE / MARGINAL / UNPROFITABLE) выполняется штатно.
2. **participation_mode override:** для WB connections `participation_mode` принудительно понижается до `RECOMMENDATION` (независимо от настройки promo_policy). promo_decision создаётся, promo_action — **не создаётся**.
3. **UI:** Promo Journal показывает рекомендации с пометкой `promo.wb.write_unavailable`. Оператор выполняет activate/deactivate вручную через ЛК Wildberries.
4. **После разблокировки:** снятие override → pipeline начинает создавать actions согласно policy. Ретроактивная обработка не требуется — следующий PROMO_SYNC + evaluation подхватит актуальное состояние.

## Связанные модули

- [ETL Pipeline](etl-pipeline.md) — `PROMO_SYNC`: загрузка акций и eligible products; materialization в `canonical_promo_campaign` / `canonical_promo_product` и ClickHouse
- [Analytics & P&L](analytics-pnl.md) — `dim_promo_campaign`, `fact_promo_product`, `mart_promo_product_analysis`; signal assembly для margin evaluation
- [Pricing](pricing.md) — Promo guard (consumer canonical participation_status); signal assembler (shared); min_price coordination
- [Integration](integration.md) — Promo write adapters (Ozon activate/deactivate, WB upload)
- [Execution](execution.md) — Shared `outbox_event` инфраструктура
- [Seller Operations](seller-operations.md) — Promo Journal, promo working queues, evaluation results UI
- [Tenancy & IAM](tenancy-iam.md) — approval/decline определяются ролями
- Детальные контракты: [Promo & Advertising Contracts](../provider-api-specs/promo-advertising-contracts.md)
