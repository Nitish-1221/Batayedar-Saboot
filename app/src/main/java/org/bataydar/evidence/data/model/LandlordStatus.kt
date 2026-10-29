package org.bataydar.evidence.data.model

import android.content.Context
import org.bataydar.evidence.R

enum class LandlordStatus {
    NOT_REQUESTED,
    REQUESTED,
    CONFIRMED_VERBAL,
    CONFIRMED_WRITTEN,
    REFUSED,
    UNRESPONSIVE;

    fun getDisplayName(context: Context): String {
        return when (this) {
            NOT_REQUESTED -> context.getString(R.string.status_not_requested)
            REQUESTED -> context.getString(R.string.status_requested)
            CONFIRMED_VERBAL -> context.getString(R.string.status_confirmed_verbal)
            CONFIRMED_WRITTEN -> context.getString(R.string.status_confirmed_written)
            REFUSED -> context.getString(R.string.status_refused)
            UNRESPONSIVE -> context.getString(R.string.status_unresponsive)
        }
    }

    fun isConfirmed(): Boolean {
        return this == CONFIRMED_VERBAL || this == CONFIRMED_WRITTEN
    }
}
