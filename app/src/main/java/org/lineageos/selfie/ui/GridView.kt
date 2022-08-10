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

    init {
        paint.isAntiAlias = true
        paint.strokeWidth = 3F
        paint.style = Paint.Style.STROKE
        paint.color = Color.WHITE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()

        val widthSection = (width / 3)
        val heightSection = (height / 3)

        canvas.drawLine(widthSection * 2, 0F, widthSection * 2, height, paint)
        canvas.drawLine(widthSection, 0F, widthSection, height, paint)
        canvas.drawLine(0F, heightSection * 2, width, heightSection * 2, paint)
        canvas.drawLine(0F, heightSection, width, heightSection, paint)
    }
}
