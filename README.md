# datapulse

Модульная SaaS-платформа (каркас) для интеграций с маркетплейсами (WB/Ozon).
Скелет: WebFlux, Spring Integration, R2DBC Postgres, Liquibase, MapStruct, Lombok, Resilience4j, Micrometer.
Сырые JSON сохраняются на диск и индексируются в БД.

## Быстрый старт
1) Подними Postgres (локально или в Docker) и создай БД/пользователя `datapulse`.
2) Проверь `spring.r2dbc.*` и блок `datapulse.marketplaces.*` в `datapulse-app/src/main/resources/application.yml`.
3) Сборка: `mvn -q -DskipTests package`
4) Запуск: `java -jar datapulse-app/target/datapulse-app-0.1.0-SNAPSHOT.jar`
5) Метрики Prometheus: http://localhost:8080/actuator/prometheus
