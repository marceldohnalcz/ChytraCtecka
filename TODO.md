# TODO

## 1. Tlačítka Zrušit/Potvrdit jsou v systémových dialozích moc blízko sebe

**Týká se jen hromadného mazání ("Vymazat vše") - mazání JEDNÉ položky
necháváme, jak je teď, beze změny.**

- **Zpětná vazba (se screenshotem)**: v dialogu "Vymazat celou historii?"
  jsou tlačítka ZRUŠIT a ANO, VYMAZAT hned vedle sebe vpravo dole (výchozí
  Android/Material rozložení) - snadno se dá omylem trefit špatné.
  Požadavek: "Zrušit" zarovnat úplně nalevo, ať jsou tlačítka od sebe dál.
- **Technický důvod**: `AlertDialog.Builder().setPositiveButton()` /
  `.setNegativeButton()` vždy vykreslí tlačítka vedle sebe v systémovém
  panelu (nejde to rozestavit jinak přes tenhle standardní API). Aby šlo
  "Zrušit" dát doleva a potvrzovací tlačítko doprava (s velkou mezerou
  mezi nimi), je potřeba udělat **vlastní view** pro tenhle dialog - stejný
  princip, jaký už používají `dialog_history.xml`/`dialog_library.xml`
  (`.setView(...)` + `.create()` + ruční `setOnClickListener` na vlastní
  tlačítka), místo `.setPositiveButton()/.setNegativeButton()`.

- **Místo v `MainActivity.kt`, které je potřeba opravit:**

  - **`showConfirmDialog()`** - sdílená funkce pro "Vymazat celou
    knihovnu?" i "Vymazat celou historii?" - přesně tohle je na
    screenshotu. Je to sdílená funkce, takže oprava tady vyřeší obě
    místa (knihovnu i historii) najednou.

- **Volitelně, ne prioritní**: `showLinkInputDialog()` ("Stáhnout" /
  "Zrušit" u zadávání odkazu) má stejnou vizuální těsnost tlačítek, ale
  nejde o mazání/nevratnou akci, takže riziko omylem kliknutí tam nevadí
  tolik. Stálo by za zvážení sjednotit vzhled i tady, ale není to nutné.

- **Beze změny (na přání)**:
  - Mazání JEDNÉ položky v knihovně ("Smazat text?" / "Smazat" +
    "Zrušit") - necháváme jak je.
  - Mazání JEDNÉ položky v historii (bez potvrzení, smaže se rovnou) -
    necháváme jak je, žádné potvrzení se nepřidává.

## 2. Konflikt ručního tažení posuvníku s auto-scrollem při čtení

- **Požadavek**: když appka čte a uživatel se dotkne/drží posuvník
  (`scrollDragHandle`), automatický posun textu (co běží při čtení) se má
  okamžitě zastavit a nechat plně vládnout ruční tažení. Až uživatel
  posuvník pustí, auto-scroll má zase normálně pokračovat.
- **Aktuální stav**: `autoScrollToPosition()` v `MainActivity.kt` (volaná
  z `highlightRange()` při každém novém přečteném úseku) běží nezávisle
  na `setupScrollDragHandle()` - obě se teď mohou přetahovat o scroll
  pozici zároveň.
- **Návrh implementace**: přidat boolean flag (např.
  `isUserDraggingScroll`), nastavit `true` v `ACTION_DOWN` dotykového
  listeneru na `scrollDragHandle`, `false` v `ACTION_UP`/`ACTION_CANCEL`.
  `autoScrollToPosition()` na začátku zkontroluje tenhle flag a pokud je
  `true`, rovnou skončí (nic neposouvá), dokud uživatel drží prst na
  posuvníku.

## 3. Zastavit (ne jen ztišit) jiné aplikace typu YouTube při čtení

- **Požadavek**: když appka spustí čtení, přehrávání v jiných appkách
  (YouTube, Spotify apod.) se má úplně **zastavit** (pauza), ne jen
  ztišit jako teď (ducking). Po dočtení textu se má ta druhá appka sama
  pustit zpátky.
- **Důležité - tohle NAHRADÍ současné ducking chování, nebude fungovat
  vedle sebe**: teď appka žádá o `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` v
  `ReadingService.requestAudioFocus()`, což ostatní appky bere jako
  "stačí mě ztišit". Pro plné zastavení je potřeba žádat o
  `AUDIOFOCUS_GAIN_TRANSIENT` (bez MAY_DUCK) - tohle Android bere jako
  signál "druhá appka by se měla pozastavit", což slušně napsané appky
  (YouTube, Spotify) respektují.
- **Auto-pokračování druhé appky po dočtení**: když appka dočte a zavolá
  `abandonAudioFocus()` (v `onDone` callbacku), systém pošle ostatním
  appkám `AUDIOFOCUS_GAIN` zpátky - většina appek (YouTube, Spotify) si
  samy pamatují, že hrály, a po obdržení zpět focusu se samy pustí. Tohle
  už je jejich vlastní chování, appka to jen musí korektně "pustit" přes
  `abandonAudioFocus()`, což už dělá.
- **K rozhodnutí předem**: chceš ducking úplně zahodit, nebo aby šlo
  přepínat mezi "ztišit" / "zastavit" jako volba v Nastavení? (Výchozí
  návrh bez dalšího zadání: prostě nahradit MAY_DUCK za obyčejný
  TRANSIENT, bez přepínače - jednodušší, ale méně flexibilní.)

---

Až budeme příště na appce pracovat, stačí napsat "pokračuj v TODO" nebo
rovnou zadat konkrétní bod.
