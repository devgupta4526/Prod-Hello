package com.example.romear.core.designsystem

import android.animation.ValueAnimator
import android.view.View

object HelloMotion {
    const val ENTER_DURATION_MS = 180L
    fun reveal(view: View) {
        view.animate().cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) { view.alpha = 1f; return }
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(ENTER_DURATION_MS).start()
    }
}
