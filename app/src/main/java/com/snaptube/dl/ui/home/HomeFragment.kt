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
import com.snaptube.dl.R
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

        // Clipboard Paste Button
        binding.btnPaste.setOnClickListener {
            pasteFromClipboard()
        }

        // Analyze & Download Button
        binding.btnAnalyze.setOnClickListener {
            val url = binding.etUrl.text?.toString()?.trim().orEmpty()
            if (url.isEmpty()) {
                Toast.makeText(requireContext(), "Please paste or enter a media URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            analyzeUrl(url)
        }

        // Platform Shortcuts
        setupPlatformShortcuts()
    }

    fun setUrlAndAnalyze(url: String) {
        _binding?.let {
            it.etUrl.setText(url)
            analyzeUrl(url)
        }
    }

    private fun pasteFromClipboard() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                binding.etUrl.setText(text)
                Toast.makeText(requireContext(), "Pasted from clipboard", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "Clipboard is empty", Toast.LENGTH_SHORT).show()
        }
    }

    private fun analyzeUrl(url: String) {
        binding.layoutLoading.visibility = View.VISIBLE
        binding.tvLoadingStatus.text = getString(R.string.analyzing_url)
        binding.btnAnalyze.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val result = DownloadManager.extractMetadata(url)
            binding.layoutLoading.visibility = View.GONE
            binding.btnAnalyze.isEnabled = true

            result.onSuccess { metadata ->
                val dialog = FormatBottomSheetDialog(metadata) {
                    // Navigate to downloads tab upon start
                    (activity as? MainActivity)?.navigateToDownloads()
                }
                dialog.show(parentFragmentManager, FormatBottomSheetDialog.TAG)
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    "Extraction failed: ${error.localizedMessage ?: "Unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupPlatformShortcuts() {
        val mainActivity = activity as? MainActivity

        binding.btnSiteYoutube.setOnClickListener {
            mainActivity?.navigateToBrowser("https://m.youtube.com")
        }
        binding.btnSiteInstagram.setOnClickListener {
            mainActivity?.navigateToBrowser("https://www.instagram.com")
        }
        binding.btnSiteTiktok.setOnClickListener {
            mainActivity?.navigateToBrowser("https://www.tiktok.com")
        }
        binding.btnSiteFacebook.setOnClickListener {
            mainActivity?.navigateToBrowser("https://m.facebook.com")
        }
        binding.btnSiteTwitter.setOnClickListener {
            mainActivity?.navigateToBrowser("https://x.com")
        }
        binding.btnSiteSoundcloud.setOnClickListener {
            mainActivity?.navigateToBrowser("https://m.soundcloud.com")
        }
        binding.btnSiteVimeo.setOnClickListener {
            mainActivity?.navigateToBrowser("https://vimeo.com")
        }
        binding.btnSiteReddit.setOnClickListener {
            mainActivity?.navigateToBrowser("https://www.reddit.com")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
