package com.example.romear.core.designsystem

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.example.romear.R
import com.google.android.material.button.MaterialButton

class QuantityStepper @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    private val decrement = MaterialButton(context).apply { setText(R.string.quantity_less); contentDescription = context.getString(R.string.quantity_less) }
    private val label = TextView(context).apply { gravity = Gravity.CENTER; setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium) }
    private val increment = MaterialButton(context).apply { setText(R.string.quantity_more); contentDescription = context.getString(R.string.quantity_more) }
    private var bounds = 1..10
    var onQuantityChanged: ((Int) -> Unit)? = null
    var quantity: Int = 1
        private set

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val target = (48 * resources.displayMetrics.density).toInt()
        addView(decrement, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(label, LayoutParams(target, LayoutParams.WRAP_CONTENT))
        addView(increment, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        decrement.minimumHeight = target
        increment.minimumHeight = target
        ViewCompat.setAccessibilityLiveRegion(label, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
        decrement.setOnClickListener { HelloHaptics.selection(it); setQuantity(quantity - 1) }
        increment.setOnClickListener { HelloHaptics.selection(it); setQuantity(quantity + 1) }
        render()
    }

    fun configure(minimum: Int, maximum: Int, value: Int) {
        require(minimum >= 0 && maximum >= minimum)
        bounds = minimum..maximum
        setQuantity(value)
    }

    private fun setQuantity(value: Int) {
        val next = value.coerceIn(bounds)
        val changed = next != quantity
        quantity = next
        render()
        if (changed) onQuantityChanged?.invoke(next)
    }

    private fun render() {
        label.text = quantity.toString()
        label.contentDescription = context.getString(R.string.quantity_value, quantity)
        decrement.isEnabled = quantity > bounds.first
        increment.isEnabled = quantity < bounds.last
    }
}
