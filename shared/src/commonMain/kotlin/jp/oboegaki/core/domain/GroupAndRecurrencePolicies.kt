package jp.oboegaki.core.domain

import jp.oboegaki.core.model.AppItem
import jp.oboegaki.core.model.ItemKind
import jp.oboegaki.core.model.ItemLifecycle
import jp.oboegaki.core.model.RecurrenceRule
import jp.oboegaki.core.model.RecurrenceUnit
import jp.oboegaki.core.model.TodoDetail
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

enum class GroupRejectionReason {
    GROUP_NOT_FOUND,
    PARENT_IS_NOT_GROUP,
    INACTIVE_PARENT,
    DIFFERENT_KIND,
    SELF_OR_DESCENDANT,
    UNSORTED_NOT_SUPPORTED,
}

sealed interface GroupPlacementDecision {
    data object Allowed : GroupPlacementDecision
    data class Rejected(val reason: GroupRejectionReason, val message: String) : GroupPlacementDecision
}

data class GroupedItem(
    val item: AppItem,
    val depth: Int,
    val hasChildren: Boolean,
)

object GroupPolicy {
    fun validatePlacement(
        item: AppItem,
        proposedGroupId: String?,
        allItems: List<AppItem>,
    ): GroupPlacementDecision {
        if (proposedGroupId == null) return GroupPlacementDecision.Allowed
        if (item.kind == ItemKind.UNSORTED) {
            return GroupPlacementDecision.Rejected(
                GroupRejectionReason.UNSORTED_NOT_SUPPORTED,
                "あとで分ける項目はグループに入れられません",
            )
        }
        val parent = allItems.firstOrNull { it.id == proposedGroupId }
            ?: return GroupPlacementDecision.Rejected(
                GroupRejectionReason.GROUP_NOT_FOUND,
                "選んだグループが見つかりません",
            )
        if (!parent.isGroup) {
            return GroupPlacementDecision.Rejected(
                GroupRejectionReason.PARENT_IS_NOT_GROUP,
                "通常の項目をグループとして選ぶことはできません",
            )
        }
        if (parent.lifecycle != ItemLifecycle.ACTIVE) {
            return GroupPlacementDecision.Rejected(
                GroupRejectionReason.INACTIVE_PARENT,
                "有効なグループにだけ入れられます",
            )
        }
        if (parent.kind != item.kind) {
            return GroupPlacementDecision.Rejected(
                GroupRejectionReason.DIFFERENT_KIND,
                "やることとメモを同じグループには入れられません",
            )
        }
        if (item.id == parent.id || parent.id in descendantIds(item.id, allItems)) {
            return GroupPlacementDecision.Rejected(
                GroupRejectionReason.SELF_OR_DESCENDANT,
                "自分自身や内側のグループには移動できません",
            )
        }
        return GroupPlacementDecision.Allowed
    }

    fun availableParents(item: AppItem, allItems: List<AppItem>): List<AppItem> {
        val forbidden = descendantIds(item.id, allItems) + item.id
        return allItems.filter {
            it.isGroup && it.kind == item.kind && it.lifecycle == ItemLifecycle.ACTIVE && it.id !in forbidden
        }.sortedBy { it.manualRank }
    }

    fun descendantIds(groupId: String, allItems: List<AppItem>): Set<String> {
        val children = allItems.groupBy { it.groupId }
        val pending = mutableListOf(groupId)
        val found = mutableSetOf<String>()
        while (pending.isNotEmpty()) {
            val current = pending.removeAt(pending.lastIndex)
            children[current].orEmpty().forEach { child ->
                if (found.add(child.id) && child.isGroup) pending += child.id
            }
        }
        return found
    }

    fun flatten(
        items: List<AppItem>,
        collapsedGroupIds: Set<String> = emptySet(),
        relations: List<jp.oboegaki.core.model.ItemRelation> = emptyList(),
    ): List<GroupedItem> {
        if (items.isEmpty()) return emptyList()
        val byId = items.associateBy { it.id }
        val children = items.groupBy { it.groupId }.mapValues { (_, values) -> orderedSiblings(values, relations) }
        val roots = orderedSiblings(items.filter { item ->
            item.groupId == null || byId[item.groupId]?.let { !it.isGroup || it.kind != item.kind } != false
        }, relations)
        val result = mutableListOf<GroupedItem>()
        val visited = mutableSetOf<String>()
        val pending = mutableListOf<Pair<AppItem, Int>>()
        roots.asReversed().forEach { pending += it to 0 }
        while (pending.isNotEmpty()) {
            val (item, depth) = pending.removeAt(pending.lastIndex)
            if (!visited.add(item.id)) continue
            val directChildren = children[item.id].orEmpty()
            result += GroupedItem(item, depth, directChildren.isNotEmpty())
            if (item.isGroup && item.id !in collapsedGroupIds) {
                directChildren.asReversed().forEach { pending += it to depth + 1 }
            } else if (item.isGroup) {
                // Collapsed descendants are intentionally hidden, not orphaned.
                // Mark them visited so the corrupt-cycle recovery below does not
                // append them at the root level.
                visited += descendantIds(item.id, items)
            }
        }
        orderedSiblings(items.filterNot { it.id in visited }, relations).forEach { orphan ->
            result += GroupedItem(orphan, 0, children[orphan.id].orEmpty().isNotEmpty())
        }
        return result
    }

    /** The single sibling-ordering path shared by display and moveWithinGroup. */
    private fun orderedSiblings(
        items: List<AppItem>,
        relations: List<jp.oboegaki.core.model.ItemRelation>,
    ): List<AppItem> = when {
        items.isEmpty() -> emptyList()
        items.first().kind == ItemKind.TODO -> OrderingPolicy.canonicalSort(items, relations)
        else -> items.sortedWith(compareBy<AppItem> { it.manualRank }.thenBy { it.createdAtEpochMillis }.thenBy { it.id })
    }

    fun recurringTemplateItems(sourceGroup: AppItem, allItems: List<AppItem>): List<AppItem> {
        if (!sourceGroup.isGroup) return emptyList()
        val children = allItems.groupBy { it.groupId }
        val eligible = setOf(ItemLifecycle.ACTIVE, ItemLifecycle.COMPLETED)
        val result = mutableListOf(sourceGroup)
        val visited = mutableSetOf(sourceGroup.id)
        val pending = mutableListOf<AppItem>()
        orderedSiblings(children[sourceGroup.id].orEmpty(), emptyList()).asReversed().forEach { pending += it }
        while (pending.isNotEmpty()) {
            val item = pending.removeAt(pending.lastIndex)
            if (!visited.add(item.id) || item.lifecycle !in eligible) continue
            result += item
            if (item.isGroup) {
                orderedSiblings(children[item.id].orEmpty(), emptyList()).asReversed().forEach { pending += it }
            }
        }
        return result
    }

    /** Maps every recurring template item to the corresponding generated copy. */
    fun recurringCopyIdMap(
        sourceGroup: AppItem,
        allItems: List<AppItem>,
        copies: List<AppItem>,
    ): Map<String, String> {
        val template = recurringTemplateItems(sourceGroup, allItems)
        if (template.size != copies.size) return emptyMap()
        return template.zip(copies).associate { (source, copy) -> source.id to copy.id }
    }

    fun breadcrumb(item: AppItem, allItems: List<AppItem>): String {
        val byId = allItems.associateBy { it.id }
        val names = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        var currentId = item.groupId
        while (currentId != null && visited.add(currentId)) {
            val current = byId[currentId] ?: break
            names += current.title
            currentId = current.groupId
        }
        return names.asReversed().joinToString(" › ")
    }
}

enum class RecurrenceRejectionReason {
    NOT_A_TODO,
    MISSING_SCHEDULE,
    INVALID_INTERVAL,
    INVALID_ANCHOR,
}

sealed interface RecurrenceValidation {
    data object Valid : RecurrenceValidation
    data class Invalid(val reason: RecurrenceRejectionReason, val message: String) : RecurrenceValidation
}

object RecurrencePolicy {
    fun validate(item: AppItem): RecurrenceValidation {
        val rule = item.todo?.recurrence ?: return RecurrenceValidation.Valid
        if (item.kind != ItemKind.TODO) {
            return RecurrenceValidation.Invalid(
                RecurrenceRejectionReason.NOT_A_TODO,
                "定期設定はやることだけに使用できます",
            )
        }
        if (item.todo.scheduledAtEpochMillis == null) {
            return RecurrenceValidation.Invalid(
                RecurrenceRejectionReason.MISSING_SCHEDULE,
                "定期設定には行う日時を設定してください",
            )
        }
        if (rule.interval !in 1..999) {
            return RecurrenceValidation.Invalid(
                RecurrenceRejectionReason.INVALID_INTERVAL,
                "繰り返す間隔は1〜999で設定してください",
            )
        }
        val anchors = listOf(
            rule.anchorMonth to rule.anchorDayOfMonth,
            item.todo.recurrenceScheduledAnchorMonth to item.todo.recurrenceScheduledAnchorDayOfMonth,
            item.todo.recurrenceAvailableAnchorMonth to item.todo.recurrenceAvailableAnchorDayOfMonth,
            item.todo.recurrenceDueAnchorMonth to item.todo.recurrenceDueAnchorDayOfMonth,
        )
        if (anchors.any { (month, day) ->
                (month != null && month !in 1..12) || (day != null && day !in 1..31)
            }
        ) {
            return RecurrenceValidation.Invalid(
                RecurrenceRejectionReason.INVALID_ANCHOR,
                "定期設定の日付が正しくありません",
            )
        }
        return RecurrenceValidation.Valid
    }

    fun buildNextOccurrence(
        source: AppItem,
        nowEpochMillis: Long,
        newId: String,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): AppItem? {
        val detail = source.todo ?: return null
        val rule = detail.recurrence ?: return null
        val scheduled = detail.scheduledAtEpochMillis ?: return null
        val normalizedRule = rule.withAnchor(scheduled, timeZone)
        val normalizedDetail = normalizeDetailAnchors(detail, normalizedRule, timeZone)
        val nextScheduled = advanceField(
            scheduled,
            normalizedRule,
            normalizedDetail.recurrenceScheduledAnchorMonth,
            normalizedDetail.recurrenceScheduledAnchorDayOfMonth,
            timeZone,
        )
        if (isAfterEndDate(nextScheduled, normalizedRule, timeZone)) return null
        return source.copy(
            id = newId,
            lifecycle = ItemLifecycle.ACTIVE,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
            completedAtEpochMillis = null,
            archivedAtEpochMillis = null,
            revision = 0,
            todo = advanceDetail(normalizedDetail, normalizedRule, timeZone, normalizedRule).copy(
                deferCount = 0,
                splitPromptDisabled = false,
            ),
        )
    }

    fun normalize(item: AppItem, timeZone: TimeZone = TimeZone.currentSystemDefault()): AppItem {
        val detail = item.todo ?: return item
        val rule = detail.recurrence ?: return item
        val scheduled = detail.scheduledAtEpochMillis ?: return item
        val normalizedRule = rule.withAnchor(scheduled, timeZone)
        return item.copy(todo = normalizeDetailAnchors(detail, normalizedRule, timeZone))
    }

    fun buildNextGroupOccurrence(
        sourceGroup: AppItem,
        allItems: List<AppItem>,
        nowEpochMillis: Long,
        idFactory: () -> String,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): List<AppItem> {
        if (!sourceGroup.isGroup) return emptyList()
        val rule = sourceGroup.todo?.recurrence ?: return emptyList()
        val root = buildNextOccurrence(sourceGroup, nowEpochMillis, idFactory(), timeZone)
            ?: return emptyList()
        val normalizedRule = root.todo?.recurrence ?: rule
        val descendants = GroupPolicy.recurringTemplateItems(sourceGroup, allItems).drop(1)
        val idMap = mutableMapOf(sourceGroup.id to root.id)
        descendants.forEach { idMap[it.id] = idFactory() }
        val copies = descendants.map { source ->
            source.copy(
                id = idMap.getValue(source.id),
                groupId = source.groupId?.let(idMap::get),
                lifecycle = ItemLifecycle.ACTIVE,
                createdAtEpochMillis = nowEpochMillis,
                updatedAtEpochMillis = nowEpochMillis,
                completedAtEpochMillis = null,
                archivedAtEpochMillis = null,
                revision = 0,
                // Descendants retain their own local anchors. Their recurrence
                // cadence is driven by the group, so they do not receive an
                // independent recurrence rule that would require a schedule.
                todo = source.todo?.let { advanceDetail(it, normalizedRule, timeZone, null) }?.copy(
                    deferCount = 0,
                    splitPromptDisabled = false,
                ),
            )
        }
        return listOf(root) + copies
    }

    fun advance(epochMillis: Long, rule: RecurrenceRule, timeZone: TimeZone): Long {
        val instant = Instant.fromEpochMilliseconds(epochMillis)
        val advanced = when (rule.unit) {
            RecurrenceUnit.DAY -> instant.plus(rule.interval, DateTimeUnit.DAY, timeZone)
            RecurrenceUnit.WEEK -> instant.plus(rule.interval * 7, DateTimeUnit.DAY, timeZone)
            RecurrenceUnit.MONTH, RecurrenceUnit.YEAR -> advanceCalendarUnit(instant, rule, timeZone)
        }
        return advanced.toEpochMilliseconds()
    }

    private fun advanceDetail(
        detail: TodoDetail,
        rule: RecurrenceRule,
        timeZone: TimeZone,
        recurrenceOverride: RecurrenceRule?,
    ): TodoDetail {
        val anchored = normalizeDetailAnchors(detail, rule, timeZone)
        val recurrence = recurrenceOverride?.copy(
            anchorMonth = anchored.recurrenceScheduledAnchorMonth ?: recurrenceOverride.anchorMonth,
            anchorDayOfMonth = anchored.recurrenceScheduledAnchorDayOfMonth ?: recurrenceOverride.anchorDayOfMonth,
        )
        return anchored.copy(
            availableFromEpochMillis = anchored.availableFromEpochMillis?.let {
                advanceField(
                    it,
                    rule,
                    anchored.recurrenceAvailableAnchorMonth,
                    anchored.recurrenceAvailableAnchorDayOfMonth,
                    timeZone,
                )
            },
            scheduledAtEpochMillis = anchored.scheduledAtEpochMillis?.let {
                advanceField(
                    it,
                    rule,
                    anchored.recurrenceScheduledAnchorMonth,
                    anchored.recurrenceScheduledAnchorDayOfMonth,
                    timeZone,
                )
            },
            dueAtEpochMillis = anchored.dueAtEpochMillis?.let {
                advanceField(
                    it,
                    rule,
                    anchored.recurrenceDueAnchorMonth,
                    anchored.recurrenceDueAnchorDayOfMonth,
                    timeZone,
                )
            },
            recurrence = recurrence,
        )
    }

    private fun normalizeDetailAnchors(
        detail: TodoDetail,
        rule: RecurrenceRule,
        timeZone: TimeZone,
    ): TodoDetail {
        val scheduled = anchorFor(
            detail.scheduledAtEpochMillis,
            detail.recurrenceScheduledAnchorMonth,
            detail.recurrenceScheduledAnchorDayOfMonth,
            detail.recurrence?.anchorMonth,
            detail.recurrence?.anchorDayOfMonth,
            timeZone,
        )
        val available = anchorFor(
            detail.availableFromEpochMillis,
            detail.recurrenceAvailableAnchorMonth,
            detail.recurrenceAvailableAnchorDayOfMonth,
            null,
            null,
            timeZone,
        )
        val due = anchorFor(
            detail.dueAtEpochMillis,
            detail.recurrenceDueAnchorMonth,
            detail.recurrenceDueAnchorDayOfMonth,
            null,
            null,
            timeZone,
        )
        return detail.copy(
            recurrence = detail.recurrence?.let {
                rule.copy(
                    anchorMonth = scheduled?.month ?: rule.anchorMonth,
                    anchorDayOfMonth = scheduled?.day ?: rule.anchorDayOfMonth,
                )
            },
            recurrenceScheduledAnchorMonth = scheduled?.month,
            recurrenceScheduledAnchorDayOfMonth = scheduled?.day,
            recurrenceAvailableAnchorMonth = available?.month,
            recurrenceAvailableAnchorDayOfMonth = available?.day,
            recurrenceDueAnchorMonth = due?.month,
            recurrenceDueAnchorDayOfMonth = due?.day,
        )
    }

    private fun advanceField(
        epochMillis: Long,
        rule: RecurrenceRule,
        anchorMonth: Int?,
        anchorDay: Int?,
        timeZone: TimeZone,
    ): Long = advance(
        epochMillis,
        if (rule.unit == RecurrenceUnit.MONTH || rule.unit == RecurrenceUnit.YEAR) {
            rule.copy(anchorMonth = anchorMonth, anchorDayOfMonth = anchorDay)
        } else {
            rule
        },
        timeZone,
    )

    private data class Anchor(val month: Int, val day: Int)

    private fun anchorFor(
        epochMillis: Long?,
        storedMonth: Int?,
        storedDay: Int?,
        legacyMonth: Int?,
        legacyDay: Int?,
        timeZone: TimeZone,
    ): Anchor? {
        if (epochMillis == null) return null
        val local = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone)
        return Anchor(
            month = storedMonth ?: legacyMonth ?: local.monthNumber,
            day = storedDay ?: legacyDay ?: local.dayOfMonth,
        )
    }

    private fun RecurrenceRule.withAnchor(epochMillis: Long, timeZone: TimeZone): RecurrenceRule {
        if (unit !in setOf(RecurrenceUnit.MONTH, RecurrenceUnit.YEAR)) return this
        val local = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone)
        return copy(
            anchorMonth = anchorMonth ?: local.monthNumber,
            anchorDayOfMonth = anchorDayOfMonth ?: local.dayOfMonth,
        )
    }

    private fun advanceCalendarUnit(instant: Instant, rule: RecurrenceRule, timeZone: TimeZone): Instant {
        val local = instant.toLocalDateTime(timeZone)
        val anchorMonth = rule.anchorMonth ?: local.monthNumber
        val anchorDay = rule.anchorDayOfMonth ?: local.dayOfMonth
        val monthOffset = when (rule.unit) {
            RecurrenceUnit.MONTH -> rule.interval
            RecurrenceUnit.YEAR -> rule.interval * 12
            else -> error("Calendar-unit recurrence required")
        }
        val targetMonthIndex = local.year * 12 + (local.monthNumber - 1) + monthOffset
        val targetYear = targetMonthIndex.floorDiv(12)
        val targetMonth = targetMonthIndex.mod(12) + 1
        val targetDay = anchorDay.coerceAtMost(daysInMonth(targetYear, targetMonth))
        return LocalDateTime(
            targetYear,
            if (rule.unit == RecurrenceUnit.YEAR) anchorMonth else targetMonth,
            targetDay,
            local.hour,
            local.minute,
            local.second,
            local.nanosecond,
        ).toInstant(timeZone)
    }

    private fun daysInMonth(year: Int, month: Int): Int = when (month) {
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    private fun isAfterEndDate(nextScheduled: Long, rule: RecurrenceRule, timeZone: TimeZone): Boolean {
        val end = rule.endAtEpochMillis ?: return false
        return Instant.fromEpochMilliseconds(nextScheduled).toLocalDateTime(timeZone).date >
            Instant.fromEpochMilliseconds(end).toLocalDateTime(timeZone).date
    }
}
