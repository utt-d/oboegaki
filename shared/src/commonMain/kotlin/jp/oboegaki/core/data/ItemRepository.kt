package jp.oboegaki.core.data

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
import jp.oboegaki.core.domain.SplitPolicy
import jp.oboegaki.core.domain.SplitValidation
import jp.oboegaki.core.domain.ThemeValidation
import jp.oboegaki.core.domain.UndoEligibility
import jp.oboegaki.core.domain.UndoOperation
import jp.oboegaki.core.domain.UndoPolicy
import jp.oboegaki.core.domain.UndoRestoreScope
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
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class RoomItemRepository(
    private val database: AppDatabase,
    private val reminderScheduler: ReminderScheduler = NoOpReminderScheduler,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true },
    private val appVersion: String = DEFAULT_APP_VERSION,
) : ItemRepository {
    private val runtime = RoomRepositoryRuntime(database, reminderScheduler, json, appVersion)
    private val settingsStore = RoomSettingsStore(runtime)
    private val themeStore = RoomThemeStore(runtime)
    private val backupStore = RoomBackupStore(runtime)
    private val queryStore = RoomItemQueryStore(runtime)
    private val dao get() = runtime.dao
    private val mutationMutex get() = runtime.mutationMutex

    override fun observeRelations(): Flow<List<ItemRelation>> = queryStore.observeRelations()

    override fun observeAllSections() = queryStore.observeAllSections()

    override fun observeActiveTodos() = queryStore.observeActiveTodos()
    override fun observeActiveMemos() = queryStore.observeActiveMemos()

    override fun observeThemes(): Flow<List<ThemeDefinition>> = themeStore.observe()

    override fun observeSettings(): Flow<AppSettings> = settingsStore.observe()

    override suspend fun getItem(id: String): AppItem? = queryStore.getItem(id)

    override suspend fun getSettings(): AppSettings = settingsStore.read()

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

    override suspend fun saveSettings(settings: AppSettings) = settingsStore.save(settings)

    override suspend fun saveTheme(theme: ThemeDefinition): ThemeValidation = themeStore.save(theme)

    override suspend fun deleteTheme(id: String) = themeStore.delete(id)

    override suspend fun exportBackupJson(): String = backupStore.exportJson()

    override suspend fun inspectBackupJson(value: String): BackupInspectionResult = backupStore.inspectJson(value)

    override suspend fun importBackupJson(value: String): BackupImportResult = backupStore.importJson(value)

    // Thin compatibility helpers keep the command implementation readable;
    // storage, journaling, IDs and reminders are owned by the runtime.
    private suspend fun mutateLocked(type: String, block: suspend RoomItemRepository.() -> Unit): String =
        runtime.mutateLocked(type) { this@RoomItemRepository.block() }

    private suspend fun currentState(): DataSnapshot = runtime.currentState()

    private suspend fun restoreSnapshot(
        snapshot: DataSnapshot,
        time: Long,
        restoreSettingsAndThemes: Boolean,
    ) = runtime.restoreSnapshot(snapshot, time, restoreSettingsAndThemes)

    private suspend fun upsert(item: AppItem) = runtime.upsert(item)

    private fun newId(): String = runtime.newId()

    private fun now(): Long = runtime.now()

    private suspend fun syncReminder(item: AppItem) = runtime.syncReminder(item)

    private fun toReminder(item: AppItem): Reminder? = runtime.toReminder(item)

    private suspend fun insertPrerequisites(itemId: String, prerequisiteIds: Set<String>, time: Long) =
        runtime.insertPrerequisites(itemId, prerequisiteIds, time)

    private suspend fun reconcileReminders(previous: List<AppItem>, restored: List<AppItem>) =
        runtime.reconcileReminders(previous, restored)

    private companion object {
        const val DEFAULT_APP_VERSION = "unknown"
        const val NOTIFICATION_UNDO_MILLIS = 10_000L
    }
}

private fun OperationEntity.toUndoOperation(): UndoOperation = UndoOperation(
    operationId = operationId,
    type = type,
    createdAtEpochMillis = createdAtEpochMillis,
    expiresAtEpochMillis = expiresAtEpochMillis,
    revertedAtEpochMillis = revertedAtEpochMillis,
)
