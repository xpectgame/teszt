import re, hashlib, json, sys, os

def extract(path, obj):
    src = open(path, encoding='utf-8').read()
    m = re.search(r'val SYSTEM: String = """\n(.*?)\n""".trimIndent\(\)', src, re.S)
    if not m:
        sys.exit(f"nem talalom a SYSTEM promptot: {path}")
    text = m.group(1)
    # trimIndent: kozos behuzas levagasa (itt 0, de legyunk pontosak)
    lines = text.split('\n')
    indents = [len(l) - len(l.lstrip()) for l in lines if l.strip()]
    common = min(indents) if indents else 0
    text = '\n'.join(l[common:] if l.strip() else '' for l in lines)
    return text

plan = extract('android/core/src/main/kotlin/hu/mealpilot/core/ai/PlanPrompts.kt', 'PlanPrompts')
chat = extract('android/core/src/main/kotlin/hu/mealpilot/core/ai/ChatPrompts.kt', 'ChatPrompts')

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
 * plan: sha256 = %s
 * chat: sha256 = %s
 */

''' % (sha(plan), sha(chat)))

def emit(name, text):
    out.write('export const %s = `%s`\n\n' % (name, text.replace('\\', '\\\\').replace('`', '\\`').replace('${', '\\${')))

emit('PLAN_SYSTEM_PROMPT', plan)
emit('CHAT_SYSTEM_PROMPT', chat)

out.write('''export const PROMPT_HASHES = {
  plan: '%s',
  chat: '%s',
} as const
''' % (sha(plan), sha(chat)))
out.close()
print("plan sha", sha(plan))
print("chat sha", sha(chat))
print("plan chars", len(plan), "chat chars", len(chat))
