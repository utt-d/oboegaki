package jp.oboegaki.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSUUID
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.posix.O_CREAT
import platform.posix.O_RDONLY
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.open
import platform.posix.read
import platform.posix.write
import kotlin.coroutines.cancellation.CancellationException

/**
 * iOS document-picker transport for the existing portable JSON backup format.
 *
 * UIKit state is serialized on the main dispatcher. Export uses Apple's
 * forExportingURLs/asCopy flow, so the picker copies a private UTF-8 temporary
 * file and the app never rewrites the user's selected destination.
 */
@OptIn(ExperimentalForeignApi::class)
class IosBackupFileGateway(
    private val presenterProvider: () -> UIViewController?,
) : BackupFileGateway {
    override val isAvailable: Boolean = true

    private var pending: PendingOperation? = null

    override suspend fun save(content: String, suggestedName: String): BackupFileResult {
        val bytes = withContext(Dispatchers.Default) { content.encodeToByteArray() }
        if (bytes.size > BACKUP_MAX_BYTES) return BackupFileResult.TooLarge
        val tempPath = temporaryPath(suggestedName)
        return try {
            if (writeBytes(tempPath, bytes) is WriteResult.Failed) {
                removeFile(tempPath)
                BackupFileResult.Failed("バックアップを保存できませんでした")
            } else {
                suspendCancellableCoroutine<BackupFileResult> { continuation ->
                    val operation = PendingOperation.Save(
                        continuation = continuation,
                        tempPath = tempPath,
                        tempUrl = NSURL.fileURLWithPath(tempPath),
                    )
                    continuation.invokeOnCancellation {
                        CoroutineScope(Dispatchers.Main).launch { cancel(operation) }
                    }
                    CoroutineScope(Dispatchers.Main).launch {
                        if (!continuation.isActive) {
                            removeFile(tempPath)
                            return@launch
                        }
                        startSave(operation)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            removeFile(tempPath)
            throw cancelled
        }
    }

    override suspend fun open(): BackupFileResult = suspendCancellableCoroutine { continuation ->
        CoroutineScope(Dispatchers.Main).launch {
            if (!continuation.isActive) return@launch
            if (pending != null) {
                continuation.completeSafely(BackupFileResult.Failed("別のファイル操作が進行中です"))
                return@launch
            }
            val operation = PendingOperation.Open(continuation)
            continuation.invokeOnCancellation {
                CoroutineScope(Dispatchers.Main).launch { cancel(operation) }
            }
            if (!continuation.isActive) return@launch
            pending = operation
            if (!continuation.isActive) {
                cancel(operation)
                return@launch
            }
            val picker = runCatching {
                UIDocumentPickerViewController(
                    documentTypes = listOf("public.json", "public.text"),
                    inMode = platform.UIKit.UIDocumentPickerMode.UIDocumentPickerModeImport,
                )
            }.getOrElse {
                finish(operation, BackupFileResult.Failed("ファイル選択画面を開けませんでした"))
                return@launch
            }
            if (!continuation.isActive) {
                cancel(operation)
                return@launch
            }
            present(picker, operation)
            if (!continuation.isActive) cancel(operation)
        }
    }

    private fun startSave(operation: PendingOperation.Save) {
        if (!operation.continuation.isActive) {
            operation.tempPath?.let(::removeFile)
            return
        }
        if (pending != null) {
            operation.tempPath?.let(::removeFile)
            operation.continuation.completeSafely(BackupFileResult.Failed("別のファイル操作が進行中です"))
            return
        }
        pending = operation
        if (!operation.continuation.isActive) {
            cancel(operation)
            return
        }
        val picker = runCatching {
            UIDocumentPickerViewController(
                forExportingURLs = listOf(operation.tempUrl),
                asCopy = true,
            )
        }.getOrElse {
            finish(operation, BackupFileResult.Failed("ファイル選択画面を開けませんでした"))
            return
        }
        if (!operation.continuation.isActive) {
            cancel(operation)
            return
        }
        present(picker, operation)
        if (!operation.continuation.isActive) cancel(operation)
    }

    /** Must run on Dispatchers.Main. */
    private fun present(picker: UIDocumentPickerViewController, operation: PendingOperation) {
        val presenter = presenterProvider()
        if (presenter == null || presenter.presentedViewController != null) {
            finish(operation, BackupFileResult.Failed("ファイル選択画面を表示できませんでした"))
            return
        }
        operation.picker = picker
        operation.delegate = PickerDelegate(operation)
        picker.delegate = operation.delegate
        presenter.presentViewController(picker, animated = true, completion = null)
    }

    /** Must run on Dispatchers.Main. */
    private fun cancel(operation: PendingOperation) {
        if (pending === operation) {
            pending = null
            operation.picker?.let { picker ->
                picker.delegate = null
                val presenter = presenterProvider()
                if (presenter?.presentedViewController === picker) {
                    presenter.dismissViewControllerAnimated(true, completion = null)
                }
            }
        }
        operation.delegate = null
        operation.tempPath?.let(::removeFile)
    }

    /** Must run on Dispatchers.Main. */
    private fun finish(operation: PendingOperation, result: BackupFileResult) {
        if (pending !== operation) {
            operation.tempPath?.let(::removeFile)
            return
        }
        pending = null
        operation.picker?.delegate = null
        operation.delegate = null
        operation.tempPath?.let(::removeFile)
        operation.continuation.completeSafely(result)
    }

    /** Called by UIDocumentPickerDelegate on the main thread. */
    private fun selected(operation: PendingOperation, urls: List<*>) {
        val url = urls.firstOrNull() as? NSURL
        if (url == null) {
            finish(operation, BackupFileResult.Failed("ファイルを選択できませんでした"))
            return
        }
        when (operation) {
            // UIDocumentPicker copies the private temp file on success.
            is PendingOperation.Save -> finish(operation, BackupFileResult.Saved)
            is PendingOperation.Open -> CoroutineScope(Dispatchers.Default).launch {
                val result = read(url)
                withContext(Dispatchers.Main) { finish(operation, result) }
            }
        }
    }

    private suspend fun writeBytes(path: String, bytes: ByteArray): WriteResult = withContext(Dispatchers.Default) {
        try {
            val descriptor = open(path, O_WRONLY or O_CREAT or O_TRUNC, 0x180)
            if (descriptor < 0) return@withContext WriteResult.Failed
            try {
                var offset = 0
                val completed = bytes.usePinned { pinned ->
                    while (offset < bytes.size) {
                        val count = write(descriptor, pinned.addressOf(offset), (bytes.size - offset).toULong())
                        if (count <= 0) return@usePinned false
                        offset += count.toInt()
                    }
                    true
                }
                if (!completed) return@withContext WriteResult.Failed
            } finally {
                close(descriptor)
            }
            WriteResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            WriteResult.Failed
        }
    }

    private suspend fun read(url: NSURL): BackupFileResult = withContext(Dispatchers.Default) {
        runCatching {
            val accessed = url.startAccessingSecurityScopedResource()
            try {
                val path = url.path ?: return@withContext BackupFileResult.Failed("ファイルを開けませんでした")
                val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)
                val size = (attributes?.get("NSFileSize") as? Number)?.toLong()
                if (size != null && size > BACKUP_MAX_BYTES) return@withContext BackupFileResult.TooLarge
                val descriptor = open(path, O_RDONLY)
                if (descriptor < 0) return@withContext BackupFileResult.Failed("ファイルを開けませんでした")
                try {
                    val bytes = ByteArray(BACKUP_MAX_BYTES + 1)
                    var total = 0
                    bytes.usePinned { pinned ->
                        while (total < bytes.size) {
                            val count = read(descriptor, pinned.addressOf(total), (bytes.size - total).toULong())
                            if (count <= 0) break
                            total += count.toInt()
                        }
                    }
                    if (total > BACKUP_MAX_BYTES) BackupFileResult.TooLarge
                    else BackupFileResult.Content(bytes.copyOf(total).decodeToString())
                } finally {
                    close(descriptor)
                }
            } finally {
                if (accessed) url.stopAccessingSecurityScopedResource()
            }
        }.getOrElse { BackupFileResult.Failed("バックアップを読み込めませんでした") }
    }

    private fun temporaryPath(suggestedName: String): String {
        val safeName = suggestedName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .filter { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
            .take(80)
            .ifBlank { "oboegaki-backup.json" }
        val withExtension = if (safeName.endsWith(".json", ignoreCase = true)) safeName else "$safeName.json"
        return "${platform.Foundation.NSTemporaryDirectory().trimEnd('/')}/oboegaki-${NSUUID().UUIDString}-$withExtension"
    }

    private fun removeFile(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }

    private sealed interface WriteResult {
        data object Success : WriteResult
        data object Failed : WriteResult
    }

    private sealed class PendingOperation(
        val continuation: CancellableContinuation<BackupFileResult>,
        val tempPath: String? = null,
    ) {
        var picker: UIDocumentPickerViewController? = null
        var delegate: UIDocumentPickerDelegateProtocol? = null

        class Save(
            continuation: CancellableContinuation<BackupFileResult>,
            tempPath: String,
            val tempUrl: NSURL,
        ) : PendingOperation(continuation, tempPath)

        class Open(continuation: CancellableContinuation<BackupFileResult>) : PendingOperation(continuation)
    }

    private inner class PickerDelegate(private val operation: PendingOperation) : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(
            controller: UIDocumentPickerViewController,
            didPickDocumentsAtURLs: List<*>,
        ) {
            operation.picker = controller
            selected(operation, didPickDocumentsAtURLs)
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            operation.picker = controller
            finish(operation, BackupFileResult.Cancelled)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun CancellableContinuation<BackupFileResult>.completeSafely(result: BackupFileResult) {
    if (!isActive) return
    runCatching { resumeWith(Result.success(result)) }
}
