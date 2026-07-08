package com.example.smartreader

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Rozpoznávání textu z obrázku (OCR) přes Google ML Kit - běží přímo na
 * telefonu, offline, zdarma, obrázek nikam neopouští zařízení.
 *
 * ML Kit ve výsledku rozděluje text na "bloky" (zhruba odstavce) a uvnitř
 * bloku na jednotlivé řádky tak, jak byly vizuálně zalomené na obrázku.
 * Kdybychom použili syrový visionText.text, zůstaly by tam zalomení přesně
 * podle obrázku (často jen pár slov na řádek) a TTS by na konci každého
 * takového "řádku" dělalo pauzu - čtení by znělo trhaně. Proto řádky uvnitř
 * bloku spojujeme zpátky do plynulého textu a jako skutečné zalomení
 * (nový odstavec) bereme jen hranici mezi bloky.
 */
object ImageTextRecognizer {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognize(context: Context, uri: Uri): String =
        suspendCancellableCoroutine { cont ->
            try {
                val image = InputImage.fromFilePath(context, uri)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        if (cont.isActive) cont.resume(reflow(visionText))
                    }
                    .addOnFailureListener { e ->
                        if (cont.isActive) cont.resumeWithException(e)
                    }
            } catch (e: Exception) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        }

    private fun reflow(visionText: Text): String {
        return visionText.textBlocks
            .map { reflowBlock(it) }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    private fun reflowBlock(block: Text.TextBlock): String {
        val lines = block.lines.map { it.text.trim() }.filter { it.isNotEmpty() }
        val sb = StringBuilder()
        for (line in lines) {
            when {
                sb.isEmpty() -> sb.append(line)
                // Slovo rozdělené pomlčkou na konci řádku - spojit bez pomlčky a mezery
                sb.endsWith("-") && line.isNotEmpty() && line[0].isLowerCase() -> {
                    sb.setLength(sb.length - 1)
                    sb.append(line)
                }
                else -> sb.append(" ").append(line)
            }
        }
        return sb.toString()
    }
}
