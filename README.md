# Chytrá čtečka textu (SmartReader)

## ⚠️ Balíček appky se znovu změnil - vyžaduje čistou instalaci

Balíček byl přejmenován z `io.github.marceldohnalcz.smartreader` na
`io.github.marciano.smartreader` (předchozí verze použila GitHub
uživatelské jméno bez svolení, teď je to opravené). Před instalací je
znovu potřeba starou verzi ručně odinstalovat - nejde o update.

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

## Nové v této verzi (2.55)

- **Klepnutí na popisek "Rychlost"** teď rovnou resetuje rychlost čtení
  zpátky na normální 1,00x - rychlejší než přesouvat posuvník ručně.

## Nové v této verzi (2.54)

- **Oprava**: po změně rychlosti čtení za chodu se zvýrazňování čteného
  textu mohlo "zbláznit" a ukazovat rychleji, než se doopravdy četlo.
  Příčina: appka si dopředu naplánovala všechny zbylé věty najednou (ne
  jednu po druhé), a při restartu čtení (to appka dělá při každé změně
  rychlosti) mohlo dorazit opožděné hlášení ze STARÉHO, už zrušeného
  čtení a poplést pozici. Appka teď každému čtení přiřadí "generaci" a
  opožděná hlášení ze starších generací ignoruje, navíc věty plánuje až
  jednu po druhé (ne všechny najednou), ať k podobnému závodu nemůže
  dojít vůbec.

## Nové v této verzi (2.53)

- **Další pokus o opravu falešné nabídky "Uložit heslo"** – tentokrát u
  dialogu Přidat/Upravit sledovaný profil. Pole "Jméno" mělo nastavení
  `textPersonName` ("tohle je jméno osoby"), což v kombinaci s dalším
  polem hned pod ním mohlo Samsung Pass svést k domněnce, že jde o
  přihlašovací formulář. Odstraněno, navíc nastaveno i přímo v kódu (ne
  jen v XML), pro jistotu.

## Nové v této verzi (2.52)

- **Stažitelný hlas Jirka (Piper) odstraněn** - kvalita hlasu nakonec
  neuspokojila. Appka se vrací k čistě systémovým TTS hlasům (Nastavení
  hlasu teď obsahuje jen hlasitost, výšku, hlasový modul a výběr
  systémového hlasu). Appka je zase o dost menší (zpátky pod 10 MB).
  Kompletní funkční implementace zůstává zachovaná v Gitu pod značkou
  `piper-voice-feature-2026-08`, kdyby se k tomu appka v budoucnu chtěla
  vrátit (např. s kvalitnějším hlasem).

## Nové v této verzi (2.51)

- **Oprava**: ukázka staženého hlasu Jirka mohla působit, že vůbec
  nefunguje - ve skutečnosti appka jen bez jakéhokoli náznaku načítala
  model z disku (chvíli to trvá) a nedávala žádnou zpětnou vazbu, že se
  něco děje. Appka si teď hlas mezi ukázkami podrží v paměti (další
  ukázky jsou pak rychlé) a při načítání ztlumí název, ať je vidět, že
  appka reaguje. Klepnutí funguje na celém řádku, ne jen přesně na jméně.
- **Zjednodušený vzhled**: odstraněny popisky pod nadpisem "Stažitelné
  hlasy" a pod jménem každého hlasu - řádek je teď stručnější.

## Nové v této verzi (2.50)

- **Stažený hlas Jirka teď jde skutečně použít pro čtení**, ne jen si
  poslechnout ukázku - v "Nastavení hlasu" u něj přibylo výběrové
  kolečko (stejně jako u systémových hlasů). Vybere se tím jako aktivní
  hlas pro čtení, vzájemně vylučující se s výběrem systémového hlasu.
  Omezení oproti systémovým hlasům: zvýraznění čteného textu je na
  úrovni věty (ne slova), pauza přehraje aktuální větu znovu od začátku
  a výška hlasu (pitch) u něj nefunguje - Piper tenhle parametr nemá.
- **Oprava**: ukázky hlasů se mohly přehrávat přes sebe (systémová a
  Piper ukázka najednou) - teď se vždy nejdřív zastaví ta předchozí.
- **Vysvětlení "duplicitních" hlasů**: když appka nabízí dva hlasy, co
  znějí stejně (např. "Hlas 1" a "Hlas 2"), nejde o chybu - systémový
  TTS modul telefonu má pro daný hlas dvě technické varianty (typicky
  "lokální" a "online"), které appka správně vidí jako dvě oddělené
  položky, i když zní stejně.

## Nové v této verzi (2.49)

- **Sloučené menu "Nastavení hlasu"**: vše kolem hlasu (hlasitost, výška,
  hlasový modul, výběr systémového hlasu i stažitelné hlasy) je teď na
  jednom místě v menu ⋮, místo dřívějších dvou oddělených položek.
  Obecné Nastavení (automatické pokračování, historie, vzhled) zůstává
  samostatně.
- **Zjednodušená ukázka stažených hlasů**: klepnutí na jméno stažitelného
  hlasu přehraje ukázku (zelené tlačítko zrušeno), stejně jako už funguje
  u systémových hlasů.
- **Jen jeden hlas Jirka místo dvou** - "nižší kvalita" zabírala skoro
  přesně stejně místa jako "vyšší kvalita" (obě cca 20 MB), takže
  nedávalo smysl nabízet horší variantu bez žádné úspory.

## Nové v této verzi (2.48)

- **Appka výrazně menší**: 126 MB → cca 32 MB. Nativní knihovny pro
  stažitelné hlasy se dřív balily pro 4 typy procesorů najednou, appka
  teď ponechává jen tu, kterou používá drtivá většina telefonů
  posledních ~8 let (arm64-v8a). Skutečné stahování enginu "až na
  vyžádání" bohužel nejde udělat spolehlivě mimo Google Play - Android
  od verze 10 z bezpečnostních důvodů blokuje spouštění nativního kódu,
  který nebyl součástí instalace appky.
- **Oprava**: textové smajlíky (":-(", ":)", ";-)" apod.) se dřív četly
  doslova po jednotlivých znacích ("dvojtečka spojovník") - teď appka
  pozná běžné smajlíky jako celek a přeskočí je, stejně jako u
  opravdových emoji. Číslované seznamy (např. "8) položka") zůstávají
  beze změny, ať se s nimi smajlík nepřeplete.
- **Oprava**: "(...)" (elipsa v závorce, typicky značí vynechaný text v
  citaci) se dřív četlo jako osamocené slovo "tečka" - teď appka
  rozpozná celou kombinaci a smaže ji najednou. Funguje jak pro tři
  tečky, tak pro unicode znak elipsy "…".

## Nové v této verzi (2.47)

- **Stažitelný offline hlas "Jirka"** (nová položka v menu ⋮ "Stažitelné
  hlasy"): appka umí stáhnout a spustit vlastní neurální hlas (Piper,
  přes sherpa-onnx engine) nezávisle na tom, jaký TTS modul má uživatel
  v telefonu. Hlas se stáhne jednou (cca 20 MB), pak funguje úplně
  offline. Zatím jde jen stáhnout a přehrát si ukázku - napojení do
  hlavního tlačítka Přehrát je další krok.
- **Jednodušší výběr hlasu v Nastavení**: klepnutí na hlas ho rovnou
  vybere A přehraje ukázku (zrušeno samostatné zelené tlačítko z minulé
  verze).
- **Ukázka po změně výšky hlasu**: posunutí posuvníku výšky hlasu a
  puštění prstu rovnou přehraje ukázkovou větu novou výškou, ať je
  změna hned slyšet.

## Nové v této verzi (2.46)

- **Výška hlasu**: nový posuvník v Nastavení (rozsah 0,5-1,5, uprostřed
  normální) - uloží se a zůstává i po zavření appky.
- **Ukázka hlasu přes tlačítko**: zelená šipka vedle každého hlasu místo
  dřívějšího podržení prstu - klepnutí na šipku přehraje ukázkovou větu,
  klepnutí na hlas ho vybere.

## Nové v této verzi (2.45)

- **Ukázka hlasu v Nastavení**: podržením prstu na kterémkoli hlasu v
  seznamu appka přehraje krátkou ukázkovou větu tím hlasem, ať slyšíš, o
  jaký hlas jde, dřív než ho vybereš. Krátké klepnutí hlas vybere jako
  doteď. Ukázka jede přes úplně samostatný TTS, takže nijak nenaruší
  případné právě probíhající čtení hlavního textu.

## Nové v této verzi (2.44)

- **Výběr hlasového modulu přímo v appce**: v Nastavení je nová sekce
  "Hlasový modul" nad výběrem hlasu - appka vypíše všechny hlasové
  moduly nainstalované v telefonu (Google, ale i appky třetích stran
  jako Vocalizer TTS a podobné) a jde mezi nimi přepínat jedním klepnutím,
  bez nutnosti chodit do nastavení telefonu. Po přepnutí modulu se
  automaticky obnoví i seznam dostupných hlasů (každý modul má vlastní
  sadu). Volba se ukládá a appka si ji pamatuje i po zavření.

## Nové v této verzi (2.43)

- **Appka se teď skutečně ukončí a přestane číst**, když ji smažeš ze
  seznamu naposledy spuštěných appek (přehodíš do koše/swipnutím pryč).
  Dřív appka v takovém případě dál běžela na pozadí a pokračovala ve
  čtení, i když sis myslel, že jsi ji zavřel - teď ukončení appky tímhle
  gestem přehrávání i celou službu na pozadí definitivně zastaví.

## Nové v této verzi (2.42)

- **Dynamická rychlost posuvníku podle délky textu**: dřív měl posuvník
  pevně danou rychlost tažení, takže u hodně dlouhého textu bylo potřeba
  přejet pruh víckrát, než se appka dostala až na konec. Teď appka
  rychlost přepočítává podle skutečné délky aktuálního textu - jedno
  přejetí prstem od horního po dolní okraj pruhu vždy pokryje úplně celý
  text, ať je krátký nebo dlouhý.

## Nové v této verzi (2.41)

- **Dokončeno zjednodušené rozložení na šířku**: v režimu na šířku jsou
  teď vidět jen tlačítka Vložit/Vymazat a ovládání přehrávání
  (Přehrát/Pauza, Zpět, Vpřed, Stop) - řádek ikon (Obrázek, Odkaz, Uložit,
  Knihovna, Historie, Sledované profily) a posuvník rychlosti jsou v
  landscape trvale schované, ať má textové pole ještě víc místa. Samotné
  funkce nikam nezmizely, jen nejsou v tomhle režimu na hlavní obrazovce -
  otočením zpět na výšku budou zase všechny vidět.

## Nové v této verzi (2.40)

- **Oprava**: po otočení telefonu (na šířku a zpět) během čtení zůstávalo
  žluté podbarvení u slova, u kterého appka byla v okamžiku otočení -
  i po pokračování čtení dál. Příčina: Android má vlastní automatický
  mechanismus pro zachování obsahu textových polí při otočení obrazovky,
  nezávislý na appce - a protože appka má svůj vlastní systém pro
  obnovení rozepsaného textu, tyhle dva mechanismy se navzájem přepisovaly
  a to systémové obnovilo i starý zvýrazňovací pruh, o kterém appka
  nevěděla. Appka teď Androidu řekne, ať se do textového pole při otočení
  nepřidává - obnovení textu si řídí sama.

## Nové v této verzi (2.39)

- **Oprava pádu appky při otočení na šířku během čtení.** Příčina:
  tlačítka Vložit a Vymazat měla v rozložení na výšku typ "tlačítko s
  textem", ale ve zvláštním rozložení pro režim na šířku (přidáno v 2.31)
  jsem je omylem předělal na typ "jen ikona" - se stejným ID, ale jiným
  typem. Android při otočení obrazovky appku zničí a znovu postaví podle
  nového rozložení, a tenhle nesoulad typů appku shodil. Opraveno - obě
  tlačítka mají teď v obou rozloženích stejný typ, jen v režimu na šířku
  zůstávají kompaktnější (menší text).

## Nové v této verzi (2.38)

- **Oprava**: appka nespolehlivě četla svislou čáru "|" (typicky ohraničení
  buněk markdown tabulky, např. "|---|---|---|") - podle konkrétního hlasu
  buď mlčela, nebo ji četla nahlas jako "svislá čára", i opakovaně.
  Teď se maže vždy spolehlivě stejně.
- **Další markdown znaky, co appka nikdy nemá číst nahlas**: zpětné
  apostrofy (kód: `` `kód` ``), vlnovky (přeškrtnutý text: `~~takhle~~`) a
  značka citace na začátku řádku ("> citace"). Skutečné "větší než"
  uprostřed věty (např. "5 > 3") zůstává beze změny - maže se jen značka
  citace na začátku řádku.
- Řádek oddělující záhlaví tabulky ("|---|---|---|") díky kombinaci s už
  existujícím pravidlem pro opakované pomlčky zmizí úplně celý.

## Nové v této verzi (2.37)

- **Vysvětlení "zaseklého počítadla" v historii**: appka nebyla rozbitá -
  historie má schválně limit na počet záznamů, ať neroste donekonečna, a
  po dosažení limitu se nejstarší záznamy automaticky mažou. Problém byl,
  že appka o tom nikde neřekla, takže to vypadalo jako chyba. Teď se
  v dialogu Historie objeví vysvětlující poznámka, jakmile je limit
  dosažený. Zároveň jsem limit zvedl ze 100 na 300 záznamů.

## Nové v této verzi (2.36)

- **Oprava**: když jinou appku (Facebook, Instagram apod.) přehrávání
  přerušilo natrvalo, appka pak nešla znovu spustit - pomohlo jen vynucené
  zavření appky v nastavení telefonu. Příčina: appka si při každé žádosti
  o "audio focus" (právo hrát zvuk) vytvářela úplně nový objekt požadavku
  místo toho, aby - jak doporučuje Android - použila pořád ten stejný.
  U některých telefonů to po přerušení mohlo appku dostat do stavu, kdy
  si focus nešlo vzít zpátky. Teď appka požadavek vytvoří jen jednou a
  dál ho opakovaně používá.

## Nové v této verzi (2.35)

- **Zásadní změna: viditelný text se už vůbec nemění.** Appka dřív při
  spuštění čtení fyzicky přepisovala text v poli na "vyčištěnou" verzi
  (bez odkazů, křížků, hvězdiček, opakovaných pomlček apod.) - proto se
  postupně honila spousta drobných chyb kolem toho (mizející mezera aj.).
  Teď appka vidí a čte dvě oddělené věci: TO, CO VIDÍŠ (beze změny,
  přesně jak jsi to vložil) a TO, CO SE ČTE (interně vyčištěná verze jen
  pro TTS). Appka si mezi nimi pamatuje mapování pozic, takže zvýrazňování
  čteného slova i posouvání textu při čtení pořád přesně sedí na viditelný
  text - jen ho appka už nikdy neupravuje.
- Praktický důsledek: odkazy, #, *, opakované pomlčky a podobné znaky
  zůstanou v textu vidět přesně tak, jak je vložíš, ale appka je nikdy
  nepřečte nahlas.

## Nové v této verzi (2.34)

- **Oprava**: opakované pomlčky za sebou ("---", typicky vizuální
  oddělovač sekcí na vlastním řádku) se četly jako "spojovník, spojovník,
  spojovník". Teď se takové oddělovače úplně odstraní - jedna pomlčka
  mezi slovy (spojení slov, e-mail apod.) zůstává beze změny.

## Nové v této verzi (2.33)

- **Oprava**: pomlčka přímo mezi dvěma čísly (bez mezer) se dřív četla
  jako "mínus", i když šlo jen o oddělovač - typicky u čísla jednacího
  nebo spisové značky ("15 C 123/2024-67"), rozsahu stránek ("12-15")
  nebo telefonu ("777-123-456"). Skutečné odečítání nebo záporné číslo
  se v textu skoro vždy píše s mezerami kolem pomlčky ("5 - 3") nebo bez
  předchozí číslice ("-5 stupňů") - tyhle případy zůstávají beze změny,
  opravuje se jen ten typický vzor "číslo-číslo" bez mezer.

## Nové v této verzi (2.32)

- **Oprava**: znak "#" se dřív mazal jen když byl nalepený na slovo (jako
  hashtag "#příklad"), ne když stál samostatně nebo třeba v nadpisu
  ("# Nadpis") - appka ho pak četla nahlas jako "křížek". Teď se maže
  vždy.
- **Nové**: markdown zvýraznění hvězdičkami (`*kurzíva*`, `**tučně**`,
  odrážky "* položka") se teď taky odstraňuje - dřív appka hvězdičky
  četla doslova ("hvězdička hvězdička").

## Nové v této verzi (2.31)

- **Nové rozložení pro režim na šířku (landscape)**: veškeré ovládání
  (Vložit/Vymazat, ikony Obrázek/Odkaz/Uložit/Knihovna/Historie/Sledované,
  posuvník rychlosti, Přehrát/Zpět/Vpřed/Stop) je sloučené do jednoho
  řádku dole - menší ikony, kompaktnější posuvník rychlosti. Textové pole
  tak dostane naprostou většinu obrazovky. Pokud by se na některém
  telefonu přece jen všechno nevešlo najednou, řádek jde doscrollovat
  vodorovně místo aby se něco neviditelně oříznulo. V režimu na výšku se
  appka chová jako doteď, beze změny.

## Nové v této verzi (2.30)

- **Oprava**: appka po každém otevření sama vysunula klávesnici -
  vedlejší efekt nastavení `adjustResize` z verze 2.27 (řešilo, ať
  klávesnice nezakrývá ovládání), které samo o sobě neurčuje, zda se má
  klávesnice zobrazit i bez toho, aby ses do textu klepnutím sám
  přihlásil. Přidáno explicitní `stateHidden` - klávesnice se teď ukáže
  jen když na text skutečně klepneš.

## Nové v této verzi (2.29)

- **Jemnější krok rychlosti čtení**: posuvník i tlačítka +/- teď mění
  rychlost po 0,05x místo po 0,1x (rozsah zůstal stejný, 0,5x-3,0x, jen
  s dvojnásobným počtem kroků). Zobrazení rychlosti teď ukazuje dvě
  desetinná místa (např. "1,05x"), ať je ten jemnější krok vidět.

## Nové v této verzi (2.28)

- **Oprava**: schovávání ovládání při vyjeté klávesnici (přidáno v 2.27)
  vůbec nefungovalo. Příčina: appka porovnávala výšku okna sama se sebou
  - kvůli `adjustResize` (nastaveno kvůli tomu, ať klávesnice nezakrývá
  ovládání) se totiž zmenší úplně stejně obě porovnávané hodnoty, takže
  rozdíl vyšel skoro nulový a klávesnice se nikdy nepoznala. Teď se
  porovnává proti skutečné celkové výšce obrazovky.

## Nové v této verzi (2.27)

- **Zjednodušené ovládání při vyjeté klávesnici** - appka teď pozná, kdy
  je klávesnice aktivní, a automaticky schová Vložit/Vymazat, řádek ikon
  (Obrázek/Odkaz/Uložit/Knihovna/Historie/Sledované), posuvník rychlosti
  a tlačítka Zpět/Vpřed/Stop. Zůstane jen Přehrát/Pauza, ať zbyde víc
  místa a ovládání je vždy po ruce. Jakmile klávesnici schováš, všechno
  se vrátí zpět.

## Nové v této verzi (2.26)

- **Klávesnice teď nezakrývá ovládání Přehrát/Pauza** - appka dřív
  neříkala systému, jak se má obsah zmenšit, když klávesnice vyjede,
  takže mohla ovládání dole schovat. Teď se textové pole zmenší a
  tlačítka zůstanou vidět nad klávesnicí.
- **Další pokus o opravu mizející mezery při klepnutí do textu** -
  minulá oprava (odstranění `.trim()`) byla ověřená a v kódu skutečně
  je, ale chyba přetrvávala, takže šlo nejspíš o klávesnici samotnou
  (automatické opravy/návrhy), ne o appku. Vypnul jsem u textového pole
  automatické návrhy klávesnice (`textNoSuggestions`), což by mělo
  zabránit klávesnici v úpravách textu jen kvůli umístění kurzoru.
  Pokud se to objeví znovu, dej vědět - bude to znamenat, že příčina je
  jinde, než jsem čekal.

## Nové v této verzi (2.25)

- **Oprava**: klepnutí na slovo v textu (start čtení od té pozice) mazalo
  mezeru před daným slovem v textovém poli - vypadalo to divně, i když to
  na čtení nemělo žádný vliv. Příčina: čisticí krok před čtením na konci
  volal `.trim()`, což se nechtěně promítlo i do viditelného textu.
  Odstraněno, mezera TTS nijak nevadí.

## Nové v této verzi (2.24)

- **Výrazný oddělovač mezi Knihovnou a Historií** v prostředním řádku
  ikon - stejný styl, jaký už odděloval skupinu Obrázek/Odkaz od
  Uložit/Knihovna vlevo.

## Nové v této verzi (2.23)

- **Přidat profil přesunuto** vedle tlačítka Zavřít (dialog Sledované
  profily) - oba na jednom řádku, stejný vzor jako u Historie.
- **Klepnutí na název sledovaného profilu** teď rovnou otevře odkaz -
  stejná akce jako klepnutí na ikonu s odkazem, jen pohodlnější (větší
  klikací plocha).

## Nové v této verzi (2.22)

- **Důkladnější oprava falešné nabídky "Uložit heslo"** - dřívější
  oprava (vypnutí autofill jen na konkrétních textových polích) nestačila
  a chyba se pořád objevovala. Teď je autofill vypnutý pro **celou
  appku** na úrovni manifestu (appka žádné přihlašování nikde nemá, takže
  to nevadí) - silnější a trvalejší řešení, které by mělo pokrýt i
  budoucí textová pole bez nutnosti to řešit zvlášť pokaždé.

## Nové v této verzi (2.21)

- **Interaktivní posuvník**: nový vlastní posuvník u textového pole -
  v klidu je úzký, jakmile ho podržíš prstem, plynule se roztáhne, a po
  puštění se zase zúží. Nahradil kombinaci systémového scrollbaru a
  neviditelné dotykové vrstvy jedním vlastním prvkem, který si sám kreslí
  pozici i výšku podle skutečné délky textu.
- **Úprava sledovaného profilu**: velké červené tlačítko Smazat u
  sledovaných profilů nahradily tři tečky (⋮) - otevřou menu s Upravit a
  Smazat. Upravit umožní opravit jméno nebo odkaz u už přidaného profilu.
  Dostupné ve všech 9 jazycích.

## Nové v této verzi (2.20)

- **Oprava ikony Sledovaných profilů** - postavička byla posunutá dolů a
  vypadala oproti ostatním ikonám v řádku níž/menší. Přepočítal jsem
  souřadnice, ať je vystředěná stejně jako ostatní ikony (podle vzoru
  ikony Historie).
- **Oprava falešné nabídky "Uložit heslo"** - telefon (hlavně Samsung)
  se ptal, jestli uložit heslo, i když appka žádné heslo vůbec nemá.
  Způsobil to systém automatického vyplňování, který si dvě textová pole
  vedle sebe (jméno + odkaz) mylně vyložil jako přihlašovací formulář.
  Appka teď textovým polím výslovně řekne, že nejde o přihlašovací údaje.

## Nové v této verzi (2.19)

- **Nová funkce: Sledované profily** - nové tlačítko v řádku ikon vedle
  Historie. Přidáš si jméno + odkaz na profil (typicky FB/IG), appka si
  pamatuje, kdy jsi ho naposledy zkontroloval. Klepnutím profil otevřeš
  v prohlížeči/appce, ručně zkontroluješ nové příspěvky a text pošleš
  appce přes Sdílet nebo výběr textu (to appka umí už dřív).
  **Důležité:** appka nic sama automaticky nestahuje ani nescrapuje - to
  by porušovalo podmínky Facebooku/Instagramu a riskovalo schválení na
  Google Play i tvůj vlastní účet. Tohle je bezpečná, ručně řízená
  varianta, akorát rychlejší.
- Dostupné ve všech 9 jazycích.

## Nové v této verzi (2.18)

- **Oprava formátování Nápovědy ve všech 9 jazycích** - text byl slitý do
  jednoho nekonečného odstavce. Příčina: CDATA v strings.xml nechrání
  odřádkování tak, jak by se čekalo - Android při kompilaci zdrojů
  doslovné řádky sloučí do jednoho. Opraveno použitím stejného
  `\n` zápisu, jaký už správně fungoval jinde v appce.
- **Rozestup tlačítek Zrušit/Potvrdit i u zbylých dialogů** - mazání
  jednotlivé položky v knihovně a zadání odkazu teď mají stejný vzhled
  (Zrušit vlevo, potvrzení vpravo s rozestupem) jako dialogy pro hromadné
  mazání, opravené dřív.

## Nové v této verzi (2.17)

- **Oprava**: u dlouhého textu BEZ odstavců (jeden velký blok textu) se
  posouvání při čtení vůbec nehýbalo - appka totiž hledala "začátek
  aktuálního odstavce", a bez prázdných řádků to vždy vyšlo jako úplný
  začátek textu, takže se pohled pořád vracel tam. Teď auto-scroll
  sleduje přímo řádek, který se právě čte (ne odstavec), takže postupuje
  plynule i v rámci jednoho dlouhého odstavce - text, co se právě čte,
  je vždy vidět.

## Nové v této verzi (2.16)

- **Oprava nalezená při důkladném auditu před vydáním**: rozepisování
  zkratek přestalo fungovat, když byla zkratka hned vedle závorky nebo
  uvozovky (např. "apod.)" nebo "(str."). Způsobil to nedopatření při
  přepisu na víc jazyků v 2.15 - hranice rozpoznávání zkratky byla příliš
  úzká (jen mezera/začátek/konec věty). Opraveno a znovu důkladně
  otestováno na desítkách vět ve všech 9 jazycích.
- Přidán `UNICODE_CASE` příznak pro správné rozlišení velkých/malých
  písmen mimo ASCII (důležité hlavně pro ruštinu/azbuku).

## Nové v této verzi (2.15)

- **Chytré čtení teď funguje ve všech 9 podporovaných jazycích**, ne jen
  v češtině. Appka rozpozná běžné zkratky podle aktuálního jazyka zařízení
  a rozepíše je na plné znění, ať TTS nedělá zbytečnou pauzu uprostřed
  věty - anglicky "e.g." → "for example", německy "z.B." → "zum Beispiel",
  francouzsky "c.-à-d." → "c'est-à-dire", a podobně pro španělštinu,
  italštinu, portugalštinu, polštinu a ruštinu. Zkratky byly vybírány
  konzervativně (jen ty nejběžnější a jednoznačné), ať nehrozí, že se
  omylem rozepíše něco jiného.

## Nové v této verzi (2.14)

- **Kompletní lokalizace appky do 9 jazyků**: čeština, angličtina
  (výchozí/fallback), němčina, španělština, francouzština, italština,
  portugalština, polština, ruština. Appka automaticky použije jazyk
  nastavený v telefonu; nepodporovaný jazyk spadne zpátky na angličtinu.
- **Čtení teď respektuje jazyk telefonu** - dřív byl hlas i "chytré"
  čtení natvrdo pro češtinu bez ohledu na jazyk appky. TTS teď použije
  jazyk zařízení (`Locale.getDefault()`), výběr hlasu v Nastavení nabízí
  jen hlasy pro aktuální jazyk, a české specifikum (rozepisování zkratek
  jako "např.") se aplikuje jen když appka skutečně čte česky - u jiných
  jazyků se nepoužije.
- Poznámka: název appky samotné je taky lokalizovaný (např. anglicky
  "Smart Text Reader", německy "Intelligenter Textleser" atd.) - jak appka
  bude na plochu telefonu, závisí na jazyce zařízení.

## Nové v této verzi (2.13)

- **Rozestup tlačítek v potvrzovacím dialogu**: "Zrušit" je teď vlevo,
  potvrzovací tlačítko vpravo, s velkou mezerou mezi nimi (dřív byla těsně
  vedle sebe, snadno se dalo trefit špatné) - týká se dialogů "Vymazat
  celou knihovnu/historii?"
- **Oprava**: tažení posuvníku a automatický posun textu při čtení si už
  nekonkurují - jakmile appka detekuje, že držíš posuvník, auto-scroll se
  na tu dobu úplně vypne
- **Jiné appky (YouTube, Spotify) se teď při čtení zastaví, ne jen
  ztiší** - appka žádá o jiný typ "audio focus", který slušně napsané
  appky berou jako "pozastav se", ne jen "buď potichu". Po dočtení se
  obvykle samy pustí zpátky (to už je jejich vlastní chování, appka jen
  korektně pustí focus)

## Nové v této verzi (2.12)

- **Oprava**: dialogy Historie a Knihovna měly u hodně položek problém, že
  seznam přerostl obrazovku a tlačítka dole (Vymazat/Zavřít) úplně zmizela
  - `maxHeight` u seznamu totiž ve skutečnosti nefungoval. Teď má seznam
  pevnou výšku s viditelným posuvníkem, tlačítka dole zůstávají vždy na
  místě
- **Oprava verzování**: žádné přeskakování čísel, jen prosté postupné
  číslování (2.11 → 2.12 → 2.13...)

## Nové v této verzi (2.11)

- **Zpět na jednoduché potvrzení tlačítkem**: mazání knihovny/historie už
  nevyžaduje psát slovo (bylo to otravné) - stačí tlačítko "Ano, vymazat"
  v potvrzovacím dialogu
- **Redesign dialogu Knihovna** - stejný styl jako Historie: barevné
  kolečko s ikonou v nadpisu, oddělovací linka, vlastní tlačítko Zavřít
- **Oprava čitelnosti**: text tlačítka "Přehrát vybrané" je teď vždy bílý
  (dřív byl matoucí, málo kontrastní vůči zelenému pozadí)

## Nové v této verzi (2.09)

- **Silnější ochrana proti omylem smazané knihovně/historii**: tlačítko
  "Vymazat vše" (jak v knihovně, tak v historii) teď vyžaduje napsat do
  pole slovo "SMAZAT", než jde vůbec potvrdit - jedno rychlé odklepnutí
  dialogu už nestačí ke smazání

## Nové v této verzi (2.08)

- **Oprava textu tlačítka Zavřít**: barva textu se teď otáčí podle motivu
  (bílá ve světlém režimu na tmavě tyrkysovém pozadí, tmavá v tmavém
  režimu na světle tyrkysovém pozadí) - dřív byla natvrdo bílá, což na
  světlém pozadí v tmavém režimu nešlo přečíst
- **Přepracovaný nadpis dialogu Historie**: barevné kolečko s bílou ikonou
  (kontrast nezávislý na motivu) + text v neutrální vysoce čitelné barvě
  (dřív byl celý nadpis barevný, což mělo na tmavém pozadí špatný
  kontrast) + oddělovací linka pod nadpisem

## Nové v této verzi (2.07)

- **Redesign dialogu Historie** - vlastní nadpis s ikonou (místo obyčejného
  systémového titulku), karta pro přepínač "Zaznamenávat historii", a
  zarovnání Vymazat/Zavřít se sloupcem textu a mazacích křížků v seznamu
  nad nimi
- **Oprava**: tlačítko Zavřít má teď vždy bílý text (v tmavém režimu bylo
  špatně čitelné)
- **Přejmenováno**: "Nastavení čtení" v menu → jen "Nastavení" (nastavuje
  se tam i motiv appky, historie a pokračování po telefonátu, ne jen
  parametry čtení)

## Nové v této verzi (2.06)

- **Zelená šipka** u přehraných položek v Historii
- **Přeskládaný dialog Historie** - Vymazat vše (vlevo) a Zavřít (vpravo)
  jsou teď na jednom řádku dole; Zavřít má barvu čitelnou i v tmavém režimu
- **Rychlejší posuvník** - tažení textu teď zrychlené (~3,5×), ať se dá
  rychle proskrolovat i dlouhý text

## Nové v této verzi (2.05)

- **Skutečně tažitelný posuvník** - dřív jen ukazoval pozici (systémový
  scrollbar), teď kdekoli v pravém pruhu textu dotkneš/táhneš prstem a
  text se rovnou posune na odpovídající místo
- **Oprava zaznamenávání historie** - zápis "přehráno" se teď dělá přímo
  v okamžiku spuštění čtení (klepnutí na Přehrát), ne podmíněně podle
  toho, jestli šlo o čerstvý start nebo pokračování - i částečné
  poslechnutí se teď spolehlivě zaznamená

## Nové v této verzi (2.04)

- **Oprava**: unicode znak elipsy "…" (jeden znak, vypadá jako tři tečky -
  proto kurzor přes něj "skáče" najednou) se teď převádí na obyčejnou
  tečku, takže i řada takových znaků za sebou ("…………") se správně sloučí
  na jednu pauzu místo doslovného "tři tečky, tři tečky..."

## Nové v této verzi (2.03)

- **Oprava**: opakovaná interpunkce oddělená mezerami (". . . . .", časté u
  textů z OCR/naskenovaných dokumentů) se teď taky správně sloučí na jeden
  znak - dřív to zachytávalo jen tečky přímo za sebou bez mezer

## Nové v této verzi (2.02)

- **Nový formát verzování**: dvě desetinná místa (2.02, 2.03...), skok na
  "velký" krok (2.10) jen po výslovné dohodě předem
- **Posuvník na pravé straně textového pole** - jde scrollovat přímým
  tažením prstu za posuvník, ne jen gestem v textu
- **Oprava spodního řádku ovládání** - Přehrát a Stop teď mají šířku
  garantovanou jednoduchým obalovým kontejnerem (ne jen `layout_weight`
  přímo na tlačítku), což by mělo definitivně vyřešit nerovnoměrnou šířku
- **Vylepšená historie**: zaznamenává se každé vložení textu (přes
  Vložit i Odkaz), a pokud byl text následně přehrán, označí se to na
  STEJNÉM záznamu (příznak "▶ přehráno"), místo aby vznikl duplicitní řádek
- **Opakovaná interpunkce**: stejná oprava jako dřív u elipsy teď platí
  obecně pro tečky, vykřičníky i otazníky ("!!!" → "!", "???" → "?")

## Nové v této verzi (2.1)

- **Čtení interpunkce**: elipsy ("...") se sloučí na jednu tečku a
  závorky/uvozovky všech typů se teď z textu odstraní (nahrazují se
  mezerou) - TTS je dřív občas četlo doslova
- **Design**: prostřední řádek ikon je teď vycentrovaný, tlačítka Vložit/
  Vymazat a Přehrát/Stop mají garantovaně stejnou šířku, ikony Uložit/
  Knihovna/Historie mají v tmavém režimu světlejší barvu (dřív byla tmavě
  modrá na tmavém pozadí špatně vidět)
- **Nová Historie čtení** – nové tlačítko v prostředním řádku (vpravo).
  Zaznamenávání je vypnuté ve výchozím stavu, zapíná se v Nastavení (nebo
  přímo v dialogu Historie). Jednotlivé záznamy jde mazat samostatně nebo
  smazat historii celou.
- Poznámka ke schránce: appka nemůže číst "historii" schránky Androidu -
  to není přes veřejné API dostupné žádné aplikaci, jen aktuální jedna
  položka (což už řeší tlačítko Vložit)

## Nové v této verzi (2.0)

- **Balíček přejmenován** na `io.github.marciano.smartreader` (bez
  reálného GitHub jména, jak jsi chtěl)
- **Přeskládané ovládání přehrávání**: Přehrát → zpět/vpřed vedle sebe →
  Stop
- **Přečíslování verze** zpět na 2.0 - další běžné úpravy budou přidávat
  0.1 (2.1, 2.2...), celé číslo jen u zásadní změny

## Nové v této verzi (3.0)

- **Nový balíček appky**: `io.github.marceldohnalcz.smartreader` místo
  placeholderu `com.example.smartreader`
- **Vlastní release podpisový klíč** místo sdíleného debug klíče
- **Appka teď staví jako `release` build** (dřív `debug`) - GitHub Actions
  workflow upraven odpovídajícím způsobem
- Cíl: omezit signály, kvůli kterým Chrome/Android automaticky mazaly
  stažený .apk jako "podezřelý soubor" (viz varování nahoře v tomto
  souboru)

## Nové v této verzi (2.3)

- **Zmenšení appky z ~49 MB zpátky na pár MB** – OCR model se teď stahuje
  přes Google Play Services místo aby byl celý zabalený v APK. Cílem bylo
  hlavně omezit, aby Chrome/Android při stahování appku automaticky mazal
  jako "neznámý soubor s nízkou reputací" (viz níže).
- Release je zpátky jako přímé `.apk`, ne zip

### Poznámka k automatickému mazání staženého APK

Chrome/Android má vestavěné skenování stažených souborů (přes Google Play
Protect) a u aplikací mimo Play Store s "nízkou reputací" je umí sám po
stažení smazat. Zmenšení appky je krok, který by tomu měl pomoct, ale
nejde o garantovanou opravu - jde o heuristiku na straně Googlu, kterou
neovlivníme stoprocentně. Pokud se soubor bude i tak ztrácet, pomůže:

- Dočasně vypnout **Play Protect** sken při stahování: Obchod Play → ikona
  profilu → Play Protect → ozubené kolo → vypnout "Scan apps with Play
  Protect" (po instalaci zase zapnout)
- Stáhnout appku na počítač a nahrát do telefonu kabelem/přes soubor,
  místo stahování rovnou v telefonu
- Dlouhodobě nejspolehlivější řešení je distribuce přes **Google Play
  Internal Testing** (viz uložený návod k publikaci na Google Play) - tam
  tohle mazání vůbec nehrozí, protože appka jde přes důvěryhodný kanál
  Google Play, ne přes libovolné stažení souboru z internetu.

## Nové v této verzi (2.2)

- **Ruční přepínač vzhledu** – v Nastavení jde appce vynutit Světlý nebo
  Tmavý motiv nezávisle na systémovém nastavení telefonu (nebo nechat na
  "Podle systému", jako dřív)

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
`main` se automaticky sestaví appka a zveřejní jako GitHub Release na
stálém odkazu `releases/tag/latest-build`.

**Proč je stažený soubor .zip, ne rovnou .apk:** Chrome/Android má
automatické skenování stažených souborů (souvisí s Google Play Protect) a
u .apk souborů s nízkou "reputací" (typicky malé appky mimo Play Store,
jako je tahle) je umí sám po stažení smazat, aniž by šlo poznat proč. Zip
tomuhle skenování nepodléhá, takže po stažení stačí rozbalit a
nainstalovat `ChytraCteckaTextu.apk`, který je uvnitř.

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
