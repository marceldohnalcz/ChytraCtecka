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

---

Až budeme na dalším update pracovat, stačí říct "pokračuj v TODO" nebo
rovnou zadat konkrétní z těchto dvou bodů.
