#!/usr/bin/env node
/**
 * Cursor MCP bridge: builds a PostgreSQL URL from infra/.env + process env and runs
 * @modelcontextprotocol/server-postgres.
 *
 * Limitation: upstream MCP lists table resources only for schema "public"; app tables
 * are in datapulse. Use the query tool with explicit schema (datapulse.job_execution) or
 * information_schema.
 */
import { loadInfraDotEnv } from './mcp-infra-env.mjs';
import { spawnNpx } from './mcp-spawn-npx.mjs';

function buildDatabaseUrl() {
  loadInfraDotEnv();

  const explicit = process.env.POSTGRES_MCP_URL?.trim();
  if (explicit) {
    return explicit;
  }

  const host = process.env.DB_HOST || 'localhost';
  const port = process.env.DB_PORT || '5432';
  const database = process.env.POSTGRES_DB || 'datapulse';
  const user = process.env.POSTGRES_USER || 'datapulse';
  const password = process.env.POSTGRES_PASSWORD || '';
  const schema = process.env.DB_SCHEMA || 'datapulse';

  const userPart = encodeURIComponent(user);
  const auth =
      password.length > 0 ? `${userPart}:${encodeURIComponent(password)}@` : `${userPart}@`;

  const searchPathOpt = encodeURIComponent(`-csearch_path=${schema}`);
  return `postgresql://${auth}${host}:${port}/${database}?options=${searchPathOpt}`;
}

const databaseUrl = buildDatabaseUrl();

const child = spawnNpx(['@modelcontextprotocol/server-postgres', databaseUrl]);

child.on('error', (err) => {
  console.error('mcp-postgres-launcher: failed to spawn npx:', err.message);
  process.exit(1);
});

child.on('exit', (code, signal) => {
  if (signal) {
    process.exit(1);
  }
  process.exit(code ?? 1);
});
