# Navigation & Shell — Implementation Specification

**Parent document:** [Frontend Design Direction](frontend-design-direction.md)
**Status:** Implementation-level spec
**Angular version:** 19 (standalone components, signals)

---

## Complete Shell Layout

```
┌────────────────────────────────────────────────────────────────────────────────────┐
│ TOP BAR (40px)                                                                      │
│ ┌─────────────────────┬──────────────────────────────────┬─────────────────────────┐│
│ │ [dp] Мой бизнес ▾   │ Операции > Все товары             │ 🔍 Ctrl+K  🔔3  AK ▾  ││
│ │ logo  workspace      │ breadcrumbs                       │ search bell user       ││
│ └─────────────────────┴──────────────────────────────────┴─────────────────────────┘│
├──────┬─────────────────────────────────────────────────────┬────────────────────────┤
│ ACT. │ TAB BAR (36px)                                      │                        │
│ BAR  │ ┌──────────┬────────────┬──────────┐  ◂ ▸          │                        │
│(48px)│ │📌Все тов.│ Маржа > 25%│ SKU-1234 ×│               │    DETAIL PANEL       │
│      │ └──────────┴────────────┴──────────┘               │    (400px default)     │
│ ┌──┐ ├─────────────────────────────────────────────────────┤                        │
│ │🔲│ │                                                     │  Header: entity name × │
│ │  │ │                 MAIN CONTENT AREA                   │  ┌────┬────┬────┐     │
│ │📊│ │                                                     │  │Обзор│P&L │Ист.│     │
│ │  │ │  Grid / Table / Form / Chart / Analytics            │  └────┴────┴────┘     │
│ │🏷│ │                                                     │                        │
│ │  │ │  (fills remaining space)                            │  Dense key-value pairs │
│ │🎁│ │                                                     │  Mini tables           │
│ │  │ │                                                     │  Explanation blocks    │
│ │  │ │                                                     │                        │
│ │  │ ├─────────────────────────────────────────────────────┤                        │
│ │──│ │ BOTTOM PANEL (collapsible, 0-200px)                 │                        │
│ │⚙│ │ Bulk actions: 12 выбрано [Одобрить] [Отклонить] [×] │                        │
│ └──┘ │                                                     │                        │
├──────┴─────────────────────────────────────────────────────┴────────────────────────┤
│ STATUS BAR (24px)                                                                    │
│ ● Мой WB 12 мин назад  ● Ozon 3 мин назад    │  Мой бизнес  │  admin@example.com   │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

### Zone sizing constraints

| Zone | Height/Width | Min | Max | Resizable |
|------|-------------|-----|-----|-----------|
| Top Bar | 40px fixed | — | — | Нет |
| Activity Bar | 48px wide fixed | — | — | Нет |
| Tab Bar | 36px fixed | — | — | Нет |
| Main Content | Fills remaining | — | — | Растягивается |
| Detail Panel | 400px default width | 320px | 50% viewport | Да (drag handle, left edge) |
| Bottom Panel | 0px collapsed | 0px | 200px | Да (drag handle, top edge) |
| Status Bar | 24px fixed | — | — | Нет |

---

## 1. Activity Bar (левая иконочная панель)

### Назначение

Вертикальная панель с иконками по левому краю окна. Каждая иконка — переход к верхнеуровневому модулю. Аналог Activity Bar в VS Code / Cursor.

### Размеры и визуал

| Свойство | Значение |
|----------|----------|
| Ширина | 48px |
| Иконка | 20×20px, lucide-angular |
| Padding вокруг иконки | 14px vertical, centered horizontal |
| Фон | `--bg-secondary` (`#F9FAFB`) |
| Разделитель справа | 1px `--border-default` (`#E5E7EB`) |
| Разделитель модули/настройки | 1px горизонтальная линия `--border-default`, с отступом 8px слева/справа |

### Список модулей

Модули расположены сверху вниз. Настройки — всегда внизу, отделены разделителем.

| # | Модуль | Lucide Icon | Route | Tooltip (рус.) |
|---|--------|-------------|-------|----------------|
| 1 | Операции | `LayoutGrid` | `/workspace/:id/grid` | Операции |
| 2 | Аналитика | `BarChart3` | `/workspace/:id/analytics` | Аналитика |
| 3 | Ценообразование | `Tag` | `/workspace/:id/pricing` | Ценообразование |
| 4 | Промо | `Gift` | `/workspace/:id/promo` | Промо |
| — | *separator* | — | — | — |
| 5 | Настройки | `Settings` | `/workspace/:id/settings` | Настройки |

### Состояния иконки

| Состояние | Визуал |
|-----------|--------|
| Default | Иконка `--text-secondary` (`#6B7280`) |
| Hover | Фон `--bg-tertiary` (`#F3F4F6`), иконка `--text-primary` (`#111827`). Tooltip справа через 500ms delay |
| Active (текущий модуль) | Вертикальная полоса 2px `--accent-primary` (`#2563EB`) по левому краю иконки. Иконка `--accent-primary`. Фон `--accent-subtle` (`#EFF6FF`) |
| Focus (keyboard) | Focus ring 2px `--accent-primary`, offset -2px |

### Tooltip

- Позиция: справа от иконки, с зазором 8px
- Стиль: `--bg-primary` фон, `--text-primary` текст, `--shadow-md`, border-radius `--radius-md` (6px)
- Шрифт: `--text-sm` (13px), Inter
- Delay: 500ms показ, 0ms скрытие
- Текст: название модуля на русском

### Keyboard navigation

- `Tab` переводит фокус на Activity Bar (первый focusable элемент shell)
- `↑ / ↓` перемещает фокус между иконками
- `Enter` / `Space` активирует модуль
- Activity Bar получает `role="navigation"`, `aria-label="Основная навигация"`
- Каждая иконка: `role="tab"`, `aria-selected`, `aria-label="{module name}"`

---

## 2. Tab Management

### Назначение

Внутри каждого модуля открытые представления (views) отображаются как вкладки — аналог editor tabs в Cursor. Позволяет работать с несколькими представлениями параллельно.

### Визуал Tab Bar

| Свойство | Значение |
|----------|----------|
| Высота | 36px |
| Фон | `--bg-secondary` (`#F9FAFB`) |
| Нижняя граница | 1px `--border-default` |
| Шрифт tab label | `--text-sm` (13px), Inter, 400 weight |
| Max ширина tab | 200px |
| Min ширина tab | 80px |
| Padding внутри tab | 0 12px |

### Состояния вкладки

| Состояние | Визуал |
|-----------|--------|
| Default | Фон прозрачный, текст `--text-secondary`, нижняя граница нет |
| Hover | Фон `--bg-tertiary` |
| Active | Фон `--bg-primary` (белый), текст `--text-primary`, нижняя граница 2px `--accent-primary` |
| Pinned | Иконка 📌 (Pin, 12px, `--text-tertiary`) слева от label. Без кнопки закрытия |
| Modified (unsaved) | Точка 6px `--accent-primary` вместо кнопки закрытия |

### Кнопка закрытия вкладки

- Иконка: `X`, 14px, `--text-tertiary`
- Появляется при hover на вкладку ИЛИ когда вкладка active
- Hover на кнопку: фон `--bg-tertiary`, border-radius 4px
- Pinned вкладки не имеют кнопки закрытия

### Default tabs по модулям

| Модуль | Default tab | Описание | Pinned | Closeable |
|--------|-------------|----------|--------|-----------|
| Операции | Все товары | Operational Grid, no filters | Да (system) | Нет |
| Операции | Требуют внимания | Grid: `stock_risk IN [CRITICAL, WARNING]` OR `last_action_status = FAILED` | Нет | Да |
| Операции | В промо | Grid: `has_active_promo = true` | Нет | Да |
| Аналитика | P&L | P&L сводка | Да (system) | Нет |
| Ценообразование | Политики | Список ценовых политик | Да (system) | Нет |
| Промо | Акции | Список промо-кампаний | Да (system) | Нет |
| Настройки | Подключения | Список marketplace connections | Да (system) | Нет |

System-pinned вкладки: нельзя закрыть, нельзя unpin. Располагаются первыми (слева).

### User-created tabs

Пользователь создаёт новые вкладки в следующих сценариях:

| Сценарий | Как создаётся | Label вкладки |
|----------|---------------|---------------|
| Saved View активирован | Клик по saved view в списке views | Название saved view |
| Drill-down из грида | Клик на строку → "Открыть в новой вкладке" | `{SKU code} — {product name}` (truncated) |
| Drill-down из P&L | Переход на детальный отчёт | `P&L > По товарам` |
| Поиск через Command Palette | Выбор entity из результатов | Название entity |

### Tab behavior

| Действие | Поведение |
|----------|-----------|
| Клик на вкладку | Активация, отображение content |
| Двойной клик на вкладку | Pin/Unpin toggle |
| Drag вкладки | Reorder. Drop zone — между существующими tabs. Pinned tabs — reorder только среди pinned. Unpinned — только среди unpinned |
| Закрытие (×) | Закрывает вкладку. Если единственная непинованная — нет эффекта (system-pinned остаётся). Active tab закрывается → фокус переходит на ближайшую правую, если нет — на ближайшую левую |
| Middle-click на tab | Закрывает вкладку (аналог × кнопки) |
| Tab overflow | Горизонтальный скролл. Chevron стрелки (`ChevronLeft` / `ChevronRight`, 16px) появляются по краям при overflow |

### Tab context menu (right-click)

| Пункт меню | Действие | Доступность |
|------------|----------|-------------|
| Закрыть | Закрыть текущую вкладку | Если closeable |
| Закрыть другие | Закрыть все вкладки кроме текущей и pinned | Всегда |
| Закрыть все | Закрыть все непиннованные вкладки | Всегда |
| Закрепить / Открепить | Toggle pin state | Для user tabs |
| — | *separator* | — |
| Сохранить как представление | Открывает dialog для сохранения текущих фильтров/sort как saved view | Для grid tabs с активными фильтрами |

### Tab persistence

| Аспект | Поведение |
|--------|-----------|
| Storage | `sessionStorage` (ключ: `dp:tabs:{workspaceId}:{moduleKey}`) |
| Что хранится | Массив: `[{ id, label, type, route, pinned, viewId?, filters? }]` + `activeTabId` |
| Восстановление | При reload страницы — восстановить tabs из sessionStorage. При смене workspace — очистить |
| TTL | Время жизни browser session. Закрытие браузера — tabs теряются |
| Переключение модуля | Tabs per module — независимые. Переключение модуля сохраняет active tab каждого модуля |

---

## 3. Breadcrumbs (в Top Bar)

### Назначение

Отображают текущий путь навигации внутри модуля. Позволяют быстро вернуться на уровень выше.

### Визуал

| Свойство | Значение |
|----------|----------|
| Позиция | Центр Top Bar |
| Шрифт | `--text-sm` (13px), Inter, 400 |
| Цвет сегментов | `--text-secondary` (`#6B7280`), последний сегмент `--text-primary` |
| Разделитель | `ChevronRight` (lucide), 12px, `--text-tertiary` (`#9CA3AF`) |
| Hover на сегмент | Underline, cursor pointer |
| Max глубина | 3 уровня |
| Overflow | Первые сегменты скрываются → `...` > Последний уровень |

### Concrete paths по модулям

#### Операции

| Контекст | Breadcrumb |
|----------|------------|
| Operational Grid (all) | `Операции` |
| Saved View | `Операции > Маржа > 25% WB` |
| Offer detail (panel) | `Операции > Все товары > SKU-1234` |
| Working Queue | `Операции > Очереди > Failed Actions` |
| Price Journal | `Операции > SKU-1234 > Журнал цен` |
| Promo Journal | `Операции > SKU-1234 > Журнал промо` |
| Mismatch Monitor | `Операции > Расхождения` |

#### Аналитика

| Контекст | Breadcrumb |
|----------|------------|
| P&L сводка | `Аналитика > P&L` |
| P&L по товарам | `Аналитика > P&L > По товарам` |
| P&L по категориям | `Аналитика > P&L > По категориям` |
| Inventory analysis | `Аналитика > Остатки` |
| Returns analysis | `Аналитика > Возвраты` |

#### Ценообразование

| Контекст | Breadcrumb |
|----------|------------|
| Policy list | `Ценообразование > Политики` |
| Policy detail | `Ценообразование > Политики > Маржа 25% WB` |
| Policy create | `Ценообразование > Политики > Новая политика` |
| Pricing runs | `Ценообразование > Запуски` |

#### Промо

| Контекст | Breadcrumb |
|----------|------------|
| Campaign list | `Промо > Акции` |
| Campaign detail | `Промо > Акции > Весенняя распродажа` |
| Promo evaluation | `Промо > Акции > Весенняя распродажа > Оценка` |

#### Настройки

| Контекст | Breadcrumb |
|----------|------------|
| Settings root | `Настройки` |
| Connections | `Настройки > Подключения` |
| Connection detail | `Настройки > Подключения > Мой кабинет WB` |
| Members | `Настройки > Участники` |
| Alert rules | `Настройки > Уведомления` |
| Audit log | `Настройки > Журнал аудита` |
| Workspace | `Настройки > Рабочее пространство` |

### Click behavior

Клик на любой сегмент (кроме последнего) выполняет навигацию на соответствующий route. Последний сегмент — неактивный, отображает текущее положение.

---

## 4. Command Palette (Ctrl+K)

### Назначение

Глобальный поиск и быстрые команды. Аналог Command Palette в VS Code / Cursor. Позволяет найти товар, политику, представление или выполнить команду без мыши.

### Визуал

```
┌──────────────────────────────────────────────────────┐
│  🔍  Поиск товаров, политик, команд...          ESC │
├──────────────────────────────────────────────────────┤
│                                                      │
│  ТОВАРЫ                                              │
│  ┌────────────────────────────────────────────────┐  │
│  │ 🔲  ABC-001 — Футболка синяя (WB)            │  │
│  │ 🔲  DEF-002 — Кроссовки белые (Ozon)         │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│  ПОЛИТИКИ                                            │
│  ┌────────────────────────────────────────────────┐  │
│  │ 🏷  Маржа 25% WB                              │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│  ПРЕДСТАВЛЕНИЯ                                       │
│  ┌────────────────────────────────────────────────┐  │
│  │ 📋  Высокая маржа WB                          │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│  КОМАНДЫ                                             │
│  ┌────────────────────────────────────────────────┐  │
│  │ ▸  Запустить синхронизацию                     │  │
│  │ ▸  Создать ценовую политику                    │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
└──────────────────────────────────────────────────────┘
```

### Размеры и стилистика

| Свойство | Значение |
|----------|----------|
| Ширина | 560px |
| Max высота dropdown | 420px (с прокруткой) |
| Позиция | Центр экрана по горизонтали, top: 20% viewport height |
| Фон | `--bg-primary` (`#FFFFFF`) |
| Тень | `--shadow-md` (`0 4px 12px rgba(0,0,0,0.08)`) |
| Border | 1px `--border-default` |
| Border-radius | `--radius-lg` (8px) |
| Backdrop | Semi-transparent overlay `rgba(0,0,0,0.15)`, click → close |
| Input height | 44px |
| Input font | `--text-base` (14px), Inter |
| Input placeholder | `"Поиск товаров, политик, команд..."`, `--text-tertiary` |
| Result item height | 36px |
| Result group header | `--text-xs` (11px), `--text-tertiary`, uppercase, letter-spacing 0.5px |

### Result groups

Результаты группируются в фиксированном порядке. Пустые группы не отображаются.

| Группа | Ключ | Иконка (lucide) | Что ищет | Max results |
|--------|------|-----------------|----------|-------------|
| Товары | `products` | `Package` | `marketplace_offer` по SKU code, product name | 5 |
| Политики | `policies` | `Tag` | `price_policy` по name | 3 |
| Промо-акции | `promos` | `Gift` | `canonical_promo_campaign` по name | 3 |
| Представления | `views` | `LayoutList` | `saved_view` по name | 3 |
| Команды | `commands` | `ChevronRight` | Статический список команд | 5 |

### Статические команды

Команды не зависят от поискового запроса — всегда доступны при пустом вводе или при частичном совпадении.

| Команда (label) | Действие | Shortcut hint |
|-----------------|----------|---------------|
| Перейти к операциям | Navigate → `/workspace/:id/grid` | — |
| Перейти к аналитике | Navigate → `/workspace/:id/analytics` | — |
| Перейти к ценообразованию | Navigate → `/workspace/:id/pricing` | — |
| Перейти к промо | Navigate → `/workspace/:id/promo` | — |
| Перейти к настройкам | Navigate → `/workspace/:id/settings` | — |
| Запустить синхронизацию | Navigate → Settings > Connections, trigger sync dialog | — |
| Создать ценовую политику | Navigate → `/workspace/:id/pricing/policies/new` | — |
| Создать представление | Open save view dialog в текущем grid | Ctrl+S |

### Keyboard interaction

| Клавиша | Действие |
|---------|----------|
| `Ctrl+K` | Открыть Command Palette. Если уже открыт — фокус на input |
| `↑` / `↓` | Навигация по результатам (пропускает group headers) |
| `Enter` | Выбрать highlighted результат → закрыть palette → navigate |
| `Escape` | Закрыть Command Palette |
| Typing | Debounce 300ms → API call `GET /api/workspace/:id/search?q={query}` |

### Поведение поиска

- Минимальная длина запроса для API-вызова: 2 символа
- При 0-1 символах: показать только команды (статический список)
- Результаты обновляются при каждом изменении input (debounce 300ms)
- Loading state: shimmer placeholder в dropdown при ожидании ответа
- Empty state: `"Ничего не найдено по запросу «{query}»"`, `--text-secondary`

### Backend API (TBD)

```
GET /api/workspace/{workspaceId}/search?q={query}&limit=20

Response:
{
  "products": [{ "offerId": 123, "skuCode": "ABC-001", "productName": "...", "marketplaceType": "WB" }],
  "policies": [{ "policyId": 42, "name": "Маржа 25% WB" }],
  "promos": [{ "campaignId": 7, "name": "Весенняя распродажа" }],
  "views": [{ "viewId": 5, "name": "Высокая маржа WB" }]
}
```

Эндпоинт ещё не специфицирован на backend — TBD при реализации Phase E.

---

## 5. Status Bar (нижняя панель состояния)

### Назначение

Постоянно видимая строка внизу экрана. Показывает здоровье data connections (свежесть данных), активный workspace и текущего пользователя.

### Визуал

| Свойство | Значение |
|----------|----------|
| Высота | 24px |
| Фон | `--bg-secondary` (`#F9FAFB`) |
| Верхняя граница | 1px `--border-default` |
| Шрифт | `--text-xs` (11px), Inter, 400 |
| Цвет текста | `--text-secondary` (`#6B7280`) |
| Layout | CSS Grid: `auto 1fr auto` (left / center / right) |
| Padding horizontal | 12px |

### Layout

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ ● Мой WB 12 мин назад  ● Ozon 3 мин назад      Мой бизнес    ak@mail.ru   │
│ ←──── left (connections) ────→              ←center→       ←── right ──→    │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Left zone: Connection sync freshness

Для каждого active `marketplace_connection` workspace отображается индикатор:

```
● {connection_name} {relative_time}
```

| Элемент | Описание |
|---------|----------|
| Dot (●) | Circle 6px, цвет по семантике (см. ниже) |
| Connection name | `marketplace_connection.name`, truncated до 20 символов |
| Relative time | Время с момента последней успешной синхронизации: `"3 мин назад"`, `"1 ч назад"`, `"2 дн назад"` |

#### Dot color semantics

| Цвет | CSS token | Условие | Tooltip |
|------|-----------|---------|---------|
| Зелёный | `--status-success` (`#059669`) | `last_success_at` < 1 час назад | `"Синхронизировано {absolute_time}"` |
| Жёлтый | `--status-warning` (`#D97706`) | `last_success_at` 1–24 часа назад | `"Данные устаревают. Последняя синхронизация: {absolute_time}"` |
| Красный | `--status-error` (`#DC2626`) | `last_success_at` > 24 часов назад ИЛИ `sync_state.status = ERROR` | `"Синхронизация не удалась. Последняя успешная: {absolute_time}"` |

Если нет ни одного connection → left zone пуста (скрыта).

Если connections > 3 → показать первые 2 + `"и ещё {N}"` (click → dropdown с полным списком).

#### Click behavior

Клик на connection dot → навигация к `Настройки > Подключения > {connection_name}` (route: `/workspace/:id/settings/connections/:connectionId`).

#### Data source

WebSocket topic `/topic/workspace/{workspaceId}/sync-status` обновляет индикаторы в реальном времени (тело: `WorkspaceSyncStatusPush` — см. integration API). Initial load: `GET /api/connections/sync-health` (агрегированный health по подключениям).

### Center zone: Workspace name

| Элемент | Описание |
|---------|----------|
| Текст | `workspace.name`, `--text-secondary`, centered |
| Overflow | Truncate с `...`, max 30 символов |

### Right zone: Current user

| Элемент | Описание |
|---------|----------|
| Текст | `app_user.email`, `--text-secondary`, right-aligned |
| Overflow | Truncate с `...`, max 30 символов |

---

## 6. Top Bar

### Назначение

Верхняя панель shell: содержит workspace switcher, breadcrumbs, глобальный поиск, уведомления и user menu.

### Визуал

| Свойство | Значение |
|----------|----------|
| Высота | 40px |
| Фон | `--bg-primary` (`#FFFFFF`) |
| Нижняя граница | 1px `--border-default` |
| Layout | CSS Grid: `240px 1fr auto` (left / center / right) |
| Vertical align | center |
| Padding horizontal | 12px |

### Left zone: Logo + Workspace Switcher

```
┌─────────────────────────┐
│ [dp]  Мой бизнес  ▾     │
│ logo  workspace-name    │
└─────────────────────────┘
```

| Элемент | Описание |
|---------|----------|
| Logo | Datapulse logomark, 20×20px. Не кликабельный (не ведёт на landing) |
| Workspace name | `--text-base` (14px), `--text-primary`, 600 weight. Truncate до 20 символов |
| Chevron | `ChevronDown` (lucide), 14px, `--text-tertiary` |
| Gap logo → name | 8px |
| Gap name → chevron | 4px |

#### Workspace Switcher Dropdown

Открывается при клике на workspace name area.

```
┌─────────────────────────────────────┐
│  Рабочие пространства               │
├─────────────────────────────────────┤
│  ✓ Мой бизнес          3 подкл.    │
│    Второй магазин       1 подкл.    │
│    Тестовый workspace   0 подкл.    │
├─────────────────────────────────────┤
│  Управление рабочими пространствами │
└─────────────────────────────────────┘
```

| Свойство | Значение |
|----------|----------|
| Ширина | 280px |
| Max height | 360px (scrollable) |
| Фон | `--bg-primary` |
| Тень | `--shadow-md` |
| Border | 1px `--border-default` |
| Border-radius | `--radius-md` (6px) |
| Item height | 40px |

| Элемент item | Описание |
|--------------|----------|
| Checkmark | `Check` (lucide), 14px, `--accent-primary` — только для current workspace |
| Workspace name | `--text-base`, `--text-primary` |
| Connection count | `--text-xs`, `--text-tertiary`, right-aligned: `"N подкл."` |
| Hover | Фон `--bg-tertiary` |

**Footer link:** `"Управление рабочими пространствами"` — `--text-sm`, `--accent-primary`. Клик → navigate `/workspaces` (workspace selector screen).

**Switching behavior:** клик на другой workspace → full context reload:
1. Clear all tabs (sessionStorage per workspace)
2. Reset filters and sort state
3. Navigate to default tab of current module
4. Reload data (new workspace context)
5. WebSocket reconnect with new workspace subscription

### Center zone: Breadcrumbs

См. §3 Breadcrumbs выше. Breadcrumbs отображаются в центре Top Bar, vertically centered.

### Right zone: Search trigger + Notifications + User Menu

```
┌──────────────────────────────────────┐
│            🔍 Ctrl+K    🔔 3   AK ▾ │
└──────────────────────────────────────┘
```

Layout: `flex`, `gap: 8px`, `align-items: center`.

#### Search trigger button

| Элемент | Описание |
|---------|----------|
| Иконка | `Search` (lucide), 16px |
| Label | `"Ctrl+K"`, `--text-xs`, `--text-tertiary`, border 1px `--border-default`, border-radius 4px, padding 2px 6px |
| Стиль | Ghost button |
| Click | Открывает Command Palette |
| Hover | Фон `--bg-tertiary` |

#### Notification bell

| Элемент | Описание |
|---------|----------|
| Иконка | `Bell` (lucide), 18px, `--text-secondary` |
| Badge (unread count) | Красный круг 16px, белый текст `--text-xs` (11px), bold. Позиция: top-right от bell icon, offset -4px/-4px |
| Badge > 99 | Показывать `"99+"` |
| Badge = 0 | Скрыть badge |
| Click | Toggle notification dropdown (overlay panel) |
| Hover | Фон `--bg-tertiary`, border-radius 4px |

##### Notification dropdown

```
┌──────────────────────────────────────────┐
│  Уведомления                  Прочитать все │
├──────────────────────────────────────────┤
│ 🔴 Синхронизация не удалась              │
│    Мой WB — ошибка FINANCE domain        │
│    5 мин назад                           │
├──────────────────────────────────────────┤
│ 🟡 Данные устаревают                     │
│    Ozon — CATALOG > 24 ч без обновления  │
│    2 ч назад                             │
├──────────────────────────────────────────┤
│ 🟢 Синхронизация завершена               │
│    Мой WB — все домены обновлены         │
│    3 ч назад                             │
├──────────────────────────────────────────┤
│            Показать все                   │
└──────────────────────────────────────────┘
```

| Свойство | Значение |
|----------|----------|
| Ширина | 360px |
| Max height | 480px (scrollable) |
| Позиция | Right-aligned под bell icon |
| Max items | 10 последних, затем "Показать все" |
| Фон | `--bg-primary` |
| Тень | `--shadow-md` |

| Элемент notification item | Описание |
|---------------------------|----------|
| Severity dot | 6px circle: CRITICAL → `--status-error`, WARNING → `--status-warning`, INFO → `--status-info` |
| Title | `--text-sm`, `--text-primary`, 500 weight |
| Body | `--text-xs`, `--text-secondary`, max 2 lines, truncate |
| Time | `--text-xs`, `--text-tertiary` |
| Unread indicator | Left border 2px `--accent-primary` для unread items |
| Click on item | Mark as read (`POST /api/notifications/{id}/read`) + navigate to relevant entity |
| Hover | Фон `--bg-tertiary` |

**Header:** `"Уведомления"` (`--text-sm`, 600 weight) + `"Прочитать все"` (`--text-xs`, `--accent-primary`, click → `POST /api/notifications/read-all`).

**Footer:** `"Показать все"` (`--text-sm`, `--accent-primary`, click → navigate to full notification page или scroll within dropdown).

**Data source:** Initial load: `GET /api/notifications?size=10` + `GET /api/notifications/unread-count`. Realtime: WebSocket `/user/queue/notifications`.

#### User Menu

| Элемент | Описание |
|---------|----------|
| Avatar | Инициалы пользователя (первые буквы имени и фамилии), circle 28px, фон `--accent-subtle`, текст `--accent-primary`, `--text-xs`, 600 weight |
| Chevron | `ChevronDown`, 12px, `--text-tertiary` |
| Click | Toggle user menu dropdown |

##### User menu dropdown

```
┌────────────────────────────┐
│  Анна Кузнецова            │
│  anna@example.com          │
├────────────────────────────┤
│  👤  Профиль               │
│  🚪  Выйти                │
└────────────────────────────┘
```

| Свойство | Значение |
|----------|----------|
| Ширина | 220px |
| Позиция | Right-aligned под avatar |
| Фон | `--bg-primary`, `--shadow-md` |
| Header | Name (`--text-sm`, 600 weight, `--text-primary`) + Email (`--text-xs`, `--text-tertiary`). Padding 12px |

| Пункт меню | Иконка (lucide) | Действие |
|------------|-----------------|----------|
| Профиль | `User` | Navigate → user profile modal/page |
| Выйти | `LogOut` | Logout: clear tokens, redirect to Keycloak logout endpoint |

Menu items: `--text-sm`, `--text-primary`, height 36px, padding 8px 12px. Hover: `--bg-tertiary`.

---

## 7. Responsive Behavior

### Breakpoints

| Viewport width | Behavior | Layout changes |
|----------------|----------|----------------|
| ≥ 1440px | **Full layout** | All zones visible. Detail Panel pushes Main Area. Side-by-side |
| 1280–1439px | **Compact layout** | Detail Panel opens as overlay (position: fixed, right: 0) с backdrop `rgba(0,0,0,0.1)`. Не push — overlay. Activity Bar: 48px (без изменений) |
| < 1280px | **Not supported** | Показать full-screen message (см. ниже) |

### Message для неподдерживаемых viewport

При `viewport.width < 1280px` — вместо shell показать centered message:

```
┌───────────────────────────────────────────┐
│                                           │
│          [dp] Datapulse                   │
│                                           │
│   Datapulse предназначен для экранов      │
│   1280px и шире.                          │
│                                           │
│   Пожалуйста, используйте компьютер      │
│   с большим экраном для работы            │
│   с платформой.                           │
│                                           │
└───────────────────────────────────────────┘
```

| Свойство | Значение |
|----------|----------|
| Фон | `--bg-primary` |
| Logo | Datapulse logomark + name, centered |
| Текст | `--text-base`, `--text-secondary`, text-align center |
| Max width | 400px, centered vertically и horizontally |

### Detail Panel responsive behavior

| Viewport | Detail Panel mode |
|----------|-------------------|
| ≥ 1440px | Push mode: Main Area сжимается, Panel рядом |
| 1280–1439px | Overlay mode: Panel поверх Main Area с backdrop. Close on backdrop click |

### Activity Bar

Не collapse'ится ни при каком viewport ≥ 1280px. Всегда 48px.

---

## 8. Global Keyboard Shortcuts

### Полная таблица

| Shortcut | Scope | Действие | Условие |
|----------|-------|----------|---------|
| `Ctrl+K` | Global | Открыть/закрыть Command Palette | Всегда |
| `Escape` | Global | Закрыть: Command Palette → Detail Panel → Modal → Dropdown (в порядке приоритета) | Есть открытый overlay |
| `Ctrl+S` | Grid context | Сохранить текущий view (открывает save dialog если новый) | Активный tab — grid с изменёнными фильтрами |
| `Ctrl+F` | Grid context | Focus на filter bar (input field для добавления фильтра) | Активный tab — grid |
| `Ctrl+E` | Grid context | Export текущего view | Активный tab — grid |
| `↑` / `↓` | Grid context | Навигация по строкам грида | Фокус в grid area |
| `Enter` | Grid context | Открыть Detail Panel для выбранной строки | Строка грида selected |
| `Space` | Grid context | Toggle checkbox на выбранной строке | Фокус в grid area |
| `Ctrl+A` | Grid context | Select all visible rows (checkboxes) | Фокус в grid area |
| `Ctrl+W` | Tab context | Закрыть активную вкладку | Активная вкладка closeable |
| `Ctrl+Tab` | Tab context | Следующая вкладка | Есть > 1 tab |
| `Ctrl+Shift+Tab` | Tab context | Предыдущая вкладка | Есть > 1 tab |
| `Ctrl+1..5` | Module context | Переключение модуля (1=Операции, 2=Аналитика, 3=Ценообразование, 4=Промо, 5=Настройки) | Всегда |
| `?` | Global | Открыть cheat sheet (overlay с таблицей горячих клавиш) | Фокус не в text input |

### Приоритет обработки

Если shortcut конфликтует с browser default:
1. `Ctrl+K` — перехватывается (preventDefault). Browser search не открывается
2. `Ctrl+S` — перехватывается. Сохранение страницы не происходит
3. `Ctrl+F` — перехватывается. Browser search не открывается (используем свой filter bar)
4. `Ctrl+W` — **НЕ** перехватывается (browser tab close). Закрытие вкладки доступно через ×, middle-click или context menu

Коррекция: `Ctrl+W` убирается из shortcut таблицы, заменяется на интерфейсное взаимодействие (× кнопка, middle-click, context menu).

### Cheat sheet overlay (`?`)

```
┌──────────────────────────────────────────────────────┐
│  Горячие клавиши                               ESC  │
├──────────────────────────────────────────────────────┤
│                                                      │
│  НАВИГАЦИЯ                                           │
│  Ctrl+K         Поиск и команды                      │
│  Ctrl+1..5      Переключить модуль                   │
│  Ctrl+Tab       Следующая вкладка                    │
│                                                      │
│  ГРИД                                                │
│  ↑ / ↓          Навигация по строкам                 │
│  Enter          Открыть детали                       │
│  Space          Выбрать строку                       │
│  Ctrl+A         Выбрать все                          │
│  Ctrl+F         Фокус на фильтры                    │
│  Ctrl+S         Сохранить представление              │
│  Ctrl+E         Экспорт                              │
│                                                      │
│  ОБЩИЕ                                               │
│  Escape         Закрыть панель/окно                  │
│  ?              Эта справка                          │
│                                                      │
└──────────────────────────────────────────────────────┘
```

---

## 9. WebSocket Connection

### Назначение

Persistent WebSocket connection для real-time обновлений: уведомления, статус синхронизации, изменение статусов actions, обновление grid rows.

### Технология

- Protocol: STOMP over WebSocket (SockJS fallback)
- Client library: `@stomp/rx-stomp`
- Server endpoint: `/ws` (datapulse-api)

### Connection lifecycle

```
Shell mount (AppShellComponent.ngOnInit)
  │
  ├─ 1. Get JWT token from auth service
  │
  ├─ 2. Connect: new RxStomp().configure({
  │       brokerURL: 'wss://{host}/ws?token={jwt}',
  │       reconnectDelay: 1000
  │     })
  │
  ├─ 3. On CONNECTED:
  │     ├─ Subscribe: /topic/workspace/{workspaceId}/alerts
  │     ├─ Subscribe: /topic/workspace/{workspaceId}/sync-status
  │     ├─ Subscribe: /topic/workspace/{workspaceId}/actions
  │     └─ Subscribe: /user/queue/notifications
  │
  ├─ 4. On MESSAGE → dispatch to appropriate SignalStore
  │
  ├─ 5. On DISCONNECT → show reconnecting banner
  │
  └─ 6. On Shell destroy → deactivate RxStomp
```

### Reconnection strategy

При потере connection — exponential backoff:

| Attempt | Delay | Max |
|---------|-------|-----|
| 1 | 1s | — |
| 2 | 2s | — |
| 3 | 4s | — |
| 4 | 8s | — |
| 5 | 16s | — |
| 6+ | 30s | 30s (cap) |

Jitter: ±20% от computed delay (предотвращает thundering herd).

### Connection lost banner

При потере WebSocket — отображается persistent banner в верхней части Main Area (под Tab Bar):

```
┌────────────────────────────────────────────────────────────────┐
│ ⚠  Соединение потеряно. Переподключение...                    │
└────────────────────────────────────────────────────────────────┘
```

| Свойство | Значение |
|----------|----------|
| Высота | 32px |
| Фон | `--status-warning` с opacity 0.1 (`rgba(217, 119, 6, 0.1)`) |
| Left border | 3px `--status-warning` |
| Текст | `--text-sm`, `--text-primary` |
| Иконка | `AlertTriangle` (lucide), 14px, `--status-warning` |
| Позиция | Фиксировано под Tab Bar, full width Main Area |
| Dismiss | Автоматически при reconnect |

### Post-reconnect sync

После успешного reconnect:
1. Dismiss banner
2. `GET /api/notifications?since={lastSeenTimestamp}` — подгрузить пропущенные уведомления
3. `GET /api/connections` — refresh sync status в Status Bar
4. Если grid active — refresh current page data (silent, без skeleton)

### Token refresh during WS session

JWT access token имеет TTL 5 минут. WebSocket connection может жить дольше.

Strategy:
- Handshake authentication — одноразовая при connect
- При token refresh (silent renew через angular-oauth2-oidc) — WS connection остаётся живой
- Если server отклонит message из-за expired token → DISCONNECT → reconnect с новым token
- `@stomp/rx-stomp` `beforeConnect` callback: refresh token перед каждым reconnect attempt

---

## Routing Table

### Полная таблица маршрутов

| Route | Module | Component | Guard | Lazy |
|-------|--------|-----------|-------|------|
| `/login` | Auth | — (redirect to Keycloak) | — | — |
| `/callback` | Auth | OAuthCallbackComponent | — | Нет |
| `/workspaces` | Tenancy | WorkspaceSelectorComponent | `authGuard` | Нет |
| `/onboarding` | Tenancy | OnboardingComponent | `authGuard`, `needsOnboardingGuard` | Да |
| `/workspace/:workspaceId` | Shell | ShellComponent (wrapper) | `authGuard`, `workspaceMemberGuard` | Нет |
| `/workspace/:workspaceId/grid` | Операции | GridPageComponent | — | Да |
| `/workspace/:workspaceId/grid/queues` | Операции | WorkingQueuesComponent | — | Да |
| `/workspace/:workspaceId/grid/queues/:queueId` | Операции | QueueDetailComponent | — | Да |
| `/workspace/:workspaceId/grid/mismatches` | Операции | MismatchMonitorComponent | — | Да |
| `/workspace/:workspaceId/analytics` | Аналитика | AnalyticsLayoutComponent | — | Да |
| `/workspace/:workspaceId/analytics/pnl` | Аналитика | PnlComponent | — | Да |
| `/workspace/:workspaceId/analytics/pnl/products` | Аналитика | PnlByProductsComponent | — | Да |
| `/workspace/:workspaceId/analytics/pnl/categories` | Аналитика | PnlByCategoriesComponent | — | Да |
| `/workspace/:workspaceId/analytics/inventory` | Аналитика | InventoryAnalysisComponent | — | Да |
| `/workspace/:workspaceId/analytics/returns` | Аналитика | ReturnsAnalysisComponent | — | Да |
| `/workspace/:workspaceId/pricing` | Ценообразование | PricingLayoutComponent | — | Да |
| `/workspace/:workspaceId/pricing/policies` | Ценообразование | PolicyListComponent | — | Да |
| `/workspace/:workspaceId/pricing/policies/new` | Ценообразование | PolicyCreateComponent | `roleGuard(PRICING_MANAGER)` | Да |
| `/workspace/:workspaceId/pricing/policies/:policyId` | Ценообразование | PolicyDetailComponent | — | Да |
| `/workspace/:workspaceId/pricing/runs` | Ценообразование | PricingRunsComponent | — | Да |
| `/workspace/:workspaceId/promo` | Промо | PromoLayoutComponent | — | Да |
| `/workspace/:workspaceId/promo/campaigns` | Промо | CampaignListComponent | — | Да |
| `/workspace/:workspaceId/promo/campaigns/:campaignId` | Промо | CampaignDetailComponent | — | Да |
| `/workspace/:workspaceId/settings` | Настройки | SettingsLayoutComponent | — | Да |
| `/workspace/:workspaceId/settings/connections` | Настройки | ConnectionListComponent | — | Да |
| `/workspace/:workspaceId/settings/connections/:connectionId` | Настройки | ConnectionDetailComponent | — | Да |
| `/workspace/:workspaceId/settings/members` | Настройки | MemberListComponent | `roleGuard(ADMIN)` | Да |
| `/workspace/:workspaceId/settings/alerts` | Настройки | AlertRulesComponent | `roleGuard(ADMIN)` | Да |
| `/workspace/:workspaceId/settings/audit` | Настройки | AuditLogComponent | `roleGuard(ADMIN)` | Да |
| `/workspace/:workspaceId/settings/workspace` | Настройки | WorkspaceSettingsComponent | `roleGuard(ADMIN)` | Да |
| `**` | — | NotFoundComponent | — | — |

### Route Guards

| Guard | Описание |
|-------|----------|
| `authGuard` | Проверяет наличие valid JWT. Redirect → `/login` при отсутствии |
| `workspaceMemberGuard` | Проверяет membership текущего user в workspace из route param. 403 при отсутствии |
| `needsOnboardingGuard` | Проверяет наличие хотя бы одного workspace. Redirect → `/onboarding` если нет |
| `roleGuard(minRole)` | Проверяет роль user в текущем workspace >= minRole. 403 при недостаточных правах |

### Default redirects

| From | To | Условие |
|------|----|---------|
| `/` | `/workspaces` | Authenticated, multiple workspaces |
| `/` | `/workspace/:lastUsedId/grid` | Authenticated, single workspace OR last used stored |
| `/workspace/:id` | `/workspace/:id/grid` | Default module — Операции |
| `/workspace/:id/analytics` | `/workspace/:id/analytics/pnl` | Default sub-route |
| `/workspace/:id/pricing` | `/workspace/:id/pricing/policies` | Default sub-route |
| `/workspace/:id/promo` | `/workspace/:id/promo/campaigns` | Default sub-route |
| `/workspace/:id/settings` | `/workspace/:id/settings/connections` | Default sub-route |

### Lazy loading strategy

Каждый модуль — отдельный lazy-loaded chunk:

| Chunk | Routes | Estimated size |
|-------|--------|----------------|
| `operations` | `/workspace/:id/grid/**` | ~200KB |
| `analytics` | `/workspace/:id/analytics/**` | ~300KB (charts) |
| `pricing` | `/workspace/:id/pricing/**` | ~150KB |
| `promo` | `/workspace/:id/promo/**` | ~120KB |
| `settings` | `/workspace/:id/settings/**` | ~100KB |
| `shell` | Shell layout, Activity Bar, Top Bar, Status Bar | ~80KB (eager) |
| `auth` | Login, callback, workspace selector | ~40KB (eager) |

---

## Angular Component Structure

### Shell hierarchy

```
AppComponent
  ├── AuthCallbackComponent        (route: /callback)
  ├── WorkspaceSelectorComponent   (route: /workspaces)
  ├── OnboardingComponent          (route: /onboarding)
  └── ShellComponent               (route: /workspace/:workspaceId)
        ├── TopBarComponent
        │     ├── WorkspaceSwitcherComponent
        │     ├── BreadcrumbsComponent
        │     ├── SearchTriggerComponent
        │     ├── NotificationBellComponent
        │     │     └── NotificationDropdownComponent
        │     └── UserMenuComponent
        ├── ActivityBarComponent
        ├── TabBarComponent
        │     └── TabItemComponent (×N)
        ├── MainContentArea
        │     └── <router-outlet>  (module content)
        ├── DetailPanelComponent
        │     ├── DetailPanelHeaderComponent
        │     └── <ng-content> (entity-specific content)
        ├── BottomPanelComponent
        │     └── BulkActionsBarComponent
        ├── StatusBarComponent
        │     └── ConnectionStatusDotComponent (×N)
        └── CommandPaletteComponent (overlay)
              ├── CommandInputComponent
              └── CommandResultListComponent
```

### Shared services (shell-level)

| Service | Scope | Описание |
|---------|-------|----------|
| `WebSocketService` | Singleton | RxStomp lifecycle, reconnection, message routing |
| `NotificationStore` | SignalStore | Unread count, notification list, mark read |
| `SyncStatusStore` | SignalStore | Connection freshness dots, updated via WebSocket |
| `TabStore` | SignalStore per module | Tab state, persistence to sessionStorage |
| `BreadcrumbService` | Singleton | Breadcrumb segments, updated by route changes |
| `CommandPaletteService` | Singleton | Open/close state, search API calls |
| `ShortcutService` | Singleton | Global keyboard shortcut registration and dispatch |
| `WorkspaceContextService` | Singleton | Current workspace, workspace switching |
| `DetailPanelService` | Singleton | Open/close state, current entity, panel width |

---

## Connection Lost & Error States

### WebSocket connection states

| State | Banner | Status Bar | User action |
|-------|--------|------------|-------------|
| Connected | Нет | Normal dots | — |
| Reconnecting | `"⚠ Соединение потеряно. Переподключение..."` (yellow) | Dots freeze (last known state) | Ждать |
| Reconnected | Banner auto-dismiss | Dots update | — |
| Failed (> 5 min) | `"⚠ Не удаётся подключиться. Данные могут быть неактуальны."` (red) | Dots gray | Manual page reload |

### No internet

| State | Banner |
|-------|--------|
| Offline | `"❌ Нет подключения к интернету. Изменения не будут сохранены."` — red background, persistent |
| Back online | Banner auto-dismiss, trigger data refresh |

Detection: `window.addEventListener('online'/'offline')` + periodic heartbeat.

---

## Accessibility

### ARIA roles and labels

| Component | Role | aria-label |
|-----------|------|------------|
| Activity Bar | `navigation` | `"Основная навигация"` |
| Activity Bar item | `tab` | `"{module name}"` |
| Tab Bar | `tablist` | `"Открытые представления"` |
| Tab item | `tab` | `"{tab label}"` |
| Tab close button | `button` | `"Закрыть вкладку {tab label}"` |
| Breadcrumb nav | `navigation` | `"Навигация по разделам"` |
| Breadcrumb segment | `link` | — (text content sufficient) |
| Notification bell | `button` | `"Уведомления, {N} непрочитанных"` |
| User menu trigger | `button` | `"Меню пользователя"` |
| Command Palette input | `combobox` | `"Поиск"` |
| Command Palette results | `listbox` | `"Результаты поиска"` |
| Status Bar | `status` | `"Статус системы"` |

### Focus management

- `Ctrl+K` → focus на Command Palette input
- `Escape` в Command Palette → focus возвращается на элемент, который был focused до открытия
- Tab switching → focus на Main Content area
- Detail Panel open → focus на panel header (close button)
- Detail Panel close → focus возвращается на grid row, которая вызвала panel

---

## Related documents

- [Frontend Design Direction](frontend-design-direction.md) — abstract design language, component patterns, color system
- [Seller Operations](../modules/seller-operations.md) — Operational Grid, saved views, working queues
- [Audit & Alerting](../modules/audit-alerting.md) — WebSocket/STOMP architecture, notifications, alert lifecycle
- [Tenancy & IAM](../modules/tenancy-iam.md) — workspace switcher, user profile, roles
- [Integration](../modules/integration.md) — connection health, sync status
