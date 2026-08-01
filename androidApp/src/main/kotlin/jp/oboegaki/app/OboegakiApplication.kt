package jp.oboegaki.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class OboegakiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(
                REMINDER_CHANNEL,
                "やることの時刻",
                NotificationManager.IMPORTANCE_DEFAULT,
            ))
        }
    }

    companion object { const val REMINDER_CHANNEL = "todo_reminders" }
}
