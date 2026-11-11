package com.example

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.WebResourceRequest
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppSettings
import com.example.data.Presets
import com.example.data.ThemeMode
import com.example.data.WallpaperScreen
import com.example.data.WallpaperStore
import com.example.wallpaper.HtmlLockWallpaperService
import com.example.wallpaper.HtmlWallpaperService
import com.example.web.ContentServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebPaperRobolectricTest {

  private val context: Context get() = ApplicationProvider.getApplicationContext<Context>()

  private class FakeRequest(url: String) : WebResourceRequest {
    private val uri: Uri = Uri.parse(url)
    override fun getUrl(): Uri = uri
    override fun isForMainFrame(): Boolean = true
    override fun isRedirect(): Boolean = false
    override fun hasGesture(): Boolean = false
    override fun getMethod(): String = "GET"
    override fun getRequestHeaders(): MutableMap<String, String> = HashMap()
  }

  private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { zip ->
      for ((name, text) in entries) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray())
        zip.closeEntry()
      }
    }
    return bytes.toByteArray()
  }

  @Test
  fun settingsHaveSensibleDefaults() {
    val settings = AppSettings(context)
    assertEquals(Presets.DEFAULT.id, settings.idFor(WallpaperScreen.HOME))
    assertEquals(Presets.DEFAULT.id, settings.idFor(WallpaperScreen.LOCK))
    assertEquals(60, settings.fps)
    assertTrue(settings.touchEnabled)
    assertTrue(settings.gpuRendering)
    assertEquals(ThemeMode.SYSTEM, settings.themeMode)
  }

  @Test
  fun homeAndLockSelectionsAreIndependent() {
    val settings = AppSettings(context)
    settings.setIdFor(WallpaperScreen.HOME, "a")
    assertEquals("a", settings.idFor(WallpaperScreen.HOME))
    assertEquals("Lock must not follow Home", Presets.DEFAULT.id, settings.idFor(WallpaperScreen.LOCK))

    settings.setIdFor(WallpaperScreen.LOCK, "b")
    assertEquals("a", settings.idFor(WallpaperScreen.HOME))
    assertEquals("b", settings.idFor(WallpaperScreen.LOCK))

    settings.setIdFor(WallpaperScreen.HOME, "c")
    assertEquals("Changing Home must not change Lock", "b", settings.idFor(WallpaperScreen.LOCK))
    settings.setIdFor(WallpaperScreen.LOCK, "d")
    assertEquals("Changing Lock must not change Home", "c", settings.idFor(WallpaperScreen.HOME))
  }

  @Test
  fun contentVersionsAreIndependentPerScreen() {
    val settings = AppSettings(context)
    settings.notifyContentChanged(WallpaperScreen.HOME)
    settings.notifyContentChanged(WallpaperScreen.HOME)
    settings.notifyContentChanged(WallpaperScreen.LOCK)
    assertEquals(2L, settings.versionFor(WallpaperScreen.HOME))
    assertEquals(1L, settings.versionFor(WallpaperScreen.LOCK))
  }

  @Test
  fun theOlderSingleActiveWallpaperSeedsBothScreensUntilTheyAreSetSeparately() {
    val settings = AppSettings(context)
    settings.legacyActiveId = "old"
    assertEquals("old", settings.idFor(WallpaperScreen.HOME))
    assertEquals("old", settings.idFor(WallpaperScreen.LOCK))
    settings.setIdFor(WallpaperScreen.HOME, "new")
    assertEquals("new", settings.idFor(WallpaperScreen.HOME))
    assertEquals("old", settings.idFor(WallpaperScreen.LOCK))
  }

  @Test
  fun eachScreenListensOnlyToItsOwnPreferenceKeys() {
    assertNotEquals(AppSettings.idKey(WallpaperScreen.HOME), AppSettings.idKey(WallpaperScreen.LOCK))
    assertNotEquals(AppSettings.versionKey(WallpaperScreen.HOME), AppSettings.versionKey(WallpaperScreen.LOCK))
    val homeKeys = setOf(AppSettings.idKey(WallpaperScreen.HOME), AppSettings.versionKey(WallpaperScreen.HOME))
    val lockKeys = setOf(AppSettings.idKey(WallpaperScreen.LOCK), AppSettings.versionKey(WallpaperScreen.LOCK))
    assertTrue("No shared key", homeKeys.intersect(lockKeys).isEmpty())
  }

  @Test
  fun bothLiveWallpaperServicesAreDeclared() {
    for (service in listOf(HtmlWallpaperService::class.java, HtmlLockWallpaperService::class.java)) {
      val info = context.packageManager.getServiceInfo(ComponentName(context, service), PackageManager.GET_META_DATA)
      assertEquals("android.permission.BIND_WALLPAPER", info.permission)
      assertTrue(info.exported)
      assertTrue(info.metaData.containsKey("android.service.wallpaper"))
    }
  }

  @Test
  fun presetAndStarterAssetsExist() {
    val preset = context.assets.open("presets/${Presets.DEFAULT.id}/index.html")
      .use { it.readBytes().toString(Charsets.UTF_8) }
    assertTrue(preset.contains("<canvas"))
    val starter = context.assets.open("templates/starter.html")
      .use { it.readBytes().toString(Charsets.UTF_8) }
    assertTrue(starter.contains("<script type=\"module\">"))
  }

  @Test
  fun serverServesThePresetThroughTheHelper() {
    val response = ContentServer(context).intercept(FakeRequest("https://cosmic-nebula.webpaper.local/index.html"))
    assertNotNull(response)
    assertEquals(200, response!!.statusCode)
    assertEquals("text/html", response.mimeType)
    val body = response.data.readBytes().toString(Charsets.UTF_8)
    assertTrue(body.contains("webpaper-injected-script"))
    assertTrue(body.contains("<canvas"))
    assertFalse("presets handle touch themselves", body.contains("forwardTouchToMouse"))
  }

  @Test
  fun serverLeavesForeignHostsAlone_andRejectsTraversal() {
    val server = ContentServer(context)
    assertNull(server.intercept(FakeRequest("https://cdn.jsdelivr.net/npm/three@latest/build/three.module.js")))
    assertEquals(404, server.intercept(FakeRequest("https://cosmic-nebula.webpaper.local/../secret"))!!.statusCode)
    assertEquals(404, server.intercept(FakeRequest("https://unknown.webpaper.local/index.html"))!!.statusCode)
  }

  @Test
  fun storeCreatesSavesPersistsAndDeletes() {
    runBlocking {
      val settings = AppSettings(context)
      val store = WallpaperStore(context, settings)
      store.load()

      val created = store.create("My wallpaper", "<h1>one</h1>")
      assertTrue(store.contains(created.id))
      assertEquals("<h1>one</h1>", store.readCode(created.id))

      store.save(created.id, "Renamed", "<h1>two</h1>")
      assertEquals("<h1>two</h1>", store.readCode(created.id))
      assertEquals("Renamed", store.items.value.first { it.id == created.id }.title)

      // A new store instance (as after an app restart) sees the same data.
      val reopened = WallpaperStore(context, settings)
      reopened.load()
      assertTrue(reopened.contains(created.id))

      // Draft edits are served on their own origin and never replace the saved code.
      val server = ContentServer(context)
      server.setDraft(created.id, "<h1>draft</h1>")
      val draft = server.intercept(FakeRequest(server.url(created.id, 1L, draft = true)))!!
      assertTrue(draft.data.readBytes().toString(Charsets.UTF_8).contains("draft"))
      val saved = server.intercept(FakeRequest(server.url(created.id, 1L)))!!
      assertTrue(saved.data.readBytes().toString(Charsets.UTF_8).contains("two"))

      store.delete(created.id)
      assertFalse(store.contains(created.id))
    }
  }

  @Test
  fun zipProjectsAreImportedFlattenedAndServedWithTheirAssets() {
    runBlocking {
      val store = WallpaperStore(context, AppSettings(context))
      store.load()
      val uri = Uri.parse("content://webpaper.test/project.zip")
      shadowOf(context.contentResolver).registerInputStream(
        uri,
        ByteArrayInputStream(
          zipOf(
            "site/index.html" to "<html><head></head><body>hi</body></html>",
            "site/app.js" to "console.log(1)"
          )
        )
      )

      val result = store.importUri(uri)
      assertTrue(result.isSuccess)
      val item = result.getOrThrow()

      val js = ContentServer(context).intercept(FakeRequest("https://${item.id}.webpaper.local/app.js"))
      assertNotNull(js)
      assertEquals(200, js!!.statusCode)
      assertEquals("text/javascript", js.mimeType)
    }
  }

  @Test
  fun zipPathTraversalIsRejectedAndLeavesNothingBehind() {
    runBlocking {
      val store = WallpaperStore(context, AppSettings(context))
      store.load()
      val uri = Uri.parse("content://webpaper.test/evil.zip")
      shadowOf(context.contentResolver).registerInputStream(
        uri,
        ByteArrayInputStream(zipOf("../evil.html" to "x", "index.html" to "<html></html>"))
      )

      assertTrue(store.importUri(uri).isFailure)
      assertEquals(0, store.items.value.count { !it.isPreset })
    }
  }
}
