package com.snaptube.dl.ui.downloads

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
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
    private var isCompletedTab: Boolean = false

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

        adapter = DownloadsAdapter { item ->
            openMediaFile(item)
        }

        binding.rvDownloads.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDownloads.adapter = adapter

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                isCompletedTab = (tab?.position == 1)
                updateList(DownloadManager.downloads.value)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        viewLifecycleOwner.lifecycleScope.launch {
            DownloadManager.downloads.collectLatest { list ->
                updateList(list)
            }
        }
    }

    private fun updateList(allDownloads: List<DownloadItem>) {
        val filtered = if (isCompletedTab) {
            allDownloads.filter { it.status == DownloadStatus.COMPLETED }
        } else {
            allDownloads.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
        }

        adapter.submitList(filtered)
        if (filtered.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.tvEmptyMessage.text = if (isCompletedTab) {
                getString(R.string.no_finished)
            } else {
                getString(R.string.no_downloads)
            }
        } else {
            binding.layoutEmpty.visibility = View.GONE
        }
    }

    private fun openMediaFile(item: DownloadItem) {
        val file = File(item.localFilePath)
        if (!file.exists()) {
            Toast.makeText(requireContext(), "File not found on storage", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(requireContext(), "Unable to open file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
