package com.example.smartreader

import android.Manifest
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.Spannable
import android.text.style.BackgroundColorSpan
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.smartreader.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity(), ReadingService.Listener {

    private lateinit var binding: ActivityMainBinding
    private var service: ReadingService? = null
    private var isServiceBound = false
    private var highlightSpan: BackgroundColorSpan? = null
    private var currentSpeedRate = 1.0f

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* není kritické, appka funguje i bez notifikace */ }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as ReadingService.LocalBinder
            service = localBinder.getService()
            service?.setListener(this@MainActivity)
            service?.setSpeed(currentSpeedRate)
            syncButtonWithServiceState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermissionIfNeeded()
        setupButtons()
        setupSpeedSlider()
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        Intent(this, ReadingService::class.java).also {
            bindService(it, serviceConnection, Context.BIND_AUTO_CREATE)
            isServiceBound = true
        }
    }

    override fun onStop() {
        super.onStop()
        service?.setListener(null)
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupButtons() {
        binding.btnPaste.setOnClickListener { pasteFromClipboard() }
        binding.btnClear.setOnClickListener { clearText() }
        binding.btnPlayPause.setOnClickListener { togglePlayPause() }
        binding.btnStop.setOnClickListener { stopReading() }
        binding.btnSpeedMinus.setOnClickListener { changeSpeedStep(-1) }
        binding.btnSpeedPlus.setOnClickListener { changeSpeedStep(1) }
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
                applySpeedChange(0.5f + (seekBar?.progress ?: 5) / 10f)
            }
        })
    }

    /** Tlačítka +/- posunou jezdec o jeden krok (0.1x) a rovnou aplikují novou rychlost. */
    private fun changeSpeedStep(deltaSteps: Int) {
        val newProgress = (binding.seekSpeed.progress + deltaSteps).coerceIn(0, binding.seekSpeed.max)
        binding.seekSpeed.progress = newProgress
        applySpeedChange(0.5f + newProgress / 10f)
    }

    /**
     * Skutečně aplikuje novou rychlost čtení. Pokud appka právě čte, pokračuje
     * přesně od aktuální pozice novou rychlostí (nerestartuje celý text).
     */
    private fun applySpeedChange(rate: Float) {
        currentSpeedRate = rate
        val svc = service ?: return
        svc.setSpeed(rate)
        if (svc.isSpeaking()) {
            val pos = svc.currentAbsolutePosition()
            svc.pause()
            placeCursorAt(pos)
            startReadingFromCursor()
        }
    }

    private fun updateSpeedLabel(rate: Float) {
        binding.tvSpeedValue.text = String.format(Locale.getDefault(), "%.1fx", rate)
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(this).toString()
        } else null

        if (text.isNullOrBlank()) {
            Toast.makeText(this, "Schránka je prázdná", Toast.LENGTH_SHORT).show()
        } else {
            binding.etContent.setText(text)
            binding.etContent.setSelection(0)
        }
    }

    private fun clearText() {
        stopReading()
        binding.etContent.setText("")
    }

    private fun togglePlayPause() {
        val svc = service ?: return
        if (svc.isSpeaking()) {
            svc.pause()
        } else {
            startReadingFromCursor()
        }
    }

    /**
     * Vždy čte aktuální (živý) obsah textového pole od místa, kde je kurzor.
     * Díky tomu funguje správně jak "Přehrát" na začátku, tak pokračování po pauze,
     * tak i případ, kdy uživatel text během pauzy upravil (smazal/přepsal) -
     * čte se přesně to, co v poli skutečně je, od aktuální pozice kurzoru.
     */
    private fun startReadingFromCursor() {
        val liveText = binding.etContent.text?.toString().orEmpty()
        if (liveText.isBlank()) {
            Toast.makeText(this, "Nejprve vlož nebo napiš text", Toast.LENGTH_SHORT).show()
            return
        }
        val cursor = binding.etContent.selectionStart.coerceIn(0, liveText.length)
        val remaining = liveText.substring(cursor)
        val cleaned = TextPreprocessor.clean(remaining)

        if (cleaned.isBlank()) {
            Toast.makeText(this, "Od pozice kurzoru už není co číst", Toast.LENGTH_SHORT).show()
            return
        }

        // Nahradíme jen "ocas" textu (od kurzoru dál) vyčištěnou verzí, ať zvýrazňování
        // odpovídá tomu, co se skutečně čte, a zbytek textu (před kurzorem) zůstane netknutý.
        if (cleaned != remaining) {
            binding.etContent.text?.replace(cursor, liveText.length, cleaned)
        }
        placeCursorAt(cursor)

        ensureServiceStarted()
        service?.setSpeed(currentSpeedRate)
        service?.speak(cleaned, cursor)
    }

    private fun ensureServiceStarted() {
        val intent = Intent(this, ReadingService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopReading() {
        service?.stopReading()
        clearHighlight()
        placeCursorAt(0)
    }

    private fun placeCursorAt(position: Int) {
        val len = binding.etContent.text?.length ?: 0
        binding.etContent.setSelection(position.coerceIn(0, len))
    }

    private fun syncButtonWithServiceState() {
        val svc = service ?: return
        onStateChanged(svc.isSpeaking(), svc.isPaused())
    }

    // --- ReadingService.Listener ---

    override fun onWordRange(start: Int, end: Int) {
        runOnUiThread { highlightRange(start, end) }
    }

    override fun onStateChanged(isSpeaking: Boolean, isPaused: Boolean) {
        runOnUiThread {
            if (isSpeaking) {
                binding.btnPlayPause.text = "Pauza"
                binding.btnPlayPause.setIconResource(R.drawable.ic_pause)
            } else {
                binding.btnPlayPause.text = "Přehrát"
                binding.btnPlayPause.setIconResource(R.drawable.ic_play)
            }
            if (isPaused) {
                val pos = service?.currentAbsolutePosition() ?: 0
                placeCursorAt(pos)
            }
            if (!isSpeaking && !isPaused) {
                clearHighlight()
            }
        }
    }

    override fun onError(message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    // --- Zvýrazňování čteného textu ---

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

    // --- Sdílení textu / odkazů z jiných aplikací ---

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
            loadFromUrl(url)
        } else {
            binding.etContent.setText(sharedText)
            binding.etContent.setSelection(0)
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
                    "Nepodařilo se z odkazu vytáhnout text (u Facebooku/Instagramu to kvůli JavaScriptu často nejde). Zkus text v dané appce označit a použít \"Chytrá čtečka textu\" z nabídky, nebo ho zkopíruj a vlož ručně.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                binding.etContent.setText(extracted)
                binding.etContent.setSelection(0)
            }
        }
    }
}
