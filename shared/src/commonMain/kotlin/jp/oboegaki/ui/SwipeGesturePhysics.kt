package jp.oboegaki.ui

import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class DragAxis { HORIZONTAL, VERTICAL }

internal enum class SwipeResult { RIGHT, LEFT, UP, DOWN }

internal object SwipeGesturePhysics {
    fun lockAxis(distanceX: Float, distanceY: Float, lockDistance: Float): DragAxis? {
        if (maxOf(abs(distanceX), abs(distanceY)) < lockDistance) return null
        return if (abs(distanceX) > abs(distanceY)) DragAxis.HORIZONTAL else DragAxis.VERTICAL
    }

    fun resolve(
        axis: DragAxis?,
        distanceX: Float,
        distanceY: Float,
        velocityX: Float,
        velocityY: Float,
        distanceThreshold: Float,
        velocityThreshold: Float,
        hasPrevious: Boolean,
        hasNext: Boolean,
    ): SwipeResult? = when (axis) {
        DragAxis.HORIZONTAL -> when (direction(distanceX, velocityX, distanceThreshold, velocityThreshold)) {
            1 -> SwipeResult.RIGHT
            -1 -> SwipeResult.LEFT
            else -> null
        }
        DragAxis.VERTICAL -> when (direction(distanceY, velocityY, distanceThreshold, velocityThreshold)) {
            1 -> if (hasPrevious) SwipeResult.DOWN else null
            -1 -> if (hasNext) SwipeResult.UP else null
            else -> null
        }
        null -> null
    }

    fun visualOffset(rawDistance: Float, follow: Float, limit: Float): Float =
        (rawDistance * follow.coerceIn(.1f, 1f)).coerceIn(-limit, limit)

    fun settleDuration(baseDurationMillis: Int, remainingDistance: Float, fullDistance: Float): Int {
        if (baseDurationMillis <= 1 || remainingDistance <= .5f) return 1
        val progress = (remainingDistance / fullDistance.coerceAtLeast(1f)).coerceIn(.2f, 1f)
        return (baseDurationMillis * progress).roundToInt().coerceAtLeast(1)
    }

    private fun direction(
        distance: Float,
        velocity: Float,
        distanceThreshold: Float,
        velocityThreshold: Float,
    ): Int = when {
        abs(distance) >= distanceThreshold -> if (distance > 0f) 1 else -1
        abs(velocity) >= velocityThreshold -> if (velocity > 0f) 1 else -1
        else -> 0
    }
}
