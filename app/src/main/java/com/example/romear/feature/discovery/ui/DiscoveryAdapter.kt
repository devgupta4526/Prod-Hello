package com.example.romear.feature.discovery.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load as loadImage
import coil3.request.crossfade
import com.example.romear.R
import com.example.romear.core.designsystem.HelloMotion
import com.example.romear.databinding.ItemDiscoveryUserBinding
import com.example.romear.feature.discovery.domain.DiscoveryUser
import com.google.android.material.chip.Chip

class DiscoveryAdapter(
    private val onUserClicked: (DiscoveryUser) -> Unit,
    private val onSayHelloClicked: (DiscoveryUser) -> Unit,
    private val onContextualHelloClicked: ((DiscoveryUser, String, String) -> Unit)? = null,
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
            binding.irlVerifiedPill.visibility = if (user.isIrlVerified) android.view.View.VISIBLE else android.view.View.GONE
            val cleanDistance = user.distanceBucket.removePrefix("📍").trim()
            binding.distanceBucket.text = cleanDistance
            binding.userSubhead.text = user.sharedInterests.take(2).joinToString(" · ")
            binding.userSubhead.visibility = if (user.sharedInterests.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE

            // Hero Photo loading via Coil with smooth crossfade
            val photoUrl = user.photoUrl
            if (!photoUrl.isNullOrBlank() && photoUrl.startsWith("https://")) {
                binding.heroPhoto.loadImage(photoUrl) {
                    crossfade(true)
                }
            } else {
                binding.heroPhoto.setImageResource(R.drawable.illustration_people)
            }

            // Circular avatar on card
            binding.avatar.load(user.photoUrl, user.displayName, user.availability, sizeDp = 52)

            val isOpen = user.availability.equals("OPEN", ignoreCase = true)
            binding.statusIndicatorDot.setBackgroundResource(
                if (isOpen) R.drawable.bg_status_dot_open else R.drawable.bg_status_dot_browsing
            )
            binding.statusBadge.text = if (isOpen) "🟢 Open to meet" else "🟡 Browsing"
            binding.statusBadge.setBackgroundResource(
                if (isOpen) R.drawable.bg_badge_emerald else R.drawable.bg_badge_peach
            )
            binding.statusBadge.setTextColor(
                ContextCompat.getColor(
                    binding.root.context,
                    if (isOpen) R.color.hello_emerald else R.color.hello_primary
                )
            )

            // Contextual "Up For" highlight
            if (!user.upFor.isNullOrBlank()) {
                binding.upForContainer.visibility = android.view.View.VISIBLE
                binding.upForText.text = user.upFor
                binding.upForContainer.setOnClickListener {
                    HelloMotion.bounce(it) {
                        onContextualHelloClicked?.invoke(user, "VIBE", user.upFor)
                            ?: onSayHelloClicked(user)
                    }
                }
            } else {
                binding.upForContainer.visibility = android.view.View.GONE
                binding.upForContainer.setOnClickListener(null)
            }

            // Shared interests
            binding.interestsGroup.removeAllViews()
            val interestsToShow = user.sharedInterests.take(4)
            interestsToShow.forEach { interest ->
                val chip = Chip(binding.root.context).apply {
                    text = "#${interest.removePrefix("#")}"
                    isClickable = true
                    isCheckable = false
                    textSize = 12f
                    setChipBackgroundColorResource(R.color.bg_surface_pill)
                    setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    chipCornerRadius = 999f
                    chipStrokeWidth = resources.displayMetrics.density
                    setChipStrokeColorResource(R.color.border_subtle)
                    setEnsureMinTouchTargetSize(false)
                    setOnClickListener {
                        HelloMotion.bounce(this) {
                            onContextualHelloClicked?.invoke(user, "INTEREST", interest)
                                ?: onSayHelloClicked(user)
                        }
                    }
                }
                binding.interestsGroup.addView(chip)
            }
            binding.interestsGroup.visibility =
                if (interestsToShow.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE

            binding.btnLikePhoto.setOnClickListener {
                HelloMotion.bounce(it) {
                    onContextualHelloClicked?.invoke(user, "PHOTO", "Liked primary photo")
                        ?: onSayHelloClicked(user)
                }
            }
            binding.root.setOnClickListener {
                HelloMotion.bounce(it) {
                    onUserClicked(user)
                }
            }
            binding.sayHelloButton.setOnClickListener {
                HelloMotion.bounce(it) {
                    onSayHelloClicked(user)
                }
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<DiscoveryUser>() {
        override fun areItemsTheSame(oldItem: DiscoveryUser, newItem: DiscoveryUser): Boolean =
            oldItem.userId == newItem.userId

        override fun areContentsTheSame(oldItem: DiscoveryUser, newItem: DiscoveryUser): Boolean =
            oldItem == newItem
    }
}
