# TODO

## 1. Textové smajlíky (:-(, :), ;-) apod.) se čtou doslova po znacích

- **Požadavek**: text jako ":-(" appka čte doslova "dvojtečka spojovník"
  místo toho, aby smajlík jako celek prostě přeskočila (jako to appka
  dělá u opravdových Unicode emoji - viz EMOJI_PATTERN v
  TextPreprocessor.kt).
- **Příčina**: emotikon složený z běžných textových znaků (:, ;, -, ), (,
  D, P...) appka nepozná jako JEDEN celek - každý znak zvlášť buď žádné
  pravidlo nezasáhne (:, obyčejná osamocená pomlčka), nebo ho zasáhne
  jiné pravidlo nekompletně (závorka se smaže na mezeru, ale zbytek
  zůstane).
- **Jak na to**: přidat nový regex pattern, který pozná běžné textové
  emotikony jako CELEK (typicky tvar: [znak očí :;=8] + volitelný nos
  [-o*'] + znak úst [)(DPp/\|3oO] apod., třeba ":)", ":-)", ":(", ":-(",
  ";)", ":D", ":P", "=)", "xD"...) a smaže je celé stejně jako
  EMOJI_PATTERN - ideálně PŘED ostatními pravidly pro jednotlivé znaky
  (závorky, pomlčky), ať se nestihnou "rozbít" na kousky dřív, než je
  appka rozpozná jako smajlík.
- Pozor na false positivy - nechtít omylem smazat legitimní použití
  dvojtečky/závorky v běžném textu (např. "Pozor: (důležité) upozornění").
  Bude potřeba pattern navrhnout dost specificky (jen typické kombinace
  krátké 2-3 znakové), otestovat na reálných příkladech před nasazením.

## 2. "(...)" (elipsa v závorce, typicky značí vynechaný text) se čte jako "tečka"

- **Požadavek**: "(...)" appka čte doslova jako "tečka" - i po sloučení
  opakovaných teček na jednu (REPEATED_PUNCTUATION_PATTERN) a smazání
  závorek (BRACKETS_AND_QUOTES_PATTERN) zbyde osamocená tečka obklopená
  mezerami, kterou TTS zjevně čte jako samostatné slovo "tečka" místo
  jako přirozenou pauzu/interpunkci.
- **Možné řešení**: "(...)" je v psaném textu skoro vždy editorská
  značka pro vynechaný text (citace, zkrácení) - dává smysl ji rozpoznat
  jako celek a smazat úplně (podobně jako značku citace ">" na začátku
  řádku), ne jen postupně odbourávat závorky/tečky zvlášť. Přidat
  pattern typu `\([.\s]*\.{2,}[.\s]*\)` (závorka, tři a víc teček
  uvnitř, možné mezery, zavírací závorka) → smazat celé, PŘED
  obecným pravidlem pro závorky.
- Případně obecněji prověřit, jestli by osamocená tečka (mezera-tečka-
  mezera, nikde jinde ve větě) neměla appka radši mazat úplně místo
  spoléhání na to, že ji TTS správně vyhodnotí jako pauzu - riziko je,
  že by to mohlo zasáhnout i legitimní krátké věty typu "Ano." na
  vlastním řádku, takže radši cíleně řešit jen kombinaci se závorkami.

---

## Poznámka: rozdělaná práce na stažitelných hlasech (Piper/sherpa-onnx)

Rozjeté, ale NEDOKONČENÉ - viz konverzace pro detaily. Hotovo: Gradle
závislosti (sherpa-onnx AAR + commons-compress), DownloadableVoices.kt,
PiperVoiceStore.kt (stahování/rozbalování), PiperVoiceEngine.kt (syntéza
+ přehrání), položka v menu ⋮, layouty dialogu. CHYBÍ: propojovací
funkce v MainActivity.kt (showDownloadableVoicesDialog - stahování
s ukazatelem postupu, přehrání ukázky, smazání), první zkušební build
přes GitHub Actions (nikdy ještě nebylo ověřeno, že se to zkompiluje -
nativní knihovny nejde ověřit jinak). Zatím jen stažení + ukázka, NE
plné zapojení do hlavního tlačítka Přehrát (to je další, samostatný krok).

---

Až budeme příště na appce pracovat, stačí napsat "pokračuj v TODO" nebo
rovnou zadat konkrétní bod.
