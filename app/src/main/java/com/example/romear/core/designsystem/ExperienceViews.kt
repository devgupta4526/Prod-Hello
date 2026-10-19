package com.example.romear.core.designsystem

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.romear.R
import com.google.android.material.button.MaterialButton

/** Native layout primitives from the Romear Figma content columns. */
class ExperienceViews(private val context: Context) {
    fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    fun column(padding: Int = 24) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
    }

    fun text(value: CharSequence, size: Float = 14f, color: Int = R.color.text_secondary, bold: Boolean = false) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(context.getColor(color))
        includeFontPadding = false
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    fun add(parent: LinearLayout, child: View, gap: Int = 18) {
        parent.addView(child, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(gap) })
    }

    fun header(parent: LinearLayout, eyebrow: String, title: String, description: String? = null) {
        add(parent, text(eyebrow, 11f, R.color.primary_coral, true), 12)
        add(parent, text(title, 28f, R.color.text_primary, true).apply {
            androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
        })
        description?.let { add(parent, text(it)) }
    }

    fun card(parent: LinearLayout, eyebrow: String? = null, title: String, body: String, onClick: (() -> Unit)? = null): LinearLayout {
        val content = column(16).apply { setBackgroundResource(R.drawable.bg_design_card) }
        eyebrow?.let { add(content, text(it, 11f, R.color.primary_coral, true), 0) }
        add(content, text(title, 16f, R.color.text_primary, true), if (eyebrow == null) 0 else 8)
        if (body.isNotBlank()) add(content, text(body), 8)
        onClick?.let { action ->
            content.isFocusable = true
            content.setOnClickListener { action() }
        }
        add(parent, content)
        return content
    }

    fun button(parent: LinearLayout, label: String, primary: Boolean = false, onClick: () -> Unit): MaterialButton {
        val button = MaterialButton(context).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            minHeight = dp(56)
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(999)
            setTextColor(context.getColor(R.color.text_primary))
            if (primary) {
                backgroundTintList = null
                setBackgroundResource(R.drawable.bg_gradient_primary)
            } else backgroundTintList = android.content.res.ColorStateList.valueOf(context.getColor(R.color.bg_surface_pill))
            setOnClickListener { onClick() }
        }
        add(parent, button)
        return button
    }
}
