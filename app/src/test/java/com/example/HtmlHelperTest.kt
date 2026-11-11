package com.example

import com.example.web.HtmlHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlHelperTest {

  private fun count(haystack: String, needle: String): Int = haystack.split(needle).size - 1

  @Test
  fun wrapsSnippetsIntoACompleteDocument() {
    val prepared = HtmlHelper.prepareHtml("<div>Interactive Canvas</div>")
    assertTrue(prepared.contains("<!DOCTYPE html>"))
    assertTrue(prepared.contains("viewport-fit=cover"))
    assertTrue(prepared.contains("touch-action: none"))
  }

  @Test
  fun isIdempotent() {
    val once = HtmlHelper.prepareHtml("<div>hello</div>")
    val twice = HtmlHelper.prepareHtml(once)
    assertEquals(once, twice)
    assertEquals(1, count(twice, "webpaper-injected-script"))
    assertEquals(1, count(twice, "webpaper-injected-styles"))
  }

  @Test
  fun helpersRunBeforeAnyPageScript() {
    val prepared = HtmlHelper.prepareHtml(
      "<html><head><script>window.mine = 1</script></head><body></body></html>"
    )
    assertTrue(prepared.indexOf("webpaper-injected-script") < prepared.indexOf("window.mine"))
  }

  @Test
  fun doesNotDuplicateTheViewportMeta() {
    val html = "<html><head><meta content=\"width=device-width\" name=\"viewport\"></head><body></body></html>"
    assertEquals(1, count(HtmlHelper.prepareHtml(html), "name=\"viewport\""))
  }

  @Test
  fun handlesHeadTagsWithAttributesAndMissingHeads() {
    val withAttributes = HtmlHelper.prepareHtml("<html lang=\"en\"><head lang=\"en\"></head><body></body></html>")
    assertTrue(withAttributes.contains("viewport-fit=cover"))
    val withoutHead = HtmlHelper.prepareHtml("<html><body>no head</body></html>")
    assertTrue(withoutHead.contains("viewport-fit=cover"))
    assertEquals(1, count(withoutHead, "webpaper-injected-script"))
  }

  @Test
  fun stripInjectedGivesBackTheUsersCode() {
    val prepared = HtmlHelper.prepareHtml("<html><head><style>b{}</style></head><body></body></html>")
    val stripped = HtmlHelper.stripInjected(prepared)
    assertFalse(stripped.contains("webpaper-injected"))
    assertTrue(stripped.contains("<style>b{}</style>"))
  }

  @Test
  fun collapsesWallpapersThatWereInjectedTwiceByOlderVersions() {
    val once = HtmlHelper.prepareHtml("<html><head></head><body></body></html>")
    val injected = once.substringAfter("<head>").substringBefore("</head>")
    val corrupted = once.replace("</head>", injected + "</head>")
    assertEquals(2, count(corrupted, "webpaper-injected-script"))
    val fixed = HtmlHelper.prepareHtml(corrupted)
    assertEquals(1, count(fixed, "webpaper-injected-script"))
    assertEquals(1, count(fixed, "webpaper-injected-styles"))
  }

  @Test
  fun touchBridgeCanBeSwitchedOff() {
    val with = HtmlHelper.prepareHtml("<div></div>", touchPolyfill = true)
    val without = HtmlHelper.prepareHtml(with, touchPolyfill = false)
    assertTrue(with.contains("forwardTouchToMouse"))
    assertFalse(without.contains("forwardTouchToMouse"))
    assertEquals(1, count(without, "webpaper-injected-script"))
  }

  @Test
  fun embedsTheFrameRateCap() {
    assertTrue(HtmlHelper.prepareHtml("<p>x</p>", fps = 30).contains("__webpaperFps = 30;"))
    assertTrue(HtmlHelper.prepareHtml("<p>x</p>", fps = 0).contains("__webpaperFps = 0;"))
  }
}
