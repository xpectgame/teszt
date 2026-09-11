// A mealpilot/ könyvtár oldalait beépíti a Workerbe (src/pages.ts).
//
// Miért beágyazva: így a jogi oldalak ugyanazzal a deployjal frissülnek, mint a
// szolgáltatás, és nem kell hozzájuk se külön tárhely, se külön domain. Pár tíz
// kilobájt, a Worker méretkorlátjához képest elhanyagolható.
//
// Futtatás:  npm run pages        (a deploy magától meghívja)

import { readdirSync, readFileSync, writeFileSync, statSync } from 'node:fs'
import { join, relative, extname } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = fileURLToPath(new URL('.', import.meta.url))
const siteDir = join(here, '..', '..', 'mealpilot')
const outFile = join(here, '..', 'src', 'pages.ts')

const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.txt': 'text/plain; charset=utf-8',
}

function walk(dir) {
  const out = []
  for (const name of readdirSync(dir).sort()) {
    const full = join(dir, name)
    if (statSync(full).isDirectory()) out.push(...walk(full))
    else out.push(full)
  }
  return out
}

const files = walk(siteDir).filter((f) => TYPES[extname(f)])
if (files.length === 0) throw new Error(`Nincs kiszolgálható fájl itt: ${siteDir}`)

const entries = files.map((full) => {
  const path = relative(siteDir, full).split('\\').join('/')
  const body = readFileSync(full, 'utf8')
  return { path, type: TYPES[extname(full)], body }
})

const escape = (s) => s.replace(/\\/g, '\\\\').replace(/`/g, '\\`').replace(/\$\{/g, '\\${')

const header = `/**
 * GENERÁLT FÁJL — ne szerkeszd kézzel.
 *
 * Forrás: a repó \`mealpilot/\` könyvtára. Újragenerálás:
 *
 *     cd backend && npm run pages
 *
 * A \`pages.test.ts\` elbukik, ha a kettő szétcsúszik, és a deploy is
 * újragenerálja, mielőtt kiküldi.
 */

export interface StaticPage {
  readonly contentType: string
  readonly body: string
}

export const PAGES: Record<string, StaticPage> = {
`

const body = entries
  .map((e) => `  '${e.path}': {\n    contentType: '${e.type}',\n    body: \`${escape(e.body)}\`,\n  },`)
  .join('\n')

writeFileSync(outFile, `${header}${body}\n}\n`, 'utf8')

const bytes = entries.reduce((sum, e) => sum + Buffer.byteLength(e.body), 0)
console.log(`${entries.length} oldal beágyazva (${(bytes / 1024).toFixed(1)} kB) -> src/pages.ts`)
for (const e of entries) console.log(`  ${e.path}`)
