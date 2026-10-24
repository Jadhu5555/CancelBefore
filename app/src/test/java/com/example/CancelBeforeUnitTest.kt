package com.example

import com.example.data.entity.RecordEntity
import com.example.model.BillingFrequency
import com.example.model.DetectedEventType
import com.example.model.RecordType
import com.example.service.DetectionAnalyzer
import com.example.service.MerchantNormalizer
import com.example.service.ReminderScheduler
import com.example.service.email.GmailOAuthDetectionAdapter
import com.example.util.BackupManager
import com.example.util.CsvExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CancelBeforeUnitTest {

    @Test
    fun testDaysUntilDueCalculation() {
        val today = LocalDate.of(2026, 10, 24).toEpochDay()
        val record = RecordEntity(
            name = "Netflix",
            nextDueDateEpochDay = LocalDate.of(2026, 10, 27).toEpochDay()
        )
        assertEquals(3L, record.daysUntilDue(today))
    }

    @Test
    fun testMonthlyAndAnnualEquivalents() {
        val yearly = RecordEntity(
            name = "Cloud Storage",
            amount = 120.0,
            frequency = BillingFrequency.YEARLY.name
        )
        assertEquals(10.0, yearly.monthlyEquivalentAmount(), 0.01)
        assertEquals(120.0, yearly.annualEquivalentAmount(), 0.01)

        val weekly = RecordEntity(
            name = "Meal Kit",
            amount = 10.0,
            frequency = BillingFrequency.WEEKLY.name
        )
        assertEquals(43.33, weekly.monthlyEquivalentAmount(), 0.1)
    }

    @Test
    fun testDetectionAnalyzerNewMandateCreated() {
        val title = "Google Pay AutoPay Mandate"
        val snippet = "New AutoPay mandate of ₹499.00 for Netflix has been approved on your account."
        val result = DetectionAnalyzer.analyze(
            packageName = "com.google.android.apps.nbu.paisa.user",
            title = title,
            content = snippet
        )

        assertTrue(result.isCandidate)
        assertEquals(DetectedEventType.NEW_MANDATE_CREATED, result.eventType)
        assertEquals("Netflix", result.normalizedMerchant)
        assertEquals(499.0, result.extractedAmount, 0.01)
        assertEquals("INR", result.extractedCurrency)
        assertTrue(result.confidence > 0.7f)
    }

    @Test
    fun testOneTimeTransactionVersusRecurring() {
        // Ordinary one-time transaction without recurring signals must NOT be a candidate
        val title = "Payment Successful"
        val snippet = "Paid ₹150 to Swiggy using UPI. Ref: 987654321."
        val result = DetectionAnalyzer.analyze(
            packageName = "com.phonepe.app",
            title = title,
            content = snippet
        )

        assertFalse(result.isCandidate)
        assertEquals(DetectedEventType.ONE_TIME_TRANSACTION, result.eventType)

        // OTP verification alert must also be filtered out
        val otpAlert = DetectionAnalyzer.analyze(
            packageName = "com.bank.app",
            title = "Bank OTP Alert",
            content = "Your OTP for transaction of $50.00 is 123456. Do not share."
        )
        assertFalse(otpAlert.isCandidate)
    }

    @Test
    fun testDetectionAnalyzerFreeTrial() {
        val title = "Amazon Prime Membership"
        val snippet = "Your 30 days free trial period started on 15/11/2026. Avoid charges by cancelling before renewal."
        val result = DetectionAnalyzer.analyze(
            packageName = "com.amazon.mShop.android.shopping",
            title = title,
            content = snippet
        )

        assertTrue(result.isCandidate)
        assertEquals(DetectedEventType.FREE_TRIAL_ACTIVATED, result.eventType)
        assertEquals(RecordType.FREE_TRIAL, result.extractedRecordType)
        assertNotNull(result.suggestedDueDateEpochDay)
    }

    @Test
    fun testUpcomingDebitNotice() {
        val title = "AutoPay Alert"
        val snippet = "Your mandate for Spotify will be debited on 28-Oct-2026 for ₹119.00."
        val result = DetectionAnalyzer.analyze(
            packageName = "com.phonepe.app",
            title = title,
            content = snippet
        )

        assertTrue(result.isCandidate)
        assertEquals(DetectedEventType.UPCOMING_RECURRING_DEBIT, result.eventType)
        assertEquals("Spotify", result.normalizedMerchant)
        assertEquals(119.0, result.extractedAmount, 0.01)
        assertNotNull(result.suggestedDueDateEpochDay)
    }

    @Test
    fun testSuccessfulRecurringPaymentVersusNewMandate() {
        // A successful recurring payment must be classified as SUCCESSFUL_RECURRING_PAYMENT, not new mandate
        val title = "AutoPay Payment"
        val snippet = "AutoPay payment of ₹499.00 for Netflix Entertainment Inc successful."
        val result = DetectionAnalyzer.analyze(
            packageName = "net.one97.paytm",
            title = title,
            content = snippet
        )

        assertTrue(result.isCandidate)
        assertEquals(DetectedEventType.SUCCESSFUL_RECURRING_PAYMENT, result.eventType)
        assertEquals("Netflix", result.normalizedMerchant)
    }

    @Test
    fun testSubscriptionRenewed() {
        val title = "Membership Renewal"
        val snippet = "Your plan has been renewed automatically for $14.99 at Amazon Prime."
        val result = DetectionAnalyzer.analyze(
            packageName = "com.amazon.mShop.android.shopping",
            title = title,
            content = snippet
        )

        assertTrue(result.isCandidate)
        assertEquals(DetectedEventType.SUBSCRIPTION_RENEWED, result.eventType)
        assertEquals("Amazon Prime", result.normalizedMerchant)
    }

    @Test
    fun testCancellationAndRevocation() {
        val cancellation = DetectionAnalyzer.analyze(
            packageName = "com.spotify.music",
            title = "Subscription Update",
            content = "Your subscription cancelled for Spotify Premium. Access continues until billing cycle end."
        )
        assertTrue(cancellation.isCandidate)
        assertEquals(DetectedEventType.SUBSCRIPTION_CANCELLED, cancellation.eventType)

        val revocation = DetectionAnalyzer.analyze(
            packageName = "com.google.android.apps.nbu.paisa.user",
            title = "Mandate Update",
            content = "Your UPI AutoPay mandate revoked for Disney+ Hotstar."
        )
        assertTrue(revocation.isCandidate)
        assertEquals(DetectedEventType.MANDATE_REVOKED, revocation.eventType)
    }

    @Test
    fun testDetectionAnalyzerMissingDateNeverInvented() {
        val title = "Subscription Alert"
        val snippet = "Your standing instruction for Spotify has been activated for $9.99 monthly."
        val result = DetectionAnalyzer.analyze(
            packageName = "com.spotify.music",
            title = title,
            content = snippet
        )

        assertTrue(result.isCandidate)
        assertNull(result.suggestedDueDateEpochDay) // Never invented!
    }

    @Test
    fun testMerchantNormalization() {
        assertEquals("Netflix", MerchantNormalizer.normalize("NETFLIX ENTERTAINMENT INC"))
        assertEquals("Netflix", MerchantNormalizer.normalize("netflix.com"))
        assertEquals("Spotify", MerchantNormalizer.normalize("SPOTIFY AB"))
        assertEquals("Amazon Prime", MerchantNormalizer.normalize("AMZN PRIME VIDEO"))
        assertEquals("Google One", MerchantNormalizer.normalize("GOOGLE *Google One"))
        assertEquals("Apple Services", MerchantNormalizer.normalize("APPLE.COM/BILL"))
        assertEquals("Microsoft 365", MerchantNormalizer.normalize("MSFT * 365"))
        assertEquals("ChatGPT Plus", MerchantNormalizer.normalize("OPENAI *CHATGPT"))
    }

    @Test
    fun testSanitizeSensitiveDataScrubbed() {
        val raw = "AutoPay for Netflix using card 4111 2222 3333 4444 with OTP 654321 approved."
        val sanitized = DetectionAnalyzer.sanitizeContent(raw)
        assertFalse(sanitized.contains("4111 2222 3333 4444"))
        assertFalse(sanitized.contains("654321"))
        assertTrue(sanitized.contains("[CARD MASKED]"))
        assertTrue(sanitized.contains("[CODE]"))
    }

    @Test
    fun testNotificationFingerprintDeduplication() {
        val fp1 = DetectionAnalyzer.computeEventFingerprint("pkg", "Netflix", "NEW_MANDATE_CREATED", 499.0, 19000L)
        val fp2 = DetectionAnalyzer.computeEventFingerprint("pkg", "Netflix", "NEW_MANDATE_CREATED", 499.0, 19000L)
        val fp3 = DetectionAnalyzer.computeEventFingerprint("pkg", "Netflix", "UPCOMING_RECURRING_DEBIT", 499.0, 19000L)

        assertEquals(fp1, fp2)
        assertTrue(fp1 != fp3)
    }

    @Test
    fun testEmailDetectionAdapter() {
        val adapter = GmailOAuthDetectionAdapter(isEnabled = false, isConnected = false)
        assertFalse(adapter.isEnabled)

        val receipt = adapter.parseReceipt(
            messageId = "msg_123",
            sender = "billing@netflix.com",
            subject = "Your Netflix Subscription Renewal",
            snippet = "Your subscription renewed for $19.99 on 15/11/2026.",
            internalDateEpochMs = System.currentTimeMillis()
        )

        assertNotNull(receipt)
        assertEquals("Netflix", receipt!!.normalizedMerchant)
        assertEquals(19.99, receipt.amount, 0.01)

        val fingerprints = setOf(receipt.deduplicationFingerprint)
        assertTrue(adapter.isDuplicateWithExisting(receipt, fingerprints))
    }

    @Test
    fun testCsvExportFormat() {
        val record = RecordEntity(
            id = 42,
            name = "Test Merchant",
            amount = 14.99,
            currency = "USD",
            startDateEpochDay = LocalDate.of(2026, 1, 1).toEpochDay(),
            nextDueDateEpochDay = LocalDate.of(2026, 2, 1).toEpochDay()
        )
        val csv = CsvExporter.generateCsv(listOf(record))
        assertTrue(csv.contains("Test Merchant"))
        assertTrue(csv.contains("14.99"))
        assertTrue(csv.contains("2026-02-01"))
    }

    @Test
    fun testBackupManagerJsonRoundTrip() {
        val original = listOf(
            RecordEntity(
                name = "Disney Plus",
                amount = 7.99,
                currency = "USD",
                category = "Entertainment"
            )
        )
        val json = BackupManager.exportToJson(original)
        val imported = BackupManager.importFromJson(json)

        assertEquals(1, imported.size)
        assertEquals("Disney Plus", imported[0].name)
        assertEquals(7.99, imported[0].amount, 0.01)
        assertEquals("Entertainment", imported[0].category)
    }
}
