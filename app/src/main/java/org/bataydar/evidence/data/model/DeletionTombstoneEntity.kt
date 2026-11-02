package org.bataydar.evidence.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Client-Side Deletion Tombstone Entity.
 * Stores records that have been deleted locally using "Delete Everywhere".
 * Prevents resurrected records on this device and persists pending cloud deletion queues
 * until the backend and Cloudinary confirm permanent destruction.
 */
@Entity(
    tableName = "deletion_tombstones",
    indices = [
        Index(value = ["firebaseUid"]),
        Index(value = ["ownerFirebaseUid"])
    ]
)
data class DeletionTombstoneEntity(
    @PrimaryKey
    val recordUuid: String, // evidenceId or recordId
    val recordType: String, // "EVIDENCE" or "CROP_RECORD"
    val recordId: String,   // parent recordId (if evidence) or self
    val firebaseUid: String,
    val ownerFirebaseUid: String = firebaseUid,
    val cloudinaryPublicId: String? = null,
    val deletedAt: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.DELETION_PENDING
)
