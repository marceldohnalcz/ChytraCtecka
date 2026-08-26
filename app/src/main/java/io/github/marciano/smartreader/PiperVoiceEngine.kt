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

    fun play(samples: FloatArray, sampleRate: Int, volume: Float = 1.0f) {
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
