package org.bataydar.evidence.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object ImageWatermarkUtils {

    /**
     * Creates a derivative watermarked image file from the original image,
     * retaining the original untouched.
     */
    fun createDerivativeWatermarkedImage(
        originalFile: File,
        destinationFile: File,
        cropType: String,
        fieldName: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        localTimeFormatted: String,
        sha256Prefix: String
    ): Boolean {
        return try {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val originalBitmap = BitmapFactory.decodeFile(originalFile.absolutePath, options)
                ?: return false

            // Work on a mutable copy
            val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
            originalBitmap.recycle()

            val canvas = Canvas(mutableBitmap)
            val width = mutableBitmap.width
            val height = mutableBitmap.height

            // Scale text size proportional to image resolution
            val baseSize = (width.coerceAtMost(height) / 36f).coerceAtLeast(24f)
            val padding = baseSize * 0.8f

            val bannerPaint = Paint().apply {
                color = Color.argb(200, 10, 35, 15) // Deep dark green transparent background
                style = Paint.Style.FILL
            }

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = baseSize
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#81C784") // Fresh light green
                textSize = baseSize * 1.15f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }

            val lines = listOf(
                "BATAYEDAR SABOOT - CROP EVIDENCE",
                "Crop: $cropType | Field: $fieldName",
                String.format(Locale.US, "GPS: %.6f°, %.6f° (±%.1fm)", latitude, longitude, accuracyMeters),
                "Captured: $localTimeFormatted",
                "SHA-256: ${sha256Prefix.take(16)}... (Original Verifiable)"
            )

            val lineHeight = baseSize * 1.4f
            val totalBannerHeight = (lines.size * lineHeight) + (padding * 2)

            // Draw banner at bottom
            val bannerRect = Rect(0, (height - totalBannerHeight).toInt(), width, height)
            canvas.drawRect(bannerRect, bannerPaint)

            var yPos = height - totalBannerHeight + padding + baseSize
            lines.forEachIndexed { index, line ->
                val paintToUse = if (index == 0) titlePaint else textPaint
                canvas.drawText(line, padding, yPos, paintToUse)
                yPos += lineHeight
            }

            // Save to destination derivative file
            FileOutputStream(destinationFile).use { out ->
                mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            mutableBitmap.recycle()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
