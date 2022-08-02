package org.lineageos.aperture.utils

import android.content.SharedPreferences
import androidx.camera.video.Quality

class VideoUtils {
    companion object {
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