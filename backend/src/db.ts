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

export async function touchUser(env: Env, userId: string, appVersion: string | null): Promise<UserRow> {
  const now = Date.now()
  await env.DB.prepare(
    `INSERT INTO users (id, created_at, last_seen_at, app_version)
     VALUES (?1, ?2, ?2, ?3)
     ON CONFLICT(id) DO UPDATE SET last_seen_at = ?2, app_version = COALESCE(?3, users.app_version)`,
  )
    .bind(userId, now, appVersion)
    .run()

  const row = await env.DB.prepare('SELECT id, purchase_hash FROM users WHERE id = ?1')
    .bind(userId)
    .first<UserRow>()
  return row ?? { id: userId, purchase_hash: null }
}

export async function bindPurchase(env: Env, userId: string, purchaseHash: string | null): Promise<void> {
  await env.DB.prepare('UPDATE users SET purchase_hash = ?2 WHERE id = ?1').bind(userId, purchaseHash).run()
}

export interface SubscriptionRow {
  purchase_hash: string
  purchase_token: string
  state: string
  expires_at: number | null
  verified_at: number
  first_user_id: string | null
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
    `INSERT INTO subscriptions (purchase_hash, purchase_token, state, expires_at, verified_at, first_user_id, linked_from)
     VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)
     ON CONFLICT(purchase_hash) DO UPDATE SET
       state = ?3, expires_at = ?4, verified_at = ?5,
       first_user_id = COALESCE(subscriptions.first_user_id, ?6),
       linked_from = COALESCE(?7, subscriptions.linked_from)`,
  )
    .bind(
      row.purchase_hash,
      row.purchase_token,
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
