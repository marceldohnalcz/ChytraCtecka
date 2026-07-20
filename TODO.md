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

---

Až budeme příště na appce pracovat, stačí napsat "pokračuj v TODO" nebo
rovnou zadat konkrétní bod.
