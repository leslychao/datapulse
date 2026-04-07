# Feature: AI-Powered Seller Intelligence — Technical Breakdown

**Статус:** TBD_READY
**Дата создания:** 2026-04-06
**Автор:** Виталий Ким
**Базовый документ:** `docs/features/2026-03-31-ai-llm-insights.md` (DRAFT)
**Инфраструктура:** `infra/llm/docker-compose.yml` (vLLM + Qwen3-8B)

---

## Содержание

1. [Обзор и приоритизация](#1-обзор-и-приоритизация)
2. [Инфраструктура: модуль datapulse-ai](#2-инфраструктура-модуль-datapulse-ai)
3. [Data model: миграции](#3-data-model-миграции)
4. [F10: Auto-classification неизвестных финансовых операций](#4-f10-auto-classification)
5. [F1: Smart Command Palette](#5-f1-smart-command-palette)
6. [F8: Morning Briefing](#6-f8-morning-briefing)
7. [F3: Weekly Digest](#7-f3-weekly-digest)
8. [F4: Pricing Advisor](#8-f4-pricing-advisor)
9. [F2: Anomaly Explanation](#9-f2-anomaly-explanation)
10. [F5: Proactive Insights](#10-f5-proactive-insights)
11. [Зависимости и порядок реализации](#11-зависимости-и-порядок-реализации)

---

## 1. Обзор и приоритизация

### Принцип

**LLM не принимает решения — он помогает понимать данные.** Все данные берутся из проверенных sources of truth (ClickHouse marts, PostgreSQL canonical). LLM формулирует, а не выдумывает. При недоступности LLM — graceful degradation, core-функциональность не страдает.

### Тиры

| Тир | Фича | Боль селлера | Тип AI-задачи | Effort |
|-----|-------|--------------|---------------|--------|
| **1** | F10: Auto-classification | P&L не сходится — неизвестные типы операций | Classification | Низкий |
| **1** | F1: Command Palette | Сложная навигация по гриду | Text-to-JSON | Средний |
| **1** | F8: Morning Briefing | «С чего начать день?» | Verbalization | Средний |
| **1** | F3: Weekly Digest | «Как прошла неделя?» | Verbalization | Низкий |
| **2** | F4: Pricing Advisor | «Стоит ли менять цену?» | Reasoning | Средний |
| **2** | F2: Anomaly Explanation | «Почему маржа упала?» | Reasoning + verbalization | Средний |
| **2** | F5: Proactive Insights | «Что я упускаю?» | Detection + verbalization | Высокий |

### Предусловия по тирам

- **Тир 1:** LLM-сервер поднят (`infra/llm/`), модуль `datapulse-ai` создан, миграции применены.
- **Тир 2 (F4):** Phase C completion — сигналы `avgCommissionPct`, `avgLogisticsPerUnit`, `returnRatePct`, `productStatus`, `marketplaceMinPrice` подключены в `PricingSignalCollector`.
- **Тир 2 (F2):** работающие алерты типов `SPIKE_DETECTION`, `RESIDUAL_ANOMALY` + материализация marts.
- **Тир 2 (F5):** материализация marts (`mart_product_pnl`, `mart_inventory_analysis`, `mart_returns_analysis`).

---

## 2. Инфраструктура: модуль datapulse-ai

### 2.1. Maven-модуль

Новый модуль `backend/datapulse-ai` по образцу `datapulse-pricing`.

**`backend/datapulse-ai/pom.xml`:**

```xml
<project>
  <parent>
    <groupId>io.datapulse</groupId>
    <artifactId>datapulse-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </parent>

  <artifactId>datapulse-ai</artifactId>

  <dependencies>
    <dependency>
      <groupId>io.datapulse</groupId>
      <artifactId>datapulse-common</artifactId>
    </dependency>
    <dependency>
      <groupId>io.datapulse</groupId>
      <artifactId>datapulse-platform</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>com.github.ben-manes.caffeine</groupId>
      <artifactId>caffeine</artifactId>
    </dependency>
    <dependency>
      <groupId>io.github.resilience4j</groupId>
      <artifactId>resilience4j-circuitbreaker</artifactId>
    </dependency>
    <dependency>
      <groupId>net.javacrumbs.shedlock</groupId>
      <artifactId>shedlock-spring</artifactId>
    </dependency>
  </dependencies>
</project>
```

**Изменения в `backend/pom.xml`:**
- Добавить `<module>datapulse-ai</module>` в `<modules>`
- Добавить `datapulse-ai` в `<dependencyManagement>`

**Изменения в `backend/datapulse-api/pom.xml`:**
- Добавить зависимость `datapulse-ai`

### 2.2. Структура пакетов

```
io.datapulse.ai/
├── config/
│   ├── AiProperties.java              -- @ConfigurationProperties(prefix = "datapulse.ai")
│   └── AiCircuitBreakerConfig.java    -- CircuitBreaker bean для LLM
│
├── domain/
│   ├── LlmClient.java                 -- @Service: WebClient → vLLM
│   ├── PromptTemplate.java            -- enum с шаблонами промптов
│   └── LlmResponse.java              -- record для парсинга ответа LLM
│
├── persistence/
│   ├── AiDigestEntity.java
│   ├── AiDigestRepository.java
│   ├── AiInsightEntity.java
│   ├── AiInsightRepository.java
│   ├── AiSuggestedClassificationEntity.java
│   └── AiSuggestedClassificationRepository.java
│
├── api/
│   ├── AiCommandController.java       -- POST /ai/command (F1)
│   ├── AiBriefingController.java      -- GET /ai/briefing (F8)
│   ├── AiClassificationController.java -- GET/POST classifications (F10)
│   ├── AiAdvisorController.java       -- GET /ai/advisor/{offerId} (F4)
│   └── dto/                           -- Request/Response records
│
└── scheduling/
    ├── ClassificationScheduler.java   -- Daily batch F10
    ├── DigestScheduler.java           -- Weekly F3
    └── InsightScheduler.java          -- Daily F5
```

### 2.3. AiProperties

```java
@Validated
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "datapulse.ai")
public class AiProperties {

  private final String baseUrl;          // ${LLM_BASE_URL:}
  private final String modelName;        // ${LLM_MODEL_NAME:datapulse}
  private final int timeoutFastMs;       // ${LLM_TIMEOUT_FAST:2000}
  private final int timeoutSlowMs;       // ${LLM_TIMEOUT_SLOW:15000}
  private final boolean enabled;         // derived: !baseUrl.isBlank()
}
```

**`application.yml` (добавить секцию):**

```yaml
datapulse:
  ai:
    base-url: ${LLM_BASE_URL:}
    model-name: ${LLM_MODEL_NAME:datapulse}
    timeout-fast-ms: ${LLM_TIMEOUT_FAST:2000}
    timeout-slow-ms: ${LLM_TIMEOUT_SLOW:15000}
```

### 2.4. LlmClient

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmClient {

  private final WebClient llmWebClient;
  private final AiProperties props;
  private final CircuitBreaker circuitBreaker;

  public record ChatMessage(String role, String content) {}

  public record ChatRequest(
      String model,
      List<ChatMessage> messages,
      double temperature,
      @JsonProperty("max_tokens") int maxTokens
  ) {}

  public record ChatChoice(ChatMessage message) {}
  public record ChatResponse(List<ChatChoice> choices) {}

  /**
   * Synchronous call to vLLM. Throws AiUnavailableException on timeout/circuit open.
   *
   * @param systemPrompt system message
   * @param userPrompt   user message
   * @param timeoutMs    per-request timeout
   * @param temperature  0.0–1.0
   * @param maxTokens    response token limit
   * @return generated text content
   */
  public String chat(String systemPrompt, String userPrompt,
      int timeoutMs, double temperature, int maxTokens) {

    if (!props.isEnabled()) {
      throw new AiUnavailableException("LLM is not configured");
    }

    var request = new ChatRequest(
        props.getModelName(),
        List.of(
            new ChatMessage("system", systemPrompt),
            new ChatMessage("user", userPrompt)
        ),
        temperature,
        maxTokens
    );

    return circuitBreaker.executeSupplier(() ->
        llmWebClient
            .post()
            .uri("/chat/completions")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(ChatResponse.class)
            .timeout(Duration.ofMillis(timeoutMs))
            .map(r -> r.choices().getFirst().message().content())
            .block()
    );
  }
}
```

**WebClient bean** (в `AiConfig.java` внутри модуля):

```java
@Configuration
@EnableConfigurationProperties(AiProperties.class)
@RequiredArgsConstructor
public class AiConfig {

  @Bean
  @ConditionalOnProperty(name = "datapulse.ai.base-url", matchIfMissing = false)
  public WebClient llmWebClient(WebClient.Builder builder, AiProperties props) {
    return builder
        .baseUrl(props.getBaseUrl())
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();
  }

  @Bean("aiCircuitBreaker")
  public CircuitBreaker aiCircuitBreaker() {
    return CircuitBreaker.of("ai-llm", CircuitBreakerConfig.custom()
        .failureRateThreshold(50)
        .waitDurationInOpenState(Duration.ofSeconds(30))
        .slidingWindowSize(10)
        .minimumNumberOfCalls(3)
        .build());
  }
}
```

### 2.5. Async executor

Добавить в `AsyncConfig.java` (`datapulse-platform`):

```java
@Bean("aiExecutor")
public TaskExecutor aiExecutor() {
  return buildExecutor("ai-", 2, 4, 30);
}
```

### 2.6. Graceful degradation

Все AI-фичи проверяют `props.isEnabled()` перед вызовом LLM. При `AiUnavailableException`:

| Фича | Degradation |
|------|-------------|
| F10 | Неизвестные операции остаются `OTHER` (текущее поведение) |
| F1 | Command Palette работает как entity search (fallback) |
| F8 | Briefing показывает числовые блоки без narrative |
| F3 | Digest не генерируется |
| F4 | Секция advisor не показывается |
| F2 | Alert без `ai_explanation` — показываются raw details |
| F5 | Insights не генерируются |

---

## 3. Data model: миграции

Файл: `backend/datapulse-api/src/main/resources/db/changelog/changes/0027-ai-tables.sql`

### 3.1. Таблица `ai_suggested_classification`

```sql
--liquibase formatted sql

--changeset datapulse:0027-ai-tables

CREATE TABLE ai_suggested_classification (
  id                  BIGSERIAL PRIMARY KEY,
  workspace_id        BIGINT NOT NULL REFERENCES workspace(id),
  source_platform     VARCHAR(10) NOT NULL,
  operation_type_name VARCHAR(255) NOT NULL,
  sample_amount       DECIMAL,
  has_posting          BOOLEAN NOT NULL,
  has_sku              BOOLEAN NOT NULL,
  occurrence_count     INT NOT NULL DEFAULT 1,
  suggested_entry_type VARCHAR(60) NOT NULL,
  suggested_measure    VARCHAR(60) NOT NULL,
  confidence           DECIMAL(3,2) NOT NULL,
  reasoning            TEXT,
  status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  reviewed_by          BIGINT REFERENCES app_user(id),
  reviewed_at          TIMESTAMPTZ,
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_ai_classification_op UNIQUE (workspace_id, source_platform, operation_type_name)
);

CREATE INDEX idx_ai_classification_status ON ai_suggested_classification(workspace_id, status);
```

### 3.2. Таблица `ai_digest`

```sql
CREATE TABLE ai_digest (
  id               BIGSERIAL PRIMARY KEY,
  workspace_id     BIGINT NOT NULL REFERENCES workspace(id),
  digest_type      VARCHAR(20) NOT NULL,
  period_start     DATE NOT NULL,
  period_end       DATE NOT NULL,
  structured_data  JSONB NOT NULL,
  generated_text   TEXT,
  generated_at     TIMESTAMPTZ,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_ai_digest UNIQUE (workspace_id, digest_type, period_start)
);
```

### 3.3. Таблица `ai_insight`

```sql
CREATE TABLE ai_insight (
  id               BIGSERIAL PRIMARY KEY,
  workspace_id     BIGINT NOT NULL REFERENCES workspace(id),
  insight_type     VARCHAR(60) NOT NULL,
  entity_type      VARCHAR(30),
  entity_id        BIGINT,
  severity         VARCHAR(20) NOT NULL DEFAULT 'INFO',
  structured_data  JSONB NOT NULL,
  generated_text   TEXT,
  dedupe_key       VARCHAR(255) NOT NULL,
  status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  dismissed_by     BIGINT REFERENCES app_user(id),
  dismissed_at     TIMESTAMPTZ,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_ai_insight_dedupe UNIQUE (workspace_id, dedupe_key)
);

CREATE INDEX idx_ai_insight_active ON ai_insight(workspace_id, status, created_at DESC);

--rollback DROP TABLE ai_insight;
--rollback DROP TABLE ai_digest;
--rollback DROP TABLE ai_suggested_classification;
```

### 3.4. Расширение NotificationType

Добавить в `io.datapulse.audit.domain.NotificationType`:

```java
public enum NotificationType {
  ALERT,
  APPROVAL_REQUEST,
  SYNC_COMPLETED,
  ACTION_FAILED,
  AI_DIGEST,
  AI_INSIGHT
}
```

### 3.5. Миграция в master changelog

Добавить в `db.changelog-master.yaml`:

```yaml
- include:
    file: changes/0027-ai-tables.sql
    relativeToChangelogFile: true
```

---

## 4. F10: Auto-classification

### 4.1. Поток данных

```
canonical_finance_entry (entry_type = 'OTHER')
        │
        ▼
┌─────────────────────────────┐
│ ClassificationScheduler     │  @Scheduled(cron), daily, ShedLock
│ 1. Query unique OTHER ops   │
│ 2. Deduplicate vs existing  │
│ 3. Batch → LLM              │
│ 4. Save ai_suggested_class. │
└─────────────────────────────┘
        │
        ▼
┌─────────────────────────────┐
│ AiClassificationController  │  GET list, POST accept/reject
│ Review UI (settings page)   │
└─────────────────────────────┘
```

### 4.2. Backend

**`UnknownOpsDetector.java`** (domain/):

```java
@Service
@RequiredArgsConstructor
public class UnknownOpsDetector {

  private final JdbcTemplate jdbc;

  private static final String UNKNOWN_OPS_SQL = """
      SELECT
        cfe.source_platform,
        CASE
          WHEN cfe.source_platform = 'OZON' THEN cfe.external_entry_id
          ELSE cfe.entry_type
        END AS raw_operation_type,
        MIN(cfe.net_payout) AS sample_amount,
        bool_or(cfe.posting_id IS NOT NULL) AS has_posting,
        bool_or(cfe.seller_sku_id IS NOT NULL) AS has_sku,
        COUNT(*) AS occurrence_count
      FROM canonical_finance_entry cfe
      WHERE cfe.connection_id IN (
        SELECT mc.id FROM marketplace_connection mc
        WHERE mc.workspace_id = ?
      )
        AND cfe.entry_type = 'OTHER'
        AND cfe.created_at >= now() - INTERVAL '30 days'
      GROUP BY cfe.source_platform, raw_operation_type
      """;

  public List<UnknownOperation> findUnknownOps(long workspaceId) {
    return jdbc.query(UNKNOWN_OPS_SQL, (rs, i) -> new UnknownOperation(
        rs.getString("source_platform"),
        rs.getString("raw_operation_type"),
        rs.getBigDecimal("sample_amount"),
        rs.getBoolean("has_posting"),
        rs.getBoolean("has_sku"),
        rs.getInt("occurrence_count")
    ), workspaceId);
  }

  public record UnknownOperation(
      String sourcePlatform,
      String operationTypeName,
      BigDecimal sampleAmount,
      boolean hasPosting,
      boolean hasSku,
      int occurrenceCount
  ) {}
}
```

**Примечание:** для Ozon `raw_operation_type` — это значение `operation_type` из API (оно хранится в лог-контексте, но не в `canonical_finance_entry` напрямую). Реальная реализация должна либо:
- (а) Сохранять `operation_type` в новую колонку `raw_operation_type` при нормализации (предпочтительно, отдельная миграция ALTER TABLE + обновление normalizer).
- (б) Или использовать `log.warn` данные из ETL-логов (ненадёжно).

**Рекомендация:** добавить колонку `raw_operation_type VARCHAR(255)` в `canonical_finance_entry`, заполнять в `OzonFinanceNormalizer` и `WbNormalizer`. Миграция:

```sql
--changeset datapulse:0027-ai-raw-operation-type
ALTER TABLE canonical_finance_entry
  ADD COLUMN IF NOT EXISTS raw_operation_type VARCHAR(255);
```

**`ClassificationService.java`** (domain/):

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassificationService {

  private final UnknownOpsDetector detector;
  private final LlmClient llmClient;
  private final AiSuggestedClassificationRepository repository;
  private final AiProperties props;

  private static final String SYSTEM_PROMPT = """
      You are a financial operations classifier for marketplace sellers (Ozon, Wildberries).

      Classify the unknown operation into one of these canonical categories:
      - REVENUE: seller revenue from sales
      - REFUND: refund/return reversal reducing revenue
      - MARKETPLACE_COMMISSION: marketplace sales commission
      - ACQUIRING: payment processing / acquiring fees
      - LOGISTICS: delivery, shipping, return logistics
      - STORAGE: warehouse storage fees
      - PENALTIES: fines, penalties, defect fees
      - ACCEPTANCE: goods acceptance/processing fees
      - MARKETING: advertising, promotions, marketing services
      - COMPENSATION: compensations, claims, write-offs
      - OTHER: cannot classify with confidence

      Respond ONLY with valid JSON:
      {"category": "LOGISTICS", "confidence": 0.85, "reasoning": "...one sentence..."}
      """;

  public void classifyBatch(long workspaceId) {
    var unknownOps = detector.findUnknownOps(workspaceId);
    var existing = repository.findExistingTypeNames(workspaceId);

    var newOps = unknownOps.stream()
        .filter(op -> !existing.contains(op.operationTypeName()))
        .toList();

    if (newOps.isEmpty()) {
      log.debug("No new unknown operations to classify: workspaceId={}", workspaceId);
      return;
    }

    for (var op : newOps) {
      try {
        var userPrompt = """
            Operation: "%s"
            Platform: %s
            Sample amount: %s
            Has posting reference: %s
            Has SKU reference: %s
            Occurrences: %d
            """.formatted(
            op.operationTypeName(), op.sourcePlatform(),
            op.sampleAmount(), op.hasPosting(), op.hasSku(),
            op.occurrenceCount());

        var response = llmClient.chat(
            SYSTEM_PROMPT, userPrompt,
            props.getTimeoutSlowMs(), 0.1, 200);

        var parsed = parseClassification(response);
        saveClassification(workspaceId, op, parsed);

      } catch (AiUnavailableException e) {
        log.warn("LLM unavailable, stopping classification batch: {}", e.getMessage());
        return;
      } catch (Exception e) {
        log.error("Classification failed for op={}: {}", op.operationTypeName(), e.getMessage());
      }
    }
  }
}
```

**`ClassificationScheduler.java`** (scheduling/):

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class ClassificationScheduler {

  private final ClassificationService classificationService;
  private final WorkspaceRepository workspaceRepository;

  @Scheduled(cron = "${datapulse.ai.classification-cron:0 0 3 * * *}")
  @SchedulerLock(name = "aiClassification", lockAtMostFor = "PT30M")
  public void classifyUnknownOperations() {
    try {
      var workspaceIds = workspaceRepository.findAllActiveWorkspaceIds();
      for (var wsId : workspaceIds) {
        classificationService.classifyBatch(wsId);
      }
    } catch (Exception e) {
      log.error("Classification scheduler failed", e);
    }
  }
}
```

### 4.3. REST API

**`AiClassificationController.java`:**

```
GET  /api/workspaces/{workspaceId}/ai/classifications?status=PENDING
     → Page<ClassificationResponse>

POST /api/workspaces/{workspaceId}/ai/classifications/{id}/accept
     → void (204)

POST /api/workspaces/{workspaceId}/ai/classifications/{id}/reject
     → void (204)
```

**`ClassificationResponse`:**

```java
public record ClassificationResponse(
    long id,
    String sourcePlatform,
    String operationTypeName,
    BigDecimal sampleAmount,
    boolean hasPosting,
    boolean hasSku,
    int occurrenceCount,
    String suggestedEntryType,
    String suggestedMeasure,
    BigDecimal confidence,
    String reasoning,
    String status,
    OffsetDateTime createdAt
) {}
```

### 4.4. Frontend

Новая страница в Settings: `settings/ai-classifications`.

- Route: добавить в `settings.routes.ts` лениво загружаемый компонент.
- Компонент: `AiClassificationsPageComponent` — AG Grid с колонками: operation name, platform, suggested category, confidence, reasoning, status, actions (accept/reject).
- При accept — toast «Классификация принята. Для применения обновите маппинг.»
- При reject — toast «Классификация отклонена.»

**Примечание:** accept сохраняет статус `ACCEPTED` в БД. Автоматическое обновление `FinanceEntryType` enum не делается — это ручное действие разработчика. В будущем можно добавить runtime lookup из `ai_suggested_classification` как fallback к enum.

### 4.5. Prompt template

| Параметр | Значение |
|----------|----------|
| Mode | Non-thinking |
| Temperature | 0.1 |
| Max tokens | 200 |
| Timeout | `timeoutSlowMs` (15s) |
| Response format | JSON: `{category, confidence, reasoning}` |

---

## 5. F1: Smart Command Palette

### 5.1. Поток данных

```
User types in Ctrl+K → "убыточные товары на WB"
        │
        ▼
CommandPaletteComponent
  1. Entity search (existing)
  2. If no results && query >= 3 chars → POST /ai/command
        │
        ▼
AiCommandController
  1. Build prompt with filter schema
  2. LLM → structured JSON
  3. Parse and validate response
        │
        ▼
Response: {action: "filter", filters: {marketplaceType: ["WB"], marginMax: 0}}
        │
        ▼
CommandPaletteComponent
  1. gridStore.updateFilters(filters)
  2. router.navigate to grid
  3. Close palette
```

### 5.2. Backend

**`AiCommandController.java`:**

```
POST /api/workspaces/{workspaceId}/ai/command
Body: { "query": "убыточные товары на WB" }
Response: CommandIntentResponse
```

**`CommandIntentResponse.java`:**

```java
public record CommandIntentResponse(
    String action,
    OfferFilter filters,
    NavigateTarget navigate,
    String text,
    String link
) {
  public record NavigateTarget(String module, String view, Map<String, String> params) {}
}
```

**`CommandIntentService.java`** (domain/):

```java
@Service
@RequiredArgsConstructor
public class CommandIntentService {

  private final LlmClient llmClient;
  private final AiProperties props;
  private final ObjectMapper objectMapper;

  private static final String SYSTEM_PROMPT = """
      You are a navigation assistant for a marketplace analytics platform (Wildberries & Ozon).

      Available grid filters (all optional):
      - marketplaceType: ["WB"] or ["OZON"] or ["WB","OZON"]
      - status: ["ACTIVE","ARCHIVED","BLOCKED","INACTIVE"]
      - marginMin / marginMax: number (percent)
      - stockRisk: ["CRITICAL","WARNING","NORMAL"]
      - hasManualLock: true/false
      - hasActivePromo: true/false
      - lastDecision: ["CHANGE","SKIP","HOLD"]
      - lastActionStatus: ["PENDING_APPROVAL","APPROVED","SUCCEEDED","FAILED",...]
      - skuCode: text (exact SKU search)
      - productName: text (product name search)

      Available navigation targets:
      - {module: "analytics", view: "pnl-summary"}
      - {module: "analytics", view: "inventory"}
      - {module: "analytics", view: "returns"}
      - {module: "pricing", view: "policies"}
      - {module: "pricing", view: "runs"}
      - {module: "pricing", view: "decisions"}
      - {module: "promo", view: "campaigns"}
      - {module: "settings", view: "connections"}

      Respond ONLY with valid JSON:
      - {"action":"filter","filters":{...}} — apply grid filters
      - {"action":"navigate","navigate":{"module":"...","view":"..."}} — go to page
      - {"action":"quick_answer","text":"...","link":"..."} — answer with link
      - {"action":"unknown"} — if you cannot understand the query

      Always respond in the user's language. Examples:
      - "убыточные товары" → {"action":"filter","filters":{"marginMax":0}}
      - "товары без остатков" → {"action":"filter","filters":{"stockRisk":["CRITICAL"]}}
      - "покажи P&L" → {"action":"navigate","navigate":{"module":"analytics","view":"pnl-summary"}}
      """;

  public CommandIntentResponse parseIntent(String userQuery) {
    var response = llmClient.chat(
        SYSTEM_PROMPT, userQuery,
        props.getTimeoutFastMs(), 0.0, 300);

    return objectMapper.readValue(
        extractJsonBlock(response),
        CommandIntentResponse.class);
  }
}
```

### 5.3. Frontend

**Изменения в `CommandPaletteComponent`:**

1. Добавить новый тип результата `ai` в groups.
2. После debounce и entity search — если результатов мало (< 2) и `query.length >= 3`:
   - Показать группу «AI-навигатор» с spinner.
   - Вызвать `AiApiService.sendCommand(workspaceId, query)`.
   - При `action === 'filter'` → `gridStore.updateFilters(response.filters)` + navigate to grid + close.
   - При `action === 'navigate'` → `router.navigateInWorkspace(target)` + close.
   - При `action === 'quick_answer'` → показать inline-текст с ссылкой.
3. Fallback: при ошибке / timeout — не показывать AI-группу, entity search остаётся.

**`AiApiService`** (core/api/):

```typescript
@Injectable({ providedIn: 'root' })
export class AiApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  sendCommand(workspaceId: number, query: string): Observable<CommandIntentResponse> {
    return this.http.post<CommandIntentResponse>(
      `${this.base}/workspaces/${workspaceId}/ai/command`,
      { query }
    );
  }

  getBriefing(workspaceId: number): Observable<BriefingResponse> {
    return this.http.get<BriefingResponse>(
      `${this.base}/workspaces/${workspaceId}/ai/briefing`
    );
  }
}
```

### 5.4. Prompt template

| Параметр | Значение |
|----------|----------|
| Mode | Non-thinking (`/no_think` если поддерживается, иначе temperature=0) |
| Temperature | 0.0 |
| Max tokens | 300 |
| Timeout | `timeoutFastMs` (2s) |
| Response format | JSON: `{action, filters?, navigate?, text?}` |

---

## 6. F8: Morning Briefing

### 6.1. Поток данных

```
User opens workspace (first time today)
        │
        ▼
Frontend: GET /ai/briefing
        │
        ▼
AiBriefingController
  1. Check cache (ai_digest WHERE digest_type='MORNING' AND period_start=today)
  2. If cached → return
  3. Aggregate data from PG + CH
  4. LLM verbalize
  5. Save to ai_digest
  6. Return
        │
        ▼
Frontend: show briefing overlay
```

### 6.2. Data aggregation

**`BriefingDataAggregator.java`** (domain/):

Собирает structured context из существующих репозиториев:

```java
public record BriefingData(
    // P&L (из PnlReadRepository — AGGREGATED_SUMMARY_SQL за текущий период)
    BigDecimal revenueCurrentMonth,
    BigDecimal revenuePrevMonth,
    BigDecimal marginPctCurrent,
    BigDecimal marginPctPrev,
    BigDecimal fullPnlCurrent,

    // Inventory (из InventoryReadRepository — OVERVIEW_SQL)
    int criticalStockCount,
    int warningStockCount,
    BigDecimal frozenCapital,

    // Pending actions (из PG: price_action WHERE status IN ('PENDING_APPROVAL','FAILED'))
    int pendingApprovalCount,
    int failedActionCount,

    // Recent alerts (из PG: alert_event WHERE status='OPEN' OR opened_at >= today)
    int openAlertCount,
    int criticalAlertCount,

    // Sync status (из PG: sync_state last sync per connection)
    List<SyncInfo> recentSyncs,

    // Returns (из ReturnsReadRepository)
    BigDecimal returnRateCurrent,
    BigDecimal returnRatePrev
) {}
```

**Зависимости модуля:** `datapulse-ai` НЕ зависит напрямую от `datapulse-analytics-pnl`. Вместо этого:
- Агрегирующие запросы дублируются в `datapulse-ai/persistence/BriefingReadRepository.java` (используя `ClickHouseReadJdbc` из `datapulse-analytics-pnl` или собственный JDBC).
- **Альтернатива (рекомендуется):** добавить зависимость `datapulse-analytics-pnl` в `datapulse-ai/pom.xml` и переиспользовать `PnlQueryService`, `InventoryReadRepository` и т.д. напрямую.

### 6.3. REST API

```
GET /api/workspaces/{workspaceId}/ai/briefing
    → BriefingResponse
```

**`BriefingResponse.java`:**

```java
public record BriefingResponse(
    BriefingData data,
    String narrative,
    OffsetDateTime generatedAt,
    boolean aiGenerated
) {}
```

Если LLM недоступен: `narrative = null`, `aiGenerated = false`. Frontend показывает числовые блоки без текстовой сводки.

### 6.4. Prompt template

```
System: Ты — бизнес-аналитик для селлера маркетплейсов.
Сформируй краткий утренний брифинг: что изменилось, что требует внимания, 4-6 буллетов.
Используй только числа из контекста, не выдумывай. Русский язык.

Контекст за {today}:
Выручка за месяц: {revenueCurrentMonth}₽ (пред. месяц: {revenuePrevMonth}₽)
Маржинальность: {marginPctCurrent}% (пред.: {marginPctPrev}%)
Stock-out (critical): {criticalStockCount} товаров
Замороженный капитал: {frozenCapital}₽
Ожидают одобрения: {pendingApprovalCount} ценовых действий
Проваленные действия: {failedActionCount}
Открытые алерты: {openAlertCount} (из них critical: {criticalAlertCount})
Последние синхронизации: {recentSyncsFormatted}
Return rate: {returnRateCurrent}% (пред.: {returnRatePrev}%)
```

| Параметр | Значение |
|----------|----------|
| Mode | Non-thinking |
| Temperature | 0.3 |
| Max tokens | 500 |
| Timeout | `timeoutSlowMs` (15s) |
| Cache | `ai_digest` WHERE `digest_type='MORNING'` AND `period_start=today` |

### 6.5. Frontend

**`MorningBriefingComponent`** — standalone component в `shared/shell/morning-briefing/`.

- Рендерится в `ShellComponent` поверх main content (z-50, полупрозрачный overlay).
- Условие показа: `briefingStore.shouldShow()` — true если: (1) workspace загружен, (2) localStorage `lastBriefingDismissed_{wsId}` < today.
- Dismiss: кнопка «Закрыть» → `localStorage.setItem(...)` + скрытие.
- Контент:
  - Если `narrative` != null — текст из LLM.
  - Всегда — числовые карточки (KPI блоки): pending actions, failed actions, critical stock, open alerts.
  - Ссылки-переходы на соответствующие страницы (grid с фильтром, alerts, pricing actions).
- Loading state: skeleton blocks при ожидании API.

---

## 7. F3: Weekly Digest

### 7.1. Поток данных

```
Monday 08:00 (cron)
        │
        ▼
DigestScheduler
  1. For each workspace:
     a. Aggregate week data (Mon-Sun prev week)
     b. LLM verbalize
     c. Save to ai_digest (digest_type='WEEKLY')
     d. Fan-out notification (type=AI_DIGEST)
```

### 7.2. Data aggregation

Аналогично F8, но за период `prev_monday..prev_sunday`:

```java
public record WeeklyDigestData(
    BigDecimal revenueWeek,
    BigDecimal revenuePrevWeek,
    BigDecimal revenueDeltaPct,
    Map<String, BigDecimal> revenueByMarketplace,
    BigDecimal marginPctWeek,
    BigDecimal marginPctPrevWeek,
    int stockOutCount,
    BigDecimal estimatedLostRevenue,
    BigDecimal returnRate,
    BigDecimal returnRatePrev,
    int promoAccepted,
    int promoDeclined,
    int pricingChangesTotal,
    int pricingChangesSucceeded,
    int pricingChangesFailed,
    int newAlerts,
    int criticalAlerts
) {}
```

### 7.3. Prompt template

```
System: Сформируй еженедельную сводку для селлера маркетплейсов.
Акцент на изменениях, проблемах и действиях. 5-8 буллетов. Конкретные числа.
Русский язык.

Данные за {weekStart} — {weekEnd}, workspace: {workspaceName}:

Выручка: {revenueWeek}₽ (пред. неделя: {revenuePrevWeek}₽, {revenueDeltaPct}%)
По маркетплейсам: {revenueByMarketplace}
Маржинальность: {marginPctWeek}% (пред.: {marginPctPrevWeek}%)
Stock-outs: {stockOutCount} товаров
Return rate: {returnRate}% (пред.: {returnRatePrev}%)
Промо: принято {promoAccepted}, отклонено {promoDeclined}
Pricing: {pricingChangesTotal} CHANGE ({pricingChangesSucceeded} успешно, {pricingChangesFailed} failed)
Алерты: {newAlerts} новых ({criticalAlerts} critical)
```

| Параметр | Значение |
|----------|----------|
| Temperature | 0.3 |
| Max tokens | 600 |
| Timeout | `timeoutSlowMs` (15s) |

### 7.4. Notification delivery

После сохранения `ai_digest`:

```java
notificationService.fanOut(
    workspaceId,
    null,
    NotificationType.AI_DIGEST,
    MessageCodes.AI_DIGEST_WEEKLY_TITLE,
    MessageCodes.AI_DIGEST_WEEKLY_BODY,
    "INFO"
);
```

Frontend: колокол показывает уведомление, клик → открыть overlay с полным текстом дайджеста.

### 7.5. Scheduler

```java
@Scheduled(cron = "${datapulse.ai.digest-cron:0 0 8 * * MON}")
@SchedulerLock(name = "aiWeeklyDigest", lockAtMostFor = "PT30M")
public void generateWeeklyDigest() { ... }
```

---

## 8. F4: Pricing Advisor

### 8.1. Предусловия

Phase C completion: `PricingSignalCollector` заполняет все 14 сигналов из ClickHouse и PostgreSQL. Без этого — advisor не включается.

### 8.2. Поток данных

```
User opens offer detail panel → clicks "AI совет"
        │
        ▼
GET /ai/advisor/{offerId}
        │
        ▼
PricingAdvisorService
  1. Check Caffeine cache (offerId, TTL 24h)
  2. Load last price_decision with signal_snapshot
  3. Load velocity/stock/returns from CH
  4. Build structured context
  5. LLM → advice text
  6. Cache and return
```

### 8.3. Backend

**`PricingAdvisorService.java`** (domain/):

```java
@Service
@RequiredArgsConstructor
public class PricingAdvisorService {

  private final PriceDecisionRepository decisionRepo;
  private final PricingClickHouseReadRepository chRepo;
  private final LlmClient llmClient;
  private final AiProperties props;
  private final Cache<Long, AdvisorResponse> cache;

  public Optional<AdvisorResponse> getAdvice(long workspaceId, long offerId) {
    var cached = cache.getIfPresent(offerId);
    if (cached != null) return Optional.of(cached);

    var decision = decisionRepo.findLatestByOfferId(workspaceId, offerId)
        .orElse(null);
    if (decision == null) return Optional.empty();

    var signals = parseSignalSnapshot(decision.getSignalSnapshot());
    if (signals == null || signals.currentPrice() == null) return Optional.empty();

    var prompt = buildPrompt(decision, signals);
    var advice = llmClient.chat(SYSTEM_PROMPT, prompt,
        props.getTimeoutSlowMs(), 0.3, 400);

    var response = new AdvisorResponse(advice, OffsetDateTime.now());
    cache.put(offerId, response);
    return Optional.of(response);
  }
}
```

### 8.4. Prompt template

```
System: Ты — советник по ценообразованию для селлера маркетплейсов.
Дай совет на основе фактов. 2-4 предложения. Только конкретные числа. Русский язык.

Товар: {productName} ({skuCode}), {marketplaceType}
Цена: {currentPrice}₽, себестоимость: {costPrice}₽, маржа: {marginPct}%
Стратегия: {strategyType} (target: {targetValue})
Скорость продаж (14д): {velocity14d} шт/день
Остаток: {availableStock} шт, дней покрытия: {daysOfCover}
Return rate: {returnRatePct}%
DRR (реклама): {adCostRatio}%
Последнее решение: {decisionType}, {skipReason}
```

| Параметр | Значение |
|----------|----------|
| Mode | Thinking (для качественного reasoning) |
| Temperature | 0.3 |
| Max tokens | 400 |
| Timeout | `timeoutSlowMs` (15s) |
| Cache | Caffeine, TTL 24h, per offerId |

### 8.5. REST API

```
GET /api/workspaces/{workspaceId}/ai/advisor/{offerId}
    → AdvisorResponse | 204 No Content
```

**`AdvisorResponse.java`:**

```java
public record AdvisorResponse(
    String advice,
    OffsetDateTime generatedAt
) {}
```

### 8.6. Frontend

В `decision-detail-panel.component.ts` и `offer-detail-panel.component.ts`:

- Новая секция «AI Совет» (после explanation block).
- Badge «AI» рядом с заголовком (визуальная маркировка AI-контента).
- Lazy load: запрос при раскрытии секции.
- Состояния: loading (skeleton), content (текст), unavailable (скрыт), error (скрыт).
- Кнопка «Обновить» — инвалидация кэша + повторный запрос.

---

## 9. F2: Anomaly Explanation

### 9.1. Поток данных

```
AlertEventCreatedEvent published
        │
        ▼
AnomalyExplanationListener (@Async("aiExecutor"), @EventListener)
  1. Check alert type: only SPIKE_DETECTION, RESIDUAL_ANOMALY
  2. Load P&L delta from marts (current vs previous period)
  3. Build structured context from alert_event.details + delta
  4. LLM → explanation text
  5. Update alert_event.details JSONB: add "ai_explanation" field
```

### 9.2. Backend

**`AnomalyExplanationListener.java`** (domain/):

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class AnomalyExplanationListener {

  private final AlertEventRepository alertRepo;
  private final LlmClient llmClient;
  private final AiProperties props;
  // CH read repositories for P&L delta data
  private final PnlDeltaQueryService pnlDeltaService;

  @Async("aiExecutor")
  @EventListener
  public void onAlertCreated(AlertEventCreatedEvent event) {
    if (!props.isEnabled()) return;

    var ruleType = event.ruleType();
    if (ruleType == null) return;
    if (ruleType != AlertRuleType.SPIKE_DETECTION
        && ruleType != AlertRuleType.RESIDUAL_ANOMALY) {
      return;
    }

    try {
      var alertEvent = alertRepo.findById(event.alertEventId()).orElse(null);
      if (alertEvent == null) return;

      var context = buildContext(alertEvent);
      var explanation = llmClient.chat(SYSTEM_PROMPT, context,
          props.getTimeoutSlowMs(), 0.3, 500);

      alertRepo.updateAiExplanation(event.alertEventId(), explanation);

    } catch (AiUnavailableException e) {
      log.debug("LLM unavailable for anomaly explanation: alertId={}", event.alertEventId());
    } catch (Exception e) {
      log.error("Anomaly explanation failed: alertId={}", event.alertEventId(), e);
    }
  }
}
```

### 9.3. Prompt template

```
System: Ты — аналитик маркетплейсов. Объясни аномалию простым языком.
3-5 предложений: что произошло, почему, impact в рублях, одна рекомендация.
Русский язык. Только конкретные числа из контекста.

Товар: {productName} ({skuCode}), {marketplaceType}
Тип аномалии: {anomalyType}
Период: {dateFrom} — {dateTo}

P&L delta:
- Маржа: {marginBefore}% → {marginAfter}%
- Revenue: {revenueBefore}₽ → {revenueAfter}₽
- Изменения компонентов: {componentsDelta}

Дополнительно:
- Return rate: {returnRateBefore}% → {returnRateAfter}%
- Velocity: {velocity} шт/день
- Stock: {availableStock} шт
```

| Параметр | Значение |
|----------|----------|
| Mode | Thinking |
| Temperature | 0.3 |
| Max tokens | 500 |
| Timeout | `timeoutSlowMs` (15s) |
| Trigger | Async, non-blocking |

### 9.4. Frontend

В компоненте алертов: если `alert_event.details.ai_explanation` не null — показывать блок «AI-объяснение» с текстом и badge «AI». Если null — поведение без изменений.

---

## 10. F5: Proactive Insights

### 10.1. Типы инсайтов

| Тип | SQL-детектор | Trigger | Порог |
|-----|-------------|---------|-------|
| `PRICE_INCREASE_CANDIDATE` | velocity >= 10 шт/день AND margin > 25% AND return_rate < 5% AND days_of_cover > 30 | Daily | >= 3 кандидатов |
| `FROZEN_CAPITAL` | days_of_cover > 60 AND velocity < 0.5 AND frozen_capital > 10000 | Daily | >= 1 товар |
| `RETURN_PATTERN_ALERT` | return_rate > 3 * avg_category_return_rate | Daily | >= 1 товар |

### 10.2. Backend

**`InsightDetector`** interface:

```java
public interface InsightDetector {
  String insightType();
  List<InsightCandidate> detect(long workspaceId, List<Long> connectionIds);

  record InsightCandidate(
      String entityType,
      long entityId,
      Map<String, Object> structuredData
  ) {}
}
```

**Реализации:** `PriceIncreaseCandidateDetector`, `FrozenCapitalDetector`, `ReturnPatternDetector` — каждый содержит SQL-запрос к marts в ClickHouse.

**`InsightScheduler.java`:**

```java
@Scheduled(cron = "${datapulse.ai.insight-cron:0 0 6 * * *}")
@SchedulerLock(name = "aiInsights", lockAtMostFor = "PT30M")
public void generateInsights() {
  for (var wsId : workspaceRepository.findAllActiveWorkspaceIds()) {
    var connections = connectionRepository.findActiveByWorkspace(wsId);
    var connIds = connections.stream().map(c -> c.getId()).toList();

    for (var detector : detectors) {
      var candidates = detector.detect(wsId, connIds);
      for (var candidate : candidates) {
        var dedupeKey = "%s:%s:%d".formatted(
            detector.insightType(), candidate.entityType(), candidate.entityId());

        if (insightRepo.existsRecentByDedupeKey(wsId, dedupeKey, 7)) continue;

        var text = verbalize(detector.insightType(), candidate);
        var insight = saveInsight(wsId, detector.insightType(), candidate, text, dedupeKey);
        fanOutNotification(wsId, insight);
      }
    }
  }
}
```

### 10.3. Prompt templates (per detector)

**PRICE_INCREASE_CANDIDATE:**

```
System: Опиши возможность повышения цены для селлера. 2-3 предложения с числами.

Кандидаты на повышение цены:
{for each candidate}
- {productName} ({skuCode}): velocity {velocity} шт/день, маржа {margin}%,
  return rate {returnRate}%, days of cover {daysOfCover}
{end for}

Суммарный потенциал при повышении на 3-5%: +{estimatedMonthlyGain}₽/мес.
```

**FROZEN_CAPITAL:**

```
System: Опиши проблему замороженного капитала. 2-3 предложения с числами.

Товары с замороженным капиталом:
{for each candidate}
- {productName} ({skuCode}): velocity {velocity} шт/день, frozen capital {frozenCapital}₽,
  days of cover {daysOfCover}, cost price {costPrice}₽
{end for}

Суммарный замороженный капитал: {totalFrozenCapital}₽.
```

### 10.4. Notification delivery

```java
notificationService.fanOut(
    workspaceId,
    null,
    NotificationType.AI_INSIGHT,
    MessageCodes.AI_INSIGHT_TITLE,
    insight.getGeneratedText().substring(0, Math.min(200, text.length())),
    severity
);
```

### 10.5. Frontend

Инсайты отображаются:
1. В колоколе уведомлений (как обычное notification с type `AI_INSIGHT`).
2. В Morning Briefing (блок «Quick wins» — top-1 per type).
3. Страница `/workspace/{id}/insights` (будущее — отдельная страница, не в scope текущего TBD).

---

## 11. Зависимости и порядок реализации

### Граф зависимостей

```
[Infra: datapulse-ai module + LlmClient + config]
        │
        ├──→ [DB migrations]
        │         │
        │         ├──→ F10: Auto-classification (backend → frontend)
        │         ├──→ F8: Morning Briefing (backend → frontend)
        │         ├──→ F3: Weekly Digest (backend only, reuses F8 aggregator)
        │         ├──→ F2: Anomaly Explanation (backend → frontend minor)
        │         └──→ F5: Proactive Insights (backend → frontend minor)
        │
        └──→ F1: Command Palette (backend → frontend)
                  (no new tables needed)

F4: Pricing Advisor
  depends on: Infra + Phase C completion
  (no new tables — uses Caffeine cache)
```

### Рекомендуемый порядок (по промптам)

| # | Что | Зависит от | Effort |
|---|-----|-----------|--------|
| 1 | Infra: Maven module + LlmClient + config | — | 1 день |
| 2 | DB migrations | #1 | 0.5 дня |
| 3 | F10 Backend | #1, #2 | 1 день |
| 4 | F10 Frontend | #3 | 0.5 дня |
| 5 | F1 Backend | #1 | 0.5 дня |
| 6 | F1 Frontend | #5 | 1 день |
| 7 | F8 Backend | #1, #2 | 1 день |
| 8 | F8 Frontend | #7 | 1 день |
| 9 | F3 Backend | #7 (reuses aggregator) | 0.5 дня |
| 10 | F4 Backend | #1 + Phase C | 1 день |
| 11 | F4 Frontend | #10 | 0.5 дня |
| 12 | F2 Backend | #1 | 1 день |
| 13 | F5 Backend | #1, #2 | 1.5 дня |

**Итого:** ~10 рабочих дней на все фичи (Тир 1 + Тир 2).

---

## MessageCodes (i18n)

Добавить в `MessageCodes.java`:

```java
// AI
public static final String AI_DIGEST_WEEKLY_TITLE = "ai.digest.weekly.title";
public static final String AI_DIGEST_WEEKLY_BODY = "ai.digest.weekly.body";
public static final String AI_INSIGHT_TITLE = "ai.insight.title";
public static final String AI_CLASSIFICATION_ACCEPTED = "ai.classification.accepted";
public static final String AI_CLASSIFICATION_REJECTED = "ai.classification.rejected";
public static final String AI_UNAVAILABLE = "ai.unavailable";
```

Добавить в `frontend/src/locale/ru.json`:

```json
{
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
}
```
