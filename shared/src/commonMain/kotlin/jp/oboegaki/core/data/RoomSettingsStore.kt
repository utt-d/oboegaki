package jp.oboegaki.core.data

import jp.oboegaki.core.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock

/** Persists settings while sharing the repository's single mutation lock. */
internal class RoomSettingsStore(private val runtime: RoomRepositoryRuntime) {
    fun observe(): Flow<AppSettings> = runtime.dao.observeSetting(ROOM_SETTINGS_KEY).map { row ->
        row?.let { runCatching { runtime.json.decodeFromString<AppSettings>(it.value) }.getOrNull() }
            ?.let(::normalizeSettings)
            ?: AppSettings()
    }

    suspend fun read(): AppSettings = runtime.readSettings()

    suspend fun save(settings: AppSettings) = runtime.mutationMutex.withLock {
        val safe = normalizeSettings(settings)
        runtime.dao.upsertSetting(
            SettingEntity(ROOM_SETTINGS_KEY, runtime.json.encodeToString(safe)),
        )
        runtime.reminderScheduler.applySettings(safe)
    }
}
