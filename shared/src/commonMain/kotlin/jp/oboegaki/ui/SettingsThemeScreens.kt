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
import androidx.compose.foundation.layout.size
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
import jp.oboegaki.core.model.ThemeIcons
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
                    AppIcon(
                        if (detailedSettingsExpanded) AppIcons.collapse else AppIcons.expand,
                        if (detailedSettingsExpanded) "詳しい設定を閉じる" else "詳しい設定を開く",
                        modifier = Modifier.size(24.dp),
                    )
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
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ThemeIcon(
                            LocalAppTheme.current.icons.all,
                            ThemeIcons().all,
                            AppIcons.all,
                            "操作ガイド",
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("操作ガイドを見る")
                    }
                }
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
            ) { AppIcon(AppIcons.next, "上へ移動") }
            OutlinedButton(
                onClick = { onOrderChange(moveInList(order, index, index + 1)) },
                enabled = index < order.lastIndex,
                modifier = Modifier.width(56.dp).height(48.dp),
            ) { AppIcon(AppIcons.previous, "下へ移動") }
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
