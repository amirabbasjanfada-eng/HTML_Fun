package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppSettings
import com.example.data.Presets
import com.example.data.AppliedState
import com.example.data.ThemeMode
import com.example.data.Wallpaper
import com.example.data.WallpaperScreen
import com.example.data.WallpaperStatus
import com.example.data.WallpaperStore
import com.example.util.WallpaperManagerHelper
import com.example.web.ContentServer
import com.example.web.Libraries
import com.example.web.Library
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The three pages of the app. */
enum class AppScreen { HOME, CODE, SETTINGS }

/**
 * All state and logic of the app. Composables only read state and call these functions, and all
 * disk work happens inside [WallpaperStore] off the main thread.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Application = application
    val settings = AppSettings(application)
    private val store = WallpaperStore(application, settings)
    val server = ContentServer(application)

    val wallpapers: StateFlow<List<Wallpaper>> = store.items

    // --- what is selected / being edited -------------------------------------------------------
    var selectedId by mutableStateOf(Presets.DEFAULT.id)
        private set
    var title by mutableStateOf("")
    var code by mutableStateOf("")
    private var savedTitle by mutableStateOf("")
    private var savedCode by mutableStateOf("")
    var previewUrl by mutableStateOf("")
        private set

    val isDirty: Boolean get() = title != savedTitle || code != savedCode

    // --- what each screen shows -------------------------------------------------------------------
    // Home and Lock are independent: separate selections, separate live wallpaper services.
    var homeId by mutableStateOf(settings.idFor(WallpaperScreen.HOME))
        private set
    var lockId by mutableStateOf(settings.idFor(WallpaperScreen.LOCK))
        private set

    /** Which of our two live wallpapers the system has set on each screen right now. */
    var applied by mutableStateOf(AppliedState.NONE)
        private set

    /** The wallpaper really shown on [screen], or null if our live wallpaper is not set there. */
    fun shownOn(screen: WallpaperScreen): String? = applied.shownOn(screen, homeId, lockId)

    /** What the Library shows next to a wallpaper: Home Screen, Lock Screen, Both or Neither. */
    fun statusOf(id: String): WallpaperStatus = WallpaperStatus.of(
        onHome = shownOn(WallpaperScreen.HOME) == id,
        onLock = shownOn(WallpaperScreen.LOCK) == id
    )

    // --- settings ----------------------------------------------------------------------------------
    var fps by mutableIntStateOf(settings.fps)
        private set
    var touchEnabled by mutableStateOf(settings.touchEnabled)
        private set
    var gpuRendering by mutableStateOf(settings.gpuRendering)
        private set
    var themeMode by mutableStateOf(settings.themeMode)
        private set

    // --- navigation / transient UI -----------------------------------------------------------------
    var screen by mutableStateOf(AppScreen.HOME)
        private set
    var showDestination by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    val console = mutableStateListOf<String>()

    private var nonce = 0
    private var draftMode = false

    init {
        refreshLive()
        viewModelScope.launch {
            store.load()
            syncIds()
            // Start with the wallpaper that is on the Home screen selected.
            val start = homeId.takeIf { store.contains(it) } ?: Presets.DEFAULT.id
            loadSelection(start)
        }
    }

    fun navigate(to: AppScreen) {
        screen = to
    }

    /** Re-reads what each screen shows and what the system has applied. Cheap; runs on every resume. */
    fun refreshLive() {
        syncIds()
        applied = WallpaperManagerHelper.appliedState(appContext)
        // The system can take a moment to report a wallpaper that was just applied: look once more.
        viewModelScope.launch {
            delay(1000)
            applied = WallpaperManagerHelper.appliedState(appContext)
        }
    }

    private fun syncIds() {
        homeId = shownId(WallpaperScreen.HOME)
        lockId = shownId(WallpaperScreen.LOCK)
    }

    /** Same fallback as the live wallpaper engine: a selection that no longer exists shows the default. */
    private fun shownId(screen: WallpaperScreen): String =
        settings.idFor(screen).takeIf { store.contains(it) } ?: Presets.DEFAULT.id

    // --- library ---------------------------------------------------------------------------------------

    fun select(id: String) {
        if (id == selectedId) return
        viewModelScope.launch { loadSelection(id) }
    }

    private suspend fun loadSelection(id: String) {
        val item = wallpapers.value.firstOrNull { it.id == id } ?: return
        selectedId = id
        draftMode = false
        server.clearDrafts()
        val text = store.readCode(id)
        if (selectedId != id) return // another selection won the race while the file was loading
        title = item.title
        code = text
        savedTitle = item.title
        savedCode = text
        console.clear()
        nonce += 1
        previewUrl = server.url(id, nonce.toLong())
    }

    fun create() {
        viewModelScope.launch {
            val item = store.create("Untitled", store.starterCode())
            loadSelection(item.id)
            message = "New wallpaper created"
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            store.delete(id)
            // A screen that was showing the deleted wallpaper falls back to the default; the other
            // screen is left alone.
            for (screen in WallpaperScreen.entries) {
                if (settings.idFor(screen) == id) settings.setIdFor(screen, Presets.DEFAULT.id)
            }
            syncIds()
            if (selectedId == id) loadSelection(Presets.DEFAULT.id)
        }
    }

    fun importUri(uri: Uri) {
        viewModelScope.launch {
            val result = store.importUri(uri)
            val imported = result.getOrNull()
            if (imported != null) {
                loadSelection(imported.id)
                message = "Imported " + imported.title
            } else {
                message = "Import failed: " + (result.exceptionOrNull()?.message ?: "unknown error")
            }
        }
    }

    // --- editor / preview ------------------------------------------------------------------------------

    /** Shows the editor's current (unsaved) code in the preview. */
    fun runDraft() {
        server.setDraft(selectedId, code)
        draftMode = true
        console.clear()
        nonce += 1
        previewUrl = server.url(selectedId, nonce.toLong(), draft = true)
    }

    fun reload() {
        console.clear()
        nonce += 1
        previewUrl = server.url(selectedId, nonce.toLong(), draft = draftMode)
    }

    fun save() {
        viewModelScope.launch { persist() }
    }

    private suspend fun persist() {
        val item = wallpapers.value.firstOrNull { it.id == selectedId } ?: return
        val newTitle = title.trim().ifBlank { item.title }
        val newCode = code
        if (item.isPreset) {
            // Presets are read-only, so saving makes an editable copy.
            val copyTitle = if (newTitle == item.title) "$newTitle copy" else newTitle
            val copy = store.create(copyTitle, newCode)
            loadSelection(copy.id)
            message = "Saved as a copy"
            return
        }
        store.save(item.id, newTitle, newCode)
        title = newTitle
        savedTitle = newTitle
        savedCode = newCode
        draftMode = false
        server.setDraft(item.id, null)
        nonce += 1
        previewUrl = server.url(item.id, nonce.toLong())
        // Reload only the screens that are showing this wallpaper.
        for (screen in WallpaperScreen.entries) {
            if (settings.idFor(screen) == item.id) settings.notifyContentChanged(screen)
        }
        message = "Saved"
    }

    fun addLibrary(library: Library) {
        val updated = Libraries.insert(code, library)
        if (updated == code) message = library.name + " is already included" else code = updated
    }

    fun log(line: String) {
        console.add(line)
        if (console.size > 100) console.removeAt(0)
    }

    // --- applying ----------------------------------------------------------------------------------------------

    /**
     * Makes the selected wallpaper the live wallpaper of [target] only. The other screen's selection
     * is never touched. Unsaved edits are saved first. If our live wallpaper for that screen is
     * already set, the running engine switches instantly; otherwise [openChooser] opens the system
     * picker for that screen's service.
     */
    fun applyTo(target: WallpaperScreen, openChooser: (WallpaperScreen) -> Unit) {
        showDestination = false
        viewModelScope.launch {
            val item = wallpapers.value.firstOrNull { it.id == selectedId } ?: return@launch
            if (!item.isPreset && isDirty) persist()
            settings.setIdFor(target, item.id)
            syncIds()
            refreshLive()
            val name = if (target == WallpaperScreen.HOME) "Home screen" else "Lock screen"
            if (applied.serviceOn(target) == target) {
                message = "$name updated"
            } else {
                message = "Choose $name in the next step"
                openChooser(target)
            }
        }
    }

    fun updateFps(value: Int) {
        fps = value
        settings.fps = value
    }

    fun updateTouch(value: Boolean) {
        touchEnabled = value
        settings.touchEnabled = value
    }

    fun updateGpu(value: Boolean) {
        gpuRendering = value
        settings.gpuRendering = value
    }

    fun updateTheme(value: ThemeMode) {
        themeMode = value
        settings.themeMode = value
    }
}
