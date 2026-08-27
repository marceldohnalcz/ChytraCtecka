package io.github.marciano.smartreader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat

/**
 * Foreground služba, která drží TTS engine mimo Activity - díky tomu čtení
 * pokračuje i se zhasnutou obrazovkou nebo když appku přepneš na pozadí.
 * Zobrazuje trvalou notifikaci s tlačítky Přehrát/Pauza a Stop a přes
 * MediaSession nabízí i NATIVNÍ ovládání na zamykací obrazovce (stejný
 * widget, jaký znáš ze Spotify/YouTube Music), včetně reakce na sluchátková
 * tlačítka.
 *
 * Zároveň při čtení požádá o "audio focus", díky čemuž se ostatní přehrávače
 * (YouTube, Spotify apod.) na dobu čtení samy pozastaví a po dočtení se
 * obvykle samy pustí zpátky.
 */
class ReadingService : Service() {

    companion object {
        const val CHANNEL_ID = "reading_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY_PAUSE = "io.github.marciano.smartreader.action.PLAY_PAUSE"
        const val ACTION_STOP = "io.github.marciano.smartreader.action.STOP"
    }

    interface Listener {
        fun onWordRange(start: Int, end: Int)
        fun onStateChanged(isSpeaking: Boolean, isPaused: Boolean)
        fun onError(message: String)
    }

    private val binder = LocalBinder()
    private var listener: Listener? = null
    private lateinit var ttsManager: TtsManager
    private lateinit var mediaSession: MediaSessionCompat
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var autoResumeAfterInterruption = false
    private var pausedDueToInterruption = false
    private var currentEnginePackage: String? = null

    // Stažený (Piper) hlas jako alternativa k systémovému TTS - null znamená
    // "čti systémovým hlasem" (výchozí chování, jako doteď).
    private var activePiperVoiceId: String? = null
    private var piperSession: PiperReadingSession? = null
    private var piperSessionVoiceId: String? = null
    private var lastSpeed = 1.0f
    private var lastVolume = 1.0f

    /** true jen pokud je vybraný Piper hlas OPRAVDU stažený - kdyby ho uživatel
     *  mezitím smazal, appka se sama bezpečně vrátí k systémovému TTS. */
    private fun isPiperVoiceReady(): Boolean {
        val id = activePiperVoiceId ?: return false
        return PiperVoiceStore.isDownloaded(this, id)
    }

    private fun ensurePiperSession(): PiperReadingSession? {
        val id = activePiperVoiceId ?: return null
        if (piperSession != null && piperSessionVoiceId == id) return piperSession
        piperSession?.shutdown()
        val session = PiperReadingSession(
            voiceDir = PiperVoiceStore.voiceDir(this, id),
            onSentenceStart = { s, e -> listener?.onWordRange(s, e) },
            onDone = {
                abandonAudioFocus()
                updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
                listener?.onStateChanged(false, false)
                stopForegroundAndSelf()
            },
            onError = { msg -> listener?.onError(msg) }
        )
        session.setSpeed(lastSpeed)
        session.setVolume(lastVolume)
        piperSession = session
        piperSessionVoiceId = id
        return session
    }

    /** Vybere Piper hlas pro čtení (voiceId) nebo se vrátí k systémovému TTS (null). */
    fun setActivePiperVoice(voiceId: String?) {
        if (activePiperVoiceId == voiceId) return
        stopReading()
        activePiperVoiceId = voiceId
        AppSettings.saveActivePiperVoice(this, voiceId)
        piperSession?.shutdown()
        piperSession = null
        piperSessionVoiceId = null
    }

    fun getActivePiperVoiceId(): String? = activePiperVoiceId

    inner class LocalBinder : Binder() {
        fun getService(): ReadingService = this@ReadingService
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        currentEnginePackage = AppSettings.loadTtsEngine(this)
        ttsManager = createTtsManager(currentEnginePackage)
        activePiperVoiceId = AppSettings.loadActivePiperVoice(this)
        createNotificationChannel()
        setupMediaSession()
    }

    private fun createTtsManager(enginePackageName: String?, onErrorOverride: ((String) -> Unit)? = null, onReadyOverride: (() -> Unit)? = null): TtsManager = TtsManager(
        context = this,
        enginePackageName = enginePackageName,
        onWordRange = { s, e -> listener?.onWordRange(s, e) },
        onDone = {
            abandonAudioFocus()
            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
            listener?.onStateChanged(false, false)
            stopForegroundAndSelf()
        },
        onError = { msg -> onErrorOverride?.invoke(msg) ?: listener?.onError(msg) },
        onReady = { onReadyOverride?.invoke() }
    )

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> handleNotificationPlayPause()
            ACTION_STOP -> stopReading()
        }
        return START_NOT_STICKY
    }

    fun setListener(l: Listener?) {
        listener = l
    }

    fun isSpeaking() = if (isPiperVoiceReady()) piperSession?.isSpeaking == true else ttsManager.isSpeaking
    fun isPaused() = if (isPiperVoiceReady()) piperSession?.isPaused == true else ttsManager.isPaused
    fun currentAbsolutePosition() =
        if (isPiperVoiceReady()) (piperSession?.currentAbsolutePosition() ?: 0) else ttsManager.currentAbsolutePosition()
    fun setSpeed(rate: Float) {
        lastSpeed = rate
        ttsManager.setSpeed(rate)
        piperSession?.setSpeed(rate)
    }
    fun setPitch(pitch: Float) = ttsManager.setPitch(pitch) // Piper hlasy výšku nepodporují
    fun setVolume(v: Float) {
        lastVolume = v
        ttsManager.setVolume(v)
        piperSession?.setVolume(v)
    }
    fun getAvailableVoicesForCurrentLanguage() = ttsManager.getAvailableVoicesForCurrentLanguage()
    fun getCurrentVoiceName(): String? = ttsManager.getCurrentVoiceName()
    fun setVoice(voice: android.speech.tts.Voice) = ttsManager.setVoice(voice)

    fun listInstalledTtsEngines(): List<TtsManager.EngineChoice> = TtsManager.listInstalledEngines(this)
    fun getCurrentEnginePackage(): String? = currentEnginePackage

    /**
     * Přepne appku na jiný hlasový modul (engine) - kompletně zastaví
     * a znovu vytvoří [ttsManager] s novým modulem. Případné právě probíhající
     * čtení se zastaví (jiný modul může mít úplně jiné hlasy/rychlost, takže
     * nedává smysl se snažit plynule navázat) - [onReady] zavolá zpátky, až je
     * nový modul připravený, ať appka může nabídnout jeho hlasy k výběru.
     */
    fun switchTtsEngine(packageName: String?, onReady: () -> Unit, onFailed: (String) -> Unit) {
        stopReading()
        ttsManager.shutdown()
        currentEnginePackage = packageName
        AppSettings.saveTtsEngine(this, packageName)
        ttsManager = createTtsManager(packageName, onErrorOverride = onFailed, onReadyOverride = onReady)
    }

    fun setAutoResumeAfterInterruption(enabled: Boolean) {
        autoResumeAfterInterruption = enabled
    }

    /** Spustí čtení daného textu. Vždy volá "čerstvě" - MainActivity si sama hlídá, odkud má číst. */
    fun speak(text: String, baseOffset: Int) {
        if (text.isBlank()) return
        requestAudioFocus()
        updateMetadata(text)
        mediaSession.isActive = true
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        startForeground(NOTIFICATION_ID, buildNotification(isPlaying = true))
        if (isPiperVoiceReady()) {
            ensurePiperSession()?.speak(text, baseOffset)
        } else {
            ttsManager.speak(text, baseOffset)
        }
        listener?.onStateChanged(true, false)
    }

    fun pause() {
        if (isPiperVoiceReady()) piperSession?.pause() else ttsManager.pause()
        pausedDueToInterruption = false
        abandonAudioFocus()
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
        listener?.onStateChanged(false, true)
        updateNotification()
    }

    /**
     * Pauza vyvolaná systémem (např. příchozí hovor) - na rozdíl od pause() NEZAHAZUJE
     * audio focus, ať appka dostane AUDIOFOCUS_GAIN zpátky, jakmile hovor skončí, a může
     * se (pokud je to zapnuté v nastavení) sama obnovit.
     */
    private fun pauseForInterruption() {
        if (isPiperVoiceReady()) piperSession?.pause() else ttsManager.pause()
        pausedDueToInterruption = true
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
        listener?.onStateChanged(false, true)
        updateNotification()
    }

    fun stopReading() {
        if (isPiperVoiceReady()) piperSession?.stop() else ttsManager.stop()
        abandonAudioFocus()
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        mediaSession.isActive = false
        listener?.onStateChanged(false, false)
        stopForegroundAndSelf()
    }

    private fun handleNotificationPlayPause() {
        if (isSpeaking()) {
            pause()
        } else if (isPaused()) {
            resumeFromPause()
        }
    }

    private fun resumeFromPause() {
        requestAudioFocus()
        if (isPiperVoiceReady()) piperSession?.resume() else ttsManager.resume()
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        startForeground(NOTIFICATION_ID, buildNotification(isPlaying = true))
        listener?.onStateChanged(true, false)
    }

    // --- MediaSession (nativní ovládání na zamykací obrazovce + sluchátka) ---

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "ChytraCteckaSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    if (isPaused()) resumeFromPause()
                }
                override fun onPause() {
                    if (isSpeaking()) pause()
                }
                override fun onStop() {
                    stopReading()
                }
            })
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            packageManager.getLaunchIntentForPackage(packageName)?.let { openIntent ->
                setSessionActivity(
                    PendingIntent.getActivity(
                        this@ReadingService, 3, openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
        }
        updatePlaybackState(PlaybackStateCompat.STATE_NONE)
    }

    private fun updatePlaybackState(state: Int) {
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_STOP
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    private fun updateMetadata(text: String) {
        val snippet = text.trim().take(60).let { if (text.length > 60) "$it…" else it }
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, snippet.ifBlank { getString(R.string.app_name) })
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, getString(R.string.app_name))
            .build()
        mediaSession.setMetadata(metadata)
    }

    // --- Audio focus (zastavení jiných přehrávačů) ---

    /**
     * AudioFocusRequest se vytváří JEDNOU a dál se opakovaně používá pro
     * všechny další žádosti/vzdání se focusu - to je doporučený postup od
     * Googlu. Dřív appka vytvářela úplně nový objekt při každém volání
     * requestAudioFocus(), což u některých výrobců (zaznamenáno hlavně po
     * přerušení jinou appkou jako Facebook/Instagram) mohlo appku dostat do
     * stavu, kdy si po přerušení nešlo znovu vzít focus zpátky a přehrávání
     * se už nedalo spustit, dokud appku někdo ručně nevynutil zavřít.
     */
    private fun getOrCreateFocusRequest(): AudioFocusRequest {
        focusRequest?.let { return it }
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        // AUDIOFOCUS_GAIN_TRANSIENT (bez MAY_DUCK) - slušně napsané appky (YouTube,
        // Spotify apod.) na tohle reagují úplnou pauzou, ne jen ztišením hlasitosti.
        // Po dočtení appka focus pustí (abandonAudioFocus) a ony se obvykle samy
        // pustí zpátky, protože si pamatují, že předtím hrály.
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { focusChange -> handleAudioFocusChange(focusChange) }
            .build()
        focusRequest = request
        return request
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Typicky telefonát - dočasné přerušení, focus si necháme "podaný",
                // ať víme, až se nám vrátí (AUDIOFOCUS_GAIN).
                if (isSpeaking()) {
                    pauseForInterruption()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Trvalá ztráta (jiná appka - třeba Facebook/Instagram - převzala
                // přehrávání natrvalo). Systém nám focus už sám odebral, takže
                // NEvoláme abandonAudioFocus() znovu (zbytečné volání uvnitř
                // tohohle callbacku dřív mohlo appku dostat do stavu, kdy si
                // focus nešlo vzít zpátky) - jen zastavíme čtení a aktualizujeme stav.
                pausedDueToInterruption = false
                if (isPiperVoiceReady()) piperSession?.pause() else ttsManager.pause()
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                listener?.onStateChanged(false, true)
                updateNotification()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (pausedDueToInterruption) {
                    pausedDueToInterruption = false
                    if (autoResumeAfterInterruption && isPaused()) {
                        resumeFromPause()
                    } else {
                        abandonAudioFocus()
                    }
                }
            }
        }
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        am.requestAudioFocus(getOrCreateFocusRequest())
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        focusRequest?.let { am.abandonAudioFocusRequest(it) }
    }

    // --- Notifikace ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(isPlaying: Boolean): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val playPauseIntent = Intent(this, ReadingService::class.java).setAction(ACTION_PLAY_PAUSE)
        val playPausePending = PendingIntent.getService(this, 0, playPauseIntent, flags)

        val stopIntent = Intent(this, ReadingService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(this, 1, stopIntent, flags)

        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPending = openAppIntent?.let {
            PendingIntent.getActivity(this, 2, it, flags)
        }

        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val playPauseLabel = if (isPlaying) getString(R.string.btn_pause) else getString(R.string.btn_play)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_read)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(if (isPlaying) getString(R.string.notification_reading) else getString(R.string.notification_paused))
            .addAction(playPauseIcon, playPauseLabel, playPausePending)
            .addAction(R.drawable.ic_stop, getString(R.string.btn_stop), stopPending)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )

        contentPending?.let { builder.setContentIntent(it) }
        return builder.build()
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(isPlaying = isSpeaking()))
    }

    private fun stopForegroundAndSelf() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    /**
     * Zavolá se, když uživatel appku smaže ze seznamu spuštěných appek
     * (přehodí ji do koše v přehledu naposledy spuštěných appek). Appka se má
     * v tenhle okamžik skutečně ukončit a přestat číst - ne pokračovat na
     * pozadí, jak by to dělal třeba hudební přehrávač.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopReading()
    }

    override fun onDestroy() {
        ttsManager.shutdown()
        piperSession?.shutdown()
        abandonAudioFocus()
        mediaSession.release()
        super.onDestroy()
    }
}
