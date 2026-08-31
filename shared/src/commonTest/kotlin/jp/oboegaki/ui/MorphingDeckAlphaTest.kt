package jp.oboegaki.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class MorphingDeckAlphaTest {
    @Test
    fun detailAndPreviewRemainVisibleAtMidpoint() {
        assertEquals(MorphingDeckAlpha(.5f, .5f), morphingDeckAlpha(true, .5f, .5f))
    }

    @Test
    fun missingPreviewItemStillUsesUnavailableLayer() {
        assertEquals(MorphingDeckAlpha(0f, 1f), morphingDeckAlpha(false, .8f, .2f))
    }
}
