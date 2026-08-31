package jp.oboegaki.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import kotlin.math.roundToInt

/** Shared release/settle motion. Direct drag frames never use this helper. */
internal object MotionAnimation {
    /** A quick deceleration keeps the release responsive without feeling abrupt. */
    val settleEasing: Easing = CubicBezierEasing(.2f, 0f, 0f, 1f)

    fun duration(baseMillis: Int, animationScale: Float, disabled: Boolean): Int =
        if (disabled || animationScale <= 0f) 1
        else (baseMillis * animationScale).roundToInt().coerceAtLeast(MIN_NORMAL_MILLIS)

    fun settleDuration(baseMillis: Int, remainingDistance: Float, fullDistance: Float): Int {
        if (baseMillis <= 1 || remainingDistance <= .5f) return 1
        val progress = (remainingDistance / fullDistance.coerceAtLeast(1f)).coerceIn(.2f, 1f)
        return (baseMillis * progress).roundToInt().coerceAtLeast(MIN_NORMAL_MILLIS)
    }

    private const val MIN_NORMAL_MILLIS = 90
}
