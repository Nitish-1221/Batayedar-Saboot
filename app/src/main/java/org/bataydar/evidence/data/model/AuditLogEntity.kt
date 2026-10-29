package org.bataydar.evidence.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val auditId: Long = 0,
    val recordId: String,
    val action: String, // CREATED, EVIDENCE_ADDED, LANDLORD_UPDATED, PDF_EXPORTED, ZIP_EXPORTED, SYNC_STATUS_CHANGED
    val timestamp: Long = System.currentTimeMillis(),
    val details: String
)
