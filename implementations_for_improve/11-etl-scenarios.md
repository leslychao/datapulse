# ETL Scenarios & Мониторинг — Implementation Plan

## Обзор

ETL Scenarios — мульти-событийные сценарии синхронизации. Один сценарий объединяет несколько `MarketplaceEvent` (CATEGORY_DICT → PRODUCT_DICT → SALES_FACT → FACT_FINANCE) с учётом зависимостей, обеспечивая корректный порядок выполнения.

## Архитектура

```
REST (POST /api/etl/scenario/run)
 → EtlScenarioOrchestratorService
   → EtlScenarioValidationService (валидация)
   → EtlScenarioDateResolutionService (даты для каждого event)
   → Persist: etl_scenario_execution + etl_scenario_item_state[]
   → EtlScenarioDispatchService (dispatch READY items)
     → EtlRunBootstrapService (child execution per event)
   → EtlScenarioProgressService (progression + finalization)
```

## Компоненты

### 1. Orchestrator — `EtlScenarioOrchestratorService`

**`startScenario(EtlScenarioRunRequest)`:**

1. **Validation:** `EtlScenarioValidationService.validate(request)`

2. **Date resolution:** `EtlScenarioDateResolutionService.resolveAll(request.events())` → `Map<MarketplaceEvent, EtlDateRange>`

3. **Dependency analysis:**
   - Для каждого event → `event.dependencies()` → check if deps are in scenario
   - Если зависимость в сценарии → `BLOCKED`, иначе → `READY`

4. **Persist:**
   - `etl_scenario_execution` (scenarioId, accountId, status=NEW, requestedBy)
   - `etl_scenario_item_state[]` (per event: status, dateFrom, dateTo)
   - CAS `NEW → IN_PROGRESS`

5. **Dispatch READY items:**
   - `dispatchService.dispatchItems(scenarioId, accountId, readyItems)`
   - Для каждого → `EtlRunBootstrapService.bootstrap()` → child execution
   - `itemRepository.updateToRunning(scenarioId, event, requestId)` — bind child execution

6. **Handle immediately completed:**
   - Items with empty source set → mark COMPLETED
   - `progressService.advanceScenario(scenarioId)` — unlock dependents

### 2. Dispatch — `EtlScenarioDispatchService`

**`dispatchItems(scenarioId, accountId, items)`:**
- Для каждого item:
  1. `EtlRunBootstrapService.bootstrap(accountId, event, dateFrom, dateTo)` → `BootstrapResult`
  2. `itemRepository.updateToRunning(scenarioId, event, requestId)` — CAS claim
  3. Return list of immediately completed events

### 3. Progress — `EtlScenarioProgressService`

**`onExecutionTerminal(requestId, status)`:**
- Вызывается из `EtlCompletionService` при завершении child execution
- Найти scenario item по `requestId`
- Mark item terminal (COMPLETED/FAILED)
- `advanceScenario(scenarioId)` — unlock dependent items

**`advanceScenario(scenarioId)`:**
1. Load all items
2. Для каждого BLOCKED item:
   - Check if all dependencies COMPLETED
   - If yes → dispatch item
3. Check scenario completion:
   - All items terminal → finalize scenario (COMPLETED/FAILED)
   - Publish `EtlScenarioTerminalEvent` → triggers sync notifications

### 4. Dependencies — `MarketplaceEvent.dependencies()`

```
WAREHOUSE_DICT → (нет зависимостей)
CATEGORY_DICT → (нет зависимостей)
PRODUCT_DICT → [CATEGORY_DICT, WAREHOUSE_DICT]
SALES_FACT → [PRODUCT_DICT]
INVENTORY_FACT → [PRODUCT_DICT, WAREHOUSE_DICT]
ADVERTISING_FACT → [PRODUCT_DICT]
FACT_FINANCE → [SALES_FACT]
PROMO_SYNC → [PRODUCT_DICT]
```

### 5. Статусы

**EtlScenarioStatus:** `NEW` → `IN_PROGRESS` → `COMPLETED` / `FAILED`

**EtlScenarioItemStatus:** `BLOCKED` → `READY` → `RUNNING` → `COMPLETED` / `FAILED`

### 6. Terminal Event

При завершении сценария публикуется `EtlScenarioTerminalEvent`:
```java
public record EtlScenarioTerminalEvent(
    String scenarioId,
    long accountId,
    Long requestedByProfileId,
    Status status  // COMPLETED / FAILED
)
```
→ `SyncNotificationListener` → уведомления всем участникам аккаунта

## Мониторинг

### Freshness — `EtlFreshnessQueryService`

**REST — `EtlFreshnessController` (`/api/etl/freshness`):**
| Endpoint | Описание |
|----------|----------|
| `GET /` | Список: дата последней загрузки по каждому event |
| `GET /summary` | Общая оценка свежести данных |

### History — `EtlScenarioHistoryController`

**REST — `/api/etl/scenarios/history`:**
| Endpoint | Описание |
|----------|----------|
| `GET /` | История запусков сценариев |

### Active — `EtlScenarioController`

**REST — `/api/etl/scenario`:**
| Endpoint | Описание |
|----------|----------|
| `POST /run` | Запуск сценария |
| `GET /active` | Активные сценарии |

## Ключевые файлы

| Файл | Модуль | Роль |
|------|--------|------|
| `EtlScenarioOrchestratorService.java` | etl | Scenario start orchestration |
| `EtlScenarioDispatchService.java` | etl | Child execution dispatch |
| `EtlScenarioProgressService.java` | etl | Dependency unlock + finalization |
| `EtlScenarioValidationService.java` | etl | Request validation |
| `EtlScenarioDateResolutionService.java` | etl | Date resolution per event |
| `MarketplaceEvent.java` | etl | Event enum with dependencies |
| `EtlScenarioTerminalEvent.java` | domain | Terminal event record |
| `EtlFreshnessQueryService.java` | core | Freshness queries |
| `EtlScenarioController.java` | application | Scenario REST API |
| `EtlFreshnessController.java` | application | Freshness REST API |
