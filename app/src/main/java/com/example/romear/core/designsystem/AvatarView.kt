package com.example.romear.core.designsystem

import coil3.load as loadImage
import coil3.asImage
import coil3.request.crossfade
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import com.example.romear.R
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.RelativeCornerSize

class AvatarView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ShapeableImageView(context, attrs) {
    init {
        shapeAppearanceModel = shapeAppearanceModel.toBuilder().setAllCornerSizes(RelativeCornerSize(0.5f)).build()
        scaleType = ScaleType.CENTER_CROP
        setBackgroundColor(ContextCompat.getColor(context, R.color.hello_surface_variant))
        setImageResource(R.drawable.ic_profile)
    }

    fun bind(label: String, presence: String?, sizeDp: Int = 72) {
        val validSize = if (sizeDp in setOf(32, 40, 48, 56, 64, 72, 120)) sizeDp else 72
        val size = (validSize * resources.displayMetrics.density).toInt()
        minimumWidth = size
        minimumHeight = size
        layoutParams?.let { it.width = size; it.height = size; layoutParams = it }
        contentDescription = if (presence == null) label else "$label, $presence"
        
        val strokeColorInt = when (presence?.uppercase()) {
            "OPEN" -> ContextCompat.getColor(context, R.color.hello_success)
            "BROWSING" -> ContextCompat.getColor(context, R.color.hello_accent)
            "AT_EVENT" -> ContextCompat.getColor(context, R.color.hello_primary)
            else -> Color.TRANSPARENT
        }
        val strokeWidthDp = if (presence != null && strokeColorInt != Color.TRANSPARENT) 3f else 0f
        strokeWidth = strokeWidthDp * resources.displayMetrics.density
        strokeColor = android.content.res.ColorStateList.valueOf(strokeColorInt)
    }

    fun load(url: String?, label: String, presence: String? = null, sizeDp: Int = 0) {
        val xmlSize = ((layoutParams?.width ?: 0) / resources.displayMetrics.density).toInt()
        bind(label, presence, if (sizeDp > 0) sizeDp else if (xmlSize > 0) xmlSize else 56)
        val fallbackImage = InitialsDrawable(
            label,
            ContextCompat.getColor(context, R.color.hello_primary_container),
            ContextCompat.getColor(context, R.color.hello_on_primary_container),
        ).asImage()
        // The image loader uses its own unauthenticated client, never API bearer tokens.
        loadImage(url?.takeIf { it.startsWith("https://") }) {
            placeholder(fallbackImage)
            error(fallbackImage)
            fallback(fallbackImage)
            crossfade(if (android.animation.ValueAnimator.areAnimatorsEnabled()) 180 else 0)
        }
    }
}
