# Datapulse — Marketplace Analytics Platform

**Datapulse** is a proprietary analytical platform for marketplace sellers (Wildberries and Ozon).
It consolidates fragmented operational and financial data into a single, consistent analytical
system and automates end-to-end data ingestion, normalization, and profit calculation.

The platform is designed as a **single source of truth** for sales, inventory, logistics,
finance, advertising, and penalties across multiple marketplaces and seller accounts.

---

## 1. Mission and Business Value

Datapulse addresses the core operational and analytical challenges faced by
multi-marketplace sellers:

### Data Consolidation
Aggregates data from multiple marketplaces and seller accounts into a unified model,
eliminating manual reconciliation in spreadsheets and reducing operational errors.

### P&L Transparency
Automatically calculates net profit by accounting for all cost components, including:
- marketplace commissions (typically 10–25%);
- logistics and fulfillment costs;
- returns and refunds;
- penalties and adjustments;
- taxes and settlements.

### Inventory Management
Prevents out-of-stock and overstock scenarios using **Days of Cover (DoC)** metrics,
which estimate how long current inventory will last based on recent sales velocity.

### Returns and Penalty Analysis
Provides a complete analytical view of returns and penalties, enabling early detection
of product quality issues, logistics failures, or incorrect marketplace configurations.

---

## 2. Technology Stack

- **Backend:** Java 17 (current) / Java 21 (target), Spring Boot 3.3.x
- **Integration:** Spring Integration (Java DSL), Project Reactor (Flux)
- **Resilience:** Resilience4j (rate limiting, retries, circuit breakers)
- **Database:** PostgreSQL 17
- **Schema Management:** Liquibase
- **Security:** Spring Vault for secure storage of marketplace API credentials
- **Messaging:** RabbitMQ for event-driven ETL orchestration

---

## 3. Project Architecture

Datapulse is organized into clearly separated modules to ensure scalability,
maintainability, and strict responsibility boundaries:

- **`datapulse-etl`**  
  Core ingestion and transformation engine.  
  Responsible for parsing, batching, validation, and materialization of incoming data.

- **`datapulse-marketplaces`**  
  Marketplace integration layer (Wildberries, Ozon).  
  Handles API clients, rate limiting, retries, and protocol normalization.

- **`datapulse-core`**  
  Core business logic, account management, access control, and Vault integration.

- **`datapulse-domain`**  
  Shared domain model: DTOs, events, enums, and value objects.

- **`datapulse-application`**  
  REST API layer and application entry point.

---

## 4. Data Warehouse Design

Datapulse follows the **“Reference Data First”** principle:
reference directories (products, warehouses, categories, tariffs) are ingested
before transactional data to guarantee referential integrity.

The storage architecture consists of three logical layers:

### RAW Layer
Stores original marketplace API responses in `JSONB` format without structural loss.
Used for traceability, reprocessing, and audit purposes.

### Data Vault Layer
Normalizes data into Hubs, Links, and Satellites to form consistent **Golden Records**
across multiple marketplaces and seller accounts.

### Enterprise Data Warehouse (EDW)
Final analytical layer implemented as a star schema with:
- fact tables (`sales_fact`, `inventory_fact`, `finance_fact`);
- descriptive dimensions (`product_dim`, `warehouse_dim`, `account_dim`, etc.).

This layer is optimized for analytical queries and reporting.

---

## 5. ETL Orchestration

The ETL pipeline is fully asynchronous and event-driven:

- **Message-Based Execution:**  
  RabbitMQ is used to parallelize independent ingestion and transformation tasks.

- **Retry and Backoff:**  
  Exponential backoff and rate limiting are applied to comply with marketplace API
  constraints (e.g., strict request limits on certain Wildberries endpoints).

- **Data Quality Controls:**  
  Automated checks validate financial consistency, including reconciliation of
  individual transactions with final marketplace payouts.

---

## 6. Getting Started

### Build

```bash
./mvnw clean package
```

### Infrastructure

```bash
docker compose up -d
```

---

## License

DataPulse is a proprietary, single-vendor commercial software product.

This repository is provided for limited evaluation and review purposes only.
No commercial, production, or internal business use is permitted without a
separate written agreement.

See the LICENSE file for full terms.
