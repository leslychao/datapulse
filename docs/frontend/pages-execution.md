# Pages: Execution & Actions

**Модуль:** Execution
**Фазы:** D (Execution), F (Simulation)
**Activity Bar icon:** `play-circle` (Lucide)
**Breadcrumb root:** `Исполнение`

---

## Общая навигация модуля

Activity Bar → Execution открывает вкладку по умолчанию — Actions List.
Вкладки внутри модуля:

| Вкладка | Route | Фаза |
|---------|-------|------|
| Действия | `/workspace/:id/execution/actions` | D |
| Симуляция | `/workspace/:id/execution/simulation` | F |

---

## Status badge mapping (все 12 состояний)

Единый словарь статусов для всего модуля. Используется в таблицах, Detail Panel, state machine визуализации.

| Status (enum) | Dot color | Label (RU) | CSS token |
|---------------|-----------|------------|-----------|
| `PENDING_APPROVAL` | blue | Ожидает | `--status-info` |
| `APPROVED` | blue | Одобрено | `--status-info` |
| `ON_HOLD` | yellow | Приостановлено | `--status-warning` |
| `SCHEDULED` | blue | Запланировано | `--status-info` |
| `EXECUTING` | yellow | Выполняется | `--status-warning` |
| `RECONCILIATION_PENDING` | yellow | Проверка | `--status-warning` |
| `RETRY_SCHEDULED` | yellow | Повтор | `--status-warning` |
| `SUCCEEDED` | green | Выполнено | `--status-success` |
| `FAILED` | red | Ошибка | `--status-error` |
| `EXPIRED` | gray | Истекло | `--status-neutral` |
| `CANCELLED` | gray | Отменено | `--status-neutral` |
| `SUPERSEDED` | gray | Заменено | `--status-neutral` |

Dot — 6px circle слева от label. Label — `--text-xs` (11px). Badge — pill-shaped, inline.

Группировка по семантике для фильтров:
- **Активные** (in-flight): PENDING_APPROVAL, APPROVED, ON_HOLD, SCHEDULED, EXECUTING, RECONCILIATION_PENDING, RETRY_SCHEDULED
- **Терминальные (успех):** SUCCEEDED
- **Терминальные (проблема):** FAILED
- **Терминальные (архив):** EXPIRED, CANCELLED, SUPERSEDED

---

## Экран 1: Actions List

### 1. Route

```
/workspace/:id/execution/actions
```

Breadcrumb: `Исполнение > Действия`

### 2. Phase

D — Execution (базовый lifecycle). F — фильтр `execution_mode = SIMULATED` становится доступен.

### 3. Purpose

Основной операционный экран модуля исполнения. Показывает все ценовые действия (price actions) с их текущим статусом, позволяет фильтровать, сортировать, выбирать для bulk-операций, переходить к деталям конкретного action.

Для кого: OPERATOR — мониторинг и hold/cancel; PRICING_MANAGER — approve/reject; ADMIN — полный обзор и manual reconciliation.

### 4. Permissions

| Role | Доступ |
|------|--------|
| VIEWER | Просмотр таблицы (read-only) |
| ANALYST | Просмотр таблицы (read-only) |
| OPERATOR | Просмотр + hold, cancel, resume (из Detail Panel) |
| PRICING_MANAGER | Просмотр + approve, reject, retry + bulk approve |
| ADMIN / OWNER | Полный доступ, включая manual reconcile |

### 5. Layout

```
┌─────────────────────────────────────────────────────────────────────┐
│  Исполнение > Действия                                    [Export] │
├─────────────────────────────────────────────────────────────────────┤
│  [Подключение ▾] [Поиск оффера...] [Статус ▾] [Режим ▾] [Период] │
│  [● Ожидает ×] [● LIVE ×]                        [⊘ Сбросить всё]│
├─────────────────────────────────────────────────────────────────────┤
│  ☐ │ Оффер          │ Целевая │ Текущая │ Δ%    │ Статус    │ Реж │
│────┼────────────────┼─────────┼─────────┼───────┼───────────┼─────│
│  ☐ │ Кроссовки Nike │ 4 290₽  │ 4 590₽  │ ↓6,5% │ ● Ожидает │LIVE │
│    │ SKU-12345      │         │         │       │           │     │
│  ☐ │ Футболка Adid  │ 1 890₽  │ 1 790₽  │ ↑5,6% │ ● Выполн. │LIVE │
│    │ SKU-67890      │         │         │       │           │     │
│  ☐ │ Рюкзак Puma    │ 3 490₽  │ 3 490₽  │ → 0%  │ ● Ошибка  │ SIM │
│    │ SKU-11223      │         │         │       │           │     │
├─────────────────────────────────────────────────────────────────────┤
│  Показано 1–50 из 1 234       ◀ 1 2 3 ... 25 ▶    [50 ▾] на стр. │
└─────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│  3 выбрано  [Одобрить выбранные] [Отменить выбранные] [Export]  ×│  ← bulk action bar (bottom)
└──────────────────────────────────────────────────────────────────┘
```

### 6. Sections / components

| Секция | Компонент | Описание |
|--------|-----------|----------|
| Toolbar | Filter bar | Горизонтальная полоса с фильтрами-pills |
| Main | AG Grid | Основная таблица с данными |
| Bottom | Bulk action bar | Появляется при выборе ≥1 строки |
| Right (on click) | Detail Panel → Action Detail | Slide-in панель с деталями action |

### 7. Data source

**Primary API:**

```
GET /api/actions?connectionId={id}&status={s1,s2}&executionMode={mode}&from={date}&to={date}&search={query}&page={n}&size={50}&sort={field,direction}
```

| Параметр | Тип | Описание |
|----------|-----|----------|
| `connectionId` | Long | Фильтр по подключению маркетплейса |
| `status` | String (multi) | Comma-separated: `PENDING_APPROVAL,APPROVED,FAILED` |
| `executionMode` | Enum | `LIVE` / `SIMULATED` |
| `from` / `to` | LocalDate | Период создания (`created_at`) |
| `search` | String | Поиск по имени оффера, SKU, barcode (серверный ILIKE) |
| `page` / `size` | Int | Пагинация. Default: page=0, size=50 |
| `sort` | String | `createdAt,desc` (default), `targetPrice,asc`, `status,asc` |

**Response:** `Page<ActionSummaryResponse>` (Spring Page wrapper).

**TanStack Query config:**

| Параметр | Значение |
|----------|----------|
| `queryKey` | `['actions', workspaceId, filters]` |
| `staleTime` | 30s |
| `refetchInterval` | 60s (если вкладка активна) |
| `placeholderData` | `keepPreviousData` (при смене страницы/фильтров — старые данные видны пока грузятся новые) |

### 8. Data grid columns

| # | Column | Field | Width | Align | Font | Sortable | Описание |
|---|--------|-------|-------|-------|------|----------|----------|
| 0 | ☐ | — | 40px | center | — | — | Checkbox для bulk selection |
| 1 | Оффер | `offerName`, `sku` | 250px min | left | Inter | ✓ (по имени) | Две строки: имя оффера (bold), SKU под ним (`--text-secondary`, `--text-xs`). Frozen |
| 2 | Целевая цена | `targetPrice` | 110px | right | JetBrains Mono | ✓ | `4 290₽`. Цена, к которой стремится action |
| 3 | Текущая цена | `currentPriceAtCreation` | 110px | right | JetBrains Mono | ✓ | `4 590₽`. Цена на момент создания action |
| 4 | Δ цены | `priceDeltaPct` | 80px | right | JetBrains Mono | ✓ | `↓ 6,5%` (red) / `↑ 5,6%` (green) / `→ 0%` (gray). Процент изменения. Monospace + delta colors |
| 5 | Статус | `status` | 140px | left | Inter | ✓ | Status badge (dot + label). См. Status badge mapping |
| 6 | Режим | `executionMode` | 80px | center | Inter | ✓ | Badge: `LIVE` — default text, `SIM` — `--text-secondary` с пунктирной border |
| 7 | Попытки | `attemptCount` | 70px | center | JetBrains Mono | ✓ | `2/3`. attemptCount / maxAttempts |
| 8 | Создано | `createdAt` | 120px | left | Inter | ✓ (default desc) | Relative time: «12 мин назад», «вчера». Tooltip — absolute: «28 мар 2026, 14:32» |

**Execution mode badge рендеринг:**
- `LIVE` — текст без фона, стандартный `--text-primary`
- `SIMULATED` — текст `SIM`, `--text-secondary`, dashed border pill

**Δ цены рендеринг:**
- Положительная (цена растёт): `↑ 5,6%`, цвет `--finance-positive`
- Отрицательная (цена падает): `↓ 6,5%`, цвет `--finance-negative`
- Нулевая: `→ 0%`, цвет `--finance-zero`

### 9. Filters & search

| Фильтр | Тип | UI component | Default |
|--------|-----|-------------|---------|
| Подключение | Single select | Dropdown с иконкой маркетплейса (WB/Ozon) | Все |
| Поиск оффера | Text input | Inline search, debounce 300ms | — |
| Статус | Multi-select | Dropdown с чекбоксами, группировка: Активные / Терминальные | Все |
| Режим | Single select | Dropdown: Все / LIVE / Симуляция | Все |
| Период | Date range | Два date picker-а (от — до) | Последние 7 дней |

Фильтры отображаются как pills в filter bar. Каждый активный фильтр — `[Поле: Значение ×]`. Кнопка `[⊘ Сбросить всё]` — сброс на defaults.

Фильтры сохраняются в URL query params для bookmarkability: `?status=PENDING_APPROVAL,FAILED&mode=LIVE&from=2026-03-24&to=2026-03-31`.

### 10. Actions & buttons

**Toolbar actions:**

| Кнопка | Тип | Видимость | Описание |
|--------|-----|-----------|----------|
| Export | Ghost | Всегда | CSV-экспорт текущего отфильтрованного набора |
| Columns | Ghost (icon) | Всегда | Конфигурация видимости колонок |

**Bulk action bar** (появляется при выборе ≥1 строки):

| Кнопка | Тип | Видимость (role) | Описание |
|--------|-----|------------------|----------|
| Одобрить выбранные | Primary | PRICING_MANAGER+ | Bulk approve. Только для строк со статусом PENDING_APPROVAL |
| Отменить выбранные | Danger | OPERATOR+ | Bulk cancel. Только для строк в допустимых для cancel статусах |
| Export | Secondary | Все | CSV-экспорт выбранных строк |

**Bulk approve flow:**

1. Пользователь выбирает несколько строк чекбоксами
2. Нажимает «Одобрить выбранные»
3. Модальное окно подтверждения:
   ```
   ┌─ Подтверждение ──────────────────────────────────────┐
   │                                                       │
   │  Одобрить 5 действий?                                │
   │                                                       │
   │  Из выбранных 8 строк, 5 находятся в статусе         │
   │  «Ожидает» и будут одобрены. Остальные 3 будут       │
   │  пропущены (неподходящий статус).                     │
   │                                                       │
   │                      [Отмена]  [Одобрить 5]           │
   └───────────────────────────────────────────────────────┘
   ```
4. `POST /api/actions/bulk-approve` Body: `{ actionIds: [1, 2, 3, 4, 5] }`
5. Toast: «Одобрено: 5 действий» (success, 3s)
6. При partial failure (некоторые CAS conflict): Toast: «Одобрено: 3 из 5. 2 действия были изменены другим пользователем» (warning, 8s)

**Row click:** открывает Detail Panel (Action Detail) справа. Не навигация — Main Area сжимается, панель slide-in.

**Row double-click:** навигация к full-page Action Detail: `/workspace/:id/execution/actions/{actionId}`.

**Context menu (right-click на строке):**

| Пункт | Видимость |
|-------|-----------|
| Открыть в новой вкладке | Всегда |
| Одобрить | PRICING_MANAGER+, status = PENDING_APPROVAL |
| Приостановить | OPERATOR+, status = APPROVED |
| Отменить | OPERATOR+, status in cancel-eligible |
| Копировать SKU | Всегда |

### 11. Status badges & visual states

См. общий Status badge mapping в начале документа.

Дополнительная визуализация в таблице:
- Строки с `FAILED` — левый border 2px `--status-error`
- Строки с `PENDING_APPROVAL` — левый border 2px `--status-info` (акцент на ожидающих действия)
- Строки с `ON_HOLD` — фон `--bg-tertiary` (приглушённый, «замороженный» вид)

### 12. Forms & inputs

На экране Actions List нет форм. Все формы — в Detail Panel (Action Detail) и модальных окнах bulk actions.

### 13. Empty state

| Условие | Сообщение | Действие |
|---------|-----------|----------|
| Нет actions вообще (новый workspace) | «Пока нет действий. Действия появятся после первого запуска ценообразования.» | Ссылка: «Перейти к настройке ценообразования →» |
| Фильтры не дали результатов | «Нет действий, соответствующих фильтрам.» | Кнопка: `[Сбросить фильтры]` |
| Нет actions в статусе PENDING_APPROVAL (при фильтре) | «Нет действий, ожидающих одобрения.» | — |

### 14. Loading state

| Ситуация | Паттерн |
|----------|---------|
| Первая загрузка страницы | Skeleton shimmer на месте таблицы (5 строк-заглушек) |
| Смена фильтров / страницы | 2px progress bar сверху Main Area. Данные предыдущей страницы остаются видимыми (`keepPreviousData`) |
| Bulk approve в процессе | Кнопка «Одобрить» → spinner + disabled. Строки, для которых идёт approve, получают приглушённый фон |

### 15. Error handling

| Ошибка | Паттерн | Сообщение |
|--------|---------|-----------|
| API 500 при загрузке списка | Inline error в Main Area (вместо таблицы) | «Не удалось загрузить список действий. [Повторить]» |
| API 500 при bulk approve | Toast (error, 8s) | «Не удалось одобрить действия. Попробуйте позже.» |
| API 409 при bulk approve (partial) | Toast (warning, 8s) | «Одобрено: N из M. Часть действий была изменена другим пользователем.» |
| API 403 | Toast (error, 8s) | «У вас нет прав для выполнения этого действия.» |
| Network error | Persistent banner (yellow) | «Нет подключения к серверу. Данные могут быть устаревшими.» |

### 16. Real-time / WebSocket

| Событие | STOMP destination | Поведение UI |
|---------|------------------|-------------|
| Action status changed | `/topic/workspace.{id}.actions` | Строка в таблице обновляется in-place (badge, цвет). Background flash 1 раз (200ms pulse `--bg-active`) |
| New action created | `/topic/workspace.{id}.actions` | Новая строка появляется вверху (при сортировке по createdAt desc). Если пользователь на другой странице — counter badge на вкладке «Действия» |
| Action stuck-state alert | `/topic/workspace.{id}.alerts` | Notification bell badge +1. Toast (warning): «Действие #{id} застряло в статусе {status}» |

---

## Экран 2: Action Detail

### 1. Route

```
/workspace/:id/execution/actions/:actionId
```

Breadcrumb: `Исполнение > Действия > Кроссовки Nike (SKU-12345)`

Доступен двумя способами:
- **Detail Panel** — slide-in справа при клике на строку в Actions List. Панель, не full page.
- **Full page** — при double-click на строку или прямой навигации по URL / открытии в новой вкладке.

Контент идентичный в обоих режимах. Detail Panel использует compact layout (одна колонка, scroll). Full page — двухколоночный layout.

### 2. Phase

D — Execution.

### 3. Purpose

Полная информация о конкретном ценовом действии: текущий статус, визуализация state machine, история попыток, данные reconciliation, кнопки ручных вмешательств. Экран для расследования проблем (failed actions) и принятия решений (approve/reject).

### 4. Permissions

| Role | Доступ |
|------|--------|
| VIEWER | Просмотр всех данных (read-only) |
| ANALYST | Просмотр всех данных (read-only) |
| OPERATOR | Просмотр + Hold, Resume, Cancel |
| PRICING_MANAGER | Просмотр + Approve, Reject, Retry + всё от OPERATOR |
| ADMIN / OWNER | Полный доступ + Manual Reconcile |

### 5. Layout

**Full page layout:**

```
┌─────────────────────────────────────────────────────────────────────────┐
│  Исполнение > Действия > Кроссовки Nike (SKU-12345)                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─ State Machine ────────────────────────────────────────────────────┐ │
│  │                                                                     │ │
│  │   ○ Ожидает  →  ● Одобрено  →  ○ Запланир.  →  ○ Выполн.  →  ... │ │
│  │                     ↕                                               │ │
│  │                 ○ Приостан.                                          │ │
│  │                                                                     │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                         │
│  ┌─ Информация ──────────────────┐  ┌─ Действия ───────────────────┐  │
│  │ Оффер:    Кроссовки Nike      │  │                               │  │
│  │ SKU:      SKU-12345           │  │  [Приостановить]  [Отменить]  │  │
│  │ Подкл.:   WB Основной         │  │                               │  │
│  │ Целевая:  4 290₽              │  │                               │  │
│  │ Текущая:  4 590₽              │  │                               │  │
│  │ Δ цены:   ↓ 6,5%             │  │                               │  │
│  │ Режим:    LIVE                │  │                               │  │
│  │ Попытки:  1/3                 │  │                               │  │
│  │ Создано:  28 мар 2026, 14:32  │  │                               │  │
│  │ Обновлено: 28 мар, 14:35     │  │                               │  │
│  │ Одобрил:  Иван Петров        │  │                               │  │
│  └────────────────────────────────┘  └───────────────────────────────┘  │
│                                                                         │
│  ┌─ История попыток ──────────────────────────────────────────────────┐ │
│  │  # │ Начало          │ Результат  │ Ошибка       │ Reconcil. │ ... │ │
│  │  1 │ 28 мар, 14:33   │ ● Неопред. │ Read timeout │ Deferred  │     │ │
│  │  2 │ 28 мар, 14:38   │ ● Успех    │ —            │ Immediate │     │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                         │
│  ┌─ Hold/Cancel reason (если есть) ───────────────────────────────────┐ │
│  │  Причина приостановки: "Ожидаем подтверждения от поставщика"       │ │
│  │  Приостановил: Мария Иванова · 28 мар, 15:10                      │ │
│  └────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

**Detail Panel layout (compact):** те же секции, одна колонка, информация и действия объединены.

### 6. Sections / components

| Секция | Описание |
|--------|----------|
| State Machine | Горизонтальный pipeline. Визуализация текущего положения action в lifecycle |
| Информация | Key-value пары: оффер, цены, режим, timestamps, actors |
| Действия | Кнопки manual intervention. Набор зависит от status + role |
| История попыток | Таблица attempts. Раскрываемые строки с provider request/response |
| Причина (conditional) | Блок с hold_reason / cancel_reason / manual_override_reason. Показывается только если заполнен |

### 7. Data source

**Primary APIs:**

```
GET /api/actions/{actionId}
GET /api/actions/{actionId}/attempts
```

**Action detail response** включает все поля `price_action` + resolved offer name, SKU, connection name, approved_by user name.

**Attempts response:** список `price_action_attempt` с полями: attempt_number, started_at, completed_at, outcome, error_classification, error_message, reconciliation_source, reconciliation_read_at, actual_price, price_match.

**TanStack Query config:**

| Параметр | Значение |
|----------|----------|
| `queryKey` | `['action', actionId]` |
| `staleTime` | 10s |
| `refetchInterval` | 15s (для in-flight actions), disabled для terminal |

### 8. State machine visualization

Горизонтальный pipeline с узлами-кружками, соединёнными линиями. Текущее состояние — заполненный кружок с цветом статуса. Пройденные состояния — заполненные серым. Будущие — пустые контуры.

```
Main flow (горизонтальный):

  ○──────→ ○──────→ ○──────→ ○──────→ ○──────→ ◉
Ожидает  Одобрено Запланир. Выполн.  Проверка  Выполнено
                                        ↘
                                         ○ Ошибка

Branch states (ответвления вниз от соответствующих узлов):

  Ожидает ─┬→ ○ Истекло
           ├→ ○ Заменено
           └→ ○ Отменено

  Одобрено ─→ ○ Приостановлено ─→ (возврат к Одобрено)
            └→ ○ Отменено

  Выполн. ──→ ○ Повтор ──→ (возврат к Выполн.)

  Проверка ─→ ○ Отменено
```

**Рендеринг:**

| Элемент | Визуализация |
|---------|-------------|
| Пройденный узел | Filled circle, `--status-neutral` (gray) + label под ним |
| Текущий узел | Filled circle, цвет из Status badge mapping + label bold + glow ring (2px `--accent-primary` с opacity 0.3) |
| Будущий узел | Empty circle, `--border-default` + label `--text-tertiary` |
| Терминальный (success) | Filled circle, `--status-success` |
| Терминальный (error) | Filled circle, `--status-error` |
| Терминальный (archive) | Filled circle, `--status-neutral` |
| Соединительная линия (пройденная) | Solid, `--status-neutral` |
| Соединительная линия (будущая) | Dashed, `--border-default` |
| Branch line | Dashed, вертикально вниз от основного узла |

Компонент реализуется через SVG (не Canvas) для чёткости на любом масштабе. Ширина адаптируется под контейнер. При недостатке места — горизонтальный scroll.

### 9. Filters & search

На экране Action Detail нет фильтров. Фильтрация — на экране Actions List.

### 10. Actions & buttons — Manual interventions

Полная таблица ручных вмешательств с условиями, ролями, формами, API и паттернами подтверждения:

| Действие | Кнопка (label) | Тип кнопки | Status (from) | Status (to) | Min role | Form fields | API call | Confirmation |
|----------|---------------|------------|---------------|-------------|----------|-------------|----------|-------------|
| Approve | Одобрить | Primary | `PENDING_APPROVAL` | `APPROVED` → `SCHEDULED` | PRICING_MANAGER | — (simple button) | `POST /api/actions/{id}/approve` | Нет (non-destructive) |
| Reject | Отклонить | Danger | `PENDING_APPROVAL` | `CANCELLED` | PRICING_MANAGER | `cancelReason` (textarea, required, min 5 chars) | `POST /api/actions/{id}/reject` Body: `{ cancelReason }` | Modal: «Отклонить действие? Причина обязательна.» |
| Hold | Приостановить | Secondary | `APPROVED` | `ON_HOLD` | OPERATOR | `holdReason` (textarea, required, min 5 chars) | `POST /api/actions/{id}/hold` Body: `{ holdReason }` | Inline form (не modal) |
| Resume | Возобновить | Primary | `ON_HOLD` | `APPROVED` → `SCHEDULED` | OPERATOR | — (simple button) | `POST /api/actions/{id}/resume` | Нет (non-destructive) |
| Cancel | Отменить | Danger | `PENDING_APPROVAL`, `APPROVED`, `ON_HOLD`, `SCHEDULED`, `RETRY_SCHEDULED`, `RECONCILIATION_PENDING` | `CANCELLED` | OPERATOR | `cancelReason` (textarea, required, min 5 chars) | `POST /api/actions/{id}/cancel` Body: `{ cancelReason }` | Зависит от status (см. ниже) |
| Retry | Повторить | Primary | `FAILED` | Новый action | PRICING_MANAGER | `retryReason` (textarea, required, min 5 chars) | `POST /api/actions/{id}/retry` Body: `{ retryReason }` | Modal: «Создать повторное действие?» |
| Manual Reconcile | Подтвердить вручную | Secondary | `RECONCILIATION_PENDING` | `SUCCEEDED` или `FAILED` | ADMIN | `outcome` (select: Выполнено / Ошибка), `manualOverrideReason` (textarea, required, min 10 chars) | `POST /api/actions/{id}/reconcile` Body: `{ outcome, manualOverrideReason }` | Modal с type-to-confirm (см. ниже) |

**Cancel — вариации подтверждения по статусу:**

| Status при cancel | Confirmation pattern | Обоснование |
|-------------------|---------------------|-------------|
| `PENDING_APPROVAL`, `APPROVED`, `ON_HOLD` | Simple modal: «Отменить действие?» + form с cancelReason | Безопасно: provider call не выполнялся |
| `SCHEDULED` | Simple modal: «Отменить запланированное действие?» + form | Безопасно: provider call не выполнялся |
| `RETRY_SCHEDULED` | Simple modal: «Отменить повторную попытку?» + form | Безопасно: предыдущий call завершён |
| `RECONCILIATION_PENDING` | **Destructive modal** (type-to-confirm): «Внимание: действие могло быть уже применено маркетплейсом. Отмена прекратит проверку. Введите "ОТМЕНИТЬ" для подтверждения.» + form с cancelReason | Write мог быть применён — высокий risk |

**Manual Reconcile — modal:**

```
┌─ Ручное подтверждение ────────────────────────────────────┐
│                                                            │
│  ⚠ Внимание: ручное подтверждение перезаписывает           │
│  результат автоматической проверки.                        │
│                                                            │
│  Результат:                                                │
│  ○ Выполнено (цена применена)                             │
│  ○ Ошибка (цена не применена)                             │
│                                                            │
│  Причина: *                                                │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ Проверил вручную в кабинете WB — цена 4290₽ ...     │  │
│  └──────────────────────────────────────────────────────┘  │
│  Минимум 10 символов                                       │
│                                                            │
│  Введите "ПОДТВЕРДИТЬ" для продолжения:                    │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                                                      │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                            │
│                          [Отмена]  [Подтвердить вручную]   │
└────────────────────────────────────────────────────────────┘
```

**Условная видимость кнопок:**

Кнопки рендерятся **только** когда оба условия выполнены:
1. Action в допустимом статусе (status condition)
2. Пользователь имеет минимальную роль (role condition)

Если role недостаточна, но status подходит — кнопка **не показывается** (не disabled, а отсутствует). Это предотвращает frustration: пользователь не видит кнопку, которую не может нажать.

Исключение: если пользователь VIEWER/ANALYST, на месте блока «Действия» — текст: «Для управления действиями необходима роль Оператор или выше.»

### 11. Status badges & visual states

См. общий Status badge mapping. На этом экране badge показывается крупнее (16px, `--text-sm`), с текстовым пояснением:

| Status | Дополнительный контекст |
|--------|------------------------|
| `PENDING_APPROVAL` | «Ожидает одобрения оператором» |
| `APPROVED` | «Одобрено {approvedByName}, {approvedAt}» |
| `ON_HOLD` | «Приостановлено: {holdReason}» |
| `EXECUTING` | «Выполняется (попытка {attemptCount}/{maxAttempts})» |
| `RECONCILIATION_PENDING` | «Проверка результата...» |
| `RETRY_SCHEDULED` | «Повторная попытка: {nextAttemptAt}» |
| `FAILED` | «Ошибка: {lastErrorMessage}» |
| `SUCCEEDED` | «Выполнено ({reconciliationSource})» |

### 12. Forms & inputs

Все формы — inline или в модальных окнах (см. manual interventions в п.10). Общие правила:

- Textarea: min height 80px, max 200px, resize vertical
- Required fields: red asterisk + inline validation on blur
- Submit disabled пока required fields не заполнены
- Submit button показывает spinner при ожидании ответа

### 13. Empty state

| Секция | Условие | Сообщение |
|--------|---------|-----------|
| История попыток | Action только создан (PENDING_APPROVAL) | «Попыток исполнения пока не было.» |
| Причина hold/cancel | Не было hold/cancel | Секция не отображается |

### 14. Loading state

| Ситуация | Паттерн |
|----------|---------|
| Первая загрузка Detail | Skeleton: state machine (прямоугольник), info block (key-value shimmer), attempts (2 строки shimmer) |
| Refresh (action status changed) | 2px progress bar, данные остаются видимыми |
| Manual intervention в процессе | Кнопка → spinner + disabled. Остальные кнопки disabled |

### 15. Error handling

| Ошибка | Паттерн | Сообщение |
|--------|---------|-----------|
| API 404 (action не найден) | Full area message | «Действие не найдено. Возможно, оно было удалено.» + `[← К списку действий]` |
| API 409 (CAS conflict) | Toast (warning, 8s) + refresh data | «Действие было изменено другим пользователем. Статус обновлён.» — данные перезагружаются автоматически |
| API 409 при approve (concurrent) | Toast (warning, 8s) + refresh | «Действие уже одобрено другим пользователем.» — state machine обновляется |
| API 400 (validation) | Inline error под form field | Текст ошибки от backend |
| API 403 | Toast (error, 8s) | «У вас нет прав для выполнения этого действия.» |
| API 500 | Toast (error, 8s) | «Не удалось выполнить операцию. Попробуйте позже.» |

**CAS conflict (409) — подробное поведение:**

При HTTP 409 backend возвращает тело: `{ currentStatus, updatedAt, updatedBy }`.

UI:
1. Показать toast с информативным сообщением
2. Автоматически refetch action detail (invalidate TanStack Query)
3. State machine обновляется — текущий статус может быть другим
4. Кнопки actions пересчитываются для нового статуса
5. Если модальное окно было открыто (cancel form, reconcile form) — закрыть его

### 16. Real-time / WebSocket

| Событие | STOMP destination | Поведение UI |
|---------|------------------|-------------|
| Action status changed (this action) | `/topic/workspace.{id}.action.{actionId}` | State machine обновляется с анимацией перехода (200ms). Info block обновляется. Кнопки actions пересчитываются |
| New attempt added | `/topic/workspace.{id}.action.{actionId}` | Новая строка появляется в таблице attempts с highlight |
| Reconciliation completed | `/topic/workspace.{id}.action.{actionId}` | State machine → terminal state. Toast: «Проверка завершена: {outcome}» |

### Attempts table — detailed columns

| # | Column | Field | Width | Align | Font | Описание |
|---|--------|-------|-------|-------|------|----------|
| 1 | # | `attemptNumber` | 40px | center | JetBrains Mono | Номер попытки |
| 2 | Начало | `startedAt` | 130px | left | Inter | Absolute timestamp: «28 мар, 14:33» |
| 3 | Завершение | `completedAt` | 130px | left | Inter | Absolute timestamp или «—» если in-progress |
| 4 | Длительность | computed | 80px | right | JetBrains Mono | `completedAt - startedAt`: «4,2 сек» |
| 5 | Результат | `outcome` | 120px | left | Inter | Badge: ● Успех (green), ● Повтор (yellow), ● Ошибка (red), ● Неопред. (yellow) |
| 6 | Классификация | `errorClassification` | 150px | left | Inter | `--text-secondary`. «Rate limit», «Transient», «Timeout», «Невосстановимая» |
| 7 | Ошибка | `errorMessage` | flex | left | Inter | Truncated с tooltip. «—» если нет ошибки |
| 8 | Reconciliation | `reconciliationSource` | 100px | left | Inter | «Immediate», «Deferred», «Manual», «—» |

**Раскрытие строки (expand row):**

Клик на строку attempt → раскрывается вниз блок с подробностями:

```
┌─ Попытка #2 — подробности ──────────────────────────────────────────┐
│                                                                      │
│  Provider request:                                                   │
│  ┌──────────────────────────────────────────────────────────────────┐│
│  │ { "endpoint": "POST /api/v1/prices", "targetPrice": 4290,      ││
│  │   "offerId": "wb-12345", "marketplace": "WILDBERRIES" }         ││
│  └──────────────────────────────────────────────────────────────────┘│
│                                                                      │
│  Provider response:                                                  │
│  ┌──────────────────────────────────────────────────────────────────┐│
│  │ { "httpStatus": 200, "uploadId": "abc-123",                     ││
│  │   "errorText": null }                                            ││
│  └──────────────────────────────────────────────────────────────────┘│
│                                                                      │
│  Reconciliation:                                                     │
│  Источник: Deferred · Проверка: 28 мар, 14:38                       │
│  Фактическая цена: 4 290₽ · Совпадение: ✓ Да                       │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

Provider request/response — в mono-spaced code block. JSON formatted, syntax-highlighted (light theme). Сворачивается по умолчанию если > 5 строк.

---

## Экран 3: Bulk Approve

### 1. Route

Нет отдельного route. Bulk approve доступен из:
- Actions List (bulk action bar)
- Working Queues (Seller Operations module) — кнопка «Одобрить выбранные»

### 2. Phase

D — Execution.

### 3. Purpose

Массовое одобрение ценовых действий в статусе PENDING_APPROVAL. Позволяет PRICING_MANAGER+ обрабатывать очередь ожидающих actions одним действием, вместо поштучного approve.

### 4. Permissions

| Role | Доступ |
|------|--------|
| VIEWER, ANALYST, OPERATOR | Кнопка bulk approve **не отображается** |
| PRICING_MANAGER | Кнопка видна, доступна |
| ADMIN / OWNER | Кнопка видна, доступна |

### 5. Layout

Bulk approve реализуется как модальное окно поверх текущего экрана (Actions List или Working Queue):

```
┌─ Одобрить действия ───────────────────────────────────────┐
│                                                            │
│  Одобрить N действий?                                      │
│                                                            │
│  ┌────────────────────────────────────────────────────────┐│
│  │ Выбрано строк: 12                                      ││
│  │ Подходят для одобрения (Ожидает): 8                    ││
│  │ Будут пропущены (другой статус): 4                     ││
│  └────────────────────────────────────────────────────────┘│
│                                                            │
│  Список действий для одобрения:                            │
│  ┌────────────────────────────────────────────────────────┐│
│  │ Кроссовки Nike (SKU-12345)   4 290₽  ↓6,5%           ││
│  │ Футболка Adidas (SKU-67890)  1 890₽  ↑5,6%           ││
│  │ Рюкзак Puma (SKU-11223)      3 490₽  → 0%            ││
│  │ ... ещё 5                                              ││
│  └────────────────────────────────────────────────────────┘│
│                                                            │
│                          [Отмена]  [Одобрить 8 действий]   │
└────────────────────────────────────────────────────────────┘
```

### 6. Sections / components

| Секция | Описание |
|--------|----------|
| Summary | Количество выбранных, подходящих для одобрения, пропускаемых |
| Preview list | Compact список actions, которые будут одобрены (scrollable, max 5 видимых, остальные «ещё N») |
| Action buttons | Cancel + Primary confirm |

### 7. Data source

Данные для modal формируются клиентом из уже загруженных строк таблицы (Actions List). Фильтрация по `status = PENDING_APPROVAL` — client-side.

**Submit API:**

```
POST /api/actions/bulk-approve
Body: { "actionIds": [1, 2, 3, 4, 5, 6, 7, 8] }
```

**Response:**

```json
{
  "approved": 6,
  "failed": 2,
  "failures": [
    { "actionId": 3, "reason": "CAS_CONFLICT", "currentStatus": "APPROVED" },
    { "actionId": 7, "reason": "CAS_CONFLICT", "currentStatus": "CANCELLED" }
  ]
}
```

### 8. Data grid columns

Preview list в modal (не full grid, compact):

| Column | Описание |
|--------|----------|
| Оффер | Имя + SKU (одна строка, truncated) |
| Целевая цена | Monospace |
| Δ цены | С delta color |

### 9. Filters & search

Нет фильтров. Набор actions определяется selection в родительской таблице.

### 10. Actions & buttons

| Кнопка | Тип | Поведение |
|--------|-----|-----------|
| Отмена | Secondary | Закрыть modal, ничего не делать |
| Одобрить N действий | Primary | Submit → spinner → toast → close |

Кнопка primary disabled до тех пор, пока count подходящих для одобрения = 0.

### 11. Status badges & visual states

В preview list — нет badges (все строки в одном статусе PENDING_APPROVAL). В summary — число пропускаемых с пометкой статуса.

### 12. Forms & inputs

Нет form fields. Bulk approve — простое подтверждение без дополнительных данных.

### 13. Empty state

| Условие | Сообщение | Действие |
|---------|-----------|----------|
| Ни одна из выбранных строк не в PENDING_APPROVAL | «Среди выбранных нет действий, ожидающих одобрения.» | Кнопка primary скрыта. Только «Закрыть» |

### 14. Loading state

| Ситуация | Паттерн |
|----------|---------|
| Bulk approve в процессе | Кнопка «Одобрить» → spinner, disabled. Кнопка «Отмена» disabled. Modal не закрывается |
| Завершение | Modal закрывается автоматически. Toast с результатом |

### 15. Error handling

| Ошибка | Паттерн | Сообщение |
|--------|---------|-----------|
| Все succeed | Toast (success, 3s) | «Одобрено: 8 действий» |
| Partial success | Toast (warning, 8s) | «Одобрено: 6 из 8. 2 действия были изменены другим пользователем.» |
| Все failed (CAS conflict) | Toast (error, 8s) | «Не удалось одобрить: все действия были изменены другим пользователем.» |
| API 500 | Toast (error, 8s) | «Ошибка сервера при массовом одобрении. Попробуйте позже.» |
| API 403 | Toast (error, 8s) | «У вас нет прав для одобрения действий.» |

После любого результата — Actions List refetch (invalidate query).

### 16. Real-time / WebSocket

Нет специфического WebSocket-поведения для modal. После submit — Actions List обновляется через refetch. Параллельные WebSocket events от других пользователей приходят в Actions List (но не в modal — modal уже закрыт к моменту обработки).

---

## Экран 4: Simulation Comparison

### 1. Route

```
/workspace/:id/execution/simulation
```

Breadcrumb: `Исполнение > Симуляция`

### 2. Phase

F — Simulation. Вкладка «Симуляция» появляется в Activity Bar → Execution **только** после Phase F.

### 3. Purpose

Сравнение результатов симулированного ценообразования с текущими ценами. Отвечает на вопрос: «Какие ценовые изменения произошли бы, если бы стратегия работала в реальном режиме?» Помогает оператору принять решение о переключении policy с SIMULATED на SEMI_AUTO.

### 4. Permissions

| Role | Доступ |
|------|--------|
| VIEWER | Просмотр KPI и данных (read-only) |
| ANALYST | Просмотр KPI и данных (read-only) |
| OPERATOR | Просмотр (read-only) |
| PRICING_MANAGER | Просмотр + Reset shadow-state |
| ADMIN / OWNER | Полный доступ + Reset shadow-state |

### 5. Layout

```
┌─────────────────────────────────────────────────────────────────────────┐
│  Исполнение > Симуляция                                     [Подкл. ▾] │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌────────────┐  │
│  │ Сим. действий │  │ Ср. Δ цены   │  │ Направление  │  │ Покрытие   │  │
│  │    1 234      │  │   ↓ 4,2%     │  │ ↑ 45% ↓ 38%  │  │   72%      │  │
│  │              │  │              │  │ → 17%        │  │ офферов   │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  └────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │ Предполагаемое влияние на маржу                                  │   │
│  │                                                                  │   │
│  │  ┌─ bar chart: margin impact per connection ─────────────────┐  │   │
│  │  │  WB Основной    ████████████  +12 340₽                    │  │   │
│  │  │  Ozon Бренд     ███████       +6 780₽                     │  │   │
│  │  │  WB Outlet      ██            −1 200₽                     │  │   │
│  │  └───────────────────────────────────────────────────────────┘  │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │ Распределение ценовых изменений                                  │   │
│  │                                                                  │   │
│  │  ┌─ histogram: price delta % distribution ───────────────────┐  │   │
│  │  │       ▂                                                    │  │   │
│  │  │      ▅█▅                                                   │  │   │
│  │  │    ▃▇███▇▃                                                 │  │   │
│  │  │  ▂▅████████▅▂                                              │  │   │
│  │  │  ─────┼─────                                               │  │   │
│  │  │ -20% -10%  0%  +10% +20%                                   │  │   │
│  │  └────────────────────────────────────────────────────────────┘  │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │ Danger zone                                                      │   │
│  │ [Сбросить shadow-state]  ← destructive, type-to-confirm         │   │
│  └──────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
```

### 6. Sections / components

| Секция | Компонент | Описание |
|--------|-----------|----------|
| KPI strip | 4 карточки (max) | Summary метрики симуляции |
| Margin impact chart | ngx-echarts bar chart | Влияние на маржу per connection. Horizontal bars, green/red |
| Distribution chart | ngx-echarts histogram | Распределение Δ цены по бакетам |
| Danger zone | Destructive action block | Reset shadow-state. Визуально отделён, red border |

### 7. Data source

**Primary API:**

```
GET /api/simulation/comparison?connectionId={id}
```

**Response:**

```json
{
  "simulatedActionsCount": 1234,
  "averagePriceDeltaPct": -4.2,
  "directionDistribution": {
    "up": 0.45,
    "down": 0.38,
    "unchanged": 0.17
  },
  "coveragePct": 0.72,
  "totalOffers": 5600,
  "coveredOffers": 4032,
  "estimatedMarginImpact": 17920.00,
  "perConnectionBreakdown": [
    {
      "connectionId": 1,
      "connectionName": "WB Основной",
      "marketplace": "WILDBERRIES",
      "simulatedCount": 800,
      "avgDeltaPct": -3.1,
      "marginImpact": 12340.00
    }
  ],
  "deltaDistribution": [
    { "bucket": "-20%", "count": 12 },
    { "bucket": "-15%", "count": 34 },
    { "bucket": "-10%", "count": 89 },
    { "bucket": "-5%", "count": 234 },
    { "bucket": "0%", "count": 210 },
    { "bucket": "+5%", "count": 312 },
    { "bucket": "+10%", "count": 198 },
    { "bucket": "+15%", "count": 89 },
    { "bucket": "+20%", "count": 56 }
  ]
}
```

**TanStack Query config:**

| Параметр | Значение |
|----------|----------|
| `queryKey` | `['simulation-comparison', workspaceId, connectionId]` |
| `staleTime` | 5min (comparison — on-demand, не real-time) |
| `refetchOnWindowFocus` | true |

### 8. KPI cards

| Карточка | Значение | Формат | Подпись |
|----------|----------|--------|--------|
| Сим. действий | `simulatedActionsCount` | `1 234` (monospace) | Успешных симулированных действий |
| Ср. Δ цены | `averagePriceDeltaPct` | `↓ 4,2%` (с delta color) | Среднее изменение цены |
| Направление | `directionDistribution` | `↑ 45%  ↓ 38%  → 17%` (три значения, colored) | Доля повышений / понижений / без изменений |
| Покрытие | `coveragePct` | `72%` (monospace) | `{coveredOffers} из {totalOffers} офферов` |

KPI cards — 4 в ряд, compact. Без trend arrows (нет временного сравнения в Phase F).

### 9. Filters & search

| Фильтр | Тип | UI component | Default |
|--------|-----|-------------|---------|
| Подключение | Single select | Dropdown с иконкой маркетплейса | Все |

Один фильтр. Переключение подключения — refetch comparison data.

### 10. Actions & buttons

| Кнопка | Тип | Видимость (role) | Описание |
|--------|-----|------------------|----------|
| Сбросить shadow-state | Danger | PRICING_MANAGER+ | Очистка всех симулированных данных для выбранного подключения |

**Reset shadow-state flow:**

1. Пользователь выбирает подключение (или «Все»)
2. Нажимает «Сбросить shadow-state»
3. **Type-to-confirm modal:**

```
┌─ Сброс симуляции ─────────────────────────────────────────┐
│                                                            │
│  ⚠ Внимание: все симулированные данные для подключения     │
│  "WB Основной" будут удалены безвозвратно.                 │
│                                                            │
│  Это действие:                                             │
│  • Удалит shadow-state для всех офферов                    │
│  • Обнулит метрики сравнения                               │
│  • Не повлияет на реальные цены                            │
│                                                            │
│  Введите "СБРОСИТЬ" для подтверждения:                     │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                                                      │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                            │
│                          [Отмена]  [Сбросить]              │
└────────────────────────────────────────────────────────────┘
```

4. `DELETE /api/simulation/shadow-state` Body: `{ connectionId }` (или без body для all connections)
5. Toast: «Shadow-state сброшен для "WB Основной"» (success, 3s)
6. KPI cards и charts обновляются (все нули / пустые)

### 11. Status badges & visual states

На этом экране нет action-статус badges. Visual states:

| Элемент | Визуализация |
|---------|-------------|
| Margin impact positive | `--finance-positive` (green bar + text) |
| Margin impact negative | `--finance-negative` (red bar + text) |
| Coverage < 50% | KPI card border `--status-warning` (yellow), tooltip: «Низкое покрытие: симуляция может быть нерепрезентативной» |
| No simulation data | Все KPI = 0, charts пустые (см. Empty state) |

### 12. Forms & inputs

Нет форм, кроме type-to-confirm в reset modal (см. п.10).

### 13. Empty state

| Условие | Сообщение | Действие |
|---------|-----------|----------|
| Нет подключений с SIMULATED policies | «Нет подключений в режиме симуляции. Переключите pricing policy в режим "Симуляция" для начала.» | Ссылка: «Перейти к настройке ценообразования →» |
| Есть SIMULATED policies, но нет completed actions | «Симуляция запущена, но результатов пока нет. Данные появятся после первого запуска ценообразования.» | — |
| Shadow-state был сброшен | «Shadow-state сброшен. Новые данные появятся после следующего запуска ценообразования.» | — |

### 14. Loading state

| Ситуация | Паттерн |
|----------|---------|
| Первая загрузка | Skeleton: 4 KPI cards (shimmer), chart placeholders (gray rectangles) |
| Смена фильтра подключения | 2px progress bar. KPI и charts fade-out 50% opacity → fade-in с новыми данными |

### 15. Error handling

| Ошибка | Паттерн | Сообщение |
|--------|---------|-----------|
| API 500 при загрузке comparison | Inline error вместо charts | «Не удалось загрузить данные симуляции. [Повторить]» |
| API 500 при reset shadow-state | Toast (error, 8s) | «Не удалось сбросить shadow-state. Попробуйте позже.» |
| API 403 при reset | Toast (error, 8s) | «У вас нет прав для сброса shadow-state.» |

### 16. Real-time / WebSocket

| Событие | STOMP destination | Поведение UI |
|---------|------------------|-------------|
| Simulated action completed | — | Нет real-time обновления. Данные обновляются при refetch (staleTime 5min). Достаточно для Phase F — simulation comparison не операционный экран |

---

## User flow scenarios

### Сценарий 1: Одобрение действия (PRICING_MANAGER)

**Контекст:** Pricing pipeline создал 12 ценовых действий в режиме SEMI_AUTO. Все в статусе PENDING_APPROVAL. Pricing Manager Анна открывает Datapulse для утренней обработки очереди.

**Поток:**

1. Анна переходит в Activity Bar → Исполнение → вкладка «Действия»
2. Устанавливает фильтр: Статус = «Ожидает», Режим = «LIVE»
3. Видит 12 строк с синими badges «Ожидает»
4. Кликает на первую строку (Кроссовки Nike) → Detail Panel открывается справа
5. Видит state machine: текущий узел «Ожидает» (blue, highlighted)
6. Видит информацию: Целевая 4 290₽, Текущая 4 590₽, Δ ↓6,5%
7. Кнопка «Одобрить» (Primary) видна, т.к. status = PENDING_APPROVAL и role = PRICING_MANAGER
8. Нажимает «Одобрить» → нет modal (non-destructive) → Toast: «Действие одобрено»
9. State machine анимирует переход: Ожидает → Одобрено → Запланировано (мгновенный каскад)
10. Кнопка «Одобрить» исчезает. Появляются «Приостановить» и «Отменить»
11. Закрывает Detail Panel (×)
12. Строка в таблице обновилась: badge «Запланировано» (blue)
13. Для оставшихся 11 — выбирает все чекбоксами → bulk action bar → «Одобрить выбранные»
14. Modal: «Одобрить 11 действий?» → Preview list с ценами → «Одобрить 11 действий»
15. Toast: «Одобрено: 11 действий» (success, 3s)
16. Таблица обновляется: все 12 строк теперь «Запланировано» или «Выполняется»

### Сценарий 2: Расследование failed action (OPERATOR/ADMIN)

**Контекст:** Оператор Михаил получил alert: «Действие #4521 застряло в статусе RECONCILIATION_PENDING более 10 минут». Он переходит к расследованию.

**Поток:**

1. Михаил получает notification (bell icon +1) или WebSocket toast
2. Кликает на notification → навигация к `/workspace/1/execution/actions/4521`
3. Видит full-page Action Detail
4. State machine: текущий узел «Проверка» (yellow, highlighted)
5. Смотрит в блок «Информация»: Оффер «Рюкзак Puma», Целевая 3 490₽, Попытки 1/3
6. Открывает «Историю попыток»:
   - Attempt #1: started 14:33, outcome «Неопред.», error «Read timeout после отправки запроса», reconciliation «Deferred»
7. Раскрывает строку attempt → видит provider request/response:
   - Request: `POST /api/v1/prices`, targetPrice: 3490, offerId: "wb-98765"
   - Response: `{ "httpStatus": null, "error": "SocketTimeoutException" }`
   - Reconciliation: source=DEFERRED, read_at=14:38, actual_price=3 490₽, price_match=true
8. Видит: reconciliation прочитала цену 3 490₽ — совпадает с target. Но CAS не перевёл в SUCCEEDED (stuck-state detector уже пометил FAILED)
9. Если Михаил — ADMIN, видит кнопку «Подтвердить вручную»
10. Нажимает → modal: outcome = «Выполнено», reason = «Deferred reconciliation подтвердила цену 3490₽ — совпадение. Stuck-state detector ложно перевёл в FAILED.»
11. Вводит "ПОДТВЕРДИТЬ" → Submit
12. `POST /api/actions/4521/reconcile` Body: `{ outcome: "SUCCEEDED", manualOverrideReason: "..." }`
13. State machine → «Выполнено» (green). Toast: «Действие подтверждено вручную»

**Альтернативный путь (CAS conflict):**

- На шаге 12, другой admin уже reconcile'ил action
- API возвращает 409: `{ currentStatus: "SUCCEEDED", updatedBy: "Иван Петров" }`
- Modal закрывается. Toast (warning): «Действие уже подтверждено пользователем Иван Петров»
- State machine обновляется до SUCCEEDED

### Сценарий 3: Анализ симуляции и решение о переключении (PRICING_MANAGER)

**Контекст:** Pricing Manager Елена настроила стратегию ценообразования для подключения «WB Основной» в режиме SIMULATED. Прошла неделя, симуляция накопила данные. Елена хочет оценить результаты перед переключением на SEMI_AUTO.

**Поток:**

1. Елена переходит в Activity Bar → Исполнение → вкладка «Симуляция»
2. Выбирает подключение: «WB Основной»
3. Видит KPI cards:
   - Сим. действий: 823
   - Ср. Δ цены: ↓ 3,1%
   - Направление: ↑ 42% ↓ 41% → 17%
   - Покрытие: 89% (710 из 800 офферов)
4. Смотрит Margin Impact chart: +12 340₽ (green bar) — симуляция показывает рост маржи
5. Смотрит Distribution chart: нормальное распределение, основная масса изменений в диапазоне ±10%
6. Оценивает: покрытие высокое (89%), направление сбалансированное, маржа растёт → решает переключить
7. Переходит в Settings → Pricing Policies → «WB Основной: Стратегия X»
8. Меняет execution_mode: SIMULATED → SEMI_AUTO
9. Возвращается в Исполнение → Действия
10. Фильтр: Статус = «Ожидает», Режим = «LIVE»
11. Видит первые LIVE actions с PENDING_APPROVAL
12. Начинает approving по одному (осторожный старт)

**Если Елена хочет сбросить старые данные симуляции:**

1. На экране Симуляция → scroll вниз до «Danger zone»
2. Кнопка «Сбросить shadow-state» (красная)
3. Type-to-confirm modal: вводит "СБРОСИТЬ"
4. `DELETE /api/simulation/shadow-state` Body: `{ connectionId: 1 }`
5. KPI cards обнуляются. Charts пустые
6. Toast: «Shadow-state сброшен для "WB Основной"»

---

## Edge cases

### CAS conflict (HTTP 409)

**Ситуация:** Два пользователя одновременно пытаются approve один и тот же action.

**Поведение:**

1. Пользователь A нажимает «Одобрить» → `POST /api/actions/123/approve`
2. Пользователь B нажимает «Одобрить» → `POST /api/actions/123/approve`
3. Сервер: CAS `WHERE status = 'PENDING_APPROVAL'` → один succeeds, один fails
4. Пользователь A: Toast «Действие одобрено» (success) → state machine обновлён
5. Пользователь B: API 409 → Toast «Действие уже одобрено другим пользователем» (warning, 8s) → auto-refetch → state machine обновлён до APPROVED/SCHEDULED

**В bulk approve:**

- Часть actionIds может вернуться с CAS conflict
- Response содержит `failures[]` с per-action причинами
- Toast: «Одобрено: N из M. K действий были изменены.»

### Concurrent approve + cancel

**Ситуация:** PRICING_MANAGER approve'ит action, одновременно OPERATOR cancel'ит его.

- Один из CAS-переходов проваливается (race condition resolved by DB)
- Проигравший получает 409 с текущим status
- UI обоих пользователей обновляется через WebSocket event

### Stuck-state alerts

**Ситуация:** Action в EXECUTING более 5 минут (stuck-state detector переводит в RECONCILIATION_PENDING + alert).

**Поведение в UI:**

1. WebSocket event: action status changed → строка в таблице обновляется
2. Notification bell: +1, текст: «Действие #4521 (Рюкзак Puma) застряло — переведено в проверку»
3. Toast (warning, 8s): «Действие #4521 застряло в статусе "Выполняется" — запущена проверка»
4. В Action Detail: state machine показывает переход EXECUTING → RECONCILIATION_PENDING. Timestamp обновления — moment перехода

**Для ADMIN:** появляется кнопка «Подтвердить вручную» для принудительного завершения.

### Action expired

**Ситуация:** Action в PENDING_APPROVAL дольше `approval_timeout_hours` (scheduled job переводит в EXPIRED).

**Поведение в UI:**

1. Строка в таблице: badge «Истекло» (gray dot)
2. В Action Detail: state machine → terminal node «Истекло» (gray)
3. Нет доступных actions (terminal state)
4. Info block: «Истекло: превышен таймаут одобрения ({approval_timeout_hours}ч)»

### Superseded action

**Ситуация:** Новый pricing run создал fresh decision для того же оффера. Старый action (PENDING_APPROVAL/APPROVED) → SUPERSEDED.

**Поведение в UI:**

1. Строка старого action: badge «Заменено» (gray dot)
2. Info block содержит ссылку: «Заменено действием #{supersedingActionId}» — кликабельная навигация
3. Нет доступных actions (terminal state)

---

## Accessibility notes

- State machine visualization: каждый узел имеет `aria-label` с полным описанием: «Статус: Одобрено. Шаг 2 из 6. Текущий статус.»
- Status badges: `aria-label` = label text (не только color)
- Confirmation modals: focus trap, Escape закрывает, auto-focus на primary button (или cancel для destructive)
- Bulk action bar: `aria-live="polite"` для объявления количества выбранных строк
- Charts: `aria-label` с текстовой альтернативой (summary value + trend description)

---

## Related documents

- [Frontend Design Direction](frontend-design-direction.md) — design system, component patterns, interaction patterns
- [Execution module](../modules/execution.md) — state machine, CAS guards, reconciliation, simulation, REST API
- [Tenancy & IAM](../modules/tenancy-iam.md) — roles, permission matrix
- [Seller Operations](../modules/seller-operations.md) — working queues (источник bulk approve)
- [Pricing](../modules/pricing.md) — policy execution_mode (SIMULATED/SEMI_AUTO/FULL_AUTO)
- [Audit & Alerting](../modules/audit-alerting.md) — alert events, notification delivery
