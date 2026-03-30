# Система инвайтов — Implementation Plan

## Обзор

Инвайт-система позволяет владельцам/админам аккаунтов приглашать новых участников по email. Поддерживает два сценария: (1) пользователь уже зарегистрирован — accept, (2) новый пользователь — setup password + accept с автоматическим provisioning в Keycloak.

## Поток

```
AccountInviteController.create(request)
 → InviteAuthorizationService.canCreateInvite() (canManageMembers по каждому accountId)
 → AccountInviteService.createInvite()
   → генерация rawToken (32 bytes, Base64url)
   → SHA-256 hash → persist AccountInviteEntity + AccountInviteTargetEntity[]
   → publish AccountInviteCreatedEvent
   → @Async @TransactionalEventListener(AFTER_COMMIT)
     → AccountInviteEmailListener
       → InviteUserProvisioner.provision(email) → Keycloak user creation if needed
       → UserProfileService.ensureUserProfileAndGetId() → pre-create domain profile
       → InviteEmailSender.sendInvite(email, rawToken) → Freemarker HTML email

AccountInviteController.resolve(token)
 → SHA-256 hash token
 → lookup by hash
 → determine ResolveState (INVALID, EXPIRED, CANCELLED, ALREADY_ACCEPTED,
     ANONYMOUS_NEED_AUTH, ANONYMOUS_NEED_PASSWORD_SETUP,
     AUTHENTICATED_EMAIL_MISMATCH, AUTHENTICATED_ALREADY_MEMBER,
     AUTHENTICATED_CAN_ACCEPT)

AccountInviteController.accept(token) [authenticated]
 → findByTokenHashForUpdate (SELECT FOR UPDATE — row lock)
 → status/expiry validation
 → email match validation
 → grantMemberships per target account
 → mark ACCEPTED

AccountInviteController.setupPasswordAndAccept(request) [no auth, whitelisted]
 → findByTokenHashForUpdate
 → inviteUserProvisioner.setPassword(email, password)
 → inviteUserProvisioner.updateUserName(email, firstName, lastName)
 → update UserProfile (fullName, username)
 → grantMemberships
 → mark ACCEPTED
```

## Компоненты

### 1. Controller — `AccountInviteController`

| Endpoint | Auth | Описание |
|----------|------|----------|
| `GET /api/invites?accountId=` | `canManageMembers(accountId)` | Список инвайтов аккаунта |
| `POST /api/invites` | `isAuthenticated() + canCreateInvite(request)` | Создание инвайта |
| `GET /api/invites/resolve?token=` | Нет (whitelist) | Resolve состояния инвайта |
| `POST /api/invites/accept` | `isAuthenticated()` | Принятие инвайта |
| `POST /api/invites/setup-password-and-accept` | Нет (whitelist) | Установка пароля + принятие |
| `POST /api/invites/{inviteId}/resend` | `isAuthenticated()` | Повторная отправка |
| `DELETE /api/invites/{inviteId}` | `isAuthenticated()` | Отмена инвайта |

### 2. Service — `AccountInviteService`

**Создание (`createInvite`):**
1. Валидация: OWNER role запрещён, email required, accountIds not empty
2. Проверка дубликатов: `existsByEmailAndStatusAndExpiresAtAfter(email, PENDING, now)`
3. Token: `SecureRandom(32 bytes)` → `Base64.getUrlEncoder().withoutPadding()`
4. Hash: `SHA-256(rawToken)` → `Base64.getUrlEncoder().withoutPadding()`
5. Expiry: `now + 3 days`
6. Persist: `AccountInviteEntity` + `AccountInviteTargetEntity[]` (per-account role)
7. Event: `AccountInviteCreatedEvent(email, rawToken)`

**Resolve (`resolveInternal`):**
State machine по данным из БД и контексту (anonymous/authenticated):
- `INVALID` — инвайт не найден
- `CANCELLED` — отменён
- `EXPIRED` — просрочен
- `ALREADY_ACCEPTED` — уже принят
- `ANONYMOUS_NEED_AUTH` — пользователь с паролем, нужна авторизация
- `ANONYMOUS_NEED_PASSWORD_SETUP` — нет credentials, нужна установка пароля
- `AUTHENTICATED_EMAIL_MISMATCH` — email текущего пользователя не совпадает
- `AUTHENTICATED_ALREADY_MEMBER` — уже участник хотя бы одного target-аккаунта
- `AUTHENTICATED_CAN_ACCEPT` — можно принять

**Accept:**
1. `findByTokenHashForUpdate(tokenHash)` — SELECT FOR UPDATE (pessimistic lock)
2. Status checks: CANCELLED → error, ACCEPTED → idempotent return
3. Email match: `normalizeEmail(currentEmail) == invite.email`
4. Expiry check → auto-mark EXPIRED if overdue
5. `grantMemberships(profileId, targets)` — per-target `AccountMemberService.grantAccountMembership()`
6. Mark `ACCEPTED`, set `acceptedByProfileId`, `acceptedAt`

**Setup Password & Accept:**
1. Same token/status validation
2. `hasCredentials(email)` check → error if already has password
3. `setPassword(email, password)` → Keycloak Admin API
4. `updateUserName(email, firstName, lastName)` → Keycloak
5. Update `UserProfileEntity` (fullName, username)
6. `grantMemberships` + mark `ACCEPTED`

**Resend:**
1. Creator check: `createdByProfileId == currentProfileId`
2. Cancel old invite → `CANCELLED`
3. Create new invite with same email + targets + fresh token + new expiry

### 3. Email — `AccountInviteEmailListener`

**`@Async @TransactionalEventListener(AFTER_COMMIT)`** — запускается в отдельном потоке после коммита.

**Логика:**
1. `inviteUserProvisioner.provision(email)` → Keycloak user creation/lookup
2. Pre-create `UserProfile` для нового пользователя
3. `inviteEmailSender.sendInvite(email, rawToken)`

### 4. Email Sender — `InviteEmailSenderImpl`

**Template:** Freemarker `mail/account-invite.ftl`
**Locale:** `ru` (hardcoded)
**URL:** `{publicBaseUrl}/invites/accept?token={rawToken}`
**i18n keys:** `INVITE_EMAIL_SUBJECT`, `INVITE_EMAIL_TITLE`, `INVITE_EMAIL_LEAD`, `INVITE_EMAIL_CTA`, etc.
**Email masking:** `a***z@domain.com` в логах

### 5. Keycloak Provisioner — `KeycloakInviteUserProvisioner`

**`provision(email)`:**
1. `searchByEmail(email, exact=true)`
2. Если найден → return `ProvisionResult(userId, newlyCreated=false)`
3. Если нет → `create(UserRepresentation)` → return `ProvisionResult(userId, newlyCreated=true)`
4. 409 Conflict → retry search

**`hasCredentials(email)`:**
- Search user → get credentials list → any with type `PASSWORD`

**`setPassword(email, password)`:**
- `resetPassword(CredentialRepresentation)` с `temporary=false`

**`updateUserName(email, firstName, lastName)`:**
- Update `UserRepresentation` с новыми `firstName`/`lastName`

### 6. Cleanup — `AccountInviteCleanupJob`

**`@Scheduled(cron = "0 0 3 * * *")`** — ежедневно в 03:00.
**Логика:** `expirePendingInvites(PENDING → EXPIRED, now)` — bulk update.

### 7. Entities

**`AccountInviteEntity` (table: `account_invite`):**
- `email`, `tokenHash`, `status` (PENDING/ACCEPTED/EXPIRED/CANCELLED)
- `expiresAt`, `createdByProfileId`, `acceptedByProfileId`, `acceptedAt`
- Extends `LongBaseEntity` (id, createdAt, updatedAt)

**`AccountInviteTargetEntity` (table: `account_invite_target`):**
- `inviteId`, `accountId`, `initialRole` (AccountMemberRole)

## Ключевые файлы

| Файл | Модуль | Роль |
|------|--------|------|
| `AccountInviteController.java` | application | REST endpoints |
| `AccountInviteService.java` | core | Business logic (489 строк) |
| `AccountInviteEmailListener.java` | core | Async email dispatch |
| `InviteEmailSenderImpl.java` | core | HTML email via Freemarker |
| `InviteUserProvisioner.java` | core | SPI interface |
| `KeycloakInviteUserProvisioner.java` | core | Keycloak Admin API |
| `AccountInviteCleanupJob.java` | core | Scheduled expiry |
| `AccountInviteEntity.java` | core | JPA entity |
| `AccountInviteTargetEntity.java` | core | Per-account role target |
