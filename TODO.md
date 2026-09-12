# TODO

## 1. Zálohování a obnova dat appky

- **Požadavek**: umožnit zazálohovat a později obnovit všechno, co si
  appka pamatuje - nastavení (hlas, rychlost, výška, hlasitost, motiv,
  automatické pokračování, historie zap/vyp), historii čtení, uložené
  texty (knihovnu) i sledované profily.
- **Kde ta data teď jsou**: čtyři oddělené SharedPreferences úložiště -
  `AppSettings` (nastavení), `ReadingHistoryStore` (historie, limit 300
  položek), `TextLibraryStore` (knihovna + rozepsaný koncept),
  `TrackedProfilesStore` (sledované profily).
- **Návrh řešení**: export do JEDNOHO souboru .json, který se uloží přes
  systémový dialog "Uložit soubor" (uživatel si vybere kam - do telefonu,
  na Disk Google apod.). Obnova = vybrat soubor přes systémový dialog a
  načíst zpátky. Přidat do menu ⋮ novou položku (např. "Záloha dat")
  se dvěma tlačítky - Zálohovat / Obnovit.
- **Na co myslet při návrhu**:
  - Do zálohy přidat číslo verze formátu, ať obnova z hodně staré zálohy
    v budoucnu pozná, že data mají jiný tvar, a neshodí appku.
  - Při obnově se zeptat, jestli data **nahradit** nebo **sloučit** se
    stávajícími (sloučení je bezpečnější výchozí volba).
  - Ošetřit vadný/cizí soubor - neshodit appku, jen slušně oznámit chybu.
  - Respektovat limit 300 položek historie i po obnově.
- **Vedlejší přínos**: tohle zároveň vyřeší problém s přechodem na nový
  podpisový klíč (odinstalace = ztráta dat) i budoucí přenos na nový
  telefon.

---

## Poznámka: stažitelné Piper hlasy - odstraněno, ale zachováno pro budoucnost

Appka měla ve verzích 2.47-2.51 kompletně funkční stahování a použití
offline neurálního hlasu (Piper "Jirka", přes sherpa-onnx engine) - šlo
ho stáhnout, vybrat a skutečně jím číst (zvýraznění po větách, pauza po
větách, bez podpory výšky hlasu). Odstraněno ve verzi 2.52, protože
kvalita hlasu nebyla dost dobrá ("zní jak retardovaný").

**Kompletní funkční kód zůstává v Gitu pod značkou (tagem)
`piper-voice-feature-2026-08`** - pro návrat stačí:
```
git checkout piper-voice-feature-2026-08
```
a podívat se, co všechno to obnáší (Gradle závislosti na sherpa-onnx AAR
+ commons-compress, tři nové Kotlin třídy DownloadableVoices/
PiperVoiceStore/PiperVoiceEngine, rozšíření ReadingService o Piper
routing, UI v Nastavení hlasu).

Než se k tomu vracet, hledat lepší český open-source hlas - při
posledním hledání (srpen 2026) žádný kvalitnější volně dostupný nebyl
k nalezení. Zvážit i placené systémové moduly (Vocalizer TTS) jako
alternativu.

---

Až budeme příště na appce pracovat, stačí napsat, co chceš změnit nebo
přidat.
