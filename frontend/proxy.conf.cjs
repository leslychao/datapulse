const fs = require('node:fs');
const path = require('node:path');

function readInfraEnv() {
  const envPath = path.resolve(__dirname, '..', 'infra', '.env');
  if (!fs.existsSync(envPath)) {
    return {};
  }

  const lines = fs.readFileSync(envPath, 'utf8').split(/\r?\n/);
  const values = {};

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) {
      continue;
    }
    const delimiterIndex = trimmed.indexOf('=');
    if (delimiterIndex <= 0) {
      continue;
    }
    const key = trimmed.slice(0, delimiterIndex).trim();
    const value = trimmed.slice(delimiterIndex + 1).trim();
    values[key] = value;
  }

  return values;
}

const infraEnv = readInfraEnv();
const proxyTarget =
  process.env.NG_PROXY_TARGET ??
  process.env.PUBLIC_EDGE_URL ??
  infraEnv.PUBLIC_EDGE_URL;

if (!proxyTarget) {
  throw new Error(
    'Proxy target is not configured. Set NG_PROXY_TARGET or PUBLIC_EDGE_URL in infra/.env.',
  );
}

module.exports = {
  '/api': {
    target: proxyTarget,
    secure: false,
    changeOrigin: true,
  },
  '/ws': {
    target: proxyTarget,
    secure: false,
    changeOrigin: true,
    ws: true,
  },
  '/oauth2': {
    target: proxyTarget,
    secure: false,
    changeOrigin: true,
  },
  '/auth': {
    target: proxyTarget,
    secure: false,
    changeOrigin: true,
  },
};
