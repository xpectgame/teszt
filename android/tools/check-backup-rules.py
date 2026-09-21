#!/usr/bin/env python3
"""A SecureKeyStore MINDEN tárolója ki van-e zárva a mentésből.

A `SecureKeyStore` két SharedPreferences fájlt használhat: a titkosítottat, és egy
titkosítatlan tartalékot arra az esetre, ha az Android Keystore nem nyílik meg. A
mentésből mindkettőt ki kell zárni, három helyen: a `backup_rules.xml` (Android 11
és korábbi), valamint a `data_extraction_rules.xml` `cloud-backup` és
`device-transfer` szakasza (Android 12+).

Ez sokáig csak a titkosított fájlra állt fent. A tartalék — amelyik az API kulcsot
OLVASHATÓAN tartja, és amelyik a telepítési azonosítót is viszi — kimaradt: vagyis
pont abban az esetben ment volna felhőbe a kulcs, amikor nincs rajta titkosítás.

A szkript nem a szándékot olvassa, hanem a kódban deklarált fájlneveket: ha valaki
új tárolót vesz fel a SecureKeyStore-ba, ez azonnal kéri hozzá a kizárást is.
"""
import re
import sys
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[2]
STORE_KT = ROOT / 'android/app/src/main/java/hu/mealpilot/app/data/prefs/SecureKeyStore.kt'
BACKUP_XML = ROOT / 'android/app/src/main/res/xml/backup_rules.xml'
EXTRACTION_XML = ROOT / 'android/app/src/main/res/xml/data_extraction_rules.xml'

FILE_CONST = re.compile(r'const val (\w*FILE_NAME)\s*=\s*"([^"]+)"')


def excluded_shared_prefs(root):
    """A `sharedpref` kizárások útjai az adott XML csomópont alatt."""
    return {
        node.get('path')
        for node in root.iter('exclude')
        if node.get('domain') == 'sharedpref' and node.get('path')
    }


def main() -> int:
    kotlin = STORE_KT.read_text(encoding='utf-8')
    stores = dict(FILE_CONST.findall(kotlin))
    if not stores:
        print(f'Nem találtam egyetlen *FILE_NAME konstanst sem a {STORE_KT.name}-ben — '
              'a szkript nem ismerte fel az alakját, tehát nem is bizonyít semmit.',
              file=sys.stderr)
        return 1

    expected = {f'{name}.xml' for name in stores.values()}

    backup = ElementTree.parse(BACKUP_XML).getroot()
    extraction = ElementTree.parse(EXTRACTION_XML).getroot()

    sections = {
        'backup_rules.xml': excluded_shared_prefs(backup),
    }
    for tag in ('cloud-backup', 'device-transfer'):
        node = extraction.find(tag)
        if node is None:
            print(f'A data_extraction_rules.xml-ből hiányzik a <{tag}> szakasz.', file=sys.stderr)
            return 1
        sections[f'data_extraction_rules.xml / {tag}'] = excluded_shared_prefs(node)

    problems = []
    for where, found in sections.items():
        for missing in sorted(expected - found):
            problems.append(f'  {where}: nincs kizárva a(z) {missing}')

    if problems:
        print('A SecureKeyStore tárolói nincsenek mind kizárva a mentésből:', file=sys.stderr)
        print('\n'.join(problems), file=sys.stderr)
        print('\nA titkosítatlan tartalék tároló az API kulcsot OLVASHATÓAN tartja, '
              'a telepítési azonosítóból pedig egy visszaállítás után két eszköz '
              'osztozna egyetlen havi kereten.', file=sys.stderr)
        return 1

    names = ', '.join(sorted(expected))
    print(f'Rendben: mind a(z) {len(expected)} SecureKeyStore tároló ({names}) '
          f'ki van zárva mind a(z) {len(sections)} mentési szakaszból.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
