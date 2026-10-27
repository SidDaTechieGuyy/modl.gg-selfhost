import { build } from 'esbuild';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import process from 'node:process';
const directory = await mkdtemp(join(tmpdir(), 'modl-e2e-'));
try {
  const outfile = join(directory, 'test.cjs');
  await build({ entryPoints: [process.env.E2E_SUITE === 'reset' ? 'tests/factory-reset.test.mjs' : process.env.E2E_SUITE === 'replay' ? 'tests/replay-storage.test.mjs' : process.env.E2E_SUITE === 'usage' ? 'tests/usage-settings.test.mjs' : 'tests/self-hosting.test.mjs'], bundle: true, platform: 'node', format: 'cjs', outfile });
  const result = spawnSync(process.execPath, [outfile], { stdio: 'inherit', env: process.env });
  process.exitCode = result.status ?? 1;
} finally { await rm(directory, { recursive: true, force: true }); }
