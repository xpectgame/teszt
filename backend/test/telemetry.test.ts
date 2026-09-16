import { describe, expect, it } from 'vitest'
import app from '../src/index.js'

/**
 * A napi számlálók feltöltése.
 *
 * A `users` oszlop az egyetlen szám, amiből kiderül, hány EMBER használja az appot.
 * A kliens indításkor is feltölt, tehát naponta többször küld — ha minden feltöltés
 * növelné, a szám a napi megnyitások számát mutatná, felhasználónak álcázva.
 */

const ctx = { waitUntil: (p: Promise<unknown>) => { void p }, passThroughOnException: () => {} } as any

function envCapturing(batches: unknown[][]) {
  return {
    ANDROID_PACKAGE: 'hu.mealpilot.app',
    DB: {
      prepare: (sql: string) => ({
        bind: (...args: unknown[]) => ({
          sql,
          args,
          run: async () => ({}),
          first: async () => null,
          all: async () => ({ results: [] }),
        }),
      }),
      batch: async (statements: any[]) => {
        batches.push(statements)
        return []
      },
    },
  } as any
}

async function upload(env: any, body: Record<string, unknown>) {
  return app.fetch(
    new Request('https://example.workers.dev/v1/telemetry', {
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

/** Az events INSERT ?6 paramétere: beleszámít-e a napi felhasználószámba. */
function userDelta(statements: any[]): number[] {
  return statements
    .filter((s) => String(s.sql).includes('INTO events'))
    .map((s) => Number(s.args[5]))
}

describe('a napi számlálók feltöltése', () => {
  it('a nap első feltöltése számít egy felhasználónak', async () => {
    const batches: unknown[][] = []
    const r = await upload(envCapturing(batches), {
      day: '2026-09-16',
      events: { app_open: 3, meal_logged: 2 },
      first_today: true,
    })
    expect(r.status).toBe(200)
    expect(userDelta(batches[0] as any[])).toEqual([1, 1])
  })

  it('a nap további feltöltései nem növelik a felhasználószámot', async () => {
    const batches: unknown[][] = []
    await upload(envCapturing(batches), {
      day: '2026-09-16',
      events: { app_open: 1 },
      first_today: false,
    })
    expect(userDelta(batches[0] as any[])).toEqual([0])
    // A darabszám viszont igen: a megnyitásokat továbbra is összeadjuk.
    const event = (batches[0] as any[]).find((s) => String(s.sql).includes('INTO events'))
    expect(Number(event.args[3])).toBe(1)
  })

  it('a jelzés nélküli (régi) feltöltés inkább alul számol', async () => {
    // Felfelé hazudni rosszabb: abból az jönne ki, hogy működik a marketing.
    const batches: unknown[][] = []
    await upload(envCapturing(batches), { day: '2026-09-16', events: { app_open: 1 } })
    expect(userDelta(batches[0] as any[])).toEqual([0])
  })

  it('a szemét eseménynevet és a negatív darabszámot eldobja', async () => {
    const batches: unknown[][] = []
    const r = await upload(envCapturing(batches), {
      day: '2026-09-16',
      events: { 'DROP TABLE': 5, app_open: -3, meal_logged: 0, weight_logged: 2 },
      first_today: true,
    })
    const stored = (await r.json()) as { stored: number }
    expect(stored.stored, 'csak a weight_logged marad').toBe(1)
  })
})
