package jp.oboegaki.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
fun OverlayHost(
    overlay: AppOverlay,
    sections: AllSections,
    settings: AppSettings,
    themes: List<ThemeDefinition>,
    controller: AppController,
) {
    val relations by controller.relations.collectAsState()
    if (overlay is AppOverlay.Add) {
        AddBottomSheet(overlay.defaultKind, sections, settings, controller)
        return
    }
    Surface(
        Modifier.fillMaxSize().safeDrawingPadding(),
        color = MaterialTheme.colors.background,
        elevation = 16.dp,
    ) {
        when (overlay) {
            is AppOverlay.Add -> Unit
            is AppOverlay.Edit -> {
                val item = allVisibleItems(sections).firstOrNull { it.id == overlay.itemId }
                if (item == null) MissingOverlay(controller) else EditScreen(item, sections, relations, settings, controller)
            }
            is AppOverlay.Split -> {
                val item = allVisibleItems(sections).firstOrNull { it.id == overlay.itemId }
                if (item == null) MissingOverlay(controller) else SplitScreen(item, controller)
            }
            AppOverlay.Settings -> SettingsScreen(settings, controller)
            AppOverlay.Themes -> ThemeListScreen(themes, settings, controller)
            is AppOverlay.ThemeEditor -> ThemeEditorScreen(overlay.theme, controller)
            AppOverlay.DataTools -> DataToolsScreen(controller)
            is AppOverlay.OperationGuide -> OperationGuideScreen(
                firstLaunch = overlay.firstLaunch,
                addButtonPosition = settings.addButtonPosition,
                controller = controller,
            )
        }
    }
}

@Composable
private fun AddBottomSheet(
    defaultKind: ItemKind,
    sections: AllSections,
    settings: AppSettings,
    controller: AppController,
) {
    val visible = remember {
        MutableTransitionState(false).apply { targetState = true }
    }
    val sheetInteraction = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    var kind by remember { mutableStateOf(defaultKind) }
    var expanded by remember { mutableStateOf(false) }
    var destinationExpanded by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val closeThreshold = with(density) { 72.dp.toPx() }
    val expandThreshold = with(density) { 36.dp.toPx() }
    val maxDown = with(density) { 140.dp.toPx() }
    val maxUp = with(density) { 48.dp.toPx() }
    val handleModifier = Modifier.pointerInput(kind, expanded) {
        detectVerticalDragGestures(
            onDragStart = { dragOffset = 0f },
            onDragCancel = { dragOffset = 0f },
            onDragEnd = {
                when {
                    dragOffset >= closeThreshold -> controller.closeOverlay()
                    dragOffset <= -expandThreshold && kind == ItemKind.TODO -> {
                        expanded = true
                        destinationExpanded = true
                    }
                }
                dragOffset = 0f
            },
        ) { change, amount ->
            change.consume()
            dragOffset = (dragOffset + amount).coerceIn(-maxUp, maxDown)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .32f))
            .clickable(onClick = controller::closeOverlay),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, dragOffset.roundToInt()) }
                    .clickable(
                        interactionSource = sheetInteraction,
                        indication = null,
                        onClick = {},
                    ),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colors.background,
                elevation = 20.dp,
            ) {
                AddScreen(
                    kind = kind,
                    onKindChange = {
                        kind = it
                        if (it != ItemKind.TODO) expanded = false
                    },
                    expanded = expanded,
                    onExpandedChange = {
                        expanded = it && kind == ItemKind.TODO
                        if (it) destinationExpanded = true
                    },
                    destinationExpanded = destinationExpanded,
                    onDestinationExpandedChange = { destinationExpanded = it },
                    handleModifier = handleModifier,
                    sections = sections,
                    settings = settings,
                    controller = controller,
                )
            }
        }
    }
}

@Composable
fun OverlayScaffold(
    title: String,
    onClose: () -> Unit,
    action: (@Composable () -> Unit)? = null,
    showHeaderClose: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showHeaderClose) {
                TextButton(onClick = onClose, modifier = Modifier.height(48.dp)) { Text("閉じる") }
            } else {
                Spacer(Modifier.width(72.dp))
            }
            Text(title, style = MaterialTheme.typography.h6, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Box(Modifier.width(72.dp), contentAlignment = Alignment.CenterEnd) { action?.invoke() }
        }
        Divider()
        content(PaddingValues(horizontal = 18.dp, vertical = 16.dp))
    }
}

@Composable
private fun AddScreen(
    kind: ItemKind,
    onKindChange: (ItemKind) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    destinationExpanded: Boolean,
    onDestinationExpandedChange: (Boolean) -> Unit,
    handleModifier: Modifier,
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
            TextButton(onClick = controller::closeOverlay, modifier = Modifier.height(48.dp)) { Text("閉じる") }
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
private fun RowScope.KindChoice(label: String, value: ItemKind, selected: ItemKind, onSelect: (ItemKind) -> Unit) {
    val modifier = Modifier.weight(1f).height(56.dp)
    if (value == selected) Button(onClick = { onSelect(value) }, modifier) { Text(label, textAlign = TextAlign.Center) }
    else OutlinedButton(onClick = { onSelect(value) }, modifier) { Text(label, textAlign = TextAlign.Center) }
}

@Composable
private fun EditScreen(
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
            if (!source.isGroup) {
                item {
                    Text("種類", style = MaterialTheme.typography.subtitle1)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        KindChoice("あとで分ける", ItemKind.UNSORTED, kind) { kind = it }
                        KindChoice("やること", ItemKind.TODO, kind) { kind = it }
                        KindChoice("メモ", ItemKind.MEMO, kind) { kind = it }
                    }
                }
            } else item {
                Text(if (kind == ItemKind.TODO) "やることグループ" else "メモグループ", style = MaterialTheme.typography.subtitle1)
            }
            item {
                OutlinedTextField(title, { title = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("タイトル") })
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(body, { body = it.take(100_000) }, Modifier.fillMaxWidth(), label = { Text("本文") }, minLines = 4, maxLines = 12)
            }
            if (kind != ItemKind.UNSORTED) item {
                GroupPicker(
                    title = "入れるグループ",
                    selectedGroupId = groupId,
                    groups = candidateGroups,
                    allItems = allItems,
                    onSelect = { groupId = it },
                )
            }
            if (kind == ItemKind.TODO) {
                item {
                    Text("優先度", style = MaterialTheme.typography.subtitle1)
                    PriorityChoices(priority) { priority = it }
                }
                if (!source.isGroup) item {
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
                item {
                    OutlinedTextField(
                        estimated, { estimated = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(),
                        label = { Text("予想時間（1〜1440分）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                if (settings.calendarIntegrationEnabled) item {
                    val hasCalendarDate = scheduled != null || due != null || available != null
                    OutlinedButton(
                        onClick = { controller.addToCalendar(draftItem()) },
                        enabled = title.isNotBlank() && hasCalendarDate,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text("端末のカレンダーに追加")
                    }
                    Text(
                        if (hasCalendarDate) {
                            "Google カレンダー、Outlookなど、端末に設定されたカレンダーへ追加します。追加先の選択方法は端末により異なります。"
                        } else {
                            "行う時刻、期限、または開始可能日時を設定すると追加できます。"
                        },
                        style = MaterialTheme.typography.caption,
                        color = parseColor(LocalThemeColors.current.textSecondary),
                    )
                }
                if ((source.todo?.deferCount ?: 0) > 0) item {
                    Text("${source.todo?.deferCount}回 後で行うことにしています")
                    OutlinedButton(onClick = { controller.closeOverlay(); controller.openThemeEditor(jp.oboegaki.core.data.BuiltInThemes.standard) }, enabled = false) {
                        Text("小さく分ける提案は後回し回数に応じて表示されます")
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
private fun GroupPicker(
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
private fun RecurrenceEditor(
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

@Composable
private fun DateOnlyField(
    label: String,
    value: Long?,
    reducedMotion: Boolean,
    onChange: (Long?) -> Unit,
) {
    var selectingDate by remember { mutableStateOf(false) }
    val local = value?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.subtitle1)
        OutlinedButton(onClick = { selectingDate = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(local?.let(::formatDatePart) ?: "終了日を設定しない")
        }
        if (value != null) TextButton(onClick = { onChange(null) }, modifier = Modifier.align(Alignment.End).height(42.dp)) {
            Text("終了日を解除")
        }
    }
    if (selectingDate) CalendarDatePickerDialog(
        label = label,
        value = value,
        reducedMotion = reducedMotion,
        onDismiss = { selectingDate = false },
        onConfirm = { date ->
            onChange(replaceDate(value, date))
            selectingDate = false
        },
    )
}

@Composable
private fun DateTimeField(
    label: String,
    value: Long?,
    reducedMotion: Boolean,
    onChange: (Long?) -> Unit,
) {
    var selectingDate by remember { mutableStateOf(false) }
    var selectingTime by remember { mutableStateOf(false) }
    val local = value?.let {
        Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault())
    }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.subtitle1)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { selectingDate = true },
                modifier = Modifier.weight(1.25f).height(52.dp),
            ) {
                Text(local?.let(::formatDatePart) ?: "日付を選ぶ", textAlign = TextAlign.Center)
            }
            OutlinedButton(
                onClick = { selectingTime = true },
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Text(local?.let(::formatTimePart) ?: "時刻を選ぶ", textAlign = TextAlign.Center)
            }
        }
        if (value == null) {
            Text(
                "設定なし",
                style = MaterialTheme.typography.caption,
                color = parseColor(LocalThemeColors.current.textSecondary),
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            TextButton(onClick = { onChange(null) }, modifier = Modifier.align(Alignment.End).height(42.dp)) {
                Text("日時を解除")
            }
        }
    }

    if (selectingDate) {
        CalendarDatePickerDialog(
            label = label,
            value = value,
            reducedMotion = reducedMotion,
            onDismiss = { selectingDate = false },
            onConfirm = {
                onChange(replaceDate(value, it))
                selectingDate = false
            },
        )
    }
    if (selectingTime) {
        ClockTimePickerDialog(
            label = label,
            value = value,
            onDismiss = { selectingTime = false },
            onConfirm = { hour, minute ->
                onChange(replaceTime(value, hour, minute))
                selectingTime = false
            },
        )
    }
}

@Composable
private fun CalendarDatePickerDialog(
    label: String,
    value: Long?,
    reducedMotion: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val initial = localDateTime(value)
    var year by remember(value) { mutableStateOf(initial.year.coerceIn(DatePickerPolicy.FIRST_YEAR, DatePickerPolicy.LAST_YEAR)) }
    var month by remember(value) { mutableStateOf(initial.monthNumber) }
    var selectedDay by remember(value) {
        mutableStateOf(DatePickerPolicy.clampDay(year, month, initial.dayOfMonth))
    }
    var showingYears by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var horizontalDrag by remember { mutableFloatStateOf(0f) }
    var verticalDrag by remember { mutableFloatStateOf(0f) }
    var horizontalGesture by remember { mutableStateOf(false) }
    val settleOffset = remember { Animatable(0f) }
    val settleScope = rememberCoroutineScope()
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val density = LocalDensity.current
    val touchSlop = with(density) { 8.dp.toPx() }
    val distanceThreshold = with(density) { 56.dp.toPx() }

    fun monthAt(amount: Int): Pair<Int, Int>? {
        val shifted = DatePickerPolicy.shiftMonth(year, month, amount)
        return shifted.takeIf { it.year in DatePickerPolicy.FIRST_YEAR..DatePickerPolicy.LAST_YEAR }
            ?.let { it.year to it.month }
    }

    fun updateMonth(amount: Int) {
        monthAt(amount)?.let { (nextYear, nextMonth) ->
            year = nextYear
            month = nextMonth
            selectedDay = DatePickerPolicy.clampDay(year, month, selectedDay)
        }
    }

    fun cancelSettle() {
        settleJob?.cancel()
        settleJob = null
    }

    Dialog(onDismissRequest = { if (showingYears) showingYears = false else onDismiss() }) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colors.background,
            elevation = 24.dp,
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("${label}の日付", style = MaterialTheme.typography.h6)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { updateMonth(-1) }, modifier = Modifier.height(44.dp)) { Text("前月") }
                    TextButton(
                        onClick = { showingYears = true },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text("$year 年 $month 月", textAlign = TextAlign.Center, style = MaterialTheme.typography.subtitle1)
                    }
                    OutlinedButton(onClick = { updateMonth(1) }, modifier = Modifier.height(44.dp)) { Text("次月") }
                }
                if (showingYears) {
                    YearPicker(
                        selectedYear = year,
                        onSelect = {
                            year = it
                            selectedDay = DatePickerPolicy.clampDay(year, month, selectedDay)
                            showingYears = false
                        },
                        onBack = { showingYears = false },
                    )
                    PlatformBackHandler(enabled = true) { showingYears = false }
                } else {
                    BoxWithConstraints(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .pointerInput(year, month) {
                                detectDragGestures(
                                    onDragStart = {
                                        cancelSettle()
                                        dragging = true
                                        horizontalDrag = 0f
                                        verticalDrag = 0f
                                        horizontalGesture = false
                                    },
                                    onDragCancel = {
                                        cancelSettle()
                                        dragging = false
                                        horizontalDrag = 0f
                                        verticalDrag = 0f
                                    },
                                    onDragEnd = {
                                        val direction = when {
                                            horizontalGesture && horizontalDrag <= -distanceThreshold -> 1
                                            horizontalGesture && horizontalDrag >= distanceThreshold -> -1
                                            else -> 0
                                        }
                                        val canMove = direction != 0 && monthAt(direction) != null
                                        if (!canMove || direction == 0) {
                                            dragging = false
                                            horizontalDrag = 0f
                                            verticalDrag = 0f
                                        } else if (reducedMotion) {
                                            updateMonth(direction)
                                            dragging = false
                                            horizontalDrag = 0f
                                            verticalDrag = 0f
                                        } else {
                                            val width = size.width.toFloat().coerceAtLeast(1f)
                                            val target = if (direction > 0) -width else width
                                            settleJob = settleScope.launch {
                                                settleOffset.snapTo(horizontalDrag)
                                                settleOffset.animateTo(target, tween(200, easing = LinearEasing))
                                                updateMonth(direction)
                                                settleOffset.snapTo(0f)
                                                horizontalDrag = 0f
                                                verticalDrag = 0f
                                                dragging = false
                                                settleJob = null
                                            }
                                        }
                                    },
                                ) { change, amount ->
                                    val nextX = horizontalDrag + amount.x
                                    val nextY = verticalDrag + amount.y
                                    if (!horizontalGesture &&
                                        (abs(nextX) > touchSlop || abs(nextY) > touchSlop)
                                    ) {
                                        horizontalGesture = abs(nextX) > abs(nextY) * 1.25f
                                    }
                                    if (horizontalGesture) {
                                        change.consume()
                                        horizontalDrag = nextX
                                        verticalDrag = nextY
                                    }
                                }
                            },
                    ) {
                        val offset = if (dragging && settleJob != null) settleOffset.value else horizontalDrag
                        val width = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                        val direction = when {
                            offset < 0f -> 1
                            offset > 0f -> -1
                            else -> 0
                        }
                        val adjacent = direction.takeIf { it != 0 }?.let(::monthAt)
                        val pageWidth = maxWidth
                        val pageOffset = if (adjacent == null) 0f else offset
                        Box(Modifier.fillMaxWidth()) {
                            if (adjacent != null) {
                                val adjacentOffset = if (pageOffset < 0f) {
                                    width + pageOffset
                                } else {
                                    -width + pageOffset
                                }
                                CalendarMonthPage(
                                    adjacent.first,
                                    adjacent.second,
                                    Modifier
                                        .width(pageWidth)
                                        .offset { IntOffset(adjacentOffset.roundToInt(), 0) },
                                    DatePickerPolicy.clampDay(adjacent.first, adjacent.second, selectedDay),
                                ) {}
                            }
                            CalendarMonthPage(
                                year,
                                month,
                                Modifier
                                    .width(pageWidth)
                                    .offset { IntOffset(pageOffset.roundToInt(), 0) },
                                selectedDay,
                            ) { selectedDay = it }
                        }
                    }
                }
                Divider(Modifier.padding(top = 8.dp))
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(50.dp)) { Text("キャンセル") }
                    Button(
                        onClick = { onConfirm(LocalDate(year, month, selectedDay)) },
                        modifier = Modifier.weight(1f).height(50.dp),
                    ) { Text("この日を選ぶ") }
                }
            }
        }
    }
}

@Composable
private fun YearPicker(selectedYear: Int, onSelect: (Int) -> Unit, onBack: () -> Unit) {
    val years = remember { (DatePickerPolicy.FIRST_YEAR..DatePickerPolicy.LAST_YEAR).toList() }
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = (selectedYear - DatePickerPolicy.FIRST_YEAR).coerceIn(0, years.lastIndex),
    )
    LaunchedEffect(selectedYear) {
        state.scrollToItem((selectedYear - DatePickerPolicy.FIRST_YEAR).coerceIn(0, years.lastIndex))
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("年を選ぶ", style = MaterialTheme.typography.subtitle1, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack, modifier = Modifier.height(44.dp)) { Text("月に戻る") }
        }
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxWidth().height(300.dp),
            contentPadding = PaddingValues(vertical = 110.dp),
        ) {
            itemsIndexed(years) { _, value ->
                if (value == selectedYear) {
                    Button(onClick = { onSelect(value) }, Modifier.fillMaxWidth().height(48.dp)) { Text("$value 年") }
                } else {
                    TextButton(onClick = { onSelect(value) }, Modifier.fillMaxWidth().height(48.dp)) { Text("$value 年") }
                }
            }
        }
    }
}

@Composable
private fun CalendarMonthPage(
    year: Int,
    month: Int,
    modifier: Modifier = Modifier,
    selectedDay: Int,
    onDaySelected: (Int) -> Unit,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            listOf("月", "火", "水", "木", "金", "土", "日").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.caption)
            }
        }
        Spacer(Modifier.height(4.dp))
        val firstOffset = LocalDate(year, month, 1).dayOfWeek.ordinal
        val dayCount = DatePickerPolicy.daysInMonth(year, month)
        repeat(6) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { weekday ->
                    val day = week * 7 + weekday - firstOffset + 1
                    if (day in 1..dayCount) {
                        val modifier = Modifier.weight(1f).height(42.dp)
                        if (day == selectedDay) {
                            Button(onClick = { onDaySelected(day) }, modifier = modifier, contentPadding = PaddingValues(0.dp)) {
                                Text(day.toString())
                            }
                        } else {
                            TextButton(onClick = { onDaySelected(day) }, modifier = modifier, contentPadding = PaddingValues(0.dp)) {
                                Text(day.toString())
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f).height(42.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ClockTimePickerDialog(
    label: String,
    value: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val initial = localDateTime(value)
    var hour by remember(value) { mutableStateOf(initial.hour) }
    var minute by remember(value) { mutableStateOf(initial.minute) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colors.background,
            elevation = 24.dp,
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${label}の時刻", style = MaterialTheme.typography.h6, modifier = Modifier.fillMaxWidth())
                Text(
                    "時間と分を上下に動かして選びます",
                    style = MaterialTheme.typography.caption,
                    color = parseColor(LocalThemeColors.current.textSecondary),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "${hour.twoDigits()}:${minute.twoDigits()}",
                    style = MaterialTheme.typography.h4,
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeWheel(
                        label = "時",
                        values = 0..23,
                        selected = hour,
                        onSelected = { hour = it },
                        modifier = Modifier.weight(1f),
                    )
                    TimeWheel(
                        label = "分",
                        values = 0..59,
                        selected = minute,
                        onSelected = { minute = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text("よく使う時刻", style = MaterialTheme.typography.subtitle2, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(9 to 0, 12 to 0, 18 to 0).forEach { (quickHour, quickMinute) ->
                        OutlinedButton(
                            onClick = { hour = quickHour; minute = quickMinute },
                            modifier = Modifier.weight(1f).height(44.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) { Text("${quickHour.twoDigits()}:${quickMinute.twoDigits()}") }
                    }
                }
                Divider(Modifier.padding(top = 18.dp))
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(50.dp)) { Text("キャンセル") }
                    Button(onClick = { onConfirm(hour, minute) }, modifier = Modifier.weight(1f).height(50.dp)) {
                        Text("この時刻を選ぶ")
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeWheel(
    label: String,
    values: IntRange,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = remember(values) { values.toList() }
    val initialIndex = options.indexOf(selected).coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val scope = rememberCoroutineScope()

    LaunchedEffect(selected, options) {
        val selectedIndex = options.indexOf(selected)
        if (selectedIndex >= 0 && !state.isScrollInProgress && state.firstVisibleItemIndex != selectedIndex) {
            state.animateScrollToItem(selectedIndex)
        }
    }

    LaunchedEffect(state, options) {
        snapshotFlow { state.isScrollInProgress to state.firstVisibleItemIndex }
            .collect { (scrolling, index) ->
                if (!scrolling) options.getOrNull(index)?.let(onSelected)
            }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.subtitle2)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(parseColor(LocalThemeColors.current.surfaceAlt), RoundedCornerShape(14.dp))
                .border(1.dp, parseColor(LocalThemeColors.current.border), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 76.dp),
                flingBehavior = rememberSnapFlingBehavior(lazyListState = state),
            ) {
                itemsIndexed(options) { index, option ->
                    val isSelected = option == selected
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable {
                                onSelected(option)
                                scope.launch { state.animateScrollToItem(index) }
                            }
                            .background(
                                if (isSelected) parseColor(LocalThemeColors.current.accent).copy(alpha = .18f)
                                else Color.Transparent,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            option.twoDigits(),
                            style = if (isSelected) MaterialTheme.typography.h5 else MaterialTheme.typography.body1,
                            color = parseColor(
                                if (isSelected) LocalThemeColors.current.textPrimary else LocalThemeColors.current.textSecondary,
                            ),
                        )
                    }
                }
            }
            Column(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Divider()
                Divider()
            }
        }
    }
}

@Composable
private fun PriorityChoices(selected: Priority, onSelect: (Priority) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Priority.values().toList().chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { value ->
                    if (value == selected) Button(onClick = { onSelect(value) }, Modifier.weight(1f).height(48.dp)) { Text(value.label) }
                    else OutlinedButton(onClick = { onSelect(value) }, Modifier.weight(1f).height(48.dp)) { Text(value.label) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SplitScreen(item: AppItem, controller: AppController) {
    val titles = remember(item.id) { mutableStateListOf("準備する", "最初の部分を行う", "確認して終える") }
    OverlayScaffold("小さく分ける", controller::closeOverlay) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(item.title, style = MaterialTheme.typography.h6)
                Spacer(Modifier.height(8.dp))
                Text("何度か後で行うことにしています。最初の一歩に分けますか")
            }
            itemsIndexed(titles) { index, value ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", Modifier.width(28.dp), textAlign = TextAlign.Center)
                    OutlinedTextField(value, { titles[index] = it.take(200) }, Modifier.weight(1f), label = { Text("分けたやること") })
                    TextButton(onClick = { if (titles.size > 1) titles.removeAt(index) }, modifier = Modifier.height(48.dp)) { Text("削除") }
                }
            }
            item {
                OutlinedButton(onClick = { titles += "" }, Modifier.fillMaxWidth().height(48.dp)) { Text("項目を追加") }
                Spacer(Modifier.height(10.dp))
                Button(onClick = { controller.split(item.id, titles) }, Modifier.fillMaxWidth().height(52.dp)) { Text("分けて置き換える") }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { controller.postponeSplit(item.id) }, Modifier.fillMaxWidth().height(48.dp)) { Text("今回は分けない") }
                TextButton(onClick = { controller.disableSplit(item.id) }, Modifier.fillMaxWidth().height(48.dp)) { Text("このやることでは今後表示しない") }
                Spacer(Modifier.height(60.dp))
            }
        }
    }
}

@Composable
private fun MissingOverlay(controller: AppController) {
    OverlayScaffold("項目がありません", controller::closeOverlay) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("項目が移動または削除されました") }
    }
}

private fun allVisibleItems(sections: AllSections) =
    sections.unsorted + sections.todoGroups + sections.todos + sections.memoGroups + sections.memos + sections.completed + sections.archived

private fun localDateTime(epoch: Long?): LocalDateTime {
    val instant = epoch?.let(Instant::fromEpochMilliseconds)
        ?: Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
    return instant.toLocalDateTime(TimeZone.currentSystemDefault())
}

private fun replaceDate(epoch: Long?, date: LocalDate): Long {
    val current = localDateTime(epoch)
    val hour = if (epoch == null) 9 else current.hour
    val minute = if (epoch == null) 0 else current.minute
    return LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, minute)
        .toInstant(TimeZone.currentSystemDefault())
        .toEpochMilliseconds()
}

private fun replaceTime(epoch: Long?, hour: Int, minute: Int): Long {
    val current = localDateTime(epoch)
    return LocalDateTime(current.year, current.monthNumber, current.dayOfMonth, hour, minute)
        .toInstant(TimeZone.currentSystemDefault())
        .toEpochMilliseconds()
}

private fun formatDatePart(value: LocalDateTime): String =
    "${value.year}/${value.monthNumber.twoDigits()}/${value.dayOfMonth.twoDigits()}"

private fun formatTimePart(value: LocalDateTime): String =
    "${value.hour.twoDigits()}:${value.minute.twoDigits()}"

private fun Int.twoDigits(): String = toString().padStart(2, '0')
