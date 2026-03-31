# Tenancy & IAM — Module Scenarios

## Роль модуля

Tenancy & IAM отвечает за multi-tenant isolation (workspaces), user management, RBAC (6 ролей), invitations, и аутентификацию через Keycloak. Обеспечивает workspace-level data isolation для всех остальных модулей.

## Сценарии

### IAM-01: Workspace creation

- **Назначение:** Создание нового workspace под существующим tenant.
- **Trigger:** `POST /api/tenants/{tenantId}/workspaces` (authenticated user).
- **Main path:** First, tenant must exist (`POST /api/tenants`). Then workspace created under tenant → creator becomes OWNER → audit_log entry. Workspace isolated: all subsequent entities scoped to this workspace.
- **Dependencies:** Keycloak auth. User authenticated. Tenant exists.
- **Failure risks:** Duplicate workspace name → unique constraint. DB failure → transaction rollback.
- **Uniqueness:** Workspace creation under tenant — foundation для всей data isolation. Единственный point of entry.

### IAM-02: User invitation

- **Назначение:** Приглашение нового пользователя в workspace с определённой ролью.
- **Trigger:** `POST /api/workspaces/{id}/invitations` (ADMIN/OWNER).
- **Main path:** Validate email + role → create invitation (PENDING) → send email → audit_log entry.
- **Dependencies:** Email service. User role: ADMIN/OWNER. Target role ≤ inviter role (role hierarchy).
- **Failure risks:** Email delivery failure → invitation created but not delivered. User can resend.
- **Uniqueness:** Async flow (email). Invitation lifecycle: PENDING → ACCEPTED / EXPIRED / REVOKED.

### IAM-03: Invitation acceptance

- **Назначение:** Приглашённый пользователь принимает приглашение.
- **Trigger:** User clicks invitation link → POST accept.
- **Main path:** Validate token → PENDING → ACCEPTED → create workspace_member с assigned role → audit_log entry.
- **Dependencies:** Valid invitation token. User authenticated (or creates account).
- **Failure risks:** Expired token → reject. Already accepted → idempotent (return existing member).
- **Uniqueness:** User-initiated. Creates workspace_member — другой state transition (Invitation → Member).

### IAM-04: Role change

- **Назначение:** Изменение роли участника workspace.
- **Trigger:** `PUT /api/workspaces/{id}/members/{memberId}/role` (ADMIN/OWNER).
- **Main path:** Validate: changer has sufficient role → update workspace_member.role → audit_log (old_role, new_role).
- **Dependencies:** Role hierarchy. Cannot demote OWNER (unless another OWNER exists). Cannot change own role.
- **Failure risks:** Last OWNER demotion → rejected (orphan workspace).
- **Uniqueness:** Role mutation — другой audit payload (old + new role). Permission boundary enforcement.

### IAM-05: Member removal

- **Назначение:** Удаление участника из workspace.
- **Trigger:** `DELETE /api/workspaces/{id}/members/{memberId}` (ADMIN/OWNER).
- **Main path:** Validate: cannot remove last OWNER → soft-delete member → audit_log entry. Removed user loses access to workspace data immediately (JWT workspace claims invalidated on next token refresh).
- **Dependencies:** Role hierarchy. At least one OWNER must remain.
- **Failure risks:** Removed user has active session → access continues until token expires. Mitigation: short token TTL.
- **Uniqueness:** Destructive action — другой business outcome (access revocation).

### IAM-06: Multi-tenant data isolation

- **Назначение:** Данные одного workspace недоступны из другого.
- **Trigger:** Every data access request.
- **Main path:** `@PreAuthorize("@accessService.canRead(#connectionId)")` → verify connection belongs to user's workspace → proceed or 403.
- **Dependencies:** workspace_member → workspace → marketplace_connection chain. Every entity has workspace_id or connection_id FK.
- **Failure risks:** Missing workspace scope check → cross-tenant data leak. Mitigation: ArchUnit rule enforcing workspace scoping.
- **Uniqueness:** Security invariant — не flow, а cross-cutting constraint проверяемый в каждом запросе.

### IAM-07: JWT validation and claims extraction

- **Назначение:** Валидация JWT от Keycloak и извлечение workspace/role claims.
- **Trigger:** Every authenticated API request.
- **Main path:** Validate JWT signature → extract claims (user_id, workspace_ids, roles) → populate SecurityContext → request proceeds.
- **Dependencies:** Keycloak public key. JWT not expired.
- **Failure risks:** Keycloak down → new tokens can't be issued. Existing tokens work until expiry. Key rotation → brief validation failure.
- **Uniqueness:** Infrastructure auth — другой layer (transport, не business).

### IAM-08: Workspace archival

- **Назначение:** Архивация workspace (soft delete).
- **Trigger:** OWNER action.
- **Main path:** Workspace → ARCHIVED. All connections → ARCHIVED. All syncs stopped. Data retained (audit compliance). Members lose access.
- **Dependencies:** Cascade: workspace → connections → syncs.
- **Failure risks:** Active pricing actions in progress → should complete or cancel before archive. Mitigation: reject archive if active actions exist.
- **Uniqueness:** Cascading lifecycle transition — другой scope (workspace-level, не connection-level).

### IAM-09: Invitation resend

- **Назначение:** Повторная отправка приглашения.
- **Trigger:** `POST /api/invitations/{id}/resend` (ADMIN/OWNER).
- **Main path:** Invitation still PENDING → regenerate token → send email → update sent_at.
- **Dependencies:** Email service. Invitation in PENDING state.
- **Failure risks:** Invitation already accepted → reject resend. Invitation expired → reject, suggest new invitation.
- **Uniqueness:** Retry of async delivery — другой trigger (explicit user action), не новое приглашение.

### IAM-10: Audit visibility boundaries

- **Назначение:** Audit log доступен только в рамках workspace.
- **Trigger:** `GET /api/audit-log` (any role).
- **Main path:** Query filtered by workspace_id from JWT claims. Cross-workspace audit entries invisible.
- **Dependencies:** audit_log.workspace_id FK. JWT workspace claims.
- **Failure risks:** Missing workspace_id filter → cross-tenant audit leak. Enforced at repository level.
- **Uniqueness:** Read-only audit scenario — другой data access pattern (audit, не operational data).

### IAM-11: Tenant creation

- **Назначение:** Создание нового tenant (организации).
- **Trigger:** `POST /api/tenants` (authenticated user, no workspace context).
- **Main path:** Create tenant → slug generated (kebab-case from name) → creator can then create workspaces under this tenant → audit_log entry.
- **Dependencies:** Authenticated user. Unique tenant name.
- **Failure risks:** Duplicate tenant name → slug collision handling (append numeric suffix). DB failure → transaction rollback.
- **Uniqueness:** Organizational container creation. Предшествует workspace creation.

### IAM-12: Ownership transfer

- **Назначение:** Передача владения workspace другому пользователю.
- **Trigger:** `POST /api/workspaces/{workspaceId}/ownership-transfer` (OWNER only). Body: `{ newOwnerUserId }`.
- **Main path:** Validate new owner is active workspace member → current OWNER → ADMIN (role change) → new member → OWNER. Audit: old_owner, new_owner.
- **Dependencies:** New owner must be existing workspace member. Current user must be OWNER.
- **Failure risks:** New owner not a member → reject. Accidental transfer → audit trail for investigation.
- **Uniqueness:** Единственный способ создать OWNER (кроме workspace creation). Bidirectional role change в одной транзакции.

### IAM-13: Workspace suspension / reactivation

- **Назначение:** Приостановка workspace (billing issue, admin action) и восстановление.
- **Trigger:** `POST /api/workspaces/{workspaceId}/suspend` (OWNER). Reactivation: `POST /api/workspaces/{workspaceId}/reactivate` (OWNER).
- **Main path:**
  - **Suspend:** ACTIVE → SUSPENDED. Все scheduled syncs paused. API переходит в read-only (403 на write operations). Actions в PENDING_APPROVAL → EXPIRED. Audit: `workspace.suspended`.
  - **Reactivate:** SUSPENDED → ACTIVE. Syncs resume. Write operations restored. Audit: `workspace.reactivated`.
- **Dependencies:** Workspace в ACTIVE (для suspend) или SUSPENDED (для reactivate). User role: OWNER.
- **Failure risks:** Suspension во время active pricing run → in-flight actions завершаются, новые блокируются. Reactivation с stale credentials → syncs fail immediately (connection validation needed).
- **Uniqueness:** Bidirectional non-terminal transition (ACTIVE ↔ SUSPENDED). Отличается от archival — данные остаются доступными на чтение, workspace восстановим.

### IAM-14: User deactivation / reactivation

- **Назначение:** Деактивация пользователя (аналог «удаления» с сохранением audit trail).
- **Trigger:** Admin action на уровне workspace.
- **Main path:**
  - **Deactivate:** ACTIVE → DEACTIVATED. `workspace_member.status` → INACTIVE для всех memberships. JWT tokens invalidated (Keycloak session revocation). Assigned working queue items → unassigned. Manual price locks → retained (`locked_by` remains for audit). Audit: `user.deactivated`.
  - **Reactivate:** DEACTIVATED → ACTIVE. Memberships restored to ACTIVE. Audit: `user.reactivated`.
- **Dependencies:** Admin role in workspace. User не является последним OWNER (если OWNER — сначала ownership transfer).
- **Failure risks:** Deactivated user's active sessions remain valid until JWT expires (short TTL mitigates). Reactivation не восстанавливает working queue assignments.
- **Uniqueness:** User deletion не поддерживается (audit trail integrity). Deactivation — единственный способ revoke access с возможностью отката.

### IAM-15: First user auto-provision (Keycloak → app_user)

- **Назначение:** Автоматическое создание app_user при первом входе через Keycloak.
- **Trigger:** Первый API-запрос с JWT от пользователя, которого нет в `app_user`.
- **Main path:** JWT validated → `WorkspaceContextFilter` → lookup `app_user` by `external_id` (JWT `sub`) → not found → INSERT `app_user` (external_id, email, name, status = ACTIVE) → response: `needs_onboarding = true` (нет tenant/workspace) → frontend → onboarding wizard (create tenant → create workspace → connect marketplace).
- **Dependencies:** Keycloak operational. JWT contains `sub`, `email`, `preferred_username`.
- **Failure risks:** Duplicate `external_id` (re-registration in Keycloak with same sub — unlikely). Race: two concurrent first requests → unique constraint on `external_id` → second INSERT fails → retry lookup → success.
- **Uniqueness:** Auto-provision в security filter — другой trigger (implicit, не explicit registration endpoint), другой flow (filter-level creation), другой outcome (onboarding flag, не workspace access).

### IAM-16: Invitation expiration (PENDING → EXPIRED)

- **Назначение:** Автоматическое истечение неиспользованных приглашений.
- **Trigger:** Scheduled job (daily).
- **Main path:** SELECT invitations WHERE status = 'PENDING' AND expires_at < now() → UPDATE status = 'EXPIRED'. Expired invitation link → user clicks → 410 Gone (invitation expired). Admin notified (optional).
- **Dependencies:** `workspace_invitation.expires_at` (default: created_at + 7 days). Scheduled job.
- **Failure risks:** Legitimate delay (user on vacation) → invitation expires → admin must create new one. Aggressive expiry → user confusion.
- **Uniqueness:** Time-based terminal transition — аналогично EXE-03 (approval timeout). Другой recovery: admin создаёт новое приглашение (не resend — старый token невалиден).

### IAM-17: Invitation cancellation by admin

- **Назначение:** Администратор отменяет pending приглашение.
- **Trigger:** `DELETE /api/invitations/{id}` или `POST /api/invitations/{id}/cancel` (ADMIN/OWNER).
- **Main path:** Validate invitation status = PENDING → UPDATE status = 'CANCELLED' → audit_log entry. Cancelled invitation link → user clicks → 410 Gone.
- **Dependencies:** User role: ADMIN/OWNER. Invitation in PENDING state.
- **Failure risks:** Race: cancellation + acceptance concurrent → CAS guard (status = PENDING). Already accepted → reject cancellation (409).
- **Uniqueness:** User-initiated terminal transition — другой actor (admin, не система), другой trigger (explicit cancel, не timeout).
