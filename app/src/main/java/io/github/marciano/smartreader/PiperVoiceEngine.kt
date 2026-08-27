package io.github.marciano.smartreader

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

/**
 * Obaluje sherpa-onnx OfflineTts pro jeden konkrétní stažený Piper hlas.
 * Na rozdíl od TtsManager (systémové Android TTS) appka tady sama generuje
 * zvukové vzorky a sama je přehrává - není to systémová služba, appka
 * si audio pipeline drží celou sama.
 */
class PiperVoiceEngine(voiceDir: File, numThreads: Int = 2) {

    private val tts: OfflineTts

    init {
        val vitsConfig = OfflineTtsVitsModelConfig(
            model = File(voiceDir, "model.onnx").absolutePath,
            tokens = File(voiceDir, "tokens.txt").absolutePath,
            dataDir = File(voiceDir, "espeak-ng-data").absolutePath
        )
        val modelConfig = OfflineTtsModelConfig(
            vits = vitsConfig,
            numThreads = numThreads,
            debug = false,
            provider = "cpu"
        )
        tts = OfflineTts(config = OfflineTtsConfig(model = modelConfig))
    }

    val sampleRate: Int get() = tts.sampleRate()

    /** Vygeneruje zvukové vzorky pro daný text (blokující volání, spouštět mimo hlavní vlákno). */
    fun generate(text: String, speed: Float = 1.0f): FloatArray =
        tts.generate(text = text, sid = 0, speed = speed).samples

    fun release() = tts.release()
}

/** Přehraje surové PCM vzorky (float, mono) přes AudioTrack. */
class PcmAudioPlayer {
    private var audioTrack: AudioTrack? = null

    /**
     * [onComplete] se zavolá (na hlavním vlákně), jakmile přehrávání dojede na
     * konec vzorků - použije se pro postupné čtení věta po větě (Piper hlas).
     */
    fun play(samples: FloatArray, sampleRate: Int, volume: Float = 1.0f, onComplete: (() -> Unit)? = null) {
        stop()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(1)
        val bufferSizeBytes = maxOf(minBufferSize, samples.size * 4)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSizeBytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.setVolume(volume.coerceIn(0f, 1f))
        track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)

        if (onComplete != null && samples.isNotEmpty()) {
            track.setNotificationMarkerPosition(samples.size)
            track.setPlaybackPositionUpdateListener(
                object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(t: AudioTrack?) = onComplete()
                    override fun onPeriodicNotification(t: AudioTrack?) {}
                },
                android.os.Handler(android.os.Looper.getMainLooper())
            )
        }

        track.play()
        audioTrack = track
    }

    fun stop() {
        audioTrack?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
                // AudioTrack ještě nezačal hrát - stop() by jinak spadl, není co řešit.
            }
            it.release()
        }
        audioTrack = null
    }
}

/**
 * Řídí čtení DELŠÍHO textu přes stažený Piper hlas - appka sama seká text na
 * kousky po větách (stejně jako TtsManager u systémového TTS) a postupně je
 * generuje a přehrává jeden po druhém, protože Piper na rozdíl od systémového
 * Android TTS nemá vlastní frontu promluv.
 *
 * ZJEDNODUŠENÍ oproti systémovému TTS:
 *  - Zvýraznění čteného textu je na úrovni VĚTY, ne slova - Piper nehlásí
 *    přesnou pozici uvnitř věty za chodu jako systémové TTS (onRangeStart).
 *  - Pauza/pokračování funguje na úrovni věty - po stisknutí Pauza se
 *    přehrávání zastaví, po Pokračovat appka danou větu přehraje od
 *    začátku znovu (ne přesně od místa, kde se přestalo) - u krátkých vět
 *    (pár sekund) to v praxi není znát.
 *  - Rychlost čtení se použije na DALŠÍ větu, ne na tu už rozehranou.
 *  - Výška hlasu (pitch) se u Piper hlasů nepoužívá - Piper na rozdíl od
 *    systémového TTS tenhle parametr nemá.
 */
class PiperReadingSession(
    private val voiceDir: File,
    private val onSentenceStart: (absoluteStart: Int, absoluteEnd: Int) -> Unit,
    private val onDone: () -> Unit,
    private val onError: (String) -> Unit
) {
    private data class Chunk(val text: String, val localOffset: Int)

    private var engine: PiperVoiceEngine? = null
    private val player = PcmAudioPlayer()
    private var chunks: List<Chunk> = emptyList()
    private var currentChunkIndex = 0
    private var baseOffset = 0
    private var speed = 1.0f
    private var volume = 1.0f
    private var stopped = true

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    var isSpeaking = false
        private set
    var isPaused = false
        private set

    fun setSpeed(rate: Float) {
        speed = rate
    }

    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
    }

    fun speak(text: String, baseOffset: Int) {
        stop()
        stopped = false
        this.baseOffset = baseOffset
        chunks = splitIntoChunks(text)
        currentChunkIndex = 0
        if (chunks.isEmpty()) {
            mainHandler.post { onDone() }
            return
        }
        isSpeaking = true
        isPaused = false
        speakChunk(0)
    }

    private fun speakChunk(index: Int) {
        if (stopped) return
        if (index >= chunks.size) {
            isSpeaking = false
            mainHandler.post { onDone() }
            return
        }
        currentChunkIndex = index
        val chunk = chunks[index]
        Thread {
            try {
                val eng = engine ?: PiperVoiceEngine(voiceDir).also { engine = it }
                val samples = eng.generate(chunk.text, speed)
                val sampleRate = eng.sampleRate
                if (stopped) return@Thread
                mainHandler.post {
                    if (stopped) return@post
                    onSentenceStart(baseOffset + chunk.localOffset, baseOffset + chunk.localOffset + chunk.text.length)
                    player.play(samples, sampleRate, volume) {
                        if (!stopped) speakChunk(index + 1)
                    }
                }
            } catch (e: Exception) {
                if (!stopped) mainHandler.post { onError(e.message ?: e.javaClass.simpleName) }
            }
        }.start()
    }

    /** Zastaví aktuálně rozehranou větu - pokračování (viz [resume]) ji přehraje od začátku znovu. */
    fun pause() {
        stopped = true
        isSpeaking = false
        isPaused = true
        player.stop()
    }

    fun resume() {
        if (chunks.isEmpty()) return
        stopped = false
        isPaused = false
        isSpeaking = true
        speakChunk(currentChunkIndex)
    }

    fun stop() {
        stopped = true
        isSpeaking = false
        isPaused = false
        player.stop()
    }

    fun currentAbsolutePosition(): Int {
        val chunk = chunks.getOrNull(currentChunkIndex) ?: return baseOffset
        return baseOffset + chunk.localOffset
    }

    fun shutdown() {
        stop()
        engine?.release()
        engine = null
    }

    /** Stejná logika jako TtsManager.splitIntoChunks - sekání po větách. */
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
