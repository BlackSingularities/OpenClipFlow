package com.openclipflow.app.update

import com.openclipflow.app.engine.VideoEffects
import com.openclipflow.app.model.FilterPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    @Test fun versionComparison() {
        assertTrue(Updates.isNewer("1.0.1", "1.0.0"))
        assertTrue(Updates.isNewer("1.0.0.12", "1.0.0"))
        assertTrue(Updates.isNewer("1.10.0", "1.9.9"))
        assertFalse(Updates.isNewer("1.0.0", "1.0.0-dev"))
        assertFalse(Updates.isNewer("0.9", "1.0.0"))
    }

    @Test fun parsesGithubRelease() {
        val json = """
            {"tag_name":"v1.2.0","html_url":"https://github.com/x/y/releases/tag/v1.2.0",
             "body":"Nowości\n- napisy\n---\nstopka",
             "assets":[{"name":"OpenClipFlow-1.2.0.apk","browser_download_url":"https://dl/apk","size":123}]}
        """.trimIndent()
        val r = Updates.parseRelease(json)
        assertEquals("1.2.0", r.version)
        assertEquals("Nowości\n- napisy", r.notes)
        assertEquals("https://dl/apk", r.assetUrl)
        assertEquals(123L, r.sizeBytes)
    }

    @Test fun filterMatricesKeepAlphaAndIdentity() {
        assertNull(VideoEffects.filterMatrix(FilterPreset.NONE, 1f))
        assertNull(VideoEffects.filterMatrix(FilterPreset.SEPIA, 0f))
        FilterPreset.entries.filter { it != FilterPreset.NONE }.forEach { f ->
            val m = VideoEffects.filterMatrix(f, 1f)!!
            assertEquals(16, m.size)
            assertEquals(1f, m[15], 0f)
        }
        // Monochromatyczny: każdy wiersz ma te same wagi luminancji.
        val mono = VideoEffects.filterMatrix(FilterPreset.MONO, 1f)!!
        assertEquals(mono[0], mono[1], 1e-5f)
        assertEquals(mono[4], mono[5], 1e-5f)
    }

    @Test fun transitionLevelRampsAtEdges() {
        assertEquals(0f, VideoEffects.transitionLevel(0, 4_000_000, 1_000_000, inT = true, outT = false), 1e-4f)
        assertEquals(1f, VideoEffects.transitionLevel(2_000_000, 4_000_000, 1_000_000, inT = true, outT = true), 1e-4f)
        assertEquals(0.5f, VideoEffects.transitionLevel(500_000, 4_000_000, 1_000_000, inT = true, outT = false), 1e-4f)
        assertEquals(1f, VideoEffects.transitionLevel(0, 4_000_000, 1_000_000, inT = false, outT = true), 1e-4f)
    }
}
