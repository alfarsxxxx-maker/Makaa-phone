package com.example.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.database.entities.AuditLogEntity
import com.example.database.entities.BackupMetadataEntity
import com.example.database.entities.SyncConflictEntity
import com.example.database.entities.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SystemIntegrityDao {
    @Query("SELECT * FROM sync_queue WHERE shopId = :shopId ORDER BY createdAt DESC LIMIT :limit")
    fun observeSyncQueue(shopId: String, limit: Int = 200): Flow<List<SyncQueueEntity>>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE shopId = :shopId AND syncState IN ('PENDING', 'RETRYING', 'FAILED')")
    fun observePendingSyncCount(shopId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE shopId = :shopId AND syncState = 'FAILED'")
    fun observeFailedSyncCount(shopId: String): Flow<Int>

    @Query("SELECT * FROM sync_queue WHERE shopId = :shopId AND syncState IN ('PENDING', 'RETRYING', 'FAILED') ORDER BY createdAt ASC")
    suspend fun getPendingSyncOperations(shopId: String): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE shopId = :shopId ORDER BY createdAt ASC")
    suspend fun getAllSyncQueue(shopId: String): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE deduplicationKey = :deduplicationKey LIMIT 1")
    suspend fun getSyncOperationByDeduplicationKey(deduplicationKey: String): SyncQueueEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSyncOperation(operation: SyncQueueEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSyncOperationsIgnoreDuplicates(operations: List<SyncQueueEntity>)

    @Update
    suspend fun updateSyncOperation(operation: SyncQueueEntity)

    @Query("SELECT * FROM sync_conflicts WHERE shopId = :shopId ORDER BY detectedAt DESC")
    fun observeSyncConflicts(shopId: String): Flow<List<SyncConflictEntity>>

    @Query("SELECT * FROM sync_conflicts WHERE shopId = :shopId ORDER BY detectedAt ASC")
    suspend fun getAllSyncConflicts(shopId: String): List<SyncConflictEntity>

    @Query("SELECT COUNT(*) FROM sync_conflicts WHERE shopId = :shopId AND resolutionStatus = 'UNRESOLVED'")
    fun observeUnresolvedConflictCount(shopId: String): Flow<Int>

    @Query("SELECT * FROM sync_conflicts WHERE localId = :conflictId LIMIT 1")
    suspend fun getConflictById(conflictId: String): SyncConflictEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSyncConflict(conflict: SyncConflictEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSyncConflictsIgnoreDuplicates(conflicts: List<SyncConflictEntity>)

    @Update
    suspend fun updateSyncConflict(conflict: SyncConflictEntity)

    @Query("SELECT * FROM audit_logs WHERE shopId = :shopId ORDER BY timestamp DESC LIMIT :limit")
    fun observeAuditLogs(shopId: String, limit: Int = 250): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE shopId = :shopId ORDER BY timestamp ASC")
    suspend fun getAllAuditLogsChronological(shopId: String): List<AuditLogEntity>

    @Query("SELECT COUNT(*) FROM audit_logs WHERE shopId = :shopId")
    suspend fun getAuditLogCount(shopId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAuditLog(log: AuditLogEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAuditLogsIgnoreDuplicates(logs: List<AuditLogEntity>)

    @Query("SELECT * FROM backup_metadata WHERE shopId = :shopId ORDER BY createdAt DESC")
    fun observeBackups(shopId: String): Flow<List<BackupMetadataEntity>>

    @Query("SELECT * FROM backup_metadata WHERE localId = :backupId LIMIT 1")
    suspend fun getBackupById(backupId: String): BackupMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackupMetadata(metadata: BackupMetadataEntity)
}
