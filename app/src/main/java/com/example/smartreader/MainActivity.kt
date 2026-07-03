package com.example.smartreader

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Spannable
import android.text.style.BackgroundColorSpan
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smartreader.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var ttsManager: TtsManager
    private var highlightSpan: BackgroundColorSpan? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTts()
        setupButtons()
        setupSpeedSlider()
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun setupTts() {
        ttsManager = TtsManager(
            context = this,
            onWordRange = { start, end -> runOnUiThread { highlightRange(start, end) } },
            onDone = { runOnUiThread { onPlaybackFinished() } },
            onError = { msg -> runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() } },
            onReady = {}
        )
    }

    private fun setupButtons() {
        binding.btnPaste.setOnClickListener { pasteFromClipboard() }
        binding.btnClear.setOnClickListener { clearText() }
        binding.btnPlayPause.setOnClickListener { togglePlayPause() }
        binding.btnStop.setOnClickListener { stopReading() }
    }

    private fun setupSpeedSlider() {
        // SeekBar 0..25 -> rychlost 0.5x .. 3.0x, výchozí 1.0x (progress = 5)
        binding.seekSpeed.max = 25
        binding.seekSpeed.progress = 5
        updateSpeedLabel(1.0f)

        binding.seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateSpeedLabel(0.5f + progress / 10f)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val rate = 0.5f + (seekBar?.progress ?: 5) / 10f
                ttsManager.setSpeed(rate)
                // Pokud právě čte, restartujeme od aktuální pozice s novou rychlostí
                if (ttsManager.isSpeaking) {
                    ttsManager.pause()
                    ttsManager.resume()
                }
            }
        })
    }

    private fun updateSpeedLabel(rate: Float) {
        binding.tvSpeedValue.text = String.format(Locale.getDefault(), "%.1fx", rate)
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(this).toString()
            if (text.isNotBlank()) {
                binding.etContent.setText(text)
            } else {
                Toast.makeText(this, "Schránka je prázdná", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Schránka je prázdná", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearText() {
        stopReading()
        binding.etContent.setText("")
    }

    private fun togglePlayPause() {
        when {
            ttsManager.isSpeaking -> {
                ttsManager.pause()
                binding.btnPlayPause.text = "▶ Přehrát"
            }
            ttsManager.isPaused -> {
                ttsManager.resume()
                binding.btnPlayPause.text = "⏸ Pauza"
            }
            else -> startReading()
        }
    }

    private fun startReading() {
        val raw = binding.etContent.text?.toString().orEmpty()
        if (raw.isBlank()) {
            Toast.makeText(this, "Nejprve vlož nebo napiš text", Toast.LENGTH_SHORT).show()
            return
        }
        val cleaned = TextPreprocessor.clean(raw)
        // Uložíme vyčištěný text zpátky do pole, ať zvýrazňování odpovídá tomu, co se skutečně čte
        if (cleaned != raw) {
            binding.etContent.setText(cleaned)
        }
        ttsManager.speak(cleaned)
        binding.btnPlayPause.text = "⏸ Pauza"
    }

    private fun stopReading() {
        ttsManager.stop()
        binding.btnPlayPause.text = "▶ Přehrát"
        clearHighlight()
    }

    private fun onPlaybackFinished() {
        binding.btnPlayPause.text = "▶ Přehrát"
        clearHighlight()
    }

    private fun highlightRange(start: Int, end: Int) {
        val text = binding.etContent.text ?: return
        if (start < 0 || end > text.length || start >= end) return
        clearHighlight()
        val span = BackgroundColorSpan(0xFFFFF176.toInt())
        highlightSpan = span
        (text as? Spannable)?.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun clearHighlight() {
        val text = binding.etContent.text
        highlightSpan?.let { (text as? Spannable)?.removeSpan(it) }
        highlightSpan = null
    }

    private fun handleIncomingIntent(intent: Intent) {
        val sharedText: String? = when (intent.action) {
            Intent.ACTION_SEND ->
                if (intent.type == "text/plain") intent.getStringExtra(Intent.EXTRA_TEXT) else null
            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        if (sharedText.isNullOrBlank()) return

        val url = TextPreprocessor.extractFirstUrl(sharedText)
        if (url != null && sharedText.trim() == url.trim()) {
            // Sdílený obsah je jen odkaz -> zkusíme z něj stáhnout text článku
            loadFromUrl(url)
        } else {
            binding.etContent.setText(sharedText)
        }
    }

    private fun loadFromUrl(url: String) {
        binding.progress.visibility = View.VISIBLE
        binding.etContent.setText("Stahuji text ze stránky…")
        lifecycleScope.launch {
            val extracted = withContext(Dispatchers.IO) { WebArticleExtractor.extractText(url) }
            binding.progress.visibility = View.GONE
            if (extracted.isNullOrBlank()) {
                binding.etContent.setText("")
                Toast.makeText(
                    this@MainActivity,
                    "Nepodařilo se z odkazu vytáhnout text (u Facebooku/Instagramu to kvůli JavaScriptu často nejde). Zkus text v dané appce označit a použít \"Chytrá čtečka\" z nabídky, nebo ho zkopíruj a vlož ručně.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                binding.etContent.setText(extracted)
            }
        }
    }

    override fun onDestroy() {
        ttsManager.shutdown()
        super.onDestroy()
    }
}
