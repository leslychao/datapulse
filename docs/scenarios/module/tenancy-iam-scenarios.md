# Tenancy & IAM — Module Scenarios

## Роль модуля

Tenancy & IAM отвечает за multi-tenant isolation (workspaces), user management, RBAC (6 ролей), invitations, и аутентификацию через Keycloak. Обеспечивает workspace-level data isolation для всех остальных модулей.

## Сценарии

### IAM-01: Workspace creation

- **Назначение:** Создание нового workspace (tenant).
- **Trigger:** `POST /api/workspaces` (authenticated user).
- **Main path:** Create workspace → creator becomes OWNER → audit_log entry. Workspace isolated: all subsequent entities scoped to this workspace.
- **Dependencies:** Keycloak auth. User authenticated.
- **Failure risks:** Duplicate workspace name → unique constraint. DB failure → transaction rollback.
- **Uniqueness:** Tenant creation — foundation для всей data isolation. Единственный point of entry.

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
