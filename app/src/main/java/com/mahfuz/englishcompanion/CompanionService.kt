package com.mahfuz.englishcompanion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/**
 * Keeps the companion alive when the app is minimised or the screen is off.
 * Shows a permanent notification (Android requires this for background microphone use).
 */
class CompanionService : Service() {
    private var listener: AudioListener? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        if (listener != null) return START_STICKY

        Engine.init(this)
        startInForeground()

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EnglishCompanion:mic").apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L)   // safety limit: 6 hours
        }

        Engine.paused = false
        Engine.onStopRequested = {
            // let the goodbye finish, then stop
            android.os.Handler(mainLooper).postDelayed({ stopEverything() }, 2500)
        }

        listener = AudioListener(
            sensitivity = { Engine.prefs.sensitivity },
            pauseMs = { Engine.prefs.pauseMs },
            shouldIgnore = {
                Engine.speaker.isSpeaking ||
                    System.currentTimeMillis() - Engine.speaker.lastSpokeAt < 400
            },
            onUtterance = { wav, secs -> Engine.submitAudio(wav, secs) }
        ).also { it.start() }

        Bus.running(true)
        Bus.status("Listening…")
        Bus.post(Kind.INFO, "Companion started. Talk naturally — English, Bangla, or both.")
        Engine.speaker.speak("Hey, I'm ready.")
        return START_STICKY
    }

    private fun startInForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Companion", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, CompanionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("English Companion is listening")
            .setContentText("Talk naturally. Tap to open.")
            .setSmallIcon(R.drawable.ic_notify)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun stopEverything() {
        listener?.stop()
        listener = null
        Engine.onStopRequested = null
        try { wakeLock?.release() } catch (_: Exception) { }
        wakeLock = null
        Bus.running(false)
        Bus.status("Ready")
        Bus.post(Kind.INFO, "Companion stopped.")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        listener?.stop()
        listener = null
        try { wakeLock?.release() } catch (_: Exception) { }
        if (Bus.isRunning) Bus.running(false)
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "companion"
        const val NOTIF_ID = 7
        const val ACTION_STOP = "stop"
    }
}
