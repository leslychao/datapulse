#!/usr/bin/env node
/**
 * Cursor MCP: reads infra/.env (CH_*), maps to CLICKHOUSE_* for @dmkozloff/clickhouse-mcp.
 * Empty CH_PASSWORD → single space (upstream schema requires min length 1).
 */
import { loadInfraDotEnv } from './mcp-infra-env.mjs';
import { spawnNpx } from './mcp-spawn-npx.mjs';

loadInfraDotEnv();

const host = process.env.CH_HOST || 'localhost';
const port = process.env.CH_PORT || '8123';
const user = process.env.CH_USERNAME || 'default';
const rawPassword = process.env.CH_PASSWORD ?? '';
const password = rawPassword.trim().length > 0 ? rawPassword : ' ';
const database = process.env.CH_DATABASE || 'datapulse';

process.env.CLICKHOUSE_HOST = host;
process.env.CLICKHOUSE_PORT = String(port);
process.env.CLICKHOUSE_USER = user;
process.env.CLICKHOUSE_PASSWORD = password;
process.env.CLICKHOUSE_DATABASE = database;
if (process.env.CLICKHOUSE_SECURE === undefined) {
  process.env.CLICKHOUSE_SECURE = 'false';
}
if (process.env.CLICKHOUSE_VERIFY === undefined) {
  process.env.CLICKHOUSE_VERIFY = 'false';
}

const child = spawnNpx(['@dmkozloff/clickhouse-mcp'], process.env);

child.on('error', (err) => {
  console.error('mcp-clickhouse-launcher: failed to spawn npx:', err.message);
  process.exit(1);
});

child.on('exit', (code, signal) => {
  if (signal) {
    process.exit(1);
  }
  process.exit(code ?? 1);
});
