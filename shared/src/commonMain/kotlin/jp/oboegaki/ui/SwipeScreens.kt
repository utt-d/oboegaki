package jp.oboegaki.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import jp.oboegaki.core.model.AddButtonPosition
import jp.oboegaki.core.model.AppItem
import jp.oboegaki.core.model.ItemKind
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun TodoScreen(
    items: List<AppItem>,
    focusedIndex: Int,
    hapticsEnabled: Boolean,
    addButtonPosition: AddButtonPosition,
    addButtonBottomOffsetDp: Int,
    controller: AppController,
) {
    val icons = LocalAppTheme.current.icons
    SwipeDeck(
        items = items,
        focusedIndex = focusedIndex,
        kind = ItemKind.TODO,
        rightLabel = "完了",
        leftLabel = "後で行う",
        rightIcon = icons.complete,
        leftIcon = icons.defer,
        onRight = controller::completeCurrent,
        onLeft = controller::deferCurrent,
        onUp = controller::nextTodo,
        onDown = controller::previousTodo,
        onEdit = { controller.openEdit(it.id) },
        hapticsEnabled = hapticsEnabled,
        addButtonPosition = addButtonPosition,
        addButtonBottomOffsetDp = addButtonBottomOffsetDp,
    )
}

@Composable
fun MemoScreen(
    items: List<AppItem>,
    focusedIndex: Int,
    hapticsEnabled: Boolean,
    addButtonPosition: AddButtonPosition,
    addButtonBottomOffsetDp: Int,
    controller: AppController,
) {
    val icons = LocalAppTheme.current.icons
    SwipeDeck(
        items = items,
        focusedIndex = focusedIndex,
        kind = ItemKind.MEMO,
        rightLabel = "やることにする",
        leftLabel = "しまう",
        rightIcon = icons.convert,
        leftIcon = icons.archive,
        onRight = controller::convertCurrentMemo,
        onLeft = controller::archiveCurrentMemo,
        onUp = controller::nextMemo,
        onDown = controller::previousMemo,
        onEdit = { controller.openEdit(it.id) },
        hapticsEnabled = hapticsEnabled,
        addButtonPosition = addButtonPosition,
        addButtonBottomOffsetDp = addButtonBottomOffsetDp,
    )
}

@Composable
private fun SwipeDeck(
    items: List<AppItem>,
    focusedIndex: Int,
    kind: ItemKind,
    rightLabel: String,
    leftLabel: String,
    rightIcon: String,
    leftIcon: String,
    onRight: () -> Unit,
    onLeft: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onEdit: (AppItem) -> Unit,
    hapticsEnabled: Boolean,
    addButtonPosition: AddButtonPosition,
    addButtonBottomOffsetDp: Int,
) {
    if (items.isEmpty()) {
        EmptyDeck(kind)
        return
    }
    val safeIndex = focusedIndex.coerceIn(items.indices)
    val item = items[safeIndex]
    val hasPrevious = safeIndex > 0
    val hasNext = safeIndex < items.lastIndex
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val theme = LocalAppTheme.current
    val tokens = LocalThemeColors.current
    var dragX by remember(item.id) { mutableFloatStateOf(0f) }
    var dragY by remember(item.id) { mutableFloatStateOf(0f) }
    var axis by remember(item.id) { mutableStateOf<DragAxis?>(null) }
    var animating by remember { mutableStateOf(false) }
    fun performHapticFeedback() {
        if (hapticsEnabled) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val threshold = max(with(density) { 64.dp.toPx() }, widthPx * .18f)
        val axisLock = with(density) { 12.dp.toPx() }
        val resistance = with(density) { 24.dp.toPx() }
        val previewHeight = 68.dp
        val centerHeight = 330.dp
        val verticalTransition = 205.dp
        val verticalTransitionPx = with(density) { verticalTransition.toPx() }

        fun settle(result: SwipeResult?, hapticAlreadySent: Boolean = false) {
            if (animating) return
            if (result != null && !hapticAlreadySent) {
                performHapticFeedback()
            }
            animating = true
            scope.launch {
                val targetX = when (result) {
                    SwipeResult.RIGHT -> widthPx * 1.15f
                    SwipeResult.LEFT -> -widthPx * 1.15f
                    else -> 0f
                }
                val targetY = when (result) {
                    SwipeResult.UP -> -verticalTransitionPx
                    SwipeResult.DOWN -> verticalTransitionPx
                    else -> 0f
                }
                val startX = dragX
                val startY = dragY
                val baseDuration = if (theme.motionStrength == jp.oboegaki.core.model.MotionStrength.NONE) 1
                else (180 * theme.animationScale).roundToInt().coerceAtLeast(1)
                val remainingDistance = when (result) {
                    SwipeResult.RIGHT, SwipeResult.LEFT -> abs(targetX - startX)
                    SwipeResult.UP, SwipeResult.DOWN -> abs(targetY - startY)
                    null -> max(abs(startX), abs(startY))
                }
                val fullDistance = when (result ?: if (axis == DragAxis.VERTICAL) SwipeResult.UP else SwipeResult.RIGHT) {
                    SwipeResult.RIGHT, SwipeResult.LEFT -> widthPx * 1.15f
                    SwipeResult.UP, SwipeResult.DOWN -> verticalTransitionPx
                }
                val duration = SwipeGesturePhysics.settleDuration(baseDuration, remainingDistance, fullDistance)
                animate(0f, 1f, animationSpec = tween(duration)) { value, _ ->
                    dragX = startX + (targetX - startX) * value
                    dragY = startY + (targetY - startY) * value
                }
                when (result) {
                    SwipeResult.RIGHT -> onRight()
                    SwipeResult.LEFT -> onLeft()
                    SwipeResult.UP -> onUp()
                    SwipeResult.DOWN -> onDown()
                    null -> Unit
                }
                dragX = 0f
                dragY = 0f
                axis = null
                animating = false
            }
        }

        val accessibilityActions = listOf(
            CustomAccessibilityAction(rightLabel) { settle(SwipeResult.RIGHT); true },
            CustomAccessibilityAction(leftLabel) { settle(SwipeResult.LEFT); true },
            CustomAccessibilityAction("次へ") { if (hasNext) settle(SwipeResult.UP); hasNext },
            CustomAccessibilityAction("前へ") { if (hasPrevious) settle(SwipeResult.DOWN); hasPrevious },
        )

        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${safeIndex + 1} / ${items.size}", style = MaterialTheme.typography.caption, color = parseColor(tokens.textSecondary))
            Spacer(Modifier.height(10.dp))
            SwipeOperationGuide(leftLabel, rightLabel, leftIcon, rightIcon)
            Spacer(Modifier.height(8.dp))
            val verticalOffset = if (axis == DragAxis.VERTICAL) {
                dragY.coerceIn(-verticalTransitionPx, verticalTransitionPx)
            } else {
                0f
            }
            val verticalProgress = (abs(verticalOffset) / verticalTransitionPx).coerceIn(0f, 1f)
            val movingUp = verticalOffset < 0f
            val movingDown = verticalOffset > 0f
            val transitionEmphasis = (4f * verticalProgress * (1f - verticalProgress)).coerceIn(0f, 1f)
            val previewWidth = .92f
            val currentHeight = lerpDp(centerHeight, previewHeight, verticalProgress)
            val currentWidth = lerpFloat(1f, previewWidth, verticalProgress)
            val topHeight = if (movingDown) lerpDp(previewHeight, centerHeight, verticalProgress) else previewHeight
            val topWidth = if (movingDown) lerpFloat(previewWidth, 1f, verticalProgress) else previewWidth
            val bottomHeight = if (movingUp) lerpDp(previewHeight, centerHeight, verticalProgress) else previewHeight
            val bottomWidth = if (movingUp) lerpFloat(previewWidth, 1f, verticalProgress) else previewWidth

            Box(Modifier.fillMaxWidth().weight(1f).clipToBounds(), contentAlignment = Alignment.Center) {
                HorizontalGuide(rightLabel, leftLabel, rightIcon, leftIcon, dragX, axis)

                MorphingDeckCard(
                    item = items.getOrNull(safeIndex - 2),
                    positionLabel = "前のカード",
                    edgeText = "ここが最初です",
                    directionMark = theme.icons.previous,
                    height = previewHeight,
                    widthFraction = previewWidth,
                    offsetX = 0f,
                    offsetY = -verticalTransitionPx * 2f + verticalOffset,
                    detailAlpha = 0f,
                    previewAlpha = 1f,
                    containerAlpha = if (movingDown) .72f * verticalProgress else 0f,
                    highlightProgress = 0f,
                    zIndex = -1f + verticalProgress,
                )

                MorphingDeckCard(
                    item = items.getOrNull(safeIndex + 2),
                    positionLabel = "次のカード",
                    edgeText = "ここが最後です",
                    directionMark = theme.icons.next,
                    height = previewHeight,
                    widthFraction = previewWidth,
                    offsetX = 0f,
                    offsetY = verticalTransitionPx * 2f + verticalOffset,
                    detailAlpha = 0f,
                    previewAlpha = 1f,
                    containerAlpha = if (movingUp) .72f * verticalProgress else 0f,
                    highlightProgress = 0f,
                    zIndex = -1f + verticalProgress,
                )

                MorphingDeckCard(
                    item = items.getOrNull(safeIndex - 1),
                    positionLabel = "前のカード",
                    edgeText = "ここが最初です",
                    directionMark = theme.icons.previous,
                    height = topHeight,
                    widthFraction = topWidth,
                    offsetX = 0f,
                    offsetY = -verticalTransitionPx + verticalOffset,
                    detailAlpha = if (movingDown) verticalProgress else 0f,
                    previewAlpha = if (movingDown) 1f - verticalProgress else 1f,
                    containerAlpha = if (movingDown) lerpFloat(.72f, 1f, verticalProgress) else .72f * (1f - verticalProgress),
                    highlightProgress = if (movingDown) transitionEmphasis else 0f,
                    zIndex = if (movingDown) verticalProgress * 3f else 0f,
                )

                MorphingDeckCard(
                    item = items.getOrNull(safeIndex + 1),
                    positionLabel = "次のカード",
                    edgeText = "ここが最後です",
                    directionMark = theme.icons.next,
                    height = bottomHeight,
                    widthFraction = bottomWidth,
                    offsetX = 0f,
                    offsetY = verticalTransitionPx + verticalOffset,
                    detailAlpha = if (movingUp) verticalProgress else 0f,
                    previewAlpha = if (movingUp) 1f - verticalProgress else 1f,
                    containerAlpha = if (movingUp) lerpFloat(.72f, 1f, verticalProgress) else .72f * (1f - verticalProgress),
                    highlightProgress = if (movingUp) transitionEmphasis else 0f,
                    zIndex = if (movingUp) verticalProgress * 3f else 0f,
                )

                MorphingDeckCard(
                    item = item,
                    positionLabel = if (movingDown) "次のカード" else "前のカード",
                    edgeText = "",
                    directionMark = if (movingDown) theme.icons.next else theme.icons.previous,
                    height = currentHeight,
                    widthFraction = currentWidth,
                    offsetX = dragX,
                    offsetY = verticalOffset,
                    detailAlpha = 1f - verticalProgress,
                    previewAlpha = verticalProgress,
                    containerAlpha = lerpFloat(1f, .72f, verticalProgress),
                    highlightProgress = 0f,
                    zIndex = 2f * (1f - verticalProgress),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(centerHeight)
                        .zIndex(4f)
                        .semantics {
                            customActions = accessibilityActions
                            onClick("編集") { onEdit(item); true }
                        }
                        .pointerInput(item.id, hasPrevious, hasNext, animating) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (animating) {
                                    do {
                                        val event = awaitPointerEvent(PointerEventPass.Main)
                                    } while (event.changes.any { it.pressed })
                                    return@awaitEachGesture
                                }

                                val velocityTracker = VelocityTracker()
                                velocityTracker.addPosition(down.uptimeMillis, Offset.Zero)
                                val speed = with(density) { 900.dp.toPx() }
                                val ownershipSlop = viewConfiguration.touchSlop
                                var rawX = 0f
                                var rawY = 0f
                                var claimedByCard = false
                                var gestureHapticSent = false
                                var blockedHapticSent = false
                                axis = null
                                dragX = 0f
                                dragY = 0f

                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null) {
                                        settle(null)
                                        break
                                    }
                                    val movement = change.position - change.previousPosition
                                    rawX += movement.x
                                    rawY += movement.y
                                    velocityTracker.addPosition(change.uptimeMillis, Offset(rawX, rawY))

                                    if (!claimedByCard && max(abs(rawX), abs(rawY)) >= ownershipSlop) {
                                        claimedByCard = true
                                    }
                                    if (axis == null) {
                                        axis = SwipeGesturePhysics.lockAxis(rawX, rawY, axisLock)
                                    }
                                    if (claimedByCard) change.consume()

                                    when (axis) {
                                        DragAxis.HORIZONTAL -> {
                                            dragX = SwipeGesturePhysics.visualOffset(rawX, theme.cardFollow, widthPx * 1.15f)
                                            dragY = 0f
                                            if (!gestureHapticSent && abs(rawX) >= threshold) {
                                                performHapticFeedback()
                                                gestureHapticSent = true
                                            }
                                        }
                                        DragAxis.VERTICAL -> {
                                            dragX = 0f
                                            val blocked = (rawY < 0 && !hasNext) || (rawY > 0 && !hasPrevious)
                                            dragY = if (blocked) {
                                                SwipeGesturePhysics.visualOffset(rawY, theme.cardFollow, resistance)
                                            } else {
                                                SwipeGesturePhysics.visualOffset(rawY, theme.cardFollow, verticalTransitionPx)
                                            }
                                            if (blocked && !blockedHapticSent && abs(rawY) >= resistance) {
                                                performHapticFeedback()
                                                blockedHapticSent = true
                                            } else if (!blocked && !gestureHapticSent && abs(rawY) >= threshold) {
                                                performHapticFeedback()
                                                gestureHapticSent = true
                                            }
                                        }
                                        null -> {
                                            dragX = rawX * theme.cardFollow
                                            dragY = rawY * theme.cardFollow
                                        }
                                    }

                                    if (!change.pressed) {
                                        if (axis == null && !claimedByCard) {
                                            dragX = 0f
                                            dragY = 0f
                                            onEdit(item)
                                        } else {
                                            val velocity = velocityTracker.calculateVelocity()
                                            val result = SwipeGesturePhysics.resolve(
                                                axis = axis,
                                                distanceX = rawX,
                                                distanceY = rawY,
                                                velocityX = velocity.x,
                                                velocityY = velocity.y,
                                                distanceThreshold = threshold,
                                                velocityThreshold = speed,
                                                hasPrevious = hasPrevious,
                                                hasNext = hasNext,
                                            )
                                            settle(result, gestureHapticSent)
                                        }
                                        break
                                    }
                                }
                            }
                        },
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onEdit(item) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (addButtonPosition == AddButtonPosition.LEFT) 88.dp else 0.dp,
                        end = if (addButtonPosition == AddButtonPosition.RIGHT) 88.dp else 0.dp,
                        bottom = if (addButtonPosition == AddButtonPosition.CENTER) {
                            (88 + addButtonBottomOffsetDp.coerceIn(0, 160)).dp
                        } else {
                            0.dp
                        },
                    )
                    .height(48.dp),
            ) { Text("${theme.icons.edit} 編集") }
        }
    }
}

@Composable
private fun HorizontalGuide(
    rightLabel: String,
    leftLabel: String,
    rightIcon: String,
    leftIcon: String,
    dragX: Float,
    axis: DragAxis?,
) {
    if (axis != DragAxis.HORIZONTAL || abs(dragX) < 1f) return
    val tokens = LocalThemeColors.current
    val revealRight = dragX > 0f
    Box(
        Modifier
            .fillMaxWidth()
            .height(330.dp)
            .background(
                parseColor(if (revealRight) tokens.success else tokens.defer),
                RoundedCornerShape(LocalAppTheme.current.cardCornerDp.dp),
            )
            .alpha((abs(dragX) / 120f).coerceIn(.2f, 1f)),
        contentAlignment = if (revealRight) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Text(
            if (revealRight) "$rightIcon  $rightLabel" else "$leftLabel  $leftIcon",
            Modifier.padding(20.dp),
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ItemCardContent(item: AppItem) {
    val tokens = LocalThemeColors.current
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(
                if (item.kind == ItemKind.TODO) "やること" else "メモ",
                style = MaterialTheme.typography.caption,
                color = tokens.colorForKind(item.kind),
            )
            Spacer(Modifier.height(16.dp))
            Text(item.title, style = MaterialTheme.typography.h5)
            if (item.body.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(item.body.take(180), style = MaterialTheme.typography.body2, color = parseColor(tokens.textSecondary), maxLines = 5)
            }
        }
        if (item.kind == ItemKind.TODO) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item.todo?.scheduledAtEpochMillis?.let { Text("行う時刻  ${formatDateTime(it)}") }
                item.todo?.dueAtEpochMillis?.let { Text("期限  ${formatDateTime(it)}", color = parseColor(tokens.warning)) }
                item.todo?.estimatedMinutes?.let { Text("約${it}分") }
                val deferred = item.todo?.deferCount ?: 0
                if (deferred > 0) Text("${LocalAppTheme.current.icons.defer} ${deferred}回 後で行う", color = parseColor(tokens.defer))
            }
        }
    }
}

@Composable
private fun MorphingDeckCard(
    item: AppItem?,
    positionLabel: String,
    edgeText: String,
    directionMark: String,
    height: Dp,
    widthFraction: Float,
    offsetX: Float,
    offsetY: Float,
    detailAlpha: Float,
    previewAlpha: Float,
    containerAlpha: Float,
    highlightProgress: Float,
    zIndex: Float,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalThemeColors.current
    val theme = LocalAppTheme.current
    val safeHighlightProgress = highlightProgress.coerceIn(0f, 1f)
    val borderColor = lerpColor(parseColor(tokens.border), parseColor(tokens.focusRing), safeHighlightProgress)
    val safeDetailAlpha = if (item == null) 0f else detailAlpha.coerceIn(0f, 1f)
    val safePreviewAlpha = if (item == null) 1f else previewAlpha.coerceIn(0f, 1f)
    val contentDifference = safeDetailAlpha - safePreviewAlpha
    val detailContentAlpha = (contentDifference * 8f).coerceIn(0f, 1f)
    val previewContentAlpha = (-contentDifference * 8f).coerceIn(0f, 1f)
    Card(
        modifier = Modifier
            .fillMaxWidth(widthFraction.coerceIn(.1f, 1f))
            .height(height)
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .zIndex(zIndex)
            .alpha(containerAlpha.coerceIn(0f, 1f))
            .border(
                lerpFloat(theme.borderWidthDp, 2f, safeHighlightProgress).dp,
                borderColor,
                RoundedCornerShape(theme.mediumCornerDp.dp),
            )
            .then(modifier),
        shape = RoundedCornerShape(
            lerpFloat(theme.mediumCornerDp, theme.cardCornerDp, safeDetailAlpha).dp,
        ),
        elevation = if (item == null) 0.dp else lerpFloat(2f, theme.cardElevationDp, safeDetailAlpha).dp,
        backgroundColor = parseColor(if (item == null) tokens.surfaceAlt else tokens.surface),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (item != null && detailContentAlpha > .01f) {
                Box(Modifier.fillMaxSize().alpha(detailContentAlpha)) {
                    ItemCardContent(item)
                }
            }
            if (item == null || previewContentAlpha > .01f) {
                Box(Modifier.fillMaxSize().alpha(if (item == null) 1f else previewContentAlpha)) {
                    PreviewCardContent(item, positionLabel, edgeText, directionMark, borderColor)
                }
            }
        }
    }
}

@Composable
private fun PreviewCardContent(
    item: AppItem?,
    positionLabel: String,
    edgeText: String,
    directionMark: String,
    borderColor: Color,
) {
    val tokens = LocalThemeColors.current
    Row(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(positionLabel, style = MaterialTheme.typography.overline, color = parseColor(tokens.textSecondary))
            Text(
                item?.title ?: edgeText,
                style = MaterialTheme.typography.body2,
                fontWeight = if (item == null) FontWeight.Normal else FontWeight.Medium,
                color = parseColor(if (item == null) tokens.unavailableGuide else tokens.textPrimary),
                maxLines = 1,
            )
        }
        Text(if (item == null) LocalAppTheme.current.icons.unavailable else directionMark, color = borderColor)
    }
}

private fun lerpFloat(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * progress.coerceIn(0f, 1f)

private fun lerpColor(start: Color, end: Color, progress: Float): Color = Color(
    red = lerpFloat(start.red, end.red, progress),
    green = lerpFloat(start.green, end.green, progress),
    blue = lerpFloat(start.blue, end.blue, progress),
    alpha = lerpFloat(start.alpha, end.alpha, progress),
)

private fun lerpDp(start: Dp, end: Dp, progress: Float): Dp =
    lerpFloat(start.value, end.value, progress).dp

@Composable
private fun SwipeOperationGuide(leftLabel: String, rightLabel: String, leftIcon: String, rightIcon: String) {
    val tokens = LocalThemeColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalAppTheme.current.smallCornerDp.dp),
        elevation = 0.dp,
        backgroundColor = parseColor(tokens.surfaceAlt),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp)) {
            Text(
                "カードをスワイプ",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.overline,
                color = parseColor(tokens.textSecondary),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("$leftIcon $leftLabel", Modifier.weight(1f), style = MaterialTheme.typography.caption)
                Text("${LocalAppTheme.current.icons.next} 次へ ・ 前へ ${LocalAppTheme.current.icons.previous}", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.caption)
                Text("$rightLabel $rightIcon", Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.caption)
            }
        }
    }
}

@Composable
private fun EmptyDeck(kind: ItemKind) {
    val label = if (kind == ItemKind.TODO) "やること" else "メモ"
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ここにはまだ${label}がありません", style = MaterialTheme.typography.h6, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("${LocalAppTheme.current.icons.add} から追加できます", color = parseColor(LocalThemeColors.current.textSecondary))
    }
}

private fun formatDateTime(epochMillis: Long): String {
    val value = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${value.monthNumber}/${value.dayOfMonth} ${value.hour.toString().padStart(2, '0')}:${value.minute.toString().padStart(2, '0')}"
}
