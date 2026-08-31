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
import kotlin.math.abs
import kotlin.math.roundToInt
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
                        icon = { ThemeIcon(icons.add, ThemeIcons().add, AppIcons.add, "追加") },
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
                            icon = {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    ThemeIcon(icons.memo, ThemeIcons().memo, AppIcons.memo, "メモ")
                                    ThemeIcon(icons.defer, ThemeIcons().defer, AppIcons.defer, "後で行う")
                                    ThemeIcon(icons.complete, ThemeIcons().complete, AppIcons.complete, "完了")
                                }
                            },
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
                            icon = {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    ThemeIcon(icons.previous, ThemeIcons().previous, AppIcons.previous, "前へ")
                                    ThemeIcon(icons.next, ThemeIcons().next, AppIcons.next, "次へ")
                                }
                            },
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
                            icon = {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    ThemeIcon(icons.defer, ThemeIcons().defer, AppIcons.defer, "後で行う")
                                    ThemeIcon(icons.complete, ThemeIcons().complete, AppIcons.complete, "完了")
                                }
                            },
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
                            icon = {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    ThemeIcon(icons.todo, ThemeIcons().todo, AppIcons.todo, "やること")
                                    ThemeIcon(icons.memo, ThemeIcons().memo, AppIcons.memo, "メモ")
                                    ThemeIcon(icons.all, ThemeIcons().all, AppIcons.all, "すべて")
                                }
                            },
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
                            icon = {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    ThemeIcon(icons.theme, ThemeIcons().theme, AppIcons.theme, "テーマ")
                                    ThemeIcon(icons.settings, ThemeIcons().settings, AppIcons.settings, "設定")
                                }
                            },
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
private fun GuideStep(icon: @Composable () -> Unit, title: String, points: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalAppTheme.current.mediumCornerDp.dp),
        elevation = 1.dp,
        backgroundColor = parseColor(LocalThemeColors.current.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) { icon() }
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
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    ThemeIcon(icons.todo, ThemeIcons().todo, AppIcons.todo, "やること")
                    Spacer(Modifier.width(6.dp))
                    Text(cardTitle, fontWeight = FontWeight.Bold)
                }
            }
            when (step) {
                0 -> {
                    Text("上下の移動", style = MaterialTheme.typography.caption)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { completeVertical(false) }, Modifier.weight(1f).height(52.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ThemeIcon(icons.previous, ThemeIcons().previous, AppIcons.previous, "前へ")
                                Text("前へ")
                            }
                        }
                        Button(onClick = { completeVertical(true) }, Modifier.weight(1f).height(52.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ThemeIcon(icons.next, ThemeIcons().next, AppIcons.next, "次へ")
                                Text("次へ")
                            }
                        }
                    }
                }
                1 -> {
                    Text("左右の整理", style = MaterialTheme.typography.caption)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { completeHorizontal(false) }, Modifier.weight(1f).height(52.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ThemeIcon(icons.defer, ThemeIcons().defer, AppIcons.defer, "後で行う")
                                Text("後で行う")
                            }
                        }
                        Button(onClick = { completeHorizontal(true) }, Modifier.weight(1f).height(52.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ThemeIcon(icons.complete, ThemeIcons().complete, AppIcons.complete, "完了")
                                Text("完了")
                            }
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
