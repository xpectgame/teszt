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

describe('az appverzió megszelídítése', () => {
  /** Az events INSERT ?3 paramétere az appverzió. */
  function versionIn(statements: any[]): string {
    const row = statements.find((s) => String(s.sql).includes('INTO events'))
    return String(row.args[2])
  }

  async function uploadWithVersion(version: string) {
    const batches: unknown[][] = []
    await app.fetch(
      new Request('https://example.workers.dev/v1/telemetry', {
        method: 'POST',
        headers: {
          authorization: 'Bearer aaaaaaaaaaaaaaaaaaaaaaaa',
          'content-type': 'application/json',
          'x-app-version': version,
        },
        body: JSON.stringify({ day: '2026-09-16', events: { app_open: 1 }, first_today: true }),
      }),
      envCapturing(batches),
      ctx,
    )
    return versionIn(batches[0] as any[])
  }

  it('a valódi verziót változatlanul hagyja', async () => {
    // A CI a futás sorszámát is beleírja: „0.1.0 (123)". A szóköz és a zárójel
    // tehát nem szemét, hanem a normál alak.
    expect(await uploadWithVersion('0.1.0 (123)')).toBe('0.1.0 (123)')
  })

  it('a hosszú és szemetes verziót levágja', async () => {
    // Az appverzió az events tábla elsődleges kulcsának része: korlátozás nélkül
    // minden kitalált érték új sort nyitna ugyanarra az eseményre.
    expect(await uploadWithVersion('x'.repeat(500))).toHaveLength(40)
    expect(await uploadWithVersion('1.0<script>')).toBe('1.0script')
    expect(await uploadWithVersion('\n\t  ')).toBe('')
  })
})

describe('az összeomlás-jelentés', () => {
  /**
   * Az app soha nem küld kivételüzenetet: a `CrashReporter` a vermet keretekből
   * építi, mert egy üzenet bárhonnan kaphat felhasználói szöveget. A szerveren
   * mégis állt hozzá egy 1000 karakteres szabad szöveges rekesz — amit a kliens nem
   * tölt ki, azt egy későbbi kliens kitölthetné.
   */
  it('a küldött kivételüzenetet nem tárolja el', async () => {
    const batches: unknown[][] = []
    const r = await upload(envCapturing(batches), {
      day: '2026-09-17',
      crashes: [
        {
          exception: 'java.lang.NumberFormatException',
          message: 'For input string: "78,5"',
          stack: 'hu.mealpilot.app.Valami.fut:42',
          fingerprint: 'abc',
        },
      ],
    })

    expect(r.status).toBe(200)
    const insert = batches
      .flat()
      .find((s: any) => String(s.sql).includes('INTO crashes')) as any
    expect(insert, 'az összeomlásnak be kell kerülnie').toBeTruthy()

    // A verem és az osztály igen, az üzenet nem.
    const stored = (insert.args as unknown[]).map(String)
    expect(stored).toContain('java.lang.NumberFormatException')
    expect(stored.some((value) => value.includes('78,5'))).toBe(false)
    expect(String(insert.sql)).not.toContain('message')
  })

  it('üzenet nélkül is elmenti, amit az app tényleg küld', async () => {
    const batches: unknown[][] = []
    await upload(envCapturing(batches), {
      day: '2026-09-17',
      device: 'Pixel 8',
      android_api: 34,
      crashes: [
        {
          exception: 'java.lang.IllegalStateException',
          stack: 'hu.mealpilot.app.Valami.fut:42',
          fingerprint: 'hu.mealpilot.app.Valami.fut',
        },
      ],
    })

    const insert = batches
      .flat()
      .find((s: any) => String(s.sql).includes('INTO crashes')) as any
    const stored = (insert.args as unknown[]).map(String)
    expect(stored).toContain('java.lang.IllegalStateException')
    expect(stored).toContain('hu.mealpilot.app.Valami.fut:42')
    expect(stored).toContain('Pixel 8')
  })
})
