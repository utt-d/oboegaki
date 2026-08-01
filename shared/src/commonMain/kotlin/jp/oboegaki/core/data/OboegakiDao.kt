package jp.oboegaki.core.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OboegakiDao {
    @Query("SELECT * FROM items WHERE lifecycle != 'DELETED'")
    fun observeItems(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM todo_details")
    fun observeTodoDetails(): Flow<List<TodoDetailEntity>>

    @Query("SELECT * FROM item_relations")
    fun observeRelations(): Flow<List<ItemRelationEntity>>

    @Query("SELECT * FROM themes ORDER BY builtIn DESC, name")
    fun observeThemes(): Flow<List<ThemeEntity>>

    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1")
    fun observeSetting(key: String): Flow<SettingEntity?>

    @Query("SELECT * FROM items WHERE lifecycle != 'DELETED'")
    suspend fun getItems(): List<ItemEntity>

    @Query("SELECT * FROM todo_details")
    suspend fun getTodoDetails(): List<TodoDetailEntity>

    @Query("SELECT * FROM item_relations")
    suspend fun getRelations(): List<ItemRelationEntity>

    @Query("SELECT * FROM themes")
    suspend fun getThemes(): List<ThemeEntity>

    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): SettingEntity?

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    suspend fun getItem(id: String): ItemEntity?

    @Query("SELECT * FROM todo_details WHERE itemId = :id LIMIT 1")
    suspend fun getTodoDetail(id: String): TodoDetailEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: ItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<ItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTodoDetail(detail: TodoDetailEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTodoDetails(details: List<TodoDetailEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRelation(relation: ItemRelationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRelations(relations: List<ItemRelationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOperation(operation: OperationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTheme(theme: ThemeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSetting(setting: SettingEntity)

    @Delete
    suspend fun deleteTodoDetail(detail: TodoDetailEntity)

    @Query("DELETE FROM item_relations WHERE id = :id")
    suspend fun deleteRelation(id: String)

    @Query("DELETE FROM item_relations WHERE toItemId = :itemId AND type = 'REQUIRED_BEFORE'")
    suspend fun deleteRequiredPrerequisites(itemId: String)

    @Query("DELETE FROM item_relations WHERE fromItemId = :itemId OR toItemId = :itemId")
    suspend fun deleteRelationsForItem(itemId: String)

    @Query("DELETE FROM themes WHERE id = :id AND builtIn = 0")
    suspend fun deleteCustomTheme(id: String)

    @Query("SELECT * FROM operations WHERE revertedAtEpochMillis IS NULL AND expiresAtEpochMillis >= :now ORDER BY createdAtEpochMillis DESC LIMIT 1")
    suspend fun getUndoableOperation(now: Long): OperationEntity?

    @Query("UPDATE operations SET revertedAtEpochMillis = :now WHERE operationId = :id")
    suspend fun markOperationReverted(id: String, now: Long)

    @Query("DELETE FROM operations WHERE operationId NOT IN (SELECT operationId FROM operations ORDER BY createdAtEpochMillis DESC LIMIT 50)")
    suspend fun trimOperations()

    @Query("DELETE FROM item_relations")
    suspend fun clearRelations()

    @Query("DELETE FROM todo_details")
    suspend fun clearTodoDetails()

    @Query("DELETE FROM items")
    suspend fun clearItems()

    @Query("DELETE FROM themes WHERE builtIn = 0")
    suspend fun clearCustomThemes()
}
