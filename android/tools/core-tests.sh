#!/usr/bin/env bash
#
# A :core modul lefordítása és tesztjeinek futtatása Gradle és Android SDK NÉLKÜL.
#
# Miért van erre szükség: van olyan fejlesztői környezet (például egy felhőben futó
# munkamenet), ahol nincs Android SDK, tehát a `./gradlew test` el sem indul. Ilyenkor
# minden Kotlin változást csak a CI ellenőriz — egy elgépelés is egy teljes CI-kört
# jelent, és a hiba percekkel később derül ki.
#
# A :core tiszta Kotlin/JVM modul: nincs benne Android-függőség. A promptok, a
# modellválasz-értelmező, a validátorok, a tápérték- és energiaszámítás mind itt van,
# vagyis a logika nagy része enélkül is ellenőrizhető.
#
# KORLÁT: az :app modul (Room, Compose, WorkManager) így NEM fordul le — azt továbbra
# is a CI ellenőrzi. Ez a szkript nem helyettesíti a `./gradlew test` futást, csak
# gyorsabb visszajelzést ad arról, ami enélkül is mérhető.
#
# Előfeltétel: a függőségek már ott vannak a Gradle gyorsítótárában (egy korábbi
# Gradle futásból vagy a CI-ből). Enélkül a szkript megmondja, mi hiányzik.
#
# Használat a repó gyökeréből:  android/tools/core-tests.sh

set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2"
WORK="${TMPDIR:-/tmp}/mealpilot-core"

# A legfrissebb illeszkedő jar a gyorsítótárból.
jar() {
  local found
  found=$(find "$CACHE" -name "$1" 2>/dev/null | sort -V | tail -1)
  if [ -z "$found" ]; then
    echo "HIÁNYZIK a Gradle gyorsítótárból: $1" >&2
    echo "Futtass egyszer egy Gradle buildet (vagy másold be a gyorsítótárat), aztán próbáld újra." >&2
    exit 1
  fi
  printf '%s' "$found"
}

STDLIB=$(jar 'kotlin-stdlib-2*.jar')
COMPILER=$(jar 'kotlin-compiler-embeddable-2*.jar')
SER_PLUGIN=$(jar 'kotlin-serialization-compiler-plugin-embeddable-2*.jar')
ANNOTATIONS=$(jar 'annotations-13.0.jar')
TROVE=$(jar 'trove4j-*.jar')
COROUTINES=$(jar 'kotlinx-coroutines-core-jvm-*.jar')
COROUTINES_TEST=$(jar 'kotlinx-coroutines-test-jvm-*.jar')
SER_CORE=$(jar 'kotlinx-serialization-core-jvm-*.jar')
SER_JSON=$(jar 'kotlinx-serialization-json-jvm-*.jar')
JUNIT=$(jar 'junit-4*.jar')
HAMCREST=$(jar 'hamcrest-core-*.jar')

# Amire magának a fordítónak van szüksége a FUTÁSHOZ (nem amit fordít).
COMPILER_CP="$COMPILER:$STDLIB:$COROUTINES:$ANNOTATIONS:$TROVE"
# Amit a lefordított kód lát.
LIBS="$STDLIB:$SER_CORE:$SER_JSON:$ANNOTATIONS:$COROUTINES:$COROUTINES_TEST"
TEST_LIBS="$LIBS:$JUNIT:$HAMCREST"

MAIN_OUT="$WORK/main"
TEST_OUT="$WORK/test"
rm -rf "$WORK"; mkdir -p "$MAIN_OUT" "$TEST_OUT"

kotlinc() {
  local out=$1 cp=$2; shift 2
  java -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -no-stdlib -Xplugin="$SER_PLUGIN" -classpath "$cp" -d "$out" "$@" 2>&1 |
    grep -viE '^picked up|^warning:|^info:'
}

echo "A :core fordítása…"
mapfile -t MAIN_SRC < <(find core/src/main/kotlin -name '*.kt')
kotlinc "$MAIN_OUT" "$LIBS" "${MAIN_SRC[@]}"
if [ -z "$(find "$MAIN_OUT" -name '*.class' -print -quit)" ]; then
  echo "A :core fordítása NEM sikerült." >&2
  exit 1
fi

echo "A tesztek fordítása…"
mapfile -t TEST_SRC < <(find core/src/test/kotlin -name '*.kt')
kotlinc "$TEST_OUT" "$TEST_LIBS:$MAIN_OUT" "${TEST_SRC[@]}"

CLASSES=$(cd "$TEST_OUT" && find . -name '*Test.class' | sed 's|^\./||; s|\.class$||; s|/|.|g' | grep -v '\$')
if [ -z "$CLASSES" ]; then
  echo "A tesztek fordítása NEM sikerült." >&2
  exit 1
fi

echo "Futtatás…"
# shellcheck disable=SC2086
java -cp "$TEST_LIBS:$MAIN_OUT:$TEST_OUT:core/src/test/resources" \
  org.junit.runner.JUnitCore $CLASSES 2>&1 | grep -viE '^picked up'
