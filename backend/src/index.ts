import { Hono } from 'hono'
import { cors } from 'hono/cors'
import { AnthropicError, costMicros, streamMessage } from './anthropic.js'
import { AuthError, resolveCaller, type Caller } from './auth.js'
import { addUsage, logRequest, readSubscription, readUsage, sha256Hex, writeSubscription } from './db.js'
import type { Env } from './env.js'
import { checkQuota, periodKey, usageDelta, type Task } from './limits.js'
import {
  CHAT_SYSTEM_PROMPT,
  CHAT_SYSTEM_PROMPT_EN,
  ESTIMATE_SYSTEM_PROMPT,
  ESTIMATE_SYSTEM_PROMPT_EN,
  PLAN_SYSTEM_PROMPT,
  PLAN_SYSTEM_PROMPT_EN,
} from './prompts.js'
import { verifySubscription } from './play.js'
import { PAGES } from './pages.js'

/**
 * Feladatonkénti keret. A kliens ezeket nem állíthatja — ez a költség felső korlátja.
 *
 * A rendszerprompt nyelve az EGYETLEN dolog, amit a kliens befolyásolhat, és azt is
 * csak választásként: vagy a magyar, vagy az angol szöveget kapja, harmadik nincs.
 * Ettől a hívás nem alakítható át — mindkét prompt ugyanazt a JSON-t kényszeríti ki.
 */
const TASK_CONFIG: Record<
  Task,
  { maxTokens: number; maxPromptChars: number; system: string; systemEn: string }
> = {
  PLAN: {
    maxTokens: 24_000,
    maxPromptChars: 24_000,
    system: PLAN_SYSTEM_PROMPT,
    systemEn: PLAN_SYSTEM_PROMPT_EN,
  },
  DAY: {
    maxTokens: 6_000,
    maxPromptChars: 16_000,
    system: PLAN_SYSTEM_PROMPT,
    systemEn: PLAN_SYSTEM_PROMPT_EN,
  },
  CHAT: {
    maxTokens: 2_000,
    maxPromptChars: 16_000,
    system: CHAT_SYSTEM_PROMPT,
    systemEn: CHAT_SYSTEM_PROMPT_EN,
  },
  // Hat mező a válasz, semmi több. A szűk keret nem takarékosság: ha a modell
  // hosszabb válaszra készül, elkezd magyarázni, és a JSON elé szöveget ír.
  ESTIMATE: {
    maxTokens: 600,
    maxPromptChars: 2_000,
    system: ESTIMATE_SYSTEM_PROMPT,
    systemEn: ESTIMATE_SYSTEM_PROMPT_EN,
  },
}

/** Egy feltöltésben ennyi fér el — a többit a kliens eldobja, nem gyűjtjük végtelenül. */
const MAX_CRASHES_PER_UPLOAD = 10
const MAX_STACK_CHARS = 20_000
const MAX_EVENT_NAMES = 40
const MAX_EVENT_COUNT = 100_000

/** UTC naptári nap, a számlálók kulcsa. */
function isoDay(at: number): string {
  return new Date(at).toISOString().slice(0, 10)
}

const app = new Hono<{ Bindings: Env }>()

app.use('/v1/*', cors({ origin: '*', allowHeaders: ['authorization', 'content-type', 'x-play-purchase-token', 'x-app-version'] }))

/**
 * Beüzemelési ellenőrzés.
 *
 * Nem csak azt mondja meg, hogy fut-e a szolgáltatás, hanem azt is, hogy a
 * beállítások a helyükön vannak-e. Ennek oka gyakorlati: a titkokat a Cloudflare
 * felületén kézzel kell elnevezni, és egy elgépelt NÉV pontosan úgy viselkedik,
 * mintha a titok nem is létezne — de csak az első valódi AI-hívásnál, egy semmitmondó
 * hibaüzenet formájában. Itt öt másodperc alatt kiderül.
 *
 * Csak logikai értékeket ad vissza, a titkok tartalmát soha. Az adatbázist egy
 * olcsó lekérdezéssel piszkálja meg, mert a binding megléte még nem jelenti azt,
 * hogy a migrációk le is futottak.
 */
app.get('/healthz', async (c) => {
  const env = c.env
  let database: 'ok' | 'nincs migrálva' | 'elérhetetlen' = 'elérhetetlen'
  try {
    await env.DB.prepare('SELECT 1 FROM events LIMIT 1').all()
    database = 'ok'
  } catch (error) {
    // A hiányzó tábla más eset, mint az elérhetetlen adatbázis: az elsőt a migráció
    // javítja, a másodikat a binding.
    database = /no such table/i.test(String(error)) ? 'nincs migrálva' : 'elérhetetlen'
  }

  const ready = database === 'ok' && Boolean(env.ANTHROPIC_API_KEY)
  return c.json({
    ok: ready,
    database,
    // Nélküle minden AI-hívás hibát ad.
    anthropicKey: Boolean(env.ANTHROPIC_API_KEY),
    // Opcionális: a fejlesztői build korlátlan kvótájához kell.
    ownerKey: Boolean(env.OWNER_KEY),
    // Opcionális: az előfizetés ellenőrzéséhez kell, a Play bekötésekor.
    playServiceAccount: Boolean(env.PLAY_SERVICE_ACCOUNT_JSON),
    rtdnSecret: Boolean(env.RTDN_SHARED_SECRET),
  }, ready ? 200 : 503)
})

/**
 * A nyilvános oldalak (adatkezelés, feltételek, támogatás, adattörlés).
 *
 * Azért itt, és nem külön tárhelyen: a Play kötelezően kér egy nyilvánosan elérhető
 * adatvédelmi címet, és ellenőrzi, hogy betölt-e. Ha ezt a szolgáltatás szolgálja ki,
 * nem kell hozzá se domain, se külön hoszting, és a szöveg ugyanazzal a deployjal
 * frissül, mint a kód — nem tud szétcsúszni a kettő.
 *
 * A `/v1/*` és a `/healthz` előbb van bejegyezve, tehát azokat ez nem takarja el.
 */
app.get('/*', (c) => {
  const requested = decodeURIComponent(new URL(c.req.url).pathname).replace(/^\/+/, '')

  // Kiterjesztés nélkül is működjön: /privacy ugyanaz, mint /privacy.html.
  const candidates = requested === ''
    ? ['index.html']
    : [requested, `${requested}.html`, `${requested}/index.html`]

  for (const candidate of candidates) {
    const page = PAGES[candidate]
    if (!page) continue
    return c.body(page.body, 200, {
      'content-type': page.contentType,
      // Rövid gyorsítótár: a jogi szöveg ritkán változik, de ha módosul, ne ragadjon
      // kint egy elavult verzió napokra.
      'cache-control': 'public, max-age=600',
      'x-content-type-options': 'nosniff',
    })
  }

  return c.text('Nincs ilyen oldal.', 404)
})

function entitlementPayload(caller: Caller, usage: { plans: number; messages: number; outputTokens: number }) {
  const limits = caller.limits
  const unlimited = (value: number) => value < 0
  return {
    tier: caller.tier,
    subscription_state: caller.subscriptionState,
    expires_at: caller.expiresAt,
    period: periodKey(),
    limits: {
      ai_plans_per_month: limits.aiPlansPerMonth,
      chat_messages_per_month: limits.chatMessagesPerMonth,
      max_plan_days: limits.maxPlanDays,
      can_refine_days: limits.canRefineDays,
    },
    usage: {
      plans: usage.plans,
      messages: usage.messages,
    },
    remaining: {
      plans: unlimited(limits.aiPlansPerMonth) ? -1 : Math.max(0, limits.aiPlansPerMonth - usage.plans),
      messages: unlimited(limits.chatMessagesPerMonth)
        ? -1
        : Math.max(0, limits.chatMessagesPerMonth - usage.messages),
      output_tokens: Math.max(0, limits.outputTokenCap - usage.outputTokens),
    },
  }
}

/**
 * Állapotlekérdezés. A kliens ebből tudja, mit írjon ki a felületen — de a döntést
 * továbbra is a szerver hozza meg minden egyes híváskor.
 */
app.post('/v1/session', async (c) => {
  const caller = await resolveCaller(c.env, c.req.raw)
  const usage = await readUsage(c.env, caller.subject, periodKey())
  return c.json(entitlementPayload(caller, usage))
})

/**
 * A tervezés, nap-átírás és beszélgetés egyetlen végpontja.
 *
 * A válasz NDJSON: soronként egy JSON objektum. Így a kliens az első másodpercekben
 * már mutathat haladást, és egy hosszú terv sem fut HTTP időkorlátba.
 */
app.post('/v1/generate', async (c) => {
  const caller = await resolveCaller(c.env, c.req.raw)

  const body = (await c.req.json().catch(() => null)) as {
    task?: string
    prompt?: string
    days?: number
    chunk_index?: number
    is_retry?: boolean
    language?: string
  } | null

  if (!body) return c.json({ error: 'BAD_REQUEST', message: 'Hibás kérés.' }, 400)

  const task = String(body.task ?? '').toUpperCase() as Task
  const config = TASK_CONFIG[task]
  if (!config) return c.json({ error: 'BAD_REQUEST', message: 'Ismeretlen feladat.' }, 400)

  const prompt = typeof body.prompt === 'string' ? body.prompt : ''
  if (prompt.trim().length === 0) {
    return c.json({ error: 'BAD_REQUEST', message: 'Üres kérés.' }, 400)
  }
  if (prompt.length > config.maxPromptChars) {
    return c.json({ error: 'PROMPT_TOO_LONG', message: 'A kérés túl hosszú.' }, 413)
  }

  const period = periodKey()
  const usage = await readUsage(c.env, caller.subject, period)
  const requestedDays = Number.isFinite(body.days) ? Number(body.days) : 1
  const chunkIndex = Number.isFinite(body.chunk_index) ? Number(body.chunk_index) : 0
  const isRetry = body.is_retry === true
  // Ismeretlen nyelvnél magyar: az app alapértelmezése is az, és egy rossz tipp itt
  // az egész tervet a felhasználó számára használhatatlan nyelven adná vissza.
  const system = String(body.language ?? '').toLowerCase() === 'en' ? config.systemEn : config.system

  const decision = checkQuota({
    tier: caller.tier,
    limits: caller.limits,
    usage,
    task,
    requestedDays,
    chunkIndex,
    isRetry,
  })

  if (!decision.allowed) {
    return c.json(
      { error: decision.code, message: decision.message, tier: caller.tier, upgrade: caller.tier === 'FREE' },
      402,
    )
  }

  const model = task === 'CHAT' ? c.env.CHAT_MODEL : c.env.PLAN_MODEL
  // A Haiku nem fogadja el az effort paramétert.
  const effort = model.includes('haiku') ? null : c.env.PLAN_EFFORT
  const requestId = crypto.randomUUID()

  const encoder = new TextEncoder()
  // A könyvelést a válasz lezárása után futtatjuk, de a futtatókörnyezetet még itt
  // kérjük el: a stream belsejéből már nem biztos, hogy elérhető.
  const execCtx = (() => {
    try {
      return c.executionCtx
    } catch {
      return null
    }
  })()
  const defer = (work: Promise<unknown>) => {
    if (execCtx) execCtx.waitUntil(work)
    else void work
  }

  const stream = new ReadableStream<Uint8Array>({
    async start(controller) {
      const send = (value: unknown) => controller.enqueue(encoder.encode(`${JSON.stringify(value)}\n`))

      send({ type: 'start', request_id: requestId, allowed_days: decision.allowedDays ?? requestedDays })

      let result: Awaited<ReturnType<typeof streamMessage>> | null = null
      let failure: string | null = null

      try {
        result = await streamMessage(
          {
            apiKey: c.env.ANTHROPIC_API_KEY,
            model,
            system,
            user: prompt,
            maxTokens: config.maxTokens,
            effort,
          },
          (text) => send({ type: 'delta', text }),
        )
        send({
          type: 'done',
          usage: { input_tokens: result.inputTokens, output_tokens: result.outputTokens },
        })
      } catch (error) {
        failure = error instanceof Error ? error.message : 'Ismeretlen hiba.'
        const status = error instanceof AnthropicError ? error.status : 500
        send({
          type: 'error',
          code: status === 429 ? 'RATE_LIMIT' : 'UPSTREAM',
          message:
            status === 429
              ? 'Most sok kérés fut egyszerre. Várj egy percet, aztán próbáld újra.'
              : 'A tervező szolgáltatás hibát adott. Próbáld újra kicsit később.',
        })
      } finally {
        controller.close()
      }

      // A könyvelés a válasz lezárása UTÁN fut, hogy ne lassítsa a felhasználót.
      const delta = usageDelta(task, chunkIndex, isRetry)
      const tokens = result ?? { inputTokens: 0, outputTokens: 0, cacheReadTokens: 0, cacheWriteTokens: 0 }
      const cost = result ? costMicros(model, result) : 0
      const bookkeeping = Promise.all([
        addUsage(c.env, caller.subject, period, {
          // Sikertelen hívás nem fogyaszt darabszám-kvótát, tokent viszont igen:
          // azt tényleg elhasználta.
          plans: result ? delta.plans : 0,
          messages: result ? delta.messages : 0,
          inputTokens: tokens.inputTokens,
          outputTokens: tokens.outputTokens,
          costMicros: cost,
        }),
        logRequest(c.env, {
          id: requestId,
          userId: caller.userId,
          subject: caller.subject,
          task,
          model,
          inputTokens: tokens.inputTokens,
          outputTokens: tokens.outputTokens,
          cacheReadTokens: tokens.cacheReadTokens,
          costMicros: cost,
          ok: result !== null,
          error: failure,
        }),
      ])
      defer(bookkeeping)
    },
  })

  return new Response(stream, {
    headers: {
      'content-type': 'application/x-ndjson; charset=utf-8',
      'cache-control': 'no-store',
      'x-request-id': requestId,
    },
  })
})

/**
 * „Jelentsd ezt a tervet". A Play a generatív AI funkcióknál elvárja, hogy legyen
 * visszajelzési út a problémás tartalomra — és gyakorlatilag ez az egyetlen jelzés
 * arról, ha a tervező hibázik.
 */
app.post('/v1/report', async (c) => {
  const caller = await resolveCaller(c.env, c.req.raw)
  const body = (await c.req.json().catch(() => null)) as {
    kind?: string
    reason?: string
    detail?: string
    payload?: string
  } | null

  if (!body?.reason) return c.json({ error: 'BAD_REQUEST', message: 'Hiányzó ok.' }, 400)

  const kind = ['PLAN', 'MEAL', 'CHAT'].includes(String(body.kind).toUpperCase())
    ? String(body.kind).toUpperCase()
    : 'PLAN'

  await c.env.DB.prepare(
    `INSERT INTO reports (id, user_id, kind, reason, detail, payload, app_version, created_at)
     VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)`,
  )
    .bind(
      crypto.randomUUID(),
      caller.userId,
      kind,
      String(body.reason).slice(0, 200),
      body.detail ? String(body.detail).slice(0, 2000) : null,
      body.payload ? String(body.payload).slice(0, 20_000) : null,
      caller.appVersion,
      Date.now(),
    )
    .run()

  return c.json({ ok: true })
})

/**
 * Összeomlások és névtelen napi számlálók fogadása.
 *
 * Nem külső szolgáltató: a saját backend gyűjti, mert már itt van, és így nem kerül
 * harmadik félhez semmi. Cserébe nincs szimbólumfeloldás és riasztás — ha az app
 * tényleg sok emberhez jut el, egy erre való eszköz (Crashlytics, Sentry) többet ad.
 */
app.post('/v1/telemetry', async (c) => {
  const caller = await resolveCaller(c.env, c.req.raw)
  const body = (await c.req.json().catch(() => null)) as {
    day?: string
    android_api?: number
    device?: string
    crashes?: Array<{
      exception?: string
      message?: string
      stack?: string
      fingerprint?: string
      happened_at?: number
    }>
    events?: Record<string, number>
  } | null

  if (!body) return c.json({ error: 'BAD_REQUEST', message: 'Hibás kérés.' }, 400)

  const now = Date.now()
  const appVersion = caller.appVersion ?? ''
  const writes: Array<D1PreparedStatement> = []

  for (const crash of (body.crashes ?? []).slice(0, MAX_CRASHES_PER_UPLOAD)) {
    if (!crash.stack || !crash.exception) continue
    writes.push(
      c.env.DB.prepare(
        `INSERT INTO crashes
           (id, user_id, app_version, android_api, device, exception, message, stack, fingerprint, happened_at, received_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11)`,
      ).bind(
        crypto.randomUUID(),
        caller.userId,
        appVersion,
        body.android_api ?? null,
        body.device ? String(body.device).slice(0, 120) : null,
        String(crash.exception).slice(0, 300),
        crash.message ? String(crash.message).slice(0, 1000) : null,
        String(crash.stack).slice(0, MAX_STACK_CHARS),
        String(crash.fingerprint ?? crash.exception).slice(0, 64),
        crash.happened_at ?? now,
        now,
      ),
    )
  }

  const day = /^\d{4}-\d{2}-\d{2}$/.test(String(body.day)) ? String(body.day) : isoDay(now)
  for (const [name, rawCount] of Object.entries(body.events ?? {}).slice(0, MAX_EVENT_NAMES)) {
    if (!/^[a-z0-9_]{1,40}$/.test(name)) continue
    const count = Math.min(Math.max(Math.trunc(Number(rawCount) || 0), 0), MAX_EVENT_COUNT)
    if (count === 0) continue
    writes.push(
      c.env.DB.prepare(
        `INSERT INTO events (day, name, app_version, count, users, updated_at)
         VALUES (?1, ?2, ?3, ?4, 1, ?5)
         ON CONFLICT(day, name, app_version) DO UPDATE SET
           count = events.count + ?4,
           users = events.users + 1,
           updated_at = ?5`,
      ).bind(day, name, appVersion, count, now),
    )
  }

  if (writes.length > 0) await c.env.DB.batch(writes)
  return c.json({ ok: true, stored: writes.length })
})

/**
 * Play valós idejű fejlesztői értesítések (Pub/Sub push).
 *
 * Enélkül a lemondás, visszatérítés és felfüggesztés csak a következő ellenőrzésnél
 * derülne ki. A végpontot közös titok védi: a Pub/Sub feliratkozás URL-jébe
 * `?secret=...` formában kerül.
 */
app.post('/v1/play/rtdn', async (c) => {
  const expected = c.env.RTDN_SHARED_SECRET
  if (!expected || c.req.query('secret') !== expected) {
    return c.json({ error: 'FORBIDDEN' }, 403)
  }

  const envelope = (await c.req.json().catch(() => null)) as { message?: { data?: string } } | null
  const encoded = envelope?.message?.data
  if (!encoded) return c.json({ ok: true })

  let notification: {
    subscriptionNotification?: { purchaseToken?: string }
    voidedPurchaseNotification?: { purchaseToken?: string }
  }
  try {
    notification = JSON.parse(atob(encoded))
  } catch {
    return c.json({ ok: true })
  }

  const purchaseToken =
    notification.subscriptionNotification?.purchaseToken ??
    notification.voidedPurchaseNotification?.purchaseToken
  if (!purchaseToken) return c.json({ ok: true })

  const purchaseHash = await sha256Hex(purchaseToken)

  if (notification.voidedPurchaseNotification) {
    await c.env.DB.prepare(
      'UPDATE subscriptions SET state = ?2, expires_at = ?3, verified_at = ?3 WHERE purchase_hash = ?1',
    )
      .bind(purchaseHash, 'REVOKED', Date.now())
      .run()
    return c.json({ ok: true })
  }

  // Az értesítés csak jelzés: az igazságot mindig a Play API-tól kérdezzük meg.
  try {
    const verified = await verifySubscription(c.env, purchaseToken)
    if (verified) {
      const existing = await readSubscription(c.env, purchaseHash)
      await writeSubscription(c.env, {
        purchase_hash: purchaseHash,
        state: verified.state,
        expires_at: verified.expiresAt,
        first_user_id: existing?.first_user_id ?? null,
        linkedFrom: verified.linkedPurchaseToken ? await sha256Hex(verified.linkedPurchaseToken) : null,
      })
    }
  } catch {
    // A Pub/Sub újrapróbálkozik; nem hibázunk el egy értesítést egy időzítés miatt.
    return c.json({ ok: false }, 500)
  }

  return c.json({ ok: true })
})

app.onError((error, c) => {
  if (error instanceof AuthError) {
    return c.json({ error: 'UNAUTHORIZED', message: error.message }, error.status as 401)
  }
  console.error('Kezeletlen hiba:', error)
  return c.json({ error: 'INTERNAL', message: 'Váratlan hiba a szolgáltatásban.' }, 500)
})

export default app
