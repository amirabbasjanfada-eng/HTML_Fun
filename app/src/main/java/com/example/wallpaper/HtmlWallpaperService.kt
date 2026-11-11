package com.example.wallpaper

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import com.example.data.AppSettings
import com.example.data.Presets
import com.example.data.WallpaperPaths
import com.example.data.WallpaperScreen
import com.example.web.ContentServer

private const val TAG = "HtmlWallpaperService"

/**
 * The live wallpaper. It only wires things together: it resolves which wallpaper its screen shows,
 * owns a [WebRenderer] (GPU first, CPU as fallback) and reacts to setting changes without
 * restarting the page unless the content really changed.
 *
 * There are two services, one per screen ([HtmlWallpaperService] = Home, [HtmlLockWallpaperService]
 * = Lock). Each reads only its own screen's selection and content version, which is what keeps the
 * Home and Lock wallpapers completely independent.
 */
abstract class BaseHtmlWallpaperService(private val screen: WallpaperScreen) : WallpaperService() {

    override fun onCreateEngine(): Engine = PaperEngine()

    private inner class PaperEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {

        private val handler = Handler(Looper.getMainLooper())
        private lateinit var settings: AppSettings
        private lateinit var server: ContentServer
        private var renderer: WebRenderer? = null
        private var holder: SurfaceHolder? = null
        private var width = 0
        private var height = 0
        private var visible = false
        private var destroyed = false
        private var gpuFailed = false
        private var currentUrl: String? = null

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            val context = this@BaseHtmlWallpaperService
            settings = AppSettings(context)
            server = ContentServer(context)
            settings.register(this)
            setTouchEventsEnabled(settings.touchEnabled)
            currentUrl = resolveUrl()
            renderer = createRenderer()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.holder = holder
            this.width = width
            this.height = height
            renderer?.attach(holder, width, height)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            renderer?.detach()
            this.holder = null
            width = 0
            height = 0
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            this.visible = visible
            renderer?.setVisible(visible)
        }

        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
            if (settings.touchEnabled) renderer?.touch(event)
        }

        override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
            handler.post {
                if (destroyed) return@post
                when (key) {
                    // This screen's wallpaper was changed or edited. The other screen's keys are not
                    // matched here, so a change for the other screen never reloads this one.
                    AppSettings.idKey(screen), AppSettings.versionKey(screen) -> reloadActive()
                    // Plain settings are applied live, without restarting the page.
                    AppSettings.KEY_FPS -> renderer?.setFps(settings.fps)
                    AppSettings.KEY_TOUCH -> setTouchEventsEnabled(settings.touchEnabled)
                    AppSettings.KEY_GPU -> {
                        gpuFailed = false
                        rebuildRenderer()
                    }
                    else -> Unit
                }
            }
        }

        override fun onDestroy() {
            destroyed = true
            settings.unregister(this)
            handler.removeCallbacksAndMessages(null)
            renderer?.release()
            renderer = null
            super.onDestroy()
        }

        private fun resolveUrl(): String {
            val context = this@BaseHtmlWallpaperService
            val id = settings.idFor(screen).takeIf { WallpaperPaths.exists(context, it) } ?: Presets.DEFAULT.id
            return server.url(id, settings.versionFor(screen))
        }

        private fun reloadActive() {
            val url = resolveUrl()
            if (url == currentUrl) return
            currentUrl = url
            renderer?.load(url)
        }

        private fun createRenderer(): WebRenderer {
            val context = this@BaseHtmlWallpaperService
            val created: WebRenderer = if (settings.gpuRendering && !gpuFailed) {
                GpuWebRenderer(
                    context,
                    server,
                    handler,
                    { onRendererGone() },
                    { error -> onGpuFailed(error) }
                )
            } else {
                CpuWebRenderer(context, server, handler) { onRendererGone() }
            }
            created.setFps(settings.fps)
            currentUrl?.let { created.load(it) }
            created.setVisible(visible)
            return created
        }

        private fun rebuildRenderer() {
            renderer?.release()
            val created = createRenderer()
            renderer = created
            val surfaceHolder = holder
            if (surfaceHolder != null && width > 0 && height > 0) {
                created.attach(surfaceHolder, width, height)
            }
        }

        private fun onRendererGone() {
            handler.post { if (!destroyed) rebuildRenderer() }
        }

        private fun onGpuFailed(error: Throwable) {
            handler.post {
                if (destroyed) return@post
                Log.w(TAG, "GPU rendering is not available here, using compatibility rendering", error)
                gpuFailed = true
                rebuildRenderer()
            }
        }
    }
}

/** The live wallpaper for the Home screen. (Same class as before, so an applied wallpaper keeps working.) */
class HtmlWallpaperService : BaseHtmlWallpaperService(WallpaperScreen.HOME)

/** The live wallpaper for the Lock screen. */
class HtmlLockWallpaperService : BaseHtmlWallpaperService(WallpaperScreen.LOCK)
