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

    @Test
    fun configuredOrderResolvesBothAdjacentSwipeDirections() {
        val order = navigationTabs(
            listOf(
                MainNavigationButton.MEMOS,
                MainNavigationButton.TODOS,
                MainNavigationButton.ALL,
            ),
        )

        assertEquals(MainTab.TODOS, adjacentNavigationTab(MainTab.MEMOS, 1, order))
        assertEquals(MainTab.MEMOS, adjacentNavigationTab(MainTab.TODOS, -1, order))
        assertEquals(MainTab.ALL, adjacentNavigationTab(MainTab.TODOS, 1, order))
        assertEquals(MainTab.TODOS, adjacentNavigationTab(MainTab.ALL, -1, order))
    }

    @Test
    fun reversedConfiguredOrderPreservesBothAdjacentSwipeDirections() {
        val order = navigationTabs(
            listOf(
                MainNavigationButton.ALL,
                MainNavigationButton.TODOS,
                MainNavigationButton.MEMOS,
            ),
        )

        assertEquals(MainTab.TODOS, adjacentNavigationTab(MainTab.ALL, 1, order))
        assertEquals(MainTab.ALL, adjacentNavigationTab(MainTab.TODOS, -1, order))
        assertEquals(MainTab.MEMOS, adjacentNavigationTab(MainTab.TODOS, 1, order))
        assertEquals(MainTab.TODOS, adjacentNavigationTab(MainTab.MEMOS, -1, order))
    }
}
