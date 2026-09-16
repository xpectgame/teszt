import { describe, expect, it } from 'vitest'
import { isEntitled, readPurchase } from '../src/play.js'

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
