import type { Env } from './env.js'
import { EMPTY_USAGE, type UsageRow } from './limits.js'

/** SHA-256 hex. A tokeneket sosem tároljuk nyersen, csak a hashüket. */
export async function sha256Hex(value: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value))
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('')
}

export interface UserRow {
  id: string
  purchase_hash: string | null
}

/**
 * A felhasználó sorának frissítése ÉS kiolvasása, egyetlen utasításban.
 *
 * Minden hitelesített kérés ezzel kezd. Eddig két D1-kör volt (upsert, aztán egy
 * külön SELECT), és mindkettő az első bájt előtt — a `RETURNING` ugyanazt egy körben
 * adja. A valódi motoron a `tools/check-migrations.py` futtatja.
 */
// TOUCH_USER_SQL_BEGIN
export const TOUCH_USER_SQL = `INSERT INTO users (id, created_at, last_seen_at, app_version)
     VALUES (?1, ?2, ?2, ?3)
     ON CONFLICT(id) DO UPDATE SET last_seen_at = ?2, app_version = COALESCE(?3, users.app_version)
     RETURNING id, purchase_hash`
// TOUCH_USER_SQL_END

export async function touchUser(env: Env, userId: string, appVersion: string | null): Promise<UserRow> {
  const row = await env.DB.prepare(TOUCH_USER_SQL).bind(userId, Date.now(), appVersion).first<UserRow>()
  return row ?? { id: userId, purchase_hash: null }
}

export async function bindPurchase(env: Env, userId: string, purchaseHash: string | null): Promise<void> {
  await env.DB.prepare('UPDATE users SET purchase_hash = ?2 WHERE id = ?1').bind(userId, purchaseHash).run()
}

export interface SubscriptionRow {
  purchase_hash: string
  state: string
  expires_at: number | null
  verified_at: number
  first_user_id: string | null
  /** Előfizetés-csere (váltás, újrafeliratkozás) esetén a korábbi token hashe. */
  linked_from?: string | null
}

export async function readSubscription(env: Env, purchaseHash: string): Promise<SubscriptionRow | null> {
  return await env.DB.prepare('SELECT * FROM subscriptions WHERE purchase_hash = ?1')
    .bind(purchaseHash)
    .first<SubscriptionRow>()
}

export async function writeSubscription(
  env: Env,
  row: Omit<SubscriptionRow, 'verified_at'> & { linkedFrom?: string | null },
): Promise<void> {
  await env.DB.prepare(
    `INSERT INTO subscriptions (purchase_hash, state, expires_at, verified_at, first_user_id, linked_from)
     VALUES (?1, ?2, ?3, ?4, ?5, ?6)
     ON CONFLICT(purchase_hash) DO UPDATE SET
       state = ?2, expires_at = ?3, verified_at = ?4,
       first_user_id = COALESCE(subscriptions.first_user_id, ?5),
       linked_from = COALESCE(?6, subscriptions.linked_from)`,
  )
    .bind(
      row.purchase_hash,
      row.state,
      row.expires_at,
      Date.now(),
      row.first_user_id,
      row.linkedFrom ?? null,
    )
    .run()
}

export async function readUsage(env: Env, subject: string, period: string): Promise<UsageRow> {
  const row = await env.DB.prepare(
    'SELECT plans, messages, input_tokens, output_tokens FROM usage WHERE subject = ?1 AND period = ?2',
  )
    .bind(subject, period)
    .first<{ plans: number; messages: number; input_tokens: number; output_tokens: number }>()
  if (!row) return { ...EMPTY_USAGE }
  return {
    plans: row.plans,
    messages: row.messages,
    inputTokens: row.input_tokens,
    outputTokens: row.output_tokens,
  }
}

export async function addUsage(
  env: Env,
  subject: string,
  period: string,
  delta: { plans?: number; messages?: number; inputTokens?: number; outputTokens?: number; costMicros?: number },
): Promise<void> {
  await env.DB.prepare(
    `INSERT INTO usage (subject, period, plans, messages, input_tokens, output_tokens, cost_micros, updated_at)
     VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)
     ON CONFLICT(subject, period) DO UPDATE SET
       plans = usage.plans + ?3,
       messages = usage.messages + ?4,
       input_tokens = usage.input_tokens + ?5,
       output_tokens = usage.output_tokens + ?6,
       cost_micros = usage.cost_micros + ?7,
       updated_at = ?8`,
  )
    .bind(
      subject,
      period,
      delta.plans ?? 0,
      delta.messages ?? 0,
      delta.inputTokens ?? 0,
      delta.outputTokens ?? 0,
      delta.costMicros ?? 0,
      Date.now(),
    )
    .run()
}

export async function logRequest(
  env: Env,
  row: {
    id: string
    userId: string
    subject: string
    task: string
    model: string
    inputTokens: number
    outputTokens: number
    cacheReadTokens: number
    costMicros: number
    ok: boolean
    error?: string | null
  },
): Promise<void> {
  await env.DB.prepare(
    `INSERT INTO requests
       (id, user_id, subject, task, model, input_tokens, output_tokens, cache_read_tokens, cost_micros, ok, error, created_at)
     VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12)`,
  )
    .bind(
      row.id,
      row.userId,
      row.subject,
      row.task,
      row.model,
      row.inputTokens,
      row.outputTokens,
      row.cacheReadTokens,
      row.costMicros,
      row.ok ? 1 : 0,
      row.error ?? null,
      Date.now(),
    )
    .run()
}

/**
 * Meddig él egy foglalás.
 *
 * A leghosszabb hívás egy tervszakasz: 24 000 kimeneti token streamelve, gondolkodással
 * együtt is jóval tíz perc alatt. Ennél tovább csak az a foglalás él, amelyiknek a
 * könyvelése elmaradt — az viszont nem eheti meg örökre a keretet.
 */
export const RESERVATION_TTL_MS = 20 * 60 * 1000

/**
 * Az atomi foglalás.
 *
 * EGYETLEN feltételes beszúrás: a motor egy utasításon belül adja össze a már
 * elhasznált mennyiséget (`usage`) és a még folyamatban lévő foglalásokat, és csak
 * akkor ír, ha a kért mennyiség belefér. Két egyszerre érkező kérés így nem láthatja
 * ugyanazt a régi állapotot — pont ez volt a hiba.
 *
 * A feltételek ugyanazok, mint a `checkQuota`-ban:
 *  - darabszám (terv, üzenet): csak akkor számít, ha a hívás tényleg fogyaszt belőle
 *    (`?4 = 0` — egy folytatólagos szakasz vagy javító kör nem új terv), és a
 *    meglévő + folyamatban lévő + új nem lépheti át a korlátot;
 *  - token: a meglévő + folyamatban lévő legyen a plafon ALATT. Ugyanaz a szabály,
 *    mint eddig egyetlen kérésnél (a plafon alatt még indulhat egy hívás) — csak most
 *    a folyamatban lévők is beleszámítanak.
 *
 * A negatív korlát korlátlant jelent. A `tools/check-migrations.py` ezt a szöveget
 * olvassa ki és futtatja le valódi SQLite motoron — ne írd át a két jelölő nélkül.
 */
// RESERVE_SQL_BEGIN
export const RESERVE_SQL = `
INSERT INTO reservations (id, subject, period, plans, messages, output_tokens, created_at)
SELECT ?1, ?2, ?3, ?4, ?5, ?6, ?7
WHERE
  (?9 < 0 OR ?4 = 0 OR
     COALESCE((SELECT plans FROM usage WHERE subject = ?2 AND period = ?3), 0)
     + COALESCE((SELECT SUM(plans) FROM reservations WHERE subject = ?2 AND period = ?3 AND created_at > ?8), 0)
     + ?4 <= ?9)
  AND (?10 < 0 OR ?5 = 0 OR
     COALESCE((SELECT messages FROM usage WHERE subject = ?2 AND period = ?3), 0)
     + COALESCE((SELECT SUM(messages) FROM reservations WHERE subject = ?2 AND period = ?3 AND created_at > ?8), 0)
     + ?5 <= ?10)
  AND (?11 < 0 OR
     COALESCE((SELECT output_tokens FROM usage WHERE subject = ?2 AND period = ?3), 0)
     + COALESCE((SELECT SUM(output_tokens) FROM reservations WHERE subject = ?2 AND period = ?3 AND created_at > ?8), 0)
     < ?11)
`
// RESERVE_SQL_END

export interface ReservationRequest {
  subject: string
  period: string
  plans: number
  messages: number
  outputTokens: number
  /** -1 = korlátlan. */
  planLimit: number
  /** -1 = korlátlan. */
  messageLimit: number
  /** -1 = nincs plafon. */
  outputTokenCap: number
}

/**
 * Foglalás. Az azonosítót adja vissza, vagy `null`-t, ha nem fért bele.
 *
 * Ha maga a tábla hiányzik (a 0006 migráció nem futott le a deploy előtt), NEM
 * tesszük tönkre a kérést: a foglalás kimarad, és marad a korábbi, előzetes
 * ellenőrzés. Ez sosem rosszabb a javítás előtti állapotnál, egy elfelejtett
 * migrációs pipa viszont nem állíthatja le a tervezést minden felhasználónak.
 * A `/healthz` ilyenkor „nincs migrálva" állapotot jelez.
 */
export async function reserveUsage(
  env: Env,
  request: ReservationRequest,
): Promise<{ reserved: true; id: string | null } | { reserved: false }> {
  const id = crypto.randomUUID()
  const now = Date.now()
  try {
    const result = await env.DB.prepare(RESERVE_SQL)
      .bind(
        id,
        request.subject,
        request.period,
        request.plans,
        request.messages,
        request.outputTokens,
        now,
        now - RESERVATION_TTL_MS,
        request.planLimit,
        request.messageLimit,
        request.outputTokenCap,
      )
      .run()
    const changes = (result as { meta?: { changes?: number } })?.meta?.changes ?? 0
    return changes > 0 ? { reserved: true, id } : { reserved: false }
  } catch (error) {
    if (/no such table/i.test(String(error))) {
      console.error('A reservations tábla hiányzik — a 0006 migráció nem futott le.')
      return { reserved: true, id: null }
    }
    throw error
  }
}

/**
 * A foglalás feloldása — a hívás könyvelése UTÁN.
 *
 * Előbb a tényleges fogyasztás kerül a `usage`-be, csak utána tűnik el a foglalás:
 * a fordított sorrendben egy pillanatig SEMMI nem számítana, és egy közben érkező
 * kérés átcsúszhatna. Így legfeljebb egy pillanatig duplán számít — az a szigorúbb
 * irány.
 *
 * A lejárt, beragadt foglalásokat is itt takarítjuk, mellékesen.
 */
export async function releaseReservations(env: Env, ids: Array<string | null>): Promise<void> {
  const cutoff = Date.now() - RESERVATION_TTL_MS
  try {
    for (const id of ids) {
      if (id) await env.DB.prepare('DELETE FROM reservations WHERE id = ?1').bind(id).run()
    }
    await env.DB.prepare('DELETE FROM reservations WHERE created_at < ?1').bind(cutoff).run()
  } catch (error) {
    if (!/no such table/i.test(String(error))) throw error
  }
}
