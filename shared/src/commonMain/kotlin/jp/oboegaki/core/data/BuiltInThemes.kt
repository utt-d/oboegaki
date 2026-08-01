package jp.oboegaki.core.data

import jp.oboegaki.core.model.MotionStrength
import jp.oboegaki.core.model.ThemeColors
import jp.oboegaki.core.model.ThemeDefinition

object BuiltInThemes {
    private val standardLight = colors(
        background = "#F7F5EF", surface = "#FFFFFF", surfaceAlt = "#ECE8DE",
        text = "#24231F", secondary = "#68655C", border = "#D8D2C5", accent = "#405D57",
        onAccent = "#FFFFFF", todo = "#315E8A", memo = "#76578B", success = "#2F7256",
        defer = "#B06C2E", archive = "#6D6B65", convert = "#315E8A", danger = "#B23A3A",
        warning = "#A45E13", disabled = "#AAA69E", guide = "#89857B", focus = "#1769AA",
    )
    private val standardDark = colors(
        background = "#171916", surface = "#222520", surfaceAlt = "#2E332C",
        text = "#F4F2EA", secondary = "#C2BFB5", border = "#44483F", accent = "#9BC8BC",
        onAccent = "#10201C", todo = "#8CC2F1", memo = "#D0AAE4", success = "#82CBAA",
        defer = "#E2A063", archive = "#BDBAB2", convert = "#8CC2F1", danger = "#F09A9A",
        warning = "#F2BD72", disabled = "#777B73", guide = "#999E95", focus = "#8FC7FF",
    )

    val standard = ThemeDefinition("1".toInt(), "standard", "標準", true, standardLight, standardDark)
    val paper = standard.copy(
        id = "paper", name = "紙", builtIn = true,
        light = standardLight.copy(background = "#F2E9D8", surface = "#FFFDF6", surfaceAlt = "#E8D9BF", accent = "#7A5138"),
        dark = standardDark.copy(background = "#201B17", surface = "#2C251F", accent = "#D9B38C"),
        fontFamily = "Noto Serif JP", cardCornerDp = 10f, cardElevationDp = 1f,
    )
    val highContrast = standard.copy(
        id = "high-contrast", name = "高コントラスト", builtIn = true,
        light = standardLight.copy(background = "#FFFFFF", surface = "#FFFFFF", textPrimary = "#000000", textSecondary = "#1A1A1A", border = "#000000", accent = "#0037FF", onAccent = "#FFFFFF"),
        dark = standardDark.copy(background = "#000000", surface = "#000000", textPrimary = "#FFFFFF", textSecondary = "#F0F0F0", border = "#FFFFFF", accent = "#FFDA00", onAccent = "#000000"),
        borderWidthDp = 2f, cardElevationDp = 0f, motionStrength = MotionStrength.GENTLE,
    )
    val oled = standard.copy(
        id = "oled", name = "OLED", builtIn = true,
        dark = standardDark.copy(background = "#000000", surface = "#090909", surfaceAlt = "#151515"),
        cardElevationDp = 0f,
    )
    val all = listOf(standard, paper, highContrast, oled)

    private fun colors(
        background: String, surface: String, surfaceAlt: String, text: String, secondary: String,
        border: String, accent: String, onAccent: String, todo: String, memo: String,
        success: String, defer: String, archive: String, convert: String, danger: String,
        warning: String, disabled: String, guide: String, focus: String,
    ) = ThemeColors(
        background, surface, surfaceAlt, text, secondary, border, accent, onAccent, todo, memo,
        success, defer, archive, convert, danger, warning, disabled, guide, focus,
    )
}

