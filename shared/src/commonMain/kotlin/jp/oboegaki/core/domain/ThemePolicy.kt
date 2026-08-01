package jp.oboegaki.core.domain

import jp.oboegaki.core.model.ThemeColors
import jp.oboegaki.core.model.ThemeDefinition
import jp.oboegaki.core.model.ThemeIcons
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class ContrastWarning(val token: String, val ratio: Double, val required: Double)

sealed interface ThemeValidation {
    data class Valid(val warnings: List<ContrastWarning>) : ThemeValidation
    data class Invalid(val message: String) : ThemeValidation
}

object ThemePolicy {
    private val colorPattern = Regex("^#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

    fun validate(theme: ThemeDefinition): ThemeValidation {
        if (theme.schemaVersion != 1) return ThemeValidation.Invalid("未対応のテーマ形式です")
        if (theme.name.trim().isEmpty()) return ThemeValidation.Invalid("テーマ名を入力してください")
        if (theme.fontFamily !in supportedFontFamilies) {
            return ThemeValidation.Invalid("対応していないフォントです")
        }
        if (allIcons(theme.icons).any { it.isBlank() || it.length > 8 }) {
            return ThemeValidation.Invalid("アイコンは1〜4文字程度で入力してください")
        }
        if (theme.fontScale !in .85f..1.30f || theme.spacingScale !in .80f..1.25f) {
            return ThemeValidation.Invalid("文字または余白の値が許可範囲外です")
        }
        if (theme.cardCornerDp !in 0f..40f || theme.borderWidthDp !in 0f..3f ||
            theme.animationScale !in 0f..2f || theme.cardFollow !in .8f..1f ||
            theme.guideReveal !in .5f..1f
        ) return ThemeValidation.Invalid("形または動きの値が許可範囲外です")

        val colors = listOf(theme.light, theme.dark)
        if (colors.flatMap(::allColors).any { !colorPattern.matches(it) }) {
            return ThemeValidation.Invalid("色は #RRGGBB または #AARRGGBB で入力してください")
        }
        val impossible = colors.any { alpha(it.textPrimary) == 0 || contrast(it.textPrimary, it.background) < 1.1 }
        if (impossible) return ThemeValidation.Invalid("主な文字が読めない配色は保存できません")

        val warnings = colors.flatMap { variant ->
            buildList {
                val primary = contrast(variant.textPrimary, variant.background)
                if (primary < 4.5) add(ContrastWarning("主な文字", primary, 4.5))
                val secondary = contrast(variant.textSecondary, variant.background)
                if (secondary < 4.5) add(ContrastWarning("補助文字", secondary, 4.5))
                val accent = contrast(variant.onAccent, variant.accent)
                if (accent < 3.0) add(ContrastWarning("主操作", accent, 3.0))
            }
        }
        return ThemeValidation.Valid(warnings)
    }

    fun contrast(foreground: String, background: String): Double {
        val a = luminance(foreground)
        val b = luminance(background)
        return (max(a, b) + .05) / (min(a, b) + .05)
    }

    private fun allColors(c: ThemeColors) = listOf(
        c.background, c.surface, c.surfaceAlt, c.textPrimary, c.textSecondary, c.border,
        c.accent, c.onAccent, c.todo, c.memo, c.success, c.defer, c.archive, c.convert,
        c.danger, c.warning, c.disabled, c.unavailableGuide, c.focusRing,
    )

    private fun allIcons(icons: ThemeIcons) = listOf(
        icons.todo, icons.memo, icons.all, icons.add, icons.edit, icons.complete, icons.defer,
        icons.convert, icons.archive, icons.next, icons.previous, icons.unavailable,
        icons.theme, icons.settings,
    )

    val supportedFontFamilies = listOf(
        "System", "Sans Serif", "Serif", "Monospace", "Cursive",
        "Noto Sans JP", "Noto Serif JP",
    )

    private fun alpha(value: String): Int = if (value.length == 9) value.substring(1, 3).toInt(16) else 255

    private fun luminance(value: String): Double {
        val raw = if (value.length == 9) value.substring(3) else value.substring(1)
        fun channel(start: Int): Double {
            val s = raw.substring(start, start + 2).toInt(16) / 255.0
            return if (s <= .03928) s / 12.92 else ((s + .055) / 1.055).pow(2.4)
        }
        return .2126 * channel(0) + .7152 * channel(2) + .0722 * channel(4)
    }
}
