# MCP (Model Context Protocol) для локальной отладки

В корне проекта — `.cursor/mcp.json`. Секреты **не** хранятся в `mcp.json`: оба сервера стартуют через `node scripts/mcp-*-launcher.mjs`, которые читают **`infra/.env`** (те же `POSTGRES_*` / `DB_*` и `CH_*`, что у Spring). Агент в Cursor может выполнять **read-only** SQL к **PostgreSQL** и **ClickHouse**.

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

Опционально для Postgres можно задать полный URI в **`POSTGRES_MCP_URL`** в `infra/.env` (перекрывает сборку из отдельных переменных).

### Пути и Cursor

В `mcp.json` заданы **`cwd`** и **`${workspaceFolder}`** в `args`, чтобы скрипты находились при открытой папке репозитория. После правок — **полный перезапуск Cursor**.

### ClickHouse и пустой пароль

Пакет требует непустой `CLICKHOUSE_PASSWORD`. Если **`CH_PASSWORD`** в `infra/.env` пустой, launcher подставляет один пробел (как раньше вручную в `mcp.json`).

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
- Коммитить `.cursor/mcp.json` безопасно: пароли только в **`infra/.env`** (в git не попадает).

## Устранение неполадок

### Windows: `spawn EINVAL` в логах MCP

Лаунчеры вызывают `npx` с **`shell: true`** только на Windows: иначе `spawn('npx.cmd', …)` без оболочки даёт `Error: spawn EINVAL` (ограничение Node). После обновления скриптов перезапустите Cursor.

### ClickHouse: `Client closed` / сервер не поднимается

Частая причина — неверный **`CH_PASSWORD`** в `infra/.env` или ClickHouse не слушает `CH_HOST`/`CH_PORT`. Убедитесь, что `infra/.env` существует и совпадает с docker-compose. Пакет требует непустой пароль в переменной окружения; при пустом `CH_PASSWORD` launcher подставляет пробел.

Перезапустите Cursor после правки `infra/.env`.
