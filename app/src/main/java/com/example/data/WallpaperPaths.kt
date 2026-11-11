package com.example.data

import android.content.Context
import java.io.File

/** Where wallpapers live on disk. One folder per wallpaper: `files/wallpapers/<id>/index.html` (+ assets). */
object WallpaperPaths {

    private val customId = Regex("^c[0-9a-f]{32}$")

    fun isCustomId(id: String): Boolean = customId.matches(id)

    fun root(context: Context): File = File(context.applicationContext.filesDir, "wallpapers")

    fun dir(context: Context, id: String): File = File(root(context), id)

    fun exists(context: Context, id: String): Boolean =
        Presets.byId(id) != null ||
            (isCustomId(id) && File(dir(context, id), "index.html").isFile)
}
