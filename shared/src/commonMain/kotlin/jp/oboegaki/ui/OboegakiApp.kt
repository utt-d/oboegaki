package jp.oboegaki.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.BottomAppBar
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Button
import androidx.compose.material.FabPosition
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import jp.oboegaki.core.data.BuiltInThemes
import jp.oboegaki.core.data.ItemRepository
import jp.oboegaki.core.model.AddButtonPosition
import jp.oboegaki.core.model.AllSections
import jp.oboegaki.core.model.AppSettings
import jp.oboegaki.core.model.MainNavigationButton
import jp.oboegaki.core.model.TopActionButton
import jp.oboegaki.platform.CalendarExporter
import jp.oboegaki.platform.NoOpCalendarExporter
import jp.oboegaki.platform.BackupFileGateway
import jp.oboegaki.platform.NoOpBackupFileGateway
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun OboegakiApp(
    repository: ItemRepository,
    calendarExporter: CalendarExporter = NoOpCalendarExporter,
    backupFileGateway: BackupFileGateway = NoOpBackupFileGateway,
    focusItemId: String? = null,
    focusRequestKey: Long = 0L,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(repository, scope, calendarExporter, backupFileGateway) {
        AppController(repository, scope, calendarExporter, backupFileGateway)
    }
    val sections by controller.sections.collectAsState()
    val settings by controller.settings.collectAsState()
    val themes by controller.themes.collectAsState()
    val tab by controller.tab.collectAsState()
    val overlay by controller.overlay.collectAsState()
    val todoIndex by controller.todoIndex.collectAsState()
    val memoIndex by controller.memoIndex.collectAsState()
    val undo by controller.undo.collectAsState()
    val message by controller.message.collectAsState()
    LaunchedEffect(focusItemId, focusRequestKey) {
        focusItemId?.let(controller::focusItem)
    }
    val theme = themes.firstOrNull { it.id == settings.selectedThemeId } ?: BuiltInThemes.standard
    val icons = theme.icons
    val tabOrder = navigationTabs(settings.navigationButtonOrder)
    val latestTabState = rememberUpdatedState(tab)
    val latestTabOrderState = rememberUpdatedState(tabOrder)
    val tabSwipeThreshold = with(LocalDensity.current) { 56.dp.toPx() }
    val tabTransitionDuration = if (settings.reducedMotion) {
        1
    } else {
        (220 * theme.animationScale).roundToInt().coerceAtLeast(1)
    }
    val hapticFeedback = LocalHapticFeedback.current
    var tabOffset by remember { mutableFloatStateOf(0f) }
    var tabViewportWidth by remember { mutableIntStateOf(0) }
    var tabTransitionJob by remember { mutableStateOf<Job?>(null) }
    var tabTransitionGeneration by remember { mutableIntStateOf(0) }

    fun cancelTabTransition() {
        tabTransitionGeneration += 1
        tabTransitionJob?.cancel()
        tabTransitionJob = null
    }

    LaunchedEffect(tab, tabOrder) {
        if (tabTransitionJob != null) {
            cancelTabTransition()
            tabOffset = 0f
        }
    }

    PlatformBackHandler(enabled = overlay != null, onBack = controller::closeOverlay)

    fun performHapticFeedback() {
        if (settings.hapticsEnabled) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun transitionDuration(distancePx: Float): Int {
        if (settings.reducedMotion || tabViewportWidth <= 0) return 1
        val pages = (distancePx / tabViewportWidth).coerceAtLeast(.05f)
        return (tabTransitionDuration * pages).roundToInt().coerceAtLeast(1)
    }

    fun animateToTab(target: MainTab) {
        if (target == tab) return
        if (tabViewportWidth <= 0) {
            controller.selectTab(target)
            return
        }
        val source = tab
        val sourceIndex = tabOrder.indexOf(source)
        val targetIndex = tabOrder.indexOf(target)
        val sourceOrder = tabOrder
        cancelTabTransition()
        val generation = tabTransitionGeneration
        tabTransitionJob = scope.launch {
            try {
                val targetOffset = -(targetIndex - sourceIndex) * tabViewportWidth.toFloat()
                val startOffset = tabOffset
                val distance = abs(targetOffset - startOffset)
                animate(
                    initialValue = startOffset,
                    targetValue = targetOffset,
                    animationSpec = tween(transitionDuration(distance), easing = LinearEasing),
                ) { value, _ -> tabOffset = value }
                if (
                    generation != tabTransitionGeneration ||
                    source != latestTabState.value ||
                    sourceOrder != latestTabOrderState.value
                ) return@launch
                controller.selectTab(target)
                tabOffset = 0f
                performHapticFeedback()
            } finally {
                if (generation == tabTransitionGeneration) {
                    tabTransitionJob = null
                }
            }
        }
    }

    fun settleTabSwipe(source: MainTab, sourceOrder: List<MainTab>, distance: Float) {
        if (source != latestTabState.value || sourceOrder != latestTabOrderState.value) {
            tabOffset = 0f
            return
        }
        val direction = if (distance < 0f) 1 else -1
        val target = if (abs(distance) >= tabSwipeThreshold) {
            adjacentNavigationTab(source, direction, sourceOrder) ?: source
        } else source
        cancelTabTransition()
        val generation = tabTransitionGeneration
        tabTransitionJob = scope.launch {
            try {
                val targetOffset = if (target == source) 0f else -direction * tabViewportWidth.toFloat()
                val startOffset = tabOffset
                val remaining = abs(targetOffset - startOffset)
                animate(
                    initialValue = startOffset,
                    targetValue = targetOffset,
                    animationSpec = tween(transitionDuration(remaining), easing = LinearEasing),
                ) { value, _ -> tabOffset = value }
                if (
                    generation != tabTransitionGeneration ||
                    source != latestTabState.value ||
                    sourceOrder != latestTabOrderState.value
                ) return@launch
                if (target != source) {
                    controller.selectTab(target)
                    performHapticFeedback()
                }
                tabOffset = 0f
            } finally {
                if (generation == tabTransitionGeneration) {
                    tabTransitionJob = null
                }
            }
        }
    }

    OboegakiTheme(theme, settings) {
        Surface(
            Modifier.fillMaxSize().safeDrawingPadding(),
            color = MaterialTheme.colors.background,
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(when (tab) {
                                    MainTab.TODOS -> "やること"
                                    MainTab.MEMOS -> "メモ"
                                    MainTab.ALL -> "すべて"
                                })
                            }
                        },
                        backgroundColor = MaterialTheme.colors.background,
                        elevation = 0.dp,
                        actions = {
                            if (tab == MainTab.ALL) {
                                normalizedButtonOrder(
                                    settings.topActionButtonOrder,
                                    TopActionButton.values().toList(),
                                ).forEach { button ->
                                    when (button) {
                                        TopActionButton.THEMES -> TextButton(onClick = controller::openThemes) {
                                            Text("${icons.theme} テーマ")
                                        }
                                        TopActionButton.SETTINGS -> TextButton(onClick = controller::openSettings) {
                                            Text("${icons.settings} 設定")
                                        }
                                    }
                                }
                            }
                        },
                    )
                },
                bottomBar = {
                    BottomAppBar(
                        backgroundColor = MaterialTheme.colors.surface,
                        contentPadding = PaddingValues(
                            start = if (settings.addButtonPosition == AddButtonPosition.LEFT) 56.dp else 0.dp,
                            end = if (settings.addButtonPosition == AddButtonPosition.RIGHT) 56.dp else 0.dp,
                        ),
                    ) {
                        normalizedButtonOrder(
                            settings.navigationButtonOrder,
                            MainNavigationButton.values().toList(),
                        ).forEach { button ->
                            when (button) {
                                MainNavigationButton.TODOS -> BottomNavItem(
                                    "やること", icons.todo, tab == MainTab.TODOS,
                                ) { animateToTab(MainTab.TODOS) }
                                MainNavigationButton.MEMOS -> BottomNavItem(
                                    "メモ", icons.memo, tab == MainTab.MEMOS,
                                ) { animateToTab(MainTab.MEMOS) }
                                MainNavigationButton.ALL -> BottomNavItem(
                                    "すべて", icons.all, tab == MainTab.ALL,
                                ) { animateToTab(MainTab.ALL) }
                            }
                        }
                    }
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = controller::openAdd,
                        modifier = Modifier.padding(bottom = settings.addButtonBottomOffsetDp.coerceIn(0, 160).dp),
                        shape = CircleShape,
                    ) {
                        Text(icons.add, style = MaterialTheme.typography.h5)
                    }
                },
                floatingActionButtonPosition = when (settings.addButtonPosition) {
                    AddButtonPosition.LEFT -> FabPosition.Start
                    AddButtonPosition.CENTER -> FabPosition.Center
                    AddButtonPosition.RIGHT -> FabPosition.End
                },
                isFloatingActionButtonDocked = false,
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .clipToBounds()
                        .onSizeChanged { tabViewportWidth = it.width }
                        .trackHorizontalTabSwipe(
                            enabled = settings.tabSwipeEnabled,
                            currentTab = tab,
                            tabOrder = tabOrder,
                            allowChildConsumption = tab == MainTab.ALL,
                            onStart = { _, _ ->
                                cancelTabTransition()
                                tabOffset = 0f
                            },
                            onDrag = { source, sourceOrder, distance ->
                                val minimum = if (adjacentNavigationTab(source, 1, sourceOrder) != null) {
                                    -tabViewportWidth.toFloat()
                                } else 0f
                                val maximum = if (adjacentNavigationTab(source, -1, sourceOrder) != null) {
                                    tabViewportWidth.toFloat()
                                } else 0f
                                tabOffset = distance.coerceIn(minimum, maximum)
                            },
                            onEnd = { source, sourceOrder, distance ->
                                settleTabSwipe(source, sourceOrder, distance)
                            },
                            onCancel = { _, _ ->
                                cancelTabTransition()
                                tabOffset = 0f
                            },
                        )
                ) {
                    if (tabViewportWidth == 0) {
                        TabScreen(tab, sections, todoIndex, memoIndex, settings, controller)
                    } else {
                        tabOrder.forEachIndexed { pageIndex, pageTab ->
                            key(pageTab) {
                                val pageBaseOffset = (pageIndex - tabOrder.indexOf(tab)) * tabViewportWidth
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .offset {
                                            IntOffset(
                                                x = pageBaseOffset + tabOffset.roundToInt(),
                                                y = 0,
                                            )
                                        },
                                ) {
                                    TabScreen(pageTab, sections, todoIndex, memoIndex, settings, controller)
                                }
                            }
                        }
                    }
                    undo?.let {
                        UndoBar(
                            it.message,
                            controller::undo,
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(
                                    start = if (settings.addButtonPosition == AddButtonPosition.LEFT) 96.dp else 12.dp,
                                    end = if (settings.addButtonPosition == AddButtonPosition.RIGHT) 96.dp else 12.dp,
                                    bottom = if (settings.addButtonPosition == AddButtonPosition.CENTER) {
                                        (104 + settings.addButtonBottomOffsetDp.coerceIn(0, 160)).dp
                                    } else {
                                        12.dp
                                    },
                                ),
                        )
                    }
                    message?.let {
                        MessageBar(it, Modifier.align(Alignment.TopCenter).padding(12.dp))
                    }
                }
            }
            overlay?.let {
                OverlayHost(it, sections, settings, themes, controller)
            }
        }
    }
}

@Composable
private fun TabScreen(
    tab: MainTab,
    sections: AllSections,
    todoIndex: Int,
    memoIndex: Int,
    settings: AppSettings,
    controller: AppController,
) {
    when (tab) {
        MainTab.TODOS -> TodoScreen(
            sections.todos,
            todoIndex,
            settings.hapticsEnabled,
            settings.operationGuideSeen,
            settings.addButtonPosition,
            settings.addButtonBottomOffsetDp,
            controller,
        )
        MainTab.MEMOS -> MemoScreen(
            sections.memos,
            memoIndex,
            settings.hapticsEnabled,
            settings.operationGuideSeen,
            settings.addButtonPosition,
            settings.addButtonBottomOffsetDp,
            controller,
        )
        MainTab.ALL -> AllItemsScreen(
            sections,
            controller.relations.collectAsState().value,
            settings.addButtonPosition,
            settings.addButtonBottomOffsetDp,
            controller,
        )
    }
}

/**
 * Detects horizontal tab swipes without stealing the card gestures on the
 * やること and メモ screens. The すべて screen owns a LazyColumn, so its child
 * consumption is deliberately allowed after horizontal intent is confirmed.
 */
@Composable
private fun Modifier.trackHorizontalTabSwipe(
    enabled: Boolean,
    currentTab: MainTab,
    tabOrder: List<MainTab>,
    allowChildConsumption: Boolean,
    onStart: (source: MainTab, sourceOrder: List<MainTab>) -> Unit,
    onDrag: (source: MainTab, sourceOrder: List<MainTab>, distance: Float) -> Unit,
    onEnd: (source: MainTab, sourceOrder: List<MainTab>, distance: Float) -> Unit,
    onCancel: (source: MainTab, sourceOrder: List<MainTab>) -> Unit,
): Modifier = if (!enabled) {
    this
} else {
    val currentOnStart by rememberUpdatedState(onStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnEnd by rememberUpdatedState(onEnd)
    val currentOnCancel by rememberUpdatedState(onCancel)
    pointerInput(enabled, currentTab, tabOrder, allowChildConsumption) {
        val gestureSlop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Final,
            )
            val gestureSource = currentTab
            val gestureOrder = tabOrder
            val gestureOnStart = currentOnStart
            val gestureOnDrag = currentOnDrag
            val gestureOnEnd = currentOnEnd
            val gestureOnCancel = currentOnCancel
            var horizontalDistance = 0f
            var verticalDistance = 0f
            var consumedByChild = down.isConsumed
            var tracking = false
            var rejected = false

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) {
                    if (tracking) gestureOnCancel(gestureSource, gestureOrder)
                    break
                }
                val delta = change.position - change.previousPosition
                horizontalDistance += delta.x
                verticalDistance += delta.y
                consumedByChild = consumedByChild || change.isConsumed

                if (tracking && !allowChildConsumption && change.isConsumed) {
                    tracking = false
                    rejected = true
                    gestureOnCancel(gestureSource, gestureOrder)
                } else if (!tracking && !rejected &&
                    (abs(horizontalDistance) > gestureSlop || abs(verticalDistance) > gestureSlop)
                ) {
                    val horizontalIntent = abs(horizontalDistance) > abs(verticalDistance) * 1.25f
                    val verticalIntent = abs(verticalDistance) > abs(horizontalDistance)
                    when {
                        horizontalIntent && (allowChildConsumption || !consumedByChild) -> {
                            tracking = true
                            gestureOnStart(gestureSource, gestureOrder)
                            gestureOnDrag(gestureSource, gestureOrder, horizontalDistance)
                        }
                        horizontalIntent || verticalIntent -> rejected = true
                    }
                } else if (tracking && change.pressed) {
                    gestureOnDrag(gestureSource, gestureOrder, horizontalDistance)
                }

                if (!change.pressed) {
                    if (tracking) gestureOnEnd(gestureSource, gestureOrder, horizontalDistance)
                    break
                }
            }
        }
    }
}

private fun <T> normalizedButtonOrder(value: List<T>, defaults: List<T>): List<T> =
    value.filter { it in defaults }.distinct() + defaults.filterNot { it in value }

internal fun navigationTabs(value: List<MainNavigationButton>): List<MainTab> =
    normalizedButtonOrder(value, MainNavigationButton.values().toList()).map {
        when (it) {
            MainNavigationButton.TODOS -> MainTab.TODOS
            MainNavigationButton.MEMOS -> MainTab.MEMOS
            MainNavigationButton.ALL -> MainTab.ALL
        }
    }

internal fun adjacentNavigationTab(
    source: MainTab,
    direction: Int,
    order: List<MainTab>,
): MainTab? {
    if (direction !in -1..1 || direction == 0) return null
    val sourceIndex = order.indexOf(source)
    if (sourceIndex < 0) return null
    return order.getOrNull(sourceIndex + direction)
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BottomNavItem(
    label: String,
    icon: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    BottomNavigationItem(
        selected = selected,
        onClick = onClick,
        icon = { Text(icon, color = if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = .62f)) },
        label = { Text(label) },
        alwaysShowLabel = true,
    )
}

@Composable
private fun UndoBar(message: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.padding(12.dp), elevation = 8.dp, shape = MaterialTheme.shapes.medium) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, Modifier.weight(1f))
            Button(onClick = onUndo, modifier = Modifier.padding(8.dp)) { Text("元に戻す") }
        }
    }
}

@Composable
private fun MessageBar(message: String, modifier: Modifier = Modifier) {
    Surface(modifier, elevation = 6.dp, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colors.onBackground) {
        Text(message, Modifier.padding(horizontal = 18.dp, vertical = 12.dp), color = MaterialTheme.colors.background)
    }
}
