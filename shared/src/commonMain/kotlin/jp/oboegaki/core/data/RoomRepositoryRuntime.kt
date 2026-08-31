package jp.oboegaki.core.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import jp.oboegaki.core.domain.GroupPlacementDecision
import jp.oboegaki.core.domain.GroupPolicy
import jp.oboegaki.core.domain.OrderingPolicy
import jp.oboegaki.core.domain.ReminderPolicy
import jp.oboegaki.core.model.AppItem
import jp.oboegaki.core.model.AppSettings
import jp.oboegaki.core.model.ItemRelation
import jp.oboegaki.core.model.ThemeDefinition
import jp.oboegaki.platform.NoOpReminderScheduler
import jp.oboegaki.platform.Reminder
import jp.oboegaki.platform.ReminderScheduler
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.time.Clock

/**
 * Shared Room runtime for the repository facade and its focused stores.
 *
 * Keeping the DAO, mutation lock, journal and recovery helpers here is
 * intentional: settings/themes/backups must serialize with item mutations and
 * must observe the same snapshot/restore semantics.
 */
internal class RoomRepositoryRuntime(
    val database: AppDatabase,
    val reminderScheduler: ReminderScheduler = NoOpReminderScheduler,
    val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true },
    appVersion: String = DEFAULT_APP_VERSION,
) {
    val dao: OboegakiDao = database.dao()
    val mutationMutex = Mutex()
    val backupCodec = BackupCodec(json, appVersion)

    suspend fun mutateLocked(type: String, block: suspend () -> Unit): String {
        val before = currentState()
        val time = now()
        val operationId = newId()
        database.inTransaction {
            block()
            dao.upsertOperation(
                OperationEntity(
                    operationId = operationId,
                    type = type,
                    createdAtEpochMillis = time,
                    expiresAtEpochMillis = time + OPERATION_UNDO_MILLIS,
                    payloadJson = json.encodeToString(before),
                    revertedAtEpochMillis = null,
                ),
            )
            dao.trimOperations()
        }
        return operationId
    }

    suspend fun currentState(): DataSnapshot {
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

    suspend fun restoreSnapshot(
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
            dao.upsertSetting(SettingEntity(ROOM_SETTINGS_KEY, json.encodeToString(normalizeSettings(settings))))
        }
    }

    suspend fun readSettings(): AppSettings = normalizeSettings(
        dao.getSetting(ROOM_SETTINGS_KEY)?.let { row ->
            runCatching { json.decodeFromString<AppSettings>(row.value) }.getOrNull()
        } ?: AppSettings(),
    )

    suspend fun upsert(item: AppItem) {
        dao.upsertItem(item.toEntity())
        item.todo?.let { dao.upsertTodoDetail(it.toEntity(item.id)) }
        if (item.todo == null) dao.getTodoDetail(item.id)?.let { dao.deleteTodoDetail(it) }
    }

    fun newId(): String = buildString {
        append(now().toString(16))
        append('-')
        append(Random.nextLong().toString(16))
    }

    fun now(): Long = Clock.System.now().toEpochMilliseconds()

    suspend fun syncReminder(item: AppItem) {
        val reminder = toReminder(item)
        if (reminder == null) reminderScheduler.cancel(item.id) else reminderScheduler.schedule(reminder)
    }

    fun toReminder(item: AppItem): Reminder? {
        val scheduled = item.todo?.scheduledAtEpochMillis ?: return null
        if (!ReminderPolicy.isEligible(item, now())) return null
        return Reminder(item.id, item.title, scheduled, item.revision)
    }

    suspend fun insertPrerequisites(itemId: String, prerequisiteIds: Set<String>, time: Long) {
        prerequisiteIds.forEach { prerequisiteId ->
            dao.upsertRelation(
                ItemRelation(
                    id = newId(),
                    fromItemId = prerequisiteId,
                    toItemId = itemId,
                    type = jp.oboegaki.core.model.RelationType.REQUIRED_BEFORE,
                    createdAtEpochMillis = time,
                ).toEntity(),
            )
        }
    }

    suspend fun reconcileReminders(previous: List<AppItem>, restored: List<AppItem>) {
        val staleIds = previous.asSequence()
            .filter { it.kind == jp.oboegaki.core.model.ItemKind.TODO && !it.isGroup && it.todo?.scheduledAtEpochMillis != null }
            .map { it.id }
            .toSet()
        reminderScheduler.reconcileAll(restored.mapNotNull(::toReminder), staleIds)
    }

    private companion object {
        const val DEFAULT_APP_VERSION = "unknown"
        const val OPERATION_UNDO_MILLIS = 10_000L
    }
}

internal const val ROOM_SETTINGS_KEY: String = "app_settings"

internal suspend fun <T> AppDatabase.inTransaction(block: suspend () -> T): T =
    useWriterConnection { connection -> connection.immediateTransaction { block() } }
