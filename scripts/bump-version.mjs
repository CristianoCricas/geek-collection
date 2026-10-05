#!/usr/bin/env node
// Bumps the app version everywhere it is stored.
//   node scripts/bump-version.mjs patch|minor|major [--dry-run]
// Rule: major is bumped by hand, minor on new features/improvements,
// patch on bug fixes and chores (see README).
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const [, , kind = 'patch', ...flags] = process.argv;
const dry = flags.includes('--dry-run');
if (!['major', 'minor', 'patch'].includes(kind)) {
  console.error(`Tipo inválido: ${kind}. Use major, minor ou patch.`);
  process.exit(2);
}

const versionFile = resolve(root, 'VERSION');
const current = readFileSync(versionFile, 'utf8').trim();
const m = current.match(/^(\d+)\.(\d+)\.(\d+)$/);
if (!m) {
  console.error(`VERSION inválido: ${current}`);
  process.exit(2);
}
let [major, minor, patch] = m.slice(1).map(Number);
if (kind === 'major') { major += 1; minor = 0; patch = 0; }
if (kind === 'minor') { minor += 1; patch = 0; }
if (kind === 'patch') patch += 1;
const next = `${major}.${minor}.${patch}`;

const edits = [
  [versionFile, () => `${next}\n`],
  [resolve(root, 'web/js/version.js'), (s) => s.replace(/VERSION = '[^']*'/, `VERSION = '${next}'`)],
  [resolve(root, 'web/sw.js'), (s) => s.replace(/const VERSION = '[^']*'/, `const VERSION = '${next}'`)],
  [resolve(root, 'web/package.json'), (s) => s.replace(/"version": "[^"]*"/, `"version": "${next}"`)],
];
for (const [file, fn] of edits) {
  const before = readFileSync(file, 'utf8');
  const after = fn(before);
  if (before === after && file !== versionFile) console.warn(`aviso: nada mudou em ${file}`);
  if (!dry) writeFileSync(file, after);
}
console.log(dry ? `${current} -> ${next} (dry run)` : next);
