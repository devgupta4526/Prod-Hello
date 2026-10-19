package com.example.romear.core.designsystem

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import coil3.load as loadImage
import com.example.romear.R

object HelloLightbox {
    fun show(context: Context, imageUrl: String?, caption: String? = null, subtext: String? = null) {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_media_lightbox, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))

        val imageView = view.findViewById<ImageView>(R.id.lightboxImage)
        val captionView = view.findViewById<TextView>(R.id.lightboxCaption)
        val subtextView = view.findViewById<TextView>(R.id.lightboxSubtext)
        val closeBtn = view.findViewById<android.view.View>(R.id.btnCloseLightbox)

        if (!imageUrl.isNullOrBlank()) {
            imageView.loadImage(imageUrl)
        }

        captionView.text = caption ?: "Photo"
        if (subtext != null) {
            subtextView.text = subtext
        }

        closeBtn.setOnClickListener { dialog.dismiss() }
        view.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}
