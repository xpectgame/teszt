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

/** Rövid életű hozzáférési token a szolgáltatásfiókból, kézzel aláírt JWT-vel. */
async function accessToken(account: ServiceAccount): Promise<string> {
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
  const body = (await response.json()) as { access_token?: string }
  if (!body.access_token) throw new Error('A Play hozzáférési token üres.')
  return body.access_token
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
  const token = await accessToken(account)
  const url =
    `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/` +
    `${encodeURIComponent(env.ANDROID_PACKAGE)}/purchases/subscriptionsv2/tokens/` +
    `${encodeURIComponent(purchaseToken)}`

  const response = await fetch(url, { headers: { authorization: `Bearer ${token}` } })

  if (response.status === 404 || response.status === 410) {
    return { state: 'EXPIRED', entitled: false, expiresAt: null, linkedPurchaseToken: null }
  }
  if (!response.ok) {
    throw new Error(`A Play ellenőrzés hibát adott: ${response.status}`)
  }

  const body = (await response.json()) as {
    subscriptionState?: string
    linkedPurchaseToken?: string
    lineItems?: Array<{ expiryTime?: string }>
  }

  const state = mapState(body.subscriptionState)
  const expiryIso = body.lineItems?.map((item) => item.expiryTime).filter(Boolean).sort().pop()
  const expiresAt = expiryIso ? Date.parse(expiryIso) : null

  return {
    state,
    entitled: isEntitled(state, expiresAt, Date.now()),
    expiresAt: Number.isFinite(expiresAt) ? expiresAt : null,
    linkedPurchaseToken: body.linkedPurchaseToken ?? null,
  }
}
