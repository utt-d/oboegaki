package jp.oboegaki.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import jp.oboegaki.core.domain.ThemePolicy
import jp.oboegaki.core.data.BackupInspectionResult
import jp.oboegaki.core.model.AppearanceMode
import jp.oboegaki.core.model.AddButtonPosition
import jp.oboegaki.core.model.AppSettings
import jp.oboegaki.core.model.MainNavigationButton
import jp.oboegaki.core.model.MotionStrength
import jp.oboegaki.core.model.ThemeColors
import jp.oboegaki.core.model.ThemeDefinition
import jp.oboegaki.core.model.TopActionButton
import jp.oboegaki.platform.NotificationState
import jp.oboegaki.platform.NotificationTestResult
import jp.oboegaki.platform.currentNotificationStatus
import jp.oboegaki.platform.notificationSettingsEvents
import jp.oboegaki.platform.openNotificationSettings
import jp.oboegaki.platform.tryTestNotification
import androidx.compose.runtime.LaunchedEffect
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt
import kotlin.math.abs

@Composable
fun SettingsScreen(settings: AppSettings, controller: AppController) {
    var draft by remember(settings) { mutableStateOf(settings) }
    var detailedSettingsExpanded by remember { mutableStateOf(false) }
    OverlayScaffold("設定", controller::closeOverlay, action = {
        TextButton(onClick = { controller.saveSettings(draft) }, modifier = Modifier.height(48.dp)) { Text("保存") }
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { SectionTitle("操作") }
            item { SettingSwitch("触覚フィードバック", draft.hapticsEnabled) { draft = draft.copy(hapticsEnabled = it) } }
            item { SettingSwitch("Enterで追加", draft.addWithEnter) { draft = draft.copy(addWithEnter = it) } }
            item { SectionTitle("通知") }
            item {
                SettingSwitch("ロック画面に内容を表示", draft.showReminderContentOnLockScreen) {
                    draft = draft.copy(showReminderContentOnLockScreen = it)
                }
                Text(
                    "初期状態ではロック画面にタイトルを表示しません。端末や省電力機能により通知が遅れる場合があります。",
                    style = MaterialTheme.typography.caption,
                    color = parseColor(LocalThemeColors.current.textSecondary),
                )
            }
            item {
                SettingSwitch("通知から完了・後で行うを使う", draft.reminderNotificationActionsEnabled) {
                    draft = draft.copy(reminderNotificationActionsEnabled = it)
                }
                Text(
                    "通知からの操作は端末内で処理し、通常の画面を勝手に前面表示しません。",
                    style = MaterialTheme.typography.caption,
                    color = parseColor(LocalThemeColors.current.textSecondary),
                )
            }
            item {
                var status by remember { mutableStateOf(currentNotificationStatus()) }
                var testMessage by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) {
                    notificationSettingsEvents().collect {
                        status = currentNotificationStatus()
                    }
                }
                Text("通知の状態", style = MaterialTheme.typography.subtitle1)
                Text(
                    when (status.state) {
                        NotificationState.ENABLED -> "通知は有効です"
                        NotificationState.POST_NOTIFICATIONS_REQUIRED -> "通知の許可が必要です（Android 13以降）"
                        NotificationState.APP_NOTIFICATIONS_DISABLED -> "アプリの通知が端末設定で無効です"
                        NotificationState.CHANNEL_DISABLED -> "やることの通知チャンネルが端末設定で無効です"
                        NotificationState.CHANNEL_NOT_READY -> "やることの通知チャンネルを準備しています"
                        NotificationState.PLATFORM_MANAGED -> "通知の状態は端末の設定で管理されています"
                    },
                    color = if (status.state == NotificationState.ENABLED) {
                        parseColor(LocalThemeColors.current.success)
                    } else {
                        parseColor(LocalThemeColors.current.textSecondary)
                    },
                )
                testMessage?.let {
                    Text(it, style = MaterialTheme.typography.caption, color = parseColor(LocalThemeColors.current.textSecondary))
                }
                if (status.state != NotificationState.PLATFORM_MANAGED) {
                    OutlinedButton(
                        onClick = {
                            val result = tryTestNotification()
                            status = currentNotificationStatus()
                            testMessage = when (result) {
                                NotificationTestResult.Sent -> "実際の通知チャンネルへ試しに通知しました"
                                NotificationTestResult.PermissionRequired -> "通知を許可すると試せます"
                                NotificationTestResult.AppNotificationsDisabled -> "アプリの通知を端末設定で有効にしてください"
                                NotificationTestResult.ChannelDisabled -> "通知チャンネルを端末設定で有効にしてください"
                                NotificationTestResult.ChannelNotReady -> "やることの通知チャンネルを準備しています"
                                NotificationTestResult.PlatformManaged -> "通知の状態は端末の設定で確認してください"
                                is NotificationTestResult.Failed -> result.reason
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("通知を試す") }
                }
                OutlinedButton(
                    onClick = {
                        openNotificationSettings()
                        status = currentNotificationStatus()
                    },
                    enabled = status.state != NotificationState.PLATFORM_MANAGED,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("端末の通知設定を開く") }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().clickable { detailedSettingsExpanded = !detailedSettingsExpanded }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("詳しい設定", style = MaterialTheme.typography.h6)
                        Text("ボタンの位置・順番など", style = MaterialTheme.typography.caption, color = parseColor(LocalThemeColors.current.textSecondary))
                    }
                    Text(if (detailedSettingsExpanded) "▾" else "▸", style = MaterialTheme.typography.h6)
                }
            }
            if (detailedSettingsExpanded) {
                item {
                    Text("追加ボタンの横位置", style = MaterialTheme.typography.subtitle1)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AddButtonPosition.values().forEach { position ->
                            val label = when (position) {
                                AddButtonPosition.LEFT -> "左"
                                AddButtonPosition.CENTER -> "中央"
                                AddButtonPosition.RIGHT -> "右"
                            }
                            if (draft.addButtonPosition == position) {
                                Button(onClick = { draft = draft.copy(addButtonPosition = position) }, Modifier.weight(1f).height(48.dp)) { Text(label) }
                            } else {
                                OutlinedButton(onClick = { draft = draft.copy(addButtonPosition = position) }, Modifier.weight(1f).height(48.dp)) { Text(label) }
                            }
                        }
                    }
                }
                item {
                    ValueSlider(
                        label = "追加ボタンの高さ",
                        valueLabel = addButtonHeightLabel(draft.addButtonBottomOffsetDp),
                        value = draft.addButtonBottomOffsetDp.toFloat(),
                        range = 0f..160f,
                        steps = 7,
                    ) { draft = draft.copy(addButtonBottomOffsetDp = it.roundToInt()) }
                }
                item {
                    val order = normalizedOrder(draft.navigationButtonOrder, MainNavigationButton.values().toList())
                    ButtonOrderEditor(
                        title = "画面切り替えボタン",
                        description = "下側に並べる順番を変更できます。",
                        order = order,
                        label = {
                            when (it) {
                                MainNavigationButton.TODOS -> "やること"
                                MainNavigationButton.MEMOS -> "メモ"
                                MainNavigationButton.ALL -> "すべて"
                            }
                        },
                        onOrderChange = { draft = draft.copy(navigationButtonOrder = it) },
                    )
                }
                item {
                    val order = normalizedOrder(draft.topActionButtonOrder, TopActionButton.values().toList())
                    ButtonOrderEditor(
                        title = "上部の操作ボタン",
                        description = "「すべて」画面の右上に並べる順番を変更できます。",
                        order = order,
                        label = {
                            when (it) {
                                TopActionButton.THEMES -> "テーマ"
                                TopActionButton.SETTINGS -> "設定"
                            }
                        },
                        onOrderChange = { draft = draft.copy(topActionButtonOrder = it) },
                    )
                }
                item {
                    OutlinedButton(
                        onClick = {
                            draft = draft.copy(
                                addButtonPosition = AddButtonPosition.LEFT,
                                addButtonBottomOffsetDp = 8,
                                navigationButtonOrder = MainNavigationButton.values().toList(),
                                topActionButtonOrder = TopActionButton.values().toList(),
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("ボタン配置を標準に戻す") }
                }
                item {
                    SettingSwitch("空きスペースの左右スワイプで画面を切り替える", draft.tabSwipeEnabled) {
                        draft = draft.copy(tabSwipeEnabled = it)
                    }
                }
                item {
                    ValueSlider("元に戻す表示", "${draft.undoSeconds}秒", draft.undoSeconds.toFloat(), 3f..10f, 6) {
                        draft = draft.copy(undoSeconds = it.roundToInt())
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { controller.saveSettingsAndOpenOperationGuide(draft) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("${LocalAppTheme.current.icons.all} 操作ガイドを見る") }
            }
            item { SectionTitle("後で行う") }
            item {
                ValueSlider("何件後に再表示", "${draft.deferItems}件後", draft.deferItems.toFloat(), 1f..20f, 18) {
                    draft = draft.copy(deferItems = it.roundToInt())
                }
            }
            item {
                SettingSwitch("何度も後で行うと、小さく分ける提案を表示", draft.splitSuggestionEnabled) {
                    draft = draft.copy(splitSuggestionEnabled = it)
                }
            }
            item {
                ValueSlider("提案する回数", "${draft.splitThreshold}回", draft.splitThreshold.toFloat(), 1f..10f, 8) {
                    draft = draft.copy(splitThreshold = it.roundToInt())
                }
            }
            item { SectionTitle("カレンダー連携") }
            item {
                SettingSwitch("やることを端末のカレンダーへ追加できるようにする", draft.calendarIntegrationEnabled) {
                    draft = draft.copy(calendarIntegrationEnabled = it)
                }
                Text(
                    "Google カレンダー、Outlookなど、端末に設定されたカレンダーを利用します。追加先の選択方法は端末により異なり、自動同期は行いません。",
                    style = MaterialTheme.typography.caption,
                    color = parseColor(LocalThemeColors.current.textSecondary),
                )
            }
            item { SectionTitle("表示") }
            item {
                Text("外観モード", style = MaterialTheme.typography.subtitle1)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppearanceMode.values().forEach { mode ->
                        val label = when (mode) {
                            AppearanceMode.SYSTEM -> "端末"
                            AppearanceMode.LIGHT -> "明るい"
                            AppearanceMode.DARK -> "暗い"
                        }
                        if (draft.appearanceMode == mode) Button(onClick = { draft = draft.copy(appearanceMode = mode) }, Modifier.weight(1f).height(48.dp)) { Text(label) }
                        else OutlinedButton(onClick = { draft = draft.copy(appearanceMode = mode) }, Modifier.weight(1f).height(48.dp)) { Text(label) }
                    }
                }
            }
            item { SettingSwitch("動きを減らす", draft.reducedMotion) { draft = draft.copy(reducedMotion = it) } }
            item {
                Divider()
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = controller::openThemes, Modifier.fillMaxWidth().height(48.dp)) { Text("テーマを選ぶ・編集する") }
                OutlinedButton(onClick = controller::openDataTools, Modifier.fillMaxWidth().height(48.dp)) { Text("データのバックアップ") }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun <T> ButtonOrderEditor(
    title: String,
    description: String,
    order: List<T>,
    label: (T) -> String,
    onOrderChange: (List<T>) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
    Text(
        description,
        style = MaterialTheme.typography.caption,
        color = parseColor(LocalThemeColors.current.textSecondary),
    )
    Spacer(Modifier.height(6.dp))
    order.forEachIndexed { index, button ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("${index + 1}. ${label(button)}", modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = { onOrderChange(moveInList(order, index, index - 1)) },
                enabled = index > 0,
                modifier = Modifier.width(56.dp).height(48.dp),
            ) { Text("←") }
            OutlinedButton(
                onClick = { onOrderChange(moveInList(order, index, index + 1)) },
                enabled = index < order.lastIndex,
                modifier = Modifier.width(56.dp).height(48.dp),
            ) { Text("→") }
        }
    }
}

private fun <T> normalizedOrder(value: List<T>, defaults: List<T>): List<T> =
    value.filter { it in defaults }.distinct() + defaults.filterNot { it in value }

private fun <T> moveInList(values: List<T>, from: Int, to: Int): List<T> {
    if (from !in values.indices || to !in values.indices || from == to) return values
    return values.toMutableList().apply {
        val moved = removeAt(from)
        add(to, moved)
    }
}

@Composable
fun OperationGuideScreen(
    firstLaunch: Boolean,
    addButtonPosition: AddButtonPosition,
    controller: AppController,
) {
    val icons = LocalAppTheme.current.icons
    val addButtonLocation = when (addButtonPosition) {
        AddButtonPosition.LEFT -> "左"
        AddButtonPosition.CENTER -> "中央"
        AddButtonPosition.RIGHT -> "右"
    }
    OverlayScaffold(
        title = if (firstLaunch) "はじめに" else "操作ガイド",
        onClose = { controller.finishOperationGuide(firstLaunch) },
        showHeaderClose = false,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        if (firstLaunch) "おぼえがきへようこそ" else "基本の操作",
                        style = MaterialTheme.typography.h5,
                    )
                    Text(
                        if (firstLaunch) {
                            "最初は、何も登録されていない空の状態です。\n\n思いついた内容は、種類を決めずに追加することもできます。"
                        } else {
                            "基本の操作を、項目ごとに確認できます。\n\nこのガイドは設定からいつでも開けます。"
                        },
                        style = MaterialTheme.typography.body1.copy(lineHeight = 23.sp),
                        color = parseColor(LocalThemeColors.current.textSecondary),
                    )
                }
                item {
                    GuideStep(
                        icon = icons.add,
                        title = "まず追加する",
                        points = listOf(
                            "画面${addButtonLocation}下の追加ボタンを押します。",
                            "追加ボタンは、設定の「詳しい設定」から位置と高さを変更できます。",
                            "ハンドルを上へ引くと詳細が開き、下へ引くと閉じます。",
                        ),
                    )
                }
                item {
                    GuidePractice(
                        icons = icons,
                    )
                }
                if (firstLaunch) {
                    item {
                        GuideStep(
                            icon = "${icons.memo}  ${icons.defer}  ${icons.complete}",
                            title = "これだけ覚えれば始められます",
                            points = listOf(
                                "メモは、左でしまう・右でやることにします。",
                                "操作後は「元に戻す」が使えます。",
                                "詳しい説明は設定からいつでも見返せます。",
                            ),
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                } else {
                    item {
                        GuideStep(
                            icon = "${icons.previous} ${icons.next}",
                            title = "カードを上下に動かす",
                            points = listOf(
                                "上へスワイプ：次の項目へ移動",
                                "下へスワイプ：前の項目へ移動",
                                "前後のカードも指に合わせて動きます。",
                            ),
                        )
                    }
                    item {
                        GuideStep(
                            icon = "${icons.defer}  ${icons.complete}",
                            title = "左右で整理する",
                            points = listOf(
                                "やること　左：後で行う ／ 右：完了",
                                "メモ　　　左：しまう ／ 右：やることにする",
                                "操作後は「元に戻す」が使えます。",
                            ),
                        )
                    }
                    item {
                        GuideStep(
                            icon = "${icons.todo}  ${icons.memo}  ${icons.all}",
                            title = "画面を切り替える",
                            points = listOf(
                                "画面下の項目をタップして切り替えます。",
                                "設定を有効にすると、空きスペースの左右スワイプでも切り替えられます。",
                                "「すべて」では、編集・並べ替え・復元ができます。",
                            ),
                        )
                    }
                    item {
                        GuideStep(
                            icon = "${icons.theme}  ${icons.settings}",
                            title = "外観と操作を調整する",
                            points = listOf(
                                "「すべて」画面から、テーマと設定を開けます。",
                                "色・フォント・アイコン・ボタン配置・動き・触覚などを変更できます。",
                                "このガイドも設定から見返せます。",
                            ),
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
            Button(
                onClick = { controller.finishOperationGuide(firstLaunch) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(if (firstLaunch) "使い始める" else "設定へ戻る") }
        }
    }
}

@Composable
private fun GuideStep(icon: String, title: String, points: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalAppTheme.current.mediumCornerDp.dp),
        elevation = 1.dp,
        backgroundColor = parseColor(LocalThemeColors.current.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, style = MaterialTheme.typography.h5, modifier = Modifier.width(64.dp), textAlign = TextAlign.Center)
                Text(title, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            points.forEach { point ->
                Row(Modifier.fillMaxWidth()) {
                    Text("•", modifier = Modifier.width(22.dp), textAlign = TextAlign.Center)
                    Text(
                        point,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.body2.copy(lineHeight = 21.sp),
                        color = parseColor(LocalThemeColors.current.textSecondary),
                    )
                }
                Spacer(Modifier.height(5.dp))
            }
        }
    }
}

@Composable
private fun GuidePractice(
    icons: jp.oboegaki.core.model.ThemeIcons,
) {
    var step by remember { mutableStateOf(0) }
    var cardTitle by remember { mutableStateOf("サンプルのやること") }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }

    fun completeVertical(next: Boolean) {
        cardTitle = if (next) "次のサンプル" else "前のサンプル"
        offsetX = 0f
        offsetY = 0f
        step = 1
    }

    fun completeHorizontal(complete: Boolean) {
        cardTitle = if (complete) "完了したサンプル" else "あとで行うサンプル"
        offsetX = 0f
        offsetY = 0f
        step = 2
    }

    val practiceColor = when {
        step == 1 && offsetX > 0f -> LocalThemeColors.current.success
        step == 1 && offsetX < 0f -> LocalThemeColors.current.defer
        else -> LocalThemeColors.current.accent
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalAppTheme.current.mediumCornerDp.dp),
        elevation = 1.dp,
        backgroundColor = parseColor(LocalThemeColors.current.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("さわって覚える", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
            Text(
                when (step) {
                    0 -> "練習用カードを上下どちらかへ動かしてください。実際のデータは変わりません。"
                    1 -> "次に、カードを左か右へ動かして整理してください。"
                    else -> "練習できました。いつでも設定から見返せます。"
                },
                color = parseColor(LocalThemeColors.current.textSecondary),
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .pointerInput(step, threshold) {
                        detectDragGestures(
                            onDragEnd = {
                                when {
                                    step == 0 && abs(offsetY) >= threshold -> completeVertical(offsetY < 0f)
                                    step == 1 && abs(offsetX) >= threshold -> completeHorizontal(offsetX > 0f)
                                    else -> {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                }
                            },
                            onDragCancel = {
                                offsetX = 0f
                                offsetY = 0f
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            when (step) {
                                0 -> offsetY += dragAmount.y
                                1 -> offsetX += dragAmount.x
                            }
                        }
                    },
                backgroundColor = parseColor(practiceColor).copy(alpha = .16f),
                elevation = 0.dp,
            ) {
                Text("${icons.todo}  $cardTitle", Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
            }
            when (step) {
                0 -> {
                    Text("上下の移動", style = MaterialTheme.typography.caption)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { completeVertical(false) }, Modifier.weight(1f).height(52.dp)) {
                            Text("${icons.previous} 前へ")
                        }
                        Button(onClick = { completeVertical(true) }, Modifier.weight(1f).height(52.dp)) {
                            Text("${icons.next} 次へ")
                        }
                    }
                }
                1 -> {
                    Text("左右の整理", style = MaterialTheme.typography.caption)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { completeHorizontal(false) }, Modifier.weight(1f).height(52.dp)) {
                            Text("${icons.defer} 後で行う")
                        }
                        Button(onClick = { completeHorizontal(true) }, Modifier.weight(1f).height(52.dp)) {
                            Text("${icons.complete} 完了")
                        }
                    }
                }
                else -> {
                    Text("カードをタップして編集することもできます。", style = MaterialTheme.typography.caption)
                    OutlinedButton(onClick = {
                        step = 0
                        cardTitle = "サンプルのやること"
                        offsetX = 0f
                        offsetY = 0f
                    }, Modifier.fillMaxWidth().height(48.dp)) {
                        Text("もう一度練習する")
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeListScreen(themes: List<ThemeDefinition>, settings: AppSettings, controller: AppController) {
    val systemDark = isSystemInDarkTheme()
    val darkVariant = when (settings.appearanceMode) {
        AppearanceMode.SYSTEM -> systemDark
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }
    OverlayScaffold("テーマ", controller::closeOverlay) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("表示を切り替えると、組み込みテーマを含む画面全体と見本がすぐに変わります。")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppearanceMode.values().forEach { mode ->
                        val label = when (mode) {
                            AppearanceMode.SYSTEM -> "端末"
                            AppearanceMode.LIGHT -> "明るい"
                            AppearanceMode.DARK -> "暗い"
                        }
                        if (settings.appearanceMode == mode) {
                            Button(
                                onClick = { controller.setAppearanceMode(mode) },
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) { Text(label) }
                        } else {
                            OutlinedButton(
                                onClick = { controller.setAppearanceMode(mode) },
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) { Text(label) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "現在の見本：${if (darkVariant) "暗い表示" else "明るい表示"}",
                    style = MaterialTheme.typography.caption,
                    color = parseColor(LocalThemeColors.current.textSecondary),
                )
            }
            items(themes, key = { it.id }) { theme ->
                ThemeListCard(theme, theme.id == settings.selectedThemeId, darkVariant, controller)
            }
            item {
                val base = themes.firstOrNull { it.id == settings.selectedThemeId } ?: themes.first()
                Button(onClick = { controller.duplicateTheme(base.copy(name = "新しいテーマ")) }, Modifier.fillMaxWidth().height(52.dp)) {
                    Text("新規テーマを作る")
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun ThemeListCard(
    theme: ThemeDefinition,
    selected: Boolean,
    darkVariant: Boolean,
    controller: AppController,
) {
    val colors = if (darkVariant) theme.dark else theme.light
    Card(
        Modifier.fillMaxWidth().clickable { controller.applyTheme(theme.id) },
        shape = RoundedCornerShape(theme.mediumCornerDp.dp),
        elevation = if (selected) 6.dp else 1.dp,
        backgroundColor = parseColor(colors.surface),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(colors.accent, colors.todo, colors.memo, colors.success).forEach {
                        Box(Modifier.width(18.dp).height(36.dp).background(parseColor(it), RoundedCornerShape(4.dp)))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${theme.icons.todo} ${theme.icons.memo}  ${theme.name}",
                        color = parseColor(colors.textPrimary),
                        fontWeight = FontWeight.Bold,
                        fontFamily = themeFontFamily(theme.fontFamily),
                    )
                    Text(
                        if (theme.builtIn) {
                            "組み込み・${if (darkVariant) "暗い表示" else "明るい表示"}"
                        } else {
                            "${theme.fontFamily}・カスタム・${if (darkVariant) "暗い表示" else "明るい表示"}"
                        },
                        color = parseColor(colors.textSecondary),
                        style = MaterialTheme.typography.caption,
                        fontFamily = themeFontFamily(theme.fontFamily),
                    )
                }
                Text(if (selected) "適用中 ✓" else "選ぶ", color = parseColor(colors.accent))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { controller.duplicateTheme(theme) }) { Text("複製") }
                if (!theme.builtIn) {
                    TextButton(onClick = { controller.openThemeEditor(theme) }) { Text("編集") }
                    TextButton(onClick = { controller.deleteTheme(theme.id) }) { Text("削除", color = MaterialTheme.colors.error) }
                }
            }
        }
    }
}

@Composable
fun ThemeEditorScreen(source: ThemeDefinition, controller: AppController) {
    var draft by remember(source.id) { mutableStateOf(source.copy(builtIn = false)) }
    var darkVariant by remember { mutableStateOf(false) }
    var jsonText by remember(source.id) { mutableStateOf("") }
    var showJson by remember { mutableStateOf(false) }
    val codec = remember { Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true } }
    val colors = if (darkVariant) draft.dark else draft.light

    fun updateColors(value: ThemeColors) {
        draft = if (darkVariant) draft.copy(dark = value) else draft.copy(light = value)
    }

    OverlayScaffold("テーマを編集", controller::closeOverlay, action = {
        TextButton(onClick = { controller.saveTheme(draft) }, modifier = Modifier.height(48.dp)) { Text("保存") }
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(draft.name, { draft = draft.copy(name = it.take(50)) }, Modifier.fillMaxWidth(), label = { Text("テーマ名") })
                Spacer(Modifier.height(10.dp))
                LiveThemePreview(draft, colors)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!darkVariant) Button(onClick = { darkVariant = false }, Modifier.weight(1f)) { Text("明るい配色") }
                    else OutlinedButton(onClick = { darkVariant = false }, Modifier.weight(1f)) { Text("明るい配色") }
                    if (darkVariant) Button(onClick = { darkVariant = true }, Modifier.weight(1f)) { Text("暗い配色") }
                    else OutlinedButton(onClick = { darkVariant = true }, Modifier.weight(1f)) { Text("暗い配色") }
                }
            }
            item { SectionTitle("色") }
            item {
                ColorField("背景", colors.background) { updateColors(colors.copy(background = it)) }
                ColorField("カード", colors.surface) { updateColors(colors.copy(surface = it)) }
                ColorField("補助面", colors.surfaceAlt) { updateColors(colors.copy(surfaceAlt = it)) }
                ColorField("主な文字", colors.textPrimary) { updateColors(colors.copy(textPrimary = it)) }
                ColorField("補助文字", colors.textSecondary) { updateColors(colors.copy(textSecondary = it)) }
                ColorField("主操作", colors.accent) { updateColors(colors.copy(accent = it)) }
                ColorField("主操作上の文字", colors.onAccent) { updateColors(colors.copy(onAccent = it)) }
                ColorField("やること", colors.todo) { updateColors(colors.copy(todo = it)) }
                ColorField("メモ", colors.memo) { updateColors(colors.copy(memo = it)) }
                ColorField("完了", colors.success) { updateColors(colors.copy(success = it)) }
                ColorField("後で行う", colors.defer) { updateColors(colors.copy(defer = it)) }
                val primaryRatio = ThemePolicy.contrast(colors.textPrimary, colors.background)
                Text("主な文字のコントラスト ${formatRatio(primaryRatio)} : 1（推奨 4.5 : 1）", color = if (primaryRatio < 4.5) parseColor(colors.warning) else parseColor(colors.success))
            }
            item { SectionTitle("フォント") }
            item {
                Text("アプリ全体で使用する文字の種類を選べます。", style = MaterialTheme.typography.body2)
                Spacer(Modifier.height(8.dp))
                val fontOptions = listOf(
                    "System" to "端末標準",
                    "Sans Serif" to "ゴシック",
                    "Serif" to "明朝",
                    "Monospace" to "等幅",
                    "Cursive" to "手書き風",
                )
                fontOptions.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (value, label) ->
                            if (draft.fontFamily == value) {
                                Button(
                                    onClick = { draft = draft.copy(fontFamily = value) },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                ) { Text(label, fontFamily = themeFontFamily(value)) }
                            } else {
                                OutlinedButton(
                                    onClick = { draft = draft.copy(fontFamily = value) },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                ) { Text(label, fontFamily = themeFontFamily(value)) }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            item { SectionTitle("アイコン") }
            item {
                Text("絵文字や記号を入力して、テーマごとに自由に変更できます。", style = MaterialTheme.typography.body2)
                Spacer(Modifier.height(8.dp))
                IconFieldRow("やること", draft.icons.todo, { draft = draft.copy(icons = draft.icons.copy(todo = it)) }, "メモ", draft.icons.memo, { draft = draft.copy(icons = draft.icons.copy(memo = it)) })
                IconFieldRow("すべて", draft.icons.all, { draft = draft.copy(icons = draft.icons.copy(all = it)) }, "追加", draft.icons.add, { draft = draft.copy(icons = draft.icons.copy(add = it)) })
                IconFieldRow("編集", draft.icons.edit, { draft = draft.copy(icons = draft.icons.copy(edit = it)) }, "完了", draft.icons.complete, { draft = draft.copy(icons = draft.icons.copy(complete = it)) })
                IconFieldRow("後で行う", draft.icons.defer, { draft = draft.copy(icons = draft.icons.copy(defer = it)) }, "やること化", draft.icons.convert, { draft = draft.copy(icons = draft.icons.copy(convert = it)) })
                IconFieldRow("しまう", draft.icons.archive, { draft = draft.copy(icons = draft.icons.copy(archive = it)) }, "次へ", draft.icons.next, { draft = draft.copy(icons = draft.icons.copy(next = it)) })
                IconFieldRow("前へ", draft.icons.previous, { draft = draft.copy(icons = draft.icons.copy(previous = it)) }, "操作不可", draft.icons.unavailable, { draft = draft.copy(icons = draft.icons.copy(unavailable = it)) })
                IconFieldRow("テーマ", draft.icons.theme, { draft = draft.copy(icons = draft.icons.copy(theme = it)) }, "設定", draft.icons.settings, { draft = draft.copy(icons = draft.icons.copy(settings = it)) })
            }
            item { SectionTitle("文字サイズ・形・余白・影") }
            item {
                ValueSlider("文字倍率", formatTwo(draft.fontScale), draft.fontScale, .85f..1.30f, 8) { draft = draft.copy(fontScale = it) }
                ValueSlider("カード角丸", "${draft.cardCornerDp.roundToInt()}dp", draft.cardCornerDp, 0f..40f, 39) { draft = draft.copy(cardCornerDp = it) }
                ValueSlider("余白倍率", formatTwo(draft.spacingScale), draft.spacingScale, .80f..1.25f, 8) { draft = draft.copy(spacingScale = it) }
                ValueSlider("カードの影", "${draft.cardElevationDp.roundToInt()}dp", draft.cardElevationDp, 0f..12f, 11) { draft = draft.copy(cardElevationDp = it) }
            }
            item { SectionTitle("動き") }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MotionStrength.values().forEach { value ->
                        val label = when (value) { MotionStrength.NONE -> "なし"; MotionStrength.GENTLE -> "控えめ"; MotionStrength.STANDARD -> "標準"; MotionStrength.STRONG -> "強め" }
                        if (draft.motionStrength == value) Button(onClick = { draft = draft.copy(motionStrength = value) }, Modifier.weight(1f)) { Text(label) }
                        else OutlinedButton(onClick = { draft = draft.copy(motionStrength = value) }, Modifier.weight(1f)) { Text(label) }
                    }
                }
                ValueSlider("時間倍率", formatTwo(draft.animationScale), draft.animationScale, 0f..2f, 19) { draft = draft.copy(animationScale = it) }
                ValueSlider("カード追従量", formatTwo(draft.cardFollow), draft.cardFollow, .8f..1f, 9) { draft = draft.copy(cardFollow = it) }
                ValueSlider("ガイド出現量", formatTwo(draft.guideReveal), draft.guideReveal, .5f..1f, 9) { draft = draft.copy(guideReveal = it) }
            }
            item {
                SectionTitle("詳細JSON")
                OutlinedButton(onClick = {
                    showJson = !showJson
                    if (showJson) jsonText = codec.encodeToString(draft)
                }, Modifier.fillMaxWidth().height(48.dp)) { Text(if (showJson) "JSONを閉じる" else "JSONで全項目を編集") }
                if (showJson) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(jsonText, { jsonText = it }, Modifier.fillMaxWidth(), minLines = 12, maxLines = 24, label = { Text("テーマJSON") })
                    Button(onClick = {
                        runCatching { codec.decodeFromString<ThemeDefinition>(jsonText) }.onSuccess { draft = it.copy(builtIn = false) }
                    }, Modifier.fillMaxWidth().height(48.dp)) { Text("JSONをプレビューへ反映") }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun LiveThemePreview(theme: ThemeDefinition, colors: ThemeColors) {
    val fontFamily = themeFontFamily(theme.fontFamily)
    Column(
        Modifier.fillMaxWidth().background(parseColor(colors.background), RoundedCornerShape(theme.largeCornerDp.dp)).padding(14.dp),
    ) {
        Text("ライブプレビュー", color = parseColor(colors.textSecondary), style = MaterialTheme.typography.caption, fontFamily = fontFamily)
        Spacer(Modifier.height(8.dp))
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(theme.cardCornerDp.dp),
            elevation = theme.cardElevationDp.dp,
            backgroundColor = parseColor(colors.surface),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("${theme.icons.todo} 見積書の金額を確認する", color = parseColor(colors.textPrimary), fontWeight = FontWeight(theme.headingWeight), fontFamily = fontFamily)
                Text("今日 15:00 ・ 約10分", color = parseColor(colors.textSecondary), fontFamily = fontFamily)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f).background(parseColor(colors.defer), RoundedCornerShape(theme.smallCornerDp.dp)).padding(10.dp)) { Text("${theme.icons.defer} 後で行う", color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), fontFamily = fontFamily) }
                    Box(Modifier.weight(1f).background(parseColor(colors.success), RoundedCornerShape(theme.smallCornerDp.dp)).padding(10.dp)) { Text("${theme.icons.complete} 完了", color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), fontFamily = fontFamily) }
                }
            }
        }
    }
}

@Composable
private fun IconFieldRow(
    leftLabel: String,
    leftValue: String,
    onLeftChange: (String) -> Unit,
    rightLabel: String,
    rightValue: String,
    onRightChange: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconField(leftLabel, leftValue, onLeftChange, Modifier.weight(1f))
        IconField(rightLabel, rightValue, onRightChange, Modifier.weight(1f))
    }
}

@Composable
private fun IconField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(8)) },
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
    )
}

@Composable
private fun ColorField(label: String, value: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(40.dp).height(40.dp).background(parseColor(value), RoundedCornerShape(8.dp)).border(1.dp, MaterialTheme.colors.onBackground.copy(alpha = .25f), RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(10.dp))
        OutlinedTextField(value, onChange, Modifier.weight(1f), label = { Text(label) }, singleLine = true)
    }
}

@Composable
fun DataToolsScreen(controller: AppController) {
    var data by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("export") }
    var showAdvanced by remember { mutableStateOf(!controller.backupFilesAvailable) }
    var inspection by remember { mutableStateOf<BackupInspectionResult?>(null) }
    var inspectionSource by remember { mutableStateOf<String?>(null) }
    var showImportConfirmation by remember { mutableStateOf(false) }
    OverlayScaffold("データ", controller::closeOverlay) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("バックアップ", style = MaterialTheme.typography.h6)
                Text("アカウントや通信を使わず、項目・関係・テーマ・設定をJSONにまとめます。")
            }
            if (controller.backupFilesAvailable) item {
                Button(onClick = controller::exportBackupFile, Modifier.fillMaxWidth().height(52.dp)) {
                    Text("ファイルに保存する")
                }
                OutlinedButton(
                    onClick = {
                        mode = "import"
                        data = ""
                        inspection = null
                        inspectionSource = null
                        showImportConfirmation = false
                        controller.importBackupFile { value ->
                            data = value
                            controller.inspectBackup(value) { result ->
                                if (data == value) {
                                    inspection = result
                                    inspectionSource = value
                                }
                            }
                        }
                    },
                    Modifier.fillMaxWidth().height(52.dp),
                ) { Text("ファイルから読み込む") }
                Text(
                    "保存先・読み込み元は端末のファイル選択画面で選べます。広い範囲の保存権限やアプリ独自の通信は使いません。",
                    style = MaterialTheme.typography.caption,
                    color = parseColor(LocalThemeColors.current.textSecondary),
                )
            } else item {
                Text(
                    "この端末ではファイル選択をまだ利用できないため、下の手動JSON操作を使用してください。",
                    style = MaterialTheme.typography.caption,
                    color = parseColor(LocalThemeColors.current.textSecondary),
                )
            }
            item {
                OutlinedButton(onClick = { showAdvanced = !showAdvanced }, Modifier.fillMaxWidth().height(48.dp)) {
                    Text(if (showAdvanced) "手動のJSON操作を閉じる" else "手動のJSON操作（詳しい方法）")
                }
            }
            if (showAdvanced) item {
                Text(
                    "通常は上のファイル操作を使ってください。JSONをコピーして保存したい場合だけ利用できます。",
                    style = MaterialTheme.typography.caption,
                )
                OutlinedButton(
                    onClick = { controller.exportBackup { data = it; mode = "export" } },
                    Modifier.fillMaxWidth().height(48.dp),
                ) { Text("JSONを表示する") }
                OutlinedButton(
                    onClick = {
                        mode = "import"
                        data = ""
                        inspection = null
                        inspectionSource = null
                        showImportConfirmation = false
                    },
                    Modifier.fillMaxWidth().height(48.dp),
                ) { Text("JSONを貼り付ける") }
                if (mode == "import") {
                    OutlinedTextField(
                        data, {
                            data = it
                            inspection = null
                            inspectionSource = null
                            showImportConfirmation = false
                        }, Modifier.fillMaxWidth(),
                        label = { Text("バックアップJSON") },
                        minLines = 8, maxLines = 20,
                    )
                    Button(
                        onClick = {
                            val source = data
                            inspection = null
                            inspectionSource = null
                            controller.inspectBackup(source) { result ->
                                if (data == source) {
                                    inspection = result
                                    inspectionSource = source
                                }
                            }
                        },
                        enabled = data.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("内容を検査する") }
                } else if (data.isNotBlank()) {
                    OutlinedTextField(
                        data, {}, Modifier.fillMaxWidth(),
                        label = { Text("作成したバックアップJSON") },
                        readOnly = true, minLines = 8, maxLines = 20,
                    )
                }
            }
            if (inspection != null && inspectionSource == data) item {
                when (val result = inspection) {
                    is BackupInspectionResult.Invalid -> {
                        Text(result.message, color = MaterialTheme.colors.error)
                    }
                    is BackupInspectionResult.Ready -> {
                        val preview = result.preview
                        Card(
                            Modifier.fillMaxWidth(),
                            backgroundColor = parseColor(LocalThemeColors.current.surface),
                            elevation = 1.dp,
                        ) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("読み込み内容の確認", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
                                Text("やること・メモなど: ${preview.itemCount}件")
                                Text("前後関係: ${preview.relationCount}件")
                                Text("作成元アプリ: ${preview.backupAppVersion}", style = MaterialTheme.typography.caption)
                                if (preview.rejectedItems > 0) Text("除外する項目: ${preview.rejectedItems}件")
                                if (preview.correctionCount > 0) Text("補正する箇所: ${preview.correctionCount}件")
                                Text("現在のデータ ${preview.currentItemCount}件・前後関係 ${preview.currentRelationCount}件は置き換わります。")
                                Text("この操作はあとから「元に戻す」で取り消せます。", style = MaterialTheme.typography.caption)
                                Button(
                                    onClick = { if (inspectionSource == data) showImportConfirmation = true },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                ) { Text("確認して置き換える") }
                            }
                        }
                    }
                    null -> Unit
                }
            }
            item {
                Spacer(Modifier.height(80.dp))
            }
        }
    }
    if (showImportConfirmation) {
        AlertDialog(
            onDismissRequest = { showImportConfirmation = false },
            title = { Text("データを置き換えますか？") },
            text = { Text("現在のデータをバックアップの内容で置き換えます。内容を確認してから続けてください。") },
            confirmButton = {
                Button(onClick = {
                    showImportConfirmation = false
                    controller.importBackup(data)
                    inspection = null
                    inspectionSource = null
                }) { Text("置き換える") }
            },
            dismissButton = { TextButton(onClick = { showImportConfirmation = false }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clickable { onChecked(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChecked)
    }
}

@Composable
private fun ValueSlider(label: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row { Text(label, Modifier.weight(1f)); Text(valueLabel, fontWeight = FontWeight.Bold) }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun SectionTitle(value: String) {
    Text(value, style = MaterialTheme.typography.h6, modifier = Modifier.padding(top = 8.dp))
}

private fun formatRatio(value: Double): String = ((value * 10).roundToInt() / 10.0).toString()
private fun formatTwo(value: Float): String = ((value * 100).roundToInt() / 100f).toString()

private fun addButtonHeightLabel(offset: Int): String = when {
    offset <= 12 -> "下端に近い"
    offset <= 48 -> "少し上"
    offset <= 96 -> "中央寄り"
    else -> "上寄り"
}
