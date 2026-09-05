package com.snaptube.dl.ui.downloads

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.snaptube.dl.R
import com.snaptube.dl.data.DownloadItem
import com.snaptube.dl.data.DownloadStatus
import com.snaptube.dl.databinding.FragmentDownloadsBinding
import com.snaptube.dl.engine.DownloadManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class DownloadsFragment : Fragment() {

    private var _binding: FragmentDownloadsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: DownloadsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDownloadsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = DownloadsAdapter(
            onPlayClicked = { item -> openMediaFile(item) },
            onShareClicked = { item -> shareMediaFile(item) },
            onDeleteClicked = { item -> confirmDelete(item) }
        )

        binding.rvDownloadedItems.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDownloadedItems.adapter = adapter

        // Clear all downloaded items
        binding.btnClearDownloaded.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear Completed")
                .setMessage("Remove all finished items from the list?")
                .setPositiveButton("Clear") { _, _ ->
                    DownloadManager.clearAllCompleted()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Filter action (Toggle video vs music)
        binding.btnFilterDownloaded.setOnClickListener {
            Toast.makeText(requireContext(), "Showing all media files", Toast.LENGTH_SHORT).show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            DownloadManager.downloads.collectLatest { allDownloads ->
                updateUi(allDownloads)
            }
        }
    }

    private fun updateUi(allDownloads: List<DownloadItem>) {
        val activeOrFailed = allDownloads.filter {
            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.FAILED
        }
        val completed = allDownloads.filter { it.status == DownloadStatus.COMPLETED }

        // Section 1: Downloading (N)
        binding.tvDownloadingHeader.text = "Downloading (${activeOrFailed.size})"
        if (activeOrFailed.isNotEmpty()) {
            binding.layoutDownloadingSection.visibility = View.VISIBLE
            val latest = activeOrFailed.first()

            binding.tvActiveTitle.text = latest.title
            Glide.with(this)
                .load(latest.thumbnailUrl)
                .placeholder(R.drawable.bg_card_dark)
                .into(binding.ivActiveThumb)

            if (latest.status == DownloadStatus.FAILED) {
                binding.tvActiveStatus.text = "Failed"
                binding.tvActiveStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_error))
                binding.tvActivePercent.text = "0.0%"
            } else {
                binding.tvActiveStatus.text = "Downloading... ${latest.speedString}"
                binding.tvActiveStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.snaptube_yellow))
                binding.tvActivePercent.text = "${latest.progress}%"
            }

            binding.cardActiveDownload.setOnClickListener {
                if (latest.status == DownloadStatus.FAILED) {
                    Toast.makeText(requireContext(), "Error: ${latest.errorMessage}", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            binding.layoutDownloadingSection.visibility = View.GONE
        }

        // Section 2: Downloaded
        adapter.submitList(completed)
        binding.layoutEmptyDownloaded.visibility = if (completed.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openMediaFile(item: DownloadItem) {
        val file = File(item.localFilePath)
        if (!file.exists()) {
            Toast.makeText(requireContext(), "File not found on device", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri: Uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )

            val mime = if (item.ext.equals("mp3", ignoreCase = true) || item.ext.equals("m4a", ignoreCase = true)) {
                "audio/*"
            } else {
                "video/*"
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Cannot play file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareMediaFile(item: DownloadItem) {
        val file = File(item.localFilePath)
        if (!file.exists()) {
            Toast.makeText(requireContext(), "File does not exist to share", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (item.ext.equals("mp3", ignoreCase = true)) "audio/*" else "video/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share via"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete(item: DownloadItem) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete")
            .setMessage("Delete \"${item.title}\"?")
            .setPositiveButton("Delete") { _, _ ->
                DownloadManager.deleteItem(item, true)
                Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}