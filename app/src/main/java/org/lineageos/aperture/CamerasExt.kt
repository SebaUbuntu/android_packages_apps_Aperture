/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture

import org.lineageos.aperture.utils.CameraFacing
import org.lineageos.aperture.utils.PhysicalCamera

typealias Cameras = Map<Int, PhysicalCamera>

/**
 * Filter map based on camera facing
 */
@androidx.camera.camera2.interop.ExperimentalCamera2Interop
internal fun Cameras.filter(facing: CameraFacing): Cameras {
    return filter {
            (_, physicalCamera) -> physicalCamera.cameraFacing == facing
    }
}
