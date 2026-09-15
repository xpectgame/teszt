/**
 * Csomagok, korlátok, kvóta. Szándékosan függőség nélküli, hogy tesztelhető legyen.
 *
 * FONTOS: ezeknek a számoknak egyezniük kell a kliens `core/billing/Tiers.kt`
 * értékeivel. A kliens csak a felület miatt ismeri őket (mit írjon ki), a döntést
 * mindig ez a fájl hozza — egy módosított app a saját számlálóját átírhatja, ezt nem.
 */

export type Tier = 'FREE' | 'PREMIUM' | 'OWNER'
export type Task = 'PLAN' | 'DAY' | 'CHAT' | 'ESTIMATE'

export interface TierLimits {
  /**
   * -1 = korlátlan.
   *
   * A számok ÉRTELMEZÉSE csomagfüggő, és ezt a `periodFor` dönti el: az ingyenes
   * sávban EGYSZERI keret (a próbaidőszak egésze), a fizetősben havi. Ezért nincs
   * a nevükben "PerMonth" — az az ingyenes sávra hazugság lenne.
   */
  aiPlans: number
  chatMessages: number
  maxPlanDays: number
  canRefineDays: boolean
  /** Kimeneti token plafon a csomag időszakára. A kemény, megkerülhetetlen korlát. */
  outputTokenCap: number
}

export const DEFAULT_LIMITS: Record<Tier, TierLimits> = {
  // Próbaidőszak, nem havi keret. Ez EGYSZER jár egy telepítésnek, és nem töltődik
  // újra — különben minden ingyenes felhasználó örökös, visszatérő költség lenne
  // bevétel nélkül, és a szolgáltatás annál többet veszítene, minél népszerűbb.
  //
  // Három terv elég ahhoz, hogy valaki eldöntse, kell-e neki: egy első terv, egy
  // igazítás utáni, és egy harmadik a következő hétre. Ennél kevesebb nem mutatja
  // meg a terméket, ennél több már ingyen kiszolgálás.
  FREE: {
    aiPlans: 3,
    chatMessages: 20,
    maxPlanDays: 3,
    canRefineDays: false,
    // ≈ 0,60 USD egyszeri költség telepítésenként. Ezt szerzési költségnek tekintjük,
    // nem kiszolgálásnak: egyszer fizetjük ki egy emberért, nem havonta.
    outputTokenCap: 60_000,
  },
  PREMIUM: {
    aiPlans: -1,
    chatMessages: -1,
    maxPlanDays: 30,
    canRefineDays: true,
    // 400k kimeneti token ≈ 4 USD Sonnet 5-ön, nagyjából az előfizetés nettó ára.
    // Valódi használatnál elérhetetlen: egy 30 napos terv ~45k tokent visz, tehát ez
    // nyolc teljes hónapnyi terv EGY hónapban. Aki ezt átlépi, nem étrendet tervez.
    outputTokenCap: 400_000,
  },
  // A fejlesztő saját buildje. Nem a Play-ből jön, nem fizet, és nem is akadhat el
  // kvótán — de tokenplafont ez is kap, hogy egy elszabadult ciklus vagy egy kiszivárgott
  // kulcs se tudjon korlátlanul költeni.
  OWNER: {
    aiPlans: -1,
    chatMessages: -1,
    maxPlanDays: 30,
    canRefineDays: true,
    // ≈ 15 USD/hó. Bőven elég saját használatra és teszteléshez, de egy elszabadult
    // ciklus vagy egy kiszivárgott kulcs itt megáll.
    outputTokenCap: 1_500_000,
  },
}

export interface UsageRow {
  plans: number
  messages: number
  inputTokens: number
  outputTokens: number
}

export const EMPTY_USAGE: UsageRow = { plans: 0, messages: 0, inputTokens: 0, outputTokens: 0 }

/**
 * Az ingyenes sáv elszámolási kulcsa.
 *
 * Nem dátum, hanem állandó: a próbakeret SOHA nem nullázódik. Ez az egy sor a
 * különbség a fenntartható működés és az örökös veszteség között — az azonosító
 * ugyanis nem jogosultság, tehát egy havi keret havonta újratölthető lenne.
 */
export const TRIAL_PERIOD = 'trial'

/**
 * Melyik elszámolási időszak vonatkozik egy csomagra.
 *
 * FREE: egyszeri próbaidőszak. PREMIUM és OWNER: naptári hónap, mert az előfizetés is
 * havonta fordul — amit kifizetett, azt minden hónapban megkapja.
 */
export function periodFor(tier: Tier, at: Date = new Date()): string {
  return tier === 'FREE' ? TRIAL_PERIOD : periodKey(at)
}

/** Naptári hónap kulcsa, UTC szerint. A kliens `BillingPeriod.keyFor` ugyanezt adja. */
export function periodKey(at: Date = new Date()): string {
  const year = at.getUTCFullYear()
  const month = `${at.getUTCMonth() + 1}`.padStart(2, '0')
  return `${year}-${month}`
}

export type DenyCode =
  | 'PLAN_QUOTA'
  | 'MESSAGE_QUOTA'
  | 'TOKEN_CAP'
  | 'PREMIUM_ONLY'
  | 'PLAN_TOO_LONG'

export interface Decision {
  allowed: boolean
  code?: DenyCode
  message?: string
  /** Tájékoztatás a kliensnek a `start` eseményben; a korlátot az elutasítás tartja be. */
  allowedDays?: number
}

const ALLOW: Decision = { allowed: true }

export interface CheckInput {
  tier: Tier
  limits: TierLimits
  usage: UsageRow
  task: Task
  /** A TELJES terv hossza napokban (csak PLAN-nél számít), nem a mostani szakaszé. */
  requestedDays: number
  /** A tervdarabolás miatt csak az első darab számít új tervnek. */
  chunkIndex: number
  /** Javító kör: ugyanazt a szakaszt kéri újra, tehát nem új terv. */
  isRetry: boolean
}

/**
 * Eldönti, kiszolgálható-e a kérés.
 *
 * A tokenplafon minden más előtt jön: ez az egyetlen korlát, amit a hívó semmilyen
 * paraméterrel nem tud megkerülni, mert a tényleges fogyasztásból számoljuk.
 */
export function checkQuota(input: CheckInput): Decision {
  const { tier, limits, usage, task, requestedDays, chunkIndex } = input

  if (usage.outputTokens >= limits.outputTokenCap) {
    return {
      allowed: false,
      code: 'TOKEN_CAP',
      message:
        tier === 'PREMIUM'
          ? 'Ebben a hónapban szokatlanul sok kérés futott le erről a fiókról. Írj nekünk, ha ez tévedés.'
          : 'Elfogyott az ingyenes próbakeret. Az előfizetéssel újra tudsz tervezni.',
    }
  }

  if (task === 'DAY' && !limits.canRefineDays) {
    return {
      allowed: false,
      code: 'PREMIUM_ONLY',
      message: 'Egy nap átírása a teljes csomag része.',
    }
  }

  if (task === 'CHAT') {
    if (limits.chatMessages >= 0 && usage.messages >= limits.chatMessages) {
      return {
        allowed: false,
        code: 'MESSAGE_QUOTA',
        message: `Elfogyott a próbaidőszak ${limits.chatMessages} üzenete. Az előfizetéssel korlátlanul beszélgethetsz.`,
      }
    }
    return ALLOW
  }

  // Az ESTIMATE (egy megevett étel tápértéke) szándékosan NEM fogyaszt sem tervet, sem
  // üzenetet. A naplózás az app napi alapművelete; ha a hónap közepén elfogy,
  // a felhasználó abbahagyja a naplózást, és akkor az egész appnak nincs értelme.
  // A költséget a kimeneti tokenkeret fogja meg: egy becslés néhány száz token, tehát
  // az ingyenes keretből is több száz fér bele.

  if (task === 'PLAN') {
    // A hosszt ELUTASÍTJUK, nem csendben levágjuk: a promptot a kliens írja, tehát a
    // rövidítést nem tudnánk kikényszeríteni — csak azt hinnénk, hogy megtettük.
    if (requestedDays > limits.maxPlanDays) {
      return {
        allowed: false,
        code: 'PLAN_TOO_LONG',
        message: `Az ingyenes csomagban legfeljebb ${limits.maxPlanDays} napos terv kérhető.`,
      }
    }
    const isNewPlan = chunkIndex <= 0 && !input.isRetry
    if (isNewPlan && limits.aiPlans >= 0 && usage.plans >= limits.aiPlans) {
      return {
        allowed: false,
        code: 'PLAN_QUOTA',
        message: `Elhasználtad mind a ${limits.aiPlans} ingyenes tervet. Az előfizetéssel korlátlanul tervezhetsz.`,
      }
    }
    return { allowed: true, allowedDays: Math.max(requestedDays, 1) }
  }

  return ALLOW
}

/**
 * A hívás után mit kell növelni a számlálókon.
 *
 * A javító kör és a folytatólagos szakaszok ugyanahhoz a tervhez tartoznak, ezért nem
 * számítanak újnak — különben egy háromhetes terv három tervet fogyasztana.
 */
export function usageDelta(
  task: Task,
  chunkIndex: number,
  isRetry: boolean,
): { plans: number; messages: number } {
  if (task === 'PLAN' && chunkIndex <= 0 && !isRetry) return { plans: 1, messages: 0 }
  if (task === 'CHAT') return { plans: 0, messages: 1 }
  return { plans: 0, messages: 0 }
}

/**
 * A globális napi mennyezet könyvelési alanya.
 *
 * A felhasználói alanyok mind előtaggal jönnek (`owner:`, `sub:`, `user:`), és mindet
 * egy hexadecimális hash zárja — ez a kulcs tehát nem tud ütközni velük. Így a
 * mennyezet elfér a meglévő `usage` táblában, migráció nélkül.
 */
export const GLOBAL_SUBJECT = 'global:daily'

/** UTC nap, a globális számláló kulcsa. A havi periódustól szándékosan eltér. */
export function dayKey(at: Date = new Date()): string {
  return at.toISOString().slice(0, 10)
}

/**
 * Elfogyott-e a napi közös keret.
 *
 * Miért kell: a telepítési azonosító nem jogosultság, bárki generálhat újat, és minden
 * új azonosítóhoz új ingyenes keret jár. A felhasználónkénti korlátok tehát egy
 * szkriptelt visszaéléssel megkerülhetők — ez a mennyezet az, ami nem.
 *
 * Nem védelem a visszaélés ELLEN, hanem a számla felső korlátja: rossz esetben a
 * szolgáltatás leáll a nap hátralévő részére, nem pedig a kártya ürül ki.
 */
export function globalCeilingReached(outputTokensToday: number, ceiling: number): boolean {
  if (!Number.isFinite(ceiling) || ceiling <= 0) return false
  return outputTokensToday >= ceiling
}
