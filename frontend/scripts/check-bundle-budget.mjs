import { gzipSync } from 'node:zlib'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const distDirectory = new URL('../dist/', import.meta.url)
const distPath = fileURLToPath(distDirectory)
const manifestPath = new URL('.vite/manifest.json', distDirectory)
const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'))
const entry = Object.entries(manifest).find(([, value]) => value.isEntry)

if (!entry) {
  throw new Error('Vite manifest does not contain an entry chunk.')
}

const visited = new Set()
const files = []

function collect(key) {
  if (visited.has(key)) return
  visited.add(key)

  const chunk = manifest[key]
  if (!chunk) throw new Error(`Missing manifest chunk: ${key}`)
  if (chunk.file.endsWith('.js')) files.push(chunk.file)
  for (const dependency of chunk.imports ?? []) collect(dependency)
}

collect(entry[0])

const measurements = files.map((file) => {
  const bytes = readFileSync(join(distPath, file))
  return { file, rawBytes: bytes.byteLength, gzipBytes: gzipSync(bytes).byteLength }
})
const totalGzipBytes = measurements.reduce((sum, item) => sum + item.gzipBytes, 0)
const budgetBytes = 350 * 1024

console.log(JSON.stringify({ budgetBytes, totalGzipBytes, files: measurements }, null, 2))

if (totalGzipBytes >= budgetBytes) {
  throw new Error(`Initial JavaScript gzip size ${totalGzipBytes} B exceeds ${budgetBytes} B budget.`)
}
