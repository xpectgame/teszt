import { describe, expect, it } from 'vitest'
import { DEFAULT_LIMITS, EMPTY_USAGE, GLOBAL_SUBJECT, checkQuota, dayKey, globalCeilingReached, periodKey, usageDelta } from '../src/limits.js'
import { isEntitled } from '../src/play.js'
import { costMicros } from '../src/anthropic.js'
import {
  PROMPT_HASHES,
  PLAN_SYSTEM_PROMPT,
  CHAT_SYSTEM_PROMPT,
  PLAN_SYSTEM_PROMPT_EN,
  CHAT_SYSTEM_PROMPT_EN,
} from '../src/prompts.js'

const free = DEFAULT_LIMITS.FREE
const premium = DEFAULT_LIMITS.PREMIUM
const owner = DEFAULT_LIMITS.OWNER

function check(overrides: Partial<Parameters<typeof checkQuota>[0]>) {
  return checkQuota({
    tier: 'FREE',
    limits: free,
    usage: { ...EMPTY_USAGE },
    task: 'PLAN',
    requestedDays: 3,
    chunkIndex: 0,
    isRetry: false,
    ...overrides,
  })
}

describe('kvóta', () => {
  it('az ingyenes sáv az első tervet kiszolgálja', () => {
    expect(check({}).allowed).toBe(true)
  })

  it('a második terv már elutasítás ugyanabban a hónapban', () => {
    const decision = check({ usage: { ...EMPTY_USAGE, plans: 1 } })
    expect(decision.allowed).toBe(false)
    expect(decision.code).toBe('PLAN_QUOTA')
  })

  it('a csomagnál hosszabb tervet elutasítja, nem vágja csendben', () => {
    // Levágni nem lehetne: a promptot a kliens írja, tehát a rövidítést nem tudnánk
    // kikényszeríteni — csak azt hinnénk, hogy megtettük.
    const denied = check({ requestedDays: 30 })
    expect(denied.allowed).toBe(false)
    expect(denied.code).toBe('PLAN_TOO_LONG')
    expect(check({ tier: 'PREMIUM', limits: premium, requestedDays: 30 }).allowed).toBe(true)
    expect(check({ tier: 'PREMIUM', limits: premium, requestedDays: 31 }).code).toBe('PLAN_TOO_LONG')
  })

  it('a folytatólagos szakaszok nem számítanak új tervnek', () => {
    const usage = { ...EMPTY_USAGE, plans: 1 }
    expect(check({ tier: 'PREMIUM', limits: premium, usage, chunkIndex: 1 }).allowed).toBe(true)
  })

  it('a javító kör sem számít új tervnek', () => {
    const usage = { ...EMPTY_USAGE, plans: 1 }
    expect(check({ usage, chunkIndex: 0, isRetry: true }).allowed).toBe(true)
    expect(check({ usage, chunkIndex: 0, isRetry: false }).code).toBe('PLAN_QUOTA')
  })

  it('az üzenetkeret elfogyása elutasítás', () => {
    const decision = check({ task: 'CHAT', usage: { ...EMPTY_USAGE, messages: free.chatMessagesPerMonth } })
    expect(decision.code).toBe('MESSAGE_QUOTA')
  })

  it('a prémium csomagban nincs darabszám-korlát', () => {
    const usage = { ...EMPTY_USAGE, plans: 500, messages: 5000 }
    expect(check({ tier: 'PREMIUM', limits: premium, usage }).allowed).toBe(true)
    expect(check({ tier: 'PREMIUM', limits: premium, usage, task: 'CHAT' }).allowed).toBe(true)
  })

  it('a fejlesztő saját buildje nem akad el darabszámon', () => {
    const usage = { ...EMPTY_USAGE, plans: 999, messages: 9999 }
    expect(check({ tier: 'OWNER', limits: owner, usage, requestedDays: 30 }).allowed).toBe(true)
    expect(check({ tier: 'OWNER', limits: owner, usage, task: 'CHAT' }).allowed).toBe(true)
    expect(check({ tier: 'OWNER', limits: owner, usage, task: 'DAY' }).allowed).toBe(true)
  })

  it('a fejlesztő saját buildje is beleütközik a tokenplafonba', () => {
    // Egy elszabadult ciklus vagy egy kiszivárgott kulcs itt akad meg.
    const usage = { ...EMPTY_USAGE, outputTokens: owner.outputTokenCap }
    expect(check({ tier: 'OWNER', limits: owner, usage }).code).toBe('TOKEN_CAP')
  })

  it('a nap átírása csak a teljes csomagban megy', () => {
    expect(check({ task: 'DAY' }).code).toBe('PREMIUM_ONLY')
    expect(check({ task: 'DAY', tier: 'PREMIUM', limits: premium }).allowed).toBe(true)
  })

  it('a tokenplafon mindent megelőz — prémiumban is', () => {
    const usage = { ...EMPTY_USAGE, outputTokens: premium.outputTokenCap }
    const decision = check({ tier: 'PREMIUM', limits: premium, usage })
    expect(decision.allowed).toBe(false)
    expect(decision.code).toBe('TOKEN_CAP')
  })

  it('a számlálót csak az első szakasz és a beszélgetés növeli', () => {
    expect(usageDelta('PLAN', 0, false)).toEqual({ plans: 1, messages: 0 })
    expect(usageDelta('PLAN', 2, false)).toEqual({ plans: 0, messages: 0 })
    expect(usageDelta('PLAN', 0, true)).toEqual({ plans: 0, messages: 0 })
    expect(usageDelta('CHAT', 0, false)).toEqual({ plans: 0, messages: 1 })
    expect(usageDelta('DAY', 0, false)).toEqual({ plans: 0, messages: 0 })
  })
})

describe('időszak kulcs', () => {
  it('naptári hónapot ad UTC szerint', () => {
    expect(periodKey(new Date('2026-01-31T23:30:00Z'))).toBe('2026-01')
    expect(periodKey(new Date('2026-02-01T00:30:00Z'))).toBe('2026-02')
    expect(periodKey(new Date('2026-12-09T10:00:00Z'))).toBe('2026-12')
  })
})

describe('előfizetés állapota', () => {
  const now = Date.parse('2026-06-01T00:00:00Z')

  it('az aktív és a türelmi idős előfizetés jár', () => {
    expect(isEntitled('ACTIVE', null, now)).toBe(true)
    expect(isEntitled('GRACE', null, now)).toBe(true)
  })

  it('a lemondott a kifizetett időszak végéig jár', () => {
    expect(isEntitled('CANCELED', now + 86_400_000, now)).toBe(true)
    expect(isEntitled('CANCELED', now - 1, now)).toBe(false)
    expect(isEntitled('CANCELED', null, now)).toBe(false)
  })

  it('a szüneteltetett, felfüggesztett és lejárt nem jár', () => {
    for (const state of ['PAUSED', 'ON_HOLD', 'EXPIRED', 'PENDING', 'REVOKED', 'UNKNOWN'] as const) {
      expect(isEntitled(state, now + 86_400_000, now)).toBe(false)
    }
  })
})

describe('költségszámítás', () => {
  it('a cache-ből olvasott bemenet a listaár tizede', () => {
    const full = costMicros('claude-sonnet-5', {
      inputTokens: 1000,
      outputTokens: 0,
      cacheReadTokens: 0,
      cacheWriteTokens: 0,
    })
    const cached = costMicros('claude-sonnet-5', {
      inputTokens: 0,
      outputTokens: 0,
      cacheReadTokens: 1000,
      cacheWriteTokens: 0,
    })
    expect(full).toBe(2000)
    expect(cached).toBe(200)
  })

  it('egy heti terv nagyságrendje néhány tized cent', () => {
    // ~2k bemenet + ~9k kimenet Sonnet 5-ön: 0,004 + 0,09 dollár
    const micros = costMicros('claude-sonnet-5', {
      inputTokens: 2000,
      outputTokens: 9000,
      cacheReadTokens: 0,
      cacheWriteTokens: 0,
    })
    expect(micros / 1_000_000).toBeCloseTo(0.094, 3)
  })

  it('ismeretlen modellre is ad árat, nem nullát', () => {
    expect(costMicros('valami-uj-modell', {
      inputTokens: 1000, outputTokens: 1000, cacheReadTokens: 0, cacheWriteTokens: 0,
    })).toBeGreaterThan(0)
  })
})

describe('rendszerpromptok', () => {
  it('a hash egyezik a tárolt értékkel — ha nem, a klienssel szétcsúszott', async () => {
    const hash = async (value: string) => {
      const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value))
      return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('')
    }
    expect(await hash(PLAN_SYSTEM_PROMPT)).toBe(PROMPT_HASHES.plan)
    expect(await hash(CHAT_SYSTEM_PROMPT)).toBe(PROMPT_HASHES.chat)
    expect(await hash(PLAN_SYSTEM_PROMPT_EN)).toBe(PROMPT_HASHES.plan_en)
    expect(await hash(CHAT_SYSTEM_PROMPT_EN)).toBe(PROMPT_HASHES.chat_en)
  })
})

describe('globális napi mennyezet', () => {
  it('a küszöb alatt átenged', () => {
    expect(globalCeilingReached(249_999, 250_000)).toBe(false)
  })

  it('a küszöbön és fölötte megállít', () => {
    expect(globalCeilingReached(250_000, 250_000)).toBe(true)
    expect(globalCeilingReached(900_000, 250_000)).toBe(true)
  })

  // Egy elgépelt vagy törölt beállítás nem állíthatja le a szolgáltatást mindenkinek:
  // a hiányzó plafon azt jelenti, hogy nincs mennyezet, nem azt, hogy nulla.
  it('értelmetlen plafon esetén nem korlátoz', () => {
    for (const bad of [Number.NaN, 0, -1]) {
      expect(globalCeilingReached(10_000_000, bad)).toBe(false)
    }
  })

  it('a napi kulcs UTC nap, a havitól eltérő alakban', () => {
    const at = new Date('2026-09-14T23:30:00Z')
    expect(dayKey(at)).toBe('2026-09-14')
    expect(periodKey(at)).toBe('2026-09')
  })

  // A közös számláló ugyanabban a táblában lakik, mint a felhasználóké. Ha egy valódi
  // alany elő tudná állítani ezt a kulcsot, elrontaná vagy kiolvashatná a mennyezetet.
  it('a fenntartott alany nem ütközhet felhasználóival', () => {
    const userSubjects = ['owner:abc123', 'user:abc123', 'sub:abc123']
    expect(userSubjects).not.toContain(GLOBAL_SUBJECT)
    for (const subject of userSubjects) {
      expect(subject.startsWith('global:')).toBe(false)
    }
  })
})
