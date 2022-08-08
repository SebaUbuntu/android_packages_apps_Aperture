package org.lineageos.selfie

import android.content.Context
import kotlin.math.roundToInt

internal fun Context.convertDpToPx(dp: Int): Int {
    val density = resources.displayMetrics.density
    return (dp * density).roundToInt()
}
