package com.example.util

import android.app.WallpaperInfo
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import com.example.data.AppliedState
import com.example.data.WallpaperScreen
import com.example.wallpaper.HtmlLockWallpaperService
import com.example.wallpaper.HtmlWallpaperService

object WallpaperManagerHelper {

    private fun serviceClass(screen: WallpaperScreen): Class<*> = when (screen) {
        WallpaperScreen.HOME -> HtmlWallpaperService::class.java
        WallpaperScreen.LOCK -> HtmlLockWallpaperService::class.java
    }

    /** Which of our two services [info] describes, or null if it is something else (or static). */
    private fun ourService(context: Context, info: WallpaperInfo?): WallpaperScreen? {
        if (info == null || info.packageName != context.packageName) return null
        return WallpaperScreen.entries.firstOrNull { serviceClass(it).name == info.serviceName }
    }

    /**
     * WallpaperManager.getWallpaperInfo(which), available on Android 14+. It is called by name, so the
     * project still compiles and runs on every SDK; if the call is unavailable or fails, nothing is
     * reported for that screen.
     */
    private fun infoFor(manager: WallpaperManager, which: Int): WallpaperInfo? = try {
        manager.javaClass.getMethod("getWallpaperInfo", Int::class.javaPrimitiveType)
            .invoke(manager, which) as? WallpaperInfo
    } catch (e: Exception) {
        null
    }

    /**
     * Asks the system which of our live wallpapers is set on the Home screen and on the Lock screen.
     * Android 14+ reports the two screens separately. Before that a live wallpaper is shown on both
     * screens, so the one system wallpaper is reported for both.
     */
    fun appliedState(context: Context): AppliedState {
        return try {
            val manager = WallpaperManager.getInstance(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                AppliedState(
                    ourService(context, infoFor(manager, WallpaperManager.FLAG_SYSTEM)),
                    ourService(context, infoFor(manager, WallpaperManager.FLAG_LOCK))
                )
            } else {
                val service = ourService(context, manager.wallpaperInfo)
                AppliedState(service, service)
            }
        } catch (e: Exception) {
            AppliedState.NONE
        }
    }

    /**
     * Opens the system's live wallpaper preview for the service that belongs to [screen]. The user
     * confirms there (Android may ask Home screen / Lock screen / both again).
     */
    fun launchLiveWallpaperChooser(context: Context, screen: WallpaperScreen): Boolean {
        val component = ComponentName(context, serviceClass(screen))
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            try {
                val fallback = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                true
            } catch (ex: Exception) {
                Toast.makeText(context, "Could not open the live wallpaper picker", Toast.LENGTH_SHORT).show()
                false
            }
        }
    }
}
