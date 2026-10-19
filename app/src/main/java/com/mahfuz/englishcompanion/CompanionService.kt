package com.mahfuz.englishcompanion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

/**
 * Keeps the companion alive when the app is minimised or the screen is off.
 * Shows a media-style player on the lock screen / notification shade with Pause, Resume and Stop.
 * Pressing play/pause on earbuds also pauses/resumes (when the phone sends the button to this app).
 */
class CompanionService : Service() {
    private var listener: AudioListener? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var session: MediaSession? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopEverything(); return START_NOT_STICKY }
            ACTION_PAUSE -> { if (listener != null) Engine.micPaused = true; return START_STICKY }
            ACTION_RESUME -> { if (listener != null) Engine.micPaused = false; return START_STICKY }
        }
        if (listener != null) return START_STICKY

        Engine.init(this)
        Engine.paused = false
        Engine.micPaused = false
        createSession()
        startForegroundCompat(buildNotification())

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EnglishCompanion:mic").apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L)   // safety limit: 6 hours
        }

        Engine.onStopRequested = {
            // let the goodbye finish, then stop
            main.postDelayed({ stopEverything() }, 2500)
        }
        Engine.onPauseChanged = { main.post { refreshPlayer() } }
        Bus.onStatusChanged = { refreshPlayer() }

        listener = AudioListener(
            sensitivity = { Engine.prefs.sensitivity },
            pauseMs = { Engine.prefs.pauseMs },
            shouldIgnore = {
                Engine.micPaused ||
                    Engine.speaker.isSpeaking ||
                    System.currentTimeMillis() - Engine.speaker.lastSpokeAt < 400
            },
            onUtterance = { pcm, secs -> Engine.submitAudio(pcm, secs) }
        ).also { it.start() }

        Bus.running(true)
        Bus.status("Listening…")
        Bus.post(Kind.INFO, "Companion started. Talk naturally — English, Bangla, or both.")
        Engine.speaker.speak("Hey, I'm ready.")
        return START_STICKY
    }

    // ---------- lock-screen player ----------
    private fun createSession() {
        session = MediaSession(this, "EnglishCompanion").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { Engine.micPaused = false }
                override fun onPause() { Engine.micPaused = true }
                override fun onStop() { stopEverything() }
            })
            setSessionActivity(openAppIntent())
            isActive = true
        }
        updateSessionState()
    }

    private fun updateSessionState() {
        val s = session ?: return
        val paused = Engine.micPaused
        s.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP
                )
                .setState(
                    if (paused) PlaybackState.STATE_PAUSED else PlaybackState.STATE_PLAYING,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f
                )
                .build()
        )
        s.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "AI English Companion")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, statusText())
                .putString(MediaMetadata.METADATA_KEY_ALBUM, Modes.title(Engine.prefs.mode))
                .putBitmap(MediaMetadata.METADATA_KEY_ART, artwork())
                .build()
        )
    }

    private var art: Bitmap? = null
    private fun artwork(): Bitmap {
        art?.let { return it }
        val d = getDrawable(R.drawable.ic_launcher)!!
        val b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        d.setBounds(0, 0, 256, 256)
        d.draw(c)
        art = b
        return b
    }

    private fun statusText(): String = when {
        Engine.micPaused -> "Paused"
        else -> Bus.status
    }

    private fun refreshPlayer() {
        if (listener == null) return
        updateSessionState()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification())
    }

    private fun openAppIntent() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun serviceIntent(action: String, code: Int) = PendingIntent.getService(
        this, code, Intent(this, CompanionService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Companion player", NotificationManager.IMPORTANCE_LOW).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
        )
        val paused = Engine.micPaused
        val toggle = if (paused)
            Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_play), "Resume",
                serviceIntent(ACTION_RESUME, 2)).build()
        else
            Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_pause), "Pause",
                serviceIntent(ACTION_PAUSE, 3)).build()
        val stop = Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_stop), "Stop",
            serviceIntent(ACTION_STOP, 1)).build()

        val style = Notification.MediaStyle().setShowActionsInCompactView(0, 1)
        session?.let { style.setMediaSession(it.sessionToken) }

        return Notification.Builder(this, CHANNEL)
            .setContentTitle("AI English Companion")
            .setContentText(statusText())
            .setSubText(Modes.title(Engine.prefs.mode))
            .setSmallIcon(R.drawable.ic_notify)
            .setLargeIcon(artwork())
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(toggle)
            .addAction(stop)
            .setStyle(style)
            .build()
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    // ---------- stopping ----------
    private fun stopEverything() {
        listener?.stop()
        listener = null
        cleanup()
        Bus.running(false)
        Bus.status("Ready")
        Bus.post(Kind.INFO, "Companion stopped.")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanup() {
        Engine.onStopRequested = null
        Engine.onPauseChanged = null
        Bus.onStatusChanged = null
        Engine.micPaused = false
        session?.let { it.isActive = false; it.release() }
        session = null
        try { wakeLock?.release() } catch (_: Exception) { }
        wakeLock = null
    }

    override fun onDestroy() {
        listener?.stop()
        listener = null
        cleanup()
        if (Bus.isRunning) Bus.running(false)
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "companion_player"
        const val NOTIF_ID = 7
        const val ACTION_STOP = "stop"
        const val ACTION_PAUSE = "pause"
        const val ACTION_RESUME = "resume"
    }
}
