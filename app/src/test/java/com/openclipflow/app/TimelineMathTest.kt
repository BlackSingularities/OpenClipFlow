package com.openclipflow.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineMathTest {
    @Test fun snapUsesNearestTargetInsideThreshold() {
        assertEquals(10f, TimelineMath.snap(9.8f, listOf(0f, 10f)), 0.0001f)
        assertEquals(9f, TimelineMath.snap(9f, listOf(0f, 10f)), 0.0001f)
    }

    @Test fun splitRejectsCutsAtClipEdges() {
        assertNull(TimelineMath.split(10f, 0f))
        assertNull(TimelineMath.split(10f, 10f))
        assertEquals(5f to 5f, TimelineMath.split(10f, 5f))
    }

    @Test fun trimClampsStartAndKeepsPositiveDuration() {
        val result = TimelineMath.trim(4f, 3f, newStart = 2f, newDuration = 0f)
        assertEquals(2f, result.first, 0.0001f)
        assertEquals(.1f, result.second, 0.0001f)
        assertEquals(0f, result.third, 0.0001f)
    }
}