package com.example.data

/** Metadata only. The (possibly large) source code is read on demand, never kept in the list. */
data class Wallpaper(
    val id: String,
    val title: String,
    val isPreset: Boolean,
    val updatedAt: Long = 0L
)

/** Wallpapers that ship inside the APK as `assets/presets/<id>/index.html`. */
object Presets {
    val DEFAULT = Wallpaper(id = "cosmic-nebula", title = "Cosmic Nebula", isPreset = true)

    val all: List<Wallpaper> = listOf(DEFAULT)

    fun byId(id: String): Wallpaper? = all.firstOrNull { it.id == id }
}
