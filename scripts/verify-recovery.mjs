import fs from 'node:fs'
import zlib from 'node:zlib'
import crypto from 'node:crypto'
import path from 'node:path'
import { execFileSync } from 'node:child_process'

const snapshotRoot = path.resolve('.tablehub-recovery/20261013-before-security')
const baseline = JSON.parse(fs.readFileSync(path.join(snapshotRoot, 'baseline.json'), 'utf8').replace(/^\uFEFF/, ''))
const hash = (buffer) => crypto.createHash('sha256').update(buffer).digest('hex')
const entries = (archivePath) => {
  const tar = zlib.gunzipSync(fs.readFileSync(archivePath))
  const files = new Map()
  let offset = 0
  while (offset + 512 <= tar.length) {
    const header = tar.subarray(offset, offset + 512)
    if (header.every((byte) => byte === 0)) break
    const string = (start, end) => header.subarray(start, end).toString('utf8').replace(/\0.*$/, '')
    const prefix = string(345, 500)
    const name = (prefix ? prefix + '/' : '') + string(0, 100)
    const length = parseInt(string(124, 136).trim() || '0', 8)
    const type = string(156, 157)
    if (type === '0' || type === '') files.set(name.replace(/^\.\//, ''), tar.subarray(offset + 512, offset + 512 + length))
    offset += 512 + Math.ceil(length / 512) * 512
  }
  return files
}
for (const [name, state] of Object.entries(baseline)) {
  if (!['backend', 'frontend'].includes(name)) continue
  try {
    execFileSync('git', ['-C', state.root, 'merge-base', '--is-ancestor', state.head.trim(), 'HEAD'], { stdio: 'pipe' })
  } catch {
    throw new Error(`${name}: original HEAD is no longer an ancestor of the current branch`)
  }
}
const backend = entries(path.join(snapshotRoot, 'backend-source.tar.gz'))
let checked = 0
for (const [relative, content] of backend) {
  if (!relative.startsWith('sql/') && !relative.startsWith('database/')) continue
  const actual = fs.readFileSync(path.resolve(relative))
  if (hash(actual) !== hash(content)) throw new Error(`Original SQL changed: ${relative}`)
  checked++
}
if (fs.existsSync('database/TableHub.sql')) throw new Error('Pre-existing SQL deletion was unexpectedly restored')
const frontend = entries(path.join(snapshotRoot, 'frontend-source.tar.gz'))
for (const relative of ['package-lock.json', 'src/tablehub-front-v2.code-workspace']) {
  const original = frontend.get(relative)
  if (!original || hash(original) !== hash(fs.readFileSync(path.resolve('../tablehub-front-v2', relative)))) throw new Error(`Pre-existing frontend file changed: ${relative}`)
}
console.log(`Recovery verified: original Git HEADs remain in branch history; ${checked} SQL files and 2 pre-existing frontend files preserved byte-for-byte.`)
