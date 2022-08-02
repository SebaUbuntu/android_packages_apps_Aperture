package org.lineageos.selfie.utils

import android.content.SharedPreferences
import androidx.camera.core.ImageCapture
import androidx.camera.video.Quality

class SharedPreferencesUtils {
    companion object {
        // Photos prefs
        private const val PHOTO_CAPTURE_MODE_KEY = "photo_capture_mode"
        private const val PHOTO_CAPTURE_MODE_DEFAULT = "maximize_quality"

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