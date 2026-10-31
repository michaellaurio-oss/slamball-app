package com.laurio.slamlog.workout

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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.laurio.slamlog.R

/**
 * Keeps the app alive while a workout runs, so switching apps or turning the screen off doesn't let
 * Android kill it and lose the workout. It does no work itself: the workout engine stays in
 * [WorkoutViewModel]. Being a foreground service (microphone + connected device) keeps the process,
 * the mic and the strap connection running; a partial wake lock keeps the 1-second ticker going with
 * the screen off. Shows an ongoing notification with the current set / rest.
 */
class WorkoutService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val types = intent?.getIntExtra(EXTRA_TYPES, 0) ?: 0
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Workout in progress"
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(this, text), types)
        } catch (e: Exception) {
            // Not allowed right now (e.g. a permission was revoked). The workout still runs, just unprotected.
            running = false
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        if (wakeLock == null) {
            wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SlamLog:workout")
                .apply { acquire(MAX_WORKOUT_MS) }
        }
        // If Android kills the process anyway, don't restart the service: the workout is gone with it.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "workout"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_TYPES = "types"
        private const val EXTRA_TEXT = "text"
        private const val MAX_WORKOUT_MS = 3 * 60 * 60 * 1000L

        @Volatile private var running = false

        /**
         * Start protecting the workout. [mic] / [bluetooth]: whether the mic is in use and the strap
         * permission is granted; Android 14+ only allows those foreground types with the permission.
         */
        fun start(context: Context, mic: Boolean, bluetooth: Boolean, text: String) {
            var types = 0
            if (Build.VERSION.SDK_INT >= 29) {
                // Microphone type exists from Android 11; before that the mic needs no special type.
                if (mic && Build.VERSION.SDK_INT >= 30) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                if (bluetooth) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                if (types == 0 && Build.VERSION.SDK_INT >= 34) return // no allowed type: nothing to protect with
            }
            createChannel(context)
            val intent = Intent(context, WorkoutService::class.java)
                .putExtra(EXTRA_TYPES, types)
                .putExtra(EXTRA_TEXT, text)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        /** Update the notification text (current set, reps, rest). No-op when not running. */
        fun update(context: Context, text: String) {
            if (!running) return
            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification(context, text))
            } catch (e: SecurityException) {
                // Notifications are off (Android 13+ permission): the service still keeps the workout alive.
            }
        }

        fun stop(context: Context) {
            running = false
            context.stopService(Intent(context, WorkoutService::class.java))
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Workout in progress", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Shows the current set while a workout runs"
                        setShowBadge(false)
                    },
                )
            }
        }

        private fun notification(context: Context, text: String): Notification {
            // Same as tapping the app icon: brings the running app (and its workout) back, never a new copy.
            val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            val pending = open?.let {
                PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_workout)
                .setContentTitle("Slam Log")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(pending)
                .build()
        }
    }
}
