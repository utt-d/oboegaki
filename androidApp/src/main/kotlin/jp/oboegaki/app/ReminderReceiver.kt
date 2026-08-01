package jp.oboegaki.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getStringExtra("item_id") ?: return
        val title = intent.getStringExtra("title") ?: "やることの時刻です"
        val openIntent = Intent(context, MainActivity::class.java).putExtra("item_id", itemId)
        val pending = PendingIntent.getActivity(
            context, itemId.hashCode(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, OboegakiApplication.REMINDER_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText("予定したやることを確認しましょう")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(itemId.hashCode(), notification)
    }
}

