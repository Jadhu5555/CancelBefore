package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.RecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Query("SELECT * FROM records ORDER BY nextDueDateEpochDay ASC")
    fun getAllRecords(): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE status = 'ACTIVE' ORDER BY nextDueDateEpochDay ASC")
    fun getActiveRecords(): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE status = 'ACTIVE' ORDER BY nextDueDateEpochDay ASC")
    suspend fun getActiveRecordsList(): List<RecordEntity>

    @Query("SELECT * FROM records WHERE id = :id")
    fun getRecordById(id: Long): Flow<RecordEntity?>

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun getRecordByIdImmediate(id: Long): RecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: RecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<RecordEntity>)

    @Update
    suspend fun update(record: RecordEntity)

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM records")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM records")
    suspend fun getCount(): Int
}
