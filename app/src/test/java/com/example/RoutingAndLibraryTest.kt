package com.example

import com.example.data.Presets
import com.example.data.WallpaperPaths
import com.example.web.ContentRoutes
import com.example.web.Libraries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingAndLibraryTest {

  private val customId = "c" + "0123456789abcdef".repeat(2)

  @Test
  fun urlsRoundTripThroughTheParser() {
    val preset = ContentRoutes.parse("cosmic-nebula.webpaper.local", "/index.html")
    assertEquals(ContentRoutes.Target("cosmic-nebula", "index.html", false), preset)

    val custom = ContentRoutes.parse("$customId.webpaper.local", "/js/app.js")
    assertEquals(ContentRoutes.Target(customId, "js/app.js", false), custom)

    val draft = ContentRoutes.parse("d-$customId.webpaper.local", "/")
    assertEquals(ContentRoutes.Target(customId, "index.html", true), draft)

    assertTrue(ContentRoutes.urlFor(customId, 7L, draft = true).startsWith("https://d-$customId.webpaper.local/index.html"))
  }

  @Test
  fun foreignHostsAreLeftToTheNetwork() {
    assertNull(ContentRoutes.parse("cdn.jsdelivr.net", "/npm/three"))
    assertNull(ContentRoutes.parse(null, "/index.html"))
    assertNull(ContentRoutes.parse("webpaper.local.evil.com", "/index.html"))
  }

  @Test
  fun pathsCannotEscapeTheWallpaperFolder() {
    val host = "cosmic-nebula.webpaper.local"
    assertNull(ContentRoutes.parse(host, "/../secret")!!.path)
    assertNull(ContentRoutes.parse(host, "/a/../../b")!!.path)
    assertNull(ContentRoutes.parse(host, "/a//b")!!.path)
    assertNull(ContentRoutes.parse(host, "/a\\b")!!.path)
    assertNotNull(ContentRoutes.parse(host, "/a/b.css")!!.path)
  }

  @Test
  fun customIdsAreStrictlyValidated() {
    assertTrue(WallpaperPaths.isCustomId(customId))
    assertFalse(WallpaperPaths.isCustomId("../$customId"))
    assertFalse(WallpaperPaths.isCustomId(customId.uppercase()))
    assertFalse(WallpaperPaths.isCustomId("cosmic-nebula"))
    assertFalse(WallpaperPaths.isCustomId(""))
  }

  @Test
  fun thereIsExactlyOnePreset() {
    assertEquals(1, Presets.all.size)
    assertEquals(Presets.DEFAULT, Presets.byId("cosmic-nebula"))
    assertTrue(Presets.DEFAULT.isPreset)
  }

  @Test
  fun librariesInsertBeforeHeadClose_andNeverTwice() {
    val three = Libraries.all.first { it.name == "Three.js" }
    val once = Libraries.insert("<html><head></head><body></body></html>", three)
    assertTrue(once.indexOf("importmap") in 0 until once.indexOf("</head>"))
    assertEquals(once, Libraries.insert(once, three))
  }

  @Test
  fun librariesFallBackToTheTopWhenThereIsNoHead() {
    val gsap = Libraries.all.first { it.name == "GSAP" }
    assertTrue(Libraries.insert("<canvas></canvas>", gsap).startsWith("<script src="))
  }
}
