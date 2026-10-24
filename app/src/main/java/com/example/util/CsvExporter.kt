package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.entity.RecordEntity
import java.io.File
import java.io.FileWriter
import java.time.LocalDate

object CsvExporter {

    fun generateCsv(records: List<RecordEntity>): String {
        val sb = StringBuilder()
        // Header
        sb.append("ID,Name,Type,Amount,Currency,Frequency,StartDate,NextDueDate,TrialEndDate,Status,Category,Reminders,ManagementUrl,Notes\n")

        for (r in records) {
            val startDate = LocalDate.ofEpochDay(r.startDateEpochDay).toString()
            val nextDueDate = LocalDate.ofEpochDay(r.nextDueDateEpochDay).toString()
            val trialEnd = r.trialEndEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: ""

            sb.append(escape(r.id.toString())).append(",")
            sb.append(escape(r.name)).append(",")
            sb.append(escape(r.recordType)).append(",")
            sb.append(r.amount).append(",")
            sb.append(escape(r.currency)).append(",")
            sb.append(escape(r.frequency)).append(",")
            sb.append(escape(startDate)).append(",")
            sb.append(escape(nextDueDate)).append(",")
            sb.append(escape(trialEnd)).append(",")
            sb.append(escape(r.status)).append(",")
            sb.append(escape(r.category)).append(",")
            sb.append(escape(r.reminderOffsetsCsv)).append(",")
            sb.append(escape(r.managementUrl)).append(",")
            sb.append(escape(r.notes)).append("\n")
        }
        return sb.toString()
    }

    private fun escape(data: String): String {
        val escaped = data.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    fun exportAndShare(context: Context, records: List<RecordEntity>) {
        val csvContent = generateCsv(records)
        val exportFile = File(context.cacheDir, "cancelbefore_export_${System.currentTimeMillis()}.csv")
        FileWriter(exportFile).use { writer ->
            writer.write(csvContent)
        }

        val fileUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            exportFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "CancelBefore Export")
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share CancelBefore CSV").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
