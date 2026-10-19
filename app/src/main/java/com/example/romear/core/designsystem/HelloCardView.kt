package com.example.romear.core.designsystem

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.romear.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip

/** Shared user, Pool, Event and ticket summary. Callers supply only authorized display data. */
class HelloCardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : MaterialCardView(context, attrs) {
    private var titleView: TextView? = null
    private var detailView: TextView? = null
    private var statusView: Chip? = null

    init {
        radius = resources.getDimension(R.dimen.card_radius)
        val defaultElevation = 2 * resources.displayMetrics.density
        maxCardElevation = 6 * resources.displayMetrics.density
        if (cardElevation <= 0f) {
            cardElevation = defaultElevation
        }
        strokeWidth = (1 * resources.displayMetrics.density).toInt()
        strokeColor = ContextCompat.getColor(context, R.color.hello_outline_subtle)
        setCardBackgroundColor(ContextCompat.getColor(context, R.color.hello_surface))
        isClickable = false
        isFocusable = false
    }

    override fun setOnClickListener(listener: android.view.View.OnClickListener?) {
        super.setOnClickListener(listener)
        isFocusable = listener != null
        isClickable = listener != null
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        if (!isClickable) return
        animate().cancel()
        val scale = if (pressed && android.animation.ValueAnimator.areAnimatorsEnabled()) 0.985f else 1f
        animate().scaleX(scale).scaleY(scale).setDuration(if (android.animation.ValueAnimator.areAnimatorsEnabled()) 120 else 0).start()
    }

    override fun performClick(): Boolean {
        if (hasOnClickListeners()) HelloHaptics.selection(this)
        return super.performClick()
    }

    override fun onDetachedFromWindow() {
        animate().cancel()
        scaleX = 1f
        scaleY = 1f
        super.onDetachedFromWindow()
    }

    fun bind(heading: CharSequence, description: CharSequence, state: CharSequence) {
        if (titleView == null) {
            val title = TextView(context).apply {
                setTextAppearance(R.style.TextAppearance_Hello_Title)
            }
            val detail = TextView(context).apply {
                setTextAppearance(R.style.TextAppearance_Hello_Body)
                setPadding(0, (6 * resources.displayMetrics.density).toInt(), 0, (8 * resources.displayMetrics.density).toInt())
            }
            val status = Chip(context).apply {
                isClickable = false
                isCheckable = false
                setChipBackgroundColorResource(R.color.hello_surface_variant)
                setTextColor(ContextCompat.getColor(context, R.color.hello_text))
            }
            titleView = title
            detailView = detail
            statusView = status
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val padding = (18 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, padding)
                addView(title)
                addView(detail)
                addView(status)
            })
        }
        titleView?.text = heading
        detailView?.text = description
        statusView?.text = state
    }
}

