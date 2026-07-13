# TODO

## 1. Tlačítka Zrušit/Potvrdit jsou v systémových dialozích moc blízko sebe

- **Zpětná vazba (se screenshotem)**: v dialogu "Vymazat celou historii?"
  jsou tlačítka ZRUŠIT a ANO, VYMAZAT hned vedle sebe vpravo dole (výchozí
  Android/Material rozložení) - snadno se dá omylem trefit špatné.
  Požadavek: "Zrušit" zarovnat úplně nalevo, ať jsou tlačítka od sebe dál.
- **Technický důvod**: `AlertDialog.Builder().setPositiveButton()` /
  `.setNegativeButton()` vždy vykreslí tlačítka vedle sebe v systémovém
  panelu (nejde to rozestavit jinak přes tenhle standardní API). Aby šlo
  "Zrušit" dát doleva a potvrzovací tlačítko doprava (s velkou mezerou
  mezi nimi), je potřeba udělat **vlastní view** pro tyhle dialogy - stejný
  princip, jaký už používají `dialog_history.xml`/`dialog_library.xml`
  (`.setView(...)` + `.create()` + ruční `setOnClickListener` na vlastní
  tlačítka), místo `.setPositiveButton()/.setNegativeButton()`.

- **Místa v `MainActivity.kt`, která tenhle vzor používají a je potřeba
  je zkontrolovat/předělat** (prošel jsem celý soubor, tohle je kompletní
  seznam):

  1. **`onDeleteClick` v `showLibraryDialog()`** (~řádek 495) - "Smazat
     text?" / "Smazat" + "Zrušit" - potvrzení při mazání JEDNÉ položky
     z knihovny. Stejný problém jako na screenshotu.
  2. **`showConfirmDialog()`** (~řádek 696) - sdílená funkce pro "Vymazat
     celou knihovnu?" i "Vymazat celou historii?" - přesně tohle je na
     screenshotu. Protože je to sdílená funkce, oprava tady vyřeší OBĚ
     místa najednou.
  3. **`showLinkInputDialog()`** (~řádek 970) - "Stáhnout" / "Zrušit" u
     zadávání odkazu. Není to mazání/nevratná akce, ale stejná vizuální
     těsnost tlačítek - stálo by za to sjednotit vzhled i tady.

- **Místa, která NEJSOU tímhle postižená** (jen jedno tlačítko, není co
  splést):
  - `showAboutDialog()` - jen "Zavřít"
  - `showHelpDialog()` - jen "Zavřít"
  - `showSettingsDialog()` - jen "Hotovo"

- **Bonus zjištění při procházení kódu**: mazání JEDNOTLIVÉ položky v
  Historii (`onDeleteClick` u `HistoryAdapter` v `showHistoryDialog()`)
  nemá ŽÁDNÉ potvrzení - smaže se rovnou po klepnutí na červený křížek.
  U Knihovny potvrzení je. Nekonzistentní - stálo by za to při opravě
  zvážit, jestli přidat potvrzení i tam (asi ano, kvůli konzistenci a
  ochraně proti překlepnutí, viz historie s omylem smazanou knihovnou).

---

Až budeme příště na appce pracovat, stačí napsat "pokračuj v TODO" nebo
rovnou zadat konkrétní bod.
