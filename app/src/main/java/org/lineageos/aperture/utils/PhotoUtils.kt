package org.lineageos.aperture.utils

import android.content.SharedPreferences
import androidx.camera.core.ImageCapture

class PhotoUtils {
    companion object {
        private const val PHOTO_CAPTURE_MODE_KEY = "photo_capture_mode"
        private const val PHOTO_CAPTURE_MODE_DEFAULT = "maximize_quality"

        public fun getCaptureMode(sharedPreferences: SharedPreferences): Int {
            return when (sharedPreferences.getString(
                    PHOTO_CAPTURE_MODE_KEY, PHOTO_CAPTURE_MODE_DEFAULT)) {
                "maximize_quality" -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                "minimize_latency" -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                "zero_shutter_lag" -> ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG
                // Default to maximize quality
                else -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            }
        }
    }
}