package com.example.smartreader

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Obaluje Android TextToSpeech a řeší věci, které TTS nativně neumí:
 *  - text delší než limit enginu (rozseká na kousky, "chunks")
 *  - "pauzu" (Android TTS nemá skutečnou pauzu, takže si pamatujeme
 *    poslední pozici a po Play znovu spustíme čtení od ní)
 *  - hlášení, které slovo/úsek se právě čte (pro zvýraznění v textu)
 */
class TtsManager(
    context: Context,
    private val onWordRange: (globalStart: Int, globalEnd: Int) -> Unit,
    private val onDone: () -> Unit,
    private val onError: (String) -> Unit,
    private val onReady: () -> Unit
) {
    private var tts: TextToSpeech? = null
    private var isReady = false

    private var fullText: String = ""
    private var chunks: List<Chunk> = emptyList()
    private var currentChunkIndex = 0
    private var lastKnownOffsetInChunk = 0

    var isSpeaking = false
        private set
    var isPaused = false
        private set

    private data class Chunk(val text: String, val globalOffset: Int)

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("cs", "CZ"))
                isReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

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
                        val chunkIndex = utteranceId?.removePrefix("chunk_")?.toIntOrNull() ?: return
                        val chunk = chunks.getOrNull(chunkIndex) ?: return
                        currentChunkIndex = chunkIndex
                        lastKnownOffsetInChunk = start
                        onWordRange(chunk.globalOffset + start, chunk.globalOffset + end)
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

    /** Spustí čtení textu. startFromChar umožňuje pokračovat od konkrétní pozice. */
    fun speak(text: String, startFromChar: Int = 0) {
        if (!isReady) return
        fullText = text
        chunks = splitIntoChunks(text)
        tts?.stop()

        var startChunk = 0
        for ((i, c) in chunks.withIndex()) {
            if (c.globalOffset + c.text.length > startFromChar) {
                startChunk = i
                break
            }
        }
        currentChunkIndex = startChunk
        isSpeaking = true
        isPaused = false

        val offsetInChunk = (startFromChar - (chunks.getOrNull(startChunk)?.globalOffset ?: 0))
            .coerceAtLeast(0)
        speakFromChunk(startChunk, offsetInChunk)
    }

    private fun speakFromChunk(index: Int, offsetWithinChunk: Int) {
        val chunk = chunks.getOrNull(index) ?: run {
            isSpeaking = false
            onDone()
            return
        }
        val params = Bundle()
        val textToSpeak = chunk.text.substring(offsetWithinChunk.coerceIn(0, chunk.text.length))
        tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params, "chunk_$index")

        for (next in (index + 1) until chunks.size) {
            tts?.speak(chunks[next].text, TextToSpeech.QUEUE_ADD, params, "chunk_$next")
        }
    }

    /** Android TTS nemá nativní pauzu - zastavíme a zapamatujeme si, kde jsme skončili. */
    fun pause() {
        tts?.stop()
        isPaused = true
        isSpeaking = false
    }

    fun resume() {
        if (!isPaused) return
        val chunk = chunks.getOrNull(currentChunkIndex) ?: return
        val resumeOffset = chunk.globalOffset + lastKnownOffsetInChunk
        speak(fullText, resumeOffset)
    }

    fun stop() {
        tts?.stop()
        isSpeaking = false
        isPaused = false
        currentChunkIndex = 0
        lastKnownOffsetInChunk = 0
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }

    /** Rozseká text na kousky pod limitem enginu, pokud možno na hranici věty/mezery. */
    private fun splitIntoChunks(text: String, maxLen: Int = 3500): List<Chunk> {
        if (text.isEmpty()) return emptyList()
        val result = mutableListOf<Chunk>()
        var start = 0
        while (start < text.length) {
            var end = (start + maxLen).coerceAtMost(text.length)
            if (end < text.length) {
                val lastBreak = text.lastIndexOfAny(charArrayOf('.', '!', '?', '\n', ' '), end - 1)
                if (lastBreak > start) end = lastBreak + 1
            }
            result.add(Chunk(text.substring(start, end), start))
            start = end
        }
        return result
    }
}
