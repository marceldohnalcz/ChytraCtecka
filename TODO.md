# TODO - naplánováno na příští update

Tyhle dva úkoly čekají na příští update appky (zadáno 11. 7. 2026, zatím
neimplementováno):

## 1. Změna formátu verzování na dvě desetinná místa

- Dosavadní formát: `2.0`, `2.1`, `2.2` ...
- Nový formát: `2.02`, `2.03`, `2.04` ... (dvě desetinná místa)
- Větší/zásadní update: skok např. na `2.10` (ne `3.0`)
- **Vždy po dohodě předem** - žádná verze se nepřečísluje na "velký" krok
  (X.10, X.20...) bez explicitního souhlasu, ani při větší změně.

## 2. Posuvník (scrollbar) na pravé straně textového pole

- Přidat viditelný svislý jezdec/posuvník na pravou stranu `etContent`
- Uživatel bude moct scrollovat nahoru/dolů přímým tažením prstu po
  posuvníku, ne jen gestem uvnitř textu
- Pravděpodobná implementace: `android:scrollbars="vertical"` +
  `android:scrollbarStyle="insideOverlay"` (nebo `outsideOverlay`) na
  EditText, případně `android:fadeScrollbars="false"`, ať je viditelný
  trvale, ne jen při scrollování. Otestovat, jestli to na dané verzi
  Androidu funguje dobře v kombinaci s auto-scrollem při čtení
  (viz `autoScrollToPosition` v MainActivity.kt).

## 3. Oprava spodního řádku (Přehrát / zpět / vpřed / Stop)

- **Zpětná vazba**: předchozí pokus o opravu (přidání `android:minWidth="0dp"`
  na obě tlačítka) problém nevyřešil - zelené tlačítko Přehrát je pořád
  užší než tlačítko Stop vpravo
- Řádek navíc není vycentrovaný
- Bude potřeba jít hlouběji než jen minWidth - zkontrolovat, jestli
  MaterialButton nemá i jiné defaultní inset/padding hodnoty, které
  narušují rovnoměrné rozdělení `layout_weight`, případně zkusit úplně
  jiný přístup k tomuhle řádku (např. bez MaterialButton stylu, nebo s
  explicitním `app:iconPadding`/`insetLeft`/`insetRight`)

## 4. Vylepšit zaznamenávání do historie

- Zaznamenávat do historie **každý vložený text** (přes tlačítko Vložit
  i další cesty vkládání), ne jen ve chvíli, kdy se spustí čtení
- Pokud byl daný text následně i přehrán, poznamenat to k **tomu samému**
  již existujícímu záznamu v historii (přidat příznak "přehráno" apod.) -
  ne vytvářet duplicitní druhý záznam pro stejný text

## 5. Opakovaná interpunkce (např. "!!!") se čte doslova

- Stejný problém jako dřív opravená elipsa ("..." → "."), teď u
  vykřičníků: víc vykřičníků za sebou ("!!!") TTS čte doslova jako
  "vykřičník vykřičník" místo přirozeného důrazu
- Řešení podle stejného vzoru jako `ELLIPSIS_PATTERN` v
  `TextPreprocessor.kt` - sloučit 2+ opakování na jeden znak (`!{2,}` → `!`)
- Zkontrolovat i další opakovatelnou interpunkci se stejným rizikem
  (např. víc otazníků "???", kombinace "?!" apod.)

---

Až budeme na dalším update pracovat, stačí říct "pokračuj v TODO" nebo
rovnou zadat konkrétní z těchto bodů.
