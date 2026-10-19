package com.example.romear.feature.chat.ui

import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.romear.R
import com.example.romear.core.designsystem.HelloHaptics
import com.example.romear.core.designsystem.HelloMotion
import com.example.romear.databinding.ItemMessageBinding
import com.example.romear.feature.chat.domain.ChatMessage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class MessagesAdapter(
    private val onInviteClicked: ((String) -> Unit)? = null
) : ListAdapter<ChatMessage, MessagesAdapter.ViewHolder>(DiffCallback) {

    private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())

    // In-memory message reactions map (messageId -> emoji)
    private val reactions = mutableMapOf<String, String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val gestureDetector = GestureDetector(
            binding.root.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val message = getItem(bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return false)
                    val currentReaction = reactions[message.id]
                    val newReaction = if (currentReaction == "❤️") "🔥" else "❤️"
                    reactions[message.id] = newReaction
                    binding.bubbleView.setReaction(newReaction)

                    HelloHaptics.selection(binding.bubbleView)
                    HelloMotion.popReaction(
                        container = binding.root as ViewGroup,
                        x = e.x,
                        y = e.y,
                        iconResId = if (newReaction == "❤️") R.drawable.ic_heart else R.drawable.ic_bolt
                    )
                    return true
                }
            }
        )

        fun bind(message: ChatMessage) {
            val formattedTime = try {
                timeFormatter.format(Instant.parse(message.createdAt))
            } catch (_: Exception) {
                ""
            }
            val delivery = if (message.isOutgoing) {
                if (message.readAt != null) "Read · $formattedTime" else "Sent · $formattedTime"
            } else {
                formattedTime
            }

            val savedReaction = reactions[message.id]
            binding.bubbleView.bind(
                text = message.body,
                deliveryLabel = delivery,
                outgoing = message.isOutgoing,
                reaction = savedReaction,
                onInviteClicked = { onInviteClicked?.invoke(message.body) }
            )

            val params = binding.bubbleView.layoutParams as FrameLayout.LayoutParams
            params.gravity = if (message.isOutgoing) Gravity.END else Gravity.START
            binding.bubbleView.layoutParams = params

            binding.bubbleView.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                true
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
            oldItem == newItem
    }
}
