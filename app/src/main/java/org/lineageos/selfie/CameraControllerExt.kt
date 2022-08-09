/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.selfie

import androidx.camera.view.CameraController
import org.lineageos.selfie.utils.PhysicalCamera

internal val CameraController.physicalCamera: PhysicalCamera?
    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    get() = cameraInfo?.let { PhysicalCamera(it) }
