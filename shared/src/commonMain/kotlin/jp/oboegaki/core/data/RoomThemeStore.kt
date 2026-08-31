package jp.oboegaki.core.data

import jp.oboegaki.core.domain.ThemePolicy
import jp.oboegaki.core.domain.ThemeValidation
import jp.oboegaki.core.model.ThemeDefinition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock

/** Owns custom theme persistence; built-in themes remain immutable constants. */
internal class RoomThemeStore(private val runtime: RoomRepositoryRuntime) {
    fun observe(): Flow<List<ThemeDefinition>> = runtime.dao.observeThemes().map { rows ->
        BuiltInThemes.all + rows.mapNotNull {
            runCatching { runtime.json.decodeFromString<ThemeDefinition>(it.json) }.getOrNull()
        }
    }

    suspend fun save(theme: ThemeDefinition): ThemeValidation = runtime.mutationMutex.withLock {
        val validation = ThemePolicy.validate(theme)
        if (validation is ThemeValidation.Valid) {
            val custom = theme.copy(builtIn = false)
            runtime.dao.upsertTheme(
                ThemeEntity(
                    id = custom.id,
                    name = custom.name,
                    builtIn = false,
                    json = runtime.json.encodeToString(custom),
                    updatedAtEpochMillis = runtime.now(),
                ),
            )
        }
        validation
    }

    suspend fun delete(id: String) = runtime.mutationMutex.withLock {
        runtime.dao.deleteCustomTheme(id)
    }
}
