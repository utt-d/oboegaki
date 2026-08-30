package jp.oboegaki.core.data

import kotlin.test.Test
import kotlin.test.assertEquals

class BackupInspectionTest {
    @Test
    fun previewKeepsCountsAndReplacementCountsTyped() {
        val preview = BackupInspection(
            itemCount = 4,
            relationCount = 2,
            rejectedItems = 1,
            duplicateItemIds = 1,
            duplicateRelationIds = 0,
            correctedGroupReferences = 1,
            correctedParentReferences = 0,
            correctedConversionReferences = 0,
            correctedRelations = 1,
            backupAppVersion = "0.3.4",
            currentItemCount = 3,
            currentRelationCount = 1,
        )

        assertEquals(4, preview.itemCount)
        assertEquals(2, preview.relationCount)
        assertEquals(1, preview.rejectedItems)
        assertEquals(3, preview.currentItemCount)
        assertEquals(1, preview.currentRelationCount)
        assertEquals("0.3.4", preview.backupAppVersion)
        assertEquals(3, preview.correctionCount)
    }
}
