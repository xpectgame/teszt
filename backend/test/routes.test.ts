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

  describe('életjel', () => {
    // A /healthz nem csak fut-e kérdésre válaszol, hanem arra is, hogy a beüzemelés
    // teljes-e. Ezért a három állapotot külön nézzük: kész, hiányzó migráció, hiányzó
    // kulcs. Az utolsó kettő a valóságban elgépelt titoknévként jelentkezik.
    function envWith(overrides: Record<string, unknown>, dbError?: string) {
      return {
        ANDROID_PACKAGE: 'hu.mealpilot.app',
        DB: {
          prepare: () => ({
            all: async () => {
              if (dbError) throw new Error(dbError)
              return { results: [] }
            },
          }),
        },
        ...overrides,
      } as any
    }

    async function health(env: any) {
      const r = await app.fetch(new Request('https://example.workers.dev/healthz'), env, ctx)
      return { status: r.status, body: (await r.json()) as Record<string, unknown> }
    }

    it('beüzemelve 200-at ad', async () => {
      const { status, body } = await health(envWith({ ANTHROPIC_API_KEY: 'x', OWNER_KEY: 'y' }))
      expect(status).toBe(200)
      expect(body.ok).toBe(true)
      expect(body.database).toBe('ok')
      expect(body.anthropicKey).toBe(true)
      expect(body.ownerKey).toBe(true)
      // A Play bekötése külön lépés, ezért hiányozhat anélkül, hogy a szolgáltatás
      // használhatatlan lenne.
      expect(body.playServiceAccount).toBe(false)
    })

    it('a titkok tartalmát soha nem adja ki', async () => {
      const { body } = await health(envWith({ ANTHROPIC_API_KEY: 'sk-titkos-ertek' }))
      expect(JSON.stringify(body)).not.toContain('sk-titkos-ertek')
    })

    it('hiányzó Anthropic kulcsnál 503', async () => {
      const { status, body } = await health(envWith({}))
      expect(status).toBe(503)
      expect(body.ok).toBe(false)
      expect(body.anthropicKey).toBe(false)
    })

    it('lefuttatatlan migrációt megkülönböztet az elérhetetlen adatbázistól', async () => {
      const missing = await health(
        envWith({ ANTHROPIC_API_KEY: 'x' }, 'D1_ERROR: no such table: events'),
      )
      expect(missing.status).toBe(503)
      expect(missing.body.database).toBe('nincs migrálva')

      const down = await health(envWith({ ANTHROPIC_API_KEY: 'x' }, 'network is unreachable'))
      expect(down.body.database).toBe('elérhetetlen')
    })

    it('hiányzó binding mellett sem dől el', async () => {
      const { status, body } = await health({ ANDROID_PACKAGE: 'hu.mealpilot.app' } as any)
      expect(status).toBe(503)
      expect(body.database).toBe('elérhetetlen')
    })
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
