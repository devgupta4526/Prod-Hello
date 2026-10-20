package com.example.romear.core.designsystem

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.example.romear.R

class VoiceWaveformView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.primary_coral) }
    private val bars = mutableListOf<Float>()
    private val reference = floatArrayOf(10f, 54.6f, 21.5f, 51.7f, 32.2f, 45.9f, 41.5f, 37.8f, 48.7f, 27.9f, 54.9f, 14.9f, 53.7f, 26.2f, 49.6f, 36.4f, 42.8f, 44.8f, 33.8f, 50.9f, 23.3f, 54.4f, 11.8f, 54.8f, 19.7f, 52.3f, 30.6f, 47f, 40.2f, 39.2f, 47.7f, 29.5f, 52.7f)

    fun amplitude(value: Int) {
        if (bars.size >= 36) bars.removeAt(0)
        bars.add((value / 32767f).coerceIn(0.08f, 1f))
        invalidate()
    }

    fun reset() { bars.clear(); invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val count = ((width / (8 * density)).toInt()).coerceIn(1, 36)
        val step = width.toFloat() / count
        for (index in 0 until count) {
            val fraction = if (bars.isEmpty()) reference[index % reference.size] / 55f else bars.getOrElse((bars.size - count + index).coerceAtLeast(0)) { 0.08f }
            val barHeight = height * fraction.coerceIn(0.08f, 1f)
            val left = step * index
            canvas.drawRoundRect(left, (height - barHeight) / 2, left + minOf(4 * density, step * 0.6f), (height + barHeight) / 2, 2 * density, 2 * density, paint)
        }
    }
}
