import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { isEntitled, readPurchase, resetAccessTokenCache, verifySubscription } from '../src/play.js'

const NOW = Date.parse('2026-09-14T12:00:00Z')
const LATER = '2026-10-14T12:00:00Z'
const EARLIER = '2026-09-01T12:00:00Z'

const PREMIUM = 'mealpilot_premium_monthly'

describe('readPurchase', () => {
  it('az aktív előfizetés a mi termékünkre jogosít', () => {
    const result = readPurchase(
      {
        subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE',
        lineItems: [{ productId: PREMIUM, expiryTime: LATER }],
      },
      PREMIUM,
      NOW,
    )

    expect(result.entitled).toBe(true)
    expect(result.state).toBe('ACTIVE')
    expect(result.expiresAt).toBe(Date.parse(LATER))
  })

  it('MÁS termékre szóló előfizetés nem jogosít', () => {
    // Ez a lényeg. A hívás a csomagnévre van szűkítve, tehát egy másik termék
    // ugyanezen az appon belül teljes prémiumot adna, ha nem néznénk a terméket.
    const result = readPurchase(
      {
        subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE',
        lineItems: [{ productId: 'mealpilot_valami_mas', expiryTime: LATER }],
      },
      PREMIUM,
      NOW,
    )

    expect(result.entitled).toBe(false)
  })

  it('több tétel közül a miénket találja meg', () => {
    const result = readPurchase(
      {
        subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE',
        lineItems: [
          { productId: 'valami_mas', expiryTime: EARLIER },
          { productId: PREMIUM, expiryTime: LATER },
        ],
      },
      PREMIUM,
      NOW,
    )

    expect(result.entitled).toBe(true)
    expect(result.expiresAt).toBe(Date.parse(LATER))
  })

  it('a lemondott előfizetés a lejáratig jár', () => {
    const active = readPurchase(
      {
        subscriptionState: 'SUBSCRIPTION_STATE_CANCELED',
        lineItems: [{ productId: PREMIUM, expiryTime: LATER }],
      },
      PREMIUM,
      NOW,
    )
    expect(active.entitled).toBe(true)

    const done = readPurchase(
      {
        subscriptionState: 'SUBSCRIPTION_STATE_CANCELED',
        lineItems: [{ productId: PREMIUM, expiryTime: EARLIER }],
      },
      PREMIUM,
      NOW,
    )
    expect(done.entitled).toBe(false)
  })

  it('a türelmi idő még jogosít, a felfüggesztés nem', () => {
    const grace = readPurchase(
      {
        subscriptionState: 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD',
        lineItems: [{ productId: PREMIUM, expiryTime: LATER }],
      },
      PREMIUM,
      NOW,
    )
    expect(grace.entitled).toBe(true)

    const hold = readPurchase(
      {
        subscriptionState: 'SUBSCRIPTION_STATE_ON_HOLD',
        lineItems: [{ productId: PREMIUM, expiryTime: LATER }],
      },
      PREMIUM,
      NOW,
    )
    expect(hold.entitled).toBe(false)
  })

  it('tétel nélküli válasz nem jogosít', () => {
    const result = readPurchase({ subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE' }, PREMIUM, NOW)
    expect(result.entitled).toBe(false)
  })

  it('beállított termékazonosító nélkül nem szűrünk', () => {
    // Egy elgépelt beállítás ne vegye el MINDENKITŐL az előfizetést: ilyenkor
    // visszaáll a korábbi, engedékenyebb viselkedés.
    const result = readPurchase(
      {
        subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE',
        lineItems: [{ productId: 'barmi', expiryTime: LATER }],
      },
      undefined,
      NOW,
    )
    expect(result.entitled).toBe(true)
  })

  it('a hibás lejárati dátum nem lesz NaN', () => {
    const result = readPurchase(
      {
        subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE',
        lineItems: [{ productId: PREMIUM, expiryTime: 'ez nem dátum' }],
      },
      PREMIUM,
      NOW,
    )
    expect(result.expiresAt).toBeNull()
    expect(result.entitled).toBe(true)
  })
})

describe('a termékellenőrzés a hitelesítés útján is érvényes', () => {
  /**
   * A jogosultságot a hitelesítés NEM az `entitled` mezőből olvassa: azt a tárolt
   * állapotból és lejáratból számolja újra, mert a gyorsítótárból dolgozva csak ez a
   * kettő áll rendelkezésre. A termékellenőrzés tehát csak akkor ér valamit, ha az
   * ÁLLAPOTBA is beleíródik — különben a gyorsítótár első frissülése után egy idegen
   * termékre szóló előfizetés teljes prémiumot adna.
   *
   * A mai egytermékes állapotban ez nem látszana. A második csomag bevezetésekor
   * viszont pénzügyi hiba lenne, és pont akkor nem tűnne fel senkinek.
   */
  function entitledFromStoredState(result: ReturnType<typeof readPurchase>): boolean {
    return isEntitled(result.state, result.expiresAt, NOW)
  }

  it('a mi termékünk a tárolt állapotból is jogosít', () => {
    const ours = readPurchase(
      {
        subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE',
        lineItems: [{ productId: PREMIUM, expiryTime: LATER }],
      },
      PREMIUM,
      NOW,
    )
    expect(entitledFromStoredState(ours)).toBe(true)
  })

  it('MÁS termék a tárolt állapotból sem jogosít', () => {
    const other = readPurchase(
      {
        subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE',
        lineItems: [{ productId: 'mealpilot_valami_mas', expiryTime: LATER }],
      },
      PREMIUM,
      NOW,
    )
    expect(other.state).toBe('OTHER_PRODUCT')
    expect(entitledFromStoredState(other)).toBe(false)
  })

  it('a tétel nélküli válasz a tárolt állapotból sem jogosít', () => {
    const empty = readPurchase({ subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE' }, PREMIUM, NOW)
    expect(entitledFromStoredState(empty)).toBe(false)
  })

  it('a lemondott, de még futó előfizetés a tárolt állapotból is jogosít', () => {
    // Fontos, hogy a javítás ezt NE vigye el: a lemondás után a kifizetett
    // időszak végéig jár a csomag, és ezt a Play is elvárja.
    const canceled = readPurchase(
      {
        subscriptionState: 'SUBSCRIPTION_STATE_CANCELED',
        lineItems: [{ productId: PREMIUM, expiryTime: LATER }],
      },
      PREMIUM,
      NOW,
    )
    expect(canceled.state).toBe('CANCELED')
    expect(entitledFromStoredState(canceled)).toBe(true)
  })
})

describe('a Play hozzáférési token', () => {
  /**
   * A Google tokenje egy óráig él. Korábban minden ellenőrzés újat kért: egy
   * RSA-aláírás és egy plusz hálózati kör a Play hívása előtt.
   */
  const original = globalThis.fetch
  let tokenCalls = 0
  let apiStatus = 200
  let env: any

  async function serviceAccountJson(): Promise<string> {
    const pair = (await crypto.subtle.generateKey(
      { name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' },
      true,
      ['sign', 'verify'],
    )) as CryptoKeyPair
    const pkcs8 = new Uint8Array(await crypto.subtle.exportKey('pkcs8', pair.privateKey) as ArrayBuffer)
    let binary = ''
    for (const byte of pkcs8) binary += String.fromCharCode(byte)
    const pem = `-----BEGIN PRIVATE KEY-----\n${btoa(binary)}\n-----END PRIVATE KEY-----\n`
    return JSON.stringify({ client_email: 'teszt@example.iam.gserviceaccount.com', private_key: pem })
  }

  beforeEach(async () => {
    resetAccessTokenCache()
    tokenCalls = 0
    apiStatus = 200
    env = {
      ANDROID_PACKAGE: 'hu.mealpilot.app',
      PREMIUM_PRODUCT_ID: PREMIUM,
      PLAY_SERVICE_ACCOUNT_JSON: await serviceAccountJson(),
    }
    globalThis.fetch = (async (input: any) => {
      const url = String(input?.url ?? input)
      if (url.includes('oauth2.googleapis.com')) {
        tokenCalls++
        return Response.json({ access_token: `token-${tokenCalls}`, expires_in: 3599 })
      }
      if (apiStatus !== 200) return new Response('', { status: apiStatus })
      return Response.json({
        subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE',
        lineItems: [{ productId: PREMIUM, expiryTime: LATER }],
      })
    }) as typeof fetch
  })

  afterEach(() => {
    globalThis.fetch = original
    resetAccessTokenCache()
  })

  it('egy tokennel több ellenőrzés is lefut', async () => {
    await verifySubscription(env, 'elso-token')
    await verifySubscription(env, 'masodik-token')
    await verifySubscription(env, 'harmadik-token')

    expect(tokenCalls).toBe(1)
  })

  it('elutasított token után újat kér, nem ragad le vele egy órára', async () => {
    await verifySubscription(env, 'elso-token')
    apiStatus = 401
    await expect(verifySubscription(env, 'masodik-token')).rejects.toThrow()
    apiStatus = 200
    await verifySubscription(env, 'harmadik-token')

    expect(tokenCalls).toBe(2)
  })

  it('másik szolgáltatásfiók tokenjét nem használja', async () => {
    await verifySubscription(env, 'elso-token')
    const other = { ...env, PLAY_SERVICE_ACCOUNT_JSON: env.PLAY_SERVICE_ACCOUNT_JSON.replace('teszt@', 'masik@') }
    await verifySubscription(other, 'masodik-token')

    expect(tokenCalls).toBe(2)
  })
})
