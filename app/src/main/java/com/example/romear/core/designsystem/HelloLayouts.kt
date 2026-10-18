package com.example.romear.core.designsystem

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator

object HelloLayouts {
    /** Re-evaluate on window resize; larger text deliberately keeps a single column. */
    fun adaptiveCards(list: RecyclerView) {
        val manager = GridLayoutManager(list.context, 1)
        list.layoutManager = manager
        (list.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        list.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            val metrics = list.resources.displayMetrics
            val widthDp = (right - left) / metrics.density
            val columns = if (list.resources.configuration.fontScale > 1.3f) 1 else (widthDp / 360f).toInt().coerceIn(1, 2)
            if (manager.spanCount != columns) manager.spanCount = columns
        }
    }
}
