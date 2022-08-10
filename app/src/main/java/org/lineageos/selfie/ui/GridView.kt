/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.selfie.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * A simple view that shows a 3x3 grid
 */
class GridView(context: Context?, attributeSet: AttributeSet?) : View(context, attributeSet) {
    private val paint: Paint = Paint()

    var size = 0
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

        if (size <= 0) {
            return
        }

        val width = width.toFloat()
        val height = height.toFloat()

        val widthSection = (width / size)
        val heightSection = (height / size)

        for (i in size - 1 downTo 1) {
            canvas.drawLine(widthSection * i, 0F, widthSection * i, height, paint)
            canvas.drawLine(0F, heightSection * i, width, heightSection * i, paint)
        }
    }
}
