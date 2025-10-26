# Datapulse

Модульная SaaS-платформа для интеграций с маркетплейсами (Wildberries, Ozon).
Технологии: Java 17, Spring Boot 3 (WebFlux), MapStruct, Liquibase (PostgreSQL), Resilience4j, Micrometer.

## Модули
- **datapulse-domain** — DTO, enum’ы, коды сообщений, i18n-конфиг.
- **datapulse-core** — JPA, Liquibase (`db/master.xml`), WebClient/Resilience, криптография (AES-GCM), мапперы/сервисы.
- **datapulse-adapter-marketplaces** — адаптеры провайдеров/маскировка кредов.
- **datapulse-etl** — базис ETL/процессинга.
- **datapulse-application** — REST API, импортирует YAML из модулей (Variant C).

## Требования
- JDK 17+
- Maven 3.9+
- PostgreSQL 14+ (по умолчанию `localhost:5434`, схема `datapulse`)
- Переменная окружения `DATAPULSE_CRYPTO_MASTER_KEY_BASE64` (16/24/32 байта ключа AES в Base64)

## Быстрый старт (локально)
1. **Postgres**
   ```sql
   create database datapulse;
   create user datapulse with password 'datapulse';
   grant all privileges on database datapulse to datapulse;
