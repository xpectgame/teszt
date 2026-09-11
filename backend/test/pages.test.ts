import { describe, expect, it } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { extname, join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'
import { PAGES } from '../src/pages.js'

const siteDir = fileURLToPath(new URL('../../mealpilot/', import.meta.url))
const SERVED = new Set(['.html', '.css', '.svg', '.txt'])

function walk(dir: string): string[] {
  return readdirSync(dir)
    .sort()
    .flatMap((name: string) => {
      const full = join(dir, name)
      return statSync(full).isDirectory() ? walk(full) : [full]
    })
}

const sourceFiles = walk(siteDir).filter((f) => SERVED.has(extname(f)))

describe('beágyazott oldalak', () => {
  it('minden forrásfájl szerepel a generált modulban', () => {
    const expected = sourceFiles.map((f) => relative(siteDir, f).split('\\').join('/')).sort()
    expect(Object.keys(PAGES).sort()).toEqual(expected)
  })

  it.each(sourceFiles)('%s tartalma egyezik a forrással', (file) => {
    // Ez a teszt bukik el, ha valaki a mealpilot/ alatt módosít, de elfelejti a
    // `npm run pages` parancsot. A javítás maga a parancs.
    const path = relative(siteDir, file).split('\\').join('/')
    expect(PAGES[path]?.body).toBe(readFileSync(file, 'utf8'))
  })

  it('a Play által kért három cím megvan és nem üres', () => {
    for (const path of ['privacy.html', 'delete-data.html', 'support.html']) {
      expect(PAGES[path], path).toBeDefined()
      expect(PAGES[path]!.body.length, path).toBeGreaterThan(500)
      expect(PAGES[path]!.contentType).toBe('text/html; charset=utf-8')
    }
  })

  it('az oldalak nem TÖLTENEK BE külső erőforrást', () => {
    // Külső CDN, webfont vagy szkript nélkül az oldal akkor is megjelenik, ha valami
    // más éppen nem elérhető — a Play pedig ellenőrzi, hogy a cím tényleg betölt-e.
    //
    // A szövegben lévő <a href> hivatkozások (Anthropic, Google, NAIH adatvédelmi
    // oldala) ettől függetlenül kellenek: azokra a tájékoztatónak hivatkoznia KELL.
    for (const [path, page] of Object.entries(PAGES)) {
      if (!path.endsWith('.html')) continue
      const loaded = [
        ...page.body.matchAll(/\bsrc="[^"]+"/g),
        ...page.body.matchAll(/<link\b[^>]*\bhref="[^"]+"/g),
      ].map((m) => m[0])
      const external = loaded.filter((tag) => /"(https?:)?\/\//.test(tag))
      expect(external, path).toEqual([])
    }
  })
})
