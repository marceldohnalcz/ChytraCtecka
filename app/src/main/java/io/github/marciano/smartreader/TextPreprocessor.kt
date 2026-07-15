package io.github.marciano.smartreader

import java.util.Locale
import java.util.regex.Pattern

/**
 * Čistí text před předáním do TTS – odstraňuje odkazy, čísla účtů,
 * podtržítka, emoji a další prvky, které nedávají smysl při poslechu.
 * Jednotlivá pravidla lze podle potřeby vypnout přes [Options].
 *
 * Rozepisování zkratek (viz [ABBREVIATIONS_BY_LANGUAGE]) je jazykově
 * specifické - appka si podle aktuálního jazyka zařízení ([Locale.getDefault])
 * vybere odpovídající slovník. Pro jazyky, které nemáme podchycené, se tenhle
 * krok jednoduše přeskočí (zbytek čištění - odkazy, čísla, interpunkce -
 * funguje pro všechny jazyky stejně, není jazykově specifický).
 */
object TextPreprocessor {

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

    // Nejběžnější emoji bloky
    private val EMOJI_PATTERN: Pattern = Pattern.compile(
        "[\\uD83C\\uDF00-\\uD83D\\uDDFF]|[\\uD83D\\uDE00-\\uD83D\\uDE4F]|" +
            "[\\uD83D\\uDE80-\\uD83D\\uDEFF]|[\\u2600-\\u27BF]|[\\u2B00-\\u2BFF]|" +
            "[\\uD83E\\uDD00-\\uD83E\\uDDFF]"
    )

    private val HASHTAG_SYMBOL: Pattern = Pattern.compile("#(?=\\S)")
    private val MENTION_SYMBOL: Pattern = Pattern.compile("@(?=\\S)")

    // Opakovaná interpunkce za sebou (elipsa "...", "!!!", "???") - některé TTS
    // enginy je čtou doslova jako "tečka tečka tečka" místo přirozené pauzy.
    // Zachytí i variantu oddělenou mezerami (". . . . ."), což je časté např.
    // u textů z OCR nebo naskenovaných dokumentů (tečkované "vodicí čáry").
    private val REPEATED_PUNCTUATION_PATTERN: Pattern = Pattern.compile("([.!?])(?:\\s*\\1){1,}")

    // Závorky (kulaté, hranaté, složené) a uvozovky všech běžných typů - TTS je
    // někdy čte doslova ("uvozovky", "levá závorka"). Nahrazujeme mezerou, aby se
    // slova kolem nespojila, ale zůstal zachovaný přirozený tok věty.
    private val BRACKETS_AND_QUOTES_PATTERN: Pattern = Pattern.compile(
        "[()\\[\\]{}\"„“«»‘’']"
    )

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
        val expandAbbreviations: Boolean = true,
        val simplifyRepeatedPunctuation: Boolean = true,
        val stripBracketsAndQuotes: Boolean = true,
        val stripUnderscores: Boolean = true,
        val stripEmoji: Boolean = true,
        val stripHashSymbol: Boolean = true,
        val stripMentionSymbol: Boolean = true
    )

    fun clean(input: String, options: Options = Options()): String {
        var text = input

        if (options.expandAbbreviations) text = expandAbbreviations(text)
        if (options.skipUrls) text = URL_PATTERN.matcher(text).replaceAll(" ")
        if (options.skipBankAccounts) {
            text = IBAN_PATTERN.matcher(text).replaceAll(" ")
            text = BANK_ACCOUNT_PATTERN.matcher(text).replaceAll(" ")
        }
        // Nejdřív odfiltrovat telefony/variabilní symboly (dokud jsou to "čisté" dlouhé
        // sekvence číslic), teprve pak sloučit tečkované tisíce - jinak by se velké
        // částky jako "1.234.567" po sloučení mylně chytily do stejného filtru.
        if (options.skipLongNumbers) text = LONG_DIGIT_PATTERN.matcher(text).replaceAll(" ")
        if (options.normalizeThousands) text = normalizeThousandsSeparators(text)
        // Unicode znak elipsy "…" (U+2026) je JEDEN znak, co vypadá jako tři tečky -
        // TTS ho čte doslova jako "tři tečky". Převedeme na obyčejnou tečku, ať ho
        // pak zachytí i sloučení opakované interpunkce níž (u víc elips za sebou).
        if (options.simplifyRepeatedPunctuation) text = text.replace('\u2026', '.')
        if (options.simplifyRepeatedPunctuation) {
            text = REPEATED_PUNCTUATION_PATTERN.matcher(text).replaceAll("$1")
        }
        if (options.stripBracketsAndQuotes) text = BRACKETS_AND_QUOTES_PATTERN.matcher(text).replaceAll(" ")
        if (options.stripEmoji) text = EMOJI_PATTERN.matcher(text).replaceAll("")
        if (options.stripUnderscores) text = text.replace('_', ' ')
        if (options.stripHashSymbol) text = HASHTAG_SYMBOL.matcher(text).replaceAll("")
        if (options.stripMentionSymbol) text = MENTION_SYMBOL.matcher(text).replaceAll("")

        // Normalizace bílých znaků, ať čtení plyne přirozeně
        text = text.replace(Regex("[ \\t]{2,}"), " ")
        text = text.replace(Regex("\\n{3,}"), "\n\n")
        text = text.trim()

        return text
    }

    /** Vrátí první nalezený odkaz v textu, nebo null. */
    fun extractFirstUrl(text: String): String? {
        val m = URL_PATTERN.matcher(text)
        return if (m.find()) m.group() else null
    }

    /** Sloučí "220.000" na "220000", ať to TTS přečte jako číslo, ne po číslicích. */
    private fun normalizeThousandsSeparators(text: String): String {
        val matcher = THOUSANDS_SEPARATOR_PATTERN.matcher(text)
        val sb = StringBuffer()
        while (matcher.find()) {
            val replacement = matcher.group().replace(".", "")
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    /**
     * Rozepíše časté zkratky podle AKTUÁLNÍHO jazyka zařízení (např. česky
     * "např." -> "například", anglicky "e.g." -> "for example"), ať TTS
     * nedělá pauzu uprostřed věty. Pro jazyky bez podchyceného slovníku text
     * beze změny vrátí.
     */
    private fun expandAbbreviations(text: String): String {
        val language = Locale.getDefault().language
        val abbreviations = ABBREVIATIONS_BY_LANGUAGE[language] ?: return text

        var result = text
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
            val matcher = pattern.matcher(result)
            val sb = StringBuffer()
            while (matcher.find()) {
                val leading = matcher.group(1)
                val abbrPart = matcher.group().substring(leading.length)
                val replacement = if (abbrPart.firstOrNull()?.isUpperCase() == true) {
                    full.replaceFirstChar { it.uppercase() }
                } else {
                    full
                }
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(leading + replacement))
            }
            matcher.appendTail(sb)
            result = sb.toString()
        }
        return result
    }
}
