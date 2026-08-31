package jp.oboegaki.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import jp.oboegaki.core.model.AllSections
import jp.oboegaki.core.model.AppItem
import jp.oboegaki.core.model.AppSettings
import jp.oboegaki.core.model.ItemKind
import jp.oboegaki.core.model.Priority
import jp.oboegaki.core.model.RecurrenceRule
import jp.oboegaki.core.model.RecurrenceUnit
import jp.oboegaki.core.model.RelationType
import jp.oboegaki.core.model.ThemeDefinition
import jp.oboegaki.core.model.TodoDetail
import jp.oboegaki.core.domain.GroupPolicy
import jp.oboegaki.core.domain.OrderingPolicy
import jp.oboegaki.core.domain.DatePickerPolicy
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.time.Clock


@Composable
internal fun EditScreen(
    source: AppItem,
    sections: AllSections,
    relations: List<jp.oboegaki.core.model.ItemRelation>,
    settings: AppSettings,
    controller: AppController,
) {
    val allItems = allVisibleItems(sections)
    var kind by remember(source.id) { mutableStateOf(source.kind) }
    var title by remember(source.id) { mutableStateOf(source.title) }
    var body by remember(source.id) { mutableStateOf(source.body) }
    var groupId by remember(source.id) { mutableStateOf(source.groupId) }
    var priority by remember(source.id) { mutableStateOf(source.todo?.priority ?: Priority.NONE) }
    var estimated by remember(source.id) { mutableStateOf(source.todo?.estimatedMinutes?.toString() ?: "") }
    var available by remember(source.id) { mutableStateOf(source.todo?.availableFromEpochMillis) }
    var scheduled by remember(source.id) { mutableStateOf(source.todo?.scheduledAtEpochMillis) }
    var due by remember(source.id) { mutableStateOf(source.todo?.dueAtEpochMillis) }
    var recurrenceUnit by remember(source.id) { mutableStateOf(source.todo?.recurrence?.unit) }
    var recurrenceInterval by remember(source.id) { mutableStateOf(source.todo?.recurrence?.interval?.toString() ?: "1") }
    var recurrenceEnd by remember(source.id) { mutableStateOf(source.todo?.recurrence?.endAtEpochMillis) }
    val prerequisites = remember(source.id, relations) {
        mutableStateListOf<String>().apply {
            addAll(relations.filter { it.toItemId == source.id && it.type == RelationType.REQUIRED_BEFORE }.map { it.fromItemId })
        }
    }
    var classificationExpanded by remember(source.id) { mutableStateOf(false) }
    var orderingExpanded by remember(source.id) { mutableStateOf(false) }
    var scheduleExpanded by remember(source.id) { mutableStateOf(false) }
    val candidateGroups = GroupPolicy.availableParents(source.copy(kind = kind), allItems)
    LaunchedEffect(kind, candidateGroups) {
        if (kind == ItemKind.UNSORTED) groupId = null
        if (kind != ItemKind.TODO) recurrenceUnit = null
        if (groupId != null && candidateGroups.none { it.id == groupId }) groupId = null
    }

    fun draftItem(): AppItem {
        val detail = if (kind == ItemKind.TODO) (source.todo ?: TodoDetail()).copy(
            priority = priority,
            estimatedMinutes = estimated.toIntOrNull()?.coerceIn(1, 1440),
            availableFromEpochMillis = available,
            scheduledAtEpochMillis = scheduled,
            dueAtEpochMillis = due,
            recurrence = recurrenceUnit?.let {
                RecurrenceRule(it, recurrenceInterval.toIntOrNull()?.coerceIn(1, 999) ?: 1, recurrenceEnd)
            },
        ) else null
        return source.copy(kind = kind, title = title, body = body, groupId = groupId, todo = detail)
    }

    fun save() {
        controller.save(
            draftItem(),
            if (kind == ItemKind.TODO) prerequisites.toSet() else emptySet(),
        )
    }

    OverlayScaffold(
        title = "編集",
        onClose = controller::closeOverlay,
        showHeaderClose = false,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
            item {
                OutlinedTextField(title, { title = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("タイトル") })
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(body, { body = it.take(100_000) }, Modifier.fillMaxWidth(), label = { Text("本文") }, minLines = 4, maxLines = 12)
            }
            item {
                CollapsibleEditSection(
                    title = "分類とグループ",
                    summary = when {
                        source.isGroup -> if (kind == ItemKind.TODO) "やることグループ" else "メモグループ"
                        kind == ItemKind.TODO -> "やること${groupId?.let { "・グループあり" } ?: "・グループなし"}"
                        kind == ItemKind.MEMO -> "メモ${groupId?.let { "・グループあり" } ?: "・グループなし"}"
                        else -> "あとで分ける"
                    },
                    expanded = classificationExpanded,
                    onToggle = { classificationExpanded = !classificationExpanded },
                ) {
                    if (!source.isGroup) {
                        Text("種類", style = MaterialTheme.typography.subtitle1)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            KindChoice("あとで分ける", ItemKind.UNSORTED, kind) { kind = it }
                            KindChoice("やること", ItemKind.TODO, kind) { kind = it }
                            KindChoice("メモ", ItemKind.MEMO, kind) { kind = it }
                        }
                    }
                    if (kind != ItemKind.UNSORTED) {
                        Spacer(Modifier.height(12.dp))
                        GroupPicker(
                            title = "入れるグループ",
                            selectedGroupId = groupId,
                            groups = candidateGroups,
                            allItems = allItems,
                            onSelect = { groupId = it },
                        )
                    }
                }
            }
            if (kind == ItemKind.TODO) item {
                CollapsibleEditSection(
                    title = "順番と目安",
                    summary = buildString {
                        append("優先度: ${priority.label}")
                        if (estimated.isBlank()) append("・予想時間なし") else append("・約${estimated}分")
                        append("・先に終える ${prerequisites.size}件")
                    },
                    expanded = orderingExpanded,
                    onToggle = { orderingExpanded = !orderingExpanded },
                ) {
                    Text("優先度", style = MaterialTheme.typography.subtitle1)
                    PriorityChoices(priority) { priority = it }
                    if (!source.isGroup) {
                        Spacer(Modifier.height(12.dp))
                        Text("先に終えるやること", style = MaterialTheme.typography.subtitle1)
                        val candidates = OrderingPolicy.prerequisiteCandidates(allItems, groupId, source.id)
                        if (candidates.isEmpty()) Text("選べるやることはありません", style = MaterialTheme.typography.caption)
                        candidates.forEach { candidate ->
                            Row(
                                Modifier.fillMaxWidth().height(52.dp).clickable {
                                    if (candidate.id in prerequisites) prerequisites.remove(candidate.id) else prerequisites.add(candidate.id)
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(candidate.id in prerequisites, onCheckedChange = {
                                    if (it) { if (candidate.id !in prerequisites) prerequisites.add(candidate.id) }
                                    else prerequisites.remove(candidate.id)
                                })
                                Text(candidate.title, Modifier.weight(1f), maxLines = 2)
                            }
                        }
                        Text("循環する前後関係は保存できません", style = MaterialTheme.typography.caption)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        estimated, { estimated = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(),
                        label = { Text("予想時間（1〜1440分）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
            if (kind == ItemKind.TODO) item {
                CollapsibleEditSection(
                    title = "日時と繰り返し",
                    summary = buildString {
                        append(if (scheduled == null) "行う時刻なし" else "行う時刻あり")
                        if (recurrenceUnit != null) append("・${recurrenceUnit!!.label}ごと")
                        if (due != null) append("・期限あり")
                    },
                    expanded = scheduleExpanded,
                    onToggle = { scheduleExpanded = !scheduleExpanded },
                ) {
                    DateTimeField("いつからできる？", available, settings.reducedMotion) { available = it }
                    DateTimeField("行う時刻", scheduled, settings.reducedMotion) { scheduled = it }
                    DateTimeField("期限", due, settings.reducedMotion) { due = it }
                    RecurrenceEditor(
                        unit = recurrenceUnit,
                        interval = recurrenceInterval,
                        endAt = recurrenceEnd,
                        scheduledAt = scheduled,
                        onUnitChange = { recurrenceUnit = it },
                        onIntervalChange = { recurrenceInterval = it },
                        onEndChange = { recurrenceEnd = it },
                        reducedMotion = settings.reducedMotion,
                    )
                    if (settings.calendarIntegrationEnabled) {
                        val hasCalendarDate = scheduled != null || due != null || available != null
                        OutlinedButton(
                            onClick = { controller.addToCalendar(draftItem()) },
                            enabled = title.isNotBlank() && hasCalendarDate,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) { Text("端末のカレンダーに追加") }
                        Text(
                            if (hasCalendarDate) "Google カレンダー、Outlookなど、端末に設定されたカレンダーへ追加します。追加先の選択方法は端末により異なります。"
                            else "行う時刻、期限、または開始可能日時を設定すると追加できます。",
                            style = MaterialTheme.typography.caption,
                            color = parseColor(LocalThemeColors.current.textSecondary),
                        )
                    }
                    if ((source.todo?.deferCount ?: 0) > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text("${source.todo?.deferCount}回 後で行うことにしています")
                        Text("小さく分ける提案は後回し回数に応じて表示されます", style = MaterialTheme.typography.caption)
                    }
                }
            }
            item {
                Divider()
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { controller.delete(source.id) }, Modifier.fillMaxWidth().height(48.dp)) {
                    Text("削除", color = MaterialTheme.colors.error)
                }
                Spacer(Modifier.height(20.dp))
            }
            }
            Divider()
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = controller::closeOverlay,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("閉じる") }
                Button(
                    onClick = ::save,
                    enabled = title.isNotBlank() && (recurrenceUnit == null || scheduled != null),
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("保存") }
            }
        }
    }
}

@Composable
private fun CollapsibleEditSection(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalAppTheme.current.mediumCornerDp.dp),
        elevation = 1.dp,
        backgroundColor = parseColor(LocalThemeColors.current.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            OutlinedButton(onClick = onToggle, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text(title, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text(summary, style = MaterialTheme.typography.caption, maxLines = 2)
                    }
                    Text(if (expanded) "⌃" else "⌄", style = MaterialTheme.typography.h6)
                }
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
internal fun GroupPicker(
    title: String,
    selectedGroupId: String?,
    groups: List<AppItem>,
    allItems: List<AppItem>,
    onSelect: (String?) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.subtitle1)
        Spacer(Modifier.height(6.dp))
        if (selectedGroupId == null) {
            Button(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("グループなし") }
        } else {
            OutlinedButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("グループなし") }
        }
        groups.forEach { group ->
            val breadcrumb = GroupPolicy.breadcrumb(group, allItems)
            val label = if (breadcrumb.isBlank()) group.title else "$breadcrumb › ${group.title}"
            Spacer(Modifier.height(6.dp))
            if (selectedGroupId == group.id) {
                Button(onClick = { onSelect(group.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(label, textAlign = TextAlign.Start)
                }
            } else {
                OutlinedButton(onClick = { onSelect(group.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(label, textAlign = TextAlign.Start)
                }
            }
        }
        if (groups.isEmpty()) {
            Text("作成済みのグループはありません", style = MaterialTheme.typography.caption, color = parseColor(LocalThemeColors.current.textSecondary))
        }
    }
}

@Composable
internal fun RecurrenceEditor(
    unit: RecurrenceUnit?,
    interval: String,
    endAt: Long?,
    scheduledAt: Long?,
    onUnitChange: (RecurrenceUnit?) -> Unit,
    onIntervalChange: (String) -> Unit,
    onEndChange: (Long?) -> Unit,
    reducedMotion: Boolean,
) {
    Column(Modifier.fillMaxWidth()) {
        Text("定期設定", style = MaterialTheme.typography.subtitle1)
        Text(
            "完了すると、次の予定が自動で作られます。グループは中身もまとめて複製します。",
            style = MaterialTheme.typography.caption,
            color = parseColor(LocalThemeColors.current.textSecondary),
        )
        Spacer(Modifier.height(8.dp))
        val choices = listOf<Pair<String, RecurrenceUnit?>>() + listOf(
            "設定しない" to null,
            "日ごと" to RecurrenceUnit.DAY,
            "週ごと" to RecurrenceUnit.WEEK,
            "月ごと" to RecurrenceUnit.MONTH,
            "年ごと" to RecurrenceUnit.YEAR,
        )
        choices.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (label, value) ->
                    if (value == unit) Button(onClick = { onUnitChange(value) }, Modifier.weight(1f).height(48.dp)) { Text(label) }
                    else OutlinedButton(onClick = { onUnitChange(value) }, Modifier.weight(1f).height(48.dp)) { Text(label) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
        }
        if (unit != null) {
            OutlinedTextField(
                value = interval,
                onValueChange = { onIntervalChange(it.filter(Char::isDigit).take(3)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("何${unit.label}ごと（1〜999）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            DateOnlyField("繰り返しの終了日", endAt, reducedMotion, onEndChange)
            if (scheduledAt == null) {
                Text(
                    "定期設定を使うには「行う時刻」を設定してください",
                    color = MaterialTheme.colors.error,
                    style = MaterialTheme.typography.caption,
                )
            }
        }
    }
}
