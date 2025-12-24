# Datapulse — Marketplace Analytics SaaS Platform

**Datapulse** is a comprehensive analytical platform designed for marketplace sellers (Wildberries and Ozon) to consolidate fragmented data into a unified, actionable system. It automates data collection from various marketplace portals, providing a "single source of truth" for sales, logistics, finance, and advertising.

## 1. Mission and Business Value
The platform is built to solve the primary "pain points" of multi-marketplace sellers:
* **Data Consolidation:** Eliminates manual tracking in spreadsheets by merging data from different marketplaces and multiple accounts into one interface.
* **P&L Transparency:** Automatically calculates net profit by accounting for all hidden costs, including marketplace commissions (10–25%), logistics, returns, and taxes.
* **Stock Management:** Prevents out-of-stock scenarios using **Days of Cover (DoC)** metrics, which predict how many days current inventory will last based on recent sales trends.
* **Return & Penalty Tracking:** Provides a 360° view of returns and penalties, helping sellers identify product quality issues or shipping errors.

## 2. Technical Stack
* **Backend:** Java 17 (Core) / Java 21 (Target), Spring Boot 3.3.5.
* **Integration:** Spring Integration DSL, Project Reactor (Flux) for asynchronous processing.
* **Resilience:** Resilience4j for handling API limits and failures.
* **Database:** PostgreSQL 17 with Liquibase for schema migrations.
* **Security:** Spring Vault for secure marketplace API credential storage.
* **Messaging:** RabbitMQ for event-driven ETL orchestration.

## 3. Project Architecture
The system is divided into specialized modules to ensure scalability:
* **`datapulse-etl`**: The core ingestion engine. Handles parsing, batch processing, and materialization of data.
* **`datapulse-marketplaces`**: Integration layer for WB/Ozon APIs with custom rate limiting and retry logic.
* **`datapulse-core`**: Contains business logic, account management, and vault services.
* **`datapulse-domain`**: Shared entities, DTOs, and event models.
* **`datapulse-application`**: REST API layer and application entry point.

## 4. Data Warehouse (DWH) Design
Datapulse follows the **"Reference Data First"** principle: directories (warehouses, categories, tariffs) are loaded before transactional data (sales, finance) to ensure referential integrity.

The storage architecture consists of three layers:
1.  **RAW Layer:** Lands raw JSON responses from marketplace APIs into `JSONB` columns without structural loss.
2.  **Data Vault Layer:** Standardizes data into Hubs (Products, Accounts, Warehouses), Links, and Satellites to create "Golden Records" across different platforms.
3.  **EDW (Star Schema):** Final analytical layer with optimized fact tables (`sales_fact`, `inventory_fact`, `finance_fact`) and descriptive dimensions (`product_dim`, `warehouse_dim`).

## 5. ETL Orchestration
The ETL process is asynchronous and event-driven:
* **Message Queues:** Uses RabbitMQ to parallelize tasks like stock updates and sales fetching.
* **Retry & Backoff:** Implements exponential backoff to respect marketplace API rate limits (e.g., WB's 1 request/min for certain endpoints).
* **Data Quality:** Includes automated cross-checks to verify that the sum of individual sales matches the final marketplace payouts.

## 6. Getting Started
To build and run the platform:

```bash
# Build the project (standardizing on Maven)
./mvnw clean package

# Launch infrastructure
docker compose up -d
