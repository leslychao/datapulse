# Pricing Module — Engineering Audit Report

**Дата:** 2026-04-12
**Scope:** Полный инженерный аудит модуля Pricing относительно спецификации `docs/modules/pricing.md`
**Объём проверки:** 134 Java-файла backend (domain 67, persistence 24, api 39, scheduling 4), 4 SQL-миграции, 38 тестов, ~32 frontend-файла, модели и API-сервисы

---

## 1. Confirmed current implementation

### Что соответствует спецификации

- **Pipeline flow** (eligibility → signal assembly → strategy → constraints → guards → decision → explanation → action scheduling) — реализован полностью в `PricingRunService`
- **Strategy + Registry** паттерн — 6 стратегий зарегистрированы через Spring auto-discovery в `PricingStrategyRegistry`
- **Все 6 стратегий** реализованы: TARGET_MARGIN, PRICE_CORRIDOR, VELOCITY_ADAPTIVE, STOCK_BALANCING, COMPOSITE, COMPETITOR_ANCHOR
- **Все 10 guards** реализованы: ManualLock, Promo, StockOut, StaleData, Frequency, Volatility, Margin, AdCost, CompetitorFreshness, CompetitorTrust
- **Constraint resolver** — порядок и логика clamp соответствуют спеке (min_price → max_price → max_change → min_margin → marketplace_min → rounding)
- **Signal assembly** — `PricingSignalCollector` собирает все 21 сигнал из спеки (PostgreSQL + ClickHouse)
- **ClickHouse fallback** — partial degradation при недоступности CH, COMPLETED_WITH_ERRORS
- **Policy versioning** — `version` инкрементируется атомарно, `policy_snapshot` сохраняется в decision
- **Assignment resolution** — specificity (SKU > CATEGORY > CONNECTION) + priority
- **Manual price lock** — CRUD + expiration scheduler + partial unique index
- **Blast radius protection** — `BlastRadiusBreaker` для FULL_AUTO runs, PAUSED transition
- **FULL_AUTO safety gate** — 5 условий проверяются при switch + runtime re-check
- **Impact preview** — synchronous dry-run с narrative async + timeout
- **Competitor model** — match + observation tables, LATERAL JOIN, CSV upload
- **AI features** — Advisor (mock/TODO), Insights (CRUD + scheduler placeholder), Narrative (async mock)
- **All 5 triggers** — POST_SYNC, MANUAL, SCHEDULED, POLICY_CHANGE, MANUAL_BULK
- **Idempotency** — dedup по `sourceJobExecutionId` для POST_SYNC, по `requestHash` для MANUAL_BULK
- **DB schema** — все таблицы, индексы, unique constraints, FK соответствуют спеке (с учётом миграции 0015)
- **Explanation builder** — структурированный audit trail с секциями [Решение], [Политика], [Стратегия], [Ограничения], [Guards], [Режим]
- **Test coverage** — 38 тестов покрывают основные сценарии стратегий, guards, pipeline, bulk operations

### Что реализовано частично

- **Impact preview** — работает только для TARGET_MARGIN и PRICE_CORRIDOR; остальные стратегии возвращают ложный HOLD
- **AI Advisor** — mock-реализация, LLM не интегрирован (Phase E)
- **Insight generation** — scheduler есть, детекторы — TODO/placeholder
- **Frontend filters** — отправляются, но backend не все принимает
- **DTO enrichment** — списки runs обогащены (connectionName, simCount), decisions и locks — нет

---

## 2. Real gaps and bugs found

### MUST_FIX (7 проблем)

#### MF-1. MarginGuard: null threshold подставляет 0% вместо skip

- **Severity:** MUST_FIX
- **Файл:** `datapulse-pricing/.../domain/guard/MarginGuard.java` (строки 54-57)
- **Суть:** При `config.effectiveMinMarginPct() == null` код подставляет `BigDecimal.ZERO` и проверяет маржу против 0%. Javadoc класса и спека: "if NULL → guard is skipped"
- **Последствие:** Ложные блокировки CHANGE-решений при отрицательной/нулевой марже, когда пользователь не задал порог
- **Продуктовое решение:** `if (threshold == null) return GuardResult.pass(guardName())`. Пользователь не задал порог → guard не имеет значения для сравнения

#### MF-2. PolicyResolver: tie-breaker развёрнут (больший id побеждает)

- **Severity:** MUST_FIX
- **Файл:** `datapulse-pricing/.../domain/PolicyResolver.java` (строка 97)
- **Суть:** `Comparator.reverseOrder()` на `pricePolicyId` — побеждает больший id (новейшая policy). Спека и javadoc: "then lower policy id (first created wins)"
- **Последствие:** При одинаковой специфичности и приоритете оффер получает последнюю созданную policy, а не первую
- **Продуктовое решение:** `Comparator.naturalOrder()`. Pricing — safety-critical flow; молчаливое перехватывание офферов новой policy без явного intent (поднять priority) — скрытый ущерб. Если пользователь хочет, чтобы новая policy работала — он должен явно выразить это через priority

#### MF-3. PricingActionScheduler: payload несовместим с consumer — actions не исполняются

- **Severity:** MUST_FIX (critical — основной flow сломан)
- **Файл:** `datapulse-pricing/.../domain/PricingActionScheduler.java` (строки 38-51)
- **Суть:** `PricingActionScheduler` публикует outbox-событие `PRICE_ACTION_EXECUTE` с payload `{decisionId, marketplaceOfferId, targetPrice, actionStatus}`. `PriceActionExecuteConsumer` ожидает `{actionId}`. PriceAction запись в БД не создаётся. Consumer читает `payload.path("actionId").asLong()` = 0 → log error → exit
- **Последствие:** Pricing run создаёт decisions, но price actions никогда не исполняются
- **Продуктовое решение:** Новый outbox-тип `PRICING_ACTION_REQUESTED` + consumer `PricingActionMaterializerConsumer` в `datapulse-api`, который вызывает `ActionService.createAction()`. ActionService сам создаёт PriceAction, обрабатывает supersede/defer, публикует outbox с `actionId`. Это единственный вариант, при котором: pricing не зависит от execution; active action conflicts обрабатываются корректно; один consumer = один контракт

#### MF-4. resumeRun не ставит задачу в outbox — run зависает навсегда

- **Severity:** MUST_FIX
- **Файл:** `datapulse-pricing/.../domain/PricingRunApiService.java` (строки 224-237)
- **Суть:** `resumeRun` переводит PAUSED → IN_PROGRESS, но не публикует outbox-событие. Worker уже завершил обработку сообщения. Run навсегда зависает в IN_PROGRESS
- **Последствие:** PAUSED run после resume не возобновляется
- **Продуктовое решение:** (1) approve ON_HOLD actions для этого run; (2) `enqueuePricingRunExecute(runId)`; (3) `PricingRunService.executeRun` должен принимать PAUSED статус (не только PENDING). Re-process всех офферов безопасен: уже обработанные дадут SKIP (no change), новые обработаются нормально

#### MF-5. Порядок guards расходится со спекой

- **Severity:** MUST_FIX
- **Файлы:** Все guards в `datapulse-pricing/.../domain/guard/`
- **Код:** 10→20(stale)→25-26(competitor)→30(stock)→35(promo)→40(margin)→50(frequency)→60(volatility)→70(ad cost)
- **Спека:** 10→11(promo)→12(stock)→15(stale)→20(frequency)→21(volatility)→22(margin)→23(ad cost)→25-26(competitor)
- **Последствие:** Пользователь видит другой blocking reason из-за short-circuit
- **Продуктовое решение:** Привести к порядку спеки. Все guards — in-memory проверки над PricingSignalSet, разница в стоимости — наносекунды. Порядок определяет UX: бизнес-meaningful блокировки (promo, stock-out) должны показываться раньше generic (stale data). Пример: товар в промо + stale data → текущий код: «Данные устарели»; спека: «Товар в промо» — второе actionable для пользователя

#### MF-6. Frontend: confirmFullAuto не передаётся при обновлении policy

- **Severity:** MUST_FIX
- **Файл:** `frontend/.../pricing/policies/policy-form-page.component.ts`
- **Суть:** `toPolicyWriteBody()` не включает `confirmFullAuto` в тело PUT-запроса. Backend при переключении на FULL_AUTO ждёт `confirmFullAuto = true`, иначе 400
- **Последствие:** Невозможно переключить policy на FULL_AUTO через UI
- **Продуктовое решение:** Confirmation dialog при выборе FULL_AUTO + флаг `confirmFullAuto: true` в request body

#### MF-7. Frontend: enum RunStatus без PAUSED/CANCELLED

- **Severity:** MUST_FIX
- **Файл:** `frontend/.../core/models/pricing.model.ts`
- **Суть:** `RunStatus` union type не включает PAUSED и CANCELLED, которые реально возвращаются backend'ом
- **Последствие:** UI не может корректно отобразить статус остановленного/отменённого run. Badges, фильтры и условная логика ломаются
- **Продуктовое решение:** Добавить PAUSED (warning badge) и CANCELLED (neutral badge) + i18n ключи + кнопки Resume/Cancel для PAUSED run

### SHOULD_FIX (9 проблем)

#### SF-1. TARGET_MARGIN validation: диапазон шире спеки

- **Severity:** SHOULD_FIX
- **Файл:** `PricePolicyService.java`
- **Суть:** `target_margin_pct` валидируется как [0, 1). Спека: [0.01, 0.80]
- **Продуктовое решение:** Ужесточить до [0.01, 0.80]. 80% — luxury territory, runtime guard поймает при cost rates > 0, но зачем допускать абсурдные значения на входе

#### SF-3. PricingInsightService: фильтр acknowledged игнорируется при insightType

- **Severity:** SHOULD_FIX
- **Файл:** `PricingInsightService.java` (строки 29-31)
- **Суть:** Если `insightType != null` — `acknowledged` полностью игнорируется
- **Продуктовое решение:** Поддержать комбинированный фильтр `type + acknowledged`

#### SF-4. Frontend фильтры не совпадают с backend

- **Severity:** SHOULD_FIX
- **Файлы:** `PricingRunFilter.java`, `PriceDecisionFilter.java`, `ManualPriceLockController.java`
- **Суть:** Frontend отправляет multi-status, triggerType, executionMode, connectionId, search — backend не все принимает
- **Продуктовое решение:** Расширить backend-фильтры: runs (triggerType, status[]), decisions (executionMode), locks (connectionId, search)

#### SF-5. Decision/Lock responses без обогащённых полей

- **Severity:** SHOULD_FIX
- **Файлы:** `PriceDecisionResponse.java`, `ManualLockResponse.java`
- **Суть:** Frontend ожидает offerName, sellerSku, connectionName, policyName, lockedByName. Backend отдаёт только ID-ки
- **Продуктовое решение:** Backend enrichment через JOIN в read repositories

#### SF-6. BiddingPostPricingRunListener: @EventListener без AFTER_COMMIT

- **Severity:** SHOULD_FIX
- **Файл:** `datapulse-api/.../execution/BiddingPostPricingRunListener.java`
- **Суть:** `@EventListener` выполняется внутри транзакции completeRun. Исключение откатит pricing run
- **Продуктовое решение:** `@TransactionalEventListener(phase = AFTER_COMMIT)`

#### SF-7. ImpactPreviewService: неполная симуляция для 4 стратегий

- **Severity:** SHOULD_FIX
- **Файл:** `ImpactPreviewService.java` (строки 97-98)
- **Суть:** VELOCITY_ADAPTIVE, STOCK_BALANCING, COMPOSITE, COMPETITOR_ANCHOR возвращают ложный HOLD с reason PRICING_NO_CHANGE
- **Продуктовое решение:** Вместо HOLD — `PRICING_PREVIEW_NOT_SUPPORTED_FOR_STRATEGY` с текстом «Для оценки эффекта запустите тестовый прогон в режиме SIMULATED». Честно вместо ложного HOLD

#### SF-8. Спека: price_decision NOT NULL расходится с реальной схемой

- **Severity:** SHOULD_FIX
- **Файл:** `docs/modules/pricing.md`
- **Суть:** Миграция 0015 сделала `price_policy_id` и `policy_snapshot` nullable (для bulk manual). Спека всё ещё NOT NULL
- **Продуктовое решение:** Обновить спеку + добавить комментарий про nullable (bulk/manual сценарии)

#### SF-9. Спека: два разных описания competitor API

- **Severity:** SHOULD_FIX
- **Файл:** `docs/modules/pricing.md` (строки 224-233 vs 1077-1084)
- **Суть:** Блок Competitor Price Model → `/matches`, `bulk-upload`. Таблица REST API → `/match`, `/observations`, `/upload-csv`. Код следует первому варианту
- **Продуктовое решение:** Удалить дубликат, оставить актуальное (первый вариант, соответствующий коду)

#### SF-10. PricingRunApiService.getRun: simulated count всегда 0

- **Severity:** SHOULD_FIX
- **Файл:** `PricingRunApiService.java` (строка 221)
- **Суть:** `listRuns` правильно считает simulated count через `countSimulatedByRunIds`. `getRun` хардкодит 0
- **Продуктовое решение:** Использовать тот же запрос, что и `listRuns`

### NOT_FIX

#### SF-2. FullAutoSafetyGate: manual lock guard check тавтологичен

- **Severity:** NOT_FIX (переклассифицировано из SHOULD_FIX)
- **Суть:** `isManualLockGuardEnabled` всегда true. ManualLockGuard по дизайну неотключаемый — проверка тавтологична, но безвредна. Код защищает от гипотетического будущего

### NICE_TO_HAVE (8 проблем)

- NH-1. BlastRadiusBreaker: потенциальная гонка при параллельных FULL_AUTO (маловероятна — один run per connection)
- NH-2. CompetitorTrustGuard: REJECTED не блокируется (зависит от продуктового требования)
- NH-3. CompetitorAnchorStrategy: undocumented fallback `2*COGS` при denominator <= 0
- NH-4. PricingInsightScheduler: TODO/mock insight generation (Phase E)
- NH-5. PricingClickHouseReadRepository: `connectionId` не используется в `findCategoriesBySellerSkuIds`
- NH-6. CompetitorService: CSV `split(",")` без экранирования кавычек
- NH-7. `execution_mode_changed_at` не описано в спеке `price_policy`
- NH-8. BulkManualPricingService: не проверяет однородность connectionId в одном bulk

---

## 3. Summary

| Категория | Количество |
|---|---|
| MUST_FIX | 7 |
| SHOULD_FIX | 9 |
| NOT_FIX | 1 |
| NICE_TO_HAVE | 8 |
| **Итого проблем** | **25** |

---

## 4. Final verdict

**Sufficient with must-fix issues.**

Модуль архитектурно корректен, pipeline реализован полностью, стратегии и guards работают. Основные проблемы:
- Критический баг: pricing actions не исполняются (MF-3)
- Один guard ложно блокирует (MF-1)
- Policy resolution выбирает не ту policy (MF-2)
- Resume run не работает (MF-4)
- Frontend не может активировать FULL_AUTO (MF-6)
- Guard order не соответствует продуктовому intent (MF-5)
- Frontend не обрабатывает PAUSED/CANCELLED (MF-7)

После закрытия MUST_FIX модуль production-ready для основного flow.

---

## 5. What should not be done now

- Переписывать pipeline на event-driven (текущий batch processing корректен)
- Создавать state machine для pricing run lifecycle (текущие CAS-переходы достаточны)
- Интегрировать LLM для Advisor/Insights (Phase E scope)
- Добавлять real-time preview для VELOCITY/STOCK/COMPOSITE/COMPETITOR (требует ClickHouse integration в preview path)
- Менять структуру outbox/RabbitMQ топологии beyond MF-3 fix
- Рефакторить BlastRadiusBreaker в thread-safe версию (один run per connection by design)

## 6. What would be overengineering

- Создание отдельного `PricingDecisionEventBus` для inter-module communication (outbox достаточен)
- Введение Strategy pattern для guard order (простой `order()` метод достаточен)
- Создание `PolicyVersionHistoryEntity` для полного changelog (snapshot в decision достаточен)
- Добавление `PricingRunCheckpoint` entity для resume (re-process всех офферов безопасен)
- Введение `PricingSignalRegistry` для dynamic signal discovery (enum + collector достаточны)
- Создание `ConstraintPipeline` абстракции поверх текущего `PricingConstraintResolver`
- Выделение `PriceDecisionFactory` — buildDecision в PricingRunService достаточно читаемый
