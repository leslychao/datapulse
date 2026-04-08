---
name: Mismatches page redesign
overview: "Полный редизайн страницы «Расхождения» (/workspace/:id/mismatches): от информационно-перегруженного dashboard к action-oriented рабочему экрану с акцентом на таблице, компактными KPI, сворачиваемыми графиками, унифицированными фильтрами и декомпозицией 895-строчного монолита на 5 компонентов."
todos:
  - id: decompose-dashboard
    content: "Декомпозиция MismatchDashboardPageComponent: вынести column defs в mismatch-column-defs.ts, создать MismatchKpiStripComponent, MismatchChartsSectionComponent, MismatchToolbarComponent, MismatchGridComponent. Оркестратор <= 200 строк."
    status: completed
  - id: unified-filter-bar
    content: Заменить кастомные pill-фильтры на shared dp-filter-bar с multi-select для type/status/severity, select для connection, date-range для period, text для search.
    status: completed
  - id: url-filters
    content: Подключить URL-based filter persistence через syncFilterBarToUrl/readFilterBarFromUrl из url-filters.ts. Fallback на ViewStateService при пустом URL.
    status: completed
  - id: compact-kpi
    content: Заменить 4 dp-kpi-card на inline KPI chips в строке заголовка (MismatchKpiStripComponent). Тренд -- в tooltip.
    status: completed
  - id: collapsible-charts
    content: Обернуть графики в MismatchChartsSectionComponent с toggle-кнопкой. По умолчанию свёрнуты. Состояние в ViewStateService.
    status: completed
  - id: grid-improvements
    content: Перейти на dp-data-grid, добавить колонку 'Давность' с цветовой кодировкой, MP-badge в колонку Оффер, CRITICAL left-border, quick-action колонку.
    status: completed
  - id: detail-panel-integration
    content: Добавить 'mismatch' в DetailPanelEntityType, сделать панель ресайзабельной, добавить prev/next навигацию между расхождениями.
    status: completed
  - id: i18n-cleanup
    content: Добавить недостающие i18n ключи в ru.json (toolbar, charts toggle, давность и т.д.)
    status: completed
isProject: false
---

# Редизайн страницы «Расхождения»

## Проблемы текущей реализации

### UX / Layout
- **60% viewport до таблицы** -- 4 KPI-карточки + 2 графика + 6 контролов фильтрации отодвигают таблицу (где пользователь работает) далеко вниз
- **Графики всегда видны** -- donut + timeline bar chart занимают ~200px, хотя пользователь приходит работать с таблицей, а не смотреть графики
- **Фильтры не унифицированы** -- кастомные pill-кнопки вместо shared `dp-filter-bar` (multi-select dropdown с badge-счётчиком), используемого в Grid и других страницах
- **Панель деталей не ресайзабельна** -- хардкод 440px, тогда как shared `DetailPanelService` даёт ресайз от 320 до 50% viewport
- **Нет навигации prev/next** в панели деталей -- чтобы перейти к следующему расхождению, надо закрыть и кликнуть
- **Кликабельность donut chart не очевидна** -- фильтр по типу при клике на сектор, но нет визуального affordance

### Архитектура / Код
- **Монолит** -- [`mismatch-dashboard-page.component.ts`](frontend/src/app/features/mismatches/mismatch-dashboard-page.component.ts) = **895 строк** (лимит 300, рекомендация 200)
- **Не использует `dp-filter-bar`** -- кастомная реализация фильтров вместо [shared filter-bar](frontend/src/app/shared/components/filter-bar/filter-bar.component.ts)
- **Не использует `dp-data-grid`** -- raw `ag-grid-angular` вместо [shared wrapper](frontend/src/app/shared/components/data-grid/data-grid.component.ts)
- **Не использует URL-based фильтры** -- `ViewStateService` без URL sync, тогда как [url-filters.ts](frontend/src/app/shared/utils/url-filters.ts) предоставляет `syncFilterBarToUrl` + `readFilterBarFromUrl`
- **Column defs в конструкторе** -- 150 строк определений колонок замусоривают класс компонента

---

## Целевой Layout

```
┌──────────────────────────────────────────────────────────────────┐
│  Расхождения    [42 акт.] [5 крит.] [3.2ч среднее] [8 авто]    │  <- Заголовок + inline KPI chips
├──────────────────────────────────────────────────────────────────┤
│  [Тип ▾] [Статус ▾2] [Важность ▾] [Подключение ▾] [Период]    │  <- dp-filter-bar
│  [Поиск...___________]          [Столбцы] [Экспорт] [▼ Графики]│  <- Toolbar
├──────────────────────────────────────────────────────────────────┤
│  (Сворачиваемая секция графиков — по умолчанию свёрнуты)        │
│  ┌──────────────┐ ┌────────────────────────────┐                │
│  │  Donut chart  │ │  Timeline stacked bar      │                │
│  └──────────────┘ └────────────────────────────┘                │
├──────────────────────────────────────────────────────────────────┤
│  AG Grid — занимает весь оставшийся viewport                     │
│  ☐  Оффер          MP  Тип   Ожид.  Факт   Δ%  Давность Статус │
│  ☐  Кроссовки...   WB  Цена  1990   2190  +10% 2ч назад ● Акт. │
│  ☐  Футболка...    Ozon Ост. 150    120   -20% 5ч назад ● Акт. │
│  ...                                                             │
├──────────────────────────────────────────────────────────────────┤
│  « 1 2 3 ... 12 »    50 / стр  Всего: 584                       │
│  ▓ 3 выбрано   [Подтвердить]  [Игнорировать]  [Отмена]          │
└──────────────────────────────────────────────────────────────────┘
                                          ┌─── Resizable Detail Panel ──┐
                                          │  ← →  Оффер: Кроссовки...  │
                                          │  WB · Цена · CRITICAL · Акт│
                                          │  ─────────────────────────  │
                                          │  [Сравнение] [История] [Акт]│
                                          │  ...                        │
                                          │  ─────────────────────────  │
                                          │  [Подтвердить] [Решить] ... │
                                          └─────────────────────────────┘
```

Ключевой принцип: **таблица -- герой страницы**. KPI дают контекст одной строкой. Графики доступны, но не навязываются.

---

## Детали по каждому изменению

### 1. Compact KPI Strip (inline в заголовке)

**Было:** 4 отдельные `dp-kpi-card` (каждая ~150px, с иконкой, трендом, shimmer) -- целая строка.

**Станет:** Компактные "chip"-ы прямо в строке заголовка. Каждый chip: число + label, цвет по accent. Формат: `[42 активных] [5 критичных] [3.2ч среднее] [8 авто-решенных]`.

- Новый компонент: `MismatchKpiStripComponent` (~60 строк)
- Данные из того же `summaryQuery`
- Chip с критичными получает `--status-error` фон, активные -- `--status-warning`
- Тренд показывается tooltip-ом при hover, а не inline (экономия места)
- Shimmer: серые прямоугольники вместо chip-ов при загрузке

### 2. Сворачиваемые графики

**Было:** Всегда видны, `grid grid-cols-[2fr_3fr]`, ~200px высоты.

**Станет:** Секция оборачивается в `dp-section-card` с toggle-кнопкой в toolbar ("Графики ▼/▲"). По умолчанию **свёрнуты**.

- Состояние collapse сохраняется в `ViewStateService` (ключ `mismatches:chartsCollapsed`)
- Когда свёрнуты -- нулевая высота, таблица занимает максимум
- Кнопка toggle в toolbar правее search, рядом с Export
- Новый компонент: `MismatchChartsSectionComponent` (~100 строк) -- владеет обоими chart options, рендерит `dp-chart`

### 3. Унифицированный Filter Bar

**Было:** 6 кастомных контролов (select для connection, pill-кнопки для type/status/severity, date inputs, search input) -- свой layout, своя логика toggle.

**Станет:** Shared `dp-filter-bar` с конфигом:

```typescript
readonly filterConfigs: FilterConfig[] = [
  { key: 'type', label: 'mismatches.filter.type', type: 'multi-select',
    options: ['PRICE','STOCK','PROMO','FINANCE'].map(t => ({
      value: t, label: 'mismatches.type.' + t
    })) },
  { key: 'status', label: 'mismatches.filter.status', type: 'multi-select',
    options: ['ACTIVE','ACKNOWLEDGED','RESOLVED','AUTO_RESOLVED','IGNORED'].map(s => ({
      value: s, label: 'mismatches.status.' + s
    })) },
  { key: 'severity', label: 'mismatches.filter.severity', type: 'multi-select',
    options: ['WARNING','CRITICAL'].map(s => ({
      value: s, label: 'mismatches.severity.' + s
    })) },
  { key: 'connectionId', label: 'mismatches.filter.connection', type: 'select',
    options: [] /* dynamic from connectionsQuery */ },
  { key: 'period', label: 'mismatches.filter.period', type: 'date-range' },
  { key: 'query', label: 'mismatches.filter.search', type: 'text' },
];
```

- URL sync через `syncFilterBarToUrl` / `readFilterBarFromUrl` из [`url-filters.ts`](frontend/src/app/shared/utils/url-filters.ts)
- Дефолтный статус `ACTIVE` -- через инициализацию сигнала
- При active filters > 0 -- кнопка "Сбросить всё" встроена в `dp-filter-bar`

### 4. Toolbar (Search + Actions)

Отдельная строка между filter-bar и таблицей:

- Слева: текст "Показано 1--50 из 584"
- Справа: `[Поиск]` input (debounced), `[Столбцы]` dropdown, `[Экспорт CSV]`, `[Графики ▼]` toggle
- Новый компонент: `MismatchToolbarComponent` (~70 строк)

### 5. Таблица -- улучшения

**Используем `dp-data-grid`** вместо raw `ag-grid-angular`:
- Автоматическая русская локаль
- Встроенный spinner при загрузке
- `viewStateKey` для сохранения ширины колонок

**Изменения в колонках:**
- **Убрать** отдельную колонку "MP" -- маркетплейс показывать badge-ом внутри колонки "Оффер" (экономия 80px)
- **Добавить колонку "Давность"** -- `formatDistanceToNow` с цветовой кодировкой: зелёный < 1ч, жёлтый 1-6ч, оранжевый 6-24ч, красный > 24ч. Это важнее абсолютной даты
- **CRITICAL строки** -- левый border `4px solid var(--status-error)` вместо css-класса (более заметно)
- **Severity badge** -- видима по умолчанию (сейчас `hide: true`)
- **Quick-action** -- иконка-кнопка "Подтвердить" (check) на строках с status=ACTIVE, без открытия панели деталей

Column defs вынести в отдельный файл `mismatch-column-defs.ts` (~120 строк).

### 6. Панель деталей -- интеграция с shared + prev/next

**Было:** Кастомный `dp-mismatch-detail-panel` с фиксированной шириной 440px.

**Станет:** 
- Добавить `'mismatch'` в `DetailPanelEntityType` в [`detail-panel.service.ts`](frontend/src/app/shared/services/detail-panel.service.ts)
- Панель ресайзабельна (drag handle, мин 320px, макс 50% viewport)
- **Prev/Next навигация**: стрелки в шапке панели для перехода между расхождениями из текущего списка

**Сохранить внутреннюю структуру панели** (comparison/timeline/action tabs), но:
- Вкладка "Сравнение": добавить визуальную стрелку `Ожидаемое → Фактическое` с delta badge
- Вкладка "История": использовать вертикальную timeline с цветными точками (уже есть, хорошо)
- Вкладка "Действие": если есть related action -- показать mini-card с ссылкой

### 7. Компонентная декомпозиция

Текущий монолит (895 строк) разбивается на:

| Компонент | Ответственность | Оценка строк |
|---|---|---|
| `MismatchDashboardPageComponent` | Оркестрация: query-ы, routing, WebSocket effect | ~180 |
| `MismatchKpiStripComponent` | Inline KPI chips из summary data | ~60 |
| `MismatchChartsSectionComponent` | Donut + timeline charts, collapsible | ~100 |
| `MismatchToolbarComponent` | Search, columns toggle, export, charts toggle | ~70 |
| `MismatchGridComponent` | AG Grid wrapper, column defs, selection, context menu | ~150 |
| `mismatch-column-defs.ts` | Column definitions (не компонент, утилитарный файл) | ~120 |
| `MismatchDetailPanelComponent` | Без изменений структуры, добавить prev/next | ~400 (уже 384) |
| `MismatchResolveModalComponent` | Без изменений | ~114 (уже) |

### 8. URL-based state management

Заменить `ViewStateService`-only подход на URL + localStorage fallback:

- **Фильтры** в URL: `?type=PRICE,STOCK&status=ACTIVE&severity=CRITICAL&connectionId=5&period_from=2026-04-01&period_to=2026-04-09&query=кроссовки`
- **Сортировка** в URL: `?sortBy=detectedAt&sortDir=desc`
- **Выбранный mismatch** в URL: `?selected=42` (уже есть)
- **Страница** в URL: `?page=2&size=50`
- При отсутствии URL-параметров -- restore из `ViewStateService`
- Используем `readFilterBarFromUrl` + `syncFilterBarToUrl` из `url-filters.ts`

---

## Чего НЕ меняем (бэкенд)

Бэкенд API (`MismatchController`, `MismatchService`, `MismatchJdbcRepository`) полностью покрывает нужды редизайна:
- Фильтрация по type, status, severity, connectionId, period, query -- есть
- Сортировка -- есть
- Пагинация -- есть
- Summary endpoint -- есть
- Bulk operations -- есть
- Export CSV -- есть
- Detail с timeline, thresholds, related action -- есть

Единственное backend-изменение -- нет. Всё чисто frontend.

---

## Порядок реализации

Шаги упорядочены так, чтобы на каждом этапе страница оставалась рабочей.
