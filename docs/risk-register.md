# Datapulse — Реестр рисков

## Активные риски

### R-01: Ломающие изменения API маркетплейсов

| Параметр | Значение |
|----------|----------|
| Риск | WB или Ozon депрекейтят или меняют endpoints без предупреждения |
| Вероятность | Высокая (наблюдалось: Ozon v2→v3/v4/v5 отключения; WB deprecated endpoints) |
| Влияние | Высокое — pipeline загрузки ломается, данные перестают обновляться |
| Митигация | Capability-based adapter boundary изолирует изменения; `@JsonIgnoreProperties(ignoreUnknown = true)` на provider DTO; версии endpoints зафиксированы в матрице провайдеров |
| Detection | Рост ошибок integration call rates; HTTP 404/410 от провайдера |

### R-02: Rate limiting маркетплейсов

| Параметр | Значение |
|----------|----------|
| Риск | API маркетплейсов throttle-ят запросы, вызывая неполную загрузку |
| Вероятность | Высокая (WB: 1 req/min для statistics; Ozon rate limits не документированы) |
| Влияние | Среднее — устаревание данных, отложенная синхронизация |
| Митигация | Token-bucket rate limiting в адаптерах; DLX-based delayed retry; lane isolation |
| Detection | Provider throttling rates в метриках; accumulation RETRY_SCHEDULED в job_item |

### R-03: Single-point-of-failure

| Параметр | Значение |
|----------|----------|
| Риск | Единственный инстанс runtime entrypoint — сбой останавливает обработку |
| Вероятность | Средняя |
| Влияние | Высокое — недоступность сервиса |
| Митигация | Outbox-паттерн: нет потери сообщений при рестарте; CAS-guards: нет дублирования при рестарте; раздельные runtime entrypoints позволяют независимый масштаб |
| Detection | Health check failure; отсутствие heartbeat |

### R-04: Корректность финансовых расчётов

| Параметр | Значение |
|----------|----------|
| Риск | Ошибки в P&L при новых паттернах данных; несовпадение sign conventions |
| Вероятность | Средняя |
| Влияние | Высокое — некорректные финансовые данные подрывают ключевое ценностное предложение |
| Митигация | Sign conventions эмпирически подтверждены; reconciliation residual отслеживается явно; golden datasets для валидации |
| Detection | Reconciliation residual > threshold; аномалии в fact_finance |

### R-05: Масштабируемость обновления витрин

| Параметр | Значение |
|----------|----------|
| Риск | Полный UPSERT витрин по аккаунту замедляется с ростом данных |
| Вероятность | Средняя (приемлемо при единицах–десятках аккаунтов) |
| Влияние | Среднее — увеличение времени ETL, устаревание аналитики |
| Митигация | `IS DISTINCT FROM` минимизирует churn; при масштабировании — партиционирование или инкрементальное обновление |
| Detection | Рост sync duration; mart freshness degradation |

### R-06: WB Returns endpoint заблокирован

| Параметр | Значение |
|----------|----------|
| Риск | Dedicated endpoint возвратов WB возвращает 400; требует Analytics-scoped токен |
| Вероятность | Подтверждён (эмпирически) |
| Влияние | Низкое — возвраты WB извлекаются из sales endpoint и finance report |
| Митигация | Альтернативные источники покрывают пробел |
| Detection | — (известное ограничение, не runtime failure) |

### R-07: Безопасность учётных данных

| Параметр | Значение |
|----------|----------|
| Риск | API-ключи маркетплейсов раскрыты в конфигурациях или логах |
| Вероятность | Низкая (Vault integration) |
| Влияние | Высокое — компрометация аккаунтов маркетплейсов |
| Митигация | Vault для production credentials; metadata и secret material разделены; маскирование в логах; credential audit |
| Detection | Credential audit alerts; unexpected API usage от провайдера |

### R-08: Ложный SUCCEEDED (premature terminal success)

| Параметр | Значение |
|----------|----------|
| Риск | Price action помечается SUCCEEDED до подтверждения эффекта маркетплейсом |
| Вероятность | Средняя (при некорректной реализации) |
| Влияние | Высокое — подрыв доверия к системе |
| Митигация | SUCCEEDED = confirmed final effect only; uncertain outcomes → RECONCILIATION_PENDING; обязательные flow tests |
| Detection | Mismatch между action intent и фактическим состоянием провайдера |

### R-09: Stale truth блокирует автоматизацию

| Параметр | Значение |
|----------|----------|
| Риск | Stale или некорректные данные приводят к ошибочным pricing decisions |
| Вероятность | Средняя |
| Влияние | Среднее — продукт не работает или принимает ошибочные решения |
| Митигация | Data quality controls: stale data guards, missing sync detection, spike detection; stale truth блокирует automated price actions |
| Detection | Stale data alerts; guard hit rates; automation blocker events |

### R-10: Несогласованность форматов timestamps

| Параметр | Значение |
|----------|----------|
| Риск | API маркетплейсов используют несогласованные timestamp formats |
| Вероятность | Подтверждён (WB: dual-format; Ozon финансы: не ISO 8601) |
| Влияние | Среднее — ошибки парсинга, некорректная привязка к датам |
| Митигация | Адаптеры реализуют flexible parser; форматы зафиксированы в архитектуре данных |
| Detection | Parse errors в логах адаптеров |

### R-11: Simulated mode загрязняет каноническую модель

| Параметр | Значение |
|----------|----------|
| Риск | Simulated execution ошибочно мутирует авторитетные данные |
| Вероятность | Низкая (при корректной реализации) |
| Влияние | Высокое — потеря целостности канонической модели |
| Митигация | Отдельная таблица `simulated_offer_state`; simulated mode не имеет права мутировать каноническую модель; parity tests; execution_mode tracking |
| Detection | Parity test failures; integrity checks |

### R-12: Расползание бизнес-логики в common/infrastructure

| Параметр | Значение |
|----------|----------|
| Риск | Бизнесовые enums, pricing logic, domain exceptions попадают в common или инфраструктурные слои |
| Вероятность | Средняя (при росте команды) |
| Влияние | Среднее — усложнение сопровождения, нарушение ownership |
| Митигация | ArchUnit + Maven Enforcer в CI; правило: common = only technical primitives |
| Detection | CI build failure при нарушении boundary |

### R-13: P&L без рекламных расходов (Phase A/B/C)

| Параметр | Значение |
|----------|----------|
| Риск | P&L завышает прибыль на сумму рекламных расходов. Для крупных рекламодателей (>10% от revenue) — значимая погрешность |
| Вероятность | Подтверждён (Phase G scope) |
| Влияние | Среднее — P&L структурно корректен, но неполный по содержанию |
| Митигация | advertising_cost в формуле = 0 (explicit). UI предупреждение "Рекламные расходы не подключены". Phase G добавит ads ingestion. Ретроактивный пересчёт marts возможен |
| Detection | N/A — known gap, не runtime failure |

### R-14: WB Incomes API deprecation (June 2026)

| Параметр | Значение |
|----------|----------|
| Риск | Endpoint `/api/v1/supplier/incomes` отключается June 2026. fact_supply для WB FBO перестанет заполняться |
| Вероятность | Высокая (confirmed deprecation timeline) |
| Влияние | Низкое — fact_supply не участвует в P&L; влияет только на inventory lead time (Phase G) |
| Митигация | FBS: `/api/v3/supplies` (Marketplace API). FBO: manual import, inventory delta analysis. Задокументировано в wb-read-contracts.md §8 |
| Detection | HTTP 410 от `/api/v1/supplier/incomes` |

### R-15: Standalone operations allocation accuracy

| Параметр | Значение |
|----------|----------|
| Риск | Pro-rata revenue share allocation для storage/fixed costs не отражает реальное распределение расходов по товарам |
| Вероятность | Средняя |
| Влияние | Низкое — влияет только на mart_product_pnl, не на mart_posting_pnl и account-level totals |
| Митигация | mart_posting_pnl не содержит allocated costs (чистый). mart_product_pnl помечает allocated amounts. Volume-weighted allocation — Phase G extension |
| Detection | Seller reports allocation не соответствует ожиданиям; UI feedback |

## Сводка рисков

| Риск | Вероятность | Влияние | Приоритет |
|------|-------------|---------|-----------|
| R-01 Ломающие изменения API | Высокая | Высокое | **Критический** |
| R-02 Rate limiting | Высокая | Среднее | Высокий |
| R-03 Single-point-of-failure | Средняя | Высокое | Высокий |
| R-04 Корректность финансовых расчётов | Средняя | Высокое | Высокий |
| R-05 Масштабируемость витрин | Средняя | Среднее | Средний |
| R-06 WB Returns заблокирован | Подтверждён | Низкое | Низкий |
| R-07 Безопасность credentials | Низкая | Высокое | Средний |
| R-08 Ложный SUCCEEDED | Средняя | Высокое | Высокий |
| R-09 Stale truth блокирует automation | Средняя | Среднее | Средний |
| R-10 Форматы timestamps | Подтверждён | Среднее | Средний |
| R-11 Simulated mode загрязняет truth | Низкая | Высокое | Средний |
| R-12 Расползание логики в common | Средняя | Среднее | Средний |
| R-13 P&L без рекламных расходов | Подтверждён | Среднее | Средний |
| R-14 WB Incomes API deprecation (June 2026) | Высокая | Низкое | Низкий |
| R-15 Standalone operations allocation accuracy | Средняя | Низкое | Низкий |

## Связанные документы

- [Архитектура данных](data-architecture.md) — инварианты, scope Phase A/B
- [Исполнение и сверка](execution-and-reconciliation.md) — action lifecycle, SUCCEEDED criteria
- [Матрица возможностей провайдеров](provider-capability-matrix.md) — provider blockers, rate limits
- [Нефункциональная архитектура](non-functional-architecture.md) — resilience, security measures
- [Архитектурная санация P&L](pnl-architecture-sanitation.md) — Q1-Q7, sanitation decisions
