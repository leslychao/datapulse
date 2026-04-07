# AI Seller Intelligence — Промпты для реализации

**Базовый документ:** `docs/features/2026-04-06-ai-seller-intelligence-tbd.md`
**Правило:** 1 промпт = 1 чат. Каждый промпт самодостаточен — содержит всё, что нужно для реализации.

---

## Порядок выполнения

```
#1  Infra: Maven module + LlmClient + config
#2  Infra: DB migrations
#3  F10 Backend: Unknown ops detector + classifier
#4  F10 Frontend: Classification review page
#5  F1 Backend: Command intent endpoint
#6  F1 Frontend: AI in Command Palette
#7  F8 Backend: Briefing data aggregator + API
#8  F8 Frontend: Morning briefing overlay
#9  F3 Backend: Weekly digest scheduler
#10 F4 Backend: Pricing advisor service
#11 F4 Frontend: Advisor panel section
#12 F2 Backend: Anomaly explanation generator
#13 F5 Backend: Insight detectors + scheduler
```

---

## Промпт #1 — Infra: Maven module + LlmClient + config

```
Реализуй инфраструктурный модуль datapulse-ai для интеграции с self-hosted LLM (vLLM + Qwen3-8B).

Полная спецификация в docs/features/2026-04-06-ai-seller-intelligence-tbd.md, секция 2 "Инфраструктура: модуль datapulse-ai".

Что нужно сделать:

1. Создать Maven-модуль backend/datapulse-ai:
   - pom.xml с parent datapulse-parent, зависимости: datapulse-common, datapulse-platform, spring-boot-starter-webflux, spring-boot-starter-data-jpa, caffeine, resilience4j-circuitbreaker, shedlock-spring.
   - Зарегистрировать модуль в backend/pom.xml (<module> + <dependencyManagement>).
   - Добавить зависимость datapulse-ai в backend/datapulse-api/pom.xml.

2. Создать структуру пакетов io.datapulse.ai:
   - config/AiProperties.java — @ConfigurationProperties(prefix = "datapulse.ai") с полями: baseUrl, modelName, timeoutFastMs, timeoutSlowMs. Метод isEnabled() = !baseUrl.isBlank().
   - config/AiConfig.java — @Configuration + @EnableConfigurationProperties(AiProperties.class). Бины: llmWebClient (@ConditionalOnProperty), aiCircuitBreaker (CircuitBreaker с failureRateThreshold=50, waitDurationInOpenState=30s, slidingWindowSize=10, minimumNumberOfCalls=3).
   - domain/LlmClient.java — @Service. Синхронный метод chat(systemPrompt, userPrompt, timeoutMs, temperature, maxTokens) → String. Использует llmWebClient POST /chat/completions (OpenAI-compatible API). Обёрнут в circuitBreaker.executeSupplier(). При недоступности — бросает AiUnavailableException.
   - domain/AiUnavailableException.java — extends RuntimeException.
   - Вложенные records в LlmClient: ChatMessage(role, content), ChatRequest(model, messages, temperature, max_tokens), ChatChoice(message), ChatResponse(choices).

3. Добавить aiExecutor в backend/datapulse-platform/src/main/java/io/datapulse/platform/config/AsyncConfig.java:
   - @Bean("aiExecutor") с параметрами: prefix "ai-", coreSize 2, maxSize 4, queueCapacity 30.

4. Добавить секцию в backend/datapulse-api/src/main/resources/application.yml:
   datapulse:
     ai:
       base-url: ${LLM_BASE_URL:}
       model-name: ${LLM_MODEL_NAME:datapulse}
       timeout-fast-ms: ${LLM_TIMEOUT_FAST:2000}
       timeout-slow-ms: ${LLM_TIMEOUT_SLOW:15000}

Стиль кода — по правилам проекта (coding-style.mdc, package-structure.mdc). Constructor injection через @RequiredArgsConstructor. Google Java Style (2 spaces indent). @Slf4j на классах с логированием.
```

---

## Промпт #2 — Infra: DB migrations

```
Создай миграции PostgreSQL для AI-фич.

Полная спецификация в docs/features/2026-04-06-ai-seller-intelligence-tbd.md, секция 3 "Data model: миграции".

Что нужно сделать:

1. Создать файл backend/datapulse-api/src/main/resources/db/changelog/changes/0027-ai-tables.sql с тремя таблицами:

   a) ai_suggested_classification — для F10 (auto-classification неизвестных финансовых операций):
      id BIGSERIAL PK, workspace_id (FK workspace), source_platform VARCHAR(10), operation_type_name VARCHAR(255), sample_amount DECIMAL, has_posting BOOLEAN, has_sku BOOLEAN, occurrence_count INT DEFAULT 1, suggested_entry_type VARCHAR(60), suggested_measure VARCHAR(60), confidence DECIMAL(3,2), reasoning TEXT, status VARCHAR(20) DEFAULT 'PENDING', reviewed_by (FK app_user nullable), reviewed_at TIMESTAMPTZ, created_at TIMESTAMPTZ DEFAULT now(). UNIQUE (workspace_id, source_platform, operation_type_name). Index по (workspace_id, status).

   b) ai_digest — для F8/F3 (briefing/digest):
      id BIGSERIAL PK, workspace_id (FK workspace), digest_type VARCHAR(20), period_start DATE, period_end DATE, structured_data JSONB, generated_text TEXT, generated_at TIMESTAMPTZ, created_at TIMESTAMPTZ DEFAULT now(). UNIQUE (workspace_id, digest_type, period_start).

   c) ai_insight — для F5 (proactive insights):
      id BIGSERIAL PK, workspace_id (FK workspace), insight_type VARCHAR(60), entity_type VARCHAR(30), entity_id BIGINT, severity VARCHAR(20) DEFAULT 'INFO', structured_data JSONB, generated_text TEXT, dedupe_key VARCHAR(255), status VARCHAR(20) DEFAULT 'ACTIVE', dismissed_by (FK app_user nullable), dismissed_at TIMESTAMPTZ, created_at TIMESTAMPTZ DEFAULT now(). UNIQUE (workspace_id, dedupe_key). Index по (workspace_id, status, created_at DESC).

   d) ALTER TABLE canonical_finance_entry ADD COLUMN IF NOT EXISTS raw_operation_type VARCHAR(255) — для сохранения оригинального типа операции провайдера.

2. Зарегистрировать в backend/datapulse-api/src/main/resources/db/changelog/db.changelog-master.yaml.

3. Создать JPA-сущности в backend/datapulse-ai/src/main/java/io/datapulse/ai/persistence/:
   - AiSuggestedClassificationEntity.java (@Getter @Setter, extends BaseEntity или standalone с id).
   - AiDigestEntity.java
   - AiInsightEntity.java
   И соответствующие JpaRepository интерфейсы.

4. Добавить в NotificationType enum (backend/datapulse-audit-alerting/.../NotificationType.java): AI_DIGEST, AI_INSIGHT.

5. Добавить в MessageCodes (backend/datapulse-common/.../MessageCodes.java): AI_DIGEST_WEEKLY_TITLE, AI_DIGEST_WEEKLY_BODY, AI_INSIGHT_TITLE, AI_CLASSIFICATION_ACCEPTED, AI_CLASSIFICATION_REJECTED, AI_UNAVAILABLE.

6. Добавить ключи в frontend/src/locale/ru.json:
   "ai.digest.weekly.title": "Еженедельная сводка",
   "ai.digest.weekly.body": "Доступна сводка за прошедшую неделю",
   "ai.insight.title": "AI-инсайт",
   "ai.classification.accepted": "Классификация принята",
   "ai.classification.rejected": "Классификация отклонена",
   "ai.unavailable": "AI-сервис временно недоступен",
   "ai.briefing.title": "Доброе утро",
   "ai.briefing.pending_actions": "Ожидают одобрения",
   "ai.briefing.failed_actions": "Проваленные действия",
   "ai.briefing.critical_stock": "Критический запас",
   "ai.briefing.open_alerts": "Открытые алерты",
   "ai.briefing.dismiss": "Закрыть",
   "ai.advisor.title": "AI Совет",
   "ai.advisor.refresh": "Обновить",
   "ai.advisor.unavailable": "Совет недоступен",
   "settings.ai_classifications.title": "AI-классификация операций",
   "settings.ai_classifications.accept": "Принять",
   "settings.ai_classifications.reject": "Отклонить"

Формат миграций: --liquibase formatted sql / --changeset datapulse:0027-... / --rollback DROP TABLE ...
```

---

## Промпт #3 — F10 Backend: Unknown ops detector + classifier

```
Реализуй бэкенд для автоклассификации неизвестных финансовых операций (F10).

Полная спецификация в docs/features/2026-04-06-ai-seller-intelligence-tbd.md, секция 4 "F10: Auto-classification".

Контекст:
- В ETL нормалайзерах (OzonFinanceNormalizer, WbNormalizer) неизвестные типы операций маппятся в FinanceEntryType.OTHER.
- Ozon: OzonFinanceNormalizer.java строки 39-42 уже логирует warn "Unmapped Ozon finance operation_type".
- Канонические записи с entry_type='OTHER' попадают в canonical_finance_entry (PostgreSQL).
- Канонические MeasureColumn values: REVENUE, REFUND, MARKETPLACE_COMMISSION, ACQUIRING, LOGISTICS, STORAGE, PENALTIES, ACCEPTANCE, MARKETING, COMPENSATION, OTHER.
- Таблица ai_suggested_classification уже создана в миграции #2.
- LlmClient уже создан в #1.

Что нужно сделать:

1. Обновить OzonFinanceNormalizer: при entryType == OTHER — сохранять tx.operationType() в NormalizedFinanceItem (добавить поле rawOperationType в record).

2. Обновить WbNormalizer.normalizeFinance: при entryType == OTHER — сохранять row.docTypeName() в rawOperationType.

3. Обновить CanonicalEntityMapper.toFinanceEntry: маппить rawOperationType → entity.setRawOperationType().

4. Обновить CanonicalFinanceEntryEntity: добавить поле rawOperationType (String, column raw_operation_type).

5. Создать в datapulse-ai:

   a) domain/classification/UnknownOpsDetector.java — @Repository с JdbcTemplate:
      - findUnknownOps(workspaceId): SELECT из canonical_finance_entry WHERE entry_type='OTHER' за 30 дней, GROUP BY source_platform, raw_operation_type. Возвращает List<UnknownOperation> record.

   b) domain/classification/ClassificationService.java — @Service:
      - classifyBatch(workspaceId): получает unknown ops, фильтрует уже существующие в ai_suggested_classification, для каждой новой — вызывает LlmClient.chat() с промптом классификации (system prompt описывает MeasureColumn categories, user prompt — конкретную операцию). Парсит JSON-ответ {category, confidence, reasoning}. Сохраняет в ai_suggested_classification.

   c) scheduling/ClassificationScheduler.java — @Component:
      - @Scheduled(cron = "${datapulse.ai.classification-cron:0 0 3 * * *}") + @SchedulerLock
      - Для каждого active workspace вызывает classifyBatch.

   d) api/AiClassificationController.java — @RestController:
      - GET /api/workspaces/{workspaceId}/ai/classifications?status=PENDING → Page<ClassificationResponse>
      - POST /api/workspaces/{workspaceId}/ai/classifications/{id}/accept → 204
      - POST /api/workspaces/{workspaceId}/ai/classifications/{id}/reject → 204
      - @PreAuthorize для OWNER/ADMIN.

   e) api/ClassificationResponse.java — record с полями из entity.

Промпт для LLM (system):
"""
You are a financial operations classifier for marketplace sellers (Ozon, Wildberries).
Classify the unknown operation into one of these canonical categories:
REVENUE, REFUND, MARKETPLACE_COMMISSION, ACQUIRING, LOGISTICS, STORAGE, PENALTIES, ACCEPTANCE, MARKETING, COMPENSATION, OTHER.
Respond ONLY with valid JSON: {"category": "LOGISTICS", "confidence": 0.85, "reasoning": "...one sentence..."}
"""

Промпт для LLM (user):
"""
Operation: "{operationTypeName}"
Platform: {sourcePlatform}
Sample amount: {sampleAmount}
Has posting reference: {hasPosting}
Has SKU reference: {hasSku}
Occurrences: {occurrenceCount}
"""

Temperature: 0.1, max_tokens: 200, timeout: timeoutSlowMs.
```

---

## Промпт #4 — F10 Frontend: Classification review page

```
Реализуй фронтенд-страницу для ревью AI-классификаций неизвестных финансовых операций (F10).

Полная спецификация в docs/features/2026-04-06-ai-seller-intelligence-tbd.md, секция 4.4 "Frontend".

Контекст:
- Backend API уже реализован (промпт #3):
  GET /api/workspaces/{wsId}/ai/classifications?status=PENDING → Page<ClassificationResponse>
  POST .../classifications/{id}/accept → 204
  POST .../classifications/{id}/reject → 204
- ClassificationResponse: id, sourcePlatform, operationTypeName, sampleAmount, hasPosting, hasSku, occurrenceCount, suggestedEntryType, suggestedMeasure, confidence, reasoning, status, createdAt.
- Стек: Angular 19, standalone components, TanStack Query, AG Grid, Tailwind CSS 4, Lucide icons, @ngx-translate.
- Стиль — по правилам frontend-coding-style.mdc.

Что нужно сделать:

1. Добавить AiApiService метод (или отдельный AiClassificationApiService) в core/api/:
   - listClassifications(workspaceId, status, pageable): Observable<Page<ClassificationResponse>>
   - acceptClassification(workspaceId, id): Observable<void>
   - rejectClassification(workspaceId, id): Observable<void>

2. Создать модель в core/models/ai.model.ts:
   - interface ClassificationResponse с полями из API.
   - type ClassificationStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED';

3. Создать страницу frontend/src/app/features/settings/ai-classifications/:
   - ai-classifications-page.component.ts — standalone, OnPush.
   - AG Grid с колонками: operationTypeName, sourcePlatform, suggestedEntryType, confidence (badge с цветом по уровню), reasoning (tooltip), occurrenceCount, sampleAmount, actions (Accept/Reject кнопки).
   - Фильтр по status (tabs: Pending / Accepted / Rejected / All).
   - TanStack Query для данных, injectMutation для accept/reject.
   - Toast при accept: 'ai.classification.accepted', при reject: 'ai.classification.rejected'.
   - Confidence badge: >= 0.8 зелёный (--status-success), 0.5-0.8 жёлтый (--status-warning), < 0.5 красный (--status-error).

4. Добавить route в settings.routes.ts:
   { path: 'ai-classifications', loadComponent: () => import('./ai-classifications/ai-classifications-page.component').then(m => m.AiClassificationsPageComponent), data: { breadcrumb: 'AI-классификация' } }

5. Добавить пункт в settings navigation (settings-layout): "AI-классификация" с иконкой Sparkles из Lucide.

Локализация — через translate pipe, ключи из ru.json (уже добавлены в промпте #2).
Selector prefix: dp-.
```

---

## Промпт #5 — F1 Backend: Command intent endpoint

```
Реализуй бэкенд-эндпоинт для Smart Command Palette (F1) — конвертация natural language запроса в grid-фильтры или навигацию.

Полная спецификация в docs/features/2026-04-06-ai-seller-intelligence-tbd.md, секция 5 "F1: Smart Command Palette".

Контекст:
- LlmClient уже создан (промпт #1).
- Фильтры грида OfferFilter: marketplaceType (WB/OZON), status, marginMin/marginMax, stockRisk, hasManualLock, hasActivePromo, lastDecision, lastActionStatus, skuCode, productName, connectionId, categoryId.
- Backend grid endpoint: GET /api/workspaces/{wsId}/grid с этими фильтрами как query params.

Что нужно сделать в datapulse-ai:

1. api/AiCommandController.java — @RestController:
   POST /api/workspaces/{workspaceId}/ai/command
   Body: CommandRequest record (String query)
   Response: CommandIntentResponse record

2. api/CommandRequest.java — record(String query).

3. api/CommandIntentResponse.java — record(String action, Object filters, NavigateTarget navigate, String text, String link).
   NavigateTarget — record(String module, String view, Map<String, String> params).
   action: "filter" | "navigate" | "quick_answer" | "unknown".

4. domain/command/CommandIntentService.java — @Service:
   - parseIntent(String userQuery): CommandIntentResponse.
   - System prompt содержит полную схему фильтров OfferFilter и навигационных targets (analytics/pnl-summary, analytics/inventory, analytics/returns, pricing/policies, pricing/runs, pricing/decisions, promo/campaigns, settings/connections).
   - Вызывает llmClient.chat() с temperature=0.0, maxTokens=300, timeout=timeoutFastMs (2s).
   - Парсит JSON из ответа LLM через ObjectMapper.
   - При ошибке парсинга — возвращает {action: "unknown"}.

5. Graceful degradation: при AiUnavailableException — возвращать {action: "unknown"}, контроллер не должен бросать 500.

System prompt — из TBD секция 5.2 (полная schema фильтров и навигационных targets).
Temperature: 0.0, max_tokens: 300, timeout: timeoutFastMs (2s).
```

---

## Промпт #6 — F1 Frontend: AI in Command Palette

```
Интегрируй AI-навигацию в существующий Command Palette (F1).

Полная спецификация в docs/features/2026-04-06-ai-seller-intelligence-tbd.md, секция 5.3 "Frontend".

Контекст:
- Command Palette уже существует: frontend/src/app/shared/shell/command-palette/command-palette.component.ts.
- Текущий функционал: entity search через SearchApiService, статические команды, навигация.
- GridStore: frontend/src/app/shared/stores/grid.store.ts — метод updateFilters(filters: OfferFilter).
- Backend API (промпт #5): POST /workspaces/{wsId}/ai/command → CommandIntentResponse {action, filters, navigate, text, link}.
- AiApiService уже имеет метод sendCommand (или создать).

Что нужно сделать:

1. В AiApiService (core/api/ai-api.service.ts) добавить метод:
   sendCommand(workspaceId: number, query: string): Observable<CommandIntentResponse>
   POST ${apiUrl}/workspaces/${workspaceId}/ai/command, body: { query }

2. Создать интерфейс CommandIntentResponse в core/models/ai.model.ts:
   action: 'filter' | 'navigate' | 'quick_answer' | 'unknown';
   filters?: OfferFilter;
   navigate?: { module: string; view: string; params?: Record<string, string> };
   text?: string;
   link?: string;

3. Модифицировать CommandPaletteComponent:
   a) После entity search debounce: если результатов мало (< 2) И query.length >= 3 — показать группу "AI-навигатор" со spinner и запустить sendCommand.
   b) При ответе с action === 'filter':
      - Inject GridStore, вызвать gridStore.updateFilters(response.filters).
      - navigateInWorkspace('grid').
      - Закрыть палитру.
   c) При action === 'navigate':
      - navigateInWorkspace(response.navigate.module + '/' + response.navigate.view).
      - Закрыть палитру.
   d) При action === 'quick_answer':
      - Показать текст response.text inline в палитре (новый тип group item).
      - Если есть link — показать как кликабельную ссылку.
   e) При action === 'unknown' или ошибке — не показывать AI-группу (silent fallback).

4. Визуальное оформление:
   - AI-группа с заголовком "AI-навигатор" и иконкой Sparkles.
   - Spinner (dp-spinner или animated Sparkles) пока запрос летит.
   - Бейдж "AI" рядом с результатом.

5. Fallback: если LLM endpoint вернул ошибку или timeout — не ломать существующий search, просто скрыть AI-группу.
```

---

## Промпт #7 — F8 Backend: Briefing data aggregator + API

```
Реализуй бэкенд для Morning Briefing (F8) — утренняя сводка при входе в workspace.

Полная спецификация в docs/features/2026-04-06-ai-seller-intelligence-tbd.md, секция 6 "F8: Morning Briefing".

Контекст:
- LlmClient уже создан (промпт #1).
- Таблица ai_digest уже создана (промпт #2).
- Аналитические данные доступны через:
  - PnlReadRepository: AGGREGATED_SUMMARY_SQL (суммы из mart_product_pnl).
  - InventoryReadRepository: OVERVIEW_SQL (stock risk counts, frozen capital).
  - ReturnsReadRepository: summary (return rate).
- Операционные данные в PostgreSQL: price_action (статусы), alert_event (open alerts), sync_state (последние синхронизации).
- datapulse-ai зависит от datapulse-analytics-pnl (добавить в pom.xml если не добавлено).

Что нужно сделать:

1. Добавить зависимость datapulse-analytics-pnl в datapulse-ai/pom.xml.

2. domain/briefing/BriefingData.java — record с полями:
   revenueCurrentMonth, revenuePrevMonth, marginPctCurrent, marginPctPrev, fullPnlCurrent (из P&L),
   criticalStockCount, warningStockCount, frozenCapital (из inventory),
   pendingApprovalCount, failedActionCount (из price_action в PG),
   openAlertCount, criticalAlertCount (из alert_event в PG),
   recentSyncs (List<SyncInfo>: connectionName, lastSyncAt, status),
   returnRateCurrent, returnRatePrev.

3. domain/briefing/BriefingDataAggregator.java — @Service:
   - aggregate(workspaceId): BriefingData.
   - Переиспользует существующие read-repositories из datapulse-analytics-pnl.
   - Для PG-данных (actions, alerts, syncs) — собственные JDBC-запросы через JdbcTemplate.

4. domain/briefing/BriefingService.java — @Service:
   - getBriefing(workspaceId): BriefingResponse.
   - Проверяет кэш: ai_digest WHERE workspace_id=? AND digest_type='MORNING' AND period_start=today.
   - Если есть — возвращает.
   - Если нет: aggregate → LLM verbalize (если enabled) → save ai_digest → return.
   - При LLM unavailable: narrative = null, aiGenerated = false.

5. api/AiBriefingController.java — @RestController:
   GET /api/workspaces/{workspaceId}/ai/briefing → BriefingResponse

6. api/BriefingResponse.java — record(BriefingData data, String narrative, OffsetDateTime generatedAt, boolean aiGenerated).

System prompt из TBD секция 6.4.
Temperature: 0.3, max_tokens: 500, timeout: timeoutSlowMs.
```

---

## Промпт #8 — F8 Frontend: Morning briefing overlay

```
Реализуй фронтенд Morning Briefing — overlay при первом входе за день (F8).

Полная спецификация в docs/features/2026-04-06-ai-seller-intelligence-tbd.md, секция 6.5 "Frontend".

Контекст:
- Backend API (промпт #7): GET /workspaces/{wsId}/ai/briefing → BriefingResponse {data, narrative, generatedAt, aiGenerated}.
- ShellComponent: frontend/src/app/shared/shell/shell.component.ts — root layout, содержит router-outlet, command-palette, toast-container.
- AiApiService уже имеет метод getBriefing.
- Стек: Angular 19, standalone, OnPush, signals, TanStack Query, Tailwind, Lucide, @ngx-translate.

Что нужно сделать:

1. Создать frontend/src/app/shared/shell/morning-briefing/morning-briefing.component.ts:
   - Standalone, OnPush.
   - Selector: dp-morning-briefing.
   - Overlay поверх main content (z-50, полупрозрачный backdrop).
   - Логика показа:
     - signal shouldShow = computed: workspaceId loaded AND localStorage `lastBriefingDismissed_{wsId}` < today (compare dates).
   - При показе: TanStack injectQuery для GET /ai/briefing.
   - Dismiss: кнопка → localStorage.setItem(`lastBriefingDismissed_{wsId}`, today ISO) → скрыть.

2. Контент overlay:
   - Заголовок: "Доброе утро" (translate: 'ai.briefing.title') + дата.
   - Если narrative !== null — текстовый блок (AI-generated, с badge "AI").
   - Числовые KPI-карточки (всегда, даже без narrative):
     - Ожидают одобрения: data.pendingApprovalCount → клик → navigate pricing/actions?status=PENDING_APPROVAL.
     - Проваленные действия: data.failedActionCount → клик → navigate pricing/actions?status=FAILED.
     - Критический запас: data.criticalStockCount → клик → navigate grid?stockRisk=CRITICAL.
     - Открытые алерты: data.openAlertCount → клик → navigate alerts.
   - Loading state: skeleton blocks.
   - Error state: скрыть overlay (не показывать ошибку, graceful).

3. Встроить в ShellComponent:
   - Добавить <dp-morning-briefing /> в конце шаблона (рядом с command-palette и toast-container).

4. Стилизация:
   - Backdrop: bg-black/40, fixed inset-0, z-50.
   - Card: centered, max-w-lg, bg-[var(--bg-primary)], shadow-md, rounded-lg, p-6.
   - KPI карточки: grid 2x2, кликабельные с hover.
   - Кнопка dismiss: внизу, full-width, text style.
```

---

## Промпт #9 — F3 Backend: Weekly digest scheduler

```
Реализуй бэкенд еженедельного дайджеста (F3).

Полная спецификация в docs/features/2026-04-06-ai-seller-intelligence-tbd.md, секция 7 "F3: Weekly Digest".

Контекст:
- BriefingDataAggregator уже создан (промпт #7) — переиспользовать для агрегации, но за другой период (prev week Mon-Sun вместо today).
- LlmClient, ai_digest, NotificationType.AI_DIGEST — всё уже есть.
- NotificationService.fanOut() — существующий метод в datapulse-audit-alerting.

Что нужно сделать:

1. domain/digest/WeeklyDigestData.java — record с полями (аналог BriefingData, но за неделю):
   revenueWeek, revenuePrevWeek, revenueDeltaPct, revenueByMarketplace (Map<String, BigDecimal>),
   marginPctWeek, marginPctPrevWeek,
   stockOutCount, returnRate, returnRatePrev,
   promoAccepted, promoDeclined,
   pricingChangesTotal, pricingChangesSucceeded, pricingChangesFailed,
   newAlerts, criticalAlerts.

2. domain/digest/WeeklyDigestService.java — @Service:
   - generateDigest(workspaceId): void.
   - Проверяет, не сгенерирован ли уже digest для prev week.
   - Агрегирует данные за prev Mon-Sun (используя те же repositories что BriefingDataAggregator, но с другим period).
   - LLM verbalize (если enabled).
   - Сохраняет в ai_digest (digest_type='WEEKLY', period_start=prev_monday, period_end=prev_sunday).
   - Fan-out notification: NotificationType.AI_DIGEST, title=MessageCodes.AI_DIGEST_WEEKLY_TITLE, body=MessageCodes.AI_DIGEST_WEEKLY_BODY.

3. scheduling/DigestScheduler.java — @Component:
   - @Scheduled(cron = "${datapulse.ai.digest-cron:0 0 8 * * MON}") + @SchedulerLock(name = "aiWeeklyDigest", lockAtMostFor = "PT30M").
   - Для каждого active workspace: generateDigest(wsId).
   - try-catch на каждый workspace — один сбой не ломает остальные.

System prompt из TBD секция 7.3.
Temperature: 0.3, max_tokens: 600, timeout: timeoutSlowMs.
```

---

## Промпт #10 — F4 Backend: Pricing advisor service

```
Реализуй бэкенд Pricing Advisor — AI-совет по ценообразованию для конкретного оффера (F4).

Полная спецификация в docs/features/2026-04-06-ai-seller-intelligence-tbd.md, секция 8 "F4: Pricing Advisor".

Контекст:
- LlmClient уже создан (промпт #1).
- PriceDecisionEntity содержит signalSnapshot (JSONB) и explanationSummary.
- PricingSignalSet — record с 20+ полями (currentPrice, cogs, velocity, stock, returnRate, adCostRatio и т.д.).
- PricingClickHouseReadRepository — методы findSalesVelocity, findDaysOfCover, findAdCostRatios.
- Кэш: Caffeine (не БД), TTL 24 часа, ключ = offerId.

Что нужно сделать:

1. domain/advisor/PricingAdvisorService.java — @Service:
   - getAdvice(workspaceId, offerId): Optional<AdvisorResponse>.
   - Проверяет Caffeine кэш (24h TTL по offerId).
   - Загружает последний price_decision по offerId (PriceDecisionRepository.findTopByMarketplaceOfferIdOrderByCreatedAtDesc).
   - Парсит signalSnapshot в PricingSignalSet через ObjectMapper.
   - Строит user prompt с данными: productName, skuCode, marketplace, currentPrice, cogs, marginPct, strategyType, velocity, stock, daysOfCover, returnRate, adCostRatio, lastDecisionType, skipReason.
   - Вызывает LlmClient.chat() с thinking mode (temperature=0.3).
   - Кэширует и возвращает AdvisorResponse(advice, generatedAt).

2. config/ — добавить Caffeine Cache bean:
   @Bean public Cache<Long, AdvisorResponse> advisorCache() { return Caffeine.newBuilder().expireAfterWrite(24, TimeUnit.HOURS).maximumSize(500).build(); }

3. api/AiAdvisorController.java — @RestController:
   GET /api/workspaces/{workspaceId}/ai/advisor/{offerId}
   → AdvisorResponse (200) или 204 No Content.
   При AiUnavailableException — 204.

4. api/AdvisorResponse.java — record(String advice, OffsetDateTime generatedAt).

5. Зависимость: datapulse-ai должен зависеть от datapulse-pricing для доступа к PriceDecisionRepository и PricingSignalSet.
   Добавить в datapulse-ai/pom.xml зависимость datapulse-pricing.
   Альтернатива: дублировать DTO/query в datapulse-ai (менее предпочтительно).

System prompt из TBD секция 8.4.
Temperature: 0.3 (thinking mode), max_tokens: 400, timeout: timeoutSlowMs.
```

---

## Промпт #11 — F4 Frontend: Advisor panel section

```
Интегрируй AI Pricing Advisor в фронтенд — секция в detail panel решений и оффер-панели (F4).

Полная спецификация в docs/features/2026-04-06-ai-seller-intelligence-tbd.md, секция 8.6 "Frontend".

Контекст:
- Backend API (промпт #10): GET /workspaces/{wsId}/ai/advisor/{offerId} → AdvisorResponse {advice, generatedAt} или 204.
- Decision detail panel: frontend/src/app/features/pricing/decisions/decision-detail-panel.component.ts.
- Offer detail panel: frontend/src/app/features/grid/components/offer-detail/offer-detail-panel.component.ts (вкладка price-journal).
- AiApiService уже создан.

Что нужно сделать:

1. Добавить метод в AiApiService:
   getAdvisorAdvice(workspaceId: number, offerId: number): Observable<AdvisorResponse | null>
   GET ${apiUrl}/workspaces/${workspaceId}/ai/advisor/${offerId}
   Обработка 204: return of(null).

2. Создать shared/components/ai-advisor-block.component.ts — standalone, OnPush:
   - Input: offerId (required signal input).
   - Inject WorkspaceContextStore для workspaceId, AiApiService.
   - TanStack injectQuery: queryKey ['ai-advisor', workspaceId, offerId], enabled: !!offerId.
   - Визуал:
     - Заголовок "AI Совет" (translate: 'ai.advisor.title') + badge "AI" (bg-[var(--accent-subtle)]).
     - Если advice !== null: текст совета, дата генерации (relative time pipe).
     - Кнопка "Обновить" (translate: 'ai.advisor.refresh') — invalidate query.
     - Loading: skeleton text block.
     - Null response (204) или error: не показывать секцию (скрыть компонент полностью).
   - Collapsible: по умолчанию раскрыт.

3. Встроить <dp-ai-advisor-block [offerId]="offerId"> в:
   a) decision-detail-panel — после блока explanation.
   b) offer-detail-panel — в overview tab, после блока pricing info.

4. Стилизация:
   - Border-left: 2px solid var(--accent-primary) — визуальное выделение AI-секции.
   - Текст: text-[var(--text-secondary)], text-sm.
   - Badge "AI": px-1.5 py-0.5, rounded-[var(--radius-sm)], text-xs, font-medium, bg-[var(--accent-subtle)], text-[var(--accent-primary)].
```

---

## Промпт #12 — F2 Backend: Anomaly explanation generator

```
Реализуй бэкенд генерации AI-объяснений для аномалий (F2).

Полная спецификация в docs/features/2026-04-06-ai-seller-intelligence-tbd.md, секция 9 "F2: Anomaly Explanation".

Контекст:
- LlmClient уже создан (промпт #1).
- Alert system: AlertEventCreatedEvent публикуется при создании alert_event. Record: alertEventId, ruleType (AlertRuleType).
- AlertRuleType включает SPIKE_DETECTION, RESIDUAL_ANOMALY.
- alert_event.details — JSONB поле.
- AlertEventRepository (datapulse-audit-alerting) — доступ к alert events.
- P&L данные — через ClickHouse marts (PnlReadRepository и т.д.).
- datapulse-ai должен зависеть от datapulse-audit-alerting.

Что нужно сделать:

1. Добавить зависимость datapulse-audit-alerting в datapulse-ai/pom.xml.

2. domain/explanation/AnomalyExplanationListener.java — @Component:
   - @Async("aiExecutor") @EventListener на AlertEventCreatedEvent.
   - Фильтр: только ruleType == SPIKE_DETECTION || RESIDUAL_ANOMALY.
   - Если AI не enabled — return.
   - Загрузить alert_event по id.
   - Извлечь structured data из details JSONB (product info, anomaly type, metrics).
   - Собрать P&L delta context (из ClickHouse marts: текущий vs предыдущий период по connection/product).
   - Вызвать LlmClient.chat() с thinking mode.
   - Обновить alert_event.details: добавить поле "ai_explanation" в JSONB.

3. domain/explanation/PnlDeltaQueryService.java — @Service:
   - getProductDelta(connectionId, sellerSkuId, currentPeriod, prevPeriod): PnlDelta record.
   - SQL к mart_product_pnl: сравнение двух периодов для одного SKU.
   - PnlDelta record: revenueCurrent, revenuePrev, marginCurrent, marginPrev, logisticsDelta, commissionDelta, returnRateCurrent, returnRatePrev и т.д.

4. persistence/ — добавить метод в AlertEventRepository (или создать свой в datapulse-ai):
   - updateAiExplanation(alertEventId, explanation): UPDATE alert_event SET details = jsonb_set(details, '{ai_explanation}', to_jsonb(?)) WHERE id = ?

5. Frontend (минимальные изменения):
   - В компонентах отображения алертов: если alert.details?.ai_explanation !== null — показать блок "AI-объяснение" с текстом и badge "AI".
   - Это можно сделать в alert-detail компонентах (найди по codebase).

System prompt из TBD секция 9.3.
Temperature: 0.3 (thinking mode), max_tokens: 500, timeout: timeoutSlowMs.
```

---

## Промпт #13 — F5 Backend: Insight detectors + scheduler

```
Реализуй бэкенд проактивных инсайтов (F5) — SQL-детекторы + LLM-вербализация + уведомления.

Полная спецификация в docs/features/2026-04-06-ai-seller-intelligence-tbd.md, секция 10 "F5: Proactive Insights".

Контекст:
- LlmClient уже создан (промпт #1).
- Таблица ai_insight уже создана (промпт #2).
- NotificationType.AI_INSIGHT, MessageCodes.AI_INSIGHT_TITLE — уже есть.
- NotificationService.fanOut() — существующий метод.
- ClickHouse marts: mart_product_pnl, mart_inventory_analysis, mart_returns_analysis.
- datapulse-ai зависит от datapulse-analytics-pnl.

Что нужно сделать:

1. domain/insight/InsightDetector.java — interface:
   String insightType();
   List<InsightCandidate> detect(long workspaceId, List<Long> connectionIds);
   record InsightCandidate(String entityType, long entityId, String entityName, Map<String, Object> structuredData);

2. Три реализации (@Component):

   a) domain/insight/PriceIncreaseCandidateDetector.java:
      - insightType() = "PRICE_INCREASE_CANDIDATE"
      - SQL к mart_product_pnl + mart_inventory_analysis (JOIN по seller_sku_id, connection_id):
        WHERE velocity_14d >= 10 AND marginPct > 25 AND returnRate < 5 AND daysOfCover > 30
      - Возвращает List<InsightCandidate> с productId, skuCode, productName, velocity, margin, returnRate, daysOfCover, estimatedGain.

   b) domain/insight/FrozenCapitalDetector.java:
      - insightType() = "FROZEN_CAPITAL"
      - SQL к mart_inventory_analysis:
        WHERE days_of_cover > 60 AND avg_daily_sales_14d < 0.5 AND frozen_capital > 10000
      - Возвращает candidates с productId, skuCode, frozenCapital, daysOfCover, velocity.

   c) domain/insight/ReturnPatternDetector.java:
      - insightType() = "RETURN_PATTERN_ALERT"
      - SQL к mart_returns_analysis:
        WHERE return_rate_pct > 3 * (SELECT avg(return_rate_pct) FROM mart_returns_analysis WHERE connection_id IN (:connIds))
      - Возвращает candidates с productId, skuCode, returnRate, avgCategoryRate, topReturnReason.

3. domain/insight/InsightService.java — @Service:
   - generateInsights(workspaceId): void.
   - Для каждого detector: detect → deduplicate (ai_insight.dedupe_key, 7 дней) → LLM verbalize → save ai_insight → fan-out notification.
   - Dedupe key format: "{insightType}:{entityType}:{entityId}".
   - Severity: PRICE_INCREASE_CANDIDATE → INFO, FROZEN_CAPITAL → WARNING, RETURN_PATTERN_ALERT → WARNING.

4. scheduling/InsightScheduler.java — @Component:
   - @Scheduled(cron = "${datapulse.ai.insight-cron:0 0 6 * * *}") + @SchedulerLock(name = "aiInsights", lockAtMostFor = "PT30M").
   - Для каждого active workspace: generateInsights(wsId).

5. Промпты для вербализации:
   - Per insight type: system prompt + structured data → 2-3 предложения с числами.
   - Temperature: 0.3, max_tokens: 300, timeout: timeoutSlowMs.

SQL-запросы используют ClickHouseReadJdbc pattern: именованные параметры, SETTINGS final = 1.
```
