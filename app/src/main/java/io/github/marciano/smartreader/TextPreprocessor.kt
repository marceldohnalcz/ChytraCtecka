package io.github.marciano.smartreader

import java.util.regex.Pattern

/**
 * Čistí text před předáním do TTS – odstraňuje odkazy, čísla účtů,
 * podtržítka, emoji a další prvky, které nedávají smysl při poslechu.
 * Jednotlivá pravidla lze podle potřeby vypnout přes [Options].
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

    // Český zápis velkých čísel s tečkou jako oddělovačem tisíců, např. "220.000" nebo "1.234.567".
    // Bez tohoto by TTS četlo číslice jednu po druhé místo "dvěstědvacet tisíc".
    private val CZECH_THOUSANDS_PATTERN: Pattern = Pattern.compile("\\b\\d{1,3}(?:\\.\\d{3})+\\b")

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

    // Časté české zkratky s tečkou - TTS engine bere tečku jako konec věty a udělá
    // pauzu i uprostřed souvětí. Rozepsáním na plné znění pauza zmizí a čte se to
    // navíc srozumitelněji.
    private val ABBREVIATIONS: Map<String, String> = mapOf(
        "např" to "například",
        "tzn" to "to znamená",
        "atd" to "a tak dále",
        "atp" to "a tak podobně",
        "apod" to "a podobně",
        "tj" to "to jest",
        "resp" to "respektive",
        "popř" to "popřípadě",
        "mj" to "mimo jiné",
        "tzv" to "takzvaný",
        "str" to "strana"
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
        val matcher = CZECH_THOUSANDS_PATTERN.matcher(text)
        val sb = StringBuffer()
        while (matcher.find()) {
            val replacement = matcher.group().replace(".", "")
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    /** Rozepíše časté zkratky ("např." -> "například"), ať TTS nedělá pauzu uprostřed věty. */
    private fun expandAbbreviations(text: String): String {
        var result = text
        for ((abbr, full) in ABBREVIATIONS) {
            val pattern = Pattern.compile("\\b(${Pattern.quote(abbr)})\\.", Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(result)
            val sb = StringBuffer()
            while (matcher.find()) {
                val matchedAbbr = matcher.group(1)
                val replacement = if (matchedAbbr.firstOrNull()?.isUpperCase() == true) {
                    full.replaceFirstChar { it.uppercase() }
                } else {
                    full
                }
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement))
            }
            matcher.appendTail(sb)
            result = sb.toString()
        }
        return result
    }
}
