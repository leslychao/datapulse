# Модуль: Seller Operations

**Фаза:** E — Seller Operations
**Зависимости:** [Analytics & P&L](analytics-pnl.md), [Pricing](pricing.md), [Execution](execution.md)
**Runtime:** datapulse-api

---

## Назначение

Ежедневный рабочий инструмент продавца. Обязательный capability, не опциональный. Обеспечивает операционный контур: наблюдение, принятие решений, контроль исполнения.

## Компоненты


| Компонент        | Описание                                                           |
| ---------------- | ------------------------------------------------------------------ |
| Operational Grid | Мастер-таблица по marketplace_offer: цена, маржа, velocity, остатки, алерты |
| Saved Views      | Персональные пресеты фильтров и сортировок                         |
| Working Queues   | Очереди задач: «требует внимания», «ожидает решения», «в процессе» |
| Price Journal    | История всех ценовых решений и действий                            |
| Promo Journal    | История участия в промо-акциях и результатов                       |
| Mismatch Monitor | Визуализация расхождений между data domains                        |


### Grain

Каждая строка operational grid = один `marketplace_offer` (конкретное предложение конкретного товара на конкретном маркетплейсе через конкретное подключение).

Один `seller_sku` с предложениями на WB и Ozon → **две строки** в гриде. Столбец «Маркетплейс» + «Подключение» — обязательные visible columns.

Группировка по `seller_sku` доступна через saved view с group-by.

## Модель данных

### Таблицы PostgreSQL


| Таблица                    | Назначение                                                                         |
| -------------------------- | ---------------------------------------------------------------------------------- |
| `saved_view`               | Персональные пресеты фильтров и сортировок для operational grid                    |
| `working_queue_definition` | Правила очереди: filter criteria, название, тип                                    |
| `working_queue_assignment` | Назначение элемента в очереди: entity_type, entity_id, assigned_to_user_id, status |


## Обязательные свойства

- Server-side filtering, sorting, pagination — dedicated read models.
- Data freshness indicators на гриде (last sync time, stale markers).
- Working queue assignment не блокирует другие операции.
- Dynamic sorting через whitelist (DTO field → SQL column mapping; SQL injection prevention).

## Пользовательские сценарии

### SC-2: Ежедневная операционная работа

Operational Grid → saved view / working queue → фильтрация и сортировка SKU → просмотр explanation → принятие решения или hold.

### SC-4: Расследование прибыльности

P&L → breakdown (комиссии, логистика, штрафы, реклама, COGS) → mismatch / residual investigation.

### SC-5: Управление остатками

Stock-out risk / overstock → days of cover → решение по replenishment и pricing.

### SC-6: Анализ результата действия

Price Journal → что изменено → применилось ли → какой эффект.

## Performance


| Требование                                 | Обоснование                                                     |
| ------------------------------------------ | --------------------------------------------------------------- |
| Server-side filtering, sorting, pagination | Клиент не загружает полный dataset                              |
| Dedicated read models                      | Операционные screens не читают из write-оптимизированных таблиц |
| Dynamic sorting через whitelist            | DTO field → SQL column mapping; SQL injection prevention        |


### Запрещённые anti-patterns


| Anti-pattern                   | Причина                                                     |
| ------------------------------ | ----------------------------------------------------------- |
| Wrong-store reads              | Аналитика из PostgreSQL вместо ClickHouse — performance bug |
| N+1 queries                    | Lazy loading без batch fetch                                |
| Full table scans на hot tables | Отсутствие index на claim/filter columns                    |
| Client-side pagination         | Загрузка полного dataset на клиент                          |


## Связанные модули

- [Tenancy & IAM](tenancy-iam.md) — saved views и queues per workspace; role-based access
- [Analytics & P&L](analytics-pnl.md) — данные для grid (P&L, inventory, returns)
- [Pricing](pricing.md) — price journal, recommendations
- [Execution](execution.md) — action status, failed action queues

