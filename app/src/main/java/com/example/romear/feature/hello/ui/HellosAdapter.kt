package com.example.romear.feature.hello.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.romear.databinding.ItemHelloBinding
import com.example.romear.feature.hello.domain.HelloItem

class HellosAdapter(
    private val isReceivedBox: () -> Boolean,
    private val onAcceptClicked: (HelloItem) -> Unit,
    private val onDeclineClicked: (HelloItem) -> Unit,
    private val onWithdrawClicked: (HelloItem) -> Unit,
    private val onItemClicked: ((HelloItem) -> Unit)? = null,
) : ListAdapter<HelloItem, HellosAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHelloBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemHelloBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HelloItem) {
            val isReceived = isReceivedBox()
            val user = if (isReceived) item.sender else item.receiver
            val displayName = user?.displayName ?: "Someone nearby"
            binding.userName.text = displayName
            binding.identityName.text = displayName
            binding.contextLabel.text = item.contextText?.let { "Replied to: $it" } ?: "Sent you a hello"
            binding.avatar.load(user?.photoUrl, displayName)
            
            binding.helloMessage.text = item.message
            binding.root.setOnClickListener { onItemClicked?.invoke(item) }

            val isPending = item.status == "PENDING"
            val isAccepted = item.status == "ACCEPTED"

            binding.statusBadge.text = when {
                isPending && isReceived -> runCatching {
                    val minutes = java.time.Duration.between(java.time.Instant.now(), java.time.Instant.parse(item.expiresAt)).toMinutes().coerceAtLeast(0)
                    if (minutes == 0L) "EXPIRED" else if (minutes < 60) "⏳ EXPIRES IN ${minutes}M" else "⏳ EXPIRES IN ${minutes / 60}H"
                }.getOrDefault("NEW HELLO")
                isPending && !isReceived -> "Waiting for reply"
                isAccepted -> "Connected"
                item.status == "DECLINED" && isReceived -> "Declined"
                item.status == "DECLINED" && !isReceived -> "Not right now"
                else -> item.status
            }

            binding.statusBadge.background = null
            binding.statusBadge.setTextColor(
                androidx.core.content.ContextCompat.getColor(
                    binding.root.context,
                    if (isAccepted) com.example.romear.R.color.hello_emerald else com.example.romear.R.color.hello_primary
                )
            )

            binding.actionsContainer.isVisible = isPending

            if (isPending) {
                if (isReceived) {
                    binding.acceptButton.isVisible = true
                    binding.declineButton.isVisible = true
                    binding.withdrawButton.isVisible = false
                    binding.acceptButton.setOnClickListener { onAcceptClicked(item) }
                    binding.declineButton.setOnClickListener { onDeclineClicked(item) }
                } else {
                    binding.acceptButton.isVisible = false
                    binding.declineButton.isVisible = false
                    binding.withdrawButton.isVisible = true
                    binding.withdrawButton.setOnClickListener { onWithdrawClicked(item) }
                }
            }
        }

    }

    companion object DiffCallback : DiffUtil.ItemCallback<HelloItem>() {
        override fun areItemsTheSame(oldItem: HelloItem, newItem: HelloItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: HelloItem, newItem: HelloItem): Boolean =
            oldItem == newItem
    }
}
