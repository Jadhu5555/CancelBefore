package com.example.util

import com.example.data.entity.RecordEntity
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object BackupManager {

    fun exportToJson(records: List<RecordEntity>): String {
        val jsonArray = JSONArray()
        for (r in records) {
            val obj = JSONObject().apply {
                put("name", r.name)
                put("amount", r.amount)
                put("currency", r.currency)
                put("recordType", r.recordType)
                put("frequency", r.frequency)
                put("customFrequencyDays", r.customFrequencyDays)
                put("startDateEpochDay", r.startDateEpochDay)
                put("nextDueDateEpochDay", r.nextDueDateEpochDay)
                if (r.trialEndEpochDay != null) put("trialEndEpochDay", r.trialEndEpochDay)
                put("reminderOffsetsCsv", r.reminderOffsetsCsv)
                put("category", r.category)
                put("notes", r.notes)
                put("managementUrl", r.managementUrl)
                put("detectionSource", r.detectionSource)
                put("detectionConfidence", r.detectionConfidence.toDouble())
                put("status", r.status)
            }
            jsonArray.put(obj)
        }
        val root = JSONObject().apply {
            put("app", "CancelBefore")
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("records", jsonArray)
        }
        return root.toString(2)
    }

    fun importFromJson(jsonString: String): List<RecordEntity> {
        if (jsonString.isBlank()) return emptyList()
        val list = mutableListOf<RecordEntity>()
        try {
            val root = JSONObject(jsonString)
            val array = root.optJSONArray("records") ?: return emptyList()
            val today = LocalDate.now().toEpochDay()

            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim()
                if (name.isBlank()) continue // Skip records with empty name

                val nextDue = obj.optLong("nextDueDateEpochDay", today + 30)
                list.add(
                    RecordEntity(
                        id = 0, // auto-generate on insert
                        name = name,
                        amount = obj.optDouble("amount", 0.0).coerceAtLeast(0.0),
                        currency = obj.optString("currency", "USD").take(5),
                        recordType = obj.optString("recordType", "SUBSCRIPTION"),
                        frequency = obj.optString("frequency", "MONTHLY"),
                        customFrequencyDays = obj.optInt("customFrequencyDays", 30).coerceAtLeast(1),
                        startDateEpochDay = obj.optLong("startDateEpochDay", today),
                        nextDueDateEpochDay = nextDue,
                        trialEndEpochDay = if (obj.has("trialEndEpochDay")) obj.getLong("trialEndEpochDay") else null,
                        reminderOffsetsCsv = obj.optString("reminderOffsetsCsv", "7,3,1,0"),
                        category = obj.optString("category", "Entertainment"),
                        notes = obj.optString("notes", ""),
                        managementUrl = obj.optString("managementUrl", ""),
                        detectionSource = obj.optString("detectionSource", "IMPORT"),
                        detectionConfidence = obj.optDouble("detectionConfidence", 1.0).toFloat(),
                        status = obj.optString("status", "ACTIVE")
                    )
                )
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return list
    }
}
