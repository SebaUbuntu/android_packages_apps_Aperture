/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.selfie

import org.lineageos.selfie.utils.CameraFacing
import org.lineageos.selfie.utils.PhysicalCamera

typealias Cameras = Map<Int, PhysicalCamera>

/**
 * Filter map based on camera facing
 */
@androidx.camera.camera2.interop.ExperimentalCamera2Interop
internal fun Cameras.filter(facing: CameraFacing): Cameras {
    return filter {
            (_, physicalCamera) -> physicalCamera.getCameraFacing() == facing
    }
}
