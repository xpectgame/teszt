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
            return {}
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
