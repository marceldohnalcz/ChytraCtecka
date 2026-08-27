package io.github.marciano.smartreader

import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Připraví text pro TTS – odstraňuje odkazy, čísla účtů, podtržítka, emoji a
 * další prvky, které nedávají smysl při poslechu (např. "#", "*", opakovaná
 * interpunkce). Jednotlivá pravidla lze podle potřeby vypnout přes [Options].
 *
 * DŮLEŽITÉ: appka NEUPRAVUJE viditelný text - [clean] vrací kromě vyčištěného
 * textu (pro TTS) i [CleanResult.originalPositions], což je mapování "pozice
 * ve vyčištěném textu -> odpovídající pozice v PŮVODNÍM (viditelném) textu".
 * Díky tomu appka pozná, kterou část PŮVODNÍHO textu právě čte, i když TTS
 * dostává jinak upravenou verzi - viditelný text tak zůstává přesně takový,
 * jaký ho uživatel vložil.
 *
 * Rozepisování zkratek (viz [ABBREVIATIONS_BY_LANGUAGE]) je jazykově
 * specifické - appka si podle aktuálního jazyka zařízení ([Locale.getDefault])
 * vybere odpovídající slovník. Pro jazyky, které nemáme podchycené, se tenhle
 * krok jednoduše přeskočí (zbytek čištění - odkazy, čísla, interpunkce -
 * funguje pro všechny jazyky stejně, není jazykově specifický).
 */
object TextPreprocessor {

    /** [text] = to, co se skutečně přečte. [originalPositions] má stejnou délku jako
     *  [text] a pro každý jeho znak udává index odpovídajícího znaku v PŮVODNÍM textu. */
    data class CleanResult(val text: String, val originalPositions: IntArray)

    private val URL_PATTERN: Pattern = Pattern.compile(
        "(https?://\\S+)|(www\\.\\S+)", Pattern.CASE_INSENSITIVE
    )

    // Český formát čísla účtu: [předčíslí-]číslo/kód banky
    private val BANK_ACCOUNT_PATTERN: Pattern = Pattern.compile(
        "\\b\\d{1,6}-?\\d{2,10}/\\d{4}\\b"
    )

    // IBAN (např. CZ65 0800...)
    private val IBAN_PATTERN: Pattern = Pattern.compile(
        "\\b[A-Z]{2}\\d{2}[ ]?(?:\\d[ ]?){10,26}\\b"
    )

    // Dlouhé čistě číselné sekvence (7+ číslic) - telefony, variabilní symboly apod.
    private val LONG_DIGIT_PATTERN: Pattern = Pattern.compile("\\b\\d{7,}\\b")

    // Zápis velkých čísel s tečkou jako oddělovačem tisíců, např. "220.000" nebo
    // "1.234.567" - běžné ve většině evropských jazyků (cs, de, es, fr, it, pt,
    // pl, ru). Bez tohoto by TTS četlo číslice jednu po druhé místo "220 tisíc".
    private val THOUSANDS_SEPARATOR_PATTERN: Pattern = Pattern.compile("\\b\\d{1,3}(?:\\.\\d{3})+\\b")

    // Pomlčka přímo mezi dvěma číslicemi (bez mezer) - typicky číslo jednací,
    // spisová značka, rozsah stránek nebo telefon ("2024-67", "12-15",
    // "777-123-456"), ne matematické minus. Skutečné odečítání/záporné číslo se
    // v běžném textu píše buď s mezerami kolem pomlčky ("5 - 3"), nebo má
    // pomlčku přímo před číslicí bez PŘEDCHOZÍ číslice ("-5 stupňů") - ani jeden
    // z těchto případů tenhle vzor nezasáhne, TTS by jinak četlo "mínus" i tam,
    // kde jde jen o oddělovač.
    private val DASH_BETWEEN_DIGITS_PATTERN: Pattern = Pattern.compile("(?<=\\d)-(?=\\d)")

    // Dvě a víc pomlček za sebou (s mezerami mezi nimi nebo bez) - typicky
    // vizuální oddělovač sekcí ("---" na vlastním řádku), ne text ke čtení. Na
    // rozdíl od opakovaných teček/vykřičníků se tu nenechává ani jedna pomlčka,
    // protože samotná pomlčka nemá pro TTS žádný přirozený prozodický význam a
    // četla by se doslova jako "spojovník". Jedna pomlčka mezi slovy (spojení
    // slov, e-mail apod.) zůstává nedotčená.
    private val REPEATED_DASH_PATTERN: Pattern = Pattern.compile("-(?:\\s*-){1,}")

    // Nejběžnější emoji bloky
    private val EMOJI_PATTERN: Pattern = Pattern.compile(
        "[\\uD83C\\uDF00-\\uD83D\\uDDFF]|[\\uD83D\\uDE00-\\uD83D\\uDE4F]|" +
            "[\\uD83D\\uDE80-\\uD83D\\uDEFF]|[\\u2600-\\u27BF]|[\\u2B00-\\u2BFF]|" +
            "[\\uD83E\\uDD00-\\uD83E\\uDDFF]"
    )

    // Křížek se má smazat vždy, ne jen když je nalepený na slovo (hashtag) -
    // TTS by ho jinak četlo jako "křížek" i samostatně nebo v nadpisu (# Nadpis).
    private val HASHTAG_SYMBOL: Pattern = Pattern.compile("#")
    private val MENTION_SYMBOL: Pattern = Pattern.compile("@(?=\\S)")
    // Markdown zvýraznění (*kurzíva*, **tučně**, i odrážky "* položka") - bez
    // téhle úpravy TTS čte hvězdičky doslova ("hvězdička hvězdička").
    private val ASTERISK_SYMBOL: Pattern = Pattern.compile("\\*+")

    // Svislá čára - skoro vždy ohraničuje buňky markdown tabulky
    // ("| Sloupec 1 | Sloupec 2 |"), TTS by ji jinak četla jako "svislá čára".
    // Odstraňuje se PŘED pravidlem pro opakované pomlčky výše, ať po
    // odstranění čar zbydou z oddělovacího řádku tabulky ("|---|---|") jen
    // pomlčky, které to pravidlo správně smaže taky.
    private val PIPE_SYMBOL: Pattern = Pattern.compile("\\|")
    // Zpětné apostrofy - markdown kód (`kód` i celé bloky kódu ```...```).
    private val BACKTICK_SYMBOL: Pattern = Pattern.compile("`+")
    // Vlnovky - markdown přeškrtnutý text (~~takhle~~).
    private val TILDE_SYMBOL: Pattern = Pattern.compile("~+")
    // Markdown citace (řádek začínající ">") - jen na začátku řádku, ať se
    // nedotkne skutečného "větší než" uprostřed věty ("5 > 3").
    private val BLOCKQUOTE_MARKER_PATTERN: Pattern = Pattern.compile("^>\\s*", Pattern.MULTILINE)

    // Unicode znak elipsy "…" (U+2026) - jeden znak, co vypadá jako tři tečky.
    private val ELLIPSIS_CHAR_PATTERN: Pattern = Pattern.compile("\u2026")
    // Podtržítko nahrazujeme mezerou (např. nazvy_souboru_takhle).
    private val UNDERSCORE_PATTERN: Pattern = Pattern.compile("_")

    // Opakovaná interpunkce za sebou (elipsa "...", "!!!", "???") - některé TTS
    // enginy je čtou doslova jako "tečka tečka tečka" místo přirozené pauzy.
    // Zachytí i variantu oddělenou mezerami (". . . . ."), což je časté např.
    // u textů z OCR nebo naskenovaných dokumentů (tečkované "vodicí čáry").
    private val REPEATED_PUNCTUATION_PATTERN: Pattern = Pattern.compile("([.!?])(?:\\s*\\1){1,}")

    // "(...)" - elipsa v závorce (tři tečky NEBO unicode znak elipsy "…"), v
    // psaném textu skoro vždy editorská značka pro vynechaný text (citace,
    // zkrácení), ne obsah k přečtení. Bez tohohle by po smazání závorek zbyla
    // osamocená tečka, kterou TTS čte doslova jako slovo "tečka". Musí běžet
    // úplně na začátku pipeline - PŘED sloučením opakované interpunkce (jinak
    // by "..." uvnitř závorky stihlo zmizet na jednu tečku dřív, než by ho
    // tohle pravidlo poznalo) i před obecným pravidlem pro závorky.
    private val PAREN_ELLIPSIS_PATTERN: Pattern = Pattern.compile(
        "\\([\\s.\u2026]*(?:\\.{2,}|\u2026)[\\s.\u2026]*\\)"
    )

    // Závorky (kulaté, hranaté, složené) a uvozovky všech běžných typů - TTS je
    // někdy čte doslova ("uvozovky", "levá závorka"). Nahrazujeme mezerou, aby se
    // slova kolem nespojily, ale zůstal zachovaný přirozený tok věty.
    private val BRACKETS_AND_QUOTES_PATTERN: Pattern = Pattern.compile(
        "[()\\[\\]{}\"„“«»‘’']"
    )

    // Textové smajlíky (:-(, :), ;-), :D, xD...) - bez tohohle appka čte
    // jednotlivé znaky doslova ("dvojtečka spojovník"). Záměrně BEZ číslice 8
    // v očích (i když "8)" jako smajlík s brýlemi existuje) - kolidovalo by to
    // s běžnými číslovanými seznamy typu "1) ... 8) ...". Ohraničení
    // (?<![\w]) / (?![\w]) hlídá, ať se nechytí uprostřed jiného slova/čísla
    // (např. čas "8:00" nebo dvojtečka jako uvod repliky "Řekl: ...").
    private val EMOTICON_PATTERN: Pattern = Pattern.compile(
        "(?<![\\w])[:;=xX][-o^']?(?:\\)+|\\(+|D+|d+|P+|p+|3|o|O|s|S|/|\\\\|\\|)(?![\\w])"
    )

    private val MULTI_SPACE_PATTERN: Pattern = Pattern.compile("[ \\t]{2,}")
    private val MULTI_NEWLINE_PATTERN: Pattern = Pattern.compile("\\n{3,}")

    /**
     * Slovníky zkratek podle jazyka (klíč = ISO kód jazyka, stejný jako
     * Locale.getDefault().language). Každý klíč ve vnitřní mapě obsahuje
     * zkratku PŘESNĚ tak, jak se píše (včetně vlastních teček) - díky tomu
     * jde stejným mechanismem zapsat jak jednoduché "atd." (jedna tečka na
     * konci), tak třeba anglické "e.g." (tečka za každým písmenem).
     *
     * Zkratky byly vybírány konzervativně - jen ty nejběžnější a
     * jednoznačné, ať nehrozí, že se omylem rozepíše něco, co zkratkou
     * vůbec nebylo (proto např. nejsou zahrnuté jednopísmenné zkratky typu
     * "S." nebo tituly jako "Dr.", kde je riziko chyby vyšší a TTS si s
     * nimi navíc obvykle poradí samo).
     */
    private val ABBREVIATIONS_BY_LANGUAGE: Map<String, Map<String, String>> = mapOf(
        "cs" to mapOf(
            "např." to "například",
            "tzn." to "to znamená",
            "atd." to "a tak dále",
            "atp." to "a tak podobně",
            "apod." to "a podobně",
            "tj." to "to jest",
            "resp." to "respektive",
            "popř." to "popřípadě",
            "mj." to "mimo jiné",
            "tzv." to "takzvaný",
            "str." to "strana"
        ),
        "en" to mapOf(
            "e.g." to "for example",
            "i.e." to "that is",
            "etc." to "and so on",
            "approx." to "approximately",
            "vs." to "versus",
            "dept." to "department",
            "govt." to "government"
        ),
        "de" to mapOf(
            "z.B." to "zum Beispiel",
            "d.h." to "das heißt",
            "usw." to "und so weiter",
            "bzw." to "beziehungsweise",
            "ca." to "circa",
            "ggf." to "gegebenenfalls",
            "z.T." to "zum Teil",
            "Nr." to "Nummer"
        ),
        "es" to mapOf(
            "p.ej." to "por ejemplo",
            "etc." to "etcétera",
            "aprox." to "aproximadamente",
            "núm." to "número",
            "pág." to "página"
        ),
        "fr" to mapOf(
            "p.ex." to "par exemple",
            "c.-à-d." to "c'est-à-dire",
            "etc." to "et cetera",
            "env." to "environ",
            "n°" to "numéro"
        ),
        "it" to mapOf(
            "ad es." to "ad esempio",
            "ecc." to "eccetera",
            "pag." to "pagina",
            "n." to "numero"
        ),
        "pt" to mapOf(
            "p.ex." to "por exemplo",
            "etc." to "etcétera",
            "aprox." to "aproximadamente",
            "pág." to "página",
            "n.º" to "número"
        ),
        "pl" to mapOf(
            "np." to "na przykład",
            "tzn." to "to znaczy",
            "itd." to "i tak dalej",
            "itp." to "i tym podobne",
            "ok." to "około",
            "str." to "strona"
        ),
        "ru" to mapOf(
            "напр." to "например",
            "т.е." to "то есть",
            "и т.д." to "и так далее",
            "и т.п." to "и тому подобное",
            "прибл." to "приблизительно",
            "стр." to "страница"
        )
    )

    data class Options(
        val skipUrls: Boolean = true,
        val skipBankAccounts: Boolean = true,
        val skipLongNumbers: Boolean = true,
        val normalizeThousands: Boolean = true,
        val normalizeDashBetweenDigits: Boolean = true,
        val stripRepeatedDashes: Boolean = true,
        val expandAbbreviations: Boolean = true,
        val simplifyRepeatedPunctuation: Boolean = true,
        val stripBracketsAndQuotes: Boolean = true,
        val stripParenEllipsis: Boolean = true,
        val stripEmoticons: Boolean = true,
        val stripUnderscores: Boolean = true,
        val stripEmoji: Boolean = true,
        val stripHashSymbol: Boolean = true,
        val stripMentionSymbol: Boolean = true,
        val stripAsteriskSymbol: Boolean = true,
        val stripPipeSymbol: Boolean = true,
        val stripBacktickSymbol: Boolean = true,
        val stripTildeSymbol: Boolean = true,
        val stripBlockquoteMarker: Boolean = true
    )

    fun clean(input: String, options: Options = Options()): CleanResult {
        var text = input
        var positions = IntArray(input.length) { it }

        fun apply(pattern: Pattern, replacer: (Matcher) -> String) {
            val (t, p) = replaceTracked(text, positions, pattern, replacer)
            text = t
            positions = p
        }

        if (options.expandAbbreviations) {
            val (t, p) = expandAbbreviationsTracked(text, positions)
            text = t
            positions = p
        }
        // Elipsa v závorce musí být úplně první krok - jinak by ji jiná
        // pravidla (sloučení opakované interpunkce, mazání závorek) stihla
        // "rozebrat" na kousky dřív, než by appka poznala, že jde o jeden celek.
        if (options.stripParenEllipsis) apply(PAREN_ELLIPSIS_PATTERN) { "" }
        if (options.skipUrls) apply(URL_PATTERN) { " " }
        if (options.skipBankAccounts) {
            apply(IBAN_PATTERN) { " " }
            apply(BANK_ACCOUNT_PATTERN) { " " }
        }
        // Nejdřív odfiltrovat telefony/variabilní symboly (dokud jsou to "čisté" dlouhé
        // sekvence číslic), teprve pak sloučit tečkované tisíce - jinak by se velké
        // částky jako "1.234.567" po sloučení mylně chytily do stejného filtru.
        if (options.skipLongNumbers) apply(LONG_DIGIT_PATTERN) { " " }
        if (options.normalizeThousands) apply(THOUSANDS_SEPARATOR_PATTERN) { m -> m.group().replace(".", "") }
        // Markdown artefakty (tabulky, kód, citace) se čistí PŘED sloučením
        // opakovaných pomlček, ať po odstranění čar zbydou z oddělovacího
        // řádku tabulky ("|---|---|") jen pomlčky, které se pak správně smažou.
        if (options.stripPipeSymbol) apply(PIPE_SYMBOL) { "" }
        if (options.stripBacktickSymbol) apply(BACKTICK_SYMBOL) { "" }
        if (options.stripTildeSymbol) apply(TILDE_SYMBOL) { "" }
        if (options.stripBlockquoteMarker) apply(BLOCKQUOTE_MARKER_PATTERN) { "" }
        if (options.stripRepeatedDashes) apply(REPEATED_DASH_PATTERN) { "" }
        if (options.normalizeDashBetweenDigits) apply(DASH_BETWEEN_DIGITS_PATTERN) { " " }
        // Unicode elipsa "…" na obyčejnou tečku, ať ji pak zachytí i sloučení
        // opakované interpunkce níž (u víc elips za sebou).
        if (options.simplifyRepeatedPunctuation) {
            apply(ELLIPSIS_CHAR_PATTERN) { "." }
            apply(REPEATED_PUNCTUATION_PATTERN) { m -> m.group(1) }
        }
        if (options.stripEmoticons) apply(EMOTICON_PATTERN) { "" }
        if (options.stripBracketsAndQuotes) apply(BRACKETS_AND_QUOTES_PATTERN) { " " }
        if (options.stripEmoji) apply(EMOJI_PATTERN) { "" }
        if (options.stripUnderscores) apply(UNDERSCORE_PATTERN) { " " }
        if (options.stripHashSymbol) apply(HASHTAG_SYMBOL) { "" }
        if (options.stripMentionSymbol) apply(MENTION_SYMBOL) { "" }
        if (options.stripAsteriskSymbol) apply(ASTERISK_SYMBOL) { "" }

        // Normalizace bílých znaků, ať čtení plyne přirozeně.
        apply(MULTI_SPACE_PATTERN) { " " }
        apply(MULTI_NEWLINE_PATTERN) { "\n\n" }

        return CleanResult(text, positions)
    }

    /** Vrátí první nalezený odkaz v textu, nebo null. */
    fun extractFirstUrl(text: String): String? {
        val m = URL_PATTERN.matcher(text)
        return if (m.find()) m.group() else null
    }

    /**
     * Aplikuje regex náhradu a ZÁROVEŇ přepočítá pole "kde v původním textu byl
     * každý znak výsledku" - nezasažené znaky si nesou svou dosavadní mapovanou
     * pozici dál, znaky vzniklé náhradou (např. rozepsaná zkratka) se namapují
     * na pozici začátku shody v původním textu - pro účely zvýrazňování při
     * čtení to naprosto stačí (nejde o úpravu jednotlivých písmen, ale o to,
     * aby appka poznala, KTEROU část původního textu zrovna čte).
     */
    private fun replaceTracked(
        text: String,
        positions: IntArray,
        pattern: Pattern,
        replacer: (Matcher) -> String
    ): Pair<String, IntArray> {
        val matcher = pattern.matcher(text)
        val sb = StringBuilder()
        val newPositions = ArrayList<Int>(text.length)
        var lastEnd = 0
        while (matcher.find()) {
            for (i in lastEnd until matcher.start()) {
                sb.append(text[i])
                newPositions.add(positions[i])
            }
            val replacement = replacer(matcher)
            val anchor = when {
                matcher.start() < positions.size -> positions[matcher.start()]
                positions.isNotEmpty() -> positions[positions.size - 1] + 1
                else -> 0
            }
            for (ch in replacement) {
                sb.append(ch)
                newPositions.add(anchor)
            }
            lastEnd = matcher.end()
        }
        for (i in lastEnd until text.length) {
            sb.append(text[i])
            newPositions.add(positions[i])
        }
        return sb.toString() to newPositions.toIntArray()
    }

    /**
     * Rozepíše časté zkratky podle AKTUÁLNÍHO jazyka zařízení (např. česky
     * "např." -> "například", anglicky "e.g." -> "for example"), ať TTS
     * nedělá pauzu uprostřed věty. Pro jazyky bez podchyceného slovníku text
     * beze změny vrátí.
     */
    private fun expandAbbreviationsTracked(text: String, positions: IntArray): Pair<String, IntArray> {
        val language = Locale.getDefault().language
        val abbreviations = ABBREVIATIONS_BY_LANGUAGE[language] ?: return text to positions

        var result = text
        var pos = positions
        for ((abbr, full) in abbreviations) {
            // Zkratka musí být ohraničená mezerou/začátkem textu/závorkou/uvozovkou
            // vlevo (zachyceno do skupiny 1, ať ji můžeme zachovat) a mezerou,
            // koncem, interpunkcí nebo závorkou/uvozovkou vpravo - ne uprostřed
            // jiného slova. UNICODE_CASE je nutný, ať malá/velká písmena správně
            // fungují i mimo ASCII (azbuka, čeština, němčina s přehláskami...).
            val pattern = Pattern.compile(
                "(^|[\\s(\\[{\"'„«])" + Pattern.quote(abbr) + "(?=[\\s,.!?;:)\\]}\"'„»]|$)",
                Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
            )
            val (t, p) = replaceTracked(result, pos, pattern) { m ->
                val leading = m.group(1)
                val abbrPart = m.group().substring(leading.length)
                val replacement = if (abbrPart.firstOrNull()?.isUpperCase() == true) {
                    full.replaceFirstChar { it.uppercase() }
                } else {
                    full
                }
                leading + replacement
            }
            result = t
            pos = p
        }
        return result to pos
    }
}
