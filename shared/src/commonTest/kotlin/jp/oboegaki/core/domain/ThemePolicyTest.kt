package jp.oboegaki.core.domain

import jp.oboegaki.core.data.BuiltInThemes
import jp.oboegaki.core.model.ThemeIcons
import kotlin.test.Test
import kotlin.test.assertIs

class ThemePolicyTest {
    @Test
    fun builtInThemesAreValid() {
        BuiltInThemes.all.forEach { assertIs<ThemeValidation.Valid>(ThemePolicy.validate(it)) }
    }

    @Test
    fun unreadableThemeIsRejected() {
        val broken = BuiltInThemes.standard.copy(
            id = "broken",
            light = BuiltInThemes.standard.light.copy(textPrimary = "#F7F5EF"),
        )
        assertIs<ThemeValidation.Invalid>(ThemePolicy.validate(broken))
    }

    @Test
    fun editableFontAndIconsAreValidated() {
        val customized = BuiltInThemes.standard.copy(
            id = "customized",
            fontFamily = "Monospace",
            icons = ThemeIcons(todo = "◆", memo = "◇", add = "✚"),
        )
        assertIs<ThemeValidation.Valid>(ThemePolicy.validate(customized))
        assertIs<ThemeValidation.Invalid>(ThemePolicy.validate(customized.copy(fontFamily = "Unknown Font")))
        assertIs<ThemeValidation.Invalid>(ThemePolicy.validate(customized.copy(icons = customized.icons.copy(add = ""))))
    }
}
