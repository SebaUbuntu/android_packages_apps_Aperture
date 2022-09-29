/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture.utils

import android.content.ContentResolver
import android.content.ContentValues
import android.location.Location
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.OutputOptions
import java.text.SimpleDateFormat
import java.util.Locale

object StorageUtils {
    private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

    private const val INTERNAL_STORAGE_DESTINATION = "single_capture"
    private const val EXTERNAL_STORAGE_DESTINATION = "DCIM/Aperture"

    /**
     * Returns a new ImageCapture.OutputFileOptions to use to store a JPEG photo.
     * If internal is set to true, file will be located inside app data.
     */
    fun getPhotoOutputOptions(
        contentResolver: ContentResolver,
        metadata: ImageCapture.Metadata,
        internal: Boolean = false,
    ): ImageCapture.OutputFileOptions {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, getCurrentTimeString())
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH,
                if (internal) INTERNAL_STORAGE_DESTINATION else EXTERNAL_STORAGE_DESTINATION)
        }

        return ImageCapture.OutputFileOptions
            .Builder(
                contentResolver,
                if (internal) MediaStore.Images.Media.INTERNAL_CONTENT_URI
                    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .setMetadata(metadata)
            .build()
    }

    /**
     * Returns a new OutputOptions to use to store a MP4 video.
     * If internal is set to true, file will be located inside app data.
     */
    @androidx.camera.view.video.ExperimentalVideo
    fun getVideoOutputOptions(
        contentResolver: ContentResolver,
        location: Location?,
        internal: Boolean = false,
    ): OutputOptions {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, getCurrentTimeString())
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH,
                if (internal) INTERNAL_STORAGE_DESTINATION else EXTERNAL_STORAGE_DESTINATION)
        }

        return MediaStoreOutputOptions
            .Builder(
                contentResolver,
                if (internal) MediaStore.Video.Media.INTERNAL_CONTENT_URI
                    else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            )
            .setContentValues(contentValues)
            .setLocation(location)
            .build()
    }

    private fun getCurrentTimeString(): String {
        return SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
    }
}
