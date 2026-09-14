import { describe, expect, it } from 'vitest'
import { readPurchase } from '../src/play.js'

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
