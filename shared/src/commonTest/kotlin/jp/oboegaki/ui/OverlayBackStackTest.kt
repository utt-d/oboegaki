package jp.oboegaki.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OverlayBackStackTest {
    @Test
    fun backReturnsToEachPreviousOverlay() {
        val history = OverlayBackStack()
        var current: AppOverlay? = history.open(null, AppOverlay.Settings)
        current = history.open(current, AppOverlay.Themes)
        current = history.open(current, AppOverlay.ThemeEditor(jp.oboegaki.core.data.BuiltInThemes.standard))

        current = history.back()
        assertEquals(AppOverlay.Themes, current)
        current = history.back()
        assertEquals(AppOverlay.Settings, current)
        assertNull(history.back())
    }

    @Test
    fun openingSameOverlayDoesNotAddDuplicateHistory() {
        val history = OverlayBackStack()
        var current: AppOverlay? = history.open(null, AppOverlay.Settings)
        current = history.open(current, AppOverlay.Settings)

        assertNull(history.back())
    }

    @Test
    fun clearRemovesPreviousScreens() {
        val history = OverlayBackStack()
        val current = history.open(null, AppOverlay.Settings)
        history.open(current, AppOverlay.Themes)

        history.clear()

        assertNull(history.back())
    }
}
