package org.lineageos.selfie.utils

import android.content.SharedPreferences
import androidx.camera.core.ImageCapture
import androidx.camera.video.Quality
import androidx.core.content.edit

class SharedPreferencesUtils {
    companion object {
        // Generic prefs
        private const val LAST_CAMERA_MODE_KEY = "last_camera_mode"
        private const val LAST_CAMERA_MODE_DEFAULT = "photo"

        public fun getLastCameraMode(sharedPreferences: SharedPreferences): CameraMode {
            return when (sharedPreferences.getString(
                LAST_CAMERA_MODE_KEY, LAST_CAMERA_MODE_DEFAULT)) {
                "photo" -> CameraMode.PHOTO
                "video" -> CameraMode.VIDEO
                // Default to photo
                else -> CameraMode.PHOTO
            }
        }

        public fun setLastCameraMode(sharedPreferences: SharedPreferences, value: CameraMode) {
            sharedPreferences.edit {
                putString(LAST_CAMERA_MODE_KEY, when (value) {
                    CameraMode.PHOTO -> "photo"
                    CameraMode.VIDEO -> "video"
                })
            }
        }

        // Photos prefs
        private const val PHOTO_CAPTURE_MODE_KEY = "photo_capture_mode"
        private const val PHOTO_CAPTURE_MODE_DEFAULT = "maximize_quality"

        @androidx.camera.core.ExperimentalZeroShutterLag
        public fun getPhotoCaptureMode(sharedPreferences: SharedPreferences): Int {
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

        public fun getPhotoFlashMode(sharedPreferences: SharedPreferences): Int {
            return when (sharedPreferences.getString(
                PHOTO_FLASH_MODE_KEY, PHOTO_FLASH_MODE_DEFAULT)) {
                "auto" -> ImageCapture.FLASH_MODE_AUTO
                "on" -> ImageCapture.FLASH_MODE_ON
                "off" -> ImageCapture.FLASH_MODE_OFF
                // Default to auto
                else -> ImageCapture.FLASH_MODE_AUTO
            }
        }

        public fun setPhotoFlashMode(sharedPreferences: SharedPreferences, value: Int) {
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

        // Video prefs
        private const val VIDEO_QUALITY_KEY = "video_quality"
        private const val VIDEO_QUALITY_DEFAULT = "highest"

        public fun getVideoQuality(sharedPreferences: SharedPreferences): Quality {
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
}