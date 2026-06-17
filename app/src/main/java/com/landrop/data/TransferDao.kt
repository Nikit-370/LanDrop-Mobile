package com.landrop.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfers ORDER BY timestamp DESC")
    fun getAllTransfersFlow(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers ORDER BY timestamp DESC")
    suspend fun getAllTransfersList(): List<TransferEntity>

    @Query("SELECT * FROM transfers WHERE id = :id LIMIT 1")
    suspend fun getTransferById(id: Long): TransferEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(transfer: TransferEntity): Long

    @Update
    suspend fun updateTransfer(transfer: TransferEntity)

    @Query("UPDATE transfers SET status = :status, progress = :progress WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Float, status: String)

    @Query("DELETE FROM transfers")
    suspend fun clearHistory()
}
