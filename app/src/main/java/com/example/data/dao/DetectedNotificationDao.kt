package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.DetectedNotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectedNotificationDao {
    @Query("SELECT * FROM detected_notifications WHERE reviewStatus = 'PENDING' AND isDismissed = 0 ORDER BY detectedAt DESC")
    fun getPendingDetections(): Flow<List<DetectedNotificationEntity>>

    @Query("SELECT * FROM detected_notifications WHERE reviewStatus = 'REVIEW_LATER' AND isDismissed = 0 ORDER BY detectedAt DESC")
    fun getReviewLaterDetections(): Flow<List<DetectedNotificationEntity>>

    @Query("SELECT * FROM detected_notifications ORDER BY detectedAt DESC")
    fun getAllDetections(): Flow<List<DetectedNotificationEntity>>

    @Query("SELECT COUNT(*) FROM detected_notifications WHERE notificationHash = :hash")
    suspend fun countByHash(hash: String): Int

    @Query("SELECT COUNT(*) FROM detected_notifications WHERE eventFingerprint = :fingerprint")
    suspend fun countByFingerprint(fingerprint: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(detected: DetectedNotificationEntity): Long

    @Update
    suspend fun update(detected: DetectedNotificationEntity)

    @Query("UPDATE detected_notifications SET reviewStatus = :status WHERE id = :id")
    suspend fun updateReviewStatus(id: Long, status: String)

    @Query("UPDATE detected_notifications SET reviewStatus = 'DISMISSED', isDismissed = 1 WHERE id = :id")
    suspend fun dismiss(id: Long)

    @Query("UPDATE detected_notifications SET reviewStatus = 'CONFIRMED', isConfirmed = 1 WHERE id = :id")
    suspend fun markConfirmed(id: Long)

    @Query("UPDATE detected_notifications SET reviewStatus = 'REVIEW_LATER' WHERE id = :id")
    suspend fun markReviewLater(id: Long)

    @Query("UPDATE detected_notifications SET reviewStatus = 'IGNORED_SOURCE', isDismissed = 1 WHERE packageName = :packageName")
    suspend fun markIgnoredSource(packageName: String)

    @Query("DELETE FROM detected_notifications WHERE detectedAt < :cutoffTimestamp AND (isDismissed = 1 OR isConfirmed = 1)")
    suspend fun deleteOlderThan(cutoffTimestamp: Long): Int

    @Query("DELETE FROM detected_notifications")
    suspend fun deleteAll()
}
