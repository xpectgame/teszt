import re, hashlib, json, sys, os

def extract(path, name='SYSTEM'):
    src = open(path, encoding='utf-8').read()
    m = re.search(r'val %s: String = """\n(.*?)\n""".trimIndent\(\)' % name, src, re.S)
    if not m:
        sys.exit(f"nem talalom a {name} promptot: {path}")
    text = m.group(1)
    # trimIndent: kozos behuzas levagasa (itt 0, de legyunk pontosak)
    lines = text.split('\n')
    indents = [len(l) - len(l.lstrip()) for l in lines if l.strip()]
    common = min(indents) if indents else 0
    text = '\n'.join(l[common:] if l.strip() else '' for l in lines)
    return text

PLAN_KT = 'android/core/src/main/kotlin/hu/mealpilot/core/ai/PlanPrompts.kt'
CHAT_KT = 'android/core/src/main/kotlin/hu/mealpilot/core/ai/ChatPrompts.kt'

plan = extract(PLAN_KT)
chat = extract(CHAT_KT)
plan_en = extract(PLAN_KT, 'SYSTEM_EN')
chat_en = extract(CHAT_KT, 'SYSTEM_EN')

def sha(s): return hashlib.sha256(s.encode('utf-8')).hexdigest()

out = open('backend/src/prompts.ts', 'w', encoding='utf-8')
out.write('''/**
 * A rendszerpromptok a SZERVEREN élnek, nem a kliensen.
 *
 * Ez nem kényelmi kérdés: a kliens csak az adatokat tartalmazó felhasználói üzenetet
 * küldi be, a modell viselkedését ez a fájl szabja meg. Így egy visszafejtett vagy
 * módosított app sem tudja a hívást általános célú asszisztenssé alakítani a te
 * Anthropic-kulcsodon — a válasz mindig étrend-JSON lesz.
 *
 * FIGYELEM: ez a két szöveg a kliens `core/ai/PlanPrompts.kt` és `core/ai/ChatPrompts.kt`
 * fájljából származik. Ha ott változik, ITT is frissítsd. A `SystemPromptSyncTest`
 * Kotlin-teszt elbukik, ha a kettő szétcsúszik — az alábbi hasheket is vele együtt írd át.
 *
 * plan:    sha256 = %s
 * chat:    sha256 = %s
 * plan_en: sha256 = %s
 * chat_en: sha256 = %s
 */

''' % (sha(plan), sha(chat), sha(plan_en), sha(chat_en)))

def emit(name, text):
    out.write('export const %s = `%s`\n\n' % (name, text.replace('\\', '\\\\').replace('`', '\\`').replace('${', '\\${')))

emit('PLAN_SYSTEM_PROMPT', plan)
emit('CHAT_SYSTEM_PROMPT', chat)
emit('PLAN_SYSTEM_PROMPT_EN', plan_en)
emit('CHAT_SYSTEM_PROMPT_EN', chat_en)

out.write('''export const PROMPT_HASHES = {
  plan: '%s',
  chat: '%s',
  plan_en: '%s',
  chat_en: '%s',
} as const
''' % (sha(plan), sha(chat), sha(plan_en), sha(chat_en)))
out.close()
for name, text in (('plan', plan), ('chat', chat), ('plan_en', plan_en), ('chat_en', chat_en)):
    print(name, "sha", sha(text), "chars", len(text))
