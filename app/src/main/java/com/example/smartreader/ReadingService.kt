package com.example.smartreader

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
 * Zároveň při čtení požádá o "audio focus" typu MAY_DUCK, díky čemuž
 * ostatní přehrávače automaticky ztiší hlasitost po dobu čtení.
 */
class ReadingService : Service() {

    companion object {
        const val CHANNEL_ID = "reading_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY_PAUSE = "com.example.smartreader.action.PLAY_PAUSE"
        const val ACTION_STOP = "com.example.smartreader.action.STOP"
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

    inner class LocalBinder : Binder() {
        fun getService(): ReadingService = this@ReadingService
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        ttsManager = TtsManager(
            context = this,
            onWordRange = { s, e -> listener?.onWordRange(s, e) },
            onDone = {
                abandonAudioFocus()
                updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
                listener?.onStateChanged(false, false)
                stopForegroundAndSelf()
            },
            onError = { msg -> listener?.onError(msg) },
            onReady = {}
        )
        createNotificationChannel()
        setupMediaSession()
    }

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

    fun isSpeaking() = ttsManager.isSpeaking
    fun isPaused() = ttsManager.isPaused
    fun currentAbsolutePosition() = ttsManager.currentAbsolutePosition()
    fun setSpeed(rate: Float) = ttsManager.setSpeed(rate)
    fun setVolume(v: Float) = ttsManager.setVolume(v)
    fun getAvailableCzechVoices() = ttsManager.getAvailableCzechVoices()
    fun getCurrentVoiceName(): String? = ttsManager.getCurrentVoiceName()
    fun setVoice(voice: android.speech.tts.Voice) = ttsManager.setVoice(voice)

    /** Spustí čtení daného textu. Vždy volá "čerstvě" - MainActivity si sama hlídá, odkud má číst. */
    fun speak(text: String, baseOffset: Int) {
        if (text.isBlank()) return
        requestAudioFocus()
        updateMetadata(text)
        mediaSession.isActive = true
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        startForeground(NOTIFICATION_ID, buildNotification(isPlaying = true))
        ttsManager.speak(text, baseOffset)
        listener?.onStateChanged(true, false)
    }

    fun pause() {
        ttsManager.pause()
        abandonAudioFocus()
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
        listener?.onStateChanged(false, true)
        updateNotification()
    }

    fun stopReading() {
        ttsManager.stop()
        abandonAudioFocus()
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        mediaSession.isActive = false
        listener?.onStateChanged(false, false)
        stopForegroundAndSelf()
    }

    private fun handleNotificationPlayPause() {
        if (ttsManager.isSpeaking) {
            pause()
        } else if (ttsManager.isPaused) {
            resumeFromPause()
        }
    }

    private fun resumeFromPause() {
        requestAudioFocus()
        ttsManager.resume()
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        startForeground(NOTIFICATION_ID, buildNotification(isPlaying = true))
        listener?.onStateChanged(true, false)
    }

    // --- MediaSession (nativní ovládání na zamykací obrazovce + sluchátka) ---

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "ChytraCteckaSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    if (ttsManager.isPaused) resumeFromPause()
                }
                override fun onPause() {
                    if (ttsManager.isSpeaking) pause()
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

    // --- Audio focus / ducking ---

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { focusChange ->
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                    focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                ) {
                    pause()
                }
            }
            .build()
        focusRequest = request
        am.requestAudioFocus(request)
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        focusRequest?.let { am.abandonAudioFocusRequest(it) }
    }

    // --- Notifikace ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Čtení textu", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ovládání čtení na pozadí"
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
        val playPauseLabel = if (isPlaying) "Pauza" else "Přehrát"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_read)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(if (isPlaying) "Čtu text nahlas…" else "Čtení pozastaveno")
            .addAction(playPauseIcon, playPauseLabel, playPausePending)
            .addAction(R.drawable.ic_stop, "Stop", stopPending)
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
        nm.notify(NOTIFICATION_ID, buildNotification(isPlaying = ttsManager.isSpeaking))
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

    override fun onDestroy() {
        ttsManager.shutdown()
        abandonAudioFocus()
        mediaSession.release()
        super.onDestroy()
    }
}
