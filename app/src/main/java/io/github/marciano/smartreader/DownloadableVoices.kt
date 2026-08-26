package io.github.marciano.smartreader

/**
 * Jeden stažitelný neurální hlas (Piper, přes sherpa-onnx engine). Na rozdíl
 * od hlasů ze systémového Android TTS appka tenhle hlas sama stáhne a spustí
 * offline, přímo v sobě - nezávisle na tom, jaký TTS modul má uživatel
 * v telefonu.
 */
data class DownloadableVoice(
    val id: String,
    val displayNameRes: Int,
    val descriptionRes: Int,
    val language: String,
    val downloadUrl: String,
    val approxSizeMb: Int
)

object DownloadableVoices {
    /**
     * Hlasy hostuje přímo open-source projekt sherpa-onnx na svém GitHubu
     * (ne appka sama) - žádná vlastní serverová infrastruktura zatím není
     * potřeba. Pokud by appka v budoucí verzi chtěla hosting/přístup řídit
     * sama (např. kvůli zpoplatnění), stačí tady změnit downloadUrl.
     */
    val ALL: List<DownloadableVoice> = listOf(
        DownloadableVoice(
            id = "cs_CZ-jirka-medium",
            displayNameRes = R.string.voice_download_jirka_medium_name,
            descriptionRes = R.string.voice_download_jirka_medium_desc,
            language = "cs",
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-cs_CZ-jirka-medium-int8.tar.bz2",
            approxSizeMb = 20
        ),
        DownloadableVoice(
            id = "cs_CZ-jirka-low",
            displayNameRes = R.string.voice_download_jirka_low_name,
            descriptionRes = R.string.voice_download_jirka_low_desc,
            language = "cs",
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-cs_CZ-jirka-low-int8.tar.bz2",
            approxSizeMb = 20
        )
    )

    fun forCurrentLanguage(languageCode: String): List<DownloadableVoice> =
        ALL.filter { it.language == languageCode }
}
