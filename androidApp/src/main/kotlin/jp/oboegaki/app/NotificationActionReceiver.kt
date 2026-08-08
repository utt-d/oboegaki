package jp.oboegaki.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import jp.oboegaki.core.model.AppSettings
import jp.oboegaki.core.model.NotificationAction
import jp.oboegaki.core.model.NotificationActionResult
import jp.oboegaki.core.model.NotificationUndoResult
import jp.oboegaki.core.model.NotificationUndoToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val finished = AtomicBoolean(false)
        fun finishOnce() {
            if (finished.compareAndSet(false, true)) {
                runCatching { pendingResult.finish() }
            }
        }

        try {
            val app = OboegakiApplication.from(context)
            val workScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                workScope.launch {
                    try {
                        if (intent.action == NotificationContract.ACTION_UNDO) {
                            handleUndo(context, app, intent)
                        } else {
                            handleAction(context, app, intent)
                        }
                    } catch (_: Throwable) {
                        val itemId = intent.getStringExtra(NotificationContract.EXTRA_ITEM_ID).orEmpty()
                        showFailureSafely(context, app, itemId, context.getString(R.string.notification_action_failed))
                    } finally {
                        finishOnce()
                        runCatching { workScope.cancel() }
                    }
                }
            } catch (_: Throwable) {
                runCatching { workScope.cancel() }
                finishOnce()
            }
        } catch (_: Throwable) {
            finishOnce()
        }
    }

    private suspend fun handleAction(
        context: Context,
        app: OboegakiApplication,
        intent: Intent,
    ) {
        val itemId = intent.getStringExtra(NotificationContract.EXTRA_ITEM_ID)
            ?.takeIf(String::isNotEmpty) ?: return
        val action = when (intent.action) {
            NotificationContract.ACTION_COMPLETE -> NotificationAction.COMPLETE
            NotificationContract.ACTION_DEFER -> NotificationAction.DEFER
            else -> return
        }
        if (!intent.hasExtra(NotificationContract.EXTRA_REVISION)) return
        val expectedRevision = intent.getLongExtra(NotificationContract.EXTRA_REVISION, 0L)
        val result = runCatching {
            app.repository.performNotificationAction(action, itemId, expectedRevision)
        }.getOrElse { NotificationActionResult.Failed(context.getString(R.string.notification_action_failed)) }
        val notificationId = NotificationIdentity.notificationId(context, itemId)
        ReminderNotificationSupport.cancelSafely(context, notificationId)
        when (result) {
            is NotificationActionResult.Applied -> showResult(
                context,
                app,
                notificationId,
                result.itemId,
                result.title,
                when {
                    result.action == NotificationAction.DEFER && result.shouldSuggestSplit ->
                        context.getString(R.string.notification_split_result)
                    result.action == NotificationAction.COMPLETE ->
                        context.getString(R.string.notification_complete_result)
                    else -> context.getString(R.string.notification_defer_result)
                },
                result.action.name,
                result.undoToken.operationId,
                result.undoToken.expiresAtEpochMillis,
            )
            NotificationActionResult.ActionsDisabled -> showFailure(
                context, app, notificationId, itemId,
                context.getString(R.string.notification_action_disabled),
            )
            NotificationActionResult.ItemNotFound -> showFailure(
                context, app, notificationId, itemId,
                context.getString(R.string.notification_item_not_found),
            )
            is NotificationActionResult.ItemNotActive -> showFailure(
                context, app, notificationId, itemId,
                context.getString(R.string.notification_item_not_active),
            )
            is NotificationActionResult.ItemNotEligible -> showFailure(
                context, app, notificationId, itemId,
                context.getString(R.string.notification_not_eligible),
            )
            NotificationActionResult.StaleNotification -> showFailure(
                context, app, notificationId, itemId,
                context.getString(R.string.notification_stale),
            )
            is NotificationActionResult.Failed -> showFailure(
                context, app, notificationId, itemId, result.reason,
            )
        }
    }

    private suspend fun handleUndo(
        context: Context,
        app: OboegakiApplication,
        intent: Intent,
    ) {
        val itemId = intent.getStringExtra(NotificationContract.EXTRA_ITEM_ID)
            ?.takeIf(String::isNotEmpty) ?: return
        val operationId = intent.getStringExtra(NotificationContract.EXTRA_OPERATION_ID)
            ?.takeIf(String::isNotEmpty) ?: return
        val action = runCatching {
            NotificationAction.valueOf(
                intent.getStringExtra(NotificationContract.EXTRA_NOTIFICATION_ACTION).orEmpty(),
            )
        }.getOrNull() ?: return
        val expiresAt = intent.getLongExtra(NotificationContract.EXTRA_EXPIRES_AT, 0L)
        val notificationId = NotificationIdentity.notificationId(context, itemId)
        val settings = runCatching { app.repository.getSettings() }.getOrDefault(AppSettings())
        val result = if (!settings.reminderNotificationActionsEnabled) {
            NotificationUndoResult.Failed(context.getString(R.string.notification_action_disabled))
        } else {
            runCatching {
                app.repository.undoNotification(NotificationUndoToken(operationId, itemId, action, expiresAt))
            }.getOrElse {
                NotificationUndoResult.Failed(context.getString(R.string.notification_undo_failed))
            }
        }
        ReminderNotificationSupport.cancelSafely(context, notificationId)
        val title = runCatching { app.repository.getItem(itemId)?.title.orEmpty() }.getOrDefault("")
        when (result) {
            NotificationUndoResult.Applied -> showResult(
                context, app, notificationId, itemId, title,
                context.getString(R.string.notification_restored_result), null, null, null,
            )
            NotificationUndoResult.Expired -> showFailure(
                context, app, notificationId, itemId,
                context.getString(R.string.notification_undo_expired),
            )
            NotificationUndoResult.AlreadyReverted -> showFailure(
                context, app, notificationId, itemId,
                context.getString(R.string.notification_undo_reverted),
            )
            NotificationUndoResult.DifferentOperationAlreadyHappened -> showFailure(
                context, app, notificationId, itemId,
                context.getString(R.string.notification_undo_later),
            )
            NotificationUndoResult.NotFound -> showFailure(
                context, app, notificationId, itemId,
                context.getString(R.string.notification_undo_not_found),
            )
            is NotificationUndoResult.Failed -> showFailure(
                context, app, notificationId, itemId, result.reason,
            )
        }
    }

    private suspend fun showResult(
        context: Context,
        app: OboegakiApplication,
        notificationId: Int,
        itemId: String,
        title: String,
        message: String,
        undoAction: String?,
        operationId: String?,
        expiresAt: Long?,
    ) {
        val settings = runCatching { app.repository.getSettings() }.getOrDefault(AppSettings())
        ReminderNotificationSupport.notifySafely(
            context,
            notificationId,
            ReminderNotificationSupport.createActionResultNotification(
                context = context.applicationContext,
                itemId = itemId,
                title = title,
                message = message,
                showContent = settings.showReminderContentOnLockScreen,
                actionsEnabled = settings.reminderNotificationActionsEnabled,
                undoAction = undoAction,
                operationId = operationId,
                expiresAt = expiresAt,
            ),
        )
    }

    private suspend fun showFailure(
        context: Context,
        app: OboegakiApplication,
        notificationId: Int,
        itemId: String,
        reason: String,
    ) = showResult(context, app, notificationId, itemId, reason, reason, null, null, null)

    private suspend fun showFailureSafely(
        context: Context,
        app: OboegakiApplication,
        itemId: String,
        reason: String,
    ) {
        val notificationId = itemId.takeIf(String::isNotEmpty)?.let {
            NotificationIdentity.notificationId(context, it)
        } ?: return
        try {
            showFailure(context, app, notificationId, itemId, reason)
        } catch (_: Throwable) {
            ReminderNotificationSupport.cancelSafely(context, notificationId)
        }
    }
}
