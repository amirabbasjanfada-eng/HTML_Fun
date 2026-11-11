package com.example.data

/** The two places a live wallpaper can be shown. They are fully independent of each other. */
enum class WallpaperScreen { HOME, LOCK }

/** What the Library shows next to a wallpaper. */
enum class WallpaperStatus(val label: String) {
    HOME("Home Screen"),
    LOCK("Lock Screen"),
    BOTH("Both"),
    NEITHER("Neither");

    companion object {
        fun of(onHome: Boolean, onLock: Boolean): WallpaperStatus = when {
            onHome && onLock -> BOTH
            onHome -> HOME
            onLock -> LOCK
            else -> NEITHER
        }
    }
}

/**
 * Which of our two live wallpaper services is currently set on each screen, as reported by the
 * system. `HOME` means "the Home service", `LOCK` means "the Lock service", null means "not ours".
 */
data class AppliedState(val onHomeScreen: WallpaperScreen?, val onLockScreen: WallpaperScreen?) {

    fun serviceOn(screen: WallpaperScreen): WallpaperScreen? =
        if (screen == WallpaperScreen.HOME) onHomeScreen else onLockScreen

    /**
     * The wallpaper really shown on [screen]: the Home service shows the Home selection, the Lock
     * service shows the Lock selection. Null when none of our live wallpapers is set there.
     */
    fun shownOn(screen: WallpaperScreen, homeId: String, lockId: String): String? =
        when (serviceOn(screen)) {
            WallpaperScreen.HOME -> homeId
            WallpaperScreen.LOCK -> lockId
            null -> null
        }

    companion object {
        val NONE = AppliedState(null, null)
    }
}
