package jp.oboegaki.core.data

import jp.oboegaki.core.model.AppSettings

/**
 * Applies storage-bound limits before settings enter Room or are restored from a backup.
 * Keeping this pure makes it safe to reuse from import preflight and persistence.
 */
internal fun normalizeSettings(settings: AppSettings): AppSettings = settings.copy(
    splitThreshold = settings.splitThreshold.coerceIn(1, 10),
    deferItems = settings.deferItems.coerceIn(1, 20),
    undoSeconds = settings.undoSeconds.coerceIn(3, 10),
)
