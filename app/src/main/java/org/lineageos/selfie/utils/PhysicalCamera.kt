/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.selfie.utils

import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo

/**
 * Class representing a physical device camera
 */
@androidx.camera.camera2.interop.ExperimentalCamera2Interop
class PhysicalCamera(private val cameraInfo: CameraInfo) {
    private val camera2CameraInfo = Camera2CameraInfo.from(cameraInfo)

    /**
     * Return a CameraX's CameraInfo object
     */
    fun getCameraInfo(): CameraInfo {
        return cameraInfo
    }

    /**
     * Return a CameraX's Camera2CameraInfo object
     */
    fun getCamera2CameraInfo(): Camera2CameraInfo {
        return camera2CameraInfo
    }

    /**
     * Returns a Camera2 compatible camera ID
     */
    fun getCameraId(): Int {
        return camera2CameraInfo.cameraId.toInt()
    }

    /**
     * Return the facing of the camera
     */
    fun getCameraFacing(): CameraFacing {
        return when (camera2CameraInfo.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_FRONT -> CameraFacing.FRONT
            CameraCharacteristics.LENS_FACING_BACK -> CameraFacing.BACK
            CameraCharacteristics.LENS_FACING_EXTERNAL -> CameraFacing.EXTERNAL
            else -> CameraFacing.UNKNOWN
        }
    }

    /**
     * Return if flash is available or not
     */
    fun hasFlashUnit(): Boolean {
        return cameraInfo.hasFlashUnit()
    }
}
