package com.snaptube.dl.engine

import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray

class WebMediaSniffer(
    private val onStreamDetected: (String, String) -> Unit
) {

    @JavascriptInterface
    fun onMediaFound(jsonArrayStr: String, title: String) {
        try {
            val array = JSONArray(jsonArrayStr)
            for (i in 0 until array.length()) {
                val url = array.getString(i)
                if (isValidMediaUrl(url)) {
                    onStreamDetected(url, title)
                    break
                }
            }
        } catch (e: Exception) {
            // Ignored
        }
    }

    fun getInjectedJs(): String {
        return """
            (function() {
                function scan() {
                    var urls = [];
                    var videos = document.getElementsByTagName('video');
                    for (var i = 0; i < videos.length; i++) {
                        var v = videos[i];
                        if (v.currentSrc && v.currentSrc.indexOf('http') === 0) urls.push(v.currentSrc);
                        else if (v.src && v.src.indexOf('http') === 0) urls.push(v.src);
                    }
                    var sources = document.getElementsByTagName('source');
                    for (var j = 0; j < sources.length; j++) {
                        var s = sources[j];
                        if (s.src && s.src.indexOf('http') === 0) urls.push(s.src);
                    }
                    if (urls.length > 0 && window.SnapBoxBridge) {
                        window.SnapBoxBridge.onMediaFound(JSON.stringify(urls), document.title);
                    }
                }
                scan();
                setInterval(scan, 1500);
            })();
        """.trimIndent()
    }

    fun inspectRequest(request: WebResourceRequest?, currentTitle: String) {
        val url = request?.url?.toString() ?: return
        if (isValidMediaUrl(url)) {
            onStreamDetected(url, currentTitle)
        }
    }

    private fun isValidMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".mp4") ||
                lower.contains(".m4a") ||
                lower.contains("googlevideo.com/videoplayback") ||
                lower.contains("cdninstagram.com") ||
                lower.contains("tiktokcdn.com") ||
                lower.contains("fbcdn.net")
    }
}