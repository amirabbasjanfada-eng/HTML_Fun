package com.example.web

import android.content.Context
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.example.data.AppSettings
import com.example.data.Presets
import com.example.data.WallpaperPaths
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Answers WebView requests for `https://<id>.webpaper.local/...` straight from the APK assets
 * (presets) or the wallpaper's folder (custom). Because the page is loaded from a real https origin:
 *  - ES modules, import maps and top-level await work,
 *  - relative files (`./style.css`, `./lib/three.module.js`) resolve,
 *  - localStorage / IndexedDB / secure-context APIs are available.
 * Requests to any other host (CDNs, APIs) are left alone and go to the network as usual.
 */
class ContentServer(context: Context) {

    private val app = context.applicationContext
    private val settings = AppSettings(app)
    private val drafts = ConcurrentHashMap<String, String>()

    fun url(id: String, version: Long = 0L, draft: Boolean = false): String =
        ContentRoutes.urlFor(id, version, draft)

    /** Unsaved editor content, served only on the `d-<id>` origin so the live wallpaper never sees it. */
    fun setDraft(id: String, html: String?) {
        if (html == null) drafts.remove(id) else drafts[id] = html
    }

    fun clearDrafts() {
        drafts.clear()
    }

    /** Called by WebViewClient.shouldInterceptRequest on a background thread. */
    fun intercept(request: WebResourceRequest?): WebResourceResponse? {
        val uri = request?.url ?: return null
        val target = ContentRoutes.parse(uri.host, uri.path) ?: return null
        return try {
            serve(target)
        } catch (e: Exception) {
            Log.w(TAG, "Could not serve $uri", e)
            notFound()
        }
    }

    private fun serve(target: ContentRoutes.Target): WebResourceResponse {
        val relative = target.path ?: return notFound()
        val preset = Presets.byId(target.id)
        if (preset == null && !WallpaperPaths.isCustomId(target.id)) return notFound()

        if (relative == "index.html") {
            val raw: String = when {
                target.draft && drafts.containsKey(target.id) -> drafts[target.id]
                preset != null -> readAsset("presets/${preset.id}/index.html")
                else -> readFile(File(WallpaperPaths.dir(app, target.id), "index.html"))
            } ?: return notFound()
            // Presets already handle touch themselves, so they don't get the touch->mouse bridge.
            val html = HtmlHelper.prepareHtml(raw, touchPolyfill = preset == null, fps = settings.fps)
            return respond("text/html", ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)))
        }

        val stream: InputStream = (
            if (preset != null) openAsset("presets/${preset.id}/$relative") else openFile(target.id, relative)
            ) ?: return notFound()
        return respond(mimeFor(relative), stream)
    }

    private fun readAsset(path: String): String? = try {
        app.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (e: IOException) {
        null
    }

    private fun readFile(file: File): String? = if (file.isFile) file.readText() else null

    private fun openAsset(path: String): InputStream? = try {
        app.assets.open(path)
    } catch (e: IOException) {
        null
    }

    private fun openFile(id: String, relative: String): InputStream? {
        val dir = WallpaperPaths.dir(app, id)
        val file = File(dir, relative)
        // Defence in depth on top of ContentRoutes: never leave the wallpaper's own folder.
        if (!file.canonicalPath.startsWith(dir.canonicalPath + File.separator) || !file.isFile) return null
        return file.inputStream()
    }

    private fun respond(mime: String, data: InputStream): WebResourceResponse {
        val textual = mime.startsWith("text/") || mime == "application/json" ||
            mime == "image/svg+xml" || mime == "application/manifest+json"
        val headers = HashMap<String, String>()
        headers["Access-Control-Allow-Origin"] = "*"
        headers["Cache-Control"] = "no-store"
        return WebResourceResponse(mime, if (textual) "utf-8" else null, 200, "OK", headers, data)
    }

    private fun notFound(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "utf-8",
        404,
        "Not Found",
        HashMap<String, String>(),
        ByteArrayInputStream(ByteArray(0))
    )

    private fun mimeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html"
        "js", "mjs", "cjs" -> "text/javascript"
        "css" -> "text/css"
        "json", "map" -> "application/json"
        "webmanifest" -> "application/manifest+json"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "avif" -> "image/avif"
        "ico" -> "image/x-icon"
        "woff2" -> "font/woff2"
        "woff" -> "font/woff"
        "ttf" -> "font/ttf"
        "otf" -> "font/otf"
        "wasm" -> "application/wasm"
        "txt", "glsl", "vert", "frag" -> "text/plain"
        "mp3" -> "audio/mpeg"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "glb" -> "model/gltf-binary"
        "gltf" -> "model/gltf+json"
        else -> "application/octet-stream"
    }

    private companion object {
        const val TAG = "ContentServer"
    }
}
