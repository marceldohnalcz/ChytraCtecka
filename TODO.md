# TODO

## 1. Posuvník vpravo od textu není chytatelný/tažitelný

- **Zpětná vazba**: posuvník (scrollbar) na pravé straně textového pole je
  sice vidět, ale nejde ho chytit prstem a přetažením posouvat text - jen
  ukazuje pozici, neumí ovládat scroll.
- **Příčina**: standardní `android:scrollbars="vertical"` u EditTextu je
  jen vizuální indikátor, Android ho u běžného TextView/EditText neumí
  udělat interaktivním/tažitelným - to je jiný mechanismus než třeba
  "fast scroller" u RecyclerView.
- **Řešení pro příště**: udělat vlastní draggable overlay - průhledný pruh
  přes pravý okraj textového pole, který:
  - zachytává `onTouchListener` (ACTION_DOWN/ACTION_MOVE) jen v oblasti
    tenkého pruhu vpravo
  - podle pozice prstu v pruhu přepočítá odpovídající scroll pozici a
    zavolá `editText.scrollTo(0, vypočtenéY)`
  - vizuálně by šlo znovu použít `scrollbar_thumb.xml`, jen by nešlo o
    systémový scrollbar, ale o vlastní View/kreslený indikátor navázaný
    na `editText.scrollY` / `editText.layout.height`
- Bude potřeba dát pozor na souběh s auto-scrollem při čtení
  (`autoScrollToPosition` v MainActivity.kt) - manuální tažení uživatelem
  by nemělo být přebíjeno auto-scrollem v okamžiku, kdy appka zrovna čte.

## 2. Ověřit: dlouhá řada teček (".....................") se čte doslova

**VYŘEŠENO ve verzi 2.03** - problém byl v tom, že tečky oddělené mezerami
(". . . . .", časté u OCR textů) regex nezachytil, jen tečky přímo za
sebou. Opraveno rozšířením patternu o volitelné mezery mezi opakováními.

---

Až budeme na dalším update pracovat, stačí říct "pokračuj v TODO".
