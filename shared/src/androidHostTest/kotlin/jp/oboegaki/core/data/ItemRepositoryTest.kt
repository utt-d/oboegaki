package jp.oboegaki.core.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.RoomDatabase
import jp.oboegaki.core.model.ItemKind
import jp.oboegaki.core.model.ItemLifecycle
import jp.oboegaki.core.model.TodoDetail
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Ignore("Room bundled SQLite JNI is unavailable in the JVM host; run these repository cases on an Android runtime.")
class ItemRepositoryTest {
    @Test
    fun completionRemovesRelationsWithInactiveEndpoints() = runBlocking {
        val database = testDatabase()
        try {
            val repository = RoomItemRepository(database)
            val parent = assertNotNull(repository.createGroup(ItemKind.TODO, "parent", null, TodoDetail()))
            val first = assertNotNull(repository.addDetailed(ItemKind.TODO, "first", "", parent.id, TodoDetail()))
            val second = assertNotNull(
                repository.addDetailed(
                    ItemKind.TODO,
                    "second",
                    "",
                    parent.id,
                    TodoDetail(),
                    requiredBeforeIds = setOf(first.id),
                ),
            )

            repository.complete(first.id)

            assertEquals(emptyList(), repository.observeRelations().first())
            assertEquals(ItemLifecycle.COMPLETED, repository.getItem(first.id)?.lifecycle)
            assertEquals(ItemLifecycle.ACTIVE, repository.getItem(second.id)?.lifecycle)
        } finally {
            database.close()
        }
    }

    @Test
    fun deletedLeafAndGroupRestoreWithoutFilteredStateLookup() = runBlocking {
        val database = testDatabase()
        try {
            val repository = RoomItemRepository(database)
            val group = assertNotNull(repository.createGroup(ItemKind.TODO, "group", null, TodoDetail()))
            val leaf = assertNotNull(repository.addDetailed(ItemKind.TODO, "leaf", "", group.id, TodoDetail()))

            repository.delete(leaf.id)
            repository.restore(leaf.id)
            assertEquals(ItemLifecycle.ACTIVE, repository.getItem(leaf.id)?.lifecycle)

            repository.delete(group.id)
            repository.restore(group.id)
            assertEquals(ItemLifecycle.ACTIVE, repository.getItem(group.id)?.lifecycle)
        } finally {
            database.close()
        }
    }

    @Test
    fun restoringGroupWithDeletedParentBecomesRootAndRelationsAreSanitized() = runBlocking {
        val database = testDatabase()
        try {
            val repository = RoomItemRepository(database)
            val root = assertNotNull(repository.createGroup(ItemKind.TODO, "root", null, TodoDetail()))
            val child = assertNotNull(repository.createGroup(ItemKind.TODO, "child", root.id, TodoDetail()))
            val leaf = assertNotNull(repository.addDetailed(ItemKind.TODO, "leaf", "", child.id, TodoDetail()))

            repository.delete(child.id)
            repository.delete(root.id)
            repository.restore(child.id)

            assertEquals(ItemLifecycle.ACTIVE, repository.getItem(child.id)?.lifecycle)
            assertEquals(null, repository.getItem(child.id)?.groupId)
            assertEquals(emptyList(), repository.observeRelations().first())
            assertNotNull(repository.getItem(leaf.id))
            Unit
        } finally {
            database.close()
        }
    }

    private fun testDatabase(): AppDatabase = buildDatabase(
        Room.inMemoryDatabaseBuilder<AppDatabase>(TestContext())
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE),
    )

    private class TestContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }
}
