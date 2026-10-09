import { spawnSync } from 'node:child_process'
import { createRequire } from 'node:module'
import fs from 'node:fs'
import path from 'node:path'

// Vitest 4.1.11 can preserve a lowercase Windows drive while Vite normalizes
// module ids to uppercase, loading two runner instances. Launch from the
// filesystem-canonical path so setup hooks and tests share one runner.
const cwd = fs.realpathSync.native(process.cwd())
const require = createRequire(import.meta.url)
const packageJson = require.resolve('vitest/package.json', { paths: [cwd] })
const bin = path.join(path.dirname(fs.realpathSync.native(packageJson)), 'vitest.mjs')

const child = spawnSync(process.execPath, [bin, 'run', ...process.argv.slice(2)], {
  cwd,
  stdio: 'inherit',
})

if (child.error) {
  console.error(child.error.message)
  process.exit(1)
}

process.exit(child.status ?? 1)
