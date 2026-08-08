package jp.oboegaki.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsTest {
    @Test
    fun addButtonDefaultsToLeft() {
        val settings = AppSettings()

        assertEquals(AddButtonPosition.LEFT, settings.addButtonPosition)
        assertEquals(8, settings.addButtonBottomOffsetDp)
        assertEquals(
            listOf(MainNavigationButton.TODOS, MainNavigationButton.MEMOS, MainNavigationButton.ALL),
            settings.navigationButtonOrder,
        )
        assertEquals(
            listOf(TopActionButton.THEMES, TopActionButton.SETTINGS),
            settings.topActionButtonOrder,
        )
        assertEquals(false, settings.showReminderContentOnLockScreen)
        assertEquals(true, settings.reminderNotificationActionsEnabled)
    }
}
