package jp.oboegaki.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import jp.oboegaki.core.model.AllSections
import jp.oboegaki.core.model.AppItem
import jp.oboegaki.core.model.AppSettings
import jp.oboegaki.core.model.ItemKind
import jp.oboegaki.core.model.Priority
import jp.oboegaki.core.model.RelationType
import jp.oboegaki.core.model.ThemeDefinition
import jp.oboegaki.core.model.TodoDetail
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

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
        AddBottomSheet(overlay.defaultKind, sections.todos, settings, controller)
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
                if (item == null) MissingOverlay(controller) else EditScreen(item, sections.todos, relations, settings, controller)
            }
            is AppOverlay.Split -> {
                val item = allVisibleItems(sections).firstOrNull { it.id == overlay.itemId }
                if (item == null) MissingOverlay(controller) else SplitScreen(item, controller)
            }
            AppOverlay.Settings -> SettingsScreen(settings, controller)
            AppOverlay.Themes -> ThemeListScreen(themes, settings, controller)
            is AppOverlay.ThemeEditor -> ThemeEditorScreen(overlay.theme, controller)
            AppOverlay.DataTools -> DataToolsScreen(controller)
        }
    }
}

@Composable
private fun AddBottomSheet(
    defaultKind: ItemKind,
    allTodos: List<AppItem>,
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
                    dragOffset <= -expandThreshold && kind == ItemKind.TODO -> expanded = true
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
                    onExpandedChange = { expanded = it && kind == ItemKind.TODO },
                    handleModifier = handleModifier,
                    allTodos = allTodos,
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
    handleModifier: Modifier,
    allTodos: List<AppItem>,
    settings: AppSettings,
    controller: AppController,
) {
    var text by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(Priority.NONE) }
    var estimated by remember { mutableStateOf("15") }
    var available by remember { mutableStateOf("") }
    var scheduled by remember { mutableStateOf("") }
    var due by remember { mutableStateOf("") }
    val prerequisites = remember { mutableStateListOf<String>() }

    fun submit() {
        controller.quickAdd(kind, text) { item ->
            if (expanded && item.kind == ItemKind.TODO) {
                controller.save(
                    item.copy(
                        body = body,
                        todo = item.todo?.copy(
                            priority = priority,
                            estimatedMinutes = estimated.toIntOrNull()?.coerceIn(1, 1440),
                            availableFromEpochMillis = parseInputDate(available),
                            scheduledAtEpochMillis = parseInputDate(scheduled),
                            dueAtEpochMillis = parseInputDate(due),
                        ),
                    ),
                    prerequisites.toSet(),
                )
            } else text = ""
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
                Text("追加先", style = MaterialTheme.typography.subtitle1)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KindChoice("あとで分ける", ItemKind.UNSORTED, kind, onKindChange)
                    KindChoice("やること", ItemKind.TODO, kind, onKindChange)
                    KindChoice("メモ", ItemKind.MEMO, kind, onKindChange)
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
                item {
                    Text("先に終えるやること", style = MaterialTheme.typography.subtitle1)
                    if (allTodos.isEmpty()) {
                        Text("選べるやることはありません", style = MaterialTheme.typography.caption)
                    }
                    allTodos.forEach { candidate ->
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
                    Text("日時は 2026-08-01 14:30 の形式", style = MaterialTheme.typography.caption)
                    DateField("いつからできる？", available) { available = it }
                    DateField("行う時刻", scheduled) { scheduled = it }
                    DateField("期限", due) { due = it }
                }
            }
            item {
                Button(onClick = ::submit, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text("追加する")
                }
            }
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
    allTodos: List<AppItem>,
    relations: List<jp.oboegaki.core.model.ItemRelation>,
    settings: AppSettings,
    controller: AppController,
) {
    var kind by remember(source.id) { mutableStateOf(source.kind) }
    var title by remember(source.id) { mutableStateOf(source.title) }
    var body by remember(source.id) { mutableStateOf(source.body) }
    var priority by remember(source.id) { mutableStateOf(source.todo?.priority ?: Priority.NONE) }
    var estimated by remember(source.id) { mutableStateOf(source.todo?.estimatedMinutes?.toString() ?: "") }
    var available by remember(source.id) { mutableStateOf(formatInputDate(source.todo?.availableFromEpochMillis)) }
    var scheduled by remember(source.id) { mutableStateOf(formatInputDate(source.todo?.scheduledAtEpochMillis)) }
    var due by remember(source.id) { mutableStateOf(formatInputDate(source.todo?.dueAtEpochMillis)) }
    val prerequisites = remember(source.id, relations) {
        mutableStateListOf<String>().apply {
            addAll(relations.filter { it.toItemId == source.id && it.type == RelationType.REQUIRED_BEFORE }.map { it.fromItemId })
        }
    }

    fun draftItem(): AppItem {
        val detail = if (kind == ItemKind.TODO) (source.todo ?: TodoDetail()).copy(
            priority = priority,
            estimatedMinutes = estimated.toIntOrNull()?.coerceIn(1, 1440),
            availableFromEpochMillis = parseInputDate(available),
            scheduledAtEpochMillis = parseInputDate(scheduled),
            dueAtEpochMillis = parseInputDate(due),
        ) else null
        return source.copy(kind = kind, title = title, body = body, todo = detail)
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
                Text("種類", style = MaterialTheme.typography.subtitle1)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KindChoice("あとで分ける", ItemKind.UNSORTED, kind) { kind = it }
                    KindChoice("やること", ItemKind.TODO, kind) { kind = it }
                    KindChoice("メモ", ItemKind.MEMO, kind) { kind = it }
                }
            }
            item {
                OutlinedTextField(title, { title = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("タイトル") })
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(body, { body = it.take(100_000) }, Modifier.fillMaxWidth(), label = { Text("本文") }, minLines = 4, maxLines = 12)
            }
            if (kind == ItemKind.TODO) {
                item {
                    Text("優先度", style = MaterialTheme.typography.subtitle1)
                    PriorityChoices(priority) { priority = it }
                }
                item {
                    Text("先に終えるやること", style = MaterialTheme.typography.subtitle1)
                    val candidates = allTodos.filter { it.id != source.id }
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
                    Text("日時は 2026-08-01 14:30 の形式", style = MaterialTheme.typography.caption)
                    DateField("いつからできる？", available) { available = it }
                    DateField("行う時刻", scheduled) { scheduled = it }
                    DateField("期限", due) { due = it }
                }
                if (settings.calendarIntegrationEnabled) item {
                    val hasCalendarDate = scheduled.isNotBlank() || due.isNotBlank() || available.isNotBlank()
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
                    enabled = title.isNotBlank(),
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("保存") }
            }
        }
    }
}

@Composable
private fun DateField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(label) },
        placeholder = { Text("設定なし") }, singleLine = true,
    )
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
    sections.unsorted + sections.todos + sections.memos + sections.completed + sections.archived

private fun formatInputDate(epoch: Long?): String {
    if (epoch == null) return ""
    val value = Instant.fromEpochMilliseconds(epoch).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${value.year}-${value.monthNumber.toString().padStart(2, '0')}-${value.dayOfMonth.toString().padStart(2, '0')} ${value.hour.toString().padStart(2, '0')}:${value.minute.toString().padStart(2, '0')}"
}

private fun parseInputDate(value: String): Long? {
    if (value.isBlank()) return null
    return runCatching {
        LocalDateTime.parse(value.trim().replace(' ', 'T')).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    }.getOrNull()
}
