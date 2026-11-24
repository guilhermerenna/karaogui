import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';

const isWindows = process.platform === 'win32';
const ngBin = isWindows ? 'npx.cmd' : 'npx';

const targets = [
  { name: 'tv', color: '\x1b[36m', args: ['ng', 'serve', '--project', 'tv', '--port', '4200', '--host', '0.0.0.0', '--no-open'] },
  { name: 'phone', color: '\x1b[35m', args: ['ng', 'serve', '--project', 'phone', '--port', '4201', '--host', '0.0.0.0', '--no-open'] },
];

const reset = '\x1b[0m';
const children = [];
let shuttingDown = false;

function pipePrefixed(stream, prefix, out) {
  const rl = createInterface({ input: stream });
  rl.on('line', (line) => out.write(`${prefix} ${line}\n`));
}

function shutdown(exitCode = 0) {
  if (shuttingDown) return;
  shuttingDown = true;
  for (const child of children) {
    if (!child.killed) {
      child.kill('SIGTERM');
    }
  }
  setTimeout(() => process.exit(exitCode), 300).unref();
}

for (const target of targets) {
  const child = spawn(ngBin, target.args, {
    stdio: ['ignore', 'pipe', 'pipe'],
    env: { ...process.env, FORCE_COLOR: '1' },
  });
  children.push(child);

  const prefix = `${target.color}[${target.name}]${reset}`;
  pipePrefixed(child.stdout, prefix, process.stdout);
  pipePrefixed(child.stderr, prefix, process.stderr);

  child.on('exit', (code, signal) => {
    if (!shuttingDown) {
      console.error(`${prefix} exited (${signal ?? code ?? 0})`);
      shutdown(code ?? 1);
    }
  });
}

process.on('SIGINT', () => shutdown(0));
process.on('SIGTERM', () => shutdown(0));
