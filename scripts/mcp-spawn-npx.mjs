/**
 * Spawn npx -y <...args>. On Windows, Node throws spawn EINVAL when invoking npx.cmd
 * without a shell; use shell: true.
 */
import { spawn } from 'node:child_process';

export function spawnNpx(npxTrailingArgs, env = process.env) {
  const win = process.platform === 'win32';
  return spawn('npx', ['-y', ...npxTrailingArgs], {
    stdio: 'inherit',
    env,
    ...(win ? { shell: true } : {}),
  });
}
