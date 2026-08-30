package jp.oboegaki.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import jp.oboegaki.core.data.AppDatabase
import jp.oboegaki.core.data.RoomItemRepository
import jp.oboegaki.core.data.buildDatabase
import jp.oboegaki.core.data.databaseBuilder
import jp.oboegaki.platform.initializeNotificationDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OboegakiApplication : Application() {
    lateinit var database: AppDatabase
        private set
    lateinit var reminderScheduler: AndroidReminderScheduler
        private set
    lateinit var repository: RoomItemRepository
        private set

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(
                REMINDER_CHANNEL,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ))
        }
        initializeNotificationDiagnostics(this)
        database = buildDatabase(databaseBuilder(this))
        reminderScheduler = AndroidReminderScheduler(this)
        repository = RoomItemRepository(
            database = database,
            reminderScheduler = reminderScheduler,
            appVersion = BuildConfig.VERSION_NAME,
        )
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { reminderScheduler.applySettings(repository.getSettings()) }
        }
    }

    companion object {
        const val REMINDER_CHANNEL = "todo_reminders"

        fun from(context: android.content.Context): OboegakiApplication =
            context.applicationContext as OboegakiApplication
    }
}
