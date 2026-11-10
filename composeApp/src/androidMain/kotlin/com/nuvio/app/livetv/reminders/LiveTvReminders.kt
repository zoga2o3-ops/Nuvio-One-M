package com.nuvio.app.livetv.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nuvio.app.livetv.model.Reminder

/** Reminders for shows you asked about: a notification a couple of minutes before they start. */
object LiveTvReminders {
    private const val CHANNEL_ID = "live_tv_reminders"
    private const val SCORING_CHANNEL_ID = "live_tv_scoring"
    private const val LEAD_MS = 2 * 60_000L
    internal const val EXTRA_CHANNEL = "channel"
    internal const val EXTRA_TITLE = "title"
    internal const val EXTRA_START = "start"
    internal const val EXTRA_KEY = "key"

    private fun intentFor(context: Context, r: Reminder): PendingIntent {
        val intent = Intent(context, LiveTvReminderReceiver::class.java)
            .putExtra(EXTRA_CHANNEL, r.channelName)
            .putExtra(EXTRA_TITLE, r.title)
            .putExtra(EXTRA_START, r.startMs)
            .putExtra(EXTRA_KEY, r.channelKey)
        val id = (r.channelKey + r.startMs).hashCode()
        return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun schedule(context: Context, r: Reminder) {
        val at = r.startMs - LEAD_MS
        if (at <= System.currentTimeMillis()) return
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Inexact on purpose: it needs no special permission, and a minute either way doesn't matter here.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intentFor(context, r))
    }

    fun cancel(context: Context, r: Reminder) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.cancel(intentFor(context, r))
    }

    /** Sets every reminder going again (after the app starts, or the reminders change). */
    fun scheduleAll(context: Context, reminders: List<Reminder>) {
        reminders.forEach { schedule(context, it) }
    }

    /** Creates the notification channels again if the system dropped them (a reinstall, a clear). */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        fun channel(id: String, name: String, description: String) {
            if (manager.getNotificationChannel(id) != null) return
            manager.createNotificationChannel(
                NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
                    this.description = description
                    enableVibration(true)
                    enableLights(true)
                }
            )
        }
        channel(CHANNEL_ID, "Live TV reminders", "A notification before a show or game you asked about starts.")
        channel(SCORING_CHANNEL_ID, "Scoring alerts", "A notification when a team you follow scores.")
    }

    internal fun notify(context: Context, channel: String, title: String, startMs: Long) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val tap = open?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        val text = if (startMs - System.currentTimeMillis() > 60_000L) "Starting soon on $channel" else "Starting now on $channel"
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.nuvio.app.R.drawable.ic_notification_small)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify((channel + startMs).hashCode(), n)
    }

    /** A plain notification, for scoring alerts and the like. */
    fun alert(context: Context, title: String, text: String) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val tap = open?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        val n = NotificationCompat.Builder(context, SCORING_CHANNEL_ID)
            .setSmallIcon(com.nuvio.app.R.drawable.ic_notification_small)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()
        manager.notify((title + text).hashCode(), n)
    }

    /**
     * Whether the app may post notifications at all: the Android 13 permission and the app's own
     * notification switch. Scoring alerts and reminders both check this before posting.
     */
    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}

class LiveTvReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val channel = intent.getStringExtra(LiveTvReminders.EXTRA_CHANNEL) ?: return
        val title = intent.getStringExtra(LiveTvReminders.EXTRA_TITLE) ?: return
        val start = intent.getLongExtra(LiveTvReminders.EXTRA_START, 0L)
        if (!LiveTvReminders.canNotify(context)) return
        LiveTvReminders.notify(context, channel, title, start)
    }
}
