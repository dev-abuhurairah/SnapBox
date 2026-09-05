package com.snaptube.dl.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.snaptube.dl.MainActivity
import com.snaptube.dl.databinding.FragmentBrowserBinding
import com.snaptube.dl.engine.DownloadManager
import com.snaptube.dl.ui.dialogs.FormatBottomSheetDialog
import kotlinx.coroutines.launch

class BrowserFragment : Fragment() {

    private var _binding: FragmentBrowserBinding? = null
    private val binding get() = _binding!!

    private var initialUrl: String = "https://m.youtube.com"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupWebView()

        arguments?.getString(ARG_URL)?.let {
            if (it.isNotBlank()) initialUrl = it
        }

        binding.webView.loadUrl(initialUrl)

        // Browser navigation controls
        binding.btnBack.setOnClickListener {
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            } else {
                (activity as? MainActivity)?.navigateToHome()
            }
        }

        binding.btnRefresh.setOnClickListener {
            binding.webView.reload()
        }

        binding.etBrowserUrl.setOnEditorActionListener { _, _, _ ->
            val input = binding.etBrowserUrl.text?.toString()?.trim().orEmpty()
            if (input.isNotEmpty()) {
                val targetUrl = if (input.startsWith("http://") || input.startsWith("https://")) {
                    input
                } else {
                    "https://www.google.com/search?q=${input}"
                }
                binding.webView.loadUrl(targetUrl)
            }
            true
        }

        // Floating Yellow Snaptube Download Button
        binding.fabDownloadPage.setOnClickListener {
            val currentUrl = binding.webView.url
            if (currentUrl.isNullOrEmpty() || currentUrl.startsWith("chrome://")) {
                Toast.makeText(requireContext(), "No downloadable video detected on this page", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            extractAndShowDialog(currentUrl)
        }
    }

    fun loadUrl(url: String) {
        initialUrl = url
        _binding?.let {
            it.webView.loadUrl(url)
            it.etBrowserUrl.setText(url)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = binding.webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadsImagesAutomatically = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                _binding?.let {
                    it.webProgress.progress = newProgress
                    it.webProgress.visibility = if (newProgress == 100) View.GONE else View.VISIBLE
                }
            }
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                _binding?.let {
                    it.webProgress.visibility = View.VISIBLE
                    it.etBrowserUrl.setText(url ?: "")
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                _binding?.let {
                    it.webProgress.visibility = View.GONE
                    it.etBrowserUrl.setText(url ?: "")
                }
            }
        }
    }

    private fun extractAndShowDialog(url: String) {
        Toast.makeText(requireContext(), "Analyzing video on page with yt-dlp...", Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch {
            val result = DownloadManager.extractMetadata(url)
            result.onSuccess { metadata ->
                val dialog = FormatBottomSheetDialog(metadata) {
                    (activity as? MainActivity)?.navigateToDownloads()
                }
                dialog.show(parentFragmentManager, FormatBottomSheetDialog.TAG)
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    "Could not extract video: ${error.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_URL = "arg_url"

        fun newInstance(url: String): BrowserFragment {
            return BrowserFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, url)
                }
            }
        }
    }
}
