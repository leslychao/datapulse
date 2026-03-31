# Frontend Specification: Pricing Module

**Модуль:** Pricing
**Фазы:** C (Pricing), E (Impact Preview)
**Activity Bar icon:** `lucide:calculator` (или `lucide:percent`)
**Breadcrumb root:** `Ценообразование`
**Minimum read role:** Any workspace role (VIEWER+)
**Minimum write role:** PRICING_MANAGER, ADMIN, OWNER

---

## Навигация внутри модуля

При клике на иконку Pricing в Activity Bar открывается вкладка по умолчанию — **Ценовые политики**.

Вторичная навигация — через табы в Main Area:

| Таб (label) | Route | Описание |
|-------------|-------|----------|
| Ценовые политики | `/workspace/:id/pricing/policies` | Список политик |
| Прогоны | `/workspace/:id/pricing/runs` | Pricing runs |
| Решения | `/workspace/:id/pricing/decisions` | Decisions log |
| Блокировки | `/workspace/:id/pricing/locks` | Manual price locks |

Каждый таб может быть открыт как отдельная вкладка в Main Area (like Cursor editor tabs). Переход к detail-экранам (policy edit, run detail, decision detail) открывает новый таб с breadcrumb-навигацией обратно к списку.

---

## 1. Ценовые политики — список

### 1.1 Route / URL

```
/workspace/:workspaceId/pricing/policies
```

### 1.2 Breadcrumbs

```
Ценообразование > Ценовые политики
```

### 1.3 Minimum role

- **Чтение:** Any role (VIEWER+)
- **Запись (создание, активация, пауза, архивация):** PRICING_MANAGER, ADMIN, OWNER

### 1.4 Phase

C — Pricing

### 1.5 Layout zone

**Main Area** — полноширинная таблица с toolbar.

### 1.6 KPI strip

Нет. Для списка политик KPI-карточки не нужны — это конфигурационный экран, не аналитический.

### 1.7 Data grid

AG Grid с серверной пагинацией.

**Колонки:**

| # | Column ID | Заголовок (RU) | Тип | Ширина | Align | Sortable | Описание |
|---|-----------|---------------|-----|--------|-------|----------|----------|
| 1 | `name` | Название | Text | 250px, flex | Left | ✓ | Имя политики. Кликабельное — открывает detail/edit |
| 2 | `strategy_type` | Стратегия | Badge | 160px | Left | ✓ | `TARGET_MARGIN` → «Целевая маржа», `PRICE_CORRIDOR` → «Ценовой коридор» |
| 3 | `execution_mode` | Режим | Badge | 150px | Left | ✓ | См. §Execution mode badges |
| 4 | `status` | Статус | Status badge | 120px | Left | ✓ | См. §Policy status badges |
| 5 | `priority` | Приоритет | Number (mono) | 100px | Right | ✓ | Числовое значение, выше = важнее |
| 6 | `version` | Версия | Number (mono) | 80px | Right | ✓ | `v{N}` формат |
| 7 | `assignments_count` | Назначения | Number (mono) | 110px | Right | — | Количество assignments (computed, не хранится в policy) |
| 8 | `created_at` | Создана | Timestamp | 140px | Left | ✓ | Relative: «12 мин назад», «вчера» |
| 9 | `updated_at` | Обновлена | Timestamp | 140px | Left | ✓ | Relative format |
| 10 | `actions` | — | Action icons | 120px | Center | — | Контекстные действия (см. §Actions) |

**Default sort:** `created_at DESC`
**Rows per page:** 50 / 100 (selector в toolbar). Для policies 50 — достаточный default.
**Frozen columns:** `name` (primary identifier)

#### Policy status badges

| Status | Label (RU) | Цвет | Dot |
|--------|-----------|------|-----|
| `DRAFT` | Черновик | `--status-neutral` (gray) | Gray |
| `ACTIVE` | Активна | `--status-success` (green) | Green |
| `PAUSED` | На паузе | `--status-warning` (yellow) | Yellow |
| `ARCHIVED` | В архиве | `--status-neutral` (gray) | Gray |

#### Execution mode badges

| Mode | Label (RU) | Цвет |
|------|-----------|------|
| `RECOMMENDATION` | Рекомендация | `--status-info` (blue) |
| `SEMI_AUTO` | Полуавтомат | `--status-warning` (yellow) |
| `FULL_AUTO` | Автомат | `--status-success` (green) |
| `SIMULATED` | Симуляция | `--status-neutral` (gray) |

### 1.8 Filter bar

| Filter | Тип | Значения | Default |
|--------|-----|----------|---------|
| Статус | Multi-select pills | Черновик, Активна, На паузе, В архиве | Все кроме «В архиве» |
| Стратегия | Single-select dropdown | Целевая маржа, Ценовой коридор | Все |
| Режим | Multi-select pills | Рекомендация, Полуавтомат, Автомат, Симуляция | Все |

По умолчанию архивные политики скрыты. Пользователь может включить фильтр «В архиве» явно.

### 1.9 Actions / buttons

**Toolbar:**

| Кнопка | Тип | Label (RU) | Иконка | Условие |
|--------|-----|-----------|--------|---------|
| Create | Primary | Создать политику | `lucide:plus` | PRICING_MANAGER+ |

**Per-row actions (icon buttons в колонке `actions`):**

| Action | Иконка | Label (tooltip, RU) | Условие |
|--------|--------|---------------------|---------|
| Edit | `lucide:pencil` | Редактировать | PRICING_MANAGER+; status ≠ ARCHIVED |
| Activate | `lucide:play` | Активировать | PRICING_MANAGER+; status = DRAFT or PAUSED |
| Pause | `lucide:pause` | Приостановить | PRICING_MANAGER+; status = ACTIVE |
| Archive | `lucide:archive` | Архивировать | PRICING_MANAGER+; status ≠ ARCHIVED |

**Confirmation modals:**

- **Activate:** простой confirmation dialog: «Активировать политику "{name}"? Она начнёт влиять на ценообразование при следующем прогоне.» → [Отмена] [Активировать]
- **Pause:** простой confirmation: «Приостановить политику "{name}"? Ценообразование по этой политике будет прекращено до реактивации.» → [Отмена] [Приостановить]
- **Archive:** danger confirmation: «Архивировать политику "{name}"? Архивированную политику нельзя будет использовать для ценообразования.» → [Отмена] [Архивировать (danger)]

### 1.10 Detail panel

Клик по строке → Detail Panel (right) с основной информацией о политике.

**Tabs в panel:**

| Tab | Label (RU) | Содержание |
|-----|-----------|------------|
| Обзор | Обзор | Key-value pairs: все поля policy + strategy_params + guard_config |
| Назначения | Назначения | Мини-таблица assignments (см. §3 Policy Assignments) |
| История | История | Хронология версий: version, дата, кто изменил, diff основных полей |

**Содержание таба «Обзор»:**

```
Название:           Маржа 25% WB
Стратегия:          Целевая маржа (TARGET_MARGIN)
Режим исполнения:   Полуавтомат
Статус:             ● Активна
Приоритет:          10
Версия:             v3

── Параметры стратегии ──
Целевая маржа:      25,0%
Источник комиссии:  Авто с ручным fallback
Ручная комиссия:    —
Период анализа:     30 дней
Мин. транзакций:    5
Источник логистики: Авто
Ручная логистика:   —
Учёт возвратов:     ✓ Да
Учёт рекламы:       ✗ Нет
Шаг округления:     10 ₽
Направление:        FLOOR (вниз)

── Ограничения ──
Мин. маржа:         15,0%
Макс. изменение:    10,0%
Мин. цена:          —
Макс. цена:         —

── Guards ──
Маржа:              ✓ Вкл
Частота:            ✓ Вкл (24 ч)
Волатильность:      ✓ Вкл (3 разворота / 7 дней)
Промо:              ✓ Вкл
Дефицит:            ✓ Вкл
Устаревшие данные:  24 ч (всегда вкл)

── Таймауты ──
Ожидание одобрения: 72 ч
```

Числовые значения — `font-family: JetBrains Mono`. Toggle-значения — иконки ✓/✗ c цветом green/gray.

### 1.11 Forms / inputs

На этом экране формы нет — форма создания/редактирования описана в §2.

### 1.12 API endpoints

| Action | Method | Endpoint | Request | Response |
|--------|--------|----------|---------|----------|
| Load list | GET | `/api/pricing/policies?status={}&strategyType={}&page={}&size={}&sort={}` | Query params | `Page<PolicySummaryResponse>` |
| Activate | POST | `/api/pricing/policies/{policyId}/activate` | — | `200` |
| Pause | POST | `/api/pricing/policies/{policyId}/pause` | — | `200` |
| Archive | POST | `/api/pricing/policies/{policyId}/archive` | — | `200` |

**Headers:** `X-Workspace-Id: {workspaceId}`, `Authorization: Bearer {token}`

### 1.13 Empty / loading / error states

| Состояние | Отображение |
|-----------|-------------|
| Загрузка (initial) | Skeleton: 6 строк shimmer в сетке, shimmer toolbar |
| Пустой список | Centered: «Нет ценовых политик. Создайте первую политику для автоматического управления ценами.» + [Создать политику] (primary button). Показывается только если нет вообще никаких политик (включая архивные) |
| Нет результатов (фильтры) | «Нет политик, соответствующих фильтрам.» + [Сбросить фильтры] |
| Ошибка загрузки | Toast (error): «Не удалось загрузить политики. Попробуйте обновить страницу.» + [Повторить] |

### 1.14 User flow scenarios

**Сценарий 1: Pricing manager создаёт первую политику**

1. Открывает «Ценообразование» → видит пустой экран с приглашением создать политику
2. Нажимает «Создать политику» → переход на форму (§2)
3. Заполняет форму → сохраняет → политика появляется в списке со статусом «Черновик»
4. Нажимает «Активировать» → confirmation → статус меняется на «Активна»
5. При следующем pricing run политика начнёт применяться

**Сценарий 2: Приостановка политики при подозрительных результатах**

1. Pricing manager замечает нехарактерные решения в «Решения»
2. Переходит в «Ценовые политики» → находит подозрительную политику
3. Нажимает ⏸ (Pause) → confirmation → статус «На паузе»
4. Открывает Detail Panel → проверяет параметры → нажимает ✏ Edit → корректирует
5. После правки — «Активировать»

**Сценарий 3: Viewer просматривает политики**

1. Viewer открывает «Ценовые политики»
2. Видит таблицу, может сортировать и фильтровать
3. Кликает по строке → Detail Panel показывает конфигурацию
4. Кнопки создания/активации/паузы/архивации **не отображаются** (insufficient role)

### 1.15 Edge cases

| Case | Поведение |
|------|----------|
| Concurrent edit: другой пользователь изменил policy | При Activate/Pause/Archive → 409 Conflict → toast: «Политика была изменена другим пользователем. Обновите страницу.» + [Обновить] |
| Активация policy без assignments | Разрешено — policy активна, но не применяется ни к одному товару. Info toast: «Политика активирована, но не назначена ни на один товар. Добавьте назначения.» |
| Архивация active policy | Политика сначала переводится в PAUSED, затем в ARCHIVED (два шага на backend). UI показывает один confirmation dialog |
| > 100 policies в workspace | Пагинация (серверная). Не ожидается — типичный workspace имеет 3–15 политик |

### 1.16 Accessibility

- Все action buttons имеют `aria-label` с полным действием: «Активировать политику Маржа 25% WB»
- Status badges имеют screen reader text: не только цвет, но и текст «Статус: Активна»
- Таблица навигируется клавиатурой (↑↓), Enter → Detail Panel
- Focus trap в confirmation modals

---

## 2. Создание / Редактирование ценовой политики

### 2.1 Route / URL

```
/workspace/:workspaceId/pricing/policies/new          # создание
/workspace/:workspaceId/pricing/policies/:policyId/edit  # редактирование
```

### 2.2 Breadcrumbs

Создание:
```
Ценообразование > Ценовые политики > Новая политика
```

Редактирование:
```
Ценообразование > Ценовые политики > {policyName} > Редактирование
```

### 2.3 Minimum role

PRICING_MANAGER, ADMIN, OWNER. Если у пользователя нет права — redirect на список с toast: «У вас нет прав для управления ценовыми политиками.»

### 2.4 Phase

C — Pricing (форма), E — Impact Preview (кнопка Preview)

### 2.5 Layout zone

**Main Area** — полноширинная форма, разбитая на секции. Без Detail Panel — форма занимает всю ширину.

### 2.6 KPI strip

Нет.

### 2.7 Data grid

Нет (это форма, не грид).

### 2.8 Filter bar

Нет.

### 2.9 Actions / buttons

**Footer (sticky, 48px, всегда видна внизу Main Area):**

| Кнопка | Тип | Label (RU) | Условие |
|--------|-----|-----------|---------|
| Save | Primary | Сохранить | Форма валидна |
| Preview | Secondary | Предпросмотр воздействия | Только для edit (policy уже существует); Phase E |
| Cancel | Ghost | Отмена | Всегда |

- **Save** при создании → `POST /api/pricing/policies` → redirect к списку + toast «Политика создана»
- **Save** при редактировании → `PUT /api/pricing/policies/{id}` → redirect к списку + toast «Политика обновлена (v{N})»
- **Preview** → открывает Impact Preview modal (§4)
- **Cancel** → если есть unsaved changes: confirmation «Несохранённые изменения будут потеряны. Выйти?» → [Остаться] [Выйти]

### 2.10 Detail panel

Закрыт. Форма занимает всю ширину Main Area.

### 2.11 Forms / inputs

Форма разбита на 4 секции с заголовками (`--text-lg`, 600 weight). Каждая секция визуально отделена `--border-default` горизонтальной линией.

#### Секция 1: Основные параметры

| # | Field ID | Label (RU) | Input type | Placeholder | Validation | Default |
|---|----------|-----------|------------|-------------|------------|---------|
| 1 | `name` | Название политики | Text input | «Например: Маржа 25% WB» | Required, max 255 chars | — |
| 2 | `strategy_type` | Тип стратегии | Radio group (2 options) | — | Required | `TARGET_MARGIN` |
| 3 | `execution_mode` | Режим исполнения | Radio group (4 options) | — | Required | `RECOMMENDATION` |
| 4 | `priority` | Приоритет | Number input | «0» | Integer, ≥ 0 | `0` |
| 5 | `approval_timeout_hours` | Таймаут одобрения (ч) | Number input | «72» | Integer, > 0 | `72` |

**Radio group: Тип стратегии**

| Value | Label (RU) | Описание (hint под radio) |
|-------|-----------|--------------------------|
| `TARGET_MARGIN` | Целевая маржа | Рассчитывает цену для достижения заданной маржинальности с учётом комиссий, логистики и возвратов |
| `PRICE_CORRIDOR` | Ценовой коридор | Ограничивает цену заданными минимальной и максимальной границами |

При переключении `strategy_type` → динамическая секция §2 (параметры стратегии) перестраивается.

**Radio group: Режим исполнения**

| Value | Label (RU) | Описание (hint) |
|-------|-----------|-----------------|
| `RECOMMENDATION` | Рекомендация | Показывает рекомендуемую цену, но не применяет автоматически |
| `SEMI_AUTO` | Полуавтомат | Создаёт ценовое действие, требующее ручного одобрения |
| `FULL_AUTO` | Автомат | Автоматически применяет изменение цены после прохождения всех guards |
| `SIMULATED` | Симуляция | Выполняет полный расчёт, но не изменяет реальную цену на маркетплейсе |

При выборе `FULL_AUTO` — inline warning (yellow background):
> ⚠ Автоматический режим: ценовые изменения будут применяться без ручного подтверждения. Убедитесь, что guards настроены корректно.

Поле `approval_timeout_hours` отображается только при `execution_mode = SEMI_AUTO`.

#### Секция 2: Параметры стратегии (динамическая)

**Когда `strategy_type = TARGET_MARGIN`:**

| # | Field ID | Label (RU) | Input type | Validation | Default | Hint |
|---|----------|-----------|------------|------------|---------|------|
| 1 | `target_margin_pct` | Целевая маржа (%) | Number input | Required, [1, 80] | — | Рекомендуемый диапазон: 15–40% |
| 2 | `commission_source` | Источник комиссии | Dropdown | Required | `AUTO_WITH_MANUAL_FALLBACK` | — |
| 3 | `commission_manual_pct` | Ручная ставка комиссии (%) | Number input | [1, 50]; required if source = MANUAL | — | Используется как fallback или основное значение |
| 4 | `commission_lookback_days` | Период анализа комиссии (дней) | Number input | [7, 365] | `30` | Количество дней для расчёта средней комиссии |
| 5 | `commission_min_transactions` | Мин. транзакций для авто-расчёта | Number input | [1, 100] | `5` | Если меньше — используется fallback |
| 6 | `logistics_source` | Источник логистики | Dropdown | Required | `AUTO_WITH_MANUAL_FALLBACK` | — |
| 7 | `logistics_manual_amount` | Ручная сумма логистики (₽) | Number input | > 0; required if source = MANUAL | — | Сумма за единицу товара |
| 8 | `include_return_adjustment` | Учитывать возвраты | Toggle switch | — | `false` | Корректирует цену с учётом процента возвратов |
| 9 | `include_ad_cost` | Учитывать рекламные расходы | Toggle switch | — | `false` | Включает рекламную нагрузку в расчёт. Рекомендуется включать после 14+ дней стабильных данных |
| 10 | `rounding_step` | Шаг округления (₽) | Number input | [1, 100] | `10` | Цена округляется до ближайшего кратного |
| 11 | `rounding_direction` | Направление округления | Dropdown (3 options) | Required | `FLOOR` | — |

**Dropdown: Источник комиссии / логистики**

| Value | Label (RU) |
|-------|-----------|
| `AUTO` | Автоматический (из данных) |
| `MANUAL` | Ручной (фиксированное значение) |
| `AUTO_WITH_MANUAL_FALLBACK` | Авто с ручным fallback |

При выборе `AUTO` — поля `commission_manual_pct` / `logistics_manual_amount` скрываются.
При выборе `MANUAL` — поля `commission_lookback_days` / `commission_min_transactions` скрываются.
При выборе `AUTO_WITH_MANUAL_FALLBACK` — отображаются все поля.

**Dropdown: Направление округления**

| Value | Label (RU) |
|-------|-----------|
| `FLOOR` | Вниз (floor) |
| `NEAREST` | Ближайшее |
| `CEIL` | Вверх (ceil) |

**Когда `strategy_type = PRICE_CORRIDOR`:**

| # | Field ID | Label (RU) | Input type | Validation | Default | Hint |
|---|----------|-----------|------------|------------|---------|------|
| 1 | `corridor_min_price` | Минимальная цена (₽) | Number input (mono) | > 0, nullable | — | Абсолютный floor цены |
| 2 | `corridor_max_price` | Максимальная цена (₽) | Number input (mono) | > corridor_min_price, nullable | — | Абсолютный ceiling цены |

Cross-field validation: если оба заполнены, `corridor_max_price` > `corridor_min_price`. Inline error под `corridor_max_price`: «Максимальная цена должна быть больше минимальной».

Хотя бы одно из двух полей должно быть заполнено — иначе inline error: «Укажите хотя бы одну границу коридора».

#### Секция 3: Ограничения (constraints)

| # | Field ID | Label (RU) | Input type | Validation | Default | Hint |
|---|----------|-----------|------------|------------|---------|------|
| 1 | `min_margin_pct` | Минимальная маржа (%) | Number input | [0, 80], nullable | — | Floor маржинальности. Цена не будет снижена ниже этого порога |
| 2 | `max_price_change_pct` | Макс. изменение цены за раз (%) | Number input | [1, 50], nullable | — | Ограничивает размер одного ценового шага |
| 3 | `min_price` | Минимальная цена (₽) | Number input (mono) | > 0, nullable | — | Абсолютный floor |
| 4 | `max_price` | Максимальная цена (₽) | Number input (mono) | > min_price, nullable | — | Абсолютный ceiling |

Все поля nullable — constraint не применяется, если не задан.

#### Секция 4: Guards (блокирующие проверки)

Визуально — список toggle-row: каждый guard — строка с toggle + label + дополнительные параметры (если есть). Non-disableable guards (`stale_data_guard`) не имеют toggle, только параметр.

| # | Guard | Label (RU) | Toggle | Параметры | Default |
|---|-------|-----------|--------|-----------|---------|
| 1 | `margin_guard` | Контроль маржи | `margin_guard_enabled` (toggle) | — | Вкл |
| 2 | `frequency_guard` | Частота изменений | `frequency_guard_enabled` (toggle) | `frequency_guard_hours` — Number input, [1, 720], label «Мин. интервал (ч)» | Вкл, 24 ч |
| 3 | `volatility_guard` | Волатильность цены | `volatility_guard_enabled` (toggle) | `volatility_guard_reversals` — Number, [1, 20], label «Макс. разворотов»; `volatility_guard_period_days` — Number, [1, 90], label «За период (дней)» | Вкл, 3 / 7 |
| 4 | `promo_guard` | Промо-защита | `promo_guard_enabled` (toggle) | — | Вкл |
| 5 | `stock_out_guard` | Нулевой остаток | `stock_out_guard_enabled` (toggle) | — | Вкл |
| 6 | `stale_data_guard` | Устаревшие данные | _(нет toggle, всегда вкл)_ | `stale_data_guard_hours` — Number input, [1, 168], label «Порог устаревания (ч)» | 24 ч |

Визуальный layout guard-строки:

```
┌───────────────────────────────────────────────────────────────┐
│ [toggle] Частота изменений          Мин. интервал: [24] ч     │
│          Блокирует изменение, если предыдущее было слишком    │
│          недавно                                              │
└───────────────────────────────────────────────────────────────┘
```

Когда toggle OFF → параметры disabled (grayed out), строка приглушена (`opacity: 0.5`).

Guard `stale_data_guard` имеет label-hint: «Всегда активен (нельзя отключить). Блокирует ценообразование при устаревших данных.»

### 2.12 API endpoints

| Action | Method | Endpoint | Request body | Response |
|--------|--------|----------|-------------|----------|
| Create | POST | `/api/pricing/policies` | `CreatePricePolicyRequest` (см. ниже) | `201 PolicyResponse` |
| Load for edit | GET | `/api/pricing/policies/{policyId}` | — | `PolicyResponse` |
| Update | PUT | `/api/pricing/policies/{policyId}` | `UpdatePricePolicyRequest` | `200 PolicyResponse` |

**CreatePricePolicyRequest (JSON body):**

```json
{
  "name": "Маржа 25% WB",
  "strategyType": "TARGET_MARGIN",
  "strategyParams": {
    "targetMarginPct": 0.25,
    "commissionSource": "AUTO_WITH_MANUAL_FALLBACK",
    "commissionManualPct": null,
    "commissionLookbackDays": 30,
    "commissionMinTransactions": 5,
    "logisticsSource": "AUTO_WITH_MANUAL_FALLBACK",
    "logisticsManualAmount": null,
    "includeReturnAdjustment": false,
    "includeAdCost": false,
    "roundingStep": 10,
    "roundingDirection": "FLOOR"
  },
  "minMarginPct": 0.15,
  "maxPriceChangePct": 0.10,
  "minPrice": null,
  "maxPrice": null,
  "guardConfig": {
    "marginGuardEnabled": true,
    "frequencyGuardEnabled": true,
    "frequencyGuardHours": 24,
    "volatilityGuardEnabled": true,
    "volatilityGuardReversals": 3,
    "volatilityGuardPeriodDays": 7,
    "promoGuardEnabled": true,
    "stockOutGuardEnabled": true,
    "staleDataGuardHours": 24
  },
  "executionMode": "SEMI_AUTO",
  "approvalTimeoutHours": 72,
  "priority": 10
}
```

### 2.13 Empty / loading / error states

| Состояние | Отображение |
|-----------|-------------|
| Загрузка формы (edit mode) | Skeleton: shimmer блоки для каждой секции |
| Policy not found (edit) | Redirect to list + toast error: «Политика не найдена» |
| Validation error (client) | Inline red text под полем + red border. Scroll to first error on submit. Focus first invalid field |
| Validation error (server, 400) | Parse field errors from response → map to form fields. If unmapped → generic toast |
| 409 Conflict | Toast: «Политика была изменена другим пользователем. Перезагрузите и попробуйте снова.» + [Обновить] |
| 403 Forbidden | Toast: «У вас нет прав для управления ценовыми политиками.» + redirect to list |

### 2.14 User flow scenarios

**Сценарий 1: Создание политики TARGET_MARGIN с полной конфигурацией**

1. Pricing manager нажимает «Создать политику»
2. Вводит название: «Маржа 25% WB»
3. Тип стратегии: «Целевая маржа» (default)
4. Заполняет: целевая маржа 25%, источник комиссии AUTO_WITH_MANUAL_FALLBACK, ручная комиссия 15% (fallback), период 30 дней, мин. транзакций 5
5. Логистика: AUTO_WITH_MANUAL_FALLBACK, ручная 150 ₽
6. Включает «Учёт возвратов»
7. Ограничения: мин. маржа 15%, макс. изменение 10%
8. Guards: всё по умолчанию, меняет frequency_guard_hours на 12
9. Режим: Полуавтомат (SEMI_AUTO)
10. Нажимает «Сохранить» → redirect к списку, toast «Политика создана»

**Сценарий 2: Редактирование с предпросмотром воздействия**

1. Pricing manager открывает политику на редактирование
2. Меняет target_margin_pct с 25% на 30%
3. Нажимает «Предпросмотр воздействия» → открывается Impact Preview (§4)
4. Видит, что 40% товаров получат снижение цены → решает уменьшить target до 28%
5. Нажимает Preview ещё раз → результат приемлемый
6. Нажимает «Сохранить» → version инкрементируется, toast «Политика обновлена (v4)»

### 2.15 Edge cases

| Case | Поведение |
|------|----------|
| Переключение strategy_type при заполненных params | Confirmation: «Параметры текущей стратегии будут сброшены. Продолжить?» → params очищаются, новая секция отображается с defaults |
| Edit archived policy | Запрещено: redirect to list + toast «Архивированные политики нельзя редактировать» |
| commission_source = MANUAL, но commission_manual_pct пуст | Client validation error: «Укажите ручную ставку комиссии» |
| target_margin_pct + effective_cost_rate ≥ 100% | Server validation (400): toast «Целевая маржа в сочетании с расходными ставками превышает 100%. Скорректируйте параметры.» |
| Browser tab close с unsaved changes | `beforeunload` event: browser native dialog «Вы уверены, что хотите покинуть страницу?» |

### 2.16 Accessibility

- Все form fields имеют `<label>` с `for` attribute
- Error messages связаны через `aria-describedby`
- Radio groups wrapped в `<fieldset>` + `<legend>`
- Toggle switches имеют `role="switch"` + `aria-checked`
- Keyboard: Tab navigates fields, Space toggles switches, Enter submits (when on save button)
- Focus management: при server error — focus на первое поле с ошибкой

---

## 3. Назначения политики (Policy Assignments)

### 3.1 Route / URL

Встроено в Detail Panel политики (§1.10, таб «Назначения») и доступно как отдельный экран:

```
/workspace/:workspaceId/pricing/policies/:policyId/assignments
```

### 3.2 Breadcrumbs

```
Ценообразование > Ценовые политики > {policyName} > Назначения
```

### 3.3 Minimum role

- **Чтение:** Any role
- **Создание / удаление assignment:** PRICING_MANAGER, ADMIN, OWNER

### 3.4 Phase

C — Pricing

### 3.5 Layout zone

**Detail Panel** (когда в контексте policy list) или **Main Area** (когда открыт как отдельный таб).

### 3.6 KPI strip

Нет.

### 3.7 Data grid

Компактная таблица assignments.

**Колонки:**

| # | Column ID | Заголовок (RU) | Тип | Ширина | Align | Описание |
|---|-----------|---------------|-----|--------|-------|----------|
| 1 | `scope_type` | Уровень | Badge | 130px | Left | `CONNECTION` → «Подключение», `CATEGORY` → «Категория», `SKU` → «Товар» |
| 2 | `connection_name` | Подключение | Text | 180px, flex | Left | Название marketplace connection |
| 3 | `marketplace` | Маркетплейс | Badge (icon) | 80px | Center | WB / Ozon иконка |
| 4 | `target` | Цель назначения | Text | 200px, flex | Left | Для CONNECTION — «—» (all offers); для CATEGORY — название категории; для SKU — название + артикул |
| 5 | `actions` | — | Action icon | 60px | Center | Удалить (trash icon) |

**Pagination:** Client-side (assignments per policy — обычно < 50).

### 3.8 Filter bar

Нет (слишком мало данных для фильтрации).

### 3.9 Actions / buttons

**Toolbar:**

| Кнопка | Тип | Label (RU) | Условие |
|--------|-----|-----------|---------|
| Add | Primary (compact) | Добавить назначение | PRICING_MANAGER+ |

**Per-row:**

| Action | Иконка | Tooltip (RU) | Условие |
|--------|--------|-------------|---------|
| Delete | `lucide:trash-2` | Удалить назначение | PRICING_MANAGER+ |

**Delete confirmation:** «Удалить назначение? Политика перестанет применяться к {target}.» → [Отмена] [Удалить (danger)]

**Форма «Добавить назначение» (inline panel, раскрывается под toolbar):**

| # | Field | Label (RU) | Input type | Validation |
|---|-------|-----------|------------|------------|
| 1 | `connectionId` | Подключение | Dropdown (marketplace connections list) | Required |
| 2 | `scopeType` | Уровень | Radio (3 options) | Required |
| 3 | `categoryId` | Категория | Dropdown with search (categories for connection) | Required if scopeType = CATEGORY |
| 4 | `marketplaceOfferId` | Товар | Dropdown with search (offers for connection) | Required if scopeType = SKU |

Поля `categoryId` / `marketplaceOfferId` отображаются условно — только при соответствующем `scopeType`.

Dropdown для товара: поиск по названию, артикулу, баркоду. Показывает: «{name} · {seller_sku} · {barcode}».

Кнопки формы: [Добавить (primary, compact)] [Отмена (ghost)]

### 3.10 Detail panel

Нет отдельного detail panel — assignments отображаются в самом panel или Main Area.

### 3.11 Forms / inputs

См. §3.9 — inline форма добавления.

### 3.12 API endpoints

| Action | Method | Endpoint | Request | Response |
|--------|--------|----------|---------|----------|
| List | GET | `/api/pricing/policies/{policyId}/assignments` | — | `List<AssignmentResponse>` |
| Add | POST | `/api/pricing/policies/{policyId}/assignments` | `{ connectionId, scopeType, categoryId?, marketplaceOfferId? }` | `201 AssignmentResponse` |
| Delete | DELETE | `/api/pricing/policies/{policyId}/assignments/{assignmentId}` | — | `204` |
| Connections (for dropdown) | GET | `/api/connections` | — | `List<ConnectionSummary>` |
| Categories (for dropdown) | GET | `/api/connections/{connId}/categories?search=` | Query | `List<CategorySummary>` |
| Offers (for dropdown) | GET | `/api/offers?connectionId={}&search=` | Query | `Page<OfferSummary>` |

### 3.13 Empty / loading / error states

| Состояние | Отображение |
|-----------|-------------|
| Нет назначений | «Политика не назначена ни на один товар или подключение.» + [Добавить назначение] |
| Дубликат (409) | Toast: «Такое назначение уже существует для этой политики.» |
| Connection not found | Toast: «Подключение не найдено или было удалено.» |

### 3.14 User flow scenarios

**Сценарий 1: Назначение политики на всё подключение WB**

1. Pricing manager открывает policy detail → таб «Назначения»
2. Нажимает «Добавить назначение»
3. Выбирает подключение: «Wildberries — Основной»
4. Уровень: «Подключение» (CONNECTION)
5. Нажимает «Добавить» → assignment появляется в таблице
6. Все товары этого WB-подключения теперь обрабатываются этой политикой

**Сценарий 2: Назначение на конкретный SKU (override)**

1. Pricing manager хочет применить другую политику к конкретному товару
2. Добавляет assignment: подключение WB, уровень «Товар» (SKU), ищет товар по артикулу
3. SKU-level assignment имеет приоритет над CONNECTION-level (специфичность 3 > 1)

### 3.15 Edge cases

| Case | Поведение |
|------|----------|
| Duplicate assignment | Server returns 409 → toast «Назначение уже существует» |
| Delete last assignment of active policy | Разрешено — policy остаётся active, но не применяется ни к чему |
| Connection disabled | Assignment показывается с warning badge: «⚠ Подключение неактивно» |

### 3.16 Accessibility

- Inline add form: focus management при открытии/закрытии
- Dropdown with search: `combobox` ARIA role
- Delete button: `aria-label` = «Удалить назначение: {scope} — {target}»

---

## 4. Предпросмотр воздействия (Impact Preview)

### 4.1 Route / URL

Нет отдельного route. Открывается как **modal overlay** из формы редактирования политики (§2) или по кнопке в Detail Panel.

Trigger: `POST /api/pricing/policies/{policyId}/preview`

### 4.2 Breadcrumbs

Не изменяются (modal поверх текущего экрана).

### 4.3 Minimum role

PRICING_MANAGER, ADMIN, OWNER

### 4.4 Phase

E — Impact Preview (Seller Operations phase)

### 4.5 Layout zone

**Modal overlay** — центрированный, ширина 80% viewport (min 960px, max 1200px), высота 80% viewport. Backdrop dim. Scrollable внутри.

### 4.6 KPI strip

**Да** — summary KPI cards в верхней части modal.

| # | KPI | Label (RU) | Формат | Цвет |
|---|-----|-----------|--------|------|
| 1 | `total_offers` | Всего товаров | `1 234` (mono) | Neutral |
| 2 | `eligible_count` | Подходящих | `987` (mono) | Neutral |
| 3 | `change_count` | Изменение цены | `654` (mono) | `--status-success` (green) |
| 4 | `skip_count` | Пропущено | `280` (mono) | `--status-warning` (yellow) |
| 5 | `hold_count` | Ожидание | `53` (mono) | `--status-neutral` (gray) |
| 6 | `avg_price_change_pct` | Среднее изменение | `↓ 8,2%` / `↑ 3,1%` (mono) | `--finance-negative` / `--finance-positive` |
| 7 | `max_price_change_pct` | Макс. изменение | `↓ 15,0%` (mono) | `--finance-negative` if down |
| 8 | `min_margin_after` | Мин. маржа после | `18,3%` (mono) | `--finance-positive` if > 0 |

KPI cards — compact (not oversized), 4 per row (2 rows).

### 4.7 Data grid

Под KPI strip — таблица per-offer breakdown. AG Grid, серверная пагинация (preview endpoint возвращает paginated offers).

**Колонки:**

| # | Column ID | Заголовок (RU) | Тип | Ширина | Align | Sortable | Описание |
|---|-----------|---------------|-----|--------|-------|----------|----------|
| 1 | `offer_name` | Товар | Text | 250px, flex | Left | — | Название товара |
| 2 | `seller_sku` | Артикул | Text (mono) | 120px | Left | — | Seller SKU |
| 3 | `current_price` | Текущая цена | Currency (mono) | 120px | Right | ✓ | `4 500 ₽` |
| 4 | `target_price` | Целевая цена | Currency (mono) | 120px | Right | ✓ | `3 890 ₽` или «—» для SKIP/HOLD |
| 5 | `change_pct` | Δ% | Percent (mono) | 80px | Right | ✓ | `↓ 13,6%` с цветом (green/red) |
| 6 | `change_amount` | Δ₽ | Currency (mono) | 100px | Right | ✓ | `−610 ₽` с цветом |
| 7 | `decision_type` | Решение | Badge | 100px | Left | ✓ | CHANGE (green), SKIP (yellow), HOLD (gray) |
| 8 | `skip_reason` | Причина | Text | 200px, flex | Left | — | Human-readable reason для SKIP/HOLD, «—» для CHANGE |

**Decision type badges:**

| Decision | Label (RU) | Цвет |
|----------|-----------|------|
| `CHANGE` | Изменение | `--status-success` |
| `SKIP` | Пропуск | `--status-warning` |
| `HOLD` | Ожидание | `--status-neutral` |

**Default sort:** `change_pct DESC` (наибольшие изменения первыми)
**Rows per page:** 50 (in modal)

### 4.8 Filter bar

Compact filter в modal (над таблицей):

| Filter | Тип | Значения |
|--------|-----|----------|
| Решение | Multi-select pills | Изменение, Пропуск, Ожидание |

### 4.9 Actions / buttons

**Modal footer:**

| Кнопка | Тип | Label (RU) | Действие |
|--------|-----|-----------|----------|
| Close | Secondary | Закрыть | Закрыть modal |
| Export | Ghost | Экспорт CSV | Экспортировать preview breakdown в CSV |

### 4.10 Detail panel

Нет (внутри modal).

### 4.11 Forms / inputs

Нет.

### 4.12 API endpoints

| Action | Method | Endpoint | Request | Response |
|--------|--------|----------|---------|----------|
| Run preview | POST | `/api/pricing/policies/{policyId}/preview` | `{ page, size, sort }` (optional) | `ImpactPreviewResponse` |

**ImpactPreviewResponse:**

```json
{
  "summary": {
    "totalOffers": 1234,
    "eligibleCount": 987,
    "changeCount": 654,
    "skipCount": 280,
    "holdCount": 53,
    "avgPriceChangePct": -8.2,
    "maxPriceChangePct": -15.0,
    "minMarginAfter": 18.3
  },
  "offers": {
    "content": [...],
    "totalElements": 987,
    "page": 0,
    "size": 50
  }
}
```

### 4.13 Empty / loading / error states

| Состояние | Отображение |
|-----------|-------------|
| Loading (computing preview) | Modal body: centered spinner + «Рассчитываем предпросмотр...» Progress bar (indeterminate). KPI cards — skeleton |
| Timeout (> 30s) | Message: «Слишком много товаров для мгновенного расчёта. Результаты будут готовы через несколько минут.» (Phase E: async polling, TBD) |
| No eligible offers | KPI strip shows 0s. Table empty: «Нет товаров, подходящих под эту политику. Проверьте назначения и данные.» |
| Policy has no assignments | Before API call: toast warning «Добавьте назначения перед предпросмотром.» Preview не запускается |
| Server error | Toast error: «Не удалось выполнить предпросмотр.» + modal body: retry button |

### 4.14 User flow scenarios

**Сценарий 1: Оценка новой политики перед активацией**

1. Pricing manager создал policy, сохранил (DRAFT), добавил assignments
2. Открывает edit → нажимает «Предпросмотр воздействия»
3. Видит: 1 200 товаров, 900 eligible, 600 CHANGE, avg −7%, min margin 19%
4. Оценивает risk: max change −15% — приемлемо
5. Закрывает preview → нажимает «Сохранить» → активирует политику

**Сценарий 2: Проверка после изменения target_margin**

1. Pricing manager изменяет target_margin с 25% на 35%
2. Запускает preview → видит: avg change +12%, но min margin after = 12% (ниже желаемого 15%)
3. Решает: ставит min_margin_pct = 15% как constraint
4. Запускает preview снова → min margin after = 15.1%, OK
5. Сохраняет

### 4.15 Edge cases

| Case | Поведение |
|------|----------|
| Preview data != actual run | Disclaimer text под KPI: «Предпросмотр — оценка на текущий момент. Фактический результат может отличаться, если данные изменятся.» (italic, `--text-secondary`) |
| Large scope (> 10 000 offers) | Async preview: modal показывает progress, polling endpoint. Timeout banner |
| Preview while ETL sync in progress | Warning: «Данные обновляются. Предпросмотр может содержать неактуальные значения.» |
| Stale data for some offers | Offers with stale data → decision_type = SKIP, skip_reason = stale data. This is reflected in summary counts |

### 4.16 Accessibility

- Modal has `role="dialog"`, `aria-modal="true"`, `aria-labelledby` → title
- Focus trapped inside modal. Close on Escape
- KPI values have `aria-label` with full description: «Изменение цены: 654 товара»
- Table sortable columns: `aria-sort="ascending"` / `"descending"`

---

## 5. Прогоны ценообразования — список (Pricing Runs)

### 5.1 Route / URL

```
/workspace/:workspaceId/pricing/runs
```

### 5.2 Breadcrumbs

```
Ценообразование > Прогоны
```

### 5.3 Minimum role

- **Чтение:** Any role
- **Запуск manual run:** PRICING_MANAGER, ADMIN, OWNER

### 5.4 Phase

C — Pricing

### 5.5 Layout zone

**Main Area** — таблица с toolbar.

### 5.6 KPI strip

Нет.

### 5.7 Data grid

AG Grid, серверная пагинация.

**Колонки:**

| # | Column ID | Заголовок (RU) | Тип | Ширина | Align | Sortable | Описание |
|---|-----------|---------------|-----|--------|-------|----------|----------|
| 1 | `id` | # | Number (mono) | 70px | Right | ✓ | Run ID |
| 2 | `trigger_type` | Триггер | Badge | 140px | Left | ✓ | См. §Trigger badges |
| 3 | `connection_name` | Подключение | Text | 200px, flex | Left | ✓ | Marketplace connection name |
| 4 | `marketplace` | МП | Badge (icon) | 60px | Center | — | WB / Ozon |
| 5 | `status` | Статус | Status badge | 160px | Left | ✓ | См. §Run status badges |
| 6 | `total_offers` | Всего | Number (mono) | 90px | Right | ✓ | — |
| 7 | `eligible_count` | Подходящих | Number (mono) | 100px | Right | ✓ | — |
| 8 | `change_count` | Изменено | Number (mono) | 100px | Right | ✓ | Green text |
| 9 | `skip_count` | Пропущено | Number (mono) | 100px | Right | ✓ | Yellow text |
| 10 | `hold_count` | Ожидание | Number (mono) | 100px | Right | ✓ | Gray text |
| 11 | `started_at` | Начало | Timestamp | 140px | Left | ✓ | «28 мар, 14:32» |
| 12 | `duration` | Длительность | Duration | 100px | Right | — | Computed: `completed_at - started_at`. Format: «12 сек», «2 мин 34 сек». «—» if IN_PROGRESS |
| 13 | `created_at` | Создан | Timestamp | 140px | Left | ✓ | Relative |

**Trigger badges:**

| Trigger | Label (RU) | Иконка |
|---------|-----------|--------|
| `POST_SYNC` | После синхр. | `lucide:refresh-cw` |
| `MANUAL` | Ручной | `lucide:hand` |
| `SCHEDULED` | По расписанию | `lucide:clock` |
| `POLICY_CHANGE` | Изм. политики | `lucide:settings` |

**Run status badges:**

| Status | Label (RU) | Цвет |
|--------|-----------|------|
| `PENDING` | Ожидание | `--status-info` (blue) |
| `IN_PROGRESS` | Выполняется | `--status-info` (blue) + spinner |
| `COMPLETED` | Завершён | `--status-success` (green) |
| `COMPLETED_WITH_ERRORS` | С ошибками | `--status-warning` (yellow) |
| `FAILED` | Ошибка | `--status-error` (red) |

**Default sort:** `created_at DESC`
**Rows per page:** 50

### 5.8 Filter bar

| Filter | Тип | Значения | Default |
|--------|-----|----------|---------|
| Подключение | Single-select dropdown | Все connections workspace | Все |
| Статус | Multi-select pills | Ожидание, Выполняется, Завершён, С ошибками, Ошибка | Все |
| Триггер | Multi-select pills | После синхр., Ручной, По расписанию, Изм. политики | Все |
| Период | Date range picker | От — До | Последние 7 дней |

### 5.9 Actions / buttons

**Toolbar:**

| Кнопка | Тип | Label (RU) | Иконка | Условие |
|--------|-----|-----------|--------|---------|
| Manual run | Primary | Запустить прогон | `lucide:play` | PRICING_MANAGER+ |

**Клик «Запустить прогон»:** открывает dropdown/popover с выбором connection:

```
┌─ Запустить прогон ──────────────┐
│ Выберите подключение:            │
│                                   │
│ ○ Wildberries — Основной         │
│ ○ Ozon — Основной                │
│                                   │
│ [Отмена]  [Запустить (primary)]  │
└───────────────────────────────────┘
```

После запуска → toast info: «Прогон запущен для {connection}. Результаты появятся в списке.» Новая строка с `PENDING` status появляется в таблице (WebSocket update или optimistic insert).

### 5.10 Detail panel

Клик по строке → Detail Panel с summary runs info (read-only).

**Содержимое panel:**

```
Прогон #42

Триггер:        ● После синхр.
Подключение:    Wildberries — Основной
Статус:         ● Завершён
Начало:         28 мар, 14:32
Окончание:      28 мар, 14:34
Длительность:   2 мин 12 сек

── Результаты ──
Всего товаров:  1 234
Подходящих:     987
Изменено:       654
Пропущено:      280
Ожидание:       53

[Открыть подробности →]
```

Кнопка «Открыть подробности» → переход к Run Detail (§6) в новом табе.

### 5.11 Forms / inputs

Только dropdown выбора connection при manual run (см. §5.9).

### 5.12 API endpoints

| Action | Method | Endpoint | Request | Response |
|--------|--------|----------|---------|----------|
| List runs | GET | `/api/pricing/runs?connectionId={}&status={}&triggerType={}&from={}&to={}&page={}&size={}&sort={}` | Query params | `Page<PricingRunSummaryResponse>` |
| Manual run | POST | `/api/pricing/runs` | `{ connectionId }` | `201 PricingRunResponse` |

### 5.13 Empty / loading / error states

| Состояние | Отображение |
|-----------|-------------|
| Загрузка | Skeleton grid |
| Нет прогонов | «Прогоны ценообразования ещё не выполнялись. Они запускаются автоматически после синхронизации данных или вручную.» |
| Нет результатов (фильтры) | «Нет прогонов, соответствующих фильтрам.» + [Сбросить фильтры] |
| Manual run: no active policies | Toast warning: «Нет активных политик для этого подключения. Создайте и активируйте политику.» |
| Manual run: ETL in progress | Toast warning: «Синхронизация данных ещё идёт. Дождитесь завершения перед запуском прогона.» |
| Manual run: pricing run already in progress | Toast warning: «Прогон для этого подключения уже выполняется.» |

### 5.14 User flow scenarios

**Сценарий 1: Проверка результатов автоматического прогона**

1. Pricing manager открывает «Прогоны» → видит новый прогон с trigger «После синхр.», status «Завершён»
2. Кликает строку → Detail Panel показывает summary: 1 200 total, 800 changes, 350 skips
3. Нажимает «Открыть подробности» → переходит к Run Detail с per-offer breakdown

**Сценарий 2: Ручной прогон после изменения политики**

1. Pricing manager изменил policy, хочет немедленный пересчёт (не ждать sync)
2. Нажимает «Запустить прогон» → выбирает connection → «Запустить»
3. В таблице появляется новый run с status «Ожидание» → «Выполняется» (WebSocket update) → «Завершён»
4. Открывает detail, проверяет результаты

### 5.15 Edge cases

| Case | Поведение |
|------|----------|
| Run in progress (real-time) | Status badge показывает spinner. Counters обновляются по WebSocket по мере обработки offers (or after completion) |
| FAILED run | Red badge. Detail panel показывает `error_details` в formatted box (mono, red border) |
| COMPLETED_WITH_ERRORS | Yellow badge. Detail panel: summary + «{N} товаров обработано с ошибками.» Link to filtered decisions |

### 5.16 Accessibility

- Manual run trigger: popover manages focus, Escape closes
- Status badges: screen reader text includes status
- IN_PROGRESS spinner: `aria-label="Выполняется"`, `role="status"`

---

## 6. Детали прогона (Pricing Run Detail)

### 6.1 Route / URL

```
/workspace/:workspaceId/pricing/runs/:runId
```

### 6.2 Breadcrumbs

```
Ценообразование > Прогоны > Прогон #{runId}
```

### 6.3 Minimum role

Any role (read-only screen)

### 6.4 Phase

C — Pricing

### 6.5 Layout zone

**Main Area** — summary strip + decisions table.

### 6.6 KPI strip

**Да** — summary KPI cards (аналогично Impact Preview, §4.6).

| # | KPI | Label (RU) | Формат | Цвет |
|---|-----|-----------|--------|------|
| 1 | `total_offers` | Всего товаров | `1 234` | Neutral |
| 2 | `eligible_count` | Подходящих | `987` | Neutral |
| 3 | `change_count` | Изменено | `654` | `--status-success` |
| 4 | `skip_count` | Пропущено | `280` | `--status-warning` |
| 5 | `hold_count` | Ожидание | `53` | `--status-neutral` |

Под KPI strip — meta info: trigger, connection, status badge, timing.

### 6.7 Data grid

Decisions для этого run. AG Grid, серверная пагинация.

**Колонки:**

| # | Column ID | Заголовок (RU) | Тип | Ширина | Align | Sortable | Описание |
|---|-----------|---------------|-----|--------|-------|----------|----------|
| 1 | `offer_name` | Товар | Text | 250px, flex | Left | ✓ | Название offer |
| 2 | `seller_sku` | Артикул | Text (mono) | 120px | Left | ✓ | Seller SKU |
| 3 | `decision_type` | Решение | Badge | 100px | Left | ✓ | CHANGE / SKIP / HOLD |
| 4 | `current_price` | Текущая цена | Currency (mono) | 120px | Right | ✓ | `4 500 ₽` |
| 5 | `target_price` | Целевая цена | Currency (mono) | 120px | Right | ✓ | `3 890 ₽` или «—» |
| 6 | `change_pct` | Δ% | Percent (mono) | 80px | Right | ✓ | `↓ 13,6%` / `↑ 5,2%` |
| 7 | `policy_name` | Политика | Text | 180px | Left | ✓ | Имя policy, применённой к offer |
| 8 | `strategy_type` | Стратегия | Badge | 140px | Left | ✓ | Целевая маржа / Ценовой коридор |
| 9 | `skip_reason` | Причина | Text | 200px, flex | Left | — | Для SKIP/HOLD |
| 10 | `execution_mode` | Режим | Badge | 130px | Left | ✓ | LIVE / SIMULATED |

**Default sort:** `decision_type ASC` (CHANGE first), then `change_pct DESC`
**Rows per page:** 100

### 6.8 Filter bar

| Filter | Тип | Значения |
|--------|-----|----------|
| Решение | Multi-select pills | Изменение, Пропуск, Ожидание |
| Стратегия | Single-select | Целевая маржа, Ценовой коридор |

### 6.9 Actions / buttons

Toolbar: нет write-actions (read-only screen).

Опциональные actions:

| Кнопка | Тип | Label (RU) | Действие |
|--------|-----|-----------|----------|
| Export | Ghost | Экспорт CSV | Экспорт решений run в CSV |

### 6.10 Detail panel

Клик по строке decision → Detail Panel с полной информацией о решении. Фактически — то же, что Decision Detail (§8), но отображается в panel, а не на отдельной странице.

### 6.11 Forms / inputs

Нет.

### 6.12 API endpoints

| Action | Method | Endpoint | Request | Response |
|--------|--------|----------|---------|----------|
| Run details | GET | `/api/pricing/runs/{runId}` | — | `PricingRunDetailResponse` |
| Run decisions | GET | `/api/pricing/decisions?pricingRunId={runId}&page={}&size={}&sort={}&decisionType={}` | Query params | `Page<DecisionSummaryResponse>` |

### 6.13 Empty / loading / error states

| Состояние | Отображение |
|-----------|-------------|
| Run not found | Redirect to runs list + toast: «Прогон не найден» |
| Run IN_PROGRESS | KPI cards show current counts (auto-refresh via WebSocket). Banner: «Прогон ещё выполняется. Данные обновляются автоматически.» |
| Run FAILED | Red banner: «Прогон завершился с ошибкой: {error summary}.» KPI shows partial data if available |

### 6.14 User flow scenarios

**Сценарий 1: Анализ результатов завершённого прогона**

1. Pricing manager открывает run detail
2. Смотрит KPI: 654 changes, 280 skips — нормальное соотношение
3. Фильтрует по «Пропуск» → видит причины: «Себестоимость не задана» (80%), «Устаревшие данные» (15%), «Ручная блокировка» (5%)
4. Понимает: нужно заполнить cost profiles для бОльшего покрытия
5. Кликает decision → panel показывает full explanation

### 6.15 Edge cases

| Case | Поведение |
|------|----------|
| Run с 0 decisions (все skipped at eligibility) | KPI: total = N, eligible = 0. Table empty: «Ни один товар не прошёл проверку допуска.» |
| Very large run (>10 000 decisions) | Server-side pagination. Export CSV — async |

### 6.16 Accessibility

- KPI values: `aria-label` с описанием
- Navigation: keyboard grid navigation
- Run status: `role="status"` for live-updating elements

---

## 7. Решения — список (Decisions List)

### 7.1 Route / URL

```
/workspace/:workspaceId/pricing/decisions
```

### 7.2 Breadcrumbs

```
Ценообразование > Решения
```

### 7.3 Minimum role

Any role (read-only)

### 7.4 Phase

C — Pricing

### 7.5 Layout zone

**Main Area** — таблица с rich filtering.

### 7.6 KPI strip

Нет. Это лог всех решений — KPI не целесообразен (нет единого контекста для агрегации).

### 7.7 Data grid

AG Grid, серверная пагинация. Это основной аналитический инструмент для прослеживания решений.

**Колонки:**

| # | Column ID | Заголовок (RU) | Тип | Ширина | Align | Sortable | Описание |
|---|-----------|---------------|-----|--------|-------|----------|----------|
| 1 | `id` | # | Number (mono) | 80px | Right | ✓ | Decision ID |
| 2 | `offer_name` | Товар | Text | 220px, flex | Left | ✓ | Offer name |
| 3 | `seller_sku` | Артикул | Text (mono) | 120px | Left | ✓ | — |
| 4 | `connection_name` | Подключение | Text | 160px | Left | ✓ | — |
| 5 | `decision_type` | Решение | Badge | 100px | Left | ✓ | CHANGE / SKIP / HOLD badges |
| 6 | `current_price` | Было | Currency (mono) | 110px | Right | ✓ | — |
| 7 | `target_price` | Стало | Currency (mono) | 110px | Right | ✓ | — |
| 8 | `change_pct` | Δ% | Percent (mono) | 80px | Right | ✓ | With sign/color |
| 9 | `policy_name` | Политика | Text | 160px | Left | ✓ | — |
| 10 | `strategy_type` | Стратегия | Badge | 130px | Left | ✓ | — |
| 11 | `execution_mode` | Режим | Badge | 100px | Left | ✓ | LIVE / SIMULATED |
| 12 | `pricing_run_id` | Прогон | Link (mono) | 80px | Right | — | `#42` — link to run detail |
| 13 | `skip_reason` | Причина | Text | 200px, flex | Left | — | Для SKIP/HOLD |
| 14 | `created_at` | Дата | Timestamp | 140px | Left | ✓ | «28 мар, 14:32» |

**Default sort:** `created_at DESC`
**Rows per page:** 100 / 200

### 7.8 Filter bar

| Filter | Тип | Значения | Default |
|--------|-----|----------|---------|
| Подключение | Single-select dropdown | All connections | Все |
| Товар | Search input (autocomplete) | Offers by name/sku | — |
| Решение | Multi-select pills | Изменение, Пропуск, Ожидание | Все |
| Прогон | Number input | Pricing run ID | — |
| Период | Date range picker | От — До | Последние 7 дней |
| Режим | Toggle pills | Рабочий (LIVE) / Симуляция (SIMULATED) | Все |

### 7.9 Actions / buttons

**Toolbar:**

| Кнопка | Тип | Label (RU) | Действие |
|--------|-----|-----------|----------|
| Export | Ghost | Экспорт CSV | Экспорт отфильтрованных решений |

### 7.10 Detail panel

Клик по строке → Detail Panel с полной информацией о решении (§8 — Decision Detail, отрисованный в panel).

### 7.11 Forms / inputs

Нет.

### 7.12 API endpoints

| Action | Method | Endpoint | Request | Response |
|--------|--------|----------|---------|----------|
| List | GET | `/api/pricing/decisions?connectionId={}&marketplaceOfferId={}&decisionType={}&pricingRunId={}&from={}&to={}&page={}&size={}&sort={}` | Query params | `Page<DecisionSummaryResponse>` |

### 7.13 Empty / loading / error states

| Состояние | Отображение |
|-----------|-------------|
| Загрузка | Skeleton grid |
| Нет решений | «Решений пока нет. Они появятся после первого прогона ценообразования.» |
| Нет результатов (фильтры) | «Нет решений, соответствующих фильтрам.» + [Сбросить фильтры] |

### 7.14 User flow scenarios

**Сценарий 1: Поиск решений по конкретному товару**

1. Pricing manager хочет понять, почему цена товара X не изменилась
2. Вводит артикул в фильтр «Товар» → видит историю решений по этому SKU
3. Последнее решение — SKIP, причина: «Себестоимость не задана»
4. Кликает → Detail Panel показывает explanation_summary
5. Понимает: нужно заполнить cost profile для этого товара

**Сценарий 2: Анализ решений за период**

1. Analyst выбирает период «Последние 30 дней», фильтр «Изменение»
2. Сортирует по `change_pct DESC` → видит наибольшие ценовые изменения
3. Проверяет top-10 решений — всё ли обосновано

### 7.15 Edge cases

| Case | Поведение |
|------|----------|
| Large dataset (>100k decisions) | Server-side pagination, index on (workspace_id, created_at DESC). No client-side caching of all pages |
| Decision older than retention | Not returned by API (automatically cleaned by retention policy: SKIP — 30 дней, RECOMMENDATION — 90 дней) |
| pricing_run_id filter → invalid run | Empty result, no error |

### 7.16 Accessibility

- Offer search autocomplete: `combobox` role, `aria-expanded`, `aria-activedescendant`
- Date range picker: keyboard-navigable calendar
- Grid pagination: `aria-label` on page controls

---

## 8. Детали решения (Decision Detail)

### 8.1 Route / URL

Как отдельная страница (deep link):
```
/workspace/:workspaceId/pricing/decisions/:decisionId
```

Также отображается в Detail Panel при клике по строке в Decisions List (§7) или Run Detail (§6).

### 8.2 Breadcrumbs

```
Ценообразование > Решения > Решение #{decisionId}
```

### 8.3 Minimum role

Any role (read-only)

### 8.4 Phase

C — Pricing

### 8.5 Layout zone

**Detail Panel** (при клике из списка) или **Main Area** (при прямом переходе по URL).

### 8.6 KPI strip

Нет (single entity view).

### 8.7 Data grid

Нет (detail view).

### 8.8 Filter bar

Нет.

### 8.9 Actions / buttons

Нет write-actions. Навигационные ссылки:
- «Открыть прогон #{runId}» → link to Run Detail
- «Открыть политику» → link to Policy Detail/Edit
- «Открыть товар» → link to Offer detail (Seller Operations module)

### 8.10 Detail panel

Полное содержание Decision Detail. Разбито на секции с `--border-default` разделителями.

#### Секция 1: Заголовок решения

```
Решение #{decisionId}

Товар:          Кроссовки Nike Air Max 90        (link → offer)
Артикул:        NIKE-AM90-42
Подключение:    Wildberries — Основной
Прогон:         #42 (28 мар, 14:32)               (link → run)
```

#### Секция 2: ExplanationBlock

Компонент `ExplanationBlock` — отображение `explanation_summary` с парсингом секций. Каждая секция `[Label]` отображается как отдельный визуальный блок.

**Визуальный формат для CHANGE decision:**

```
┌─ Explanation ──────────────────────────────────────────────────┐
│                                                                 │
│  [Решение]                                                     │
│  CHANGE: 4 500 ₽ → 3 890 ₽ (−13,6%)                          │
│  ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬ price bar visualization          │
│                                                                 │
│  [Политика]                                                    │
│  «Маржа 25% WB» (TARGET_MARGIN, v3)                link →     │
│                                                                 │
│  [Стратегия]                                                   │
│  target_margin = 25,0%                                         │
│  effective_cost_rate = 38,2%                                   │
│    commission  15,0%                                           │
│    logistics    8,2%                                           │
│    returns      5,0%                                           │
│    ads         10,0%                                           │
│  → raw target = 3 842 ₽                                       │
│                                                                 │
│  [Ограничения]                                                 │
│  ┌────────────────────────────────────────────┐                │
│  │ rounding (FLOOR, step=10)  3 842 → 3 840  │                │
│  │ min_price (3 890)          3 840 → 3 890  │                │
│  └────────────────────────────────────────────┘                │
│                                                                 │
│  [Guards]                                                      │
│  ✓ Все пройдены                                                │
│                                                                 │
│  [Режим]                                                       │
│  SEMI_AUTO → action PENDING_APPROVAL                           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Визуальный формат для SKIP decision:**

```
┌─ Explanation ──────────────────────────────────────────────────┐
│                                                                 │
│  [Решение]                                                     │
│  SKIP                                          ● Пропущено     │
│                                                                 │
│  [Причина]                                                     │
│  Данные старше 24 часов                                        │
│  (last sync: 30 мар, 08:15)                                   │
│                                                                 │
│  [Guard]                                                       │
│  ✗ stale_data_guard                                            │
│    last_success_at = 30 мар, 08:15                             │
│    threshold = 24 ч                                            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Визуальный формат для HOLD decision:**

```
┌─ Explanation ──────────────────────────────────────────────────┐
│                                                                 │
│  [Решение]                                                     │
│  HOLD                                          ● Ожидание      │
│                                                                 │
│  [Причина]                                                     │
│  Себестоимость не задана                                       │
│                                                                 │
│  [Политика]                                                    │
│  «Маржа 25% WB» (TARGET_MARGIN, v3) — requires COGS           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Стилизация ExplanationBlock:**

- Background: `--bg-secondary` (#F9FAFB)
- Border: 1px `--border-default`, border-radius `--radius-md`
- Padding: `--space-4` (16px)
- Section labels `[Решение]`, `[Политика]` etc: `--text-sm`, `--text-secondary`, uppercase, bold
- Price values: `font-family: JetBrains Mono`
- Constraint steps: mini-table with mono font, `--bg-tertiary` background
- Guard status: ✓ green for passed, ✗ red for blocked

#### Секция 3: Signal Snapshot

Таблица key-value — все собранные signals на момент решения.

| Signal | Label (RU) | Формат | Пример |
|--------|-----------|--------|--------|
| `current_price` | Текущая цена | Currency | `4 500 ₽` |
| `cogs` | Себестоимость (COGS) | Currency | `1 800 ₽` |
| `product_status` | Статус товара | Text | `ACTIVE` |
| `available_stock` | Доступный остаток | Number | `142` |
| `manual_lock` | Ручная блокировка | Boolean | `Нет` |
| `avg_commission_pct` | Комиссия (средняя) | Percent | `15,0%` |
| `avg_logistics_per_unit` | Логистика (средняя) | Currency | `350 ₽` |
| `return_rate_pct` | Процент возвратов | Percent | `5,2%` |
| `ad_cost_ratio` | Рекламная нагрузка | Percent | `10,0%` |
| `last_price_change_at` | Последнее изменение | Timestamp | `27 мар, 10:15` |
| `data_freshness` | Свежесть данных | Relative | `3 ч назад` |

Null-значения отображаются как «—» с hint (tooltip): «Данные отсутствуют».

#### Секция 4: Constraints Applied

Ordered list — каждый constraint, который изменил цену.

```
# | Ограничение           | До        | После
1 | rounding (FLOOR, 10)  | 3 842 ₽   | 3 840 ₽
2 | min_price (3 890)     | 3 840 ₽   | 3 890 ₽
```

Если constraints не изменили цену: «Ограничения не повлияли на расчётную цену.»

Визуально — compact table с mono font. Стрелки между «До» и «После».

#### Секция 5: Guards Evaluated

Ordered list — все guards, прошли или заблокировали.

```
# | Guard                | Результат | Детали
1 | margin_guard         | ✓ Пройден | margin 23.5% > min 15.0%
2 | frequency_guard      | ✓ Пройден | last change 3 дня назад > threshold 24 ч
3 | volatility_guard     | ✓ Пройден | 1 разворот за 7 дней < max 3
4 | stale_data_guard     | ✓ Пройден | данные 3 ч назад < threshold 24 ч
5 | promo_guard          | ✓ Пройден | товар не в промо
6 | stock_out_guard      | ✓ Пройден | остаток 142 > 0
```

Passed guards: ✓ green icon + text. Blocked guards: ✗ red icon + text + highlighted row.

#### Секция 6: Policy Snapshot

Collapsible section (по умолчанию свёрнут). При раскрытии — formatted JSON (read-only code block, `font-family: JetBrains Mono`, `--bg-secondary` background) с полным `policy_snapshot` JSONB.

### 8.11 Forms / inputs

Нет.

### 8.12 API endpoints

| Action | Method | Endpoint | Request | Response |
|--------|--------|----------|---------|----------|
| Get decision | GET | `/api/pricing/decisions/{decisionId}` | — | `DecisionDetailResponse` |

**DecisionDetailResponse** includes: all fields from `price_decision` table — `signal_snapshot`, `constraints_applied`, `guards_evaluated`, `explanation_summary`, `policy_snapshot`.

### 8.13 Empty / loading / error states

| Состояние | Отображение |
|-----------|-------------|
| Loading | Skeleton blocks for each section |
| Not found | Toast: «Решение не найдено». Redirect to decisions list |
| Corrupted explanation_summary | Fallback: show raw text without section parsing |

### 8.14 User flow scenarios

**Сценарий 1: Разбор причины пропуска товара**

1. Pricing manager ищет товар в Decisions List → находит последнее решение: SKIP
2. Кликает → Detail Panel открывается
3. ExplanationBlock: `[Причина] Себестоимость не задана`
4. Signal Snapshot: `cogs = —`
5. Понимает: нужно заполнить cost profile. Переходит к товару (ссылка «Открыть товар»)

**Сценарий 2: Проверка корректности расчёта цены**

1. Pricing manager видит неожиданно высокое снижение цены
2. Открывает Decision Detail → ExplanationBlock показывает формулу
3. Проверяет: effective_cost_rate = 38.2% — комиссия 15% + логистика 8.2% + returns 5% + ads 10%
4. Signal Snapshot: ad_cost_ratio = 10% — вау, рекламная нагрузка высокая
5. Решает: может стоит отключить include_ad_cost или пересмотреть рекламный бюджет

### 8.15 Edge cases

| Case | Поведение |
|------|----------|
| Decision with SIMULATED execution_mode | Banner: «Это симулированное решение. Реальная цена на маркетплейсе не изменялась.» (blue info banner) |
| policy_snapshot version differs from current policy version | Note in Policy Snapshot section: «Политика была обновлена после этого решения (текущая версия: v{current}, версия решения: v{decision}).» |
| signal_snapshot contains null signals | Display as «—» with tooltip explaining the null (e.g., «Нет данных о комиссии за период анализа») |

### 8.16 Accessibility

- ExplanationBlock sections: semantic headings (`h3` or `h4`) for each `[Label]`
- Code block (policy snapshot): `role="textbox"`, `aria-readonly="true"`
- Navigation links: standard `<a>` with `aria-label`
- Guard status icons: screen reader text «Пройден» / «Заблокирован»

---

## 9. Ручные блокировки цен (Manual Price Locks)

### 9.1 Route / URL

```
/workspace/:workspaceId/pricing/locks
```

### 9.2 Breadcrumbs

```
Ценообразование > Блокировки
```

### 9.3 Minimum role

- **Чтение:** Any role
- **Создание / удаление lock:** OPERATOR, PRICING_MANAGER, ADMIN, OWNER

### 9.4 Phase

C — Pricing

### 9.5 Layout zone

**Main Area** — таблица active locks + форма создания.

### 9.6 KPI strip

Нет.

### 9.7 Data grid

AG Grid, серверная пагинация.

**Колонки:**

| # | Column ID | Заголовок (RU) | Тип | Ширина | Align | Sortable | Описание |
|---|-----------|---------------|-----|--------|-------|----------|----------|
| 1 | `offer_name` | Товар | Text | 250px, flex | Left | ✓ | Offer name, кликабельное (link → offer detail) |
| 2 | `seller_sku` | Артикул | Text (mono) | 120px | Left | ✓ | — |
| 3 | `connection_name` | Подключение | Text | 160px | Left | ✓ | — |
| 4 | `locked_price` | Зафикс. цена | Currency (mono) | 120px | Right | ✓ | `4 500 ₽` |
| 5 | `reason` | Причина | Text | 200px, flex | Left | — | Truncated, full on hover (tooltip) |
| 6 | `locked_by_name` | Заблокировал | Text | 140px | Left | ✓ | User name |
| 7 | `locked_at` | Дата блокировки | Timestamp | 140px | Left | ✓ | «28 мар, 14:32» |
| 8 | `expires_at` | Истекает | Timestamp | 140px | Left | ✓ | «30 мар» или «Бессрочно» |
| 9 | `time_remaining` | Осталось | Duration/Text | 120px | Left | — | «2 дня 4 ч» / «Бессрочно» / highlight red if < 24h |
| 10 | `actions` | — | Action icon | 60px | Center | — | Unlock button |

**Default sort:** `locked_at DESC`
**Rows per page:** 50

**Note:** по умолчанию отображаются только **активные** locks (`unlocked_at IS NULL`). Нет фильтра для просмотра исторических (unlocked) — они доступны через Audit log.

### 9.8 Filter bar

| Filter | Тип | Значения | Default |
|--------|-----|----------|---------|
| Подключение | Single-select dropdown | All connections | Все |
| Товар | Search input (autocomplete) | By name/sku | — |
| Истечение | Single-select | Все, Истекает скоро (< 24ч), Бессрочные | Все |

### 9.9 Actions / buttons

**Toolbar:**

| Кнопка | Тип | Label (RU) | Иконка | Условие |
|--------|-----|-----------|--------|---------|
| Create lock | Primary | Заблокировать цену | `lucide:lock` | OPERATOR+ |

**Per-row:**

| Action | Иконка | Tooltip (RU) | Условие |
|--------|--------|-------------|---------|
| Unlock | `lucide:unlock` | Разблокировать | OPERATOR+ |

**Unlock confirmation:** «Разблокировать цену для "{offer_name}"? Товар снова будет участвовать в автоматическом ценообразовании при следующем прогоне.» → [Отмена] [Разблокировать (primary)]

### 9.10 Detail panel

Клик по строке → Detail Panel с полной информацией о lock.

**Содержимое:**

```
Блокировка цены

Товар:              Кроссовки Nike Air Max 90      (link → offer)
Артикул:            NIKE-AM90-42
Подключение:        Wildberries — Основной

Зафиксированная цена:  4 500 ₽
Причина:            Акция магазина, ручная цена до конца недели

Заблокировал:       Иван Петров
Дата блокировки:    28 мар, 14:32
Истекает:           30 мар, 23:59  (через 2 дня 4 ч)

[Разблокировать]
```

### 9.11 Forms / inputs

**Форма «Заблокировать цену»** — отображается как inline panel (раскрывается над таблицей, push down), или как modal.

| # | Field ID | Label (RU) | Input type | Validation | Default |
|---|----------|-----------|------------|------------|---------|
| 1 | `marketplaceOfferId` | Товар | Dropdown with search (autocomplete) | Required | — |
| 2 | `lockedPrice` | Зафиксированная цена (₽) | Number input (mono) | Required, > 0 | Предзаполняется текущей ценой выбранного offer |
| 3 | `reason` | Причина блокировки | Textarea (3 rows) | Optional, max 500 chars | — |
| 4 | `expiresAt` | Истекает | Date-time picker | Optional (null = бессрочно) | — |

**Offer search dropdown:** поиск по названию, артикулу, баркоду. При выборе — поле `lockedPrice` заполняется текущей ценой offer (с возможностью изменения).

**Checkbox/toggle:** «Бессрочная блокировка» — если вкл, `expiresAt` picker скрывается.

**Кнопки формы:** [Заблокировать (primary)] [Отмена (ghost)]

### 9.12 API endpoints

| Action | Method | Endpoint | Request | Response |
|--------|--------|----------|---------|----------|
| List active locks | GET | `/api/pricing/locks?connectionId={}&marketplaceOfferId={}&page={}&size={}&sort={}` | Query | `Page<ManualPriceLockResponse>` |
| Create lock | POST | `/api/pricing/locks` | `{ marketplaceOfferId, lockedPrice, reason, expiresAt }` | `201 ManualPriceLockResponse` |
| Unlock | DELETE | `/api/pricing/locks/{lockId}` | — | `204` |
| Offer search (for dropdown) | GET | `/api/offers?search={}&connectionId={}&page=0&size=20` | Query | `Page<OfferSummary>` |

### 9.13 Empty / loading / error states

| Состояние | Отображение |
|-----------|-------------|
| Загрузка | Skeleton grid |
| Нет блокировок | «Нет активных блокировок цен. Все товары участвуют в автоматическом ценообразовании.» |
| Duplicate lock (409) | Toast: «Этот товар уже заблокирован. Разблокируйте текущую блокировку, чтобы создать новую.» |
| Offer not found | Search dropdown: «Товар не найден» |
| Lock creation failed | Toast error: «Не удалось заблокировать цену. Попробуйте снова.» |

### 9.14 User flow scenarios

**Сценарий 1: Оператор блокирует цену на время акции магазина**

1. Оператор знает, что на конкретный товар запланирована внутренняя акция — цена не должна меняться
2. Нажимает «Заблокировать цену»
3. Ищет товар по артикулу → выбирает
4. Цена предзаполняется (4 500 ₽) — оставляет как есть
5. Причина: «Внутренняя акция до 30 марта»
6. Снимает «Бессрочная блокировка», выбирает дату 30 марта, 23:59
7. Нажимает «Заблокировать» → lock создан, toast «Цена заблокирована»
8. При следующем pricing run этот товар будет пропущен (SKIP, reason = MANUAL_LOCK)

**Сценарий 2: Pricing manager разблокирует просроченную блокировку**

1. Pricing manager видит блокировку с истекшим сроком (expired locks автоматически снимаются scheduled job, но пользователь может снять вручную раньше)
2. Нажимает 🔓 (unlock) → confirmation → lock снят
3. Товар снова участвует в ценообразовании

**Сценарий 3: Блокировка из Operational Grid (Seller Operations)**

1. Оператор работает в Operational Grid → видит товар
2. Двойной клик на ячейку `manual_lock` (toggle) → переключает lock
3. При включении → modal с формой: цена (предзаполнена), причина, срок
4. Это взаимодействие делегировано в тот же `POST /api/pricing/locks` endpoint

### 9.15 Edge cases

| Case | Поведение |
|------|----------|
| Lock already active for this offer | Server 409 → toast с предложением разблокировать текущую |
| Offer belongs to disabled connection | Разрешено — lock действителен, даже если connection неактивно |
| Lock expired by scheduled job | Lock автоматически снимается (unlocked_at set, unlocked_by = NULL → system). Строка исчезает из active locks list |
| Lock price = 0 or negative | Client validation error: «Цена должна быть больше нуля» |
| Very long reason text | Truncated in grid cell (200px). Full text in tooltip + Detail Panel |

### 9.16 Accessibility

- Offer search: `combobox` ARIA role
- Lock/unlock buttons: `aria-label` = «Заблокировать цену для {offer}» / «Разблокировать цену для {offer}»
- Expiration warning (< 24h): `aria-label` includes «Истекает скоро»
- Date-time picker: keyboard navigable

---

## Общие компоненты модуля Pricing

### ExplanationBlock component

Переиспользуемый Angular-компонент для отображения `explanation_summary` из `price_decision`.

**Input:** `explanationSummary: string` (raw text from API)

**Parsing logic:**
1. Split by newline
2. Lines starting with `[Label]` → section header
3. Content lines → section body
4. Apply formatting: numbers → mono font, prices → currency format, percentages → colored

**Sections rendering:**

| Section | Visual treatment |
|---------|-----------------|
| `[Решение]` | Prominent: large text, decision badge, price change visualization (bar) |
| `[Политика]` | Policy name as link, version badge |
| `[Стратегия]` | Key-value pairs with indentation for cost rate breakdown |
| `[Ограничения]` | Mini-table: constraint name, from→to prices |
| `[Guards]` | List with ✓/✗ icons, green/red coloring |
| `[Причина]` | Highlighted text block (yellow background for attention) |
| `[Режим]` | Execution mode badge + action status badge |

### Decision type badge component

| Decision | Badge text (RU) | Color | Icon |
|----------|----------------|-------|------|
| `CHANGE` | Изменение | `--status-success` bg | `lucide:arrow-right-left` |
| `SKIP` | Пропуск | `--status-warning` bg | `lucide:skip-forward` |
| `HOLD` | Ожидание | `--status-neutral` bg | `lucide:pause` |

### Price change visualization

Inline в ExplanationBlock и в grid cells. Compact horizontal bar:

```
[████████████░░░░░░░] 4 500 → 3 890 (−13,6%)
 ←── old price ──→ ←── target ──→
```

- Green bar section if price increases
- Red bar section if price decreases
- Gray for unchanged

---

## WebSocket real-time updates

### Pricing module subscriptions

| STOMP destination | Trigger | Data | Used on screen |
|-------------------|---------|------|----------------|
| `/topic/workspace/{id}/pricing-runs` | Run status change | `{ runId, status, counts }` | Runs List (§5): auto-update status badge and counts |
| `/topic/workspace/{id}/pricing-decisions` | Batch of decisions created | `{ runId, batchSize }` | Runs List: update total counts in real-time |

### Behavior

- **Runs List:** when a run status changes (PENDING → IN_PROGRESS → COMPLETED), the row updates in place. No full page reload. Spinner on IN_PROGRESS badge.
- **Decisions List:** not real-time updated (too many events). User refreshes manually or navigates away and back.
- **Locks:** updated on create/delete via standard API response, no WebSocket needed (low frequency).

---

## Permission matrix (pricing-specific)

| Action | VIEWER | ANALYST | OPERATOR | PRICING_MANAGER | ADMIN | OWNER |
|--------|--------|---------|----------|-----------------|-------|-------|
| View policies | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Create/Edit/Activate/Pause/Archive policy | — | — | — | ✓ | ✓ | ✓ |
| View assignments | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Add/Delete assignments | — | — | — | ✓ | ✓ | ✓ |
| Run impact preview | — | — | — | ✓ | ✓ | ✓ |
| View pricing runs | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Trigger manual run | — | — | — | ✓ | ✓ | ✓ |
| View decisions | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| View locks | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Create/Delete locks | — | — | ✓ | ✓ | ✓ | ✓ |
| Export CSV (any screen) | — | ✓ | ✓ | ✓ | ✓ | ✓ |

**UI behavior for insufficient permissions:**
- Write action buttons **not rendered** (not disabled, not shown). User with VIEWER role sees a clean read-only table without action columns.
- If user tries to navigate to `/policies/new` directly (URL) with insufficient role → redirect to list + toast error.

---

## Command Palette (Ctrl+K) — Pricing entities

| Entity type | Search fields | Display format | Action |
|-------------|---------------|----------------|--------|
| Policy | name | `📋 {name} — {strategy_type}` | Navigate to policy edit |
| Pricing run | id, connection name | `▶ Прогон #{id} — {connection}` | Navigate to run detail |
| Lock | offer name, seller_sku | `🔒 {offer_name} ({seller_sku})` | Navigate to lock in locks list |

---

## Related documents

- [Frontend Design Direction](frontend-design-direction.md) — design system, components, patterns
- [Pricing module](../modules/pricing.md) — policies, strategies, decisions, signals, guards, REST API
- [Tenancy & IAM](../modules/tenancy-iam.md) — roles, permission matrix
- [Execution module](../modules/execution.md) — action lifecycle after pricing decision
- [Seller Operations](../modules/seller-operations.md) — operational grid, manual_lock toggle integration
