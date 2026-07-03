package com.example.smartreader

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

    // Nejběžnější emoji bloky
    private val EMOJI_PATTERN: Pattern = Pattern.compile(
        "[\\uD83C\\uDF00-\\uD83D\\uDDFF]|[\\uD83D\\uDE00-\\uD83D\\uDE4F]|" +
            "[\\uD83D\\uDE80-\\uD83D\\uDEFF]|[\\u2600-\\u27BF]|[\\u2B00-\\u2BFF]|" +
            "[\\uD83E\\uDD00-\\uD83E\\uDDFF]"
    )

    private val HASHTAG_SYMBOL: Pattern = Pattern.compile("#(?=\\S)")
    private val MENTION_SYMBOL: Pattern = Pattern.compile("@(?=\\S)")

    data class Options(
        val skipUrls: Boolean = true,
        val skipBankAccounts: Boolean = true,
        val skipLongNumbers: Boolean = true,
        val stripUnderscores: Boolean = true,
        val stripEmoji: Boolean = true,
        val stripHashSymbol: Boolean = true,
        val stripMentionSymbol: Boolean = true
    )

    fun clean(input: String, options: Options = Options()): String {
        var text = input

        if (options.skipUrls) text = URL_PATTERN.matcher(text).replaceAll(" ")
        if (options.skipBankAccounts) {
            text = IBAN_PATTERN.matcher(text).replaceAll(" ")
            text = BANK_ACCOUNT_PATTERN.matcher(text).replaceAll(" ")
        }
        if (options.skipLongNumbers) text = LONG_DIGIT_PATTERN.matcher(text).replaceAll(" ")
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
}
