package com.example

import android.content.Context
import android.content.Intent
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.CancelBeforeDatabase
import com.example.data.entity.RecordEntity
import com.example.model.BillingFrequency
import com.example.model.DetectedEventType
import com.example.model.RecordStatus
import com.example.model.RecordType
import com.example.receiver.BootReceiver
import com.example.service.DetectionAnalyzer
import com.example.service.MerchantNormalizer
import com.example.service.ReminderScheduler
import com.example.util.BackupManager
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
class DetectionCorpusTest {

    // 1. New UPI AutoPay mandate
    @Test
    fun testCorpusNewUpiAutoPayMandate() {
        val result = DetectionAnalyzer.analyze(
            packageName = "com.phonepe.app",
            title = "AutoPay Mandate Setup",
            content = "New AutoPay mandate of ₹499.00 for Netflix Entertainment Inc approved on your account."
        )
        assertTrue(result.isCandidate)
        assertEquals(DetectedEventType.NEW_MANDATE_CREATED, result.eventType)
        assertEquals("Netflix", result.normalizedMerchant)
        assertEquals(499.0, result.extractedAmount, 0.01)
        assertEquals("INR", result.extractedCurrency)
        assertEquals(RecordType.UPI_AUTOPAY, result.extractedRecordType)
    }

    // 2. Bank standing instruction
    @Test
    fun testCorpusBankStandingInstruction() {
        val result = DetectionAnalyzer.analyze(
            packageName = "com.hdfc.bank",
            title = "Standing Instruction Registered",
            content = "Standing instruction created for Rs. 999.00 at Disney+ Hotstar successfully."
        )
        assertTrue(result.isCandidate)
        assertEquals(DetectedEventType.NEW_MANDATE_CREATED, result.eventType)
        assertEquals("Disney+ Hotstar", result.normalizedMerchant)
        assertEquals(999.0, result.extractedAmount, 0.01)
        assertEquals("INR", result.extractedCurrency)
    }

    // 3. Card recurring-payment confirmation
    @Test
    fun testCorpusCardRecurringPaymentConfirmation() {
        // A recurring payment MUST be classified as SUCCESSFUL_RECURRING_PAYMENT, not a new mandate
        val result = DetectionAnalyzer.analyze(
            packageName = "com.visa.alerts",
            title = "Recurring Payment Completed",
            content = "Recurring payment completed: Auto-debit for Spotify of $10.99 was executed on your Visa."
        )
        assertTrue(result.isCandidate)
        assertEquals(DetectedEventType.SUCCESSFUL_RECURRING_PAYMENT, result.eventType)
        assertEquals("Spotify", result.normalizedMerchant)
        assertEquals(10.99, result.extractedAmount, 0.01)
        assertEquals("USD", result.extractedCurrency)
    }

    // 4. Subscription renewal
    @Test
    fun testCorpusSubscriptionRenewal() {
        val result = DetectionAnalyzer.analyze(
            packageName = "com.apple.android.music",
            title = "Receipt from Apple",
            content = "Your subscription renewed for $10.99 on 25/11/2026. Access to Apple Services continues."
        )
        assertTrue(result.isCandidate)
        assertEquals(DetectedEventType.SUBSCRIPTION_RENEWED, result.eventType)
        assertEquals("Apple Services", result.normalizedMerchant)
        assertEquals(10.99, result.extractedAmount, 0.01)
    }

    // 5. Free-trial confirmation
    @Test
    fun testCorpusFreeTrialConfirmation() {
        val result = DetectionAnalyzer.analyze(
            packageName = "com.amazon.mShop.android.shopping",
            title = "Amazon Prime Welcome",
            content = "Your 30 days free trial period started on 15/12/2026. Auto-renews at $14.99/mo unless cancelled."
        )
        assertTrue(result.isCandidate)
        assertEquals(DetectedEventType.FREE_TRIAL_ACTIVATED, result.eventType)
        assertEquals(RecordType.FREE_TRIAL, result.extractedRecordType)
        assertEquals("Amazon Prime", result.normalizedMerchant)
        assertNotNull(result.suggestedDueDateEpochDay)
    }

    // 6. Upcoming debit notification
    @Test
    fun testCorpusUpcomingDebitNotification() {
        val result = DetectionAnalyzer.analyze(
            packageName = "net.one97.paytm",
            title = "AutoPay Alert: Your mandate",
            content = "Your mandate for Google One will be debited on 28-Nov-2026 for ₹130.00."
        )
        assertTrue(result.isCandidate)
        assertEquals(DetectedEventType.UPCOMING_RECURRING_DEBIT, result.eventType)
        assertEquals("Google One", result.normalizedMerchant)
        assertEquals(130.0, result.extractedAmount, 0.01)
        assertNotNull(result.suggestedDueDateEpochDay)
    }

    // 7. Failed recurring payment
    @Test
    fun testCorpusFailedRecurringPayment() {
        val result = DetectionAnalyzer.analyze(
            packageName = "com.phonepe.app",
            title = "AutoPay Alert",
            content = "AutoPay failed for Netflix due to insufficient balance. Retry will occur in 24 hours."
        )
        assertTrue(result.isCandidate)
        assertEquals(DetectedEventType.FAILED_PAYMENT, result.eventType)
        assertEquals("Netflix", result.normalizedMerchant)
    }

    // 8. Ordinary payment (Swiggy, grocery, store QR) - MUST NOT BE CANDIDATE
    @Test
    fun testCorpusOrdinaryPaymentFilteredOut() {
        val swiggyOneTime = DetectionAnalyzer.analyze(
            packageName = "com.phonepe.app",
            title = "Payment Successful",
            content = "Paid ₹349 to Swiggy using UPI. Transaction ID: 12345678."
        )
        assertFalse("One-time Swiggy payment must not be a recurring candidate", swiggyOneTime.isCandidate)
        assertEquals(DetectedEventType.ONE_TIME_TRANSACTION, swiggyOneTime.eventType)

        val qrPayment = DetectionAnalyzer.analyze(
            packageName = "com.google.android.apps.nbu.paisa.user",
            title = "Payment to Merchant",
            content = "Paid at store using QR ₹85.00."
        )
        assertFalse(qrPayment.isCandidate)
        assertEquals(DetectedEventType.ONE_TIME_TRANSACTION, qrPayment.eventType)
    }

    // 9. OTP message - MUST NOT BE CANDIDATE
    @Test
    fun testCorpusOtpFilteredOut() {
        val otpAlert = DetectionAnalyzer.analyze(
            packageName = "com.bank.app",
            title = "Verification Code",
            content = "Your OTP for transaction of $150.00 is 987654. Do not disclose to anyone."
        )
        assertFalse(otpAlert.isCandidate)
        assertEquals(DetectedEventType.ONE_TIME_TRANSACTION, otpAlert.eventType)
    }

    // 10. Refund notification - MUST NOT BE CANDIDATE
    @Test
    fun testCorpusRefundFilteredOut() {
        val refund = DetectionAnalyzer.analyze(
            packageName = "com.amazon.mShop.android.shopping",
            title = "Refund Processed",
            content = "Refund credited of $29.99 for returned items."
        )
        assertFalse(refund.isCandidate)
        assertEquals(DetectedEventType.ONE_TIME_TRANSACTION, refund.eventType)
    }

    // 11. Cancellation confirmation
    @Test
    fun testCorpusCancellationConfirmation() {
        val cancellation = DetectionAnalyzer.analyze(
            packageName = "com.spotify.music",
            title = "Plan Update",
            content = "Subscription cancelled for Spotify Premium. Enjoy music until cycle ends."
        )
        assertTrue(cancellation.isCandidate)
        assertEquals(DetectedEventType.SUBSCRIPTION_CANCELLED, cancellation.eventType)
        assertEquals("Spotify", cancellation.normalizedMerchant)
    }

    // 12. Mandate revocation
    @Test
    fun testCorpusMandateRevocation() {
        val revocation = DetectionAnalyzer.analyze(
            packageName = "com.google.android.apps.nbu.paisa.user",
            title = "Mandate Management",
            content = "Your standing instruction revoked for Microsoft 365."
        )
        assertTrue(revocation.isCandidate)
        assertEquals(DetectedEventType.MANDATE_REVOKED, revocation.eventType)
        assertEquals("Microsoft 365", revocation.normalizedMerchant)
    }

    // 13. Unrelated notification - MUST NOT BE CANDIDATE
    @Test
    fun testCorpusUnrelatedNotificationFilteredOut() {
        val chat = DetectionAnalyzer.analyze(
            packageName = "com.whatsapp",
            title = "John Doe",
            content = "Hey, let's meet up tomorrow at 6 PM!"
        )
        assertFalse(chat.isCandidate)
        assertEquals(DetectedEventType.UNRELATED_NOTIFICATION, chat.eventType)

        val promo = DetectionAnalyzer.analyze(
            packageName = "com.deals.app",
            title = "Flash Sale!",
            content = "Get up to 70% off on all clothing items this weekend only."
        )
        assertFalse(promo.isCandidate)
        assertEquals(DetectedEventType.UNRELATED_NOTIFICATION, promo.eventType)
    }

    // 14. Redacted or incomplete notification - Graceful handling without exceptions
    @Test
    fun testCorpusRedactedOrMalformedNotification() {
        val emptyResult = DetectionAnalyzer.analyze(
            packageName = "com.unknown.app",
            title = "",
            content = ""
        )
        assertFalse(emptyResult.isCandidate)

        val snippetWithConflictingTokens = DetectionAnalyzer.analyze(
            packageName = "com.test.bank",
            title = "Alert",
            content = "AutoPay mandate registered with no explicit date and no clear sum."
        )
        assertTrue(snippetWithConflictingTokens.isCandidate)
        assertNull(snippetWithConflictingTokens.suggestedDueDateEpochDay)
        assertEquals(0.0, snippetWithConflictingTokens.extractedAmount, 0.01)
    }

    // 15. Month-end & Leap Year Dates Handling
    @Test
    fun testMonthEndAndLeapYearArithmetic() {
        // Leap year: 2028 is a leap year (Feb 29)
        val leapYearStart = LocalDate.of(2028, 1, 31)
        val nextMonth = leapYearStart.plusMonths(1)
        assertEquals(LocalDate.of(2028, 2, 29), nextMonth)

        // Non-leap year: 2027 (Feb 28)
        val nonLeapYearStart = LocalDate.of(2027, 1, 31)
        val nextMonthNonLeap = nonLeapYearStart.plusMonths(1)
        assertEquals(LocalDate.of(2027, 2, 28), nextMonthNonLeap)

        // RecordEntity daysUntilDue calculation across month-end
        val record = RecordEntity(
            name = "Test Subscription",
            amount = 9.99,
            startDateEpochDay = leapYearStart.toEpochDay(),
            nextDueDateEpochDay = nextMonth.toEpochDay()
        )
        assertEquals(29L, record.daysUntilDue(leapYearStart.toEpochDay()))
    }

    // 16. Safe Non-Destructive Migration Logic Verification
    @Test
    fun testMigration1To2SqlSyntax() {
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name(null) // In-memory database
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create v1 schema
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS records (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            amount REAL NOT NULL,
                            currency TEXT NOT NULL,
                            recordType TEXT NOT NULL,
                            frequency TEXT NOT NULL,
                            customFrequencyDays INTEGER NOT NULL,
                            startDateEpochDay INTEGER NOT NULL,
                            nextDueDateEpochDay INTEGER NOT NULL,
                            trialEndEpochDay INTEGER,
                            reminderOffsetsCsv TEXT NOT NULL,
                            category TEXT NOT NULL,
                            notes TEXT NOT NULL,
                            managementUrl TEXT NOT NULL,
                            detectionSource TEXT NOT NULL,
                            detectionConfidence REAL NOT NULL,
                            detectionRawSnippet TEXT,
                            status TEXT NOT NULL,
                            cancelledDateEpochDay INTEGER,
                            effectiveCancellationEpochDay INTEGER,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS detected_notifications (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            appName TEXT NOT NULL,
                            packageName TEXT NOT NULL,
                            detectedTitle TEXT NOT NULL,
                            detectedSnippet TEXT NOT NULL,
                            extractedMerchant TEXT NOT NULL,
                            extractedAmount REAL NOT NULL,
                            extractedCurrency TEXT NOT NULL,
                            extractedRecordType TEXT NOT NULL,
                            extractedFrequency TEXT NOT NULL,
                            suggestedDueDateEpochDay INTEGER,
                            confidence REAL NOT NULL,
                            confidenceReason TEXT NOT NULL,
                            notificationHash TEXT NOT NULL,
                            detectedAt INTEGER NOT NULL,
                            isDismissed INTEGER NOT NULL,
                            isConfirmed INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )

                    // Insert pre-existing user subscription
                    db.execSQL("INSERT INTO records (name, amount, currency, recordType, frequency, customFrequencyDays, startDateEpochDay, nextDueDateEpochDay, reminderOffsetsCsv, category, notes, managementUrl, detectionSource, detectionConfidence, status, createdAt, updatedAt) VALUES ('Existing Netflix', 15.99, 'USD', 'SUBSCRIPTION', 'MONTHLY', 30, 20000, 20030, '7,3,1,0', 'Entertainment', '', '', 'MANUAL', 1.0, 'ACTIVE', 1000, 1000)")

                    // Insert pre-existing v1 detected candidate
                    db.execSQL("INSERT INTO detected_notifications (appName, packageName, detectedTitle, detectedSnippet, extractedMerchant, extractedAmount, extractedCurrency, extractedRecordType, extractedFrequency, confidence, confidenceReason, notificationHash, detectedAt, isDismissed, isConfirmed) VALUES ('Google Pay', 'com.google.android.apps.nbu.paisa.user', 'AutoPay Mandate', 'Mandate registered for Hotstar', 'Hotstar', 499.0, 'INR', 'UPI_AUTOPAY', 'MONTHLY', 0.9, 'Reason', 'hash_abc_123', 5000, 0, 0)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    if (oldVersion == 1 && newVersion == 2) {
                        CancelBeforeDatabase.MIGRATION_1_2.migrate(db)
                    }
                }
            })
            .build()

        val helper = helperFactory.create(config)
        val db = helper.writableDatabase

        // Execute migration 1 -> 2
        CancelBeforeDatabase.MIGRATION_1_2.migrate(db)

        // Verify existing user subscription SURVIVED the migration
        val cursorRecord = db.query("SELECT name, amount FROM records WHERE name = 'Existing Netflix'")
        assertTrue("Existing record must survive Room migration", cursorRecord.moveToFirst())
        assertEquals("Existing Netflix", cursorRecord.getString(0))
        assertEquals(15.99, cursorRecord.getDouble(1), 0.01)
        cursorRecord.close()

        // Verify existing detected notification survived with backfilled v2 columns
        val cursorNotif = db.query("SELECT extractedMerchant, normalizedMerchant, eventCategory, eventFingerprint, reviewStatus FROM detected_notifications WHERE extractedMerchant = 'Hotstar'")
        assertTrue("Detected notification must survive migration", cursorNotif.moveToFirst())
        assertEquals("Hotstar", cursorNotif.getString(0))
        assertEquals("Hotstar", cursorNotif.getString(1)) // Backfilled from extractedMerchant
        assertEquals("NEW_MANDATE_CREATED", cursorNotif.getString(2)) // Backfilled default
        assertTrue("Fingerprint must be populated", cursorNotif.getString(3).isNotBlank())
        assertEquals("PENDING", cursorNotif.getString(4))
        cursorNotif.close()

        db.close()
    }

    // 17. Broadcast Receiver Trigger Safety
    @Test
    fun testBootReceiverActions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = BootReceiver()

        // Verify handling of ACTION_MY_PACKAGE_REPLACED
        val packageReplacedIntent = Intent(Intent.ACTION_MY_PACKAGE_REPLACED)
        receiver.onReceive(context, packageReplacedIntent)

        // Verify handling of ACTION_TIMEZONE_CHANGED
        val timezoneIntent = Intent(Intent.ACTION_TIMEZONE_CHANGED)
        receiver.onReceive(context, timezoneIntent)

        // Verify handling of ACTION_BOOT_COMPLETED
        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(context, bootIntent)
    }

    // 18. Reminder Scheduler Exact Alarm & Inexact Fallback Check
    @Test
    fun testReminderSchedulerExactFallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ReminderScheduler.createNotificationChannel(context)

        val record = RecordEntity(
            id = 999L,
            name = "Test Spotify",
            amount = 9.99,
            startDateEpochDay = LocalDate.now().toEpochDay(),
            nextDueDateEpochDay = LocalDate.now().plusDays(5).toEpochDay()
        )

        // Scheduling must succeed without exceptions
        val scheduled = ReminderScheduler.scheduleRemindersForRecord(context, record)
        assertTrue(scheduled >= 0)

        // Cancel must execute cleanly
        ReminderScheduler.cancelRemindersForRecord(context, 999L)
    }
}
