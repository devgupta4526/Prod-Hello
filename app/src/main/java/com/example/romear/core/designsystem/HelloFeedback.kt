package com.example.romear.core.designsystem

import android.view.View
import com.example.romear.R
import com.google.android.material.snackbar.Snackbar

object HelloFeedback {
    fun success(view: View, message: CharSequence) {
        HelloHaptics.success(view)
        show(view, message)
    }

    fun error(view: View, message: CharSequence) {
        HelloHaptics.error(view)
        show(view, message)
    }

    private fun show(view: View, message: CharSequence) {
        Snackbar.make(view, message, Snackbar.LENGTH_LONG)
            .setAction(R.string.feedback_dismiss) { }
            .setTextMaxLines(4)
            .show()
    }
}
