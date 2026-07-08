# Chytrá čtečka textu (SmartReader)

## Proč tahle appka vznikla

Kolik článků, e-mailů a dlouhých zpráv denně otevřeš a hned zase zavřeš,
protože "na to teď není čas"? Chytrá čtečka textu vznikla z jednoduché
myšlenky: čas strávený čtením u obrazovky se dá získat zpátky. Stačí nechat
text přečíst nahlas a poslouchat ho cestou do práce, při vaření, na
procházce se psem nebo při skládání prádla.

Nejde o to číst rychleji - jde o to nemuset si sednout a číst vůbec, a
přesto obsah zachytit. Novinový článek, dlouhý e-mail, PDF report nebo
vlastní poznámky k učení - všechno se dá "odposlouchat" mimochodem, zatímco
děláš něco jiného.

Appka rostla postupně, přesně podle toho, co se v praxi hodilo: nejdřív
základní čtení nahlas, pak možnost pokračovat přesně tam, kde člověk
přestal, pak čtení na pozadí (aby nebylo nutné civět na telefon), pak
možnost načíst text rovnou z fotky nebo webového odkazu, aby nebylo potřeba
nic opisovat ručně. Cíl zůstává od začátku stejný: ušetřit čas tam, kde
čtení není nutné, jen zvykové.

## Co appka umí

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

## Nové v této verzi (2.1)

- **Tmavý režim** – appka teď automaticky respektuje systémové nastavení
  tmavého/světlého vzhledu telefonu
- **Přeskočit odstavec vpřed/vzad** – nová tlačítka vedle Přehrát, chovají
  se jako v audioknihách (dost hluboko v odstavci "předchozí" skočí na jeho
  začátek, blízko začátku až na odstavec před ním)
- **Fronta textů z knihovny** – v dialogu Knihovna jde zaškrtnout víc textů
  a přehrát je za sebou tlačítkem "Přehrát vybrané" (v1: spojí je do
  jednoho souvislého textu, ne odděleně track po tracku)
- **Lepší čtení zkratek** – "např.", "tzn.", "atd." a podobné se teď rozepíší
  na plné znění, ať TTS nedělá zbytečnou pauzu uprostřed věty
- **Automatické pokračování po telefonátu** – volitelný přepínač v
  Nastavení; když je zapnutý, appka po skončení hovoru sama naváže tam, kde
  přestala

- **Menu se třemi tečkami** v hlavičce obsahuje: Nastavení čtení, Nápověda
  a tipy, Vymazat celou knihovnu, Sdílet appku, Zkontrolovat aktualizace
  (otevře stránku s releasy), O aplikaci (verze + odkaz na GitHub)

- **Plynulejší čtení textu z OCR** – appka teď spojuje řádky rozpoznané na
  obrázku zpátky do souvislých vět (podle bloků, jak je detekuje ML Kit),
  místo aby dělala pauzu na konci každého vizuálního řádku z fotky
- **Design tlačítek doladěn dle zpětné vazby** – Vložit/Vymazat mají text.
  Obrázek, Odkaz, Uložit, Knihovna a Nastavení jsou teď jen ikonová tlačítka
  zarovnaná vlevo v jednom řádku (bez zalamování textu na dva řádky). Mezery
  mezi řádky i hlavička s názvem appky nahoře jsou užší.
- **Rozpoznávání textu z obrázku (OCR)** – nové tlačítko "Obrázek" umožní buď
  vybrat fotku/screenshot z galerie, nebo rovnou vyfotit dokument
  fotoaparátem. Text z obrázku appka rozpozná přímo na telefonu (Google ML
  Kit, on-device), vloží do textového pole a dá se rovnou přečíst.
  Rozpoznávací model je rovnou součástí instalačního APK (funguje offline
  hned od prvního spuštění, bez závislosti na Google Play Services) - proto
  appka nově váží cca 49 MB místo dřívějších ~5 MB. Obrázek appka nikam
  neposílá, vše se zpracovává lokálně v telefonu.
- GitHub Release s přímo stažitelným `.apk` (bez zipu) – vždy aktuální na
  stálém odkazu `releases/tag/latest-build`
- Repozitář je nově veřejný – stahování bez přihlášení

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
