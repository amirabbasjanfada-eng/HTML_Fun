package com.example

import com.example.data.AppliedState
import com.example.data.WallpaperScreen
import com.example.data.WallpaperStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WallpaperScreenTest {

  @Test
  fun statusLabelsMatchWhatTheLibraryShows() {
    assertEquals("Home Screen", WallpaperStatus.of(onHome = true, onLock = false).label)
    assertEquals("Lock Screen", WallpaperStatus.of(onHome = false, onLock = true).label)
    assertEquals("Both", WallpaperStatus.of(onHome = true, onLock = true).label)
    assertEquals("Neither", WallpaperStatus.of(onHome = false, onLock = false).label)
  }

  @Test
  fun twoDifferentWallpapersCanBeActiveAtTheSameTime() {
    val applied = AppliedState(WallpaperScreen.HOME, WallpaperScreen.LOCK)
    assertEquals("A", applied.shownOn(WallpaperScreen.HOME, homeId = "A", lockId = "B"))
    assertEquals("B", applied.shownOn(WallpaperScreen.LOCK, homeId = "A", lockId = "B"))
  }

  @Test
  fun changingOneSelectionNeverChangesWhatTheOtherScreenShows() {
    val applied = AppliedState(WallpaperScreen.HOME, WallpaperScreen.LOCK)
    for (newHome in listOf("A", "C", "D")) {
      assertEquals("B", applied.shownOn(WallpaperScreen.LOCK, homeId = newHome, lockId = "B"))
    }
    for (newLock in listOf("B", "C", "D")) {
      assertEquals("A", applied.shownOn(WallpaperScreen.HOME, homeId = "A", lockId = newLock))
    }
  }

  @Test
  fun aScreenWithoutOurLiveWallpaperShowsNothing() {
    val onlyHome = AppliedState(WallpaperScreen.HOME, null)
    assertEquals("A", onlyHome.shownOn(WallpaperScreen.HOME, homeId = "A", lockId = "B"))
    assertNull(onlyHome.shownOn(WallpaperScreen.LOCK, homeId = "A", lockId = "B"))
    assertNull(AppliedState.NONE.shownOn(WallpaperScreen.HOME, homeId = "A", lockId = "B"))
  }

  @Test
  fun oneSharedServiceShowsOneWallpaperOnBothScreens() {
    val shared = AppliedState(WallpaperScreen.HOME, WallpaperScreen.HOME)
    val onHome = shared.shownOn(WallpaperScreen.HOME, homeId = "A", lockId = "B")
    val onLock = shared.shownOn(WallpaperScreen.LOCK, homeId = "A", lockId = "B")
    assertEquals("A", onHome)
    assertEquals("A", onLock)
    assertEquals(WallpaperStatus.BOTH, WallpaperStatus.of(onHome == "A", onLock == "A"))
  }

  @Test
  fun theLockServiceSetOnTheHomeScreenShowsTheLockSelection() {
    val swapped = AppliedState(WallpaperScreen.LOCK, null)
    assertEquals("B", swapped.shownOn(WallpaperScreen.HOME, homeId = "A", lockId = "B"))
  }
}
