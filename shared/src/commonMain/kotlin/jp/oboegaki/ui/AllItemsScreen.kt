package jp.oboegaki.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.oboegaki.core.domain.GroupPolicy
import jp.oboegaki.core.domain.GroupedItem
import jp.oboegaki.core.model.AddButtonPosition
import jp.oboegaki.core.model.AllSections
import jp.oboegaki.core.model.AppItem
import jp.oboegaki.core.model.ItemKind
import jp.oboegaki.core.model.ItemRelation
import jp.oboegaki.core.model.RecurrenceUnit

@Composable
fun AllItemsScreen(
    sections: AllSections,
    relations: List<ItemRelation>,
    addButtonPosition: AddButtonPosition,
    addButtonBottomOffsetDp: Int,
    controller: AppController,
) {
    val expanded = remember { mutableStateMapOf(
        "unsorted" to true, "todos" to true, "memos" to true, "completed" to false, "archived" to false,
    ) }
    val collapsedGroups = remember { mutableStateMapOf<String, Boolean>() }
    val todoActive = remember(sections.todoGroups, sections.todos, relations, collapsedGroups.toMap()) {
        GroupPolicy.flatten(sections.todoGroups + sections.todos, collapsedGroups.filterValues { it }.keys, relations)
    }
    val memoActive = remember(sections.memoGroups, sections.memos, collapsedGroups.toMap()) {
        GroupPolicy.flatten(sections.memoGroups + sections.memos, collapsedGroups.filterValues { it }.keys, relations)
    }
    val completed = remember(sections.completed, relations, collapsedGroups.toMap()) {
        GroupPolicy.flatten(sections.completed, collapsedGroups.filterValues { it }.keys, relations)
    }
    val archived = remember(sections.archived, relations, collapsedGroups.toMap()) {
        GroupPolicy.flatten(sections.archived, collapsedGroups.filterValues { it }.keys, relations)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        plainSection("あとで分ける", "unsorted", sections.unsorted, expanded, controller, addButtonPosition)
        groupedSection("やること", "todos", todoActive, expanded, collapsedGroups, controller, addButtonPosition)
        groupedSection("メモ", "memos", memoActive, expanded, collapsedGroups, controller, addButtonPosition)
        groupedSection("完了したこと", "completed", completed, expanded, collapsedGroups, controller, addButtonPosition, restoreLabel = "やることへ戻す")
        groupedSection("しまったメモ", "archived", archived, expanded, collapsedGroups, controller, addButtonPosition, restoreLabel = "メモへ戻す")
        item {
            Spacer(Modifier.height(88.dp))
            OutlinedButton(
                onClick = controller::openDataTools,
                modifier = Modifier
                    .fillMaxWidth(if (addButtonPosition == AddButtonPosition.CENTER) .45f else 1f)
                    .padding(
                        start = if (addButtonPosition == AddButtonPosition.LEFT) 82.dp else 0.dp,
                        end = if (addButtonPosition == AddButtonPosition.RIGHT) 82.dp else 0.dp,
                        bottom = if (addButtonPosition == AddButtonPosition.CENTER) (82 + addButtonBottomOffsetDp.coerceIn(0, 160)).dp else 0.dp,
                    )
                    .height(48.dp),
            ) { Text("データのバックアップと読み込み") }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.sectionHeader(
    title: String,
    key: String,
    count: Int,
    expanded: MutableMap<String, Boolean>,
) {
    item(key = "header-$key") {
        Row(
            Modifier.fillMaxWidth().clickable { expanded[key] = expanded[key] != true }.padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (expanded[key] == true) "▾" else "▸", style = MaterialTheme.typography.h6)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.h6, modifier = Modifier.weight(1f))
            Text("${count}件", style = MaterialTheme.typography.caption, color = parseColor(LocalThemeColors.current.textSecondary))
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.plainSection(
    title: String,
    key: String,
    values: List<AppItem>,
    expanded: MutableMap<String, Boolean>,
    controller: AppController,
    addButtonPosition: AddButtonPosition,
) {
    sectionHeader(title, key, values.size, expanded)
    if (expanded[key] != true) return
    if (values.isEmpty()) item(key = "empty-$key") { EmptySection() }
    items(values, key = { "$key-${it.id}" }) { item ->
        val index = values.indexOfFirst { it.id == item.id }
        AllItemRow(GroupedItem(item, 0, false), index, values.size, true, null, addButtonPosition, mutableMapOf(), controller)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.groupedSection(
    title: String,
    key: String,
    values: List<GroupedItem>,
    expanded: MutableMap<String, Boolean>,
    collapsedGroups: MutableMap<String, Boolean>,
    controller: AppController,
    addButtonPosition: AddButtonPosition,
    restoreLabel: String? = null,
) {
    sectionHeader(title, key, values.size, expanded)
    if (expanded[key] != true) return
    if (values.isEmpty()) item(key = "empty-$key") { EmptySection() }
    val siblingCounts = values.groupingBy { it.item.groupId }.eachCount()
    val siblingIndices = mutableMapOf<String, Int>()
    values.forEach { grouped ->
        val siblingKey = grouped.item.groupId ?: "<root>"
        val siblingIndex = siblingIndices[siblingKey] ?: 0
        siblingIndices[siblingKey] = siblingIndex + 1
        item(key = "$key-${grouped.item.id}") {
            AllItemRow(
                grouped = grouped,
                index = siblingIndex,
                count = siblingCounts[grouped.item.groupId] ?: 1,
                reorderable = restoreLabel == null,
                restoreLabel = restoreLabel,
                addButtonPosition = addButtonPosition,
                collapsedGroups = collapsedGroups,
                controller = controller,
            )
        }
    }
}

@Composable
private fun EmptySection() {
    Text(
        "項目はありません",
        Modifier.fillMaxWidth().padding(14.dp),
        color = parseColor(LocalThemeColors.current.textSecondary),
    )
}

@Composable
private fun AllItemRow(
    grouped: GroupedItem,
    index: Int,
    count: Int,
    reorderable: Boolean,
    restoreLabel: String?,
    addButtonPosition: AddButtonPosition,
    collapsedGroups: MutableMap<String, Boolean>,
    controller: AppController,
) {
    val item = grouped.item
    val theme = LocalAppTheme.current
    val tokens = LocalThemeColors.current
    val density = LocalDensity.current
    var dragTotal by remember(item.id) { mutableFloatStateOf(0f) }
    fun move(direction: Int) {
        if (item.kind == ItemKind.UNSORTED) {
            controller.moveFree(item.id, index + direction)
        } else {
            controller.moveWithinGroup(item.id, direction)
        }
    }
    val customActions = buildList {
        add(CustomAccessibilityAction("編集") { controller.openEdit(item.id); true })
        if (reorderable && index > 0) add(CustomAccessibilityAction("上へ移動") { move(-1); true })
        if (reorderable && index < count - 1) add(CustomAccessibilityAction("下へ移動") { move(1); true })
    }
    val indent = (grouped.depth.coerceAtMost(6) * 16).dp
    Card(
        modifier = Modifier.padding(start = indent).fillMaxWidth().semantics { this.customActions = customActions }.clickable { controller.openEdit(item.id) },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(theme.mediumCornerDp.dp),
        elevation = if (item.isGroup) 3.dp else 1.dp,
        backgroundColor = parseColor(if (item.isGroup) tokens.surfaceAlt else tokens.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.isGroup && grouped.hasChildren) {
                    TextButton(
                        onClick = { collapsedGroups[item.id] = collapsedGroups[item.id] != true },
                        modifier = Modifier.size(48.dp),
                    ) { Text(if (collapsedGroups[item.id] == true) "▸" else "▾") }
                }
                if (reorderable) {
                    Box(
                        Modifier.size(48.dp).pointerInput(item.id, index, count) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragTotal = 0f },
                                onDragEnd = { dragTotal = 0f },
                                onDragCancel = { dragTotal = 0f },
                            ) { change, drag ->
                                change.consume()
                                dragTotal += drag.y
                                val step = with(density) { 56.dp.toPx() }
                                if (dragTotal < -step && index > 0) { move(-1); dragTotal = 0f }
                                else if (dragTotal > step && index < count - 1) { move(1); dragTotal = 0f }
                            }
                        },
                        contentAlignment = Alignment.Center,
                    ) { Text(if (item.isGroup) "▣" else LocalAppTheme.current.icons.all, style = MaterialTheme.typography.h6, color = itemKindColor(item)) }
                } else if (!(item.isGroup && grouped.hasChildren)) {
                    Box(Modifier.size(12.dp).background(itemKindColor(item), shape = androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    if (grouped.depth > 6) Text("階層 ${grouped.depth + 1}", style = MaterialTheme.typography.caption, color = parseColor(tokens.textSecondary))
                    Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(itemSubtitle(item), style = MaterialTheme.typography.caption, color = parseColor(tokens.textSecondary))
                }
                TextButton(onClick = { controller.openEdit(item.id) }, modifier = Modifier.height(48.dp)) { Text("${theme.icons.edit} 編集") }
            }
            if (restoreLabel != null && (item.groupId == null || item.isGroup)) {
                OutlinedButton(
                    onClick = { controller.restore(item.id) },
                    modifier = Modifier.fillMaxWidth(if (addButtonPosition == AddButtonPosition.CENTER) .45f else 1f).height(48.dp),
                ) { Text(restoreLabel) }
            } else if (restoreLabel == null && item.isGroup) {
                val label = if (item.kind == ItemKind.TODO) "グループを完了" else "グループをしまう"
                OutlinedButton(
                    onClick = { if (item.kind == ItemKind.TODO) controller.complete(item.id) else controller.archiveMemo(item.id) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun itemKindColor(item: AppItem): Color = LocalThemeColors.current.colorForKind(item.kind)

private fun itemSubtitle(item: AppItem): String = when {
    item.isGroup -> buildString {
        append(if (item.kind == ItemKind.TODO) "やることグループ" else "メモグループ")
        item.todo?.recurrence?.let { rule ->
            append(" ・ ")
            append(recurrenceSummary(rule.unit, rule.interval))
        }
    }
    item.kind == ItemKind.UNSORTED -> "あとで分ける"
    item.kind == ItemKind.MEMO -> if (item.body.isBlank()) "メモ" else item.body.take(45)
    else -> buildString {
        append(item.todo?.priority?.label ?: "指定なし")
        item.todo?.estimatedMinutes?.let { append(" ・ 約${it}分") }
        if ((item.todo?.deferCount ?: 0) > 0) append(" ・ 後で行う ${item.todo?.deferCount}回")
        item.todo?.recurrence?.let { append(" ・ ${recurrenceSummary(it.unit, it.interval)}") }
    }
}

private fun recurrenceSummary(unit: RecurrenceUnit, interval: Int): String = when {
    interval == 1 && unit == RecurrenceUnit.DAY -> "毎日"
    interval == 1 && unit == RecurrenceUnit.WEEK -> "毎週"
    interval == 1 && unit == RecurrenceUnit.MONTH -> "毎月"
    interval == 1 && unit == RecurrenceUnit.YEAR -> "毎年"
    else -> "$interval${unit.label}ごと"
}
