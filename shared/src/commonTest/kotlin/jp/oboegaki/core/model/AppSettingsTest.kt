package jp.oboegaki.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsTest {
    @Test
    fun addButtonDefaultsToLeft() {
        assertEquals(AddButtonPosition.LEFT, AppSettings().addButtonPosition)
    }
}
