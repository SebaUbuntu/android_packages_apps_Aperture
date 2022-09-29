/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture.utils

/**
 * Zoom value used to indicate an ignored camera
 */
private const val IGNORE = -1.0

/**
 * Class representing a camera module type.
 * If you can't find a type that represent one of your device's camera module
 * please report it to the app maintainers.
 */
enum class CameraType(
    val overlayName: String,
    val description: String,
    val zoom: Double,
) {
    // Common types

    /**
     * Main camera module.
     * Zoom indicator will be 1.0x.
     */
    MAIN("main", "Main", 1.0),

    /**
     * Bokeh camera module.
     * This camera will be ignored.
     */
    BOKEH("bokeh", "Bokeh", IGNORE),

    /**
     * Macro camera module.
     * Zoom indicator will be 1.5x.
     */
    MACRO("macro", "Macro", 1.5),

    /**
     * Telephoto camera module.
     * Zoom indicator will be 10.0x.
     */
    TELEPHOTO("telephoto", "Telephoto", 10.0),

    /**
     * Time of Flight camera module, usually used for face recognition and depth measuring.
     * This camera will be ignored.
     */
    TOF("tof", "ToF", IGNORE),

    /**
     * Ultra wide angle camera module.
     * Zoom indicator will be 0.7x.
     */
    ULTRA_WIDE_ANGLE("ultra_wide_angle", "Ultra wide angle", 0.7),

    /**
     * Unknown camera module.
     * This camera will be ignored.
     */
    UNKNOWN("unknown", "Unknown", IGNORE);

    // End common types

    val ignore = zoom == IGNORE

    override fun toString(): String {
        return "$description (${zoom}x)"
    }

    companion object {
        /**
         * Get a CameraType from overlay name. Returns CameraType.UNKNOWN if not found.
         */
        fun fromOverlayName(overlayName: String): CameraType {
            return values().firstOrNull {
                it.overlayName == overlayName
            } ?: UNKNOWN
        }
    }
}
