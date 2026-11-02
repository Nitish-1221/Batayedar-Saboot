package org.bataydar.evidence.data.model

enum class SyncStatus {
    LOCAL_ONLY,
    PENDING_SYNC,
    UPLOADING,
    SYNCED,
    DELETION_PENDING,
    DELETED,
    SYNC_FAILED
}
