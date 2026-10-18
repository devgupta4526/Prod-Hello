package com.example.romear.core.designsystem

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/** Semantic, optional feedback. Android owns intensity and the system opt-out. */
object HelloHaptics {
    private const val PREFERENCES = "hello_experience"
    private const val ENABLED = "haptics_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean(ENABLED, enabled).apply()
    }

    fun selection(view: View) = perform(view, HapticFeedbackConstants.CLOCK_TICK)

    fun success(view: View) = perform(
        view,
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
    )

    fun error(view: View) = perform(
        view,
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS,
    )

    private fun perform(view: View, feedback: Int) {
        if (view.isAttachedToWindow && view.hasWindowFocus() && isEnabled(view.context)) {
            // No ignore-setting flags and no direct Vibrator calls.
            view.performHapticFeedback(feedback)
        }
    }
}
