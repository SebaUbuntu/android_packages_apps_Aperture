/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.selfie.utils

import java.util.Locale
import java.util.concurrent.TimeUnit

object TimeUtils {
    fun convertSecondsToString(nanos: Long): String {
        return java.lang.String.format(
            Locale.US, "%02d:%02d:%02d",
            TimeUnit.SECONDS.toHours(nanos),
            TimeUnit.SECONDS.toMinutes(nanos) % TimeUnit.HOURS.toMinutes(1),
            TimeUnit.SECONDS.toSeconds(nanos) % TimeUnit.MINUTES.toSeconds(1)
        )
    }
}
