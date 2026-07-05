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

- **Změna hlasu za chodu** – dřív bylo potřeba dát Stop a spustit znova,
  teď pokračuje čtení stejným principem jako u rychlosti (od aktuální pozice)
- **Nová ikona ve stavové liště** – výrazný glyf reproduktoru se zvukovými
  vlnami místo nevýrazného obdélníku
- **Přebarvené tlačítko Přehrát/Pauza** – zelená = Přehrát, oranžová = Pauza,
  na první pohled jasné, co tlačítko dělá
- **Čtení čísel v kontextu** – "220.000" se teď sloučí na "220000" a TTS ho
  přečte jako "dvě stě dvacet tisíc", ne po jednotlivých číslicích (viz
  poznámka níže)
- **Auto-scroll po odstavcích** – text se posouvá tak, aby byl začátek
  právě čteného odstavce v horní třetině obrazovky, ne až na posledním řádku
- **Redesign hlavičky** – barevný gradientový pruh s vlastním logem místo
  obyčejného textu
- **Načtení textu z odkazu ručně** – nové tlačítko s ikonou odkazu otevře
  dialog, kam vložíš URL, a appka stáhne a vyčistí text článku; totéž se
  stane automaticky i při vložení URL ze schránky
- Vylepšené heuristiky stahování článků (blíž k "reader mode" prohlížečů)

### Poznámka ke čtení čísel

Oprava řeší konkrétní problém: český zápis velkých čísel s tečkou jako
oddělovačem tisíců (220.000) se sloučí na čisté číslo (220000), které pak
Android TTS engine přečte správně - tohle už umí nativně. Appka nedělá
vlastní gramatiku pro skloňování čísel (to je nad rámec rozumného rozsahu),
spoléhá na to, co telefonní TTS engine zvládá sám, jakmile dostane číslo
ve správném formátu.

### Poznámka ke stahování z Facebooku/Instagramu

I po vylepšení heuristik platí totéž omezení jako dřív: FB/IG posílají bez
přihlášení a bez JavaScriptu jen minimum obsahu (nanejvýš krátký
og:description popisek), takže spolehlivé stažení celého veřejného
příspěvku touto cestou není možné - je to omezení dané tím, jak tyto
platformy fungují, ne otázka lepšího parsování. Spolehlivá cesta zůstává:
označit text přímo v appce FB/IG a vybrat "Chytrá čtečka textu" z nabídky.

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
