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
import kotlin.math.roundToInt
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ThemeIcon(theme.icons.todo, ThemeIcons().todo, AppIcons.todo, "やること", tint = parseColor(colors.textPrimary))
                        ThemeIcon(theme.icons.memo, ThemeIcons().memo, AppIcons.memo, "メモ", tint = parseColor(colors.textPrimary))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            theme.name,
                            color = parseColor(colors.textPrimary),
                            fontWeight = FontWeight.Bold,
                            fontFamily = themeFontFamily(theme.fontFamily),
                        )
                    }
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
                if (selected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("適用中", color = parseColor(colors.accent))
                        AppIcon(AppIcons.complete, "適用中", tint = parseColor(colors.accent))
                    }
                } else {
                    Text("選ぶ", color = parseColor(colors.accent))
                }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ThemeIcon(theme.icons.todo, ThemeIcons().todo, AppIcons.todo, "やること", tint = parseColor(colors.textPrimary))
                    Spacer(Modifier.width(4.dp))
                    Text("見積書の金額を確認する", color = parseColor(colors.textPrimary), fontWeight = FontWeight(theme.headingWeight), fontFamily = fontFamily)
                }
                Text("今日 15:00 ・ 約10分", color = parseColor(colors.textSecondary), fontFamily = fontFamily)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f).background(parseColor(colors.defer), RoundedCornerShape(theme.smallCornerDp.dp)).padding(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            ThemeIcon(theme.icons.defer, ThemeIcons().defer, AppIcons.defer, "後で行う", tint = Color.White)
                            Text("後で行う", color = Color.White, fontFamily = fontFamily)
                        }
                    }
                    Box(Modifier.weight(1f).background(parseColor(colors.success), RoundedCornerShape(theme.smallCornerDp.dp)).padding(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            ThemeIcon(theme.icons.complete, ThemeIcons().complete, AppIcons.complete, "完了", tint = Color.White)
                            Text("完了", color = Color.White, fontFamily = fontFamily)
                        }
                    }
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
