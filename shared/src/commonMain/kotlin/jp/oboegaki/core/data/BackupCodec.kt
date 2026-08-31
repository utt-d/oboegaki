package jp.oboegaki.core.data

import jp.oboegaki.core.domain.GroupPolicy
import jp.oboegaki.core.domain.RecurrencePolicy
import jp.oboegaki.core.domain.RecurrenceValidation
import jp.oboegaki.core.domain.ThemePolicy
import jp.oboegaki.core.domain.ThemeValidation
import jp.oboegaki.core.model.AllSections
import jp.oboegaki.core.model.AppItem
import jp.oboegaki.core.model.AppSettings
import jp.oboegaki.core.model.ItemRelation
import jp.oboegaki.core.model.ThemeDefinition
import jp.oboegaki.core.model.TodoDetail
import jp.oboegaki.platform.BACKUP_MAX_BYTES
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed interface BackupInspectionResult {
    data class Ready(val preview: BackupInspection) : BackupInspectionResult
    data class Invalid(val message: String) : BackupInspectionResult
}
data class BackupInspection(
    val itemCount: Int,
    val relationCount: Int,
    val rejectedItems: Int,
    val duplicateItemIds: Int,
    val duplicateRelationIds: Int,
    val correctedGroupReferences: Int,
    val correctedParentReferences: Int,
    val correctedConversionReferences: Int,
    val correctedRelations: Int,
    val backupAppVersion: String,
    val currentItemCount: Int,
    val currentRelationCount: Int,
) {
    val correctionCount: Int
        get() = duplicateItemIds + duplicateRelationIds + correctedGroupReferences +
            correctedParentReferences + correctedConversionReferences + correctedRelations
}

data class BackupImportResult(
    val importedItems: Int,
    val rejectedItems: Int,
    val message: String,
    val correctedRelations: Int = 0,
    val successful: Boolean = false,
    val duplicateItemIds: Int = 0,
    val duplicateRelationIds: Int = 0,
    val correctedGroupReferences: Int = 0,
    val correctedParentReferences: Int = 0,
    val correctedConversionReferences: Int = 0,
)

@Serializable
internal data class DataSnapshot(
    val items: List<AppItem>,
    val relations: List<ItemRelation>,
    // Nullable defaults preserve themes/settings when decoding an operation
    // snapshot written before those fields existed.
    val customThemes: List<ThemeDefinition>? = null,
    val settings: AppSettings? = null,
)

@Serializable
internal data class BackupManifest(
    val schemaVersion: Int = 4,
    val appVersion: String = "unknown",
    val createdAtEpochMillis: Long,
)

@Serializable
internal data class BackupEnvelope(
    val manifest: BackupManifest,
    val items: List<AppItem>,
    val relations: List<ItemRelation>,
    val themes: List<ThemeDefinition> = emptyList(),
    val settings: AppSettings = AppSettings(),
)

internal data class PreparedBackup(
    val envelope: BackupEnvelope,
    val validItems: List<AppItem>,
    val relations: List<ItemRelation>,
    val safeThemes: List<ThemeDefinition>,
    val safeSettings: AppSettings,
    val rejectedItems: Int,
    val normalized: BackupNormalizationResult,
)

internal sealed interface BackupPreflight {
    data class Ready(val value: PreparedBackup) : BackupPreflight
    data class Invalid(val message: String) : BackupPreflight
}

/** Creates a portable manifest for exports and keeps older tests/source callers stable. */
internal fun createBackupManifest(appVersion: String, createdAtEpochMillis: Long): BackupManifest =
    BackupManifest(
        appVersion = appVersion.trim().ifBlank { "unknown" },
        createdAtEpochMillis = createdAtEpochMillis,
    )

/**
 * Side-effect-free codec and preflight for the portable backup format.
 *
 * Database replacement and reminder reconciliation remain in RoomItemRepository;
 * this component owns only JSON framing, schema checks and deterministic cleanup.
 */
internal class BackupCodec(
    private val json: Json,
    private val appVersion: String,
) {
    fun encode(snapshot: DataSnapshot, themes: List<ThemeDefinition>, settings: AppSettings, createdAt: Long): String =
        json.encodeToString(
            BackupEnvelope(
                createBackupManifest(appVersion, createdAt),
                snapshot.items,
                snapshot.relations,
                themes,
                settings,
            ),
        )

    fun prepare(value: String): BackupPreflight {
        if (value.encodeToByteArray().size > BACKUP_MAX_BYTES) {
            return BackupPreflight.Invalid("50MBを超えるバックアップは読み込めません")
        }
        val backup = runCatching { json.decodeFromString<BackupEnvelope>(value) }.getOrElse {
            return BackupPreflight.Invalid("バックアップの形式を確認できません")
        }
        if (backup.manifest.schemaVersion !in 1..4) {
            return BackupPreflight.Invalid("未対応のバックアップ形式です")
        }
        val basicValid = backup.items.filter(::isBackupItemImportable)
        val recurrenceSafe = basicValid.map { item ->
            val recurrence = RecurrencePolicy.validate(item)
            RecurrencePolicy.normalize(item.copy(
                todo = item.todo?.copy(
                    recurrence = if (recurrence is RecurrenceValidation.Invalid) null else item.todo.recurrence,
                    // Backups before 0.3.0 stored the implicit global value
                    // as a per-item override. Restore inheritance on import.
                    deferValue = item.todo.deferValue?.takeUnless { it == LEGACY_IMPLICIT_DEFER_ITEMS },
                ),
            ))
        }
        val normalized = normalizeBackupData(recurrenceSafe, backup.relations)
        return BackupPreflight.Ready(
            PreparedBackup(
                envelope = backup,
                validItems = normalized.items,
                relations = normalized.relations,
                safeThemes = backup.themes.filter { ThemePolicy.validate(it) is ThemeValidation.Valid },
                safeSettings = normalizeSettings(backup.settings),
                rejectedItems = backup.items.size - basicValid.size,
                normalized = normalized,
            ),
        )
    }

    private companion object {
        const val LEGACY_IMPLICIT_DEFER_ITEMS = 3
    }
}
