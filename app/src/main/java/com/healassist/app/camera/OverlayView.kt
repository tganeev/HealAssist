package com.healassist.app.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var landmarks: List<NormalizedLandmark>? = null
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    var mirrored: Boolean = false

    private val pointPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        strokeWidth = 5f
    }

    fun setFrame(landmarks: List<NormalizedLandmark>, width: Int, height: Int) {
        this.landmarks = landmarks
        this.imageWidth = width
        this.imageHeight = height
        invalidate()
    }

    fun clearFrame() {
        this.landmarks = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val lm = landmarks ?: return

        val scaleX = width.toFloat() / imageWidth
        val scaleY = height.toFloat() / imageHeight

        for (point in lm) {
            var x = point.x() * imageWidth * scaleX
            val y = point.y() * imageHeight * scaleY
            if (mirrored) {
                x = width - x
            }
            // Рисуем только точки рук (индексы 15-22) и лица (0-10) для простоты
            if (point.visibility().orElse(0f) > 0.5f) {
                canvas.drawCircle(x, y, 8f, pointPaint)
            }
        }
    }
}