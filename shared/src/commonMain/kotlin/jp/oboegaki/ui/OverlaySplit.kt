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
internal fun SplitScreen(item: AppItem, controller: AppController) {
    val titles = remember(item.id) { mutableStateListOf("準備する", "最初の部分を行う", "確認して終える") }
    OverlayScaffold("小さく分ける", controller::closeOverlay) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(item.title, style = MaterialTheme.typography.h6)
                Spacer(Modifier.height(8.dp))
                Text("何度か後で行うことにしています。最初の一歩に分けますか")
            }
            itemsIndexed(titles) { index, value ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", Modifier.width(28.dp), textAlign = TextAlign.Center)
                    OutlinedTextField(value, { titles[index] = it.take(200) }, Modifier.weight(1f), label = { Text("分けたやること") })
                    TextButton(onClick = { if (titles.size > 1) titles.removeAt(index) }, modifier = Modifier.height(48.dp)) { Text("削除") }
                }
            }
            item {
                OutlinedButton(onClick = { titles += "" }, Modifier.fillMaxWidth().height(48.dp)) { Text("項目を追加") }
                Spacer(Modifier.height(10.dp))
                Button(onClick = { controller.split(item.id, titles) }, Modifier.fillMaxWidth().height(52.dp)) { Text("分けて置き換える") }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { controller.postponeSplit(item.id) }, Modifier.fillMaxWidth().height(48.dp)) { Text("今回は分けない") }
                TextButton(onClick = { controller.disableSplit(item.id) }, Modifier.fillMaxWidth().height(48.dp)) { Text("このやることでは今後表示しない") }
                Spacer(Modifier.height(60.dp))
            }
        }
    }
}

@Composable
internal fun MissingOverlay(controller: AppController) {
    OverlayScaffold("項目がありません", controller::closeOverlay) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("項目が移動または削除されました") }
    }
}
