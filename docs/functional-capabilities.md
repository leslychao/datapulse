# Datapulse — Функциональные возможности

## Карта возможностей

Каждая группа описывает обязательное поведение системы. Приоритет и фазировка — [Видение и границы](project-vision-and-scope.md).

## 1. Загрузка и нормализация данных маркетплейсов

### Назначение

Загрузка данных из API Wildberries и Ozon в единую каноническую модель. Поддержка нескольких кабинетов одного продавца с изоляцией сбоев по маркетплейсам.

### Data domains

| Domain | Содержание |
|--------|------------|
| Каталог | Товары, SKU, бренды, категории, статусы |
| Цены | Текущие цены, скидки, ценовые иерархии |
| Остатки | По складам (FBO/FBS/seller), доступные и зарезервированные |
| Заказы | Отправления FBO/FBS, статусы |
| Продажи | Фактические продажи с привязкой к заказам |
| Возвраты | Возвраты, невыкупы, причины |
| Финансы | Транзакции, комиссии, логистика, компенсации, штрафы |
| Промо | Акции маркетплейсов, участие товаров |
| Реклама | Кампании, статистика (показы, клики, расход) |

### Обязательные свойства

- Идемпотентная загрузка (дедупликация по SHA-256 record key).
- Rate limit handling с retry и backoff.
- Lane isolation: сбой одного маркетплейса не блокирует другой.
- Сохранение исходных payload для traceability (raw layer).
- Валидация credentials перед началом синхронизации.
- Строго последовательный pipeline: raw → normalized → canonical → analytics.

## 2. P&L и unit economics

### Назначение

Правдивая, сверяемая прибыльность по SKU, категории, кабинету, маркетплейсу и периоду.

### Обязательные свойства

- Полная формула P&L: revenue − комиссии − логистика − хранение − штрафы − реклама − COGS − возвраты + компенсации − прочие удержания + reconciliation_residual.
- Reconciliation residual: явно отслеживается как мера расхождения между выплатой и суммой компонентов.
- COGS по SCD2: себестоимость привязана к моменту продажи.
- Drill-down от P&L до отдельных финансовых записей с provenance.
- Advertising allocation: пропорциональная аллокация рекламных расходов по revenue share.

Детали формулы и материализации — [Архитектура данных](data-architecture.md).

## 3. Аналитика остатков

### Назначение

Оценка остатков с точки зрения бизнес-рисков.

### Capabilities

| Capability | Вход | Выход |
|------------|------|-------|
| Days of cover | Доступные остатки, продажи за N дней | Число дней до stock-out |
| Stock-out risk | Days of cover, lead time | Уровень: critical / warning / normal |
| Overstock / frozen capital | Days of cover, себестоимость | Сумма замороженного капитала |
| Replenishment signal | Stock-out risk, velocity | Рекомендуемый объём пополнения |

- Все расчёты выполняются отдельно по типам складов (FBO/FBS/seller).
- Velocity — скользящее среднее продаж за настраиваемый период (default: 14 дней).
- SKU без продаж за период: velocity = 0, отдельная маркировка (no-sales / new product).

### Алгоритмы

- Days of cover: `available / avg_daily_sales(N)`.
- Stock-out risk: threshold-based — critical если `days_of_cover < lead_time`, warning если `< 2× lead_time`.
- Frozen capital: `excess_qty × cost_price`, где `excess_qty = available − (avg_daily_sales × target_days_of_cover)`.
- Replenishment: `recommended_qty = avg_daily_sales(N) × target_days_of_cover − available`.

## 4. Возвраты и штрафы

### Назначение

Агрегация финансовых потерь по возвратам, невыкупам и штрафам.

### Capabilities

- Агрегация потерь по: категории возврата, SKU, периоду, маркетплейсу.
- Drill-down до evidence (конкретные записи, ссылки на raw source).
- Trend analysis: динамика return rate.
- Breakdown штрафов: по типам (штрафы, хранение, приёмка, прочее).

## 5. Ценообразование

### Назначение

Объяснимый repricing с полным audit trail.

### Pipeline

```
Eligibility → Signal Assembly → Strategy Evaluation → Constraint Resolution → Guard Pipeline → Decision → Explanation → Action Scheduling
```

### Обязательные свойства

- Каждое решение содержит explanation (почему именно эта цена).
- Решения строятся только на канонической модели (decision-grade reads).
- Manual approval обязателен до включения auto-execution.
- Guard pipeline блокирует опасные решения (margin guard, frequency guard, volatility guard).
- Конфигурация политик per SKU / категория / маркетплейс (трёхуровневая специфичность).
- Все decisions (включая SKIP) сохраняются для аудита и объяснимости.

### Этапы pipeline

| Этап | Ответственность |
|------|-----------------|
| Eligibility | Определяет, подлежит ли SKU автоматическому ценообразованию (active policy, active product, no lock, COGS exists) |
| Signal Assembly | Собирает входные сигналы из canonical state (PostgreSQL) и derived signals (ClickHouse) через signal assembler |
| Strategy Evaluation | Вычисляет raw target price на основе сигналов и конфигурации стратегии |
| Constraint Resolution | Корректирует target price: min/max price, max change %, min margin floor, marketplace limits, rounding |
| Guard Pipeline | Блокирующие проверки: margin, frequency, volatility, stale data, promo, manual lock, stock-out |
| Decision | Финальное решение: CHANGE / SKIP / HOLD |
| Explanation | Формирует человекочитаемое объяснение решения с указанием source каждого input |
| Action Scheduling | Создаёт action intent для execution layer (PENDING_APPROVAL или APPROVED в зависимости от execution mode) |

Детали pipeline, стратегий, constraints, guards — [Архитектура ценообразования](pricing-architecture-analysis.md).

## 6. Контролируемое исполнение

### Назначение

Lifecycle внешних действий (price changes) с гарантиями доставки и подтверждения результата.

### Lifecycle

```
PENDING_APPROVAL → APPROVED → SCHEDULED → EXECUTING → RECONCILIATION_PENDING → SUCCEEDED / FAILED
    ↓                ↕           ↓
  EXPIRED         ON_HOLD    CANCELLED
                    ↓
                 CANCELLED
```

### Обязательные свойства

- DB-first: action intent фиксируется в PostgreSQL до внешнего вызова.
- Transactional outbox: доставка через outbox → брокер → worker.
- Retry с exponential backoff.
- SUCCEEDED означает только подтверждённый результат (reconciliation re-read).
- CAS guards на все state transitions.
- Manual override: approval, hold (ON_HOLD), cancel (CANCELLED), retry, manual reconciliation.
- Approval timeout: PENDING_APPROVAL → EXPIRED при превышении configurable timeout.

Детали lifecycle, retry, reconciliation — [Исполнение и сверка](execution-and-reconciliation.md).

## 7. Контроль качества данных

### Назначение

Защита бизнес-решений от некачественных данных.

### Controls

| Control | Описание |
|---------|----------|
| Stale data detection | Устаревшие синхронизации: freshness > threshold |
| Missing sync detection | Полностью пропущенные синхронизации |
| Spike detection | Аномальные всплески в финансовых/inventory метриках |
| Mismatch detection | Расхождения между связанными data domains (заказы vs финансы) |
| Residual tracking | Reconciliation residual за пределами порога |
| Automation blocker | Блокировка автоматического ценообразования при broken truth |

## 8. Операционный слой (Seller Operations)

### Назначение

Ежедневный рабочий инструмент продавца. Обязательный capability, не опциональный.

### Компоненты

| Компонент | Описание |
|-----------|----------|
| Operational Grid | Мастер-таблица SKU: цена, маржа, velocity, остатки, алерты |
| Saved Views | Персональные пресеты фильтров и сортировок |
| Working Queues | Очереди задач: «требует внимания», «ожидает решения», «в процессе» |
| Price Journal | История всех ценовых решений и действий |
| Promo Journal | История участия в промо-акциях и результатов |
| Mismatch Monitor | Визуализация расхождений между data domains |

### Обязательные свойства

- Server-side filtering, sorting, pagination — dedicated read models.
- Data freshness indicators на гриде (last sync time, stale markers).
- Working queue assignment не блокирует другие операции.

## 9. Аудит и объяснимость

### Назначение

Полная прослеживаемость данных и решений.

| Аспект | Требование |
|--------|------------|
| Data provenance | Каждая каноническая запись прослеживаема до raw source |
| Decision audit | Каждое pricing decision сохраняется с inputs, constraints, explanation |
| Action audit | Каждый external action — с attempts, outcomes, timing |
| Credential audit | Все попытки доступа к credentials аудируются |
| User audit | Действия пользователей в системе аудируются |

## 10. Симулированное исполнение

### Назначение

Полный pricing pipeline без реальной записи в маркетплейс.

### Обязательные свойства

- Проходит весь pipeline: eligibility → decision → explanation → action scheduling.
- Simulated gateway вместо реального API маркетплейса.
- Shadow-state: результаты хранятся отдельно от канонической модели.
- Изоляция: simulated mode не мутирует каноническую модель.
- Parity tests: верификация идентичности шагов simulated и live pipeline.
- Execution mode tracking: каждый action помечен `execution_mode = SIMULATED / LIVE`.

## Ключевые пользовательские сценарии

### SC-1: Онбординг

Создание workspace → подключение кабинета WB/Ozon → валидация credentials → видимость статуса синхронизации и доступных capabilities.

### SC-2: Ежедневная операционная работа

Operational Grid → saved view / working queue → фильтрация и сортировка SKU → просмотр explanation → принятие решения или hold.

### SC-3: Ценообразование

Рекомендованная цена с объяснением → manual approval или auto-action → отслеживание execution и reconciliation.

### SC-4: Расследование прибыльности

P&L → breakdown (комиссии, логистика, штрафы, реклама, COGS) → mismatch / residual investigation.

### SC-5: Управление остатками

Stock-out risk / overstock → days of cover → решение по replenishment и pricing.

### SC-6: Анализ результата действия

Price Journal → что изменено → применилось ли → какой эффект.

## Связанные документы

- [Видение и границы](project-vision-and-scope.md) — фазы поставки, scope
- [Архитектура данных](data-architecture.md) — scope Phase A/B, инварианты, полная модель данных
- [Архитектура данных](data-architecture.md) — P&L formula, data layers
- [Исполнение и сверка](execution-and-reconciliation.md) — action lifecycle
- [Нефункциональная архитектура](non-functional-architecture.md) — performance, resilience для capabilities
