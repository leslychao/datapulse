# IAM & Мультитенантность — Implementation Plan

## Обзор

DataPulse реализует мультитенантную модель, где пользователи объединяются в аккаунты (магазины/компании). Аутентификация через OAuth2 JWT (Keycloak), авторизация — ролевая модель в PostgreSQL.

## Архитектура

```
JWT (Keycloak)
 → SecurityConfig (OAuth2 Resource Server)
 → BearerTokenAuthenticationFilter
 → IamFilter (profile resolution + cache)
 → DomainUserContext (request-scoped)
 → @PreAuthorize + AccountAccessService
 → Controller → Service
```

## Компоненты

### 1. Security — `SecurityConfig`

**Два `SecurityFilterChain` бина** (по профилю):
- `@Profile("local")` — `anyRequest().permitAll()` (JWT опционален для dev)
- `@Profile("!local")` — `anyRequest().authenticated()` (production)

**Общие настройки:**
- CSRF отключен (stateless API)
- CORS включен (default)
- Stateless sessions (`SessionCreationPolicy.STATELESS`)
- OAuth2 Resource Server с JWT validation (issuer из `application.yml`)
- `IamFilter` после `BearerTokenAuthenticationFilter`

**Whitelist (без аутентификации):**
- `/actuator/health/**`
- `/api/invites/resolve` — публичный resolve инвайтов
- `/api/invites/setup-password-and-accept` — установка пароля для новых пользователей
- `/ws/**` — WebSocket handshake

### 2. JWT → Domain Profile — `IamFilter`

**`OncePerRequestFilter`** — выполняется на каждый HTTP-запрос.

**Skip условия:**
- OPTIONS (CORS preflight)
- `/actuator`, `/swagger`, `/v3/api-docs`, `/error`

**Логика:**
1. `securityHelper.getCurrentUserIfAuthenticated()` — извлечение `AuthenticatedUser` из JWT
2. `userProfileIdCache.getOrLoad(keycloakSub, ...)` — кеширование profileId по Keycloak subject
3. `iamService.ensureUserProfileAndGetId(user)` — создание/обновление `UserProfileEntity`
4. Заполнение `DomainUserContext`:
   - `profileId` — ID профиля в БД
   - `UserPrincipalSnapshot` — keycloakSub, username, email, fullName

### 3. SecurityHelper

**Извлечение claims из JWT:**
- `sub` → `keycloakSub`
- `preferred_username` → `username`
- `email` → `email`
- `name` → `fullName`
- `given_name`, `family_name` → для invite provisioning
- `locale` → `Locale`
- `auth_time` → `Instant`

### 4. DomainUserContext

**Request-scoped bean** (`@Scope(WebApplicationContext.SCOPE_REQUEST, proxyMode = TARGET_CLASS)`):
- `profileId: Long` — nullable, заполняется IamFilter
- `principal: UserPrincipalSnapshot` — JWT claims snapshot
- `requireProfileId()` — throws `SecurityException` если не заполнен
- `requireCurrentEmail()` — throws если email отсутствует
- `getProfileId()` → `Optional<Long>`

### 5. Ролевая модель — `AccountMemberRole`

**Enum с 4 ролями:**
| Роль | Read | Write | Manage Members | Delete Account |
|------|------|-------|----------------|----------------|
| OWNER | ✅ | ✅ | ✅ | ✅ |
| ADMIN | ✅ | ✅ | ✅ | ❌ |
| OPERATOR | ✅ | ✅ | ❌ | ❌ |
| VIEWER | ✅ | ❌ | ❌ | ❌ |

**Static methods:**
- `writeRoles()` → `EnumSet.of(OWNER, ADMIN, OPERATOR)`
- `manageMembersRoles()` → `EnumSet.of(OWNER, ADMIN)`
- `destructiveRoles()` → `EnumSet.of(OWNER)`

### 6. Авторизация — `AccountAccessService`

**Проверки доступа** (все `@Transactional(readOnly = true)`):
- `canRead(accountId)` → boolean
- `canWrite(accountId)` → boolean
- `canManageMembers(accountId)` → boolean
- `canDeleteAccount(accountId)` → boolean

**require-варианты:**
- `requireRead(accountId)` → throws `SecurityException` / `NotFoundException`
- `requireWrite(accountId)` → аналогично
- `requireManageMembers(accountId)` → void
- `requireDeleteAccount(accountId)` → boolean

**Логика `requireExistingAccountAndMember`:**
1. `domainUserContext.requireProfileId()` — получение текущего profile
2. `accountMemberRepository.findByAccount_IdAndUser_Id(accountId, profileId)` — поиск membership
3. Если membership найден → проверка role capabilities
4. Если не найден → проверка `accountRepository.existsById()`:
   - Аккаунт не существует → `NotFoundException`
   - Аккаунт существует, но пользователь не участник → `SecurityException`

**`AccountRoleCapabilities`** — pure functions без state, статические методы маппинга role → capability.

### 7. Invite Authorization — `InviteAuthorizationService`

**Проверка `canCreateInvite(AccountInviteCreateRequest)`:**
1. Collect distinct `accountIds` из request
2. Для каждого `accountId` → `accountAccessService.canManageMembers(id)`
3. Если хотя бы один forbidden → `SecurityException.accessDeniedForAccounts(forbiddenIds)`

### 8. Account Onboarding — `AccountOnboardingService`

**`createAccount(AccountCreateRequest)` (facade в application):**
1. `accountService.createFromRequest(request)` → JPA persist
2. `accountMemberService.ensureOwnerMembership(account.id(), profileId)` → создание OWNER membership
3. Возврат `AccountResponse.withCurrentUserRole(OWNER)`

### 9. Account Members — `AccountMemberService`

**CRUD с бизнес-правилами:**

**`grantAccountMembership(accountId, userId, role)`:**
- Если membership существует → activate + update role
- Если не существует → create
- Race condition handling через `DataIntegrityViolationException` → retry as activate

**`ensureOwnerMembership(accountId, userId)`:**
- Promote existing membership to OWNER+ACTIVE
- Или create new OWNER membership
- Idempotent (через try/catch на constraint violation)

**Защиты:**
- **Self-promotion forbidden:** `assertSelfNotPromotingRole()` — нельзя повысить свою роль (rank check: VIEWER=10, OPERATOR=20, ADMIN=30, OWNER=40)
- **Last OWNER protection:** `assertNotDemotingLastActiveOwner()` / `assertNotLastActiveOwnerDelete()` — нельзя удалить/понизить последнего active OWNER
- **Single OWNER constraint:** ловится через `DataIntegrityViolationException`

### 10. IAM Service — `IamService` (core)

**Делегация:**
- `getCurrentUserProfile(profileId)` → `UserProfileService`
- `ensureUserProfileAndGetId(user)` → `UserProfileService` (upsert по keycloakSub)
- `getAccessibleActiveAccounts(profileId)` → `AccessibleAccountsQueryService`

### 11. Accessible Accounts — `AccessibleAccountsQueryService`

**`findAccessibleActiveAccounts(profileId)`:**
1. `accountMemberRepository.findActiveMembershipsWithAccount(profileId)` — fetch join
2. Map to `AccountResponse.withCurrentUserRole(member.getRole())`

### 12. Keycloak Integration

**`KeycloakProperties`** (`@ConfigurationProperties("app.keycloak")`):
- `serverUrl`, `realm`
- `adminClient.clientId`, `adminClient.clientSecret`, `adminClient.redirectClientId`

**`KeycloakAdminConfig`** — `@Bean Keycloak` через `client_credentials` flow.

Keycloak используется исключительно как IdP (Identity Provider). Авторизация на уровне аккаунтов — через PostgreSQL membership rows, не через Keycloak realm roles.

## Использование в контроллерах

```java
@GetMapping
@PreAuthorize("@accountAccessService.canRead(#accountId)")
public Page<OrderPnlResponse> list(@PathVariable Long accountId, ...) { ... }

@PostMapping
@PreAuthorize("@accountAccessService.canWrite(#accountId)")
public PriceApplyResponse apply(@PathVariable Long accountId, ...) { ... }

@DeleteMapping
@PreAuthorize("@accountAccessService.requireDeleteAccount(#accountId)")
public void delete(@PathVariable Long accountId) { ... }
```

## Ключевые файлы

| Файл | Модуль | Роль |
|------|--------|------|
| `SecurityConfig.java` | application | SecurityFilterChain |
| `IamFilter.java` | application | JWT → domain profile |
| `SecurityHelper.java` | application | JWT claim extraction |
| `DomainUserContext.java` | application | Request-scoped identity |
| `AccountAccessService.java` | application | Role-based authorization |
| `AccountRoleCapabilities.java` | application | Role → capability mapping |
| `InviteAuthorizationService.java` | application | Invite-specific authz |
| `AccountOnboardingService.java` | application | Account creation facade |
| `IamService.java` | core | Profile management |
| `AccessibleAccountsQueryService.java` | core | Multi-account listing |
| `AccountMemberService.java` | core | Member CRUD + business rules |
| `AccountMemberRole.java` | domain | Role enum |
| `KeycloakAdminConfig.java` | core | Keycloak admin client bean |
