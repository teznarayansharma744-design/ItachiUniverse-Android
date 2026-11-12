package com.arknox.youtube

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject

/**
 * YouTube player view using WebView with Plyr.js.
 * Uses WebViewAssetLoader to serve assets from https:// URL for YouTube API compatibility.
 * Supports fullscreen video playback.
 */
@SuppressLint("SetJavaScriptEnabled")
class YouTubePlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    private val listeners = mutableListOf<YouTubePlayerListener>()
    private var pendingVideoId: String? = null
    private var isPageLoaded = false

    // Fullscreen support
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var originalOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var isFullscreen = false

    // Asset loader to serve files from https:// URL
    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()

    init {
        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
        }

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                return request?.let { assetLoader.shouldInterceptRequest(it.url) }
                    ?: super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isPageLoaded = true
                pendingVideoId?.let {
                    evaluateJavascript("loadVideo('$it')", null)
                    pendingVideoId = null
                }
            }
        }

        // Custom WebChromeClient with fullscreen support
        webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (fullscreenView != null) {
                    callback?.onCustomViewHidden()
                    return
                }

                val activity = getActivity() ?: return

                fullscreenView = view
                fullscreenCallback = callback
                originalOrientation = activity.requestedOrientation

                // Set landscape orientation for fullscreen video
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

                // Hide system UI for immersive fullscreen
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

                // Add fullscreen view to window
                val decorView = activity.window.decorView as FrameLayout
                view?.setBackgroundColor(Color.BLACK)
                decorView.addView(view, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ))

                isFullscreen = true
                listeners.forEach { it.onFullscreenChange(true) }
            }

            override fun onHideCustomView() {
                if (fullscreenView == null) return

                val activity = getActivity() ?: return

                // Remove fullscreen view
                val decorView = activity.window.decorView as FrameLayout
                decorView.removeView(fullscreenView)

                // Restore orientation
                activity.requestedOrientation = originalOrientation

                // Restore system UI
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

                fullscreenCallback?.onCustomViewHidden()
                fullscreenView = null
                fullscreenCallback = null

                isFullscreen = false
                listeners.forEach { it.onFullscreenChange(false) }
            }
        }

        addJavascriptInterface(PlayerJSInterface(), "Android")

        // Load from https:// URL using asset loader (required for YouTube API)
        loadUrl("https://appassets.androidplatform.net/assets/player.html")
    }

    private fun getActivity(): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    fun addListener(listener: YouTubePlayerListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: YouTubePlayerListener) {
        listeners.remove(listener)
    }

    /**
     * Load a YouTube video by ID or URL.
     */
    fun loadVideo(videoIdOrUrl: String) {
        if (isPageLoaded) {
            evaluateJavascript("loadVideo('$videoIdOrUrl')", null)
        } else {
            pendingVideoId = videoIdOrUrl
        }
    }

    fun play() {
        if (isPageLoaded) {
            evaluateJavascript("if(player) player.play()", null)
        }
    }

    fun pause() {
        if (isPageLoaded) {
            evaluateJavascript("if(player) player.pause()", null)
        }
    }

    /**
     * Toggle fullscreen mode programmatically.
     */
    fun toggleFullscreen() {
        if (isPageLoaded) {
            evaluateJavascript("if(player) player.fullscreen.toggle()", null)
        }
    }

    /**
     * Exit fullscreen if currently in fullscreen.
     * Returns true if was in fullscreen and exited.
     */
    fun exitFullscreen(): Boolean {
        if (isFullscreen && fullscreenCallback != null) {
            fullscreenCallback?.onCustomViewHidden()
            return true
        }
        return false
    }

    /**
     * Check if currently in fullscreen mode.
     */
    fun isInFullscreen(): Boolean = isFullscreen

    /**
     * Release resources. Call this in your Activity's onDestroy().
     * Note: Don't call this if the view is still attached to the window.
     */
    fun release() {
        exitFullscreen()
        listeners.clear()
        isPageLoaded = false
        pendingVideoId = null
        stopLoading()
        // Clear WebView content but don't destroy - let the system handle it
        loadUrl("about:blank")
        clearHistory()
        clearCache(true)
        removeAllViews()
        // Only call destroy if not attached
        if (parent == null) {
            destroy()
        }
    }

    private inner class PlayerJSInterface {
        @JavascriptInterface
        fun onPlayerEvent(event: String, dataJson: String) {
            post {
                val data = try {
                    JSONObject(dataJson)
                } catch (e: Exception) {
                    JSONObject()
                }

                when (event) {
                    "ready" -> {
                        val videoId = data.optString("videoId", "")
                        listeners.forEach { it.onReady(videoId) }
                    }
                    "playing" -> {
                        val videoId = data.optString("videoId", "")
                        listeners.forEach { it.onPlaying(videoId) }
                    }
                    "paused" -> {
                        val videoId = data.optString("videoId", "")
                        listeners.forEach { it.onPaused(videoId) }
                    }
                    "ended" -> {
                        val videoId = data.optString("videoId", "")
                        listeners.forEach { it.onEnded(videoId) }
                    }
                    "error" -> {
                        val message = data.optString("message", "Unknown error")
                        listeners.forEach { it.onError(message) }
                    }
                }
            }
        }
    }
}
