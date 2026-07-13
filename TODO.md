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

---

Až budeme příště na appce pracovat, stačí napsat "pokračuj v TODO" nebo
rovnou zadat konkrétní bod.
