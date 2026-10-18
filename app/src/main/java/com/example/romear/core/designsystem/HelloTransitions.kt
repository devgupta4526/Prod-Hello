package com.example.romear.core.designsystem

import android.animation.ValueAnimator
import com.google.android.material.transition.MaterialContainerTransform
import com.google.android.material.transition.MaterialFadeThrough
import com.google.android.material.transition.MaterialSharedAxis

object HelloTransitions {
    fun step() = MaterialSharedAxis(MaterialSharedAxis.X, true).apply {
        duration = if (ValueAnimator.areAnimatorsEnabled()) 300 else 0
    }
    fun tab() = MaterialFadeThrough().apply {
        duration = if (ValueAnimator.areAnimatorsEnabled()) 200 else 0
    }
    fun card() = MaterialContainerTransform().apply {
        duration = if (ValueAnimator.areAnimatorsEnabled()) 300 else 0
    }
    fun forward() = MaterialSharedAxis(MaterialSharedAxis.Z, true).apply {
        duration = if (ValueAnimator.areAnimatorsEnabled()) 300 else 0
    }
    fun backward() = MaterialSharedAxis(MaterialSharedAxis.Z, false).apply {
        duration = if (ValueAnimator.areAnimatorsEnabled()) 300 else 0
    }
}
