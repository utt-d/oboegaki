package jp.oboegaki.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.oboegaki.core.domain.ThemePolicy
import jp.oboegaki.core.model.AppearanceMode
import jp.oboegaki.core.model.AddButtonPosition
import jp.oboegaki.core.model.AppSettings
import jp.oboegaki.core.model.MotionStrength
import jp.oboegaki.core.model.ThemeColors
import jp.oboegaki.core.model.ThemeDefinition
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(settings: AppSettings, controller: AppController) {
    var draft by remember(settings) { mutableStateOf(settings) }
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
            item {
                Text("追加ボタンの位置", style = MaterialTheme.typography.subtitle1)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AddButtonPosition.values().forEach { position ->
                        val label = if (position == AddButtonPosition.LEFT) "左下" else "右下"
                        if (draft.addButtonPosition == position) {
                            Button(
                                onClick = { draft = draft.copy(addButtonPosition = position) },
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) { Text(label) }
                        } else {
                            OutlinedButton(
                                onClick = { draft = draft.copy(addButtonPosition = position) },
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) { Text(label) }
                        }
                    }
                }
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
fun OperationGuideScreen(firstLaunch: Boolean, controller: AppController) {
    val icons = LocalAppTheme.current.icons
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
                            "画面左下の追加ボタンを押します。",
                            "追加ボタンは、設定から右下へ移動できます。",
                            "ハンドルを上へ引くと詳細が開き、下へ引くと閉じます。",
                        ),
                    )
                }
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
                            "色・フォント・アイコン・動き・触覚などを変更できます。",
                            "このガイドも設定から見返せます。",
                        ),
                    )
                    Spacer(Modifier.height(20.dp))
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
    OverlayScaffold("データ", controller::closeOverlay) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("バックアップ", style = MaterialTheme.typography.h6)
                Text("アカウントや通信を使わず、項目・関係・テーマ・設定をJSONにまとめます。")
            }
            item {
                Button(onClick = { controller.exportBackup { data = it; mode = "export" } }, Modifier.fillMaxWidth().height(52.dp)) { Text("バックアップを書き出す") }
                OutlinedButton(onClick = { mode = "import"; data = "" }, Modifier.fillMaxWidth().height(48.dp)) { Text("バックアップを読み込む") }
            }
            item {
                OutlinedTextField(
                    data, { if (mode == "import") data = it }, Modifier.fillMaxWidth(),
                    label = { Text(if (mode == "import") "バックアップJSONを貼り付け" else "作成したバックアップJSON") },
                    readOnly = mode != "import", minLines = 12, maxLines = 26,
                )
                if (mode == "import") {
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { controller.importBackup(data) }, enabled = data.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("内容を確認して読み込む")
                    }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
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
