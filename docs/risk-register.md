# Datapulse — Реестр рисков

## Активные риски

### R-01: Ломающие изменения API маркетплейсов


| Параметр    | Значение                                                                                                                                                                   |
| ----------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Риск        | WB или Ozon депрекейтят или меняют endpoints без предупреждения                                                                                                            |
| Вероятность | Высокая (наблюдалось: Ozon v2→v3/v4/v5 отключения; WB deprecated endpoints)                                                                                                |
| Влияние     | Высокое — pipeline загрузки ломается, данные перестают обновляться                                                                                                         |
| Митигация   | Capability-based adapter boundary изолирует изменения; `@JsonIgnoreProperties(ignoreUnknown = true)` на provider DTO; версии endpoints зафиксированы в матрице провайдеров |
| Detection   | Рост ошибок integration call rates; HTTP 404/410 от провайдера                                                                                                             |


### R-02: Rate limiting маркетплейсов


| Параметр    | Значение                                                                        |
| ----------- | ------------------------------------------------------------------------------- |
| Риск        | API маркетплейсов throttle-ят запросы, вызывая неполную загрузку                |
| Вероятность | Высокая (WB: 1 req/min для statistics; Ozon rate limits не документированы)     |
| Влияние     | Среднее — устаревание данных, отложенная синхронизация                          |
| Митигация   | Redis-based token bucket per (connection, rate_limit_group) с cross-runtime координацией (ETL + Execution); AIMD adaptive rate для unknown лимитов; per-product counter (Ozon 10/hour); Retry-After header support; DLX-based delayed retry; lane isolation. Детали: [Integration §Rate limiting](modules/integration.md#rate-limiting) |
| Detection   | `marketplace_rate_limit_throttled_total` (429 count), `marketplace_rate_limit_wait_seconds` (p95 > 60s), `RateLimitThrottlingSustained` alert; accumulation RETRY_SCHEDULED в job_item |


### R-03: Single-point-of-failure


| Параметр    | Значение                                                                                                                                                   |
| ----------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Риск        | Единственный инстанс runtime entrypoint — сбой останавливает обработку                                                                                     |
| Вероятность | Средняя                                                                                                                                                    |
| Влияние     | Высокое — недоступность сервиса                                                                                                                            |
| Митигация   | Outbox-паттерн: нет потери сообщений при рестарте; CAS-guards: нет дублирования при рестарте; раздельные runtime entrypoints позволяют независимый масштаб |
| Detection   | Health check failure; отсутствие heartbeat                                                                                                                 |


### R-04: Корректность финансовых расчётов


| Параметр    | Значение                                                                                                             |
| ----------- | -------------------------------------------------------------------------------------------------------------------- |
| Риск        | Ошибки в P&L при новых паттернах данных; несовпадение sign conventions                                               |
| Вероятность | Средняя                                                                                                              |
| Влияние     | Высокое — некорректные финансовые данные подрывают ключевое ценностное предложение                                   |
| Митигация   | Sign conventions эмпирически подтверждены; reconciliation residual отслеживается явно; golden datasets для валидации |
| Detection   | Reconciliation residual > threshold; аномалии в fact_finance                                                         |


### R-05: Масштабируемость обновления витрин


| Параметр    | Значение                                                                                                      |
| ----------- | ------------------------------------------------------------------------------------------------------------- |
| Риск        | Полный UPSERT витрин по аккаунту замедляется с ростом данных                                                  |
| Вероятность | Средняя (приемлемо при единицах–десятках аккаунтов)                                                           |
| Влияние     | Среднее — увеличение времени ETL, устаревание аналитики                                                       |
| Митигация   | `IS DISTINCT FROM` минимизирует churn; при масштабировании — партиционирование или инкрементальное обновление |
| Detection   | Рост sync duration; mart freshness degradation                                                                |


### R-06: WB Returns endpoint — RESOLVED (2026-03-30)


| Параметр    | Значение                                                                                           |
| ----------- | -------------------------------------------------------------------------------------------------- |
| Статус      | **RESOLVED**                                                                                       |
| Причина     | Root cause: формат даты `dateFrom`/`dateTo` — date-only (`YYYY-MM-DD`), не datetime               |
| Дата        | 2026-03-30                                                                                         |
| Влияние     | Нет — endpoint работает с корректными параметрами                                                  |


### R-07: Безопасность учётных данных


| Параметр    | Значение                                                                                                       |
| ----------- | -------------------------------------------------------------------------------------------------------------- |
| Риск        | API-ключи маркетплейсов раскрыты в конфигурациях или логах                                                     |
| Вероятность | Низкая (Vault integration)                                                                                     |
| Влияние     | Высокое — компрометация аккаунтов маркетплейсов                                                                |
| Митигация   | Vault для production credentials; metadata и secret material разделены; маскирование в логах; credential audit |
| Detection   | Credential audit alerts; unexpected API usage от провайдера                                                    |


### R-08: Ложный SUCCEEDED (premature terminal success)


| Параметр    | Значение                                                                                                      |
| ----------- | ------------------------------------------------------------------------------------------------------------- |
| Риск        | Price action помечается SUCCEEDED до подтверждения эффекта маркетплейсом                                      |
| Вероятность | Средняя (при некорректной реализации)                                                                         |
| Влияние     | Высокое — подрыв доверия к системе                                                                            |
| Митигация   | SUCCEEDED = confirmed final effect only; uncertain outcomes → RECONCILIATION_PENDING; evidence hierarchy (primary write confirmation + secondary read-after-write); reconciliation tolerance (exact match WB/Ozon); gateway defence-in-depth assertion (execution_mode check); stuck-state detector self-monitoring; обязательные flow tests. Детали: [Execution §Reconciliation](modules/execution.md#reconciliation) |
| Detection   | Mismatch между action intent и фактическим состоянием провайдера; alert `reconciliation.read_mismatch_after_confirmed_write`; метрика `execution_cas_conflict_total`; alert `execution_stuck_detector_last_run_at` stale |
| Status      | Архитектура полностью специфицирована (evidence hierarchy, tolerance, conflict resolution SLA, stuck detector self-monitoring). Read-after-write verification code — NOT IMPLEMENTED (write-contracts.md F-4). Приоритет: Phase D implementation |


### R-09: Stale truth блокирует автоматизацию


| Параметр    | Значение                                                                                                                         |
| ----------- | -------------------------------------------------------------------------------------------------------------------------------- |
| Риск        | Stale или некорректные данные приводят к ошибочным pricing decisions                                                             |
| Вероятность | Средняя                                                                                                                          |
| Влияние     | Среднее — продукт не работает или принимает ошибочные решения                                                                    |
| Митигация   | Data quality controls: stale data guards, missing sync detection, spike detection; stale truth блокирует automated price actions. Signal criticality classification ([Pricing §Signal criticality](modules/pricing.md#signal-criticality-и-clickhouse-fallback)): CRITICAL/REQUIRED/OPTIONAL per signal; per-signal ClickHouse fallback; REQUIRED signals cascade (per-SKU → per-category → manual); 5s query timeout; partial ClickHouse failure → per-SKU HOLD, не FAILED run |
| Detection   | Stale data alerts; guard hit rates; automation blocker events; `pricing_signal_unavailable_total` counter per signal type        |


### R-10: Несогласованность форматов timestamps


| Параметр    | Значение                                                                       |
| ----------- | ------------------------------------------------------------------------------ |
| Риск        | API маркетплейсов используют несогласованные timestamp formats                 |
| Вероятность | Подтверждён (WB: dual-format; Ozon финансы: не ISO 8601)                       |
| Влияние     | Среднее — ошибки парсинга, некорректная привязка к датам                       |
| Митигация   | Адаптеры реализуют flexible parser; форматы зафиксированы в архитектуре данных |
| Detection   | Parse errors в логах адаптеров                                                 |


### R-11: Simulated mode загрязняет каноническую модель


| Параметр    | Значение                                                                                                                                       |
| ----------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| Риск        | Simulated execution ошибочно мутирует авторитетные данные                                                                                      |
| Вероятность | Низкая (при корректной реализации)                                                                                                             |
| Влияние     | Высокое — потеря целостности канонической модели                                                                                               |
| Митигация   | **Pricing:** отдельная таблица `simulated_offer_state`; executor-worker проверяет `execution_mode` и пропускает canonical write для SIMULATED. **Promotions:** executor-worker при `execution_mode = SIMULATED` пропускает marketplace API call и CAS-update `canonical_promo_product.participation_status`; partial unique index `idx_promo_action_active_simulated` изолирует simulated от live actions. Общее: parity tests; execution_mode tracking; simulated mode не имеет права мутировать каноническую модель |
| Detection   | Parity test failures; integrity checks                                                                                                         |


### R-12: Расползание бизнес-логики в common/infrastructure


| Параметр    | Значение                                                                                       |
| ----------- | ---------------------------------------------------------------------------------------------- |
| Риск        | Бизнесовые enums, pricing logic, domain exceptions попадают в common или инфраструктурные слои |
| Вероятность | Средняя (при росте команды)                                                                    |
| Влияние     | Среднее — усложнение сопровождения, нарушение ownership                                        |
| Митигация   | ArchUnit + Maven Enforcer в CI; правило: common = only technical primitives                    |
| Detection   | CI build failure при нарушении boundary                                                        |


### R-13: P&L без рекламных расходов (Phase B core)


| Параметр    | Значение                                                                                                                                                             |
| ----------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Риск        | P&L завышает прибыль на сумму рекламных расходов. Для крупных рекламодателей (>10% от revenue) — значимая погрешность                                                |
| Вероятность | Подтверждён (до подключения Phase B extended)                                                                                                                        |
| Влияние     | Среднее — P&L структурно корректен, но неполный по содержанию                                                                                                        |
| Митигация   | advertising_cost = 0 (explicit fallback). UI предупреждение per marketplace. Phase B extended добавляет ads ingestion: WB — после adapter migration, Ozon — после OAuth2 setup. Ретроактивный пересчёт marts при подключении |
| Detection   | N/A до подключения; после — stale ad data alert                                                                                                                     |


### R-14: WB Incomes API deprecation (June 2026)


| Параметр    | Значение                                                                                                                             |
| ----------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| Риск        | Endpoint `/api/v1/supplier/incomes` отключается June 2026. fact_supply для WB FBO перестанет заполняться                             |
| Вероятность | Высокая (confirmed deprecation timeline)                                                                                             |
| Влияние     | Низкое — fact_supply не участвует в P&L; влияет только на inventory lead time (Phase G)                                              |
| Митигация   | FBS: `/api/v3/supplies` (Marketplace API). FBO: manual import, inventory delta analysis. Задокументировано в wb-read-contracts.md §8 |
| Detection   | HTTP 410 от `/api/v1/supplier/incomes`                                                                                               |


### R-15: Standalone operations allocation accuracy


| Параметр    | Значение                                                                                                                                           |
| ----------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| Риск        | Pro-rata revenue share allocation для storage/fixed costs не отражает реальное распределение расходов по товарам                                   |
| Вероятность | Средняя                                                                                                                                            |
| Влияние     | Низкое — влияет только на mart_product_pnl, не на mart_posting_pnl и account-level totals                                                          |
| Митигация   | mart_posting_pnl не содержит allocated costs (чистый). mart_product_pnl помечает allocated amounts. Volume-weighted allocation — Phase G extension |
| Detection   | Seller reports allocation не соответствует ожиданиям; UI feedback                                                                                  |


### R-16: WB Price Write недоступен


| Параметр    | Значение                                                                                                     |
| ----------- | ------------------------------------------------------------------------------------------------------------ |
| Риск        | WB Price Write endpoint недоступен: DNS migration (`discounts-api` → `discounts-prices-api`) + token scope   |
| Вероятность | Подтверждён (2026-03-30)                                                                                     |
| Влияние     | Высокое — Phase D Execution для WB невозможен                                                                |
| Митигация   | F-1: обновить host на `discounts-prices-api.wildberries.ru`. F-2: получить production токен с write scope    |
| Detection   | DNS resolution failure; HTTP 401 на write endpoint                                                           |
| Blocking    | Phase D (Execution) для WB                                                                                   |
| Not blocking | Phase A-C; Ozon Execution                                                                                   |


### R-17: Массовое повреждение цен через широкую policy (blast radius)


| Параметр    | Значение                                                                                                                                                                                             |
| ----------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Риск        | Ошибка в конфигурации `price_policy` с `scope_type = CONNECTION` массово меняет цены по всему ассортименту подключения                                                                              |
| Вероятность | Средняя (человеческая ошибка: неверный `target_margin_pct`, забытый constraint)                                                                                                                     |
| Влияние     | Высокое — массовая потеря маржи, штрафы маркетплейса за некорректные цены, репутационный ущерб                                                                                                      |
| Митигация   | 1) `SEMI_AUTO` default — все actions через approval; 2) `max_price_change_pct` ограничивает per-SKU изменение; 3) Safety gate для `FULL_AUTO` (минимум 7 дней в SEMI_AUTO, 0 FAILED actions, runtime re-check — см. [Pricing §Safety gate enforcement](modules/pricing.md#safety-gate--enforcement)); 4) Validation constraints на strategy_params при создании policy; 5) **Impact preview** (Phase E): dry-run pipeline показывает масштаб и направление изменений до активации policy (см. [Pricing → Impact preview](modules/pricing.md#impact-preview-phase-e)); 6) **Aggregate blast radius circuit breaker** (FULL_AUTO only): `change_ratio > 30%` или `max_abs_change_pct > 25%` → pricing run PAUSED, actions ON_HOLD, manual resume/cancel (см. [Pricing §Aggregate blast radius](modules/pricing.md#aggregate-blast-radius-protection)); 7) **Mandatory impact preview** для CONNECTION-scope FULL_AUTO policies при активации |
| Detection   | Резкий рост CHANGE decisions в pricing run; аномальный `avg_price_change_pct`; alert `pricing.run.blast_radius_breached` при breach aggregate thresholds; `pricing_approval_expired_total` counter   |


### R-18: Ozon Performance OAuth2 задерживает advertising ingestion


| Параметр    | Значение                                                                                                       |
| ----------- | -------------------------------------------------------------------------------------------------------------- |
| Риск        | OAuth2 credentials для Ozon Performance API не получены или token exchange нестабилен                           |
| Вероятность | Средняя (отдельная регистрация, 30-мин TTL, async report flow)                                                 |
| Влияние     | Среднее — Ozon advertising data не загружается; P&L для Ozon остаётся без рекламных расходов                   |
| Митигация   | WB advertising работает независимо (отдельный auth). Token caching с buffer. Retry при token failure. Graceful degradation: P&L без Ozon ads = допустимо |
| Detection   | Token exchange failure rate; empty advertising data for Ozon connections                                        |


### R-19: WB Advertising API дальнейшие breaking changes


| Параметр    | Значение                                                                                                       |
| ----------- | -------------------------------------------------------------------------------------------------------------- |
| Риск        | WB повторно мигрирует advertising endpoints (как v1→v2, v2→v3)                                                |
| Вероятность | Средняя (прецедент: два breaking change за 2025)                                                               |
| Влияние     | Среднее — advertising ingestion ломается; P&L возвращается к fallback (advertising_cost = 0)                   |
| Митигация   | `@JsonIgnoreProperties(ignoreUnknown = true)`. Мониторинг HTTP 404/410. Version pinning в adapter config. Graceful degradation при adapter failure |
| Detection   | HTTP 404/410 от advert-api; рост ошибок в integration_call_log                                                 |


### R-20: ClickHouse unavailability останавливает аналитику и pricing


| Параметр    | Значение                                                                                                       |
| ----------- | -------------------------------------------------------------------------------------------------------------- |
| Риск        | ClickHouse недоступен — аналитические страницы и pricing signal assembler деградируют                          |
| Вероятность | Низкая (single-node Phase B; средняя при росте нагрузки)                                                       |
| Влияние     | Среднее — аналитика недоступна; pricing pipeline skip-and-retry (не принимает stale decisions); core ops работают |
| Митигация   | Circuit breaker (Resilience4j) на analytics API endpoints; signal assembler fail-fast + pricing run FAILED с retry; двухфазный swap (staging table) для materialization; health indicator в `/actuator/health`; UI banner «Аналитика временно недоступна»; stale data guard блокирует automation при prolonged outage (>24h). Детали: [Analytics & P&L](modules/analytics-pnl.md) §Graceful degradation |
| Detection   | ClickHouse health indicator DOWN; circuit breaker open events; `materialization_run_status = FAILED`; pricing run `reason = ANALYTICS_UNAVAILABLE` |


### R-21: WB Promo Write недоступен (P-4)


| Параметр    | Значение                                                                                                       |
| ----------- | -------------------------------------------------------------------------------------------------------------- |
| Риск        | WB Promo activate/deactivate API недоступен: требуется Promotion-scoped токен, формат запроса не подтверждён   |
| Вероятность | Подтверждён (P-4 OPEN)                                                                                         |
| Влияние     | Среднее — WB promo pipeline работает в recommendation-only режиме; Ozon promo execution не затронут            |
| Митигация   | Graceful degradation: `participation_mode` принудительно RECOMMENDATION для WB; evaluation pipeline работает; UI показывает рекомендации с `promo.wb.write_unavailable`; оператор выполняет действия вручную через ЛК WB. Детали: [Promotions §P-4 Graceful degradation](modules/promotions.md) |
| Detection   | P-4 status в promotions.md; отсутствие SUCCEEDED promo_actions для WB connections                              |
| Blocking    | Phase D (Promo Execution) для WB                                                                               |
| Not blocking | WB evaluation, Ozon full pipeline, Phase A-C                                                                   |


## Сводка рисков


| Риск                                           | Вероятность | Влияние | Приоритет       |
| ---------------------------------------------- | ----------- | ------- | --------------- |
| R-01 Ломающие изменения API                    | Высокая     | Высокое | **Критический** |
| R-02 Rate limiting                             | Высокая     | Среднее | Высокий         |
| R-03 Single-point-of-failure                   | Средняя     | Высокое | Высокий         |
| R-04 Корректность финансовых расчётов          | Средняя     | Высокое | Высокий         |
| R-05 Масштабируемость витрин                   | Средняя     | Среднее | Средний         |
| R-06 WB Returns ~~заблокирован~~ RESOLVED      | —           | —       | —               |
| R-07 Безопасность credentials                  | Низкая      | Высокое | Средний         |
| R-08 Ложный SUCCEEDED                          | Средняя     | Высокое | Высокий         |
| R-09 Stale truth блокирует automation          | Средняя     | Среднее | Средний         |
| R-10 Форматы timestamps                        | Подтверждён | Среднее | Средний         |
| R-11 Simulated mode загрязняет truth           | Низкая      | Высокое | Средний         |
| R-12 Расползание логики в common               | Средняя     | Среднее | Средний         |
| R-13 P&L без рекламных расходов (B core)        | Подтверждён | Среднее | Средний         |
| R-14 WB Incomes API deprecation (June 2026)    | Высокая     | Низкое  | Низкий          |
| R-15 Standalone operations allocation accuracy | Средняя     | Низкое  | Низкий          |
| R-16 WB Price Write недоступен                 | Подтверждён | Высокое | Высокий         |
| R-17 Blast radius широкой policy               | Средняя     | Высокое | Высокий         |
| R-18 Ozon OAuth2 задерживает ads ingestion     | Средняя     | Среднее | Средний         |
| R-19 WB Advertising API breaking changes       | Средняя     | Среднее | Средний         |
| R-20 ClickHouse unavailability                 | Низкая      | Среднее | Средний         |
| R-21 WB Promo Write недоступен (P-4)          | Подтверждён | Среднее | Средний         |


## Связанные документы

- [Data Model](data-model.md) — инварианты, scope
- [Execution](modules/execution.md) — action lifecycle, SUCCEEDED criteria
- [Integration](modules/integration.md) — provider blockers, rate limits
- [Non-Functional Architecture](non-functional-architecture.md) — resilience, security measures
- [Analytics & P&L](modules/analytics-pnl.md) — P&L sanitation decisions

