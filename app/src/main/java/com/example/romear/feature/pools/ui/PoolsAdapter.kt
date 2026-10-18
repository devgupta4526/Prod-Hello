package com.example.romear.feature.pools.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
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
            binding.poolTitle.text = pool.title
            binding.activityBadge.text = pool.activity.uppercase()
            binding.capacityBadge.text = "${pool.memberCount} / ${pool.capacity} joined"
            binding.hostName.text = "Hosted by ${pool.host.displayName}"
            binding.hostAvatar.load(pool.host.photoUrl, pool.host.displayName, sizeDp = 32)
            binding.distanceBucket.text = "📍 ${pool.distanceBucket}"

            binding.root.setOnClickListener { onPoolClicked(pool) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<PoolItem>() {
        override fun areItemsTheSame(oldItem: PoolItem, newItem: PoolItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: PoolItem, newItem: PoolItem): Boolean =
            oldItem == newItem
    }
}
