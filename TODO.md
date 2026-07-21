# TODO

## 1. Interaktivní posuvník - roztažení při podržení prstu

- **Požadavek**: když se dotknu posuvníku a držím prst, má se vizuálně
  roztáhnout (zesílit/zvětšit), a jakmile prst pustím, má se zase vrátit
  na svou normální velikost. Jde o vizuální/dotykovou zpětnou vazbu, ne o
  změnu funkčnosti posouvání.

- **Aktuální stav**: teď appka používá kombinaci dvou věcí -
  1) systémový `android:scrollbars="vertical"` na `etContent` s pevnou
     `scrollbarSize="6dp"` (jen vizuální ukazatel, nejde ho animovat
     jednoduše přes XML atributy za běhu)
  2) samostatná neviditelná vrstva `scrollDragHandle` (32dp pruh), která
     zachytává dotyk a posouvá text (`setupScrollDragHandle()` v
     `MainActivity.kt`)

- **Proč to půjde nejlíp předělat, ne jen doladit**: systémový scrollbar
  nejde za běhu plynule animovat na jinou šířku (je to interní kreslení
  Androidu, ne náš View). Pro "roztažení při podržení" bude nejspíš
  potřeba nahradit současnou kombinaci **jedním vlastním View**
  (custom View s vlastním `onDraw()`), který:
  - sám nakreslí pilulku/pruh reprezentující pozici scrollu (podobně jako
    teď `scrollbar_thumb.xml`, ale kreslený ručně, ne přes systémový
    scrollbar mechanismus)
  - v klidu má šířku jako teď (cca 6dp)
  - na `ACTION_DOWN` animuje (`ValueAnimator` nebo
    `ViewPropertyAnimator`) rozšíření na větší šířku (např. 12-14dp)
  - na `ACTION_UP`/`ACTION_CANCEL` animuje zpět na původní šířku
  - zachová stávající logiku posouvání (`scrollTextByDelta`/
    `scrollTextToTouchRatio` v `MainActivity.kt` lze v podstatě převzít)

- Tenhle vlastní View by nahradil jak systémový scrollbar, tak
  `scrollDragHandle` - sloučilo by se to do jedné komponenty, která umí
  obojí (vykreslení i dotyk).

## 2. Možnost upravit sledovaný profil (jméno/odkaz) + menší tlačítko mazání

- **Požadavek**: teď jde sledovaný profil jen přidat nebo smazat, ne
  upravit (opravit překlep ve jméně, změnit odkaz). Zároveň tlačítko
  Smazat (červený křížek) působí v řádku zbytečně velké/nápadné.

- **Doporučené řešení** (probráno v konverzaci, uživatel souhlasí):
  nahradit tlačítko Smazat v `item_tracked_profile.xml` malou ikonou
  **tří teček (⋮)**, ne tří čar (☰) - čáry mají v Androidu zavedený jiný
  význam (postranní/hlavní menu appky), zatímco tři tečky jsou
  univerzální standard pro "další možnosti u téhle položky" (stejný
  vzor jako `showMoreMenu()` už používá v hlavičce appky - `PopupMenu`).

- **Rozložení řádku po úpravě**:
  - Tlačítko **Otevřít** (ikona odkazu) zůstává jako teď - je to
    nejčastější akce
  - Tři tečky otevřou `PopupMenu` se dvěma položkami: **Upravit** a
    **Smazat**

- **Upravit profil**: znovupoužít `dialog_add_tracked_profile.xml` a
  `showAddTrackedProfileDialog()`, jen:
  - přidat parametr pro existující `TrackedProfile` (null = nový, jinak
    editace)
  - předvyplnit `etProfileName`/`etProfileUrl` z existujícího záznamu
  - titulek/tlačítko změnit na "Upravit profil"/"Uložit změny" (nové
    stringy potřeba přidat do všech 9 jazyků)
  - přidat `TrackedProfilesStore.updateProfile(context, id, name, url)`
    - podobně jako `addProfile`, jen upraví existující záznam podle id
    místo přidání nového

- Menu s Upravit/Smazat potřebuje i lokalizované texty ("Upravit",
  "Smazat") - zkontrolovat, jestli už `btn_delete`/podobný klíč
  neexistuje a dá se znovupoužít, než vytvářet duplicitní překlady.

---

Až budeme příště na appce pracovat, stačí napsat "pokračuj v TODO" nebo
rovnou zadat konkrétní bod.
