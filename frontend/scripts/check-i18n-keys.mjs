import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.join(__dirname, '..');

const ru = JSON.parse(fs.readFileSync(path.join(root, 'src/locale/ru.json'), 'utf8'));

function hasKey(k) {
  if (ru[k] !== undefined) return true;
  const parts = k.split('.');
  let t = ru;
  for (const p of parts) {
    if (t && typeof t === 'object' && p in t) t = t[p];
    else return false;
  }
  return typeof t === 'string';
}

function walk(dir, acc = []) {
  for (const name of fs.readdirSync(dir)) {
    const p = path.join(dir, name);
    const st = fs.statSync(p);
    if (st.isDirectory()) walk(p, acc);
    else if (p.endsWith('.ts') || p.endsWith('.html')) acc.push(p);
  }
  return acc;
}

const files = walk(path.join(root, 'src/app')).filter((f) => {
  const c = fs.readFileSync(f, 'utf8');
  return c.includes('translate');
});

const keys = new Set();
const keyPipe = /['"]([a-z][a-z0-9_.]*)['"]\s*\|\s*translate/gi;
const instantStatic = /\.instant\(\s*['"]([^'"]+)['"]/g;
const getStatic = /\.get\(\s*['"]([^'"]+)['"]/g;

for (const f of files) {
  const c = fs.readFileSync(f, 'utf8');
  let m;
  while ((m = keyPipe.exec(c))) keys.add(m[1]);
  while ((m = instantStatic.exec(c))) keys.add(m[1]);
  while ((m = getStatic.exec(c))) keys.add(m[1]);
}

const missing = [...keys].filter((k) => !hasKey(k) && !k.includes('${')).sort();
console.log('Total unique keys:', keys.size);
console.log('Missing count:', missing.length);
console.log(missing.join('\n'));
