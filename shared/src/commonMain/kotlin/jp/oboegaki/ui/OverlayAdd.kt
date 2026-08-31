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
internal fun AddScreen(
    kind: ItemKind,
    onKindChange: (ItemKind) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    destinationExpanded: Boolean,
    onDestinationExpandedChange: (Boolean) -> Unit,
    handleModifier: Modifier,
    onClose: () -> Unit,
    sections: AllSections,
    settings: AppSettings,
    controller: AppController,
) {
    val allItems = allVisibleItems(sections)
    var text by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var isGroup by remember { mutableStateOf(false) }
    var groupId by remember { mutableStateOf<String?>(null) }
    var priority by remember { mutableStateOf(Priority.NONE) }
    var estimated by remember { mutableStateOf("15") }
    var available by remember { mutableStateOf<Long?>(null) }
    var scheduled by remember { mutableStateOf<Long?>(null) }
    var due by remember { mutableStateOf<Long?>(null) }
    var recurrenceUnit by remember { mutableStateOf<RecurrenceUnit?>(null) }
    var recurrenceInterval by remember { mutableStateOf("1") }
    var recurrenceEnd by remember { mutableStateOf<Long?>(null) }
    val prerequisites = remember { mutableStateListOf<String>() }

    val availableGroups = when (kind) {
        ItemKind.TODO -> sections.todoGroups
        ItemKind.MEMO -> sections.memoGroups
        ItemKind.UNSORTED -> emptyList()
    }
    LaunchedEffect(kind, availableGroups) {
        if (kind == ItemKind.UNSORTED) {
            isGroup = false
            groupId = null
        }
        if (kind != ItemKind.TODO) recurrenceUnit = null
        if (groupId != null && availableGroups.none { it.id == groupId }) groupId = null
    }

    fun todoDetail() = TodoDetail(
        priority = priority,
        estimatedMinutes = estimated.toIntOrNull()?.coerceIn(1, 1440),
        availableFromEpochMillis = available,
        scheduledAtEpochMillis = scheduled,
        dueAtEpochMillis = due,
        recurrence = recurrenceUnit?.let {
            RecurrenceRule(it, recurrenceInterval.toIntOrNull()?.coerceIn(1, 999) ?: 1, recurrenceEnd)
        },
    )

    fun submit() {
        if (isGroup) {
            controller.createGroup(
                kind,
                text,
                groupId,
                if (kind == ItemKind.TODO) todoDetail() else null,
                prerequisites.toSet(),
            )
            return
        }
        if (expanded && kind == ItemKind.TODO) {
            controller.addDetailed(
                kind = kind,
                title = text,
                body = body,
                groupId = groupId,
                detail = todoDetail(),
                requiredBeforeIds = prerequisites.toSet(),
            ) { text = "" }
        } else {
            controller.quickAdd(kind, text, groupId) { text = "" }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(handleModifier)
                .padding(top = 10.dp, bottom = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.width(48.dp).height(5.dp).background(
                        parseColor(LocalThemeColors.current.border),
                        RoundedCornerShape(999.dp),
                    ),
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    if (kind == ItemKind.TODO && !expanded) "下に引くと閉じる・上に引くと詳細" else "下に引くと閉じる",
                    style = MaterialTheme.typography.caption,
                    color = parseColor(LocalThemeColors.current.textSecondary),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("追加", style = MaterialTheme.typography.h6, modifier = Modifier.weight(1f).padding(start = 10.dp))
            TextButton(onClick = onClose, modifier = Modifier.height(48.dp)) { Text("閉じる") }
        }
        Divider()
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 680.dp),
            contentPadding = PaddingValues(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("思いついたこと") },
                    placeholder = { Text("種類や日時はあとから決められます") },
                    minLines = 2,
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(imeAction = if (settings.addWithEnter) ImeAction.Done else ImeAction.Default),
                    keyboardActions = KeyboardActions(onDone = { if (settings.addWithEnter) submit() }),
                )
            }
            item {
                Button(
                    onClick = ::submit,
                    enabled = text.isNotBlank() && (recurrenceUnit == null || scheduled != null),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) { Text("追加する") }
            }
            item {
                val destinationSummary = when (kind) {
                    ItemKind.UNSORTED -> "あとで分ける"
                    ItemKind.TODO -> "やること"
                    ItemKind.MEMO -> "メモ"
                }.let { base ->
                    groupId?.let { selectedGroupId ->
                        val groupTitle = allItems.firstOrNull { it.id == selectedGroupId && it.isGroup }?.title
                        if (groupTitle.isNullOrBlank()) "$base（グループを確認できません）"
                        else "$base（${groupTitle}）"
                    } ?: base
                }
                OutlinedButton(
                    onClick = { onDestinationExpandedChange(!destinationExpanded) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                ) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                        Text("追加先: $destinationSummary")
                        Text(
                            if (destinationExpanded) "種類やグループを閉じる" else "種類やグループを変更する",
                            style = MaterialTheme.typography.caption,
                            color = parseColor(LocalThemeColors.current.textSecondary),
                        )
                    }
                }
            }
            if (destinationExpanded) {
                item {
                    Text("追加先", style = MaterialTheme.typography.subtitle1)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        KindChoice("あとで分ける", ItemKind.UNSORTED, kind, onKindChange)
                        KindChoice("やること", ItemKind.TODO, kind, onKindChange)
                        KindChoice("メモ", ItemKind.MEMO, kind, onKindChange)
                    }
                }
            }
            if (destinationExpanded && kind != ItemKind.UNSORTED) {
                item {
                    GroupPicker(
                        title = "入れるグループ",
                        selectedGroupId = groupId,
                        groups = availableGroups,
                        allItems = allItems,
                        onSelect = { groupId = it },
                    )
                    Row(
                        Modifier.fillMaxWidth().height(52.dp).clickable { isGroup = !isGroup },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(isGroup, onCheckedChange = { isGroup = it })
                        Column(Modifier.weight(1f)) {
                            Text(if (kind == ItemKind.TODO) "やることグループとして追加" else "メモグループとして追加")
                            Text("中に項目やグループを何階層でも追加できます", style = MaterialTheme.typography.caption)
                        }
                    }
                }
            }
            if (kind == ItemKind.TODO) {
                item {
                    OutlinedButton(onClick = { onExpandedChange(!expanded) }, Modifier.fillMaxWidth().height(48.dp)) {
                        Text(if (expanded) "詳細を閉じる" else "やることを詳しく設定")
                    }
                }
            }
            if (expanded && kind == ItemKind.TODO) {
                item {
                    OutlinedTextField(
                        body,
                        { body = it.take(100_000) },
                        Modifier.fillMaxWidth(),
                        label = { Text("メモ・補足") },
                        minLines = 3,
                        maxLines = 10,
                    )
                }
                item {
                    Text("優先度", style = MaterialTheme.typography.subtitle1)
                    PriorityChoices(priority) { priority = it }
                }
                if (!isGroup) item {
                    Text("先に終えるやること", style = MaterialTheme.typography.subtitle1)
                    val candidates = OrderingPolicy.prerequisiteCandidates(allItems, groupId)
                    if (candidates.isEmpty()) {
                        Text("選べるやることはありません", style = MaterialTheme.typography.caption)
                    }
                    candidates.forEach { candidate ->
                        Row(
                            Modifier.fillMaxWidth().height(52.dp).clickable {
                                if (candidate.id in prerequisites) prerequisites.remove(candidate.id) else prerequisites.add(candidate.id)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(candidate.id in prerequisites, onCheckedChange = {
                                if (it) {
                                    if (candidate.id !in prerequisites) prerequisites.add(candidate.id)
                                } else prerequisites.remove(candidate.id)
                            })
                            Text(candidate.title, Modifier.weight(1f), maxLines = 2)
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = estimated,
                        onValueChange = { estimated = it.filter(Char::isDigit).take(4) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("予想時間（分）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                item {
                    DateTimeField("いつからできる？", available, settings.reducedMotion) { available = it }
                    DateTimeField("行う時刻", scheduled, settings.reducedMotion) { scheduled = it }
                    DateTimeField("期限", due, settings.reducedMotion) { due = it }
                }
                item {
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
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
internal fun RowScope.KindChoice(label: String, value: ItemKind, selected: ItemKind, onSelect: (ItemKind) -> Unit) {
    val modifier = Modifier.weight(1f).height(56.dp)
    if (value == selected) Button(onClick = { onSelect(value) }, modifier) { Text(label, textAlign = TextAlign.Center) }
    else OutlinedButton(onClick = { onSelect(value) }, modifier) { Text(label, textAlign = TextAlign.Center) }
}
