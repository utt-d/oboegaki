package jp.oboegaki.core.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import jp.oboegaki.core.domain.DeferDecision
import jp.oboegaki.core.domain.DeferConfiguration
import jp.oboegaki.core.domain.DeferPolicy
import jp.oboegaki.core.domain.GroupPlacementDecision
import jp.oboegaki.core.domain.GroupPolicy
import jp.oboegaki.core.domain.MoveDecision
import jp.oboegaki.core.domain.OrderingPolicy
import jp.oboegaki.core.domain.PrerequisiteValidation
import jp.oboegaki.core.domain.RecurrencePolicy
import jp.oboegaki.core.domain.RecurrenceValidation
import jp.oboegaki.core.domain.ReminderPolicy
import jp.oboegaki.core.domain.SplitPolicy
import jp.oboegaki.core.domain.SplitValidation
import jp.oboegaki.core.domain.ThemePolicy
import jp.oboegaki.core.domain.ThemeValidation
import jp.oboegaki.core.domain.UndoEligibility
import jp.oboegaki.core.domain.UndoOperation
import jp.oboegaki.core.domain.UndoPolicy
import jp.oboegaki.core.domain.UndoRestoreScope
import jp.oboegaki.core.model.AllSections
import jp.oboegaki.core.model.AppItem
import jp.oboegaki.core.model.AppSettings
import jp.oboegaki.core.model.ItemKind
import jp.oboegaki.core.model.ItemLifecycle
import jp.oboegaki.core.model.ItemRelation
import jp.oboegaki.core.model.RelationType
import jp.oboegaki.core.model.ThemeDefinition
import jp.oboegaki.core.model.TodoDetail
import jp.oboegaki.core.model.NotificationAction
import jp.oboegaki.core.model.NotificationActionResult
import jp.oboegaki.core.model.NotificationUndoResult
import jp.oboegaki.core.model.NotificationUndoToken
import jp.oboegaki.platform.NoOpReminderScheduler
import jp.oboegaki.platform.Reminder
import jp.oboegaki.platform.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.time.Clock

interface ItemRepository {
    fun observeActiveTodos(): Flow<List<AppItem>>
    fun observeActiveMemos(): Flow<List<AppItem>>
    fun observeAllSections(): Flow<AllSections>
    fun observeRelations(): Flow<List<ItemRelation>>
    fun observeThemes(): Flow<List<ThemeDefinition>>
    fun observeSettings(): Flow<AppSettings>
    suspend fun getItem(id: String): AppItem?
    suspend fun getSettings(): AppSettings
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
    suspend fun performNotificationAction(
        action: NotificationAction,
        itemId: String,
        expectedRevision: Long? = null,
    ): NotificationActionResult
    suspend fun undoNotification(token: NotificationUndoToken): NotificationUndoResult
    suspend fun rescheduleAllReminders()
    suspend fun saveSettings(settings: AppSettings)
    suspend fun saveTheme(theme: ThemeDefinition): ThemeValidation
    suspend fun deleteTheme(id: String)
    suspend fun exportBackupJson(): String
    suspend fun importBackupJson(value: String): BackupImportResult
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
private data class DataSnapshot(
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
private data class BackupEnvelope(
    val manifest: BackupManifest,
    val items: List<AppItem>,
    val relations: List<ItemRelation>,
    val themes: List<ThemeDefinition> = emptyList(),
    val settings: AppSettings = AppSettings(),
)

internal fun createBackupManifest(appVersion: String, createdAtEpochMillis: Long): BackupManifest =
    BackupManifest(
        appVersion = appVersion.trim().ifBlank { "unknown" },
        createdAtEpochMillis = createdAtEpochMillis,
    )

class RoomItemRepository(
    private val database: AppDatabase,
    private val reminderScheduler: ReminderScheduler = NoOpReminderScheduler,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true },
    private val appVersion: String = DEFAULT_APP_VERSION,
) : ItemRepository {
    private val dao = database.dao()
    private val mutationMutex = Mutex()

    private val itemModels: Flow<List<AppItem>> = combine(
        dao.observeItems(), dao.observeTodoDetails(),
    ) { items, details ->
        val detailById = details.associateBy { it.itemId }
        items.map { it.toModel(detailById[it.id]) }
    }

    override fun observeRelations(): Flow<List<ItemRelation>> =
        dao.observeRelations().map { values -> values.map(ItemRelationEntity::toModel) }

    override fun observeAllSections(): Flow<AllSections> = combine(
        itemModels, observeRelations(),
    ) { items, relations ->
        val activeTodos = OrderingPolicy.canonicalSort(
            items.filter { it.kind == ItemKind.TODO && it.lifecycle == ItemLifecycle.ACTIVE && !it.isGroup },
            relations,
        )
        AllSections(
            unsorted = items.filter { it.kind == ItemKind.UNSORTED && it.lifecycle == ItemLifecycle.ACTIVE }
                .sortedBy { it.manualRank },
            todos = activeTodos,
            todoGroups = items.filter { it.kind == ItemKind.TODO && it.lifecycle == ItemLifecycle.ACTIVE && it.isGroup }
                .sortedBy { it.manualRank },
            memos = items.filter { it.kind == ItemKind.MEMO && it.lifecycle == ItemLifecycle.ACTIVE }
                .filterNot { it.isGroup }
                .sortedBy { it.manualRank },
            memoGroups = items.filter { it.kind == ItemKind.MEMO && it.lifecycle == ItemLifecycle.ACTIVE && it.isGroup }
                .sortedBy { it.manualRank },
            completed = items.filter { it.kind == ItemKind.TODO && it.lifecycle == ItemLifecycle.COMPLETED }
                .sortedByDescending { it.completedAtEpochMillis },
            archived = items.filter { it.kind == ItemKind.MEMO && it.lifecycle == ItemLifecycle.ARCHIVED }
                .sortedByDescending { it.archivedAtEpochMillis },
        )
    }

    override fun observeActiveTodos(): Flow<List<AppItem>> = observeAllSections().map { it.todos }
    override fun observeActiveMemos(): Flow<List<AppItem>> = observeAllSections().map { it.memos }

    override fun observeThemes(): Flow<List<ThemeDefinition>> = dao.observeThemes().map { rows ->
        BuiltInThemes.all + rows.mapNotNull { runCatching { json.decodeFromString<ThemeDefinition>(it.json) }.getOrNull() }
    }

    override fun observeSettings(): Flow<AppSettings> = dao.observeSetting(SETTINGS_KEY).map { row ->
        row?.let { runCatching { json.decodeFromString<AppSettings>(it.value) }.getOrNull() } ?: AppSettings()
    }

    override suspend fun getItem(id: String): AppItem? =
        dao.getItem(id)?.toModel(dao.getTodoDetail(id))

    override suspend fun getSettings(): AppSettings = readSettings()

    override suspend fun quickAdd(kind: ItemKind, text: String, groupId: String?): AppItem? = mutationMutex.withLock {
        val clean = text.trim()
        if (clean.isEmpty()) return@withLock null
        val title = clean.take(200)
        val body = if (clean.length > 200) clean.drop(200).trim() else ""
        val now = now()
        val rank = (dao.getItems().maxOfOrNull { it.manualRank } ?: 0L) + 1_000L
        val item = RecurrencePolicy.normalize(AppItem(
            id = newId(), kind = kind, title = title, body = body, manualRank = rank,
            groupId = groupId,
            createdAtEpochMillis = now, updatedAtEpochMillis = now,
            todo = if (kind == ItemKind.TODO) TodoDetail() else null,
        ))
        val placement = GroupPolicy.validatePlacement(item, groupId, currentState().items)
        if (placement is GroupPlacementDecision.Rejected) return@withLock null
        mutateLocked("CREATE") { upsert(item) }
        item
    }

    override suspend fun addDetailed(
        kind: ItemKind,
        title: String,
        body: String,
        groupId: String?,
        detail: TodoDetail?,
        requiredBeforeIds: Set<String>,
    ): AppItem? = mutationMutex.withLock {
        val cleanTitle = title.trim().take(200)
        if (cleanTitle.isEmpty()) return@withLock null
        val state = currentState()
        val time = now()
        val item = RecurrencePolicy.normalize(AppItem(
            id = newId(),
            kind = kind,
            title = cleanTitle,
            body = body.take(100_000),
            manualRank = (state.items.maxOfOrNull { it.manualRank } ?: 0L) + 1_000L,
            groupId = groupId,
            createdAtEpochMillis = time,
            updatedAtEpochMillis = time,
            todo = if (kind == ItemKind.TODO) detail ?: TodoDetail() else null,
        ))
        if (GroupPolicy.validatePlacement(item, groupId, state.items) !is GroupPlacementDecision.Allowed) return@withLock null
        if (RecurrencePolicy.validate(item) is RecurrenceValidation.Invalid) return@withLock null
        val validation = OrderingPolicy.validateProposedPrerequisites(
            state.items,
            OrderingPolicy.sanitizeRelations(state.items, state.relations),
            item,
            requiredBeforeIds,
        )
        if (validation !is PrerequisiteValidation.Valid) return@withLock null

        mutateLocked("CREATE_DETAILED") {
            upsert(item)
            insertPrerequisites(item.id, requiredBeforeIds, time)
        }
        syncReminder(item)
        item
    }

    override suspend fun createGroup(
        kind: ItemKind,
        title: String,
        groupId: String?,
        detail: TodoDetail?,
        requiredBeforeIds: Set<String>,
    ): AppItem? = mutationMutex.withLock {
        val cleanTitle = title.trim().take(200)
        if (cleanTitle.isEmpty() || kind == ItemKind.UNSORTED) return@withLock null
        val time = now()
        val item = RecurrencePolicy.normalize(AppItem(
            id = newId(),
            kind = kind,
            title = cleanTitle,
            body = "",
            manualRank = (dao.getItems().maxOfOrNull { it.manualRank } ?: 0L) + 1_000L,
            isGroup = true,
            groupId = groupId,
            createdAtEpochMillis = time,
            updatedAtEpochMillis = time,
            todo = if (kind == ItemKind.TODO) detail ?: TodoDetail(estimatedMinutes = null) else null,
        ))
        val state = currentState()
        val placement = GroupPolicy.validatePlacement(item, groupId, state.items)
        if (placement is GroupPlacementDecision.Rejected) return@withLock null
        val recurrence = RecurrencePolicy.validate(item)
        if (recurrence is RecurrenceValidation.Invalid) return@withLock null
        val validation = OrderingPolicy.validateProposedPrerequisites(
            state.items,
            OrderingPolicy.sanitizeRelations(state.items, state.relations),
            item,
            requiredBeforeIds,
        )
        if (validation !is PrerequisiteValidation.Valid) return@withLock null
        mutateLocked("CREATE_GROUP") {
            upsert(item)
            insertPrerequisites(item.id, requiredBeforeIds, time)
        }
        syncReminder(item)
        item
    }

    override suspend fun save(item: AppItem, requiredBeforeIds: Set<String>?): AppItem = mutationMutex.withLock {
        val stored = getItem(item.id)
        if (stored != null && stored.revision != item.revision) {
            throw IllegalStateException("項目が変更されたため保存できません")
        }
        val clean = RecurrencePolicy.normalize(item.copy(
            title = item.title.trim().take(200),
            body = item.body.take(100_000),
            updatedAtEpochMillis = now(),
            revision = item.revision + 1,
            todo = if (item.kind == ItemKind.TODO) item.todo ?: TodoDetail() else null,
        ))
        require(clean.title.isNotEmpty()) { "タイトルを入力してください" }
        require(!clean.isGroup || clean.kind != ItemKind.UNSORTED) { "あとで分ける項目はグループにできません" }
        val stateForValidation = currentState()
        when (val placement = GroupPolicy.validatePlacement(clean, clean.groupId, stateForValidation.items)) {
            GroupPlacementDecision.Allowed -> Unit
            is GroupPlacementDecision.Rejected -> throw IllegalArgumentException(placement.message)
        }
        when (val recurrence = RecurrencePolicy.validate(clean)) {
            RecurrenceValidation.Valid -> Unit
            is RecurrenceValidation.Invalid -> throw IllegalArgumentException(recurrence.message)
        }
        if (clean.isGroup) {
            val children = stateForValidation.items.filter { it.groupId == clean.id }
            require(children.all { it.kind == clean.kind }) { "中の項目と異なる種類には変更できません" }
        }
        /*
        if (false) {
            val state = currentState()
            require(validPrerequisites(requiredBeforeIds, clean, state.items, allowCurrent = true)) {
                "前提にできるのは既存の有効なやることだけです"
            }
            val unrelated = state.relations.filterNot { it.toItemId == clean.id && it.type == RelationType.REQUIRED_BEFORE }
            requiredBeforeIds.forEach { prerequisiteId ->
                if (OrderingPolicy.wouldCreateCycle(unrelated, prerequisiteId, clean.id)) {
                    throw IllegalArgumentException("前後関係が循環するため保存できません")
                }
            }
            mutateLocked("UPDATE") {
                upsert(clean)
                dao.deleteRequiredPrerequisites(clean.id)
                requiredBeforeIds.forEach { prerequisiteId ->
                    dao.upsertRelation(ItemRelation(
                        id = newId(), fromItemId = prerequisiteId, toItemId = clean.id,
                        type = RelationType.REQUIRED_BEFORE,
                        createdAtEpochMillis = now(),
                    ).toEntity())
                }
            }
        }
        */
        val current = currentState()
        val prospectiveItems = current.items.filterNot { it.id == clean.id } + clean
        val safeExistingRelations = OrderingPolicy.sanitizeRelations(current.items, current.relations)
        if (requiredBeforeIds != null && clean.kind == ItemKind.TODO) {
            val validation = OrderingPolicy.validateProposedPrerequisites(
                prospectiveItems,
                safeExistingRelations,
                clean,
                requiredBeforeIds,
            )
            if (validation is PrerequisiteValidation.Invalid) {
                throw IllegalArgumentException(validation.message)
            }
            val relationTime = now()
            val replacementRelations = safeExistingRelations
                .filterNot { it.toItemId == clean.id && it.type == RelationType.REQUIRED_BEFORE }
                .plus(
                    requiredBeforeIds.sorted().map { prerequisiteId ->
                        ItemRelation(
                            id = newId(),
                            fromItemId = prerequisiteId,
                            toItemId = clean.id,
                            type = RelationType.REQUIRED_BEFORE,
                            createdAtEpochMillis = relationTime,
                        )
                    },
                )
            mutateLocked("UPDATE") {
                upsert(clean)
                dao.clearRelations()
                dao.upsertRelations(replacementRelations.map(ItemRelation::toEntity))
            }
        } else {
            val relationsWithoutChangedItem = OrderingPolicy.relationsAfterKindChange(
                clean.id,
                clean.kind,
                current.relations,
            )
            val safeRelations = OrderingPolicy.sanitizeRelations(prospectiveItems, relationsWithoutChangedItem)
            mutateLocked("UPDATE") {
                upsert(clean)
                if (safeRelations != current.relations) {
                    dao.clearRelations()
                    dao.upsertRelations(safeRelations.map(ItemRelation::toEntity))
                }
            }
        }
        syncReminder(clean)
        clean
    }

    override suspend fun delete(id: String) = mutationMutex.withLock {
        val item = getItem(id) ?: return@withLock
        val time = now()
        val state = currentState()
        val children = if (item.isGroup) state.items.filter { it.groupId == item.id } else emptyList()
        val afterDelete = state.items.map { current ->
            when {
                current.id == item.id -> current.copy(lifecycle = ItemLifecycle.DELETED, updatedAtEpochMillis = time)
                current.id in children.map { it.id } -> current.copy(groupId = item.groupId, updatedAtEpochMillis = time)
                else -> current
            }
        }
        val safeRelations = OrderingPolicy.sanitizeRelations(afterDelete, state.relations)
        mutateLocked("DELETE") {
            children.forEach { upsert(it.copy(groupId = item.groupId, updatedAtEpochMillis = time)) }
            upsert(item.copy(lifecycle = ItemLifecycle.DELETED, updatedAtEpochMillis = time))
            dao.clearRelations()
            dao.upsertRelations(safeRelations.map(ItemRelation::toEntity))
        }
        reminderScheduler.cancel(id)
    }

    override suspend fun complete(id: String) = mutationMutex.withLock {
        completeWithOperation(id)
        Unit
    }

    private suspend fun completeWithOperation(id: String, operationType: String = "COMPLETE"): String? {
        val item = getItem(id) ?: return null
        if (item.lifecycle != ItemLifecycle.ACTIVE) return null
        val time = now()
        val state = currentState()
        val affected = if (item.isGroup) {
            val ids = GroupPolicy.descendantIds(item.id, state.items) + item.id
            state.items.filter { it.id in ids && it.lifecycle == ItemLifecycle.ACTIVE }
        } else {
            listOf(item)
        }
        val originalsForCopy = if (item.isGroup) GroupPolicy.recurringTemplateItems(item, state.items) else listOf(item)
        val nextItems = if (item.isGroup) {
            RecurrencePolicy.buildNextGroupOccurrence(item, state.items, time, ::newId)
        } else {
            RecurrencePolicy.buildNextOccurrence(item, time, newId())?.let(::listOf).orEmpty()
        }
        val copiedIds = if (item.isGroup) {
            GroupPolicy.recurringCopyIdMap(item, state.items, nextItems)
        } else {
            originalsForCopy.zip(nextItems).associate { (original, copy) -> original.id to copy.id }
        }
        val affectedIds = affected.map { it.id }.toSet()
        val copiedRelations = state.relations.filter {
            it.fromItemId in copiedIds && it.toItemId in copiedIds
        }.map {
            it.copy(
                id = newId(),
                fromItemId = copiedIds.getValue(it.fromItemId),
                toItemId = copiedIds.getValue(it.toItemId),
                createdAtEpochMillis = time,
            )
        }
        val completedItems = state.items.map { current ->
            if (current.id in affectedIds) current.copy(
                lifecycle = ItemLifecycle.COMPLETED,
                completedAtEpochMillis = time,
                updatedAtEpochMillis = time,
                revision = current.revision + 1,
            ) else current
        }
        val safeRelations = OrderingPolicy.sanitizeRelations(
            completedItems + nextItems,
            state.relations + copiedRelations,
        )
        val operationId = mutateLocked(operationType) {
            affected.forEach { current ->
                upsert(current.copy(
                    lifecycle = ItemLifecycle.COMPLETED,
                    completedAtEpochMillis = time,
                    updatedAtEpochMillis = time,
                    revision = current.revision + 1,
                ))
            }
            nextItems.forEach { upsert(it) }
            dao.clearRelations()
            dao.upsertRelations(safeRelations.map(ItemRelation::toEntity))
        }
        affected.forEach { reminderScheduler.cancel(it.id) }
        nextItems.forEach { syncReminder(it) }
        return operationId
    }

    override suspend fun defer(id: String, configuration: DeferConfiguration): DeferDecision? = mutationMutex.withLock {
        deferWithOperation(id, configuration)?.first
    }

    private suspend fun deferWithOperation(
        id: String,
        configuration: DeferConfiguration,
        operationType: String = "DEFER",
    ): Pair<DeferDecision, String>? {
        val state = currentState()
        val items = OrderingPolicy.canonicalSort(
            state.items.filter { it.kind == ItemKind.TODO && it.lifecycle == ItemLifecycle.ACTIVE && !it.isGroup },
            state.relations,
        )
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return null
        val decision = DeferPolicy.decide(items[index], index, items.size, configuration)
        val destination = items.getOrNull(decision.destinationIndex)
        val rank = destination?.manualRank?.plus(1) ?: decision.updated.manualRank
        val updated = decision.updated.copy(
            manualRank = rank,
            updatedAtEpochMillis = now(),
            revision = decision.updated.revision + 1,
        )
        val operationId = mutateLocked(operationType) { upsert(updated) }
        syncReminder(updated)
        return decision.copy(updated = updated) to operationId
    }

    override suspend fun convertMemo(id: String): AppItem? = mutationMutex.withLock {
        val memo = getItem(id) ?: return@withLock null
        if (memo.kind != ItemKind.MEMO) return@withLock null
        val time = now()
        val created = AppItem(
            id = newId(), kind = ItemKind.TODO, title = memo.title, body = memo.body,
            manualRank = (dao.getItems().maxOfOrNull { it.manualRank } ?: 0L) + 1_000L,
            convertedFromId = memo.id, createdAtEpochMillis = time, updatedAtEpochMillis = time,
            todo = TodoDetail(),
        )
        mutateLocked("CONVERT_MEMO") {
            upsert(memo.copy(lifecycle = ItemLifecycle.CONVERTED, updatedAtEpochMillis = time))
            upsert(created)
        }
        created
    }

    override suspend fun archiveMemo(id: String) = mutationMutex.withLock {
        val memo = getItem(id) ?: return@withLock
        if (memo.kind != ItemKind.MEMO) return@withLock
        val state = currentState()
        val affected = if (memo.isGroup) {
            val ids = GroupPolicy.descendantIds(memo.id, state.items) + memo.id
            state.items.filter { it.id in ids && it.lifecycle == ItemLifecycle.ACTIVE }
        } else listOf(memo)
        val time = now()
        mutateLocked("ARCHIVE_MEMO") {
            affected.forEach { current ->
                upsert(current.copy(lifecycle = ItemLifecycle.ARCHIVED, archivedAtEpochMillis = time, updatedAtEpochMillis = time))
            }
        }
        Unit
    }

    override suspend fun restore(id: String) = mutationMutex.withLock {
        // currentState() intentionally excludes DELETED rows. Read the target
        // directly so a deleted leaf can be restored without searching a
        // filtered state snapshot.
        val item = dao.getItem(id)?.toModel(dao.getTodoDetail(id)) ?: return@withLock
        val state = currentState()
        val restoringIds = if (item.isGroup) {
            val ids = GroupPolicy.descendantIds(item.id, state.items) + item.id
            ids
        } else setOf(item.id)
        // A deleted group has already reparented its direct children during
        // delete. If the group is absent from currentState, restoring the
        // group itself therefore preserves that explicit reparenting policy.
        val restoring = (state.items.filter { it.id in restoringIds } + item)
            .distinctBy { it.id }
        val time = now()
        val restorationCandidates = restoring.map { current ->
            current.copy(
                lifecycle = ItemLifecycle.ACTIVE,
                completedAtEpochMillis = null,
                archivedAtEpochMillis = null,
                updatedAtEpochMillis = time,
            )
        }
        val restoreContext = state.items.filterNot { it.id in restoringIds } + restorationCandidates
        val restoredItems = (state.items.map { current ->
            restorationCandidates.firstOrNull { it.id == current.id } ?: current
        } + restorationCandidates.filter { candidate ->
            state.items.none { it.id == candidate.id }
        }).map { candidate ->
            if (candidate.groupId != null &&
                GroupPolicy.validatePlacement(candidate, candidate.groupId, restoreContext) !is GroupPlacementDecision.Allowed
            ) candidate.copy(groupId = null) else candidate
        }
        val restoredById = restoredItems.associateBy { it.id }
        val safeRelations = OrderingPolicy.sanitizeRelations(restoredItems, state.relations)
        mutateLocked("RESTORE") {
            restorationCandidates.forEach { candidate ->
                upsert(restoredById.getValue(candidate.id))
            }
            dao.clearRelations()
            dao.upsertRelations(safeRelations.map(ItemRelation::toEntity))
        }
        reconcileReminders(state.items, restoredItems)
    }

    override suspend fun validateMove(id: String, destinationIndex: Int): MoveDecision {
        val state = currentState()
        val items = OrderingPolicy.canonicalSort(
            state.items.filter { it.kind == ItemKind.TODO && it.lifecycle == ItemLifecycle.ACTIVE && !it.isGroup },
            state.relations,
        )
        return OrderingPolicy.validateMove(items, state.relations, id, destinationIndex)
    }

    override suspend fun move(id: String, destinationIndex: Int): MoveDecision = mutationMutex.withLock {
        val decision = validateMove(id, destinationIndex)
        if (decision is MoveDecision.Allowed) {
            val item = getItem(id) ?: return@withLock MoveDecision.Rejected(
                jp.oboegaki.core.domain.MoveRejectionReason.INVALID_INDEX, "やることが見つかりません",
            )
            mutateLocked("MOVE") { upsert(item.copy(manualRank = decision.newRank, updatedAtEpochMillis = now())) }
        }
        decision
    }

    override suspend fun moveFree(id: String, destinationIndex: Int) = mutationMutex.withLock {
        val item = getItem(id) ?: return@withLock
        if (item.kind == ItemKind.TODO || item.lifecycle != ItemLifecycle.ACTIVE) return@withLock
        val group = currentState().items
            .filter { it.kind == item.kind && it.lifecycle == ItemLifecycle.ACTIVE && it.groupId == item.groupId }
            .sortedWith(compareBy<AppItem> { it.manualRank }.thenBy { it.createdAtEpochMillis }.thenBy { it.id })
        if (destinationIndex !in group.indices) return@withLock
        val moved = group.toMutableList().apply {
            val source = indexOfFirst { it.id == id }
            if (source < 0) return@withLock
            add(destinationIndex.coerceAtMost(size - 1), removeAt(source))
        }
        val previous = moved.getOrNull(destinationIndex - 1)?.manualRank
        val next = moved.getOrNull(destinationIndex + 1)?.manualRank
        val rank = when {
            previous == null && next == null -> 1_000L
            previous == null -> next!! - 1_000L
            next == null -> previous + 1_000L
            next - previous > 1 -> previous + (next - previous) / 2
            else -> (destinationIndex + 1L) * 1_000L
        }
        mutateLocked("MOVE") { upsert(item.copy(manualRank = rank, updatedAtEpochMillis = now())) }
        Unit
    }

    override suspend fun moveWithinGroup(id: String, direction: Int): MoveDecision = mutationMutex.withLock {
        val state = currentState()
        val item = state.items.firstOrNull { it.id == id } ?: return@withLock MoveDecision.Rejected(
            jp.oboegaki.core.domain.MoveRejectionReason.INVALID_INDEX,
            "項目が見つかりません",
        )
        val siblings = state.items.filter {
            it.kind == item.kind && it.lifecycle == ItemLifecycle.ACTIVE && it.groupId == item.groupId
        }.let { values ->
            if (item.kind == ItemKind.TODO) OrderingPolicy.canonicalSort(values, state.relations)
            else values.sortedWith(compareBy<AppItem> { it.manualRank }.thenBy { it.createdAtEpochMillis }.thenBy { it.id })
        }
        val sourceIndex = siblings.indexOfFirst { it.id == id }
        val destinationIndex = sourceIndex + direction.coerceIn(-1, 1)
        if (sourceIndex < 0 || destinationIndex !in siblings.indices) {
            return@withLock MoveDecision.Rejected(
                jp.oboegaki.core.domain.MoveRejectionReason.INVALID_INDEX,
                "この方向には移動できません",
            )
        }
        if (item.kind != ItemKind.TODO) {
            val moved = siblings.toMutableList().apply {
                add(destinationIndex, removeAt(sourceIndex))
            }
            val time = now()
            mutateLocked("MOVE") {
                moved.forEachIndexed { index, sibling ->
                    upsert(sibling.copy(manualRank = (index + 1L) * 1_000L, updatedAtEpochMillis = time))
                }
            }
            return@withLock MoveDecision.Allowed(destinationIndex, (destinationIndex + 1L) * 1_000L)
        }
        val decision = OrderingPolicy.validateMove(siblings, state.relations, id, destinationIndex)
        if (decision is MoveDecision.Allowed) {
            mutateLocked("MOVE") { upsert(item.copy(manualRank = decision.newRank, updatedAtEpochMillis = now())) }
        }
        decision
    }

    override suspend fun split(id: String, titles: List<String>): SplitValidation = mutationMutex.withLock {
        val validation = SplitPolicy.validate(titles)
        if (validation !is SplitValidation.Valid) return@withLock validation
        val parent = getItem(id) ?: return@withLock SplitValidation.Invalid("元のやることが見つかりません")
        val time = now()
        val children = SplitPolicy.buildChildren(parent, validation.titles, time, ::newId)
        val relations = currentState().relations
        val firstId = children.first().id
        val lastId = children.last().id
        val rewired = relations.filter { it.fromItemId == parent.id || it.toItemId == parent.id }.map {
            it.copy(
                id = newId(),
                fromItemId = if (it.fromItemId == parent.id) lastId else it.fromItemId,
                toItemId = if (it.toItemId == parent.id) firstId else it.toItemId,
                createdAtEpochMillis = time,
            )
        }
        val safeRewired = OrderingPolicy.sanitizeRelations(
            listOf(parent.copy(lifecycle = ItemLifecycle.SPLIT)) + children,
            rewired,
        )
        mutateLocked("SPLIT") {
            upsert(parent.copy(lifecycle = ItemLifecycle.SPLIT, updatedAtEpochMillis = time))
            children.forEach { upsert(it) }
            dao.deleteRelationsForItem(parent.id)
            dao.upsertRelations(safeRewired.map(ItemRelation::toEntity))
        }
        reminderScheduler.cancel(id)
        children.forEach { syncReminder(it) }
        validation
    }

    override suspend fun postponeSplitPrompt(id: String, threshold: Int) = mutationMutex.withLock {
        val item = getItem(id) ?: return@withLock
        mutateLocked("POSTPONE_SPLIT") { upsert(DeferPolicy.postponePrompt(item, threshold)) }
        Unit
    }

    override suspend fun disableSplitPrompt(id: String) = mutationMutex.withLock {
        val item = getItem(id) ?: return@withLock
        val detail = item.todo ?: return@withLock
        mutateLocked("DISABLE_SPLIT") { upsert(item.copy(todo = detail.copy(splitPromptDisabled = true))) }
        Unit
    }

    override suspend fun undo(): Boolean = mutationMutex.withLock {
        val time = now()
        val operation = dao.getUndoableOperation(time) ?: return@withLock false
        val snapshot = runCatching { json.decodeFromString<DataSnapshot>(operation.payloadJson) }.getOrNull()
            ?: return@withLock false
        val current = currentState()
        var eligibility: UndoEligibility? = null
        database.inTransaction {
            val target = dao.getOperation(operation.operationId)?.toUndoOperation()
                ?: return@inTransaction
            val candidates = dao.getOperations().map(OperationEntity::toUndoOperation)
            eligibility = UndoPolicy.evaluate(target, time, candidates)
            if (eligibility is UndoEligibility.Allowed) {
                restoreSnapshot(
                    snapshot,
                    time,
                    (eligibility as UndoEligibility.Allowed).scope == UndoRestoreScope.FULL_STATE,
                )
                dao.markOperationReverted(operation.operationId, time)
            }
        }
        if (eligibility !is UndoEligibility.Allowed) return@withLock false
        reconcileReminders(current.items, snapshot.items)
        reminderScheduler.applySettings(getSettings())
        true
    }

    override suspend fun performNotificationAction(
        action: NotificationAction,
        itemId: String,
        expectedRevision: Long?,
    ): NotificationActionResult = mutationMutex.withLock {
        val settings = getSettings()
        if (!settings.reminderNotificationActionsEnabled) {
            return@withLock NotificationActionResult.ActionsDisabled
        }
        val item = getItem(itemId) ?: return@withLock NotificationActionResult.ItemNotFound
        if (item.lifecycle != ItemLifecycle.ACTIVE) {
            return@withLock NotificationActionResult.ItemNotActive(item.lifecycle)
        }
        if (item.kind != ItemKind.TODO || item.isGroup) {
            return@withLock NotificationActionResult.ItemNotEligible(item.kind, item.isGroup)
        }
        if (expectedRevision != null && item.revision != expectedRevision) {
            return@withLock NotificationActionResult.StaleNotification
        }

        val applied = when (action) {
            NotificationAction.COMPLETE -> completeWithOperation(itemId, "NOTIFICATION_COMPLETE:$itemId")?.let {
                it to false
            }
            NotificationAction.DEFER -> deferWithOperation(
                itemId,
                DeferConfiguration.from(settings),
                "NOTIFICATION_DEFER:$itemId",
            )?.let { (decision, operationId) -> operationId to decision.shouldSuggestSplit }
        } ?: return@withLock NotificationActionResult.StaleNotification

        NotificationActionResult.Applied(
            action = action,
            itemId = itemId,
            title = item.title,
            undoToken = NotificationUndoToken(
                operationId = applied.first,
                itemId = itemId,
                action = action,
                expiresAtEpochMillis = now() + NOTIFICATION_UNDO_MILLIS,
            ),
            shouldSuggestSplit = applied.second,
        )
    }

    override suspend fun undoNotification(token: NotificationUndoToken): NotificationUndoResult = mutationMutex.withLock {
        val time = now()
        if (time > token.expiresAtEpochMillis) return@withLock NotificationUndoResult.Expired
        val operation = dao.getOperation(token.operationId) ?: return@withLock NotificationUndoResult.NotFound
        if (operation.type != "NOTIFICATION_${token.action.name}:${token.itemId}") {
            return@withLock NotificationUndoResult.NotFound
        }
        val snapshot = runCatching { json.decodeFromString<DataSnapshot>(operation.payloadJson) }
            .getOrNull() ?: return@withLock NotificationUndoResult.Failed("元に戻す内容を確認できません")
        val current = currentState()
        if (snapshot.items.none { it.id == token.itemId }) {
            return@withLock NotificationUndoResult.NotFound
        }
        var eligibility: UndoEligibility? = null
        database.inTransaction {
            // Conflict inspection and restore share the same writer transaction.
            val target = dao.getOperation(operation.operationId)?.toUndoOperation()
                ?: return@inTransaction
            eligibility = UndoPolicy.evaluate(
                target,
                time,
                dao.getOperations().map(OperationEntity::toUndoOperation),
            )
            if (eligibility is UndoEligibility.Allowed) {
                restoreSnapshot(snapshot, time, restoreSettingsAndThemes = false)
                dao.markOperationReverted(operation.operationId, time)
            }
        }
        when (val result = eligibility) {
            UndoEligibility.Expired -> return@withLock NotificationUndoResult.Expired
            UndoEligibility.AlreadyReverted -> return@withLock NotificationUndoResult.AlreadyReverted
            UndoEligibility.LaterOperation -> return@withLock NotificationUndoResult.DifferentOperationAlreadyHappened
            is UndoEligibility.Allowed -> if (result.scope != UndoRestoreScope.ITEMS_ONLY) {
                return@withLock NotificationUndoResult.Failed("元に戻せませんでした")
            }
            else -> return@withLock NotificationUndoResult.Failed("元に戻せませんでした")
        }
        reconcileReminders(current.items, snapshot.items)
        reminderScheduler.applySettings(getSettings())
        NotificationUndoResult.Applied
    }

    override suspend fun rescheduleAllReminders() = mutationMutex.withLock {
        val state = currentState()
        val staleIds = state.items.asSequence()
            .filter { it.kind == ItemKind.TODO && !it.isGroup && it.todo?.scheduledAtEpochMillis != null }
            .map { it.id }
            .toSet()
        reminderScheduler.reconcileAll(state.items.mapNotNull(::toReminder), staleIds)
    }

    override suspend fun saveSettings(settings: AppSettings) = mutationMutex.withLock {
        val safe = normalizeSettings(settings)
        dao.upsertSetting(SettingEntity(SETTINGS_KEY, json.encodeToString(safe)))
        reminderScheduler.applySettings(safe)
    }

    override suspend fun saveTheme(theme: ThemeDefinition): ThemeValidation = mutationMutex.withLock {
        val validation = ThemePolicy.validate(theme)
        if (validation is ThemeValidation.Valid) {
            val custom = theme.copy(builtIn = false)
            dao.upsertTheme(ThemeEntity(custom.id, custom.name, false, json.encodeToString(custom), now()))
        }
        validation
    }

    override suspend fun deleteTheme(id: String) = mutationMutex.withLock { dao.deleteCustomTheme(id) }

    override suspend fun exportBackupJson(): String {
        val state = currentState()
        val themes = dao.getThemes().mapNotNull { runCatching { json.decodeFromString<ThemeDefinition>(it.json) }.getOrNull() }
        val settings = dao.getSetting(SETTINGS_KEY)?.let {
            runCatching { json.decodeFromString<AppSettings>(it.value) }.getOrNull()
        } ?: AppSettings()
        return json.encodeToString(
            BackupEnvelope(
                createBackupManifest(appVersion, now()),
                state.items,
                state.relations,
                themes,
                settings,
            ),
        )
    }

    override suspend fun importBackupJson(value: String): BackupImportResult = mutationMutex.withLock {
        if (value.encodeToByteArray().size > 50 * 1024 * 1024) {
            return@withLock BackupImportResult(0, 0, "50MBを超えるバックアップは読み込めません")
        }
        val backup = runCatching { json.decodeFromString<BackupEnvelope>(value) }.getOrElse {
            return@withLock BackupImportResult(0, 0, "バックアップの形式を確認できません")
        }
        if (backup.manifest.schemaVersion !in 1..4) return@withLock BackupImportResult(0, 0, "未対応のバックアップ形式です")
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
        val valid = normalized.items
        val relations = normalized.relations
        val correctedRelations = backup.relations.size - relations.size
        val safeThemes = backup.themes.filter { ThemePolicy.validate(it) is ThemeValidation.Valid }
        val safeSettings = normalizeSettings(backup.settings)
        val before = currentState()
        val time = now()
        database.inTransaction {
            dao.upsertOperation(OperationEntity(newId(), "IMPORT", time, time + 10_000, json.encodeToString(before), null))
            dao.clearRelations()
            dao.clearTodoDetails()
            dao.clearItems()
            valid.forEach { upsert(it) }
            dao.upsertRelations(relations.map(ItemRelation::toEntity))
            dao.clearCustomThemes()
            safeThemes.forEach { theme ->
                val custom = theme.copy(builtIn = false)
                dao.upsertTheme(ThemeEntity(custom.id, custom.name, false, json.encodeToString(custom), time))
            }
            dao.upsertSetting(SettingEntity(SETTINGS_KEY, json.encodeToString(safeSettings)))
        }
        reconcileReminders(before.items, valid)
        reminderScheduler.applySettings(safeSettings)
        val correctionParts = buildList {
            if (normalized.duplicateItemIds > 0) add("重複したID ${normalized.duplicateItemIds}件")
            if (normalized.duplicateRelationIds > 0) add("重複した前後関係ID ${normalized.duplicateRelationIds}件")
            if (normalized.correctedGroupReferences > 0) add("グループ参照 ${normalized.correctedGroupReferences}件")
            if (normalized.correctedParentReferences > 0) add("親参照 ${normalized.correctedParentReferences}件")
            if (normalized.correctedConversionReferences > 0) add("変換元参照 ${normalized.correctedConversionReferences}件")
            if (normalized.correctedRelations > 0) add("前後関係 ${normalized.correctedRelations}件")
        }
        val correctionMessage = correctionParts.takeIf { it.isNotEmpty() }?.joinToString("、", prefix = "（補正: ", postfix = "）") ?: ""
        BackupImportResult(
            valid.size,
            backup.items.size - valid.size,
            "${valid.size}件を読み込みました$correctionMessage",
            correctedRelations,
            successful = true,
            duplicateItemIds = normalized.duplicateItemIds,
            duplicateRelationIds = normalized.duplicateRelationIds,
            correctedGroupReferences = normalized.correctedGroupReferences,
            correctedParentReferences = normalized.correctedParentReferences,
            correctedConversionReferences = normalized.correctedConversionReferences,
        )
    }

    /** Must only be called while mutationMutex is held. */
    private suspend fun mutateLocked(type: String, block: suspend RoomItemRepository.() -> Unit): String {
        val before = currentState()
        val time = now()
        val operationId = newId()
        database.inTransaction {
            block()
            dao.upsertOperation(OperationEntity(
                operationId = operationId, type = type, createdAtEpochMillis = time,
                expiresAtEpochMillis = time + 10_000,
                payloadJson = json.encodeToString(before), revertedAtEpochMillis = null,
            ))
            dao.trimOperations()
        }
        return operationId
    }

    private suspend fun currentState(): DataSnapshot {
        val details = dao.getTodoDetails().associateBy { it.itemId }
        return DataSnapshot(
            items = dao.getItems().map { it.toModel(details[it.id]) },
            relations = dao.getRelations().map(ItemRelationEntity::toModel),
            customThemes = dao.getThemes().mapNotNull { row ->
                runCatching { json.decodeFromString<ThemeDefinition>(row.json) }.getOrNull()
            },
            settings = readSettings(),
        )
    }

    private suspend fun restoreSnapshot(
        snapshot: DataSnapshot,
        time: Long,
        restoreSettingsAndThemes: Boolean,
    ) {
        val safeItems = snapshot.items.map { item ->
            if (item.groupId != null &&
                GroupPolicy.validatePlacement(item, item.groupId, snapshot.items) !is GroupPlacementDecision.Allowed
            ) item.copy(groupId = null) else item
        }
        val safeRelations = OrderingPolicy.sanitizeRelations(safeItems, snapshot.relations)
        dao.clearRelations()
        dao.clearTodoDetails()
        dao.clearItems()
        safeItems.forEach { upsert(it) }
        dao.upsertRelations(safeRelations.map(ItemRelation::toEntity))
        if (restoreSettingsAndThemes) snapshot.customThemes?.let { themes ->
            dao.clearCustomThemes()
            themes.forEach { theme ->
                val custom = theme.copy(builtIn = false)
                dao.upsertTheme(ThemeEntity(custom.id, custom.name, false, json.encodeToString(custom), time))
            }
        }
        if (restoreSettingsAndThemes) snapshot.settings?.let { settings ->
            dao.upsertSetting(SettingEntity(SETTINGS_KEY, json.encodeToString(normalizeSettings(settings))))
        }
    }

    private fun normalizeSettings(settings: AppSettings): AppSettings = settings.copy(
        splitThreshold = settings.splitThreshold.coerceIn(1, 10),
        deferItems = settings.deferItems.coerceIn(1, 20),
        undoSeconds = settings.undoSeconds.coerceIn(3, 10),
    )

    private suspend fun readSettings(): AppSettings = dao.getSetting(SETTINGS_KEY)?.let { row ->
        runCatching { json.decodeFromString<AppSettings>(row.value) }.getOrNull()
    } ?: AppSettings()

    private suspend fun upsert(item: AppItem) {
        dao.upsertItem(item.toEntity())
        item.todo?.let { dao.upsertTodoDetail(it.toEntity(item.id)) }
        if (item.todo == null) dao.getTodoDetail(item.id)?.let { dao.deleteTodoDetail(it) }
    }

    private fun newId(): String = buildString {
        append(now().toString(16))
        append('-')
        append(Random.nextLong().toString(16))
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    private suspend fun syncReminder(item: AppItem) {
        val reminder = toReminder(item)
        if (reminder == null) reminderScheduler.cancel(item.id) else reminderScheduler.schedule(reminder)
    }

    private fun toReminder(item: AppItem): Reminder? {
        val scheduled = item.todo?.scheduledAtEpochMillis ?: return null
        if (!ReminderPolicy.isEligible(item, now())) return null
        return Reminder(item.id, item.title, scheduled, item.revision)
    }

    private fun validPrerequisites(
        prerequisiteIds: Set<String>,
        item: AppItem,
        items: List<AppItem>,
        allowCurrent: Boolean = false,
    ): Boolean {
        if (item.kind != ItemKind.TODO && prerequisiteIds.isNotEmpty()) return false
        val activeTodoIds = items.asSequence()
            .filter { it.kind == ItemKind.TODO && it.lifecycle == ItemLifecycle.ACTIVE }
            .map { it.id }
            .toSet()
        return prerequisiteIds.all { it in activeTodoIds && (allowCurrent || it != item.id) }
    }

    private suspend fun insertPrerequisites(itemId: String, prerequisiteIds: Set<String>, time: Long) {
        prerequisiteIds.forEach { prerequisiteId ->
            dao.upsertRelation(ItemRelation(
                id = newId(),
                fromItemId = prerequisiteId,
                toItemId = itemId,
                type = RelationType.REQUIRED_BEFORE,
                createdAtEpochMillis = time,
            ).toEntity())
        }
    }

    private suspend fun reconcileReminders(previous: List<AppItem>, restored: List<AppItem>) {
        val staleIds = previous.asSequence()
            .filter { it.kind == ItemKind.TODO && !it.isGroup && it.todo?.scheduledAtEpochMillis != null }
            .map { it.id }
            .toSet()
        reminderScheduler.reconcileAll(restored.mapNotNull(::toReminder), staleIds)
    }

    private companion object {
        const val DEFAULT_APP_VERSION = "unknown"
        const val SETTINGS_KEY = "app_settings"
        const val NOTIFICATION_UNDO_MILLIS = 10_000L
        const val LEGACY_IMPLICIT_DEFER_ITEMS = 3
    }
}

private fun OperationEntity.toUndoOperation(): UndoOperation = UndoOperation(
    operationId = operationId,
    type = type,
    createdAtEpochMillis = createdAtEpochMillis,
    expiresAtEpochMillis = expiresAtEpochMillis,
    revertedAtEpochMillis = revertedAtEpochMillis,
)

private suspend fun <T> AppDatabase.inTransaction(block: suspend () -> T): T =
    useWriterConnection { connection -> connection.immediateTransaction { block() } }
