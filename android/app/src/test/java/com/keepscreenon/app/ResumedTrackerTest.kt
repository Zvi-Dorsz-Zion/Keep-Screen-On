package com.keepscreenon.app

import com.keepscreenon.app.ResumedTracker.Companion.PAUSED
import com.keepscreenon.app.ResumedTracker.Companion.RESUMED
import com.keepscreenon.app.ResumedTracker.Companion.STOPPED
import org.junit.Assert.assertEquals
import org.junit.Test

class ResumedTrackerTest {
    private val tracker = ResumedTracker().apply { homePackages = setOf("home") }

    @Test
    fun appIsVisibleUntilPaused() {
        tracker.onEvent("a", "A", RESUMED)
        assertEquals(setOf("a"), tracker.visiblePackages())
        tracker.onEvent("a", "A", PAUSED)
        assertEquals(emptySet<String>(), tracker.visiblePackages())
    }

    @Test
    fun movingBetweenActivitiesOfSameApp() {
        tracker.onEvent("a", "A1", RESUMED)
        tracker.onEvent("a", "A1", PAUSED)
        tracker.onEvent("a", "A2", RESUMED)
        tracker.onEvent("a", "A1", STOPPED)
        assertEquals(setOf("a"), tracker.visiblePackages())
    }

    @Test
    fun splitScreenKeepsBoth() {
        tracker.onEvent("a", "A", RESUMED)
        tracker.onEvent("b", "B", RESUMED)
        assertEquals(setOf("a", "b"), tracker.visiblePackages())
        tracker.onEvent("b", "B", PAUSED)
        assertEquals(setOf("a"), tracker.visiblePackages())
    }

    @Test
    fun homeClearsLeftoversOfCrashedApp() {
        tracker.onEvent("a", "A", RESUMED) // קרסה בלי אירוע השהיה
        tracker.onEvent("home", "H", RESUMED)
        assertEquals(setOf("home"), tracker.visiblePackages())
    }

    @Test
    fun replayingEventsGivesSameResult() {
        val events = listOf(
            Triple("a", "A", PAUSED),
            Triple("b", "B", RESUMED),
        )
        tracker.onEvent("a", "A", RESUMED)
        repeat(2) { events.forEach { (p, c, t) -> tracker.onEvent(p, c, t) } }
        assertEquals(setOf("b"), tracker.visiblePackages())
    }

    @Test
    fun nullClassNameIsHandled() {
        tracker.onEvent("a", null, RESUMED)
        tracker.onEvent("a", null, PAUSED)
        assertEquals(emptySet<String>(), tracker.visiblePackages())
    }
}
