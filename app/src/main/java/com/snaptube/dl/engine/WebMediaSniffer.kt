package com.snaptube.dl.engine

import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import org.json.JSONArray

class WebMediaSniffer(
    private val onStreamDetected: (String, String) -> Unit
) {
    private var lastDetectedUrl: String = ""
    private var lastDetectedTime: Long = 0L

    @JavascriptInterface
    fun onMediaFound(jsonArrayStr: String, title: String) {
        try {
            val array = JSONArray(jsonArrayStr)
            for (i in 0 until array.length()) {
                val url = cleanMediaUrl(array.getString(i))
                if (isValidMediaUrl(url)) {
                    notifyDetected(url, title)
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
                if (window.__snapbox_sniffer_active) return;
                window.__snapbox_sniffer_active = true;

                function report(url) {
                    if (!url || typeof url !== 'string') return;
                    if (url.indexOf('http') !== 0) return;
                    if (window.SnapBoxBridge) {
                        window.SnapBoxBridge.onMediaFound(JSON.stringify([url]), document.title || 'Web Video');
                    }
                }

                // Intercept XHR
                try {
                    var origOpen = XMLHttpRequest.prototype.open;
                    XMLHttpRequest.prototype.open = function(method, url) {
                        report(url);
                        return origOpen.apply(this, arguments);
                    };
                } catch(e) {}

                // Intercept fetch
                try {
                    if (window.fetch) {
                        var origFetch = window.fetch;
                        window.fetch = function() {
                            var arg = arguments[0];
                            var url = (typeof arg === 'string') ? arg : (arg ? arg.url : '');
                            report(url);
                            return origFetch.apply(this, arguments);
                        };
                    }
                } catch(e) {}

                // Periodically scan DOM video elements
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
                        window.SnapBoxBridge.onMediaFound(JSON.stringify(urls), document.title || 'Web Video');
                    }
                }
                scan();
                setInterval(scan, 1500);
            })();
        """.trimIndent()
    }

    fun inspectRequest(request: WebResourceRequest?, currentTitle: String) {
        val rawUrl = request?.url?.toString() ?: return
        val url = cleanMediaUrl(rawUrl)
        if (isValidMediaUrl(url)) {
            notifyDetected(url, currentTitle)
        }
    }

    private fun cleanMediaUrl(raw: String): String {
        return raw.replace(Regex("&range=\\d+-\\d+"), "")
    }

    @Synchronized
    private fun notifyDetected(url: String, title: String) {
        val now = System.currentTimeMillis()
        if (url == lastDetectedUrl && (now - lastDetectedTime) < 4000) {
            return
        }
        lastDetectedUrl = url
        lastDetectedTime = now
        onStreamDetected(url, title)
    }

    fun isValidMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        // Ignore static web assets and analytics
        if (lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png") ||
            lower.contains(".webp") || lower.contains(".gif") || lower.contains(".svg") ||
            lower.contains(".css") || lower.contains(".js") || lower.contains(".ico") ||
            lower.contains("google-analytics") || lower.contains("doubleclick") ||
            lower.contains("facebook.com/tr") || lower.contains("favicon")) {
            return false
        }

        return lower.contains(".mp4") ||
                lower.contains(".m4a") ||
                lower.contains(".webm") ||
                lower.contains(".m3u8") ||
                lower.contains("videoplayback") ||
                lower.contains("googlevideo.com") ||
                lower.contains("cdninstagram.com") ||
                lower.contains("tiktokcdn") ||
                lower.contains("fbcdn.net") ||
                lower.contains("mime=video") ||
                lower.contains("mime=audio") ||
                lower.contains("/video/") ||
                lower.contains("video_id=")
    }
}