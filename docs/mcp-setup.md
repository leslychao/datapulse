# MCP (Model Context Protocol) для локальной отладки

В корне проекта — `.cursor/mcp.json` (dev без паролей в БД). Шаблон с плейсхолдерами: **`.cursor/mcp.json.example`** (скопируйте в `mcp.json` и подставьте значения из `infra/.env`). Агент в Cursor может выполнять **read-only** SQL к **PostgreSQL** и **ClickHouse**.

## Что подключено

| Сервер | Пакет | Назначение |
|--------|--------|------------|
| `datapulse-postgres` | `@modelcontextprotocol/server-postgres` | Запросы к БД приложения, схема `datapulse` |
| `datapulse-clickhouse` | `@dmkozloff/clickhouse-mcp` | `SELECT` / `SHOW` / `DESCRIBE` к аналитике (`mart_*`, `fact_*`, `dim_*`) |

ClickHouse-сервер принудительно **readonly** (только безопасные запросы).

## Параметры по умолчанию

Совпадают с дефолтами `backend/datapulse-api/src/main/resources/application.yml`:

- PostgreSQL: `localhost:5432`, БД `datapulse`, пользователь `datapulse`, **пустой пароль**, `search_path=datapulse`.
- ClickHouse: `localhost:8123`, пользователь `default`, БД `datapulse`, без TLS (`CLICKHOUSE_SECURE=false`).

Если у вас заданы `POSTGRES_PASSWORD` или `CH_PASSWORD`, отредактируйте `.cursor/mcp.json` вручную (строка подключения Postgres или `CLICKHOUSE_PASSWORD` в `env`).

### Стек с `infra/.env` (как в `docker-compose.local.yml`)

Cursor **не подхватывает** `infra/.env` для MCP автоматически: переменные нужно **один раз перенести** в конфиг MCP вручную или держать отдельный локальный файл, который не коммитится.

1. **PostgreSQL** — в `args` последним аргументом идёт URI. Формат:
   `postgresql://POSTGRES_USER:POSTGRES_PASSWORD@DB_HOST:DB_PORT/POSTGRES_DB?options=-csearch_path%3DDB_SCHEMA`  
   Если в пароле есть символы `@`, `:`, `/`, `#` и т.п., их нужно **URL-encode** в компоненте пароля.

2. **ClickHouse** — в блоке `env` для `datapulse-clickhouse` задайте `CLICKHOUSE_PASSWORD` значением из **`CH_PASSWORD`** в вашем `.env`. Пакет требует непустой строки (`min(1)`); если пароль в окружении пустой, используйте один пробел `" "` (см. ниже).

3. После правки — перезапуск Cursor.

Имеет смысл держать **редактируемую только у себя** копию настроек MCP (например `%USERPROFILE%\.cursor\mcp.json` для пользовательских серверов или локальный override), а в репозитории — шаблон без секретов.

## Как включить

1. Установите Node.js 18+ (для `npx`).
2. Убедитесь, что подняты PostgreSQL и ClickHouse с теми же хостами/портами.
3. Перезапустите Cursor, чтобы подхватить MCP.
4. В настройках Cursor: **Tools & MCP** — серверы `datapulse-postgres` и `datapulse-clickhouse` должны быть в состоянии connected.

## Примеры запросов (ClickHouse)

```sql
SELECT period, seller_sku_id, product_id, attribution_level, revenue_amount
FROM mart_product_pnl
WHERE period = 202403
SETTINGS final = 1;
```

```sql
SELECT count() AS c FROM mart_posting_pnl SETTINGS final = 1;
```

## Безопасность

- Файл **`infra/.env`** уже в `.gitignore` — не добавляйте его в git и не вставляйте содержимое (пароли, токены Vault, OAuth, почты) в тикеты, чаты и публичные репозитории. При утечке — **смените пароли и токены** в инфраструктуре.
- Не коммитьте `.cursor/mcp.json`, если в нём прописаны реальные `POSTGRES_PASSWORD` / `CH_PASSWORD` — используйте шаблон в репозитории и секреты только локально.

## Устранение неполадок

### ClickHouse: `Client closed` / сервер не поднимается

Пакет `@dmkozloff/clickhouse-mcp` в `env.js` задаёт **`CLICKHOUSE_PASSWORD` как обязательное поле с `min(1)`** — пустая строка `""` в конфиге Cursor **не подходит**, процесс падает при старте (в логах MCP нет текста ошибки, только «Client closed»).

Для локального ClickHouse с пользователем `default` и **пустым паролем** в `.cursor/mcp.json` задан один пробел: `"CLICKHOUSE_PASSWORD": " "` (см. репозиторий). Если запросы к CH начинают отвечать ошибкой аутентификации — укажите реальный пароль вместо пробела.

Перезапустите Cursor после правки `mcp.json`.
