package com.example.smartreader

import android.Manifest
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.tts.Voice
import android.text.Spannable
import android.text.style.BackgroundColorSpan
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smartreader.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity(), ReadingService.Listener {

    private lateinit var binding: ActivityMainBinding
    private var service: ReadingService? = null
    private var isServiceBound = false
    private var highlightSpan: BackgroundColorSpan? = null
    private var currentSpeedRate = 1.0f
    private var currentVolume = 1.0f

    /** Pokud je aktuální text načtený z knihovny, sledujeme jeho id kvůli ukládání pozice. */
    private var currentLibraryItemId: String? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* funguje i bez notifikace */ }

    private var pendingCameraUri: Uri? = null

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { processImageForOcr(it) }
        }

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                pendingCameraUri?.let { processImageForOcr(it) }
            }
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchCamera()
            } else {
                Toast.makeText(this, "Bez přístupu ke kameře nejde fotit", Toast.LENGTH_SHORT).show()
            }
        }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as ReadingService.LocalBinder
            service = localBinder.getService()
            service?.setListener(this@MainActivity)
            service?.setSpeed(currentSpeedRate)
            service?.setVolume(currentVolume)
            service?.setAutoResumeAfterInterruption(AppSettings.loadAutoResumeAfterCall(this@MainActivity))
            AppSettings.loadVoiceName(this@MainActivity)?.let { name ->
                service?.getAvailableCzechVoices()?.find { it.name == name }?.let { service?.setVoice(it) }
            }
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

        currentSpeedRate = AppSettings.loadSpeed(this)
        currentVolume = AppSettings.loadVolume(this)

        requestNotificationPermissionIfNeeded()
        setupButtons()
        setupSpeedSlider()
        restoreDraftOrHandleIntent(intent)
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
        saveDraftState()
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
        binding.btnSkipPrev.setOnClickListener { skipToParagraph(-1) }
        binding.btnSkipNext.setOnClickListener { skipToParagraph(1) }
        binding.btnSpeedMinus.setOnClickListener { changeSpeedStep(-1) }
        binding.btnSpeedPlus.setOnClickListener { changeSpeedStep(1) }
        binding.btnMoreMenu.setOnClickListener { showMoreMenu(it) }
        binding.btnSave.setOnClickListener { saveCurrentTextToLibrary() }
        binding.btnLibrary.setOnClickListener { showLibraryDialog() }
        binding.btnLink.setOnClickListener { showLinkInputDialog() }
        binding.btnImage.setOnClickListener { showImageSourceDialog() }
    }

    private fun setupSpeedSlider() {
        binding.seekSpeed.max = 25
        val initialProgress = ((currentSpeedRate - 0.5f) * 10).toInt().coerceIn(0, 25)
        binding.seekSpeed.progress = initialProgress
        updateSpeedLabel(currentSpeedRate)

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
     * Skutečně aplikuje novou rychlost čtení a uloží ji jako výchozí pro příště.
     * Pokud appka právě čte, pokračuje přesně od aktuální pozice novou rychlostí
     * (nerestartuje celý text).
     */
    private fun applySpeedChange(rate: Float) {
        currentSpeedRate = rate
        AppSettings.saveSpeed(this, rate)
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
            currentLibraryItemId = null
            val url = TextPreprocessor.extractFirstUrl(text)
            if (url != null && text.trim() == url.trim()) {
                loadFromUrl(url)
            } else {
                binding.etContent.setText(text)
                binding.etContent.setSelection(0)
            }
        }
    }

    private fun clearText() {
        stopReading()
        currentLibraryItemId = null
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
     * tak i případ, kdy uživatel text během pauzy upravil - čte se přesně to,
     * co v poli skutečně je, od aktuální pozice kurzoru.
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

        if (cleaned != remaining) {
            binding.etContent.text?.replace(cursor, liveText.length, cleaned)
        }
        placeCursorAt(cursor)

        ensureServiceStarted()
        service?.setSpeed(currentSpeedRate)
        service?.setVolume(currentVolume)
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

    /**
     * Přeskočí na začátek dalšího/předchozího odstavce (podle direction: +1/-1).
     * Pokud appka právě čte, pokračuje čtení hned od nové pozice. Chová se jako
     * v audioknihách: "předchozí" hodně hluboko v odstavci skočí na jeho začátek,
     * blízko začátku skočí až na odstavec před ním.
     */
    private fun skipToParagraph(direction: Int) {
        val text = binding.etContent.text?.toString().orEmpty()
        if (text.isBlank()) return
        val svc = service ?: return

        val wasSpeaking = svc.isSpeaking()
        val currentPos = if (svc.isSpeaking() || svc.isPaused()) {
            svc.currentAbsolutePosition()
        } else {
            binding.etContent.selectionStart
        }

        val starts = findParagraphStarts(text)
        val targetPos = if (direction > 0) {
            starts.firstOrNull { it > currentPos } ?: text.length
        } else {
            val currentParagraphStart = starts.lastOrNull { it <= currentPos } ?: 0
            if (currentPos - currentParagraphStart > 15) {
                currentParagraphStart
            } else {
                starts.lastOrNull { it < currentParagraphStart } ?: 0
            }
        }

        if (svc.isSpeaking()) {
            svc.pause()
        }
        placeCursorAt(targetPos)
        if (wasSpeaking) {
            startReadingFromCursor()
        } else {
            clearHighlight()
        }
    }

    /** Vrátí pozice, kde v textu začíná každý odstavec (0 a pak vždy za "\n\n"). */
    private fun findParagraphStarts(text: String): List<Int> {
        val starts = mutableListOf(0)
        var idx = text.indexOf("\n\n")
        while (idx != -1) {
            starts.add(idx + 2)
            idx = text.indexOf("\n\n", idx + 2)
        }
        return starts
    }

    private fun placeCursorAt(position: Int) {
        val len = binding.etContent.text?.length ?: 0
        binding.etContent.setSelection(position.coerceIn(0, len))
    }

    private fun syncButtonWithServiceState() {
        val svc = service ?: return
        onStateChanged(svc.isSpeaking(), svc.isPaused())
    }

    // --- Rozečtený text (draft) ---

    private fun restoreDraftOrHandleIntent(intent: Intent) {
        val hasShareContent = intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_PROCESS_TEXT
        if (hasShareContent) {
            handleIncomingIntent(intent)
            return
        }
        val draftText = TextLibraryStore.loadDraftText(this)
        if (draftText.isNotBlank()) {
            binding.etContent.setText(draftText)
            val pos = TextLibraryStore.loadDraftPosition(this).coerceIn(0, draftText.length)
            binding.etContent.setSelection(pos)
        }
    }

    private fun saveDraftState() {
        val text = binding.etContent.text?.toString().orEmpty()
        val position = service?.let {
            if (it.isSpeaking() || it.isPaused()) it.currentAbsolutePosition() else binding.etContent.selectionStart
        } ?: binding.etContent.selectionStart
        TextLibraryStore.saveDraft(this, text, position)
        currentLibraryItemId?.let { id -> TextLibraryStore.updateCursorPosition(this, id, position) }
    }

    // --- Knihovna uložených textů ---

    private fun saveCurrentTextToLibrary() {
        val text = binding.etContent.text?.toString().orEmpty()
        if (text.isBlank()) {
            Toast.makeText(this, "Není co uložit - textové pole je prázdné", Toast.LENGTH_SHORT).show()
            return
        }
        val saved = TextLibraryStore.addToLibrary(this, text)
        currentLibraryItemId = saved.id
        Toast.makeText(this, "Uloženo do knihovny", Toast.LENGTH_SHORT).show()
    }

    private fun showLibraryDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_library, null)
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerLibrary)
        val empty = view.findViewById<TextView>(R.id.tvLibraryEmpty)
        val btnPlaySelected = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPlaySelected)
        recycler.layoutManager = LinearLayoutManager(this)

        lateinit var dialog: AlertDialog
        lateinit var adapter: LibraryAdapter
        val selectedIds = mutableSetOf<String>()

        fun refreshPlayButton() {
            val count = selectedIds.size
            btnPlaySelected.isEnabled = count > 0
            btnPlaySelected.text = if (count > 0) "Přehrát vybrané ($count)" else "Přehrát vybrané"
        }

        fun refresh() {
            val items = TextLibraryStore.getLibrary(this)
            selectedIds.retainAll(items.map { it.id }.toSet())
            empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            recycler.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
            adapter.updateItems(items)
            refreshPlayButton()
        }

        adapter = LibraryAdapter(
            items = TextLibraryStore.getLibrary(this),
            selectedIds = selectedIds,
            onItemClick = { item ->
                loadSavedTextIntoEditor(item)
                dialog.dismiss()
            },
            onDeleteClick = { item ->
                AlertDialog.Builder(this)
                    .setTitle("Smazat text?")
                    .setMessage("„${item.title}“ bude natrvalo odstraněn z knihovny.")
                    .setPositiveButton("Smazat") { _, _ ->
                        TextLibraryStore.removeFromLibrary(this, item.id)
                        if (currentLibraryItemId == item.id) currentLibraryItemId = null
                        refresh()
                    }
                    .setNegativeButton("Zrušit", null)
                    .show()
            },
            onSelectionChanged = { refreshPlayButton() }
        )
        recycler.adapter = adapter
        refresh()

        btnPlaySelected.setOnClickListener {
            val allItems = TextLibraryStore.getLibrary(this)
            playSelectedFromLibrary(selectedIds, allItems)
            dialog.dismiss()
        }

        dialog = AlertDialog.Builder(this)
            .setTitle("Knihovna textů")
            .setView(view)
            .setNegativeButton("Zavřít", null)
            .create()
        dialog.show()
    }

    /**
     * Spojí vybrané texty z knihovny do jednoho souvislého textu (v pořadí, v jakém
     * jsou v knihovně) a rovnou spustí čtení - přehrají se tak jeden po druhém.
     */
    private fun playSelectedFromLibrary(selectedIds: Set<String>, allItems: List<SavedText>) {
        val ordered = allItems.filter { selectedIds.contains(it.id) }
        if (ordered.isEmpty()) return
        val combined = ordered.joinToString("\n\n") { it.content }
        currentLibraryItemId = null
        binding.etContent.setText(combined)
        binding.etContent.setSelection(0)
        startReadingFromCursor()
    }

    private fun loadSavedTextIntoEditor(item: SavedText) {
        service?.stopReading()
        clearHighlight()
        binding.etContent.setText(item.content)
        val pos = item.cursorPosition.coerceIn(0, item.content.length)
        binding.etContent.setSelection(pos)
        currentLibraryItemId = item.id
        Toast.makeText(this, "Načteno: ${item.title}", Toast.LENGTH_SHORT).show()
    }

    // --- Nastavení: hlasitost a výběr hlasu ---

    /** Menu se třemi tečkami v hlavičce. */
    private fun showMoreMenu(anchor: View) {
        val popup = android.widget.PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_more, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menuSettings -> {
                    showSettingsDialog()
                    true
                }
                R.id.menuHelp -> {
                    showHelpDialog()
                    true
                }
                R.id.menuClearLibrary -> {
                    confirmClearLibrary()
                    true
                }
                R.id.menuShareApp -> {
                    shareAppLink()
                    true
                }
                R.id.menuCheckUpdates -> {
                    openUrlInBrowser("https://github.com/marceldohnalcz/ChytraCtecka/releases/tag/latest-build")
                    true
                }
                R.id.menuAbout -> {
                    showAboutDialog()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showHelpDialog() {
        val message = """
            • Text jde sdílet z jiných appek přímo sem (tlačítko Sdílet v dané appce).

            • Označ text v libovolné appce a v nabídce vyber "Chytrá čtečka textu" - přečte přesně to, co jsi vybral.

            • Tlačítko Obrázek rozpozná text z fotky nebo screenshotu, funguje i offline.

            • Tlačítko Odkaz stáhne a přečte text z webové stránky. U Facebooku a Instagramu to kvůli technickým omezením těchto platforem obvykle nefunguje - tam radši označ text přímo v appce.

            • Klepnutím kamkoli do textu spustíš čtení přesně od té pozice.

            • V Nastavení jde změnit hlas, hlasitost čtení a rychlost.
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("Nápověda a tipy")
            .setMessage(message)
            .setPositiveButton("Zavřít", null)
            .show()
    }

    private fun confirmClearLibrary() {
        val items = TextLibraryStore.getLibrary(this)
        if (items.isEmpty()) {
            Toast.makeText(this, "Knihovna je už prázdná", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Vymazat celou knihovnu?")
            .setMessage("Smaže se všech ${items.size} uložených textů. Tuhle akci nejde vrátit zpět.")
            .setPositiveButton("Vymazat vše") { _, _ ->
                TextLibraryStore.clearLibrary(this)
                currentLibraryItemId = null
                Toast.makeText(this, "Knihovna vymazána", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Zrušit", null)
            .show()
    }

    private fun shareAppLink() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Chytrá čtečka textu")
            putExtra(
                Intent.EXTRA_TEXT,
                "Vyzkoušej Chytrou čtečku textu - appku, co ti nahlas přečte text, odkazy nebo i fotky:\n" +
                    "https://github.com/marceldohnalcz/ChytraCtecka/releases/tag/latest-build"
            )
        }
        startActivity(Intent.createChooser(shareIntent, "Sdílet appku"))
    }

    private fun openUrlInBrowser(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "Nepodařilo se otevřít prohlížeč", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAboutDialog() {
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "?"
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.app_name))
            .setMessage("Verze $versionName\n\nZdrojový kód a nejnovější verze:\ngithub.com/marceldohnalcz/ChytraCtecka")
            .setPositiveButton("Zavřít", null)
            .setNeutralButton("Otevřít GitHub") { _, _ ->
                openUrlInBrowser("https://github.com/marceldohnalcz/ChytraCtecka")
            }
            .show()
    }

    private fun showSettingsDialog() {
        val svc = service
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        val seekVolume = view.findViewById<SeekBar>(R.id.seekVolumeDialog)
        val radioGroup = view.findViewById<RadioGroup>(R.id.radioGroupVoices)
        val switchAutoResume = view.findViewById<android.widget.Switch>(R.id.switchAutoResume)

        switchAutoResume.isChecked = AppSettings.loadAutoResumeAfterCall(this)
        switchAutoResume.setOnCheckedChangeListener { _, checked ->
            AppSettings.saveAutoResumeAfterCall(this, checked)
            service?.setAutoResumeAfterInterruption(checked)
        }

        seekVolume.progress = (currentVolume * 100).toInt()
        seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentVolume = progress / 100f
                    service?.setVolume(currentVolume)
                    AppSettings.saveVolume(this@MainActivity, currentVolume)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        val voices = svc?.getAvailableCzechVoices().orEmpty()
        val currentVoiceName = svc?.getCurrentVoiceName()
        if (voices.isEmpty()) {
            val tv = TextView(this)
            tv.text = "Nenalezen žádný další český hlas. Zkontroluj Nastavení > Řeč v telefonu."
            tv.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            radioGroup.addView(tv)
        } else {
            voices.forEachIndexed { index, voice ->
                val radio = RadioButton(this)
                radio.text = voiceLabel(voice, index)
                radio.id = View.generateViewId()
                radio.isChecked = voice.name == currentVoiceName
                radio.setOnClickListener {
                    AppSettings.saveVoiceName(this, voice.name)
                    applyVoiceChange(voice)
                }
                radioGroup.addView(radio)
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Nastavení čtení")
            .setView(view)
            .setPositiveButton("Hotovo", null)
            .show()
    }

    private fun voiceLabel(voice: Voice, index: Int): String {
        val quality = when (voice.quality) {
            Voice.QUALITY_VERY_HIGH -> "velmi vysoká kvalita"
            Voice.QUALITY_HIGH -> "vysoká kvalita"
            Voice.QUALITY_NORMAL -> "normální kvalita"
            Voice.QUALITY_LOW -> "nízká kvalita"
            else -> "kvalita neznámá"
        }
        val network = if (voice.isNetworkConnectionRequired) " • potřebuje internet" else ""
        return "Hlas ${index + 1} ($quality$network)"
    }

    /**
     * Aplikuje nový hlas. Pokud appka právě čte, pokračuje přesně od aktuální
     * pozice novým hlasem (nerestartuje celý text od začátku).
     */
    private fun applyVoiceChange(voice: Voice) {
        val svc = service ?: return
        if (svc.isSpeaking()) {
            val pos = svc.currentAbsolutePosition()
            svc.pause()
            svc.setVoice(voice)
            placeCursorAt(pos)
            startReadingFromCursor()
        } else {
            svc.setVoice(voice)
        }
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
                binding.btnPlayPause.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.brand_pause)
                    )
            } else {
                binding.btnPlayPause.text = "Přehrát"
                binding.btnPlayPause.setIconResource(R.drawable.ic_play)
                binding.btnPlayPause.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.brand_play)
                    )
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

    // --- Zvýrazňování a auto-scroll čteného textu ---

    private fun highlightRange(start: Int, end: Int) {
        val text = binding.etContent.text ?: return
        if (start < 0 || end > text.length || start >= end) return
        clearHighlight()
        val span = BackgroundColorSpan(0xFFFFF176.toInt())
        highlightSpan = span
        (text as? Spannable)?.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        autoScrollToPosition(start)
    }

    /**
     * Posune textové pole tak, aby byl začátek právě čteného ODSTAVCE vždy
     * v horní třetině viditelné oblasti - ne jen poslední řádek na spodním okraji.
     * Posouvá se jen tehdy, když aktuální pozice čtení už není pohodlně vidět,
     * ať text neposkakuje při každé větě uvnitř stejného odstavce.
     */
    private fun autoScrollToPosition(position: Int) {
        val editText = binding.etContent
        val layout = editText.layout ?: return
        val fullText = editText.text?.toString() ?: return
        val len = fullText.length
        if (len == 0) return
        val pos = position.coerceIn(0, len)

        val visibleHeight = editText.height - editText.paddingTop - editText.paddingBottom
        if (visibleHeight <= 0) return

        val posLine = layout.getLineForOffset(pos)
        val posTop = layout.getLineTop(posLine)
        val posBottom = layout.getLineBottom(posLine)
        val scrollY = editText.scrollY

        val isComfortablyVisible = posTop >= scrollY && posBottom <= scrollY + visibleHeight
        if (isComfortablyVisible) return

        // Najdi začátek aktuálního odstavce (poslední prázdný řádek před pozicí, nebo začátek textu)
        val paragraphStartIndex = fullText.lastIndexOf("\n\n", (pos - 1).coerceAtLeast(0))
            .let { if (it == -1) 0 else it + 2 }
        val paragraphLine = layout.getLineForOffset(paragraphStartIndex.coerceIn(0, len))
        val paragraphTop = layout.getLineTop(paragraphLine)

        val maxScroll = (layout.height - visibleHeight).coerceAtLeast(0)
        val targetScrollY = (paragraphTop - visibleHeight / 3).coerceIn(0, maxScroll)
        editText.scrollTo(0, targetScrollY)
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
        currentLibraryItemId = null
        if (url != null && sharedText.trim() == url.trim()) {
            loadFromUrl(url)
        } else {
            binding.etContent.setText(sharedText)
            binding.etContent.setSelection(0)
        }
    }

    /** Otevře dialog pro ruční zadání odkazu na článek, který se má stáhnout a přečíst. */
    private fun showLinkInputDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "https://…"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI or android.text.InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        AlertDialog.Builder(this)
            .setTitle("Načíst text z odkazu")
            .setView(input)
            .setPositiveButton("Stáhnout") { _, _ ->
                var url = input.text.toString().trim()
                if (url.isNotBlank()) {
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "https://$url"
                    }
                    currentLibraryItemId = null
                    loadFromUrl(url)
                }
            }
            .setNegativeButton("Zrušit", null)
            .show()
    }

    /** Otevře dialog pro výběr, odkud vzít obrázek k rozpoznání textu (OCR). */
    private fun showImageSourceDialog() {
        val options = arrayOf("Vybrat z galerie", "Vyfotit fotoaparátem")
        AlertDialog.Builder(this)
            .setTitle("Rozpoznat text z obrázku")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickImageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                    1 -> captureImageFromCamera()
                }
            }
            .show()
    }

    private fun captureImageFromCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            launchCamera()
        }
    }

    private fun launchCamera() {
        val dir = File(cacheDir, "images").apply { mkdirs() }
        val imageFile = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", imageFile)
        pendingCameraUri = uri
        takePictureLauncher.launch(uri)
    }

    /** Rozpozná text z obrázku (ML Kit, na zařízení) a vloží ho do textového pole. */
    private fun processImageForOcr(uri: Uri) {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val recognized = try {
                withContext(Dispatchers.IO) { ImageTextRecognizer.recognize(this@MainActivity, uri) }
            } catch (e: Exception) {
                null
            }
            binding.progress.visibility = View.GONE
            if (recognized.isNullOrBlank()) {
                Toast.makeText(
                    this@MainActivity,
                    "V obrázku se nepodařilo najít žádný text. Zkus ostřejší nebo lépe osvětlenou fotku.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                currentLibraryItemId = null
                binding.etContent.setText(recognized)
                binding.etContent.setSelection(0)
                Toast.makeText(
                    this@MainActivity,
                    "Text rozpoznán (${recognized.length} znaků)",
                    Toast.LENGTH_SHORT
                ).show()
            }
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
