package jp.oboegaki.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SwipeGesturePhysicsTest {
    @Test
    fun dominantAxisLocksOnlyAfterRequiredDistance() {
        assertNull(SwipeGesturePhysics.lockAxis(8f, 9f, 12f))
        assertEquals(DragAxis.VERTICAL, SwipeGesturePhysics.lockAxis(5f, 13f, 12f))
        assertEquals(DragAxis.HORIZONTAL, SwipeGesturePhysics.lockAxis(-14f, 13f, 12f))
    }

    @Test
    fun shortFastDownwardSwipeMovesToPrevious() {
        val result = SwipeGesturePhysics.resolve(
            axis = DragAxis.VERTICAL,
            distanceX = 0f,
            distanceY = 40f,
            velocityX = 0f,
            velocityY = 1_100f,
            distanceThreshold = 64f,
            velocityThreshold = 900f,
            hasPrevious = true,
            hasNext = true,
        )

        assertEquals(SwipeResult.DOWN, result)
    }

    @Test
    fun unavailableVerticalDirectionNeverCommits() {
        assertNull(
            SwipeGesturePhysics.resolve(
                axis = DragAxis.VERTICAL,
                distanceX = 0f,
                distanceY = 100f,
                velocityX = 0f,
                velocityY = 0f,
                distanceThreshold = 64f,
                velocityThreshold = 900f,
                hasPrevious = false,
                hasNext = true,
            ),
        )
    }

    @Test
    fun distanceDirectionWinsOverOpposingReleaseVelocity() {
        val result = SwipeGesturePhysics.resolve(
            axis = DragAxis.VERTICAL,
            distanceX = 0f,
            distanceY = 80f,
            velocityX = 0f,
            velocityY = -1_200f,
            distanceThreshold = 64f,
            velocityThreshold = 900f,
            hasPrevious = true,
            hasNext = true,
        )

        assertEquals(SwipeResult.DOWN, result)
    }

    @Test
    fun themeFollowChangesOnlyVisualOffset() {
        assertEquals(64f, SwipeGesturePhysics.visualOffset(80f, .8f, 200f))
        val result = SwipeGesturePhysics.resolve(
            axis = DragAxis.VERTICAL,
            distanceX = 0f,
            distanceY = 80f,
            velocityX = 0f,
            velocityY = 0f,
            distanceThreshold = 64f,
            velocityThreshold = 900f,
            hasPrevious = true,
            hasNext = true,
        )
        assertEquals(SwipeResult.DOWN, result)
    }

    @Test
    fun completedDragDoesNotWaitForInvisibleAnimation() {
        assertEquals(1, SwipeGesturePhysics.settleDuration(180, 0f, 500f))
        assertTrue(SwipeGesturePhysics.settleDuration(180, 50f, 500f) < 180)
    }
}
