package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.BillingFrequency
import com.example.model.DetectionSource
import com.example.model.RecordStatus
import com.example.model.RecordType
import java.time.LocalDate

@Entity(tableName = "records")
data class RecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val amount: Double = 0.0,
    val currency: String = "USD",
    val recordType: String = RecordType.SUBSCRIPTION.name,
    val frequency: String = BillingFrequency.MONTHLY.name,
    val customFrequencyDays: Int = 30,
    val startDateEpochDay: Long = LocalDate.now().toEpochDay(),
    val nextDueDateEpochDay: Long = LocalDate.now().plusMonths(1).toEpochDay(),
    val trialEndEpochDay: Long? = null,
    val reminderOffsetsCsv: String = "7,3,1,0",
    val category: String = "Entertainment",
    val notes: String = "",
    val managementUrl: String = "",
    val detectionSource: String = DetectionSource.MANUAL.name,
    val detectionConfidence: Float = 1.0f,
    val detectionRawSnippet: String? = null,
    val status: String = RecordStatus.ACTIVE.name,
    val cancelledDateEpochDay: Long? = null,
    val effectiveCancellationEpochDay: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun getRecordTypeEnum(): RecordType {
        return try {
            RecordType.valueOf(recordType)
        } catch (_: Exception) {
            RecordType.SUBSCRIPTION
        }
    }

    fun getFrequencyEnum(): BillingFrequency {
        return try {
            BillingFrequency.valueOf(frequency)
        } catch (_: Exception) {
            BillingFrequency.MONTHLY
        }
    }

    fun getStatusEnum(): RecordStatus {
        return try {
            RecordStatus.valueOf(status)
        } catch (_: Exception) {
            RecordStatus.ACTIVE
        }
    }

    fun getDetectionSourceEnum(): DetectionSource {
        return try {
            DetectionSource.valueOf(detectionSource)
        } catch (_: Exception) {
            DetectionSource.MANUAL
        }
    }

    fun getReminderOffsets(): List<Int> {
        if (reminderOffsetsCsv.isBlank()) return emptyList()
        return reminderOffsetsCsv.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .sortedDescending()
    }

    fun daysUntilDue(todayEpochDay: Long = LocalDate.now().toEpochDay()): Long {
        // If it's a trial with active trialEnd, check trialEnd first
        val targetDay = if (getRecordTypeEnum() == RecordType.FREE_TRIAL && trialEndEpochDay != null && trialEndEpochDay >= todayEpochDay) {
            trialEndEpochDay
        } else {
            nextDueDateEpochDay
        }
        return targetDay - todayEpochDay
    }

    fun monthlyEquivalentAmount(): Double {
        if (amount <= 0.0) return 0.0
        return when (getFrequencyEnum()) {
            BillingFrequency.ONE_TIME -> 0.0
            BillingFrequency.WEEKLY -> (amount * 52.0) / 12.0
            BillingFrequency.MONTHLY -> amount
            BillingFrequency.QUARTERLY -> amount / 3.0
            BillingFrequency.YEARLY -> amount / 12.0
            BillingFrequency.CUSTOM -> {
                val days = if (customFrequencyDays > 0) customFrequencyDays else 30
                (amount * 30.416) / days.toDouble()
            }
        }
    }

    fun annualEquivalentAmount(): Double {
        if (amount <= 0.0) return 0.0
        return monthlyEquivalentAmount() * 12.0
    }
}
