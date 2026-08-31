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
internal fun SettingSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clickable { onChecked(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChecked)
    }
}

@Composable
internal fun ValueSlider(label: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row { Text(label, Modifier.weight(1f)); Text(valueLabel, fontWeight = FontWeight.Bold) }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
internal fun SectionTitle(value: String) {
    Text(value, style = MaterialTheme.typography.h6, modifier = Modifier.padding(top = 8.dp))
}

internal fun formatRatio(value: Double): String = ((value * 10).roundToInt() / 10.0).toString()
internal fun formatTwo(value: Float): String = ((value * 100).roundToInt() / 100f).toString()

internal fun addButtonHeightLabel(offset: Int): String = when {
    offset <= 12 -> "下端に近い"
    offset <= 48 -> "少し上"
    offset <= 96 -> "中央寄り"
    else -> "上寄り"
}
