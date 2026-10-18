package com.example.romear.core.designsystem

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.romear.databinding.ActivityDesignGalleryBinding
import com.example.romear.R
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DesignGalleryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val binding = ActivityDesignGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        binding.back.setOnClickListener { finish() }
        val spacing = resources.getDimensionPixelSize(R.dimen.space_md)
        fun add(view: android.view.View) {
            binding.components.addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = spacing })
        }
        add(QuantityStepper(this))
        listOf(32, 48, 72, 120).forEach { size ->
            val avatar = AvatarView(this)
            add(avatar)
            avatar.bind(getString(R.string.gallery_avatar), getString(R.string.gallery_open), size)
        }
        listOf(R.string.gallery_user_card, R.string.gallery_pool_card, R.string.gallery_event_card, R.string.gallery_ticket_card).forEach { title ->
            add(HelloCardView(this).apply { bind(getString(title), getString(R.string.gallery_sample), getString(R.string.gallery_demo)) })
        }
        add(MessageBubbleView(this).apply { bind(getString(R.string.gallery_incoming), getString(R.string.gallery_demo), false) })
        add(MessageBubbleView(this).apply { bind(getString(R.string.gallery_outgoing), getString(R.string.gallery_sent), true) })
        val skeleton = SkeletonView(this)
        add(skeleton)
        skeleton.layoutParams.height = resources.getDimensionPixelSize(R.dimen.button_height)
        add(MaterialButton(this).apply {
            setText(R.string.gallery_snackbar)
            setOnClickListener { Snackbar.make(binding.root, R.string.gallery_sample, Snackbar.LENGTH_LONG).show() }
        })
        add(MaterialButton(this).apply {
            setText(R.string.gallery_dialog)
            setOnClickListener { MaterialAlertDialogBuilder(this@DesignGalleryActivity).setTitle(R.string.gallery_dialog).setMessage(R.string.gallery_sample).setPositiveButton(R.string.back, null).show() }
        })
        add(MaterialButton(this).apply {
            setText(R.string.gallery_sheet)
            setOnClickListener {
                val sheet = BottomSheetDialog(this@DesignGalleryActivity)
                sheet.setContentView(MaterialButton(this@DesignGalleryActivity).apply { setText(R.string.back); setOnClickListener { sheet.dismiss() } })
                sheet.show()
            }
        })
    }
}
