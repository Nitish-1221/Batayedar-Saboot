package org.bataydar.evidence.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bataydar.evidence.R
import org.bataydar.evidence.data.model.FarmerProfileEntity
import org.bataydar.evidence.data.model.GeotaggedEvidenceEntity
import org.bataydar.evidence.data.model.RecordWithEvidence
import org.bataydar.evidence.util.FileUtils
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfDossierExporter(private val context: Context) {

    companion object {
        const val PAGE_WIDTH = 595 // A4 standard width in points
        const val PAGE_HEIGHT = 842 // A4 standard height in points
        const val MARGIN = 36f // 0.5 inch margins
        const val LEGAL_DISCLAIMER =
            "This bundle contains farmer-provided information and supporting evidence. GPS metadata and photographs do not independently establish land ownership, legal tenancy, or legal entitlement. The legal significance of the evidence depends on applicable state laws and assessment by the relevant authorities."
    }

    suspend fun generateDossierPdf(
        recordWithEvidence: RecordWithEvidence,
        farmerProfile: FarmerProfileEntity?
    ): Result<File> = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        val record = recordWithEvidence.record
        val evidenceList = recordWithEvidence.evidenceList

        val exportDir = FileUtils.getExportDirectory(context)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val pdfFile = File(exportDir, "Evidence_Report_${record.recordId}_$timeStamp.pdf")

        try {
            var currentPageNumber = 1

            // --- PAGE 1: Dossier Summary & Metadata ---
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1B5E20")
                textSize = 16f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#43A047")
                textSize = 10f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#2E7D32")
                textSize = 12f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#212121")
                textSize = 9.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val boldLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1C281F")
                textSize = 9.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val disclaimerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#37474F")
                textSize = 8f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            }
            val borderPaint = Paint().apply {
                color = Color.parseColor("#C8E6C9")
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            val cardBgPaint = Paint().apply {
                color = Color.parseColor("#F9FBF9")
                style = Paint.Style.FILL
            }

            var y = MARGIN + 10f

            // Top Header Banner
            canvas.drawText("BATAYEDAR SABOOT", MARGIN, y, titlePaint)
            y += 14f
            canvas.drawText("Tenant Farmer Crop Cultivation Evidence Dossier", MARGIN, y, subtitlePaint)
            y += 10f

            // Top divider
            val dividerPaint = Paint().apply {
                color = Color.parseColor("#2E7D32")
                strokeWidth = 2f
            }
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, dividerPaint)
            y += 18f

            // Prominent Legal Disclaimer Box
            val disclaimerText = context.getString(R.string.legal_disclaimer_text)
            val boxHeight = 52f
            val rect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + boxHeight)
            val disclaimerBg = Paint().apply {
                color = Color.parseColor("#FFFDE7") // Light yellow / warm alert
                style = Paint.Style.FILL
            }
            val disclaimerBorder = Paint().apply {
                color = Color.parseColor("#FBC02D")
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            canvas.drawRoundRect(rect, 4f, 4f, disclaimerBg)
            canvas.drawRoundRect(rect, 4f, 4f, disclaimerBorder)

            val disclaimerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E65100")
                textSize = 8.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            canvas.drawText("MANDATORY LEGAL NOTICE & DISCLAIMER:", MARGIN + 8f, y + 12f, disclaimerTitlePaint)

            // Multi-line wrap disclaimer
            drawWrappedText(
                canvas = canvas,
                text = disclaimerText,
                x = MARGIN + 8f,
                startY = y + 23f,
                maxWidth = PAGE_WIDTH - (MARGIN * 2) - 16f,
                paint = disclaimerTextPaint,
                lineSpacing = 10f
            )
            y += boxHeight + 16f

            // Section 1: Farmer Profile
            canvas.drawText("1. FARMER (TENANT CULTIVATOR) PROFILE", MARGIN, y, headerPaint)
            y += 14f
            val card1Rect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 60f)
            canvas.drawRoundRect(card1Rect, 4f, 4f, cardBgPaint)
            canvas.drawRoundRect(card1Rect, 4f, 4f, borderPaint)

            var card1Y = y + 14f
            val fName = farmerProfile?.fullName ?: "Not specified"
            val fId = farmerProfile?.farmerId ?: record.farmerId
            val fPhone = farmerProfile?.phoneNumber ?: "Not specified"
            val fVillage = farmerProfile?.village ?: record.village
            val fDist = farmerProfile?.district ?: record.district
            val fState = farmerProfile?.state ?: record.state

            canvas.drawText("Farmer ID:", MARGIN + 10f, card1Y, boldLabelPaint)
            canvas.drawText(fId, MARGIN + 80f, card1Y, textPaint)
            canvas.drawText("Full Name:", MARGIN + 260f, card1Y, boldLabelPaint)
            canvas.drawText(fName, MARGIN + 330f, card1Y, textPaint)

            card1Y += 16f
            canvas.drawText("Mobile:", MARGIN + 10f, card1Y, boldLabelPaint)
            canvas.drawText(fPhone, MARGIN + 80f, card1Y, textPaint)
            canvas.drawText("Village / Dist:", MARGIN + 260f, card1Y, boldLabelPaint)
            canvas.drawText("$fVillage, $fDist", MARGIN + 330f, card1Y, textPaint)

            card1Y += 16f
            canvas.drawText("State:", MARGIN + 10f, card1Y, boldLabelPaint)
            canvas.drawText(fState, MARGIN + 80f, card1Y, textPaint)

            y += 74f

            // Section 2: Cultivation Field Record
            canvas.drawText("2. CROP CULTIVATION DETAILS (FARMER-DECLARED)", MARGIN, y, headerPaint)
            y += 14f
            val card2Rect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 76f)
            canvas.drawRoundRect(card2Rect, 4f, 4f, cardBgPaint)
            canvas.drawRoundRect(card2Rect, 4f, 4f, borderPaint)

            var card2Y = y + 14f
            canvas.drawText("Record ID:", MARGIN + 10f, card2Y, boldLabelPaint)
            canvas.drawText(record.recordId, MARGIN + 80f, card2Y, textPaint)
            canvas.drawText("Field Identifier:", MARGIN + 260f, card2Y, boldLabelPaint)
            canvas.drawText(record.fieldName, MARGIN + 340f, card2Y, textPaint)

            card2Y += 16f
            canvas.drawText("Crop Type:", MARGIN + 10f, card2Y, boldLabelPaint)
            canvas.drawText(record.cropType, MARGIN + 80f, card2Y, textPaint)
            canvas.drawText("Sowing Date:", MARGIN + 260f, card2Y, boldLabelPaint)
            canvas.drawText(record.sowingDate, MARGIN + 340f, card2Y, textPaint)

            card2Y += 16f
            canvas.drawText("Notes:", MARGIN + 10f, card2Y, boldLabelPaint)
            canvas.drawText(record.fieldDescription ?: "None recorded", MARGIN + 80f, card2Y, textPaint)

            card2Y += 16f
            val recordCreatedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(record.createdAt))
            canvas.drawText("Created At:", MARGIN + 10f, card2Y, boldLabelPaint)
            canvas.drawText(recordCreatedDate, MARGIN + 80f, card2Y, textPaint)

            y += 90f

            // Section 3: Optional Landlord Confirmation
            canvas.drawText("3. OPTIONAL LANDLORD CONFIRMATION STATUS", MARGIN, y, headerPaint)
            y += 14f
            val card3Rect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 54f)
            canvas.drawRoundRect(card3Rect, 4f, 4f, cardBgPaint)
            canvas.drawRoundRect(card3Rect, 4f, 4f, borderPaint)

            var card3Y = y + 14f
            canvas.drawText("Status:", MARGIN + 10f, card3Y, boldLabelPaint)
            canvas.drawText(record.landlordConfirmationStatus.getDisplayName(context), MARGIN + 80f, card3Y, textPaint)
            canvas.drawText("Landowner:", MARGIN + 260f, card3Y, boldLabelPaint)
            canvas.drawText(record.landlordName ?: "Not provided", MARGIN + 340f, card3Y, textPaint)

            card3Y += 16f
            canvas.drawText("Notes:", MARGIN + 10f, card3Y, boldLabelPaint)
            canvas.drawText(record.landlordConfirmationNotes ?: "No confirmation notes", MARGIN + 80f, card3Y, textPaint)

            y += 68f

            // Section 4: Geotagged Evidence Photographs Overview
            canvas.drawText("4. MANDATORY GPS-VERIFIED EVIDENCE PHOTOGRAPHS (${evidenceList.size})", MARGIN, y, headerPaint)
            y += 14f

            if (evidenceList.isEmpty()) {
                canvas.drawText("No GPS-verified photographs attached to this record yet.", MARGIN + 10f, y + 10f, textPaint)
                y += 24f
            } else {
                // If there's 1 photo, draw it on page 1 if it fits, else on subsequent pages
                val firstPhoto = evidenceList[0]
                val photoAvailableSpace = PAGE_HEIGHT - MARGIN - y - 30f
                if (photoAvailableSpace >= 220f) {
                    y = drawEvidencePhotoSection(canvas, firstPhoto, y, boldLabelPaint, textPaint, borderPaint)
                }
            }

            // Footer on Page 1
            drawFooter(canvas, currentPageNumber, record.recordId)
            document.finishPage(page)

            // --- PAGE 2+ (Subsequent Evidence Photos) ---
            val photosToDrawOnNextPages = if (evidenceList.isNotEmpty() && (PAGE_HEIGHT - MARGIN - y - 30f >= 0)) {
                evidenceList.drop(1)
            } else {
                evidenceList
            }

            for (evidence in photosToDrawOnNextPages) {
                currentPageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas

                var subY = MARGIN + 10f
                canvas.drawText("GEOTAGGED EVIDENCE PHOTOGRAPH - ${evidence.evidenceId}", MARGIN, subY, headerPaint)
                subY += 16f

                drawEvidencePhotoSection(canvas, evidence, subY, boldLabelPaint, textPaint, borderPaint)
                drawFooter(canvas, currentPageNumber, record.recordId)
                document.finishPage(page)
            }

            FileOutputStream(pdfFile).use { out ->
                document.writeTo(out)
            }
            document.close()

            Result.success(pdfFile)
        } catch (e: Exception) {
            e.printStackTrace()
            document.close()
            Result.failure(e)
        }
    }

    private fun drawEvidencePhotoSection(
        canvas: Canvas,
        evidence: GeotaggedEvidenceEntity,
        startY: Float,
        boldPaint: Paint,
        textPaint: Paint,
        borderPaint: Paint
    ): Float {
        var y = startY
        val photoFile = File(evidence.originalPhotoPath)
        val imageWidth = 240f
        val imageHeight = 180f

        if (photoFile.exists()) {
            val bmp = decodeSampledBitmap(photoFile.absolutePath, 480, 360)
            if (bmp != null) {
                val destRect = RectF(MARGIN, y, MARGIN + imageWidth, y + imageHeight)
                canvas.drawBitmap(bmp, null, destRect, null)
                canvas.drawRect(destRect, borderPaint)
                bmp.recycle()
            }
        }

        // Beside photo: Draw GPS Metadata Table
        val metaX = MARGIN + imageWidth + 14f
        var metaY = y + 14f

        canvas.drawText("GPS Coordinates:", metaX, metaY, boldPaint)
        metaY += 14f
        canvas.drawText(String.format(Locale.US, "%.6f°, %.6f°", evidence.latitude, evidence.longitude), metaX, metaY, textPaint)

        metaY += 16f
        canvas.drawText("GPS Accuracy:", metaX, metaY, boldPaint)
        metaY += 14f
        canvas.drawText(String.format(Locale.US, "±%.1f meters (Verified Satellite)", evidence.accuracyMeters), metaX, metaY, textPaint)

        metaY += 16f
        canvas.drawText("Local Capture Time:", metaX, metaY, boldPaint)
        metaY += 14f
        canvas.drawText(evidence.capturedAtLocalFormatted, metaX, metaY, textPaint)

        metaY += 16f
        canvas.drawText("UTC Timestamp:", metaX, metaY, boldPaint)
        metaY += 14f
        val utcSdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        canvas.drawText(utcSdf.format(Date(evidence.capturedAtUtc)), metaX, metaY, textPaint)

        metaY += 16f
        canvas.drawText("SHA-256 Digital Fingerprint:", metaX, metaY, boldPaint)
        metaY += 14f
        val hashText = evidence.photoSha256
        canvas.drawText(hashText.take(28) + "...", metaX, metaY, textPaint)

        metaY += 16f
        canvas.drawText("Device:", metaX, metaY, boldPaint)
        metaY += 14f
        canvas.drawText(evidence.deviceModel, metaX, metaY, textPaint)

        y += imageHeight + 20f
        return y
    }

    private fun drawFooter(canvas: Canvas, pageNumber: Int, recordId: String) {
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#78909C")
            textSize = 7.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val exportTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val footerText = "Batayedar Saboot Dossier | Record: $recordId | Exported: $exportTime | Page $pageNumber"
        canvas.drawText(footerText, MARGIN, PAGE_HEIGHT - MARGIN + 12f, footerPaint)
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
        lineSpacing: Float
    ) {
        val words = text.split(" ")
        var currentLine = StringBuilder()
        var currentY = startY

        for (word in words) {
            val potentialLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(potentialLine) <= maxWidth) {
                currentLine.append(if (currentLine.isEmpty()) word else " $word")
            } else {
                canvas.drawText(currentLine.toString(), x, currentY, paint)
                currentY += lineSpacing
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            canvas.drawText(currentLine.toString(), x, currentY, paint)
        }
    }

    private fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)

        var inSampleSize = 1
        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        options.inJustDecodeBounds = false
        options.inSampleSize = inSampleSize
        return BitmapFactory.decodeFile(path, options)
    }
}
