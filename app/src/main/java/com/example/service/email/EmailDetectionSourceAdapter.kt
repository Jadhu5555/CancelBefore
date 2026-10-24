package com.example.service.email

import com.example.model.BillingFrequency
import com.example.model.DetectedEventType
import com.example.model.RecordType
import com.example.service.DetectionAnalyzer
import com.example.service.MerchantNormalizer
import java.security.MessageDigest
import java.time.LocalDate

data class ParsedEmailReceipt(
    val emailId: String,
    val senderAddress: String,
    val subject: String,
    val merchant: String,
    val normalizedMerchant: String,
    val amount: Double,
    val currency: String,
    val eventType: DetectedEventType,
    val recordType: RecordType,
    val frequency: BillingFrequency,
    val receivedDateEpochMs: Long,
    val renewalDateEpochDay: Long?,
    val confidence: Float,
    val confidenceReason: String,
    val snippet: String,
    val deduplicationFingerprint: String
)

interface EmailDetectionSourceAdapter {
    val isEnabled: Boolean
    val isConnected: Boolean
    val accountEmail: String?

    fun parseReceipt(
        messageId: String,
        sender: String,
        subject: String,
        snippet: String,
        internalDateEpochMs: Long
    ): ParsedEmailReceipt?

    fun isDuplicateWithExisting(
        emailReceipt: ParsedEmailReceipt,
        existingFingerprints: Set<String>
    ): Boolean

    fun disconnect()
}

/**
 * Future-Ready Gmail OAuth 2.0 Integration Foundation.
 *
 * Security & Privacy Architecture:
 * 1. Uses official Google Identity Services / Credential Manager (GetSignInWithGoogleOption).
 * 2. Uses minimal read-only metadata scope (https://www.googleapis.com/auth/gmail.readonly).
 * 3. Never requests user email passwords.
 * 4. All receipt analysis is performed strictly on-device without remote servers.
 * 5. Remains disabled by default until user connects credentials.
 */
class GmailOAuthDetectionAdapter(
    override var isEnabled: Boolean = false,
    override var isConnected: Boolean = false,
    override var accountEmail: String? = null
) : EmailDetectionSourceAdapter {

    override fun parseReceipt(
        messageId: String,
        sender: String,
        subject: String,
        snippet: String,
        internalDateEpochMs: Long
    ): ParsedEmailReceipt? {
        val fullContent = "$subject $snippet"
        val analysis = DetectionAnalyzer.analyze(
            packageName = "com.google.android.gm",
            title = subject,
            content = snippet
        )

        if (!analysis.isCandidate) return null

        val fingerprint = computeEmailFingerprint(sender, analysis.normalizedMerchant, analysis.extractedAmount, analysis.suggestedDueDateEpochDay)

        return ParsedEmailReceipt(
            emailId = messageId,
            senderAddress = sender,
            subject = subject,
            merchant = analysis.extractedMerchant,
            normalizedMerchant = analysis.normalizedMerchant,
            amount = analysis.extractedAmount,
            currency = analysis.extractedCurrency,
            eventType = analysis.eventType,
            recordType = analysis.extractedRecordType,
            frequency = analysis.extractedFrequency,
            receivedDateEpochMs = internalDateEpochMs,
            renewalDateEpochDay = analysis.suggestedDueDateEpochDay,
            confidence = analysis.confidence,
            confidenceReason = "Email Receipt Analysis: ${analysis.confidenceReason}",
            snippet = analysis.sanitizedSnippet,
            deduplicationFingerprint = fingerprint
        )
    }

    override fun isDuplicateWithExisting(
        emailReceipt: ParsedEmailReceipt,
        existingFingerprints: Set<String>
    ): Boolean {
        return existingFingerprints.contains(emailReceipt.deduplicationFingerprint)
    }

    override fun disconnect() {
        isConnected = false
        accountEmail = null
        isEnabled = false
    }

    private fun computeEmailFingerprint(
        sender: String,
        normalizedMerchant: String,
        amount: Double,
        dateEpochDay: Long?
    ): String {
        val input = "email|$sender|$normalizedMerchant|${"%.2f".format(amount)}|${dateEpochDay ?: -1}"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
