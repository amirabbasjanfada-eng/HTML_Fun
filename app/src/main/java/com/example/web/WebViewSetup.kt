package com.example.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/** One WebViewClient for the in-app preview and both wallpaper renderers. */
class PaperWebViewClient(
    private val server: ContentServer,
    private val renderGone: (() -> Unit)? = null,
    private val pageFinished: (() -> Unit)? = null
) : WebViewClient() {

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
        server.intercept(request)

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        pageFinished?.invoke()
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            Log.w(TAG, "Page failed to load: " + error?.description)
        }
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        Log.w(TAG, "WebView renderer process is gone")
        renderGone?.invoke()
        return true // keep the app alive; the owner rebuilds the WebView
    }

    private companion object {
        const val TAG = "PaperWebViewClient"
    }
}

object WebViewSetup {

    private const val TAG = "WebPaper"

    /**
     * Creates a WebView configured for wallpaper pages. The system WebView is Chromium and updates
     * through Google Play, so current HTML, CSS and JavaScript (ES modules, import maps, container
     * queries, WebGL, ...) are available without shipping a browser engine of our own.
     */
    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    fun create(
        context: Context,
        client: WebViewClient,
        onConsole: ((String) -> Unit)?,
        softwareLayer: Boolean = false
    ): WebView {
        // Lets you inspect wallpaper pages from chrome://inspect in debug builds.
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        val view = WebView(context)
        view.setBackgroundColor(Color.BLACK)
        view.isVerticalScrollBarEnabled = false
        view.isHorizontalScrollBarEnabled = false
        view.overScrollMode = View.OVER_SCROLL_NEVER
        if (softwareLayer) view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        val settings = view.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // Wallpaper code is user supplied: it never needs access to local files or content providers.
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.textZoom = 100 // don't let the system font size change a wallpaper's layout
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = false // API 26+: unguarded calls crash Android 7.x
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Build.VERSION.SDK_INT < 33) {
            // Android 10-12 may otherwise "force dark" a page and change its colours.
            settings.forceDark = WebSettings.FORCE_DARK_OFF
        }

        view.webViewClient = client
        view.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                if (message != null) {
                    val level = message.messageLevel()
                    val prefix = if (level == ConsoleMessage.MessageLevel.ERROR) "error: " else ""
                    val line = prefix + message.message() + " (line " + message.lineNumber() + ")"
                    Log.d(TAG, "JS $level: $line")
                    onConsole?.invoke(line)
                }
                return true
            }
        }
        return view
    }

    fun dispose(view: WebView) {
        try {
            view.stopLoading()
            view.webViewClient = WebViewClient()
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "WebView dispose failed", e)
        }
    }

    /** Human readable version of the Chromium based engine that runs the wallpapers. */
    fun engineVersion(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val info = WebView.getCurrentWebViewPackage()
            if (info != null) return "WebView " + (info.versionName ?: "")
        }
        return "System WebView"
    }
}
