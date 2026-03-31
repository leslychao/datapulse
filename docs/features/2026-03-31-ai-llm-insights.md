# Feature: AI-Powered Seller Intelligence (LLM)

**Статус:** DRAFT
**Дата создания:** 2026-03-31
**Автор:** Виталий Ким
**Целевая фаза:** E (Command Palette AI), G (остальные capabilities)

---

## Business Context

### Проблема

Селлер маркетплейсов ежедневно работает с большим объёмом данных: сотни SKU, десятки метрик, алерты, решения, журналы. Datapulse уже собирает, нормализует и визуализирует эти данные — но интерпретация остаётся на плечах человека.

Текущие ограничения:
- **Навигация по данным** требует знания фильтров, колонок и структуры грида. Новый пользователь не знает, «куда нажать».
- **Алерты** — сухие технические факты (`residual anomaly detected, delta > 2σ`). Селлер видит проблему, но не понимает причину и что делать.
- **Отсутствие проактивных инсайтов** — система реагирует на запросы, но не подсказывает возможности.
- **Ценовые решения** — explanation summary детальный, но технический. Контекстный совет в терминах бизнеса отсутствует.
- **Нет обзорной картины** — селлер не видит «что изменилось за неделю» одним взглядом.

### Бизнес-ценность

**Конкурентное преимущество:** ни один из текущих аналогов (MPStats, Маяк, SellersBoard, MarketGuru) не предлагает LLM-driven intelligence по данным селлера. Даже базовые AI-фичи дифференцируют продукт.

**Measurable outcomes:**
- Время на навигацию к нужным данным: снижение с минут до секунд (natural language query).
- Время на понимание аномалии: с 10-15 минут drill-down до мгновенного объяснения.
- Количество упущенных возможностей: снижение за счёт проактивных инсайтов.
- Онбординг новых пользователей: natural language снижает порог входа.

### Ключевой принцип

**LLM не принимает решения — он помогает понимать данные.**

Datapulse построен на explainability: каждое решение прозрачно, каждая цифра сверяема. LLM используется как **вербализатор** и **навигатор**, не как чёрный ящик для бизнес-решений. Все данные берутся из проверенных sources of truth (ClickHouse, PostgreSQL). LLM формулирует, а не выдумывает.

---

## Capabilities

### F1: Smart Command Palette (Natural Language Query)

**Фаза:** E (Seller Operations)

Расширение существующего `Ctrl+K` Command Palette. Помимо поиска по сущностям — распознавание natural language запросов и конвертация в фильтры грида, навигацию или data queries.

**Примеры:**

| Запрос пользователя | Действие системы |
|---|---|
| «товары на WB с маржой ниже 15%» | Конвертация в фильтры грида: `marketplace_type=WB, margin_max=15` → применить и показать |
| «что случилось вчера» | Сводка: аномалии, изменения статусов, алерты за вчера |
| «покажи убыточные товары» | Фильтр: `margin_max=0` |
| «товары без себестоимости» | Фильтр: `cost_price IS NULL` (saved view или ad-hoc) |
| «сколько товаров в stock-out» | Quick answer: число + ссылка на filtered view |
| «открой P&L за март по Ozon» | Навигация: Analytics → P&L → filters `connection=Ozon, period=2026-03` |

**Архитектура:**

LLM получает фиксированный system prompt с описанием доступных фильтров, колонок и навигационных действий. Пользовательский запрос конвертируется в structured JSON. Никаких прямых SQL-запросов от LLM — только routing к существующим API и фильтрам.

**Prompt template (text-to-filter):**

```
System: Ты — навигатор аналитической системы для селлеров маркетплейсов.

Доступные фильтры грида:
- marketplace_type: [WB, OZON]
- status: [ACTIVE, ARCHIVED, BLOCKED]
- margin_min / margin_max: число (%)
- stock_risk: [CRITICAL, WARNING, NORMAL]
- has_active_promo: boolean
- has_manual_lock: boolean
- last_action_status: [SUCCEEDED, FAILED, PENDING_APPROVAL]
- category_id: число
- sku_code: текст (поиск)
- product_name: текст (поиск)

Доступные навигационные действия:
- navigate: {module, view, filters}
- quick_answer: {text, link}

Пользователь написал: "{user_query}"

Верни JSON:
- {"action": "filter", "filters": {...}} — для применения фильтров грида
- {"action": "navigate", "target": {...}} — для навигации
- {"action": "quick_answer", "text": "...", "link": "..."} — для быстрого ответа
- {"action": "search", "query": "..."} — если запрос не про фильтры/навигацию
```

**Fallback:** если LLM недоступен — `Ctrl+K` работает как обычный entity search (graceful degradation).

---

### F2: Anomaly Explanation (объяснение аномалий)

**Фаза:** G (Intelligence)

Когда система детектирует аномалию (alert_event), LLM генерирует human-readable объяснение: что произошло, почему, какой impact, что рекомендуется сделать.

**Пример output:**

> Маржа по товару «Кроссовки Nike Air» (SKU: NK-AIR-42) упала с 22% до 10% за последнюю неделю.
>
> **Причина:** логистика подорожала на 35₽ за единицу (logistics_cost: −180₽ → −215₽), плюс return rate вырос с 3% до 8%. Основная причина возвратов — «не подошёл размер».
>
> **Impact:** при текущем velocity (12 шт/день) потери ~5 040₽/мес.
>
> **Рекомендация:** проверить размерную сетку в описании товара. Рассмотреть повышение цены на 3-5% для компенсации логистики.

**Архитектура:**

Backend собирает structured context из marts (P&L delta, component breakdown, return reasons, velocity). LLM получает **предагрегированные данные** и формулирует текст. LLM не делает запросы к данным сам.

**Prompt template:**

```
System: Ты — аналитик маркетплейсов. Объясни аномалию простым языком для селлера.

Контекст:
- Товар: {product_name} ({sku_code}), {marketplace_type}
- Тип аномалии: {anomaly_type}
- Период: {date_from} — {date_to}

P&L delta:
- Маржа: {margin_before}% → {margin_after}%
- Revenue: {revenue_before}₽ → {revenue_after}₽
- Изменения компонентов:
  {components_delta_json}

Дополнительный контекст:
- Return rate: {return_rate_before}% → {return_rate_after}%
- Top return reason: "{top_return_reason}"
- Velocity: {velocity} шт/день
- Days of cover: {days_of_cover}

Напиши объяснение (3-5 предложений): что произошло, почему, impact в рублях, одна рекомендация.
```

**Trigger:** генерация запускается при создании `alert_event` типов SPIKE_DETECTION, RESIDUAL_ANOMALY. Результат сохраняется в `alert_event.details` (JSONB, поле `ai_explanation`). Генерация async — `@Async`, не на критическом пути.

---

### F3: Weekly Digest (еженедельная сводка)

**Фаза:** G (Intelligence)

Автоматическая бизнес-сводка за неделю. Генерируется по расписанию (понедельник утром), доставляется как notification + доступна в UI.

**Пример output:**

> **Неделя 24–30 марта 2026**
>
> - Выручка: 130 200₽ (+8.2% к прошлой неделе). Рост за счёт WB (+15 400₽), Ozon стабилен.
> - Маржинальность: 19.1% (было 22.3%). Основной драйвер: рост логистики по 5 товарам на WB.
> - 3 товара вышли в stock-out. Estimated потери revenue: ~12 000₽. Кандидаты на пополнение: SKU-A, SKU-B, SKU-C.
> - Return rate стабилен: 4.2% (было 4.0%).
> - Промо: 2 акции приняты, 1 отклонена (маржа при промо-цене ниже порога).
> - Pricing: 45 решений CHANGE (41 успешно, 4 failed). 2 failed actions в очереди.
> - Новых алертов: 3 (1 critical — stale data WB, resolved).

**Архитектура:**

Scheduled job (cron: `0 8 * * MON` — понедельник 08:00) собирает агрегаты из marts и PostgreSQL, формирует structured context, отправляет в LLM для вербализации. Результат сохраняется в `ai_digest` (новая таблица) и push-уется как `user_notification`.

**Prompt template:**

```
System: Сформируй еженедельную сводку для селлера маркетплейсов.
Акцент на изменениях, проблемах и действиях. 5-8 буллетов. Конкретные числа.

Данные за {week_start} — {week_end}, workspace: {workspace_name}:

Выручка: {revenue_current}₽ (пред. неделя: {revenue_prev}₽)
Выручка по МП: {revenue_by_marketplace_json}
Маржинальность: {margin_current}% (пред.: {margin_prev}%)
Top-3 драйвера изменения маржи: {margin_drivers_json}
Stock-outs: {stockout_count} товаров, estimated lost revenue: {lost_revenue}₽
Stock-out SKUs: {stockout_skus_json}
Return rate: {return_rate}% (пред.: {return_rate_prev}%)
Промо: принято {promo_accepted}, отклонено {promo_declined}
Pricing: {changes_total} CHANGE ({changes_succeeded} успешно, {changes_failed} failed)
Алерты: {alerts_new} новых ({alerts_critical} critical)
```

---

### F4: Pricing Advisor (советник по ценообразованию)

**Фаза:** G (Intelligence)

Контекстный совет, дополняющий pricing explanation. Отображается в Detail Panel рядом с последним pricing decision. Не замена — дополнение.

**Пример output:**

> Товар стабильно продаётся по 50 шт/день при цене 1 200₽. За последний месяц velocity не реагировала на колебания цены ±5% (по ценовой истории). Return rate низкий (2%). Текущая стратегия TARGET_MARGIN (25%) выдерживается. Есть пространство для повышения цены на 3-5% без потери velocity — это увеличит маржу на ~1 800₽/мес.

**Prompt template:**

```
System: Ты — советник по ценообразованию. Совет на основе фактов. 2-4 предложения.
Только конкретные числа, без общих фраз.

Товар: {product_name} ({sku_code}), {marketplace_type}
Цена: {current_price}₽, себестоимость: {cost_price}₽, маржа: {margin_pct}%
Стратегия: {strategy_type} (target: {target_value})
Velocity (14d): {velocity} шт/день, тренд (30d): {velocity_trend}
Ценовая история (30d): min {price_min}₽, max {price_max}₽
Velocity при min/max цене: {velocity_at_min} / {velocity_at_max} шт/день
Return rate: {return_rate}%
Stock: {available} шт, days of cover: {days_of_cover}
Последнее решение: {last_decision_type}, {last_decision_date}
```

**Trigger:** генерация on-demand при открытии Detail Panel (lazy, cached 24h).

---

### F5: Proactive Opportunity Detection (проактивные инсайты)

**Фаза:** G (Intelligence)

Система сканирует данные по расписанию и выявляет бизнес-возможности, которые селлер мог не заметить. Push-уведомления в notification bell.

**Типы инсайтов:**

| Тип | Описание | Data source | Пример |
|---|---|---|---|
| **Price increase candidate** | Товар со стабильной velocity, высокой маржой, низким return rate | mart_product_pnl + fact_price_snapshot + mart_returns_analysis | «3 товара стабильно продаются с маржой 30%+ и velocity > 20 шт/день. Кандидаты для повышения цены — потенциал +4 200₽/мес.» |
| **Cost structure change** | МП изменил комиссию, логистику или тарифы для категории | fact_finance (period-over-period component delta per category) | «WB снизил комиссию в категории "Спорт" с 15% до 12%. Затрагивает 8 ваших товаров. Маржа вырастет на ~2.5%.» |
| **Return pattern alert** | Аномально высокий return rate с повторяющейся причиной | mart_returns_analysis | «Товар "Кроссовки Nike" — return rate 18% (среднее по категории 5%). Причина: "не подошёл размер" (85% возвратов). Обновите размерную сетку.» |
| **Frozen capital opportunity** | Overstock с высокой себестоимостью и низкой velocity | mart_inventory_analysis + fact_product_cost | «42 000₽ заморожены в 3 товарах с velocity < 0.5 шт/день. Снижение цены на 10-15% может ускорить оборот.» |
| **Promo effectiveness insight** | Анализ результатов завершённых промо | promo_decision + mart_product_pnl | «Промо "Весенняя распродажа" завершилось. Revenue +25%, но маржа −8%. Чистый эффект: −3 200₽. Рекомендация: участвовать в промо только для товаров с маржой > 20%.» |

**Архитектура:**

Scheduled job (daily, configurable). Для каждого типа инсайта — dedicated detector (Java-сервис) собирает structured data, проверяет пороги, при срабатывании передаёт контекст в LLM для вербализации. Результат → `user_notification` (type = `AI_INSIGHT`).

**Detector → LLM → Notification:**

```
1. PriceIncreaseCandidateDetector: SQL query → list of candidates
2. Если candidates.size() > 0:
   a. Structured context → LLM prompt
   b. LLM → human-readable insight text
   c. INSERT user_notification (AI_INSIGHT)
   d. WebSocket push
```

**Deduplification:** один инсайт per SKU per type не чаще чем раз в 7 дней. Предотвращает спам одинаковыми инсайтами.

---

### F6: Smart Alert Triage (приоритизация алертов)

**Фаза:** G (Intelligence)

Вместо плоского списка из 15 алертов одинакового вида — LLM ранжирует по бизнес-impact и группирует.

**Пример output:**

> **Сейчас в работе 12 алертов:**
>
> **Критический (1):** Товар «Кроссовки Nike» теряет ~5 000₽/день — stock-out + failed pricing action. Нужна немедленная реакция.
>
> **Средний (3):** Рост логистики по 3 товарам на WB (impact: ~2 100₽/мес). Можно компенсировать повышением цен.
>
> **Низкий (8):** Стандартные колебания return rate, мелкие mismatch-и. Можно разобрать позже.

**Архитектура:**

При открытии alerts dashboard (или по запросу) — backend собирает все OPEN/ACKNOWLEDGED alert_events с enrichment данными (estimated daily loss, affected SKU count), передаёт в LLM для группировки и summary. Результат кешируется на 1 час (invalidation при новом alert_event).

---

### F7: Conversational Data Explorer (multi-turn диалог с данными)

**Фаза:** G+ (расширение)

Полноценный чат с данными. Не one-shot запрос (как F1), а multi-turn разговор с сохранением контекста.

**Пример сессии:**

```
Пользователь: покажи продажи за март
→ Таблица: revenue по товарам за март

Пользователь: а теперь только WB
→ Таблица обновлена, фильтр WB

Пользователь: сравни с февралём
→ Таблица: две колонки (февраль / март), delta, %

Пользователь: почему у этих трёх товаров упало?
→ Объяснение: для каждого из top-3 declined — breakdown (logistics up, commission change, etc.)

Пользователь: что можно с этим сделать?
→ Рекомендации per-SKU: повысить цену / обновить описание / запустить промо
```

**Архитектура:**

Отдельный UI panel (slide-in, аналогия — chat panel в Cursor IDE). Backend хранит session context (последние N turns). Каждый turn: LLM решает, нужен ли data query (→ structured query → API → данные → LLM verbalization) или достаточно текстового ответа.

**Инвариант:** все data queries идут через существующие REST API / ClickHouse endpoints. LLM не пишет SQL напрямую. LLM генерирует structured query intent → backend транслирует в реальный запрос.

---

### F8: Morning Briefing (утренний брифинг)

**Фаза:** G (Intelligence)

Персонализированная сводка при входе в систему. Появляется в Main Area при первом входе за день (вместо пустого грида). Закрывается одним кликом → переход к обычному workspace.

**Контент:**

| Блок | Данные | Описание |
|---|---|---|
| **Что изменилось за ночь** | Синхронизации, новые алерты, resolved алерты | «Синхронизировано: WB (14:23), Ozon (14:45). 2 новых алерта.» |
| **Требует внимания** | Working queues: pending count per queue | «Failed Actions: 3. Pending Approvals: 5. Stock Critical: 2.» |
| **Quick wins** | Proactive insights (F5), top-1 per type | «Можно поднять цену на 3 товарах (+2 100₽/мес)» |
| **Вчерашние итоги** | Revenue, orders, returns — delta vs среднее | «Выручка вчера: 18 500₽ (−3% vs avg)» |

**Архитектура:**

Briefing собирается при первом API-запросе за день (lazy). Backend агрегирует данные из notification feed, working queues, ai_digest (если есть), marts. LLM вербализирует top-level summary. Кешируется до конца дня.

---

### F9: Impact Simulation Narrative (описание последствий)

**Фаза:** G (Intelligence)

Расширение Impact Preview (Pricing module, Phase E). К числовому preview добавляется LLM-generated narrative: что произойдёт, если применить pricing policy / изменить цену.

**Пример output (при ручном изменении цены):**

> Вы хотите снизить цену с 1 500₽ до 1 350₽ (−10%).
>
> **Ожидаемый эффект:** при текущей observed эластичности velocity может вырасти на ~15% (с 8 до ~9.2 шт/день). Однако маржа снизится с 25% до 18%. Чистый monthly impact: +1 200₽ revenue, −2 800₽ margin = **−1 600₽/мес**.
>
> **Запас стока:** 95 шт (10 дней при текущей velocity, 9 дней при ожидаемой). Достаточно.
>
> **Вывод:** снижение цены невыгодно при текущих cost rates. Рассмотрите снижение на 5% — break-even по margin.

**Prompt template:**

```
System: Опиши последствия ценового изменения. Конкретные числа.
Включи: expected velocity change, margin impact, stock sufficiency, вывод.

Товар: {product_name} ({sku_code}), {marketplace_type}
Текущая цена: {current_price}₽ → Новая цена: {new_price}₽ ({change_pct}%)
COGS: {cost_price}₽
Effective cost rate: {effective_cost_rate}%
Текущая маржа: {current_margin_pct}%, новая маржа: {new_margin_pct}%
Velocity: {current_velocity} шт/день
Observed elasticity: при изменении цены на {historical_price_delta}% velocity менялась на {historical_velocity_delta}%
Stock: {available} шт, days of cover: {days_of_cover} (current), {projected_days_of_cover} (projected)
Monthly revenue change: {monthly_revenue_delta}₽
Monthly margin change: {monthly_margin_delta}₽
```

---

### F10: Auto-Classification of Unknown Operations (автоклассификация)

**Фаза:** G (Intelligence)

Когда ETL normalizer встречает нераспознанный тип операции маркетплейса (entry_type = `OTHER`), LLM предлагает классификацию на основе названия операции, суммы, контекста.

**Пример:**

```
Нераспознанная операция Ozon:
  operation_type_name: "MarketplaceServiceItemInstallmentCommission"
  amount: -125.40
  has_posting: true
  has_sku: true

LLM suggestion: MARKETPLACE_COMMISSION
  confidence: 0.85
  reasoning: "Название содержит 'Commission', привязана к posting и SKU,
             отрицательная сумма (debit) — характерно для комиссий."
```

**Архитектура:**

Не автоматическое применение — **предложение для review**. Normalizer логирует `OTHER` entries. Scheduled job (daily) агрегирует unique unknown operation_type_name, отправляет batch в LLM. Результат сохраняется как `suggested_classification` (новая таблица). Оператор (или разработчик) ревьюит предложения → при подтверждении обновляет mapping в normalizer.

**Ценность:** маркетплейсы постоянно добавляют новые типы операций (Ozon: +6 типов за 14 месяцев). Auto-classification ускоряет адаптацию.

---

## User Stories

### US-1: Natural language навигация

**Как** селлер,
**я хочу** написать в Ctrl+K «убыточные товары на WB»,
**чтобы** мгновенно увидеть отфильтрованный грид без ручной настройки 3-4 фильтров.

### US-2: Понимание аномалии

**Как** селлер,
**я хочу** видеть объяснение алерта на человеческом языке (причина, impact, рекомендация),
**чтобы** не тратить 10 минут на drill-down и самостоятельный анализ.

### US-3: Еженедельный обзор

**Как** владелец бизнеса,
**я хочу** получать в понедельник утром краткую сводку за неделю,
**чтобы** за 30 секунд понять общее состояние бизнеса и что требует внимания.

### US-4: Ценовая рекомендация

**Как** менеджер по ценообразованию,
**я хочу** видеть контекстный совет по каждому товару (рядом с pricing decision),
**чтобы** принимать более обоснованные решения о цене.

### US-5: Проактивные возможности

**Как** селлер,
**я хочу** получать уведомления о возможностях (кандидаты на повышение цены, frozen capital, тренды),
**чтобы** не упускать прибыль, даже если не анализирую каждый SKU вручную.

### US-6: Утренний брифинг

**Как** селлер,
**я хочу** при входе в систему видеть короткую сводку: что изменилось, что требует внимания,
**чтобы** сразу знать, с чего начать день.

### US-7: Разговор с данными

**Как** аналитик,
**я хочу** в чате спрашивать «покажи продажи за март, сравни с февралём, почему упало»,
**чтобы** анализировать данные без построения сложных фильтров и ручных сравнений.

---

## Acceptance Criteria

- [ ] Ctrl+K (F1): natural language query конвертируется в фильтры грида за < 1 секунды. Fallback на обычный search при недоступности LLM
- [ ] Anomaly explanation (F2): каждый SPIKE/RESIDUAL alert сопровождается текстовым объяснением с причиной, impact и рекомендацией
- [ ] Weekly digest (F3): еженедельная сводка генерируется автоматически и доставляется как notification
- [ ] Pricing advisor (F4): контекстный совет отображается в Detail Panel при наличии достаточных данных
- [ ] Proactive insights (F5): ≥ 3 типа инсайтов генерируются и доставляются как notifications
- [ ] Alert triage (F6): alerts dashboard показывает приоритизированную и сгруппированную сводку
- [ ] Morning briefing (F8): при первом входе за день отображается краткая сводка
- [ ] Все AI-фичи graceful degrade при недоступности LLM (core functionality не страдает)
- [ ] Данные не покидают контур (self-hosted LLM)
- [ ] AI-generated content визуально маркирован как AI-generated (transparency)

---

## Scope

### В scope

- 10 capabilities (F1–F10) с фазированием
- Self-hosted LLM инфраструктура
- Prompt template система
- REST API для AI-фич
- Graceful degradation
- Caching и deduplication

### Вне scope

- LLM для принятия pricing decisions (pricing pipeline остаётся rule-based)
- Генерация описаний товаров / контента
- Прогнозирование продаж через LLM (это задача для ML-моделей, не языковых)
- Интеграция с внешними AI-сервисами (OpenAI, Anthropic) — только self-hosted
- Голосовой ввод
- Image analysis (фото товаров)

---

## Architectural Impact

**Статус:** не начат

### Затронутые модули

| Модуль | Тип изменения | Описание |
|---|---|---|
| [Seller Operations](../modules/seller-operations.md) | Расширение | Command Palette AI (F1), Morning Briefing UI (F8), Alert triage UI (F6) |
| [Audit & Alerting](../modules/audit-alerting.md) | Расширение | `ai_explanation` field в alert_event.details; AI_INSIGHT notification_type |
| [Pricing](../modules/pricing.md) | Расширение | Pricing advisor display (F4), Impact narrative (F9) |
| [Analytics & P&L](../modules/analytics-pnl.md) | Расширение | Data aggregation queries для digest (F3) и opportunity detection (F5) |

### Новые компоненты

- **AI Service module** (`io.datapulse.ai`) — новый пакет: prompt templates, LLM client, caching, async generation
- **datapulse-llm** — Docker-контейнер с self-hosted LLM (vLLM + модель)

### Изменения в data model

Новые таблицы (PostgreSQL):

| Таблица | Назначение |
|---|---|
| `ai_digest` | Сгенерированные дайджесты (weekly/daily). Fields: workspace_id, period_type, period_start, period_end, structured_data (JSONB), generated_text (TEXT), generated_at |
| `ai_insight` | Проактивные инсайты (F5). Fields: workspace_id, insight_type, entity_type, entity_id, structured_data (JSONB), generated_text (TEXT), severity, dedupe_key, generated_at |
| `ai_suggested_classification` | Предложенные классификации unknown ops (F10). Fields: operation_type_name, suggested_entry_type, confidence, reasoning, status (PENDING/ACCEPTED/REJECTED), reviewed_by, reviewed_at |

Изменения существующих таблиц:

| Таблица | Изменение |
|---|---|
| `alert_event.details` | Новое JSONB-поле `ai_explanation` (TEXT внутри details JSONB) |
| `user_notification` | Новый notification_type: `AI_INSIGHT`, `AI_DIGEST` |

---

## LLM Infrastructure

### Self-Hosted Architecture

```
┌─────────────┐      HTTP (OpenAI-compatible API)     ┌──────────────────┐
│datapulse-api│ ──────────────────────────────────────→│  datapulse-llm   │
│  (backend)  │                                        │ (vLLM container) │
└─────────────┘                                        │                  │
                                                       │ Model: Qwen3-8B │
                                                       │         Q4      │
                                                       │ GPU: 1×RTX 3060 │
                                                       │      12GB       │
                                                       └──────────────────┘
```

### Выбор модели

| Критерий | Требование | Выбор |
|---|---|---|
| Русский язык | Генерация текстов, объяснений, дайджестов на русском | Qwen3-8B — 119 языков (включая русский), 36 трлн токенов обучения. Community fine-tune `Qwen3-8B-ru` для русского |
| Structured output | text-to-filter (F1), JSON responses | Qwen3 поддерживает function calling и structured output из коробки |
| Inference speed | Command Palette < 1s, объяснения < 3s | vLLM + GPU: 8B Q4 — 50-80 tokens/sec на RTX 3060 12GB |
| Privacy | Финансовые данные селлера не покидают контур | Self-hosted, никаких внешних API |
| VRAM | Минимальная стоимость GPU | 8B Q4 ≈ 6GB VRAM → RTX 3060 12GB (запас 6GB) |
| Thinking mode | Сложные задачи (F2, F4, F9) требуют рассуждений | Qwen3 hybrid thinking: non-thinking для F1 (быстро), thinking для F2/F4/F9 (качественно) |
| Бюджет | Минимальные инфраструктурные расходы | Consumer GPU RTX 3060 12GB (~$250-300 б/у) вместо RTX 4090 (~$1500+) |

**Почему Qwen3-8B, а не Qwen 2.5 14B:**
- Qwen3-8B умнее Qwen2.5-14B по бенчмаркам (MMLU-Pro: 74 vs ~70, MATH: 90 vs ~50)
- Thinking mode компенсирует меньший размер на сложных задачах
- 119 языков вместо 29 — значительно лучше русский
- Вдвое меньше VRAM → вдвое дешевле GPU

**Путь роста модели (без изменения кода):**

| Этап | Модель | GPU | Стоимость GPU |
|---|---|---|---|
| MVP (бюджет) | Qwen3-8B Q4 | RTX 3060 12GB | ~$250-300 |
| Рост | Qwen3-14B Q4 | RTX 4060 Ti 16GB | ~$400-500 |
| Масштаб | Qwen3-30B-A3B (MoE) Q4 | RTX 4090 24GB | ~$1500 |

Смена модели = одна строка в Docker Compose. Backend, промты, API — без изменений.

### Inference Engine

**vLLM** — production-grade inference server:
- OpenAI-compatible API (drop-in replacement)
- Continuous batching (несколько concurrent requests)
- PagedAttention (эффективное использование VRAM)
- Docker image: `vllm/vllm-openai`

### Fallback при недоступности LLM

| Capability | Degradation |
|---|---|
| F1 Command Palette | Entity search (текущий функционал без AI) |
| F2 Anomaly explanation | alert_event без `ai_explanation` — показываются raw details |
| F3 Weekly digest | Не генерируется. Structured data доступны через API (числа без narrative) |
| F4 Pricing advisor | Не отображается. Pricing explanation (rule-based) остаётся |
| F5 Proactive insights | Не генерируются |
| F6 Alert triage | Плоский список алертов (текущий функционал) |
| F7 Data explorer | Недоступен |
| F8 Morning briefing | Показываются только числовые блоки (queues count, alerts count) без narrative |
| F9 Impact narrative | Impact preview (числовой) без narrative |
| F10 Auto-classification | Unknown ops логируются без suggested classification |

### Docker Compose

```yaml
datapulse-llm:
  image: vllm/vllm-openai:latest
  command: >
    --model Qwen/Qwen3-8B
    --served-model-name datapulse
    --max-model-len 4096
    --gpu-memory-utilization 0.85
    --quantization gptq
  deploy:
    resources:
      reservations:
        devices:
          - driver: nvidia
            count: 1
            capabilities: [gpu]
  ports:
    - "8100:8000"
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8000/health"]
    interval: 30s
    timeout: 10s
    retries: 3
```

### Hardware Requirements (LLM-сервер)

**Минимальная конфигурация (MVP):**

| Компонент | Требование | Обоснование |
|---|---|---|
| GPU | RTX 3060 12GB | Qwen3-8B Q4 ≈ 6GB VRAM, запас 6GB для KV-cache |
| CPU | 4+ ядер (Intel i5-10400 / Ryzen 5 3600) | CPU загрузка ~5-10% при inference; только обслуживание HTTP-запросов |
| RAM | 16 GB DDR4 | Модель загружается в RAM перед переносом на GPU. 8B модель ≈ 5-8 GB в RAM |
| Диск | SSD 256 GB (SATA достаточно) | Модель (5-8 GB) загружается с диска один раз при старте. HDD тоже работает, но холодный старт 30s вместо 5s |
| Материнская плата | PCIe x16 Gen 3+ | Стандартный слот для GPU. Без райзеров |
| БП | 500W | RTX 3060 ~170W + остальное ~80W = ~250W, запас 2x |

**Бюджетная стратегия:** б/у офисный ПК (Dell OptiPlex / HP ProDesk / Lenovo ThinkCentre, i5 10-11th gen) + RTX 3060 12GB + замена БП на 500W. Ориентировочная стоимость: **~$430-480**.

**Deployment topology:**

```
Вариант A (рекомендуемый): отдельный LLM-сервер
  [Основной сервер]              [LLM-сервер]
    datapulse-api                  datapulse-llm (vLLM)
    PostgreSQL, ClickHouse         RTX 3060 12GB
    RabbitMQ, Redis                i5 + 16GB RAM
         ───── HTTP ────→

Вариант B (всё на одном): для MVP при минимальном бюджете
  [Единый сервер]
    datapulse-api + datapulse-llm
    PostgreSQL, ClickHouse, RabbitMQ, Redis
    RTX 3060 12GB + i5/Ryzen 5 + 32GB RAM + SSD 512GB
```

Вариант A предпочтителен: LLM inference не конкурирует за ресурсы с БД, независимый upgrade path. Вариант B допустим для MVP — PostgreSQL/ClickHouse нагружают CPU/RAM, LLM нагружает GPU, конкуренция минимальна.

### Backend Integration

Java-сервис `AiService` (`io.datapulse.ai`):

- **LLM Client:** `WebClient` к `http://datapulse-llm:8000/v1/chat/completions` (OpenAI-compatible)
- **Prompt Registry:** enum/class с prompt templates per capability. Версионируемые в коде
- **Response Parser:** structured output → domain objects (filters, insights, etc.)
- **Cache:** Caffeine (TTL per capability: F1 — нет, F4 — 24h, F6 — 1h, F8 — до конца дня)
- **Async:** `@Async("aiExecutor")` для non-blocking generation (F2, F3, F5)
- **Circuit Breaker:** Resilience4j `@CircuitBreaker` — при persistent failures → graceful degradation
- **Timeout:** per-capability (F1: 2s strict, F2-F10: 15s)

### Prompt Template Management

Prompt-ы хранятся как версионируемые ресурсы в Java-коде:

```java
public enum AiPromptTemplate {
    COMMAND_PALETTE_FILTER("command-palette-filter", """
        System: Ты — навигатор аналитической системы...
        {schema}
        User: {user_query}
        """),
    ANOMALY_EXPLANATION("anomaly-explanation", """
        System: Ты — аналитик маркетплейсов...
        {context}
        """),
    // ... per capability
}
```

Каждый template:
- Фиксированный system prompt с ролью и форматом ответа
- Placeholder-ы для structured data ({context}, {user_query}, etc.)
- Backend заполняет placeholder-ы перед отправкой в LLM
- LLM не имеет доступа к базам данных — только к pre-aggregated данным в промте

---

## Конкурентное позиционирование

### Что есть у конкурентов

| Продукт | AI-фичи | Ограничения |
|---|---|---|
| MPStats | Нет | Только дашборды и графики |
| SellersBoard | Нет | P&L без AI-объяснений |
| Маяк | Нет | Analytics без proactive insights |
| MarketGuru | Нет | Мониторинг без intelligence |

### Чем Datapulse выделяется

| Differentiator | Описание |
|---|---|
| **Natural language access** | Единственный инструмент, где можно спросить «почему упала маржа» и получить ответ с числами |
| **Proactive intelligence** | Система сама находит возможности и проблемы, не ждёт запроса |
| **Explainable AI** | LLM объясняет, а не решает. Все данные прозрачны и сверяемы |
| **Privacy-first** | Self-hosted — данные селлера не уходят в облако OpenAI/Anthropic |
| **Operational, не dashboard** | AI встроен в рабочий процесс (Ctrl+K, Detail Panel, alerts), а не в отдельный «AI-раздел» |

---

## Фазирование

| Фаза | Capabilities | Предусловия |
|---|---|---|
| **E** (Seller Operations) | F1: Smart Command Palette | Грид, фильтры, saved views готовы. LLM container deployed |
| **G** (Intelligence) | F2: Anomaly explanation | Alerts, data quality controls (Phase B) |
| **G** | F3: Weekly digest | Marts, materialization (Phase B) |
| **G** | F4: Pricing advisor | Pricing pipeline, signal assembly (Phase C) |
| **G** | F5: Proactive insights | Marts, alerts, pricing (Phases B-D) |
| **G** | F6: Alert triage | Alerts (Phase B) |
| **G** | F8: Morning briefing | Working queues (Phase E), alerts, marts |
| **G** | F9: Impact narrative | Impact preview (Phase E) |
| **G** | F10: Auto-classification | ETL normalizer, entry_type taxonomy (Phase A) |
| **G+** | F7: Conversational explorer | Все вышеперечисленное + multi-turn session management |

---

## Technical Breakdown (TBD)

**Статус:** не начат

### Предусловия

- [ ] LLM-сервер собран (RTX 3060 12GB + i5 + 16GB RAM + SSD)
- [ ] Docker Compose с GPU support (nvidia-docker)
- [ ] vLLM container запускается и обслуживает Qwen3-8B
- [ ] Operational grid (Phase E) развёрнут
- [ ] Alerts и data quality controls (Phase B) развёрнуты

### Риски реализации

| Риск | Вероятность | Impact | Митигация |
|---|---|---|---|
| Качество русскоязычной генерации | LOW | Плохие объяснения → потеря доверия | Qwen3 — 119 языков, 36 трлн токенов; thinking mode для сложных задач; fallback на шаблонные объяснения; community fine-tune `Qwen3-8B-ru` |
| GPU-сервер стоимость | LOW | Инфраструктурные расходы | Бюджетная сборка ~$430-480 (б/у офисный ПК + RTX 3060 12GB). Inference cost = только электричество (~170W) |
| Hallucination (LLM выдумывает числа) | MED | Ложные данные в UI | LLM получает ТОЛЬКО pre-aggregated данные; числа из prompt, не из генерации. Post-validation: числа в response сверяются с input |
| Prompt injection (пользователь манипулирует через Ctrl+K) | LOW | Нежелательное поведение | Strict output schema (JSON only for F1); input sanitization; output validation |
| Latency F1 (Command Palette) > 1s | LOW | Плохой UX | Qwen3-8B non-thinking mode для F1 (минимальная задержка); shorter prompts; quantization Q4; timeout + fallback |
| vLLM memory leak / OOM | LOW | LLM container падает | Healthcheck + auto-restart; circuit breaker в backend |
| Модель устаревает (новая версия Qwen) | LOW | Потеря конкурентного преимущества | Model swap = одна строка в Docker Compose; prompts не привязаны к модели. Путь роста: 8B → 14B → 30B-A3B (MoE) |

---

## References

- [Frontend Design Direction](../frontend/frontend-design-direction.md) — Ctrl+K Command Palette spec
- [Seller Operations](../modules/seller-operations.md) — Grid, Detail Panel, Working Queues
- [Audit & Alerting](../modules/audit-alerting.md) — Alert events, notifications, WebSocket
- [Pricing](../modules/pricing.md) — Decision explanation, Impact preview
- [Analytics & P&L](../modules/analytics-pnl.md) — Marts, P&L formula, data quality controls
- [Project Vision & Scope](../project-vision-and-scope.md) — Delivery phases, mandatory capabilities
