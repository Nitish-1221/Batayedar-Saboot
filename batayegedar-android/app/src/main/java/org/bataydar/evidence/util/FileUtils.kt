package org.bataydar.evidence.util

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {

    fun getEvidencePhotosDirectory(context: Context): File {
        // App-private external storage or internal fallback
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: File(context.filesDir, "evidence_photos")
        val evidenceFolder = File(dir, "crop_evidence")
        if (!evidenceFolder.exists()) {
            evidenceFolder.mkdirs()
        }
        return evidenceFolder
    }

    fun getExportDirectory(context: Context): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: File(context.filesDir, "exports")
        val exportFolder = File(dir, "dossiers")
        if (!exportFolder.exists()) {
            exportFolder.mkdirs()
        }
        return exportFolder
    }

    fun createPhotoFile(context: Context, recordId: String): File {
        val storageDir = getEvidencePhotosDirectory(context)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val cleanRecordId = recordId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        return File(storageDir, "ORIG_${cleanRecordId}_${timeStamp}.jpg")
    }

    fun createWatermarkedPhotoFile(context: Context, recordId: String): File {
        val storageDir = getEvidencePhotosDirectory(context)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val cleanRecordId = recordId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        return File(storageDir, "WATERMARKED_${cleanRecordId}_${timeStamp}.jpg")
    }
}
