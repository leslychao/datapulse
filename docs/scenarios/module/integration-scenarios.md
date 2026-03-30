# Integration — Module Scenarios

## Роль модуля

Integration управляет marketplace connections, credentials (Vault), provider authentication, rate limiting, retry, health checks, и circuit breaker. Является infrastructure boundary между Datapulse и внешними маркетплейсами.

## Сценарии

### INT-01: Connection creation — happy path

- **Назначение:** Администратор подключает новый маркетплейс.
- **Trigger:** POST /api/connections (ADMIN).
- **Main path:** Validate input → store credentials in Vault → create secret_reference → create marketplace_connection (PENDING_VALIDATION) → async validation → ACTIVE → trigger first FULL_SYNC.
- **Dependencies:** Vault available. Provider API reachable. User role: ADMIN.
- **Failure risks:** Vault unavailable → creation fails. Invalid credentials → AUTH_FAILED.
- **Uniqueness:** Entry point для всей data pipeline. Единственный user-initiated creation flow.

### INT-02: Connection creation — validation failure

- **Назначение:** Credentials невалидны (неверный API key, expired token).
- **Trigger:** Async validation call fails (auth error from provider).
- **Main path:** PENDING_VALIDATION → AUTH_FAILED. last_error_code populated. UI показывает ошибку. No sync scheduled.
- **Dependencies:** Provider validation endpoint.
- **Failure risks:** Transient provider error classified as auth failure → false AUTH_FAILED. Mitigation: retry validation once.
- **Uniqueness:** Failure path при creation — другой terminal state, другое user experience.

### INT-03: Health-check — success

- **Назначение:** Периодическая проверка работоспособности connection.
- **Trigger:** Scheduled job (every 15 min) для всех ACTIVE connections.
- **Main path:** Lightweight API call → success → update last_check_at, last_success_at.
- **Dependencies:** Provider API reachable. Connection ACTIVE.
- **Failure risks:** False positive (API returns 200 but data incorrect). Minimal risk — health-check only verifies reachability.
- **Uniqueness:** Scheduled infrastructure check — не business operation.

### INT-04: Health-check — failure → AUTH_FAILED

- **Назначение:** Обнаружение протухших или отозванных credentials.
- **Trigger:** 3 consecutive health-check failures.
- **Main path:** update last_error_at, last_error_code → status = AUTH_FAILED → dispatch ConnectionHealthDegraded event → syncs paused.
- **Dependencies:** Failure threshold (configurable, default: 3).
- **Failure risks:** Transient provider outage → false AUTH_FAILED (3 consecutive = 45 min window reduces risk).
- **Uniqueness:** State transition ACTIVE → AUTH_FAILED — другой trigger (health-check, не validation), другой business effect (syncs paused).

### INT-05: Credential update → re-validation

- **Назначение:** Пользователь обновляет credentials после AUTH_FAILED.
- **Trigger:** PUT /api/connections/{id}/credentials.
- **Main path:** Update Vault secret → async re-validation → on success: AUTH_FAILED → ACTIVE → resume syncs.
- **Dependencies:** Vault available. New credentials valid.
- **Failure risks:** New credentials also invalid → remain AUTH_FAILED.
- **Uniqueness:** Recovery path — другой trigger (user action), другой state transition direction (AUTH_FAILED → ACTIVE).

### INT-06: Ozon Performance OAuth2 token lifecycle

- **Назначение:** Управление short-lived OAuth2 access token для Ozon Performance API.
- **Trigger:** Any call to Ozon Performance API (advertising data).
- **Main path:** Check in-memory cache → token valid → use. Token expired → call token endpoint → cache new token (TTL = expires_in - 300s) → use.
- **Dependencies:** Vault (client_id + client_secret). Ozon Performance token endpoint.
- **Failure risks:** Token endpoint down → advertising sync fails. Concurrent token refresh → race condition (acceptable: duplicate token fetch, last one wins).
- **Uniqueness:** OAuth2 flow — отдельный auth mechanism (не API key). Short-lived token. Отдельный secret_type.

### INT-07: Rate limiting — token bucket

- **Назначение:** Соблюдение rate limits маркетплейсов.
- **Trigger:** Every outbound API call.
- **Main path:** Acquire token from bucket → proceed. No token → wait (backoff) → retry acquire.
- **Dependencies:** Rate limit config per provider per API group.
- **Failure risks:** Incorrect limits → 429 responses. Recovery: adaptive backoff on 429.
- **Uniqueness:** Infrastructure cross-cutting concern. Per-adapter, per-API-group granularity.

### INT-08: Rate limit hit (429 response)

- **Назначение:** Provider вернул 429 Too Many Requests.
- **Trigger:** HTTP 429 response.
- **Main path:** Backoff (exponential, with Retry-After header if present) → retry → success. If Retry-After present → use it as delay.
- **Dependencies:** Retry policy. Rate limiter adjustment (reduce token refill rate temporarily).
- **Failure risks:** Persistent 429 → sync stalled. Mitigation: max retry count, alert.
- **Uniqueness:** Provider-initiated throttling — другой trigger (response-driven), другой recovery (adaptive backoff).

### INT-09: Connection disable / archive

- **Назначение:** Администратор отключает или архивирует connection.
- **Trigger:** POST disable / POST archive (ADMIN).
- **Main path:** ACTIVE/AUTH_FAILED → DISABLED (disable) или ARCHIVED (archive). All syncs stopped. Data retained.
- **Dependencies:** User role: ADMIN.
- **Failure risks:** Accidental disable → data freshness degrades (all syncs stop). Recovery: re-enable.
- **Uniqueness:** User-initiated lifecycle terminal transition. Другой business effect (full stop, не degradation).

### INT-10: Circuit breaker — transient degradation

- **Назначение:** Обнаружение транзиентной деградации провайдера (не auth failure).
- **Trigger:** Error rate > 50% за 15 мин при ≥ 5 calls.
- **Main path:** ConnectionDegradedEvent → log warning → increase retry backoff. 3 consecutive sync failures per domain → SyncDomainStalled alert → pause domain sync.
- **Dependencies:** Call metrics tracking. Alert rules.
- **Failure risks:** False positive (temporary spike) → unnecessary pause. Recovery: successful sync → auto-resume.
- **Uniqueness:** Отличается от health-check (INT-04): connection остаётся ACTIVE, деградация на уровне domain sync.

### INT-11: Vault unavailability

- **Назначение:** HashiCorp Vault недоступен.
- **Trigger:** Vault connection error, timeout, sealed state.
- **Main path:** Sync/write requiring credentials → fail → FAILED с error_details "vault_unavailable". Connection status НЕ меняется (infrastructure issue, не credential problem). Prolonged outage (>30 мин) → CRITICAL alert.
- **Dependencies:** Vault health monitoring.
- **Failure risks:** All syncs and writes blocked. No credential caching (security requirement).
- **Uniqueness:** Infrastructure failure — другой failure source (Vault, не provider). Другое поведение (не retry на provider, а wait for Vault).

### INT-12: Lane isolation — marketplace failure containment

- **Назначение:** Сбой одного маркетплейса не влияет на другой.
- **Trigger:** Provider-specific failure (API down, auth failed, rate limited).
- **Main path:** WB connection fails → WB syncs paused. Ozon syncs continue unaffected. Vice versa.
- **Dependencies:** Per-connection processing. Separate adapters.
- **Failure risks:** Shared infrastructure failure (PostgreSQL, Vault) → both affected (не lane-specific).
- **Uniqueness:** Isolation guarantee — architectural invariant, не отдельный flow.

### INT-13: API call logging

- **Назначение:** Логирование каждого вызова к маркетплейсу для observability и debugging.
- **Trigger:** Every outbound API call.
- **Main path:** Log: correlation_id, connection_id, provider, capability, HTTP method, endpoint, status_code, duration_ms, retry_count, error_details → integration_call_log.
- **Dependencies:** Call log table. Correlation context.
- **Failure risks:** Log write failure → non-blocking (best-effort). High volume → table growth → retention policy.
- **Uniqueness:** Observability concern — не business flow. Write-only, append-only.

### INT-14: Ozon Performance credentials attachment

- **Назначение:** Привязка Performance OAuth2 credentials к существующему Ozon connection.
- **Trigger:** PUT /api/connections/{connectionId}/performance-credentials.
- **Main path:** Validate Ozon connection exists → store performance credentials in Vault → create secret_reference (OZON_PERFORMANCE_OAUTH2) → link to connection via perf_secret_reference_id.
- **Dependencies:** Existing Ozon connection. Vault available.
- **Failure risks:** Connection not Ozon → reject. Invalid performance credentials → validation failure.
- **Uniqueness:** Дополнительный credential attachment — другой flow (не connection creation, а расширение существующего).
