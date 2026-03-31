# Pages: Authentication & Onboarding

**Фаза:** A — Foundation
**Зависимости:** [Tenancy & IAM](../modules/tenancy-iam.md), [Integration](../modules/integration.md)
**Дизайн-система:** [Frontend Design Direction](frontend-design-direction.md)

---

## Обзор

Документ описывает все экраны, которые пользователь видит **до входа в основной application shell**: аутентификация через Keycloak, выбор workspace, first-run онбординг, принятие приглашения.

Общие паттерны (цвета, типографика, кнопки, тосты, ошибки) описаны в `frontend-design-direction.md` и здесь не дублируются.

### Карта маршрутов

| Маршрут | Экран | Условие показа |
|---------|-------|----------------|
| `/` | Redirect → Keycloak или workspace | Всегда (entry point) |
| `/callback` | OAuth2 callback handler | Redirect от Keycloak |
| `/workspaces` | Workspace Selector | >1 workspace у пользователя |
| `/onboarding` | First-Run Onboarding Wizard | `needs_onboarding: true` |
| `/invitations/accept?token=...` | Invitation Acceptance | Переход по ссылке из email |

### Auth Guard — общая логика маршрутизации

```
User opens any route
  │
  ├─ No valid token?
  │    └─ Redirect → Keycloak login
  │         └─ On success → /callback → resume original route
  │
  ├─ Valid token, GET /api/users/me
  │    │
  │    ├─ needs_onboarding: true?
  │    │    └─ Redirect → /onboarding
  │    │
  │    ├─ memberships.length === 0?
  │    │    └─ Redirect → /workspaces (empty state)
  │    │
  │    ├─ memberships.length === 1?
  │    │    └─ Auto-redirect → /workspace/:id/grid
  │    │
  │    └─ memberships.length > 1?
  │         ├─ localStorage has lastWorkspaceId AND it exists in memberships?
  │         │    └─ Auto-redirect → /workspace/:lastId/grid
  │         └─ else → /workspaces
  │
  └─ Token expired mid-session?
       └─ Silent refresh (see §1.5)
```

---

## 1. Keycloak Authentication Flow

**Фаза:** A

### 1.1 Назначение

Datapulse делегирует аутентификацию Keycloak. Собственной формы логина нет — SPA перенаправляет пользователя на Keycloak-hosted страницу, стилизованную под Datapulse.

### 1.2 Конфигурация Keycloak

| Параметр | Значение | Источник |
|----------|----------|----------|
| Realm | `datapulse` | tenancy-iam.md |
| Client ID | `datapulse-spa` | public client, PKCE |
| Flow | Authorization Code + PKCE | angular-oauth2-oidc |
| Access token lifetime | 5 мин | Keycloak config |
| Refresh token lifetime | 30 мин | Keycloak config |
| Session idle timeout | 30 мин | Keycloak config |
| Response type | `code` | PKCE standard |

### 1.3 Login Flow

```
┌──────────────────┐         ┌──────────────────┐         ┌──────────────────┐
│    User opens     │         │     Keycloak      │         │   Datapulse SPA   │
│   datapulse.app   │         │   login page      │         │   /callback       │
│                  │────1───►│                  │         │                  │
│                  │         │  (Datapulse brand) │────2───►│                  │
│                  │         │                  │         │  Store tokens     │
│                  │         │                  │         │  GET /api/users/me│
│                  │         │                  │         │  Route decision   │
└──────────────────┘         └──────────────────┘         └──────────────────┘
```

1. SPA проверяет наличие valid access token в memory. Если нет — `OAuthService.initCodeFlow()` перенаправляет на Keycloak login page.
2. Пользователь вводит email + пароль (или SSO). Keycloak redirect → `/callback?code=...&state=...`.
3. SPA обменивает `code` на token pair (access + refresh) через PKCE. Токены хранятся в memory (не localStorage — XSS protection). Refresh token — в memory для silent renew.

### 1.4 Token Storage

| Что | Где | Почему |
|-----|-----|--------|
| Access token | In-memory (OAuthService) | XSS protection: не в localStorage/sessionStorage |
| Refresh token | In-memory (OAuthService) | То же |
| PKCE code verifier | SessionStorage (temporary) | Нужен между redirect'ами, удаляется после обмена |
| Last workspace ID | localStorage | Persistence между сессиями (не чувствительные данные) |

### 1.5 Silent Token Refresh

| Параметр | Значение |
|----------|----------|
| Механизм | `angular-oauth2-oidc` silent refresh (iframe-based или refresh_token grant) |
| Когда обновлять | За 60 секунд до истечения access token (token lifetime 5 мин → refresh на 4-й минуте) |
| Retry при ошибке refresh | 1 retry через 5 секунд |
| Если refresh token истёк | Redirect на Keycloak login (полная ре-аутентификация) |
| Если refresh прошёл | Новые токены подставляются бесшовно, пользователь продолжает работу |

### 1.6 Token Expiry Mid-Session

| Сценарий | Поведение |
|----------|-----------|
| Access token истёк, refresh token жив | Silent refresh в фоне. Запросы, попавшие на expired token (401), ставятся в очередь и повторяются после refresh |
| Оба токена истекли | Persistent banner (жёлтый): «Сессия истекла. Необходимо войти заново.» + кнопка «Войти» → redirect на Keycloak |
| 401 от API при valid token | Toast (error): «Ошибка авторизации. Попробуйте войти заново.» Не auto-redirect (может быть permission issue, не token issue) |
| Session idle timeout (30 мин без активности) | Keycloak invalidates session. Следующий API-вызов → 401 → refresh fails → banner + redirect |

### 1.7 Keycloak Login Page Branding

Keycloak hosted-страницы стилизованы через custom Keycloak theme:

| Элемент | Стиль |
|---------|-------|
| Логотип | Datapulse logo, центрировано сверху |
| Фон | `#FFFFFF` (белый), аналогично основному приложению |
| Поля ввода | Стиль формы из `frontend-design-direction.md`: высота 32px, border `--border-default`, focus ring `--accent-primary` |
| Кнопка «Войти» | Primary button: `--accent-primary`, белый текст, 28px высота |
| Текст | Шрифт Inter, `--text-base` (14px), `--text-primary` (#111827) |
| Ссылка «Регистрация» | Под формой, `--accent-primary` цвет, текст: «Нет аккаунта? Зарегистрироваться» |
| Ссылка «Забыли пароль?» | Под полем пароля, `--text-secondary`, текст: «Забыли пароль?» |
| Ошибки | Inline red text под полем: «Неверный email или пароль» |

### 1.8 Callback Page (`/callback`)

| Свойство | Значение |
|----------|----------|
| **Назначение** | Технический маршрут для обработки OAuth2 redirect |
| **URL** | `/callback` |
| **Видимость** | Пользователь видит эту страницу < 1 секунды (пока идёт token exchange) |
| **Содержимое** | Центрированный спиннер (16px, `--accent-primary`) + текст «Выполняется вход...» (`--text-secondary`, `--text-sm`) |
| **Ошибка** | Если token exchange провалился: текст «Не удалось выполнить вход. Попробуйте ещё раз.» + кнопка «Войти заново» (Primary) → redirect на Keycloak |
| **После успеха** | `GET /api/users/me` → маршрутизация по логике Auth Guard (§Карта маршрутов) |

### 1.9 Logout

| Свойство | Значение |
|----------|----------|
| Триггер | User menu (Top Bar) → «Выйти» |
| Действие | `OAuthService.logOut()` → redirect на Keycloak logout endpoint → redirect обратно на Datapulse (→ Keycloak login) |
| Подтверждение | Нет (non-destructive action) |
| Очистка | In-memory tokens cleared. localStorage `lastWorkspaceId` сохраняется |

---

## 2. Workspace Selector

**Фаза:** A

### 2.1 Назначение

Экран выбора workspace. Показывается после аутентификации, если у пользователя больше одного workspace. Если workspace один — пользователь автоматически перенаправляется в рабочую область.

### 2.2 Маршрут

| Свойство | Значение |
|----------|----------|
| **URL** | `/workspaces` |
| **Entry point** | Auth Guard (post-login) при `memberships.length > 1` |
| **Permissions** | Authenticated (любая роль) |
| **Guard** | `AuthGuard` — redirect на Keycloak если нет токена |

### 2.3 Data Sources

| Endpoint | Method | Описание | Response |
|----------|--------|----------|----------|
| `GET /api/users/me` | GET | Профиль + список memberships | `{ id, email, name, memberships: [{ workspaceId, workspaceName, tenantName, role }] }` |
| `GET /api/workspaces` | GET | Список workspace-ов с деталями | `[{ id, name, slug, status, connectionsCount, membersCount }]` |

Данные обогащаются на клиенте: membership (роль) + workspace details (connections count, members count) объединяются для отображения карточек.

### 2.4 Layout

Экран использует **упрощённый layout** — без Activity Bar, без Detail Panel, без Status Bar. Только Top Bar (упрощённый: логотип + user menu) и центрированная область контента.

```
┌─────────────────────────────────────────────────────────────────┐
│  [Datapulse logo]                              [User ▾] [Выйти]│
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│                   Выберите рабочее пространство                 │
│                                                                 │
│    ┌─────────────────┐  ┌─────────────────┐  ┌────────────────┐ │
│    │  Workspace A    │  │  Workspace B    │  │  Workspace C   │ │
│    │                 │  │                 │  │                │ │
│    │  Tenant: Org 1  │  │  Tenant: Org 1  │  │  Tenant: Org 2 │ │
│    │  WB · Ozon      │  │  WB             │  │  Ozon          │ │
│    │  3 участника    │  │  1 участник     │  │  2 участника   │ │
│    │  [ADMIN]        │  │  [OWNER]        │  │  [VIEWER]      │ │
│    └─────────────────┘  └─────────────────┘  └────────────────┘ │
│                                                                 │
│              [+ Создать рабочее пространство]                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 2.5 Screen States

| State | Описание | Визуал |
|-------|----------|--------|
| **Loading** | Загрузка `/api/users/me` + `/api/workspaces` | Центрированный спиннер + «Загрузка...» |
| **Loaded** | Список workspace-ов | Карточки (см. wireframe) |
| **Empty** | `memberships.length === 0` | Центрированное сообщение: «Нет доступных рабочих пространств» + описание: «Создайте своё пространство или попросите коллегу отправить приглашение.» + Primary button: «Создать рабочее пространство» |
| **Error** | API ошибка | Центрированное сообщение: «Не удалось загрузить рабочие пространства.» + Secondary button: «Повторить» |
| **Single workspace** | `memberships.length === 1` | Экран не показывается — auto-redirect на `/workspace/:id/grid` |

### 2.6 Workspace Card — интерактивные элементы

| Элемент | Компонент | Описание |
|---------|-----------|----------|
| Вся карточка | Clickable card | Hover: `--bg-tertiary` фон, `--border-default` → `--accent-primary` border. Cursor: pointer |
| Название workspace | Text, `--text-lg` (16px, 600) | Первая строка карточки |
| Название tenant | Text, `--text-sm`, `--text-secondary` | Под названием workspace |
| Marketplace badges | Inline badges | Иконки/текст подключённых маркетплейсов: «WB», «Ozon». `--text-xs`, `--bg-secondary` background, `--radius-sm` |
| Количество участников | Text, `--text-sm`, `--text-secondary` | Формат: «N участник(ов)» (склонение) |
| Роль пользователя | Status badge | Badge с ролью пользователя в этом workspace. Цвет по роли (см. таблицу ниже) |
| Click action | Navigate | `router.navigate(['/workspace', workspaceId, 'grid'])`. Сохранить `workspaceId` в `localStorage.lastWorkspaceId` |

**Роль badges:**

| Роль | Текст | Стиль |
|------|-------|-------|
| OWNER | Владелец | `--accent-primary` фон, белый текст |
| ADMIN | Администратор | `--accent-subtle` фон, `--accent-primary` текст |
| PRICING_MANAGER | Менеджер цен | `--accent-subtle` фон, `--accent-primary` текст |
| OPERATOR | Оператор | `--bg-tertiary` фон, `--text-primary` текст |
| ANALYST | Аналитик | `--bg-tertiary` фон, `--text-primary` текст |
| VIEWER | Наблюдатель | `--bg-tertiary` фон, `--text-secondary` текст |

### 2.7 Карточка workspace — детальная структура

```
┌─────────────────────────────┐
│  Мой магазин WB              │   ← --text-lg (16px, 600)
│  ООО «Торговый дом»         │   ← --text-sm, --text-secondary (tenant name)
│                              │
│  [WB] [Ozon]                │   ← marketplace badges, inline
│  3 участника                │   ← --text-sm, --text-secondary
│                              │
│  ┌──────────────┐           │
│  │ Администратор │           │   ← role badge
│  └──────────────┘           │
└─────────────────────────────┘
```

Размер карточки: ширина 280px, auto height. Grid: до 3 карточек в ряд (responsive: 2 на 1280px, 3 на 1440px+). Gap: `--space-4` (16px).

Карточка: `--bg-primary` фон, 1px `--border-default` border, `--radius-lg` (8px), `--shadow-sm`. Hover: `--shadow-md`, border → `--accent-primary`.

### 2.8 CTA «Создать рабочее пространство»

| Свойство | Значение |
|----------|----------|
| Текст | «+ Создать рабочее пространство» |
| Тип | Ghost button (карточки есть) или Primary button (empty state) |
| Видимость | Всегда (пользователь может создавать workspace-ы, ограничение: 3 tenant per user) |
| Action | Redirect → `/onboarding` (step 1: создание tenant, если нет tenant, или step 2: создание workspace) |

### 2.9 localStorage

| Key | Value | Описание |
|-----|-------|----------|
| `dp_last_workspace_id` | `number` | ID последнего выбранного workspace. Используется Auth Guard для auto-redirect |

### 2.10 Edge Cases

| Кейс | Поведение |
|------|-----------|
| Все workspace-ы SUSPENDED | Карточки отображаются с жёлтым badge «Приостановлено». Click → redirect в workspace (read-only mode, определяется shell) |
| Все workspace-ы ARCHIVED | Empty state: «Нет активных рабочих пространств.» + CTA создать новое |
| localStorage содержит ID несуществующего workspace | Игнорировать, показать Workspace Selector |
| API вернул 401 во время загрузки | Silent refresh → retry. Если refresh failed → redirect на Keycloak |
| Пользователь напрямую идёт на `/workspaces` имея 1 workspace | Auto-redirect в workspace (не показывать Selector для одного workspace) |

---

## 3. First-Run Onboarding Wizard

**Фаза:** A

### 3.1 Назначение

Пошаговый визард для нового пользователя без tenant/workspace. Проводит через создание организации, рабочего пространства и первого подключения к маркетплейсу.

### 3.2 Маршрут

| Свойство | Значение |
|----------|----------|
| **URL** | `/onboarding` (единый маршрут, step управляется state, не URL) |
| **Entry point** | Auth Guard: `GET /api/users/me` → `needs_onboarding: true` (нет memberships) |
| **Permissions** | Authenticated (без workspace context — нет `X-Workspace-Id` header на шагах 1–2) |
| **Guard** | `OnboardingGuard`: если пользователь уже имеет workspace → redirect на `/workspaces` |

### 3.3 Data Sources

| Step | Endpoint | Method | Body | Response |
|------|----------|--------|------|----------|
| 1 | `/api/tenants` | POST | `{ name }` | `201 { id, name, slug }` |
| 2 | `/api/tenants/{tenantId}/workspaces` | POST | `{ name }` | `201 { id, name, slug }` |
| 3 | `/api/connections` | POST | `{ marketplaceType, name, credentials }` | `201 { id, name, status }` |
| 3 (poll) | `/api/connections/{connectionId}` | GET | — | `{ id, status, lastErrorCode }` |

### 3.4 Layout

Онбординг занимает **Main Area** application shell. Activity Bar видна, но все иконки серые (disabled, `--text-tertiary`, pointer-events: none). Top Bar показывает логотип и user menu. Status Bar скрыта. Detail Panel закрыта.

```
┌─────────────────────────────────────────────────────────────────┐
│  [Datapulse logo]                              [User ▾] [Выйти]│
├────┬────────────────────────────────────────────────────────────┤
│    │                                                            │
│ A  │           Добро пожаловать в Datapulse                     │
│ c  │                                                            │
│ t  │       ●───────────●───────────○                            │
│ i  │    Организация  Пространство  Подключение                  │
│ v  │                                                            │
│ i  │    ┌──────────────────────────────────────────────┐        │
│ t  │    │                                              │        │
│ y  │    │          [ Step content area ]               │        │
│    │    │                                              │        │
│ B  │    │                                              │        │
│ a  │    └──────────────────────────────────────────────┘        │
│ r  │                                                            │
│    │                          [Назад]  [Далее]                  │
│(g) │                                                            │
│(r) │                                                            │
│(a) │                                                            │
│(y) │                                                            │
├────┴────────────────────────────────────────────────────────────┤
```

### 3.5 Progress Indicator

Горизонтальный stepper в верхней части Main Area. Три шага, соединённых линиями.

| Состояние шага | Визуал |
|----------------|--------|
| Completed | Заполненный круг `--accent-primary` + галочка (белая). Линия к следующему: `--accent-primary` |
| Current | Заполненный круг `--accent-primary`. Подпись bold (`--text-base`, 600). Линия к следующему: `--border-default` |
| Upcoming | Пустой круг, border `--border-default`. Подпись: `--text-secondary`. Линия: `--border-default` |

Подписи шагов:
1. «Организация»
2. «Пространство»
3. «Подключение»

Размер круга: 24px. Линия: 2px толщина, длина ~80px. Stepper центрирован горизонтально.

### 3.6 Общие элементы навигации

| Элемент | Тип | Видимость | Action |
|---------|-----|-----------|--------|
| «Назад» | Secondary button | Шаги 2, 3 | Вернуться на предыдущий шаг. Данные предыдущего шага сохранены в state |
| «Далее» / «Создать» | Primary button | Шаги 1, 2 | Submit формы текущего шага. Disabled до валидации |
| «Настроить позже» | Ghost button (text link) | Шаг 3 | Пропустить подключение маркетплейса. Redirect → `/workspace/:id/grid` |

Кнопки расположены в нижней части step content area, выровнены вправо. «Назад» слева, «Далее» справа. Gap: `--space-3` (12px).

---

### 3.7 Step 1 — Создание организации (Tenant)

#### Назначение

Создание tenant (организации) — верхнеуровневого контейнера для workspace-ов.

#### Wireframe

```
┌──────────────────────────────────────────────────────────┐
│                                                          │
│   Создайте организацию                                   │
│                                                          │
│   Организация — это ваша компания или юрлицо.            │
│   Внутри организации можно создать несколько              │
│   рабочих пространств для разных брендов                 │
│   или команд.                                            │
│                                                          │
│   Название организации *                                 │
│   ┌──────────────────────────────────────────┐           │
│   │  ООО «Торговый дом»                      │           │
│   └──────────────────────────────────────────┘           │
│                                                          │
│                                      [Далее →]           │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

#### Форма

| Поле | Label | Тип | Required | Validation | Placeholder | Default |
|------|-------|-----|----------|------------|-------------|---------|
| `name` | Название организации | Text input | Да | Min 3, max 255 символов. Только буквы (кириллица/латиница), цифры, пробелы, дефисы, кавычки | `ООО «Торговый дом»` | — |

#### Validation Rules

| Правило | Момент | Сообщение об ошибке |
|---------|--------|---------------------|
| Required | On blur + on submit | «Укажите название организации» |
| Min length 3 | On blur | «Название должно содержать не менее 3 символов» |
| Max length 255 | On input (prevent typing beyond limit) | Counter «N / 255» рядом с полем |
| Server error: duplicate slug | On submit (API 409) | «Организация с таким названием уже существует» |
| Server error: rate limit (3 tenants per user) | On submit (API 429) | «Достигнут лимит: не более 3 организаций на пользователя» |

#### API Call

| Trigger | Endpoint | Body | Success | Error |
|---------|----------|------|---------|-------|
| Click «Далее» | `POST /api/tenants` | `{ "name": "..." }` | Сохранить `tenantId` в wizard state. Перейти на Step 2 | Inline error под полем (duplicate) или toast (server error) |

#### Interactive Elements

| Элемент | Label (RU) | Тип | Action | Visibility | Confirmation |
|---------|------------|-----|--------|------------|-------------|
| Поле ввода | «Название организации» | Text input, 32px height | — | Всегда | — |
| Кнопка «Далее» | «Далее» | Primary button | Submit form → POST /api/tenants | Всегда. Disabled: поле пустое или < 3 символов | — |

#### Screen States

| State | Визуал |
|-------|--------|
| Initial | Пустое поле, «Далее» disabled |
| Valid input | «Далее» enabled (accent-primary) |
| Submitting | «Далее» → spinner внутри кнопки, кнопка disabled. Поле readonly |
| Validation error | Red border на поле + inline error text под полем (`--status-error`) |
| Server error | Toast (error variant): текст ошибки. Поле остаётся editable |
| Success | Transition → Step 2 |

---

### 3.8 Step 2 — Создание рабочего пространства (Workspace)

#### Назначение

Создание workspace — операционного пространства внутри tenant. Workspace является границей изоляции данных.

#### Wireframe

```
┌──────────────────────────────────────────────────────────┐
│                                                          │
│   Создайте рабочее пространство                          │
│                                                          │
│   Рабочее пространство — это отдельная среда для         │
│   работы с маркетплейсами. Можно создать несколько       │
│   пространств для разных брендов или направлений.        │
│                                                          │
│   Организация: ООО «Торговый дом»                        │
│                                                          │
│   Название пространства *                                │
│   ┌──────────────────────────────────────────┐           │
│   │  Основной бренд                          │           │
│   └──────────────────────────────────────────┘           │
│                                                          │
│                              [← Назад]  [Создать →]      │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

#### Форма

| Поле | Label | Тип | Required | Validation | Placeholder | Default |
|------|-------|-----|----------|------------|-------------|---------|
| `name` | Название пространства | Text input | Да | Min 3, max 255 символов | `Основной бренд` | — |

Над полем: read-only текст «Организация: {tenantName}» — показывает, в какой организации создаётся workspace. `--text-sm`, `--text-secondary`.

#### Validation Rules

| Правило | Момент | Сообщение об ошибке |
|---------|--------|---------------------|
| Required | On blur + on submit | «Укажите название пространства» |
| Min length 3 | On blur | «Название должно содержать не менее 3 символов» |
| Max length 255 | On input | Counter «N / 255» |
| Server error: duplicate slug within tenant | On submit (API 409) | «Пространство с таким названием уже существует в этой организации» |

#### API Call

| Trigger | Endpoint | Body | Success | Error |
|---------|----------|------|---------|-------|
| Click «Создать» | `POST /api/tenants/{tenantId}/workspaces` | `{ "name": "..." }` | Сохранить `workspaceId` в wizard state. Пользователь автоматически становится OWNER. Перейти на Step 3 | Inline error (duplicate) или toast (server error) |

#### Interactive Elements

| Элемент | Label (RU) | Тип | Action | Visibility | Confirmation |
|---------|------------|-----|--------|------------|-------------|
| Поле ввода | «Название пространства» | Text input, 32px height | — | Всегда | — |
| Кнопка «Назад» | «Назад» | Secondary button | Вернуться на Step 1 (данные tenant уже созданы — шаг 1 показывает success state с именем tenant, поле disabled) | Всегда | — |
| Кнопка «Создать» | «Создать» | Primary button | Submit → POST | Всегда. Disabled: поле пустое или < 3 символов | — |

#### Screen States

Аналогично Step 1 (Initial → Valid → Submitting → Error → Success → Step 3).

#### Edge Case: возврат на Step 1

Если пользователь нажал «Назад» — tenant уже создан. Step 1 показывает:
- Поле `name` заполнено и **disabled** (read-only).
- Текст под полем: «Организация создана» (`--status-success`, зелёный).
- Кнопка «Далее» заменена на «Продолжить» → возвращает на Step 2.
- Повторное создание tenant не происходит.

---

### 3.9 Step 3 — Подключение маркетплейса

#### Назначение

Подключение первого кабинета маркетплейса (WB или Ozon) с вводом credentials и async-валидацией.

#### Wireframe — выбор маркетплейса

```
┌──────────────────────────────────────────────────────────┐
│                                                          │
│   Подключите маркетплейс                                 │
│                                                          │
│   Подключите кабинет Wildberries или Ozon,               │
│   чтобы начать загрузку данных.                          │
│                                                          │
│   ┌─────────────────────┐  ┌─────────────────────┐      │
│   │                     │  │                     │      │
│   │   [WB logo]         │  │   [Ozon logo]       │      │
│   │                     │  │                     │      │
│   │   Wildberries       │  │   Ozon              │      │
│   │                     │  │                     │      │
│   └─────────────────────┘  └─────────────────────┘      │
│                                                          │
│                                                          │
│                   [← Назад]  [Настроить позже]           │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

#### Marketplace Cards

| Элемент | Компонент | Описание |
|---------|-----------|----------|
| WB Card | Selectable card | 200×140px. Логотип WB (сверху), текст «Wildberries» (снизу). Hover: `--bg-tertiary`. Selected: `--accent-subtle` фон, `--accent-primary` border (2px) |
| Ozon Card | Selectable card | Аналогично. Логотип Ozon + текст «Ozon» |

Click на карточку → показывает credentials form для выбранного маркетплейса ниже.

#### Wireframe — форма WB

```
┌──────────────────────────────────────────────────────────┐
│                                                          │
│   Подключите маркетплейс                                 │
│                                                          │
│   ┌──────────┐  ┌──────────┐                             │
│   │ [WB] ✓   │  │ [Ozon]   │                             │
│   └──────────┘  └──────────┘                             │
│                                                          │
│   Название подключения *                                 │
│   ┌──────────────────────────────────────────┐           │
│   │  Мой кабинет WB                          │           │
│   └──────────────────────────────────────────┘           │
│                                                          │
│   API-токен *                                            │
│   ┌──────────────────────────────────────────┐           │
│   │  ••••••••••••••••••••••••••••••••        │           │
│   │                                          │           │
│   │                                          │           │
│   └──────────────────────────────────────────┘  [👁]     │
│   Токен из личного кабинета WB →                         │
│   Настройки → Доступ к API                               │
│                                                          │
│                    [← Назад]  [Подключить]               │
│                                         [Настроить позже]│
│                                                          │
└──────────────────────────────────────────────────────────┘
```

#### Wireframe — форма Ozon

```
┌──────────────────────────────────────────────────────────┐
│                                                          │
│   Подключите маркетплейс                                 │
│                                                          │
│   ┌──────────┐  ┌──────────┐                             │
│   │ [WB]     │  │ [Ozon] ✓ │                             │
│   └──────────┘  └──────────┘                             │
│                                                          │
│   Название подключения *                                 │
│   ┌──────────────────────────────────────────┐           │
│   │  Мой кабинет Ozon                        │           │
│   └──────────────────────────────────────────┘           │
│                                                          │
│   Client ID *                                            │
│   ┌──────────────────────────────────────────┐           │
│   │  1943980                                 │           │
│   └──────────────────────────────────────────┘           │
│                                                          │
│   API Key *                                              │
│   ┌──────────────────────────────────────────┐           │
│   │  ••••••••••••••••••••••••••••••••        │  [👁]     │
│   └──────────────────────────────────────────┘           │
│   Client ID и API Key из Ozon Seller →                   │
│   Настройки → API ключи                                  │
│                                                          │
│                    [← Назад]  [Подключить]               │
│                                         [Настроить позже]│
│                                                          │
└──────────────────────────────────────────────────────────┘
```

#### Forms — WB Credentials

| Поле | Label | Тип | Required | Validation | Placeholder | Default |
|------|-------|-----|----------|------------|-------------|---------|
| `name` | Название подключения | Text input | Да | Min 3, max 255 | `Мой кабинет WB` | — |
| `apiToken` | API-токен | Textarea (3 строки), masked | Да | Non-blank, min 20 символов | — | — |

**Textarea masking:** по умолчанию — `type: password` стиль (содержимое скрыто точками). Кнопка-иконка «глаз» (👁) справа переключает видимость.

**Hint text** (под полем `apiToken`): «Токен из личного кабинета WB → Настройки → Доступ к API» — `--text-xs`, `--text-secondary`.

#### Forms — Ozon Credentials

| Поле | Label | Тип | Required | Validation | Placeholder | Default |
|------|-------|-----|----------|------------|-------------|---------|
| `name` | Название подключения | Text input | Да | Min 3, max 255 | `Мой кабинет Ozon` | — |
| `clientId` | Client ID | Text input | Да | Non-blank, numeric string | `1943980` | — |
| `apiKey` | API Key | Text input, masked | Да | Non-blank, min 10 символов | — | — |

**Hint text** (под `apiKey`): «Client ID и API Key из Ozon Seller → Настройки → API ключи» — `--text-xs`, `--text-secondary`.

#### Validation Rules (общие)

| Правило | Момент | Сообщение |
|---------|--------|-----------|
| Required (все поля) | On blur + on submit | «Заполните это поле» |
| Min length (name) | On blur | «Минимум 3 символа» |
| WB apiToken min length | On blur | «Токен слишком короткий» |
| Ozon clientId not numeric | On blur | «Client ID должен содержать только цифры» |
| Ozon apiKey min length | On blur | «API Key слишком короткий» |

#### API Call — Create Connection

| Trigger | Endpoint | Body (WB) | Body (Ozon) |
|---------|----------|-----------|-------------|
| Click «Подключить» | `POST /api/connections` | `{ "marketplaceType": "WB", "name": "...", "credentials": { "apiToken": "..." } }` | `{ "marketplaceType": "OZON", "name": "...", "credentials": { "clientId": "...", "apiKey": "..." } }` |

**Header:** `X-Workspace-Id: {workspaceId}` (workspace создан на шаге 2).

#### Async Validation Flow

После `POST /api/connections` → `201 { id, status: "PENDING_VALIDATION" }` начинается polling:

```
POST /api/connections → 201 (PENDING_VALIDATION)
  │
  ├─ UI: форма disabled, спиннер + «Проверяем подключение...»
  │
  ├─ Poll: GET /api/connections/{id} каждые 3 секунды
  │    │
  │    ├─ status: PENDING_VALIDATION → продолжить poll
  │    │
  │    ├─ status: ACTIVE → Success!
  │    │    └─ «Подключение установлено ✓»
  │    │    └─ Auto-redirect → /workspace/:id/grid (через 2 секунды)
  │    │
  │    └─ status: AUTH_FAILED → Failure
  │         └─ «Не удалось подключиться: неверные credentials»
  │         └─ Форма снова editable, кнопка «Попробовать снова»
  │
  └─ Timeout: 30 секунд без ответа
       └─ «Проверка занимает больше времени, чем обычно.
           Вы можете подождать или настроить подключение позже.»
       └─ Кнопки: [Подождать ещё] [Настроить позже]
```

#### Interactive Elements — Step 3

| Элемент | Label (RU) | Тип | Action | Visibility | Confirmation |
|---------|------------|-----|--------|------------|-------------|
| WB card | «Wildberries» | Selectable card | Показать WB credential form | Всегда | — |
| Ozon card | «Ozon» | Selectable card | Показать Ozon credential form | Всегда | — |
| Поле name | «Название подключения» | Text input | — | После выбора marketplace | — |
| Поле apiToken (WB) | «API-токен» | Textarea, masked | — | Если выбран WB | — |
| Поле clientId (Ozon) | «Client ID» | Text input | — | Если выбран Ozon | — |
| Поле apiKey (Ozon) | «API Key» | Text input, masked | — | Если выбран Ozon | — |
| Toggle visibility | 👁 иконка | Icon button (ghost) | Toggle password visibility | Рядом с masked полями | — |
| «Назад» | «Назад» | Secondary button | Вернуться на Step 2 (workspace уже создан — Step 2 в read-only) | Всегда | — |
| «Подключить» | «Подключить» | Primary button | Submit → POST /api/connections → poll | После выбора marketplace + заполнения формы. Disabled до валидации | — |
| «Настроить позже» | «Настроить позже» | Ghost button (text link) | Skip → redirect `/workspace/:id/grid` | Всегда | — |

#### Screen States — Step 3

| State | Визуал |
|-------|--------|
| Initial (no marketplace selected) | Две карточки маркетплейсов. Форма скрыта. «Подключить» отсутствует |
| Marketplace selected | Выбранная карточка highlighted. Форма появляется ниже с анимацией slide-down (`--transition-normal`) |
| Valid form | «Подключить» enabled |
| Submitting | Форма disabled. Кнопка «Подключить» → спиннер. Текст под формой: «Проверяем подключение...» (`--text-secondary`, italic) |
| Validating (poll) | Спиннер + «Проверяем подключение...» Кнопка «Подключить» скрыта. «Настроить позже» видна |
| Validation success | Зелёная галочка + «Подключение установлено» (`--status-success`). Auto-redirect через 2 сек. Progress bar (2px, `--status-success`) |
| Validation failure | Красная иконка + «Не удалось подключиться» + `lastErrorCode` → human-readable message. Форма editable. Кнопка «Попробовать снова» (Primary) |
| Validation timeout | Warning текст + [Подождать ещё] (Secondary) + [Настроить позже] (Ghost) |
| API error (network/server) | Toast (error): «Ошибка сервера. Попробуйте ещё раз.» Форма editable |

#### Error Code Mapping

| `lastErrorCode` | Сообщение (RU) |
|-----------------|----------------|
| `AUTH_FAILED` | «Неверные credentials. Проверьте токен/ключи и попробуйте снова.» |
| `RATE_LIMITED` | «Слишком много запросов к маркетплейсу. Подождите минуту и попробуйте снова.» |
| `TIMEOUT` | «Маркетплейс не ответил. Попробуйте позже.» |
| `null` / unknown | «Не удалось проверить подключение. Проверьте данные и попробуйте ещё раз.» |

---

### 3.10 Post-Onboarding Redirect

| Сценарий | Действие |
|----------|----------|
| Step 3 success (ACTIVE) | Auto-redirect → `/workspace/:workspaceId/grid` через 2 секунды |
| Step 3 skip («Настроить позже») | Redirect → `/workspace/:workspaceId/grid` (workspace без connections → empty state в grid) |
| Step 3 validation failure + skip | Redirect → workspace. Connection в статусе `AUTH_FAILED` отображается в Settings |

### 3.11 Edge Cases — Onboarding

| Кейс | Поведение |
|------|-----------|
| Пользователь обновляет страницу во время wizard | State wizard хранится в Angular service (in-memory). При F5 — wizard начинается сначала. `GET /api/users/me` → если tenant/workspace уже созданы → skip пройденные шаги (Step 1 read-only, Step 2 read-only, Step 3 active) |
| Пользователь закрывает вкладку после Step 1 | При следующем входе: `GET /api/users/me` → memberships пусто (workspace не создан). Но tenant существует. → Onboarding начинается с Step 2 (tenant уже есть — skip Step 1) |
| Пользователь закрывает вкладку после Step 2 | Workspace создан, пользователь OWNER, memberships непустой → Auth Guard перенаправляет в workspace. Подключение маркетплейса можно настроить в Settings |
| Tenant rate limit (3 per user) | Step 1 → API 429 → inline error: «Достигнут лимит организаций» |
| Пользователь вернулся на `/onboarding` имея workspace | `OnboardingGuard` → redirect `/workspaces` |
| API недоступен | Full-area error state: «Не удалось связаться с сервером.» + [Повторить] |
| Keycloak session expires during wizard | Silent refresh. Если failed → banner + redirect Keycloak. Wizard state lost |

---

## 4. Invitation Acceptance Flow

**Фаза:** A

### 4.1 Назначение

Обработка перехода по invitation link из email. Приглашённый пользователь принимает приглашение в workspace.

### 4.2 Маршрут

| Свойство | Значение |
|----------|----------|
| **URL** | `/invitations/accept?token=<invitation_token>` |
| **Entry point** | Клик по ссылке в email |
| **Permissions** | Authenticated (без workspace context) |
| **Guard** | `AuthGuard` — если не аутентифицирован → redirect Keycloak с `returnUrl=/invitations/accept?token=...` |

### 4.3 Data Sources

| Endpoint | Method | Body | Response |
|----------|--------|------|----------|
| `POST /api/invitations/accept` | POST | `{ "token": "..." }` | `200 { workspaceId, workspaceName, role }` |

### 4.4 Layout

Упрощённый layout (как Workspace Selector): логотип + user menu сверху, центрированная область контента. Без Activity Bar.

### 4.5 Flow

```
User clicks invitation link in email
  │
  ├─ Not authenticated?
  │    └─ Redirect → Keycloak login
  │         └─ returnUrl = /invitations/accept?token=...
  │         └─ After login → resume flow below
  │
  ├─ Token present in query params?
  │    │
  │    ├─ Yes → Auto-submit: POST /api/invitations/accept { token }
  │    │    │
  │    │    ├─ 200 OK → Success screen
  │    │    │    └─ «Вы присоединились к пространству "{workspaceName}"
  │    │    │         как {role_label}»
  │    │    │    └─ [Перейти в пространство] → /workspace/:id/grid
  │    │    │
  │    │    ├─ 404 → Invalid token
  │    │    │    └─ «Приглашение не найдено»
  │    │    │
  │    │    ├─ 410 → Expired
  │    │    │    └─ «Срок действия приглашения истёк»
  │    │    │
  │    │    └─ 409 → Already accepted
  │    │         └─ «Приглашение уже принято»
  │    │
  │    └─ No token → Error
  │         └─ «Некорректная ссылка приглашения»
  │
  └─ Auto-provision: если app_user не существует,
     backend создаёт его автоматически при POST accept
```

### 4.6 Wireframe — Loading State

```
┌─────────────────────────────────────────────────────────────────┐
│  [Datapulse logo]                              [User ▾] [Выйти]│
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│                                                                 │
│                                                                 │
│                     ◐  Принимаем приглашение...                 │
│                                                                 │
│                                                                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 4.7 Wireframe — Success

```
┌─────────────────────────────────────────────────────────────────┐
│  [Datapulse logo]                              [User ▾] [Выйти]│
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│                                                                 │
│                    ✓  Добро пожаловать!                         │
│                                                                 │
│          Вы присоединились к пространству                       │
│                «Основной бренд»                                 │
│                                                                 │
│              Ваша роль: Оператор                                │
│                                                                 │
│            [Перейти в пространство →]                            │
│                                                                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 4.8 Wireframe — Error States

```
┌─────────────────────────────────────────────────────────────────┐
│  [Datapulse logo]                              [User ▾] [Выйти]│
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│                                                                 │
│                    ✕  {Error title}                             │
│                                                                 │
│                    {Error description}                           │
│                                                                 │
│                    [{Action button}]                             │
│                                                                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 4.9 Screen States

| State | Визуал | Иконка | Заголовок | Описание | Кнопка |
|-------|--------|--------|-----------|----------|--------|
| **Loading** | Спиннер, `--accent-primary` | ◐ | — | «Принимаем приглашение...» (`--text-secondary`) | — |
| **Success** | Зелёная галочка (24px), `--status-success` | ✓ | «Добро пожаловать!» (`--text-xl`, 600) | «Вы присоединились к пространству "{name}"» + «Ваша роль: {role_label}» | «Перейти в пространство» (Primary) |
| **Expired** | Красная иконка (24px), `--status-error` | ✕ | «Приглашение истекло» | «Срок действия приглашения истёк. Попросите администратора отправить новое приглашение.» | «На главную» (Secondary) → `/workspaces` |
| **Already accepted** | Info иконка (24px), `--status-info` | ℹ | «Приглашение уже принято» | «Вы уже являетесь участником этого пространства.» | «Перейти в пространство» (Primary) → `/workspace/:id/grid` (если workspaceId в response) или «На главную» (Secondary) |
| **Invalid token** | Красная иконка (24px), `--status-error` | ✕ | «Приглашение не найдено» | «Ссылка приглашения недействительна. Возможно, она была отменена.» | «На главную» (Secondary) → `/workspaces` |
| **No token in URL** | Красная иконка, `--status-error` | ✕ | «Некорректная ссылка» | «В ссылке отсутствует токен приглашения. Проверьте ссылку из письма.» | «На главную» (Secondary) → `/workspaces` |
| **Server error** | Красная иконка, `--status-error` | ✕ | «Ошибка сервера» | «Не удалось принять приглашение. Попробуйте ещё раз.» | «Повторить» (Primary) → retry POST |
| **Network error** | Красная иконка, `--status-error` | ✕ | «Нет соединения» | «Проверьте подключение к интернету и попробуйте ещё раз.» | «Повторить» (Primary) |

### 4.10 Interactive Elements

| Элемент | Label (RU) | Тип | Action | Visibility | Confirmation |
|---------|------------|-----|--------|------------|-------------|
| «Перейти в пространство» | «Перейти в пространство» | Primary button | Navigate → `/workspace/:workspaceId/grid`. Set `localStorage.dp_last_workspace_id` | Success state | — |
| «На главную» | «На главную» | Secondary button | Navigate → `/workspaces` | Error states (expired, invalid, no token) | — |
| «Повторить» | «Повторить» | Primary button | Retry `POST /api/invitations/accept` | Server/network error states | — |

### 4.11 Role Label Mapping

| Role (API) | Label (RU) |
|------------|------------|
| `OWNER` | Владелец |
| `ADMIN` | Администратор |
| `PRICING_MANAGER` | Менеджер цен |
| `OPERATOR` | Оператор |
| `ANALYST` | Аналитик |
| `VIEWER` | Наблюдатель |

### 4.12 Edge Cases — Invitation

| Кейс | Поведение |
|------|-----------|
| Незарегистрированный пользователь по ссылке | Keycloak login → Keycloak registration (ссылка «Зарегистрироваться» на login-странице) → callback → auto-provision `app_user` → POST accept → success |
| Уже аутентифицированный пользователь | Пропустить Keycloak redirect, сразу POST accept |
| Token в URL содержит спецсимволы | URL-decode перед отправкой |
| Пользователь копирует URL и открывает в другом браузере | Нет токена в memory → Keycloak login → returnUrl сохраняет query params → resume accept flow |
| Двойное принятие (повторный клик по ссылке) | API 409 → «Приглашение уже принято» + кнопка «Перейти в пространство» |
| Invitation для другого email | API вернёт 403 или 404 (token hash не совпадёт) → «Приглашение не найдено» |
| Приглашение отменено (CANCELLED) | API 404 → «Приглашение не найдено» |
| Workspace suspended | Принятие работает, но workspace в read-only. Success screen показывает workspace как обычно |
| Одновременный accept + token expiry | Server решает — если expiry job прошёл первым → 410 Expired |

---

## 5. Angular Routing Configuration

**Фаза:** A

### 5.1 Route Table

| Path | Component | Guard | Data |
|------|-----------|-------|------|
| `` | — | `RootRedirectGuard` | Redirect logic (§Auth Guard) |
| `callback` | `CallbackComponent` | — | OAuth2 callback handler |
| `workspaces` | `WorkspaceSelectorComponent` | `AuthGuard` | — |
| `onboarding` | `OnboardingWizardComponent` | `AuthGuard`, `OnboardingGuard` | — |
| `invitations/accept` | `InvitationAcceptComponent` | `AuthGuard` | Query param: `token` |
| `workspace/:id/**` | `ShellComponent` (lazy) | `AuthGuard`, `WorkspaceGuard` | Application shell |

### 5.2 Guards

| Guard | Логика |
|-------|--------|
| `AuthGuard` | Проверяет наличие valid access token. Если нет → `OAuthService.initCodeFlow()` с `returnUrl`. Если token expired → attempt silent refresh → if fails → redirect Keycloak |
| `RootRedirectGuard` | `GET /api/users/me` → route decision (§Auth Guard — общая логика маршрутизации) |
| `OnboardingGuard` | Проверяет `needs_onboarding`. Если false (есть memberships) → redirect `/workspaces`. Prevents manual navigation to `/onboarding` after setup |
| `WorkspaceGuard` | Проверяет, что workspace `:id` есть в memberships текущего пользователя. Если нет → redirect `/workspaces` с toast: «У вас нет доступа к этому пространству.» |

### 5.3 HTTP Interceptor — Auth

| Аспект | Описание |
|--------|----------|
| Authorization header | `Bearer {access_token}` на все запросы к `/api/**` |
| X-Workspace-Id header | Добавляется из `WorkspaceContextService.currentWorkspaceId` (signal). Не добавляется на workspace-independent endpoints (`/api/users/me`, `/api/tenants`, `/api/workspaces`, `/api/invitations/accept`) |
| 401 handling | Queue failed request → attempt token refresh → replay queued requests with new token. If refresh fails → redirect Keycloak |
| 403 handling | Toast: «У вас нет прав для этого действия.» Не redirect |

---

## 6. Shared Components (Auth & Onboarding)

**Фаза:** A

Эти компоненты используются **только** на экранах auth/onboarding (до входа в shell). Общие shell-компоненты описаны в `frontend-design-direction.md`.

### 6.1 Minimal Top Bar

Упрощённый Top Bar для pre-shell screens (Workspace Selector, Onboarding, Invitation).

```
┌─────────────────────────────────────────────────────────────────┐
│  [Datapulse logo]                              [User ▾] [Выйти]│
└─────────────────────────────────────────────────────────────────┘
```

| Элемент | Описание |
|---------|----------|
| Logo | Datapulse логотип, 24px height, выровнен влево с padding `--space-4` |
| User dropdown | Email/имя пользователя, `--text-sm`. Click → dropdown: «Выйти» |
| «Выйти» | Ghost button / dropdown item. Action: logout (§1.9) |

Без: workspace switcher, breadcrumbs, search, notification bell. Эти элементы — только в full shell.

### 6.2 Centered Content Container

Контейнер для контента на pre-shell screens. Центрирован горизонтально и вертикально (или с отступом сверху ~20vh).

| Свойство | Значение |
|----------|----------|
| Max width | 640px (Onboarding forms), 960px (Workspace Selector) |
| Padding | `--space-8` (32px) |
| Background | `--bg-primary` (#FFFFFF) |
| Border | Нет (content area, не card) |

### 6.3 Status Message Block

Используется на Callback, Invitation Accept для полноэкранных статусных сообщений.

```
       [Icon 24px]
    Title (--text-xl, 600)
   Description (--text-base, --text-secondary)
      [Action button]
```

Центрирован горизонтально и вертикально. Spacing: `--space-3` между элементами.

---

## 7. Accessibility

**Фаза:** A

| Аспект | Реализация |
|--------|------------|
| Focus management | После redirect на `/callback` → фокус на контент. После навигации между step-ами → фокус на первое поле формы |
| ARIA live regions | Статус валидации подключения (polling) — `aria-live="polite"` для обновлений «Проверяем подключение...» → «Подключение установлено» |
| Form labels | Все поля связаны с label через `for`/`id`. Не placeholder-only labels |
| Error announcements | Validation errors: `aria-describedby` на input ссылается на error message. Screen reader анонсирует ошибку |
| Stepper | `role="navigation"`, `aria-label="Шаги настройки"`. Каждый шаг: `aria-current="step"` для текущего |
| Workspace cards | `role="button"`, `aria-label="{workspace name}, роль: {role}"`, keyboard-accessible (Enter/Space) |
| Marketplace cards | `role="radio"` внутри `role="radiogroup"`, `aria-label="Выбор маркетплейса"` |
| Skip link | «Перейти к содержимому» (hidden, visible on focus) — standard a11y pattern |
| Keyboard navigation | Tab order: Top Bar → content. Escape closes dropdowns. Enter submits forms |

---

## Связанные документы

- [Frontend Design Direction](frontend-design-direction.md) — дизайн-система, токены, shell, паттерны компонентов
- [Tenancy & IAM](../modules/tenancy-iam.md) — auth flow, роли, signup, invitation, REST API
- [Integration](../modules/integration.md) — connection creation, credentials format, REST API
- [Project Vision & Scope](../project-vision-and-scope.md) — фазы, constraints
