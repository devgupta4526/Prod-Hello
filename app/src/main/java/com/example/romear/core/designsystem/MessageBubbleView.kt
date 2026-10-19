package com.example.romear.core.designsystem

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.romear.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class MessageBubbleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val messageText = TextView(context).apply {
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
    }

    private val statusText = TextView(context).apply {
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall)
    }

    // Reaction badge floating at bubble corner (Instagram style)
    private val reactionBadge = TextView(context).apply {
        textSize = 12f
        setPadding(
            (6 * resources.displayMetrics.density).toInt(),
            (2 * resources.displayMetrics.density).toInt(),
            (6 * resources.displayMetrics.density).toInt(),
            (2 * resources.displayMetrics.density).toInt()
        )
        background = ContextCompat.getDrawable(context, R.drawable.bg_badge_pill)
        visibility = View.GONE
    }

    // Interactive in-chat activity invite card
    private val inviteCard = MaterialCardView(context).apply {
        radius = 16 * resources.displayMetrics.density
        cardElevation = 0f
        strokeWidth = (1 * resources.displayMetrics.density).toInt()
        strokeColor = ContextCompat.getColor(context, R.color.hello_outline_subtle)
        setCardBackgroundColor(ContextCompat.getColor(context, R.color.hello_surface))
        visibility = View.GONE
    }

    private val inviteTitle = TextView(context).apply {
        textSize = 13f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setTextColor(ContextCompat.getColor(context, R.color.hello_text))
    }

    private val inviteButton = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonTonalStyle).apply {
        text = "View Activity ⚡"
        cornerRadius = (16 * resources.displayMetrics.density).toInt()
        minHeight = (36 * resources.displayMetrics.density).toInt()
        textSize = 12f
    }

    // Voice note preview
    private val voiceContainer = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
        setPadding(0, 0, 0, (4 * resources.displayMetrics.density).toInt())
    }

    private val voicePlayIcon = ImageView(context).apply {
        setImageResource(R.drawable.ic_mic)
        setColorFilter(ContextCompat.getColor(context, R.color.hello_primary))
        layoutParams = LayoutParams(
            (24 * resources.displayMetrics.density).toInt(),
            (24 * resources.displayMetrics.density).toInt()
        )
    }

    private val voiceWaveform = TextView(context).apply {
        text = " ▂▃▅▆▇▆▅▃▂ ▂▃"
        textSize = 13f
        letterSpacing = 0.08f
        setTextColor(ContextCompat.getColor(context, R.color.hello_primary))
        setPadding((8 * resources.displayMetrics.density).toInt(), 0, (8 * resources.displayMetrics.density).toInt(), 0)
    }

    private val voiceDuration = TextView(context).apply {
        text = "0:12"
        textSize = 11f
        setTextColor(ContextCompat.getColor(context, R.color.hello_text_secondary))
    }

    init {
        orientation = VERTICAL
        val padding = resources.getDimensionPixelSize(R.dimen.space_md)
        setPadding(padding, padding, padding, padding)

        // Setup Invite card content
        val cardLayout = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(
                (12 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt()
            )
            addView(inviteTitle)
            addView(inviteButton)
        }
        inviteCard.addView(cardLayout)

        // Setup Voice container
        voiceContainer.addView(voicePlayIcon)
        voiceContainer.addView(voiceWaveform)
        voiceContainer.addView(voiceDuration)

        addView(voiceContainer)
        addView(inviteCard)
        addView(messageText)

        val bottomRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(statusText)
            addView(reactionBadge)
        }
        addView(bottomRow)
    }

    fun bind(
        text: CharSequence,
        deliveryLabel: CharSequence,
        outgoing: Boolean,
        reaction: String? = null,
        onInviteClicked: (() -> Unit)? = null
    ) {
        val rawText = text.toString()

        // Handle in-chat Activity / Pool invite
        if (rawText.startsWith("[POOL:") || rawText.startsWith("[EVENT:") || rawText.startsWith("[ACTIVITY:")) {
            val parts = rawText.removePrefix("[").removeSuffix("]").split(":")
            val title = parts.getOrNull(2) ?: "Activity Plan"
            inviteCard.visibility = View.VISIBLE
            inviteTitle.text = "⚡ Plan Invite: $title"
            inviteButton.setOnClickListener { onInviteClicked?.invoke() }
            messageText.visibility = View.GONE
            voiceContainer.visibility = View.GONE
        } else if (rawText.startsWith("[VOICE]")) {
            voiceContainer.visibility = View.VISIBLE
            inviteCard.visibility = View.GONE
            messageText.visibility = View.GONE
        } else {
            inviteCard.visibility = View.GONE
            voiceContainer.visibility = View.GONE
            messageText.visibility = View.VISIBLE
            messageText.text = text
        }

        statusText.text = deliveryLabel
        gravity = if (outgoing) Gravity.END else Gravity.START

        if (!reaction.isNullOrBlank()) {
            reactionBadge.visibility = View.VISIBLE
            reactionBadge.text = reaction
        } else {
            reactionBadge.visibility = View.GONE
        }

        val radius = 18 * resources.displayMetrics.density
        val tail = 4 * resources.displayMetrics.density
        background = GradientDrawable().apply {
            setColor(
                ContextCompat.getColor(
                    context,
                    if (outgoing) R.color.hello_primary_container else R.color.hello_surface
                )
            )
            setStroke(
                (1 * resources.displayMetrics.density).toInt(),
                ContextCompat.getColor(
                    context,
                    if (outgoing) R.color.hello_primary else R.color.hello_outline_subtle
                )
            )
            cornerRadii = if (outgoing) {
                floatArrayOf(radius, radius, radius, radius, tail, tail, radius, radius)
            } else {
                floatArrayOf(radius, radius, radius, radius, radius, radius, tail, tail)
            }
        }

        messageText.setTextColor(ContextCompat.getColor(context, R.color.hello_text))
        messageText.setLineSpacing(3 * resources.displayMetrics.density, 1f)
        statusText.setTextColor(ContextCompat.getColor(context, R.color.hello_text_secondary))
        statusText.setPadding(0, (4 * resources.displayMetrics.density).toInt(), 0, 0)
    }

    fun setReaction(reactionEmoji: String) {
        reactionBadge.visibility = View.VISIBLE
        reactionBadge.text = reactionEmoji
        HelloMotion.bounce(reactionBadge)
    }
}
