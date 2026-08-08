package jp.oboegaki.core.domain

import jp.oboegaki.core.model.AppItem
import jp.oboegaki.core.model.AppSettings
import jp.oboegaki.core.model.ItemKind
import jp.oboegaki.core.model.TodoDetail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DeferSplitPolicyTest {
    @Test
    fun thirdDeferSuggestsSplit() {
        val item = todo(TodoDetail(deferCount = 2, nextSplitPromptAt = 3))
        val result = DeferPolicy.decide(item, 0, 8, DeferConfiguration.from(AppSettings()))
        assertEquals(3, result.updated.todo?.deferCount)
        assertTrue(result.shouldSuggestSplit)
        assertEquals(3, result.destinationIndex)
    }

    @Test
    fun disabledItemDoesNotSuggestSplit() {
        val item = todo(TodoDetail(deferCount = 2, nextSplitPromptAt = 3, splitPromptDisabled = true))
        assertFalse(DeferPolicy.decide(item, 0, 8, DeferConfiguration.from(AppSettings())).shouldSuggestSplit)
    }

    @Test
    fun configurationUsesDefaultItemsAndItemOverride() {
        val configuration = DeferConfiguration(defaultItems = 5, splitThreshold = 3, splitSuggestionEnabled = true)
        assertEquals(5, DeferPolicy.decide(todo(TodoDetail()), 0, 10, configuration).destinationIndex)
        assertEquals(5, DeferPolicy.decide(todo(TodoDetail(deferValue = null)), 0, 10, configuration).destinationIndex)
        assertEquals(2, DeferPolicy.decide(todo(TodoDetail(deferValue = 2)), 0, 10, configuration).destinationIndex)
    }

    @Test
    fun disabledSplitSuggestionIsHonoredByDomain() {
        val item = todo(TodoDetail(deferCount = 2, nextSplitPromptAt = 3))
        val configuration = DeferConfiguration(defaultItems = 3, splitThreshold = 3, splitSuggestionEnabled = false)
        assertFalse(DeferPolicy.decide(item, 0, 8, configuration).shouldSuggestSplit)
    }

    @Test
    fun splitRequiresTwoNonBlankItems() {
        assertIs<SplitValidation.Invalid>(SplitPolicy.validate(listOf("準備", "  ")))
        assertIs<SplitValidation.Valid>(SplitPolicy.validate(listOf("準備", "確認")))
    }

    @Test
    fun estimateIsDistributedWithRemainderFirst() {
        val children = SplitPolicy.buildChildren(
            todo(TodoDetail(estimatedMinutes = 10)), listOf("a", "b", "c"), 1,
        ) { "child-${counter++}" }
        assertEquals(listOf(4, 3, 3), children.map { it.todo?.estimatedMinutes })
    }

    private fun todo(detail: TodoDetail) = AppItem(
        id = "parent", kind = ItemKind.TODO, title = "parent", manualRank = 1_000,
        createdAtEpochMillis = 0, updatedAtEpochMillis = 0, todo = detail,
    )

    private companion object { var counter = 0 }
}
