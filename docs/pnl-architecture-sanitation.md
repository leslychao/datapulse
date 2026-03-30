# Архитектурная санация P&L-модели

## 1. Какая проблема реально решается

### Бизнес-задача

P&L-модель Datapulse отвечает на вопрос: **«Сколько я заработал или потерял на каждом товаре / каждой отправке / каждом кабинете / каждом маркетплейсе за период?»**

Специфика: селлер работает на WB и Ozon одновременно, имеет несколько кабинетов, и ни один из маркетплейсов не предоставляет готовый P&L. Финансовые данные разбросаны по разным отчётам, имеют разные форматы, разные знаковые конвенции, разные ключи связывания. Задача системы — собрать их в единую, сверяемую прибыльность.

### Основные аналитические ответы

1. **P&L по SKU за период** — сколько заработано на конкретном товаре после всех расходов.
2. **P&L по отправке (posting)** — полная экономика одной транзакции.
3. **P&L по кабинету / маркетплейсу** — агрегат для управленческого контроля.
4. **Breakdown расходов** — где теряются деньги: комиссия, логистика, штрафы, хранение, реклама.
5. **Reconciliation residual** — расхождение между выплатой маркетплейса и суммой вычисленных компонентов.
6. **Сигналы для pricing** — средние ставки комиссии, логистики, return rate — как входы для ценообразования.

### Ядро модели

**Ядро — fact_finance с консолидированными финансовыми компонентами.** Все остальные факты, витрины и измерения обслуживают либо заполнение fact_finance, либо его обогащение (COGS, advertising), либо его чтение (marts). P&L — это производная от fact_finance + fact_product_cost + fact_advertising_costs.

---

## 2. Какая концептуальная модель выглядит правильной

### Первичные бизнес-сущности

| Сущность | Роль | Source of truth |
|----------|------|-----------------|
| **Финансовая операция** (finance entry) | Атомарный факт движения денег: продажа, комиссия, логистика, штраф, компенсация | Provider finance API → canonical_finance_entry → fact_finance |
| **Себестоимость** (cost profile) | Стоимость единицы товара на момент продажи | Ввод селлером → cost_profile (SCD2) → fact_product_cost |
| **Рекламный расход** (advertising cost) | Дневной расход на кампанию | Provider advertising API → fact_advertising_costs |
| **Товарная карточка** (product / offer) | Идентификация и атрибуция товара | Provider catalog API → canonical catalog → dim_product |

### Какие события должны быть зафиксированы

Для P&L важны три класса событий:

1. **Финансовые транзакции маркетплейса** — основной поток. Включает revenue, все виды удержаний, компенсации. Это то, что маркетплейс выплачивает или удерживает.
2. **Факт продажи с количеством** — необходим для расчёта COGS (quantity × unit_cost). Количество проданных единиц не присутствует в финансовых транзакциях как отдельная мера.
3. **Рекламный расход** — приходит из отдельного API, имеет другой grain (кампания × день), аллоцируется на товары пропорционально revenue.

### Source of truth для P&L

| Компонент P&L | Source of truth | Почему |
|---------------|-----------------|--------|
| Revenue, комиссии, логистика, штрафы, хранение, компенсации | **fact_finance** (из provider finance API) | Финансовый отчёт маркетплейса — единственный authoritative источник расчётов с селлером |
| COGS | **fact_product_cost** (SCD2, из ввода селлера) | Себестоимость известна только селлеру |
| Advertising | **fact_advertising_costs** (из advertising API) | Рекламные расходы приходят из отдельного API |
| Quantity (для COGS) | **fact_sales** или fact_finance (если обогатить) | Необходимо для расчёта COGS = qty × unit_cost |

---

## 3. Что в текущей архитектуре выглядит здравым и должно быть сохранено

### 3.1. Четырёхслойный pipeline (Raw → Normalized → Canonical → Analytics)

**Что это.** Строгая последовательность: immutable raw (S3) → typed DTO (in-process) → canonical PostgreSQL → star schema ClickHouse.

**Почему правильно.** Каждый слой имеет чёткую ответственность, нет пропусков стадий. Raw обеспечивает replay и forensics. Normalized изолирует provider-specific parsing. Canonical стирает различия между маркетплейсами. Analytics обеспечивает производительность OLAP-запросов.

**Почему не legacy artifact.** Это осознанная архитектурная декомпозиция, соответствующая data vault / medallion подходам. Каждый слой отвечает на свой вопрос: «что пришло?», «что это значит в терминах провайдера?», «что это значит в бизнес-терминах?», «как это агрегировать?».

### 3.2. Canonical State vs Canonical Flow

**Что это.** Разделение canonical layer на State (текущее состояние: цены, остатки, каталог) и Flow (поток событий: заказы, продажи, возвраты, финансы).

**Почему правильно.** State и Flow имеют фундаментально разную семантику: State читается для текущих решений (pricing), Flow пишется для исторической аналитики. State живёт в PostgreSQL и читается прямо. Flow пишется в PostgreSQL как source of truth, аналитические агрегаты — из ClickHouse.

**Почему не legacy artifact.** Это domain-driven разделение, а не технический компромисс.

### 3.3. Каталожная иерархия product_master → seller_sku → marketplace_offer

**Что это.** Трёхуровневая иерархия товара: внутренний товар (cross-marketplace) → артикул продавца → предложение на конкретном маркетплейсе.

**Почему правильно.** Обеспечивает cross-marketplace P&L по товару (один product_master на WB и Ozon). Себестоимость привязана к seller_sku (логично — cost одинакова вне зависимости от маркетплейса). marketplace_offer хранит marketplace-specific identifiers.

**Почему не legacy artifact.** Это доменная необходимость для multi-marketplace business.

### 3.4. fact_finance как консолидированная финансовая витрина

**Что это.** Единая fact-таблица со всеми финансовыми компонентами P&L в виде measures (revenue, commission, logistics, penalties, etc.).

**Почему правильно.** Одна таблица — один запрос для P&L. Spine pattern гарантирует, что все компоненты привязаны к одному ключу (posting_id + account_id + finance_date). reconciliation_residual вычисляется как net_payout − Σ(компоненты).

**Почему не legacy artifact.** Это правильный финальный результат. Проблема не в fact_finance, а в том, что РЯДОМ с ней существуют избыточные промежуточные факты.

### 3.5. SCD2 для себестоимости

**Что это.** cost_profile с valid_from / valid_to, привязка COGS к моменту продажи.

**Почему правильно.** Себестоимость меняется. Если товар стоил 500₽ в январе и 600₽ в марте, продажа в январе должна учитывать 500₽. SCD2 — стандартный паттерн для этого.

### 3.6. Reconciliation residual как явная метрика

**Что это.** `reconciliation_residual = net_payout − Σ(все компоненты P&L)`.

**Почему правильно.** Признаёт, что маппинг провайдерских данных неполный (новые типы операций, SPP-компенсация у WB). Вместо того чтобы скрывать расхождения, модель их явно отслеживает. Это обеспечивает сверяемость P&L.

### 3.7. Знаковая нормализация на уровне adapter

**Что это.** WB (все значения положительные, семантика по имени поля) и Ozon (signed values) нормализуются к единой конвенции (positive = credit, negative = debit) при ingestion.

**Почему правильно.** Canonical layer не должен знать о provider-specific конвенциях. Нормализация на границе — anti-corruption layer в действии.

---

## 4. Признаки архитектурных наростов / legacy residue

### 4.1. Избыточная фрагментация финансовых фактов

**Что выглядит проблемным.** Рядом с fact_finance существуют 4 компонентных факта: fact_commission, fact_logistics_costs, fact_marketing_costs, fact_penalties. Все они содержат данные, которые уже присутствуют как measures в fact_finance.

**Почему это похоже на исторический артефакт.** Вероятный сценарий: сначала были спроектированы отдельные таблицы по компонентам расходов, потом пришло понимание, что для P&L нужна единая таблица — и был добавлен fact_finance как spine над ними. Но компонентные таблицы остались, хотя fact_finance уже содержит всю информацию.

**Что искажает.** Создаёт иллюзию, что fact_finance ЗАВИСИТ от компонентных фактов (spine pattern). На самом деле, все данные идут из одного источника (provider finance API → canonical_finance_entry). Spine — это not a join of independent facts, это join фрагментов одного и того же исходного набора данных.

**Что делать.** Компонентные факты (fact_commission, fact_logistics_costs, fact_marketing_costs, fact_penalties) можно убрать. fact_finance материализуется напрямую из canonical_finance_entry. Если нужен drill-down до отдельных сервисных операций (например, «покажи все логистические списания по этому posting»), это обеспечивается canonical_finance_entry в PostgreSQL или дополнительным detail-level фактом (fact_finance_detail), а не отдельной таблицей на каждый тип расхода.

### 4.2. Spine pattern — лишняя индирекция

**Что выглядит проблемным.** Описание spine: «UNION keys from all fact tables per account → final SELECT с COALESCE». Это предполагает, что fact_finance собирается из fact_commission + fact_logistics + и т.д. Но все эти факты материализуются из одного ETL event (FACT_FINANCE) и одного canonical entity (canonical_finance_entry).

**Почему это похоже на исторический артефакт.** Spine pattern оправдан, когда данные приходят из РАЗНЫХ источников с РАЗНЫМ timing (например, commission из одного API, logistics из другого). В данном случае все компоненты приходят из одного финансового отчёта одним запросом.

**Что искажает.** Усложняет materialization pipeline (нужно сначала записать 4 компонентных факта, потом собрать из них fact_finance). Создаёт риск рассинхронизации между компонентными фактами и fact_finance.

**Что делать.** Материализовать fact_finance напрямую из canonical_finance_entry. Один проход, одна запись. Spine pattern убрать.

### 4.3. Нейминг «order» в mart_order_pnl

**Что выглядит проблемным.** Таблица называется mart_order_pnl, но grain — posting/srid (одна отправка), а не покупательский заказ. Один заказ покупателя может содержать несколько posting-ов (Ozon) или несколько строк (WB).

**Почему это похоже на исторический артефакт.** Вероятно, начиналось с идеи «P&L по заказу», но быстро стало ясно, что grain должен быть posting (самый мелкий уровень, на котором маркетплейс выдаёт финансовые данные). Название осталось.

**Что искажает.** Вводит в заблуждение при чтении архитектуры и в UI. «P&L по заказу» воспринимается как P&L по покупательскому заказу, а не по отправке.

**Что делать.** Переименовать в `mart_posting_pnl` или `mart_transaction_pnl`.

### 4.4. fact_supply как WB-specific артефакт

**Что выглядит проблемным.** fact_supply (поставки / WB incomes) существует как отдельный факт. Это WB-специфический домен, не имеющий аналога на Ozon и не участвующий в P&L.

**Почему это похоже на исторический артефакт.** WB API для incomes был доступен и прост для ingestion, поэтому данные были загружены и материализованы.

**Что искажает.** Загрязняет star schema фактом, не имеющим отношения к основной аналитической модели.

**Что делать.** Оставить в scope inventory analysis (если поставки используются для расчёта lead time / days of cover). Не включать в P&L-scope. Если fact_supply не используется ни одной витриной — убрать.

### 4.5. Дублирование posting_id и operation_id в fact_finance

**Что выглядит проблемным.** fact_finance использует posting_id как основной ключ, но для операций без привязки к заказу (storage, subscriptions, compensation) используется operation_id как fallback. Одно поле — два значения.

**Почему это похоже на исторический артефакт.** Изначально fact_finance был спроектирован для order-linked операций (каждая строка = один posting). Позже добавились standalone операции, и posting_id был перегружен.

**Что искажает.** Смешение grain: order-linked (posting grain) и standalone (operation grain) в одной таблице с одним ключом. Аналитические запросы должны учитывать, что «posting_id» может быть не posting.

**Что делать.** Ввести явное разделение: добавить `transaction_key` (posting_number для order-linked, operation_id для standalone) и `transaction_type` (ORDER_LINKED / STANDALONE). Или оставить posting_id с документацией fallback-семантики, если standalone операций мало (хранение, подписки — десятки записей в месяц).

### 4.6. fact_orders — fact в основном для operational intelligence, не для P&L

**Что выглядит проблемным.** fact_orders включён в зависимости mart_order_pnl, но P&L строится на финансовых данных (fact_finance), а не на заказах. Заказ — это intent, а не финансовый факт.

**Почему это похоже на исторический артефакт.** На ранних этапах заказы казались основой P&L. Позже пришло понимание, что P&L строится на финансовых отчётах маркетплейса, а не на заказах.

**Что искажает.** mart_order_pnl зависит от fact_orders, хотя основные данные берёт из fact_finance. Это создаёт лишний join и dependency.

**Что делать.** Сохранить fact_orders для operational intelligence (order funnel, conversion). Убрать из прямых зависимостей P&L-витрин. mart_posting_pnl должен зависеть от fact_finance + fact_sales (для quantity) + fact_advertising_costs + fact_product_cost.

---

## 5. Анализ по слоям

### 5.1 Raw-слой

**Каким должен быть.** Immutable, source-faithful хранилище исходных JSON-ответов от API маркетплейсов. Единственная задача — обеспечить replay и forensic traceability.

**Что правильно:**
- Хранение в S3-compatible storage — правильный выбор для immutable blobs.
- SHA-256 дедупликация — предотвращает дублирование.
- job_item как index в PostgreSQL — связывает raw payload с execution context.
- Configurable retention (keep_count = 3 последних снапшотов) — баланс между traceability и стоимостью.

**Что лишнее / неверное:**
- Ничего лишнего не обнаружено. Raw-слой чистый.

**Где raw оторван от реального provider contract:**
- Нет отрыва. Raw хранит ровно то, что вернул provider API. Нормализация начинается на следующем слое.

**Что требует уточнения:**
- Стратегия хранения large payloads (WB finance report может быть 100K+ строк) — хранить как один blob или chunk-ами. Текущее решение не описано.
- Retention policy для flow-данных (финансы, заказы) vs state-данных (каталог, цены) — одинаковый keep_count=3 может быть недостаточен для финансов (нужен более длинный retention для audit).

### 5.2 Fact-слой

**Какие facts действительно нужны для P&L:**

| Fact | Необходим для P&L | Grain | Обоснование |
|------|-------------------|-------|-------------|
| **fact_finance** | Да — центральный | posting × date | Все финансовые компоненты P&L |
| **fact_sales** | Да — для COGS | posting × product | Quantity для расчёта COGS = qty × unit_cost |
| **fact_advertising_costs** | Да — для ad allocation | campaign × product × date | Отдельный источник данных, отдельный grain |
| **fact_product_cost** | Да — для COGS | product × validity period (SCD2) | Себестоимость единицы товара |

**Какие facts нужны для других аналитических задач (не P&L):**

| Fact | Назначение | Grain |
|------|------------|-------|
| **fact_orders** | Order funnel, conversion, operational monitoring | posting × date |
| **fact_returns** | Return rate analysis, причины возвратов | return × date |
| **fact_price_snapshot** | Ценовая история | product × captured_at |
| **fact_inventory_snapshot** | Складская аналитика, days of cover | product × warehouse × captured_at |
| **fact_promo_product** | Эффективность промо | product × campaign |

**Какие facts избыточны / вторичны:**

| Fact | Статус | Проблема |
|------|--------|----------|
| **fact_commission** | Избыточен | Данные полностью содержатся в fact_finance.marketplace_commission_amount. Для drill-down используй canonical_finance_entry |
| **fact_logistics_costs** | Избыточен | Данные в fact_finance.logistics_cost_amount |
| **fact_marketing_costs** | Избыточен | Данные в fact_finance.marketing_cost_amount |
| **fact_penalties** | Избыточен | Данные в fact_finance.penalties_amount |
| **fact_supply** | Не P&L | WB-specific, не участвует в P&L. Оценить необходимость для inventory analysis |

**Где grain выбран неудачно или смешан:**
- fact_finance: mixed grain (order-linked vs standalone) — см. раздел 4.5. Приемлемо при документировании, но не идеально.
- fact_sales: grain отличается между WB и Ozon. WB sales факты заполняются из FACT_FINANCE event (финансовый отчёт), Ozon — из SALES_FACT (delivered postings). Это означает, что fact_sales для WB — это фактически subset данных из financial report, а для Ozon — из posting API. Grain одинаковый (posting × product), но timing материализации разный. Это документировано, но создаёт implicit coupling.

**Где нарушен source of truth:**
- Pricing signal assembler читает `fact_commission / fact_sales` для avg commission rate. Но если fact_commission убрать, те же данные доступны из `fact_finance.marketplace_commission_amount / fact_finance.revenue_amount`. Source of truth не нарушен, но query paths нужно обновить.

### 5.3 Dimensional-слой

**Какие dimensions действительно нужны:**

| Dimension | Необходима | Обоснование |
|-----------|------------|-------------|
| **dim_product** | Да | Основной аналитический разрез: бренд, категория, marketplace IDs. Без dim_product невозможна аналитика по товарам |
| **dim_category** | Да | Иерархическая категория для roll-up агрегаций |

**Какие доменно оправданы, но не критичны для P&L:**

| Dimension | Оправдана | Обоснование |
|-----------|-----------|-------------|
| **dim_warehouse** | Для inventory analysis | Не участвует напрямую в P&L. Нужна для inventory intelligence (days of cover по складам) |
| **dim_promo_campaign** | Для promo analysis | Не участвует в P&L calculation, но нужна для promo effectiveness |

**Какие выглядят как workaround:**
- Явных workaround-dimensions не обнаружено. Отсутствие dim_account и dim_marketplace (account_id и source_platform денормализованы в facts) — осознанное решение, не workaround.

**Где дублирование смысла с facts или marts:**
- dim_product дублирует каталожные данные из PostgreSQL (product_master, seller_sku, marketplace_offer). Это стандартное star schema поведение (dimension replication для performance), не дефект.

### 5.4 Mart-слой

**Какие marts действительно нужны:**

| Mart | Необходим | Обоснование |
|------|-----------|-------------|
| **mart_order_pnl** (→ переименовать в mart_posting_pnl) | Да | Основная P&L витрина на уровне отправки. Агрегирует fact_finance + COGS + advertising |
| **mart_product_pnl** | Да | P&L по товару за период. Агрегация из mart_posting_pnl |
| **mart_inventory_analysis** | Да (не P&L) | Inventory intelligence: days of cover, stock-out risk |
| **mart_returns_analysis** | Да (не P&L) | Return rate analysis, penalty breakdown |

**Какие marts выглядят как нормальные read-модели:**
- Все текущие marts — нормальные read-модели. Они содержат pre-computed агрегаты для быстрого чтения.

**Какие marts компенсируют проблемы нижних слоев:**
- mart_promo_product_analysis — **требует уточнения**. Если promo data достаточна в fact_promo_product + dim_promo_campaign, mart нужен. Если нет — это placeholder для будущего функционала.

**Где mart содержит бизнес-логику, которая должна быть ниже:**
- **Advertising allocation** (pro-rata по revenue share) вычисляется в mart_posting_pnl. Это правильное место — аллокация — это аналитическое решение, не исходный факт.
- **COGS join** (fact_sales × fact_product_cost с SCD2 привязкой к sale_ts) выполняется при построении mart. Это правильное место — COGS computation — это derived metric.

---

## 6. Где сейчас нарушена архитектурная чистота модели

### Grain consistency

| Проблема | Где | Severity |
|----------|-----|----------|
| Mixed grain в fact_finance (order-linked vs standalone) | fact_finance.posting_id | Низкая — standalone операций мало, документировано |
| Naming grain confusion (mart_order_pnl ≠ order) | mart_order_pnl | Средняя — вводит в заблуждение |
| WB sales материализуются из finance report, Ozon — из postings | fact_sales | Низкая — grain одинаковый, источник разный |

### Ownership of business meaning

| Проблема | Где | Severity |
|----------|-----|----------|
| Компонентные факты (fact_commission и др.) дублируют ответственность fact_finance | fact_commission, fact_logistics_costs, fact_marketing_costs, fact_penalties | Средняя — confusion «откуда читать?» |

### Duplication of metrics

| Проблема | Где | Severity |
|----------|-----|----------|
| commission amount хранится и в fact_commission, и в fact_finance | fact_commission + fact_finance | Средняя — два source, один truth |
| logistics cost хранится и в fact_logistics_costs, и в fact_finance | fact_logistics_costs + fact_finance | Средняя |
| penalties хранятся и в fact_penalties, и в fact_finance | fact_penalties + fact_finance | Средняя |
| marketing cost хранится и в fact_marketing_costs, и в fact_finance | fact_marketing_costs + fact_finance | Низкая (мало данных) |

### Multiple sources of truth

- **Нет критичных нарушений.** Source of truth чётко определён: canonical PostgreSQL → facts ClickHouse → marts. Проблема только в том, что один и тот же truth дублируется в нескольких fact-таблицах.

### Confusing joins

| Проблема | Где | Severity |
|----------|-----|----------|
| mart_order_pnl зависит от fact_orders, хотя P&L-данные берёт из fact_finance | mart_order_pnl dependencies | Низкая — лишний join, но не ломает |
| Spine pattern создаёт join chain: fact_commission + fact_logistics + ... → fact_finance | fact_finance materialization | Средняя — лишняя индирекция |

### Derived values stored too low or too high

| Проблема | Где | Severity |
|----------|-----|----------|
| Нет выявленных. revenue_amount хранится в fact (правильно), P&L вычисляется в mart (правильно), COGS join — в mart (правильно) | — | — |

### Coupling between ingestion shape and analytical shape

| Проблема | Где | Severity |
|----------|-----|----------|
| WB sales/returns заполняются в FACT_FINANCE handler, а не в SALES_FACT | ETL event routing | Низкая — обусловлено provider contract (WB finance report = source of truth для финансовых данных, включая sales). Это не coupling — это адаптация к реальности API |
| Separate ETL events для returns: Ozon в SALES_FACT, WB в FACT_FINANCE | ETL event routing | Низкая — документировано, обусловлено различием API |

---

## 7. Как должна выглядеть очищенная целевая архитектура P&L

### Слои

```
Raw (S3)
  ↓ immutable JSON, provider-faithful
Normalized (in-process)
  ↓ typed DTO, provider-specific
Canonical (PostgreSQL)
  ↓ marketplace-agnostic entities (State + Flow)
Analytics (ClickHouse)
  ├── Dimensions: dim_product, dim_category, [dim_warehouse, dim_promo_campaign]
  ├── Facts: fact_finance, fact_sales, fact_returns, fact_orders,
  │          fact_advertising_costs, fact_product_cost,
  │          fact_price_snapshot, fact_inventory_snapshot,
  │          [fact_promo_product]
  └── Marts: mart_posting_pnl, mart_product_pnl,
             [mart_inventory_analysis, mart_returns_analysis]
```

### Назначение каждого слоя

| Слой | Назначение | Хранит |
|------|------------|--------|
| Raw | Replay, forensics, immutable evidence | Исходные JSON payloads от API |
| Normalized | Type coercion, format normalization, sign normalization | In-process DTO (не персистируется) |
| Canonical | Marketplace-agnostic business truth | Текущее состояние + поток событий |
| Analytics — Facts | Historical events, atomic facts | Append-only факты с sorting key |
| Analytics — Dimensions | Attributes for slicing | Slowly-changing descriptors |
| Analytics — Marts | Pre-computed read models | Derived aggregates |

### Ключевые facts для P&L

| Fact | Grain | Назначение в P&L | Источник |
|------|-------|-------------------|----------|
| **fact_finance** | posting × date (order-linked) или operation × date (standalone) | Все финансовые компоненты: revenue, commission, logistics, storage, penalties, marketing, acceptance, other charges, compensation, refund, net_payout, residual | canonical_finance_entry → direct materialization |
| **fact_sales** | posting × product | Quantity для COGS calculation | canonical_sale |
| **fact_advertising_costs** | campaign × product × day | Advertising cost для pro-rata allocation | advertising API |
| **fact_product_cost** | product × validity period (SCD2) | Unit cost для COGS | cost_profile |

### Ключевые dimensions

| Dimension | Назначение |
|-----------|------------|
| **dim_product** | Product attributes: name, brand, category, seller_sku, marketplace IDs |
| **dim_category** | Category hierarchy для roll-up |

### Ключевые marts для P&L

| Mart | Grain | Формула | Зависимости |
|------|-------|---------|-------------|
| **mart_posting_pnl** | posting × date | revenue − commission − acquiring − logistics − storage − penalties − acceptance − marketing − other_charges − refund − COGS − advertising_allocation + compensation | fact_finance, fact_sales (qty), fact_product_cost (COGS), fact_advertising_costs (allocation) |
| **mart_product_pnl** | product × period | Σ(mart_posting_pnl) по продукту за период | mart_posting_pnl |

### Что должно вычисляться где

| Вычисление | Где | Почему |
|------------|-----|--------|
| Sign normalization (WB → canonical) | Adapter/Normalizer | Anti-corruption boundary |
| Aggregation по posting (Ozon: multiple operations → one row) | Materializer (canonical → fact_finance) | Grain alignment |
| COGS (qty × unit_cost with SCD2) | mart_posting_pnl | Derived from two facts |
| Advertising allocation (pro-rata) | mart_posting_pnl | Allocation rule, not raw data |
| reconciliation_residual | fact_finance | net_payout − Σ(компоненты) — вычисляется при materialization |
| P&L формула | mart_posting_pnl | Итоговый derived metric |

### Что является первичной правдой, а что — производным представлением

| Уровень | Первичная правда | Производное |
|---------|-----------------|-------------|
| Raw | Да — immutable evidence | — |
| Canonical | Да — business truth (marketplace-agnostic) | Производное от raw/normalized |
| fact_finance | Первичная аналитическая правда (финансовые компоненты) | Производное от canonical_finance_entry |
| fact_sales, fact_product_cost | Первичные аналитические факты (qty, cost) | Производные от canonical/cost_profile |
| mart_posting_pnl | Производное | Производное от facts |
| mart_product_pnl | Производное | Производное от mart_posting_pnl |

---

## 8. Очищенный целевой набор таблиц

### Raw

| Сущность | Назначение | Storage | Grain / Key |
|----------|------------|---------|-------------|
| Raw JSON payloads | Immutable provider responses | S3 | (request_id, source_id, record_key) |
| job_item (index) | Raw layer index, dedup, provenance | PostgreSQL | (job_execution_id, record_key) |

### Facts

| Сущность | Назначение | Grain / Key | Почему нужна |
|----------|------------|-------------|--------------|
| **fact_finance** | Consolidated финансовые компоненты P&L | (account_id, source_platform, posting_id, finance_date) | Центральный факт P&L. Все финансовые measures. Материализуется напрямую из canonical_finance_entry |
| **fact_sales** | Количество проданных единиц, sale amount | (account_id, source_platform, posting_id, product_id) | Необходим для COGS = quantity × unit_cost |
| **fact_returns** | Операционные данные возвратов: причина, дата, количество | (account_id, source_platform, return_id) | Необходим для return rate analysis, причины возвратов |
| **fact_orders** | Заказы / отправления: количество, статус | (account_id, source_platform, posting_id, order_date) | Operational intelligence: order funnel, conversion. Не участвует напрямую в P&L |
| **fact_advertising_costs** | Рекламные расходы per campaign per product per day | (account_id, source_platform, campaign_id, product_id, date) | Pro-rata allocation в P&L. Отдельный API-источник |
| **fact_product_cost** | Себестоимость единицы товара (SCD2) | (product_id, valid_from, valid_to) | COGS calculation с привязкой к моменту продажи |
| **fact_price_snapshot** | Исторические снимки цен | (account_id, product_id, captured_at) | Ценовая история, ценовые тренды |
| **fact_inventory_snapshot** | Снимки остатков | (account_id, product_id, warehouse_id, captured_at) | Inventory intelligence, days of cover |
| **fact_promo_product** | Участие товаров в промо-акциях | (account_id, product_id, campaign_id) | Promo effectiveness analysis |

**Убраны по сравнению с текущей моделью:**
- ~~fact_commission~~ → поглощён fact_finance.marketplace_commission_amount
- ~~fact_logistics_costs~~ → поглощён fact_finance.logistics_cost_amount
- ~~fact_marketing_costs~~ → поглощён fact_finance.marketing_cost_amount
- ~~fact_penalties~~ → поглощён fact_finance.penalties_amount
- ~~fact_supply~~ → оценить необходимость для inventory; если не используется витринами — убрать

### Dimensions

| Сущность | Назначение | Grain / Key | Почему нужна |
|----------|------------|-------------|--------------|
| **dim_product** | Товар: название, бренд, категория, marketplace IDs, seller_sku | (account_id, source_platform, product_id) | Основной аналитический разрез. Без dim_product невозможна аналитика по товарам |
| **dim_category** | Иерархия категорий | (category_id, parent_id) | Roll-up агрегации по категориям |
| **dim_warehouse** | Склады: тип, название, location | (warehouse_id, source_platform) | Аналитика остатков по складам |
| **dim_promo_campaign** | Промо-кампании: даты, тип, название | (campaign_id, source_platform) | Promo effectiveness |

### Marts

| Сущность | Назначение | Grain / Key | Зависимости | Почему нужна |
|----------|------------|-------------|-------------|--------------|
| **mart_posting_pnl** | P&L по отправке | (account_id, source_platform, posting_id, date) | fact_finance, fact_sales, fact_product_cost, fact_advertising_costs | Основная витрина P&L. Позволяет видеть экономику каждой транзакции |
| **mart_product_pnl** | P&L по продукту за период | (account_id, source_platform, product_id, period) | mart_posting_pnl | Агрегированный P&L для управленческих решений |
| **mart_inventory_analysis** | Inventory intelligence | (account_id, product_id, warehouse_id) | fact_inventory_snapshot, fact_sales, fact_product_cost | Days of cover, stock-out risk, frozen capital |
| **mart_returns_analysis** | Returns & penalties analysis | (account_id, product_id) | fact_returns, fact_finance (penalties_amount), fact_sales | Return rate trends, penalty breakdown |

---

## 9. Что нельзя переносить в новую реализацию

- **Не переносить** компонентные fact-таблицы (fact_commission, fact_logistics_costs, fact_marketing_costs, fact_penalties) как отдельные сущности — их данные должны быть measures в fact_finance.
- **Не переносить** spine pattern для fact_finance — материализовать напрямую из canonical_finance_entry.
- **Не сохранять** нейминг «order» в mart_order_pnl — переименовать в mart_posting_pnl (grain = posting, не покупательский заказ).
- **Не воспроизводить** зависимость mart_posting_pnl от fact_orders — P&L строится на fact_finance, не на заказах.
- **Не переносить** fact_supply без подтверждённого потребителя (витрины или сигнала).
- **Не воспроизводить** идею, что fact_finance зависит от других financial facts — fact_finance должен быть primary, а не derived.

---

## 10. Минимальный план перехода от текущей модели к очищенной

### Шаг 1: Переименование mart_order_pnl → mart_posting_pnl

- Косметическое изменение, ноль риска.
- Обновить документацию, DDL, код materializer.
- Нулевой impact на данные.

### Шаг 2: Прямая материализация fact_finance из canonical_finance_entry

- Переписать materializer: canonical_finance_entry → fact_finance напрямую, без промежуточных component facts.
- Для Ozon: агрегация по posting_number (multiple operations → one row) в materializer.
- Для WB: один row reportDetailByPeriod → один row fact_finance (split по measure columns).
- Проверить, что все measures fact_finance заполняются корректно.
- reconciliation_residual вычисляется inline.

### Шаг 3: Перенаправление pricing signal queries

- Avg commission: `SUM(marketplace_commission_amount) / SUM(revenue_amount)` from fact_finance вместо `fact_commission / fact_sales`.
- Avg logistics: `SUM(logistics_cost_amount) / COUNT(DISTINCT posting_id)` from fact_finance вместо `fact_logistics_costs / fact_orders`.
- Return rate: `COUNT(DISTINCT refund posting_id) / COUNT(DISTINCT sale posting_id)` from fact_finance, или оставить `fact_returns / fact_sales`.
- Протестировать с реальными данными, что значения совпадают.

### Шаг 4: Удаление компонентных fact-таблиц

- Убрать fact_commission, fact_logistics_costs, fact_marketing_costs, fact_penalties.
- Убрать spine pattern.
- Если нужен drill-down до отдельных сервисных операций (какие конкретно логистические списания были по этому posting) — добавить fact_finance_detail (grain = individual operation) или использовать canonical_finance_entry в PostgreSQL.
- Обновить граф зависимостей ETL.

### Шаг 5: Пересмотр зависимостей mart_posting_pnl

- Убрать зависимость от fact_orders.
- Оставить зависимости: fact_finance, fact_sales (qty), fact_product_cost (COGS), fact_advertising_costs (allocation).
- Проверить, что COGS calculation корректен (quantity из fact_sales × unit_cost из fact_product_cost с SCD2 match по sale_ts).

### Шаг 6: Оценка fact_supply

- Проверить, используется ли fact_supply в mart_inventory_analysis или другой витрине.
- Если да — оставить, документировать как inventory-specific fact.
- Если нет — удалить.

---

## 11. Resolved: open design questions (2026-03-30)

### Q1: Quantity в fact_finance — RESOLVED: НЕ ДОБАВЛЯТЬ

**Проблема:** Если добавить quantity в fact_finance, можно избежать join с fact_sales для COGS.

**Анализ:** fact_finance и fact_sales имеют фундаментально разный grain:

| Fact | Grain | Пример |
|------|-------|--------|
| `fact_finance` | posting × date (один row = финансовые компоненты отправки) | posting "87621408-0010-1": revenue=257₽ |
| `fact_sales` | posting × product (один row = один товар в отправке) | posting ..., Product A: qty=3; Product B: qty=2 |

Multi-product posting содержит несколько товаров с разной себестоимостью. fact_finance хранит суммарный revenue, для COGS нужно `3 × cost_A + 2 × cost_B`. Один `quantity` field не содержит товарную разбивку.

Ozon finance `items[]` содержит `sku + name`, но **не содержит quantity** — оно доступно только в posting API. Обогащение потребует тот же lookup.

**Решение:** fact_sales остаётся необходимым для COGS. Оптимизация: pre-compute `cogs_amount` при материализации mart_posting_pnl (один проход по fact_sales + fact_product_cost → записать cogs_amount в mart).

---

### Q2: Detail-level drill-down — RESOLVED: canonical_finance_entry (PostgreSQL)

**Проблема:** Без component facts — как показать «все логистические списания по posting»?

**Анализ:** Drill-down = operational query (точечный lookup), а не analytical aggregation.

| Тип запроса | Store |
|-------------|-------|
| "P&L по SKU за Q1" (агрегация тысяч postings) | ClickHouse (fact_finance + marts) |
| "Breakdown логистики posting X по сервисам" (точечный lookup) | PostgreSQL (canonical_finance_entry) |

canonical_finance_entry хранит каждую финансовую операцию с `entryType` и `amount`. `SELECT entryType, amount WHERE posting_id = ?` → полный breakdown. PostgreSQL обеспечивает sub-ms lookup по indexed posting_id. Объём ~100K entries/month per account — trivial.

**Решение:**
- Уровень 1 (агрегат): fact_finance (ClickHouse) → mart_posting_pnl
- Уровень 2 (detail): canonical_finance_entry (PostgreSQL) → drill-down по клику
- fact_finance_detail в ClickHouse — НЕ нужен для Phase A/B. Phase G extension при необходимости analytical aggregation по типам сервисов.

---

### Q3: fact_supply — RESOLVED: Phase G, НЕ Phase A/B

**Проблема:** Есть ли потребители fact_supply?

**Анализ:** текущие зависимости mart_inventory_analysis: `fact_inventory_snapshot, fact_sales, fact_product_cost` — fact_supply не используется. Ни одна витрина не зависит от fact_supply.

| Capability | Нужен fact_supply? | Phase |
|-----------|-------------------|-------|
| Days of Cover (`stock / avg_sales`) | Нет | B |
| Lead Time | **Потенциально** (Phase G). Но в текущей модели = configurable параметр (ввод селлера), не computed metric | G |
| Reorder Point | Через lead_time (configurable) | G |

**Ключевые факты:**
- WB `/api/v1/supplier/incomes` — sandbox-verified, контракт задокументирован (wb-read-contracts.md §8)
- Endpoint deprecated June 2026 (R-14)
- fact_supply — WB-only, нет аналога на Ozon → нарушает marketplace-agnostic принцип star schema
- Lead time определён как configurable parameter (functional-capabilities.md), не как computed signal

**Решение:**
1. fact_supply и SUPPLY_FACT event **НЕ реализуются в Phase A/B**
2. API контракт **задокументирован** (wb-read-contracts.md §8) — готов для Phase G
3. **Phase G:** если потребуется computed lead time → реализовать тогда (до June 2026 deprecation)
4. **НЕ включать** в P&L-витрины (не P&L fact)
5. Если computed lead time не потребуется — селлер вводит lead time вручную

Согласовано с `pnl-sanitation-resolved-decisions.md` §Q3.

---

### Q4: Standalone operations — RESOLVED: SMART ALLOCATION

**Проблема:** Storage, subscriptions, compensations не имеют posting_id.

**Анализ операций без posting (эмпирические данные Ozon, Jan 2025):**

| Операция | Кол-во/мес | items[].sku? | Привязка |
|----------|-----------|-------------|----------|
| Storage (`OperationMarketplaceServiceStorage`) | 22 | **Нет** | Period-level |
| Packaging (`OperationElectronicServiceStencil`) | 134 | **Да** | Per-item |
| Disposal (`DisposalReasonXxx`) | 3-4 | **Да** | Per-item |
| Subscriptions (`StarsMembership`) | 5 | Нет | Account |
| Compensation (`AccrualInternalClaim` и др.) | 1-2 | Нет | Account |

**WB:** НЕ имеет standalone операций. Все суммы (storage_fee, penalty, acceptance) = поля внутри строки reportDetailByPeriod, привязанной к nm_id/srid. WB allocates per-item by design.

**Решение — smart allocation:**

| Тип операции | items[].sku? | Allocation | mart_product_pnl |
|-------------|-------------|------------|-------------------|
| Packaging, disposal | **Да** | По sku → product lookup | Product-level P&L |
| Storage, subscriptions, compensation | Нет | **Account-level** (НЕ аллоцируется) | Отдельная строка «Account-level charges» |

**Почему НЕ pro-rata allocation для storage:**
Storage зависит от объёма/веса товаров на складе, НЕ от выручки. Pro-rata по revenue = ложная precision (крупный дешёвый товар занимает много места, но имеет низкую revenue). Честнее показать storage как account overhead.

**Следствие для fact_finance:** добавить `product_id` (nullable):
- WB: `nm_id` (всегда заполнен)
- Ozon order-linked: через `items[].sku → catalog lookup`
- Ozon standalone с items[]: через `items[].sku → catalog lookup`
- Ozon standalone без items[]: NULL

**Формула:** `account_P&L = Σ(product_P&L) + account_level_charges`

UI: posting P&L = order-linked only. Product P&L = attributable only. Account = всё.

Согласовано с `pnl-sanitation-resolved-decisions.md` §Q4.

---

### Q5: mart_promo_product_analysis — RESOLVED: Phase F/G PLACEHOLDER

**Проблема:** Нужна ли витрина на текущем scope?

**Анализ:**
- Data dependency: fact_promo_product ← PROMO_SYNC ← Promo APIs (ingestion pipeline не реализован)
- Phase: Promo intelligence = Phase F (Simulation) / Phase G (Intelligence)
- Impact of deferring: нулевой — P&L, pricing, inventory не зависят от promo mart
- Promo Guard (Phase C): читает promo participation из canonical state (PostgreSQL), НЕ из ClickHouse. Работает без mart.

**Решение:**
1. mart_promo_product_analysis = **Phase F/G**, не создаётся в Phase A/B
2. dim_promo_campaign + fact_promo_product = **Phase F**, DDL не генерируются
3. PROMO_SYNC event — оставить в графе, реализовать в Phase F
4. Promo Guard в pricing (Phase C) не зависит от этого mart

---

### Q6: Advertising data readiness — RESOLVED: P&L БЕЗ РЕКЛАМЫ КОРРЕКТЕН

**Проблема:** WB Ads API = NEEDS MIGRATION, Ozon Performance = отдельный OAuth2 host.

**Анализ:**
- Phase scope: Advertising = Phase G. Phase A/B/C не включают ads ingestion
- P&L formula: `advertising_cost` = один из 13 компонентов. При отсутствии данных = 0
- Impact: P&L завышает прибыль на сумму рекламных расходов. Для крупных рекламодателей (>10% от revenue) — значимо
- reconciliation_residual НЕ поймает advertising (отдельный API, не часть finance report)
- Signal assembler: `ad_cost_ratio` fallback → 0% (ignore)

**Решение:**
1. P&L без рекламы = **допустимо** для Phase A/B/C
2. UI: показывать предупреждение "Рекламные расходы не подключены"
3. Phase G: добавить ads ingestion, P&L автоматически включит advertising_cost
4. Pricing (Phase C): `include_ad_cost = true` + fallback → manual value / 0%. Explanation: "рекламные расходы: 0% (данные не подключены)"
5. Ретроактивный пересчёт: при подключении ads можно пересчитать marts за историю
6. **Не является архитектурным дефектом** — осознанная phased delivery

---

### Q7: WB SPP delta — RESOLVED: НЕ ДОБАВЛЯТЬ отдельный measure

**Проблема:** Нужен ли `spp_compensation_amount`?

**Анализ финансового потока SPP:**

```
retail_price_withdisc_rub = 1400₽  (после скидки продавца, ДО SPP)
ppvz_spp_prc = 15%                 (SPP — скидка WB для покупателя)
buyer_pays ≈ 1190₽                 (что платит покупатель)

Финансовый поток:
  ppvz_for_pay рассчитывается от 1400₽, НЕ от 1190₽
  → SPP delta компенсируется WB из собственных средств
  → Продавец не теряет на SPP
  → SPP НЕ создаёт отдельной финансовой операции
```

reconciliation_residual: весь расчёт идёт от pre-SPP цены (`retail_price_withdisc_rub`), SPP delta НЕ попадает в residual.

**Решение:**
1. **НЕ добавлять** `spp_compensation_amount` в P&L. SPP не является компонентом P&L продавца
2. **Хранить** `ppvz_spp_prc` в canonical_finance_entry (JSONB metadata) — для UI
3. **UI (Phase E):** SPP% как информационный бейдж: "SPP: -15%, покупатель видит ≈1190₽"
4. **Pricing (Phase G):** SPP влияет на конверсию — входной сигнал для velocity-based стратегий

---

## 12. Итоговый вердикт

**Текущая модель содержит заметные архитектурные искажения, но может быть очищена эволюционно.**

Обоснование:

- **Ядро модели концептуально здорово:** четырёхслойный pipeline, canonical layer, fact_finance как консолидированный финансовый факт, SCD2 для COGS, reconciliation residual, sign normalization — всё это правильные архитектурные решения, основанные на реальной доменной модели.

- **Основные искажения — accumulation, не corruption:** лишние промежуточные fact-таблицы, spine pattern, нейминг — это следы итеративного развития (сначала component facts, потом fact_finance поверх них), а не фундаментальные ошибки. Данные не потеряны и не искажены.

- **Очистка возможна без redesign:** все шаги — переименование, удаление дублирующих фактов, упрощение materialization — можно делать инкрементально, не ломая текущую работу.

- **Модель масштабируема:** добавление нового маркетплейса потребует нового adapter и normalizer, но не изменения star schema. Это признак правильного canonical layer.

Главный риск при очистке — pricing signal assembler, который сейчас читает из component facts. При удалении component facts нужно перенаправить queries на fact_finance. Это тестируемое изменение с проверяемым результатом.
