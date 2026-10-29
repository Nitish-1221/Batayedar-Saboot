package org.bataydar.evidence.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class RecordWithEvidence(
    @Embedded
    val record: CropRecordEntity,

    @Relation(
        parentColumn = "recordId",
        entityColumn = "recordId"
    )
    val evidenceList: List<GeotaggedEvidenceEntity> = emptyList()
)
