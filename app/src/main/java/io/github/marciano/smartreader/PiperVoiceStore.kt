package io.github.marciano.smartreader

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Stahuje a spravuje offline neurální (Piper) hlasy - na rozdíl od hlasů ze
 * systémového TTS appka tyhle hlasy stáhne jednou a pak je má natrvalo
 * uložené v telefonu, funguje to bez připojení k internetu.
 */
object PiperVoiceStore {

    private fun voicesRootDir(context: Context): File =
        File(context.filesDir, "piper_voices")

    fun voiceDir(context: Context, voiceId: String): File =
        File(voicesRootDir(context), voiceId)

    fun isDownloaded(context: Context, voiceId: String): Boolean {
        val dir = voiceDir(context, voiceId)
        return File(dir, "model.onnx").exists() &&
            File(dir, "tokens.txt").exists() &&
            File(dir, "espeak-ng-data").isDirectory
    }

    fun deleteVoice(context: Context, voiceId: String) {
        voiceDir(context, voiceId).deleteRecursively()
    }

    /**
     * Stáhne a rozbalí hlas na pozadí. Callbacky se volají zpátky na hlavním
     * vlákně, ať appka může rovnou aktualizovat UI bez dalšího přepínání vláken.
     */
    fun downloadVoice(
        context: Context,
        voice: DownloadableVoice,
        onProgress: (percent: Int) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        Thread {
            var connection: HttpURLConnection? = null
            val tempFile = File(context.cacheDir, "${voice.id}.tar.bz2")
            try {
                val url = URL(voice.downloadUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 20_000
                connection.readTimeout = 20_000
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw java.io.IOException("HTTP ${connection.responseCode}")
                }

                val totalSize = connection.contentLength
                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var totalRead = 0L
                        var lastReportedPercent = -1
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            totalRead += read
                            if (totalSize > 0) {
                                // Stažení = 0-90 % postupu, zbytek (90-100 %) je rozbalení.
                                val percent = ((totalRead * 90) / totalSize).toInt().coerceIn(0, 90)
                                if (percent != lastReportedPercent) {
                                    lastReportedPercent = percent
                                    mainHandler.post { onProgress(percent) }
                                }
                            }
                        }
                    }
                }

                mainHandler.post { onProgress(92) }

                val targetDir = voiceDir(context, voice.id)
                targetDir.deleteRecursively()
                targetDir.mkdirs()
                extractTarBz2(tempFile, targetDir)
                tempFile.delete()

                if (!isDownloaded(context, voice.id)) {
                    throw java.io.IOException("Archiv nemá očekávaný obsah")
                }

                mainHandler.post {
                    onProgress(100)
                    onSuccess()
                }
            } catch (e: Exception) {
                tempFile.delete()
                voiceDir(context, voice.id).deleteRecursively()
                mainHandler.post { onError(e.message ?: e.javaClass.simpleName) }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    /**
     * Archiv má strukturu "vits-piper-{id}/soubor..." - appka ji zplošťuje
     * přímo do cílové složky a hlavní .onnx model vždy přejmenuje na
     * jednotné "model.onnx" (v archivu má pokaždé jiný přesný název).
     */
    private fun extractTarBz2(archiveFile: File, targetDir: File) {
        BZip2CompressorInputStream(archiveFile.inputStream().buffered()).use { bzIn ->
            TarArchiveInputStream(bzIn).use { tarIn ->
                var entry = tarIn.nextEntry
                while (entry != null) {
                    val nameAfterRoot = entry.name.substringAfter('/', "")
                    if (nameAfterRoot.isNotBlank()) {
                        val normalized = if (nameAfterRoot.endsWith(".onnx") && !nameAfterRoot.contains("/")) {
                            "model.onnx"
                        } else {
                            nameAfterRoot
                        }
                        val outFile = File(targetDir, normalized)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { out -> tarIn.copyTo(out) }
                        }
                    }
                    entry = tarIn.nextEntry
                }
            }
        }
    }
}
