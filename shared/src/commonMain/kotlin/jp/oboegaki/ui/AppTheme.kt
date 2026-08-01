package jp.oboegaki.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import jp.oboegaki.core.data.BuiltInThemes
import jp.oboegaki.core.model.AppearanceMode
import jp.oboegaki.core.model.AppSettings
import jp.oboegaki.core.model.ThemeColors
import jp.oboegaki.core.model.ThemeDefinition

val LocalAppTheme = staticCompositionLocalOf { BuiltInThemes.standard }
val LocalThemeColors = staticCompositionLocalOf { BuiltInThemes.standard.light }

@Composable
fun OboegakiTheme(
    theme: ThemeDefinition,
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val dark = when (settings.appearanceMode) {
        AppearanceMode.SYSTEM -> isSystemInDarkTheme()
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }
    val tokens = if (dark) theme.dark else theme.light
    val colors = if (dark) darkColors(
        primary = parseColor(tokens.accent),
        primaryVariant = parseColor(tokens.todo),
        secondary = parseColor(tokens.memo),
        background = parseColor(tokens.background),
        surface = parseColor(tokens.surface),
        error = parseColor(tokens.danger),
        onPrimary = parseColor(tokens.onAccent),
        onSecondary = parseColor(tokens.textPrimary),
        onBackground = parseColor(tokens.textPrimary),
        onSurface = parseColor(tokens.textPrimary),
        onError = parseColor(tokens.surface),
    ) else lightColors(
        primary = parseColor(tokens.accent),
        primaryVariant = parseColor(tokens.todo),
        secondary = parseColor(tokens.memo),
        background = parseColor(tokens.background),
        surface = parseColor(tokens.surface),
        error = parseColor(tokens.danger),
        onPrimary = parseColor(tokens.onAccent),
        onSecondary = parseColor(tokens.textPrimary),
        onBackground = parseColor(tokens.textPrimary),
        onSurface = parseColor(tokens.textPrimary),
        onError = parseColor(tokens.surface),
    )
    val scale = theme.fontScale
    val fontFamily = themeFontFamily(theme.fontFamily)
    val typography = Typography(
        defaultFontFamily = fontFamily,
        h4 = TextStyle(fontSize = 28.sp * scale, fontWeight = FontWeight(theme.headingWeight), fontFamily = fontFamily),
        h5 = TextStyle(fontSize = 23.sp * scale, fontWeight = FontWeight(theme.headingWeight), fontFamily = fontFamily),
        h6 = TextStyle(fontSize = 19.sp * scale, fontWeight = FontWeight(theme.headingWeight), fontFamily = fontFamily),
        subtitle1 = TextStyle(fontSize = 16.sp * scale, fontWeight = FontWeight.Medium, fontFamily = fontFamily),
        body1 = TextStyle(fontSize = 16.sp * scale, fontWeight = FontWeight(theme.bodyWeight), fontFamily = fontFamily),
        body2 = TextStyle(fontSize = 14.sp * scale, fontWeight = FontWeight(theme.bodyWeight), fontFamily = fontFamily),
        button = TextStyle(fontSize = 14.sp * scale, fontWeight = FontWeight.SemiBold, fontFamily = fontFamily),
        caption = TextStyle(fontSize = 12.sp * scale, fontWeight = FontWeight.Normal, fontFamily = fontFamily),
    )
    CompositionLocalProvider(LocalAppTheme provides theme, LocalThemeColors provides tokens) {
        MaterialTheme(colors = colors, typography = typography, content = content)
    }
}

fun themeFontFamily(value: String): FontFamily = when (value) {
    "Sans Serif", "Noto Sans JP" -> FontFamily.SansSerif
    "Serif", "Noto Serif JP" -> FontFamily.Serif
    "Monospace" -> FontFamily.Monospace
    "Cursive" -> FontFamily.Cursive
    else -> FontFamily.Default
}

fun parseColor(value: String): Color {
    val clean = value.removePrefix("#")
    if (clean.length != 6 && clean.length != 8) return Color.Magenta
    val number = clean.toLongOrNull(16) ?: return Color.Magenta
    val alpha = if (clean.length == 8) ((number shr 24) and 0xFF) else 0xFF
    val red = if (clean.length == 8) ((number shr 16) and 0xFF) else ((number shr 16) and 0xFF)
    val green = (number shr 8) and 0xFF
    val blue = number and 0xFF
    return Color(red / 255f, green / 255f, blue / 255f, alpha / 255f)
}

fun ThemeColors.colorForKind(kind: jp.oboegaki.core.model.ItemKind): Color = parseColor(
    when (kind) {
        jp.oboegaki.core.model.ItemKind.TODO -> todo
        jp.oboegaki.core.model.ItemKind.MEMO -> memo
        jp.oboegaki.core.model.ItemKind.UNSORTED -> warning
    },
)
