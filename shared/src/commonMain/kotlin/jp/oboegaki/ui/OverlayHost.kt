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
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val theme = LocalAppTheme.current
    val motionDisabled = settings.reducedMotion ||
        theme.motionStrength == jp.oboegaki.core.model.MotionStrength.NONE
    val transitionDuration = if (motionDisabled) 1 else {
        (220 * theme.animationScale).roundToInt().coerceAtLeast(1)
    }
    var kind by remember { mutableStateOf(defaultKind) }
    var expanded by remember { mutableStateOf(false) }
    var destinationExpanded by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val closeThreshold = with(density) { 72.dp.toPx() }
    val expandThreshold = with(density) { 36.dp.toPx() }
    val maxDown = with(density) { 140.dp.toPx() }
    val maxUp = with(density) { 48.dp.toPx() }
    fun closeSheet() {
        if (!visible.targetState) return
        visible.targetState = false
        scope.launch {
            delay(transitionDuration.toLong())
            if (!visible.targetState) controller.closeOverlay()
        }
    }
    val handleModifier = Modifier.pointerInput(kind, expanded) {
        detectVerticalDragGestures(
            onDragStart = { dragOffset = 0f },
            onDragCancel = { dragOffset = 0f },
            onDragEnd = {
                val closing = dragOffset >= closeThreshold
                when {
                    closing -> closeSheet()
                    dragOffset <= -expandThreshold && kind == ItemKind.TODO -> {
                        expanded = true
                        destinationExpanded = true
                    }
                }
                if (!closing) dragOffset = 0f
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
            .clickable(onClick = ::closeSheet),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = slideInVertically(
                animationSpec = tween(transitionDuration, easing = LinearEasing),
                initialOffsetY = { it },
            ) + fadeIn(animationSpec = tween(transitionDuration, easing = LinearEasing)),
            exit = slideOutVertically(
                animationSpec = tween(transitionDuration, easing = LinearEasing),
                targetOffsetY = { it },
            ) + fadeOut(animationSpec = tween(transitionDuration, easing = LinearEasing)),
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
                    onClose = ::closeSheet,
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
