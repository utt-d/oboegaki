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
internal fun DateOnlyField(
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
internal fun DateTimeField(
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
internal fun PriorityChoices(selected: Priority, onSelect: (Priority) -> Unit) {
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
