# TODO

Aktuálně žádné otevřené úkoly.

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
