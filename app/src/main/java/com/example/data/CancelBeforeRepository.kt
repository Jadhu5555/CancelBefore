package com.example.data

import android.content.Context
import com.example.data.entity.DetectedNotificationEntity
import com.example.data.entity.RecordEntity
import com.example.model.BillingFrequency
import com.example.model.DetectionReviewStatus
import com.example.model.DetectionSource
import com.example.model.RecordStatus
import com.example.model.RecordType
import com.example.service.MerchantNormalizer
import com.example.service.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class CancelBeforeRepository(
    private val context: Context,
    private val database: CancelBeforeDatabase,
    private val preferencesRepository: PreferencesRepository
) {
    val allRecords: Flow<List<RecordEntity>> = database.recordDao().getAllRecords()
    val activeRecords: Flow<List<RecordEntity>> = database.recordDao().getActiveRecords()
    val pendingDetections: Flow<List<DetectedNotificationEntity>> =
        database.detectedNotificationDao().getPendingDetections()

    // Settings flows
    val themeFlow = preferencesRepository.themeFlow
    val currencyFlow = preferencesRepository.currencyFlow
    val defaultRemindersFlow = preferencesRepository.defaultRemindersFlow
    val notifListenerEnabledFlow = preferencesRepository.notifListenerEnabledFlow
    val emailSyncEnabledFlow = preferencesRepository.emailSyncEnabledFlow
    val monitoredPackagesFlow = preferencesRepository.monitoredPackagesFlow
    val ignoredPackagesFlow = preferencesRepository.ignoredPackagesFlow
    val autoPurgeDaysFlow = preferencesRepository.autoPurgeDaysFlow

    suspend fun getRecordById(id: Long): RecordEntity? {
        return database.recordDao().getRecordByIdImmediate(id)
    }

    suspend fun findMatchingActiveRecord(merchantName: String): RecordEntity? {
        val target = MerchantNormalizer.normalize(merchantName)
        val active = database.recordDao().getActiveRecordsList()
        return active.find {
            MerchantNormalizer.normalize(it.name).equals(target, ignoreCase = true)
        }
    }

    suspend fun saveRecord(record: RecordEntity): Long {
        val insertedId = database.recordDao().insert(record)
        val finalRecord = if (record.id == 0L) record.copy(id = insertedId) else record
        if (finalRecord.status == RecordStatus.ACTIVE.name) {
            ReminderScheduler.scheduleRemindersForRecord(context, finalRecord)
        }
        return insertedId
    }

    suspend fun updateRecord(record: RecordEntity) {
        database.recordDao().update(record)
        if (record.status == RecordStatus.ACTIVE.name) {
            ReminderScheduler.scheduleRemindersForRecord(context, record)
        } else {
            ReminderScheduler.cancelRemindersForRecord(context, record.id, record.getReminderOffsets())
        }
    }

    suspend fun deleteRecord(id: Long) {
        ReminderScheduler.cancelRemindersForRecord(context, id)
        database.recordDao().deleteById(id)
    }

    suspend fun pauseRecord(record: RecordEntity) {
        val updated = record.copy(status = RecordStatus.PAUSED.name, updatedAt = System.currentTimeMillis())
        updateRecord(updated)
    }

    suspend fun resumeRecord(record: RecordEntity) {
        val updated = record.copy(status = RecordStatus.ACTIVE.name, updatedAt = System.currentTimeMillis())
        updateRecord(updated)
    }

    suspend fun markCancelled(record: RecordEntity, effectiveDay: Long? = null) {
        val today = LocalDate.now().toEpochDay()
        val updated = record.copy(
            status = RecordStatus.CANCELLED.name,
            cancelledDateEpochDay = today,
            effectiveCancellationEpochDay = effectiveDay ?: today,
            updatedAt = System.currentTimeMillis()
        )
        updateRecord(updated)
    }

    suspend fun confirmOrUpdateDetection(
        detectionId: Long,
        record: RecordEntity,
        updateExistingRecordId: Long?
    ): Long {
        val finalRecordId = if (updateExistingRecordId != null && updateExistingRecordId > 0) {
            val existing = getRecordById(updateExistingRecordId)
            if (existing != null) {
                val updated = existing.copy(
                    nextDueDateEpochDay = record.nextDueDateEpochDay,
                    amount = if (record.amount > 0.0) record.amount else existing.amount,
                    currency = record.currency,
                    notes = (existing.notes + "\nAuto-updated from ${record.name} notification.").trim(),
                    updatedAt = System.currentTimeMillis()
                )
                updateRecord(updated)
                updateExistingRecordId
            } else {
                saveRecord(record)
            }
        } else {
            saveRecord(record)
        }

        database.detectedNotificationDao().markConfirmed(detectionId)
        return finalRecordId
    }

    suspend fun confirmDetectionAsRecord(detectionId: Long, record: RecordEntity): Long {
        return confirmOrUpdateDetection(detectionId, record, null)
    }

    suspend fun insertDetection(entity: DetectedNotificationEntity): Long {
        return database.detectedNotificationDao().insert(entity)
    }

    suspend fun dismissDetection(detectionId: Long) {
        database.detectedNotificationDao().dismiss(detectionId)
    }

    suspend fun markReviewLater(detectionId: Long) {
        database.detectedNotificationDao().markReviewLater(detectionId)
    }

    suspend fun ignoreSource(packageName: String) {
        database.detectedNotificationDao().markIgnoredSource(packageName)
        val current = ignoredPackagesFlow.first().toMutableSet()
        current.add(packageName)
        preferencesRepository.setIgnoredPackages(current)

        // Also remove from monitored list if present
        val monitored = monitoredPackagesFlow.first().toMutableSet()
        if (monitored.contains(packageName)) {
            monitored.remove(packageName)
            preferencesRepository.setMonitoredPackages(monitored)
        }
    }

    suspend fun unignoreSource(packageName: String) {
        val current = ignoredPackagesFlow.first().toMutableSet()
        current.remove(packageName)
        preferencesRepository.setIgnoredPackages(current)
    }

    suspend fun clearDetectionHistory() {
        database.detectedNotificationDao().deleteAll()
    }

    suspend fun purgeOldDetections() {
        val days = autoPurgeDaysFlow.first()
        if (days <= 0) return
        val cutoffMs = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L)
        database.detectedNotificationDao().deleteOlderThan(cutoffMs)
    }

    suspend fun setTheme(theme: String) = preferencesRepository.setTheme(theme)
    suspend fun setCurrency(currency: String) = preferencesRepository.setCurrency(currency)
    suspend fun setDefaultReminders(offsets: List<Int>) = preferencesRepository.setDefaultReminders(offsets)
    suspend fun setNotifListenerEnabled(enabled: Boolean) = preferencesRepository.setNotifListenerEnabled(enabled)
    suspend fun setEmailSyncEnabled(enabled: Boolean) = preferencesRepository.setEmailSyncEnabled(enabled)
    suspend fun setMonitoredPackages(packages: Set<String>) = preferencesRepository.setMonitoredPackages(packages)
    suspend fun setAutoPurgeDays(days: Int) = preferencesRepository.setAutoPurgeDays(days)

    suspend fun seedSampleDataIfEmpty() {
        if (database.recordDao().getCount() > 0) return

        val today = LocalDate.now()
        val sampleList = listOf(
            RecordEntity(
                name = "Netflix 4K Ultra",
                amount = 19.99,
                currency = "USD",
                recordType = RecordType.SUBSCRIPTION.name,
                frequency = BillingFrequency.MONTHLY.name,
                startDateEpochDay = today.minusDays(25).toEpochDay(),
                nextDueDateEpochDay = today.plusDays(5).toEpochDay(),
                reminderOffsetsCsv = "7,3,1,0",
                category = "Entertainment",
                notes = "Auto-debit from Visa ending in 4242. Cancel before 5 days to avoid renewal.",
                managementUrl = "https://www.netflix.com/youraccount",
                detectionSource = DetectionSource.MANUAL.name
            ),
            RecordEntity(
                name = "Amazon Prime 30-Day Trial",
                amount = 14.99,
                currency = "USD",
                recordType = RecordType.FREE_TRIAL.name,
                frequency = BillingFrequency.MONTHLY.name,
                startDateEpochDay = today.minusDays(27).toEpochDay(),
                nextDueDateEpochDay = today.plusDays(3).toEpochDay(),
                trialEndEpochDay = today.plusDays(3).toEpochDay(),
                reminderOffsetsCsv = "3,1,0",
                category = "Shopping",
                notes = "Free trial will convert to paid monthly subscription if not cancelled!",
                managementUrl = "https://www.amazon.com/mc/manage",
                detectionSource = DetectionSource.NOTIFICATION.name,
                detectionConfidence = 0.94f,
                detectionRawSnippet = "Your 30-day free trial will end in 3 days. Membership will auto-renew at $14.99/mo."
            ),
            RecordEntity(
                name = "Spotify Premium Duo",
                amount = 14.99,
                currency = "USD",
                recordType = RecordType.UPI_AUTOPAY.name,
                frequency = BillingFrequency.MONTHLY.name,
                startDateEpochDay = today.minusDays(18).toEpochDay(),
                nextDueDateEpochDay = today.plusDays(12).toEpochDay(),
                reminderOffsetsCsv = "7,3,1",
                category = "Entertainment",
                notes = "UPI AutoPay mandate registered. Revoke mandate if not using.",
                managementUrl = "https://www.spotify.com/account/overview/",
                detectionSource = DetectionSource.MANUAL.name
            ),
            RecordEntity(
                name = "Wireless Earbuds Return Window",
                amount = 89.00,
                currency = "USD",
                recordType = RecordType.PRODUCT_RETURN.name,
                frequency = BillingFrequency.ONE_TIME.name,
                startDateEpochDay = today.minusDays(10).toEpochDay(),
                nextDueDateEpochDay = today.plusDays(4).toEpochDay(),
                reminderOffsetsCsv = "3,1,0",
                category = "Shopping",
                notes = "Return eligible until deadline. Keep original box and receipt.",
                managementUrl = "https://order-returns.example.com",
                detectionSource = DetectionSource.MANUAL.name
            ),
            RecordEntity(
                name = "Home Fiber Internet",
                amount = 65.00,
                currency = "USD",
                recordType = RecordType.BILL_REMINDER.name,
                frequency = BillingFrequency.MONTHLY.name,
                startDateEpochDay = today.minusMonths(2).toEpochDay(),
                nextDueDateEpochDay = today.plusDays(16).toEpochDay(),
                reminderOffsetsCsv = "7,3,1,0",
                category = "Utilities",
                notes = "Monthly broadband service bill.",
                managementUrl = "https://provider.example.com/billing",
                detectionSource = DetectionSource.MANUAL.name
            )
        )

        for (item in sampleList) {
            saveRecord(item)
        }
    }

    suspend fun clearAllData() {
        val active = database.recordDao().getActiveRecordsList()
        for (record in active) {
            ReminderScheduler.cancelRemindersForRecord(context, record.id, record.getReminderOffsets())
        }
        database.recordDao().deleteAll()
        database.detectedNotificationDao().deleteAll()
        ReminderScheduler.recordSchedulingResult("Cleared all user data and cancelled all alarms.")
    }

    suspend fun importRecords(records: List<RecordEntity>) {
        for (r in records) {
            saveRecord(r)
        }
    }
}
