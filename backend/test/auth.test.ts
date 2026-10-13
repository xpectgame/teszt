import { describe, expect, it } from 'vitest'
import { resolveCaller, subscriptionRoot } from '../src/auth.js'
import { sha256Hex } from '../src/db.js'

/**
 * Az előfizetés kvótaalanya csomagváltás után.
 *
 * Csomagváltáskor (és egy lemondott, de még le nem járt előfizetés újraindításakor)
 * a Play ÚJ vásárlási tokent ad, és a régit a `linkedPurchaseToken` mezőben jelöli
 * meg. Ezt eddig elmentettük (`linked_from`), de soha senki nem olvasta: a kvóta
 * alanya a mindenkori token hashe volt, tehát egy váltás után a havi tokenplafon
 * nulláról indult újra — ugyanannak az előfizetőnek, ugyanabban a hónapban.
 */

interface SubscriptionFixture {
  state: string
  expires_at: number | null
  verified_at: number
  linked_from?: string | null
}

function envWithSubscriptions(subscriptions: Record<string, SubscriptionFixture>, reads: string[] = []) {
  return {
    ANDROID_PACKAGE: 'hu.mealpilot.app',
    DB: {
      prepare: (sql: string) => ({
        bind: (...args: unknown[]) => ({
          run: async () => ({ meta: { changes: 1 } }),
          first: async () => {
            if (sql.includes('FROM users')) return { id: args[0], purchase_hash: null }
            if (sql.includes('FROM subscriptions')) {
              const hash = String(args[0])
              reads.push(hash)
              const row = subscriptions[hash]
              return row ? { purchase_hash: hash, first_user_id: null, ...row } : null
            }
            return null
          },
          all: async () => ({ results: [] }),
        }),
      }),
    },
  } as any
}

function request(purchaseToken: string) {
  return new Request('https://example.workers.dev/v1/session', {
    method: 'POST',
    headers: {
      authorization: 'Bearer aaaaaaaaaaaaaaaaaaaaaaaa',
      'x-play-purchase-token': purchaseToken,
    },
  })
}

describe('az előfizetés gyökere', () => {
  it('lánc nélkül a token maga a gyökér', async () => {
    const env = envWithSubscriptions({})
    expect(await subscriptionRoot(env, 'aaa')).toBe('aaa')
  })

  it('a váltások láncát a legelső vásárlásig követi', async () => {
    const env = envWithSubscriptions({
      harmadik: { state: 'ACTIVE', expires_at: null, verified_at: 0, linked_from: 'masodik' },
      masodik: { state: 'EXPIRED', expires_at: null, verified_at: 0, linked_from: 'elso' },
      elso: { state: 'EXPIRED', expires_at: null, verified_at: 0, linked_from: null },
    })
    expect(await subscriptionRoot(env, 'harmadik')).toBe('elso')
  })

  it('ha a régi token sora nincs meg, a hashe akkor is jó alany', async () => {
    // A régi tokennel indított hívások pont ezen a néven könyvelődtek.
    const env = envWithSubscriptions({
      uj: { state: 'ACTIVE', expires_at: null, verified_at: 0, linked_from: 'regi' },
    })
    expect(await subscriptionRoot(env, 'uj')).toBe('regi')
  })

  it('egy körkörös adat sem akasztja meg', async () => {
    const env = envWithSubscriptions({
      a: { state: 'ACTIVE', expires_at: null, verified_at: 0, linked_from: 'b' },
      b: { state: 'ACTIVE', expires_at: null, verified_at: 0, linked_from: 'a' },
    })
    expect(['a', 'b']).toContain(await subscriptionRoot(env, 'a'))
  })
})

describe('a kvóta alanya a kérés útján', () => {
  const now = Date.now()
  const future = now + 30 * 24 * 60 * 60 * 1000

  it('csomagváltás után ugyanaz az alany — a havi keret nem indul újra', async () => {
    const oldHash = await sha256Hex('regi-vasarlasi-token')
    const newHash = await sha256Hex('uj-vasarlasi-token')
    const env = envWithSubscriptions({
      // Friss, gyorsítótárazott ellenőrzés: a Play API-t nem kell hívni.
      [newHash]: { state: 'ACTIVE', expires_at: future, verified_at: now, linked_from: oldHash },
      [oldHash]: { state: 'EXPIRED', expires_at: now - 1000, verified_at: now, linked_from: null },
    })

    const before = await resolveCaller(
      envWithSubscriptions({
        [oldHash]: { state: 'ACTIVE', expires_at: future, verified_at: now, linked_from: null },
      }),
      request('regi-vasarlasi-token'),
    )
    const after = await resolveCaller(env, request('uj-vasarlasi-token'))

    expect(before.tier).toBe('PREMIUM')
    expect(after.tier).toBe('PREMIUM')
    expect(after.subject, 'a váltás után is ugyanaz a kvótaalany').toBe(before.subject)
    expect(after.subject).toBe(`sub:${oldHash}`)
  })

  it('a lejárt előfizetés láncát nem követjük: ott a telepítés az alany', async () => {
    const oldHash = await sha256Hex('regi-vasarlasi-token')
    const newHash = await sha256Hex('uj-vasarlasi-token')
    const env = envWithSubscriptions({
      [newHash]: { state: 'EXPIRED', expires_at: now - 1000, verified_at: now, linked_from: oldHash },
    })

    const caller = await resolveCaller(env, request('uj-vasarlasi-token'))

    expect(caller.tier).toBe('FREE')
    expect(caller.subject.startsWith('user:')).toBe(true)
  })

  it('a már beolvasott sort nem olvassa be újra', async () => {
    // Minden prémium kérés ezen az úton megy át: egy felesleges D1-kör itt a
    // forgalommal arányos késés és költség.
    const hash = await sha256Hex('vasarlasi-token')
    const reads: string[] = []
    const env = envWithSubscriptions(
      { [hash]: { state: 'ACTIVE', expires_at: future, verified_at: now, linked_from: null } },
      reads,
    )

    const caller = await resolveCaller(env, request('vasarlasi-token'))

    expect(caller.subject).toBe(`sub:${hash}`)
    expect(reads).toEqual([hash])
  })

  it('váltás után csak a lánc régi szemét olvassa be pluszban', async () => {
    const oldHash = await sha256Hex('regi-vasarlasi-token')
    const newHash = await sha256Hex('uj-vasarlasi-token')
    const reads: string[] = []
    const env = envWithSubscriptions(
      {
        [newHash]: { state: 'ACTIVE', expires_at: future, verified_at: now, linked_from: oldHash },
        [oldHash]: { state: 'EXPIRED', expires_at: now - 1000, verified_at: now, linked_from: null },
      },
      reads,
    )

    const caller = await resolveCaller(env, request('uj-vasarlasi-token'))

    expect(caller.subject).toBe(`sub:${oldHash}`)
    expect(reads).toEqual([newHash, oldHash])
  })
})
