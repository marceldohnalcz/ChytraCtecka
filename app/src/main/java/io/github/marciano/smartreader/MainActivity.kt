package io.github.marciano.smartreader

import android.Manifest
import android.annotation.SuppressLint
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
import io.github.marciano.smartreader.databinding.ActivityMainBinding
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
                Toast.makeText(this, getString(R.string.toast_no_camera_access), Toast.LENGTH_SHORT).show()
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
                service?.getAvailableVoicesForCurrentLanguage()?.find { it.name == name }?.let { service?.setVoice(it) }
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
        setupScrollDragHandle()
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
        binding.btnHistory.setOnClickListener { showHistoryDialog() }
        binding.btnLink.setOnClickListener { showLinkInputDialog() }
        binding.btnImage.setOnClickListener { showImageSourceDialog() }
    }

    /**
     * Neviditelná dotyková vrstva přes pravý okraj textového pole. Na rozdíl od
     * systémového scrollbaru (jen vizuální indikátor, nejde chytit prstem) tohle
     * skutečně reaguje na dotek - kdekoli v pruhu dotkneš/táhneš prstem, text se
     * posune na odpovídající pozici (poměr pozice prstu v pruhu = poměr pozice
     * v textu).
     */
    @SuppressLint("ClickableViewAccessibility")
    // Kolikrát rychleji se text posune oproti tomu, o kolik se posune prst -
    // čisté 1:1 mapování bylo na dlouhé texty vnímané jako pomalé.
    private val SCROLL_DRAG_SPEED_MULTIPLIER = 3.5f
    private var lastScrollDragY = 0f
    private var isUserDraggingScroll = false

    private fun setupScrollDragHandle() {
        binding.scrollDragHandle.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    isUserDraggingScroll = true
                    lastScrollDragY = event.y
                    // Rovnou skoč zhruba na odpovídající místo podle pozice doteku
                    scrollTextToTouchRatio(event.y / view.height.toFloat())
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.y - lastScrollDragY
                    lastScrollDragY = event.y
                    scrollTextByDelta(deltaY * SCROLL_DRAG_SPEED_MULTIPLIER)
                    true
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    isUserDraggingScroll = false
                    true
                }
                else -> false
            }
        }
    }

    private fun scrollTextToTouchRatio(rawRatio: Float) {
        val editText = binding.etContent
        val layout = editText.layout ?: return
        val visibleHeight = editText.height - editText.paddingTop - editText.paddingBottom
        val maxScroll = (layout.height - visibleHeight).coerceAtLeast(0)
        if (maxScroll <= 0) return
        val ratio = rawRatio.coerceIn(0f, 1f)
        editText.scrollTo(0, (ratio * maxScroll).toInt())
    }

    private fun scrollTextByDelta(deltaY: Float) {
        val editText = binding.etContent
        val layout = editText.layout ?: return
        val visibleHeight = editText.height - editText.paddingTop - editText.paddingBottom
        val maxScroll = (layout.height - visibleHeight).coerceAtLeast(0)
        if (maxScroll <= 0) return
        val newScroll = (editText.scrollY + deltaY.toInt()).coerceIn(0, maxScroll)
        editText.scrollTo(0, newScroll)
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
            Toast.makeText(this, getString(R.string.toast_clipboard_empty), Toast.LENGTH_SHORT).show()
        } else {
            currentLibraryItemId = null
            val url = TextPreprocessor.extractFirstUrl(text)
            if (url != null && text.trim() == url.trim()) {
                loadFromUrl(url)
            } else {
                binding.etContent.setText(text)
                binding.etContent.setSelection(0)
                recordPasteInHistory(text)
            }
        }
    }

    private fun recordPasteInHistory(text: String) {
        if (!AppSettings.loadHistoryEnabled(this)) return
        if (text.isBlank()) return
        ReadingHistoryStore.addEntry(this, text)
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

    private fun recordHistoryIfEnabled(text: String) {
        if (!AppSettings.loadHistoryEnabled(this)) return
        if (text.isBlank()) return
        ReadingHistoryStore.markPlayedByContent(this, text)
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
            Toast.makeText(this, getString(R.string.toast_enter_text_first), Toast.LENGTH_SHORT).show()
            return
        }
        val cursor = binding.etContent.selectionStart.coerceIn(0, liveText.length)
        val remaining = liveText.substring(cursor)
        // Rozepisování zkratek si TextPreprocessor teď řeší sám podle aktuálního
        // jazyka zařízení (viz ABBREVIATIONS_BY_LANGUAGE) - žádné ruční omezení
        // na konkrétní jazyk už tady není potřeba.
        val cleaned = TextPreprocessor.clean(remaining)

        if (cleaned.isBlank()) {
            Toast.makeText(this, getString(R.string.toast_nothing_to_read_from_cursor), Toast.LENGTH_SHORT).show()
            return
        }

        if (cleaned != remaining) {
            binding.etContent.text?.replace(cursor, liveText.length, cleaned)
        }
        placeCursorAt(cursor)

        // Zaznamenat do historie hned v okamžiku spuštění čtení (klepnutí na Přehrát),
        // ne až po dokončení - i částečné poslechnutí se tak počítá jako "přehráno".
        recordHistoryIfEnabled(liveText)

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
            Toast.makeText(this, getString(R.string.toast_nothing_to_save), Toast.LENGTH_SHORT).show()
            return
        }
        val saved = TextLibraryStore.addToLibrary(this, text)
        currentLibraryItemId = saved.id
        Toast.makeText(this, getString(R.string.toast_saved_to_library), Toast.LENGTH_SHORT).show()
    }

    private fun showLibraryDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_library, null)
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerLibrary)
        val empty = view.findViewById<TextView>(R.id.tvLibraryEmpty)
        val btnPlaySelected = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPlaySelected)
        val btnCloseLibrary = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCloseLibrary)
        recycler.layoutManager = LinearLayoutManager(this)

        lateinit var dialog: AlertDialog
        lateinit var adapter: LibraryAdapter
        val selectedIds = mutableSetOf<String>()

        fun refreshPlayButton() {
            val count = selectedIds.size
            btnPlaySelected.isEnabled = count > 0
            btnPlaySelected.text = if (count > 0) getString(R.string.btn_play_selected_count, count) else getString(R.string.btn_play_selected)
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
                    .setTitle(getString(R.string.dialog_title_delete_text))
                    .setMessage(getString(R.string.dialog_msg_delete_text, item.title))
                    .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                        TextLibraryStore.removeFromLibrary(this, item.id)
                        if (currentLibraryItemId == item.id) currentLibraryItemId = null
                        refresh()
                    }
                    .setNegativeButton(getString(R.string.btn_cancel), null)
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
            .setView(view)
            .create()

        btnCloseLibrary.setOnClickListener { dialog.dismiss() }
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
        Toast.makeText(this, getString(R.string.toast_loaded_named, item.title), Toast.LENGTH_SHORT).show()
    }

    // --- Historie čtení ---

    private fun showHistoryDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_history, null)
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerHistory)
        val empty = view.findViewById<TextView>(R.id.tvHistoryEmpty)
        val switchEnabled = view.findViewById<android.widget.Switch>(R.id.switchHistoryEnabledInDialog)
        val btnClearHistory = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnClearHistory)
        val btnCloseHistory = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCloseHistory)
        recycler.layoutManager = LinearLayoutManager(this)

        switchEnabled.isChecked = AppSettings.loadHistoryEnabled(this)
        switchEnabled.setOnCheckedChangeListener { _, checked ->
            AppSettings.saveHistoryEnabled(this, checked)
        }

        lateinit var dialog: AlertDialog
        lateinit var adapter: HistoryAdapter

        fun refresh() {
            val items = ReadingHistoryStore.getHistory(this)
            empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            recycler.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
            adapter.updateItems(items)
        }

        adapter = HistoryAdapter(
            items = ReadingHistoryStore.getHistory(this),
            onItemClick = { item ->
                currentLibraryItemId = null
                binding.etContent.setText(item.content)
                binding.etContent.setSelection(0)
                dialog.dismiss()
            },
            onDeleteClick = { item ->
                ReadingHistoryStore.removeEntry(this, item.id)
                refresh()
            }
        )
        recycler.adapter = adapter
        refresh()

        btnClearHistory.setOnClickListener {
            val items = ReadingHistoryStore.getHistory(this)
            if (items.isEmpty()) {
                Toast.makeText(this, getString(R.string.toast_history_already_empty), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showConfirmDialog(
                title = getString(R.string.dialog_title_clear_history),
                message = getString(R.string.dialog_msg_clear_history, items.size)
            ) {
                ReadingHistoryStore.clearHistory(this)
                refresh()
            }
        }

        dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        btnCloseHistory.setOnClickListener { dialog.dismiss() }
        dialog.show()
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
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_title_help))
            .setMessage(getString(R.string.help_message))
            .setPositiveButton(getString(R.string.btn_close), null)
            .show()
    }

    private fun confirmClearLibrary() {
        val items = TextLibraryStore.getLibrary(this)
        if (items.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_library_already_empty), Toast.LENGTH_SHORT).show()
            return
        }
        showConfirmDialog(
            title = getString(R.string.dialog_title_clear_library),
            message = getString(R.string.dialog_msg_clear_library, items.size)
        ) {
            TextLibraryStore.clearLibrary(this)
            currentLibraryItemId = null
            Toast.makeText(this, getString(R.string.toast_library_cleared), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Silnější potvrzení pro nevratné akce (smazání celé knihovny/historie) -
     * tlačítko potvrzení je neaktivní, dokud uživatel nenapíše přesně slovo
     * "SMAZAT". Chrání proti omylem odklepnutému běžnému Ano/Ne dialogu.
     */
    /** Jednoduché potvrzení nevratné akce tlačítkem Ano/Zrušit. */
    /** Potvrzení nevratné akce - Zrušit vlevo, Ano/vymazat vpravo s rozestupem mezi nimi. */
    private fun showConfirmDialog(title: String, message: String, onConfirmed: () -> Unit) {
        val view = layoutInflater.inflate(R.layout.dialog_confirm_action, null)
        view.findViewById<TextView>(R.id.tvConfirmTitle).text = title
        view.findViewById<TextView>(R.id.tvConfirmMessage).text = message

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirmCancel)
            .setOnClickListener { dialog.dismiss() }
        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirmOk)
            .setOnClickListener {
                onConfirmed()
                dialog.dismiss()
            }
        dialog.show()
    }

    private fun shareAppLink() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
            putExtra(
                Intent.EXTRA_TEXT,
                getString(
                    R.string.share_app_text,
                    getString(R.string.app_name),
                    "https://github.com/marceldohnalcz/ChytraCtecka/releases/tag/latest-build"
                )
            )
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_app_chooser_title)))
    }

    private fun openUrlInBrowser(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_failed_open_browser), Toast.LENGTH_SHORT).show()
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
            .setMessage(getString(R.string.about_message, versionName))
            .setPositiveButton(getString(R.string.btn_close), null)
            .setNeutralButton(getString(R.string.btn_open_github)) { _, _ ->
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
        val switchHistoryEnabled = view.findViewById<android.widget.Switch>(R.id.switchHistoryEnabled)

        switchAutoResume.isChecked = AppSettings.loadAutoResumeAfterCall(this)
        switchAutoResume.setOnCheckedChangeListener { _, checked ->
            AppSettings.saveAutoResumeAfterCall(this, checked)
            service?.setAutoResumeAfterInterruption(checked)
        }

        switchHistoryEnabled.isChecked = AppSettings.loadHistoryEnabled(this)
        switchHistoryEnabled.setOnCheckedChangeListener { _, checked ->
            AppSettings.saveHistoryEnabled(this, checked)
        }

        val radioGroupTheme = view.findViewById<RadioGroup>(R.id.radioGroupTheme)
        when (AppSettings.loadThemeMode(this)) {
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO ->
                radioGroupTheme.check(R.id.radioThemeLight)
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES ->
                radioGroupTheme.check(R.id.radioThemeDark)
            else ->
                radioGroupTheme.check(R.id.radioThemeSystem)
        }
        radioGroupTheme.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radioThemeLight -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                R.id.radioThemeDark -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppSettings.saveThemeMode(this, mode)
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
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

        val voices = svc?.getAvailableVoicesForCurrentLanguage().orEmpty()
        val currentVoiceName = svc?.getCurrentVoiceName()
        if (voices.isEmpty()) {
            val tv = TextView(this)
            tv.text = getString(R.string.voice_none_found)
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
            .setTitle(getString(R.string.dialog_title_settings))
            .setView(view)
            .setPositiveButton(getString(R.string.btn_done), null)
            .show()
    }

    private fun voiceLabel(voice: Voice, index: Int): String {
        val quality = when (voice.quality) {
            Voice.QUALITY_VERY_HIGH -> getString(R.string.voice_quality_very_high)
            Voice.QUALITY_HIGH -> getString(R.string.voice_quality_high)
            Voice.QUALITY_NORMAL -> getString(R.string.voice_quality_normal)
            Voice.QUALITY_LOW -> getString(R.string.voice_quality_low)
            else -> getString(R.string.voice_quality_unknown)
        }
        val network = if (voice.isNetworkConnectionRequired) getString(R.string.voice_needs_internet) else ""
        return getString(R.string.voice_label, index + 1, quality, network)
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
                binding.btnPlayPause.text = getString(R.string.btn_pause)
                binding.btnPlayPause.setIconResource(R.drawable.ic_pause)
                binding.btnPlayPause.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.brand_pause)
                    )
            } else {
                binding.btnPlayPause.text = getString(R.string.btn_play)
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
        if (isUserDraggingScroll) return
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
            hint = getString(R.string.hint_link_input)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI or android.text.InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_title_link_input))
            .setView(input)
            .setPositiveButton(getString(R.string.btn_download)) { _, _ ->
                var url = input.text.toString().trim()
                if (url.isNotBlank()) {
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "https://$url"
                    }
                    currentLibraryItemId = null
                    loadFromUrl(url)
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    /** Otevře dialog pro výběr, odkud vzít obrázek k rozpoznání textu (OCR). */
    private fun showImageSourceDialog() {
        val options = arrayOf(getString(R.string.option_pick_gallery), getString(R.string.option_take_photo))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_title_image_source))
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
                    getString(R.string.toast_ocr_no_text_found),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                currentLibraryItemId = null
                binding.etContent.setText(recognized)
                binding.etContent.setSelection(0)
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.toast_ocr_recognized_chars, recognized.length),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun loadFromUrl(url: String) {
        binding.progress.visibility = View.VISIBLE
        binding.etContent.setText(getString(R.string.toast_stahuji_stranku))
        lifecycleScope.launch {
            val extracted = withContext(Dispatchers.IO) { WebArticleExtractor.extractText(url) }
            binding.progress.visibility = View.GONE
            if (extracted.isNullOrBlank()) {
                binding.etContent.setText("")
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.toast_url_extract_failed, getString(R.string.app_name)),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                binding.etContent.setText(extracted)
                binding.etContent.setSelection(0)
                recordPasteInHistory(extracted)
            }
        }
    }
}
