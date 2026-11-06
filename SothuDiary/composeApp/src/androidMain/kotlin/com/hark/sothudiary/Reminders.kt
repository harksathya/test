package com.hark.sothudiary

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hark.sothudiary.data.nowMillis
import com.hark.sothudiary.domain.daysUntil
import com.hark.sothudiary.i18n.S
import java.util.Calendar

/** Daily 9 AM (inexact) check for overdue / today's follow-ups, posted as one notification. */
object Reminders {
    private const val CHANNEL = "followups"
    private const val NOTIFICATION_ID = 9001
    const val EXTRA_OPEN = "open"
    const val OPEN_FOLLOWUPS = "followups"

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 1, Intent(context, ReminderReceiver::class.java).setAction(ACTION_CHECK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(context: Context, enabled: Boolean) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = pending(context)
        am.cancel(pi)
        if (!enabled) return
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        runCatching { am.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.timeInMillis, AlarmManager.INTERVAL_DAY, pi) }
    }

    fun notifyIfDue(context: Context) {
        val repo = AppGraph.repository(context)
        val data = repo.data
        if (!data.settings.reminders) return
        val now = nowMillis()
        val due = data.interactions.filter { i -> !i.followUpDone && i.followUpAt != null && daysUntil(i.followUpAt, now) <= 0 }
        if (due.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val lang = data.settings.lang
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, S.remindersChannel.of(lang), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val overdue = due.count { daysUntil(it.followUpAt ?: now, now) < 0 }
        val today = due.size - overdue
        val titles = due.mapNotNull { i ->
            i.propertyId?.let { pid -> data.properties.firstOrNull { it.id == pid }?.title }
                ?: i.brokerId?.let { bid -> data.brokers.firstOrNull { it.id == bid }?.name }
        }.distinct()
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN, OPEN_FOLLOWUPS)
        val pi = PendingIntent.getActivity(context, 2, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = titles.take(4).joinToString(" • ")
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setColor(0xFF0B5CAD.toInt())
            .setContentTitle(S.reminderTitle.format(lang, overdue, today))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(titles.take(8).joinToString("\n")))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n) }
    }

    const val ACTION_CHECK = "com.hark.sothudiary.CHECK_FOLLOWUPS"
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                Reminders.schedule(context, AppGraph.repository(context).data.settings.reminders)
            else -> Reminders.notifyIfDue(context)
        }
    }
}
