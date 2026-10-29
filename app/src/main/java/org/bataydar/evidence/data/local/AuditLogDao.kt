package org.bataydar.evidence.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.bataydar.evidence.data.model.AuditLogEntity

@Dao
interface AuditLogDao {

    @Insert
    suspend fun insertLog(log: AuditLogEntity): Long

    @Query("SELECT * FROM audit_logs WHERE recordId = :recordId ORDER BY timestamp ASC")
    fun getLogsForRecordFlow(recordId: String): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE recordId = :recordId ORDER BY timestamp ASC")
    suspend fun getLogsForRecord(recordId: String): List<AuditLogEntity>

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogsFlow(limit: Int = 50): Flow<List<AuditLogEntity>>
}
