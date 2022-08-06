package org.lineageos.selfie.utils

import android.content.SharedPreferences
import androidx.camera.core.ImageCapture
import androidx.camera.extensions.ExtensionMode
import androidx.camera.video.Quality
import androidx.core.content.edit

object SharedPreferencesUtils {
    // Generic prefs
    private const val LAST_CAMERA_FACING_KEY = "last_camera_facing"
    private const val LAST_CAMERA_FACING_DEFAULT = "back"

    fun getLastCameraFacing(sharedPreferences: SharedPreferences): CameraFacing {
        return when (sharedPreferences.getString(
            LAST_CAMERA_FACING_KEY, LAST_CAMERA_FACING_DEFAULT)) {
            "unknown" -> CameraFacing.UNKNOWN
            "front" -> CameraFacing.FRONT
            "back" -> CameraFacing.BACK
            "external" -> CameraFacing.EXTERNAL
            // Default to back
            else -> CameraFacing.BACK
        }
    }

    fun setLastCameraFacing(sharedPreferences: SharedPreferences, value: CameraFacing) {
        sharedPreferences.edit {
            putString(LAST_CAMERA_FACING_KEY, when (value) {
                CameraFacing.UNKNOWN -> "unknown"
                CameraFacing.FRONT -> "front"
                CameraFacing.BACK -> "back"
                CameraFacing.EXTERNAL -> "external"
            })
        }
    }

    private const val LAST_CAMERA_MODE_KEY = "last_camera_mode"
    private const val LAST_CAMERA_MODE_DEFAULT = "photo"

    fun getLastCameraMode(sharedPreferences: SharedPreferences): CameraMode {
        return when (sharedPreferences.getString(
            LAST_CAMERA_MODE_KEY, LAST_CAMERA_MODE_DEFAULT)) {
            "photo" -> CameraMode.PHOTO
            "video" -> CameraMode.VIDEO
            // Default to photo
            else -> CameraMode.PHOTO
        }
    }

    fun setLastCameraMode(sharedPreferences: SharedPreferences, value: CameraMode) {
        sharedPreferences.edit {
            putString(LAST_CAMERA_MODE_KEY, when (value) {
                CameraMode.PHOTO -> "photo"
                CameraMode.VIDEO -> "video"
            })
        }
    }

    private const val LAST_GRID_MODE_KEY = "last_grid_mode"
    private const val LAST_GRID_MODE_DEFAULT = "off"

    fun getLastGridMode(sharedPreferences: SharedPreferences): GridMode {
        return when (sharedPreferences.getString(
            LAST_GRID_MODE_KEY, LAST_GRID_MODE_DEFAULT)) {
            "off" -> GridMode.OFF
            "on_3" -> GridMode.ON_3
            // Default to off
            else -> GridMode.OFF
        }
    }

    fun setLastGridMode(sharedPreferences: SharedPreferences, value: GridMode) {
        sharedPreferences.edit {
            putString(LAST_GRID_MODE_KEY, when (value) {
                GridMode.OFF -> "off"
                GridMode.ON_3 -> "on_3"
            })
        }
    }

    // Photos prefs
    private const val PHOTO_CAPTURE_MODE_KEY = "photo_capture_mode"
    private const val PHOTO_CAPTURE_MODE_DEFAULT = "maximize_quality"

    @androidx.camera.core.ExperimentalZeroShutterLag
    fun getPhotoCaptureMode(sharedPreferences: SharedPreferences): Int {
        return when (sharedPreferences.getString(
            PHOTO_CAPTURE_MODE_KEY, PHOTO_CAPTURE_MODE_DEFAULT)) {
            "maximize_quality" -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            "minimize_latency" -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            "zero_shutter_lag" -> ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG
            // Default to maximize quality
            else -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        }
    }

    private const val PHOTO_FLASH_MODE_KEY = "photo_flash_mode"
    private const val PHOTO_FLASH_MODE_DEFAULT = "auto"

    fun getPhotoFlashMode(sharedPreferences: SharedPreferences): Int {
        return when (sharedPreferences.getString(
            PHOTO_FLASH_MODE_KEY, PHOTO_FLASH_MODE_DEFAULT)) {
            "auto" -> ImageCapture.FLASH_MODE_AUTO
            "on" -> ImageCapture.FLASH_MODE_ON
            "off" -> ImageCapture.FLASH_MODE_OFF
            // Default to auto
            else -> ImageCapture.FLASH_MODE_AUTO
        }
    }

    fun setPhotoFlashMode(sharedPreferences: SharedPreferences, value: Int) {
        sharedPreferences.edit {
            putString(PHOTO_FLASH_MODE_KEY, when (value) {
                ImageCapture.FLASH_MODE_AUTO -> "auto"
                ImageCapture.FLASH_MODE_ON -> "on"
                ImageCapture.FLASH_MODE_OFF -> "off"
                // Default to auto
                else -> PHOTO_FLASH_MODE_DEFAULT
            })
        }
    }

    private const val PHOTO_EFFECT_KEY = "photo_effect"
    private const val PHOTO_EFFECT_DEFAULT = "none"

    fun getPhotoEffect(sharedPreferences: SharedPreferences): Int {
        return when (sharedPreferences.getString(
            PHOTO_EFFECT_KEY, PHOTO_EFFECT_DEFAULT)) {
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

    fun setPhotoEffect(sharedPreferences: SharedPreferences, value: Int) {
        sharedPreferences.edit {
            putString(PHOTO_EFFECT_KEY, when (value) {
                ExtensionMode.NONE -> "none"
                ExtensionMode.BOKEH -> "bokeh"
                ExtensionMode.HDR -> "hdr"
                ExtensionMode.NIGHT -> "night"
                ExtensionMode.FACE_RETOUCH -> "face_retouch"
                ExtensionMode.AUTO -> "auto"
                // Default to none
                else -> PHOTO_EFFECT_DEFAULT
            })
        }
    }

    // Video prefs
    private const val VIDEO_QUALITY_KEY = "video_quality"
    private const val VIDEO_QUALITY_DEFAULT = "highest"

    fun getVideoQuality(sharedPreferences: SharedPreferences): Quality {
        return when (sharedPreferences.getString(VIDEO_QUALITY_KEY, VIDEO_QUALITY_DEFAULT)) {
            "lowest" -> Quality.LOWEST
            "hd" -> Quality.HD
            "fhd" -> Quality.FHD
            "uhd" -> Quality.UHD
            "highest" -> Quality.HIGHEST
            // Default to highest
            else -> Quality.HIGHEST
        }
    }
}