# Pages: Alerts & Notifications

**Фаза:** B (Alerting foundation — alert events, automation blockers, toast notifications, WebSocket), E (notification bell, notification page, alert rule management UI)
**Модуль-владелец:** [Audit & Alerting](../modules/audit-alerting.md)
**Связанные модули:** [Integration](../modules/integration.md) (connection health), [ETL Pipeline](../modules/etl-pipeline.md) (sync status), [Execution](../modules/execution.md) (action lifecycle), [Pricing](../modules/pricing.md) (automation blocker)

---

## Обзор

Спецификация описывает все UI-компоненты, связанные с алертингом и уведомлениями: от глобальных элементов (bell, toast, automation banner), присутствующих на каждом экране, до отдельных страниц (Notifications, Alert Events). Компоненты организованы по слоям видимости:

| Слой | Компонент | Расположение | Видимость |
|------|-----------|-------------|-----------|
| Global overlay | Toast Notifications | Нижний правый угол viewport | Поверх всего контента |
| Global banner | Automation Blocker Banner | Под Top Bar, над Main Area | Пока условие активно |
| Top Bar element | Notification Bell & Dropdown | Top Bar, правая часть | Всегда (bell), по клику (dropdown) |
| Page | Notifications Page | `/workspace/:id/notifications` | Отдельная страница |
| Page | Alert Events Page | `/workspace/:id/alerts` | Отдельная страница |
| Cross-cutting | WebSocket Integration | Невидимый слой | Фоновый транспорт |

---

## 1. Notification Bell & Dropdown

### 1.1 Расположение и роль

Bell — элемент Top Bar, расположен справа, между глобальным поиском (Ctrl+K) и аватаром пользователя. Это точка входа в систему уведомлений — компактный способ увидеть последние события без покидания текущего контекста.

### 1.2 Визуальная структура

```
Top Bar:
┌──────────────────────────────────────────────────────────────────────┐
│  [Logo] [Workspace ▾]  Модуль > Вид > ...    🔍 Ctrl+K   🔔⁹⁹⁺  👤 │
└──────────────────────────────────────────────────────────────────────┘
                                                 ↑
                                          Bell + Badge
```

**Bell icon:**
- Lucide icon: `bell` (24×24), цвет `--text-secondary` (#6B7280)
- При наличии unread: icon сдвигается на 2px влево, чтобы badge не обрезался
- Hover: `--text-primary` (#111827)
- Active (dropdown открыт): `--accent-primary` (#2563EB), фон `--accent-subtle` (#EFF6FF), border-radius 6px

**Badge (unread count):**
- Позиция: top-right от bell icon, смещение -4px top, -6px right
- Форма: pill shape (border-radius 999px)
- Фон: `--status-error` (#DC2626), текст: white, font: Inter 10px/600
- Значения: `1`..`99` — точное число; `99+` при ≥100
- Размеры: min-width 16px (одна цифра), 22px (две цифры), 28px (99+), height 16px
- Анимация появления: scale 0→1 за 200ms ease-out (когда badge появляется из нулевого состояния)
- Без badge: при 0 непрочитанных — badge полностью скрыт (не показывать «0»)

### 1.3 Dropdown Panel

Открывается по клику на bell. Клик повторно или вне области — закрывает.

```
                                        ┌─────────────────────────────────┐
                                        │ Уведомления           [✓ все]  │
                                        ├─────────────────────────────────┤
                                        │ 🔴 Критический алерт           │
                                        │    Данные Ozon устарели на 26ч  │
                                        │    5 мин назад            •     │
                                        ├ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─┤
                                        │ 🟡 Предупреждение              │
                                        │    Spike обнаружен: логистика   │
                                        │    12 мин назад                 │
                                        ├ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─┤
                                        │ 🔵 Синхронизация завершена      │
                                        │    WB Sales: 1 234 записи      │
                                        │    1 ч назад                    │
                                        ├ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─┤
                                        │        ... (scroll) ...         │
                                        ├─────────────────────────────────┤
                                        │       Показать все →            │
                                        └─────────────────────────────────┘
```

**Размеры:**
- Ширина: 360px (фиксированная)
- Максимальная высота: 480px (включая header и footer)
- Область списка: scrollable, max ~400px
- Позиционирование: Angular CDK Overlay, привязка к bell icon, выравнивание по правому краю, offset-y 8px вниз

**Header:**
- Текст: «Уведомления», font `--text-lg` (16px/600), цвет `--text-primary`
- Кнопка «✓ все» (ghost button): текст «Отметить все как прочитанные», Lucide icon `check-check` (16×16) + текст 13px
- Кнопка видна только при наличии непрочитанных
- По клику: `POST /api/notifications/read-all` → все items визуально становятся «read», badge обнуляется

**Footer:**
- Разделитель: 1px `--border-default`
- Ссылка: «Показать все» → router navigate to `/workspace/:id/notifications`
- Стиль: центрированный текст, `--accent-primary`, font 13px, padding 10px
- Hover: `--bg-tertiary` фон

### 1.4 Notification Item (внутри dropdown)

Каждый элемент списка — интерактивная строка:

```
┌─[Severity Icon]─[Content]──────────────────────[Time]──┐
│  ●               Title (1 line, truncate)     5 мин назад│
│                  Body preview (1 line, trunc)           │
└─────────────────────────────────────────────────────────┘
```

**Поля:**

| Поле | Источник | Отображение |
|------|---------|-------------|
| Severity icon | `notification.severity` | Цветная точка 8px: CRITICAL=#DC2626, WARNING=#D97706, INFO=#2563EB |
| Title | `notification.title` | `--text-sm` (13px/500), `--text-primary`, 1 строка, text-overflow: ellipsis |
| Body preview | `notification.body` | `--text-xs` (11px/400), `--text-secondary`, 1 строка, text-overflow: ellipsis |
| Timestamp | `notification.created_at` | `--text-xs` (11px/400), `--text-tertiary`, relative format |
| Unread indicator | `notification.read_at === null` | Точка 6px `--accent-primary` справа от timestamp |

**Состояния строки:**

| Состояние | Фон | Шрифт title |
|-----------|-----|-------------|
| Unread | `--bg-secondary` (#F9FAFB) | font-weight: 600 |
| Read | `--bg-primary` (#FFFFFF) | font-weight: 400 |
| Hover | `--bg-tertiary` (#F3F4F6) | без изменений |

**Высота строки:** auto, padding 10px 12px, min-height 56px.

**Разделитель:** 1px `--border-subtle` между items.

**Интерактивность:**
- Клик по item → `POST /api/notifications/{id}/read` → помечается прочитанным → навигация к source entity (если есть контекст: connection settings, alert detail, action в execution queue)
- Определение target route по `notification_type`:
  - `ALERT` → `/workspace/:id/alerts` (с фильтром по alert_event_id)
  - `SYNC_COMPLETED` → `/workspace/:id/settings/connections/:connectionId`
  - `ACTION_FAILED` → `/workspace/:id/execution` (с фильтром по action_id)
  - `APPROVAL_REQUEST` → `/workspace/:id/execution?status=PENDING_APPROVAL`

### 1.5 Данные и API

**Начальная загрузка (при рендере shell):**
1. `GET /api/notifications/unread-count` → число для badge
2. Подписка на WebSocket `/user/queue/notifications` → real-time push новых уведомлений

**При открытии dropdown:**
3. `GET /api/notifications?limit=20` → последние 20 (и read, и unread), sorted by `created_at DESC`

**Обновление badge:**
- WebSocket message с новым notification → badge count +1 (optimistic, без повторного GET)
- `POST /api/notifications/{id}/read` → badge count -1
- `POST /api/notifications/read-all` → badge count = 0
- Fallback: если WebSocket disconnected, при reconnect → `GET /api/notifications/unread-count` для sync

**Формат WebSocket message (`/user/queue/notifications`):**
```json
{
  "notificationId": 42,
  "notificationType": "ALERT",
  "title": "Данные Ozon устарели на 26 ч",
  "body": "Finance sync не выполнялся более 24 ч для подключения Ozon Main",
  "severity": "CRITICAL",
  "createdAt": "2026-03-31T12:05:00Z"
}
```

### 1.6 Типографика

| Элемент | Font | Size | Weight | Color token |
|---------|------|------|--------|-------------|
| Header «Уведомления» | Inter | 16px | 600 | `--text-primary` |
| «Отметить все» button | Inter | 13px | 400 | `--accent-primary` |
| Item title (unread) | Inter | 13px | 600 | `--text-primary` |
| Item title (read) | Inter | 13px | 400 | `--text-primary` |
| Item body | Inter | 11px | 400 | `--text-secondary` |
| Timestamp | Inter | 11px | 400 | `--text-tertiary` |
| Footer «Показать все» | Inter | 13px | 500 | `--accent-primary` |

### 1.7 Отступы и размеры

| Элемент | Значение |
|---------|---------|
| Dropdown width | 360px |
| Dropdown max-height | 480px |
| Dropdown border-radius | `--radius-lg` (8px) |
| Dropdown shadow | `--shadow-md` (0 4px 12px rgba(0,0,0,0.08)) |
| Dropdown border | 1px `--border-default` |
| Header padding | 12px 16px |
| Item padding | 10px 12px |
| Severity dot size | 8px |
| Unread indicator dot | 6px |
| Gap: severity dot → content | 10px |
| Footer padding | 10px 16px |

### 1.8 Поведение и интерактивность

- Dropdown открывается/закрывается по клику на bell (toggle)
- Клик вне dropdown → закрытие (CDK Overlay backdrop, прозрачный)
- Escape → закрытие
- Scroll внутри dropdown: нативный overflow-y: auto, тонкий scrollbar (4px width)
- При поступлении нового notification, если dropdown открыт → item появляется сверху списка с fade-in анимацией (200ms)
- Если dropdown закрыт → только badge increment

### 1.9 Responsive поведение

- ≥1440px: dropdown выравнивается по правому краю bell icon
- 1280–1440px: то же самое, dropdown может упираться в правый край viewport → CDK Overlay автоматически сдвинет влево
- <1280px: не поддерживается (см. frontend-design-direction.md)

### 1.10 Состояния

| Состояние | Отображение |
|-----------|-------------|
| 0 уведомлений | Badge скрыт. Dropdown при открытии: пустое состояние (см. 1.11) |
| 1-99 unread | Badge с точным числом |
| 100+ unread | Badge «99+» |
| Загрузка dropdown | Skeleton: 3 строки shimmer (height 56px каждая) |
| Ошибка загрузки | Текст: «Не удалось загрузить уведомления. Повторить?» + ghost button «Повторить» |

### 1.11 Пустое состояние

```
┌─────────────────────────────────┐
│ Уведомления                     │
├─────────────────────────────────┤
│                                 │
│     Нет новых уведомлений       │
│                                 │
│     События будут появляться    │
│     здесь по мере работы        │
│     системы                     │
│                                 │
├─────────────────────────────────┤
│       Показать все →            │
└─────────────────────────────────┘
```

Текст: `--text-secondary`, 13px, центрирован. Без иллюстраций, без иконок — минимализм.

### 1.12 Навигация и роутинг

- Bell → dropdown: не влияет на URL
- Клик по item в dropdown → router.navigate в зависимости от `notification_type` (см. 1.4)
- «Показать все» → `/workspace/:id/notifications`

### 1.13 Доступность (a11y)

- Bell button: `aria-label="Уведомления, N непрочитанных"` (динамический, обновляется при изменении count)
- Badge: `aria-hidden="true"` (информация дублируется в aria-label кнопки)
- Dropdown: `role="dialog"`, `aria-label="Панель уведомлений"`
- Items: `role="listitem"` внутри `role="list"`
- Unread status: `aria-label` на item включает «непрочитано» для screenreader
- Focus trap: Tab cycle внутри открытого dropdown (header action → items → footer)
- Escape: закрытие и возврат фокуса на bell

### 1.14 Обработка ошибок

| Ситуация | Поведение |
|----------|-----------|
| GET unread-count failed | Badge скрыт, retry через 30s |
| GET notifications failed | Показать ошибку в dropdown с кнопкой «Повторить» |
| POST mark-read failed | Toast error «Не удалось отметить как прочитанное», revert optimistic update |
| POST mark-all-read failed | Toast error, revert badge count |
| WebSocket disconnected | Badge показывает последнее известное значение; при reconnect — sync через REST |

### 1.15 Анимации

| Анимация | Параметры |
|----------|-----------|
| Dropdown open | opacity 0→1, translateY(-4px→0), 150ms ease-out |
| Dropdown close | opacity 1→0, 100ms ease-in |
| Badge appear (0→N) | scale 0→1, 200ms ease-out (bounce) |
| Badge update (N→M) | scale 1→1.2→1, 150ms (pulse) |
| New item in open dropdown | fade-in opacity 0→1, 200ms; остальные items сдвигаются вниз 200ms ease |

### 1.16 Роли и права доступа

| Роль | Доступ |
|------|--------|
| Все роли (ANALYST+) | Видят bell, свои уведомления, mark as read |
| ADMIN | Дополнительно: ссылка на управление alert rules в dropdown footer (Phase E) |

---

## 2. Notifications Page

### 2.1 Расположение и роль

Полная страница уведомлений — таблица со всеми notifications пользователя в текущем workspace. Доступ через «Показать все» в dropdown bell или через Activity Bar (модуль Alerts, вкладка «Уведомления»).

**Route:** `/workspace/:id/notifications`

### 2.2 Визуальная структура

```
┌─ Top Bar ──────────────────────────────────────────────────────────┐
├─ Activity Bar ─┬─ Main Area ───────────────────────────────────────┤
│                │                                                    │
│   [Alerts]     │  Уведомления                                      │
│   ● active     │                                                    │
│                │  ┌─ Filter Bar ─────────────────────────────────┐  │
│                │  │ [Важность ▾] [Статус ▾] [Период ▾] [Тип ▾]  │  │
│                │  │                              [⊘ Сбросить]    │  │
│                │  └──────────────────────────────────────────────┘  │
│                │                                                    │
│                │  ┌─ Bulk Actions (если выбраны) ─────────────────┐ │
│                │  │ 5 выбрано  [Отметить прочитанными]            │ │
│                │  └──────────────────────────────────────────────┘  │
│                │                                                    │
│                │  ┌─ Table ──────────────────────────────────────┐  │
│                │  │ ☐ │ ● │ Заголовок      │ Сообщ. │ Тип │ Дата│  │
│                │  │───┼───┼────────────────┼────────┼─────┼─────│  │
│                │  │ ☐ │ 🔴│ Данные устарели│ Finance│ALERT│5 мин│  │
│                │  │ ☐ │ 🟡│ Spike: логист. │ Logist.│ALERT│12м  │  │
│                │  │ ☐ │ 🔵│ Синхр. завершен│ WB Sal │SYNC │1 ч  │  │
│                │  │ ☐ │ 🟢│ Действие выпол.│ Price  │ACTIO│2 ч  │  │
│                │  └──────────────────────────────────────────────┘  │
│                │                                                    │
│                │  Показано 1–50 из 234          [◀] 1 2 3 ... [▶]  │
│                │                                                    │
│                ├────────────────────────────────────────────────────┤
│                │  Detail Expansion (inline, below selected row)     │
│                │  ┌────────────────────────────────────────────────┐│
│                │  │ Полное сообщение: ...                          ││
│                │  │ Источник: STALE_DATA alert для Ozon Main       ││
│                │  │ [Перейти к алерту →]                           ││
│                │  └────────────────────────────────────────────────┘│
├────────────────┴────────────────────────────────────────────────────┤
│ Status Bar                                                          │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.3 Колонки таблицы

| # | Колонка | Ширина | Содержимое | Сортировка |
|---|---------|--------|-----------|------------|
| 0 | Checkbox | 40px, frozen | Выбор строки для bulk actions | — |
| 1 | Severity | 40px | Цветная точка (8px) по severity | Да |
| 2 | Заголовок | flex (min 200px) | `title`, truncate, bold если unread | Да |
| 3 | Сообщение | flex (min 150px) | `body` preview, 1 строка, truncate | — |
| 4 | Тип | 140px | `notification_type` → русский label | Да |
| 5 | Источник | 140px | Connection name или module name | Да |
| 6 | Дата | 120px | `created_at`, relative → absolute при hover (tooltip) | Да (default DESC) |
| 7 | Статус | 80px | Dot: прочитано (gray) / непрочитано (blue) | Да |

**Маппинг notification_type → русские labels:**

| notification_type | Label UI |
|-------------------|----------|
| `ALERT` | Алерт |
| `APPROVAL_REQUEST` | Запрос одобрения |
| `SYNC_COMPLETED` | Синхронизация |
| `ACTION_FAILED` | Ошибка действия |

**Маппинг severity → визуал:**

| Severity | Цвет точки | Tooltip |
|----------|------------|---------|
| CRITICAL | `--status-error` (#DC2626) | Критический |
| WARNING | `--status-warning` (#D97706) | Предупреждение |
| INFO | `--status-info` (#2563EB) | Информация |

### 2.4 Фильтры

Filter bar — горизонтальная полоса pill-фильтров (паттерн из frontend-design-direction.md):

| Фильтр | Тип контрола | Опции |
|--------|-------------|-------|
| Важность | Multi-select dropdown | Критический, Предупреждение, Информация |
| Статус | Single-select dropdown | Все, Непрочитанные, Прочитанные |
| Период | Date range picker | Сегодня, Последние 7 дней, Последние 30 дней, Произвольный диапазон |
| Тип | Multi-select dropdown | Алерт, Запрос одобрения, Синхронизация, Ошибка действия |

Активные фильтры показываются как pills: `[Важность: Критический ×]`. Кнопка «Сбросить» — сбрасывает все фильтры.

### 2.5 Данные и API

**Основной запрос:**
```
GET /api/notifications?page=0&size=50&sort=createdAt,desc
  &severity=CRITICAL,WARNING          (optional)
  &read=false                          (optional)
  &type=ALERT,ACTION_FAILED            (optional)
  &from=2026-03-01T00:00:00Z           (optional)
  &to=2026-03-31T23:59:59Z             (optional)
```

**Response:** `Page<NotificationResponse>` (Spring Page):
```json
{
  "content": [
    {
      "id": 42,
      "notificationType": "ALERT",
      "title": "Данные Ozon устарели на 26 ч",
      "body": "Finance sync не выполнялся более 24 ч для подключения Ozon Main. Автоматизация приостановлена.",
      "severity": "CRITICAL",
      "alertEventId": 15,
      "readAt": null,
      "createdAt": "2026-03-31T12:05:00Z"
    }
  ],
  "totalElements": 234,
  "totalPages": 5,
  "number": 0,
  "size": 50
}
```

**Bulk actions:**
- `POST /api/notifications/read-all` — mark all (в текущем workspace) прочитанными
- `POST /api/notifications/{id}/read` — mark single

Для bulk mark selected: клиент отправляет N параллельных `POST /api/notifications/{id}/read` (или batch endpoint, если будет добавлен).

### 2.6 Типографика

| Элемент | Font | Size | Weight | Color |
|---------|------|------|--------|-------|
| Заголовок страницы «Уведомления» | Inter | 20px | 600 | `--text-primary` |
| Table header | Inter | 11px | 600 | `--text-secondary`, uppercase, letter-spacing 0.5px |
| Table cell (title, unread) | Inter | 13px | 600 | `--text-primary` |
| Table cell (title, read) | Inter | 13px | 400 | `--text-primary` |
| Table cell (body) | Inter | 13px | 400 | `--text-secondary` |
| Table cell (type, source) | Inter | 13px | 400 | `--text-secondary` |
| Table cell (date) | Inter | 11px | 400 | `--text-tertiary` |
| Filter pill | Inter | 13px | 400 | `--text-primary` |
| Bulk action bar | Inter | 13px | 500 | `--text-primary` |
| Pagination | Inter | 13px | 400 | `--text-secondary` |
| Empty state | Inter | 14px | 400 | `--text-secondary` |

### 2.7 Отступы и размеры

| Элемент | Значение |
|---------|---------|
| Page header margin-bottom | `--space-4` (16px) |
| Filter bar height | 40px |
| Filter bar margin-bottom | `--space-3` (12px) |
| Table row height | 40px (comfortable), 32px (compact) |
| Table cell padding | 8px 12px |
| Checkbox column width | 40px |
| Severity dot column width | 40px |
| Expansion panel padding | 16px 12px 16px 92px (aligned with title column) |
| Expansion panel max-height | 200px |
| Pagination area height | 48px |

### 2.8 Поведение и интерактивность

**Row click (single):**
- Клик по строке → строка раскрывается вниз, показывая полный текст `body`, информацию об источнике и ссылку на source entity
- Повторный клик → сворачивает
- Только одна строка раскрыта одновременно (accordion)
- При раскрытии: если notification unread → автоматически `POST /api/notifications/{id}/read`

**Expansion content:**
```
┌─────────────────────────────────────────────────────────────────────┐
│ Полное сообщение:                                                   │
│ Finance sync не выполнялся более 24 ч для подключения Ozon Main.    │
│ Последняя успешная синхронизация: 29 мар 2026, 14:32.               │
│ Автоматизация ценообразования приостановлена для этого подключения.  │
│                                                                     │
│ Источник: Алерт STALE_DATA · Подключение: Ozon Main                │
│ [Перейти к алерту →]   [Перейти к подключению →]                    │
└─────────────────────────────────────────────────────────────────────┘
```

**Bulk selection:**
- Checkbox в header → select all on current page
- При выбранных строках: bulk action bar появляется между filter bar и таблицей
- Actions: «Отметить прочитанными» (primary button)
- Deselect: checkbox в header снимает всё, или клик «×» в bulk bar

**Real-time updates:**
- WebSocket push нового notification → prepend строку в таблицу (если не отфильтрована текущими фильтрами)
- Анимация: новая строка slide-down с highlight (`--bg-active`) на 2s, затем fade to normal

### 2.9 Responsive поведение

- ≥1440px: все колонки видимы
- 1280–1440px: колонка «Сообщение» скрывается (доступна через expansion)
- <1280px: не поддерживается

### 2.10 Состояния

| Состояние | Отображение |
|-----------|-------------|
| Загрузка | Skeleton таблицы: 8 строк shimmer |
| Данные загружены | Таблица с данными |
| Пустой результат (есть фильтры) | «Нет уведомлений, соответствующих фильтрам.» + [Сбросить фильтры] |
| Пустой результат (без фильтров) | «У вас пока нет уведомлений. Они появятся при возникновении событий в системе.» |
| Ошибка загрузки | «Не удалось загрузить уведомления.» + [Повторить] |
| Все прочитаны | Таблица без bold строк, badge в bell = 0 |

### 2.11 Пустое состояние

Центрировано в Main Area:

```
    Нет уведомлений

    Уведомления будут появляться здесь при возникновении
    алертов, завершении синхронизаций и других событий.
```

Текст: `--text-secondary`, 14px, max-width 400px, text-align: center.

### 2.12 Навигация и роутинг

- Route: `/workspace/:id/notifications`
- Tab в Activity Bar модуле «Alerts» (если модуль содержит несколько tabs: «Алерты», «Уведомления»)
- Breadcrumb: `Алерты > Уведомления`
- Query params для фильтров: `?severity=CRITICAL&read=false` (bookmarkable, shareable)
- Клик «Перейти к алерту» в expansion → `/workspace/:id/alerts?eventId=15`
- Клик «Перейти к подключению» → `/workspace/:id/settings/connections/:connectionId`

### 2.13 Доступность (a11y)

- Таблица: `<table role="grid">` с `aria-label="Список уведомлений"`
- Sortable headers: `aria-sort="ascending"` / `"descending"` / `"none"`
- Checkbox: `aria-label="Выбрать уведомление: {title}"`
- Expansion: `aria-expanded="true/false"` на row
- Severity: color + text (в tooltip и screen reader: «Критический», «Предупреждение», «Информация»)
- Keyboard: ↑/↓ — навигация строк, Enter — expand/collapse, Space — toggle checkbox
- Bulk action bar: `role="toolbar"`, `aria-label="Массовые действия"`
- Pagination: `nav` с `aria-label="Пагинация"`

### 2.14 Обработка ошибок

| Ситуация | Поведение |
|----------|-----------|
| GET notifications failed | Показать full-area error state с retry |
| POST mark-read failed | Toast error, revert visual state (bold title, unread dot) |
| POST mark-all-read failed | Toast error, revert all items |
| Bulk mark-read partial failure | Toast: «Не удалось отметить N из M уведомлений» |
| Network timeout | Retry автоматически через 5s, показать inline banner |

### 2.15 Анимации

| Анимация | Параметры |
|----------|-----------|
| Row expansion | height 0→auto, 200ms ease; content fade-in 150ms |
| Row collapse | height auto→0, 150ms ease |
| New row (WebSocket) | slide-down from top, background highlight 2s, 200ms ease |
| Bulk action bar appear | slide-up from bottom, 150ms ease |
| Mark read (visual) | font-weight 600→400, unread dot fade-out, 200ms |

### 2.16 Роли и права доступа

| Роль | Доступ |
|------|--------|
| ANALYST | Видит свои уведомления (INFO+) |
| OPERATOR | Видит свои уведомления (WARNING+, ACTION_FAILED) |
| PRICING_MANAGER | Видит свои + APPROVAL_REQUEST |
| ADMIN | Видит все свои уведомления + может видеть notification stats (Phase G) |

Fan-out уже отфильтрован на backend (см. audit-alerting.md §Fan-out). Frontend показывает всё, что backend вернул — клиентская фильтрация по роли не нужна.

---

## 3. Alert Events Page

### 3.1 Расположение и роль

Страница активных и исторических alert events. Показывает срабатывания alert rules и event-driven alerts. Основной инструмент для мониторинга health системы: stale data, anomalies, mismatches, failed actions.

**Route:** `/workspace/:id/alerts`

### 3.2 Визуальная структура

```
┌─ Activity Bar ─┬─ Main Area ──────────────────────┬─ Detail Panel ────┐
│                │                                    │                   │
│   [Alerts]     │  Алерты                           │  Alert Detail     │
│   ● active     │                                    │                   │
│                │  ┌─ KPI Strip ──────────────────┐  │  STALE_DATA       │
│                │  │ 🔴 3 критич. │ 🟡 5 предупр. │  │  Severity: CRIT.  │
│                │  │ ✅ 12 решено │ ⏱ 2 подтвержд │  │  Status: OPEN     │
│                │  └─────────────────────────────┘  │  Connection: Ozon  │
│                │                                    │                   │
│                │  ┌─ Filter Bar ────────────────┐  │  Opened: 31 мар   │
│                │  │ [Тип ▾] [Важн. ▾] [Подкл ▾]│  │                   │
│                │  │ [Статус ▾]    [⊘ Сбросить]  │  │  ── Контекст ──   │
│                │  └────────────────────────────┘  │  last_success_at:  │
│                │                                    │   29 мар 14:32    │
│                │  ┌─ Table ────────────────────┐  │  hours_since:26.5  │
│                │  │ Тип    │Важн│Подкл.│Дата  │  │  domain: FINANCE   │
│                │  │────────┼────┼──────┼──────│  │                   │
│                │  │→STALE  │ 🔴 │ Ozon │5 мин │  │  ── Действия ──   │
│                │  │ SPIKE  │ 🟡 │ WB   │12 мин│  │  [Подтвердить]    │
│                │  │ MISMA  │ 🟡 │ Ozon │1 ч   │  │  [Закрыть]        │
│                │  └────────────────────────────┘  │                   │
│                │                                    │                   │
│                │  1–50 из 20        [◀] 1 [▶]      │                   │
├────────────────┴────────────────────────────────────┴───────────────────┤
│ Status Bar                                                              │
└─────────────────────────────────────────────────────────────────────────┘
```

### 3.3 KPI Strip

4 карточки в линию над фильтрами — компактные счётчики:

| Карточка | Значение | Цвет | Источник |
|----------|---------|------|----------|
| Критические | Кол-во OPEN alerts с severity=CRITICAL | `--status-error` | count query |
| Предупреждения | Кол-во OPEN alerts с severity=WARNING | `--status-warning` | count query |
| Решено (за 7 дн) | Кол-во RESOLVED+AUTO_RESOLVED за 7 дней | `--status-success` | count query |
| Подтверждено | Кол-во ACKNOWLEDGED alerts | `--status-info` | count query |

Карточки кликабельны → клик применяет соответствующий фильтр (например, клик на «Критические» → filter severity=CRITICAL, status=OPEN).

Стиль карточки: высота 64px, border 1px `--border-default`, border-radius `--radius-md` (6px), padding 12px 16px. Число: `--text-xl` (20px/600) в JetBrains Mono. Label: `--text-xs` (11px/400), `--text-secondary`.

### 3.4 Колонки таблицы

| # | Колонка | Ширина | Содержимое | Сортировка |
|---|---------|--------|-----------|------------|
| 1 | Тип проверки | 180px | `rule_type` → русский label + icon | Да |
| 2 | Важность | 100px | Severity badge (цвет dot + label) | Да |
| 3 | Подключение | 160px | `connection.name` или «—» (workspace-wide) | Да |
| 4 | Статус | 140px | Status badge (OPEN / ACKNOWLEDGED / RESOLVED / AUTO_RESOLVED) | Да |
| 5 | Заголовок | flex (min 200px) | `title`, truncate | — |
| 6 | Открыт | 120px | `opened_at`, relative time | Да (default DESC) |
| 7 | Решён | 120px | `resolved_at`, relative time или «—» | Да |

**Маппинг rule_type → русские labels:**

| rule_type | Label | Lucide icon |
|-----------|-------|-------------|
| `STALE_DATA` | Устаревшие данные | `clock-alert` |
| `MISSING_SYNC` | Пропущенная синхр. | `refresh-cw-off` |
| `RESIDUAL_ANOMALY` | Аномалия сверки | `triangle-alert` |
| `SPIKE_DETECTION` | Всплеск показателя | `trending-up` |
| `MISMATCH` | Расхождение данных | `git-compare` |
| `ACTION_FAILED` | Ошибка действия | `x-circle` |
| `STUCK_STATE` | Зависшее действие | `pause-circle` |
| `RECONCILIATION_FAILED` | Ошибка сверки | `file-x` |
| `POISON_PILL` | Ошибка обработки | `skull` |
| `PROMO_MISMATCH` | Промо расхождение | `percent` |
| `ACTION_DEFERRED` | Отложенное действие | `clock` |

**Маппинг status → badge:**

| Status | Label | Dot color | Badge bg |
|--------|-------|-----------|----------|
| OPEN | Активен | `--status-error` (#DC2626) | `#FEF2F2` |
| ACKNOWLEDGED | Подтверждён | `--status-info` (#2563EB) | `#EFF6FF` |
| RESOLVED | Закрыт | `--status-success` (#059669) | `#F0FDF4` |
| AUTO_RESOLVED | Авто-закрыт | `--status-neutral` (#6B7280) | `#F9FAFB` |

### 3.5 Фильтры

| Фильтр | Тип | Опции |
|--------|-----|-------|
| Тип проверки | Multi-select | Все rule_types из маппинга 3.4 |
| Важность | Multi-select | Критический, Предупреждение, Информация |
| Подключение | Single-select | Список active connections workspace + «Все» |
| Статус | Multi-select | Активен, Подтверждён, Закрыт, Авто-закрыт. Default: Активен + Подтверждён |

Default фильтр при входе на страницу: status = OPEN, ACKNOWLEDGED (показывать активные проблемы).

### 3.6 Данные и API

**Основной запрос:**
```
GET /api/alerts?page=0&size=50&sort=openedAt,desc
  &ruleType=STALE_DATA,MISSING_SYNC       (optional)
  &severity=CRITICAL                        (optional)
  &connectionId=5                           (optional)
  &status=OPEN,ACKNOWLEDGED                 (optional, default)
```

**Response:** `Page<AlertEventResponse>`:
```json
{
  "content": [
    {
      "id": 15,
      "alertRuleId": 3,
      "ruleType": "STALE_DATA",
      "severity": "CRITICAL",
      "status": "OPEN",
      "title": "Данные Ozon устарели на 26 ч",
      "details": {
        "connectionName": "Ozon Main",
        "domain": "FINANCE",
        "lastSuccessAt": "2026-03-29T14:32:00Z",
        "hoursSinceSync": 26.5,
        "threshold": 24
      },
      "blocksAutomation": true,
      "connectionId": 5,
      "connectionName": "Ozon Main",
      "openedAt": "2026-03-31T12:05:00Z",
      "acknowledgedAt": null,
      "resolvedAt": null
    }
  ],
  "totalElements": 20,
  "totalPages": 1,
  "number": 0,
  "size": 50
}
```

**KPI counts:** отдельный lightweight endpoint или derived from filtered results:
```
GET /api/alerts/summary
```
Response:
```json
{
  "openCritical": 3,
  "openWarning": 5,
  "acknowledged": 2,
  "resolvedLast7Days": 12
}
```

**Actions:**
- `POST /api/alerts/{id}/acknowledge` → OPEN → ACKNOWLEDGED
- `POST /api/alerts/{id}/resolve` → ACKNOWLEDGED → RESOLVED

### 3.7 Типографика

| Элемент | Font | Size | Weight | Color |
|---------|------|------|--------|-------|
| Заголовок «Алерты» | Inter | 20px | 600 | `--text-primary` |
| KPI number | JetBrains Mono | 20px | 600 | semantic color |
| KPI label | Inter | 11px | 400 | `--text-secondary` |
| Table header | Inter | 11px | 600 | `--text-secondary`, uppercase |
| Table cell (rule type) | Inter | 13px | 500 | `--text-primary` |
| Table cell (title) | Inter | 13px | 400 | `--text-primary` |
| Table cell (dates) | Inter | 11px | 400 | `--text-tertiary` |
| Status badge label | Inter | 11px | 500 | semantic color (darkened) |
| Detail panel heading | Inter | 16px | 600 | `--text-primary` |
| Detail panel key | Inter | 11px | 500 | `--text-secondary` |
| Detail panel value | JetBrains Mono (numbers), Inter (text) | 13px | 400 | `--text-primary` |

### 3.8 Отступы и размеры

| Элемент | Значение |
|---------|---------|
| KPI strip height | 64px per card |
| KPI strip gap | `--space-3` (12px) |
| KPI strip margin-bottom | `--space-4` (16px) |
| Filter bar height | 40px |
| Table row height | 40px |
| Detail Panel width | 400px (default), min 320px, max 50% viewport |
| Detail Panel padding | 16px |
| Status badge padding | 4px 8px |
| Status badge border-radius | `--radius-sm` (4px) |

### 3.9 Detail Panel (Right Side)

Открывается по клику на строку таблицы. Стандартный Detail Panel (паттерн из frontend-design-direction.md): push Main Area, не overlay.

**Структура:**

```
┌─ Alert Detail ──────────────────── [×] ┐
│                                         │
│  STALE_DATA                             │
│  ● Критический        Активен           │
│                                         │
│  ── Общая информация ──                 │
│  Подключение:     Ozon Main             │
│  Открыт:          31 мар, 12:05         │
│  Автоматизация:   ⛔ Заблокирована      │
│                                         │
│  ── Контекст (details) ──              │
│  domain:           FINANCE              │
│  last_success_at:  29 мар, 14:32        │
│  hours_since_sync: 26.5                 │
│  threshold:        24                   │
│                                         │
│  ── Действия ──                         │
│  [Подтвердить]   (Primary btn)          │
│  [Закрыть]       (Secondary btn)        │
│                                         │
│  ── Связанные элементы ──              │
│  [→ Настройки подключения]              │
│  [→ История синхронизаций]              │
└─────────────────────────────────────────┘
```

**Секция «Контекст»:** alert `details` JSONB отображается как key-value таблица. Ключи — human-readable (маппинг на frontend). Значения: числа в JetBrains Mono, даты форматированы, строки as-is.

**Кнопки действий:**

| Action | Показывается когда | Endpoint | Button style |
|--------|-------------------|----------|-------------|
| Подтвердить | status = OPEN | `POST /api/alerts/{id}/acknowledge` | Primary |
| Закрыть | status = ACKNOWLEDGED | `POST /api/alerts/{id}/resolve` | Primary |
| — | status = RESOLVED / AUTO_RESOLVED | — | Только информация о закрытии |

**Ссылки на связанные сущности:**
- «Настройки подключения» → `/workspace/:id/settings/connections/:connectionId`
- «История синхронизаций» → `/workspace/:id/settings/connections/:connectionId/sync-history`
- «Перейти к действию» (для ACTION_FAILED) → `/workspace/:id/execution?actionId=...`

### 3.10 Состояния

| Состояние | Отображение |
|-----------|-------------|
| Загрузка | KPI strip: 4 skeleton cards. Таблица: 6 rows shimmer |
| Данные загружены | KPI strip + filtered table |
| Нет active alerts (good state!) | KPI: все нули. Таблица: «Нет активных алертов. Все системы работают штатно.» с зелёной галочкой |
| Нет результатов (фильтр) | «Нет алертов, соответствующих фильтрам.» + [Сбросить фильтры] |
| Ошибка загрузки | Full-area error с retry |

### 3.11 Пустое состояние (нет active alerts)

```
        ✓ Все системы работают штатно

        Активных алертов нет. Проверки выполняются
        автоматически каждые несколько минут.
```

Icon: Lucide `check-circle`, 32px, `--status-success`. Текст: `--text-secondary`, 14px, centered.

### 3.12 Навигация и роутинг

- Route: `/workspace/:id/alerts`
- Query params: `?ruleType=STALE_DATA&severity=CRITICAL&status=OPEN&connectionId=5&eventId=15`
- `eventId` query param → автоматически select строку и открыть Detail Panel
- Activity Bar: модуль «Alerts» с двумя вкладками — «Алерты» и «Уведомления»
- Breadcrumb: `Алерты > Алерты`

### 3.13 Доступность (a11y)

- Table: `role="grid"`, sortable headers с `aria-sort`
- KPI cards: `role="button"`, `aria-label="Критические алерты: 3. Нажмите для фильтрации"`
- Detail Panel: `role="complementary"`, `aria-label="Детали алерта"`
- Status badges: text label + color (не только цвет)
- Action buttons: `aria-label` с контекстом: «Подтвердить алерт: Данные Ozon устарели»
- Keyboard: ↑/↓ navigate rows, Enter open Detail Panel, Escape close Panel

### 3.14 Обработка ошибок

| Ситуация | Поведение |
|----------|-----------|
| GET alerts failed | Full-area error, retry button |
| GET alerts/summary failed | KPI strip показывает «—» вместо чисел |
| POST acknowledge failed | Toast error: «Не удалось подтвердить алерт. Повторите позже.» |
| POST resolve failed | Toast error: «Не удалось закрыть алерт.» |
| Alert already resolved (409 Conflict) | Toast info: «Алерт уже был закрыт.» + refresh table row |
| WebSocket push: alert status change | Update row in-place (status badge, resolved_at) |

### 3.15 Анимации

| Анимация | Параметры |
|----------|-----------|
| Detail Panel open | slide-in from right, 200ms ease. Main Area shrinks 200ms ease |
| Detail Panel close | slide-out, 150ms. Main Area expands |
| KPI counter update | number morph (count up/down), 300ms |
| New alert row (WebSocket) | row flash (`--bg-active`→normal), 2s |
| Status change (WebSocket) | badge color transition 300ms, brief row highlight |

### 3.16 Роли и права доступа

| Роль | Доступ |
|------|--------|
| ANALYST | Просмотр alert events (read-only) |
| OPERATOR | Просмотр + Acknowledge + Resolve |
| ADMIN | Просмотр + Acknowledge + Resolve + ссылка на Alert Rules management |

Кнопки «Подтвердить» / «Закрыть» скрыты для ANALYST (не disabled, а именно скрыты — чтобы не создавать ложное ожидание). Backend enforce через `@PreAuthorize`.

---

## 4. Toast Notifications

### 4.1 Расположение и роль

Global overlay — кратковременные информационные сообщения, появляющиеся поверх всего контента. Информируют о событиях, не требующих немедленного перехода: завершение синхронизации, новый алерт, изменение статуса действия.

Toast-ы отличаются от notification bell: bell — персистентная лента (хранится в БД), toast — эфемерный UI-элемент (только в текущей сессии).

### 4.2 Визуальная структура

```
                                            ┌───────────────────────────────────┐
                                            │ ▌🔴 Данные устарели               │
                                            │ ▌   Ozon Finance: >24ч без синхр. │
                                            │ ▌                    [Подробнее] ×│
                                            ├───────────────────────────────────┤
                                            │ ▌🟡 Spike обнаружен              │
                                            │ ▌   Логистика WB: 3× от нормы    │
                                            │ ▌                              × │
                                            ├───────────────────────────────────┤
                                            │ ▌🔵 Синхронизация завершена       │
                                            │ ▌   WB Sales: 1 234 записи       │
                                            │ ▌                              × │
                                            └───────────────────────────────────┘
                                                                    ↑
                                                        Bottom-right corner
                                                        offset: 16px from edges
```

**Структура одного toast:**

```
┌──┬────────────────────────────────────────────┐
│▌ │  [Icon]  Title                          [×]│
│▌ │          Message body (1-2 lines)          │
│▌ │                             [Action link]  │
│▌ │  ▓▓▓▓▓▓▓▓▓▓░░░░░░░░  (auto-dismiss bar)  │
└──┴────────────────────────────────────────────┘
 ↑
 Severity stripe (4px)
```

### 4.3 Severity-based styling

| Severity | Stripe color | Icon (Lucide) | Icon color | Auto-dismiss |
|----------|-------------|---------------|------------|-------------|
| CRITICAL | `--status-error` (#DC2626) | `alert-circle` | #DC2626 | Нет (persistent) |
| WARNING | `--status-warning` (#D97706) | `alert-triangle` | #D97706 | 8 секунд |
| INFO | `--status-info` (#2563EB) | `info` | #2563EB | 5 секунд |
| SUCCESS | `--status-success` (#059669) | `check-circle` | #059669 | 3 секунды |

**Auto-dismiss progress bar:**
- Тонкая полоса (2px) внизу toast, заполняется слева направо за время auto-dismiss
- Цвет: severity stripe color с opacity 0.3
- При hover на toast — progress bar pauseится (таймер приостановлен)
- При уходе курсора — таймер продолжается с текущей позиции

### 4.4 Toast содержимое

| Поле | Описание | Стиль |
|------|---------|-------|
| Title | Краткий заголовок события (1 строка) | Inter 13px/600, `--text-primary` |
| Body | Описание (1-2 строки, truncate) | Inter 13px/400, `--text-secondary` |
| Action link | Опциональная ссылка-действие | Inter 13px/500, `--accent-primary`, underline on hover |
| Close button | «×» icon button | Lucide `x` 16×16, `--text-tertiary`, hover `--text-primary` |
| Severity stripe | Вертикальная полоса слева | 4px width, severity color, full height |

### 4.5 Данные и API (WebSocket triggers)

Toast-ы триггерятся WebSocket сообщениями. Mapping:

| STOMP Destination | Event type | Toast severity | Title template | Action link |
|-------------------|-----------|---------------|----------------|-------------|
| `/topic/workspace/{id}/alerts` | New alert (OPEN) | По `severity` alert | `{title}` | «Подробнее» → alerts page |
| `/topic/workspace/{id}/alerts` | Alert auto-resolved | SUCCESS | «Алерт закрыт: {title}» | — |
| `/topic/workspace/{id}/sync-status` | `WorkspaceSyncStatusPush` | — | (status bar / `SyncStatusStore`; toast не из этого топика) | — |
| `/topic/workspace/{id}/actions` | Action completed | SUCCESS | «Действие выполнено: {offerId}» | — |
| `/topic/workspace/{id}/actions` | Action failed | CRITICAL | «Ошибка действия: {offerId}» | «Подробнее» → execution |
| `/user/queue/notifications` | Любая нотификация (в т.ч. `SYNC_COMPLETED` после ETL) | По `severity` | `{title}` / ключи `MessageCodes` | «Открыть» → по типу |

**WebSocket message format (alerts topic):**
```json
{
  "alertEventId": 15,
  "ruleType": "STALE_DATA",
  "severity": "CRITICAL",
  "title": "Данные Ozon устарели на 26 ч",
  "status": "OPEN",
  "connectionId": 5
}
```

**WebSocket message format (sync-status topic):**
```json
{
  "reason": "STATE_CHANGED",
  "connection": {
    "connectionId": 5,
    "connectionName": "Ozon main",
    "lastSuccessAt": "2026-04-03T10:00:00+03:00",
    "status": "OK"
  }
}
```
(`reason`: `STATE_CHANGED` | `ETL_JOB_COMPLETED`; `connection` — тот же контракт, что `GET /api/connections/sync-health`.)

**WebSocket message format (actions topic):**
```json
{
  "actionId": 123,
  "actionType": "PRICE_CHANGE",
  "status": "CONFIRMED",
  "offerId": 456
}
```

### 4.6 Типографика

| Элемент | Font | Size | Weight | Color |
|---------|------|------|--------|-------|
| Title | Inter | 13px | 600 | `--text-primary` |
| Body | Inter | 13px | 400 | `--text-secondary` |
| Action link | Inter | 13px | 500 | `--accent-primary` |
| Close icon | — | 16px | — | `--text-tertiary` |

### 4.7 Отступы и размеры

| Элемент | Значение |
|---------|---------|
| Toast width | 360px |
| Toast min-height | 56px |
| Toast max-height | 120px |
| Toast padding | 12px 12px 12px 16px (left includes stripe space) |
| Stripe width | 4px |
| Border-radius | `--radius-md` (6px) |
| Shadow | `--shadow-md` (0 4px 12px rgba(0,0,0,0.08)) |
| Border | 1px `--border-default` |
| Background | `--bg-primary` (#FFFFFF) |
| Gap between toasts | `--space-2` (8px) |
| Container offset from viewport | 16px right, 16px bottom |
| Icon size | 18×18 |
| Gap: icon → content | 10px |

### 4.8 Поведение и интерактивность

**Stacking:**
- Максимум 3 toast-а видимы одновременно
- Новый toast → oldest auto-dismiss toast удаляется (если уже 3 видимых)
- CRITICAL toast-ы имеют приоритет и не вытесняются INFO/WARNING
- Stack direction: снизу вверх (новый добавляется внизу, стек растёт вверх)

**Hover:**
- Auto-dismiss timer pauseится при hover
- Close button становится более видимым (opacity 0.5 → 1.0)

**Click на action link:**
- Toast закрывается
- Router navigate к target

**Click на body toast (не на link/close):**
- Если есть action link → выполняет navigate как action link
- Если нет → toast закрывается

**Dismiss:**
- Click close button → immediate remove
- Auto-dismiss timer → remove
- Swipe right (optional, CDK drag) → dismiss

### 4.9 Responsive поведение

- ≥1440px: toast container bottom-right, 16px offset
- 1280–1440px: то же самое
- Toast width фиксирован (360px), не адаптируется

### 4.10 Flood protection

При высокой частоте событий (например, массовый sync или cascade failures):

| Ситуация | Поведение |
|----------|-----------|
| >5 toasts за 10 секунд | Группировка: «И ещё N событий...» + link на Notifications page |
| >10 toasts за 30 секунд | Подавление: toast «Множество событий. Откройте уведомления для просмотра.» |
| Один и тот же alert_type повторно | Дедупликация: обновление existing toast (counter +1) вместо нового |

Дедупликация: toast с тем же `ruleType + connectionId` в течение 30s → update body existing toast, не создавать новый. Пример: «Данные Ozon устарели (обновлено 3×)».

### 4.11 Пустое состояние

Не применимо — toast-ы появляются только при наличии событий.

### 4.12 Навигация и роутинг

Toast не влияет на URL. Клик по action link → router navigate:
- Alert → `/workspace/:id/alerts?eventId={alertEventId}`
- Sync → `/workspace/:id/settings/connections/{connectionId}`
- Action → `/workspace/:id/execution?actionId={actionId}`

### 4.13 Доступность (a11y)

- Toast container: `role="alert"`, `aria-live="polite"` (INFO/SUCCESS), `aria-live="assertive"` (WARNING/CRITICAL)
- Close button: `aria-label="Закрыть уведомление"`
- Auto-dismiss: screenreader announces toast, gives enough time to read (5-8s)
- Severity conveyed by text + icon, не только цветом
- Focus: toast не перехватывает фокус (не modal). Tab к toast не требуется — info-only

### 4.14 Обработка ошибок

| Ситуация | Поведение |
|----------|-----------|
| WebSocket disconnected | Toast-ы не приходят; см. reconnection (секция 6) |
| Invalid message format | Ignore silently, log.warn в console |
| Action link target not found | Navigate to page, page handles 404 |

### 4.15 Анимации

| Анимация | Параметры |
|----------|-----------|
| Appear | slide-in from right (translateX(100%→0)), fade-in (0→1), 200ms ease-out |
| Dismiss (auto/manual) | slide-out right (translateX(0→100%)), fade-out (1→0), 150ms ease-in |
| Stack reflow (toast removed) | remaining toasts slide down, 200ms ease |
| Progress bar | linear width 100%→0% over dismiss duration |
| Hover pause | progress bar stops, slight scale(1.01) on toast, 100ms |

### 4.16 Роли и права доступа

Toast-ы отображаются всем авторизованным пользователям workspace. Фильтрация происходит на уровне WebSocket subscription (user получает только те topic-и, на которые подписан). Notification fan-out уже учитывает роль (см. audit-alerting.md §Fan-out).

---

## 5. Automation Blocker Banner

### 5.1 Расположение и роль

Persistent banner, предупреждающий о приостановке автоматизации для одного или нескольких подключений. Появляется, когда есть active alert с `blocks_automation = true`. Расположен между Top Bar и Main Area, виден на всех страницах workspace.

Это критически важный UI-элемент: если пользователь не знает, что автоматизация заблокирована, он может потерять деньги (цены не обновляются, промо не применяются).

### 5.2 Визуальная структура

```
┌─ Top Bar ──────────────────────────────────────────────────────────┐
├─ Automation Blocker Banner ────────────────────────────────────────┤
│ ⚠ Автоматизация приостановлена для Ozon Main: данные устарели     │
│   на 26 ч.                            [Подробнее]    [Скрыть]     │
├─ Activity Bar ─┬─ Main Area ───────────────────────────────────────┤
```

**Один connection:**
```
⚠ Автоматизация приостановлена для {connection_name}: {reason}. Данные устарели на {hours} ч.
```

**Несколько connections:**
```
⚠ Автоматизация приостановлена для 3 подключений. Данные устарели.    [Подробнее]  [Скрыть]
```

### 5.3 Стиль

| Свойство | Значение |
|---------|---------|
| Background | `#FFFBEB` (amber-50, light yellow) |
| Border-bottom | 1px `#FCD34D` (amber-300) |
| Text color | `#92400E` (amber-800) |
| Icon | Lucide `alert-triangle` 16px, color `#D97706` |
| Font | Inter 13px/500 |
| Height | auto (min 36px, multi-line if needed) |
| Padding | 8px 16px |
| «Подробнее» link | `#92400E`, underline, → `/workspace/:id/alerts?blocksAutomation=true` |
| «Скрыть» button | ghost, `#92400E`, → hides banner until next check cycle (5 min) |

### 5.4 Данные и API

Banner state определяется из двух источников:

1. **WebSocket `/topic/workspace/{id}/alerts`** — real-time push новых alert events и auto-resolve
2. **Initial load:** `GET /api/alerts?blocksAutomation=true&status=OPEN,ACKNOWLEDGED&size=10`

**Логика отображения:**
- При загрузке shell → query blocking alerts
- Если `count > 0` → показать banner
- При WebSocket event: new alert с `blocksAutomation=true` → показать/обновить banner
- При WebSocket event: alert auto-resolved/resolved → проверить, остались ли blocking alerts → если нет, скрыть banner

**Dismiss logic:**
- «Скрыть» → banner скрывается
- Скрытое состояние сохраняется в `sessionStorage` с TTL = 5 минут
- Через 5 минут (или при новом blocking alert) — banner появляется снова
- Не хранится в `localStorage` — при новой сессии banner показывается заново

### 5.5 Типографика

| Элемент | Font | Size | Weight | Color |
|---------|------|------|--------|-------|
| Main text | Inter | 13px | 500 | `#92400E` |
| Connection name | Inter | 13px | 600 | `#92400E` |
| Hours number | JetBrains Mono | 13px | 500 | `#92400E` |
| «Подробнее» | Inter | 13px | 500 | `#92400E`, underline |
| «Скрыть» | Inter | 13px | 400 | `#92400E` |

### 5.6 Отступы и размеры

| Элемент | Значение |
|---------|---------|
| Banner height | auto (min 36px) |
| Padding | 8px 16px |
| Icon size | 16px |
| Gap: icon → text | 8px |
| Gap: text → links | 16px |
| «Скрыть» button padding | 4px 8px |

### 5.7 Поведение и интерактивность

- Banner НЕ является dismissible навсегда — только временно (5 мин или до нового event)
- «Подробнее» → navigate to alerts page с фильтром `blocksAutomation=true`
- При нескольких connections — banner показывает summary, детали на alerts page
- Banner не стекируется: всегда один banner, содержимое агрегировано

### 5.8 Responsive поведение

- Full width, между Top Bar и Activity Bar / Main Area
- Text wraps if needed (height: auto)
- Ширина: 100% viewport

### 5.9 Состояния

| Состояние | Отображение |
|-----------|-------------|
| Нет blocking alerts | Banner скрыт (height 0, no layout space) |
| 1 blocking alert | Полный текст с connection name и часами |
| 2+ blocking alerts | Summary: «Приостановлена для N подключений» |
| Banner dismissed | Скрыт (5 мин TTL) |
| Loading (initial) | Banner не показывается до получения данных (prevent flash) |

### 5.10 Пустое состояние

Banner просто отсутствует — нет placeholder-а.

### 5.11 Навигация и роутинг

- «Подробнее» → `/workspace/:id/alerts?blocksAutomation=true&status=OPEN,ACKNOWLEDGED`
- Не влияет на текущий route

### 5.12 Доступность (a11y)

- `role="alert"` — screenreader объявляет при появлении
- `aria-live="assertive"` — критическая информация
- Icon: `aria-hidden="true"` (текст достаточен)
- «Скрыть»: `aria-label="Скрыть предупреждение об автоматизации на 5 минут"`

### 5.13 Обработка ошибок

| Ситуация | Поведение |
|----------|-----------|
| GET blocking alerts failed | Banner не показывается (не блокировать UI из-за meta-query) |
| WebSocket disconnected | Banner сохраняет последнее известное состояние |
| Stale banner data (>5 min без update) | При reconnect → refresh blocking alerts |

### 5.14 Анимации

| Анимация | Параметры |
|----------|-----------|
| Appear | slideDown, height 0→auto, 200ms ease. Main area сдвигается вниз |
| Dismiss | slideUp, height auto→0, 150ms ease. Main area сдвигается вверх |
| Content update (text change) | crossfade текста, 200ms |

### 5.15 Роли и права доступа

Все роли видят banner — это safety-critical информация. «Скрыть» доступен всем (скрывает только для текущего пользователя в текущей сессии).

### 5.16 Множественные blockers

Если несколько connections заблокированы одновременно:

```
⚠ Автоматизация приостановлена для 3 подключений: Ozon Main, WB Store, WB FBS.
  Данные устарели.                                         [Подробнее]  [Скрыть]
```

При >3 connections: показать первые 2 + «и ещё N»:
```
⚠ Автоматизация приостановлена для Ozon Main, WB Store и ещё 3 подключений.
```

---

## 6. Real-time WebSocket Integration

### 6.1 Роль

WebSocket (STOMP over SockJS) — единый транспорт real-time событий от backend к frontend. Используется всеми компонентами этой спецификации: bell badge, toast-ы, alert table updates, banner state.

### 6.2 Архитектура подключения

**Endpoint:** `/ws` (SockJS-compatible)

**Authentication:** JWT token передаётся как query parameter при handshake:
```
ws://host/ws?token=eyJhbGci...
```

**Connection lifecycle:**
1. User authenticated → Angular service инициирует STOMP connect
2. Handshake → server validates JWT, extracts userId + workspaceId
3. STOMP CONNECT frame → connected
4. Client subscribes to destinations (см. 6.3)
5. Messages flow server → client
6. Disconnect → reconnect loop (см. 6.4)

**Angular service:** singleton `WebSocketService` (provided in root), использует `@stomp/rx-stomp`:
```
RxStompService → configure(url, token) → activate()
                → watch('/topic/workspace/{id}/...') → Observable<IMessage>
                → watch('/user/queue/notifications') → Observable<IMessage>
```

### 6.3 STOMP Subscriptions

При успешном connect, клиент подписывается на 4 destination-а:

| Destination | Тип | Consumers |
|-------------|-----|-----------|
| `/topic/workspace/{workspaceId}/alerts` | Broadcast | Alert Events table, Toast, Banner, Bell badge |
| `/topic/workspace/{workspaceId}/sync-status` | Broadcast | Status bar (`SyncStatusStore`); при `ETL_JOB_COMPLETED` — инвалидация offers/analytics (TanStack) |
| `/topic/workspace/{workspaceId}/actions` | Broadcast | Toast, Execution queue (другая страница) |
| `/user/queue/notifications` | User-specific | Bell badge, Notification dropdown, Toast |

**Authorization:** server-side `ChannelInterceptor` проверяет, что user имеет membership в workspace для topic subscriptions. `/user/queue/notifications` — автоматически scoped to authenticated user.

### 6.4 Reconnection strategy

При потере соединения (network drop, server restart):

| Attempt | Delay | Max |
|---------|-------|-----|
| 1 | 1 секунда | — |
| 2 | 2 секунды | — |
| 3 | 4 секунды | — |
| 4 | 8 секунд | — |
| 5+ | 16 секунд | — |
| N | min(2^N, 30) секунд | 30 секунд max backoff |

**Jitter:** ±20% рандомизация для предотвращения thundering herd.

**UI feedback:**
- Disconnect detected → после 3 секунд (не сразу!) → yellow banner: «Соединение потеряно. Переподключение...»
- Задержка 3с — чтобы кратковременные разрывы не мерцали banner
- Reconnect success → banner исчезает, toast: «Соединение восстановлено» (INFO, 3s)
- After 60s of failed reconnects → banner меняется на: «Не удаётся подключиться. Данные могут быть неактуальны. [Обновить страницу]»

### 6.5 Offline queue & sync

При reconnect клиент должен синхронизировать пропущенные события:

1. Клиент хранит `lastSeenTimestamp` (самый свежий `createdAt` из полученных messages)
2. При reconnect → `GET /api/notifications?since={lastSeenTimestamp}` — sync пропущенных notification
3. При reconnect → `GET /api/alerts?status=OPEN,ACKNOWLEDGED&updatedSince={lastSeenTimestamp}` — sync alert state changes
4. При reconnect → refresh `GET /api/notifications/unread-count` — актуализировать badge

**Merge strategy:** при получении REST sync data — deduplicate по id (если message с таким id уже был получен через WebSocket до disconnect — пропустить).

### 6.6 Message format convention

Все STOMP messages — JSON body. Общая структура:

```json
{
  "eventType": "ALERT_OPENED",
  "payload": { ... },
  "timestamp": "2026-03-31T12:05:00Z"
}
```

**Event types per destination:**

| Destination | eventType values |
|-------------|-----------------|
| alerts | `ALERT_OPENED`, `ALERT_ACKNOWLEDGED`, `ALERT_RESOLVED`, `ALERT_AUTO_RESOLVED` |
| sync-status | `WorkspaceSyncStatusPush.reason`: `STATE_CHANGED`, `ETL_JOB_COMPLETED` |
| actions | `ACTION_CREATED`, `ACTION_CONFIRMED`, `ACTION_FAILED`, `ACTION_EXPIRED` |
| notifications | `NOTIFICATION_CREATED` |

### 6.7 Типографика

Не применимо (невидимый слой).

### 6.8 Отступы и размеры

Не применимо.

### 6.9 Поведение и интерактивность

- WebSocket connect автоматически при загрузке shell (после auth)
- Disconnect → автоматический reconnect (exponential backoff)
- Tab visibility: при `document.hidden` (вкладка неактивна) — соединение сохраняется (нет disconnect при blur)
- Token refresh: при обновлении JWT token — reconnect с новым token (старое соединение close → new connect)

### 6.10 Состояния

| Состояние | UI indicator |
|-----------|-------------|
| Connected | Нет индикатора (нормальное состояние) |
| Connecting (initial) | Нет индикатора (ожидание <3с) |
| Disconnected (0-3с) | Нет индикатора (grace period) |
| Disconnected (>3с) | Yellow banner: «Соединение потеряно. Переподключение...» |
| Reconnecting | Yellow banner с анимированной точкой: «Переподключение...» |
| Failed (>60с) | Red banner: «Не удаётся подключиться. Данные могут быть неактуальны.» |
| Reconnected | Banner исчезает, toast «Соединение восстановлено» |

### 6.11 Пустое состояние

Не применимо.

### 6.12 Навигация и роутинг

- WebSocket connection persistent across route changes (singleton service)
- При смене workspace → disconnect + reconnect с новым workspaceId (new subscriptions)

### 6.13 Доступность (a11y)

- Connection status banner: `role="status"`, `aria-live="polite"`
- Reconnection banner: screenreader объявляет один раз при появлении
- Не прерывает keyboard navigation

### 6.14 Обработка ошибок

| Ситуация | Поведение |
|----------|-----------|
| Invalid JWT at handshake | Redirect to login (token expired) |
| Server rejects subscription (403) | Log error, no retry for that destination |
| Malformed message | Skip, log.warn in console |
| Message processing error | Catch, log, continue (не ломать всю подписку) |
| SockJS fallback (no WebSocket support) | Automatic fallback to HTTP long-polling |

### 6.15 Анимации

Disconnect/reconnect banners используют те же анимации, что и Automation Blocker Banner (slideDown/slideUp).

### 6.16 Роли и права доступа

- Все authenticated users могут connect к WebSocket
- Subscription filtering: server-side ChannelInterceptor проверяет workspace membership
- `/user/queue/notifications` scoped автоматически (STOMP user destination)

---

## User Flow Scenarios

### Scenario 1: Получение критического алерта о stale data

**Контекст:** Оператор работает на странице Pricing, Ozon Finance data не синхронизировалось >24 часов.

1. **Scheduled checker** (backend, каждые 5 мин) обнаруживает `hours_since_sync > 24` для Ozon Main connection
2. Backend INSERT `alert_event` (OPEN, CRITICAL, `blocks_automation=true`) → fan-out → INSERT `user_notification` per workspace member
3. **WebSocket push:** сообщение по `/topic/workspace/{id}/alerts` и `/user/queue/notifications`
4. **UI реакция (одновременно):**
   - **Toast** (CRITICAL) появляется bottom-right: «Данные Ozon устарели на 26 ч» с link «Подробнее». Persistent (без auto-dismiss)
   - **Bell badge** инкрементируется (+1)
   - **Automation Blocker Banner** появляется под Top Bar: «⚠ Автоматизация приостановлена для Ozon Main...»
   - **Status Bar** (если показывает Ozon sync status) — иконка меняется на красную
5. Оператор замечает toast → кликает «Подробнее» → navigate to `/workspace/:id/alerts?eventId=15`
6. Alert Events page открывается, строка с alert выделена, Detail Panel открыт
7. Оператор видит контекст: `last_success_at`, `hours_since_sync`, `threshold`
8. Оператор идёт в Settings → Connections → Ozon Main → проверяет credentials, запускает manual sync
9. После успешного sync → backend checker: condition cleared → `alert_event` AUTO_RESOLVED
10. **WebSocket push:** alert resolved → toast (SUCCESS): «Алерт закрыт: данные Ozon» → banner исчезает

### Scenario 2: Просмотр и обработка накопившихся уведомлений

**Контекст:** Оператор возвращается после перерыва, видит badge «12» на bell.

1. Оператор кликает bell → dropdown открывается
2. Видит 20 последних notifications (12 unread, bold)
3. Быстро просматривает: 2 CRITICAL (stale data), 3 WARNING (spikes), 7 INFO (sync completed)
4. Кликает на CRITICAL notification → notification marked read, navigate to alerts page
5. Разбирает alert (acknowledge → investigate → resolve или ждёт auto-resolve)
6. Возвращается → кликает bell → кликает «Отметить все как прочитанные» (для INFO notifications, которые не требуют действий)
7. Badge обнуляется
8. Для детального review → кликает «Показать все» → Notifications page
9. На Notifications page применяет фильтр severity=WARNING → видит только warnings за сегодня
10. Просматривает expansion каждого → решает, какие требуют действий

### Scenario 3: Расследование stale data с WebSocket disconnect

**Контекст:** Оператор работает, Wi-Fi моргнул на 20 секунд.

1. WebSocket disconnect detected → 3с grace period (нет UI feedback)
2. Через 3с → yellow banner: «Соединение потеряно. Переподключение...»
3. Reconnect attempts: 1с, 2с, 4с (backoff)
4. На 3-й попытке (через ~7с) → connection restored
5. Banner исчезает → toast (INFO): «Соединение восстановлено» (3s auto-dismiss)
6. Client sync:
   - `GET /api/notifications?since={lastSeenTimestamp}` → получает 2 пропущенных notification
   - `GET /api/notifications/unread-count` → badge обновляется
   - `GET /api/alerts?status=OPEN,ACKNOWLEDGED&updatedSince=...` → проверяет, не изменились ли alerts
7. Bell badge обновляется (+2). Если alert page открыта — таблица обновляется.
8. Пропущенные notifications НЕ показываются как toast (они были >30с назад, toast неактуален). Только badge update и sync в notification list.

---

## Edge Cases

### E1: Notification flood

**Проблема:** массовый sync failure → 50 alerts за 10 секунд.

**Решение:**
- Toast flood protection (секция 4.10): после 5 toasts за 10с — группировка
- Bell badge: incrementing count (работает корректно)
- Notifications page: все 50 видны в таблице
- Backend fan-out: все 50 notifications создаются (нет server-side throttling — данные не должны теряться)

### E2: WebSocket disconnect during alert storm

**Проблема:** 30 alerts пока WebSocket был отключён.

**Решение:**
- При reconnect → REST sync подтягивает все пропущенные notifications
- Badge показывает точное число после sync
- Toast-ы для пропущенных НЕ показываются (они неактуальны по времени)
- Alert Events page обновляется при следующем visit/refresh

### E3: Concurrent mark-read

**Проблема:** user кликает «Отметить все» и одновременно приходит новый notification.

**Решение:**
- `POST /api/notifications/read-all` на backend помечает все текущие на момент запроса
- Новый notification (arrived after the request) остаётся unread
- Badge: optimistic set 0 → WebSocket push нового notification → badge = 1
- Race condition безопасна: worst case — user видит неожиданный «1» на badge после mark-all

### E4: Alert auto-resolved while Detail Panel open

**Проблема:** оператор смотрит Detail Panel alert-а, backend auto-resolves его.

**Решение:**
- WebSocket push: alert status change → update row в таблице (status badge changes)
- Detail Panel: action buttons обновляются (скрываются, т.к. alert уже resolved)
- Toast (SUCCESS): «Алерт закрыт автоматически: {title}»
- Inline notice в Detail Panel: «Этот алерт был закрыт автоматически» (info text, blue)

### E5: 0 notifications, 0 alerts (fresh workspace)

**Решение:**
- Bell: badge скрыт, dropdown показывает empty state (секция 1.11)
- Notifications page: empty state (секция 2.11)
- Alert Events page: green «Все системы работают штатно» (секция 3.11)
- Banner: скрыт
- Toast-ы: нет событий — нет toast-ов

### E6: Very long alert title/body

**Решение:**
- Toast: title 1 line truncate (ellipsis), body 2 lines max
- Dropdown item: title 1 line, body 1 line
- Table cell: truncate with ellipsis
- Detail Panel / expansion: полный текст, word-wrap

### E7: Multiple browser tabs

**Решение:**
- Каждая вкладка имеет свой WebSocket connection (стандартное поведение)
- Mark-read в одной вкладке → WebSocket НЕ пушит read status другим вкладкам (Phase G optimization)
- При переключении на другую вкладку → stale badge. Workaround: `GET /api/notifications/unread-count` при `document.visibilitychange` event

---

## Связанные документы

- [Frontend Design Direction](frontend-design-direction.md) — design system, component patterns, shell layout
- [Audit & Alerting module](../modules/audit-alerting.md) — data model, alert_rule, alert_event, user_notification, WebSocket, REST API
- [Integration module](../modules/integration.md) — marketplace connections, sync status
- [ETL Pipeline module](../modules/etl-pipeline.md) — job_execution, sync lifecycle
- [Execution module](../modules/execution.md) — action lifecycle, reconciliation
- [Pricing module](../modules/pricing.md) — automation blocker consumer
