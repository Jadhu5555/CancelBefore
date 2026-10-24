package com.example.model

enum class RecordType(val displayName: String, val defaultCategory: String) {
    SUBSCRIPTION("Subscription", "Entertainment"),
    FREE_TRIAL("Free Trial", "Software"),
    UPI_AUTOPAY("UPI AutoPay", "Finance"),
    CARD_RECURRING("Card Recurring", "Finance"),
    BANK_DEBIT("Bank Debit", "Finance"),
    BILL_REMINDER("Bill Reminder", "Utilities"),
    PRODUCT_RETURN("Product Return", "Shopping"),
    WARRANTY_EXPIRATION("Warranty Expiry", "Shopping"),
    DOCUMENT_EXPIRATION("Document Expiry", "Personal"),
    CUSTOM_REMINDER("Custom Reminder", "General");

    fun isDeadlineType(): Boolean {
        return this == FREE_TRIAL || this == PRODUCT_RETURN ||
                this == WARRANTY_EXPIRATION || this == DOCUMENT_EXPIRATION
    }
}

enum class BillingFrequency(val displayName: String, val approximateDays: Int) {
    ONE_TIME("One-time", 0),
    WEEKLY("Weekly", 7),
    MONTHLY("Monthly", 30),
    QUARTERLY("Quarterly", 90),
    YEARLY("Yearly", 365),
    CUSTOM("Custom", 0);

    fun calculateNextEpochDay(fromEpochDay: Long, customDays: Int = 30): Long {
        return when (this) {
            ONE_TIME -> fromEpochDay
            WEEKLY -> fromEpochDay + 7
            MONTHLY -> fromEpochDay + 30 // Approx or exact calendar month addition
            QUARTERLY -> fromEpochDay + 91
            YEARLY -> fromEpochDay + 365
            CUSTOM -> fromEpochDay + (if (customDays > 0) customDays else 30)
        }
    }
}

enum class RecordStatus(val displayName: String) {
    ACTIVE("Active"),
    PAUSED("Paused"),
    CANCELLED("Cancelled"),
    ARCHIVED("Archived")
}

enum class DetectionSource(val displayName: String) {
    MANUAL("Manual Entry"),
    NOTIFICATION("Notification Detection"),
    EMAIL_STUB("Email Integration (Planned)"),
    IMPORT("Data Import")
}

enum class DetectedEventType(val displayName: String, val isActionable: Boolean) {
    NEW_MANDATE_CREATED("New Mandate Created", true),
    FREE_TRIAL_ACTIVATED("Free Trial Activated", true),
    UPCOMING_RECURRING_DEBIT("Upcoming Scheduled Debit", true),
    SUCCESSFUL_RECURRING_PAYMENT("Recurring Payment Successful", true),
    FAILED_PAYMENT("Payment Failed", true),
    SUBSCRIPTION_RENEWED("Subscription Renewed", true),
    SUBSCRIPTION_CANCELLED("Subscription Cancelled", true),
    MANDATE_REVOKED("Mandate Revoked", true),
    ONE_TIME_TRANSACTION("One-Time Transaction", false),
    UNRELATED_NOTIFICATION("Unrelated Notification", false)
}

enum class DetectionReviewStatus(val displayName: String) {
    PENDING("Pending Review"),
    CONFIRMED("Confirmed"),
    DISMISSED("Dismissed"),
    IGNORED_SOURCE("Ignored Source"),
    REVIEW_LATER("Review Later")
}

