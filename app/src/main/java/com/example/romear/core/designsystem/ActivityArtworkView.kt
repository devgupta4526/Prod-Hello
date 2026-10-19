package com.example.romear.core.designsystem

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/** Dynamic modern artwork banner with category-tailored gradients and stylized emblems. */
class ActivityArtworkView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var category: String = "social"
        set(value) { field = value; invalidate() }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val clipPath = Path()
    private val iconPath = Path()
    private val boundsRect = RectF()

    private val outdoorKeywords = listOf("outdoor", "cycling", "hiking", "sport", "fitness", "badminton", "run")
    private val musicKeywords = listOf("music", "indie", "concert", "gig", "band", "acoustic")
    private val cafeKeywords = listOf("coffee", "cafe", "food", "breakfast", "dinner", "brunch", "drinks")

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val cat = category.lowercase()
        val isMusic = musicKeywords.any { cat.contains(it) }
        val isOutdoor = outdoorKeywords.any { cat.contains(it) }
        val isCafe = cafeKeywords.any { cat.contains(it) }

        // Color palettes (Start, Mid, End)
        val colors = when {
            isMusic -> intArrayOf(Color.parseColor("#3B0764"), Color.parseColor("#7E22CE"), Color.parseColor("#C026D3"))
            isOutdoor -> intArrayOf(Color.parseColor("#064E3B"), Color.parseColor("#059669"), Color.parseColor("#10B981"))
            isCafe -> intArrayOf(Color.parseColor("#7C2D12"), Color.parseColor("#EA580C"), Color.parseColor("#F59E0B"))
            else -> intArrayOf(Color.parseColor("#1E1B4B"), Color.parseColor("#4338CA"), Color.parseColor("#6366F1"))
        }

        // Clip top corners to match card radius (20dp)
        clipPath.reset()
        boundsRect.set(0f, 0f, w, h)
        val cornerRadius = 20f * resources.displayMetrics.density
        val radii = floatArrayOf(cornerRadius, cornerRadius, cornerRadius, cornerRadius, 0f, 0f, 0f, 0f)
        clipPath.addRoundRect(boundsRect, radii, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        // Background Gradient
        val gradient = LinearGradient(0f, 0f, w, h, colors, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        fillPaint.shader = gradient
        canvas.drawRect(0f, 0f, w, h, fillPaint)
        fillPaint.shader = null

        // Soft ambient background orbs
        fillPaint.color = Color.WHITE
        fillPaint.alpha = 24
        canvas.drawCircle(w * 0.85f, h * 0.25f, h * 0.7f, fillPaint)
        fillPaint.alpha = 18
        canvas.drawCircle(w * 0.15f, h * 0.85f, h * 0.5f, fillPaint)
        fillPaint.alpha = 14
        canvas.drawCircle(w * 0.5f, h * 0.1f, h * 0.35f, fillPaint)

        // Subtle diagonal rhythmic lines
        strokePaint.color = Color.WHITE
        strokePaint.alpha = 30
        strokePaint.strokeWidth = 2f * resources.displayMetrics.density
        canvas.drawLine(w * 0.05f, h * 0.95f, w * 0.35f, h * 0.05f, strokePaint)
        canvas.drawLine(w * 0.70f, h * 0.95f, w * 0.95f, h * 0.25f, strokePaint)

        // Center stylized category badge / icon
        val cx = w * 0.5f
        val cy = h * 0.5f
        val iconSize = (h * 0.45f).coerceAtMost(64f * resources.displayMetrics.density)

        canvas.save()
        canvas.translate(cx, cy)
        val scale = iconSize / 60f
        canvas.scale(scale, scale)

        // Center glow
        fillPaint.color = Color.WHITE
        fillPaint.alpha = 35
        canvas.drawCircle(0f, 0f, 32f, fillPaint)

        strokePaint.color = Color.WHITE
        strokePaint.alpha = 240
        strokePaint.strokeWidth = 3.5f

        when {
            isMusic -> {
                // Musical note & sound bars
                iconPath.reset()
                iconPath.moveTo(-10f, 10f)
                iconPath.lineTo(-10f, -14f)
                iconPath.lineTo(12f, -20f)
                iconPath.lineTo(12f, 4f)
                canvas.drawPath(iconPath, strokePaint)

                fillPaint.color = Color.WHITE
                fillPaint.alpha = 240
                canvas.drawOval(RectF(-18f, 4f, -6f, 14f), fillPaint)
                canvas.drawOval(RectF(4f, -2f, 16f, 8f), fillPaint)

                // Sound waves
                strokePaint.strokeWidth = 2.5f
                strokePaint.alpha = 180
                canvas.drawArc(RectF(18f, -24f, 26f, -10f), -60f, 120f, false, strokePaint)
            }
            isOutdoor -> {
                // Mountain peaks & sun
                iconPath.reset()
                iconPath.moveTo(-24f, 16f)
                iconPath.lineTo(-6f, -14f)
                iconPath.lineTo(8f, 8f)
                iconPath.lineTo(16f, -4f)
                iconPath.lineTo(24f, 16f)
                iconPath.close()
                canvas.drawPath(iconPath, strokePaint)

                fillPaint.color = Color.WHITE
                fillPaint.alpha = 230
                canvas.drawCircle(12f, -16f, 6f, fillPaint)
            }
            isCafe -> {
                // Warm coffee mug & aroma
                iconPath.reset()
                val mugRect = RectF(-14f, -6f, 8f, 16f)
                canvas.drawRoundRect(mugRect, 6f, 6f, strokePaint)
                // Handle
                canvas.drawArc(RectF(2f, -2f, 18f, 12f), -80f, 160f, false, strokePaint)
                // Saucer
                canvas.drawLine(-18f, 18f, 12f, 18f, strokePaint)
                // Steam waves
                strokePaint.strokeWidth = 2f
                strokePaint.alpha = 190
                iconPath.reset()
                iconPath.moveTo(-8f, -10f)
                iconPath.quadTo(-10f, -16f, -7f, -22f)
                iconPath.moveTo(2f, -10f)
                iconPath.quadTo(0f, -16f, 3f, -22f)
                canvas.drawPath(iconPath, strokePaint)
            }
            else -> {
                // Social sparkles & connection stars
                fillPaint.color = Color.WHITE
                fillPaint.alpha = 240
                canvas.drawCircle(-8f, 2f, 10f, strokePaint)
                canvas.drawCircle(10f, -6f, 8f, strokePaint)
                strokePaint.strokeWidth = 2f
                canvas.drawLine(-2f, -1f, 4f, -4f, strokePaint)
            }
        }

        canvas.restore()
        canvas.restore()
    }
}

