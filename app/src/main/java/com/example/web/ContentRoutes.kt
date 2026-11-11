package com.example.web

/**
 * URL scheme for wallpaper content. Every wallpaper gets its *own* origin, so localStorage and
 * IndexedDB are isolated per wallpaper:
 *
 *   https://<id>.webpaper.local/index.html     the wallpaper (a preset or a custom folder)
 *   https://d-<id>.webpaper.local/index.html   an unsaved draft from the editor ("Run")
 *
 * Nothing is ever requested from these hosts over the network; the requests are answered locally.
 * Pure Kotlin (no Android types) so it can be unit tested.
 */
object ContentRoutes {

    const val SUFFIX = ".webpaper.local"
    private const val DRAFT_PREFIX = "d-"

    /** [path] is relative ("index.html", "js/app.js") or null when it tried to escape the folder. */
    data class Target(val id: String, val path: String?, val draft: Boolean)

    fun urlFor(id: String, version: Long = 0L, draft: Boolean = false): String =
        "https://" + (if (draft) DRAFT_PREFIX else "") + id + SUFFIX + "/index.html?v=" + version

    fun parse(host: String?, rawPath: String?): Target? {
        if (host == null || !host.endsWith(SUFFIX)) return null
        var label = host.removeSuffix(SUFFIX)
        val draft = label.startsWith(DRAFT_PREFIX)
        if (draft) label = label.removePrefix(DRAFT_PREFIX)
        if (label.isEmpty()) return null
        return Target(label, cleanPath(rawPath), draft)
    }

    private fun cleanPath(raw: String?): String? {
        val path = if (raw.isNullOrEmpty() || raw == "/") "/index.html" else raw
        val relative = path.trimStart('/')
        if (relative.isEmpty()) return "index.html"
        val parts = relative.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." || it.contains('\\') }) return null
        return relative
    }
}
