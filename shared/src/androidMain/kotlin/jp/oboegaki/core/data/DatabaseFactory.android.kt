package jp.oboegaki.core.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun databaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    val appContext = context.applicationContext
    val path = appContext.getDatabasePath("oboegaki.db").absolutePath
    return Room.databaseBuilder<AppDatabase>(appContext, path)
}

