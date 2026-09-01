package jp.oboegaki.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import jp.oboegaki.core.model.ThemeIcons
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
    val motionDisabled = settings.reducedMotion ||
        theme.motionStrength == jp.oboegaki.core.model.MotionStrength.NONE
    val tabTransitionDuration = MotionAnimation.duration(220, theme.animationScale, motionDisabled)
    val hapticFeedback = LocalHapticFeedback.current
    var tabTransitionState by remember { mutableStateOf(TabTransitionRenderState(tab)) }
    var tabViewportWidth by remember { mutableIntStateOf(0) }
    var tabTransitionJob by remember { mutableStateOf<Job?>(null) }

    fun cancelTabTransition() {
        tabTransitionJob?.cancel()
        tabTransitionJob = null
        tabTransitionState = tabTransitionState.invalidate(latestTabState.value)
    }

    LaunchedEffect(tab, tabOrder) {
        if (
            tabTransitionState.anchorTab != tab ||
            tabTransitionState.phase != TabTransitionPhase.IDLE
        ) {
            cancelTabTransition()
        }
    }

    PlatformBackHandler(enabled = overlay != null, onBack = controller::closeOverlay)

    fun performHapticFeedback() {
        if (settings.hapticsEnabled) {
            hapticFeedback.performAppHaptic(AppHapticIntent.TICK)
        }
    }

    fun transitionDuration(distancePx: Float): Int {
        if (motionDisabled || tabViewportWidth <= 0) return 1
        val pages = (distancePx / tabViewportWidth).coerceAtLeast(.05f)
        return (tabTransitionDuration * pages).roundToInt().coerceAtLeast(90)
    }

    fun animateToTab(target: MainTab) {
        if (target == tab && tabTransitionState.phase == TabTransitionPhase.IDLE) return
        if (tabTransitionState.phase != TabTransitionPhase.IDLE) {
            cancelTabTransition()
        }
        val source = latestTabState.value
        if (target == source) return
        if (tabViewportWidth <= 0) {
            tabTransitionState = tabTransitionState.invalidate(target)
            controller.selectTab(target)
            return
        }
        val sourceIndex = tabOrder.indexOf(source)
        val targetIndex = tabOrder.indexOf(target)
        val sourceOrder = tabOrder
        val transition = tabTransitionState.beginProgrammatic(source, target) ?: return
        val generation = transition.generation
        tabTransitionState = transition
        tabTransitionJob = scope.launch {
            try {
                val targetOffset = -(targetIndex - sourceIndex) * tabViewportWidth.toFloat()
                val startOffset = tabTransitionState.offsetPx
                val distance = abs(targetOffset - startOffset)
                animate(
                    initialValue = startOffset,
                    targetValue = targetOffset,
                    animationSpec = tween(transitionDuration(distance), easing = MotionAnimation.settleEasing),
                ) { value, _ ->
                    tabTransitionState.updateSettle(source, generation, value)?.let {
                        tabTransitionState = it
                    }
                }
                if (
                    generation != tabTransitionState.generation ||
                    source != latestTabState.value ||
                    sourceOrder != latestTabOrderState.value ||
                    tabTransitionState.targetTab != target
                ) {
                    if (generation == tabTransitionState.generation) {
                        tabTransitionState = tabTransitionState.invalidate(latestTabState.value)
                    }
                    return@launch
                }
                val committed = tabTransitionState.commit(source, generation, target) ?: return@launch
                tabTransitionState = committed
                controller.selectTab(target)
                performHapticFeedback()
            } finally {
                if (generation == tabTransitionState.generation) {
                    tabTransitionJob = null
                }
            }
        }
    }

    fun settleTabSwipe(source: MainTab, sourceOrder: List<MainTab>, distance: Float) {
        if (source != latestTabState.value || sourceOrder != latestTabOrderState.value) {
            cancelTabTransition()
            return
        }
        val direction = if (distance < 0f) 1 else -1
        val target = if (abs(distance) >= tabSwipeThreshold) {
            adjacentNavigationTab(source, direction, sourceOrder) ?: source
        } else source
        val generation = tabTransitionState.generation
        val settling = tabTransitionState.beginSettle(source, generation, target) ?: return
        val startOffset = tabTransitionState.offsetPx
        tabTransitionState = settling
        tabTransitionJob = scope.launch {
            try {
                val targetOffset = if (target == source) 0f else -direction * tabViewportWidth.toFloat()
                val remaining = abs(targetOffset - startOffset)
                animate(
                    initialValue = startOffset,
                    targetValue = targetOffset,
                    animationSpec = tween(transitionDuration(remaining), easing = MotionAnimation.settleEasing),
                ) { value, _ ->
                    tabTransitionState.updateSettle(source, generation, value)?.let {
                        tabTransitionState = it
                    }
                }
                if (
                    generation != tabTransitionState.generation ||
                    source != latestTabState.value ||
                    sourceOrder != latestTabOrderState.value ||
                    tabTransitionState.targetTab != target
                ) {
                    if (generation == tabTransitionState.generation) {
                        tabTransitionState = tabTransitionState.invalidate(latestTabState.value)
                    }
                    return@launch
                }
                if (target != source) {
                    tabTransitionState = tabTransitionState.commit(source, generation, target) ?: return@launch
                    controller.selectTab(target)
                    performHapticFeedback()
                } else {
                    tabTransitionState = tabTransitionState.commit(source, generation, target) ?: return@launch
                }
            } finally {
                if (generation == tabTransitionState.generation) {
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
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                ThemeIcon(icons.theme, ThemeIcons().theme, AppIcons.theme, null)
                                                Text("テーマ")
                                            }
                                        }
                                        TopActionButton.SETTINGS -> TextButton(onClick = controller::openSettings) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                ThemeIcon(icons.settings, ThemeIcons().settings, AppIcons.settings, null)
                                                Text("設定")
                                            }
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
                                    "やること",
                                    { ThemeIcon(icons.todo, ThemeIcons().todo, AppIcons.todo, null) },
                                    tab == MainTab.TODOS,
                                ) { animateToTab(MainTab.TODOS) }
                                MainNavigationButton.MEMOS -> BottomNavItem(
                                    "メモ",
                                    { ThemeIcon(icons.memo, ThemeIcons().memo, AppIcons.memo, null) },
                                    tab == MainTab.MEMOS,
                                ) { animateToTab(MainTab.MEMOS) }
                                MainNavigationButton.ALL -> BottomNavItem(
                                    "すべて",
                                    { ThemeIcon(icons.all, ThemeIcons().all, AppIcons.all, null) },
                                    tab == MainTab.ALL,
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
                        ThemeIcon(icons.add, ThemeIcons().add, AppIcons.add, "追加", tint = MaterialTheme.colors.onPrimary)
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
                                tabTransitionState.beginDrag(tab)?.let {
                                    tabTransitionState = it
                                }
                            },
                            onDrag = { source, sourceOrder, distance ->
                                val minimum = if (adjacentNavigationTab(source, 1, sourceOrder) != null) {
                                    -tabViewportWidth.toFloat()
                                } else 0f
                                val maximum = if (adjacentNavigationTab(source, -1, sourceOrder) != null) {
                                    tabViewportWidth.toFloat()
                                } else 0f
                                tabTransitionState.updateDrag(
                                    source,
                                    tabTransitionState.generation,
                                    distance.coerceIn(minimum, maximum),
                                )?.let {
                                    tabTransitionState = it
                                }
                            },
                            onEnd = { source, sourceOrder, distance ->
                                settleTabSwipe(source, sourceOrder, distance)
                            },
                            onCancel = { _, _ ->
                                cancelTabTransition()
                            },
                        )
                ) {
                    if (tabViewportWidth == 0) {
                        TabScreen(tab, sections, todoIndex, memoIndex, settings, controller)
                    } else {
                        tabOrder.forEachIndexed { pageIndex, pageTab ->
                            key(pageTab) {
                                val renderState = tabTransitionState
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .offset {
                                            IntOffset(
                                                x = tabPageOffset(pageIndex, tabOrder, renderState, tabViewportWidth),
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
            settings.reducedMotion,
            settings.operationGuideSeen,
            settings.addButtonPosition,
            settings.addButtonBottomOffsetDp,
            controller,
        )
        MainTab.MEMOS -> MemoScreen(
            sections.memos,
            memoIndex,
            settings.hapticsEnabled,
            settings.reducedMotion,
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
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
) {
    BottomNavigationItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
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
