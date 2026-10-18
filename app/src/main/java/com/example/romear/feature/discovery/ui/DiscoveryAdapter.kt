package com.example.romear.feature.discovery.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.romear.databinding.ItemDiscoveryUserBinding
import com.example.romear.feature.discovery.domain.DiscoveryUser
import com.google.android.material.chip.Chip

class DiscoveryAdapter(
    private val onUserClicked: (DiscoveryUser) -> Unit,
    private val onSayHelloClicked: (DiscoveryUser) -> Unit,
) : ListAdapter<DiscoveryUser, DiscoveryAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDiscoveryUserBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemDiscoveryUserBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(user: DiscoveryUser) {
            val nameText = if (user.age != null) "${user.displayName}, ${user.age}" else user.displayName
            binding.userName.text = nameText
            binding.distanceBucket.text = user.distanceBucket + (user.upFor?.let { " · Up for $it" } ?: "")
            binding.avatar.load(user.photoUrl, user.displayName, user.availability)

            val isOpen = user.availability.equals("OPEN", ignoreCase = true)
            binding.statusIndicatorDot.setBackgroundResource(
                if (isOpen) com.example.romear.R.drawable.bg_status_dot_open else com.example.romear.R.drawable.bg_status_dot_browsing
            )
            binding.statusBadge.text = if (isOpen) "Open" else "Browsing"
            binding.statusBadge.setBackgroundResource(
                if (isOpen) com.example.romear.R.drawable.bg_badge_emerald else com.example.romear.R.drawable.bg_badge_peach
            )
            binding.statusBadge.setTextColor(
                androidx.core.content.ContextCompat.getColor(
                    binding.root.context,
                    if (isOpen) com.example.romear.R.color.hello_emerald else com.example.romear.R.color.hello_primary
                )
            )

            binding.interestsGroup.removeAllViews()
            user.sharedInterests.take(4).forEach { interest ->
                val chip = Chip(binding.root.context).apply {
                    text = interest
                    isClickable = false
                    isCheckable = false
                    textSize = 14f
                    setChipBackgroundColorResource(com.example.romear.R.color.hello_surface_variant)
                    setTextColor(androidx.core.content.ContextCompat.getColor(context, com.example.romear.R.color.hello_text))
                    setEnsureMinTouchTargetSize(false)
                }
                binding.interestsGroup.addView(chip)
            }

            binding.root.setOnClickListener { onUserClicked(user) }
            binding.sayHelloButton.setOnClickListener { onSayHelloClicked(user) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<DiscoveryUser>() {
        override fun areItemsTheSame(oldItem: DiscoveryUser, newItem: DiscoveryUser): Boolean =
            oldItem.userId == newItem.userId

        override fun areContentsTheSame(oldItem: DiscoveryUser, newItem: DiscoveryUser): Boolean =
            oldItem == newItem
    }
}
