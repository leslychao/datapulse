# Модуль: Tenancy & IAM

**Фаза:** A — Foundation
**Зависимости:** нет (корневой модуль)
**Runtime:** datapulse-api

---

## Назначение

Мультитенантная модель с workspace-изоляцией данных, управление пользователями, ролями, приглашениями и авторизацией. Обеспечивает security boundary для всех остальных модулей.

## Ключевые концепции

### Tenant vs Workspace

- **Tenant** — юрлицо / организация. Контейнер для workspace-ов. Граница биллинга.
- **Workspace** — операционное пространство (бренд, команда). **Граница изоляции данных**. Все бизнес-сущности привязаны к workspace напрямую или транзитивно.

Tenant — организационный контейнер (юрлицо, биллинг), **не** граница данных. Workspace — граница изоляции данных.

### Иерархия

```
Tenant (юрлицо)
  └── Workspace (операционное пространство)
        ├── Marketplace connections
        ├── Products, orders, finances
        ├── Pricing policies
        └── Workspace members (users + roles)
```

## Модель данных

### Таблицы PostgreSQL

| Таблица | Назначение | Ключевые поля |
|---------|------------|---------------|
| `tenant` | Юрлицо / организация; контейнер для workspace-ов | name, slug, status, owner_user_id |
| `workspace` | Операционное пространство; **граница изоляции данных** | tenant_id (FK), name, slug, status, owner_user_id |
| `app_user` | Глобальный пользователь системы | external_id (unique, Keycloak `sub`), email (unique), name, status |
| `workspace_member` | Членство пользователя в workspace с ролью | workspace_id (FK), user_id (FK), role (enum), status; unique (workspace_id, user_id) |
| `workspace_invitation` | Приглашение пользователя в workspace | workspace_id (FK), email, role, status, token_hash, expires_at, invited_by_user_id |

### DDL

```sql
tenant:
  id                  BIGSERIAL PK
  name                VARCHAR(255) NOT NULL
  slug                VARCHAR(80) NOT NULL UNIQUE       -- auto-generated: lowercase(name), transliterate, replace spaces with '-', append random 4-char suffix on collision
  status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'  -- ACTIVE, SUSPENDED, ARCHIVED
  owner_user_id       BIGINT FK → app_user              NOT NULL
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()

workspace:
  id                  BIGSERIAL PK
  tenant_id           BIGINT FK → tenant                NOT NULL
  name                VARCHAR(255) NOT NULL
  slug                VARCHAR(80) NOT NULL               -- unique within tenant; same generation rules as tenant.slug
  status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'  -- ACTIVE, SUSPENDED, ARCHIVED
  owner_user_id       BIGINT FK → app_user              NOT NULL  -- denormalized; authoritative source = workspace_member WHERE role = OWNER
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()

  UNIQUE (tenant_id, slug)

app_user:
  id                  BIGSERIAL PK
  external_id         VARCHAR(120) NOT NULL UNIQUE       -- Keycloak `sub` (UUID)
  email               VARCHAR(320) NOT NULL UNIQUE       -- RFC 5321 max length
  name                VARCHAR(255) NOT NULL
  status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'  -- ACTIVE, DEACTIVATED
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()

workspace_member:
  id                  BIGSERIAL PK
  workspace_id        BIGINT FK → workspace              NOT NULL
  user_id             BIGINT FK → app_user               NOT NULL
  role                VARCHAR(30) NOT NULL               -- OWNER, ADMIN, PRICING_MANAGER, OPERATOR, ANALYST, VIEWER
  status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'  -- ACTIVE, INACTIVE
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()

  UNIQUE (workspace_id, user_id)

workspace_invitation:
  id                  BIGSERIAL PK
  workspace_id        BIGINT FK → workspace              NOT NULL
  email               VARCHAR(320) NOT NULL
  role                VARCHAR(30) NOT NULL               -- target role on accept
  status              VARCHAR(20) NOT NULL DEFAULT 'PENDING'  -- PENDING, ACCEPTED, EXPIRED, CANCELLED
  token_hash          VARCHAR(64) NOT NULL               -- SHA-256 of invitation token
  expires_at          TIMESTAMPTZ NOT NULL               -- default: created_at + 7 days (configurable)
  invited_by_user_id  BIGINT FK → app_user               NOT NULL
  accepted_by_user_id BIGINT FK → app_user               (nullable — set on accept)
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()

  UNIQUE (workspace_id, email) WHERE status = 'PENDING'  -- один active invite per email per workspace
```

### Slug generation

Slug — человекочитаемый URL-safe идентификатор для tenant и workspace.

**Алгоритм:**
1. `input = name.trim().toLowerCase()`
2. Транслитерация кириллицы (ICU4J или ручная таблица: а→a, б→b, в→v, ...)
3. Замена пробелов и спецсимволов на `-`
4. Удаление повторяющихся `-` и trailing `-`
5. Truncate до 70 символов
6. Проверка уникальности: `tenant.slug` — глобально; `workspace.slug` — в рамках `tenant_id`
7. При коллизии: append `-XXXX` (4 случайных alphanumeric символа)
8. Максимальная длина с суффиксом: 80 символов

**Реализация:** utility-метод `SlugUtils.generateSlug(name)` + retry на unique constraint violation (max 3 attempts).

### Entity statuses

| Таблица | Enum поле | Значения | Описание |
|---------|-----------|----------|----------|
| `tenant` | `status` | `ACTIVE`, `SUSPENDED`, `ARCHIVED` | SUSPENDED — billing / admin action; ARCHIVED — soft delete |
| `workspace` | `status` | `ACTIVE`, `SUSPENDED`, `ARCHIVED` | См. §Workspace lifecycle |
| `app_user` | `status` | `ACTIVE`, `DEACTIVATED` | См. §User lifecycle |
| `workspace_member` | `status` | `ACTIVE`, `INACTIVE` | INACTIVE — при деактивации пользователя или ручном удалении из workspace |
| `workspace_invitation` | `status` | `PENDING`, `ACCEPTED`, `EXPIRED`, `CANCELLED` | См. §Invitation flow |

### Роли

Enum в `workspace_member.role`:

| Роль | Назначение |
|------|------------|
| `OWNER` | Полный доступ; обязан иметь запись в `workspace_member` |
| `ADMIN` | Управление workspace: пользователи, подключения, конфигурация |
| `PRICING_MANAGER` | Управление ценообразованием: policy config, approval, auto-execution |
| `OPERATOR` | Операционная работа: hold/lock, working queues, manual actions |
| `ANALYST` | Аналитика: просмотр данных, saved views |
| `VIEWER` | Только просмотр |

`workspace.owner_user_id` — denormalized pointer для удобства. Авторитетный source — `workspace_member` с `role = OWNER`.

### Матрица разрешений

| Capability | viewer | analyst | operator | pricing manager | admin |
|------------|--------|---------|----------|-----------------|-------|
| Просмотр данных (P&L, остатки, заказы) | ✓ | ✓ | ✓ | ✓ | ✓ |
| Saved views (создание / редактирование) | — | ✓ | ✓ | ✓ | ✓ |
| Working queues (просмотр / assignment) | — | — | ✓ | ✓ | ✓ |
| Manual price lock / hold | — | — | ✓ | ✓ | ✓ |
| Pricing policy configuration | — | — | — | ✓ | ✓ |
| Manual approval / reject price actions | — | — | — | ✓ | ✓ |
| Enable auto-execution | — | — | — | ✓ | ✓ |
| Marketplace account management | — | — | — | — | ✓ |
| User / role management | — | — | — | — | ✓ |
| Workspace configuration | — | — | — | — | ✓ |

> **OWNER** не выделен в отдельную колонку: OWNER наследует все permissions роли ADMIN и дополнительно обладает ownership-specific capabilities (ownership transfer, delete workspace). См. §Ownership transfer ниже.

### Ownership transfer

- OWNER не может быть удалён из workspace, пока не назначен другой OWNER.
- Transfer: атомарно (одна транзакция) — старый OWNER → ADMIN, новый member → OWNER.
- `workspace.owner_user_id` обновляется в той же транзакции.
- Только текущий OWNER может инициировать transfer.
- Audit: `action_type = 'workspace.transfer_ownership'`.

## Аутентификация

| Требование | Обоснование |
|------------|-------------|
| OAuth2 Resource Server (JWT от Keycloak) | Единый identity provider; stateless token validation |
| Edge proxy (oauth2-proxy) для внешнего трафика | Дополнительный слой защиты на границе |

### Keycloak configuration

| Параметр | Значение | Обоснование |
|----------|----------|-------------|
| Realm | `datapulse` | Единый realm для всех environments |
| Client: API | `datapulse-api` (confidential) | Backend-to-backend, token validation |
| Client: SPA | `datapulse-spa` (public, PKCE) | Frontend SPA, authorization code flow |
| Access token lifetime | 5 мин | Короткий — минимизация окна при компрометации |
| Refresh token lifetime | 30 мин | Достаточно для рабочей сессии |
| Session idle timeout | 30 мин | Автоматический logout при бездействии |

### JWT claims и WorkspaceContext

**JWT payload (Keycloak стандартные claims):**

| Claim | Описание | Использование |
|-------|----------|---------------|
| `sub` | Keycloak user UUID | Связь с `app_user.external_id` |
| `email` | Email пользователя | Fallback lookup: `app_user.email` |
| `preferred_username` | Display name (primary) | UI display; fallback chain: preferred_username → given_name + family_name → name claim → email |
| `realm_access.roles` | Keycloak realm roles | Не используется для RBAC (роли — в `workspace_member`) |

**WorkspaceContext resolution:**

```
1. JWT validated by Spring Security OAuth2 Resource Server
2. Security filter extracts `sub` → lookup app_user by external_id
3. HTTP header `X-Workspace-Id` → workspace_id
4. Validate: workspace_member EXISTS (user_id, workspace_id, status = ACTIVE)
5. Set WorkspaceContext (request-scoped bean): { user_id, workspace_id, role }
6. Enrich SecurityContext: workspace role добавляется как GrantedAuthority (ROLE_{role})
   в JwtAuthenticationToken — это позволяет @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
   работать корректно
7. @PreAuthorize checks reference enriched authorities
```

**Без `X-Workspace-Id`:** endpoints не привязанные к workspace (user profile, workspace list, invitation accept) работают без header. Workspace-scoped endpoints: если header отсутствует, `WorkspaceContextFilter` не заполняет `WorkspaceContext` и не обогащает `SecurityContext` ролевыми `GrantedAuthority`. В результате `@PreAuthorize` (проверяющий `hasAnyAuthority('ROLE_...')` или `@workspaceAccessService.isCurrentWorkspace(...)`) отклоняет запрос с HTTP 403.

### Security filter chain

```
CorsFilter
→ OAuth2ResourceServerFilter (Spring Security — JWT validation, issuer check)
→ WorkspaceContextFilter (X-Workspace-Id → membership check → WorkspaceContext bean + RBAC enrichment)
→ @PreAuthorize handlers (SpEL, role-based + workspace access checks)
```

| Настройка | Значение | Обоснование |
|-----------|----------|-------------|
| CORS | Configurable allowed origins | SPA на отдельном домене |
| CSRF | Disabled | Stateless JWT API, CSRF не применим |
| Session | Stateless (`SessionCreationPolicy.STATELESS`) | JWT — единственный auth mechanism |
| `/ws/**` | `permitAll()` | WebSocket endpoint. Аутентификация — на уровне STOMP handshake (за пределами HTTP filter chain). WebSocket security описан в модуле Integration |
| Actuator | `permitAll()` для `/actuator/health`, `/actuator/info`, `/actuator/prometheus` | Health checks и мониторинг |

## Авторизация

| Требование | Обоснование |
|------------|-------------|
| `@PreAuthorize` на уровне методов с SpEL | Декларативная авторизация, workspace-scoped проверки |
| Multi-tenant access isolation | `@workspaceAccessService.isCurrentWorkspace(#workspaceId)` — SpEL-проверка, что path-переменная `{workspaceId}` совпадает с `X-Workspace-Id` header (предотвращение IDOR) |
| RBAC | `hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')` — проверка ролевых `GrantedAuthority`, обогащённых в `WorkspaceContextFilter` |

**`WorkspaceAccessService`** (`io.datapulse.platform.security`) — access-checking bean для SpEL:
- `isCurrentWorkspace(Long workspaceId)` — проверяет, что path workspaceId == `WorkspaceContext.workspaceId` (предотвращает IDOR-атаки, где path variable отличается от header).
- Применяется ко всем endpoint-ам с `{workspaceId}` в path: MemberController, InvitationController, WorkspaceController (get/update).
- Для endpoint-ов с `{connectionId}` (ConnectionController, JobController): workspace isolation обеспечивается на уровне сервиса — service-методы принимают `workspaceContext.getWorkspaceId()` и скопируют запросы в БД.

**Формат `@PreAuthorize` для workspace-scoped endpoint-ов с RBAC:**
```java
@PreAuthorize("@workspaceAccessService.isCurrentWorkspace(#workspaceId) and hasAnyAuthority('ROLE_ADMIN', 'ROLE_OWNER')")
```
Обе проверки (workspace identity + RBAC) комбинируются через `and` в одной SpEL-аннотации на уровне метода. Класс-уровневый `@PreAuthorize` не используется, т.к. в Spring Security 6.x метод-уровневый `@PreAuthorize` перекрывает класс-уровневый.

Получение текущего пользователя в сервисах: через request-scoped context-бин. Прямой доступ к `SecurityContextHolder` — только в инфраструктурном коде (фильтры, handshake handlers).

## Invitation flow

### Lifecycle приглашения

```
PENDING → ACCEPTED → (workspace_member created)
    ↓
  EXPIRED (по timeout)
    ↓
  CANCELLED (admin отменил)
```

### Правила

- **Role constraint:** ADMIN может приглашать с ролями: PRICING_MANAGER, OPERATOR, ANALYST, VIEWER. OWNER может приглашать с любой ролью, включая ADMIN. Назначение роли OWNER через invitation невозможно — используй ownership transfer.
- Приглашение содержит `token_hash` (SHA-256 от invitation token) — token отправляется по email, хранится только хеш.
- `expires_at` — configurable timeout.
- При принятии: создаётся `workspace_member` с указанной ролью.
- Повторное приглашение на тот же email в тот же workspace: обновляет существующее PENDING приглашение.
- Все попытки принятия/отклонения аудируются.

### Email delivery

- Отправка через `@Async` + `JavaMailSender` (Spring Boot Mail).
- Шаблон: `invitation-email.html` (Thymeleaf). Содержит: имя приглашающего, workspace name, ссылку с token, срок действия.
- При ошибке отправки: `log.warn`, invitation остаётся PENDING. Оператор может Resend (генерирует новый token, обновляет token_hash и expires_at).
- Phase A: SMTP relay (configurable). Phase G: transactional email service (SendGrid / AWS SES).
- Конфигурация: `datapulse.mail.from` (обратный адрес), `datapulse.mail.invitation-base-url` (базовый URL для ссылки в email).
- `InvitationMailService` — условный бин (`@ConditionalOnBean(JavaMailSender.class)`): если SMTP не настроен, email не отправляется, invitation остаётся PENDING.
- Executor: `@Async("mailExecutor")` — отдельный thread pool для отправки email.

## Signup и регистрация

### Модель: auto-provision из Keycloak

Datapulse не управляет паролями. Регистрация и аутентификация — через Keycloak. `app_user` создаётся автоматически при первом логине.

### Flow первого пользователя (self-service)

```
1. Пользователь регистрируется в Keycloak (email + пароль, или SSO)
2. Первый запрос к datapulse-api с JWT
3. WorkspaceContextFilter: app_user не найден по external_id
4. Auto-provision: INSERT app_user (external_id = sub, email, name, status = ACTIVE)
5. Ответ: 200 с флагом needs_onboarding = true (нет tenant/workspace)
6. Frontend → Onboarding wizard:
   a. Создать tenant: POST /api/tenants { name }
   b. Создать workspace: POST /api/tenants/{id}/workspaces { name }
   c. Пользователь автоматически становится OWNER workspace
7. Redirect → подключение первого маркетплейса
```

### Flow приглашённого пользователя

```
1. Пользователь получает email с invitation link (содержит token)
2. Переход по ссылке → Keycloak login/register (если нет аккаунта)
3. POST /api/invitations/accept { token } (с JWT)
4. Auto-provision app_user (если первый вход)
5. Invitation validated: token_hash match, status = PENDING, not expired
6. CREATE workspace_member (role из invitation)
7. Invitation.status → ACCEPTED
8. Redirect → workspace dashboard
```

### Ограничения Phase A

- Один workspace per tenant (multi-workspace — Phase G).
- Tenant creation доступна любому зарегистрированному пользователю (rate limit: 3 tenants per user). Реализация: `OnboardingService.createTenant()` проверяет `tenantRepository.countByOwnerUserId(ownerUserId) >= MAX_TENANTS_PER_USER` и выбрасывает `BadRequestException("tenant.limit.exceeded")` при превышении.

## Entity lifecycles

### Workspace lifecycle

```
ACTIVE → SUSPENDED → ACTIVE (reactivation)
       → ARCHIVED (soft delete)
```

| Переход | Триггер | Последствия |
|---------|---------|-------------|
| → ACTIVE | Workspace creation | Seed default alert_rules, saved_views, working_queues |
| ACTIVE → SUSPENDED | Admin action или billing issue | Все scheduled syncs paused. API read-only (403 на write operations). Actions PENDING_APPROVAL → expired |
| SUSPENDED → ACTIVE | Admin reactivation | Syncs resume. Write operations restored |
| ACTIVE → ARCHIVED | Owner explicitly archives | Все connections disabled. Все active syncs cancelled. Data retained (configurable retention). Users see "archived" badge. Workspace не удаляется физически |

**Data retention при ARCHIVED:** canonical data и analytics сохраняются. Raw S3 artifacts подчиняются стандартной retention policy. Workspace может быть unarchived (Phase G: если retention ≠ expired).

### User lifecycle

```
ACTIVE → DEACTIVATED → ACTIVE (reactivation)
```

| Переход | Триггер | Последствия |
|---------|---------|-------------|
| → ACTIVE | User registration / invitation accept | |
| ACTIVE → DEACTIVATED | Admin action | `workspace_member.status = INACTIVE` для всех memberships. JWT tokens invalidated (Keycloak session revocation). Assigned working queue items → unassigned. Manual price locks → retained (locked_by remains for audit) |
| DEACTIVATED → ACTIVE | Admin reactivation | Memberships restored to ACTIVE |

**User deletion:** не поддерживается (audit trail integrity). Deactivation — единственный способ «удалить» пользователя.

### Invitation expiry

Scheduled job (hourly): `UPDATE workspace_invitation SET status = 'EXPIRED' WHERE status = 'PENDING' AND expires_at < NOW()`.

Default `expires_at`: 7 дней от создания. Cron: `datapulse.tenancy.invitation-expiry-cron` (default: `0 0 * * * *` — каждый час).

Реализация: `InvitationExpiryScheduler` — `@Scheduled` + `@Transactional`, bulk JPQL UPDATE.

## Пользовательский сценарий: Онбординг (SC-1)

Создание workspace → подключение кабинета WB/Ozon → валидация credentials → видимость статуса синхронизации и доступных capabilities.

## Аудит

| Domain | Требование |
|--------|------------|
| User action audit | Действия пользователей: login, configuration changes, manual approvals/overrides |
| Credential audit | Все попытки доступа к credentials: кто, когда, какой account, результат |

### DDL: audit_log

Авторитетная DDL `audit_log` определена в [Audit & Alerting](audit-alerting.md) §audit_log — schema. Tenancy & IAM публикует `AuditEvent` (из `io.datapulse.platform.audit`) для workspace/user/invitation действий; Audit & Alerting listener (`AuditEventListener`) записывает в единую таблицу.

Публикация: `TenancyAuditPublisher` — helper-сервис, обёртка над `ApplicationEventPublisher`. Вызывается из `OnboardingService`, `MemberService`, `InvitationService` после каждого мутирующего действия. Исключение: `user.provision` публикуется из `WorkspaceContextFilter.provisionUser()` напрямую через `ApplicationEventPublisher` (workspace context ещё не инициализирован, `workspaceId = 0`, `actorType = "SYSTEM"`).

Audit records immutable: update и delete запрещены. Retention: не менее 12 месяцев.

### Action types (audit)

| Action type | Entity type | Описание |
|-------------|-------------|----------|
| `user.provision` | `app_user` | Auto-provision при первом логине |
| `user.deactivate` | `app_user` | Деактивация пользователя |
| `user.reactivate` | `app_user` | Реактивация пользователя |
| `workspace.create` | `workspace` | Создание workspace |
| `workspace.suspend` | `workspace` | Suspend |
| `workspace.reactivate` | `workspace` | Reactivate |
| `workspace.archive` | `workspace` | Archive |
| `workspace.transfer_ownership` | `workspace` | OWNER transfer; details: `{ from, to }` |
| `member.invite` | `workspace_invitation` | Приглашение отправлено |
| `member.accept_invitation` | `workspace_invitation` | Приглашение принято |
| `member.cancel_invitation` | `workspace_invitation` | Приглашение отменено |
| `member.change_role` | `workspace_member` | Роль изменена; details: `{ old_role, new_role }` |
| `member.remove` | `workspace_member` | Удалён из workspace |
| `credential.access` | `marketplace_connection` | Credential read (vault) |
| `credential.rotate` | `marketplace_connection` | Credential rotation |

## Workspace isolation enforcement

1. `WorkspaceContext` — request-scoped бин, устанавливается из JWT claims в security filter.
2. Все repository queries обязаны фильтровать по `workspace_id` из `WorkspaceContext`.
3. Worker'ы (без HTTP request): `WorkspaceContext` устанавливается из контекста задачи. Для ETL — через `job_execution.connection_id → marketplace_connection.workspace_id`. Для pricing/execution — из `price_action.workspace_id` или `pricing_run.workspace_id`.
4. CI: ArchUnit тест проверяет, что все public repository methods принимают `workspaceId` или используют `WorkspaceContext`.
5. RLS — не используется (может быть добавлен позже без архитектурных изменений).

## REST API

### Tenants

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| POST | `/api/tenants` | Authenticated (no workspace) | Создать tenant. Body: `{ name }`. Response: `201 { id, name, slug }` |
| GET | `/api/tenants/{tenantId}` | OWNER, ADMIN | Получить tenant |

### Workspaces

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| POST | `/api/tenants/{tenantId}/workspaces` | OWNER (tenant) | Создать workspace. Body: `{ name }`. Response: `201 { id, name, slug }`. Создатель → OWNER |
| GET | `/api/workspaces` | Authenticated | Список workspace-ов текущего пользователя (все memberships). No `X-Workspace-Id` header |
| GET | `/api/workspaces/{workspaceId}` | Any role | Детали workspace. Response: `{ id, name, slug, status, createdAt, tenantId, tenantName, tenantSlug }` |
| PUT | `/api/workspaces/{workspaceId}` | ADMIN, OWNER | Обновить workspace (name). Body: `{ name }` |
| POST | `/api/workspaces/{workspaceId}/suspend` | OWNER | Suspend workspace |
| POST | `/api/workspaces/{workspaceId}/reactivate` | OWNER | Reactivate suspended workspace |
| POST | `/api/workspaces/{workspaceId}/archive` | OWNER | Archive workspace (soft delete) |

### Members

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| GET | `/api/workspaces/{workspaceId}/members` | Any role | Список членов workspace. Response: `[{ userId, email, name, role, status }]` |
| PUT | `/api/workspaces/{workspaceId}/members/{userId}/role` | ADMIN, OWNER | Изменить роль. Body: `{ role }`. Ограничения: нельзя изменить OWNER; нельзя назначить OWNER (используй ownership transfer) |
| DELETE | `/api/workspaces/{workspaceId}/members/{userId}` | ADMIN, OWNER | Удалить из workspace → `workspace_member.status = INACTIVE`. Нельзя удалить OWNER |
| POST | `/api/workspaces/{workspaceId}/ownership-transfer` | OWNER | Transfer ownership. Body: `{ newOwnerUserId }` |

### Invitations

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| POST | `/api/workspaces/{workspaceId}/invitations` | ADMIN, OWNER | Отправить приглашение. Body: `{ email, role }`. Response: `201`. Дубль на pending email → обновляет role, token и expires_at |
| GET | `/api/workspaces/{workspaceId}/invitations` | ADMIN, OWNER | Список приглашений (все статусы, сортировка по дате создания desc) |
| DELETE | `/api/workspaces/{workspaceId}/invitations/{invitationId}` | ADMIN, OWNER | Отменить приглашение → `status = CANCELLED` |
| POST | `/api/workspaces/{workspaceId}/invitations/{invitationId}/resend` | ADMIN, OWNER | Resend: новый token, обновлён expires_at, email отправлен |
| POST | `/api/invitations/accept` | Authenticated (no workspace header) | Принять приглашение. Body: `{ token }`. Response: `{ workspaceId, workspaceName, role }`. Создаёт workspace_member. Errors: 404 — token not found; 409 — already accepted / already member; 410 — invitation expired |

### User profile

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| GET | `/api/users/me` | Authenticated | Текущий пользователь: `{ id, email, name, needsOnboarding, memberships: [{ workspaceId, workspaceName, tenantId, tenantName, role }] }`. `needsOnboarding = true` если нет активных memberships |
| PUT | `/api/users/me` | Authenticated | Обновить профиль. Body: `{ name }` |

### Audit log

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| GET | `/api/workspaces/{workspaceId}/audit-log` | ADMIN, OWNER | Paginated audit log. Filters: `?actionType=...&from=...&to=...&userId=...` |

## Frontend: Workspace Context

### WorkspaceContextStore

`WorkspaceContextStore` (`shared/stores/workspace-context.store.ts`) — глобальный NgRx SignalStore (`providedIn: 'root'`), хранящий текущий workspace context на стороне SPA.

**State:**
- `currentWorkspaceId: number | null`
- `currentWorkspaceName: string | null`
- `loading: boolean`

**Methods:**
- `setWorkspace(id, name)` — устанавливает текущий workspace, сохраняет `id` в `localStorage` (`dp_last_workspace_id`) для persistence между сессиями.
- `clearWorkspace()` — сбрасывает context.
- `setLoading(boolean)` — флаг загрузки.

**Computed:**
- `hasWorkspace` — `true` если `currentWorkspaceId !== null`.

**Использование:**
- `auth.interceptor.ts` — читает `currentWorkspaceId` и проставляет header `X-Workspace-Id` во все HTTP-запросы.
- `workspace.guard.ts` — проверяет наличие workspace перед входом в workspace-scoped routes; если нет — redirect на workspace selector.
- Shell-компоненты (top-bar, status-bar, activity-bar) — отображают текущий workspace.
- Workspace selector и onboarding wizard — вызывают `setWorkspace()` после выбора/создания.

## Связанные модули

- [Integration](integration.md) — marketplace connections привязаны к workspace
- [Pricing](pricing.md) — permission matrix определяет, кто может управлять pricing policies
- [Execution](execution.md) — approval/hold/cancel определяются ролями
- [Seller Operations](seller-operations.md) — working queues и saved views per workspace
