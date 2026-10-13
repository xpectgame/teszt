import { describe, expect, it } from 'vitest'
import app from '../src/index.js'

/**
 * A /v1/generate könyvelése.
 *
 * Ami itt a tét: a kimeneti tokenekből lesz a napi közös mennyezet, vagyis a számla
 * felső korlátja. Ha egy hívás nyom nélkül tud elmenni, a mennyezet nem korlát, csak
 * egy szám. A megszakadt kapcsolat pedig nem kivételes eset — a felhasználó kilép az
 * appból, elmegy a térerő, és pont a leghosszabb (legdrágább) tervkérésnél a
 * legvalószínűbb.
 */

const ctx = { waitUntil: (p: Promise<unknown>) => { void p }, passThroughOnException: () => {} } as any

interface Recorded {
  subject: string
  sql: string
  args: unknown[]
}

function sseLine(event: unknown): string {
  return `data: ${JSON.stringify(event)}\n\n`
}

function envRecording(writes: Recorded[]) {
  return {
    ANDROID_PACKAGE: 'hu.mealpilot.app',
    ANTHROPIC_API_KEY: 'nem-hasznaljuk',
    PLAN_MODEL: 'claude-sonnet-5',
    CHAT_MODEL: 'claude-sonnet-5',
    DAILY_OUTPUT_TOKEN_CEILING: '250000',
    DB: {
      prepare: (sql: string) => ({
        bind: (...args: unknown[]) => ({
          run: async () => {
            writes.push({ subject: String(args[0]), sql, args })
            // A valódi D1 is visszaadja, hány sort érintett az utasítás — a foglalás
            // ebből tudja meg, hogy belefért-e a keretbe.
            return { meta: { changes: 1 } }
          },
          first: async () =>
            sql.includes('FROM usage')
              ? { plans: 0, messages: 0, input_tokens: 0, output_tokens: 0 }
              : null,
          all: async () => ({ results: [] }),
        }),
      }),
    },
  } as any
}

/** Az Anthropic folyamának utánzata. `deltas` darab szövegrészt ad, aztán lezár. */
function fakeAnthropic(options: {
  deltas: number
  deltaChars: number
  finalOutputTokens: number | null
  onAbort?: () => void
}) {
  const original = globalThis.fetch
  globalThis.fetch = (async (input: any, init: any) => {
    if (!String(input?.url ?? input).includes('anthropic.com')) return original(input, init)
    let aborted = false
    let abort: (() => void) | null = null
    init?.signal?.addEventListener?.('abort', () => {
      aborted = true
      abort?.()
      options.onAbort?.()
    })
    const body = new ReadableStream<Uint8Array>({
      async start(controller) {
        // Egy valódi megszakítás HIBÁRA futtatja az olvasót, nem szépen lezárja.
        // Ez a különbség dönti el, hogy a hívás „végigfutott"-nak számít-e.
        abort = () => {
          try {
            controller.error(new DOMException('The operation was aborted.', 'AbortError'))
          } catch {
            // Már lezárt folyam.
          }
        }
        const enc = new TextEncoder()
        controller.enqueue(
          enc.encode(sseLine({ type: 'message_start', message: { usage: { input_tokens: 1200 } } })),
        )
        for (let i = 0; i < options.deltas; i++) {
          if (aborted) return
          controller.enqueue(
            enc.encode(sseLine({ type: 'content_block_delta', delta: { text: 'x'.repeat(options.deltaChars) } })),
          )
          await new Promise((r) => setTimeout(r, 0))
        }
        if (options.finalOutputTokens !== null) {
          controller.enqueue(enc.encode(sseLine({ type: 'message_delta', usage: { output_tokens: options.finalOutputTokens } })))
        }
        controller.close()
      },
    })
    return new Response(body, { status: 200 })
  }) as typeof fetch
  return () => {
    globalThis.fetch = original
  }
}

/** Az Anthropic HIBÁVAL válaszol, és a hibatörzs visszaidézi a kérés egy darabját. */
function fakeAnthropicFailure(bodyText: string, status = 400) {
  const original = globalThis.fetch
  globalThis.fetch = (async (input: any, init: any) => {
    if (!String(input?.url ?? input).includes('anthropic.com')) return original(input, init)
    return new Response(bodyText, { status })
  }) as typeof fetch
  return () => {
    globalThis.fetch = original
  }
}

function generate(env: any) {
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

/** A usage INSERT ?6 paramétere a kimeneti token. */
function outputTokensFor(writes: Recorded[], subject: string): number | null {
  const row = writes.find((w) => w.subject === subject && w.sql.includes('INSERT INTO usage'))
  return row ? Number(row.args[5]) : null
}

describe('a /v1/generate könyvelése', () => {
  it('a végigfutó hívás a pontos tokenszámot könyveli', async () => {
    const writes: Recorded[] = []
    const restore = fakeAnthropic({ deltas: 5, deltaChars: 40, finalOutputTokens: 1234 })
    try {
      const r = await generate(envRecording(writes))
      await r.text()
      await new Promise((res) => setTimeout(res, 50))
    } finally {
      restore()
    }

    expect(outputTokensFor(writes, 'global:daily')).toBe(1234)
    const request = writes.find((w) => w.sql.includes('INSERT INTO requests'))
    expect(request, 'a kérésnaplóba is kell sor').toBeTruthy()
    // ok = 1: a hívás végigment.
    expect(request!.args[9]).toBe(1)
  })

  it('a megszakadt kapcsolat sem tűnik el a könyvelésből', async () => {
    const writes: Recorded[] = []
    let aborted = false
    const restore = fakeAnthropic({
      deltas: 500,
      deltaChars: 40,
      finalOutputTokens: 9999,
      onAbort: () => {
        aborted = true
      },
    })
    try {
      const r = await generate(envRecording(writes))
      const reader = r.body!.getReader()
      await reader.read()
      await reader.read()
      await reader.cancel()
      await new Promise((res) => setTimeout(res, 80))
    } finally {
      restore()
    }

    // A felfelé menő hívást bontjuk: enélkül a modell a végéig generálna, és a
    // teljes tervet kifizetnénk egy olyan felhasználóért, aki már nincs ott.
    expect(aborted, 'az Anthropic hívását meg kell szakítani').toBe(true)

    // A pontos tokenszám a folyam végén jönne; itt nem jött meg, tehát becsülünk.
    // A lényeg, hogy NE nulla legyen: abból a napi mennyezet semmit nem lát.
    const globalTokens = outputTokensFor(writes, 'global:daily')
    expect(globalTokens, 'a globális napi számláló').toBeGreaterThan(0)

    const request = writes.find((w) => w.sql.includes('INSERT INTO requests'))
    expect(request, 'a megszakadt hívásnak is van kérésnaplója').toBeTruthy()
    // ok = 0: nem futott végig, tehát tervkvótát sem fogyaszt.
    expect(request!.args[9]).toBe(0)
    const own = writes.find(
      (w) => w.subject.startsWith('user:') && w.sql.includes('INSERT INTO usage'),
    )
    expect(Number(own!.args[2]), 'félbeszakadt terv nem fogyaszt tervkvótát').toBe(0)
    expect(Number(own!.args[5]), 'a tokent viszont elhasználta').toBeGreaterThan(0)
  })
})

describe('a kérésnapló hibamezője', () => {
  /**
   * Az adatkezelési tájékoztató 3. pontja azt ígéri, hogy a szerver NEM írja le a
   * kérés szövegét — az étrendet, a testadatot és az üzeneteket —, egyetlen
   * kivétellel (amit a felhasználó maga jelent be). A szolgáltatás hibaválaszának
   * nyers törzsét mentettük a `requests.error` oszlopba: az szabad szöveg, és
   * visszaidézheti a kérés egy darabját.
   */
  it('a szolgáltatás nyers hibaszövege nem kerül az adatbázisba', async () => {
    const leaky = JSON.stringify({
      type: 'error',
      error: {
        type: 'invalid_request_error',
        message: "messages.0.content: 'Kovács Máté, 96 kg, 41 éves' is too long",
      },
    })
    const writes: Recorded[] = []
    const restore = fakeAnthropicFailure(leaky)
    try {
      const r = await generate(envRecording(writes))
      await r.text()
      await new Promise((res) => setTimeout(res, 50))
    } finally {
      restore()
    }

    const request = writes.find((w) => w.sql.includes('INSERT INTO requests'))
    expect(request, 'a kérésnaplóba kell sor a hibás hívásról is').toBeTruthy()
    const stored = String(request!.args[10])

    expect(stored).toBe('anthropic:400:invalid_request_error')
    expect(stored).not.toContain('Kovács')
    expect(stored).not.toContain('96 kg')
    // ok = 0: a hívás elbukott, és ez nyomot hagy.
    expect(request!.args[9]).toBe(0)
  })
})

/**
 * Egyszerre futó kérések és a keret.
 *
 * A kvótát az előszűrő ELŐRE olvassa ki, a könyvelés viszont a hívás UTÁN jön — és a
 * kettő között percek telhetnek el. Eddig ezalatt minden további kérés ugyanazt a
 * régi számlálót látta: egyszerre indított kérések mind átmentek, három ingyenes terv
 * helyett annyi, ahány egyszerre futott. A tokenplafon és a közös napi mennyezet
 * ugyanezzel a réssel működött.
 *
 * Itt a VÉGPONT viselkedését mérjük: mit tesz, ha a foglalás nem fér bele. Hogy a
 * foglalás feltétele valódi SQLite motoron tényleg korlátoz-e, azt a
 * `tools/check-migrations.py` méri — ugyanazon a SQL szövegen, amit a kód küld.
 */
describe('a foglalás egyszerre futó kéréseknél', () => {
  interface Statement {
    sql: string
    args: unknown[]
  }

  function envReserving(options: {
    statements: Statement[]
    /** Melyik alany foglalása fér bele. */
    accept: (subject: string) => boolean
    /** A friss újraolvasásnál a saját terv-számláló. */
    plansUsed?: number
    /** Ha igaz, a foglalótábla hiányzik — elmaradt migráció. */
    missingTable?: boolean
  }) {
    return {
      ANDROID_PACKAGE: 'hu.mealpilot.app',
      ANTHROPIC_API_KEY: 'nem-hasznaljuk',
      PLAN_MODEL: 'claude-sonnet-5',
      CHAT_MODEL: 'claude-sonnet-5',
      DAILY_OUTPUT_TOKEN_CEILING: '250000',
      DB: {
        prepare: (sql: string) => ({
          bind: (...args: unknown[]) => ({
            run: async () => {
              options.statements.push({ sql, args })
              if (sql.includes('reservations') && options.missingTable) {
                throw new Error('D1_ERROR: no such table: reservations')
              }
              if (sql.includes('INSERT INTO reservations')) {
                return { meta: { changes: options.accept(String(args[1])) ? 1 : 0 } }
              }
              return { meta: { changes: 1 } }
            },
            first: async () =>
              sql.includes('FROM usage')
                ? {
                    plans: String(args[0]).startsWith('user:') ? (options.plansUsed ?? 0) : 0,
                    messages: 0,
                    input_tokens: 0,
                    output_tokens: 0,
                  }
                : null,
            all: async () => ({ results: [] }),
          }),
        }),
      },
    } as any
  }

  /** Igaz, ha a kérés eljutott a modellig — vagyis pénzbe került. */
  function trackAnthropic() {
    const state = { called: false }
    const original = globalThis.fetch
    globalThis.fetch = (async (input: any, init: any) => {
      if (String(input?.url ?? input).includes('anthropic.com')) state.called = true
      return original(input, init)
    }) as typeof fetch
    return { state, restore: () => { globalThis.fetch = original } }
  }

  it('ha egy másik kérés még fut, várakozásra kér — nem fizetőfalat mutat', async () => {
    const statements: Statement[] = []
    const tracker = trackAnthropic()
    try {
      const r = await generate(envReserving({ statements, accept: (s) => !s.startsWith('user:') }))
      const body = (await r.json()) as { error: string }

      expect(r.status, 'ez nem kvótahiba: a 402 fizetőfalat nyitna').toBe(429)
      expect(body.error).toBe('RATE_LIMIT')
      expect(tracker.state.called, 'a modellt nem szabad hívni').toBe(false)
    } finally {
      tracker.restore()
    }
  })

  it('ha közben a keret tényleg elfogyott, azt mondja meg', async () => {
    const statements: Statement[] = []
    const tracker = trackAnthropic()
    try {
      // Az előszűrő még 2-t látott (belefért), a foglaláskor viszont egy egyszerre
      // futó kérés már elvitte a harmadikat.
      let reads = 0
      const env = envReserving({ statements, accept: (s) => !s.startsWith('user:') })
      const prepare = env.DB.prepare
      env.DB.prepare = (sql: string) => {
        const statement = prepare(sql)
        return {
          bind: (...args: unknown[]) => {
            const bound = statement.bind(...args)
            return {
              ...bound,
              first: async () => {
                if (sql.includes('FROM usage') && String(args[0]).startsWith('user:')) {
                  reads++
                  return { plans: reads === 1 ? 2 : 3, messages: 0, input_tokens: 0, output_tokens: 0 }
                }
                return bound.first()
              },
            }
          },
        }
      }
      const r = await generate(env)
      const body = (await r.json()) as { error: string }

      expect(r.status).toBe(402)
      expect(body.error).toBe('PLAN_QUOTA')
      expect(tracker.state.called).toBe(false)
    } finally {
      tracker.restore()
    }
  })

  it('ha a közös mennyezet nem fér bele, a saját foglalást is visszaadja', async () => {
    const statements: Statement[] = []
    const tracker = trackAnthropic()
    try {
      const r = await generate(envReserving({ statements, accept: (s) => s !== 'global:daily' }))
      await r.text()

      expect(r.status).toBe(503)
      expect(tracker.state.called).toBe(false)

      const own = statements.find(
        (s) => s.sql.includes('INSERT INTO reservations') && String(s.args[1]).startsWith('user:'),
      )
      const released = statements.some(
        (s) => s.sql.includes('DELETE FROM reservations WHERE id') && s.args[0] === own!.args[0],
      )
      expect(released, 'különben lejáratig elvenné a felhasználó keretét').toBe(true)
    } finally {
      tracker.restore()
    }
  })

  it('a foglalás a könyvelés UTÁN oldódik, és mindkettő', async () => {
    const statements: Statement[] = []
    const restore = fakeAnthropic({ deltas: 3, deltaChars: 20, finalOutputTokens: 500 })
    try {
      const r = await generate(envReserving({ statements, accept: () => true }))
      await r.text()
      await new Promise((res) => setTimeout(res, 50))
    } finally {
      restore()
    }

    const reservations = statements
      .filter((s) => s.sql.includes('INSERT INTO reservations'))
      .map((s) => s.args[0])
    expect(reservations.length, 'a saját és a közös foglalás').toBe(2)

    const releaseIndex = (id: unknown) =>
      statements.findIndex((s) => s.sql.includes('DELETE FROM reservations WHERE id') && s.args[0] === id)
    const lastUsageWrite = statements.map((s) => s.sql).lastIndexOf(
      statements.filter((s) => s.sql.includes('INSERT INTO usage')).at(-1)!.sql,
    )
    for (const id of reservations) {
      expect(releaseIndex(id), 'minden foglalás feloldódik').toBeGreaterThan(-1)
      // Fordítva egy pillanatig SEMMI nem számítana, és egy közben érkező kérés
      // átcsúszhatna.
      expect(releaseIndex(id), 'és csak a könyvelés után').toBeGreaterThan(lastUsageWrite)
    }
  })

  it('egy elmaradt migráció nem állítja le a tervezést', async () => {
    // Ha a deploynál nem pipálták be a migrációt, a foglalótábla nincs meg. Ez nem
    // lehet ok arra, hogy MINDEN felhasználónál elhasaljon a tervezés: a foglalás
    // kimarad, és marad a korábbi előszűrő — ami sosem rosszabb a javítás előttinél.
    const statements: Statement[] = []
    const restore = fakeAnthropic({ deltas: 2, deltaChars: 20, finalOutputTokens: 300 })
    try {
      const r = await generate(envReserving({ statements, accept: () => true, missingTable: true }))
      const text = await r.text()

      expect(r.status).toBe(200)
      expect(text).toContain('"type":"done"')
    } finally {
      restore()
    }
  })
})
