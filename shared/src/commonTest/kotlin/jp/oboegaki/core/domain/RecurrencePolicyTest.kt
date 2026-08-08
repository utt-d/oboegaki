package jp.oboegaki.core.domain

import jp.oboegaki.core.model.AppItem
import jp.oboegaki.core.model.ItemKind
import jp.oboegaki.core.model.RecurrenceRule
import jp.oboegaki.core.model.RecurrenceUnit
import jp.oboegaki.core.model.ItemLifecycle
import jp.oboegaki.core.model.TodoDetail
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class RecurrencePolicyTest {
    @Test
    fun requiresAScheduledDateAndTime() {
        val item = todo(
            "todo",
            TodoDetail(recurrence = RecurrenceRule(RecurrenceUnit.DAY)),
        )

        assertIs<RecurrenceValidation.Invalid>(RecurrencePolicy.validate(item))
    }

    @Test
    fun dailyRepeatPreservesLocalTimeAcrossDaylightSavingChange() {
        val zone = TimeZone.of("America/New_York")
        val start = LocalDateTime(2026, 3, 7, 9, 30).toInstant(zone).toEpochMilliseconds()
        val item = todo(
            "todo",
            TodoDetail(
                scheduledAtEpochMillis = start,
                recurrence = RecurrenceRule(RecurrenceUnit.DAY),
            ),
        )

        val next = RecurrencePolicy.buildNextOccurrence(item, start, "next", zone)!!
        val local = kotlinx.datetime.Instant.fromEpochMilliseconds(next.todo!!.scheduledAtEpochMillis!!)
            .toLocalDateTime(zone)

        assertEquals(LocalDateTime(2026, 3, 8, 9, 30), local)
    }

    @Test
    fun monthlyRepeatUsesTheLastValidDayForShorterMonth() {
        val zone = TimeZone.UTC
        val start = LocalDateTime(2027, 1, 31, 18, 0).toInstant(zone).toEpochMilliseconds()
        val next = RecurrencePolicy.buildNextOccurrence(
            todo(
                "todo",
                TodoDetail(
                    scheduledAtEpochMillis = start,
                    recurrence = RecurrenceRule(RecurrenceUnit.MONTH),
                ),
            ),
            start,
            "next",
            zone,
        )!!

        assertEquals(
            LocalDateTime(2027, 2, 28, 18, 0),
            kotlinx.datetime.Instant.fromEpochMilliseconds(next.todo!!.scheduledAtEpochMillis!!)
                .toLocalDateTime(zone),
        )
    }

    @Test
    fun monthlyRepeatReturnsToTheAnchorDayAfterAShortMonth() {
        val zone = TimeZone.UTC
        val start = LocalDateTime(2027, 1, 31, 18, 0).toInstant(zone).toEpochMilliseconds()
        val rule = RecurrenceRule(RecurrenceUnit.MONTH)
        val february = RecurrencePolicy.advance(start, rule, zone)
        val march = RecurrencePolicy.advance(february, rule.copy(anchorMonth = 1, anchorDayOfMonth = 31), zone)

        assertEquals(LocalDateTime(2027, 2, 28, 18, 0), Instant.fromEpochMilliseconds(february).toLocalDateTime(zone))
        assertEquals(LocalDateTime(2027, 3, 31, 18, 0), Instant.fromEpochMilliseconds(march).toLocalDateTime(zone))
    }

    @Test
    fun availableAndDueDatesReturnToTheirOwnAnchorAfterMultipleTransitions() {
        val zone = TimeZone.UTC
        val scheduled = LocalDateTime(2027, 1, 1, 9, 0).toInstant(zone).toEpochMilliseconds()
        val available = LocalDateTime(2027, 1, 31, 8, 0).toInstant(zone).toEpochMilliseconds()
        val due = LocalDateTime(2027, 1, 31, 18, 0).toInstant(zone).toEpochMilliseconds()
        val source = todo(
            "todo",
            TodoDetail(
                availableFromEpochMillis = available,
                scheduledAtEpochMillis = scheduled,
                dueAtEpochMillis = due,
                recurrence = RecurrenceRule(RecurrenceUnit.MONTH),
            ),
        )

        val february = RecurrencePolicy.buildNextOccurrence(source, scheduled, "february", zone)!!
        val march = RecurrencePolicy.buildNextOccurrence(february, scheduled, "march", zone)!!

        assertEquals(
            LocalDateTime(2027, 2, 28, 8, 0),
            Instant.fromEpochMilliseconds(february.todo!!.availableFromEpochMillis!!).toLocalDateTime(zone),
        )
        assertEquals(
            LocalDateTime(2027, 3, 31, 8, 0),
            Instant.fromEpochMilliseconds(march.todo!!.availableFromEpochMillis!!).toLocalDateTime(zone),
        )
        assertEquals(
            LocalDateTime(2027, 3, 31, 18, 0),
            Instant.fromEpochMilliseconds(march.todo!!.dueAtEpochMillis!!).toLocalDateTime(zone),
        )
    }

    @Test
    fun yearlyRepeatReturnsToFebruaryTwentyNinthInTheNextLeapYear() {
        val zone = TimeZone.UTC
        val start = LocalDateTime(2024, 2, 29, 8, 15).toInstant(zone).toEpochMilliseconds()
        val rule = RecurrenceRule(RecurrenceUnit.YEAR, anchorMonth = 2, anchorDayOfMonth = 29)
        val y2025 = RecurrencePolicy.advance(start, rule, zone)
        val y2026 = RecurrencePolicy.advance(y2025, rule, zone)
        val y2027 = RecurrencePolicy.advance(y2026, rule, zone)
        val y2028 = RecurrencePolicy.advance(y2027, rule, zone)

        assertEquals(LocalDateTime(2025, 2, 28, 8, 15), Instant.fromEpochMilliseconds(y2025).toLocalDateTime(zone))
        assertEquals(LocalDateTime(2026, 2, 28, 8, 15), Instant.fromEpochMilliseconds(y2026).toLocalDateTime(zone))
        assertEquals(LocalDateTime(2027, 2, 28, 8, 15), Instant.fromEpochMilliseconds(y2027).toLocalDateTime(zone))
        assertEquals(LocalDateTime(2028, 2, 29, 8, 15), Instant.fromEpochMilliseconds(y2028).toLocalDateTime(zone))
    }

    @Test
    fun doesNotCreateAnOccurrenceAfterTheEndDate() {
        val zone = TimeZone.UTC
        val start = LocalDateTime(2026, 8, 2, 9, 0).toInstant(zone).toEpochMilliseconds()
        val end = LocalDateTime(2026, 8, 2, 23, 0).toInstant(zone).toEpochMilliseconds()
        val item = todo(
            "todo",
            TodoDetail(
                scheduledAtEpochMillis = start,
                recurrence = RecurrenceRule(RecurrenceUnit.DAY, endAtEpochMillis = end),
            ),
        )

        assertNull(RecurrencePolicy.buildNextOccurrence(item, start, "next", zone))
    }

    @Test
    fun recurringGroupCopiesItsUnlimitedTreeAndParentLinks() {
        val zone = TimeZone.UTC
        val start = LocalDateTime(2026, 8, 2, 9, 0).toInstant(zone).toEpochMilliseconds()
        val root = todo(
            "root",
            TodoDetail(
                scheduledAtEpochMillis = start,
                recurrence = RecurrenceRule(RecurrenceUnit.WEEK),
            ),
            isGroup = true,
        )
        val inner = todo("inner", TodoDetail(), isGroup = true, groupId = root.id)
        val leaf = todo(
            "leaf",
            TodoDetail(scheduledAtEpochMillis = start),
            groupId = inner.id,
        ).copy(lifecycle = ItemLifecycle.COMPLETED)
        var id = 0

        val copies = RecurrencePolicy.buildNextGroupOccurrence(
            root,
            listOf(root, inner, leaf),
            start,
            { "copy-${id++}" },
            zone,
        )

        assertEquals(3, copies.size)
        assertEquals(copies[0].id, copies[1].groupId)
        assertEquals(copies[1].id, copies[2].groupId)
        assertEquals(
            LocalDateTime(2026, 8, 9, 9, 0),
            kotlinx.datetime.Instant.fromEpochMilliseconds(copies[2].todo!!.scheduledAtEpochMillis!!)
                .toLocalDateTime(zone),
        )
    }

    @Test
    fun recurringGroupPreservesEachDescendantsCalendarAnchorAcrossMultipleTransitions() {
        val zone = TimeZone.UTC
        val rootDate = LocalDateTime(2027, 1, 31, 9, 0).toInstant(zone).toEpochMilliseconds()
        val childDate = LocalDateTime(2027, 1, 30, 10, 0).toInstant(zone).toEpochMilliseconds()
        val childDue = LocalDateTime(2027, 1, 31, 18, 0).toInstant(zone).toEpochMilliseconds()
        val root = todo(
            "root",
            TodoDetail(
                scheduledAtEpochMillis = rootDate,
                recurrence = RecurrenceRule(RecurrenceUnit.MONTH),
            ),
            isGroup = true,
        )
        val child = todo(
            "child",
            TodoDetail(scheduledAtEpochMillis = childDate, dueAtEpochMillis = childDue),
            groupId = root.id,
        )

        var sequence = 0
        val february = RecurrencePolicy.buildNextGroupOccurrence(
            root,
            listOf(root, child),
            rootDate,
            { "copy-${sequence++}" },
            zone,
        )
        val march = RecurrencePolicy.buildNextGroupOccurrence(
            february.first(),
            february,
            rootDate,
            { "copy-${sequence++}" },
            zone,
        )

        assertEquals(
            LocalDateTime(2027, 3, 31, 9, 0),
            Instant.fromEpochMilliseconds(march[0].todo!!.scheduledAtEpochMillis!!).toLocalDateTime(zone),
        )
        assertEquals(
            LocalDateTime(2027, 3, 30, 10, 0),
            Instant.fromEpochMilliseconds(march[1].todo!!.scheduledAtEpochMillis!!).toLocalDateTime(zone),
        )
        assertEquals(
            LocalDateTime(2027, 3, 31, 18, 0),
            Instant.fromEpochMilliseconds(march[1].todo!!.dueAtEpochMillis!!).toLocalDateTime(zone),
        )
    }

    private fun todo(
        id: String,
        detail: TodoDetail,
        isGroup: Boolean = false,
        groupId: String? = null,
    ) = AppItem(
        id = id,
        kind = ItemKind.TODO,
        title = id,
        manualRank = 0,
        isGroup = isGroup,
        groupId = groupId,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
        todo = detail,
    )
}
