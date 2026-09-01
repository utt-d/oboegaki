package jp.oboegaki.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TabTransitionStateTest {
    private val order = listOf(MainTab.TODOS, MainTab.MEMOS, MainTab.ALL)

    @Test
    fun commitRebasesAnchorAndOffsetAsOneState() {
        val dragging = TabTransitionRenderState(MainTab.TODOS)
            .beginDrag(MainTab.TODOS)!!
        val settling = dragging
            .updateDrag(MainTab.TODOS, dragging.generation, -120f)!!
            .beginSettle(MainTab.TODOS, dragging.generation, MainTab.MEMOS)!!
        val committed = settling.commit(MainTab.TODOS, settling.generation, MainTab.MEMOS)!!

        assertEquals(MainTab.MEMOS, committed.anchorTab)
        assertEquals(0f, committed.offsetPx)
        assertEquals(TabTransitionPhase.IDLE, committed.phase)
        assertEquals(0, tabPageOffset(1, order, committed, 400))
        assertEquals(400, tabPageOffset(2, order, committed, 400))
    }

    @Test
    fun staleOrDuplicateCommitCannotChangeRenderAnchor() {
        val settling = TabTransitionRenderState(MainTab.MEMOS)
            .beginDrag(MainTab.MEMOS)!!
            .beginSettle(MainTab.MEMOS, 1, MainTab.TODOS)!!

        val committed = settling.commit(MainTab.MEMOS, 1, MainTab.TODOS)!!

        assertNull(committed.commit(MainTab.MEMOS, 1, MainTab.TODOS))
        assertNull(settling.commit(MainTab.MEMOS, settling.generation + 1, MainTab.TODOS))
        assertEquals(MainTab.TODOS, committed.anchorTab)
        assertEquals(0f, committed.offsetPx)
    }

    @Test
    fun customOrderStillUsesOneAdjacentPagePerSwipe() {
        val customOrder = listOf(MainTab.ALL, MainTab.TODOS, MainTab.MEMOS)
        val source = TabTransitionRenderState(MainTab.ALL)
            .beginDrag(MainTab.ALL)!!
        val target = adjacentNavigationTab(MainTab.ALL, 1, customOrder)!!
        val committed = source
            .beginSettle(MainTab.ALL, source.generation, target)!!
            .commit(MainTab.ALL, source.generation, target)!!

        assertEquals(MainTab.TODOS, committed.anchorTab)
        assertEquals(0, tabPageOffset(1, customOrder, committed, 400))
        assertEquals(MainTab.MEMOS, adjacentNavigationTab(committed.anchorTab, 1, customOrder))
    }
}
