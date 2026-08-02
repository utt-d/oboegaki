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
import androidx.compose.foundation.lazy.itemsIndexed
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
import jp.oboegaki.core.model.AddButtonPosition
import jp.oboegaki.core.model.AllSections
import jp.oboegaki.core.model.AppItem
import jp.oboegaki.core.model.ItemKind

@Composable
fun AllItemsScreen(
    sections: AllSections,
    addButtonPosition: AddButtonPosition,
    controller: AppController,
) {
    val expanded = remember { mutableStateMapOf(
        "unsorted" to true, "todos" to true, "memos" to true, "completed" to true, "archived" to true,
    ) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        section("あとで分ける", "unsorted", sections.unsorted, expanded, controller, reorderable = true)
        section("やること", "todos", sections.todos, expanded, controller, reorderable = true)
        section("メモ", "memos", sections.memos, expanded, controller, reorderable = true)
        section("完了したこと", "completed", sections.completed, expanded, controller, reorderable = false, restoreLabel = "やることへ戻す")
        section("しまったメモ", "archived", sections.archived, expanded, controller, reorderable = false, restoreLabel = "メモへ戻す")
        item {
            Spacer(Modifier.height(88.dp))
            OutlinedButton(
                onClick = controller::openDataTools,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (addButtonPosition == AddButtonPosition.LEFT) 82.dp else 0.dp,
                        end = if (addButtonPosition == AddButtonPosition.RIGHT) 82.dp else 0.dp,
                    )
                    .height(48.dp),
            ) {
                Text("データのバックアップと読み込み")
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    key: String,
    values: List<AppItem>,
    expanded: MutableMap<String, Boolean>,
    controller: AppController,
    reorderable: Boolean,
    restoreLabel: String? = null,
) {
    item(key = "header-$key") {
        Row(
            Modifier.fillMaxWidth().clickable { expanded[key] = expanded[key] != true }.padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (expanded[key] == true) "⌄" else "›", style = MaterialTheme.typography.h6)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.h6, modifier = Modifier.weight(1f))
            Text("${values.size}件", style = MaterialTheme.typography.caption, color = parseColor(LocalThemeColors.current.textSecondary))
        }
    }
    if (expanded[key] == true) {
        if (values.isEmpty()) item(key = "empty-$key") {
            Text("項目はありません", Modifier.fillMaxWidth().padding(14.dp), color = parseColor(LocalThemeColors.current.textSecondary))
        }
        itemsIndexed(values, key = { _, item -> "$key-${item.id}" }) { index, item ->
            AllItemRow(item, index, values.size, reorderable, restoreLabel, controller)
        }
    }
}

@Composable
private fun AllItemRow(
    item: AppItem,
    index: Int,
    count: Int,
    reorderable: Boolean,
    restoreLabel: String?,
    controller: AppController,
) {
    val theme = LocalAppTheme.current
    val tokens = LocalThemeColors.current
    val density = LocalDensity.current
    var dragTotal by remember(item.id) { mutableFloatStateOf(0f) }
    val customActions = buildList {
        add(CustomAccessibilityAction("編集") { controller.openEdit(item.id); true })
        if (reorderable && index > 0) add(CustomAccessibilityAction("上へ移動") {
            if (item.kind == ItemKind.TODO) controller.moveTodo(item.id, index - 1) else controller.moveFree(item.id, index - 1); true
        })
        if (reorderable && index < count - 1) add(CustomAccessibilityAction("下へ移動") {
            if (item.kind == ItemKind.TODO) controller.moveTodo(item.id, index + 1) else controller.moveFree(item.id, index + 1); true
        })
    }
    Card(
        modifier = Modifier.fillMaxWidth().semantics { this.customActions = customActions }.clickable { controller.openEdit(item.id) },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(theme.mediumCornerDp.dp),
        elevation = 1.dp,
        backgroundColor = parseColor(tokens.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                                if (dragTotal < -step && index > 0) {
                                    if (item.kind == ItemKind.TODO) controller.moveTodo(item.id, index - 1)
                                    else controller.moveFree(item.id, index - 1)
                                    dragTotal = 0f
                                } else if (dragTotal > step && index < count - 1) {
                                    if (item.kind == ItemKind.TODO) controller.moveTodo(item.id, index + 1)
                                    else controller.moveFree(item.id, index + 1)
                                    dragTotal = 0f
                                }
                            }
                        },
                        contentAlignment = Alignment.Center,
                    ) { Text(LocalAppTheme.current.icons.all, style = MaterialTheme.typography.h6, color = itemKindColor(item)) }
                } else {
                    Box(Modifier.size(12.dp).background(itemKindColor(item), shape = androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(itemSubtitle(item), style = MaterialTheme.typography.caption, color = parseColor(tokens.textSecondary))
                }
                TextButton(onClick = { controller.openEdit(item.id) }, modifier = Modifier.height(48.dp)) { Text("${theme.icons.edit} 編集") }
            }
            restoreLabel?.let {
                OutlinedButton(onClick = { controller.restore(item.id) }, Modifier.fillMaxWidth().height(48.dp)) { Text(it) }
            }
        }
    }
}

@Composable
private fun itemKindColor(item: AppItem): Color = LocalThemeColors.current.colorForKind(item.kind)

private fun itemSubtitle(item: AppItem): String = when (item.kind) {
    ItemKind.UNSORTED -> "あとで分ける"
    ItemKind.MEMO -> if (item.body.isBlank()) "メモ" else item.body.take(45)
    ItemKind.TODO -> buildString {
        append(item.todo?.priority?.label ?: "指定なし")
        item.todo?.estimatedMinutes?.let { append(" ・ 約${it}分") }
        if ((item.todo?.deferCount ?: 0) > 0) append(" ・ 後で行う ${item.todo?.deferCount}回")
    }
}
