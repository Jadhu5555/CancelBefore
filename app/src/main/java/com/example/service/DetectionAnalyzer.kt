package com.example.service

import com.example.model.BillingFrequency
import com.example.model.DetectedEventType
import com.example.model.RecordType
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern

data class ParsedNotificationResult(
    val isCandidate: Boolean,
    val eventType: DetectedEventType,
    val extractedMerchant: String,
    val normalizedMerchant: String,
    val extractedAmount: Double,
    val extractedCurrency: String,
    val extractedRecordType: RecordType,
    val extractedFrequency: BillingFrequency,
    val suggestedDueDateEpochDay: Long?,
    val confidence: Float,
    val confidenceReason: String,
    val sanitizedSnippet: String,
    val eventFingerprint: String
)

object DetectionAnalyzer {

    // 1. One-time transaction patterns (should NOT be flagged as recurring candidates)
    private val ONE_TIME_PATTERNS = listOf(
        Regex("""\b(?:otp|one\s*time\s*password|verification\s*code|secret\s*code)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:sent\s+to|transferred\s+to|received\s+from|cashback\s+credited|refund\s+credited)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:paid\s+(?:(?:[$€£₹]|rs\.?|inr)\s*)?[0-9,.]+\s+to|paid\s+using\s+upi|paid\s+via\s+upi)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:paid\s+at\s+store|scanned\s+qr|paid\s+using\s+qr)\b""", RegexOption.IGNORE_CASE)
    )

    // 2. Specific event keywords
    private val MANDATE_CREATED_KEYWORDS = listOf(
        "mandate created", "autopay setup", "autopay created", "standing instruction registered",
        "standing instruction created", "emandate created", "mandate approved", "mandate registered",
        "new autopay mandate", "si mandate registered", "e-mandate active"
    )

    private val TRIAL_KEYWORDS = listOf(
        "free trial started", "trial period started", "trial activated", "trial ends on",
        "days free trial", "free trial ends", "trial membership started"
    )

    private val UPCOMING_DEBIT_KEYWORDS = listOf(
        "will be debited on", "scheduled debit", "upcoming autopay", "pre-debit notification",
        "autopay alert: your mandate", "mandate scheduled for debit", "scheduled for auto-debit",
        "next billing date", "renewal reminder"
    )

    private val SUCCESSFUL_PAYMENT_KEYWORDS = listOf(
        "autopay successful", "auto-debit successful", "mandate debited", "recurring payment completed",
        "autopay payment of", "standing instruction executed", "auto debited for", "auto-debit of"
    )

    private val FAILED_PAYMENT_KEYWORDS = listOf(
        "autopay failed", "recurring payment declined", "mandate execution failed",
        "auto-debit failed", "insufficient balance for autopay", "payment failed for mandate"
    )

    private val RENEWAL_KEYWORDS = listOf(
        "subscription renewed", "membership renewed", "plan renewed automatically",
        "subscription has renewed", "plan has been renewed"
    )

    private val CANCELLATION_KEYWORDS = listOf(
        "subscription cancelled", "subscription canceled", "membership ended",
        "recurring payment cancelled", "recurring payment canceled"
    )

    private val REVOCATION_KEYWORDS = listOf(
        "mandate revoked", "autopay mandate paused", "standing instruction cancelled",
        "standing instruction revoked", "mandate deleted", "autopay cancelled", "autopay canceled"
    )

    // General recurring keywords
    private val GENERAL_RECURRING_KEYWORDS = listOf(
        "autopay", "mandate", "standing instruction", "auto-debit", "auto debit",
        "recurring", "subscription", "membership"
    )

    // Regex for amounts: $9.99, ₹499, Rs. 199.00, INR 899, €12.50, £7.99
    private val AMOUNT_PATTERN = Pattern.compile(
        """(?:(?<curr>[$€£₹]|Rs\.?|INR|USD|EUR|GBP|CAD|AUD)\s*)(?<amt>[0-9,]+(?:\.[0-9]{2})?)""",
        Pattern.CASE_INSENSITIVE
    )

    // Regex for merchants: "at Netflix", "to Spotify", "for Amazon Prime", "on Google Play", "from Disney+"
    private val MERCHANT_PATTERN = Pattern.compile(
        """(?:to|for|at|on|from)\s+([A-Za-z0-9\s&.+'-]{2,50}?)(?:\s+for|\s+of|\s+has|\s+is|\s+renewed|\s+ending|\s+will|\s+successful|\s+succeeded|\s+completed|\s+approved|\s+failed|\s+active|\s+using|\s+via|\.|\,)""",
        Pattern.CASE_INSENSITIVE
    )

    fun analyze(packageName: String, title: String, content: String): ParsedNotificationResult {
        val fullText = "$title $content"
        val lowerText = fullText.lowercase(Locale.ROOT)

        // Check if one-time transaction or OTP first (filter out)
        if (ONE_TIME_PATTERNS.any { it.containsMatchIn(lowerText) }) {
            val isMandateToo = MANDATE_CREATED_KEYWORDS.any { lowerText.contains(it) } ||
                    UPCOMING_DEBIT_KEYWORDS.any { lowerText.contains(it) }
            if (!isMandateToo) {
                return notACandidate(
                    DetectedEventType.ONE_TIME_TRANSACTION,
                    "Ordinary one-time transaction or OTP code detected. Ignored."
                )
            }
        }

        // Classify Event
        val eventType = when {
            MANDATE_CREATED_KEYWORDS.any { lowerText.contains(it) } -> DetectedEventType.NEW_MANDATE_CREATED
            TRIAL_KEYWORDS.any { lowerText.contains(it) } -> DetectedEventType.FREE_TRIAL_ACTIVATED
            UPCOMING_DEBIT_KEYWORDS.any { lowerText.contains(it) } -> DetectedEventType.UPCOMING_RECURRING_DEBIT
            SUCCESSFUL_PAYMENT_KEYWORDS.any { lowerText.contains(it) } -> DetectedEventType.SUCCESSFUL_RECURRING_PAYMENT
            FAILED_PAYMENT_KEYWORDS.any { lowerText.contains(it) } -> DetectedEventType.FAILED_PAYMENT
            RENEWAL_KEYWORDS.any { lowerText.contains(it) } -> DetectedEventType.SUBSCRIPTION_RENEWED
            CANCELLATION_KEYWORDS.any { lowerText.contains(it) } -> DetectedEventType.SUBSCRIPTION_CANCELLED
            REVOCATION_KEYWORDS.any { lowerText.contains(it) } -> DetectedEventType.MANDATE_REVOKED
            GENERAL_RECURRING_KEYWORDS.any { lowerText.contains(it) } -> DetectedEventType.NEW_MANDATE_CREATED
            else -> DetectedEventType.UNRELATED_NOTIFICATION
        }

        if (!eventType.isActionable) {
            return notACandidate(eventType, "Notification did not match recurring payment or deadline criteria.")
        }

        // Extract Amount & Currency
        var amount = 0.0
        var currency = "USD"
        val amountMatcher = AMOUNT_PATTERN.matcher(fullText)
        if (amountMatcher.find()) {
            val currSymbol = amountMatcher.group("curr")?.trim() ?: "$"
            val rawAmt = amountMatcher.group("amt")?.replace(",", "") ?: "0"
            amount = rawAmt.toDoubleOrNull() ?: 0.0

            currency = when {
                currSymbol.contains("₹") || currSymbol.contains("Rs", ignoreCase = true) || currSymbol.contains("INR", ignoreCase = true) -> "INR"
                currSymbol.contains("€") || currSymbol.contains("EUR", ignoreCase = true) -> "EUR"
                currSymbol.contains("£") || currSymbol.contains("GBP", ignoreCase = true) -> "GBP"
                currSymbol.contains("CAD", ignoreCase = true) -> "CAD"
                currSymbol.contains("AUD", ignoreCase = true) -> "AUD"
                else -> "USD"
            }
        }

        // Extract Merchant: First check known canonical brands in the entire text (highest accuracy)
        val knownBrand = MerchantNormalizer.findKnownMerchant(fullText)

        var rawMerchant = ""
        val merchantMatcher = MERCHANT_PATTERN.matcher(fullText)
        if (merchantMatcher.find()) {
            val candidate = merchantMatcher.group(1)?.trim()?.replace(Regex("""\s+"""), " ") ?: ""
            // Avoid capturing just an amount as merchant
            if (!candidate.startsWith("$") && !candidate.startsWith("₹") && !candidate.startsWith("Rs", ignoreCase = true)) {
                rawMerchant = candidate
            }
        }
        if (rawMerchant.isBlank()) {
            rawMerchant = deriveMerchantFromPackageOrTitle(packageName, title)
        }

        val normalizedMerchant = knownBrand ?: MerchantNormalizer.normalize(rawMerchant)

        // RecordType mapping
        val recordType = when (eventType) {
            DetectedEventType.FREE_TRIAL_ACTIVATED -> RecordType.FREE_TRIAL
            DetectedEventType.NEW_MANDATE_CREATED, DetectedEventType.UPCOMING_RECURRING_DEBIT,
            DetectedEventType.SUCCESSFUL_RECURRING_PAYMENT -> {
                if (lowerText.contains("upi") || lowerText.contains("autopay") || lowerText.contains("mandate")) {
                    RecordType.UPI_AUTOPAY
                } else if (lowerText.contains("card")) {
                    RecordType.CARD_RECURRING
                } else if (lowerText.contains("bank") || lowerText.contains("nach")) {
                    RecordType.BANK_DEBIT
                } else {
                    RecordType.SUBSCRIPTION
                }
            }
            else -> RecordType.SUBSCRIPTION
        }

        // Frequency extraction
        val frequency = when {
            recordType == RecordType.FREE_TRIAL -> BillingFrequency.ONE_TIME
            lowerText.contains("yearly") || lowerText.contains("annual") -> BillingFrequency.YEARLY
            lowerText.contains("quarterly") -> BillingFrequency.QUARTERLY
            lowerText.contains("weekly") -> BillingFrequency.WEEKLY
            else -> BillingFrequency.MONTHLY
        }

        // Explicit Date extraction (NEVER fabricated!)
        val extractedDate = extractExplicitDate(fullText)

        // Confidence calculation & explainable evidence
        var confidence = 0.40f
        val reasons = mutableListOf<String>()

        reasons.add("Event classified as ${eventType.displayName}")
        when (eventType) {
            DetectedEventType.NEW_MANDATE_CREATED -> confidence += 0.25f
            DetectedEventType.FREE_TRIAL_ACTIVATED -> confidence += 0.25f
            DetectedEventType.UPCOMING_RECURRING_DEBIT -> confidence += 0.20f
            DetectedEventType.SUCCESSFUL_RECURRING_PAYMENT -> confidence += 0.15f
            DetectedEventType.SUBSCRIPTION_RENEWED -> confidence += 0.20f
            DetectedEventType.SUBSCRIPTION_CANCELLED -> confidence += 0.25f
            DetectedEventType.MANDATE_REVOKED -> confidence += 0.25f
            else -> {}
        }

        if (normalizedMerchant != "Unknown Merchant") {
            confidence += 0.15f
            reasons.add("Identified normalized merchant '$normalizedMerchant'")
        }

        if (amount > 0.0) {
            confidence += 0.15f
            reasons.add("Extracted amount $currency %.2f".format(amount))
        }

        if (extractedDate != null) {
            confidence += 0.15f
            val dateStr = LocalDate.ofEpochDay(extractedDate).toString()
            reasons.add("Explicit date found: $dateStr")
        } else {
            reasons.add("No explicit date in alert (user must provide renewal date)")
        }

        // Sanitized snippet for user privacy (scrubs OTP/card numbers, max 120 chars)
        val sanitizedSnippet = sanitizeContent(content.ifBlank { title })

        val fingerprint = computeEventFingerprint(
            packageName = packageName,
            normalizedMerchant = normalizedMerchant,
            eventType = eventType.name,
            amount = amount,
            dateEpochDay = extractedDate
        )

        return ParsedNotificationResult(
            isCandidate = true,
            eventType = eventType,
            extractedMerchant = rawMerchant.ifBlank { normalizedMerchant },
            normalizedMerchant = normalizedMerchant,
            extractedAmount = amount,
            extractedCurrency = currency,
            extractedRecordType = recordType,
            extractedFrequency = frequency,
            suggestedDueDateEpochDay = extractedDate,
            confidence = confidence.coerceIn(0.1f, 0.98f),
            confidenceReason = reasons.joinToString(" • "),
            sanitizedSnippet = sanitizedSnippet,
            eventFingerprint = fingerprint
        )
    }

    private fun notACandidate(type: DetectedEventType, reason: String): ParsedNotificationResult {
        return ParsedNotificationResult(
            isCandidate = false,
            eventType = type,
            extractedMerchant = "",
            normalizedMerchant = "",
            extractedAmount = 0.0,
            extractedCurrency = "USD",
            extractedRecordType = RecordType.SUBSCRIPTION,
            extractedFrequency = BillingFrequency.MONTHLY,
            suggestedDueDateEpochDay = null,
            confidence = 0f,
            confidenceReason = reason,
            sanitizedSnippet = "",
            eventFingerprint = ""
        )
    }

    fun extractExplicitDate(text: String): Long? {
        val today = LocalDate.now()

        // 1. Try patterns: dd/MM/yyyy, dd-MM-yyyy, yyyy-MM-dd
        val numericDateRegex = Regex("""\b(\d{1,2}[/-]\d{1,2}[/-]\d{4})\b""")
        val numMatch = numericDateRegex.find(text)
        if (numMatch != null) {
            val dateStr = numMatch.groupValues[1].replace("-", "/")
            val formatters = listOf(
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                DateTimeFormatter.ofPattern("d/M/yyyy")
            )
            for (fmt in formatters) {
                try {
                    val parsed = LocalDate.parse(dateStr, fmt)
                    if (parsed.isAfter(today.minusDays(7))) {
                        return parsed.toEpochDay()
                    }
                } catch (_: Exception) {}
            }
        }

        // 2. Try pattern: dd MMM yyyy (e.g., 28 Oct 2026, 28-Oct-2026)
        val textDateRegex = Regex("""\b(\d{1,2})[\s\-]+([A-Za-z]{3,9})[\s\-]+(\d{4})\b""")
        val textMatch = textDateRegex.find(text)
        if (textMatch != null) {
            val day = textMatch.groupValues[1]
            val month = textMatch.groupValues[2]
            val year = textMatch.groupValues[3]
            val candidateStr = "$day $month $year"

            val formatters = listOf(
                DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
            )
            for (fmt in formatters) {
                try {
                    val parsed = LocalDate.parse(candidateStr, fmt)
                    if (parsed.isAfter(today.minusDays(7))) {
                        return parsed.toEpochDay()
                    }
                } catch (_: Exception) {}
            }
        }

        // 3. Try relative phrasing: "in X days"
        val relativeRegex = Regex("""\bin\s+(\d{1,2})\s+days\b""", RegexOption.IGNORE_CASE)
        val relMatch = relativeRegex.find(text)
        if (relMatch != null) {
            val days = relMatch.groupValues[1].toLongOrNull()
            if (days != null && days in 1..90) {
                return today.plusDays(days).toEpochDay()
            }
        }

        return null // Never invent a renewal date if absent!
    }

    fun sanitizeContent(text: String): String {
        var sanitized = text
            // Mask 16-digit card numbers
            .replace(Regex("""\b\d{4}[ -]?\d{4}[ -]?\d{4}[ -]?\d{4}\b"""), "[CARD MASKED]")
            // Mask 6-digit OTP codes
            .replace(Regex("""\b\d{6}\b"""), "[CODE]")
            // Mask 4-digit PIN mentions
            .replace(Regex("""(?:pin|upi\s*pin)\s*[:=]?\s*\d{4,6}""", RegexOption.IGNORE_CASE), "[PIN MASKED]")
            .replace(Regex("""\s+"""), " ")
            .trim()

        return sanitized.take(120)
    }

    fun computeEventFingerprint(
        packageName: String,
        normalizedMerchant: String,
        eventType: String,
        amount: Double,
        dateEpochDay: Long?
    ): String {
        val input = "$packageName|$normalizedMerchant|$eventType|${"%.2f".format(amount)}|${dateEpochDay ?: -1}"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun deriveMerchantFromPackageOrTitle(packageName: String, title: String): String {
        return when {
            packageName.contains("netflix") -> "Netflix"
            packageName.contains("spotify") -> "Spotify"
            packageName.contains("amazon") -> "Amazon"
            packageName.contains("hotstar") -> "Disney+ Hotstar"
            packageName.contains("youtube") -> "YouTube"
            title.isNotBlank() && title.length in 3..25 -> title
            else -> "Unknown Merchant"
        }
    }
}
