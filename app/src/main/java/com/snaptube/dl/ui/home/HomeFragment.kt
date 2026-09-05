package com.snaptube.dl.ui.home

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.snaptube.dl.MainActivity
import com.snaptube.dl.databinding.FragmentHomeBinding
import com.snaptube.dl.engine.DownloadManager
import com.snaptube.dl.ui.dialogs.FormatBottomSheetDialog
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Circular Yellow Search Button
        binding.btnSearchCircle.setOnClickListener {
            handleSearchOrDownload()
        }

        binding.etSearchInput.setOnEditorActionListener { _, _, _ ->
            handleSearchOrDownload()
            true
        }

        // Quick Paste Button
        binding.btnHomePaste.setOnClickListener {
            pasteFromClipboard()
        }

        // Top Category Tabs
        binding.tabYoutube.setOnClickListener {
            (activity as? MainActivity)?.navigateToBrowser("https://m.youtube.com")
        }
        binding.tabMusic.setOnClickListener {
            (activity as? MainActivity)?.navigateToBrowser("https://m.soundcloud.com")
        }
        binding.tabMore.setOnClickListener {
            (activity as? MainActivity)?.navigateToBrowser("https://www.instagram.com")
        }
    }

    fun analyzeDirectUrl(url: String) {
        _binding?.let {
            it.etSearchInput.setText(url)
        }
        analyzeUrl(url)
    }

    private fun handleSearchOrDownload() {
        val input = binding.etSearchInput.text?.toString()?.trim().orEmpty()
        if (input.isEmpty()) {
            Toast.makeText(requireContext(), "Please paste a link or enter a query", Toast.LENGTH_SHORT).show()
            return
        }

        if (input.startsWith("http://") || input.startsWith("https://")) {
            analyzeUrl(input)
        } else {
            // Search on YouTube in browser tab
            val searchUrl = "https://m.youtube.com/results?search_query=${android.net.Uri.encode(input)}"
            (activity as? MainActivity)?.navigateToBrowser(searchUrl)
        }
    }

    private fun pasteFromClipboard() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                binding.etSearchInput.setText(text)
                if (text.startsWith("http://") || text.startsWith("https://")) {
                    analyzeUrl(text)
                }
            } else {
                Toast.makeText(requireContext(), "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "Clipboard is empty", Toast.LENGTH_SHORT).show()
        }
    }

    private fun analyzeUrl(url: String) {
        binding.layoutExtracting.visibility = View.VISIBLE
        binding.btnSearchCircle.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val result = DownloadManager.extractMetadata(url)
            binding.layoutExtracting.visibility = View.GONE
            binding.btnSearchCircle.isEnabled = true

            result.onSuccess { metadata ->
                val dialog = FormatBottomSheetDialog(metadata) {
                    (activity as? MainActivity)?.navigateToDownloads()
                }
                dialog.show(parentFragmentManager, FormatBottomSheetDialog.TAG)
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    "Could not extract video: ${error.localizedMessage ?: "Unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}