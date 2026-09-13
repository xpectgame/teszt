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
EST_KT = 'android/core/src/main/kotlin/hu/mealpilot/core/ai/EstimatePrompts.kt'

SOURCES = (
    ('plan', PLAN_KT, 'SYSTEM'),
    ('chat', CHAT_KT, 'SYSTEM'),
    ('estimate', EST_KT, 'SYSTEM'),
    ('plan_en', PLAN_KT, 'SYSTEM_EN'),
    ('chat_en', CHAT_KT, 'SYSTEM_EN'),
    ('estimate_en', EST_KT, 'SYSTEM_EN'),
)
prompts = {key: extract(path, name) for key, path, name in SOURCES}

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
 * FIGYELEM: EZT A FÁJLT GÉP ÍRJA. A forrás a kliens core/ai/*Prompts.kt fájljaiban van;
 * ha ott változik valami, futtasd a backend/tools/gen-prompts.py szkriptet, és írd át a
 * SystemPromptSyncTest hasheit is. A Kotlin-teszt elbukik, ha a kettő szétcsúszik.
 *
%s */

''' % ''.join(' * %-12s sha256 = %s\n' % (key + ':', sha(text)) for key, text in prompts.items()))

def emit(name, text):
    out.write('export const %s = `%s`\n\n' % (name, text.replace('\\', '\\\\').replace('`', '\\`').replace('${', '\\${')))

for key, text in prompts.items():
    emit(key.upper().replace('_EN', '') + '_SYSTEM_PROMPT' + ('_EN' if key.endswith('_en') else ''), text)

out.write('export const PROMPT_HASHES = {\n')
for key, text in prompts.items():
    out.write("  %s: '%s',\n" % (key, sha(text)))
out.write('} as const\n')
out.close()
for key, text in prompts.items():
    print(key, "sha", sha(text), "chars", len(text))
