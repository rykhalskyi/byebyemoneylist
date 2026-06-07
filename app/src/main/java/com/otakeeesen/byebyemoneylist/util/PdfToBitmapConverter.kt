package com.otakeeesen.byebyemoneylist.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log

/**
 * Utility for converting PDF documents to Bitmaps.
 */
object PdfToBitmapConverter {
    private const val TAG = "PdfToBitmapConverter"
    private const val DEFAULT_SCALE = 2.0f
    private const val MAX_TOTAL_PIXELS = 20_000_000 // Cap at ~80MB (ARGB_8888)

    /**
     * Converts all pages of a PDF from the given URI into a single vertical Bitmap.
     * 
     * @param context The application context.
     * @param uri The URI of the PDF file.
     * @return A concatenated Bitmap of all pages, or null if conversion failed.
     */
    fun convertPdfToBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount == 0) return null

                    val pageBitmaps = mutableListOf<Bitmap>()
                    var totalHeight = 0
                    var maxWidth = 0

                    try {
                        for (i in 0 until renderer.pageCount) {
                            renderer.openPage(i).use { page ->
                                // Scale up for better OCR quality
                                val width = (page.width * DEFAULT_SCALE).toInt()
                                val height = (page.height * DEFAULT_SCALE).toInt()

                                // Use RGB_565 to save memory (receipts are mostly grayscale/text)
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                
                                pageBitmaps.add(bitmap)
                                totalHeight += height
                                if (width > maxWidth) maxWidth = width
                            }
                        }

                        if (pageBitmaps.isEmpty()) return null
                        if (pageBitmaps.size == 1) return pageBitmaps[0]

                        // Safety check: reduce scale if the resulting bitmap would be too large
                        val totalPixels = maxWidth.toLong() * totalHeight
                        if (totalPixels > MAX_TOTAL_PIXELS) {
                            Log.w(TAG, "Combined bitmap too large ($totalPixels pixels), scaling down.")
                            val reductionScale = Math.sqrt(MAX_TOTAL_PIXELS.toDouble() / totalPixels).toFloat()
                            val newMaxWidth = (maxWidth * reductionScale).toInt()
                            val newTotalHeight = (totalHeight * reductionScale).toInt()
                            
                            val resultBitmap = Bitmap.createBitmap(newMaxWidth, newTotalHeight, Bitmap.Config.RGB_565)
                            val canvas = Canvas(resultBitmap)
                            canvas.drawColor(Color.WHITE)
                            
                            var currentY = 0f
                            for (bitmap in pageBitmaps) {
                                val scaledHeight = (bitmap.height * reductionScale).toInt()
                                val scaledWidth = (bitmap.width * reductionScale).toInt()
                                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                                
                                canvas.drawBitmap(scaledBitmap, 0f, currentY, null)
                                currentY += scaledHeight
                                scaledBitmap.recycle()
                                bitmap.recycle()
                            }
                            return resultBitmap
                        }

                        // Concatenate bitmaps vertically
                        val resultBitmap = Bitmap.createBitmap(maxWidth, totalHeight, Bitmap.Config.RGB_565)
                        val canvas = Canvas(resultBitmap)
                        canvas.drawColor(Color.WHITE)

                        var currentY = 0f
                        for (bitmap in pageBitmaps) {
                            canvas.drawBitmap(bitmap, 0f, currentY, null)
                            currentY += bitmap.height
                            bitmap.recycle()
                        }

                        resultBitmap
                    } catch (e: Exception) {
                        // Ensure cleanup on failure
                        pageBitmaps.forEach { it.recycle() }
                        throw e
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert PDF to Bitmap", e)
            null
        }
    }
}
