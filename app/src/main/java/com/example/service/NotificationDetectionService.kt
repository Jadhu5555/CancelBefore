package com.example.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.CancelBeforeDatabase
import com.example.data.PreferencesRepository
import com.example.data.entity.DetectedNotificationEntity
import com.example.model.DetectionReviewStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NotificationDetectionService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        @Volatile
        var lastDetectionSummary: String = "No notifications detected in this session yet."
            private set

        fun recordDetectionResult(summary: String) {
            lastDetectionSummary = summary
        }

        fun isNotificationAccessGranted(context: Context): Boolean {
            return try {
                val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
                enabledListeners.contains(context.packageName)
            } catch (_: Exception) {
                false
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // 1. Skip ongoing notifications (e.g., active media player, foreground file sync)
        if (sbn.isOngoing) return

        val packageName = sbn.packageName ?: return
        if (packageName == applicationContext.packageName) return // Ignore self-notifications

        val extras = sbn.notification?.extras ?: return

        // 2. Extract multi-line and expanded notification texts (supports InboxStyle & BigTextStyle)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString() ?: ""

        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        val linesContent = textLines?.joinToString(" ") { it.toString() } ?: ""

        val content = listOf(bigText, text, linesContent, subText, summaryText)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")

        if (title.isBlank() && content.isBlank()) return

        serviceScope.launch {
            try {
                val prefsRepo = PreferencesRepository(applicationContext)
                val isEnabled = prefsRepo.notifListenerEnabledFlow.first()
                if (!isEnabled) return@launch

                // 3. Check explicitly ignored packages
                val ignoredPackages = prefsRepo.ignoredPackagesFlow.first()
                if (ignoredPackages.contains(packageName)) {
                    return@launch
                }

                // 4. Check package allowlist if configured
                val monitoredPackages = prefsRepo.monitoredPackagesFlow.first()
                if (monitoredPackages.isNotEmpty() && !monitoredPackages.contains(packageName)) {
                    // Skip apps user has not chosen to monitor
                    return@launch
                }

                // 5. Run analyzer
                val result = DetectionAnalyzer.analyze(packageName, title, content)
                if (!result.isCandidate) return@launch

                val db = CancelBeforeDatabase.getDatabase(applicationContext)
                val dao = db.detectedNotificationDao()

                // 6. Check deduplication by fingerprint and content hash
                val hash = DetectionAnalyzer.computeEventFingerprint(
                    packageName = packageName,
                    normalizedMerchant = result.normalizedMerchant,
                    eventType = result.eventType.name,
                    amount = result.extractedAmount,
                    dateEpochDay = result.suggestedDueDateEpochDay
                )

                if (dao.countByFingerprint(result.eventFingerprint) > 0 || dao.countByHash(hash) > 0) {
                    Log.d("CancelBefore", "Duplicate detection ignored for event fingerprint")
                    return@launch
                }

                // 7. Check if matching subscription already exists in records
                val records = db.recordDao().getActiveRecordsList()
                val matchedRecord = records.find {
                    MerchantNormalizer.normalize(it.name).equals(result.normalizedMerchant, ignoreCase = true)
                }

                val appName = try {
                    val pm = packageManager
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    packageName
                }

                val entity = DetectedNotificationEntity(
                    appName = appName,
                    packageName = packageName,
                    detectedTitle = title.take(100),
                    detectedSnippet = result.sanitizedSnippet,
                    extractedMerchant = result.extractedMerchant,
                    normalizedMerchant = result.normalizedMerchant,
                    extractedAmount = result.extractedAmount,
                    extractedCurrency = result.extractedCurrency,
                    eventCategory = result.eventType.name,
                    extractedRecordType = result.extractedRecordType.name,
                    extractedFrequency = result.extractedFrequency.name,
                    suggestedDueDateEpochDay = result.suggestedDueDateEpochDay,
                    confidence = result.confidence,
                    confidenceReason = result.confidenceReason,
                    notificationHash = hash,
                    eventFingerprint = result.eventFingerprint,
                    detectedAt = System.currentTimeMillis(),
                    reviewStatus = DetectionReviewStatus.PENDING.name,
                    matchedRecordId = matchedRecord?.id
                )

                val insertedId = dao.insert(entity)
                val summary = "Detected ${result.normalizedMerchant} (${result.eventType.displayName}) from $appName"
                recordDetectionResult(summary)
                Log.d("CancelBefore", "Stored new candidate ($insertedId): [Event: ${result.eventType.name}]")

                // Notify user in system bar to review detected payment
                notifyCandidateReview(insertedId, appName, result.normalizedMerchant, result.eventType.displayName)
            } catch (e: Exception) {
                Log.e("CancelBefore", "Error analyzing notification safely", e)
            }
        }
    }

    private fun notifyCandidateReview(detectionId: Long, appName: String, merchant: String, eventDesc: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ReminderScheduler.createNotificationChannel(applicationContext)

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("target_detection_id", detectionId)
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            (9000 + detectionId).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Possible recurring payment detected")
            .setContentText("$merchant ($eventDesc from $appName). Tap to confirm or edit details.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("CancelBefore detected a potential $eventDesc from $appName for $merchant. Review now to avoid unwanted renewals."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify((8000 + detectionId).toInt(), notification)
    }
}
