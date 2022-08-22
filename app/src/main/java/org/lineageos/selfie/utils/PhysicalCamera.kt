/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.selfie.utils

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector

/**
 * Class representing a physical device camera
 */
@androidx.camera.camera2.interop.ExperimentalCamera2Interop
class PhysicalCamera(val cameraInfo: CameraInfo) {
    /**
     * Camera2 CameraInfo
     */
    val camera2CameraInfo = Camera2CameraInfo.from(cameraInfo)

    /**
     * Camera2 compatible camera ID
     */
    val cameraId = camera2CameraInfo.cameraId.toInt()

    /**
     * Facing of the camera
     */
    val cameraFacing =
        when (camera2CameraInfo.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_FRONT -> CameraFacing.FRONT
            CameraCharacteristics.LENS_FACING_BACK -> CameraFacing.BACK
            CameraCharacteristics.LENS_FACING_EXTERNAL -> CameraFacing.EXTERNAL
            else -> CameraFacing.UNKNOWN
        }

    /**
     * Flash is available or not
     */
    val hasFlashUnit = cameraInfo.hasFlashUnit()

    companion object {
        internal fun supportedVideoQualities(
            context: Context,
            cameraFacing: CameraFacing
        ): List<Quality> {
            val cameraProvider = ProcessCameraProvider.getInstance(context).get()
            val cameraInfo = cameraProvider.availableCameraInfos.first {
                PhysicalCamera(it).cameraFacing == cameraFacing
            }
            return QualitySelector.getSupportedQualities(cameraInfo)
        }
    }
}
