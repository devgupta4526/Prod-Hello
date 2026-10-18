package com.example.romear.core.designsystem

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.romear.R

/** Static placeholder when system animations are disabled; animation lives only while visible. */
class SkeletonView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var pulse: ValueAnimator? = null
    init {
        setBackgroundColor(ContextCompat.getColor(context, R.color.hello_surface_variant))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); updateAnimation() }
    private fun updateAnimation() {
        pulse?.cancel()
        pulse = null
        alpha = 1f
        if (isAttachedToWindow && windowVisibility == VISIBLE && ValueAnimator.areAnimatorsEnabled()) {
            pulse = ValueAnimator.ofFloat(0.45f, 1f).apply {
                duration = 900
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                addUpdateListener { alpha = it.animatedValue as Float }
                start()
            }
        }
    }
    override fun onDetachedFromWindow() { pulse?.cancel(); pulse = null; super.onDetachedFromWindow() }
}
