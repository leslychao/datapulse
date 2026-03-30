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
| `app_user` | Глобальный пользователь системы | email (unique), name, status |
| `workspace_member` | Членство пользователя в workspace с ролью | workspace_id (FK), user_id (FK), role (enum), status; unique (workspace_id, user_id) |
| `workspace_invitation` | Приглашение пользователя в workspace | workspace_id (FK), email, role, status, token_hash, expires_at, invited_by_user_id |

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

## Аутентификация

| Требование | Обоснование |
|------------|-------------|
| OAuth2 Resource Server (JWT от Keycloak) | Единый identity provider; stateless token validation |
| Edge proxy (oauth2-proxy) для внешнего трафика | Дополнительный слой защиты на границе |

## Авторизация

| Требование | Обоснование |
|------------|-------------|
| `@PreAuthorize` на уровне методов с SpEL | Декларативная авторизация, account-scoped проверки |
| Multi-tenant access isolation | `@PreAuthorize("@accessService.canRead(#connectionId)")` — проверка принадлежности connection к workspace пользователя |

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

- Приглашение содержит `token_hash` (SHA-256 от invitation token) — token отправляется по email, хранится только хеш.
- `expires_at` — configurable timeout.
- При принятии: создаётся `workspace_member` с указанной ролью.
- Повторное приглашение на тот же email в тот же workspace: обновляет существующее PENDING приглашение.
- Все попытки принятия/отклонения аудируются.

## Пользовательский сценарий: Онбординг (SC-1)

Создание workspace → подключение кабинета WB/Ozon → валидация credentials → видимость статуса синхронизации и доступных capabilities.

## Аудит

| Domain | Требование |
|--------|------------|
| User action audit | Действия пользователей: login, configuration changes, manual approvals/overrides |
| Credential audit | Все попытки доступа к credentials: кто, когда, какой account, результат |

Audit records immutable: update и delete запрещены. Retention: не менее 12 месяцев.

## Workspace isolation enforcement

1. `WorkspaceContext` — request-scoped бин, устанавливается из JWT claims в security filter.
2. Все repository queries обязаны фильтровать по `workspace_id` из `WorkspaceContext`.
3. Worker'ы (без HTTP request): `WorkspaceContext` устанавливается из `job_execution.workspace_id` перед обработкой.
4. CI: ArchUnit тест проверяет, что все public repository methods принимают `workspaceId` или используют `WorkspaceContext`.
5. RLS — не используется (может быть добавлен позже без архитектурных изменений).

## Связанные модули

- [Integration](integration.md) — marketplace connections привязаны к workspace
- [Pricing](pricing.md) — permission matrix определяет, кто может управлять pricing policies
- [Execution](execution.md) — approval/hold/cancel определяются ролями
- [Seller Operations](seller-operations.md) — working queues и saved views per workspace
