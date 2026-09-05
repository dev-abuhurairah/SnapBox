package com.snaptube.dl.ui.downloads

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.snaptube.dl.R
import com.snaptube.dl.data.DownloadItem
import com.snaptube.dl.databinding.ItemDownloadBinding

class DownloadsAdapter(
    private val onPlayClicked: (DownloadItem) -> Unit,
    private val onShareClicked: (DownloadItem) -> Unit,
    private val onDeleteClicked: (DownloadItem) -> Unit
) : ListAdapter<DownloadItem, DownloadsAdapter.DownloadViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val binding = ItemDownloadBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DownloadViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DownloadViewHolder(private val binding: ItemDownloadBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DownloadItem) {
            val context = binding.root.context

            // Title with (EXT) suffix like Screenshot 1
            val extTag = item.ext.uppercase()
            binding.tvItemTitle.text = if (item.title.contains("($extTag)")) item.title else "${item.title}($extTag)"
            binding.tvItemDuration.text = if (item.duration.isNotBlank()) item.duration else "00:00"
            binding.tvItemSize.text = if (item.fileSizeString.isNotBlank()) item.fileSizeString else item.formatLabel

            Glide.with(context)
                .load(item.thumbnailUrl)
                .placeholder(R.drawable.bg_card_dark)
                .into(binding.ivItemThumb)

            binding.root.setOnClickListener {
                onPlayClicked(item)
            }

            // 3-dots popup menu (⋮)
            binding.btnItemMenu.setOnClickListener { view ->
                val popup = PopupMenu(context, view)
                popup.menu.add(0, 1, 0, "Play")
                popup.menu.add(0, 2, 1, "Share")
                popup.menu.add(0, 3, 2, "Delete")

                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        1 -> onPlayClicked(item)
                        2 -> onShareClicked(item)
                        3 -> onDeleteClicked(item)
                    }
                    true
                }
                popup.show()
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<DownloadItem>() {
        override fun areItemsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
            return oldItem == newItem
        }
    }
}