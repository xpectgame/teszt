import type { Env } from './env.js'

/**
 * Google Play előfizetés-ellenőrzés a Play Developer API-val.
 *
 * Ez a lényegi különbség a korábbi, csak kliensoldali állapothoz képest: itt a Google
 * mondja meg, hogy valaki előfizető-e, nem az app. Egy módosított kliens hazudhat a
 * saját jogosultságáról, de vásárlási tokent nem tud hamisítani.
 */

export type SubscriptionState =
  | 'ACTIVE'
  | 'GRACE'
  | 'PENDING'
  | 'PAUSED'
  | 'ON_HOLD'
  | 'CANCELED'
  | 'EXPIRED'
  | 'REVOKED'
  /**
   * A token létezik, de NEM a mi termékünkre szól (vagy egyetlen ránk vonatkozó
   * tételt sem tartalmaz). Saját állapot, nem a Play-től jön.
   *
   * Azért állapot és nem külön mező: a jogosultságot a hívó a tárolt állapotból is
   * kiszámolja, amikor a gyorsítótárból dolgozik. Egy külön logikai mező, ami nem fér
   * be az állapot mellé az adatbázisba, pont a gyorsítótáras úton veszne el — és a
   * hatodik óra után mindenki jogosulttá válna, akit az első hívás elutasított.
   */
  | 'OTHER_PRODUCT'
  | 'UNKNOWN'

export interface VerifiedSubscription {
  state: SubscriptionState
  entitled: boolean
  expiresAt: number | null
  linkedPurchaseToken: string | null
}

/** A lemondott előfizetés a kifizetett időszak végéig jár — ez a Play elvárása is. */
export function isEntitled(state: SubscriptionState, expiresAt: number | null, now: number): boolean {
  if (state === 'ACTIVE' || state === 'GRACE') return true
  if (state === 'CANCELED') return expiresAt !== null && expiresAt > now
  return false
}

function mapState(raw: string | undefined): SubscriptionState {
  switch (raw) {
    case 'SUBSCRIPTION_STATE_ACTIVE':
      return 'ACTIVE'
    case 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD':
      return 'GRACE'
    case 'SUBSCRIPTION_STATE_PENDING':
      return 'PENDING'
    case 'SUBSCRIPTION_STATE_PAUSED':
      return 'PAUSED'
    case 'SUBSCRIPTION_STATE_ON_HOLD':
      return 'ON_HOLD'
    case 'SUBSCRIPTION_STATE_CANCELED':
      return 'CANCELED'
    case 'SUBSCRIPTION_STATE_EXPIRED':
      return 'EXPIRED'
    default:
      return 'UNKNOWN'
  }
}

interface ServiceAccount {
  client_email: string
  private_key: string
  token_uri?: string
}

function base64Url(bytes: ArrayBuffer | Uint8Array): string {
  const view = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes)
  let binary = ''
  for (const byte of view) binary += String.fromCharCode(byte)
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function pemToPkcs8(pem: string): ArrayBuffer {
  const body = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, '')
    .replace(/-----END PRIVATE KEY-----/, '')
    .replace(/\s+/g, '')
  const binary = atob(body)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
  return bytes.buffer
}

/**
 * A hozzáférési token gyorsítótára, izolátumonként.
 *
 * A Google tokenje egy óráig él, mi viszont minden egyes ellenőrzésnél újat kértünk:
 * egy RSA-aláírás és egy plusz hálózati kör a Play hívása ELŐTT — egy prémium
 * felhasználó kérésének útjában, valahányszor lejár a hatórás gyorsítótár, és minden
 * RTDN-értesítésnél. A kulcs a szolgáltatásfiók: ha a titkot lecserélik, a régi
 * token nem szolgálhatja ki az újat.
 */
let tokenCache: { account: string; token: string; expiresAt: number } | null = null

/** Ennyivel a lejárat előtt már újat kérünk: egy épp lejáró token a hívás közben halna el. */
const TOKEN_REFRESH_MARGIN_MS = 5 * 60 * 1000

/** Teszteknek: a gyorsítótár modulszintű, tehát a tesztek között megmaradna. */
export function resetAccessTokenCache(): void {
  tokenCache = null
}

async function cachedAccessToken(account: ServiceAccount): Promise<string> {
  const now = Date.now()
  if (
    tokenCache &&
    tokenCache.account === account.client_email &&
    tokenCache.expiresAt - TOKEN_REFRESH_MARGIN_MS > now
  ) {
    return tokenCache.token
  }
  const fresh = await accessToken(account)
  tokenCache = { account: account.client_email, token: fresh.token, expiresAt: now + fresh.expiresInSec * 1000 }
  return fresh.token
}

/** Rövid életű hozzáférési token a szolgáltatásfiókból, kézzel aláírt JWT-vel. */
async function accessToken(account: ServiceAccount): Promise<{ token: string; expiresInSec: number }> {
  const now = Math.floor(Date.now() / 1000)
  const header = base64Url(new TextEncoder().encode(JSON.stringify({ alg: 'RS256', typ: 'JWT' })))
  const tokenUri = account.token_uri ?? 'https://oauth2.googleapis.com/token'
  const claims = base64Url(
    new TextEncoder().encode(
      JSON.stringify({
        iss: account.client_email,
        scope: 'https://www.googleapis.com/auth/androidpublisher',
        aud: tokenUri,
        iat: now,
        exp: now + 3600,
      }),
    ),
  )
  const unsigned = `${header}.${claims}`
  const key = await crypto.subtle.importKey(
    'pkcs8',
    pemToPkcs8(account.private_key),
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign'],
  )
  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(unsigned))
  const assertion = `${unsigned}.${base64Url(signature)}`

  const response = await fetch(tokenUri, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion,
    }),
  })
  if (!response.ok) {
    throw new Error(`A Play hozzáférési token kérése nem sikerült: ${response.status}`)
  }
  const body = (await response.json()) as { access_token?: string; expires_in?: number }
  if (!body.access_token) throw new Error('A Play hozzáférési token üres.')
  const expiresInSec = typeof body.expires_in === 'number' && body.expires_in > 0 ? body.expires_in : 3600
  return { token: body.access_token, expiresInSec }
}

/**
 * Egy vásárlási token ellenőrzése.
 *
 * Ha a szolgáltatásfiók nincs beállítva, `null`-t ad vissza — ilyenkor a hívó dönti el,
 * mit tesz. Fejlesztés közben ez kényelmes, élesben viszont a beállítás hiánya azt
 * jelenti, hogy senki nem kap prémium kiszolgálást.
 */
export async function verifySubscription(
  env: Env,
  purchaseToken: string,
): Promise<VerifiedSubscription | null> {
  if (!env.PLAY_SERVICE_ACCOUNT_JSON) return null

  const account = JSON.parse(env.PLAY_SERVICE_ACCOUNT_JSON) as ServiceAccount
  const token = await cachedAccessToken(account)
  const url =
    `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/` +
    `${encodeURIComponent(env.ANDROID_PACKAGE)}/purchases/subscriptionsv2/tokens/` +
    `${encodeURIComponent(purchaseToken)}`

  const response = await fetch(url, { headers: { authorization: `Bearer ${token}` } })

  if (response.status === 404 || response.status === 410) {
    return { state: 'EXPIRED', entitled: false, expiresAt: null, linkedPurchaseToken: null }
  }
  if (response.status === 401) {
    // A tárolt token érvénytelen lett (visszavonták, lecserélték a fiók kulcsát). Ne
    // ragadjunk le vele egy órára: a következő hívás újat kér.
    tokenCache = null
  }
  if (!response.ok) {
    throw new Error(`A Play ellenőrzés hibát adott: ${response.status}`)
  }

  const body = (await response.json()) as PurchaseBody
  return readPurchase(body, env.PREMIUM_PRODUCT_ID, Date.now())
}

export interface PurchaseBody {
  subscriptionState?: string
  linkedPurchaseToken?: string
  lineItems?: Array<{ expiryTime?: string; productId?: string }>
}

/**
 * A Play válaszából olvassa ki, jár-e a prémium — hálózat nélkül, hogy tesztelhető legyen.
 *
 * A TERMÉKET is nézi, nem csak az állapotot. A hívás a csomagnévre van szűkítve, tehát
 * ma — egyetlen termékkel — minden érvényes token úgyis a prémiumra szól. A második
 * termék bevezetésekor viszont (éves csomag, olcsóbb sáv) e nélkül BÁRMELYIKRE szóló
 * előfizetés teljes prémiumot adna. Ez pénzügyi hiba, és a bevezetés pillanatában
 * senkinek nem tűnne fel.
 *
 * Ha a [productId] üres, nem szűrünk: így egy elgépelt beállítás nem veszi el mindenkitől
 * az előfizetést, csak visszaáll a korábbi, engedékenyebb viselkedésre.
 */
export function readPurchase(
  body: PurchaseBody,
  productId: string | undefined,
  now: number,
): VerifiedSubscription {
  const state = mapState(body.subscriptionState)

  const items = body.lineItems ?? []
  const matching = productId ? items.filter((item) => item.productId === productId) : items

  const expiryIso = matching.map((item) => item.expiryTime).filter(Boolean).sort().pop()
  const parsed = expiryIso ? Date.parse(expiryIso) : NaN
  const expiresAt = Number.isFinite(parsed) ? parsed : null

  // Nincs a keresett termékre szóló tétel: az előfizetés létezik, de nem ezé.
  const forThisProduct = matching.length > 0

  // Az állapotba ÍRJUK BELE, hogy nem a mi termékünkről van szó — nem csak az
  // `entitled` mezőbe. A hívó a gyorsítótárból dolgozva már csak az állapotot és a
  // lejáratot látja: ha a termék eredménye csak az `entitled` mezőben élne, a
  // gyorsítótár első frissülése után egy idegen termékre szóló, aktív előfizetés
  // teljes prémiumot adna.
  const effectiveState: SubscriptionState = forThisProduct ? state : 'OTHER_PRODUCT'

  return {
    state: effectiveState,
    entitled: forThisProduct && isEntitled(state, expiresAt, now),
    expiresAt,
    linkedPurchaseToken: body.linkedPurchaseToken ?? null,
  }
}
