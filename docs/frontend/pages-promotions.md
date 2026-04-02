# Promotions Module — Frontend Page Specification

**Фаза:** D — Promotions
**Модуль:** [Promotions](../modules/promotions.md)
**Дизайн-система:** [Frontend Design Direction](frontend-design-direction.md)

---

## Содержание

- [Глоссарий badge-ов и статусов](#глоссарий-badge-ов-и-статусов)
- [Экран 1: Активные кампании](#экран-1-активные-кампании)
- [Экран 2: Детали кампании](#экран-2-детали-кампании)
- [Экран 3: Промо-политики](#экран-3-промо-политики)
- [Экран 4: Форма промо-политики](#экран-4-форма-промо-политики)
- [Экран 5: Назначения политики](#экран-5-назначения-политики)
- [Экран 6: Оценки (Evaluations)](#экран-6-оценки-evaluations)
- [Экран 7: Решения (Decisions)](#экран-7-решения-decisions)
- [Экран 8: Ручные промо-действия](#экран-8-ручные-промо-действия)
- [Пользовательские сценарии](#пользовательские-сценарии)
- [Граничные случаи (Edge cases)](#граничные-случаи-edge-cases)

---

## Глоссарий badge-ов и статусов

### Campaign status (canonical_promo_campaign.status)

| Значение | Badge | Цвет | Русская метка |
|----------|-------|------|---------------|
| `UPCOMING` | `--status-info` | Синяя точка | Предстоящая |
| `ACTIVE` | `--status-success` | Зелёная точка | Активна |
| `FROZEN` | `--status-warning` | Жёлтая точка | Заморожена |
| `ENDED` | `--status-neutral` | Серая точка | Завершена |
| `CANCELLED` | `--status-neutral` | Серая точка | Отменена |

### Participation status (canonical_promo_product.participation_status)

| Значение | Badge | Цвет | Русская метка |
|----------|-------|------|---------------|
| `ELIGIBLE` | `--status-info` | Синяя точка | Доступен |
| `PARTICIPATING` | `--status-success` | Зелёная точка | Участвует |
| `DECLINED` | `--status-neutral` | Серая точка | Отклонён |
| `REMOVED` | `--status-neutral` | Серая точка | Удалён |
| `BANNED` | `--status-error` | Красная точка | Заблокирован |
| `AUTO_DECLINED` | `--status-neutral` | Серая точка | Авто-отклонён |

### Evaluation result (promo_evaluation.evaluation_result)

| Значение | Badge | Цвет | Русская метка |
|----------|-------|------|---------------|
| `PROFITABLE` | `--status-success` | Зелёная точка | Прибыльно |
| `MARGINAL` | `--status-warning` | Жёлтая точка | Пограничный |
| `UNPROFITABLE` | `--status-error` | Красная точка | Убыточно |
| `INSUFFICIENT_STOCK` | `--status-warning` | Жёлтая точка | Мало остатков |
| `INSUFFICIENT_DATA` | `--status-warning` | Жёлтая точка | Нет данных |

### Decision type (promo_decision.decision_type)

| Значение | Badge | Цвет | Русская метка |
|----------|-------|------|---------------|
| `PARTICIPATE` | `--status-success` | Зелёная точка | Участвовать |
| `DECLINE` | `--status-neutral` | Серая точка | Отказать |
| `DEACTIVATE` | `--status-error` | Красная точка | Деактивировать |
| `PENDING_REVIEW` | `--status-warning` | Жёлтая точка | На проверку |

### Promo action status (promo_action.status)

| Значение | Badge | Цвет | Русская метка |
|----------|-------|------|---------------|
| `PENDING_APPROVAL` | `--status-warning` | Жёлтая точка | Ожидает одобрения |
| `APPROVED` | `--status-info` | Синяя точка | Одобрено |
| `EXECUTING` | `--status-info` + spinner | Синяя точка | Выполняется |
| `SUCCEEDED` | `--status-success` | Зелёная точка | Выполнено |
| `FAILED` | `--status-error` | Красная точка | Ошибка |
| `EXPIRED` | `--status-neutral` | Серая точка | Истекло |
| `CANCELLED` | `--status-neutral` | Серая точка | Отменено |

### Promo policy status (promo_policy.status)

| Значение | Badge | Цвет | Русская метка |
|----------|-------|------|---------------|
| `DRAFT` | `--status-neutral` | Серая точка | Черновик |
| `ACTIVE` | `--status-success` | Зелёная точка | Активна |
| `PAUSED` | `--status-warning` | Жёлтая точка | Приостановлена |
| `ARCHIVED` | `--status-neutral` | Серая точка | Архив |

### Participation mode (promo_policy.participation_mode)

| Значение | Русская метка | Tooltip |
|----------|---------------|---------|
| `RECOMMENDATION` | Рекомендация | Показывает рекомендацию, оператор решает |
| `SEMI_AUTO` | Полу-авто | Создаёт действие, ожидает одобрения |
| `FULL_AUTO` | Полный авто | Автоматическое участие через guards |
| `SIMULATED` | Симуляция | Имитация без реального вызова API |

### Marketplace badge

| Маркетплейс | Метка | Цвет фона |
|-------------|-------|-----------|
| Wildberries | WB | `#CB11AB` (фирменный розовый), белый текст |
| Ozon | Ozon | `#005BFF` (фирменный синий), белый текст |

### Формат объяснений (Decision explanation)

Блок объяснения решения в Detail Panel использует structured layout:

```
┌─ Объяснение решения ────────────────────────────────┐
│                                                      │
│  Результат оценки    [PROFITABLE badge]              │
│                                                      │
│  Маржа на промо-цене     14,2%   (мин. 10,0%)  ✓   │
│  Скидка                  23,0%   (макс. 30,0%) ✓   │
│  Остатки (дней покрытия) 18      (мин. 7)      ✓   │
│  Категория               Одежда  (whitelist)    ✓   │
│                                                      │
│  Политика: «Стандартная промо-политика» v3           │
│  Режим: Полу-авто                                    │
│                                                      │
│  Решение: Участвовать → действие ожидает одобрения   │
└──────────────────────────────────────────────────────┘
```

Каждый constraint отображается как строка с:
- Название параметра (`--text-sm`, `--text-secondary`)
- Фактическое значение (monospace, `--text-primary`)
- Пороговое значение в скобках (`--text-secondary`)
- Иконка результата: ✓ зелёная (прошёл) / ✗ красная (не прошёл) / ⚠ жёлтая (пограничный)

---

## Экран 1: Активные кампании

### 1. Маршрут и навигация

| Свойство | Значение |
|----------|----------|
| Route | `/workspace/:id/promo/campaigns` |
| Breadcrumbs | Промо → Кампании |
| Activity Bar | Иконка модуля «Промо» (например, `megaphone` из Lucide) |
| Tab | «Кампании» — tab по умолчанию при входе в модуль |
| Ctrl+K | Поиск по названию кампании, external_promo_id |

### 2. Назначение экрана

Обзор всех промо-акций маркетплейсов, подключённых к workspace. Центральная точка входа для работы с промо: просмотр предстоящих, активных и завершённых кампаний, быстрая навигация к деталям, контроль участия.

### 3. Расположение в shell

| Зона | Содержимое |
|------|------------|
| Main Area | KPI-полоска + фильтры + таблица кампаний |
| Detail Panel | Открывается при клике на строку — сводка кампании |
| Bottom Panel | Не используется (нет массовых действий на уровне кампаний) |
| Status Bar | Стандартный: время последнего PROMO_SYNC per connection |

### 4. KPI-полоска

4 карточки над таблицей:

| Карточка | Значение | Источник |
|----------|----------|----------|
| Активных кампаний | Число | Кол-во `status = ACTIVE` |
| Предстоящих | Число | Кол-во `status = UPCOMING` |
| Товаров участвует | Число | Σ `participated_count` по active campaigns |
| Ожидают решения | Число | Кол-во promo_action с `status = PENDING_APPROVAL` |

Дельта-тренд не показывается (промо — event-driven, trend неинформативен).

### 5. Таблица / грид

AG Grid. Дефолтная сортировка: `starts_at DESC` (новые сверху).

| # | Колонка | Поле API | Тип | Ширина | Выравнивание | Формат | Frozen |
|---|---------|----------|-----|--------|-------------|--------|--------|
| 1 | ☐ | — | Checkbox | 40px | center | — | Да |
| 2 | Название | `promo_name` | Text | 250px | left | Truncate с tooltip | Да |
| 3 | Маркетплейс | `source_platform` | Badge | 80px | center | WB / Ozon badge | Нет |
| 4 | Тип | `promo_type` | Text | 140px | left | Оригинальная таксономия провайдера | Нет |
| 5 | Механика | `mechanic` | Text | 120px | left | DISCOUNT, SPP, CASHBACK и т.д. | Нет |
| 6 | Начало | `date_from` | Date | 110px | left | `28 мар 2026` | Нет |
| 7 | Окончание | `date_to` | Date | 110px | left | `28 мар 2026` или «Бессрочная» если null | Нет |
| 8 | Заморозка | `freeze_at` | Date | 110px | left | `28 мар 14:00` или `—` | Нет |
| 9 | Доступно товаров | `eligible_count` | Number | 100px | right | Monospace, `1 234` | Нет |
| 10 | Участвует | `participated_count` | Number | 100px | right | Monospace, `1 234` | Нет |
| 11 | Статус | `status` | Badge | 120px | center | См. badge mapping campaign status | Нет |
| 12 | Подключение | `connection_name` | Text | 150px | left | Название marketplace_connection | Нет |

Колонки по умолчанию: все. Скрыты по умолчанию: Заморозка, Подключение (доступны через «Колонки»).

### 6. Панель фильтров

| Фильтр | Тип | Значения | По умолчанию |
|--------|-----|----------|-------------|
| Подключение | Multi-select dropdown | Все connection-ы workspace | Все |
| Маркетплейс | Multi-select chips | WB, Ozon | Все |
| Тип акции | Multi-select dropdown | Динамический из данных | Все |
| Статус | Multi-select chips | Предстоящая, Активна, Заморожена, Завершена, Отменена | Предстоящая + Активна |
| Период | Date range picker | `from`, `to` | Последние 30 дней |
| Поиск | Text input | Поиск по названию | — |

Фильтры отображаются как pills. Дефолтный пресет: статус = Предстоящая + Активна (прошедшие скрыты, чтобы фокус на актуальном).

### 7. Сортировка и пагинация

- Sortable: Начало, Окончание, Доступно товаров, Участвует, Статус.
- Дефолтная сортировка: Начало DESC.
- Пагинация: серверная, 50 / 100 / 200 строк. «Показано 1–50 из 312».

### 8. Панель деталей (Detail Panel)

Открывается при клике на строку. Ширина по умолчанию: 400px.

**Header:**
- Название кампании (bold, `--text-lg`)
- Marketplace badge (WB / Ozon)
- Status badge

**Табы:**

| Таб | Содержимое |
|-----|------------|
| Сводка | Даты, тип, механика, описание, статистика (eligible/participating/declined/banned) |
| Товары (top 10) | Мини-таблица: товар, промо-цена, participation_status. Ссылка «Все товары →» ведёт на Экран 2 |
| Действия | Список promo_action по кампании: тип, статус, дата |

**Сводка — key-value pairs:**

| Метка | Значение |
|-------|----------|
| Маркетплейс | WB / Ozon badge |
| Тип акции | promo_type |
| Механика | mechanic |
| Начало | date_from (формат: `28 мар 2026, 10:00`) |
| Окончание | date_to |
| Заморозка | freeze_at или `—` |
| Дедлайн заявки | participation_deadline или `—` |
| Описание | description (scrollable, max 200px height) |
| Доступно товаров | eligible_count |
| Участвует | participated_count |
| Отклонено | declined_count |
| Заблокировано | banned_count |

### 9. Действия и кнопки

| Действие | Тип кнопки | Расположение | Роли |
|----------|-----------|-------------|------|
| Перейти к деталям | Ghost link | Detail Panel header | Any role |
| Экспорт | Secondary | Toolbar | Any role |
| Обновить данные | Ghost icon (refresh) | Toolbar | Any role |

Нет write-действий на уровне списка кампаний — все решения принимаются на уровне товаров (Экран 2).

### 10. Массовые действия

Не предусмотрены на этом экране. Checkbox используется только для экспорта выбранных строк.

### 11. Пустые состояния

| Ситуация | Сообщение | Действие |
|----------|-----------|----------|
| Нет кампаний (ни одного PROMO_SYNC) | «Промо-акции ещё не загружены. Убедитесь, что подключение настроено и синхронизация промо включена.» | [Перейти к подключениям] |
| Фильтры ничего не нашли | «Нет кампаний, соответствующих фильтрам.» | [Сбросить фильтры] |
| Нет активных подключений | «Нет активных подключений к маркетплейсам.» | [Настроить подключение] |

### 12. Состояния загрузки

| Ситуация | Паттерн |
|----------|---------|
| Первоначальная загрузка | Skeleton: 4 KPI-карточки (shimmer) + таблица из 10 строк |
| Обновление данных (refetch) | Полоса прогресса 2px сверху, данные на месте |
| Применение фильтра | Полоса прогресса 2px, таблица не исчезает |

### 13. Состояния ошибок

| Ситуация | Паттерн |
|----------|---------|
| API недоступен | Toast (error): «Не удалось загрузить кампании. Попробуйте позже.» |
| Частичная загрузка (KPI ok, grid fail) | Grid показывает inline error, KPI остаются |
| 403 Forbidden | Toast (error): «У вас нет доступа к промо-модулю.» |

### 14. Доступ и роли

| Действие | Минимальная роль |
|----------|-----------------|
| Просмотр списка кампаний | VIEWER |
| Просмотр Detail Panel | VIEWER |
| Экспорт | ANALYST |

### 15. Привязка к API

| Компонент | Endpoint | Метод | Параметры |
|-----------|----------|-------|-----------|
| KPI-полоска | `GET /api/promo/campaigns` | GET | `?status=ACTIVE` (count), `?status=UPCOMING` (count) — или агрегатный endpoint |
| Таблица | `GET /api/promo/campaigns` | GET | `?connectionId=...&status=...&marketplaceType=...&from=...&to=...&page=0&size=50&sort=date_from,desc` |
| Detail Panel → сводка | `GET /api/promo/campaigns/{campaignId}` | GET | — |
| Detail Panel → товары | `GET /api/promo/campaigns/{campaignId}/products` | GET | `?page=0&size=10` |
| Detail Panel → действия | `GET /api/promo/actions` | GET | `?campaignId=...` |

TanStack Query: `staleTime: 30s`, `refetchOnWindowFocus: true`. Background refetch показывает progress bar.

### 16. Реальное время / WebSocket

| Событие | STOMP destination | Действие UI |
|---------|-------------------|-------------|
| PROMO_SYNC завершён | `/topic/workspace.{id}.sync.completed` | Refetch таблицы и KPI. Flash-анимация на изменённых строках |
| Campaign status changed | `/topic/workspace.{id}.promo.campaign` | Обновить badge статуса строки in-place |
| New campaign discovered | `/topic/workspace.{id}.promo.campaign` | Добавить строку с highlight-анимацией |

---

## Экран 2: Детали кампании

### 1. Маршрут и навигация

| Свойство | Значение |
|----------|----------|
| Route | `/workspace/:id/promo/campaigns/:campaignId` |
| Breadcrumbs | Промо → Кампании → {название кампании} |
| Tab | Открывается как новый tab: «{название кампании}» (truncated до 30 символов) |
| Переход | Клик по строке в Экране 1 (Detail Panel → «Все товары →»), или двойной клик на строку |

### 2. Назначение экрана

Полная информация о промо-кампании: товары, их evaluation results, решения об участии, статусы действий. Основной экран операционной работы оператора: здесь принимаются решения force-participate / force-skip, одобряются PENDING_APPROVAL действия.

### 3. Расположение в shell

| Зона | Содержимое |
|------|------------|
| Main Area | Header кампании + KPI-полоска + фильтры + таблица товаров |
| Detail Panel | Открывается при клике на товар — evaluation details, decision explanation |
| Bottom Panel | Массовые действия (approve all, reject all) при множественном выборе |

### 4. KPI-полоска

**Header блок** (над KPI, отдельная секция):

```
┌──────────────────────────────────────────────────────────────────────────┐
│  [Ozon badge]  Летняя распродажа 2026                                   │
│  Тип: Скидка на товар · Механика: DISCOUNT · [ACTIVE badge]            │
│  28 мар 2026 — 15 апр 2026 · Заморозка: 27 мар 14:00                  │
└──────────────────────────────────────────────────────────────────────────┘
```

6 KPI-карточек:

| Карточка | Значение | Цвет |
|----------|----------|------|
| Доступно | eligible_count | Нейтральный |
| Участвует | participated_count | `--finance-positive` |
| Отклонено | declined_count | Нейтральный |
| Ожидает решения | pending_review_count | `--status-warning` |
| Выполнено действий | succeeded_actions_count | `--status-success` |
| Ошибки | failed_actions_count | `--status-error` (скрыта если 0) |

### 5. Таблица / грид

AG Grid. Дефолтная сортировка: `participation_status ASC` (ELIGIBLE и PENDING_REVIEW сверху — то, что требует внимания).

| # | Колонка | Поле API | Тип | Ширина | Выравнивание | Формат |
|---|---------|----------|-----|--------|-------------|--------|
| 1 | ☐ | — | Checkbox | 40px | center | — |
| 2 | Товар | `product_name` | Text | 280px | left | Truncate + tooltip. Frozen |
| 3 | SKU | `marketplace_sku` | Text/Code | 120px | left | Monospace |
| 4 | Артикул | `seller_sku_code` | Text/Code | 120px | left | Monospace |
| 5 | Промо-цена | `required_price` | Number | 100px | right | Monospace, `1 290 ₽` |
| 6 | Макс. промо-цена | `max_promo_price` | Number | 110px | right | Monospace, `1 490 ₽` или `—` |
| 7 | Текущая цена | `current_price` | Number | 100px | right | Monospace, `1 590 ₽` |
| 8 | Скидка | computed | Number | 80px | right | Monospace, `23,0%`, `--finance-negative` |
| 9 | Маржа при промо-цене | `margin_at_promo_price` | Number | 120px | right | Monospace, `14,2%`, finance color coding |
| 10 | Остатки | `stock_available` | Number | 80px | right | Monospace, `1 234` |
| 11 | Дней покрытия | `stock_days_of_cover` | Number | 100px | right | Monospace, `18 д.` |
| 12 | Оценка | `evaluation_result` | Badge | 120px | center | См. evaluation result badge |
| 13 | Решение | `decision_type` | Badge | 120px | center | См. decision type badge |
| 14 | Статус участия | `participation_status` | Badge | 130px | center | См. participation status badge |
| 15 | Статус действия | `action_status` | Badge | 130px | center | См. action status badge. Пусто если нет promo_action |
| 16 | Действия | — | Action | 140px | center | Кнопки (см. Экран 8) |

Скрыты по умолчанию: Макс. промо-цена, Артикул, Дней покрытия.

**Conditional formatting:**
- Маржа при промо-цене: `--finance-positive` если ≥ min_margin, `--finance-negative` если < 0, `--status-warning` если 0 < margin < min_margin.
- Остатки: `--status-error` фон если stock_days_of_cover < min_stock_days.
- Скидка: всегда `--finance-negative` (скидка = потеря цены).

### 6. Панель фильтров

| Фильтр | Тип | Значения | По умолчанию |
|--------|-----|----------|-------------|
| Статус участия | Multi-select chips | Доступен, Участвует, Отклонён, Удалён, Заблокирован, Авто-отклонён | Все |
| Оценка | Multi-select chips | Прибыльно, Пограничный, Убыточно, Мало остатков, Нет данных | Все |
| Решение | Multi-select chips | Участвовать, Отказать, На проверку | Все |
| Статус действия | Multi-select chips | Ожидает одобрения, Одобрено, Выполняется, Выполнено, Ошибка, Истекло, Отменено | Все |
| Поиск | Text input | По названию товара, SKU, артикулу | — |

### 7. Сортировка и пагинация

- Sortable: Товар, Промо-цена, Текущая цена, Скидка, Маржа при промо-цене, Остатки, Оценка, Решение, Статус участия.
- Дефолтная сортировка: Статус участия ASC (ELIGIBLE первые), затем Оценка ASC.
- Пагинация: серверная, 50 / 100 / 200 строк.

### 8. Панель деталей (Detail Panel)

Открывается при клике на строку товара.

**Header:**
- Название товара (bold, `--text-lg`)
- SKU (monospace, `--text-sm`, `--text-secondary`)
- Participation status badge

**Табы:**

| Таб | Содержимое |
|-----|------------|
| Оценка | Полный evaluation breakdown (см. [формат объяснений](#формат-объяснений-decision-explanation)) |
| Решение | Decision details: policy snapshot, decision_type, explanation_summary |
| Действие | Текущий promo_action: lifecycle, attempt_count, last_error, timestamps |
| Товар | Ссылка на карточку товара в Seller Operations grid |

**Таб «Оценка» — детали:**

| Метка | Значение |
|-------|----------|
| Промо-цена | `1 290 ₽` |
| Текущая цена | `1 590 ₽` |
| Скидка | `23,0%` |
| Себестоимость (COGS) | `850 ₽` или «Не задана» (warning icon) |
| Маржа при промо-цене | `14,2%` (finance color) |
| Маржа при обычной цене | `22,5%` (finance color) |
| Дельта маржи | `↓ 8,3 п.п.` (`--finance-negative`) |
| Эфф. ставка расходов | `12,8%` |
| Остатки | `234 шт.` |
| Avg. продаж/день | `12,4 шт.` |
| Дней покрытия | `18 д.` |
| Прод. акции (ожид.) | `14 дн.` |
| Результат оценки | [PROFITABLE badge] |

**Таб «Решение»:**

Использует structured explanation layout (см. [глоссарий](#формат-объяснений-decision-explanation)).

Дополнительно:
| Метка | Значение |
|-------|----------|
| Политика | Название + `v{version}` |
| Режим | participation_mode label |
| Решение | decision_type badge |
| Принято | Автоматически / Имя оператора |
| Время | Timestamp |

**Таб «Действие»:**

Если promo_action существует:

| Метка | Значение |
|-------|----------|
| Тип | ACTIVATE / DEACTIVATE |
| Статус | Badge (см. action status) |
| Режим исполнения | LIVE / SIMULATED |
| Промо-цена (целевая) | `1 290 ₽` |
| Попыток | `1 / 2` |
| Последняя ошибка | Текст или `—` |
| Создано | Timestamp |
| Обновлено | Timestamp |

Если promo_action нет: «Действие не создано. Товар оценён, но действие не назначено (режим — Рекомендация).»

### 9. Действия и кнопки

**Toolbar:**

| Действие | Тип кнопки | Роли |
|----------|-----------|------|
| Экспорт | Secondary | ANALYST+ |
| Обновить | Ghost icon | Any role |
| Колонки | Ghost icon | Any role |

**Per-row actions (колонка «Действия»):** см. [Экран 8: Ручные промо-действия](#экран-8-ручные-промо-действия).

### 10. Массовые действия

При множественном выборе (checkboxes) появляется Bottom Panel:

```
┌─────────────────────────────────────────────────────────────────────┐
│  12 товаров выбрано  [Одобрить все] [Отклонить все] [Экспорт]   ×  │
└─────────────────────────────────────────────────────────────────────┘
```

| Действие | Условие | Роли | API |
|----------|---------|------|-----|
| Одобрить все | Все выбранные имеют promo_action в `PENDING_APPROVAL` | PRICING_MANAGER+ | `POST /api/promo/actions/bulk-approve` Body: `{ actionIds: [...] }` |
| Отклонить все | Все выбранные имеют promo_action в `PENDING_APPROVAL` | PRICING_MANAGER+ | `POST /api/promo/actions/bulk-reject` Body: `{ actionIds: [...], reason }`. Модальное подтверждение с полем «Причина» |
| Экспорт | Любое выделение | ANALYST+ | CSV export |

Если выборка смешанная (часть с PENDING_APPROVAL, часть без): кнопки «Одобрить/Отклонить» показывают count рядом — «Одобрить (8 из 12)». Применяется только к eligible строкам.

### 11. Пустые состояния

| Ситуация | Сообщение |
|----------|-----------|
| Кампания без eligible товаров | «В этой акции нет доступных товаров. Возможно, sync ещё не завершился.» |
| Фильтры ничего не нашли | «Нет товаров, соответствующих фильтрам.» + [Сбросить фильтры] |
| Кампания ENDED | Banner сверху: «Кампания завершена. Данные доступны для просмотра.» (neutral, `--bg-secondary`) |

### 12. Состояния загрузки

| Ситуация | Паттерн |
|----------|---------|
| Первоначальная загрузка | Skeleton: header + 6 KPI + таблица 10 строк |
| Pagination / filter change | Progress bar 2px |
| Action в процессе | Spinner в ячейке «Статус действия» конкретной строки |

### 13. Состояния ошибок

| Ситуация | Паттерн |
|----------|---------|
| Campaign not found (404) | Full area: «Кампания не найдена.» + [Вернуться к списку кампаний] |
| Products load failed | Inline error в области таблицы: «Не удалось загрузить товары.» + [Повторить] |
| Action failed (409 Conflict) | Toast (error): «Не удалось выполнить действие: статус изменился. Обновите страницу.» |
| Campaign expired during review | Toast (warning): «Кампания заморожена. Действия недоступны.» Кнопки действий → disabled |

### 14. Доступ и роли

| Действие | Минимальная роль |
|----------|-----------------|
| Просмотр деталей кампании и товаров | VIEWER |
| Экспорт | ANALYST |
| Approve / Reject действий | PRICING_MANAGER |
| Force participate / Force skip | PRICING_MANAGER |

### 15. Привязка к API

| Компонент | Endpoint | Метод | Параметры |
|-----------|----------|-------|-----------|
| Header + KPI | `GET /api/promo/campaigns/{campaignId}` | GET | — |
| Таблица товаров | `GET /api/promo/campaigns/{campaignId}/products` | GET | `?participationStatus=...&search=...&page=0&size=50` |
| Detail Panel → оценка | `GET /api/promo/evaluations` | GET | `?campaignId=...&marketplaceOfferId=...` |
| Detail Panel → решение | `GET /api/promo/decisions` | GET | `?campaignId=...&marketplaceOfferId=...` (latest) |
| Detail Panel → действие | `GET /api/promo/actions` | GET | `?campaignId=...&marketplaceOfferId=...` |
| Force participate | `POST /api/promo/products/{promoProductId}/participate` | POST | `{ targetPromoPrice? }` |
| Force skip | `POST /api/promo/products/{promoProductId}/decline` | POST | `{ reason? }` |
| Approve | `POST /api/promo/actions/{actionId}/approve` | POST | — |
| Reject | `POST /api/promo/actions/{actionId}/reject` | POST | `{ reason }` |
| Cancel | `POST /api/promo/actions/{actionId}/cancel` | POST | `{ cancelReason }` |

### 16. Реальное время / WebSocket

| Событие | STOMP destination | Действие UI |
|---------|-------------------|-------------|
| promo_action status changed | `/topic/workspace.{id}.promo.action` | Обновить badge статуса в строке товара. Flash-анимация. |
| Campaign status changed (FROZEN/ENDED) | `/topic/workspace.{id}.promo.campaign` | Обновить header badge. При FROZEN — disable action buttons, показать banner. |
| PROMO_SYNC completed | `/topic/workspace.{id}.sync.completed` | Refetch таблицы товаров (новые eligible могут появиться). |

---

## Экран 3: Промо-политики

### 1. Маршрут и навигация

| Свойство | Значение |
|----------|----------|
| Route | `/workspace/:id/promo/policies` |
| Breadcrumbs | Промо → Политики |
| Tab | «Политики» |

### 2. Назначение экрана

Управление правилами автоматического участия в промо-акциях. Просмотр, создание, редактирование, активация/приостановка промо-политик.

### 3. Расположение в shell

| Зона | Содержимое |
|------|------------|
| Main Area | Таблица политик + toolbar |
| Detail Panel | Открывается при клике — сводка политики с табами |
| Bottom Panel | Не используется |

### 4. KPI-полоска

Не используется. Политик обычно немного (единицы-десятки), KPI не информативен.

### 5. Таблица / грид

AG Grid. Дефолтная сортировка: `status ASC` (ACTIVE первые), затем `updated_at DESC`.

| # | Колонка | Поле API | Тип | Ширина | Выравнивание | Формат |
|---|---------|----------|-----|--------|-------------|--------|
| 1 | ☐ | — | Checkbox | 40px | center | — |
| 2 | Название | `name` | Text | 250px | left | Frozen |
| 3 | Статус | `status` | Badge | 120px | center | См. policy status badge |
| 4 | Режим участия | `participation_mode` | Text | 140px | left | Русская метка (см. глоссарий) |
| 5 | Мин. маржа | `min_margin_pct` | Number | 100px | right | Monospace, `10,0%` |
| 6 | Мин. остатков (дней) | `min_stock_days_of_cover` | Number | 110px | right | Monospace, `7 д.` |
| 7 | Макс. скидка | `max_promo_discount_pct` | Number | 100px | right | Monospace, `30,0%` или `—` |
| 8 | Версия | `version` | Number | 60px | right | Monospace, `v3` |
| 9 | Назначений | `assignment_count` | Number | 90px | right | Monospace, `5` |
| 10 | Обновлено | `updated_at` | Date | 130px | left | `28 мар, 14:32` |
| 11 | Создал | `created_by_name` | Text | 140px | left | Имя пользователя |

### 6. Панель фильтров

| Фильтр | Тип | Значения | По умолчанию |
|--------|-----|----------|-------------|
| Статус | Multi-select chips | Черновик, Активна, Приостановлена, Архив | Черновик + Активна + Приостановлена |
| Режим | Multi-select chips | Рекомендация, Полу-авто, Полный авто, Симуляция | Все |
| Поиск | Text input | По названию | — |

### 7. Сортировка и пагинация

- Sortable: Название, Статус, Режим участия, Мин. маржа, Версия, Обновлено.
- Дефолтная сортировка: Статус ASC → Обновлено DESC.
- Пагинация: серверная, 50 строк (маловероятен больший объём).

### 8. Панель деталей (Detail Panel)

**Header:**
- Название политики (bold)
- Status badge
- Кнопки: [Редактировать] [Активировать / Приостановить / Архивировать]

**Табы:**

| Таб | Содержимое |
|-----|------------|
| Параметры | Все поля политики в key-value формате |
| Назначения | Список promo_policy_assignment (см. Экран 5) |
| История | Аудит изменений (из audit_log) |

**Таб «Параметры»:**

| Метка | Значение |
|-------|----------|
| Название | name |
| Статус | status badge |
| Режим участия | participation_mode label + tooltip с описанием |
| Мин. маржа при промо-цене | `10,0%` |
| Мин. остатков (дней покрытия) | `7 д.` |
| Макс. скидка промо | `30,0%` или «Без ограничения» |
| Whitelist категорий | Список или «Не задан» |
| Blacklist категорий | Список или «Не задан» |
| Конфиг оценки | JSON viewer (collapsible) |
| Версия | `v3` |
| Создано | Timestamp + имя |

### 9. Действия и кнопки

**Toolbar:**

| Действие | Тип кнопки | Роли |
|----------|-----------|------|
| Создать политику | Primary | PRICING_MANAGER+ |
| Экспорт | Secondary | ANALYST+ |

**Detail Panel actions:**

| Действие | Тип кнопки | Условие | Роли | API |
|----------|-----------|---------|------|-----|
| Редактировать | Secondary | status ≠ ARCHIVED | PRICING_MANAGER+ | Переход к Экрану 4 |
| Активировать | Primary | status = DRAFT или PAUSED | PRICING_MANAGER+ | `POST /api/promo/policies/{id}/activate` |
| Приостановить | Secondary | status = ACTIVE | PRICING_MANAGER+ | `POST /api/promo/policies/{id}/pause` |
| Архивировать | Danger | status ≠ ARCHIVED | PRICING_MANAGER+ | `POST /api/promo/policies/{id}/archive` |

**Confirmation:**
- Активировать: Toast confirmation, без модала.
- Приостановить: Toast confirmation.
- Архивировать: Модальное подтверждение: «Архивировать политику «{name}»? Назначения будут деактивированы.»

### 10. Массовые действия

Не предусмотрены. Политик обычно немного, массовые операции не нужны.

### 11. Пустые состояния

| Ситуация | Сообщение |
|----------|-----------|
| Нет политик | «Промо-политики ещё не созданы. Настройте правила автоматического участия в акциях.» + [Создать политику] |
| Фильтры ничего не нашли | «Нет политик, соответствующих фильтрам.» + [Сбросить фильтры] |

### 12. Состояния загрузки

| Ситуация | Паттерн |
|----------|---------|
| Первоначальная загрузка | Skeleton: таблица 5 строк |
| Действие (activate/pause/archive) | Spinner на кнопке, кнопка disabled |

### 13. Состояния ошибок

| Ситуация | Паттерн |
|----------|---------|
| Ошибка загрузки | Toast (error): «Не удалось загрузить политики.» + [Повторить] |
| Ошибка действия | Toast (error): «Не удалось выполнить действие.» с описанием |
| 409 Conflict (concurrent edit) | Toast (error): «Политика была изменена другим пользователем. Обновите страницу.» |

### 14. Доступ и роли

| Действие | Минимальная роль |
|----------|-----------------|
| Просмотр списка политик | VIEWER |
| Создание / редактирование | PRICING_MANAGER |
| Активация / приостановка / архивация | PRICING_MANAGER |

### 15. Привязка к API

| Компонент | Endpoint | Метод |
|-----------|----------|-------|
| Таблица | `GET /api/promo/policies` | GET |
| Detail Panel | `GET /api/promo/policies/{policyId}` | GET |
| Создание | `POST /api/promo/policies` | POST |
| Обновление | `PUT /api/promo/policies/{policyId}` | PUT |
| Активация | `POST /api/promo/policies/{policyId}/activate` | POST |
| Приостановка | `POST /api/promo/policies/{policyId}/pause` | POST |
| Архивация | `POST /api/promo/policies/{policyId}/archive` | POST |

### 16. Реальное время / WebSocket

Не критично. Политики редактируются редко. Refetch при возврате на tab (TanStack Query `refetchOnWindowFocus`).

---

## Экран 4: Форма промо-политики

### 1. Маршрут и навигация

| Свойство | Значение |
|----------|----------|
| Route (create) | `/workspace/:id/promo/policies/new` |
| Route (edit) | `/workspace/:id/promo/policies/:policyId/edit` |
| Breadcrumbs | Промо → Политики → Новая политика / Редактирование: {name} |
| Tab | «Новая политика» или «{name} (ред.)» |

### 2. Назначение экрана

Создание или редактирование промо-политики. Все параметры оценки, пороги, whitelist/blacklist категорий, режим участия.

### 3. Расположение в shell

| Зона | Содержимое |
|------|------------|
| Main Area | Форма с секциями |
| Detail Panel | Не используется |
| Bottom Panel | Не используется |

### 4. KPI-полоска

Не используется.

### 5. Таблица / грид

Не используется (это форма, не список).

### 6. Панель фильтров

Не применимо.

### 7. Сортировка и пагинация

Не применимо.

### 8. Панель деталей (Detail Panel)

Не используется. Форма занимает всю Main Area.

### 9. Действия и кнопки

**Форма разделена на секции:**

#### Секция 1: Основные параметры

| Поле | Тип | Обязательность | Валидация | Label |
|------|-----|---------------|-----------|-------|
| Название | Text input | Обязательно | 1–255 символов | «Название политики» |
| Режим участия | Radio group или Segmented control | Обязательно | — | «Режим участия» |

**Режим участия — visual selector:**

```
┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
│ Рекомендация│ │  Полу-авто  │ │ Полный авто │ │  Симуляция  │
│             │ │             │ │             │ │             │
│ Показывает  │ │ Ожидает     │ │ Автомат.    │ │ Без вызова  │
│ рекомендацию│ │ одобрения   │ │ через guards│ │ API         │
└─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘
```

Каждый вариант: карточка с заголовком и описанием. Выбранный — `--bg-active` фон + `--accent-primary` border.

#### Секция 2: Пороги оценки

| Поле | Тип | Обязательность | Валидация | Label | Placeholder |
|------|-----|---------------|-----------|-------|-------------|
| Мин. маржа при промо-цене | Number input (%) | Обязательно | 0–100, 1 decimal | «Минимальная маржа, %» | `10,0` |
| Мин. остатков (дней покрытия) | Number input (int) | Обязательно, default 7 | ≥ 1 | «Мин. дней покрытия остатков» | `7` |
| Макс. скидка промо | Number input (%) | Необязательно | 0–100, 1 decimal | «Макс. скидка промо, %» | `—` |

#### Секция 3: Фильтр по категориям

| Поле | Тип | Обязательность | Label |
|------|-----|---------------|-------|
| Whitelist категорий | Multi-select dropdown с поиском | Необязательно | «Только для категорий» |
| Blacklist категорий | Multi-select dropdown с поиском | Необязательно | «Исключить категории» |

Взаимоисключающие: если задан whitelist, blacklist disabled (и наоборот). Визуальная подсказка: «Задайте whitelist ИЛИ blacklist — оба одновременно не поддерживаются.»

#### Секция 4: Расширенные параметры

| Поле | Тип | Обязательность | Label |
|------|-----|---------------|-------|
| Конфигурация оценки (JSON) | Code editor (Monaco-like, monospace textarea) | Необязательно | «Расширенная конфигурация (JSON)» |

Collapsible секция, по умолчанию свёрнута. Tooltip: «Продвинутые параметры для тонкой настройки логики оценки. Обычно не требуется.»

**Кнопки:**

| Кнопка | Тип | Расположение | Действие |
|--------|-----|-------------|----------|
| Сохранить | Primary | Sticky footer | POST (create) или PUT (edit). Toast: «Политика сохранена» |
| Сохранить и активировать | Primary (secondary стиль) | Sticky footer, справа от «Сохранить» | POST/PUT + POST activate. Только при create или status = DRAFT |
| Отмена | Ghost | Sticky footer, слева | Возврат к списку. Если есть несохранённые изменения — модал: «Есть несохранённые изменения. Уйти?» |

### 10. Массовые действия

Не применимо.

### 11. Пустые состояния

Не применимо (форма всегда имеет структуру).

### 12. Состояния загрузки

| Ситуация | Паттерн |
|----------|---------|
| Загрузка данных для edit | Skeleton формы (все поля — shimmer блоки) |
| Сохранение | Spinner на кнопке «Сохранить», все поля disabled |
| Загрузка категорий для dropdown | Shimmer в dropdown, «Загрузка категорий...» |

### 13. Состояния ошибок

| Ситуация | Паттерн |
|----------|---------|
| Ошибка валидации | Inline: красный border + текст под полем. Scroll to first error |
| Name uniqueness conflict | Inline под полем «Название»: «Политика с таким названием уже существует» |
| 409 Conflict (concurrent edit) | Toast (error): «Политика была изменена. Обновите и попробуйте снова.» |
| Server error | Toast (error): «Не удалось сохранить политику.» |
| Policy not found (edit, 404) | Full area: «Политика не найдена.» + [Вернуться к списку] |

### 14. Доступ и роли

| Действие | Минимальная роль |
|----------|-----------------|
| Открыть форму создания/редактирования | PRICING_MANAGER |

Пользователи с ролью ниже PRICING_MANAGER не видят кнопку «Создать» и ссылку «Редактировать». При прямом переходе по URL — redirect на список политик с toast: «Недостаточно прав.»

### 15. Привязка к API

| Компонент | Endpoint | Метод |
|-----------|----------|-------|
| Загрузка для edit | `GET /api/promo/policies/{policyId}` | GET |
| Создание | `POST /api/promo/policies` | POST |
| Обновление | `PUT /api/promo/policies/{policyId}` | PUT |
| Активация (после save) | `POST /api/promo/policies/{policyId}/activate` | POST |
| Список категорий (для dropdown) | `GET /api/categories` | GET |

**Request body (create/update):**

```json
{
  "name": "Стандартная промо-политика",
  "participationMode": "SEMI_AUTO",
  "minMarginPct": 10.0,
  "minStockDaysOfCover": 7,
  "maxPromoDiscountPct": 30.0,
  "autoParticipateCategories": [123, 456],
  "autoDeclineCategories": null,
  "evaluationConfig": {}
}
```

### 16. Реальное время / WebSocket

Не используется. Форма — синхронная операция. При concurrent edit — 409 Conflict (optimistic locking через `version`).

---

## Экран 5: Назначения политики

### 1. Маршрут и навигация

| Свойство | Значение |
|----------|----------|
| Route | Внутри Detail Panel экрана 3 (таб «Назначения»), или `/workspace/:id/promo/policies/:policyId` → таб |
| Breadcrumbs | Промо → Политики → {название} → Назначения |

### 2. Назначение экрана

Привязка промо-политики к конкретным подключениям, категориям или SKU. Определяет, какие товары оцениваются по данной политике.

### 3. Расположение в shell

Два варианта отображения:
- **Внутри Detail Panel** (таб «Назначения» при клике на политику в Экране 3): компактная мини-таблица.
- **Как отдельный route** при переходе «Открыть полностью»: полноценная таблица в Main Area.

### 4. KPI-полоска

Не используется.

### 5. Таблица / грид

| # | Колонка | Поле API | Тип | Ширина | Выравнивание | Формат |
|---|---------|----------|-----|--------|-------------|--------|
| 1 | Подключение | `connection_name` | Text | 180px | left | Название + marketplace badge |
| 2 | Тип области | `scope_type` | Badge | 120px | center | Подключение / Категория / SKU |
| 3 | Область | `scope_target_name` | Text | 200px | left | Название категории, SKU code, или «Всё подключение» |
| 4 | Действия | — | Action | 60px | center | Иконка удаления (trash) |

**Scope type badge:**

| scope_type | Русская метка | Цвет |
|------------|---------------|------|
| `CONNECTION` | Подключение | `--status-info` |
| `CATEGORY` | Категория | `--status-neutral` |
| `SKU` | SKU | `--status-neutral` |

### 6. Панель фильтров

Не используется (назначений обычно немного).

### 7. Сортировка и пагинация

- Сортировка: scope_type ASC (CONNECTION → CATEGORY → SKU).
- Пагинация: клиентская (все назначения загружаются разом — их редко > 50).

### 8. Панель деталей (Detail Panel)

Не используется на этом уровне (уже внутри Detail Panel).

### 9. Действия и кнопки

| Действие | Тип кнопки | Расположение | Роли | API |
|----------|-----------|-------------|------|-----|
| Добавить назначение | Primary (compact) | Над таблицей | PRICING_MANAGER+ | Открывает inline форму |
| Удалить | Icon button (trash), danger | Per-row | PRICING_MANAGER+ | `DELETE /api/promo/policies/{policyId}/assignments/{assignmentId}` |

**Форма добавления назначения (inline, раскрывается над таблицей):**

```
┌──────────────────────────────────────────────────────────────────┐
│  Подключение: [dropdown]   Тип: [CONNECTION|CATEGORY|SKU]       │
│  Область: [dropdown — зависит от типа]         [Добавить] [×]   │
└──────────────────────────────────────────────────────────────────┘
```

| Поле | Тип | Зависимость |
|------|-----|-------------|
| Подключение | Dropdown (marketplace_connection) | Обязательно |
| Тип области | Segmented control: Подключение / Категория / SKU | Обязательно |
| Область | Dropdown с поиском | Disabled при «Подключение». Категории при «Категория». SKU при «SKU» |

**Удаление:** Подтверждение inline: «Удалить назначение?» [Да] [Нет]. Без модала (операция нетяжёлая).

### 10. Массовые действия

Не предусмотрены.

### 11. Пустые состояния

| Ситуация | Сообщение |
|----------|-----------|
| Нет назначений | «Политика не назначена ни на одно подключение. Добавьте назначение, чтобы она начала работать.» + [Добавить назначение] |

### 12. Состояния загрузки

| Ситуация | Паттерн |
|----------|---------|
| Загрузка назначений | Skeleton: 3 строки |
| Добавление | Spinner на кнопке «Добавить» |
| Удаление | Строка fade-out анимация |

### 13. Состояния ошибок

| Ситуация | Паттерн |
|----------|---------|
| Duplicate assignment | Toast (error): «Такое назначение уже существует.» |
| Delete failed | Toast (error): «Не удалось удалить назначение.» |

### 14. Доступ и роли

| Действие | Минимальная роль |
|----------|-----------------|
| Просмотр назначений | VIEWER |
| Добавление / удаление | PRICING_MANAGER |

### 15. Привязка к API

| Компонент | Endpoint | Метод |
|-----------|----------|-------|
| Список | `GET /api/promo/policies/{policyId}/assignments` | GET |
| Добавить | `POST /api/promo/policies/{policyId}/assignments` | POST |
| Удалить | `DELETE /api/promo/policies/{policyId}/assignments/{assignmentId}` | DELETE |
| Подключения (dropdown) | `GET /api/connections` | GET |
| Категории (dropdown) | `GET /api/categories?connectionId=...` | GET |
| SKU (dropdown) | `GET /api/offers?connectionId=...&search=...` | GET |

**Request body (add):**

```json
{
  "marketplaceConnectionId": 42,
  "scopeType": "CATEGORY",
  "categoryId": 123,
  "marketplaceOfferId": null
}
```

### 16. Реальное время / WebSocket

Не используется.

---

## Экран 6: Оценки (Evaluations)

### 1. Маршрут и навигация

| Свойство | Значение |
|----------|----------|
| Route | `/workspace/:id/promo/evaluations` |
| Breadcrumbs | Промо → Оценки |
| Tab | «Оценки» |

### 2. Назначение экрана

Лог всех evaluation results по товарам в промо-акциях. Позволяет анализировать, почему конкретный товар был оценён как PROFITABLE / UNPROFITABLE, и отслеживать историю оценок.

### 3. Расположение в shell

| Зона | Содержимое |
|------|------------|
| Main Area | Фильтры + таблица оценок |
| Detail Panel | При клике — полный evaluation breakdown |
| Bottom Panel | Не используется |

### 4. KPI-полоска

4 карточки:

| Карточка | Значение |
|----------|----------|
| Всего оценок | Общее количество (filtered) |
| Прибыльных | count `PROFITABLE` |
| Пограничных | count `MARGINAL` |
| Убыточных | count `UNPROFITABLE` + `INSUFFICIENT_STOCK` + `INSUFFICIENT_DATA` |

### 5. Таблица / грид

AG Grid. Дефолтная сортировка: `evaluated_at DESC`.

| # | Колонка | Поле API | Тип | Ширина | Выравнивание | Формат |
|---|---------|----------|-----|--------|-------------|--------|
| 1 | Кампания | `campaign_name` | Text | 200px | left | Truncate + tooltip. Link → Экран 2 |
| 2 | Маркетплейс | `source_platform` | Badge | 80px | center | WB / Ozon badge |
| 3 | Товар | `product_name` | Text | 230px | left | Truncate + tooltip |
| 4 | SKU | `marketplace_sku` | Text/Code | 120px | left | Monospace |
| 5 | Промо-цена | `promo_price` | Number | 100px | right | Monospace, `1 290 ₽` |
| 6 | Обычная цена | `regular_price` | Number | 100px | right | Monospace, `1 590 ₽` |
| 7 | Скидка | `discount_pct` | Number | 80px | right | Monospace, `23,0%` |
| 8 | COGS | `cogs` | Number | 90px | right | Monospace, `850 ₽` или `—` |
| 9 | Маржа (промо) | `margin_at_promo_price` | Number | 100px | right | Monospace, `14,2%`, finance color |
| 10 | Маржа (обычная) | `margin_at_regular_price` | Number | 100px | right | Monospace, `22,5%` |
| 11 | Дельта маржи | `margin_delta_pct` | Number | 100px | right | `↓ 8,3 п.п.`, finance color |
| 12 | Остатки | `stock_available` | Number | 80px | right | Monospace |
| 13 | Дней покрытия | `stock_days_of_cover` | Number | 90px | right | Monospace, `18 д.` |
| 14 | Достаточно остатков | `stock_sufficient` | Boolean icon | 60px | center | ✓ / ✗ |
| 15 | Результат | `evaluation_result` | Badge | 130px | center | См. evaluation result badge |
| 16 | Причина пропуска | `skip_reason` | Text | 180px | left | Текст или `—` |
| 17 | Политика | `policy_name` | Text | 150px | left | Название promo_policy |
| 18 | Оценено | `evaluated_at` | Date | 130px | left | `28 мар, 14:32` |

Скрыты по умолчанию: COGS, Маржа (обычная), Причина пропуска, Достаточно остатков, Дней покрытия.

### 6. Панель фильтров

| Фильтр | Тип | Значения | По умолчанию |
|--------|-----|----------|-------------|
| Кампания | Dropdown с поиском | Все кампании (active + upcoming) | Все |
| Подключение | Multi-select dropdown | Все connection-ы | Все |
| Маркетплейс | Multi-select chips | WB, Ozon | Все |
| Результат оценки | Multi-select chips | Прибыльно, Пограничный, Убыточно, Мало остатков, Нет данных | Все |
| Период | Date range picker | evaluated_at range | Последние 7 дней |
| Поиск | Text input | По товару, SKU | — |

### 7. Сортировка и пагинация

- Sortable: Кампания, Товар, Промо-цена, Маржа (промо), Дельта маржи, Остатки, Результат, Оценено.
- Дефолтная сортировка: Оценено DESC.
- Пагинация: серверная, 50 / 100 / 200 строк.

### 8. Панель деталей (Detail Panel)

Открывается при клике. Содержит полный evaluation breakdown — тот же формат, что в Detail Panel Экрана 2, таб «Оценка».

Дополнительно: `signal_snapshot` — expandable JSON viewer для debug/advanced users.

### 9. Действия и кнопки

| Действие | Тип кнопки | Роли |
|----------|-----------|------|
| Экспорт | Secondary | ANALYST+ |
| Обновить | Ghost icon | Any role |
| Колонки | Ghost icon | Any role |

Нет write-действий на этом экране — это read-only журнал оценок.

### 10. Массовые действия

Не предусмотрены (read-only).

### 11. Пустые состояния

| Ситуация | Сообщение |
|----------|-----------|
| Нет оценок | «Оценки промо ещё не выполнялись. Убедитесь, что активна хотя бы одна промо-политика и sync загрузил акции.» |
| Фильтры ничего не нашли | «Нет оценок, соответствующих фильтрам.» + [Сбросить фильтры] |

### 12. Состояния загрузки

| Ситуация | Паттерн |
|----------|---------|
| Первоначальная загрузка | Skeleton: 4 KPI + таблица 10 строк |
| Filter / pagination | Progress bar 2px |

### 13. Состояния ошибок

| Ситуация | Паттерн |
|----------|---------|
| API error | Toast (error): «Не удалось загрузить оценки.» |

### 14. Доступ и роли

| Действие | Минимальная роль |
|----------|-----------------|
| Просмотр оценок | VIEWER |
| Экспорт | ANALYST |

### 15. Привязка к API

| Компонент | Endpoint | Метод | Параметры |
|-----------|----------|-------|-----------|
| KPI | `GET /api/promo/evaluations` | GET | `?groupBy=evaluationResult` (count aggregation) |
| Таблица | `GET /api/promo/evaluations` | GET | `?campaignId=...&marketplaceOfferId=...&evaluationResult=...&from=...&to=...&page=0&size=50&sort=evaluated_at,desc` |

### 16. Реальное время / WebSocket

| Событие | STOMP destination | Действие UI |
|---------|-------------------|-------------|
| Evaluation batch completed | `/topic/workspace.{id}.promo.evaluation` | Refetch таблицы. Toast (info): «Новые оценки доступны.» |

---

## Экран 7: Решения (Decisions)

### 1. Маршрут и навигация

| Свойство | Значение |
|----------|----------|
| Route | `/workspace/:id/promo/decisions` |
| Breadcrumbs | Промо → Решения |
| Tab | «Решения» |

### 2. Назначение экрана

Журнал решений об участии в промо-акциях. Каждая запись — decision с привязкой к evaluation, policy snapshot и explanation. Основной экран для аудита и объяснимости: «почему система решила участвовать / отказать?»

### 3. Расположение в shell

| Зона | Содержимое |
|------|------------|
| Main Area | Фильтры + таблица решений |
| Detail Panel | При клике — полное объяснение решения |
| Bottom Panel | Не используется |

### 4. KPI-полоска

3 карточки:

| Карточка | Значение |
|----------|----------|
| Участвовать | count `PARTICIPATE` |
| Отказать | count `DECLINE` |
| На проверку | count `PENDING_REVIEW` |

### 5. Таблица / грид

AG Grid. Дефолтная сортировка: `created_at DESC`.

| # | Колонка | Поле API | Тип | Ширина | Выравнивание | Формат |
|---|---------|----------|-----|--------|-------------|--------|
| 1 | Кампания | `campaign_name` | Text | 200px | left | Link → Экран 2 |
| 2 | Маркетплейс | `source_platform` | Badge | 80px | center | WB / Ozon badge |
| 3 | Товар | `product_name` | Text | 230px | left | Truncate + tooltip |
| 4 | SKU | `marketplace_sku` | Text/Code | 120px | left | Monospace |
| 5 | Решение | `decision_type` | Badge | 120px | center | См. decision type badge |
| 6 | Режим | `participation_mode` | Text | 120px | left | Русская метка |
| 7 | Целевая промо-цена | `target_promo_price` | Number | 110px | right | Monospace, `1 290 ₽` или `—` |
| 8 | Объяснение | `explanation_summary` | Text | 250px | left | Truncate (первые 80 символов) + tooltip. Полный текст — в Detail Panel |
| 9 | Принято | `decided_by_name` | Text | 130px | left | Имя или «Автоматически» |
| 10 | Политика | `policy_name` + `policy_version` | Text | 150px | left | «{name} v{version}» |
| 11 | Дата | `created_at` | Date | 130px | left | `28 мар, 14:32` |

### 6. Панель фильтров

| Фильтр | Тип | Значения | По умолчанию |
|--------|-----|----------|-------------|
| Кампания | Dropdown с поиском | Все кампании | Все |
| Подключение | Multi-select dropdown | Все connection-ы | Все |
| Решение | Multi-select chips | Участвовать, Отказать, На проверку | Все |
| Принято | Toggle: Все / Автоматически / Вручную | — | Все |
| Период | Date range picker | created_at range | Последние 7 дней |
| Поиск | Text input | По товару, SKU | — |

### 7. Сортировка и пагинация

- Sortable: Кампания, Товар, Решение, Режим, Дата.
- Дефолтная сортировка: Дата DESC.
- Пагинация: серверная, 50 / 100 / 200 строк.

### 8. Панель деталей (Detail Panel)

Открывается при клике на строку. **Основной экран объяснимости (explainability).**

**Header:**
- Товар (bold)
- Кампания (secondary)
- Decision type badge

**Содержимое:**

1. **Блок объяснения** — structured explanation layout (см. [глоссарий](#формат-объяснений-decision-explanation)). Полный текст `explanation_summary`.

2. **Policy snapshot** — key-value:

| Метка | Значение |
|-------|----------|
| Политика | name |
| Версия | `v{version}` |
| Режим | participation_mode label |
| Мин. маржа | `10,0%` |
| Мин. остатков | `7 д.` |
| Макс. скидка | `30,0%` |
| Whitelist | Список или «Не задан» |
| Blacklist | Список или «Не задан» |

3. **Evaluation link** — кнопка «Показать оценку →» → скролл к evaluation detail (или open в новом табе).

4. **Action status** — если promo_action связан: тип + текущий статус + timeline.

### 9. Действия и кнопки

| Действие | Тип кнопки | Роли |
|----------|-----------|------|
| Экспорт | Secondary | ANALYST+ |
| Обновить | Ghost icon | Any role |
| Колонки | Ghost icon | Any role |

Read-only экран. Все write-действия — через Экран 2 (Campaign Detail).

### 10. Массовые действия

Не предусмотрены.

### 11. Пустые состояния

| Ситуация | Сообщение |
|----------|-----------|
| Нет решений | «Решения по промо ещё не принимались. Настройте промо-политику и дождитесь синхронизации акций.» |
| Фильтры ничего не нашли | «Нет решений, соответствующих фильтрам.» + [Сбросить фильтры] |

### 12. Состояния загрузки

| Ситуация | Паттерн |
|----------|---------|
| Первоначальная загрузка | Skeleton: 3 KPI + таблица 10 строк |
| Filter / pagination | Progress bar 2px |

### 13. Состояния ошибок

| Ситуация | Паттерн |
|----------|---------|
| API error | Toast (error): «Не удалось загрузить решения.» |

### 14. Доступ и роли

| Действие | Минимальная роль |
|----------|-----------------|
| Просмотр решений | VIEWER |
| Экспорт | ANALYST |

### 15. Привязка к API

| Компонент | Endpoint | Метод | Параметры |
|-----------|----------|-------|-----------|
| KPI | `GET /api/promo/decisions` | GET | `?groupBy=decisionType` |
| Таблица | `GET /api/promo/decisions` | GET | `?campaignId=...&decisionType=...&from=...&to=...&page=0&size=50&sort=created_at,desc` |

### 16. Реальное время / WebSocket

| Событие | STOMP destination | Действие UI |
|---------|-------------------|-------------|
| New decisions batch | `/topic/workspace.{id}.promo.decision` | Refetch таблицы и KPI |

---

## Экран 8: Ручные промо-действия

### 1. Маршрут и навигация

Ручные действия не имеют отдельного route. Они встроены в Экран 2 (Campaign Detail) как кнопки в колонке «Действия» каждой строки таблицы товаров, а также в Detail Panel.

### 2. Назначение экрана

Позволяет оператору вручную управлять участием товаров в промо-акциях: принудительно добавить, отклонить, одобрить/отклонить pending-действия, отменить запланированные действия.

### 3. Расположение в shell

Действия отображаются в двух местах:
- **Per-row buttons** в колонке «Действия» таблицы Экрана 2.
- **Action buttons** в Detail Panel товара (таб «Действие»).

### 4. KPI-полоска

Не применимо (встроено в Экран 2).

### 5. Таблица / грид

Не применимо. Действия — кнопки внутри грида Экрана 2.

**Матрица доступности кнопок в зависимости от состояния:**

| Текущий статус участия | Текущий action status | Доступные кнопки | Недоступные кнопки (disabled + tooltip) |
|------------------------|----------------------|------------------|-----------------------------------------|
| `ELIGIBLE`, нет action | — | [Участвовать] [Отклонить] | — |
| `ELIGIBLE`, action `PENDING_APPROVAL` | PENDING_APPROVAL | [Одобрить] [Отклонить] [Отменить] | [Участвовать] — «Действие уже создано» |
| `ELIGIBLE`, action `APPROVED` | APPROVED | [Отменить] | [Участвовать] [Одобрить] |
| `ELIGIBLE`, action `EXECUTING` | EXECUTING | — (все disabled) | «Действие выполняется» |
| `PARTICIPATING` | any terminal | [Удалить из акции] | [Участвовать] — «Уже участвует» |
| `DECLINED` / `AUTO_DECLINED` | — | [Участвовать] | [Отклонить] — «Уже отклонён» |
| `BANNED` | — | — (все disabled) | «Заблокирован маркетплейсом» |
| Campaign `FROZEN` | any | — (все disabled) | «Кампания заморожена» |
| Campaign `ENDED` | any | — (все disabled) | «Кампания завершена» |

### 6. Панель фильтров

Не применимо.

### 7. Сортировка и пагинация

Не применимо.

### 8. Панель деталей (Detail Panel)

В Detail Panel (таб «Действие») кнопки дублируются в более крупном формате с дополнительным контекстом:

```
┌─ Действие ──────────────────────────────────────────┐
│                                                      │
│  Статус: [PENDING_APPROVAL badge]                    │
│  Тип: ACTIVATE                                       │
│  Промо-цена: 1 290 ₽                                │
│  Создано: 28 мар, 14:32                              │
│                                                      │
│  [Одобрить (Primary)]  [Отклонить (Danger)]          │
│  [Отменить (Ghost)]                                  │
│                                                      │
└──────────────────────────────────────────────────────┘
```

### 9. Действия и кнопки

#### Действие: Участвовать (Force Participate)

| Свойство | Значение |
|----------|----------|
| Кнопка | Primary (compact), icon `check-circle` + текст «Участвовать» |
| Роли | PRICING_MANAGER, ADMIN, OWNER |
| Условие | participation_status = ELIGIBLE или DECLINED/AUTO_DECLINED, campaign не FROZEN/ENDED |
| UI | Click → popup form (не модал, a dropdown panel рядом с кнопкой) |
| Форма | Целевая промо-цена (Number input, optional, prefilled с `required_price`). Причина (Text input, optional) |
| Подтверждение | Кнопка «Подтвердить» в popup |
| API | `POST /api/promo/products/{promoProductId}/participate` Body: `{ targetPromoPrice?, reason? }` |
| Feedback | Toast (success): «Участие подтверждено. Действие создано.» Строка обновляется: action_status → APPROVED |

#### Действие: Отклонить (Force Skip / Decline)

| Свойство | Значение |
|----------|----------|
| Кнопка | Ghost (compact), icon `x-circle` + текст «Отклонить» |
| Роли | PRICING_MANAGER, ADMIN, OWNER |
| Условие | participation_status = ELIGIBLE, campaign не FROZEN/ENDED |
| UI | Click → popup form |
| Форма | Причина (Text input, optional но рекомендуемо). Placeholder: «Укажите причину отклонения» |
| API | `POST /api/promo/products/{promoProductId}/decline` Body: `{ reason? }` |
| Feedback | Toast (success): «Товар отклонён.» Строка обновляется: participation_status → DECLINED |

#### Действие: Удалить из акции (Deactivate participating product)

| Свойство | Значение |
|----------|----------|
| Кнопка | Danger (compact), icon `log-out` + текст «Удалить из акции» |
| Роли | PRICING_MANAGER, ADMIN, OWNER |
| Условие | participation_status = PARTICIPATING, campaign не FROZEN/ENDED |
| UI | Click → popup form |
| Форма | Причина (Text input, optional но рекомендуемо). Placeholder: «Укажите причину выхода из акции» |
| API | `POST /api/promo/products/{promoProductId}/deactivate` Body: `{ reason? }` |
| Feedback | Toast (success): «Товар удалён из акции. Действие создано.» Строка: action_status → APPROVED (DEACTIVATE) |

#### Действие: Одобрить (Approve pending action)

| Свойство | Значение |
|----------|----------|
| Кнопка | Primary (compact), icon `check` + текст «Одобрить» |
| Роли | PRICING_MANAGER, ADMIN, OWNER |
| Условие | promo_action.status = PENDING_APPROVAL |
| UI | Click → immediate (без формы). Confirmation если campaign.freeze_at < 1 час: «Заморозка через {N} мин. Одобрить?» |
| API | `POST /api/promo/actions/{actionId}/approve` |
| Feedback | Toast (success): «Действие одобрено.» Строка: action_status → APPROVED |

#### Действие: Отклонить действие (Reject pending action)

| Свойство | Значение |
|----------|----------|
| Кнопка | Danger (compact), icon `x` + текст «Отклонить» |
| Роли | PRICING_MANAGER, ADMIN, OWNER |
| Условие | promo_action.status = PENDING_APPROVAL |
| UI | Click → popup form |
| Форма | Причина (Text input, обязательно) |
| API | `POST /api/promo/actions/{actionId}/reject` Body: `{ reason }` |
| Feedback | Toast (success): «Действие отклонено.» |

#### Действие: Отменить (Cancel action)

| Свойство | Значение |
|----------|----------|
| Кнопка | Ghost (compact), icon `ban` + текст «Отменить» |
| Роли | PRICING_MANAGER, ADMIN, OWNER |
| Условие | promo_action.status = PENDING_APPROVAL или APPROVED |
| UI | Click → popup form |
| Форма | Причина отмены (Text input, обязательно) |
| API | `POST /api/promo/actions/{actionId}/cancel` Body: `{ cancelReason }` |
| Feedback | Toast (success): «Действие отменено.» |

### 10. Массовые действия

Массовые approve / reject описаны в Экране 2, §10.

### 11. Пустые состояния

Не применимо (кнопки скрываются или disabled по условию).

### 12. Состояния загрузки

| Ситуация | Паттерн |
|----------|---------|
| Action в процессе | Spinner на конкретной кнопке, остальные кнопки строки — disabled |
| Bulk approve | Progress toast: «Одобрение действий... (3/12)» |

### 13. Состояния ошибок

| Ситуация | Паттерн |
|----------|---------|
| 409 Conflict (status changed) | Toast (error): «Статус изменился. Обновите страницу.» Auto-refetch строки |
| 403 Forbidden | Toast (error): «Недостаточно прав для этого действия.» |
| Campaign FROZEN (race) | Toast (warning): «Кампания заморожена, действие невозможно.» Disable all buttons |
| Network error | Toast (error): «Ошибка сети. Попробуйте ещё раз.» |
| Action already exists (duplicate) | Toast (warning): «Действие уже существует для этого товара.» |

### 14. Доступ и роли

| Действие | Минимальная роль |
|----------|-----------------|
| Force participate | PRICING_MANAGER |
| Force skip / decline | PRICING_MANAGER |
| Approve | PRICING_MANAGER |
| Reject | PRICING_MANAGER |
| Cancel | PRICING_MANAGER |

Пользователи с ролью ниже PRICING_MANAGER видят колонку «Действия» без кнопок. В Detail Panel — текст: «Для управления участием необходима роль Менеджер ценообразования или выше.»

### 15. Привязка к API

| Действие | Endpoint | Метод | Body |
|----------|----------|-------|------|
| Participate | `POST /api/promo/products/{promoProductId}/participate` | POST | `{ targetPromoPrice?, reason? }` |
| Decline | `POST /api/promo/products/{promoProductId}/decline` | POST | `{ reason? }` |
| Deactivate | `POST /api/promo/products/{promoProductId}/deactivate` | POST | `{ reason? }` |
| Approve | `POST /api/promo/actions/{actionId}/approve` | POST | — |
| Reject | `POST /api/promo/actions/{actionId}/reject` | POST | `{ reason }` |
| Cancel | `POST /api/promo/actions/{actionId}/cancel` | POST | `{ cancelReason }` |
| Actions list | `GET /api/promo/actions` | GET | `?campaignId=...&status=...&actionType=...` |

### 16. Реальное время / WebSocket

| Событие | STOMP destination | Действие UI |
|---------|-------------------|-------------|
| promo_action status changed | `/topic/workspace.{id}.promo.action` | Обновить кнопки в строке: пересчитать доступность, обновить badge статуса |
| Campaign frozen | `/topic/workspace.{id}.promo.campaign` | Disable все action buttons, показать banner |

---

## Пользовательские сценарии

### Сценарий 1: Обзор предстоящей кампании

**Роль:** PRICING_MANAGER
**Цель:** Увидеть новую промо-акцию Ozon, оценить товары, одобрить участие.

1. Пользователь заходит в модуль «Промо» → вкладка «Кампании».
2. В KPI-полоске видит «Предстоящих: 3». Фильтр по умолчанию: статус = Предстоящая + Активна.
3. Находит кампанию «Летняя распродажа 2026» (статус: Предстоящая, Ozon badge).
4. Кликает на строку → Detail Panel показывает сводку: 45 доступных товаров, 0 участвует, заморозка через 2 дня.
5. Кликает «Все товары →» → открывается Экран 2 (Campaign Detail) в новом табе.
6. Видит таблицу: 45 товаров. 28 оценены как PROFITABLE, 7 как MARGINAL, 10 как UNPROFITABLE.
7. Фильтрует по «На проверку» (decision = PENDING_REVIEW) — видит 7 пограничных товаров.
8. Кликает на первый → Detail Panel: маржа 4,2% при пороге 10%. Объяснение: «Маржа ниже минимальной (4,2% < 10,0%). Рекомендация: проверить вручную.»
9. Решает участвовать → жмёт [Участвовать], указывает промо-цену, подтверждает.
10. Toast: «Участие подтверждено.» Статус действия → APPROVED.
11. Выделяет 5 оставшихся PENDING_APPROVAL товаров → Bottom Panel: [Одобрить все (5)].
12. Жмёт «Одобрить все» → Toast: «5 действий одобрено.»

### Сценарий 2: Настройка auto-accept политики

**Роль:** PRICING_MANAGER
**Цель:** Создать политику, которая автоматически участвует в прибыльных акциях для категории «Одежда».

1. Переход: Промо → Политики → [Создать политику].
2. Открывается форма (Экран 4):
   - Название: «Авто-участие: Одежда»
   - Режим: выбирает «Полный авто» (карточка с описанием).
   - Мин. маржа: 8,0%
   - Мин. остатков (дней): 10
   - Макс. скидка: 40,0%
   - Whitelist категорий: выбирает «Одежда» из dropdown.
3. Жмёт «Сохранить и активировать».
4. Toast: «Политика сохранена и активирована.»
5. Redirect на список политик → политика в статусе «Активна».
6. Кликает на неё → Detail Panel → таб «Назначения».
7. Видит: «Нет назначений.» Жмёт «Добавить назначение».
8. Выбирает: Подключение = «Ozon — Основной», Тип = «Подключение» (применить ко всем товарам подключения).
9. Жмёт «Добавить». Назначение появляется в списке.
10. При следующем PROMO_SYNC товары из категории «Одежда» с маржой ≥ 8% автоматически получат promo_action APPROVED.

### Сценарий 3: Принудительное участие в промо

**Роль:** PRICING_MANAGER
**Цель:** Товар отклонён автоматически (маржа низкая), но менеджер хочет участвовать для продвижения новинки.

1. Промо → Кампании → «Весенний фестиваль WB» → Campaign Detail.
2. Фильтрует: Статус участия = «Авто-отклонён».
3. Находит новинку «Куртка зимняя арт. WJ-2026».
4. Detail Panel → таб «Оценка»: маржа −2,3% (убыточно). Причина auto-decline: UNPROFITABLE.
5. Detail Panel → таб «Решение»: Решение = Отказать. Объяснение: «Маржа при промо-цене отрицательная (−2,3%). Автоматический отказ по политике «Стандартная промо-политика» v2.»
6. Менеджер понимает, что товар новый, нужна видимость. Жмёт [Участвовать].
7. Popup: промо-цена предзаполнена (1 290 ₽). Причина: «Продвижение новинки, маржа допустима краткосрочно.»
8. Жмёт «Подтвердить».
9. Toast: «Участие подтверждено.» Статус: participation_status → PARTICIPATING (после execution), action_status → APPROVED → EXECUTING → SUCCEEDED.
10. В журнале решений (Экран 7) появляется запись: decision_type = PARTICIPATE, decided_by = {менеджер}, explanation = «Ручное участие: Продвижение новинки, маржа допустима краткосрочно.»

---

## Граничные случаи (Edge cases)

### EC-1: Нет синхронизированных кампаний

**Ситуация:** PROMO_SYNC ещё ни разу не завершался для данного workspace (новое подключение или sync disabled).

**Поведение:**
- Экран 1 (Кампании): empty state с message: «Промо-акции ещё не загружены. Убедитесь, что подключение настроено и синхронизация промо включена.» + [Перейти к подключениям].
- KPI-полоска: все значения = 0.
- Status Bar: отсутствие `PROMO_SYNC` → жёлтая точка: «Промо: данные не загружены».
- Экран 6 (Оценки), Экран 7 (Решения): empty state с соответствующим сообщением.

### EC-2: Кампания заморожена во время работы оператора

**Ситуация:** Оператор просматривает Campaign Detail, одобряет товары. Пока он работает, `freeze_at` deadline проходит — кампания переходит в FROZEN.

**Поведение:**
1. WebSocket event `campaign.status.changed` → статус FROZEN.
2. Header badge обновляется: [FROZEN badge].
3. Non-dismissible banner над таблицей: «⚠ Кампания заморожена. Изменение участия невозможно.» (жёлтый фон).
4. Все кнопки действий → disabled. Tooltip: «Кампания заморожена».
5. Если оператор уже нажал «Одобрить» и запрос в полёте:
   - Если action ещё PENDING_APPROVAL → backend переведёт в EXPIRED (scheduled job).
   - Если backend успел перевести в APPROVED до freeze → APPROVED, но execution может не успеть до freeze → EXPIRED.
   - UI: Toast (warning): «Действие создано, но кампания заморожена. Действие может быть отклонено.»
6. promo_action в PENDING_APPROVAL при FROZEN → scheduled job переводит в EXPIRED → UI обновляется.

### EC-3: Concurrent force-participate

**Ситуация:** Два оператора одновременно пытаются force-participate для одного товара.

**Поведение:**
1. Первый запрос: `POST /api/promo/products/{id}/participate` → promo_action создаётся (APPROVED).
2. Второй запрос: backend обнаруживает existing active action (DB unique index `idx_promo_action_active_live`) → HTTP 409 Conflict.
3. UI второго оператора: Toast (error): «Действие уже существует для этого товара. Страница обновлена.»
4. Auto-refetch строки → отображается актуальный статус.

### EC-4: Кампания завершилась (ENDED)

**Ситуация:** Оператор открывает Campaign Detail для завершённой кампании.

**Поведение:**
- Header: [ENDED badge].
- Non-dismissible banner: «Кампания завершена. Данные доступны для просмотра.» (neutral, `--bg-secondary`).
- Все action buttons → hidden (не disabled, а полностью скрыты — нет смысла показывать).
- Данные полностью доступны для просмотра и экспорта.
- Detail Panel evaluation/decision — read-only, как обычно.

### EC-5: Товар заблокирован маркетплейсом (BANNED)

**Ситуация:** Marketplace заблокировал участие товара (Ozon banned_products).

**Поведение:**
- participation_status = BANNED (красный badge).
- Все action buttons → disabled. Tooltip: «Маркетплейс заблокировал участие этого товара.»
- Detail Panel: статус «Заблокирован маркетплейсом. Причина блокировки определяется маркетплейсом и не может быть изменена через Datapulse.»

### EC-6: COGS не задан для товара

**Ситуация:** Evaluation pipeline не может рассчитать маржу, потому что `cost_profile` отсутствует.

**Поведение:**
- evaluation_result = INSUFFICIENT_DATA.
- В таблице: колонка «COGS» = `—`, маржа = `—`, колонка «Результат» = [INSUFFICIENT_DATA badge].
- Detail Panel → evaluation: «Себестоимость (COGS) не задана. Невозможно рассчитать маржу. Задайте себестоимость для SKU в разделе «Себестоимость».» + [Перейти к себестоимости].
- Decision: PENDING_REVIEW (при SEMI_AUTO/FULL_AUTO) или рекомендация «Нет данных» (при RECOMMENDATION).

### EC-7: Массовое одобрение смешанной выборки

**Ситуация:** Оператор выбрал 12 товаров, из которых 8 в PENDING_APPROVAL и 4 уже в другом статусе.

**Поведение:**
- Bottom Panel: «12 товаров выбрано. [Одобрить (8 из 12)] [Отклонить (8 из 12)] [Экспорт]»
- Count рядом с кнопкой показывает, сколько реально будет обработано.
- При клике: обрабатываются только eligible (8 шт.). Остальные 4 — пропущены.
- Toast: «8 действий одобрено. 4 пропущено (не ожидают одобрения).»

### EC-8: Evaluation ещё не выполнялся для кампании

**Ситуация:** PROMO_SYNC загрузил новую кампанию, но evaluation pipeline ещё не запустился (нет active promo_policy или evaluation запланирован на позже).

**Поведение:**
- Campaign Detail (Экран 2): товары отображаются с participation_status из sync (ELIGIBLE / PARTICIPATING).
- Колонки «Оценка», «Решение», «Статус действия» — пустые.
- Banner над таблицей (info): «Оценка товаров ещё не выполнялась. Она запустится автоматически после настройки промо-политики.»
- Detail Panel → таб «Оценка»: «Оценка не выполнялась.» Таб «Решение»: «Решение не принималось.»

---

## Связанные документы

- [Frontend Design Direction](frontend-design-direction.md) — дизайн-система, компоненты, паттерны
- [Promotions module](../modules/promotions.md) — lifecycle, data model, REST API, evaluation pipeline
- [ETL Pipeline](../modules/etl-pipeline.md) — canonical_promo_campaign, canonical_promo_product, PROMO_SYNC
- [Tenancy & IAM](../modules/tenancy-iam.md) — роли, permissions, WorkspaceContext
- [Pricing module](../modules/pricing.md) — аналогичные паттерны для pricing policies, promo guard
