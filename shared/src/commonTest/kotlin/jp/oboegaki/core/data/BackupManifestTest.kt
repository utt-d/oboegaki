package jp.oboegaki.core.data

import kotlin.test.Test
import kotlin.test.assertEquals

class BackupManifestTest {
    @Test
    fun manifestUsesInjectedVersionAndSafeFallback() {
        assertEquals("0.3.4", createBackupManifest("0.3.4", 123).appVersion)
        assertEquals("unknown", createBackupManifest("  ", 123).appVersion)
        assertEquals(123, createBackupManifest("0.3.4", 123).createdAtEpochMillis)
    }
}
