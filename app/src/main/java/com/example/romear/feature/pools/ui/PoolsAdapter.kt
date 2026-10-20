package com.example.romear.feature.pools.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.romear.R
import com.example.romear.databinding.ItemPoolBinding
import com.example.romear.feature.pools.domain.PoolItem

class PoolsAdapter(
    private val onPoolClicked: (PoolItem) -> Unit,
) : ListAdapter<PoolItem, PoolsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPoolBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemPoolBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(pool: PoolItem) {
            val context = binding.root.context
            binding.activityArtwork.category = pool.activity
            binding.poolTitle.text = pool.title
            val emoji = when (pool.activity.lowercase()) {
                "coffee", "cafe" -> "☕"
                "badminton", "sports", "tennis", "football", "running" -> "🏃"
                "food", "dinner", "lunch" -> "🍕"
                "tech", "coding", "gaming" -> "🎮"
                "music", "concert" -> "🎵"
                else -> "⚡"
            }
            binding.activityBadge.text = "$emoji ${pool.activity.uppercase()}"
            val spotsLeft = (pool.capacity - pool.memberCount).coerceAtLeast(0)
            binding.capacityBadge.text = if (spotsLeft == 0) "Hangout full" else "$spotsLeft spots left"
            if (spotsLeft == 0) {
                binding.capacityBadge.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_tertiary))
                binding.capacityBadge.background = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.bg_category_pill)
            } else {
                binding.capacityBadge.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.accent_emerald))
                binding.capacityBadge.background = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.bg_badge_emerald_pill)
            }

            binding.hostName.text = "Host: ${pool.host.displayName} + ${pool.memberCount.coerceAtLeast(1) - 1} joined"
            binding.hostAvatar.load(pool.host.photoUrl, pool.host.displayName, sizeDp = 38)

            val time = runCatching {
                val start = java.time.Instant.parse(pool.timeWindowStart).atZone(java.time.ZoneId.systemDefault())
                val now = java.time.ZonedDateTime.now()
                val diffMinutes = java.time.Duration.between(now, start).toMinutes()
                when {
                    diffMinutes in 0..60 -> "Starts in ${diffMinutes}m"
                    start.toLocalDate() == now.toLocalDate() -> "Today, ${start.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))}"
                    else -> start.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a"))
                }
            }.getOrDefault(pool.timeWindowStart)
            binding.poolTime.text = time
            val cleanDistance = pool.distanceBucket.removePrefix("📍").trim()
            binding.distanceBucket.text = cleanDistance

            binding.root.setOnClickListener { onPoolClicked(pool) }
            binding.viewPoolAction.text = if (spotsLeft == 0) "View Hangout →" else "Join Hangout →"
            binding.viewPoolAction.setOnClickListener { onPoolClicked(pool) }
        }

    }

    companion object DiffCallback : DiffUtil.ItemCallback<PoolItem>() {
        override fun areItemsTheSame(oldItem: PoolItem, newItem: PoolItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: PoolItem, newItem: PoolItem): Boolean =
            oldItem == newItem
    }
}
