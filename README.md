# Chytrá čtečka textu (SmartReader)

Android aplikace, která nahlas čte český text – "inteligentně", tedy
přeskakuje odkazy, čísla bankovních účtů/IBAN, dlouhá čísla (telefony apod.),
podtržítka a emoji.

## Funkce

- Vlastní textové pole – lze psát, vkládat i upravovat text před i během čtení
- Vložit ze schránky / Vymazat
- Přehrát / Pauza / Stop
- Posuvník rychlosti čtení (0.5x–3.0x), mění se za chodu
- Zvýraznění právě čteného úseku textu
- **Čtení pokračuje i se zhasnutou obrazovkou nebo na pozadí** – běží jako
  foreground služba s notifikací (Přehrát/Pauza, Stop přímo z notifikace)
- **Ducking** – při čtení se hudba v jiných appkách (Spotify, YouTube Music...)
  automaticky ztiší, místo aby se zastavila
- Sdílení z jiných aplikací a označení textu v libovolné appce → "Chytrá
  čtečka textu" v nabídce
- Sdílený odkaz appka zkusí sama stáhnout a vytáhnout hlavní text stránky

## Opravené chyby

- **Změna rychlosti za chodu**: dřív (i u konkurenčních appek) změna rychlosti
  restartovala čtení od úplného začátku textu. Teď appka sleduje pozici po
  větách (ne po obřích blocích textu) a při změně rychlosti pokračuje přesně
  odtud, kde čtení právě bylo – ne od začátku.
- **Pauza + úprava textu**: appka teď vždy čte aktuální obsah textového pole
  od pozice kurzoru – ne zapamatovaný starý text. Když text během pauzy
  upravíš (např. smažeš první půlku), po Play se čte správně to, co tam
  skutečně je, od místa kurzoru. Stejně tak můžeš kdykoli kliknout kamkoli do
  textu a stisknout Přehrát – čtení začne přesně odtud.

  Poznámka k oběma opravám: žádný Android TTS engine neumí měnit rychlost
  doprostřed už generované promluvy bez jejího zastavení – to je omezení
  systému, ne appky. Rozdíl je v tom, o *kolik* textu appka při obnovení
  přijde: díky sekání po větách je to v nejhorším případě jedna věta, ne celý
  článek.

## Nové v této verzi

- **Uložení rozečteného textu** – appka si při zavření zapamatuje text i pozici,
  kde jsi skončil, a po dalším spuštění je nabídne zpátky
- **Knihovna textů** – tlačítko "Uložit" uloží aktuální text do knihovny,
  tlačítko "Knihovna" zobrazí seznam uložených textů (např. různé afirmace) a
  umožní mezi nimi přepínat nebo je smazat; appka si pamatuje, kde jsi u
  každého uloženého textu naposledy skončil
- **Auto-scroll** – textové pole se samo posouvá tak, aby byla právě čtená
  věta vždy vidět
- **Výběr hlasu** – v Nastavení (ikona ozubených koleček) jde vybrat mezi
  všemi českými hlasy nainstalovanými v telefonu
- **Nezávislá hlasitost čtení** – posuvník v Nastavení mění hlasitost čtení
  jako násobič nad systémovou hlasitostí médií (netýká se hlasitosti ostatních
  appek)
- Tlačítka +/- vedle jezdce rychlosti
- Nativní ovládání na zamykací obrazovce přes MediaSession
- Nová ikona aplikace, přejmenováno na "Chytrá čtečka textu"
- Hezčí grafika – Material Design tlačítka s ikonami, zaoblené textové pole
- Pevný debug podpisový klíč (`debug.keystore` v repozitáři) – každá nová
  verze appky se dá nainstalovat jako update předchozí

## Jak spustit

1. Nainstaluj [Android Studio](https://developer.android.com/studio)
2. `File > Open` → vyber tuto složku
3. Počkej na Gradle sync
4. Připoj telefon nebo spusť emulátor, klikni **Run ▶**
5. V telefonu musí být nainstalovaný **český hlas** pro Převod textu na řeč
6. Appka si při prvním spuštění vyžádá **oprávnění na notifikace** (Android
   13+) – potřebuje ho pro ovládání čtení na pozadí. Bez povolení appka
   pořád funguje, jen neuvidíš ovládací notifikaci.

## Sestavení přes GitHub Actions

Repozitář obsahuje `.github/workflows/build.yml` – při každém pushi na
`main` se automaticky sestaví `app-debug.apk`, který najdeš v záložce
**Actions** u daného běhu, sekce **Artifacts**.

## Důležité omezení

Facebook a Instagram většinou při sdílení pošlou jen odkaz, jehož obsah se
načítá přes JavaScript, který appka nespustí – stahování textu z takového
odkazu proto často selže. Řešení: v appce Facebook/Instagram text příspěvku
prstem označ a z nabídky vyber "Chytrá čtečka textu" – přečte přesně to, co
vidíš na obrazovce. U novinových článků funguje sdílení odkazu spolehlivě.

## Možná budoucí vylepšení

- Nativní ovládání na zamykací obrazovce přes MediaSession (teď je jen
  vlastní notifikace s tlačítky, což pokrývá běžné použití)
- Automatické obnovení čtení po skončení telefonátu
