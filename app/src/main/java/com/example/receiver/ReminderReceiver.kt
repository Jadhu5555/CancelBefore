package com.example.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.service.ReminderScheduler

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val recordId = intent.getLongExtra(ReminderScheduler.EXTRA_RECORD_ID, 0L)
        val recordName = intent.getStringExtra(ReminderScheduler.EXTRA_RECORD_NAME) ?: "Subscription"
        val amount = intent.getDoubleExtra(ReminderScheduler.EXTRA_AMOUNT, 0.0)
        val currency = intent.getStringExtra(ReminderScheduler.EXTRA_CURRENCY) ?: "USD"
        val daysLeft = intent.getIntExtra(ReminderScheduler.EXTRA_DAYS_LEFT, 0)
        val recordType = intent.getStringExtra(ReminderScheduler.EXTRA_RECORD_TYPE) ?: "Subscription"

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("target_record_id", recordId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            recordId.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when (daysLeft) {
            0 -> "🚨 Action Required Today: $recordName"
            1 -> "⏰ Renews Tomorrow: $recordName"
            else -> "📅 $daysLeft Days Left: $recordName"
        }

        val amountStr = if (amount > 0.0) " ($currency %.2f)".format(amount) else ""
        val contentText = when (daysLeft) {
            0 -> "$recordName$amountStr is due today! Avoid unwanted charges by reviewing your account now."
            1 -> "$recordName$amountStr will renew tomorrow. Review or cancel before you are billed."
            else -> "$recordName$amountStr deadline is coming up in $daysLeft days. Tap to manage or cancel."
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ReminderScheduler.createNotificationChannel(context)

        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        val notifId = (recordId * 10 + daysLeft).toInt()
        notificationManager.notify(notifId, notification)
    }
}
