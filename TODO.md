# TODO

## 1. Přesunout "Přidat profil" na řádek s "Zavřít" (dialog Sledované profily)

- **Požadavek**: tlačítko "Přidat profil" (`btnAddTrackedProfile`) se má
  přesunout níž, na stejný řádek jako "Zavřít" (`btnCloseTrackedProfiles`),
  a to nalevo od něj.
- **Aktuální stav** (`dialog_tracked_profiles.xml`): "Přidat profil" je
  teď samostatné tlačítko přes celou šířku (`match_parent`) na vlastním
  řádku, "Zavřít" je pod ním na dalším řádku úplně vpravo (s prázdným
  `View` jako vyplňovačem před ním).
- **Jak na to**: stejný vzor, jaký appka už používá v dialogu Historie
  (`dialog_history.xml`) - "Vymazat vše" vlevo a "Zavřít" vpravo na
  jednom řádku. Tady by to bylo obdobně: "Přidat profil" vlevo (asi
  `wrap_content` místo `match_parent`, ať se vejde vedle druhého
  tlačítka), `View` jako vyplňovač uprostřed, "Zavřít" vpravo - vše v
  jednom `LinearLayout` řádku místo dvou oddělených.
- Pozor na barvu/styl - "Přidat profil" má teď `app:icon` (ikonu
  postavičky) a je široké přes celý dialog; při zúžení na `wrap_content`
  zkontrolovat, že text s ikonou nebude action-button příliš stěsnaný.

---

Až budeme příště na appce pracovat, stačí napsat "pokračuj v TODO" nebo
rovnou zadat konkrétní bod.
