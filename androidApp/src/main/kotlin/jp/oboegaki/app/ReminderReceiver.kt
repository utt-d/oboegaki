package jp.oboegaki.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import java.nio.ByteBuffer
import java.security.MessageDigest

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getStringExtra("item_id") ?: return
        val title = intent.getStringExtra("title") ?: "やることの時刻です"
        val openIntent = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("oboegaki://item/$itemId"))
            .putExtra("item_id", itemId)
        val pending = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, OboegakiApplication.REMINDER_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText("予定したやることを確認しましょう")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(notificationId(context, itemId), notification)
    }

    private fun notificationId(context: Context, itemId: String): Int {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val key = "$ID_PREFIX$itemId"
        preferences.getInt(key, 0).takeIf { it != 0 }?.let { return it }

        val digest = MessageDigest.getInstance("SHA-256").digest(itemId.encodeToByteArray())
        var candidate = ByteBuffer.wrap(digest).int and Int.MAX_VALUE
        if (candidate == 0) candidate = 1
        val used = preferences.all
            .filterKeys { it.startsWith(ID_PREFIX) }
            .values
            .filterIsInstance<Int>()
            .toHashSet()
        while (candidate in used || candidate == 0) {
            candidate = if (candidate == Int.MAX_VALUE) 1 else candidate + 1
        }
        preferences.edit().putInt(key, candidate).apply()
        return candidate
    }

    private companion object {
        const val PREFERENCES = "reminder_identity"
        const val ID_PREFIX = "notification_id:"
    }
}
