package jp.oboegaki.ui

internal data class MorphingDeckAlpha(
    val detail: Float,
    val preview: Float,
)

/** Keeps the two card layers as a true crossfade during a vertical drag. */
internal fun morphingDeckAlpha(
    itemPresent: Boolean,
    detailAlpha: Float,
    previewAlpha: Float,
): MorphingDeckAlpha = if (!itemPresent) {
    MorphingDeckAlpha(detail = 0f, preview = 1f)
} else {
    MorphingDeckAlpha(
        detail = detailAlpha.coerceIn(0f, 1f),
        preview = previewAlpha.coerceIn(0f, 1f),
    )
}
