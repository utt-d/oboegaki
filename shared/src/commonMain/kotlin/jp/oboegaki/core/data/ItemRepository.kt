package jp.oboegaki.core.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import jp.oboegaki.core.domain.DeferDecision
import jp.oboegaki.core.domain.DeferPolicy
import jp.oboegaki.core.domain.MoveDecision
import jp.oboegaki.core.domain.OrderingPolicy
import jp.oboegaki.core.domain.SplitPolicy
import jp.oboegaki.core.domain.SplitValidation
import jp.oboegaki.core.domain.ThemePolicy
import jp.oboegaki.core.domain.ThemeValidation
import jp.oboegaki.core.model.AllSections
import jp.oboegaki.core.model.AppItem
import jp.oboegaki.core.model.AppSettings
import jp.oboegaki.core.model.ItemKind
import jp.oboegaki.core.model.ItemLifecycle
import jp.oboegaki.core.model.ItemRelation
import jp.oboegaki.core.model.Priority
import jp.oboegaki.core.model.ThemeDefinition
import jp.oboegaki.core.model.TodoDetail
import jp.oboegaki.platform.NoOpReminderScheduler
import jp.oboegaki.platform.Reminder
import jp.oboegaki.platform.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    suspend fun quickAdd(kind: ItemKind, text: String): AppItem?
    suspend fun save(item: AppItem, requiredBeforeIds: Set<String>? = null): AppItem
    suspend fun delete(id: String)
    suspend fun complete(id: String)
    suspend fun defer(id: String, threshold: Int): DeferDecision?
    suspend fun convertMemo(id: String): AppItem?
    suspend fun archiveMemo(id: String)
    suspend fun restore(id: String)
    suspend fun validateMove(id: String, destinationIndex: Int): MoveDecision
    suspend fun move(id: String, destinationIndex: Int): MoveDecision
    suspend fun moveFree(id: String, destinationIndex: Int)
    suspend fun split(id: String, titles: List<String>): SplitValidation
    suspend fun postponeSplitPrompt(id: String, threshold: Int)
    suspend fun disableSplitPrompt(id: String)
    suspend fun undo(): Boolean
    suspend fun saveSettings(settings: AppSettings)
    suspend fun saveTheme(theme: ThemeDefinition): ThemeValidation
    suspend fun deleteTheme(id: String)
    suspend fun seedIfEmpty()
    suspend fun exportBackupJson(): String
    suspend fun importBackupJson(value: String): BackupImportResult
}

data class BackupImportResult(val importedItems: Int, val rejectedItems: Int, val message: String)

@Serializable
private data class DataSnapshot(
    val items: List<AppItem>,
    val relations: List<ItemRelation>,
)

@Serializable
private data class BackupManifest(
    val schemaVersion: Int = 1,
    val appVersion: String = "0.1.0",
    val createdAtEpochMillis: Long,
)

@Serializable
private data class BackupEnvelope(
    val manifest: BackupManifest,
    val items: List<AppItem>,
    val relations: List<ItemRelation>,
    val themes: List<ThemeDefinition>,
    val settings: AppSettings,
)

class RoomItemRepository(
    private val database: AppDatabase,
    private val reminderScheduler: ReminderScheduler = NoOpReminderScheduler,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true },
) : ItemRepository {
    private val dao = database.dao()

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
            items.filter { it.kind == ItemKind.TODO && it.lifecycle == ItemLifecycle.ACTIVE },
            relations,
        )
        AllSections(
            unsorted = items.filter { it.kind == ItemKind.UNSORTED && it.lifecycle == ItemLifecycle.ACTIVE }
                .sortedBy { it.manualRank },
            todos = activeTodos,
            memos = items.filter { it.kind == ItemKind.MEMO && it.lifecycle == ItemLifecycle.ACTIVE }
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

    override suspend fun quickAdd(kind: ItemKind, text: String): AppItem? {
        val clean = text.trim()
        if (clean.isEmpty()) return null
        val title = clean.take(200)
        val body = if (clean.length > 200) clean.drop(200).trim() else ""
        val now = now()
        val rank = (dao.getItems().maxOfOrNull { it.manualRank } ?: 0L) + 1_000L
        val item = AppItem(
            id = newId(), kind = kind, title = title, body = body, manualRank = rank,
            createdAtEpochMillis = now, updatedAtEpochMillis = now,
            todo = if (kind == ItemKind.TODO) TodoDetail() else null,
        )
        mutate("CREATE") { upsert(item) }
        return item
    }

    override suspend fun save(item: AppItem, requiredBeforeIds: Set<String>?): AppItem {
        val clean = item.copy(
            title = item.title.trim().take(200),
            body = item.body.take(100_000),
            updatedAtEpochMillis = now(),
            revision = item.revision + 1,
            todo = if (item.kind == ItemKind.TODO) item.todo ?: TodoDetail() else null,
        )
        require(clean.title.isNotEmpty()) { "タイトルを入力してください" }
        if (requiredBeforeIds != null) {
            val state = currentState()
            val unrelated = state.relations.filterNot { it.toItemId == clean.id && it.type == jp.oboegaki.core.model.RelationType.REQUIRED_BEFORE }
            requiredBeforeIds.forEach { prerequisiteId ->
                if (OrderingPolicy.wouldCreateCycle(unrelated, prerequisiteId, clean.id)) {
                    throw IllegalArgumentException("前後関係が循環するため保存できません")
                }
            }
            mutate("UPDATE") {
                upsert(clean)
                dao.deleteRequiredPrerequisites(clean.id)
                requiredBeforeIds.forEach { prerequisiteId ->
                    dao.upsertRelation(ItemRelation(
                        id = newId(), fromItemId = prerequisiteId, toItemId = clean.id,
                        type = jp.oboegaki.core.model.RelationType.REQUIRED_BEFORE,
                        createdAtEpochMillis = now(),
                    ).toEntity())
                }
            }
        } else mutate("UPDATE") { upsert(clean) }
        syncReminder(clean)
        return clean
    }

    override suspend fun delete(id: String) {
        val item = getItem(id) ?: return
        mutate("DELETE") { upsert(item.copy(lifecycle = ItemLifecycle.DELETED, updatedAtEpochMillis = now())) }
        reminderScheduler.cancel(id)
    }

    override suspend fun complete(id: String) {
        val item = getItem(id) ?: return
        val time = now()
        mutate("COMPLETE") {
            upsert(item.copy(lifecycle = ItemLifecycle.COMPLETED, completedAtEpochMillis = time, updatedAtEpochMillis = time))
        }
        reminderScheduler.cancel(id)
    }

    override suspend fun defer(id: String, threshold: Int): DeferDecision? {
        val state = currentState()
        val items = OrderingPolicy.canonicalSort(
            state.items.filter { it.kind == ItemKind.TODO && it.lifecycle == ItemLifecycle.ACTIVE },
            state.relations,
        )
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return null
        val decision = DeferPolicy.decide(items[index], index, items.size, threshold)
        val destination = items.getOrNull(decision.destinationIndex)
        val rank = destination?.manualRank?.plus(1) ?: decision.updated.manualRank
        val updated = decision.updated.copy(manualRank = rank, updatedAtEpochMillis = now())
        mutate("DEFER") { upsert(updated) }
        return decision.copy(updated = updated)
    }

    override suspend fun convertMemo(id: String): AppItem? {
        val memo = getItem(id) ?: return null
        if (memo.kind != ItemKind.MEMO) return null
        val time = now()
        val created = AppItem(
            id = newId(), kind = ItemKind.TODO, title = memo.title, body = memo.body,
            manualRank = (dao.getItems().maxOfOrNull { it.manualRank } ?: 0L) + 1_000L,
            convertedFromId = memo.id, createdAtEpochMillis = time, updatedAtEpochMillis = time,
            todo = TodoDetail(),
        )
        mutate("CONVERT_MEMO") {
            upsert(memo.copy(lifecycle = ItemLifecycle.CONVERTED, updatedAtEpochMillis = time))
            upsert(created)
        }
        return created
    }

    override suspend fun archiveMemo(id: String) {
        val memo = getItem(id) ?: return
        val time = now()
        mutate("ARCHIVE_MEMO") {
            upsert(memo.copy(lifecycle = ItemLifecycle.ARCHIVED, archivedAtEpochMillis = time, updatedAtEpochMillis = time))
        }
    }

    override suspend fun restore(id: String) {
        val item = getItem(id) ?: return
        mutate("RESTORE") {
            upsert(item.copy(
                lifecycle = ItemLifecycle.ACTIVE,
                completedAtEpochMillis = null,
                archivedAtEpochMillis = null,
                updatedAtEpochMillis = now(),
            ))
        }
    }

    override suspend fun validateMove(id: String, destinationIndex: Int): MoveDecision {
        val state = currentState()
        val items = OrderingPolicy.canonicalSort(
            state.items.filter { it.kind == ItemKind.TODO && it.lifecycle == ItemLifecycle.ACTIVE },
            state.relations,
        )
        return OrderingPolicy.validateMove(items, state.relations, id, destinationIndex)
    }

    override suspend fun move(id: String, destinationIndex: Int): MoveDecision {
        val decision = validateMove(id, destinationIndex)
        if (decision is MoveDecision.Allowed) {
            val item = getItem(id) ?: return MoveDecision.Rejected(
                jp.oboegaki.core.domain.MoveRejectionReason.INVALID_INDEX, "やることが見つかりません",
            )
            mutate("MOVE") { upsert(item.copy(manualRank = decision.newRank, updatedAtEpochMillis = now())) }
        }
        return decision
    }

    override suspend fun moveFree(id: String, destinationIndex: Int) {
        val item = getItem(id) ?: return
        if (item.kind == ItemKind.TODO || item.lifecycle != ItemLifecycle.ACTIVE) return
        val group = currentState().items
            .filter { it.kind == item.kind && it.lifecycle == ItemLifecycle.ACTIVE }
            .sortedBy { it.manualRank }
        if (destinationIndex !in group.indices) return
        val moved = group.toMutableList().apply {
            val source = indexOfFirst { it.id == id }
            if (source < 0) return
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
        mutate("MOVE") { upsert(item.copy(manualRank = rank, updatedAtEpochMillis = now())) }
    }

    override suspend fun split(id: String, titles: List<String>): SplitValidation {
        val validation = SplitPolicy.validate(titles)
        if (validation !is SplitValidation.Valid) return validation
        val parent = getItem(id) ?: return SplitValidation.Invalid("元のやることが見つかりません")
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
        mutate("SPLIT") {
            upsert(parent.copy(lifecycle = ItemLifecycle.SPLIT, updatedAtEpochMillis = time))
            children.forEach { upsert(it) }
            dao.deleteRelationsForItem(parent.id)
            dao.upsertRelations(rewired.map(ItemRelation::toEntity))
        }
        reminderScheduler.cancel(id)
        children.forEach { syncReminder(it) }
        return validation
    }

    override suspend fun postponeSplitPrompt(id: String, threshold: Int) {
        val item = getItem(id) ?: return
        mutate("POSTPONE_SPLIT") { upsert(DeferPolicy.postponePrompt(item, threshold)) }
    }

    override suspend fun disableSplitPrompt(id: String) {
        val item = getItem(id) ?: return
        val detail = item.todo ?: return
        mutate("DISABLE_SPLIT") { upsert(item.copy(todo = detail.copy(splitPromptDisabled = true))) }
    }

    override suspend fun undo(): Boolean {
        val time = now()
        val operation = dao.getUndoableOperation(time) ?: return false
        val snapshot = runCatching { json.decodeFromString<DataSnapshot>(operation.payloadJson) }.getOrNull()
            ?: return false
        database.inTransaction {
            dao.clearRelations()
            dao.clearTodoDetails()
            dao.clearItems()
            snapshot.items.forEach { upsert(it) }
            dao.upsertRelations(snapshot.relations.map(ItemRelation::toEntity))
            dao.markOperationReverted(operation.operationId, time)
        }
        return true
    }

    override suspend fun saveSettings(settings: AppSettings) {
        val safe = settings.copy(
            splitThreshold = settings.splitThreshold.coerceIn(1, 10),
            deferItems = settings.deferItems.coerceIn(1, 20),
            undoSeconds = settings.undoSeconds.coerceIn(3, 10),
        )
        dao.upsertSetting(SettingEntity(SETTINGS_KEY, json.encodeToString(safe)))
    }

    override suspend fun saveTheme(theme: ThemeDefinition): ThemeValidation {
        val validation = ThemePolicy.validate(theme)
        if (validation is ThemeValidation.Valid) {
            val custom = theme.copy(builtIn = false)
            dao.upsertTheme(ThemeEntity(custom.id, custom.name, false, json.encodeToString(custom), now()))
        }
        return validation
    }

    override suspend fun deleteTheme(id: String) = dao.deleteCustomTheme(id)

    override suspend fun seedIfEmpty() {
        if (dao.getItems().isNotEmpty()) return
        val time = now()
        val samples = listOf(
            sampleTodo("見積書の金額を確認する", time, 1_000, Priority.HIGH, 10),
            sampleTodo("メールに返信する", time, 2_000, Priority.NORMAL, 10),
            sampleTodo("机の上を5分だけ片付ける", time, 3_000, Priority.NORMAL, 5),
            AppItem(newId(), ItemKind.MEMO, title = "読みたい本を調べる", body = "気になった題名やURLをここへ", manualRank = 1_000, createdAtEpochMillis = time, updatedAtEpochMillis = time),
            AppItem(newId(), ItemKind.UNSORTED, title = "週末の予定を考える", manualRank = 1_000, createdAtEpochMillis = time, updatedAtEpochMillis = time),
        )
        database.inTransaction { samples.forEach { upsert(it) } }
    }

    override suspend fun exportBackupJson(): String {
        val state = currentState()
        val themes = dao.getThemes().mapNotNull { runCatching { json.decodeFromString<ThemeDefinition>(it.json) }.getOrNull() }
        val settings = dao.getSetting(SETTINGS_KEY)?.let {
            runCatching { json.decodeFromString<AppSettings>(it.value) }.getOrNull()
        } ?: AppSettings()
        return json.encodeToString(BackupEnvelope(BackupManifest(createdAtEpochMillis = now()), state.items, state.relations, themes, settings))
    }

    override suspend fun importBackupJson(value: String): BackupImportResult {
        if (value.encodeToByteArray().size > 50 * 1024 * 1024) {
            return BackupImportResult(0, 0, "50MBを超えるバックアップは読み込めません")
        }
        val backup = runCatching { json.decodeFromString<BackupEnvelope>(value) }.getOrElse {
            return BackupImportResult(0, 0, "バックアップの形式を確認できません")
        }
        if (backup.manifest.schemaVersion != 1) return BackupImportResult(0, 0, "未対応のバックアップ形式です")
        val valid = backup.items.filter { it.title.isNotBlank() && it.title.length <= 200 && it.body.length <= 100_000 }
        val ids = valid.map { it.id }.toSet()
        val relations = backup.relations.filter { it.fromItemId in ids && it.toItemId in ids }
        val safeThemes = backup.themes.filter { ThemePolicy.validate(it) is ThemeValidation.Valid }
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
            dao.upsertSetting(SettingEntity(SETTINGS_KEY, json.encodeToString(backup.settings)))
        }
        reminderScheduler.reconcileAll(valid.mapNotNull(::toReminder))
        return BackupImportResult(valid.size, backup.items.size - valid.size, "${valid.size}件を読み込みました")
    }

    private suspend fun mutate(type: String, block: suspend RoomItemRepository.() -> Unit) {
        val before = currentState()
        val time = now()
        database.inTransaction {
            block()
            dao.upsertOperation(OperationEntity(
                operationId = newId(), type = type, createdAtEpochMillis = time,
                expiresAtEpochMillis = time + 10_000,
                payloadJson = json.encodeToString(before), revertedAtEpochMillis = null,
            ))
            dao.trimOperations()
        }
    }

    private suspend fun currentState(): DataSnapshot {
        val details = dao.getTodoDetails().associateBy { it.itemId }
        return DataSnapshot(
            items = dao.getItems().map { it.toModel(details[it.id]) },
            relations = dao.getRelations().map(ItemRelationEntity::toModel),
        )
    }

    private suspend fun upsert(item: AppItem) {
        dao.upsertItem(item.toEntity())
        item.todo?.let { dao.upsertTodoDetail(it.toEntity(item.id)) }
        if (item.todo == null) dao.getTodoDetail(item.id)?.let { dao.deleteTodoDetail(it) }
    }

    private fun sampleTodo(title: String, time: Long, rank: Long, priority: Priority, minutes: Int) =
        AppItem(
            id = newId(), kind = ItemKind.TODO, title = title, manualRank = rank,
            createdAtEpochMillis = time, updatedAtEpochMillis = time,
            todo = TodoDetail(priority = priority, estimatedMinutes = minutes),
        )

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
        if (item.kind != ItemKind.TODO || item.lifecycle != ItemLifecycle.ACTIVE || scheduled <= now()) return null
        return Reminder(item.id, item.title, scheduled)
    }

    private companion object { const val SETTINGS_KEY = "app_settings" }
}

private suspend fun <T> AppDatabase.inTransaction(block: suspend () -> T): T =
    useWriterConnection { connection -> connection.immediateTransaction { block() } }
