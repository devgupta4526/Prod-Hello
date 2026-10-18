package com.example.romear.core.designsystem

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable

/** A stable, personal fallback for missing or unavailable profile images. */
class InitialsDrawable(label: String, private val surface: Int, private val foreground: Int) : Drawable() {
    private val initials = label.trim().split(Regex("\\s+"))
        .filter { it.isNotEmpty() }.take(2)
        .joinToString("") { String(Character.toChars(it.codePointAt(0))) }.uppercase()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    override fun draw(canvas: Canvas) {
        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()
        paint.color = surface
        canvas.drawCircle(centerX, centerY, minOf(bounds.width(), bounds.height()) / 2f, paint)
        paint.color = foreground
        paint.textSize = minOf(bounds.width(), bounds.height()) * 0.32f
        canvas.drawText(initials, centerX, centerY - (paint.ascent() + paint.descent()) / 2, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = 160
    override fun getIntrinsicHeight(): Int = 160
}
