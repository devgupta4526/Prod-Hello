package com.example.romear.feature.discovery.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.romear.core.designsystem.HelloMotion
import com.example.romear.databinding.ItemLiveStatusNoteBinding
import com.example.romear.feature.discovery.domain.LiveStatusNote

class LiveStatusAdapter(
    private val onNoteClicked: (LiveStatusNote) -> Unit,
    private val onAddNoteClicked: () -> Unit,
) : ListAdapter<LiveStatusNote, LiveStatusAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLiveStatusNoteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemLiveStatusNoteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(note: LiveStatusNote) {
            if (note.isCurrentUser) {
                binding.addNoteBadge.visibility = View.VISIBLE
                binding.liveStatusBubble.text = note.noteText.ifBlank { "+ Note" }
                binding.liveUserName.text = "Your Note"
                binding.liveAvatar.load(note.avatarUrl, note.displayName, sizeDp = 56)
                binding.root.setOnClickListener {
                    HelloMotion.bounce(it) {
                        onAddNoteClicked()
                    }
                }
            } else {
                binding.addNoteBadge.visibility = View.GONE
                binding.liveStatusBubble.text = note.noteText
                binding.liveUserName.text = note.displayName.split(" ").firstOrNull() ?: note.displayName
                binding.liveAvatar.load(note.avatarUrl, note.displayName, sizeDp = 56)
                binding.root.setOnClickListener {
                    HelloMotion.bounce(it) {
                        onNoteClicked(note)
                    }
                }
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<LiveStatusNote>() {
        override fun areItemsTheSame(oldItem: LiveStatusNote, newItem: LiveStatusNote): Boolean =
            oldItem.userId == newItem.userId

        override fun areContentsTheSame(oldItem: LiveStatusNote, newItem: LiveStatusNote): Boolean =
            oldItem == newItem
    }
}
