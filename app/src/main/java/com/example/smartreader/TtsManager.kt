package com.example.smartreader

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/**
 * Obaluje Android TextToSpeech a řeší věci, které TTS nativně neumí:
 *  - text delší než limit enginu (rozseká na kousky - "chunks", cca po větách)
 *  - "pauzu" (Android TTS nemá skutečnou pauzu, takže si pamatujeme
 *    poslední pozici a po Play znovu spustíme čtení od ní)
 *  - hlášení, které slovo/úsek se právě čte (pro zvýraznění v textu)
 *
 * DŮLEŽITÉ: text se seká po VĚTÁCH (ne po tisících znaků najednou), protože
 * spoustu telefonních hlasových enginů nespolehlivě hlásí přesnou pozici
 * uvnitř dlouhé promluvy (onRangeStart). Díky sekání po větách víme vždy
 * aspoň to, u které věty jsme skončili - i na enginech, které slovo přesně
 * nehlásí - takže pauza/změna rychlosti nikdy neskočí na úplný začátek textu.
 */
class TtsManager(
    context: Context,
    private val onWordRange: (absoluteStart: Int, absoluteEnd: Int) -> Unit,
    private val onDone: () -> Unit,
    private val onError: (String) -> Unit,
    private val onReady: () -> Unit
) {
    private var tts: TextToSpeech? = null
    private var isReady = false

    private var fullText: String = ""
    private var baseOffset: Int = 0
    private var chunks: List<Chunk> = emptyList()
    private var currentChunkIndex = 0
    private var lastKnownOffsetInChunk = 0
    private var volume = 1.0f

    var isSpeaking = false
        private set
    var isPaused = false
        private set

    private data class Chunk(val text: String, val localOffset: Int)

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("cs", "CZ"))
                isReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        // Garantovaně voláno pro každou promluvu na VŠECH enginech -
                        // díky tomu máme jistou pozici na úrovni věty i bez onRangeStart.
                        val idx = utteranceId?.removePrefix("chunk_")?.toIntOrNull() ?: return
                        currentChunkIndex = idx
                        lastKnownOffsetInChunk = 0
                    }

                    override fun onDone(utteranceId: String?) {
                        val idx = utteranceId?.removePrefix("chunk_")?.toIntOrNull() ?: return
                        if (idx == chunks.lastIndex) {
                            isSpeaking = false
                            onDone()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        onError("Chyba při přehrávání")
                    }

                    override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                        // Volitelné zpřesnění na úroveň slova - pokud to engine podporuje.
                        val chunkIndex = utteranceId?.removePrefix("chunk_")?.toIntOrNull() ?: return
                        val chunk = chunks.getOrNull(chunkIndex) ?: return
                        currentChunkIndex = chunkIndex
                        lastKnownOffsetInChunk = start
                        onWordRange(
                            baseOffset + chunk.localOffset + start,
                            baseOffset + chunk.localOffset + end
                        )
                    }
                })

                if (isReady) {
                    onReady()
                } else {
                    onError("Čeština není na tomto zařízení dostupná. Nainstaluj český hlas v Nastavení > Řeč > Převod textu na řeč.")
                }
            } else {
                onError("Nepodařilo se spustit hlasový modul (TTS).")
            }
        }
    }

    fun setSpeed(rate: Float) {
        tts?.setSpeechRate(rate)
    }

    /** Hlasitost čtení nezávislá na systémové hlasitosti (0.0 - 1.0, násobí ji). */
    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
    }

    /** Vrátí dostupné české hlasy nainstalované v systému. */
    fun getAvailableCzechVoices(): List<Voice> =
        tts?.voices?.filter { it.locale.language == "cs" }?.sortedBy { it.name } ?: emptyList()

    fun setVoice(voice: Voice) {
        tts?.voice = voice
    }

    fun getCurrentVoiceName(): String? = tts?.voice?.name

    /** Spustí čtení textu od nuly. baseOffset = pozice tohoto textu v celém dokumentu (pro zvýrazňování a kurzor). */
    fun speak(text: String, baseOffset: Int = 0) {
        if (!isReady || text.isEmpty()) return
        this.fullText = text
        this.baseOffset = baseOffset
        this.chunks = splitIntoChunks(text)
        tts?.stop()
        currentChunkIndex = 0
        lastKnownOffsetInChunk = 0
        isSpeaking = true
        isPaused = false
        speakFromChunk(0, 0)
    }

    private fun speakFromChunk(index: Int, offsetWithinChunk: Int) {
        val chunk = chunks.getOrNull(index) ?: run {
            isSpeaking = false
            onDone()
            return
        }
        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        val textToSpeak = chunk.text.substring(offsetWithinChunk.coerceIn(0, chunk.text.length))
        tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params, "chunk_$index")
        for (next in (index + 1) until chunks.size) {
            tts?.speak(chunks[next].text, TextToSpeech.QUEUE_ADD, params, "chunk_$next")
        }
    }

    /** Android TTS nemá nativní pauzu - zastavíme a zapamatujeme si přesně, kde jsme skončili. */
    fun pause() {
        tts?.stop()
        isPaused = true
        isSpeaking = false
    }

    /** Pokračuje přesně tam, kde se naposledy přestalo číst (stejný text jako před pauzou). */
    fun resume() {
        if (!isPaused) return
        val chunk = chunks.getOrNull(currentChunkIndex) ?: return
        isSpeaking = true
        isPaused = false
        speakFromChunk(currentChunkIndex, lastKnownOffsetInChunk.coerceIn(0, chunk.text.length))
    }

    /** Absolutní pozice v dokumentu, kde čtení naposledy skončilo/je (pro umístění kurzoru). */
    fun currentAbsolutePosition(): Int {
        val chunk = chunks.getOrNull(currentChunkIndex) ?: return baseOffset
        return baseOffset + chunk.localOffset + lastKnownOffsetInChunk
    }

    fun stop() {
        tts?.stop()
        isSpeaking = false
        isPaused = false
        currentChunkIndex = 0
        lastKnownOffsetInChunk = 0
        chunks = emptyList()
        fullText = ""
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }

    /**
     * Rozseká text na kousky po cca [targetLen] znacích, ale vždy na hranici věty
     * (tečka/vykřičník/otazník/nový řádek). [hardMax] je pojistka pro text bez
     * jakékoli interpunkce, ať jeden "chunk" nenaroste do nekonečna.
     */
    private fun splitIntoChunks(text: String, targetLen: Int = 220, hardMax: Int = 450): List<Chunk> {
        if (text.isEmpty()) return emptyList()
        val result = mutableListOf<Chunk>()
        var start = 0
        val n = text.length
        while (start < n) {
            var scan = start
            var end = -1
            while (scan < n) {
                val c = text[scan]
                val isBoundary = c == '.' || c == '!' || c == '?' || c == '\n'
                val lenSoFar = scan - start + 1
                if (isBoundary && lenSoFar >= targetLen) {
                    end = scan + 1
                    break
                }
                if (lenSoFar >= hardMax) {
                    val lastSpace = text.lastIndexOf(' ', scan)
                    end = if (lastSpace > start) lastSpace + 1 else scan + 1
                    break
                }
                scan++
            }
            if (end == -1) end = n
            result.add(Chunk(text.substring(start, end), start))
            start = end
        }
        return result
    }
}
