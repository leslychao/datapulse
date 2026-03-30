# Архитектурная санация P&L-модели

## 1. Какая проблема реально решается

### Бизнес-задача

P&L-модель Datapulse отвечает на вопрос: **«Сколько я заработал или потерял на каждом товаре / каждой отправке / каждом кабинете / каждом маркетплейсе за период?»**

Специфика: селлер работает на WB и Ozon одновременно, имеет несколько кабинетов, и ни один из маркетплейсов не предоставляет готовый P&L. Финансовые данные разбросаны по разным отчётам, имеют разные форматы, разные знаковые конвенции, разные ключи связывания. Задача системы — собрать их в единую, сверяемую прибыльность.

### Ядро модели

**Ядро — fact_finance с консолидированными финансовыми компонентами.** Все остальные факты, витрины и измерения обслуживают либо заполнение fact_finance, либо его обогащение (COGS, advertising), либо его чтение (marts). P&L — это производная от fact_finance + fact_product_cost + fact_advertising_costs.

---

## 2. Концептуальная модель

### Первичные бизнес-сущности

| Сущность | Роль | Source of truth |
|----------|------|-----------------|
| **Финансовая операция** (finance entry) | Атомарный факт движения денег | Provider finance API → canonical_finance_entry → fact_finance |
| **Себестоимость** (cost profile) | Стоимость единицы товара на момент продажи | Ввод селлером → cost_profile (SCD2) → fact_product_cost |
| **Рекламный расход** (advertising cost) | Дневной расход на кампанию | Provider advertising API → fact_advertising_costs |
| **Товарная карточка** (product / offer) | Идентификация и атрибуция товара | Provider catalog API → canonical catalog → dim_product |

### Source of truth для P&L

| Компонент P&L | Source of truth | Почему |
|---------------|-----------------|--------|
| Revenue, комиссии, логистика, штрафы, хранение, компенсации | **fact_finance** | Финансовый отчёт маркетплейса — единственный authoritative источник |
| COGS | **fact_product_cost** (SCD2) | Себестоимость известна только селлеру |
| Advertising | **fact_advertising_costs** | Отдельный API |
| Quantity (для COGS) | **fact_sales** | Необходимо для расчёта COGS = qty × unit_cost |

---

## 3. Выявленные архитектурные наросты

### 3.1. Избыточная фрагментация финансовых фактов

Рядом с fact_finance существовали 4 компонентных факта: fact_commission, fact_logistics_costs, fact_marketing_costs, fact_penalties. Все они содержали данные, уже присутствующие как measures в fact_finance.

**Причина:** сначала были спроектированы отдельные таблицы по компонентам, потом добавлен fact_finance как spine. Компонентные таблицы остались, хотя fact_finance уже содержал всю информацию.

**Решение:** компонентные факты удалены. fact_finance материализуется напрямую из canonical_finance_entry. Detail drill-down обеспечивается через canonical_finance_entry (PostgreSQL).

### 3.2. Spine pattern — лишняя индирекция

Spine предполагал, что fact_finance собирается из fact_commission + fact_logistics + и т.д. Но все эти данные приходят из одного финансового отчёта одним запросом. Spine оправдан когда данные приходят из РАЗНЫХ источников с РАЗНЫМ timing — здесь это не так.

**Решение:** spine pattern убран. Прямая материализация из canonical_finance_entry.

### 3.3. Naming «order» в mart_order_pnl

Grain — posting/srid (одна отправка), а не покупательский заказ. Один заказ покупателя может содержать несколько posting-ов.

**Решение:** переименовано в `mart_posting_pnl`.

### 3.4. fact_supply как WB-specific артефакт

WB-специфический домен без аналога на Ozon, не участвующий в P&L. Ни одна витрина не зависит от fact_supply.

**Решение:** fact_supply и SUPPLY_FACT event НЕ реализуются в Phase A/B. API контракт задокументирован для Phase G.

### 3.5. fact_orders в зависимостях P&L витрин

fact_orders включён в зависимости mart_order_pnl, но P&L строится на финансовых данных (fact_finance), а не на заказах.

**Решение:** fact_orders сохранён для operational intelligence. Убран из зависимостей P&L-витрин. mart_posting_pnl зависит от fact_finance + fact_sales + fact_product_cost + fact_advertising_costs.

---

## 4. Нарушения архитектурной чистоты (выявленные и исправленные)

| Проблема | Где | Severity | Решение |
|----------|-----|----------|---------|
| Mixed grain в fact_finance (order-linked vs standalone) | fact_finance.posting_id | Низкая | Документировано; standalone операций мало |
| Naming grain confusion | mart_order_pnl | Средняя | Переименовано в mart_posting_pnl |
| Компонентные факты дублируют fact_finance | fact_commission и др. | Средняя | Удалены |
| Spine pattern — лишняя индирекция | fact_finance materialization | Средняя | Убран |
| WB sales/returns из FACT_FINANCE, Ozon из SALES_FACT | ETL event routing | Низкая | Документировано; обусловлено различием API |

---

## 5. Resolved: design questions (2026-03-30)

### Q1: Quantity в fact_finance — НЕ ДОБАВЛЯТЬ

fact_finance и fact_sales имеют фундаментально разный grain. Multi-product posting содержит несколько товаров с разной себестоимостью. Один `quantity` field не содержит товарную разбивку. fact_sales остаётся необходимым для COGS.

### Q2: Detail-level drill-down — canonical_finance_entry (PostgreSQL)

Без component facts drill-down обеспечивается через canonical_finance_entry (PostgreSQL): `SELECT entryType, amount WHERE posting_id = ?`. fact_finance_detail в ClickHouse — потенциальное Phase G extension.

### Q3: fact_supply — Phase G, НЕ Phase A/B

Ни одна витрина не зависит от fact_supply. Lead time — configurable параметр, не computed metric. API контракт задокументирован в `wb-read-contracts.md §8`.

### Q4: Standalone operations — smart allocation

Ozon standalone операции с items[].sku (packaging, disposal) → product lookup → product-level P&L. Без items[] (storage, subscriptions, compensation) → account-level charges, НЕ аллоцируются по товарам. Storage зависит от объёма/веса, не от выручки — pro-rata по revenue = ложная precision.

Поле `product_id` (nullable) добавлено в fact_finance. WB: всегда заполнен (nm_id). Ozon: через items[].sku lookup или NULL.

Формула: `account_P&L = Σ(product_P&L) + account_level_charges`

### Q5: mart_promo_product_analysis — Phase F/G

Promo Guard в pricing (Phase C) читает из canonical state (PostgreSQL), не из ClickHouse. Mart не нужен до Phase F.

### Q6: P&L без рекламы — корректен

advertising_cost = 0 до Phase G. P&L структурно корректен, но неполон. UI предупреждение.

### Q7: WB SPP — НЕ ДОБАВЛЯТЬ отдельный measure

SPP — скидка WB для покупателя, оплачиваемая WB. Продавец получает revenue на основе pre-SPP цены. SPP не является компонентом P&L продавца. reconciliation_residual включает SPP-компенсацию (positive residual ~3-5%).

---

## 6. Что нельзя переносить в новую реализацию

- **Не переносить** компонентные fact-таблицы (fact_commission, fact_logistics_costs, fact_marketing_costs, fact_penalties) — поглощены fact_finance
- **Не переносить** spine pattern — прямая материализация из canonical_finance_entry
- **Не сохранять** нейминг «order» в mart — grain = posting, не покупательский заказ
- **Не воспроизводить** зависимость mart_posting_pnl от fact_orders — P&L = fact_finance
- **Не переносить** fact_supply без подтверждённого потребителя
- **Не воспроизводить** зависимость fact_finance от других financial facts

---

## 7. Вердикт

Текущая модель содержала заметные архитектурные искажения, но они были очищены эволюционно.

- **Ядро модели концептуально здорово:** четырёхслойный pipeline, canonical layer, fact_finance как консолидированный финансовый факт, SCD2 для COGS, reconciliation residual, sign normalization.
- **Основные искажения — accumulation, не corruption:** лишние промежуточные fact-таблицы, spine pattern, нейминг — следы итеративного развития.
- **Очистка выполнена без redesign:** переименование, удаление дублирующих фактов, упрощение materialization.
- **Модель масштабируема:** добавление нового маркетплейса = новый adapter и normalizer, без изменения star schema.

## Связанные документы

- [Архитектура данных](data-architecture.md) — полная модель данных, формула P&L, design decisions
- [Функциональные возможности](functional-capabilities.md) — P&L, inventory intelligence
