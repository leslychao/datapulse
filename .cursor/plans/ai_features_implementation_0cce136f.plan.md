---
name: AI Features Implementation
overview: Создать самодостаточный TBD-документ для реализации AI-фич Тир 1 (F10, F1, F8/F3) и Тир 2 (F4, F2, F5), включая инфраструктурный модуль `datapulse-ai`, prompt templates, миграции БД, backend API и frontend интеграцию. Плюс набор атомарных промптов для реализации (1 промпт = 1 чат).
todos:
  - id: tbd-doc
    content: Написать полный TBD-документ docs/features/2026-04-06-ai-seller-intelligence-tbd.md
    status: completed
  - id: prompts-doc
    content: Написать набор промптов docs/features/2026-04-06-ai-implementation-prompts.md
    status: completed
isProject: false
---

# AI Features Implementation Plan

## Что нужно создать

Один самодостаточный документ `docs/features/2026-04-06-ai-seller-intelligence-tbd.md` содержащий:

- Полный TBD (Technical Breakdown Document) для 6 фич (F10, F1, F8, F3, F4, F2, F5)
- Инфраструктурный модуль `datapulse-ai` (Maven, config, LLM client, prompt registry, async)
- Миграции PostgreSQL (3 новые таблицы + расширение существующих)
- Backend API для каждой фичи
- Frontend интеграцию
- Набор промптов для пошаговой реализации

Отдельный файл `docs/features/2026-04-06-ai-implementation-prompts.md` с набором промптов.

## Ключевые архитектурные решения (на основе анализа кода)

### Модуль `datapulse-ai`

- Новый Maven-модуль `backend/datapulse-ai` по образцу `datapulse-pricing`
- Зависимости: `datapulse-common`, `datapulse-platform` (WebClient.Builder, AsyncConfig)
- Пакет: `io.datapulse.ai` с подпакетами `config/`, `domain/`, `api/`, `persistence/`
- LLM-клиент через `WebClient.Builder` из [WebClientConfig](backend/datapulse-platform/src/main/java/io/datapulse/platform/config/WebClientConfig.java)
- Новый executor `aiExecutor` в [AsyncConfig](backend/datapulse-platform/src/main/java/io/datapulse/platform/config/AsyncConfig.java)
- Circuit breaker по паттерну из [AnalyticsCircuitBreakerConfig](backend/datapulse-analytics-pnl/src/main/java/io/datapulse/analytics/config/AnalyticsCircuitBreakerConfig.java)
- Properties: `datapulse.ai.*` в `application.yml`

### F10: Auto-classification

- Источник данных: [FinanceEntryType](backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/FinanceEntryType.java) -- lookup maps, `fromOzonOperationType()` и `fromWbDocTypeName()` возвращают `OTHER`
- Уже есть warn-лог в [OzonFinanceNormalizer](backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/ozon/OzonFinanceNormalizer.java) строки 39-42
- Новая таблица `ai_suggested_classification` (PG)
- Scheduled job в `datapulse-ai/scheduling/`
- Промпт: operation_type_name + amount + has_posting + has_sku + список canonical MeasureColumn values
- Batch: сбор уникальных OTHER из `canonical_finance_entry` за последние N дней, дедупликация по operation_type_name

### F1: Smart Command Palette

- Текущий компонент: [CommandPaletteComponent](frontend/src/app/shared/shell/command-palette/command-palette.component.ts)
- Фильтры грида: [OfferFilter](frontend/src/app/core/models/offer.model.ts) -- 13 полей
- Store: [GridStore](frontend/src/app/shared/stores/grid.store.ts) -- `updateFilters(filters)`, `resetFilters()`
- Ctrl+K уже перехватывается в Command Palette
- Расширение: при вводе текста >= 3 символов и отсутствии совпадений по entity search -- отправка в AI endpoint
- Backend: `POST /api/workspaces/{id}/ai/command` -> LLM -> structured JSON -> возврат `{action, filters?, navigate?, text?}`
- Frontend: новый тип результата в Command Palette, применение filters через `gridStore.updateFilters()`

### F8/F3: Morning Briefing / Weekly Digest

- Уведомления: [NotificationType](backend/datapulse-audit-alerting/src/main/java/io/datapulse/audit/domain/NotificationType.java) -- добавить `AI_DIGEST`, `AI_INSIGHT`
- WebSocket: уже есть `/user/queue/notifications` через STOMP
- Shell: [ShellComponent](frontend/src/app/shared/shell/shell.component.ts) -- место для briefing overlay
- Данные: переиспользовать [PnlReadRepository](backend/datapulse-analytics-pnl/src/main/java/io/datapulse/analytics/persistence/PnlReadRepository.java) (AGGREGATED_SUMMARY_SQL), [InventoryReadRepository](backend/datapulse-analytics-pnl/src/main/java/io/datapulse/analytics/persistence/InventoryReadRepository.java) (OVERVIEW_SQL), [ReturnsReadRepository](backend/datapulse-analytics-pnl/src/main/java/io/datapulse/analytics/persistence/ReturnsReadRepository.java)
- Новая таблица `ai_digest` (PG)
- F8: lazy generation при первом входе за день, кэш до конца дня
- F3: cron понедельник 08:00 + ShedLock

### F4: Pricing Advisor

- Decision entity: [PriceDecisionEntity](backend/datapulse-pricing/src/main/java/io/datapulse/pricing/persistence/PriceDecisionEntity.java) -- `signalSnapshot` (JSONB), `explanationSummary`
- Signals: [PricingSignalSet](backend/datapulse-pricing/src/main/java/io/datapulse/pricing/domain/PricingSignalSet.java) -- 20+ полей
- CH queries: [PricingClickHouseReadRepository](backend/datapulse-pricing/src/main/java/io/datapulse/pricing/persistence/PricingClickHouseReadRepository.java) -- velocity, days of cover, ad cost
- Frontend panel: [decision-detail-panel](frontend/src/app/features/pricing/decisions/decision-detail-panel.component.ts)
- Lazy generation on-demand при открытии detail panel, кэш 24ч
- Предусловие: Phase C completion (сигналы из CH подключены)

### F2: Anomaly Explanation

- Алерты: `alert_event.details` JSONB, типы SPIKE_DETECTION / RESIDUAL_ANOMALY
- Расширение: новое поле `ai_explanation` в `details` JSONB
- Trigger: @Async при создании alert_event через `AlertEventCreatedEvent`
- Данные: P&L delta из marts, component breakdown

### F5: Proactive Insights

- Новая таблица `ai_insight` (PG)
- Java-детекторы (SQL) + LLM-вербализация
- Детектируемые типы: price_increase_candidate, frozen_capital, return_pattern
- Scheduled daily + ShedLock
- Результат -> `user_notification` (type = AI_INSIGHT)
- Дедупликация: 1 инсайт per SKU per type per 7 дней

## Структура документа

1. Infrastructure: `datapulse-ai` module, LlmClient, PromptRegistry, AiProperties, aiExecutor, circuit breaker
2. Data model: миграции (3 таблицы + ALTER), NotificationType расширение
3. F10: Auto-classification (scheduler, detector, prompt, review API, review UI)
4. F1: Command Palette AI (backend endpoint, prompt, frontend integration)
5. F8: Morning Briefing (data aggregator, prompt, API, frontend overlay)
6. F3: Weekly Digest (scheduler, aggregator, prompt, notification delivery)
7. F4: Pricing Advisor (advisor service, prompt, cache, frontend panel section)
8. F2: Anomaly Explanation (async generator, prompt, alert enrichment)
9. F5: Proactive Insights (detectors, prompts, scheduler, notification)

## Набор промптов (13 промптов)

1. **Infra: Maven module + LlmClient + config** -- datapulse-ai модуль, LlmClient, AiProperties, aiExecutor, circuit breaker
2. **Infra: DB migrations** -- 3 таблицы + ALTER alert_event + NotificationType
3. **F10 Backend: Unknown ops detector + classifier** -- scheduled job, query OTHER entries, LLM classify, save results
4. **F10 Frontend: Classification review page** -- settings/ai-classifications, review table, accept/reject
5. **F1 Backend: Command intent endpoint** -- POST /ai/command, prompt, response parser
6. **F1 Frontend: AI in Command Palette** -- AI mode detection, filter/navigate/quick_answer handling
7. **F8 Backend: Briefing data aggregator + API** -- collect data from marts/PG, LLM verbalize, cache, REST endpoint
8. **F8 Frontend: Morning briefing overlay** -- shell overlay component, dismiss, lazy load
9. **F3 Backend: Weekly digest scheduler** -- cron job, aggregate week data, LLM, save digest, fan-out notification
10. **F4 Backend: Pricing advisor service** -- on-demand generation, prompt with signal snapshot, cache 24h
11. **F4 Frontend: Advisor panel section** -- new section in decision-detail-panel, lazy load, AI badge
12. **F2 Backend: Anomaly explanation generator** -- async on AlertEventCreatedEvent, mart delta query, LLM, save to details
13. **F5 Backend: Insight detectors + scheduler** -- 3 SQL detectors, LLM verbalize, ai_insight table, notification fan-out
