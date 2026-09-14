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

describe('globális napi mennyezet a kérés útján', () => {
  // A mennyezet a tiszta függvényben tesztelve van; itt az a kérdés, hogy a kérés
  // útjába tényleg be van-e kötve — az Anthropic hívása ELŐTT. Ezért nincs hálózati
  // utánzat: ha a mennyezet nem fogna, a teszt valódi hívással szállna el.
  function envWithUsage(outputTokensToday: number, ceiling = '250000') {
    return {
      ANDROID_PACKAGE: 'hu.mealpilot.app',
      ANTHROPIC_API_KEY: 'nem-hasznaljuk',
      PLAN_MODEL: 'claude-sonnet-5',
      CHAT_MODEL: 'claude-sonnet-5',
      DAILY_OUTPUT_TOKEN_CEILING: ceiling,
      DB: {
        prepare: (sql: string) => ({
          bind: (...args: unknown[]) => ({
            run: async () => ({}),
            first: async () => {
              if (sql.includes('FROM usage')) {
                // Csak a közös alany áll a plafonon; a hívó saját kerete érintetlen.
                return args[0] === 'global:daily'
                  ? { plans: 0, messages: 0, input_tokens: 0, output_tokens: outputTokensToday }
                  : { plans: 0, messages: 0, input_tokens: 0, output_tokens: 0 }
              }
              return null
            },
            all: async () => ({ results: [] }),
          }),
        }),
      },
    } as any
  }

  async function generate(env: any) {
    return app.fetch(
      new Request('https://example.workers.dev/v1/generate', {
        method: 'POST',
        headers: {
          authorization: 'Bearer aaaaaaaaaaaaaaaaaaaaaaaa',
          'content-type': 'application/json',
        },
        body: JSON.stringify({ task: 'PLAN', prompt: 'kérek egy tervet', days: 1 }),
      }),
      env,
      ctx,
    )
  }

  it('a keret elfogyásakor 503, az AI hívása nélkül', async () => {
    const r = await generate(envWithUsage(250_000))
    expect(r.status).toBe(503)
    const body = (await r.json()) as Record<string, unknown>
    expect(body.error).toBe('SERVICE_BUSY')
    // Ez nem fizetési fal: a felhasználónak nincs mit vennie ettől.
    expect(body.upgrade).toBe(false)
  })

  it('a tulajdonost nem zárja ki a saját szolgáltatásából', async () => {
    const env = envWithUsage(250_000)
    env.OWNER_KEY = 'tulajdonosi-kulcs'

    // A hálózatot elzárjuk, és a hívás tényét mérjük: ha a mennyezet megfogná a
    // tulajdonost, ide sosem jutna el a vezérlés. Így a teszt nem függ külső
    // szolgáltatástól, és pont azt állítja, amit akarunk.
    const original = globalThis.fetch
    let calledAnthropic = false
    globalThis.fetch = (async (input: any) => {
      calledAnthropic = String(input?.url ?? input).includes('anthropic.com')
      return new Response('', { status: 500 })
    }) as typeof fetch

    try {
      const r = await app.fetch(
        new Request('https://example.workers.dev/v1/generate', {
          method: 'POST',
          headers: {
            authorization: 'Bearer aaaaaaaaaaaaaaaaaaaaaaaa',
            'x-owner-key': 'tulajdonosi-kulcs',
            'content-type': 'application/json',
          },
          body: JSON.stringify({ task: 'PLAN', prompt: 'kérek egy tervet', days: 1 }),
        }),
        env,
        ctx,
      )
      expect(r.status).not.toBe(503)
      // A választ ki kell olvasni: a hívás a folyam belsejében indul.
      await r.text()
      expect(calledAnthropic).toBe(true)
    } finally {
      globalThis.fetch = original
    }
  })
})
