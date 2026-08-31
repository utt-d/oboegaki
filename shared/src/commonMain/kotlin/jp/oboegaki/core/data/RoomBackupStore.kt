package jp.oboegaki.core.data

import jp.oboegaki.core.model.ItemRelation
import jp.oboegaki.core.model.ThemeDefinition
import kotlinx.coroutines.sync.withLock

/** JSON backup framing and database replacement, serialized with item writes. */
internal class RoomBackupStore(private val runtime: RoomRepositoryRuntime) {
    suspend fun exportJson(): String = runtime.mutationMutex.withLock {
        val state = runtime.currentState()
        val themes = runtime.dao.getThemes().mapNotNull {
            runCatching { runtime.json.decodeFromString<ThemeDefinition>(it.json) }.getOrNull()
        }
        val settings = runtime.readSettings()
        runtime.backupCodec.encode(state, themes, settings, runtime.now())
    }

    suspend fun inspectJson(value: String): BackupInspectionResult = runtime.mutationMutex.withLock {
        when (val result = runtime.backupCodec.prepare(value)) {
            is BackupPreflight.Invalid -> BackupInspectionResult.Invalid(result.message)
            is BackupPreflight.Ready -> {
                val current = runtime.currentState()
                val prepared = result.value
                BackupInspectionResult.Ready(
                    BackupInspection(
                        itemCount = prepared.validItems.size,
                        relationCount = prepared.relations.size,
                        rejectedItems = prepared.rejectedItems,
                        duplicateItemIds = prepared.normalized.duplicateItemIds,
                        duplicateRelationIds = prepared.normalized.duplicateRelationIds,
                        correctedGroupReferences = prepared.normalized.correctedGroupReferences,
                        correctedParentReferences = prepared.normalized.correctedParentReferences,
                        correctedConversionReferences = prepared.normalized.correctedConversionReferences,
                        correctedRelations = prepared.normalized.correctedRelations,
                        backupAppVersion = prepared.envelope.manifest.appVersion,
                        currentItemCount = current.items.size,
                        currentRelationCount = current.relations.size,
                    ),
                )
            }
        }
    }

    suspend fun importJson(value: String): BackupImportResult = runtime.mutationMutex.withLock {
        val prepared = when (val result = runtime.backupCodec.prepare(value)) {
            is BackupPreflight.Invalid -> return@withLock BackupImportResult(0, 0, result.message)
            is BackupPreflight.Ready -> result.value
        }
        val before = runtime.currentState()
        val time = runtime.now()
        runtime.database.inTransaction {
            runtime.dao.upsertOperation(
                OperationEntity(
                    runtime.newId(), "IMPORT", time, time + 10_000,
                    runtime.json.encodeToString(before), null,
                ),
            )
            runtime.dao.clearRelations()
            runtime.dao.clearTodoDetails()
            runtime.dao.clearItems()
            prepared.validItems.forEach { runtime.upsert(it) }
            runtime.dao.upsertRelations(prepared.relations.map(ItemRelation::toEntity))
            runtime.dao.clearCustomThemes()
            prepared.safeThemes.forEach { theme ->
                val custom = theme.copy(builtIn = false)
                runtime.dao.upsertTheme(
                    ThemeEntity(custom.id, custom.name, false, runtime.json.encodeToString(custom), time),
                )
            }
            runtime.dao.upsertSetting(
                SettingEntity(ROOM_SETTINGS_KEY, runtime.json.encodeToString(prepared.safeSettings)),
            )
        }
        runtime.reconcileReminders(before.items, prepared.validItems)
        runtime.reminderScheduler.applySettings(prepared.safeSettings)
        val correctionParts = buildList {
            val normalized = prepared.normalized
            if (normalized.duplicateItemIds > 0) add("重複したID ${normalized.duplicateItemIds}件")
            if (normalized.duplicateRelationIds > 0) add("重複した前後関係ID ${normalized.duplicateRelationIds}件")
            if (normalized.correctedGroupReferences > 0) add("グループ参照 ${normalized.correctedGroupReferences}件")
            if (normalized.correctedParentReferences > 0) add("親参照 ${normalized.correctedParentReferences}件")
            if (normalized.correctedConversionReferences > 0) add("変換元参照 ${normalized.correctedConversionReferences}件")
            if (normalized.correctedRelations > 0) add("前後関係 ${normalized.correctedRelations}件")
        }
        val correctionMessage = correctionParts.takeIf { it.isNotEmpty() }
            ?.joinToString("、", prefix = "（補正: ", postfix = "）") ?: ""
        BackupImportResult(
            importedItems = prepared.validItems.size,
            rejectedItems = prepared.rejectedItems,
            message = "${prepared.validItems.size}件を読み込みました$correctionMessage",
            correctedRelations = prepared.normalized.correctedRelations,
            successful = true,
            duplicateItemIds = prepared.normalized.duplicateItemIds,
            duplicateRelationIds = prepared.normalized.duplicateRelationIds,
            correctedGroupReferences = prepared.normalized.correctedGroupReferences,
            correctedParentReferences = prepared.normalized.correctedParentReferences,
            correctedConversionReferences = prepared.normalized.correctedConversionReferences,
        )
    }
}
