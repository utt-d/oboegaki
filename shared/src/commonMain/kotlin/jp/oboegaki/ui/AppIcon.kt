package jp.oboegaki.ui

import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Icons that are part of the fixed application structure use Material vectors
 * by default. A user-edited ThemeIcons value remains visible as text so the
 * theme editor keeps its promise of allowing arbitrary glyphs and emoji.
 */
@Composable
fun ThemeIcon(
    value: String,
    defaultValue: String,
    imageVector: ImageVector,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    if (value == defaultValue) {
        Icon(imageVector, contentDescription, modifier, tint)
    } else {
        val semanticsModifier = Modifier.clearAndSetSemantics {
            if (contentDescription != null) this.contentDescription = contentDescription
        }
        Box(
            modifier = modifier.size(24.dp).then(semanticsModifier),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material.Text(
                text = value,
                modifier = Modifier.fillMaxWidth(),
                color = tint,
                style = MaterialTheme.typography.body1,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun AppIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(imageVector, contentDescription, modifier, tint)
}

/** App-wide structural icons, kept in one place so the visual language is consistent. */
object AppIcons {
    val add: ImageVector get() = Icons.Filled.Add
    val all: ImageVector get() = Icons.Filled.Menu
    val archive: ImageVector get() = archiveVector
    val complete: ImageVector get() = Icons.Filled.Check
    val convert: ImageVector get() = Icons.AutoMirrored.Filled.ArrowForward
    val defer: ImageVector get() = Icons.Filled.DateRange
    val edit: ImageVector get() = Icons.Filled.Edit
    val memo: ImageVector get() = Icons.Filled.Create
    val next: ImageVector get() = Icons.Filled.KeyboardArrowUp
    val previous: ImageVector get() = Icons.Filled.KeyboardArrowDown
    val todo: ImageVector get() = Icons.Filled.Done
    val unavailable: ImageVector get() = Icons.Filled.Clear
    val theme: ImageVector get() = Icons.Filled.Star
    val settings: ImageVector get() = Icons.Filled.Settings
    val group: ImageVector get() = Icons.AutoMirrored.Filled.List
    val expand: ImageVector get() = Icons.Filled.KeyboardArrowDown
    val collapse: ImageVector get() = Icons.Filled.KeyboardArrowUp
}

/** Material-style archive box, kept local to avoid the large extended-icons dependency. */
private val archiveVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "Archive",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(20.54f, 5.23f)
            lineTo(19.15f, 3.55f)
            curveTo(18.88f, 3.21f, 18.47f, 3f, 18f, 3f)
            horizontalLineTo(6f)
            curveTo(5.53f, 3f, 5.12f, 3.21f, 4.85f, 3.55f)
            lineTo(3.46f, 5.23f)
            curveTo(3.17f, 5.57f, 3f, 6.02f, 3f, 6.5f)
            verticalLineTo(19f)
            curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
            horizontalLineTo(19f)
            curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
            verticalLineTo(6.5f)
            curveTo(21f, 6.02f, 20.83f, 5.57f, 20.54f, 5.23f)
            close()
            moveTo(6.24f, 5f)
            horizontalLineTo(17.76f)
            lineTo(18.57f, 5.97f)
            horizontalLineTo(5.44f)
            close()
            moveTo(5f, 19f)
            verticalLineTo(8f)
            horizontalLineTo(19f)
            verticalLineTo(19f)
            close()
            moveTo(13f, 10f)
            horizontalLineTo(11f)
            verticalLineTo(13f)
            horizontalLineTo(8f)
            lineTo(12f, 17f)
            lineTo(16f, 13f)
            horizontalLineTo(13f)
            close()
        }
    }.build()
}

@Composable
fun ThemedIcon(
    value: String,
    slotDefault: String,
    vector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = LocalContentColor.current,
) = ThemeIcon(value, slotDefault, vector, contentDescription, modifier, tint)
