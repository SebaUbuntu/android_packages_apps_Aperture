/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture

import android.content.Context
import kotlin.math.roundToInt

internal fun Context.convertDpToPx(dp: Int): Int {
    val density = resources.displayMetrics.density
    return (dp * density).roundToInt()
}
