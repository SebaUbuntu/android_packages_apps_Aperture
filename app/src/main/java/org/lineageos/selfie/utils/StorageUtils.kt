package org.lineageos.selfie.utils

import android.content.ContentResolver
import android.content.ContentValues
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.video.MediaStoreOutputOptions
import java.text.SimpleDateFormat
import java.util.Locale

object StorageUtils {
    private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    private const val STORAGE_DESTINATION = "DCIM/Selfie"

    /**
     * Returns a new ImageCapture.OutputFileOptions to use to store a JPEG photo
     */
    fun getPhotoMediaStoreOutputOptions(
            contentResolver: ContentResolver): ImageCapture.OutputFileOptions {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, getCurrentTimeString())
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, STORAGE_DESTINATION)
        }

        return ImageCapture.OutputFileOptions
            .Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()
    }

    /**
     * Returns a new MediaStoreOutputOptions to use to store a MP4 video
     */
    fun getVideoMediaStoreOutputOptions(contentResolver: ContentResolver): MediaStoreOutputOptions {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, getCurrentTimeString())
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, STORAGE_DESTINATION)
        }

        return MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
    }

    private fun getCurrentTimeString(): String {
        return SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
    }
}
