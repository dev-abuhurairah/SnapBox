package com.snaptube.dl.ui.downloads

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.snaptube.dl.R
import com.snaptube.dl.data.DownloadItem
import com.snaptube.dl.data.DownloadStatus
import com.snaptube.dl.databinding.ItemDownloadBinding
import com.snaptube.dl.engine.DownloadManager
import java.io.File

class DownloadsAdapter(
    private val onPlayClicked: (DownloadItem) -> Unit
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
            binding.tvDownloadTitle.text = item.title
            binding.tvDownloadSubtitle.text = "${item.formatLabel} • ${item.ext.uppercase()}"

            Glide.with(context)
                .load(item.thumbnailUrl)
                .placeholder(R.drawable.bg_card_dark)
                .into(binding.ivDownloadThumb)

            when (item.status) {
                DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                    binding.pbDownload.visibility = View.VISIBLE
                    binding.layoutProgressMeta.visibility = View.VISIBLE
                    binding.pbDownload.progress = item.progress

                    binding.tvStatusText.text = "Downloading... ${item.progress}%"
                    binding.tvStatusText.setTextColor(ContextCompat.getColor(context, R.color.status_downloading))
                    binding.tvSpeedText.text = item.speedString

                    binding.btnAction.setImageResource(R.drawable.ic_close)
                    binding.btnAction.setColorFilter(ContextCompat.getColor(context, R.color.text_secondary))
                    binding.btnAction.setOnClickListener {
                        DownloadManager.cancelDownload(item.id)
                        Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
                    }
                }
                DownloadStatus.COMPLETED -> {
                    binding.pbDownload.visibility = View.GONE
                    binding.layoutProgressMeta.visibility = View.VISIBLE
                    binding.tvStatusText.text = "Finished"
                    binding.tvStatusText.setTextColor(ContextCompat.getColor(context, R.color.status_completed))
                    binding.tvSpeedText.text = ""

                    binding.btnAction.setImageResource(R.drawable.ic_play)
                    binding.btnAction.setColorFilter(ContextCompat.getColor(context, R.color.snaptube_yellow))
                    binding.btnAction.setOnClickListener {
                        onPlayClicked(item)
                    }

                    binding.root.setOnClickListener {
                        onPlayClicked(item)
                    }
                }
                DownloadStatus.FAILED -> {
                    binding.pbDownload.visibility = View.GONE
                    binding.layoutProgressMeta.visibility = View.VISIBLE
                    binding.tvStatusText.text = "Download Failed"
                    binding.tvStatusText.setTextColor(ContextCompat.getColor(context, R.color.status_error))
                    binding.tvSpeedText.text = ""

                    binding.btnAction.setImageResource(R.drawable.ic_close)
                    binding.btnAction.setColorFilter(ContextCompat.getColor(context, R.color.status_error))
                }
                DownloadStatus.CANCELLED -> {
                    binding.pbDownload.visibility = View.GONE
                    binding.layoutProgressMeta.visibility = View.VISIBLE
                    binding.tvStatusText.text = "Cancelled"
                    binding.tvStatusText.setTextColor(ContextCompat.getColor(context, R.color.text_muted))
                    binding.tvSpeedText.text = ""
                }
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
