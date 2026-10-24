package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.BillingFrequency
import com.example.model.DetectedEventType
import com.example.model.DetectionReviewStatus
import com.example.model.RecordType

@Entity(
    tableName = "detected_notifications",
    indices = [
        Index(value = ["notificationHash"], unique = false),
        Index(value = ["eventFingerprint"], unique = true)
    ]
)
data class DetectedNotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val appName: String,
    val packageName: String,
    val detectedTitle: String,
    val detectedSnippet: String,
    val extractedMerchant: String,
    val normalizedMerchant: String = "",
    val extractedAmount: Double = 0.0,
    val extractedCurrency: String = "USD",
    val eventCategory: String = DetectedEventType.NEW_MANDATE_CREATED.name,
    val extractedRecordType: String = RecordType.UPI_AUTOPAY.name,
    val extractedFrequency: String = BillingFrequency.MONTHLY.name,
    val suggestedDueDateEpochDay: Long? = null,
    val confidence: Float = 0.85f,
    val confidenceReason: String = "",
    val notificationHash: String,
    val eventFingerprint: String = "",
    val detectedAt: Long = System.currentTimeMillis(),
    val reviewStatus: String = DetectionReviewStatus.PENDING.name,
    val isDismissed: Boolean = false,
    val isConfirmed: Boolean = false,
    val matchedRecordId: Long? = null
) {
    fun getRecordTypeEnum(): RecordType {
        return try {
            RecordType.valueOf(extractedRecordType)
        } catch (_: Exception) {
            RecordType.UPI_AUTOPAY
        }
    }

    fun getFrequencyEnum(): BillingFrequency {
        return try {
            BillingFrequency.valueOf(extractedFrequency)
        } catch (_: Exception) {
            BillingFrequency.MONTHLY
        }
    }

    fun getEventCategoryEnum(): DetectedEventType {
        return try {
            DetectedEventType.valueOf(eventCategory)
        } catch (_: Exception) {
            DetectedEventType.NEW_MANDATE_CREATED
        }
    }

    fun getReviewStatusEnum(): DetectionReviewStatus {
        return try {
            DetectionReviewStatus.valueOf(reviewStatus)
        } catch (_: Exception) {
            DetectionReviewStatus.PENDING
        }
    }
}
