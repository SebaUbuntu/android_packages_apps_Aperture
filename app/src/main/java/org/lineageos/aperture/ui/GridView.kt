/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import org.lineageos.aperture.utils.GridMode

/**
 * A simple view that shows a 3x3 grid
 */
class GridView(context: Context?, attributeSet: AttributeSet?) : View(context, attributeSet) {
    private val paint: Paint = Paint()

    var mode: GridMode = GridMode.OFF
        set(value) {
            field = value
            invalidate()
        }

    init {
        paint.isAntiAlias = true
        paint.strokeWidth = 3F
        paint.style = Paint.Style.STROKE
        paint.color = Color.WHITE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = when (mode) {
            GridMode.OFF -> 0
            GridMode.ON_3 -> 3
            GridMode.ON_4 -> 4
            GridMode.ON_GOLDENRATIO -> 3
        }

        if (size <= 0) {
            return
        }

        val width = width.toFloat()
        val height = height.toFloat()

        val unitDiv = if (mode == GridMode.ON_GOLDENRATIO) GOLDEN_RATIO_UNIT else size.toFloat()

        val widthSection = width / unitDiv
        val heightSection = height / unitDiv

        for (i in size - 1 downTo 1) {
            val position =
                if (mode == GridMode.ON_GOLDENRATIO && i == 2) 1 + GOLDEN_RATIO
                else i.toFloat()

            canvas.drawLine(
                widthSection * position, 0F,
                widthSection * position, height, paint
            )
            canvas.drawLine(
                0F, heightSection * position,
                width, heightSection * position, paint
            )
        }
    }

    companion object {
        private const val GOLDEN_RATIO = 0.618f
        private const val GOLDEN_RATIO_UNIT = 2 + GOLDEN_RATIO
    }
}
