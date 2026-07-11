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

- **Zpětná vazba**: appka prý čte dlouhou řadu teček doslova (např. "tři
  tečky") místo aby je jen sloučila do jedné pauzy.
- **Důležitá poznámka**: tohle by měl řešit fix z verze 2.02
  (`REPEATED_PUNCTUATION_PATTERN` v `TextPreprocessor.kt`), který slučuje
  JAKKOLIV dlouhou řadu stejné interpunkce (`.`, `!`, `?`) na jeden jediný
  znak - tedy i 20 teček za sebou by se mělo zredukovat na jednu tečku, ne
  jen konkrétně tři.
- **První krok příště**: ověřit, jestli hlášení vzniklo na verzi PŘED
  2.02 (pak stačí potvrdit, že už je to v pořádku), nebo jestli se
  problém reálně objevuje i po instalaci 2.02 (pak jde o skutečný bug v
  regexu/pipeline, který bude potřeba dohledat samostatně - např. zda se
  `simplifyRepeatedPunctuation` v `TextPreprocessor.Options` skutečně
  aplikuje na text z OCR/staženého odkazu stejně jako na ručně psaný text).

---

Až budeme na dalším update pracovat, stačí říct "pokračuj v TODO".
