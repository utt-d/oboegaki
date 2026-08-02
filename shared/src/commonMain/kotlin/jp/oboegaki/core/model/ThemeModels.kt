package jp.oboegaki.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AppearanceMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class MotionStrength { NONE, GENTLE, STANDARD, STRONG }

@Serializable
enum class AddButtonPosition { LEFT, CENTER, RIGHT }

@Serializable
enum class MainNavigationButton { TODOS, MEMOS, ALL }

@Serializable
enum class TopActionButton { THEMES, SETTINGS }

@Serializable
data class ThemeColors(
    val background: String,
    val surface: String,
    val surfaceAlt: String,
    val textPrimary: String,
    val textSecondary: String,
    val border: String,
    val accent: String,
    val onAccent: String,
    val todo: String,
    val memo: String,
    val success: String,
    val defer: String,
    val archive: String,
    val convert: String,
    val danger: String,
    val warning: String,
    val disabled: String,
    val unavailableGuide: String,
    val focusRing: String,
)

@Serializable
data class ThemeIcons(
    val todo: String = "✓",
    val memo: String = "✎",
    val all: String = "☰",
    val add: String = "＋",
    val edit: String = "✎",
    val complete: String = "✓",
    val defer: String = "↶",
    val convert: String = "→",
    val archive: String = "□",
    val next: String = "↑",
    val previous: String = "↓",
    val unavailable: String = "⊘",
    val theme: String = "◉",
    val settings: String = "⚙",
)

@Serializable
data class ThemeDefinition(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val builtIn: Boolean = false,
    val light: ThemeColors,
    val dark: ThemeColors,
    val fontFamily: String = "System",
    val icons: ThemeIcons = ThemeIcons(),
    val fontScale: Float = 1f,
    val headingWeight: Int = 600,
    val bodyWeight: Int = 400,
    val tabularNumbers: Boolean = true,
    val smallCornerDp: Float = 8f,
    val mediumCornerDp: Float = 16f,
    val largeCornerDp: Float = 28f,
    val cardCornerDp: Float = 24f,
    val borderWidthDp: Float = 1f,
    val spacingScale: Float = 1f,
    val cardElevationDp: Float = 3f,
    val shadowAlpha: Float = .12f,
    val motionStrength: MotionStrength = MotionStrength.STANDARD,
    val animationScale: Float = 1f,
    val cardFollow: Float = 1f,
    val guideReveal: Float = .85f,
)

@Serializable
data class AppSettings(
    val splitSuggestionEnabled: Boolean = true,
    val splitThreshold: Int = 3,
    val deferItems: Int = 3,
    val hapticsEnabled: Boolean = true,
    val undoSeconds: Int = 5,
    val addWithEnter: Boolean = false,
    val addButtonPosition: AddButtonPosition = AddButtonPosition.LEFT,
    val addButtonBottomOffsetDp: Int = 8,
    val navigationButtonOrder: List<MainNavigationButton> = listOf(
        MainNavigationButton.TODOS,
        MainNavigationButton.MEMOS,
        MainNavigationButton.ALL,
    ),
    val topActionButtonOrder: List<TopActionButton> = listOf(
        TopActionButton.THEMES,
        TopActionButton.SETTINGS,
    ),
    val tabSwipeEnabled: Boolean = true,
    val calendarIntegrationEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
    val appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    val selectedThemeId: String = "standard",
    val operationGuideSeen: Boolean = false,
)
