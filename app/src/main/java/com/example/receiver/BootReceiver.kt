package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.CancelBeforeDatabase
import com.example.service.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d("CancelBefore", "BootReceiver triggered by action: $action")

        val supportedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED
        )

        if (action in supportedActions) {
            val pendingResult = try { goAsync() } catch (_: Exception) { null }
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = CancelBeforeDatabase.getDatabase(context)
                    val activeRecords = db.recordDao().getActiveRecordsList()
                    var scheduledCount = 0
                    for (record in activeRecords) {
                        ReminderScheduler.scheduleRemindersForRecord(context, record)
                        scheduledCount++
                    }
                    ReminderScheduler.recordSchedulingResult("Rescheduled reminders for $scheduledCount active records on $action")
                    Log.d("CancelBefore", "Rescheduled reminders for $scheduledCount records after $action")
                } catch (e: Exception) {
                    Log.e("CancelBefore", "Failed to reschedule reminders on $action", e)
                } finally {
                    try {
                        pendingResult?.finish()
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
