# Pricing Module Audit — Implementation Prompts

**Источник:** `docs/modules/pricing-audit-report.md`
**Порядок:** Чаты упорядочены по зависимостям. Каждый чат — самостоятельный, но следующий может зависеть от предыдущего.

---

## Чат 1: Domain correctness — простые фиксы (MF-1, MF-2, SF-1, SF-3, SF-10)

Группа из 5 фиксов, которые не зависят друг от друга и затрагивают один-два файла каждый. Безопасно делать в одном чате.

### Промпт

```
Контекст: проведён инженерный аудит модуля Pricing (см. docs/modules/pricing-audit-report.md). Нужно закрыть 5 простых фиксов domain-уровня. Каждый — правка одного-двух файлов.

## MF-1. MarginGuard: null threshold → skip

Файл: `backend/datapulse-pricing/src/main/java/io/datapulse/pricing/domain/guard/MarginGuard.java`

Проблема: при `config.effectiveMinMarginPct() == null` подставляется `BigDecimal.ZERO` и маржа проверяется против 0%. Спека и javadoc: "if null → guard is skipped".

Фикс: если `threshold == null` → `return GuardResult.pass(guardName())`. Убрать fallback на ZERO.

## MF-2. PolicyResolver: tie-breaker развёрнут

Файл: `backend/datapulse-pricing/src/main/java/io/datapulse/pricing/domain/PolicyResolver.java` (строка 97)

Проблема: `Comparator.reverseOrder()` на `pricePolicyId` — побеждает больший id. Спека: "first created wins" (меньший id).

Фикс: заменить `Comparator.reverseOrder()` → `Comparator.naturalOrder()`.

## SF-1. TARGET_MARGIN validation: диапазон шире спеки

Файл: `backend/datapulse-pricing/src/main/java/io/datapulse/pricing/domain/PricePolicyService.java`

Проблема: `target_margin_pct` валидируется как [0, 1). Спека: [0.01, 0.80].

Фикс: ужесточить min до 0.01 и max до 0.80. Найти место валидации и обновить. Добавить или обновить message key в MessageCodes + ru.json.

## SF-3. PricingInsightService: фильтр acknowledged игнорируется при insightType

Файл: `backend/datapulse-pricing/src/main/java/io/datapulse/pricing/domain/PricingInsightService.java`

Проблема: если `insightType != null`, метод вызывает `findByInsightType`, полностью игнорируя `acknowledged`.

Фикс: добавить комбинированный запрос в InsightRepository (findByTypeAndAcknowledged) и использовать его. Обработать все 4 комбинации: type=null/not null × acknowledged=null/not null.

## SF-10. PricingRunApiService.getRun: simulated count всегда 0

Файл: `backend/datapulse-pricing/src/main/java/io/datapulse/pricing/domain/PricingRunApiService.java`

Проблема: `listRuns` корректно считает simulated count через `countSimulatedByRunIds`. `getRun` хардкодит `0`.

Фикс: в `getRun` использовать тот же подход — запросить count из `decisionRepository` (или аналогичный метод). Посмотри как это сделано в `listRuns` и повтори для `getRun`.

## Общие требования

- После каждого фикса проверь, что проект компилируется
- Если есть тесты для затронутых классов — обнови их
- Для MF-1 и MF-2 желательно добавить unit-тесты, покрывающие исправленное поведение
- Следуй coding-style правилам проекта (Google Java Style, 2 пробела indent, @RequiredArgsConstructor, etc.)
- Для SF-1: message key для валидационной ошибки — добавить в MessageCodes + frontend/src/locale/ru.json
- Закоммить каждый фикс отдельно с осмысленным сообщением
```

---

## Чат 2: Action scheduling integration — MF-3 (самый сложный фикс)

Требует изменений в 3 модулях: datapulse-pricing, datapulse-api, datapulse-execution. Отдельный чат, чтобы сфокусироваться.

### Промпт

```
Контекст: проведён инженерный аудит модуля Pricing (см. docs/modules/pricing-audit-report.md). Нужно закрыть MF-3 — самый критический баг: pricing actions не исполняются из-за несовместимого контракта между producer и consumer.

## Проблема

`PricingActionScheduler` (в datapulse-pricing) публикует outbox-событие `PRICE_ACTION_EXECUTE` с payload:
```json
{
  "decisionId": 123,
  "marketplaceOfferId": 456,
  "targetPrice": "1500.00",
  "actionStatus": "PENDING_EXECUTION",
  "executionMode": "APPROVE_REQUIRED",
  "workspaceId": 1
}
```

`PriceActionExecuteConsumer` (в datapulse-api) ожидает payload с `actionId`:
```json
{
  "actionId": 789
}
```

`PriceActionEntity` не создаётся нигде. Consumer читает `actionId` = 0 → log error → exit. Actions никогда не исполняются.

## Требуемое решение

### 1. Новый outbox-тип: `PRICING_ACTION_REQUESTED`

Добавить в `OutboxEventType` enum новое значение `PRICING_ACTION_REQUESTED`.

### 2. Изменить `PricingActionScheduler`

Вместо `PRICE_ACTION_EXECUTE` публиковать `PRICING_ACTION_REQUESTED` с payload:
```json
{
  "decisionId": 123,
  "marketplaceOfferId": 456,
  "targetPrice": "1500.00",
  "executionMode": "APPROVE_REQUIRED",
  "workspaceId": 1,
  "connectionId": 789,
  "runId": 10
}
```

### 3. Новый consumer: `PricingActionMaterializerConsumer`

Создать в `datapulse-api` (пакет `execution/`):

- Слушает `PRICING_ACTION_REQUESTED`
- Извлекает payload
- Вызывает `ActionService.createAction(...)` (уже существует в datapulse-execution)
- `ActionService.createAction` сам:
  - Создаёт `PriceActionEntity` в БД
  - Обрабатывает supersede/defer для active actions на тот же offer
  - Если `autoApprove` (executionMode = FULL_AUTO) — сразу вызывает `scheduleExecution` → outbox `PRICE_ACTION_EXECUTE` с `actionId`
  - Если не auto-approve — action остаётся в PENDING_APPROVAL

### 4. Не трогать `PriceActionExecuteConsumer`

Он уже корректен — принимает `actionId` и вызывает `executor.execute(actionId)`.

## Порядок работы

1. Прочитай текущий `PricingActionScheduler.java`, `PriceActionExecuteConsumer.java`, `ActionService.java`, `OutboxEventType.java`
2. Добавь `PRICING_ACTION_REQUESTED` в OutboxEventType
3. Измени `PricingActionScheduler` — публиковать новый тип с полным payload
4. Создай `PricingActionMaterializerConsumer` в datapulse-api/execution/
5. Убедись, что `ActionService.createAction` принимает все нужные параметры. Если нет — добавь перегрузку или расширь
6. Проверь, что `PricingActionScheduler` передаёт `connectionId` (нужен для ActionService), при необходимости добавь его в метод `scheduleAction`
7. Убедись, что проект компилируется
8. Обнови/добавь тесты

## Контракт ActionService.createAction

Изучи существующую сигнатуру `ActionService.createAction`. Нужные данные для создания PriceAction:
- decisionId
- marketplaceOfferId
- targetPrice (BigDecimal)
- connectionId
- workspaceId
- executionMode (для определения autoApprove)

Если метода createAction с такой сигнатурой нет — посмотри, что есть, и адаптируй consumer под существующий API.

## Закоммить с осмысленным сообщением
```

---

## Чат 3: Run lifecycle — MF-4, SF-6

Два связанных фикса: resume run и event listener safety.

### Промпт

```
Контекст: проведён инженерный аудит модуля Pricing (см. docs/modules/pricing-audit-report.md). Нужно закрыть MF-4 (resumeRun не работает) и SF-6 (BiddingPostPricingRunListener без AFTER_COMMIT).

## MF-4. resumeRun не ставит задачу в outbox

Файл: `backend/datapulse-pricing/src/main/java/io/datapulse/pricing/domain/PricingRunApiService.java`

### Проблема

`resumeRun` (строки ~224-237):
1. Переводит run из PAUSED в IN_PROGRESS
2. **Не** публикует outbox-событие
3. Worker уже завершил обработку сообщения
4. Run навсегда зависает в IN_PROGRESS

### Требуемое решение

В `resumeRun`, после смены статуса:

1. **Approve ON_HOLD actions**: найти все `PriceAction` со статусом `ON_HOLD` для данного runId и перевести их в `PENDING_EXECUTION` (или `APPROVED`). Посмотри как устроены action статусы в `ActionService` / `PriceActionEntity`

2. **Опубликовать outbox-событие**: `enqueuePricingRunExecute(runId)` — аналогично тому, как это делается при создании run. Посмотри как `createRun` / `triggerRun` ставят задачу в outbox

3. **Поддержать PAUSED в executeRun**: в `PricingRunService.executeRun` сейчас guard принимает только PENDING (или IN_PROGRESS). Нужно убедиться, что run в статусе PAUSED/IN_PROGRESS после resume корректно обрабатывается. Re-process всех офферов безопасен — уже обработанные дадут NO_CHANGE

## SF-6. BiddingPostPricingRunListener: @EventListener без AFTER_COMMIT

Файл: `backend/datapulse-api/src/main/java/io/datapulse/api/execution/BiddingPostPricingRunListener.java`

### Проблема

`@EventListener` выполняется внутри транзакции `completeRun`. Если listener бросит исключение — откатится транзакция pricing run.

### Фикс

Заменить `@EventListener` → `@TransactionalEventListener(phase = AFTER_COMMIT)`.

Если listener использует `@Async` — проверить, что `@Async` + `@TransactionalEventListener` корректно работают вместе (должны — @Async создаёт новый поток, @TransactionalEventListener ждёт коммита).

## Порядок работы

1. Прочитай текущий `PricingRunApiService.resumeRun`, `PricingRunService.executeRun`, понять текущий flow
2. Найди, как outbox-событие ставится при создании run (скорее всего `enqueuePricingRunExecute` или аналог)
3. Найди, как action статусы управляются (ON_HOLD → APPROVED/PENDING_EXECUTION)
4. Реализуй фикс MF-4
5. Реализуй фикс SF-6
6. Проверь компиляцию
7. Закоммить

## Важно
- Не менять PricingRunService.executeRun больше, чем нужно — только добавить поддержку PAUSED
- Не менять логику blast radius breaker
```

---

## Чат 4: Guard order alignment — MF-5

Отдельный чат, потому что затрагивает 10 файлов.

### Промпт

```
Контекст: проведён инженерный аудит модуля Pricing (см. docs/modules/pricing-audit-report.md). Нужно закрыть MF-5 — порядок guards расходится со спекой.

## Проблема

Guards выполняются в порядке `order()` метода (short-circuit при первом BLOCK). Текущий порядок в коде не совпадает со спекой.

## Текущий порядок (код)

| Guard | order() |
|---|---|
| ManualLockGuard | 10 |
| StaleAdvertisingDataGuard | 20 |
| CompetitorFreshnessGuard | 25 |
| CompetitorTrustGuard | 26 |
| StockOutGuard | 30 |
| ActivePromoGuard | 35 |
| MarginGuard | 40 |
| FrequencyGuard | 50 |
| VolatilityGuard | 60 |
| AdCostGuard | 70 |

## Требуемый порядок (спека — docs/modules/pricing.md, секция Guards)

| Guard | order() |
|---|---|
| ManualLockGuard | 10 |
| ActivePromoGuard | 11 |
| StockOutGuard | 12 |
| StaleAdvertisingDataGuard | 15 |
| FrequencyGuard | 20 |
| VolatilityGuard | 21 |
| MarginGuard | 22 |
| AdCostGuard | 23 |
| CompetitorFreshnessGuard | 25 |
| CompetitorTrustGuard | 26 |

## Что сделать

1. Открой каждый guard-файл в `backend/datapulse-pricing/src/main/java/io/datapulse/pricing/domain/guard/`
2. Измени метод `order()` (или поле ORDER) на значение из таблицы выше
3. Убедись, что все 10 guards обновлены
4. Проверь, что нет дубликатов order values
5. Обнови тесты, если они проверяют порядок guard execution
6. Проверь компиляцию
7. Закоммить

## Обоснование

Все guards — in-memory проверки над PricingSignalSet. Разница в стоимости выполнения — наносекунды. Порядок определяет UX: бизнес-meaningful блокировки (товар в промо, stock-out) должны показываться раньше generic (stale data). Пример: товар в промо + stale data → текущий код: «Данные устарели»; спека: «Товар в промо» — второе actionable для пользователя.
```

---

## Чат 5: Frontend fixes — MF-6, MF-7

Два frontend-фикса.

### Промпт

```
Контекст: проведён инженерный аудит модуля Pricing (см. docs/modules/pricing-audit-report.md). Нужно закрыть два frontend-бага.

## MF-6. confirmFullAuto не передаётся при обновлении policy

### Проблема

Файл: `frontend/src/app/features/pricing/policies/policy-form-page.component.ts`

Метод `toPolicyWriteBody()` не включает `confirmFullAuto` в тело PUT-запроса. Backend при переключении на FULL_AUTO ждёт `confirmFullAuto = true`, иначе возвращает 400.

### Требуемое решение

1. При сохранении policy с `executionMode == 'FULL_AUTO'`:
   - Показать confirmation modal с предупреждением (на русском):
     - Заголовок: «Включение полного автомата»
     - Текст: «Политика будет автоматически изменять цены без подтверждения. Убедитесь, что все guards и ограничения настроены корректно.»
     - Кнопки: «Подтвердить» / «Отмена»
   - Используй существующий `ConfirmationModalComponent` из shared/components
   - Используй i18n ключи (добавь в ru.json):
     - `pricing.policy.full_auto.confirm_title` = "Включение полного автомата"
     - `pricing.policy.full_auto.confirm_message` = "Политика будет автоматически изменять цены без подтверждения. Убедитесь, что все guards и ограничения настроены корректно."
     - `pricing.policy.full_auto.confirm_button` = "Подтвердить"

2. После подтверждения — включить `confirmFullAuto: true` в тело запроса

3. Если пользователь отменил — не отправлять запрос

4. Если executionMode != FULL_AUTO — не показывать modal, отправлять запрос без `confirmFullAuto`

## MF-7. RunStatus без PAUSED/CANCELLED

### Проблема

Файл: `frontend/src/app/core/models/pricing.model.ts`

`RunStatus` union type не включает PAUSED и CANCELLED.

### Требуемое решение

1. В `pricing.model.ts` — добавить `'PAUSED' | 'CANCELLED'` к RunStatus type

2. Найти все места, где RunStatus используется для рендеринга (badges, labels, цвета, фильтры):
   - Status badge component или inline логика в runs list
   - Filter dropdown для runs

3. Добавить стили:
   - PAUSED: warning (жёлтый) — `--status-warning`
   - CANCELLED: neutral (серый) — `--status-neutral`

4. Добавить i18n ключи в `frontend/src/locale/ru.json`:
   - `pricing.run.status.paused` = "Приостановлен"
   - `pricing.run.status.cancelled` = "Отменён"

5. Если на странице runs для PAUSED статуса показываются кнопки действий — добавить:
   - «Возобновить» (Resume) → вызов PUT /api/pricing/runs/{id}/resume
   - «Отменить» (Cancel) → вызов PUT /api/pricing/runs/{id}/cancel

6. Проверь pricing API service на фронте — если методов resume/cancel нет, добавь

## Закоммить каждый фикс отдельно
```

---

## Чат 6: Backend DTO enrichment и фильтры — SF-4, SF-5

Backend-расширения для UI.

### Промпт

```
Контекст: проведён инженерный аудит модуля Pricing (см. docs/modules/pricing-audit-report.md). Нужно закрыть SF-4 (фильтры) и SF-5 (DTO enrichment) — backend-расширения для полноценной работы фронтенда.

## SF-4. Расширить backend-фильтры

### Runs

Файл: `backend/datapulse-pricing/.../api/PricingRunFilter.java` (или аналог)

Сейчас backend принимает один `status`. Frontend шлёт:
- `status[]` (multi-select) — нужен `List<RunStatus>`
- `triggerType` — нужен фильтр по trigger type

Найди read repository для runs (вероятно JDBC), и добавь поддержку:
- `List<RunStatus> statuses` (WHERE status IN (...))
- `TriggerType triggerType` (WHERE trigger_type = ...)

### Decisions

Файл: `backend/datapulse-pricing/.../api/PriceDecisionFilter.java` (или аналог)

Frontend шлёт `executionMode`. Backend не поддерживает.

Добавь:
- `ExecutionMode executionMode` — фильтр (через join с pricing_run или policy_snapshot)

### Locks

Файл: `backend/datapulse-pricing/.../api/ManualPriceLockController.java`

Frontend шлёт `connectionId` и `search`. Backend принимает только `marketplaceOfferId`.

Добавь:
- `Long connectionId` — фильтр по connection
- `String search` — поиск по marketplace_offer_id (ILIKE) или offer name

## SF-5. DTO enrichment через JOIN

### Decisions

`PriceDecisionResponse` должен содержать:
- `offerName` — из marketplace_offer (или canonical таблицы)
- `sellerSku` — из marketplace_offer
- `connectionName` — из connection
- `policyName` — из price_policy

Обогати read repository (JDBC) — добавь LEFT JOIN и верни доп. поля. Обнови Response DTO.

### Locks

`ManualPriceLockResponse` (или аналог) должен содержать:
- `offerName` — из marketplace_offer
- `sellerSku` — из marketplace_offer
- `lockedByName` — имя пользователя из таблицы users (join по locked_by_user_id)

## Общие требования

- Используй JDBC read repositories с динамическими WHERE-клаузами
- Для enrichment — LEFT JOIN (данные могут отсутствовать)
- SQL в text blocks (`"""..."""`)
- Сортировка: whitelist `Map<String, String>` для предотвращения SQL injection
- Не ломай существующие endpoint-ы — только расширяй
- Закоммить
```

---

## Чат 7: Impact preview improvement — SF-7

### Промпт

```
Контекст: проведён инженерный аудит модуля Pricing (см. docs/modules/pricing-audit-report.md). Нужно закрыть SF-7 — ImpactPreviewService даёт ложный HOLD для 4 стратегий.

## Проблема

Файл: `backend/datapulse-pricing/src/main/java/io/datapulse/pricing/domain/ImpactPreviewService.java`

Для стратегий VELOCITY_ADAPTIVE, STOCK_BALANCING, COMPOSITE, COMPETITOR_ANCHOR метод `evaluateOffer` (строки ~97-98) возвращает `EvaluatedOffer.hold(...)` с reason `PRICING_NO_CHANGE`. Это ложная информация — не "нет изменений", а "preview не поддерживается для этой стратегии".

## Требуемое решение

1. Добавить новый message key: `PRICING_PREVIEW_NOT_SUPPORTED_FOR_STRATEGY`
   - В `MessageCodes.java`: `public static final String PRICING_PREVIEW_NOT_SUPPORTED_FOR_STRATEGY = "pricing.preview.not_supported_for_strategy";`
   - В `frontend/src/locale/ru.json`: `"pricing.preview.not_supported_for_strategy": "Предпросмотр недоступен для этой стратегии. Запустите тестовый прогон в режиме «Симуляция»."`

2. В `ImpactPreviewService.evaluateOffer` — для неподдерживаемых стратегий вернуть:
   - `EvaluatedOffer.skip(...)` (или аналогичный factory method) с reason `PRICING_PREVIEW_NOT_SUPPORTED_FOR_STRATEGY`
   - Если `skip` factory method не существует — используй `hold` но с правильным reason key вместо `PRICING_NO_CHANGE`

3. Убедись, что frontend корректно отобразит этот reason (как info-сообщение, не как ошибку)

## Закоммить
```

---

## Чат 8: Documentation sync — SF-8, SF-9, guard order docs

### Промпт

```
Контекст: проведён инженерный аудит модуля Pricing (см. docs/modules/pricing-audit-report.md). Нужно привести docs/modules/pricing.md в соответствие с реальным кодом.

## SF-8. price_decision nullable fields

Миграция `0015-pricing-bulk-nullable.sql` сделала `price_policy_id` и `policy_snapshot` nullable (для bulk manual сценариев). Спека всё ещё описывает их как NOT NULL.

### Фикс

В `docs/modules/pricing.md` найди описание модели `price_decision` и обнови:
- `price_policy_id` → NULLABLE (null при bulk manual pricing)
- `policy_snapshot` → NULLABLE (null при bulk manual pricing)
- Добавь комментарий: "Nullable для решений, созданных через bulk manual pricing (без привязки к policy)"

## SF-9. Competitor API paths — убрать дублирование

В `docs/modules/pricing.md` есть два блока с описанием competitor API:
- Блок "Competitor Price Model" (строки ~224-233): `/matches`, `bulk-upload`
- Таблица REST API (строки ~1077-1084): `/match`, `/observations`, `/upload-csv`

Код соответствует первому варианту.

### Фикс

Удали второй блок (таблица REST API для competitors) и оставь первый (Competitor Price Model). Или объедини в один непротиворечивый блок, следуя коду.

## Guard order docs

После фикса MF-5 (чат 4) порядок guards в коде будет соответствовать спеке. Проверь, что в `docs/modules/pricing.md` в секции Guards порядок совпадает с тем, что задано в чате 4.

## execution_mode_changed_at

Поле `execution_mode_changed_at` есть в `price_policy` entity, но не описано в спеке.

### Фикс

Добавь его в описание модели `price_policy` в спеке:
- `execution_mode_changed_at` — TIMESTAMPTZ, NULLABLE — "Время последнего изменения execution_mode. Используется FullAutoSafetyGate для проверки минимальной выдержки"

## Закоммить
```

---

## Чат 9: Тесты для всех фиксов

Финальный чат — после всех фиксов.

### Промпт

```
Контекст: проведён инженерный аудит модуля Pricing (см. docs/modules/pricing-audit-report.md). Все фиксы из аудита реализованы. Нужно убедиться, что тестовое покрытие адекватно, и закрыть критические gaps.

## Задача

Пройди по каждому фиксу из аудита и проверь, есть ли тесты для исправленного поведения. Если нет — добавь.

### MF-1. MarginGuard

Тест: `MarginGuardTest.java` (или создай)
- `should_pass_when_threshold_is_null` — config.effectiveMinMarginPct() == null → GuardResult.pass
- `should_block_when_margin_below_threshold` — margin < threshold → GuardResult.block
- `should_pass_when_margin_above_threshold` — margin >= threshold → GuardResult.pass

### MF-2. PolicyResolver

Тест: `PolicyResolverTest.java` (или создай)
- `should_prefer_lower_policy_id_as_tiebreaker` — два assignment с одинаковой specificity и priority, разными policy id → побеждает меньший

### MF-3. PricingActionMaterializerConsumer

Тест: `PricingActionMaterializerConsumerTest.java`
- `should_call_createAction_with_correct_params` — mock ActionService, verify вызов с правильными аргументами
- `should_log_error_on_invalid_payload` — невалидный JSON → log error, не бросает exception

### MF-4. resumeRun

Тест в `PricingRunApiServiceTest.java`:
- `should_enqueue_outbox_after_resume` — after resume, verify outbox event published
- `should_approve_on_hold_actions_on_resume` — verify ON_HOLD → approved transition

### MF-5. Guard order

Тест: `GuardOrderTest.java` (или добавь в существующий)
- `should_execute_guards_in_spec_order` — собрать все guards из registry, проверить order() values: 10, 11, 12, 15, 20, 21, 22, 23, 25, 26

### Дополнительные test gaps (из аудита)

1. **BlastRadiusBreaker**: тест на порог (>5% affected → PAUSE) — если не покрыт
2. **FullAutoSafetyGate**: тест на все 5 условий — если не покрыт полностью
3. **BigDecimal scale comparison**: убедись, что тесты сравнивают через `compareTo`, а не `equals`

## Общие требования

- Используй JUnit 5 + Mockito + AssertJ
- Naming: `should_<expectedBehavior>_when_<condition>`
- Файлы `*Test.java` рядом с тестируемым файлом в test tree
- Не создавай integration-тесты — только unit-тесты (быстрые, изолированные)
- Закоммить
```

---

## Порядок выполнения и зависимости

```
Чат 1 (MF-1, MF-2, SF-1, SF-3, SF-10) — независимый, начинай с него
    │
    ├── Чат 2 (MF-3) — независимый, можно параллельно с Чатом 1
    │
    ├── Чат 3 (MF-4, SF-6) — зависит от Чата 2 (нужен PRICING_ACTION_REQUESTED event type)
    │
    ├── Чат 4 (MF-5) — независимый
    │
    ├── Чат 5 (MF-6, MF-7) — независимый (frontend)
    │
    ├── Чат 6 (SF-4, SF-5) — независимый (backend enrichment)
    │
    ├── Чат 7 (SF-7) — независимый
    │
    └── Чат 8 (SF-8, SF-9, docs) — после Чата 4 (guard order нужен для docs)
         │
         └── Чат 9 (тесты) — после всех остальных чатов
```

## Параллелизм

Можно запускать одновременно:
- **Волна 1:** Чат 1 + Чат 2 + Чат 4 + Чат 5 + Чат 6 + Чат 7
- **Волна 2:** Чат 3 (после Чата 2) + Чат 8 (после Чата 4)
- **Волна 3:** Чат 9 (после всех)
