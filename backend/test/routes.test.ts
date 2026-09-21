import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
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

describe('a bejelentés és az összeomlás napi korlátja', () => {
  /**
   * A telepítési azonosító nem jogosultság: bárki generál magának újat. A tervezésnél
   * ezt a kimeneti tokenplafon fogja meg, a `/v1/report` és a `/v1/telemetry` viszont
   * korlátlanul fogadott több kilobájtos sorokat. A D1 napi írási keretének
   * kimerítése a KÖNYVELÉST is megbénítaná, tehát a fizető felhasználókat.
   */
  function envRecordingSql(statements: Array<{ sql: string; args: unknown[] }>) {
    const bind = (sql: string) => (...args: unknown[]) => ({
      sql,
      args,
      run: async () => {
        statements.push({ sql, args })
        return {}
      },
      first: async () => null,
      all: async () => ({ results: [] }),
    })
    return {
      ANDROID_PACKAGE: 'hu.mealpilot.app',
      DB: {
        prepare: (sql: string) => ({ bind: bind(sql) }),
        batch: async (list: any[]) => {
          for (const s of list) statements.push({ sql: s.sql, args: s.args })
          return []
        },
      },
    } as any
  }

  function post(env: any, path: string, body: unknown) {
    return app.fetch(
      new Request(`https://example.workers.dev${path}`, {
        method: 'POST',
        headers: {
          authorization: 'Bearer aaaaaaaaaaaaaaaaaaaaaaaa',
          'content-type': 'application/json',
        },
        body: JSON.stringify(body),
      }),
      env,
      ctx,
    )
  }

  it('a bejelentés beszúrása maga tartja be a napi keretet', async () => {
    const statements: Array<{ sql: string; args: unknown[] }> = []
    const r = await post(envRecordingSql(statements), '/v1/report', {
      kind: 'PLAN',
      reason: 'WRONG_NUTRITION',
      payload: 'x'.repeat(100),
    })

    expect(r.status).toBe(200)
    const insert = statements.find((s) => s.sql.includes('INTO reports'))
    expect(insert, 'a bejelentésnek be kell kerülnie').toBeTruthy()
    // Nem sima VALUES: a feltételt maga az utasítás hordozza, tehát nincs
    // beolvasás-majd-írás rés, amin két egyszerre futó kérés átcsúszhatna.
    expect(insert!.sql).toContain('SELECT COUNT(*)')
    expect(insert!.sql).not.toContain('VALUES')
    // Az utolsó paraméter a korlát, az előtte lévő az ablak kezdete.
    const args = insert!.args
    expect(Number(args[args.length - 1])).toBeGreaterThan(0)
    expect(Number(args[args.length - 2])).toBeLessThan(Date.now())
  })

  it('az összeomlás beszúrása is a saját keretét nézi', async () => {
    const statements: Array<{ sql: string; args: unknown[] }> = []
    await post(envRecordingSql(statements), '/v1/telemetry', {
      day: '2026-09-17',
      crashes: [{ exception: 'java.lang.IllegalStateException', stack: 'a.b.c:1', fingerprint: 'f' }],
    })

    const insert = statements.find((s) => String(s.sql).includes('INTO crashes'))
    expect(insert, 'az összeomlásnak be kell kerülnie').toBeTruthy()
    expect(String(insert!.sql)).toContain('SELECT COUNT(*)')
    expect(String(insert!.sql)).not.toContain('VALUES')
  })
})

describe('az RTDN-végpont titka', () => {
  // A közös titok az URL lekérdezési részében utazik. A `secretMatches` szabályát az
  // `OWNER_KEY`-re már kimondtuk; itt sima `!==` állt.
  const env = { ANDROID_PACKAGE: 'hu.mealpilot.app', RTDN_SHARED_SECRET: 'abcdef0123456789' } as any

  function rtdn(secret: string | null) {
    const url = secret === null
      ? 'https://example.workers.dev/v1/play/rtdn'
      : `https://example.workers.dev/v1/play/rtdn?secret=${encodeURIComponent(secret)}`
    return app.fetch(
      new Request(url, { method: 'POST', body: '{}', headers: { 'content-type': 'application/json' } }),
      env,
      ctx,
    )
  }

  it('rossz titokkal 403', async () => {
    expect((await rtdn('rossz')).status).toBe(403)
    // Ugyanolyan hosszú, de más: a hosszellenőrzés ne fedje el a tartalmi eltérést.
    expect((await rtdn('abcdef0123456788')).status).toBe(403)
    expect((await rtdn(null)).status).toBe(403)
    expect((await rtdn('')).status).toBe(403)
  })

  it('a jó titkot átengedi', async () => {
    const r = await rtdn('abcdef0123456789')
    expect(r.status).toBe(200)
  })

  /**
   * Ez FORRÁSELLENŐRZÉS, nem viselkedési teszt — és ezt ki kell mondani.
   *
   * A `!==` és a `secretMatches` kimenete azonos; a különbség az IDŐZÍTÉS, amit egy
   * egységteszt nem tud megfogni. A fenti tesztek ezért csak azt rögzítik, hogy a
   * végpont kit enged be. Hogy az összehasonlítás közben ne szivárogjon információ,
   * azt csak így lehet visszaesés ellen védeni.
   */
  it('a titkot időzítésre nem árulkodó összehasonlítás nézi', () => {
    const source = readFileSync(fileURLToPath(new URL('../src/index.ts', import.meta.url)), 'utf8')
    const handler = source.slice(source.indexOf("app.post('/v1/play/rtdn'"))
    expect(handler).toContain('secretMatches(provided, expected)')
    expect(handler.slice(0, handler.indexOf('}'))).not.toContain('provided !== expected')
  })

  it('titok nélküli telepítésen senki nem jut be', async () => {
    const bare = { ANDROID_PACKAGE: 'hu.mealpilot.app' } as any
    const r = await app.fetch(
      new Request('https://example.workers.dev/v1/play/rtdn?secret=barmi', {
        method: 'POST',
        body: '{}',
        headers: { 'content-type': 'application/json' },
      }),
      bare,
      ctx,
    )
    expect(r.status).toBe(403)
  })
})

describe('statikus oldalak keresése', () => {
  const ctx = { waitUntil() {}, passThroughOnException() {} } as any
  const pageEnv = { ANDROID_PACKAGE: 'hu.mealpilot.app' } as any

  // Az Object prototípusának nevei nem oldalak. Egy sima `PAGES[candidate]` keresés
  // viszont megtalálja őket, és a `if (!page) continue` ŐR nem fog rajtuk: a
  // `constructor` egy függvény, tehát igaz. A válasz így 404 helyett egy üres
  // törzsű 200 (vagy a fejléc miatt 500) lett volna — öt nyilvános címen.
  it.each(['constructor', '__proto__', 'toString', 'valueOf', 'hasOwnProperty'])(
    '/%s nem oldal, hanem 404',
    async (name) => {
      const r = await app.fetch(new Request(`https://example.workers.dev/${name}`), pageEnv, ctx)
      expect(r.status).toBe(404)
    },
  )

  it('a valódi oldalak ettől még kiszolgálódnak', async () => {
    for (const path of ['/privacy', '/privacy.html', '/']) {
      const r = await app.fetch(new Request(`https://example.workers.dev${path}`), pageEnv, ctx)
      expect(r.status, path).toBe(200)
      expect((await r.text()).length, path).toBeGreaterThan(0)
    }
  })
})
