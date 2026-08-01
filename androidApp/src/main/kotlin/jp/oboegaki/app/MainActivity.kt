package jp.oboegaki.app

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import jp.oboegaki.core.data.RoomItemRepository
import jp.oboegaki.core.data.buildDatabase
import jp.oboegaki.core.data.databaseBuilder
import jp.oboegaki.ui.OboegakiApp

class MainActivity : ComponentActivity() {
    private val repository by lazy {
        RoomItemRepository(buildDatabase(databaseBuilder(this)), AndroidReminderScheduler(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        setContent { OboegakiApp(repository, AndroidCalendarExporter(this)) }
    }
}
