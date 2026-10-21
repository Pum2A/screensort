// PostToolUse hook for Edit|Write: formats or lints only the file that just changed.
// Silent on success. On a remaining lint error it exits 2, so a short report goes back to Claude.
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
// Git Bash style paths (/c/Users/...) resolve wrongly on Windows; turn them into C:/Users/...
const toNative = (value) => process.platform === 'win32' ? value.replace(/^\/([A-Za-z])\//, '$1:/') : value;

let input;
try {
  input = JSON.parse(readFileSync(0, 'utf8'));
} catch {
  process.exit(0);
}
const filePath = input?.tool_input?.file_path ?? input?.tool_response?.filePath;
if (!filePath) process.exit(0);

const relative = path.relative(root, path.resolve(toNative(filePath))).split(path.sep).join('/');
if (relative.startsWith('..')) process.exit(0);

function run(command, args, cwd) {
  const result = spawnSync(command, args, { cwd, encoding: 'utf8', shell: process.platform === 'win32' });
  return { status: result.status ?? 1, output: `${result.stdout ?? ''}${result.stderr ?? ''}`.trim() };
}

function report(label, output) {
  const lines = output.split(/\r?\n/).filter(Boolean).slice(0, 20).join('\n');
  process.stderr.write(`${label} reported problems in ${relative}:\n${lines}\n`);
  process.exit(2);
}

const quoted = (value) => `"${value}"`;

if (relative.startsWith('frontend/src-tauri/src/') && relative.endsWith('.rs')) {
  const result = run('rustfmt', ['--edition', '2021', quoted(path.join(root, relative))], root);
  if (result.status !== 0) report('rustfmt', result.output);
} else if (relative.startsWith('frontend/') && /\.(ts|tsx)$/.test(relative)) {
  const target = relative.slice('frontend/'.length);
  const result = run('npx', ['--no-install', 'oxlint', '--quiet', quoted(target)], path.join(root, 'frontend'));
  if (result.status !== 0) report('oxlint', result.output);
} else if (relative.startsWith('analysis-service/') && relative.endsWith('.py')) {
  const target = relative.slice('analysis-service/'.length);
  const result = run('uv', ['run', '--no-sync', 'ruff', 'check', '--fix', '--quiet', quoted(target)], path.join(root, 'analysis-service'));
  if (result.status !== 0) report('ruff', result.output);
}
process.exit(0);
