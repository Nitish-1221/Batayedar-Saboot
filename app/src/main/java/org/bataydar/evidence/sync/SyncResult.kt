package org.bataydar.evidence.sync

sealed class SyncResult {
    data class Success(val itemsSyncedCount: Int, val message: String) : SyncResult()
    data class NetworkUnavailable(val message: String) : SyncResult()
    data class NotConfigured(val message: String) : SyncResult()
    data class Failure(val error: Throwable, val message: String) : SyncResult()
}
