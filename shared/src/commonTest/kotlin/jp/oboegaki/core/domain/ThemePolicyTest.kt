package jp.oboegaki.core.domain

import jp.oboegaki.core.data.BuiltInThemes
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
}

