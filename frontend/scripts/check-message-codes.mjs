import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.join(__dirname, '..');
const ru = JSON.parse(fs.readFileSync(path.join(root, 'src/locale/ru.json'), 'utf8'));
const mcPath = path.join(
  root,
  '..',
  'backend',
  'datapulse-common',
  'src',
  'main',
  'java',
  'io',
  'datapulse',
  'common',
  'error',
  'MessageCodes.java',
);
const java = fs.readFileSync(mcPath, 'utf8');
const re = /=\s*"([^"]+)"\s*;/g;
const codes = new Set();
let m;
while ((m = re.exec(java))) {
  if (m[1].includes('.')) codes.add(m[1]);
}
const missing = [...codes].filter((k) => ru[k] === undefined).sort();
console.log('MessageCodes strings:', codes.size);
console.log('Missing in ru.json:', missing.length);
missing.forEach((k) => console.log(k));
