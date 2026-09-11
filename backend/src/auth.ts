import type { Env } from './env.js'
import { intVar } from './env.js'
import { bindPurchase, readSubscription, sha256Hex, touchUser, writeSubscription } from './db.js'
import { DEFAULT_LIMITS, type Tier, type TierLimits } from './limits.js'
import { isEntitled, verifySubscription, type SubscriptionState } from './play.js'

/** Az előfizetést nem kérdezzük meg a Google-től minden hívásnál. */
const SUBSCRIPTION_CACHE_MS = 6 * 60 * 60 * 1000

/** Hálózati hiba esetén ennyi ideig még elhisszük a korábbi, érvényes ellenőrzést. */
const SUBSCRIPTION_GRACE_MS = 72 * 60 * 60 * 1000

export class AuthError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message)
  }
}

export interface Caller {
  userId: string
  tier: Tier
  limits: TierLimits
  /** A kvóta alanya: prémiumnál az előfizetés, ingyenesnél a telepítés. */
  subject: string
  subscriptionState: SubscriptionState | null
  expiresAt: number | null
  appVersion: string | null
}

/**
 * A telepítési azonosító formai ellenőrzése. Nem jogosultság: bárki generálhat újat.
 * Ezért van az ingyenes sávon szigorú tokenplafon, és ezért érdemes később a
 * Play Integrity API-t is bekötni (lásd README).
 */
function readInstallToken(request: Request): string {
  const header = request.headers.get('authorization') ?? ''
  const token = header.startsWith('Bearer ') ? header.slice(7).trim() : ''
  if (!/^[A-Za-z0-9_-]{22,128}$/.test(token)) {
    throw new AuthError('Hiányzó vagy hibás azonosító.', 401)
  }
  return token
}

function limitsFor(env: Env, tier: Tier): TierLimits {
  const base = DEFAULT_LIMITS[tier]
  const configured =
    tier === 'OWNER'
      ? env.OWNER_OUTPUT_TOKEN_CAP
      : tier === 'PREMIUM'
        ? env.PREMIUM_OUTPUT_TOKEN_CAP
        : env.FREE_OUTPUT_TOKEN_CAP
  return { ...base, outputTokenCap: intVar(configured, base.outputTokenCap) }
}

/**
 * Időzítésre nem árulkodó összehasonlítás.
 *
 * Egy sima `===` a nem egyező karakternél azonnal visszatér, amiből elvileg ki lehet
 * mérni a kulcsot. Itt nem valószínű támadás, de a helyes forma nem kerül semmibe.
 */
function secretMatches(provided: string, expected: string): boolean {
  if (provided.length !== expected.length) return false
  let diff = 0
  for (let i = 0; i < provided.length; i++) {
    diff |= provided.charCodeAt(i) ^ expected.charCodeAt(i)
  }
  return diff === 0
}

export async function resolveCaller(env: Env, request: Request): Promise<Caller> {
  const installToken = readInstallToken(request)
  const userId = await sha256Hex(installToken)
  const appVersion = request.headers.get('x-app-version')
  const user = await touchUser(env, userId, appVersion)

  // A fejlesztő saját buildje. Ez megelőz mindent: nem a boltból jön, nincs vásárlási
  // tokenje, és nem is kellene, hogy legyen.
  const ownerKey = request.headers.get('x-owner-key')?.trim() || null
  if (ownerKey && env.OWNER_KEY && secretMatches(ownerKey, env.OWNER_KEY)) {
    return {
      userId,
      tier: 'OWNER',
      limits: limitsFor(env, 'OWNER'),
      subject: `owner:${userId}`,
      subscriptionState: null,
      expiresAt: null,
      appVersion,
    }
  }

  const purchaseToken = request.headers.get('x-play-purchase-token')?.trim() || null
  const now = Date.now()

  let purchaseHash: string | null = null
  let state: SubscriptionState | null = null
  let expiresAt: number | null = null

  if (purchaseToken) {
    purchaseHash = await sha256Hex(purchaseToken)
    const cached = await readSubscription(env, purchaseHash)
    const fresh = cached && now - cached.verified_at < SUBSCRIPTION_CACHE_MS

    if (fresh && cached) {
      state = cached.state as SubscriptionState
      expiresAt = cached.expires_at
    } else {
      try {
        const verified = await verifySubscription(env, purchaseToken)
        if (verified) {
          state = verified.state
          expiresAt = verified.expiresAt
          await writeSubscription(env, {
            purchase_hash: purchaseHash,
            purchase_token: purchaseToken,
            state: verified.state,
            expires_at: verified.expiresAt,
            first_user_id: userId,
            linkedFrom: verified.linkedPurchaseToken ? await sha256Hex(verified.linkedPurchaseToken) : null,
          })
        } else if (cached) {
          state = cached.state as SubscriptionState
          expiresAt = cached.expires_at
        }
      } catch {
        // A Google elérhetetlen. Egy korábbi, még nem túl régi ellenőrzést elfogadunk:
        // egy fizető felhasználót nem tehetünk offline-ná azért, mert a Play akadozik.
        if (cached && now - cached.verified_at < SUBSCRIPTION_GRACE_MS) {
          state = cached.state as SubscriptionState
          expiresAt = cached.expires_at
        }
      }
    }
  } else if (user.purchase_hash) {
    // A kliens most nem küldött tokent (a Play épp nem elérhető az eszközön), de
    // korábban ide kötöttünk egy előfizetést. Amíg nem járt le, érvényes marad.
    const cached = await readSubscription(env, user.purchase_hash)
    if (cached && now - cached.verified_at < SUBSCRIPTION_GRACE_MS) {
      purchaseHash = cached.purchase_hash
      state = cached.state as SubscriptionState
      expiresAt = cached.expires_at
    }
  }

  const entitled = state !== null && isEntitled(state, expiresAt, now)
  const tier: Tier = entitled ? 'PREMIUM' : 'FREE'

  if (entitled && purchaseHash && user.purchase_hash !== purchaseHash) {
    await bindPurchase(env, userId, purchaseHash)
  }

  return {
    userId,
    tier,
    limits: limitsFor(env, tier),
    // A kvóta prémiumnál az ELŐFIZETÉSHEZ tartozik, nem a telepítéshez: egy előfizetésből
    // nem lehet több készülék újratelepítésével többszörös keretet csinálni.
    subject: entitled && purchaseHash ? `sub:${purchaseHash}` : `user:${userId}`,
    subscriptionState: state,
    expiresAt,
    appVersion,
  }
}
