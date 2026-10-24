package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.CancelBeforeRepository
import com.example.data.entity.DetectedNotificationEntity
import com.example.data.entity.RecordEntity
import com.example.model.BillingFrequency
import com.example.model.DetectedEventType
import com.example.model.DetectionSource
import com.example.model.RecordStatus
import com.example.model.RecordType
import com.example.service.MerchantNormalizer
import com.example.util.BackupManager
import com.example.util.CsvExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class DashboardFilter(val label: String) {
    ALL("All"),
    SUBSCRIPTIONS("Subscriptions"),
    FREE_TRIALS("Free Trials"),
    UPI_AUTOPAY("UPI / AutoPay"),
    DEADLINES("Deadlines & Returns"),
    BILLS("Bills")
}

enum class DashboardSort(val label: String) {
    DUE_DATE("Renewal Date"),
    AMOUNT_DESC("Highest Cost"),
    NAME_ASC("Name (A-Z)")
}

data class DashboardMetrics(
    val activeCount: Int = 0,
    val upcoming7DaysCount: Int = 0,
    val freeTrialsNearDeadlineCount: Int = 0,
    val returnsAndWarrantiesCount: Int = 0,
    val monthlySpendByCurrency: Map<String, Double> = emptyMap(),
    val nextImportantDeadline: RecordEntity? = null
)

class SubscriptionViewModel(
    private val repository: CancelBeforeRepository
) : ViewModel() {

    val allRecords: StateFlow<List<RecordEntity>> = repository.allRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingDetections: StateFlow<List<DetectedNotificationEntity>> = repository.pendingDetections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val themeFlow: StateFlow<String> = repository.themeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM")

    val currencyFlow: StateFlow<String> = repository.currencyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "USD")

    val defaultRemindersFlow: StateFlow<List<Int>> = repository.defaultRemindersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(7, 3, 1, 0))

    val notifListenerEnabledFlow: StateFlow<Boolean> = repository.notifListenerEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val emailSyncEnabledFlow: StateFlow<Boolean> = repository.emailSyncEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val monitoredPackagesFlow: StateFlow<Set<String>> = repository.monitoredPackagesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val ignoredPackagesFlow: StateFlow<Set<String>> = repository.ignoredPackagesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val autoPurgeDaysFlow: StateFlow<Int> = repository.autoPurgeDaysFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)

    val currentFilter = MutableStateFlow(DashboardFilter.ALL)
    val currentSort = MutableStateFlow(DashboardSort.DUE_DATE)
    val searchQuery = MutableStateFlow("")

    val uiMessage = MutableStateFlow<String?>(null)

    // Filtered and Sorted Records Flow
    val filteredRecords: StateFlow<List<RecordEntity>> = combine(
        allRecords,
        currentFilter,
        currentSort,
        searchQuery
    ) { records, filter, sort, query ->
        val today = LocalDate.now().toEpochDay()
        var result = records

        // Search query filter
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            result = result.filter {
                it.name.lowercase().contains(q) ||
                it.category.lowercase().contains(q) ||
                it.notes.lowercase().contains(q)
            }
        }

        // Category / Type filter
        result = when (filter) {
            DashboardFilter.ALL -> result
            DashboardFilter.SUBSCRIPTIONS -> result.filter { it.getRecordTypeEnum() == RecordType.SUBSCRIPTION || it.getRecordTypeEnum() == RecordType.CARD_RECURRING }
            DashboardFilter.FREE_TRIALS -> result.filter { it.getRecordTypeEnum() == RecordType.FREE_TRIAL }
            DashboardFilter.UPI_AUTOPAY -> result.filter { it.getRecordTypeEnum() == RecordType.UPI_AUTOPAY || it.getRecordTypeEnum() == RecordType.BANK_DEBIT }
            DashboardFilter.DEADLINES -> result.filter { it.getRecordTypeEnum().isDeadlineType() }
            DashboardFilter.BILLS -> result.filter { it.getRecordTypeEnum() == RecordType.BILL_REMINDER || it.getRecordTypeEnum() == RecordType.CUSTOM_REMINDER }
        }

        // Sorting
        when (sort) {
            DashboardSort.DUE_DATE -> result.sortedBy { it.daysUntilDue(today) }
            DashboardSort.AMOUNT_DESC -> result.sortedByDescending { it.amount }
            DashboardSort.NAME_ASC -> result.sortedBy { it.name.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dashboard metrics
    val dashboardMetrics: StateFlow<DashboardMetrics> = allRecords.combine(MutableStateFlow(Unit)) { records, _ ->
        val today = LocalDate.now().toEpochDay()
        val activeList = records.filter { it.status == RecordStatus.ACTIVE.name }

        val upcoming7 = activeList.filter {
            val days = it.daysUntilDue(today)
            days in 0..7
        }

        val freeTrialsNear = activeList.filter {
            it.getRecordTypeEnum() == RecordType.FREE_TRIAL && it.daysUntilDue(today) in 0..7
        }

        val returnsWarranties = activeList.filter {
            it.getRecordTypeEnum() == RecordType.PRODUCT_RETURN || it.getRecordTypeEnum() == RecordType.WARRANTY_EXPIRATION
        }

        // Group monthly spend by currency strictly (no currency mixing)
        val spendMap = mutableMapOf<String, Double>()
        for (item in activeList) {
            if (item.amount > 0.0 && item.getFrequencyEnum() != BillingFrequency.ONE_TIME) {
                val curr = item.currency.ifBlank { "USD" }
                val currSpend = spendMap.getOrDefault(curr, 0.0)
                spendMap[curr] = currSpend + item.monthlyEquivalentAmount()
            }
        }

        val nextDeadline = activeList.minByOrNull { it.daysUntilDue(today) }

        DashboardMetrics(
            activeCount = activeList.size,
            upcoming7DaysCount = upcoming7.size,
            freeTrialsNearDeadlineCount = freeTrialsNear.size,
            returnsAndWarrantiesCount = returnsWarranties.size,
            monthlySpendByCurrency = spendMap,
            nextImportantDeadline = nextDeadline
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardMetrics())

    init {
        viewModelScope.launch {
            repository.seedSampleDataIfEmpty()
            repository.purgeOldDetections()
        }
    }

    fun findMatchingRecord(detection: DetectedNotificationEntity): RecordEntity? {
        val targetName = detection.normalizedMerchant.ifBlank { detection.extractedMerchant }
        val targetNormalized = MerchantNormalizer.normalize(targetName)
        return allRecords.value.find {
            it.status == RecordStatus.ACTIVE.name &&
            MerchantNormalizer.normalize(it.name).equals(targetNormalized, ignoreCase = true)
        }
    }

    fun saveRecord(record: RecordEntity) {
        viewModelScope.launch {
            repository.saveRecord(record)
            uiMessage.value = "Saved '${record.name}'"
        }
    }

    fun updateRecord(record: RecordEntity) {
        viewModelScope.launch {
            repository.updateRecord(record)
            uiMessage.value = "Updated '${record.name}'"
        }
    }

    fun deleteRecord(id: Long, name: String) {
        viewModelScope.launch {
            repository.deleteRecord(id)
            uiMessage.value = "Deleted '$name'"
        }
    }

    fun pauseRecord(record: RecordEntity) {
        viewModelScope.launch {
            repository.pauseRecord(record)
            uiMessage.value = "Paused reminders for '${record.name}'"
        }
    }

    fun resumeRecord(record: RecordEntity) {
        viewModelScope.launch {
            repository.resumeRecord(record)
            uiMessage.value = "Resumed '${record.name}'"
        }
    }

    fun markCancelled(record: RecordEntity, effectiveDay: Long? = null) {
        viewModelScope.launch {
            repository.markCancelled(record, effectiveDay)
            uiMessage.value = "Marked '${record.name}' as cancelled"
        }
    }

    fun confirmDetection(
        detection: DetectedNotificationEntity,
        updateExistingRecordId: Long? = null,
        customDueDateEpochDay: Long? = null,
        customFrequency: BillingFrequency? = null
    ) {
        viewModelScope.launch {
            val today = LocalDate.now().toEpochDay()
            val dueDate = customDueDateEpochDay ?: detection.suggestedDueDateEpochDay ?: (today + 30)
            val freq = customFrequency ?: detection.getFrequencyEnum()

            val record = RecordEntity(
                name = detection.normalizedMerchant.ifBlank { detection.extractedMerchant.ifBlank { detection.appName } },
                amount = detection.extractedAmount,
                currency = detection.extractedCurrency,
                recordType = detection.extractedRecordType,
                frequency = freq.name,
                startDateEpochDay = today,
                nextDueDateEpochDay = dueDate,
                reminderOffsetsCsv = "7,3,1,0",
                category = detection.getRecordTypeEnum().defaultCategory,
                notes = "Auto-detected from ${detection.appName}: \"${detection.detectedSnippet}\"",
                detectionSource = DetectionSource.NOTIFICATION.name,
                detectionConfidence = detection.confidence,
                detectionRawSnippet = detection.detectedSnippet
            )

            val matchedId = updateExistingRecordId ?: findMatchingRecord(detection)?.id

            if (matchedId != null && matchedId > 0) {
                repository.confirmOrUpdateDetection(detection.id, record, matchedId)
                uiMessage.value = "Updated existing record for ${record.name}"
            } else {
                repository.confirmOrUpdateDetection(detection.id, record, null)
                uiMessage.value = "Created subscription for ${record.name}"
            }
        }
    }

    fun dismissDetection(detectionId: Long) {
        viewModelScope.launch {
            repository.dismissDetection(detectionId)
            uiMessage.value = "Dismissed detection"
        }
    }

    fun markReviewLater(detectionId: Long) {
        viewModelScope.launch {
            repository.markReviewLater(detectionId)
            uiMessage.value = "Snoozed for later review"
        }
    }

    fun ignoreSource(packageName: String, appName: String) {
        viewModelScope.launch {
            repository.ignoreSource(packageName)
            uiMessage.value = "Ignored notifications from $appName"
        }
    }

    fun unignoreSource(packageName: String) {
        viewModelScope.launch {
            repository.unignoreSource(packageName)
            uiMessage.value = "Restored notification scanning for source"
        }
    }

    fun clearDetectionHistory() {
        viewModelScope.launch {
            repository.clearDetectionHistory()
            uiMessage.value = "Cleared notification detection history"
        }
    }

    fun exportCsv(context: Context) {
        val records = allRecords.value
        if (records.isEmpty()) {
            uiMessage.value = "No records to export"
            return
        }
        CsvExporter.exportAndShare(context, records)
    }

    fun exportBackupJson(): String {
        return BackupManager.exportToJson(allRecords.value)
    }

    fun importBackupJson(json: String) {
        viewModelScope.launch {
            try {
                val list = BackupManager.importFromJson(json)
                if (list.isNotEmpty()) {
                    repository.importRecords(list)
                    uiMessage.value = "Successfully imported ${list.size} records"
                } else {
                    uiMessage.value = "No valid records found in backup"
                }
            } catch (e: Exception) {
                uiMessage.value = "Import failed: ${e.message}"
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            uiMessage.value = "All local data has been cleared"
        }
    }

    fun seedSampleData() {
        viewModelScope.launch {
            repository.seedSampleDataIfEmpty()
            uiMessage.value = "Loaded sample tracking records"
        }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch { repository.setTheme(theme) }
    }

    fun setCurrency(currency: String) {
        viewModelScope.launch { repository.setCurrency(currency) }
    }

    fun setNotifListenerEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setNotifListenerEnabled(enabled) }
    }

    fun setEmailSyncEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setEmailSyncEnabled(enabled) }
    }

    fun setAutoPurgeDays(days: Int) {
        viewModelScope.launch { repository.setAutoPurgeDays(days) }
    }

    fun toggleMonitoredPackage(pkg: String, enable: Boolean) {
        viewModelScope.launch {
            val current = monitoredPackagesFlow.value.toMutableSet()
            if (enable) current.add(pkg) else current.remove(pkg)
            repository.setMonitoredPackages(current)
        }
    }

    fun rescheduleAllReminders(context: Context) {
        viewModelScope.launch {
            val active = allRecords.value.filter { it.status == com.example.model.RecordStatus.ACTIVE.name }
            var count = 0
            for (rec in active) {
                com.example.service.ReminderScheduler.scheduleRemindersForRecord(context, rec)
                count++
            }
            com.example.service.ReminderScheduler.recordSchedulingResult("Manual diagnostic reschedule: $count records refreshed.")
            uiMessage.value = "Rescheduled reminders for $count active records."
        }
    }

    fun simulateSyntheticDetection(
        merchant: String = "Netflix",
        amount: Double = 499.0,
        currency: String = "INR",
        eventType: com.example.model.DetectedEventType = com.example.model.DetectedEventType.NEW_MANDATE_CREATED
    ) {
        viewModelScope.launch {
            val dummyHash = "synth_${System.currentTimeMillis()}"
            val entity = com.example.data.entity.DetectedNotificationEntity(
                appName = "Google Pay",
                packageName = "com.google.android.apps.nbu.paisa.user",
                detectedTitle = "AutoPay Mandate Setup",
                detectedSnippet = "New AutoPay mandate of $currency $amount for $merchant has been registered.",
                extractedMerchant = merchant,
                normalizedMerchant = merchant,
                extractedAmount = amount,
                extractedCurrency = currency,
                eventCategory = eventType.name,
                extractedRecordType = com.example.model.RecordType.UPI_AUTOPAY.name,
                extractedFrequency = com.example.model.BillingFrequency.MONTHLY.name,
                suggestedDueDateEpochDay = java.time.LocalDate.now().plusDays(5).toEpochDay(),
                confidence = 0.92f,
                confidenceReason = "Synthetic diagnostic test event generated for beta validation.",
                notificationHash = dummyHash,
                eventFingerprint = dummyHash,
                detectedAt = System.currentTimeMillis()
            )
            repository.insertDetection(entity)
            com.example.service.NotificationDetectionService.recordDetectionResult("Simulated $merchant ($currency $amount)")
            uiMessage.value = "Synthetic detection created for $merchant ($currency $amount)."
        }
    }

    fun purgeOldDetections() {
        viewModelScope.launch {
            repository.purgeOldDetections()
            uiMessage.value = "Purged expired detection candidates."
        }
    }

    fun clearMessage() {
        uiMessage.value = null
    }

    companion object {
        fun provideFactory(repository: CancelBeforeRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SubscriptionViewModel(repository) as T
                }
            }
    }
}
