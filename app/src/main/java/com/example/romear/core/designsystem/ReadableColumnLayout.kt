package com.example.romear.core.designsystem

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

/** Keeps forms and account controls comfortably readable in wide windows. */
class ReadableColumnLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maximum = (640 * resources.displayMetrics.density).toInt()
        val mode = View.MeasureSpec.getMode(widthMeasureSpec)
        val available = View.MeasureSpec.getSize(widthMeasureSpec)
        val width = if (mode == View.MeasureSpec.UNSPECIFIED) maximum else minOf(available, maximum)
        super.onMeasure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), heightMeasureSpec)
    }
}
