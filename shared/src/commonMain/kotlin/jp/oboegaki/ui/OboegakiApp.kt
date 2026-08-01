package jp.oboegaki.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.BottomAppBar
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Button
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import jp.oboegaki.core.data.BuiltInThemes
import jp.oboegaki.core.data.ItemRepository
import jp.oboegaki.platform.CalendarExporter
import jp.oboegaki.platform.NoOpCalendarExporter
import kotlin.math.abs

@Composable
fun OboegakiApp(
    repository: ItemRepository,
    calendarExporter: CalendarExporter = NoOpCalendarExporter,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(repository, scope, calendarExporter) { AppController(repository, scope, calendarExporter) }
    val sections by controller.sections.collectAsState()
    val settings by controller.settings.collectAsState()
    val themes by controller.themes.collectAsState()
    val tab by controller.tab.collectAsState()
    val overlay by controller.overlay.collectAsState()
    val todoIndex by controller.todoIndex.collectAsState()
    val memoIndex by controller.memoIndex.collectAsState()
    val undo by controller.undo.collectAsState()
    val message by controller.message.collectAsState()
    val theme = themes.firstOrNull { it.id == settings.selectedThemeId } ?: BuiltInThemes.standard
    val tabSwipeThreshold = with(LocalDensity.current) { 56.dp.toPx() }
    val hapticFeedback = LocalHapticFeedback.current

    fun performHapticFeedback() {
        if (settings.hapticsEnabled) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
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
                                TextButton(onClick = controller::openThemes) { Text("テーマ") }
                                TextButton(onClick = controller::openSettings) { Text("設定") }
                            }
                        },
                    )
                },
                bottomBar = {
                    BottomAppBar(backgroundColor = MaterialTheme.colors.surface) {
                        BottomNavItem("やること", tab == MainTab.TODOS) { controller.selectTab(MainTab.TODOS) }
                        BottomNavItem("メモ", tab == MainTab.MEMOS) { controller.selectTab(MainTab.MEMOS) }
                        BottomNavItem("すべて", tab == MainTab.ALL) { controller.selectTab(MainTab.ALL) }
                    }
                },
                floatingActionButton = {
                    FloatingActionButton(onClick = controller::openAdd, shape = CircleShape) {
                        Text("＋", style = MaterialTheme.typography.h5)
                    }
                },
                isFloatingActionButtonDocked = false,
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .detectUnconsumedHorizontalSwipe(
                            enabled = settings.tabSwipeEnabled,
                            thresholdPx = tabSwipeThreshold,
                        ) { forward ->
                            if (controller.selectAdjacentTab(forward)) {
                                performHapticFeedback()
                            }
                        },
                ) {
                    when (tab) {
                        MainTab.TODOS -> TodoScreen(
                            sections.todos,
                            todoIndex,
                            settings.hapticsEnabled,
                            controller,
                        )
                        MainTab.MEMOS -> MemoScreen(
                            sections.memos,
                            memoIndex,
                            settings.hapticsEnabled,
                            controller,
                        )
                        MainTab.ALL -> AllItemsScreen(sections, controller)
                    }
                    undo?.let {
                        UndoBar(
                            it.message,
                            controller::undo,
                            Modifier.align(Alignment.BottomCenter).padding(end = 172.dp),
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

/**
 * Detects a horizontal swipe only when no child consumed the gesture. This lets
 * blank screen areas change tabs without stealing card swipes, list scrolling,
 * button taps, or drag-to-reorder gestures from their owning components.
 */
private fun Modifier.detectUnconsumedHorizontalSwipe(
    enabled: Boolean,
    thresholdPx: Float,
    onSwipe: (forward: Boolean) -> Unit,
): Modifier = if (!enabled) {
    this
} else {
    pointerInput(enabled, thresholdPx) {
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Final,
            )
            var horizontalDistance = 0f
            var verticalDistance = 0f
            var consumedByChild = down.isConsumed

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val delta = change.position - change.previousPosition
                horizontalDistance += delta.x
                verticalDistance += delta.y
                consumedByChild = consumedByChild || change.isConsumed

                if (!change.pressed) {
                    val horizontalEnough = abs(horizontalDistance) >= thresholdPx
                    val horizontalIntent = abs(horizontalDistance) > abs(verticalDistance) * 1.25f
                    if (!consumedByChild && horizontalEnough && horizontalIntent) {
                        onSwipe(horizontalDistance < 0f)
                    }
                    break
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BottomNavItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    BottomNavigationItem(
        selected = selected,
        onClick = onClick,
        icon = { Text(if (selected) "●" else "○") },
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
