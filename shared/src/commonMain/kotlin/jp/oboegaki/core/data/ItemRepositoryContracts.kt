package jp.oboegaki.core.data

import jp.oboegaki.core.domain.DeferConfiguration
import jp.oboegaki.core.domain.DeferDecision
import jp.oboegaki.core.domain.MoveDecision
import jp.oboegaki.core.domain.SplitValidation
import jp.oboegaki.core.domain.ThemeValidation
import jp.oboegaki.core.model.AllSections
import jp.oboegaki.core.model.AppItem
import jp.oboegaki.core.model.AppSettings
import jp.oboegaki.core.model.ItemKind
import jp.oboegaki.core.model.ItemRelation
import jp.oboegaki.core.model.NotificationAction
import jp.oboegaki.core.model.NotificationActionResult
import jp.oboegaki.core.model.NotificationUndoResult
import jp.oboegaki.core.model.NotificationUndoToken
import jp.oboegaki.core.model.ThemeDefinition
import jp.oboegaki.core.model.TodoDetail
import kotlinx.coroutines.flow.Flow

/** Read-only item and relationship streams used by the application state. */
interface ItemRepositoryQueries {
    fun observeActiveTodos(): Flow<List<AppItem>>
    fun observeActiveMemos(): Flow<List<AppItem>>
    fun observeAllSections(): Flow<AllSections>
    fun observeRelations(): Flow<List<ItemRelation>>
    suspend fun getItem(id: String): AppItem?
}

/** Mutations that change item content, ordering and lifecycle. */
interface ItemRepositoryCommands {
    suspend fun quickAdd(kind: ItemKind, text: String, groupId: String? = null): AppItem?
    suspend fun addDetailed(
        kind: ItemKind,
        title: String,
        body: String,
        groupId: String?,
        detail: TodoDetail?,
        requiredBeforeIds: Set<String> = emptySet(),
    ): AppItem?
    suspend fun createGroup(
        kind: ItemKind,
        title: String,
        groupId: String?,
        detail: TodoDetail?,
        requiredBeforeIds: Set<String> = emptySet(),
    ): AppItem?
    suspend fun save(item: AppItem, requiredBeforeIds: Set<String>? = null): AppItem
    suspend fun delete(id: String)
    suspend fun complete(id: String)
    suspend fun defer(id: String, configuration: DeferConfiguration): DeferDecision?
    suspend fun defer(id: String, threshold: Int): DeferDecision? = defer(
        id,
        DeferConfiguration(defaultItems = threshold, splitThreshold = threshold, splitSuggestionEnabled = true),
    )
    suspend fun convertMemo(id: String): AppItem?
    suspend fun archiveMemo(id: String)
    suspend fun restore(id: String)
    suspend fun validateMove(id: String, destinationIndex: Int): MoveDecision
    suspend fun move(id: String, destinationIndex: Int): MoveDecision
    suspend fun moveFree(id: String, destinationIndex: Int)
    suspend fun moveWithinGroup(id: String, direction: Int): MoveDecision
    suspend fun split(id: String, titles: List<String>): SplitValidation
    suspend fun postponeSplitPrompt(id: String, threshold: Int)
    suspend fun disableSplitPrompt(id: String)
    suspend fun undo(): Boolean
}

/** Actions invoked from a task notification and their short-lived undo tokens. */
interface NotificationActionRepository {
    suspend fun performNotificationAction(
        action: NotificationAction,
        itemId: String,
        expectedRevision: Long? = null,
    ): NotificationActionResult
    suspend fun undoNotification(token: NotificationUndoToken): NotificationUndoResult
}

/** Platform-facing reminder reconciliation kept separate from item commands. */
interface ReminderRepository {
    suspend fun rescheduleAllReminders()
}

/** Application settings persistence. */
interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>
    suspend fun getSettings(): AppSettings
    suspend fun saveSettings(settings: AppSettings)
}

/** User-authored theme persistence. */
interface ThemeRepository {
    fun observeThemes(): Flow<List<ThemeDefinition>>
    suspend fun saveTheme(theme: ThemeDefinition): ThemeValidation
    suspend fun deleteTheme(id: String)
}

/** Portable offline backup operations. */
interface BackupRepository {
    suspend fun exportBackupJson(): String
    suspend fun inspectBackupJson(value: String): BackupInspectionResult
    suspend fun importBackupJson(value: String): BackupImportResult
}

/**
 * Stable source-compatible facade for callers.
 *
 * Responsibility-specific contracts above keep implementations and tests focused
 * while existing UI code continues to depend on ItemRepository.
 */
interface ItemRepository :
    ItemRepositoryQueries,
    ItemRepositoryCommands,
    NotificationActionRepository,
    ReminderRepository,
    SettingsRepository,
    ThemeRepository,
    BackupRepository
