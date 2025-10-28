# Datapulse

Модульная платформа для интеграции с маркетплейсами и построения витрин/аналитики: коннекторы, базовые ETL-пайплайны, модели хранения, алерты и экспорт. Вендор-агностичная архитектура и готовность к мультиплатформенным интеграциям.

## Архитектура и модули

Моно-репозиторий Maven с подпроектами:
- **datapulse-domain** — общие DTO/enum, коды сообщений, i18n.
- **datapulse-core** — база сервисов/мапперов, доступ к БД, Liquibase, шифрование чувствительных полей, интеграционная инфраструктура (WebClient/Resilience и др.).
- **datapulse-marketplaces** — адаптеры маркетплейсов (Wildberries, Ozon и др.), унификация заголовков/аутентификации, маскирование/шифрование кредов.
- **datapulse-etl** — базис для задач ETL и процессинга.
- **datapulse-application** — REST-приложение (Spring Boot), собирает конфиг из модулей и предоставляет API.

## Технологический стек

- Java 17, Spring Boot 3  
- MapStruct — маппинг DTO ↔ Entity  
- Liquibase (PostgreSQL)  
- Resilience4j / Micrometer  
- AES-шифрование чувствительных данных  

## Требования

- **JDK** 17+  
- **Maven** 3.9+ (есть `mvnw`)  
- **PostgreSQL** 14+  
- **Ключ шифрования**:  
  `DATAPULSE_CRYPTO_MASTER_KEY_BASE64` — Base64-ключ AES (16/24/32 байта) для шифрования токенов и паролей.

## Быстрый старт

```bash
# подготовить БД
CREATE DATABASE datapulse;
CREATE USER datapulse WITH PASSWORD 'datapulse';
GRANT ALL PRIVILEGES ON DATABASE datapulse TO datapulse;

# экспортировать ключ шифрования
export DATAPULSE_CRYPTO_MASTER_KEY_BASE64="BASE64_КЛЮЧ_ЗДЕСЬ"

# собрать
./mvnw -T 1C -DskipTests package

# запустить
java -jar datapulse-application/target/datapulse-application-*.jar
```

Файлы `common.http` и `http-client.env.json` содержат примеры запросов API (IntelliJ HTTP Client).

## Конфигурация

- `spring.datasource.*` — подключение к PostgreSQL  
- `spring.liquibase.*` — миграции схемы  
- `DATAPULSE_CRYPTO_MASTER_KEY_BASE64` — ключ AES  

## Docker

```bash
docker build -t datapulse:local .
docker run --rm   -e DATAPULSE_CRYPTO_MASTER_KEY_BASE64=BASE64_КЛЮЧ   -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/datapulse   -e SPRING_DATASOURCE_USERNAME=datapulse   -e SPRING_DATASOURCE_PASSWORD=datapulse   -p 8080:8080 datapulse:local
```

## Основные возможности

- Учётные записи и подключения аккаунтов к маркетплейсам  
- Интеграционные клиенты (WB/Ozon) с rate-limit и bulkhead  
- ETL-обвязка для загрузки/нормализации данных  
- REST API для CRUD и бизнес-операций  
- Метрики и готовность к алертингу  

## Планы

- Новые коннекторы маркетплейсов  
- Расширение ETL и экспортов  
- Алертинг/нотификации  
- BI/дашборды поверх витрины  

## Лицензия

(Добавьте `LICENSE`, если планируется открытая лицензия)
