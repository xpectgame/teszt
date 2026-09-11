import { describe, expect, it } from 'vitest'
import app from '../src/index.js'

const env = { ANDROID_PACKAGE: 'hu.mealpilot.app' } as any
const ctx = { waitUntil: () => {}, passThroughOnException: () => {} } as any

async function get(path: string) {
  return app.fetch(new Request(`https://example.workers.dev${path}`), env, ctx)
}

describe('útvonalak', () => {
  it('a gyökér az index oldalt adja', async () => {
    const r = await get('/')
    expect(r.status).toBe(200)
    expect(r.headers.get('content-type')).toContain('text/html')
    expect(await r.text()).toContain('MealPilot')
  })

  it.each([
    '/privacy.html', '/terms.html', '/support.html', '/delete-data.html', '/assets/site.css',
  ])('%s kiszolgálva', async (path) => {
    const r = await get(path)
    expect(r.status).toBe(200)
  })

  it.each(['/privacy', '/terms', '/support', '/delete-data'])(
    '%s kiterjesztés nélkül is működik',
    async (path) => {
      const r = await get(path)
      expect(r.status).toBe(200)
      expect(r.headers.get('content-type')).toContain('text/html')
    },
  )

  it('az életjelet nem takarja el', async () => {
    const r = await get('/healthz')
    expect(r.status).toBe(200)
    expect(await r.json()).toEqual({ ok: true })
  })

  it('ismeretlen cím 404', async () => {
    expect((await get('/nincs-ilyen')).status).toBe(404)
  })

  it('a könyvtáron kívülre nem lehet kimászni', async () => {
    for (const path of ['/../package.json', '/%2e%2e/package.json', '/assets/../../wrangler.toml']) {
      const r = await get(path)
      expect(r.status, path).toBe(404)
    }
  })
})
