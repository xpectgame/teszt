/** A Workerhez kötött környezet. A titkok `wrangler secret put`-tal kerülnek be. */
export interface Env {
  DB: D1Database

  // Titkok
  ANTHROPIC_API_KEY: string
  /** A Play Developer API-hoz tartozó szolgáltatásfiók JSON-ja, egy sorban. */
  PLAY_SERVICE_ACCOUNT_JSON?: string
  /** A Pub/Sub push végpont közös titka — enélkül a webhookot bárki hívhatná. */
  RTDN_SHARED_SECRET?: string

  // Beállítások (wrangler.toml [vars])
  ANDROID_PACKAGE: string
  PREMIUM_PRODUCT_ID: string
  PLAN_MODEL: string
  CHAT_MODEL: string
  PLAN_EFFORT: string
  FREE_OUTPUT_TOKEN_CAP: string
  PREMIUM_OUTPUT_TOKEN_CAP: string
}

export function intVar(value: string | undefined, fallback: number): number {
  const parsed = Number.parseInt(value ?? '', 10)
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback
}
