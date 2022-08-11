/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.selfie

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.net.Uri
import androidx.camera.core.AspectRatio
import androidx.camera.core.ImageCapture
import androidx.camera.extensions.ExtensionMode
import androidx.camera.video.Quality
import org.lineageos.selfie.utils.CameraFacing
import org.lineageos.selfie.utils.CameraMode
import org.lineageos.selfie.utils.GridMode

@SuppressLint("ApplySharedPref")
inline fun SharedPreferences.edit(
    commit: Boolean = false,
    action: SharedPreferences.Editor.() -> Unit
) {
    val editor = edit()
    action(editor)
    if (commit) {
        editor.commit()
    } else {
        editor.apply()
    }
}

// Generic prefs
private const val LAST_CAMERA_FACING_KEY = "last_camera_facing"
private const val LAST_CAMERA_FACING_DEFAULT = "back"

internal var SharedPreferences.lastCameraFacing: CameraFacing
    get() {
        return when (getString(LAST_CAMERA_FACING_KEY, LAST_CAMERA_FACING_DEFAULT)) {
            "unknown" -> CameraFacing.UNKNOWN
            "front" -> CameraFacing.FRONT
            "back" -> CameraFacing.BACK
            "external" -> CameraFacing.EXTERNAL
            // Default to back
            else -> CameraFacing.BACK
        }
    }
    set(value) {
        edit {
            putString(
                LAST_CAMERA_FACING_KEY, when (value) {
                    CameraFacing.UNKNOWN -> "unknown"
                    CameraFacing.FRONT -> "front"
                    CameraFacing.BACK -> "back"
                    CameraFacing.EXTERNAL -> "external"
                }
            )
        }
    }

private const val LAST_CAMERA_MODE_KEY = "last_camera_mode"
private const val LAST_CAMERA_MODE_DEFAULT = "photo"

internal var SharedPreferences.lastCameraMode: CameraMode
    get() {
        return when (getString(LAST_CAMERA_MODE_KEY, LAST_CAMERA_MODE_DEFAULT)) {
            "qr" -> CameraMode.QR
            "photo" -> CameraMode.PHOTO
            "video" -> CameraMode.VIDEO
            // Default to photo
            else -> CameraMode.PHOTO
        }
    }
    set(value) {
        edit {
            putString(
                LAST_CAMERA_MODE_KEY, when (value) {
                    CameraMode.QR -> "qr"
                    CameraMode.PHOTO -> "photo"
                    CameraMode.VIDEO -> "video"
                }
            )
        }
    }

private const val LAST_GRID_MODE_KEY = "last_grid_mode"
private const val LAST_GRID_MODE_DEFAULT = "off"

internal var SharedPreferences.lastGridMode: GridMode
    get() {
        return when (getString(LAST_GRID_MODE_KEY, LAST_GRID_MODE_DEFAULT)) {
            "off" -> GridMode.OFF
            "on_3" -> GridMode.ON_3
            "on_4" -> GridMode.ON_4
            "on_goldenratio" -> GridMode.ON_GOLDENRATIO
            // Default to off
            else -> GridMode.OFF
        }
    }
    set(value) {
        edit {
            putString(
                LAST_GRID_MODE_KEY, when (value) {
                    GridMode.OFF -> "off"
                    GridMode.ON_3 -> "on_3"
                    GridMode.ON_4 -> "on_4"
                    GridMode.ON_GOLDENRATIO -> "on_goldenratio"
                }
            )
        }
    }

// Photos prefs
private const val PHOTO_CAPTURE_MODE_KEY = "photo_capture_mode"
private const val PHOTO_CAPTURE_MODE_DEFAULT = "minimize_latency"

internal var SharedPreferences.photoCaptureMode: Int
    @androidx.camera.core.ExperimentalZeroShutterLag
    get() {
        return when (getString(PHOTO_CAPTURE_MODE_KEY, PHOTO_CAPTURE_MODE_DEFAULT)) {
            "maximize_quality" -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            "minimize_latency" -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            "zero_shutter_lag" -> ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG
            // Default to minimize latency
            else -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }
    }
    @androidx.camera.core.ExperimentalZeroShutterLag
    set(value) {
        edit {
            putString(
                PHOTO_CAPTURE_MODE_KEY, when (value) {
                    ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY -> "maximize_quality"
                    ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY -> "minimize_latency"
                    ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG -> "zero_shutter_lag"
                    // Default to maximize quality
                    else -> PHOTO_CAPTURE_MODE_DEFAULT
                }
            )
        }
    }

private const val PHOTO_FLASH_MODE_KEY = "photo_flash_mode"
private const val PHOTO_FLASH_MODE_DEFAULT = "auto"

internal var SharedPreferences.photoFlashMode: Int
    get() {
        return when (getString(PHOTO_FLASH_MODE_KEY, PHOTO_FLASH_MODE_DEFAULT)) {
            "auto" -> ImageCapture.FLASH_MODE_AUTO
            "on" -> ImageCapture.FLASH_MODE_ON
            "off" -> ImageCapture.FLASH_MODE_OFF
            // Default to auto
            else -> ImageCapture.FLASH_MODE_AUTO
        }
    }
    set(value) {
        edit {
            putString(
                PHOTO_FLASH_MODE_KEY, when (value) {
                    ImageCapture.FLASH_MODE_AUTO -> "auto"
                    ImageCapture.FLASH_MODE_ON -> "on"
                    ImageCapture.FLASH_MODE_OFF -> "off"
                    // Default to auto
                    else -> PHOTO_FLASH_MODE_DEFAULT
                }
            )
        }
    }

private const val PHOTO_EFFECT_KEY = "photo_effect"
private const val PHOTO_EFFECT_DEFAULT = "none"

internal var SharedPreferences.photoEffect: Int
    get() {
        return when (getString(PHOTO_EFFECT_KEY, PHOTO_EFFECT_DEFAULT)) {
            "none" -> ExtensionMode.NONE
            "bokeh" -> ExtensionMode.BOKEH
            "hdr" -> ExtensionMode.HDR
            "night" -> ExtensionMode.NIGHT
            "face_retouch" -> ExtensionMode.FACE_RETOUCH
            "auto" -> ExtensionMode.AUTO
            // Default to none
            else -> ExtensionMode.NONE
        }
    }
    set(value) {
        edit {
            putString(
                PHOTO_EFFECT_KEY, when (value) {
                    ExtensionMode.NONE -> "none"
                    ExtensionMode.BOKEH -> "bokeh"
                    ExtensionMode.HDR -> "hdr"
                    ExtensionMode.NIGHT -> "night"
                    ExtensionMode.FACE_RETOUCH -> "face_retouch"
                    ExtensionMode.AUTO -> "auto"
                    // Default to none
                    else -> PHOTO_EFFECT_DEFAULT
                }
            )
        }
    }

// Video prefs
private const val VIDEO_QUALITY_KEY = "video_quality"
private const val VIDEO_QUALITY_DEFAULT = "highest"

internal var SharedPreferences.videoQuality: Quality
    get() {
        return when (getString(VIDEO_QUALITY_KEY, VIDEO_QUALITY_DEFAULT)) {
            "lowest" -> Quality.LOWEST
            "hd" -> Quality.HD
            "fhd" -> Quality.FHD
            "uhd" -> Quality.UHD
            "highest" -> Quality.HIGHEST
            // Default to highest
            else -> Quality.HIGHEST
        }
    }
    set(value) {
        edit {
            putString(
                VIDEO_QUALITY_KEY, when (value) {
                    Quality.LOWEST -> "lowest"
                    Quality.HD -> "hd"
                    Quality.FHD -> "fhd"
                    Quality.UHD -> "uhd"
                    Quality.HIGHEST -> "highest"
                    // Default to highest
                    else -> VIDEO_QUALITY_DEFAULT
                }
            )
        }
    }

// Timer mode
private const val TIMER_MODE_KEY = "timer_mode"
private const val TIMER_MODE_DEFAULT = 0

internal var SharedPreferences.timerMode: Int
    get() {
        return getInt(TIMER_MODE_KEY, TIMER_MODE_DEFAULT)
    }
    set(value) {
        edit {
            putInt(TIMER_MODE_KEY, value)
        }
    }

private const val ASPECT_RATIO_KEY = "aspect_ratio"
private const val ASPECT_RATIO_DEFAULT = "4_3"

internal var SharedPreferences.aspectRatio: Int
    get() {
        return when (getString(ASPECT_RATIO_KEY, ASPECT_RATIO_DEFAULT)) {
            "4_3" -> AspectRatio.RATIO_4_3
            "16_9" -> AspectRatio.RATIO_16_9
            else -> AspectRatio.RATIO_4_3
        }
    }
    set(value) {
        edit {
            putString(
                ASPECT_RATIO_KEY, when (value) {
                    AspectRatio.RATIO_4_3 -> "4_3"
                    AspectRatio.RATIO_16_9 -> "16_9"
                    // Default to 4:3
                    else -> ASPECT_RATIO_DEFAULT
                }
            )
        }
    }

// Last saved URI
private const val LAST_SAVED_URI_KEY = "saved_uri"

internal var SharedPreferences.lastSavedUri: Uri?
    get() {
        val raw = getString(LAST_SAVED_URI_KEY, null) ?: return null
        return Uri.parse(raw)
    }
    set(value) {
        edit {
            putString(LAST_SAVED_URI_KEY, value.toString())
        }
    }
