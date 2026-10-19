package com.example.romear.core.designsystem

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.example.romear.R

object HelloMotion {
    const val ENTER_DURATION_MS = 280L
    const val FAST_DURATION_MS = 180L

    fun reveal(view: View) {
        view.animate().cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            view.alpha = 1f
            return
        }
        view.alpha = 0f
        view.animate()
            .alpha(1f)
            .setDuration(ENTER_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /**
     * Spring bounce entrance used for cards, dialogs, and hero elements.
     */
    fun springReveal(view: View, startDelayMs: Long = 0) {
        view.animate().cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            view.alpha = 1f
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        view.alpha = 0f
        view.scaleX = 0.92f
        view.scaleY = 0.92f
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(startDelayMs)
            .setDuration(320L)
            .setInterpolator(OvershootInterpolator(1.25f))
            .start()
    }

    /**
     * Micro-interaction: Playful scale down and bounce back on button/card press.
     */
    fun bounce(view: View, onEnd: (() -> Unit)? = null) {
        view.animate().cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            onEnd?.invoke()
            return
        }
        view.animate()
            .scaleX(0.93f)
            .scaleY(0.93f)
            .setDuration(90L)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(160L)
                        .setInterpolator(OvershootInterpolator(2.0f))
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                onEnd?.invoke()
                            }
                        })
                        .start()
                }
            })
            .start()
    }

    /**
     * Staggered cascade for list items or chips.
     */
    fun staggeredReveal(views: List<View>, baseDelayMs: Long = 45L) {
        views.forEachIndexed { index, view ->
            springReveal(view, startDelayMs = index * baseDelayMs)
        }
    }

    /**
     * Smooth slide-up entrance for sheets and banners.
     */
    fun slideUp(view: View, durationMs: Long = ENTER_DURATION_MS) {
        view.animate().cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            view.translationY = 0f
            view.alpha = 1f
            return
        }
        view.alpha = 0f
        view.translationY = 60f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /**
     * Attention-grabbing pulse for live indicators or primary CTAs.
     */
    fun pulse(view: View) {
        view.animate().cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) return
        view.animate()
            .scaleX(1.08f)
            .scaleY(1.08f)
            .setDuration(500L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(500L)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .setListener(null)
                        .start()
                }
            })
            .start()
    }

    /**
     * Instagram-style floating reaction heart on double tap.
     */
    fun popReaction(container: ViewGroup, x: Float, y: Float, iconResId: Int = R.drawable.ic_heart) {
        val context = container.context
        val heart = ImageView(context).apply {
            setImageResource(iconResId)
            setColorFilter(ContextCompat.getColor(context, R.color.hello_coral))
            layoutParams = ViewGroup.LayoutParams(
                (48 * resources.displayMetrics.density).toInt(),
                (48 * resources.displayMetrics.density).toInt()
            )
            this.x = x - (24 * resources.displayMetrics.density)
            this.y = y - (24 * resources.displayMetrics.density)
            scaleX = 0.2f
            scaleY = 0.2f
            alpha = 0f
        }
        container.addView(heart)

        heart.animate()
            .alpha(1f)
            .scaleX(1.25f)
            .scaleY(1.25f)
            .setDuration(220L)
            .setInterpolator(OvershootInterpolator(2.2f))
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    heart.animate()
                        .translationYBy(-100f)
                        .alpha(0f)
                        .scaleX(0.7f)
                        .scaleY(0.7f)
                        .setDuration(350L)
                        .setInterpolator(DecelerateInterpolator())
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                container.removeView(heart)
                            }
                        })
                        .start()
                }
            })
            .start()
    }
}
