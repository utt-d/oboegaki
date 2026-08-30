package jp.oboegaki.platform

/** The result of a user-selected backup file operation. */
sealed interface BackupFileResult {
    data class Content(val value: String) : BackupFileResult
    data object Saved : BackupFileResult
    data object Cancelled : BackupFileResult
    data object TooLarge : BackupFileResult
    data class Failed(val reason: String) : BackupFileResult
}

/**
 * Platform file selection boundary for backups.
 *
 * JSON creation, validation and database replacement stay in common code;
 * implementations only provide the user-selected file transport.
 */
interface BackupFileGateway {
    val isAvailable: Boolean

    suspend fun save(content: String, suggestedName: String = "oboegaki-backup.json"): BackupFileResult
    suspend fun open(): BackupFileResult
}

object NoOpBackupFileGateway : BackupFileGateway {
    override val isAvailable: Boolean = false

    override suspend fun save(content: String, suggestedName: String): BackupFileResult =
        BackupFileResult.Failed("この端末ではファイル保存を利用できません")

    override suspend fun open(): BackupFileResult =
        BackupFileResult.Failed("この端末ではファイル選択を利用できません")
}

const val BACKUP_MAX_BYTES: Int = 50 * 1024 * 1024
