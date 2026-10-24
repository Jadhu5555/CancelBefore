package com.example.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.entity.RecordEntity
import com.example.model.RecordType
import com.example.receiver.ReminderReceiver
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ReminderScheduler {

    const val CHANNEL_ID = "cancelbefore_reminders"
    const val CHANNEL_NAME = "Deadline & Payment Reminders"
    const val ACTION_REMINDER = "com.aistudio.cancelbefore.ACTION_REMINDER"

    const val EXTRA_RECORD_ID = "extra_record_id"
    const val EXTRA_RECORD_NAME = "extra_record_name"
    const val EXTRA_AMOUNT = "extra_amount"
    const val EXTRA_CURRENCY = "extra_currency"
    const val EXTRA_DAYS_LEFT = "extra_days_left"
    const val EXTRA_RECORD_TYPE = "extra_record_type"

    @Volatile
    var lastSchedulingSummary: String = "No reminders scheduled in this session yet."
        private set

    fun recordSchedulingResult(summary: String) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        lastSchedulingSummary = "[$timestamp] $summary"
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies you before subscriptions renew, free trials end, or bills are due"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun isExactAlarmPermitted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun scheduleRemindersForRecord(context: Context, record: RecordEntity): Int {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val todayEpochDay = LocalDate.now().toEpochDay()

        // Target deadline day (trial end or next renewal)
        val targetDay = if (record.getRecordTypeEnum() == RecordType.FREE_TRIAL && record.trialEndEpochDay != null && record.trialEndEpochDay >= todayEpochDay) {
            record.trialEndEpochDay
        } else {
            record.nextDueDateEpochDay
        }

        val offsets = record.getReminderOffsets()
        val now = System.currentTimeMillis()
        var scheduledAlarmsCount = 0

        for (offset in offsets) {
            val triggerEpochDay = targetDay - offset
            if (triggerEpochDay < todayEpochDay) {
                // In the past, skip
                continue
            }

            // Schedule for 9:00 AM on the trigger day in system default timezone
            val triggerDate = LocalDate.ofEpochDay(triggerEpochDay)
            val triggerDateTime = LocalDateTime.of(triggerDate, LocalTime.of(9, 0))
            val triggerMillis = triggerDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            if (triggerMillis <= now) {
                continue
            }

            val requestCode = getRequestCode(record.id, offset)
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_REMINDER
                putExtra(EXTRA_RECORD_ID, record.id)
                putExtra(EXTRA_RECORD_NAME, record.name)
                putExtra(EXTRA_AMOUNT, record.amount)
                putExtra(EXTRA_CURRENCY, record.currency)
                putExtra(EXTRA_DAYS_LEFT, offset)
                putExtra(EXTRA_RECORD_TYPE, record.recordType)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                // Check if exact alarms can be scheduled
                val canExact = isExactAlarmPermitted(context)
                if (canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerMillis,
                            pendingIntent
                        )
                    } catch (secEx: SecurityException) {
                        // Fallback gracefully to non-exact alarm
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerMillis,
                            pendingIntent
                        )
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerMillis,
                        pendingIntent
                    )
                }
                scheduledAlarmsCount++
                Log.d("CancelBefore", "Scheduled reminder for ${record.name} ($offset days before) at $triggerMillis (exact: $canExact)")
            } catch (e: Exception) {
                Log.e("CancelBefore", "Failed to schedule alarm for ${record.name}", e)
            }
        }

        recordSchedulingResult("Scheduled $scheduledAlarmsCount reminders for ${record.name} (exact: ${isExactAlarmPermitted(context)})")
        return scheduledAlarmsCount
    }

    fun cancelRemindersForRecord(context: Context, recordId: Long, offsets: List<Int> = listOf(7, 3, 1, 0, 14, 30)) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (offset in offsets) {
            val requestCode = getRequestCode(recordId, offset)
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_REMINDER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
        recordSchedulingResult("Cancelled reminders for record ID $recordId")
    }

    private fun getRequestCode(recordId: Long, offset: Int): Int {
        return ((recordId % 100000) * 100 + offset).toInt()
    }
}
