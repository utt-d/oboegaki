package jp.oboegaki.ui

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/** Semantic haptic intents used by gestures; each gesture emits at most one. */
enum class AppHapticIntent {
    TICK,
    CONFIRM,
    REJECT,
}

fun HapticFeedback.performAppHaptic(intent: AppHapticIntent) {
    performHapticFeedback(
        when (intent) {
            AppHapticIntent.TICK -> HapticFeedbackType.SegmentTick
            AppHapticIntent.CONFIRM -> HapticFeedbackType.Confirm
            AppHapticIntent.REJECT -> HapticFeedbackType.Reject
        },
    )
}
