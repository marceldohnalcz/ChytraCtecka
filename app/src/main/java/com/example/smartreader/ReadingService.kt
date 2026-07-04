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
import androidx.core.app.NotificationCompat

/**
 * Foreground služba, která drží TTS engine mimo Activity - díky tomu čtení
 * pokračuje i se zhasnutou obrazovkou nebo když appku přepneš na pozadí.
 * Zobrazuje trvalou notifikaci s tlačítky Přehrát/Pauza a Stop.
 *
 * Zároveň při čtení požádá o "audio focus" typu MAY_DUCK, díky čemuž
 * ostatní přehrávače (Spotify, YouTube Music...) automaticky ztiší hlasitost
 * po dobu čtení, místo aby se úplně zastavily.
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
                listener?.onStateChanged(false, false)
                stopForegroundAndSelf()
            },
            onError = { msg -> listener?.onError(msg) },
            onReady = {}
        )
        createNotificationChannel()
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

    /** Spustí čtení daného textu. Vždy volá "čerstvě" - MainActivity si sama hlídá, odkud má číst. */
    fun speak(text: String, baseOffset: Int) {
        if (text.isBlank()) return
        requestAudioFocus()
        startForeground(NOTIFICATION_ID, buildNotification(isPlaying = true))
        ttsManager.speak(text, baseOffset)
        listener?.onStateChanged(true, false)
    }

    fun pause() {
        ttsManager.pause()
        abandonAudioFocus()
        listener?.onStateChanged(false, true)
        updateNotification()
    }

    fun stopReading() {
        ttsManager.stop()
        abandonAudioFocus()
        listener?.onStateChanged(false, false)
        stopForegroundAndSelf()
    }

    private fun handleNotificationPlayPause() {
        if (ttsManager.isSpeaking) {
            pause()
        } else if (ttsManager.isPaused) {
            requestAudioFocus()
            ttsManager.resume()
            startForeground(NOTIFICATION_ID, buildNotification(isPlaying = true))
            listener?.onStateChanged(true, false)
        }
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        val attrs = AudioAttributes.Builder()
            // Tento typ Android speciálně chápe jako "krátké mluvené oznámení" a
            // ostatní hudební appky na něj obvykle reagují ztišením (duckingem).
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { focusChange ->
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                    focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                ) {
                    // Např. telefonát nebo appka, co nechce duckovat - raději pauza než přeřvávání.
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
        super.onDestroy()
    }
}
