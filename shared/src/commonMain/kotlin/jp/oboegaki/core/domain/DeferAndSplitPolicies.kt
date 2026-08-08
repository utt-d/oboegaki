package jp.oboegaki.core.domain

import jp.oboegaki.core.model.AppItem
import jp.oboegaki.core.model.ItemKind
import jp.oboegaki.core.model.ItemLifecycle
import jp.oboegaki.core.model.TodoDetail

data class DeferDecision(
    val updated: AppItem,
    val destinationIndex: Int,
    val shouldSuggestSplit: Boolean,
)

object DeferPolicy {
    fun decide(item: AppItem, currentIndex: Int, itemCount: Int, threshold: Int): DeferDecision {
        val detail = requireNotNull(item.todo)
        val count = detail.deferCount + 1
        val shouldPrompt = !detail.splitPromptDisabled && count >= detail.nextSplitPromptAt
        val offset = (detail.deferValue ?: 3).coerceAtLeast(1)
        return DeferDecision(
            updated = item.copy(todo = detail.copy(deferCount = count)),
            destinationIndex = (currentIndex + offset).coerceAtMost((itemCount - 1).coerceAtLeast(0)),
            shouldSuggestSplit = shouldPrompt && threshold in 1..10,
        )
    }

    fun postponePrompt(item: AppItem, threshold: Int): AppItem {
        val detail = requireNotNull(item.todo)
        return item.copy(todo = detail.copy(nextSplitPromptAt = detail.deferCount + threshold.coerceIn(1, 10)))
    }
}

sealed interface SplitValidation {
    data class Valid(val titles: List<String>) : SplitValidation
    data class Invalid(val message: String) : SplitValidation
}

object SplitPolicy {
    fun validate(titles: List<String>): SplitValidation {
        val clean = titles.map(String::trim).filter(String::isNotEmpty)
        return if (clean.size >= 2) SplitValidation.Valid(clean)
        else SplitValidation.Invalid("2件以上のやることに分けてください")
    }

    fun buildChildren(
        parent: AppItem,
        titles: List<String>,
        now: Long,
        idFactory: () -> String,
    ): List<AppItem> {
        val clean = (validate(titles) as? SplitValidation.Valid)?.titles
            ?: throw IllegalArgumentException("At least two child items are required")
        val detail = requireNotNull(parent.todo)
        val baseMinutes = detail.estimatedMinutes?.div(clean.size)
        val remainder = detail.estimatedMinutes?.rem(clean.size) ?: 0
        return clean.mapIndexed { index, title ->
            AppItem(
                id = idFactory(),
                kind = ItemKind.TODO,
                lifecycle = ItemLifecycle.ACTIVE,
                title = title,
                body = if (index == 0) parent.body else "",
                manualRank = parent.manualRank + index,
                groupId = parent.groupId,
                parentId = parent.id,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                todo = detail.copy(
                    estimatedMinutes = baseMinutes?.plus(if (index == 0) remainder else 0),
                    deferCount = 0,
                    nextSplitPromptAt = detail.nextSplitPromptAt.coerceAtLeast(1),
                    splitPromptDisabled = false,
                ),
            )
        }
    }
}
