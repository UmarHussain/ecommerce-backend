import { spawnSync } from 'node:child_process'
import { createRequire } from 'node:module'
import fs from 'node:fs'
import path from 'node:path'

// Vitest 4.1.11 keeps the drive-letter case of the path that launched it, while
// Vite normalizes module ids to an uppercase drive. On Windows those are two
// module instances, and setupFiles then call afterEach before a suite exists.
// The filesystem path uses one spelling, so spawn from that path.
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
