package jp.oboegaki.app

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import jp.oboegaki.platform.BACKUP_MAX_BYTES
import jp.oboegaki.platform.BackupFileGateway
import jp.oboegaki.platform.BackupFileResult
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Storage Access Framework adapter. No storage permission is required. */
class AndroidBackupFileGateway(activity: ComponentActivity) : BackupFileGateway {
    override val isAvailable: Boolean = true

    private var saveContinuation: kotlinx.coroutines.CancellableContinuation<BackupFileResult>? = null
    private var openContinuation: kotlinx.coroutines.CancellableContinuation<BackupFileResult>? = null

    private val createDocument = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val continuation = saveContinuation ?: return@registerForActivityResult
        saveContinuation = null
        if (uri == null) {
            pendingBytes = null
            continuation.completeSafely(BackupFileResult.Cancelled)
        } else {
            val bytes = pendingBytes
            pendingBytes = null
            if (bytes == null) {
                continuation.completeSafely(BackupFileResult.Failed("保存する内容を確認できませんでした"))
                return@registerForActivityResult
            }
            CoroutineScope(continuation.context).launch {
                continuation.completeSafely(write(uri, bytes))
            }
        }
    }

    private val openDocument = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val continuation = openContinuation ?: return@registerForActivityResult
        openContinuation = null
        if (uri == null) {
            continuation.completeSafely(BackupFileResult.Cancelled)
        } else {
            CoroutineScope(continuation.context).launch {
                continuation.completeSafely(read(uri))
            }
        }
    }

    private var pendingBytes: ByteArray? = null

    override suspend fun save(content: String, suggestedName: String): BackupFileResult {
        val bytes = withContext(Dispatchers.Default) { content.encodeToByteArray() }
        if (bytes.size > BACKUP_MAX_BYTES) return BackupFileResult.TooLarge
        return suspendCancellableCoroutine { continuation ->
            if (saveContinuation != null || openContinuation != null) {
                continuation.completeSafely(BackupFileResult.Failed("別のファイル操作が進行中です"))
                return@suspendCancellableCoroutine
            }
            pendingBytes = bytes
            saveContinuation = continuation
            continuation.invokeOnCancellation {
                if (saveContinuation === continuation) {
                    saveContinuation = null
                    pendingBytes = null
                }
            }
            runCatching { createDocument.launch(suggestedName) }.onFailure {
                saveContinuation = null
                pendingBytes = null
                continuation.completeSafely(BackupFileResult.Failed("ファイル選択画面を開けませんでした"))
            }
        }
    }

    override suspend fun open(): BackupFileResult =
        suspendCancellableCoroutine { continuation ->
            if (saveContinuation != null || openContinuation != null) {
                continuation.completeSafely(BackupFileResult.Failed("別のファイル操作が進行中です"))
                return@suspendCancellableCoroutine
            }
            openContinuation = continuation
            continuation.invokeOnCancellation { if (openContinuation === continuation) openContinuation = null }
            runCatching { openDocument.launch(arrayOf("application/json", "text/plain")) }.onFailure {
                openContinuation = null
                continuation.completeSafely(BackupFileResult.Failed("ファイル選択画面を開けませんでした"))
            }
        }

    private suspend fun write(uri: Uri, bytes: ByteArray): BackupFileResult = withContext(Dispatchers.IO) {
        runCatching {
            if (bytes.size > BACKUP_MAX_BYTES) return@withContext BackupFileResult.TooLarge
            val resolver = activity.contentResolver
            resolver.openOutputStream(uri)?.use { output ->
                output.write(bytes)
                output.flush()
            } ?: return@withContext BackupFileResult.Failed("保存先を開けませんでした")
            BackupFileResult.Saved
        }.getOrElse { BackupFileResult.Failed(it.message ?: "バックアップを保存できませんでした") }
    }

    private suspend fun read(uri: Uri): BackupFileResult = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = activity.contentResolver
            val input = resolver.openInputStream(uri) ?: return@withContext BackupFileResult.Failed("ファイルを開けませんでした")
            val bytes = input.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                var total = 0
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > BACKUP_MAX_BYTES) return@withContext BackupFileResult.TooLarge
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            BackupFileResult.Content(bytes.decodeToString())
        }.getOrElse { BackupFileResult.Failed(it.message ?: "バックアップを読み込めませんでした") }
    }

    private val activity: ComponentActivity = activity

    private fun CancellableContinuation<BackupFileResult>.completeSafely(result: BackupFileResult) {
        if (!isActive) return
        // Cancellation can race the ActivityResult callback. resumeWith is
        // guarded so a late callback never crashes the activity.
        runCatching { resumeWith(Result.success(result)) }
    }
}
