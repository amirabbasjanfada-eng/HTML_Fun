package com.example.wallpaper

import android.app.Presentation
import android.content.Context
import android.graphics.Canvas
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout
import com.example.web.ContentServer
import com.example.web.PaperWebViewClient
import com.example.web.WebViewSetup

private const val TAG = "WebRenderer"

/** Puts a web page on the wallpaper surface. Every method is called on the main thread. */
internal interface WebRenderer {
    fun attach(holder: SurfaceHolder, width: Int, height: Int)
    fun detach()
    fun setVisible(visible: Boolean)
    fun load(url: String)
    fun setFps(fps: Int)
    fun touch(event: MotionEvent)
    fun release()
}

/**
 * Compatibility renderer: a software-layer WebView is drawn into the surface on a timer.
 * Works everywhere (DOM, CSS, SVG, Canvas 2D) but runs on the CPU and cannot show WebGL.
 */
internal class CpuWebRenderer(
    private val context: Context,
    server: ContentServer,
    private val handler: Handler,
    onRendererGone: () -> Unit
) : WebRenderer {

    private var webView: WebView? = null
    private var holder: SurfaceHolder? = null
    private var width = 0
    private var height = 0
    private var visible = false
    private var released = false
    private var url: String? = null
    private var loadedUrl: String? = null
    private var fps = 60

    private val frame = object : Runnable {
        override fun run() {
            if (!visible || released) return
            drawFrame()
            handler.postDelayed(this, frameInterval())
        }
    }

    init {
        webView = WebViewSetup.create(
            context,
            PaperWebViewClient(server, renderGone = onRendererGone, pageFinished = { drawFrame() }),
            onConsole = null,
            softwareLayer = true
        )
    }

    override fun attach(holder: SurfaceHolder, width: Int, height: Int) {
        if (released) return
        this.holder = holder
        this.width = width
        this.height = height
        layoutWebView()
        loadIfReady()
        drawFrame()
        if (visible) {
            handler.removeCallbacks(frame)
            handler.post(frame)
        }
    }

    override fun detach() {
        handler.removeCallbacks(frame)
        holder = null
        width = 0
        height = 0
    }

    override fun setVisible(visible: Boolean) {
        this.visible = visible
        handler.removeCallbacks(frame)
        if (released) return
        if (visible) {
            webView?.onResume()
            handler.post(frame)
        } else {
            webView?.onPause()
        }
    }

    override fun load(url: String) {
        this.url = url
        loadIfReady()
    }

    override fun setFps(fps: Int) {
        this.fps = fps
        webView?.evaluateJavascript("window.__webpaperFps=" + fps + ";", null)
    }

    override fun touch(event: MotionEvent) {
        // The framework recycles the event when onTouchEvent returns, so it is dispatched right now.
        webView?.dispatchTouchEvent(event)
        drawFrame()
    }

    override fun release() {
        released = true
        handler.removeCallbacks(frame)
        webView?.let { WebViewSetup.dispose(it) }
        webView = null
        holder = null
    }

    private fun frameInterval(): Long = if (fps > 0) (1000L / fps).coerceAtLeast(8L) else 16L

    /** The WebView gets its size *before* the page loads, so scripts never see a 0x0 window. */
    private fun layoutWebView() {
        val view = webView ?: return
        if (width <= 0 || height <= 0) return
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
    }

    private fun loadIfReady() {
        val target = url ?: return
        if (width <= 0 || height <= 0 || target == loadedUrl) return
        loadedUrl = target
        webView?.loadUrl(target)
    }

    private fun drawFrame() {
        val view = webView ?: return
        val surfaceHolder = holder ?: return
        if (released || width <= 0 || height <= 0) return
        var canvas: Canvas? = null
        try {
            canvas = surfaceHolder.lockCanvas()
            if (canvas != null) view.draw(canvas)
        } catch (e: Exception) {
            Log.w(TAG, "drawFrame failed", e)
        } finally {
            if (canvas != null) {
                try {
                    surfaceHolder.unlockCanvasAndPost(canvas)
                } catch (e: Exception) {
                    Log.w(TAG, "unlockCanvasAndPost failed", e)
                }
            }
        }
    }
}

/**
 * GPU renderer. The WebView lives in a [Presentation] window on a private [VirtualDisplay] whose
 * output surface *is* the wallpaper surface. The page is therefore rendered by Chromium's normal
 * hardware-accelerated pipeline (WebGL, compositor-driven CSS animations, GPU raster) and the
 * system composites it straight to the wallpaper: no per-frame CPU copy.
 *
 * If anything fails (or the page never produces a frame) [onFailure] is called and the engine
 * switches to [CpuWebRenderer].
 */
internal class GpuWebRenderer(
    private val context: Context,
    private val server: ContentServer,
    private val handler: Handler,
    private val onRendererGone: () -> Unit,
    private val onFailure: (Throwable) -> Unit
) : WebRenderer {

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var root: FrameLayout? = null
    private var webView: WebView? = null
    private var holder: SurfaceHolder? = null
    private var width = 0
    private var height = 0
    private var visible = false
    private var released = false
    private var failed = false
    private var verified = false
    private var verifyAttempt = 0
    private var url: String? = null
    private var loadedUrl: String? = null

    private val verifyTask = Runnable { runVerification() }

    override fun attach(holder: SurfaceHolder, width: Int, height: Int) {
        if (released || failed || width <= 0 || height <= 0) return
        this.holder = holder
        try {
            val display = virtualDisplay
            if (display == null) {
                this.width = width
                this.height = height
                createDisplay()
            } else if (width != this.width || height != this.height) {
                this.width = width
                this.height = height
                display.resize(width, height, context.resources.displayMetrics.densityDpi)
            }
            applySurface()
            loadIfReady()
        } catch (e: Exception) {
            fail(e)
        }
    }

    override fun detach() {
        holder = null
        try {
            applySurface()
        } catch (e: Exception) {
            Log.w(TAG, "detach failed", e)
        }
    }

    override fun setVisible(visible: Boolean) {
        this.visible = visible
        if (released || failed) return
        try {
            if (visible) webView?.onResume() else webView?.onPause()
            applySurface()
            if (visible && !verified && loadedUrl != null) scheduleVerification(1500L)
        } catch (e: Exception) {
            fail(e)
        }
    }

    override fun load(url: String) {
        this.url = url
        if (!released && !failed) loadIfReady()
    }

    override fun setFps(fps: Int) {
        webView?.evaluateJavascript("window.__webpaperFps=" + fps + ";", null)
    }

    override fun touch(event: MotionEvent) {
        if (!failed) root?.dispatchTouchEvent(event)
    }

    override fun release() {
        released = true
        handler.removeCallbacks(verifyTask)
        try {
            virtualDisplay?.surface = null
        } catch (e: Exception) {
            Log.w(TAG, "clearing surface failed", e)
        }
        try {
            presentation?.dismiss()
        } catch (e: Exception) {
            Log.w(TAG, "dismiss failed", e)
        }
        webView?.let { WebViewSetup.dispose(it) }
        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            Log.w(TAG, "virtual display release failed", e)
        }
        presentation = null
        root = null
        webView = null
        virtualDisplay = null
        holder = null
    }

    private fun createDisplay() {
        val dpi = context.resources.displayMetrics.densityDpi
        val manager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        // OWN_CONTENT_ONLY: show only our window, never mirror the real screen. No permission needed.
        val display = manager.createVirtualDisplay(
            "webpaper", width, height, dpi, null, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
        ) ?: throw IllegalStateException("Could not create a virtual display")
        virtualDisplay = display

        val dialog = Presentation(context, display.display)
        dialog.window?.let { window ->
            window.addFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            )
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val view = WebViewSetup.create(
            dialog.context,
            PaperWebViewClient(server, renderGone = onRendererGone),
            onConsole = null,
            softwareLayer = false
        )
        val container = FrameLayout(dialog.context)
        container.addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        dialog.setContentView(container)
        presentation = dialog
        root = container
        webView = view
        dialog.show()
        if (!visible) view.onPause()
    }

    /** Frames only flow to the wallpaper while it is visible; a null surface suspends the display. */
    private fun applySurface() {
        val display = virtualDisplay ?: return
        val surfaceHolder = holder
        display.surface = if (visible && surfaceHolder != null && surfaceHolder.surface.isValid) {
            surfaceHolder.surface
        } else {
            null
        }
    }

    private fun loadIfReady() {
        val target = url ?: return
        val view = webView ?: return
        if (target == loadedUrl) return
        loadedUrl = target
        verified = false
        verifyAttempt = 0
        view.loadUrl(target)
        scheduleVerification(2500L)
    }

    private fun scheduleVerification(delayMs: Long) {
        handler.removeCallbacks(verifyTask)
        handler.postDelayed(verifyTask, delayMs)
    }

    /**
     * The injected helper counts the first few animation frames. If the page never renders one while
     * the wallpaper is visible, the GPU path is not working on this device: fall back.
     */
    private fun runVerification() {
        val view = webView
        if (released || failed || verified || view == null || !visible) return
        view.evaluateJavascript("window.__webpaperFrames||0") { result ->
            if (released || failed || verified) return@evaluateJavascript
            val frames = result?.trim('"')?.toIntOrNull() ?: 0
            if (frames > 0) {
                verified = true
            } else if (++verifyAttempt >= MAX_VERIFY_ATTEMPTS) {
                fail(IllegalStateException("The page never produced a frame"))
            } else {
                scheduleVerification(VERIFY_INTERVAL_MS)
            }
        }
    }

    private fun fail(error: Throwable) {
        if (failed || released) return
        failed = true
        onFailure(error)
    }

    private companion object {
        const val MAX_VERIFY_ATTEMPTS = 3
        const val VERIFY_INTERVAL_MS = 2000L
    }
}
