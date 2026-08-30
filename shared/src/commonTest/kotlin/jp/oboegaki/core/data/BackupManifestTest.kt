package jp.oboegaki.core.data

import kotlin.test.Test
import kotlin.test.assertEquals

class BackupManifestTest {
    @Test
    fun manifestUsesInjectedVersionAndSafeFallback() {
        assertEquals("0.3.3", createBackupManifest("0.3.3", 123).appVersion)
        assertEquals("unknown", createBackupManifest("  ", 123).appVersion)
        assertEquals(123, createBackupManifest("0.3.3", 123).createdAtEpochMillis)
    }
}
