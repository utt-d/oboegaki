package jp.oboegaki.ui

import jp.oboegaki.core.model.MainNavigationButton
import kotlin.test.Test
import kotlin.test.assertEquals

class TabNavigationOrderTest {
    @Test
    fun swipeOrderMatchesTheVisibleNavigationButtonOrder() {
        assertEquals(
            listOf(MainTab.MEMOS, MainTab.TODOS, MainTab.ALL),
            navigationTabs(
                listOf(
                    MainNavigationButton.MEMOS,
                    MainNavigationButton.TODOS,
                    MainNavigationButton.ALL,
                ),
            ),
        )
    }

    @Test
    fun incompleteOrDuplicateSettingsAreNormalized() {
        assertEquals(
            listOf(MainTab.ALL, MainTab.TODOS, MainTab.MEMOS),
            navigationTabs(
                listOf(
                    MainNavigationButton.ALL,
                    MainNavigationButton.ALL,
                    MainNavigationButton.TODOS,
                ),
            ),
        )
    }
}
