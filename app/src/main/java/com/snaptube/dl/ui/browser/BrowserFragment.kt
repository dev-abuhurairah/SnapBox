package com.snaptube.dl.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.snaptube.dl.MainActivity
import com.snaptube.dl.R
import com.snaptube.dl.data.FormatOption
import com.snaptube.dl.data.VideoMetadata
import com.snaptube.dl.databinding.FragmentBrowserBinding
import com.snaptube.dl.engine.DownloadManager
import com.snaptube.dl.engine.WebMediaSniffer
import com.snaptube.dl.ui.dialogs.FormatBottomSheetDialog
import java.util.UUID

class BrowserFragment : Fragment() {

    private var _binding: FragmentBrowserBinding? = null
    private val binding get() = _binding!!

    private var initialUrl: String = "https://m.youtube.com"
    private var sniffedStreamUrl: String? = null
    private var sniffedTitle: String = "Video on Page"
    private var lastAutoPromptUrl: String? = null

    private val sniffer = WebMediaSniffer { url, title ->
        activity?.runOnUiThread {
            if (_binding == null || !isAdded) return@runOnUiThread
            sniffedStreamUrl = url
            val currentWebTitle = binding.webView.title.orEmpty()
            val clean = cleanTitle(if (title.isNotBlank() && title != "Web Video") title else currentWebTitle)
            if (clean.isNotBlank()) sniffedTitle = clean

            // Pulse floating Snaptube yellow download button
            binding.fabDownloadPage.setColorFilter(ContextCompat.getColor(requireContext(), R.color.text_on_yellow))
            binding.fabDownloadPage.animate().scaleX(1.25f).scaleY(1.25f).setDuration(220).withEndAction {
                _binding?.fabDownloadPage?.animate()?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(220)?.start()
            }.start()

            // Automatically open download bottom sheet once per detected stream
            if (lastAutoPromptUrl != url && parentFragmentManager.findFragmentByTag(FormatBottomSheetDialog.TAG) == null) {
                lastAutoPromptUrl = url
                showDownloadSheetForStream(url, sniffedTitle, binding.webView.url.orEmpty())
            }
        }
    }

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

        // Floating Yellow Snaptube Action Button
        binding.fabDownloadPage.setOnClickListener {
            val detected = sniffedStreamUrl
            val pageUrl = binding.webView.url.orEmpty()

            if (detected != null) {
                showDownloadSheetForStream(detected, sniffedTitle, pageUrl)
            } else if (pageUrl.isNotBlank() && (pageUrl.startsWith("http://") || pageUrl.startsWith("https://"))) {
                // Fallback: extract page link
                (activity as? MainActivity)?.let {
                    Toast.makeText(requireContext(), "Extracting media from page...", Toast.LENGTH_SHORT).show()
                    val metadata = VideoMetadata(
                        id = UUID.randomUUID().toString(),
                        title = binding.webView.title ?: "Web Video",
                        uploader = "Online Stream",
                        duration = "01:00",
                        thumbnailUrl = "",
                        webpageUrl = pageUrl,
                        audioFormats = listOf(
                            FormatOption("audio-1", "Audio (MP3)", "mp3", "~3 - 6 MB", pageUrl, true)
                        ),
                        videoFormats = listOf(
                            FormatOption("video-1", "Video (MP4)", "mp4", "~10 - 25 MB", pageUrl, false)
                        )
                    )
                    val dialog = FormatBottomSheetDialog(metadata) {
                        it.navigateToDownloads()
                    }
                    dialog.show(parentFragmentManager, FormatBottomSheetDialog.TAG)
                }
            } else {
                Toast.makeText(requireContext(), "No media stream detected on this page yet. Play the video to detect!", Toast.LENGTH_SHORT).show()
            }
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
        settings.mediaPlaybackRequiresUserGesture = false
        settings.loadsImagesAutomatically = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        binding.webView.addJavascriptInterface(sniffer, "SnapBoxBridge")

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
                sniffedStreamUrl = null
                lastAutoPromptUrl = null
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                _binding?.let {
                    it.webProgress.visibility = View.GONE
                    it.etBrowserUrl.setText(url ?: "")
                    it.webView.evaluateJavascript(sniffer.getInjectedJs(), null)
                }
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                sniffer.inspectRequest(request, binding.webView.title ?: "Web Video")
                return super.shouldInterceptRequest(view, request)
            }
        }
    }

    private fun cleanTitle(raw: String): String {
        return raw.replace(" - YouTube", "")
            .replace(" | Instagram", "")
            .replace(" on TikTok", "")
            .replace(" | Facebook", "")
            .trim().ifEmpty { "Web Video" }
    }

    fun canGoBack(): Boolean = _binding?.webView?.canGoBack() == true
    fun goBack() { _binding?.webView?.goBack() }

    fun loadUrl(url: String) {
        _binding?.let {
            it.webView.loadUrl(url)
        } ?: run {
            initialUrl = url
        }
    }

    private fun showDownloadSheetForStream(streamUrl: String, title: String, pageUrl: String) {
        val clean = cleanTitle(title)
        val metadata = VideoMetadata(
            id = UUID.randomUUID().toString(),
            title = clean,
            uploader = "Media Stream",
            duration = "01:00",
            thumbnailUrl = "",
            webpageUrl = pageUrl,
            audioFormats = listOf(
                FormatOption("audio-1", "Audio (MP3 / M4A)", "mp3", "~3 - 6 MB", streamUrl, true)
            ),
            videoFormats = listOf(
                FormatOption("video-1", "Video (MP4 Direct)", "mp4", "~10 - 35 MB", streamUrl, false, 720)
            )
        )

        val dialog = FormatBottomSheetDialog(metadata) {
            (activity as? MainActivity)?.navigateToDownloads()
        }
        dialog.show(parentFragmentManager, FormatBottomSheetDialog.TAG)
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