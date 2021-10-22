package com.example.licenseplaterecognition

import android.content.Context
import android.graphics.*
import android.view.View

class DrawBox(context: Context?) : View(context) {

    lateinit var paint: Paint
    lateinit var textPaint: Paint

    init {
        init()
    }

    private val rects: MutableList<Rect> = mutableListOf()

    private fun init() {
        paint = Paint()
        paint.color = Color.RED
        paint.strokeWidth = 20f
        paint.style = Paint.Style.STROKE

        textPaint = Paint()
        textPaint.color = Color.RED
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 80f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (it in rects) {
            canvas.drawRect(it, paint)
        }
    }

    fun drawRects(rects: Rect){
        this.rects.clear()
        this.rects.add(rects)
        invalidate()
    }
}