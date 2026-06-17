package com.otakeeesen.byebyemoneylist.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Utility for managing product and store preview images.
 * Handles cropping, resizing, storage, and cleanup.
 */
object ImageStorageManager {
    private const val PREVIEWS_DIR = "previews"
    
    /**
     * Target size for images in pixels.
     */
    private const val TARGET_SIZE = 300
    
    /**
     * JPEG compression quality (0-100).
     */
    private const val JPEG_QUALITY = 80

    /**
     * Crops the bitmap to a square, resizes it to 300x300, and saves it to internal storage.
     * @param context The application context.
     * @param bitmap The bitmap to save.
     * @return The absolute path to the saved image, or null if saving failed.
     */
    fun saveImage(context: Context, bitmap: Bitmap): String? {
        val previewsDir = File(context.filesDir, PREVIEWS_DIR)
        if (!previewsDir.exists()) {
            if (!previewsDir.mkdirs()) {
                Log.e("ImageStorageManager", "Failed to create directory")
                return null
            }
        }

        val croppedBitmap = cropToSquare(bitmap)
        // Use a filter to ensure better quality resizing
        val resizedBitmap = Bitmap.createScaledBitmap(croppedBitmap, TARGET_SIZE, TARGET_SIZE, true)
        Log.d("ImageStorageManager", "Resized bitmap size: ${resizedBitmap.width}x${resizedBitmap.height}")

        val fileName = "img_${System.currentTimeMillis()}.jpg"
        val file = File(previewsDir, fileName)

        return try {
            FileOutputStream(file).use { out ->
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e("ImageStorageManager", "Failed to save image", e)
            null
        } finally {
            if (croppedBitmap != bitmap) croppedBitmap.recycle()
            if (resizedBitmap != croppedBitmap) resizedBitmap.recycle()
        }
    }

    fun saveImage(context: Context, uri: Uri): String? {
        return try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            saveImage(context, bitmap)
        } catch (e: Exception) {
            Log.e("ImageStorageManager", "Failed to load/save image from URI", e)
            null
        }
    }

    fun saveBitmap(context: Context, bitmap: Bitmap): String? {
        return saveImage(context, bitmap)
    }

    fun getImageFile(context: Context, path: String): File {
        return File(path)
    }

    /**
     * Crops the center 80% of the bitmap to a square to match the camera preview.
     * @param bitmap The bitmap to crop.
     * @return The cropped square bitmap.
     */
    private fun cropToSquare(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        // Match the 80% ratio from SquareFrameOverlay
        val side = (if (width < height) width else height) * 0.8f
        val newSize = side.toInt()
        
        val x = ((width - newSize) / 2)
        val y = ((height - newSize) / 2)
        
        val cropped = Bitmap.createBitmap(bitmap, x, y, newSize, newSize)
        Log.d("ImageStorageManager", "Cropped bitmap size: ${cropped.width}x${cropped.height} (from ${width}x${height})")
        return cropped
    }

    /**
     * Deletes the image at the given path if it's located in the app's internal preview directory.
     * @param path The path of the image to delete.
     */
    fun deleteImage(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            val file = File(path)
            // Safety check: only delete files within our previews directory
            if (file.exists() && file.absolutePath.contains(PREVIEWS_DIR)) {
                if (!file.delete()) {
                    Log.e("ImageStorageManager", "Failed to delete file: $path")
                }
            }
        } catch (e: Exception) {
            Log.e("ImageStorageManager", "Error deleting image", e)
        }
    }
}
