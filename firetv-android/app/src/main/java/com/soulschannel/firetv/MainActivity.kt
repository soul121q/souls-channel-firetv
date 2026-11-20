package com.soulschannel.firetv

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Soul's Channel — Amazon Fire TV Cube Native Application
 *
 * - Optimised for 10-foot TV display & Amazon Fire TV Alexa Voice Remote (zero keyboard/mouse needed)
 * - Hardware-accelerated Media3 ExoPlayer bridge for Fire TV Cube HLS/M3U8 & 4K playback
 * - Strict Security Sandbox: blocks pop-ups, external downloads, file-system access, and untrusted navigation
 */
class MainActivity : AppCompatActivity() {

    private lateinit var rootLayout: FrameLayout
    private lateinit var webView: WebView
    private var nativePlayerView: PlayerView? = null
    private var exoPlayer: ExoPlayer? = null
    private var allowedOriginHost: String = ""

    companion object {
        private val BLOCKED_DOWNLOAD_REGEX =
            Regex("""\.(exe|msi|apk|aab|xapk|bat|cmd|ps1|vbs|scr|dmg|pkg|sh|jar|zip|rar)(\?|#|$)""", RegexOption.IGNORE_CASE)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep TV screen awake during playback
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prefs = getSharedPreferences("souls_channel_tv", MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", getString(R.string.default_server_url))
            ?: getString(R.string.default_server_url)
        allowedOriginHost = Uri.parse(serverUrl).host?.lowercase().orEmpty()

        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#090B10"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#090B10"))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFocusable = true
            isFocusableInTouchMode = true
        }

        // Strict Security Sandbox for WebView (never disables Fire OS security protections)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "$userAgentString SoulsChannelFireTV/1.0 AFTCube"
        }

        // Block all automatic software downloads from streaming sources
        webView.setDownloadListener { _, _, _, _, _ ->
            // Automatically rejected for Fire TV security
        }

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return true
                val scheme = uri.scheme?.lowercase().orEmpty()
                val host = uri.host?.lowercase().orEmpty()
                val raw = uri.toString()

                // Block non-HTTP(S) schemes (javascript:, file:, intent:, etc.) and deceptive downloads
                if (scheme != "https" && scheme != "http") return true
                if (BLOCKED_DOWNLOAD_REGEX.containsMatchIn(raw)) return true

                // Keep top-level navigation strictly locked to Soul's Channel server origin
                return host != allowedOriginHost
            }
        }

        webView.addJavascriptInterface(FireTvBridge(), "FireTvNative")
        rootLayout.addView(webView)
        setContentView(rootLayout)

        webView.loadUrl(serverUrl)
        webView.requestFocus()
    }

    /**
     * Native Fire TV Cube Hardware ExoPlayer Bridge for smooth HLS (.m3u8) / 4K decoding
     */
    inner class FireTvBridge {
        @JavascriptInterface
        fun isFireTv(): Boolean = true

        @JavascriptInterface
        @OptIn(UnstableApi::class)
        fun playNativeStream(streamUrl: String) {
            val uri = Uri.parse(streamUrl)
            val scheme = uri.scheme?.lowercase().orEmpty()
            if ((scheme != "https" && scheme != "http") || BLOCKED_DOWNLOAD_REGEX.containsMatchIn(streamUrl)) {
                return
            }
            runOnUiThread {
                stopNativePlaybackInternal()
                val player = ExoPlayer.Builder(this@MainActivity).build()
                val view = PlayerView(this@MainActivity).apply {
                    setBackgroundColor(Color.BLACK)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    this.player = player
                    keepScreenOn = true
                }
                exoPlayer = player
                nativePlayerView = view
                rootLayout.addView(view)
                view.requestFocus()

                player.setMediaItem(MediaItem.fromUri(uri))
                player.prepare()
                player.playWhenReady = true
            }
        }

        @JavascriptInterface
        fun stopAllPlayback() {
            runOnUiThread {
                stopNativePlaybackInternal()
            }
        }

        @JavascriptInterface
        fun clearTempCache() {
            runOnUiThread {
                stopNativePlaybackInternal()
                webView.clearCache(true)
            }
        }
    }

    private fun stopNativePlaybackInternal() {
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
        nativePlayerView?.let { rootLayout.removeView(it) }
        nativePlayerView = null
        webView.requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // When native Fire TV ExoPlayer is open, Back closes it cleanly back to Soul's Channel
        if (nativePlayerView != null) {
            if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK) {
                stopNativePlaybackInternal()
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        // Route Fire TV Remote Media Keys directly into Soul's Channel Web TV controller
        if (event.action == KeyEvent.ACTION_DOWN) {
            val jsKey = when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "MediaPlayPause"
                KeyEvent.KEYCODE_MEDIA_PLAY -> "MediaPlay"
                KeyEvent.KEYCODE_MEDIA_PAUSE -> "MediaPause"
                KeyEvent.KEYCODE_MEDIA_STOP -> "MediaStop"
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "MediaFastForward"
                KeyEvent.KEYCODE_MEDIA_REWIND -> "MediaRewind"
                KeyEvent.KEYCODE_MEDIA_NEXT -> "MediaTrackNext"
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "MediaTrackPrevious"
                KeyEvent.KEYCODE_CHANNEL_UP -> "ChannelUp"
                KeyEvent.KEYCODE_CHANNEL_DOWN -> "ChannelDown"
                KeyEvent.KEYCODE_BACK -> "Escape"
                else -> null
            }
            if (jsKey != null) {
                webView.evaluateJavascript(
                    "window.dispatchEvent(new KeyboardEvent('keydown', { key: '$jsKey', bubbles: true }));",
                    null
                )
                return true
            }
        }

        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        stopNativePlaybackInternal()
        webView.destroy()
        super.onDestroy()
    }
}
