# Datapulse — Marketplace Analytics Platform

⚠️ **PROPRIETARY SOFTWARE — NO USAGE RIGHTS**

Datapulse is proprietary software owned exclusively by **Vitalii Kim**.
This repository is provided for **read-only review** purposes only.
Any use is prohibited, including running, compiling, deploying, modifying, integrating,
or using it in any business or operational context.

See [LICENSE](./LICENSE).

---

**Datapulse** is a proprietary analytical platform for marketplace sellers (Wildberries and Ozon).
It consolidates fragmented operational and financial data into a single, consistent analytical
system and formalizes end-to-end data ingestion, normalization, and profit calculation.

The platform is designed as a **single source of truth** for sales, inventory, logistics,
finance, advertising, and penalties across multiple marketplaces and seller accounts.

---

## 1. Mission and Business Value

Datapulse addresses the core operational and analytical challenges faced by multi-marketplace sellers:

### Data Consolidation
Aggregates data from multiple marketplaces and seller accounts into a unified model,
eliminating manual reconciliation in spreadsheets and reducing operational errors.

### P&L Transparency
Calculates net profit by accounting for cost components, including:
- marketplace commissions;
- logistics and fulfillment costs;
- returns and refunds;
- penalties and adjustments;
- taxes and settlements.

### Inventory Management
Supports inventory control using **Days of Cover (DoC)** metrics, estimating how long current
inventory will last based on recent sales velocity.

### Returns and Penalty Analysis
Provides an analytical view of returns and penalties to detect product quality issues,
logistics failures, or incorrect marketplace configurations.

---

## 2. Technology Stack

- Backend: Java 17 (current) / Java 21 (target), Spring Boot 3.x
- Integration: Spring Integration (Java DSL), Project Reactor (Flux)
- Database: PostgreSQL
- Schema Management: Liquibase
- Security: Spring Vault for secure storage of marketplace API credentials
- Messaging: RabbitMQ for event-driven ETL orchestration

---

## 3. Project Architecture

Datapulse is organized into separated modules with strict responsibility boundaries:

- `datapulse-etl`  
  Core ingestion and transformation engine (parsing, batching, validation, materialization).

- `datapulse-marketplaces`  
  Marketplace integration layer (Wildberries, Ozon): API clients and protocol normalization.

- `datapulse-core`  
  Core business logic, account management, access control, Vault integration.

- `datapulse-domain`  
  Shared domain model: DTOs, events, enums, and value objects.

- `datapulse-application`  
  REST API layer and application entry point.

---

## 4. Data Warehouse Design

Datapulse follows the **Reference Data First** principle:
reference directories (products, warehouses, categories, tariffs) are ingested before transactional
data to improve consistency.

The storage architecture consists of three logical layers:

### RAW Layer
Stores original marketplace API responses in JSONB format without structural loss.
Used for traceability, reprocessing, and audit purposes.

### Data Vault Layer
Normalizes data into Hubs, Links, and Satellites to form consistent records across multiple marketplaces
and seller accounts.

### Enterprise Data Warehouse (EDW)
Final analytical layer implemented as a star schema with fact tables and descriptive dimensions.
Optimized for analytical queries and reporting.

---

## 5. ETL Orchestration

The ETL pipeline is asynchronous and event-driven:

- Message-Based Execution  
  RabbitMQ is used to parallelize independent ingestion and transformation tasks.

- Retry and Backoff  
  Rate limiting and retry policies are applied to comply with marketplace API constraints.

- Data Quality Controls  
  Automated checks validate financial consistency and reconciliation against marketplace payouts.

---

## License

DataPulse is proprietary software owned exclusively by Vitalii Kim.

This repository is provided for **read-only review** purposes only.
No rights are granted to use, run, compile, deploy, modify, integrate, or distribute the software.

See [LICENSE](./LICENSE).
