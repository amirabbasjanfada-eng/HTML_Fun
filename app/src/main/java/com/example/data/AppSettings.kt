package com.example.data

import android.content.Context
import android.content.SharedPreferences

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * All user settings. Backed by SharedPreferences so the wallpaper service (same process) can listen
 * for changes and react immediately without restarting the page.
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * The wallpaper shown on each screen. Home and Lock are completely independent: each has its own
     * id and its own content version, and is read by its own live wallpaper service, so changing one
     * can never change the other. Until a screen is set explicitly it keeps showing the single
     * "active" wallpaper of older versions.
     */
    fun idFor(screen: WallpaperScreen): String =
        prefs.getString(idKey(screen), null) ?: legacyActiveId ?: Presets.DEFAULT.id

    fun setIdFor(screen: WallpaperScreen, id: String) {
        prefs.edit().putString(idKey(screen), id).apply()
    }

    fun versionFor(screen: WallpaperScreen): Long = prefs.getLong(versionKey(screen), 0L)

    /** Tells the engine of [screen] that its wallpaper's code changed, so it reloads the page. */
    fun notifyContentChanged(screen: WallpaperScreen) {
        prefs.edit().putLong(versionKey(screen), versionFor(screen) + 1).apply()
    }

    /** The single "active wallpaper" from before Home and Lock were separate. Only a fallback now. */
    var legacyActiveId: String?
        get() = prefs.getString(KEY_ACTIVE_ID, null)
        set(value) {
            prefs.edit().putString(KEY_ACTIVE_ID, value).apply()
        }

    /** Frame-rate cap in fps. 0 means "no cap": follow the display. */
    var fps: Int
        get() = prefs.getInt(KEY_FPS, 60)
        set(value) {
            prefs.edit().putInt(KEY_FPS, value).apply()
        }

    var touchEnabled: Boolean
        get() = prefs.getBoolean(KEY_TOUCH, true)
        set(value) {
            prefs.edit().putBoolean(KEY_TOUCH, value).apply()
        }

    /** Hardware-accelerated rendering (needed for WebGL). Falls back to CPU rendering on failure. */
    var gpuRendering: Boolean
        get() = prefs.getBoolean(KEY_GPU, true)
        set(value) {
            prefs.edit().putBoolean(KEY_GPU, value).apply()
        }

    var themeMode: ThemeMode
        get() = ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) }
            ?: ThemeMode.SYSTEM
        set(value) {
            prefs.edit().putString(KEY_THEME, value.name).apply()
        }

    var legacyMigrated: Boolean
        get() = prefs.getBoolean(KEY_LEGACY_MIGRATED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_LEGACY_MIGRATED, value).apply()
        }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        const val PREFS_NAME = "webpaper_prefs"
        const val KEY_ACTIVE_ID = "key_active_wallpaper_id" // legacy, see legacyActiveId
        const val KEY_HOME_ID = "key_home_wallpaper_id"
        const val KEY_LOCK_ID = "key_lock_wallpaper_id"
        const val KEY_HOME_VERSION = "key_home_version"
        const val KEY_LOCK_VERSION = "key_lock_version"
        const val KEY_FPS = "key_global_fps"
        const val KEY_TOUCH = "key_touch_enabled"
        const val KEY_GPU = "key_gpu_rendering"
        const val KEY_THEME = "key_theme_mode"
        const val KEY_LEGACY_MIGRATED = "key_legacy_migrated"

        fun idKey(screen: WallpaperScreen): String =
            if (screen == WallpaperScreen.HOME) KEY_HOME_ID else KEY_LOCK_ID

        fun versionKey(screen: WallpaperScreen): String =
            if (screen == WallpaperScreen.HOME) KEY_HOME_VERSION else KEY_LOCK_VERSION
    }
}
