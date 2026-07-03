# Chytrá čtečka (SmartReader)

Android aplikace, která nahlas čte český text – "inteligentně", tedy
přeskakuje odkazy, čísla bankovních účtů/IBAN, dlouhá čísla (telefony apod.),
podtržítka a emoji, aby to znělo jako čtení člověka, ne robota předčítajícího znak po znaku.

## Funkce

- Vlastní textové pole – lze psát, vkládat i upravovat text před čtením
- Tlačítko **Vložit ze schránky**
- Tlačítko **Vymazat**
- **Přehrát / Pauza / Stop**
- **Posuvník rychlosti** čtení (0.5x–3.0x), mění se za chodu
- Zvýraznění právě čteného úseku textu
- **Sdílení z jiných aplikací**: v libovolné appce (prohlížeč, Facebook, IG...)
  klikni na *Sdílet* → *Chytrá čtečka* a text/odkaz se rovnou načte
- **Označení textu**: v jakékoli appce označ text prstem → v nabídce
  "Kopírovat / Sdílet..." se objeví i *Chytrá čtečka* → přečte přesně to, co jsi označil
- Pokud sdílíš jen odkaz (ne text), appka se pokusí sama stáhnout a
  vytáhnout hlavní text stránky (funguje spolehlivě u novinových
  článků a blogů)

## Jak spustit

1. Nainstaluj [Android Studio](https://developer.android.com/studio) (nejnovější verze)
2. `File > Open` → vyber tuto složku `SmartReader`
3. Nech Android Studio dokončit Gradle sync (může nabídnout doplnění
   Gradle Wrapperu – klikni „OK"/„Sync Now")
4. Připoj telefon (nebo spusť emulátor) a klikni **Run ▶**
5. V telefonu musí být nainstalovaný **český hlas** pro Převod textu na
   řeč – pokud appka po spuštění hlásí, že čeština chybí, jdi do
   `Nastavení > Řeč > Převod textu na řeč (TTS)` a stáhni si český hlas
   (typicky přes Google TTS engine)

## Důležité omezení, o kterém bys měl vědět

Facebook a Instagram většinou při sdílení pošlou **jen odkaz**, ne
samotný text příspěvku – a obsah těchto stránek se navíc dotahuje přes
JavaScript, který appka (podobně jako žádný jednoduchý "reader mode")
neumí spustit. U těchto dvou služeb proto stahování z odkazu často
selže.

**Řešení**: v appce Facebook/Instagram text příspěvku prstem označ
(podrž prst a přetáhni) a z nabídky vyber *Chytrá čtečka* – appka
přečte přesně to, co vidíš na obrazovce, spolehlivě. U novinových
článků a většiny webů naopak funguje sdílení odkazu bez problémů.

## Možná budoucí vylepšení

- Čtení na pozadí i při zhasnuté obrazovce (vyžadovalo by foreground
  službu s notifikací)
- Ducking/ztišení jiné hudby při čtení
- Vlastní ikona aplikace (teď appka běží s výchozí ikonou Android Studia)
- Jemnější přeskakování více typů čísel (např. konkrétně jen telefonní čísla)
