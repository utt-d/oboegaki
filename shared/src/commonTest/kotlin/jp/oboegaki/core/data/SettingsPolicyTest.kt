package jp.oboegaki.core.data

import jp.oboegaki.core.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsPolicyTest {
    @Test
    fun normalizeSettingsClampsStorageBoundValues() {
        val normalized = normalizeSettings(
            AppSettings(
                splitThreshold = 99,
                deferItems = 0,
                undoSeconds = 99,
            ),
        )

        assertEquals(10, normalized.splitThreshold)
        assertEquals(1, normalized.deferItems)
        assertEquals(10, normalized.undoSeconds)
    }
}
