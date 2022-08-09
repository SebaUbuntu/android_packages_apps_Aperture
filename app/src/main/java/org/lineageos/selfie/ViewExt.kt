/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.selfie

import android.view.View

internal fun View.setPadding(value: Int) {
    setPadding(value, value, value, value)
}
