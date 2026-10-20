package com.example.romear.core.designsystem

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.romear.R

/** Figma's 24dp content column, scrollable body and 56dp bottom action. */
class ExperienceScreen(private val fragment: Fragment) {
    val ui = ExperienceViews(fragment.requireContext())
    val content = ui.column(0)
    val actions = ui.column(0)
    var onDismiss: (() -> Unit)? = null
    private val dialog = AppCompatDialog(fragment.requireContext(), R.style.Theme_Romear)

    fun show() {
        val context = fragment.requireContext()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(context.getColor(R.color.bg_canvas))
        }
        val back = ImageButton(context).apply {
            setImageResource(R.drawable.ic_back)
            setBackgroundResource(R.drawable.bg_circle_icon_button)
            contentDescription = context.getString(R.string.back)
            setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10))
            setOnClickListener { dismiss() }
        }
        root.addView(back, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)).apply {
            topMargin = ui.dp(8)
            bottomMargin = ui.dp(4)
        })
        val scroll = ScrollView(context).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            addView(content)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(16) })
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(ui.dp(24) + bars.left, bars.top, ui.dp(24) + bars.right, bars.bottom)
            insets
        }
        dialog.setContentView(root)
        val lifecycle = fragment.viewLifecycleOwner.lifecycle
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) { dialog.dismiss() }
        }
        lifecycle.addObserver(observer)
        dialog.setOnDismissListener { lifecycle.removeObserver(observer); onDismiss?.invoke() }
        dialog.show()
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawableResource(R.color.bg_canvas)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(this, false)
        }
        ViewCompat.requestApplyInsets(root)
    }

    fun dismiss() = dialog.dismiss()
}
