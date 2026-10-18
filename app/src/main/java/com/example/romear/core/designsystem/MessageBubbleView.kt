package com.example.romear.core.designsystem

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.romear.R

class MessageBubbleView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    private val message = TextView(context).apply { setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge) }
    private val status = TextView(context).apply { setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium) }
    init {
        orientation = VERTICAL
        val padding = resources.getDimensionPixelSize(R.dimen.space_md)
        setPadding(padding, padding, padding, padding)
        addView(message)
        addView(status)
    }
    fun bind(text: CharSequence, deliveryLabel: CharSequence, outgoing: Boolean) {
        message.text = text
        status.text = deliveryLabel
        gravity = if (outgoing) Gravity.END else Gravity.START
        val radius = 20 * resources.displayMetrics.density
        val tail = 6 * resources.displayMetrics.density
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(ContextCompat.getColor(context, if (outgoing) R.color.hello_primary_container else R.color.hello_surface))
            cornerRadii = if (outgoing) floatArrayOf(radius, radius, radius, radius, tail, tail, radius, radius)
                else floatArrayOf(radius, radius, radius, radius, radius, radius, tail, tail)
        }
        message.setTextColor(ContextCompat.getColor(context, R.color.hello_text))
        status.setTextColor(ContextCompat.getColor(context, R.color.hello_text_secondary))
        status.setPadding(0, (6 * resources.displayMetrics.density).toInt(), 0, 0)
    }
}
