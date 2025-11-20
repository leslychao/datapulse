# Datapulse — README

## О проекте
Datapulse — SaaS‑платформа для продавцов маркетплейсов (Wildberries, Ozon, Яндекс Маркет), объединяющая данные продаж, аналитики, логистики и рекламы в единую систему.

## Основные возможности
- Единый ETL‑пайплайн для всех маркетплейсов.
- Автоматическое скачивание и обработка снапшотов.
- Юнит‑экономика и прозрачный P&L.
- Аналитика возвратов, штрафов, остатков и конкурентов.
- Автоматизация управления ценами и рекламой.
- Поддержка мультиаккаунтов и мультиплатформенности.

## Архитектура
- **datapulse-etl** — ingestion, парсинг, batch‑обработка, materialization.
- **datapulse-marketplaces** — интеграции с API (WB/Ozon/YM).
- **datapulse-core** — бизнес‑логика и обработка данных.
- **datapulse-domain** — типы, DTO, события.
- **datapulse-api** — REST‑слой.
- **datapulse-admin** — админка и настройки.

## Технологии
- Java 21, Spring Boot 3, Spring Integration DSL.
- Reactor, FluxMessageChannel.
- MapStruct.
- PostgreSQL 17 + Liquibase.
- Kubernetes, Docker, GitLab CI.

## Запуск
```bash
./gradlew clean build
docker compose up -d
```

## Тестирование
- Unit‑тесты на критические части ETL.
- Integration‑тесты на materialization.
- E2E — через API /api/etl/run.
