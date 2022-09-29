/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture

import androidx.camera.core.CameraProvider
import org.lineageos.aperture.utils.PhysicalCamera

/**
 * Get a list of cameras
 */
@androidx.camera.camera2.interop.ExperimentalCamera2Interop
internal fun CameraProvider.getCameras(): Cameras {
    return availableCameraInfos.associate {
        val physicalCamera = PhysicalCamera(it)
        physicalCamera.cameraId to physicalCamera
    }
}
