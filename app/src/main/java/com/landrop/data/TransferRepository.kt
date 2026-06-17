package com.landrop.data

import kotlinx.coroutines.flow.Flow

class TransferRepository(private val transferDao: TransferDao) {
    val allTransfers: Flow<List<TransferEntity>> = transferDao.getAllTransfersFlow()

    suspend fun getAllTransfersList(): List<TransferEntity> {
        return transferDao.getAllTransfersList()
    }

    suspend fun getTransferById(id: Long): TransferEntity? {
        return transferDao.getTransferById(id)
    }

    suspend fun insert(transfer: TransferEntity): Long {
        return transferDao.insertTransfer(transfer)
    }

    suspend fun update(transfer: TransferEntity) {
        transferDao.updateTransfer(transfer)
    }

    suspend fun updateProgress(id: Long, progress: Float, status: String) {
        transferDao.updateProgress(id, progress, status)
    }

    suspend fun clearAll() {
        transferDao.clearHistory()
    }
}
