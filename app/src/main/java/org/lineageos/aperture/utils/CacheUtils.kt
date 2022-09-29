/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture.utils

import java.time.format.DateTimeFormatter
import java.io.File
import java.time.LocalDateTime
import java.util.Locale

class CacheUtils(cacheDir: File) {
    private val imagesDir = File(cacheDir, IMAGES_DIR).apply {
        mkdirs()
    }
    private val videosDir = File(cacheDir, VIDEOS_DIR).apply {
        mkdirs()
    }

    fun prepareImageOutput(): File {
        return File(imagesDir, "${getFilename()}.png")
    }

    fun prepareVideoOutput(): File {
        return File(videosDir, "${getFilename()}.mp4")
    }

    private fun getFilename(): String {
        return LocalDateTime.now().format(DATE_FORMATTER)
    }

    companion object {
        private const val IMAGES_DIR = "images"
        private const val VIDEOS_DIR = "videos"

        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.US)
    }
}
