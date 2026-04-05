# Страница: Operational Grid (главный рабочий экран)

**Фаза:** E — Seller Operations
**Route:** `/workspace/:workspaceId/grid`
**Модульная документация:** [seller-operations.md](../modules/seller-operations.md), [pricing.md](../modules/pricing.md), [execution.md](../modules/execution.md), [promotions.md](../modules/promotions.md), [analytics-pnl.md](../modules/analytics-pnl.md)
**Дизайн-система:** [frontend-design-direction.md](frontend-design-direction.md)

---

## 1. Назначение

Operational Grid — **основной рабочий экран** Datapulse. Аналог «редактора кода» в IDE-метафоре Cursor. Оператор проводит здесь 80%+ рабочего времени: просматривает товары, оценивает маржу, реагирует на алерты, одобряет ценовые действия, управляет блокировками.

Каждая строка = один `marketplace_offer` (предложение конкретного товара на конкретном маркетплейсе через конкретное подключение). Один SKU на WB и Ozon — **две строки**.

---

## 2. Route и навигация

| Свойство | Значение |
|----------|----------|
| Route | `/workspace/:workspaceId/grid` |
| Route (с view) | `/workspace/:workspaceId/grid?viewId=5` |
| Activity Bar иконка | Сетка (grid icon), первая позиция |
| Breadcrumbs | `Товары > {Название текущего view}` |
| Default tab | «Все товары» (system view) |

### Entry points

- **Activity Bar → иконка «Товары»** — открывает grid с последним активным view.
- **Command Palette (Ctrl+K)** → поиск по SKU/названию → переход к grid с выделенной строкой.
- **Notification click** (failed action, stock-out alert) → grid с pre-applied фильтром.
- **Deep link** — `/workspace/123/grid?viewId=5&offerId=789` → grid + open detail panel.
- **Other screens** → ссылки «Перейти к товару» в P&L, pricing policies, promo campaigns.

---

## 3. Разрешения (RBAC)

| Действие | VIEWER | ANALYST | OPERATOR | PRICING_MANAGER | ADMIN | OWNER |
|----------|--------|---------|----------|-----------------|-------|-------|
| Просмотр grid | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Фильтрация / сортировка | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Создание / редактирование saved views | — | ✓ | ✓ | ✓ | ✓ | ✓ |
| CSV export | — | ✓ | ✓ | ✓ | ✓ | ✓ |
| Inline edit: cost_price | — | — | ✓ | ✓ | ✓ | ✓ |
| Inline edit: manual_lock | — | — | ✓ | ✓ | ✓ | ✓ |
| Approve / Reject action | — | — | — | ✓ | ✓ | ✓ |
| Hold / Resume action | — | — | ✓ | ✓ | ✓ | ✓ |
| Cancel action | — | — | ✓ | ✓ | ✓ | ✓ |
| Bulk approve / reject | — | — | — | ✓ | ✓ | ✓ |
| Bulk price formula | — | — | — | ✓ | ✓ | ✓ |
| Bulk cost update | — | — | — | ✓ | ✓ | ✓ |
| Draft mode | — | — | — | ✓ | ✓ | ✓ |

Кнопки и элементы, на которые у роли нет прав — **не отображаются** (не disabled, а hidden). Исключение: inline-edit поля показываются как read-only text.

---

## 4. Layout (зоны экрана)

```
┌───────────────────────────────────────────────────────────────────────────────┐
│  Top Bar: Workspace switcher · Товары > Все товары · 🔍 Ctrl+K · 👤 User    │
├────┬──────────────────────────────────────────────────────────┬───────────────┤
│    │ ┌─ KPI Strip ─────────────────────────────────────────┐ │               │
│    │ │ [Всего] [Ср.маржа] [Ожидают] [Критич.] [Выручка]   │ │               │
│ A  │ └─────────────────────────────────────────────────────┘ │               │
│ c  │ ┌─ View Tabs ─────────────────────────────────────────┐ │   Detail      │
│ t  │ │ [Все товары] [Требуют внимания] [В промо] [+ ▾]    │ │   Panel       │
│ i  │ └─────────────────────────────────────────────────────┘ │   (C)         │
│ v  │ ┌─ Toolbar ───────────────────────────────────────────┐ │               │
│ i  │ │ [Filters...] [Columns] [Density] [Export]           │ │   400px       │
│ t  │ └─────────────────────────────────────────────────────┘ │   resizable   │
│ y  │ ┌─ AG Grid ───────────────────────────────────────────┐ │   slide-in    │
│    │ │ ☐ │ Артикул │ Название │ МП │ Цена │ Маржа │ ...   │ │               │
│ B  │ │───┼─────────┼──────────┼────┼──────┼───────┼───────│ │  ┌──────────┐ │
│ a  │ │ ☐ │ ABC-001 │ Футболка │ WB │1 500 │ 60,0% │ ...   │ │  │ Header   │ │
│ r  │ │ ☐ │ DEF-002 │ Кроссовк │ OZ │3 890 │ 25,3% │ ...   │ │  │ Tabs     │ │
│    │ │ ...                                                  │ │  │ Content  │ │
│    │ └─────────────────────────────────────────────────────┘ │  │          │ │
│    │ ┌─ Pagination ────────────────────────────────────────┐ │  │          │ │
│    │ │ Показано 1–50 из 1 234 · [◀] 1 ... 25 [▶] · [50▾] │ │  └──────────┘ │
│    │ └─────────────────────────────────────────────────────┘ │               │
│    ├──────────────────────────────────────────────────────────┤               │
│    │ ┌─ Bulk Actions Bar (visible when rows selected) ─────┐ │               │
│    │ │ 12 элементов выбрано [Одобрить все] [Отклонить] [×]  │ │               │
│    │ └─────────────────────────────────────────────────────┘ │               │
├────┴──────────────────────────────────────────────────────────┴───────────────┤
│  Status Bar: ● WB синхр. 12 мин назад · ● Ozon синхр. 3 мин назад          │
└───────────────────────────────────────────────────────────────────────────────┘
```

### Зоны

| Зона | Описание | Размеры |
|------|----------|---------|
| KPI Strip | 4–6 карточек с метриками workspace-а | Высота ~72px, full width |
| View Tabs | Tab strip сохранённых views | Высота 36px |
| Toolbar | Filter bar + controls | Высота 40px, растягивается при наличии filter pills |
| AG Grid | Основная таблица | Заполняет оставшееся пространство, virtual scroll |
| Pagination | Навигация по страницам | Высота 40px |
| Bulk Actions Bar | Bottom panel, появляется при выборе строк | Высота 48px, slide-up |
| Detail Panel | Правая панель деталей по клику на строку | 400px default, min 320px, max 50% viewport |

---

## 5. Data sources

| Данные | Store | Endpoint | Описание |
|--------|-------|----------|----------|
| Grid rows (PG columns) | PostgreSQL | `GET /api/workspace/{id}/grid` | Canonical state, pricing, execution, promo |
| Grid rows (CH enrichment) | ClickHouse | Merged server-side | `revenue_30d`, `net_pnl_30d`, `velocity_14d`, `return_rate_pct`, `days_of_cover`, `stock_risk` |
| KPI strip | PostgreSQL + ClickHouse | `GET /api/workspace/{id}/grid/kpi` (TBD) | Агрегированные метрики |
| Saved views | PostgreSQL | `GET /api/workspace/{id}/views` | Пресеты фильтров, columns, sort |
| Offer detail | PostgreSQL + ClickHouse | `GET /api/workspace/{id}/offers/{offerId}` | Composite detail |
| Price journal | PostgreSQL | `GET /api/workspace/{id}/offers/{offerId}/price-journal` | История ценовых решений |
| Promo journal | PostgreSQL | `GET /api/workspace/{id}/offers/{offerId}/promo-journal` | История промо-участия |
| Stock per warehouse | PostgreSQL + ClickHouse | Included in offer detail | Per-warehouse breakdown |
| Product P&L | ClickHouse | `GET /api/analytics/pnl/by-product` | Product-level P&L |
| CSV export | Server-side streaming | `GET /api/workspace/{id}/grid/export` | Full filtered dataset |

### Two-store merge pattern

PostgreSQL отвечает за фильтрацию, сортировку (по PG-колонкам), пагинацию. ClickHouse-enrichment подгружается batch-запросом по `marketplace_offer_id` набора текущей страницы и мержится в application layer. Если ClickHouse недоступен — enriched-поля приходят как `null`, grid остаётся функциональным (graceful degradation).

---

## 6. Screen states

### 6.1. Loading (начальная загрузка)

```
┌────────────────────────────────────────────────┐
│ ┌─ KPI Strip ───────────────────────────────┐  │
│ │ [░░░░░░░░] [░░░░░░░░] [░░░░░░░░] [░░░░░] │  │
│ └───────────────────────────────────────────┘  │
│ ┌─ View Tabs ───────────────────────────────┐  │
│ │ [Все товары] [░░░░░░░░░░░░] [░░░░░░]      │  │
│ └───────────────────────────────────────────┘  │
│ ┌─ Grid skeleton ───────────────────────────┐  │
│ │ ░░░░░░░ │ ░░░░░░░░░ │ ░░░░ │ ░░░░░░░░░░ │  │
│ │ ░░░░░░░ │ ░░░░░░░░░ │ ░░░░ │ ░░░░░░░░░░ │  │
│ │ ░░░░░░░ │ ░░░░░░░░░ │ ░░░░ │ ░░░░░░░░░░ │  │
│ └───────────────────────────────────────────┘  │
└────────────────────────────────────────────────┘
```

- Skeleton shimmer blocks повторяют layout grid-а (header + 10 строк).
- KPI strip — 4 shimmer-блока.
- View tabs — заголовки загружены (из кэша), содержимое — skeleton.

### 6.2. Loaded (данные отображены)

Полный layout как в §4. Все колонки, rows, KPI values видны.

### 6.3. Empty (фильтры вернули 0 результатов)

```
┌────────────────────────────────────────────────┐
│                                                │
│        Нет товаров, соответствующих            │
│              вашим фильтрам.                   │
│                                                │
│            [Сбросить фильтры]                  │
│                                                │
└────────────────────────────────────────────────┘
```

- Centered message, без иллюстраций.
- Кнопка «Сбросить фильтры» (secondary) — очищает все active filters.

### 6.4. Empty (нет данных вообще, новый workspace)

```
┌────────────────────────────────────────────────┐
│                                                │
│        Данные ещё не загружены.                 │
│    Проверьте настройки подключений.            │
│                                                │
│         [Перейти в Настройки]                  │
│                                                │
└────────────────────────────────────────────────┘
```

### 6.5. Error (API сбой)

```
┌────────────────────────────────────────────────┐
│                                                │
│     Не удалось загрузить данные.               │
│     Попробуйте обновить страницу.              │
│                                                │
│     [Обновить]                                 │
│                                                │
└────────────────────────────────────────────────┘
```

- Toast (error variant): «Ошибка загрузки данных. [Повторить]» — 8 сек, manual dismiss.

### 6.6. Partial (ClickHouse degradation)

Grid отображается полностью — PostgreSQL-колонки работают. ClickHouse-enriched колонки показывают `—` (em-dash) вместо значений. Tooltip на заголовке: «Аналитические данные временно недоступны».

Жёлтый information banner над grid-ом (32px):
```
ⓘ Часть аналитических данных временно недоступна. Основные данные отображаются корректно.
```

Колонки, затронутые degradation: `revenue_30d`, `net_pnl_30d`, `velocity_14d`, `return_rate_pct`, `days_of_cover`, `stock_risk`.

---

## 7. KPI Strip

Горизонтальный ряд из 5 карточек над grid-ом. Background: `--bg-secondary`. Высота: 72px. Padding: `--space-4` (16px).

### Карточки

| # | Русский label | Значение | Формат | Trend | Источник |
|---|---------------|----------|--------|-------|----------|
| 1 | Всего товаров | `totalOffers` | `1 234` (число, monospace) | — | Count `marketplace_offer` |
| 2 | Средняя маржа | `avgMarginPct` | `32,4%` (monospace) | `↑ 2,1%` / `↓ 1,3%` vs прошлый период | Computed avg |
| 3 | Ожидают действий | `pendingActionsCount` | `12` (число, monospace) | — | Count `price_action WHERE status = PENDING_APPROVAL` |
| 4 | Критический остаток | `criticalStockCount` | `7` (число, monospace) | — | Count `stock_risk = CRITICAL` |
| 5 | Выручка 30 дн. | `revenue30dTotal` | `1 290 000 ₽` (monospace) | `↑ 8,2%` / `↓ 3,1%` vs прошлые 30 дн. | SUM `revenue_30d` |

### Wireframe одной карточки

```
┌──────────────────┐
│ Средняя маржа    │  ← label: --text-sm, --text-secondary
│ 32,4%            │  ← value: --text-xl, JetBrains Mono, --text-primary
│ ↑ 2,1%           │  ← trend: --text-xs, --finance-positive / --finance-negative
└──────────────────┘
```

- Card background: `--bg-primary` (white).
- Border: 1px `--border-default`.
- Border-radius: `--radius-md` (6px).
- Shadow: нет.
- Trend delta: стрелка `↑` зелёная (`--finance-positive`), `↓` красная (`--finance-negative`), `→ 0,0%` серая (`--finance-zero`).

### KPI с ClickHouse degradation

При недоступности ClickHouse: карточки «Средняя маржа», «Критический остаток», «Выручка 30 дн.» показывают `—` вместо значения. Tooltip: «Данные временно недоступны».

---

## 8. View Tabs (Saved Views)

### Tab strip

```
┌────────────────────────────────────────────────────────────────────────┐
│ [Все товары ▾] │ [Требуют внимания (7)] │ [В промо] │ [Высокая...] │ [+]  │
└────────────────────────────────────────────────────────────────────────┘
```

- Расположение: горизонтальная полоса под KPI strip.
- Active tab: border-bottom 2px `--accent-primary`, `--text-primary` font weight 600.
- Inactive tabs: `--text-secondary`, no border.
- Overflow (>5 tabs): горизонтальный scroll с chevron arrows `‹` `›`.
- Counter badge на tab: для views типа «Требуют внимания» — число matching rows в круглых скобках.

### System views (seeded, non-deletable)

| View name (RU) | Фильтры | Sort | Описание |
|----------------|---------|------|----------|
| Все товары | Нет фильтров | `sku_code ASC` | Полный каталог |
| Требуют внимания | `stock_risk IN [CRITICAL, WARNING]` OR `last_action_status = FAILED` | `stock_risk DESC` | Товары с проблемами |
| В промо | `has_active_promo = true` | `promo_status ASC` | Товары в промо-акциях |

System views: иконка замка (🔒) рядом с названием. Нельзя редактировать, удалять, переименовывать.

### User views (custom)

- **Создание:** кнопка `[+]` справа от tabs → dropdown:
  - «Создать новый view» → modal: название + текущие фильтры/sort/columns.
  - «Дублировать текущий» → modal: pre-filled с текущим state.
- **Редактирование:** right-click на tab → context menu: «Переименовать», «Обновить фильтры», «Удалить».
- **Double-click** на tab → inline rename.
- **Drag** tab → reorder.
- **Удаление:** context menu → «Удалить view» → confirmation modal: «Удалить view '{name}'? Это действие нельзя отменить.»

### View state (persisted)

| Компонент | Persisted в `saved_view` |
|-----------|--------------------------|
| Фильтры | `filters` (JSONB) |
| Сортировка | `sort_column`, `sort_direction` |
| Видимые колонки + порядок | `visible_columns` (JSONB ordered array) |
| Группировка по SKU | `group_by_sku` (boolean) |

### API

| Endpoint | Method | Описание |
|----------|--------|----------|
| `GET /api/workspace/{id}/views` | GET | Список views пользователя |
| `POST /api/workspace/{id}/views` | POST | Создать view |
| `PUT /api/workspace/{id}/views/{viewId}` | PUT | Обновить view |
| `DELETE /api/workspace/{id}/views/{viewId}` | DELETE | Удалить view |

---

## 9. Toolbar

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ [Маркетплейс: WB ×] [Маржа: ≥ 25% ×] [+ Фильтр] [⊘]  │  [✎ Черновик] [≡ Колонки] [⫶ Плотность] [↓ Экспорт] │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

### Левая часть: Filter bar

Active фильтры отображаются как **pills** (компактные чипсы):

```
[Маркетплейс: WB ×]  [Маржа: ≥ 25% ×]  [Статус: Активный ×]
```

- Каждый pill: фон `--accent-subtle`, border 1px `--border-default`, border-radius `--radius-md`.
- Label: `{Поле}: {Оператор} {Значение}` — `--text-sm`.
- `×` — close button, удаляет фильтр.
- Click на pill → inline dropdown для редактирования значения.

**«+ Фильтр»** — ghost button. Click → dropdown со всеми доступными полями фильтрации. Поля, уже добавленные как pills, отображаются dimmed.

**«⊘»** — ghost icon button. Click → очистить все фильтры. Visible только при наличии active фильтров.

### Правая часть: Actions

| Элемент | Тип | Русский label | Описание |
|---------|-----|---------------|----------|
| Draft toggle | Toggle button + icon | «Черновик» | Включает/выключает Draft Mode (§14.3). Outline → filled + accent when active. PRICING_MANAGER+ |
| Columns | Ghost button + icon | «Колонки» | Открывает column configuration panel |
| Density | Ghost button + icon | «Плотность» | Toggle: компакт (32px) ↔ удобно (40px) |
| Export | Secondary button + icon | «Экспорт» | CSV export текущего filtered dataset |

---

## 10. Filter bar — полная спецификация фильтров

| # | Русский label | Поле | Тип фильтра | Оператор | UI control | API param |
|---|---------------|------|-------------|----------|------------|-----------|
| 1 | Маркетплейс | `marketplace_type` | Enum multi-select | IN | Checkbox dropdown: WB, Ozon | `marketplace_type=WB,OZON` |
| 2 | Подключение | `connection_id` | ID multi-select | IN | Dropdown (названия подключений) | `connection_id=1,2` |
| 3 | Категория | `category_id` | ID multi-select | IN | Searchable dropdown (tree) | `category_id=42,55` |
| 4 | Статус | `status` | Enum multi-select | IN | Checkbox dropdown: Активный, Архив, Заблокирован | `status=ACTIVE,ARCHIVED` |
| 5 | Артикул | `sku_code` | Text search | ILIKE | Text input (live search, debounce 300ms) | `sku_code=ABC` |
| 6 | Название | `product_name` | Text search | ILIKE | Text input (live search, debounce 300ms) | `product_name=футболка` |
| 7 | Маржа от | `margin_min` | Number range (min) | ≥ | Number input + `%` suffix | `margin_min=10.0` |
| 8 | Маржа до | `margin_max` | Number range (max) | ≤ | Number input + `%` suffix | `margin_max=50.0` |
| 9 | Риск остатка | `stock_risk` | Enum multi-select | IN | Checkbox dropdown: Критический, Предупреждение, Нормальный | `stock_risk=CRITICAL,WARNING` |
| 10 | Ручная блокировка | `has_manual_lock` | Boolean | = | Toggle pill: Да / Нет / Любой | `has_manual_lock=true` |
| 11 | В промо | `has_active_promo` | Boolean | = | Toggle pill: Да / Нет / Любой | `has_active_promo=true` |
| 12 | Последнее решение | `last_decision` | Enum multi-select | IN | Checkbox dropdown: CHANGE, SKIP, HOLD | `last_decision=CHANGE,SKIP` |
| 13 | Статус действия | `last_action_status` | Enum multi-select | IN | Checkbox dropdown: все статусы action | `last_action_status=FAILED,PENDING_APPROVAL` |

### Filter pill display format

| Фильтр | Pill text |
|--------|-----------|
| Маркетплейс: WB | `Маркетплейс: WB` |
| Маркетплейс: WB, Ozon | `Маркетплейс: WB, Ozon` |
| Маржа ≥ 25% | `Маржа: ≥ 25%` |
| Маржа 10% – 50% | `Маржа: 10% – 50%` |
| Ручная блокировка: Да | `Блокировка: Да` |
| Артикул содержит ABC | `Артикул: ABC` |

### Filter (stock_risk) — post-filter note

`stock_risk` enriched из ClickHouse. Фильтрация выполняется post-filter на application layer (после PG pagination). При ClickHouse degradation: фильтр `stock_risk` недоступен — pill показывается как disabled с tooltip «Фильтр временно недоступен».

---

## 11. Column configuration panel

Открывается по кнопке «Колонки» в toolbar. Dropdown panel (не modal), привязан к кнопке. Width: 300px. Max-height: 500px с scroll.

```
┌─ Колонки ──────────────────────────────┐
│ 🔍 Поиск колонок...                    │
│                                         │
│ ☑ Артикул             (закреплена)     │
│ ⠿ ☑ Название                           │
│ ⠿ ☑ Маркетплейс                        │
│ ⠿ ☑ Текущая цена                       │
│ ⠿ ☑ Маржинальность                     │
│ ⠿ ☑ Доступный остаток                  │
│ ⠿ ☐ Цена со скидкой                    │
│ ⠿ ☐ Себестоимость                      │
│ ⠿ ☐ Дней покрытия                      │
│ ⠿ ☐ Риск остатка                       │
│ ⠿ ☐ Выручка 30д                        │
│ ...                                     │
│                                         │
│ [Сбросить по умолчанию]                │
└─────────────────────────────────────────┘
```

- **Search input** (`--text-sm`): instant filter по названию колонки.
- **⠿** = drag handle для reorder (drag & drop).
- **Checkbox** toggle visibility. Изменения применяются мгновенно (no save button).
- **(закреплена)** — frozen column, нельзя скрыть или переместить.
- **«Сбросить по умолчанию»** — ghost button, восстанавливает default visible columns set.
- Column configuration сохраняется в текущем saved view (auto-persist).

---

## 12. AG Grid — полная спецификация колонок

### Default visible columns (в порядке по умолчанию)

| # | Visible by default |
|---|-------------------|
| 0 | ☐ Checkbox (selection) |
| 1 | Артикул (`sku_code`) |
| 2 | Название (`product_name`) |
| 3 | Маркетплейс (`marketplace_type`) |
| 4 | Текущая цена (`current_price`) |
| 5 | Маржинальность (`margin_pct`) |
| 6 | Доступный остаток (`available_stock`) |
| 7 | Скорость продаж (`velocity_14d`) |
| 8 | Последнее решение (`last_decision`) |
| 9 | Статус действия (`last_action_status`) |
| 10 | Промо (`promo_status`) |
| 11 | Блокировка (`manual_lock`) |
| 12 | Свежесть данных (`data_freshness`) |

### Default sort

`sku_code ASC` (по артикулу, алфавитный).

### Полная спецификация всех 25+ колонок

| # | Русский label | Field | Тип данных | Align | Width | Sortable | Filterable | Editable | Format | Frozen | Default visible |
|---|---------------|-------|-----------|-------|-------|----------|------------|----------|--------|--------|-----------------|
| 0 | — | `checkbox` | — | center | 40px | — | — | — | Checkbox | ✓ (frozen) | ✓ |
| 1 | Артикул | `sku_code` | string | left | 120px | ✓ (PG) | ✓ (text ILIKE) | — | Monospace, `--text-sm` | ✓ (frozen) | ✓ |
| 2 | Название | `product_name` | string | left | 200px flex | ✓ (PG) | ✓ (text ILIKE) | — | Truncate with ellipsis, tooltip full name | — | ✓ |
| 3 | Маркетплейс | `marketplace_type` | enum | center | 60px | — | ✓ (enum) | — | Badge: `WB` (purple), `Ozon` (blue) | — | ✓ |
| 4 | Подключение | `connection_name` | string | left | 150px | — | ✓ (id) | — | `--text-sm` | — | — |
| 5 | Статус | `status` | enum | center | 100px | — | ✓ (enum) | — | Badge: Активный (green), Архив (gray), Заблокирован (red) | — | — |
| 6 | Категория | `category` | string | left | 140px | — | ✓ (id) | — | `--text-sm`, truncate | — | — |
| 7 | Текущая цена | `current_price` | decimal | right | 110px | ✓ (PG) | — | — | `1 500 ₽` monospace, `--text-sm` | — | ✓ |
| 8 | Цена со скидкой | `discount_price` | decimal | right | 110px | — | — | — | `1 200 ₽` monospace. Если = current_price → `—` | — | — |
| 9 | Себестоимость | `cost_price` | decimal | right | 110px | — | — | ✓ (number) | `600 ₽` monospace. Editable: click → number input | — | — |
| 10 | Маржинальность | `margin_pct` | decimal | right | 90px | ✓ (PG, computed) | ✓ (range) | — | `60,0%` monospace. Color: ≥30% green, 10–30% default, <10% red | — | ✓ |
| 11 | Доступный остаток | `available_stock` | int | right | 90px | ✓ (PG) | — | — | `142` monospace. 0 → red bold | — | ✓ |
| 12 | Дней покрытия | `days_of_cover` | decimal | right | 90px | ✓ (CH) | — | — | `18,5` monospace. <7 → red, 7–14 → yellow | — | — |
| 13 | Риск остатка | `stock_risk` | enum | center | 100px | — | ✓ (enum, post-filter) | — | Badge: Критический (red), Предупреждение (yellow), Нормальный (green) | — | — |
| 14 | Выручка 30д | `revenue_30d` | decimal | right | 120px | ✓ (CH) | — | — | `45 000 ₽` monospace | — | — |
| 15 | P&L 30д | `net_pnl_30d` | decimal | right | 120px | ✓ (CH) | — | — | `12 000 ₽` monospace. Positive: green, negative: red, zero: gray | — | — |
| 16 | Скорость продаж | `velocity_14d` | decimal | right | 90px | ✓ (CH) | — | — | `3,2` monospace (шт./день). 0 → gray italic | — | ✓ |
| 17 | % возвратов | `return_rate_pct` | decimal | right | 90px | ✓ (CH) | — | — | `4,1%` monospace. >10% → red, 5–10% → yellow | — | — |
| 18 | Ценовая политика | `active_policy` | string | left | 160px | — | — | — | Название политики, `--text-sm`. null → `—` | — | — |
| 19 | Последнее решение | `last_decision` | enum | center | 110px | — | ✓ (enum) | — | Badge: CHANGE (blue), SKIP (gray), HOLD (yellow) | — | ✓ |
| 20 | Статус действия | `last_action_status` | enum | center | 130px | — | ✓ (enum) | — | Badge с dot: SUCCEEDED (green), FAILED (red), PENDING_APPROVAL (blue), EXECUTING (пульсирующий blue), ON_HOLD (yellow), EXPIRED (gray), CANCELLED (gray), SUPERSEDED (gray) | — | ✓ |
| 21 | Промо | `promo_status` | enum | center | 110px | — | ✓ (boolean) | — | Badge: Участвует (green), Доступно (blue outline), — (empty) | — | ✓ |
| 22 | Блокировка | `manual_lock` | boolean | center | 80px | — | ✓ (boolean) | ✓ (toggle) | Toggle icon: 🔒 locked (red) / 🔓 unlocked (gray). Click → toggle | — | ✓ |
| 23 | Симулир. цена | `simulated_price` | decimal | right | 110px | — | — | — | `1 350 ₽` monospace. null → `—`. Phase F column | — | — |
| 24 | Δ симуляции | `simulated_delta_pct` | decimal | right | 80px | — | — | — | `−10,0%` monospace. Positive: green, negative: red | — | — |
| 25 | Последняя синхр. | `last_sync_at` | timestamp | left | 120px | ✓ (PG) | — | — | Relative: `12 мин назад`. Tooltip: `30 мар 2026, 14:23` | — | — |
| 26 | Свежесть | `data_freshness` | enum | center | 80px | — | — | — | Dot indicator: FRESH (green dot), STALE (red dot + tooltip) | — | ✓ |

### Sortable columns — сводка

| Сортировка | Columns | Механизм |
|-----------|---------|----------|
| PostgreSQL (server-side, primary) | `sku_code`, `product_name`, `current_price`, `margin_pct`, `available_stock`, `last_sync_at` | SQL `ORDER BY` с whitelist |
| ClickHouse (server-side, pre-fetch) | `revenue_30d`, `net_pnl_30d`, `velocity_14d`, `return_rate_pct`, `days_of_cover` | CH query → sorted IDs → PG `ORDER BY array_position(...)` |

### Column header behavior

- **Click** → sort toggle: ASC → DESC → remove sort.
- Sort indicator: `▲` (ASC) / `▼` (DESC) рядом с названием колонки.
- **Drag border** → resize column width.
- **Drag header** → reorder column.

### Row behavior

| Взаимодействие | Действие |
|----------------|----------|
| Hover | Background: `--bg-tertiary` |
| Single click | Select row (checkbox toggle) + highlight `--bg-active` + left 2px accent border |
| Row click (на non-checkbox, non-editable area) | Open Detail Panel (right) |
| Double-click on editable cell | Enter inline edit mode |
| Shift+click | Range select (checkbox toggle для диапазона) |
| Ctrl+click | Multi-select toggle |
| Right-click | Context menu |

### Row density

| Mode | Русский label | Row height | Cell font |
|------|---------------|------------|-----------|
| Compact | Компактный | 32px | `--text-sm` (13px) |
| Comfortable | Удобный | 40px | `--text-sm` (13px) |

Переключатель — toggle button в toolbar. Default: Compact.

### Context menu (right-click на строке)

```
┌──────────────────────────┐
│ Открыть детали            │
│ Открыть в новой вкладке   │
│ ─────────────────────── │
│ Копировать артикул        │
│ Копировать название       │
│ ─────────────────────── │
│ Заблокировать цену        │  ← visible only for OPERATOR+
│ Разблокировать цену       │  ← visible only if locked
│ ─────────────────────── │
│ Экспорт выбранных         │  ← visible only with selection
└──────────────────────────┘
```

---

## 13. Pagination

```
┌────────────────────────────────────────────────────────────────────────┐
│ Показано 1–50 из 1 234     [◀ Назад]  1  2  3  ...  25  [Далее ▶]   │[50 ▾]│
└────────────────────────────────────────────────────────────────────────┘
```

| Элемент | Описание |
|---------|----------|
| «Показано X–Y из Z» | Range + total count, `--text-sm`, `--text-secondary` |
| Page buttons | Numbered pages. Active page: `--accent-primary` bg. Max 7 visible, ellipsis for gaps |
| «Назад» / «Далее» | Secondary buttons with chevron icons |
| Page size selector | Dropdown: `50` / `100` / `200`. Default: 50 |

Pagination — server-side. API params: `page=0&size=50`.

---

## 14. Inline Editing

### 14.1. Cost price (себестоимость)

| Свойство | Значение |
|----------|----------|
| Trigger | Double-click на ячейку `cost_price` |
| Edit control | Number input, right-aligned, monospace, `₽` suffix |
| Save | Blur или Enter |
| Cancel | Escape |
| Validation | > 0, decimal с 2 знаками. Пустое = remove cost_price |
| API | `PUT /api/cost-profiles` — updates `cost_profile` SCD2 |
| Feedback | Cell flash (green pulse) on success. Toast «Себестоимость обновлена» (3s) |
| Error | Red border on cell + toast «Не удалось сохранить. [Повторить]» |
| Permissions | OPERATOR, PRICING_MANAGER, ADMIN, OWNER |

```
Before:  │  600 ₽  │
Edit:    │ [650,00₽]│  ← focused input, blue border ring
After:   │  650 ₽  │  ← green flash, then normal
```

### 14.2. Manual lock (ручная блокировка)

| Свойство | Значение |
|----------|----------|
| Trigger | Single click на toggle icon (🔒 / 🔓) |
| Lock → Unlock | Click 🔒 → API `POST .../unlock` → icon becomes 🔓 |
| Unlock → Lock | Click 🔓 → mini-form dropdown: locked_price (pre-filled current_price), reason (optional), expires_at (optional) → API `POST .../lock` → icon becomes 🔒 |
| Feedback | Toggle animation + toast «Цена заблокирована» / «Блокировка снята» |
| Permissions | OPERATOR, PRICING_MANAGER, ADMIN, OWNER |

### Lock form (dropdown on lock toggle)

```
┌─ Заблокировать цену ─────────────────┐
│ Зафиксированная цена:  [1 500 ₽    ] │
│ Причина:               [необязательно]│
│ Срок:                  [Бессрочно ▾ ] │
│                                       │
│ [Отмена]              [Заблокировать] │
└───────────────────────────────────────┘
```

- «Срок» dropdown: Бессрочно / 24 часа / 7 дней / 30 дней / Дата...
- «Заблокировать» — primary button.
- «Отмена» — ghost button.

### 14.3. Draft Mode (режим черновика)

Режим массового inline-редактирования цен с client-side staging и server-side validation перед apply. Полная спецификация: [Bulk Operations & Draft Mode](../features/2026-03-31-bulk-operations-draft-mode.md).

| Свойство | Значение |
|----------|----------|
| Trigger | Toggle «✎ Черновик» в правой части Toolbar |
| Permissions | PRICING_MANAGER, ADMIN, OWNER |
| State storage | Client-side React state: `Map<offerId, { targetPrice, originalPrice }>` |
| Persistence | **Нет** — при закрытии вкладки теряется. `beforeunload` warning |

#### Активация

1. Кнопка «✎ Черновик» → filled style с `--accent-emphasis`, label «Черновик ✓»
2. Draft banner появляется между toolbar и grid (§14.3.1)
3. Колонка `current_price` становится editable — double-click → number input
4. Добавляется virtual column `projected_margin` (live computed из `cost_price` + draft price)

#### Деактивация

- Если есть unsaved changes → confirmation dialog: «Отменить {N} изменений?» → «Да, отменить» / «Нет, остаться»
- Если нет changes → просто toggle off

#### 14.3.1. Draft Banner

Появляется между Toolbar и Grid при активном Draft Mode. Height: 48px. Background: `--bg-accent-subtle`. Border-bottom: 1px `--border-accent`.

```
┌───────────────────────────────────────────────────────────────────────────────┐
│ ✎ Черновик: 23 изменения                                                     │
│ Ср. изменение: +3,7%  │  Мин. маржа: 14,2%  │  ⚠ 2 заблокированы guards    │
│                                                                               │
│ [Показать diff]  [Сбросить все]                         [Применить (21)]      │
└───────────────────────────────────────────────────────────────────────────────┘
```

| Элемент | Тип | Описание |
|---------|-----|----------|
| Counter | Text, `--text-emphasis` | `{N} изменений` — live counter |
| Avg change | Text | Средний % изменения, `--text-positive` или `--text-negative` по знаку |
| Min margin | Text | Минимальная projected margin. `--text-danger` если < 10% |
| Guards warning | Text + icon ⚠ | Count товаров, не проходящих client-side guard pre-check (manual_lock, promo, stock=0) |
| «Показать diff» | Ghost button | Фильтрует grid до изменённых строк. Добавляет filter pill `[Только изменения ×]` |
| «Сбросить все» | Danger ghost button | Очищает все draft entries. Без confirmation |
| «Применить (N)» | Primary button | N = count без заблокированных. → Server dry-run → confirmation modal → apply |

#### 14.3.2. Grid cells в Draft Mode

| Состояние ячейки | Визуал |
|------------------|--------|
| Изменённая цена | Background `--bg-warning-subtle`. Старая цена: `text-decoration: line-through`, `--text-secondary`. Новая цена: `--text-primary`, `font-weight: 600` |
| Projected margin (изменённая) | `старая% → новая%`. Новая маржа < 10% → `--text-danger` |
| Неизменённая строка | Read-only, обычный стиль |
| Заблокированная (guard pre-check) | Icon ⚠ в ячейке цены. Tooltip с причиной. Non-editable, cursor: `not-allowed` |

#### 14.3.3. Inline price edit (в Draft Mode)

| Свойство | Значение |
|----------|----------|
| Trigger | Double-click на ячейку `current_price` при активном Draft Mode |
| Edit control | Number input, right-aligned, monospace, `₽` suffix. Pre-filled: current_price |
| Save to draft | Enter или Blur → запись в client-side `draftChanges` Map. **Не отправляется на сервер** |
| Cancel | Escape → возврат к current_price (не к draft значению) |
| Undo per-cell | Right-click → context menu → «Отменить изменение» → remove из draftChanges |
| Validation | > 0, decimal до 2 знаков |

#### 14.3.4. «Показать diff» view

При нажатии «Показать diff» grid фильтруется, показывая **только изменённые строки**. Toolbar получает filter pill `[Только изменения ×]`.

Дополнительные колонки в diff view:

| Колонка | Описание |
|---------|----------|
| Текущая цена | `current_price` (read-only) |
| Новая цена | `draft_price` (editable) |
| Δ цены | `new - old`, `₽`, с знаком |
| Δ% | `(new - old) / old × 100`, с знаком |
| Текущая маржа | computed |
| Новая маржа | computed |
| Guard status | ✓ / ⚠ (причина) |

#### 14.3.5. Apply flow

1. «Применить» → `POST /api/pricing/bulk-manual/preview` (synchronous, 30s timeout)
2. Server response: per-offer result (CHANGE/SKIP) + summary (avg%, min margin, guard blocks)
3. **Confirmation modal:**

```
┌─ Применить массовое изменение цен ─────────────────────────┐
│                                                              │
│ 41 товар будет изменён                                       │
│                                                              │
│ Среднее изменение:  +4,8%                                    │
│ Минимальная маржа:  14,2%                                    │
│ Макс. изменение:    +12,5%                                   │
│                                                              │
│ ⚠ 4 товара пропущены (guards)                               │
│ ⚠ 2 товара скорректированы constraints                      │
│   (запрошенная цена отличается от итоговой)                  │
│                                                              │
│ Действия будут созданы со статусом APPROVED                  │
│ и отправлены на исполнение.                                  │
│                                                              │
│ [Отмена]                              [Применить (41)]       │
└──────────────────────────────────────────────────────────────┘
```

4. «Применить» → `POST /api/pricing/bulk-manual/apply`
5. Success → draft очищается, Draft Mode деактивируется, toast «41 ценовое действие создано», grid refresh
6. Partial failure → toast warning, draft entries для failed offers остаются

---

## 15. Bulk Actions Bar

Появляется внизу экрана (slide-up) при наличии selected rows (≥1 checkbox).

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│  12 элементов выбрано   [💲 Изменить цену]  [📦 Себестоимость]  [Одобрить все]  [Отклонить все]  [Экспорт]  ×  │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

| Элемент | Тип | Русский label | Действие | Permissions | API |
|---------|-----|---------------|----------|-------------|-----|
| Counter | Text | `{N} элементов выбрано` | — | All | — |
| **Bulk price** | **Accent button** | **«Изменить цену»** | **Открывает Formula Panel (§15.1)** | **PRICING_MANAGER+** | **`POST /api/pricing/bulk-manual/preview` → apply** |
| **Bulk cost** | **Secondary button** | **«Себестоимость»** | **Открывает Cost Update Panel (§15.2)** | **PRICING_MANAGER+** | **`POST /api/cost-profiles/bulk-update`** |
| Approve all | Primary button | «Одобрить все» | Bulk approve selected price_actions в PENDING_APPROVAL | PRICING_MANAGER+ | `POST /api/actions/bulk-approve { actionIds }` |
| Reject all | Danger button | «Отклонить все» | Bulk reject selected price_actions | PRICING_MANAGER+ | `POST /api/actions/bulk-approve` с reject |
| Export | Secondary button | «Экспорт» | Export selected rows as CSV | ANALYST+ | Client-side subset of IDs |
| Close | Icon button (×) | — | Deselect all | All | — |

### Bulk action confirmation modal

```
┌─ Подтверждение ──────────────────────┐
│                                       │
│  Одобрить 12 ценовых действий?        │
│                                       │
│  Это запустит исполнение              │
│  для выбранных товаров.               │
│                                       │
│  [Отмена]            [Одобрить (12)]  │
└───────────────────────────────────────┘
```

### Bulk action feedback

- Toast: «12 действий одобрено» (success, 3s).
- Partial failure: toast «Одобрено 10 из 12. 2 не удалось.» (warning, 8s) — click → detail.
- Grid rows refresh in-place (status badges update).

### 15.1. Formula Panel (Bulk Price)

Открывается при нажатии «Изменить цену» в Bulk Actions Bar. Dropdown panel, привязан к кнопке. Width: 420px.

```
┌─ Массовое изменение цены ───────────────────────────┐
│                                                      │
│ Действие:    [Увеличить на %          ▾]            │
│                                                      │
│ Значение:    [5,0          ] %                       │
│                                                      │
│ Округление:  ☑ Округлить до [10 ₽] шаг [FLOOR ▾]   │
│                                                      │
│ ─────────────────────────────────────────────────── │
│ Предпросмотр (47 товаров)                            │
│                                                      │
│ Ср. изменение:    +5,0%                              │
│ Мин. новая цена:  525 ₽ (маржа: 12,4%)             │
│ Макс. новая цена: 15 750 ₽ (маржа: 64,2%)          │
│ Мин. маржа после: 12,4%                             │
│                                                      │
│ ─────────────────────────────────────────────────── │
│ ⚠ 3 товара заблокированы:                           │
│   • 1 — ручная блокировка цены                      │
│   • 1 — товар в активном промо                      │
│   • 1 — себестоимость не задана                     │
│   [Показать заблокированные]                         │
│                                                      │
│ [Отмена]                    [Применить (44)]         │
└──────────────────────────────────────────────────────┘
```

**Формулы (dropdown «Действие»):**

| # | Label | Формула | Поле ввода |
|---|-------|---------|------------|
| 1 | Увеличить на % | `current_price × (1 + pct/100)` | `pct` + `%` suffix |
| 2 | Уменьшить на % | `current_price × (1 − pct/100)` | `pct` + `%` suffix |
| 3 | Умножить на коэффициент | `current_price × factor` | `factor` (×) |
| 4 | Установить фиксированную | `fixed_price` | `fixed_price` + `₽` suffix |
| 5 | Наценка от себестоимости | `cost_price × (1 + markup_pct/100)` | `markup_pct` + `%` suffix |
| 6 | Округлить до шага | `round(current_price, step, direction)` | `step` + `₽`, direction dropdown |

**Поведение:**
- Предпросмотр computed **client-side** (instant) из grid data (`current_price`, `cost_price`).
- «Применить» → server-side dry-run (`POST /api/pricing/bulk-manual/preview`) → confirmation modal (§14.3.5) → apply.
- Формула 5 (наценка от с/с): товары без `cost_price` → auto-excluded. Count в preview: «⚠ N — себестоимость не задана».
- Max 500 offers per operation. При попытке > 500 → validation message в panel, кнопка «Применить» disabled.

### 15.2. Cost Update Panel (Bulk Cost)

Открывается при нажатии «Себестоимость» в Bulk Actions Bar. Dropdown panel, привязан к кнопке. Width: 380px.

```
┌─ Массовое изменение себестоимости ──────────────────┐
│                                                      │
│ Действие:    [Установить фиксированную    ▾]        │
│                                                      │
│ Значение:    [650          ] ₽                       │
│                                                      │
│ Дата начала: [01.04.2026   ] (начало действия)      │
│                                                      │
│ ─────────────────────────────────────────────────── │
│ 47 товаров будет обновлено.                          │
│ ⚠ 5 товаров без текущей себестоимости —             │
│   будет создана первая версия.                       │
│                                                      │
│ [Отмена]                    [Обновить (47)]          │
└──────────────────────────────────────────────────────┘
```

**Операции:** Установить фиксированную / Увеличить на % / Уменьшить на % / Умножить на коэффициент.

**«Дата начала»:** calendar date picker. Default: завтра. Определяет `valid_from` в SCD2 cost_profile.

**Feedback:**
- Success toast: «47 себестоимостей обновлено» (3s).
- Partial failure: «Обновлено 45 из 47. 2 ошибки.» (warning, 8s).
- Grid cells `cost_price` и `margin_pct` refresh in-place.

---

## 16. Export (CSV)

| Свойство | Значение |
|----------|----------|
| Trigger | Toolbar → «Экспорт» button |
| Scope | Все строки, соответствующие текущим фильтрам (не только видимая страница) |
| Format | CSV, UTF-8 BOM, semicolon delimiter |
| Columns | Текущие visible columns в текущем порядке |
| Header row | Русские названия колонок |
| Max rows | 100 000 (configurable). Превышение → 400, toast «Слишком много данных. Сузьте фильтры» |
| API | `GET /api/workspace/{id}/grid/export?[current filters]` |
| Response | `Content-Type: text/csv`, `Content-Disposition: attachment; filename="datapulse-export-2026-03-31.csv"`, `Transfer-Encoding: chunked` |

### Export flow

1. Click «Экспорт» → info toast: «Подготовка экспорта...» (non-blocking, spinner icon).
2. Server generates CSV (streaming, JDBC cursor `fetchSize=500`).
3. Browser download starts automatically.
4. Toast updates: «Экспорт готов — 1 234 строки» (success, 3s, link to re-download).

Large exports (>10 000 rows):
1. Click «Экспорт» → info toast: «Экспорт готовится. Мы уведомим, когда будет готов.»
2. Async server processing.
3. Notification bell → «Экспорт готов. Скачать.»

---

## 17. Detail Panel (Offer Detail)

### Общее поведение

| Свойство | Значение |
|----------|----------|
| Trigger | Click на строку grid-а (на non-checkbox, non-editable area) |
| Position | Правая сторона, pushes main content left |
| Default width | 400px |
| Min width | 320px |
| Max width | 50% viewport |
| Resize | Drag handle на левой границе panel |
| Close | `×` button в header, Escape |
| Collapse | `◁` button → panel collapses to 0, grid expands |
| URL sync | `/workspace/:id/grid?offerId=789` → deep-linkable |

### Panel layout

```
┌─ Detail Panel ────────────────────────┐
│ ┌─ Header ──────────────────────────┐ │
│ │ Футболка синяя XL              ×  │ │
│ │ [WB] Мой кабинет WB      [◁] [×] │ │
│ └───────────────────────────────────┘ │
│ ┌─ Actions Bar ─────────────────────┐ │
│ │ [🔒 Заблокировать] [✓ Одобрить]  │ │
│ └───────────────────────────────────┘ │
│ ┌─ Tab Strip ───────────────────────┐ │
│ │ [Обзор] [Цены] [Промо] [Ост.] [P&L] │
│ └───────────────────────────────────┘ │
│ ┌─ Content ─────────────────────────┐ │
│ │                                    │ │
│ │   (tab content)                    │ │
│ │                                    │ │
│ └───────────────────────────────────┘ │
└───────────────────────────────────────┘
```

### Header

- **Product name**: `--text-lg` (16px), font weight 600, truncate with tooltip.
- **Marketplace badge**: `[WB]` purple badge / `[Ozon]` blue badge.
- **Connection name**: `--text-sm`, `--text-secondary`.
- **Close button (×)**: icon button, top-right.
- **Collapse button (◁)**: icon button, collapses panel.

### Actions bar (contextual)

Кнопки зависят от состояния offer и прав пользователя.

| Условие | Кнопки |
|---------|--------|
| Есть `price_action` в `PENDING_APPROVAL` | `[✓ Одобрить]` primary, `[✕ Отклонить]` danger |
| Есть `price_action` в `APPROVED` | `[⏸ Приостановить]` secondary |
| Есть `price_action` в `ON_HOLD` | `[▶ Возобновить]` primary, `[✕ Отменить]` danger |
| Lock = false | `[🔒 Заблокировать]` secondary |
| Lock = true | `[🔓 Разблокировать]` secondary |
| Нет pending actions | Только lock/unlock |

### Tab strip

| # | Русский label | Content |
|---|---------------|---------|
| 1 | Обзор | Основная информация по offer |
| 2 | Ценовой журнал | Price journal (история решений) |
| 3 | Промо журнал | Promo journal (история промо-участия) |
| 4 | Остатки | Stock per warehouse |
| 5 | P&L | Product-level P&L breakdown |

---

### Tab «Обзор»

Dense key-value pairs layout. 2 columns where space allows.

```
┌────────────────────────────────────┐
│ Основное                           │
│ Артикул          ABC-001           │
│ Маркетплейс      WB                │
│ Подключение      Мой кабинет WB    │
│ Статус           ● Активный        │
│ Категория        Футболки          │
│                                    │
│ Ценообразование                    │
│ Текущая цена     1 500 ₽           │
│ Цена со скидкой  1 200 ₽           │
│ Себестоимость    600 ₽    [✎]      │
│ Маржа            60,0%             │
│ Блокировка       🔓 Нет            │
│                                    │
│ Ценовая политика                   │
│ Политика         Маржа 25% WB      │
│ Стратегия        TARGET_MARGIN     │
│ Режим            SEMI_AUTO         │
│                                    │
│ Последнее решение                  │
│ Тип              CHANGE            │
│ Цена             1 500 → 1 200 ₽  │
│ Дата             30 мар 2026       │
│ Пояснение        [Решение] CHANGE: │
│                  1 500 → 1 200...  │
│                  [Подробнее →]      │
│                                    │
│ Последнее действие                 │
│ Статус           ● Выполнено       │
│ Цена             1 200 ₽           │
│ Режим            LIVE              │
│                                    │
│ Промо                              │
│ Статус           ● Участвует       │
│ Акция            Весенняя распрод. │
│ Промо-цена       1 100 ₽           │
│ Окончание        15 апр 2026       │
│                                    │
│ Аналитика (30 дней)                │
│ Выручка          45 000 ₽          │
│ P&L              12 000 ₽          │
│ Скорость         3,2 шт./день     │
│ % возвратов      4,1%              │
│                                    │
│ Остатки                            │
│ Доступно         142 шт.           │
│ Дней покрытия    18,5              │
│ Риск             ● Нормальный      │
│                                    │
│ Данные                             │
│ Синхронизация    12 мин назад      │
│ Свежесть         ● Актуальные      │
└────────────────────────────────────┘
```

- Section headers: `--text-base`, font weight 600, `--text-primary`.
- Labels: `--text-sm`, `--text-secondary`, left column.
- Values: `--text-sm`, `--text-primary`, right column. Numbers in JetBrains Mono.
- `[✎]` — inline edit trigger for cost_price.
- `[Подробнее →]` — link to expand full explanation text.
- **API:** `GET /api/workspace/{id}/offers/{offerId}`

### Tab «Ценовой журнал»

Paginated table (mini-table inside panel). Ordered by `decision_date DESC`.

```
┌─────────────────────────────────────┐
│ ┌─ Filters ──────────────────────┐  │
│ │ [Все типы ▾] [Период: 30 дн ▾]│  │
│ └────────────────────────────────┘  │
│                                     │
│ 30 мар 2026, 10:00                  │
│ ┌──────────────────────────────────┐│
│ │ CHANGE  1 500 → 1 200 ₽ (−20%) ││
│ │ Политика: Маржа 25% WB (v3)     ││
│ │ Действие: ● Выполнено            ││
│ │ [Решение] CHANGE: 1 500 → 1 2...││
│ │                    [Подробнее →] ││
│ └──────────────────────────────────┘│
│                                     │
│ 28 мар 2026, 14:15                  │
│ ┌──────────────────────────────────┐│
│ │ SKIP                              ││
│ │ Причина: Данные старше 24 часов  ││
│ │ Guard: stale_data_guard           ││
│ └──────────────────────────────────┘│
│                                     │
│ [Загрузить ещё]                     │
└─────────────────────────────────────┘
```

#### Journal entry fields

| Поле | Русский label | Формат |
|------|---------------|--------|
| `decision_date` | (timestamp header) | `30 мар 2026, 10:00` |
| `decision_type` | Тип | Badge: CHANGE (blue), SKIP (gray), HOLD (yellow) |
| `current_price` → `target_price` | Цена | `1 500 → 1 200 ₽ (−20,0%)`, monospace |
| `policy_name` (v`policy_version`) | Политика | Text, `--text-sm` |
| `action_status` | Действие | Status badge with dot |
| `execution_mode` | Режим | LIVE / SIMULATED badge |
| `explanation_summary` | Пояснение | Truncated to 2 lines, expandable |
| `actual_price` | Факт. цена | Monospace, shown if SUCCEEDED |
| `reconciliation_source` | Сверка | IMMEDIATE / DEFERRED / MANUAL |

#### Filters

- **Decision type:** dropdown: Все / CHANGE / SKIP / HOLD.
- **Period:** dropdown: 7 дней / 30 дней / 90 дней / Все время.

#### Pagination

Infinite scroll with «Загрузить ещё» button. Default page size: 20.

**API:** `GET /api/workspace/{id}/offers/{offerId}/price-journal?page=0&size=20&decisionType=...&from=...&to=...`

### Tab «Промо журнал»

Paginated timeline. Ordered by `promo_decision.created_at DESC`.

```
┌─────────────────────────────────────┐
│ Весенняя распродажа                 │
│ 01 апр – 15 апр 2026 · Скидка       │
│ ┌──────────────────────────────────┐│
│ │ Решение: PARTICIPATE              ││
│ │ Промо-цена: 1 100 ₽              ││
│ │ Маржа при промо: 18,3%           ││
│ │ Потеря маржи: −6,7%              ││
│ │ Действие: ● Выполнено             ││
│ │ Пояснение: Маржинальность выше...││
│ └──────────────────────────────────┘│
│                                     │
│ Зимняя распродажа                   │
│ 15 дек – 31 дек 2025 · Скидка       │
│ ┌──────────────────────────────────┐│
│ │ Решение: DECLINE                  ││
│ │ Промо-цена: 900 ₽                ││
│ │ Оценка: UNPROFITABLE              ││
│ │ Маржа при промо: −2,1%           ││
│ │ Пояснение: Маржинальность отриц.  ││
│ └──────────────────────────────────┘│
│                                     │
│ [Загрузить ещё]                     │
└─────────────────────────────────────┘
```

#### Journal entry fields

| Поле | Русский label | Формат |
|------|---------------|--------|
| `promo_name` | (header) | Bold text |
| `period` | Период | `01 апр – 15 апр 2026` |
| `promo_type` | Тип | Badge |
| `participation_decision` | Решение | Badge: PARTICIPATE (green), DECLINE (red), PENDING_REVIEW (yellow) |
| `evaluation_result` | Оценка | PROFITABLE / MARGINAL / UNPROFITABLE / INSUFFICIENT_STOCK / INSUFFICIENT_DATA |
| `required_price` | Промо-цена | Monospace with ₽ |
| `margin_at_promo_price` | Маржа при промо | `18,3%` colored |
| `margin_delta_pct` | Потеря маржи | `−6,7%` red |
| `action_status` | Действие | Status badge |
| `explanation_summary` | Пояснение | Expandable text |

**API:** `GET /api/workspace/{id}/offers/{offerId}/promo-journal?page=0&size=20`

### Tab «Остатки»

Per-warehouse stock breakdown table.

```
┌─────────────────────────────────────┐
│ Сводка                              │
│ Всего доступно     142 шт.          │
│ Дней покрытия      18,5             │
│ Риск               ● Нормальный     │
│                                     │
│ По складам                          │
│ ┌──────┬─────────┬──────┬──────────┐│
│ │Склад │Доступно │Резерв│Покрытие  ││
│ ├──────┼─────────┼──────┼──────────┤│
│ │Коледино│   80  │  12  │  25,0 дн ││
│ │Казань  │   42  │   5  │  13,1 дн ││
│ │Хабар.  │   20  │   0  │   6,3 дн ││
│ └──────┴─────────┴──────┴──────────┘│
│                                     │
│ Скорость продаж (14 дн): 3,2 шт./д │
│ Себестоимость: 600 ₽                │
│ Замороженный капитал: —             │
└─────────────────────────────────────┘
```

- **Сводка:** aggregated across all warehouses.
- **По складам:** mini-table, sorted by `available DESC`.
- **Columns:** Склад (name), Доступно (int), Резерв (int), Покрытие (days_of_cover per warehouse).
- Row coloring: `days_of_cover < 7` → red background-subtle, `7–14` → yellow background-subtle.

**Data source:** `mart_inventory_analysis` (ClickHouse) + `canonical_stock_current` (PostgreSQL). Included in offer detail response.

### Tab «P&L»

Product-level P&L breakdown.

```
┌─────────────────────────────────────┐
│ P&L за 30 дней         [Период ▾]   │
│                                     │
│ Выручка                  45 000 ₽   │
│ ─────────────────────────────────── │
│ Комиссия МП              −6 750 ₽   │
│ Эквайринг                  −450 ₽   │
│ Логистика                −3 600 ₽   │
│ Хранение                   −225 ₽   │
│ Штрафы                       0 ₽   │
│ Приёмка                    −180 ₽   │
│ Маркетинг                    0 ₽   │
│ Прочие удержания           −135 ₽   │
│ ─────────────────────────────────── │
│ Marketplace P&L          33 660 ₽   │
│ ─────────────────────────────────── │
│ Реклама                  −2 400 ₽   │
│ Себестоимость           −19 200 ₽   │
│ ─────────────────────────────────── │
│ Итого P&L               12 060 ₽   │
│ Маржа                     26,8%     │
│                                     │
│ Возвраты                 −1 845 ₽   │
│ Компенсации                 360 ₽   │
│                                     │
│ ⓘ Себестоимость не задана           │
│   для части товаров.                │
│   [Настроить себестоимость]          │
└─────────────────────────────────────┘
```

- **Period selector:** dropdown: 7 дней / 30 дней / 90 дней / Текущий месяц / Прошлый месяц.
- **Layout:** vertical list, label left, value right.
- Revenue: green. Costs: red. Totals: bold.
- Values: JetBrains Mono, right-aligned.
- Separator lines: `--border-subtle`.
- Warning block (yellow bg): если `cogs_status ≠ OK`.

**Data source:** `GET /api/analytics/pnl/by-product?sellerSkuId={id}&from=...&to=...`

---

## 18. Actions in Detail Panel

### Approve action

| Свойство | Значение |
|----------|----------|
| Trigger | «Одобрить» primary button in actions bar |
| Conditions | `price_action.status = PENDING_APPROVAL` |
| Confirmation | No modal (non-destructive) |
| API | `POST /api/actions/{actionId}/approve` |
| Feedback | Toast: «Действие одобрено» (success, 3s). Badge updates to APPROVED → SCHEDULED |
| Permissions | PRICING_MANAGER, ADMIN, OWNER |

### Reject action

| Свойство | Значение |
|----------|----------|
| Trigger | «Отклонить» danger button |
| Conditions | `price_action.status = PENDING_APPROVAL` |
| Confirmation | Inline reason input: «Причина отклонения:» (required) |
| API | `POST /api/actions/{actionId}/reject { cancelReason }` |
| Feedback | Toast: «Действие отклонено» (neutral, 3s) |
| Permissions | PRICING_MANAGER, ADMIN, OWNER |

### Hold action

| Свойство | Значение |
|----------|----------|
| Trigger | «Приостановить» secondary button |
| Conditions | `price_action.status = APPROVED` |
| Confirmation | Inline reason input: «Причина приостановки:» (required) |
| API | `POST /api/actions/{actionId}/hold { holdReason }` |
| Feedback | Toast: «Действие приостановлено» (neutral, 3s) |
| Permissions | OPERATOR, PRICING_MANAGER, ADMIN, OWNER |

### Resume action

| Свойство | Значение |
|----------|----------|
| Trigger | «Возобновить» primary button |
| Conditions | `price_action.status = ON_HOLD` |
| API | `POST /api/actions/{actionId}/resume` |
| Feedback | Toast: «Действие возобновлено» (success, 3s) |
| Permissions | OPERATOR, PRICING_MANAGER, ADMIN, OWNER |

### Cancel action

| Свойство | Значение |
|----------|----------|
| Trigger | «Отменить» danger button |
| Conditions | `status IN (PENDING_APPROVAL, APPROVED, ON_HOLD, SCHEDULED, RETRY_SCHEDULED, RECONCILIATION_PENDING)` |
| Confirmation | Destructive modal: «Отменить действие? Эта операция необратима.» + reason input (required for RECONCILIATION_PENDING) |
| API | `POST /api/actions/{actionId}/cancel { cancelReason }` |
| Feedback | Toast: «Действие отменено» (neutral, 3s) |
| Permissions | OPERATOR, PRICING_MANAGER, ADMIN, OWNER |

### Lock / Unlock

См. §14.2 выше. Те же controls, но в actions bar panel-а (а не inline в grid).

---

## 19. Modals

### 19.1. Create / Edit View modal

```
┌─ Создать view ──────────────────────┐
│                                      │
│ Название:  [Высокая маржа WB      ] │
│                                      │
│ Фильтры:  (текущие фильтры grid)    │
│  [Маркетплейс: WB ×] [Маржа ≥ 25%] │
│                                      │
│ Сортировка: [Маржинальность ▾] [↓]  │
│                                      │
│ ☐ По умолчанию                       │
│                                      │
│ [Отмена]              [Сохранить]    │
└──────────────────────────────────────┘
```

### 19.2. Delete View confirmation

```
┌─ Удалить view ──────────────────────┐
│                                      │
│ Удалить view «Высокая маржа WB»?    │
│                                      │
│ Это действие нельзя отменить.        │
│                                      │
│ [Отмена]                [Удалить]    │
└──────────────────────────────────────┘
```

### 19.3. Bulk action confirmation

См. §15 выше.

### 19.4. Cancel action (destructive)

```
┌─ Отменить действие ─────────────────┐
│                                      │
│ Отменить ценовое действие            │
│ для «Футболка синяя XL»?             │
│                                      │
│ Цена: 1 500 → 1 200 ₽               │
│ Статус: В очереди                    │
│                                      │
│ Причина:  [                        ] │
│                                      │
│ Это действие необратимо.             │
│                                      │
│ [Назад]                 [Отменить]   │
└──────────────────────────────────────┘
```

---

## 20. Charts

Operational Grid не содержит полноценных chart-ов (это data grid, не dashboard). Единственные графические элементы:

### 20.1. Sparklines (optional, per column)

Future enhancement: опциональные 7-day trend sparklines в ячейках `velocity_14d`, `revenue_30d`. Реализация через AG Grid sparkline column type + ngx-echarts micro-chart.

Спецификация sparkline:
- Width: 60px, height: 20px (внутри ячейки).
- Color: single color line (`--accent-primary`).
- No axes, no labels, no tooltip.
- Data: 7 data points (daily values for last 7 days).

### 20.2. KPI trend arrows

Trend arrows в KPI strip (§7) — текстовые, не chart-based.

---

## 21. Real-time updates (WebSocket)

| Topic (STOMP destination) | Payload | UI action |
|---------------------------|---------|-----------|
| `/topic/workspace/{id}/grid-updates` | `{ offerId, changedFields: { field: newValue, ... } }` | Update specific cells in-place. Green flash animation on changed cells |
| `/topic/workspace/{id}/action-updates` | `{ actionId, offerId, newStatus }` | Update `last_action_status` badge. If detail panel open for this offer → refresh action state |
| `/topic/workspace/{id}/sync-status` | `WorkspaceSyncStatusPush` (`reason` + `connection` health DTO, same as sync-health REST) | Status bar: `upsertConnection`. При `reason === ETL_JOB_COMPLETED` — инвалидация offers/analytics (один WS-кадр после коммита успеха; без дубля из Rabbit) |
| `/topic/workspace/{id}/kpi-updates` | `{ kpiKey, newValue, trend }` | Update KPI strip card values |

### Update behavior

- **Row update:** only visible rows update in-place (AG Grid `applyTransaction`). No full reload.
- **Flash animation:** changed cell gets `--bg-active` background pulse (200ms fade-in, 1s hold, 200ms fade-out). Only on visible rows.
- **New items:** don't auto-appear (would disrupt pagination). Show notification: «Появились новые товары. [Обновить]».
- **Status bar:** sync times update live via WebSocket.
- **Connection lost:** yellow persistent banner: «Соединение потеряно. Переподключение...» Auto-reconnect with exponential backoff (1s, 2s, 4s, 8s, max 30s).

---

## 22. Keyboard shortcuts

| Shortcut | Действие | Context |
|----------|----------|---------|
| `Ctrl+K` | Command Palette | Global |
| `Ctrl+F` | Focus filter bar (search input) | Grid |
| `Ctrl+S` | Save current view | Grid (когда есть unsaved changes) |
| `↑` / `↓` | Navigate grid rows | Grid focused |
| `Enter` | Open detail panel for highlighted row | Grid row highlighted |
| `Escape` | Close detail panel / close modal / cancel inline edit | Context-dependent |
| `Space` | Toggle checkbox on highlighted row | Grid row highlighted |
| `Ctrl+A` | Select all rows on current page | Grid focused |
| `Ctrl+Shift+A` | Deselect all | Grid focused |
| `Tab` | Move to next interactive element | Panel / form |
| `Shift+Tab` | Move to previous interactive element | Panel / form |
| `F5` | Refresh grid data | Grid |
| `Ctrl+E` | Export current view | Grid |

---

## 23. Automation blocker banner

Когда data staleness блокирует automation для текущего workspace (см. [analytics-pnl.md](../modules/analytics-pnl.md) §Automation blocker), non-dismissible banner появляется выше KPI strip:

```
┌──────────────────────────────────────────────────────────────────────────┐
│ ⚠ Автоматизация приостановлена: данные WB устарели на 26 часов.         │
│   Ручные действия доступны. Обновите синхронизацию.              [→]    │
└──────────────────────────────────────────────────────────────────────────┘
```

- Background: `--status-warning` (yellow).
- Height: 36px.
- `[→]` link → navigate to connection settings.
- Banner disappears automatically when data freshness is restored.
- Multiple blockers → multiple banners stacked.

---

## 24. User flow scenarios

### Scenario 1: Ежедневная утренняя проверка

**Персона:** Оператор, начало рабочего дня.

1. Оператор открывает Datapulse → автоматически попадает на grid (last used workspace).
2. Смотрит **KPI strip**: «Ожидают действий: 8», «Критический остаток: 3».
3. Переключается на tab **«Требуют внимания»** → видит 11 товаров с проблемами.
4. Сортирует по `stock_risk` → товары с CRITICAL наверху.
5. Кликает на первый товар → открывается **Detail Panel**.
6. Tab **«Остатки»**: видит, что на складе Коледино 0 шт., days_of_cover = 0.
7. Tab **«Обзор»**: проверяет velocity (5,2 шт./день) — товар продаётся активно.
8. Решает **заблокировать цену** (чтобы pricing не поднял цену на дефицитный товар).
9. Нажимает **«Заблокировать»** → заполняет: locked_price = current, reason = «Stock-out на основном складе», срок = 7 дней.
10. Toast: «Цена заблокирована». Grid row обновляется: 🔒 icon.
11. Переходит к следующему товару.

### Scenario 2: Одобрение ценовых действий

**Персона:** Pricing Manager, SEMI_AUTO mode.

1. Pricing Manager видит KPI: «Ожидают действий: 12».
2. Добавляет фильтр: `last_action_status = PENDING_APPROVAL`.
3. Видит 12 товаров с pending actions.
4. Кликает на первый → Detail Panel → tab **«Ценовой журнал»**.
5. Читает explanation: `[Решение] CHANGE: 4 500 → 3 890 (−13,6%)`. Стратегия TARGET_MARGIN, все guards пройдены.
6. Доволен → нажимает **«Одобрить»** в actions bar.
7. Toast: «Действие одобрено». Статус меняется на APPROVED → SCHEDULED.
8. Решает одобрить оставшиеся массово: **Ctrl+A** → выделяет все 11.
9. Bulk Actions Bar: «11 элементов выбрано [Одобрить все]».
10. Нажимает **«Одобрить все»** → modal подтверждения → «Одобрить (11)».
11. Toast: «11 действий одобрено».
12. Grid обновляется: все badge-и → SCHEDULED.

### Scenario 3: Расследование убыточного товара

**Персона:** Аналитик, исследует drop в марже.

1. Аналитик сортирует grid по `margin_pct ASC` → товары с низкой маржой наверху.
2. Видит товар «Кроссовки зимние» с маржой 3,2%.
3. Кликает → Detail Panel → tab **«P&L»**.
4. Видит breakdown:
   - Выручка: 89 000 ₽
   - Комиссия МП: −13 350 ₽ (15%)
   - Логистика: −12 460 ₽ ← аномально высокая
   - Возвраты: −8 900 ₽
   - Себестоимость: −48 000 ₽
   - Итого P&L: 2 850 ₽ (3,2%)
5. Переходит на tab **«Промо журнал»** → товар участвовал в промо по 2 990 ₽ (при regular 4 500 ₽) → потеря маржи −12,7%.
6. Переходит на tab **«Остатки»** → return_rate 18,4% ← very high.
7. Tab **«Ценовой журнал»** → видит 3 SKIP решения подряд с причиной «Товар в активном промо» (promo guard).
8. Решение: подождать окончания промо, после чего pricing pipeline автоматически восстановит цену.
9. Создаёт saved view: «Убыточные товары» с фильтром `margin_max = 10%`, sort `margin_pct ASC`.

---

## 25. API reference (сводка)

### Grid

| Method | Path | Описание |
|--------|------|----------|
| GET | `/api/workspace/{workspaceId}/grid` | Paginated grid. Query: `page`, `size`, `sort`, `direction`, filters, `view_id` |
| GET | `/api/workspace/{workspaceId}/grid/export` | CSV export (streaming). Same filters as grid |
| GET | `/api/workspace/{workspaceId}/grid/kpi` | KPI strip aggregates (TBD endpoint) |

### Offer detail

| Method | Path | Описание |
|--------|------|----------|
| GET | `/api/workspace/{workspaceId}/offers/{offerId}` | Composite offer detail |
| GET | `/api/workspace/{workspaceId}/offers/{offerId}/price-journal` | Price journal (paginated) |
| GET | `/api/workspace/{workspaceId}/offers/{offerId}/promo-journal` | Promo journal (paginated) |

### Saved views

| Method | Path | Описание |
|--------|------|----------|
| GET | `/api/workspace/{workspaceId}/views` | List user views |
| POST | `/api/workspace/{workspaceId}/views` | Create view |
| PUT | `/api/workspace/{workspaceId}/views/{viewId}` | Update view |
| DELETE | `/api/workspace/{workspaceId}/views/{viewId}` | Delete view |

### Actions (delegated to Execution/Pricing)

| Method | Path | Описание |
|--------|------|----------|
| POST | `/api/actions/{actionId}/approve` | Approve action |
| POST | `/api/actions/{actionId}/reject` | Reject action. Body: `{ cancelReason }` |
| POST | `/api/actions/{actionId}/hold` | Hold action. Body: `{ holdReason }` |
| POST | `/api/actions/{actionId}/resume` | Resume action |
| POST | `/api/actions/{actionId}/cancel` | Cancel action. Body: `{ cancelReason }` |
| POST | `/api/actions/bulk-approve` | Bulk approve. Body: `{ actionIds: [...] }` |
| POST | `/api/workspace/{workspaceId}/offers/{offerId}/lock` | Lock price |
| POST | `/api/workspace/{workspaceId}/offers/{offerId}/unlock` | Unlock price |

### Cost profile

| Method | Path | Описание |
|--------|------|----------|
| PUT | `/api/cost-profiles` | Update cost_price (SCD2) |

---

## 26. Data display conventions

Все conventions наследуются от [frontend-design-direction.md](frontend-design-direction.md) §Data display conventions.

### Числа

| Тип | Формат | Пример | Font |
|-----|--------|--------|------|
| Валюта | Space тысяч, comma decimal, ₽ suffix | `1 290 ₽`, `45 000 ₽` | JetBrains Mono |
| Процент | Comma decimal, один знак, % suffix | `18,3%`, `60,0%` | JetBrains Mono |
| Количество | Space тысяч, без десятичных | `1 234`, `142` | JetBrains Mono |
| Дельта | Arrow + %, colored | `↑ 8,2%` green, `↓ 2,1%` red, `→ 0,0%` gray | JetBrains Mono |
| Скорость | Comma decimal, шт./день | `3,2 шт./день` | JetBrains Mono |
| Дней покрытия | Comma decimal, один знак | `18,5` | JetBrains Mono |

### Даты

| Тип | Формат | Пример |
|-----|--------|--------|
| Relative (recency) | Русский relative | `12 мин назад`, `3 часа назад`, `вчера` |
| Absolute (historical) | Russian short month | `28 мар 2026` |
| Timestamp | Date + time 24h | `28 мар, 14:32` |
| Period | Range | `01 апр – 15 апр 2026` |

### Status badges

| Status | Русский label | Color | Dot |
|--------|---------------|-------|-----|
| SUCCEEDED | Выполнено | `--status-success` | ● green |
| FAILED | Ошибка | `--status-error` | ● red |
| PENDING_APPROVAL | Ожидает | `--status-info` | ● blue |
| EXECUTING | Выполняется | `--status-info` | ● blue (pulsing) |
| ON_HOLD | Приостановлено | `--status-warning` | ● yellow |
| EXPIRED | Просрочено | `--status-neutral` | ● gray |
| CANCELLED | Отменено | `--status-neutral` | ● gray |
| SUPERSEDED | Заменено | `--status-neutral` | ● gray |
| RETRY_SCHEDULED | Повтор | `--status-warning` | ● yellow |
| RECONCILIATION_PENDING | Сверка | `--status-info` | ● blue |

### Decision type badges

| Type | Русский label | Color |
|------|---------------|-------|
| CHANGE | Изменение | `--status-info` (blue) |
| SKIP | Пропуск | `--status-neutral` (gray) |
| HOLD | Удержание | `--status-warning` (yellow) |

### Marketplace badges

| Marketplace | Label | Color |
|-------------|-------|-------|
| WB (Wildberries) | `WB` | Purple (#7B2D8E bg, white text) |
| Ozon | `Ozon` | Blue (#005BFF bg, white text) |

### Stock risk badges

| Risk | Русский label | Color |
|------|---------------|-------|
| CRITICAL | Критический | `--status-error` (red) |
| WARNING | Предупреждение | `--status-warning` (yellow) |
| NORMAL | Нормальный | `--status-success` (green) |

### Promo status badges

| Status | Русский label | Color |
|--------|---------------|-------|
| PARTICIPATING | Участвует | `--status-success` (green, filled) |
| ELIGIBLE | Доступно | `--status-info` (blue, outline) |
| — (no promo) | — | No badge |

### Margin color thresholds

| Range | Color |
|-------|-------|
| ≥ 30% | `--finance-positive` (green) |
| 10% – 30% | `--text-primary` (default) |
| 0% – 10% | `--status-warning` (yellow) |
| < 0% | `--finance-negative` (red) |

---

## 27. Accessibility

- **Grid rows:** navigable with `↑`/`↓` arrow keys. Focus ring (`2px --accent-primary`) on active row.
- **Checkboxes:** `Space` to toggle. `aria-label="Выбрать {product_name}"`.
- **Status badges:** `aria-label` includes text status, not just color. Example: `aria-label="Статус: Ошибка"`.
- **Icon buttons:** `aria-label` on all icon-only buttons (lock, export, close, etc.).
- **Filter pills:** focusable with Tab, removable with `Delete` key.
- **Detail Panel:** focus trap when open. `Escape` to close.
- **Modals:** focus trap. Close with `Escape`.
- **Color contrast:** all text meets WCAG 2.1 AA (4.5:1 for normal text, 3:1 for large text).
- **No information by color alone:** all status badges have text labels alongside colored dots.

---

## 28. Performance requirements

| Metric | Target | Механизм |
|--------|--------|----------|
| Grid page load (P95) | < 200ms | Server-side pagination, JDBC read model, batched CH enrichment |
| Grid sort/filter (P95) | < 300ms | Dynamic SQL whitelist, indexed columns |
| Detail panel load (P95) | < 150ms | Composite query per offer |
| CSV export (1k rows) | < 3s | Streaming response, JDBC cursor |
| CSV export (100k rows) | < 60s | Streaming, async for >10k |
| WebSocket update latency | < 500ms | STOMP over WebSocket |
| Column config change | Instant | Client-side, persisted async |
| Inline edit save | < 500ms | Single API call |

### Frontend performance

| Metric | Target |
|--------|--------|
| Initial bundle (grid module) | < 200KB gzipped |
| AG Grid virtual scroll | Smooth at 200 rows × 25 columns |
| Filter apply | < 100ms (client-side URL update + API call) |
| Tab switch (detail panel) | < 50ms (lazy loaded, cached) |

---

## 29. Related documents

- [frontend-design-direction.md](frontend-design-direction.md) — design system, components, layout, conventions
- [seller-operations.md](../modules/seller-operations.md) — grid read model, filters, columns, views, queues, journals, REST API
- [pricing.md](../modules/pricing.md) — decisions, policies, locks, explanation format
- [execution.md](../modules/execution.md) — action lifecycle, state machine, manual interventions
- [promotions.md](../modules/promotions.md) — promo evaluation, participation, promo journal
- [analytics-pnl.md](../modules/analytics-pnl.md) — P&L formula, ClickHouse enrichment, inventory intelligence, data quality
- [Bulk Operations & Draft Mode](../features/2026-03-31-bulk-operations-draft-mode.md) — bulk formula, draft mode, bulk cost update — feature spec
