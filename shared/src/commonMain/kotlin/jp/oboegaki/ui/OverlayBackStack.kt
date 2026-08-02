package jp.oboegaki.ui

internal class OverlayBackStack {
    private val previous = mutableListOf<AppOverlay>()

    fun open(current: AppOverlay?, next: AppOverlay): AppOverlay {
        if (current != null && current != next) previous += current
        return next
    }

    fun back(): AppOverlay? =
        if (previous.isEmpty()) null else previous.removeAt(previous.lastIndex)

    fun clear() {
        previous.clear()
    }
}
