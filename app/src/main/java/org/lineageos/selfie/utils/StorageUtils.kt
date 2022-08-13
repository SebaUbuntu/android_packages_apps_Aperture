/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.selfie.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.view.video.Metadata
import androidx.camera.view.video.OutputFileOptions
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import org.lineageos.selfie.lastSavedUri
import java.text.SimpleDateFormat
import java.util.Locale

object StorageUtils {
    private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    private const val STORAGE_DESTINATION = "DCIM/Selfie"

    /**
     * Returns a new ImageCapture.OutputFileOptions to use to store a JPEG photo
     */
    fun getPhotoMediaStoreOutputOptions(
        context: Context,
        customStorageLocation: Uri?,
        metadata: ImageCapture.Metadata
    ): ImageCapture.OutputFileOptions {
        if (customStorageLocation != null) {
            val documentFile = DocumentFile.fromTreeUri(context, customStorageLocation)
            val file = documentFile?.createFile("image/jpeg", getCurrentTimeString())

            // Store URI in shared preferences
            PreferenceManager.getDefaultSharedPreferences(context).lastSavedUri = file!!.uri

            return ImageCapture.OutputFileOptions
                .Builder(context.contentResolver.openOutputStream(file.uri)!!)
                .setMetadata(metadata)
                .build()
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, getCurrentTimeString())
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, STORAGE_DESTINATION)
        }

        return ImageCapture.OutputFileOptions
            .Builder(
                context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .setMetadata(metadata)
            .build()
    }

    /**
     * Returns a new OutputFileOptions to use to store a MP4 video
     */
    @androidx.camera.view.video.ExperimentalVideo
    fun getVideoMediaStoreOutputOptions(
        context: Context,
        customStorageLocation: Uri?,
        metadata: Metadata
    ): OutputFileOptions {
        if (customStorageLocation != null) {
            val documentFile = DocumentFile.fromTreeUri(context, customStorageLocation)
            val file = documentFile?.createFile("video/mp4", getCurrentTimeString())

            // Store URI in shared preferences
            PreferenceManager.getDefaultSharedPreferences(context).lastSavedUri = file!!.uri

            return OutputFileOptions
                .builder(context.contentResolver.openFileDescriptor(file.uri, "w")!!)
                .setMetadata(metadata)
                .build()
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, getCurrentTimeString())
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, STORAGE_DESTINATION)
        }

        return OutputFileOptions
            .builder(
                context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues
            )
            .setMetadata(metadata)
            .build()
    }

    private fun getCurrentTimeString(): String {
        return SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
    }
}
