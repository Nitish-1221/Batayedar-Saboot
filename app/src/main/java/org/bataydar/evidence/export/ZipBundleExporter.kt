package org.bataydar.evidence.export

import android.content.Context
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bataydar.evidence.R
import org.bataydar.evidence.data.model.FarmerProfileEntity
import org.bataydar.evidence.data.model.RecordWithEvidence
import org.bataydar.evidence.util.FileUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipBundleExporter(
    private val context: Context,
    private val pdfExporter: PdfDossierExporter
) {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun exportZipBundle(
        recordWithEvidence: RecordWithEvidence,
        farmerProfile: FarmerProfileEntity?
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val record = recordWithEvidence.record
            val exportDir = FileUtils.getExportDirectory(context)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val zipFile = File(exportDir, "Dossier_Bundle_${record.recordId}_$timeStamp.zip")

            // 1. Generate PDF
            val pdfResult = pdfExporter.generateDossierPdf(recordWithEvidence, farmerProfile)
            val pdfFile = pdfResult.getOrThrow()

            // 2. Generate JSON metadata
            val metadataJson = buildMetadataJson(recordWithEvidence, farmerProfile)

            // 3. Write ZIP
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                // Add PDF report
                addFileToZip(zos, pdfFile, "evidence_report_${record.recordId}.pdf")

                // Add metadata.json
                val metadataBytes = metadataJson.toByteArray(Charsets.UTF_8)
                val jsonEntry = ZipEntry("metadata.json")
                zos.putNextEntry(jsonEntry)
                zos.write(metadataBytes)
                zos.closeEntry()

                // Add original and derivative photos
                recordWithEvidence.evidenceList.forEach { evidence ->
                    val origFile = File(evidence.originalPhotoPath)
                    if (origFile.exists()) {
                        addFileToZip(zos, origFile, "photos/original_${evidence.evidenceId}.jpg")
                    }
                    evidence.watermarkedPhotoPath?.let { watermarkedPath ->
                        val wFile = File(watermarkedPath)
                        if (wFile.exists()) {
                            addFileToZip(zos, wFile, "photos/watermarked_${evidence.evidenceId}.jpg")
                        }
                    }
                }
            }

            Result.success(zipFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryPath: String) {
        val entry = ZipEntry(entryPath)
        zos.putNextEntry(entry)
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var count: Int
            while (fis.read(buffer).also { count = it } != -1) {
                zos.write(buffer, 0, count)
            }
        }
        zos.closeEntry()
    }

    private fun buildMetadataJson(
        recordWithEvidence: RecordWithEvidence,
        profile: FarmerProfileEntity?
    ): String {
        val record = recordWithEvidence.record
        val utcSdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val metadataMap = mapOf(
            "dossierFormatVersion" to "1.0.0",
            "generatorApp" to "Batayedar Saboot Android",
            "exportedAtUtc" to utcSdf.format(Date()),
            "legalNoticeAndDisclaimer" to context.getString(R.string.legal_disclaimer_text),
            "farmerProfile" to mapOf(
                "farmerId" to (profile?.farmerId ?: record.farmerId),
                "fullName" to (profile?.fullName ?: "Not specified"),
                "phoneNumber" to (profile?.phoneNumber ?: "Not specified"),
                "village" to (profile?.village ?: record.village),
                "district" to (profile?.district ?: record.district),
                "state" to (profile?.state ?: record.state)
            ),
            "cropRecord" to mapOf(
                "recordId" to record.recordId,
                "fieldName" to record.fieldName,
                "cropType" to record.cropType,
                "sowingDate" to record.sowingDate,
                "fieldDescription" to (record.fieldDescription ?: ""),
                "createdAtUtc" to utcSdf.format(Date(record.createdAt)),
                "syncStatus" to record.syncStatus.name
            ),
            "landlordConfirmation" to mapOf(
                "status" to record.landlordConfirmationStatus.name,
                "landownerName" to (record.landlordName ?: ""),
                "landownerPhone" to (record.landlordPhone ?: ""),
                "notes" to (record.landlordConfirmationNotes ?: ""),
                "confirmationDateUtc" to (record.landlordConfirmationDate?.let { utcSdf.format(Date(it)) } ?: "")
            ),
            "evidencePhotographs" to recordWithEvidence.evidenceList.map { evidence ->
                mapOf(
                    "evidenceId" to evidence.evidenceId,
                    "sha256Checksum" to evidence.photoSha256,
                    "latitude" to evidence.latitude,
                    "longitude" to evidence.longitude,
                    "altitude" to evidence.altitude,
                    "accuracyMeters" to evidence.accuracyMeters,
                    "gpsFixAgeMs" to evidence.gpsFixAgeMs,
                    "capturedAtUtc" to utcSdf.format(Date(evidence.capturedAtUtc)),
                    "capturedAtLocal" to evidence.capturedAtLocalFormatted,
                    "deviceModel" to evidence.deviceModel,
                    "provider" to evidence.provider,
                    "notes" to (evidence.notes ?: "")
                )
            }
        )

        return gson.toJson(metadataMap)
    }
}
