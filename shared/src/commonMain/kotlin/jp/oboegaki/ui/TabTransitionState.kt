package jp.oboegaki.ui

import kotlin.math.roundToInt

/**
 * The render anchor is kept together with the drag offset.  Updating these
 * values as one immutable object prevents a tab StateFlow update from being
 * observed while the old page is still offset by one viewport.
 */
internal enum class TabTransitionPhase {
    IDLE,
    DRAGGING,
    SETTLING,
}

internal data class TabTransitionRenderState(
    val anchorTab: MainTab,
    val offsetPx: Float = 0f,
    val generation: Int = 0,
    val phase: TabTransitionPhase = TabTransitionPhase.IDLE,
    val targetTab: MainTab? = null,
)

internal fun TabTransitionRenderState.invalidate(anchorTab: MainTab): TabTransitionRenderState = copy(
    anchorTab = anchorTab,
    offsetPx = 0f,
    generation = generation + 1,
    phase = TabTransitionPhase.IDLE,
    targetTab = null,
)

internal fun TabTransitionRenderState.beginDrag(source: MainTab): TabTransitionRenderState? =
    if (anchorTab != source) {
        null
    } else {
        copy(
            offsetPx = 0f,
            generation = generation + 1,
            phase = TabTransitionPhase.DRAGGING,
            targetTab = null,
        )
    }

internal fun TabTransitionRenderState.updateDrag(
    source: MainTab,
    generation: Int,
    offsetPx: Float,
): TabTransitionRenderState? =
    if (anchorTab != source || this.generation != generation || phase != TabTransitionPhase.DRAGGING) {
        null
    } else {
        copy(offsetPx = offsetPx)
    }

internal fun TabTransitionRenderState.beginSettle(
    source: MainTab,
    generation: Int,
    target: MainTab,
): TabTransitionRenderState? =
    if (anchorTab != source || this.generation != generation || phase != TabTransitionPhase.DRAGGING) {
        null
    } else {
        copy(phase = TabTransitionPhase.SETTLING, targetTab = target)
    }

internal fun TabTransitionRenderState.beginProgrammatic(
    source: MainTab,
    target: MainTab,
): TabTransitionRenderState? =
    if (anchorTab != source || phase != TabTransitionPhase.IDLE) {
        null
    } else {
        copy(
            generation = generation + 1,
            phase = TabTransitionPhase.SETTLING,
            targetTab = target,
        )
    }

internal fun TabTransitionRenderState.updateSettle(
    source: MainTab,
    generation: Int,
    offsetPx: Float,
): TabTransitionRenderState? =
    if (anchorTab != source || this.generation != generation || phase != TabTransitionPhase.SETTLING) {
        null
    } else {
        copy(offsetPx = offsetPx)
    }

/**
 * Commits the tab and clears its offset in the same immutable state update.
 * A second commit for the same generation is rejected because the state is
 * already IDLE and no longer carries a target.
 */
internal fun TabTransitionRenderState.commit(
    source: MainTab,
    generation: Int,
    target: MainTab,
): TabTransitionRenderState? =
    if (
        anchorTab != source ||
        this.generation != generation ||
        phase != TabTransitionPhase.SETTLING ||
        targetTab != target
    ) {
        null
    } else {
        copy(
            anchorTab = target,
            offsetPx = 0f,
            phase = TabTransitionPhase.IDLE,
            targetTab = null,
        )
    }

/**
 * Computes a page's screen position from one render snapshot.
 */
internal fun tabPageOffset(
    pageIndex: Int,
    tabOrder: List<MainTab>,
    state: TabTransitionRenderState,
    viewportWidth: Int,
): Int {
    val anchorIndex = tabOrder.indexOf(state.anchorTab)
    if (anchorIndex < 0) return state.offsetPx.roundToInt()
    return (pageIndex - anchorIndex) * viewportWidth + state.offsetPx.roundToInt()
}
