package com.openclipflow.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DissolvesTest {
    private val v = MediaAsset(id = "v", uri = "content://v", name = "a.mp4", kind = MediaKind.VIDEO, durationMs = 20_000)
    private val photo = MediaAsset(id = "p", uri = "content://p", name = "p.jpg", kind = MediaKind.IMAGE, durationMs = 0)

    private fun project(vararg clips: Clip) = Project(assets = listOf(v, photo), clips = clips.toList())

    @Test fun usesPreRollOfNextClip() {
        val a = Clip(id = "a", assetId = "v", trackId = "v1", startMs = 0, durationMs = 4_000, sourceInMs = 0)
        val b = Clip(id = "b", assetId = "v", trackId = "v1", startMs = 4_000, durationMs = 4_000, sourceInMs = 6_000, dissolveInMs = 1_000)
        val j = Dissolves.join(project(a, b), b)!!
        assertEquals(1_000L, j.pre)
        assertEquals(0L, j.post)
        assertEquals(3_000L, j.windowStart)
        assertEquals(4_000L, j.windowEnd)
    }

    @Test fun fallsBackToPostRollOfPreviousClip() {
        // „b” zaczyna się od początku pliku – brak zapasu przed wejściem, używamy zapasu za wyjściem „a”.
        val a = Clip(id = "a", assetId = "v", trackId = "v1", startMs = 0, durationMs = 4_000, sourceInMs = 2_000)
        val b = Clip(id = "b", assetId = "v", trackId = "v1", startMs = 4_000, durationMs = 4_000, sourceInMs = 300, dissolveInMs = 1_000)
        val j = Dissolves.join(project(a, b), b)!!
        assertEquals(300L, j.pre)
        assertEquals(700L, j.post)
    }

    @Test fun limitedByHalfOfShorterClipAndMissingMaterial() {
        val a = Clip(id = "a", assetId = "v", trackId = "v1", startMs = 0, durationMs = 1_000, sourceInMs = 0)
        val b = Clip(id = "b", assetId = "v", trackId = "v1", startMs = 1_000, durationMs = 6_000, sourceInMs = 5_000, dissolveInMs = 2_000)
        assertEquals(500L, Dissolves.join(project(a, b), b)!!.total)
        // Oba klipy wykorzystują cały plik – przenikanie niemożliwe.
        val c = Clip(id = "c", assetId = "v", trackId = "v1", startMs = 0, durationMs = 20_000)
        val d = Clip(id = "d", assetId = "v", trackId = "v1", startMs = 20_000, durationMs = 20_000, dissolveInMs = 1_000)
        assertNull(Dissolves.join(project(c, d), d))
        assertEquals(0L, Dissolves.maxPossible(project(c, d), d))
    }

    @Test fun photosAlwaysHaveMaterial() {
        val a = Clip(id = "a", assetId = "p", trackId = "v1", startMs = 0, durationMs = 4_000)
        val b = Clip(id = "b", assetId = "p", trackId = "v1", startMs = 4_000, durationMs = 4_000, dissolveInMs = 1_500)
        assertEquals(1_500L, Dissolves.join(project(a, b), b)!!.pre)
    }

    @Test fun requiresAdjacentClips() {
        val a = Clip(id = "a", assetId = "p", trackId = "v1", startMs = 0, durationMs = 4_000)
        val b = Clip(id = "b", assetId = "p", trackId = "v1", startMs = 5_000, durationMs = 4_000, dissolveInMs = 1_000)
        assertNull(Dissolves.join(project(a, b), b))
        assertFalse(Dissolves.hasAny(project(a, b)))
    }

    @Test fun layersAlternateWithoutOverlapAndKeepCutPoints() {
        val a = Clip(id = "a", assetId = "p", trackId = "v1", startMs = 0, durationMs = 4_000, transitionOut = Transition.FADE_BLACK)
        val b = Clip(id = "b", assetId = "p", trackId = "v1", startMs = 4_000, durationMs = 4_000, dissolveInMs = 1_000)
        val c = Clip(id = "c", assetId = "p", trackId = "v1", startMs = 8_000, durationMs = 4_000, dissolveInMs = 1_000)
        val p = project(a, b, c)
        val items = Dissolves.layers(p)
        assertEquals(listOf(0, 1, 0), items.map { it.layer })
        val (ia, ib, ic) = items
        assertEquals(3_000L, ib.render.startMs)
        assertEquals(5_000L, ib.render.durationMs)
        assertEquals(7_000L, ic.render.startMs)
        assertEquals(Transition.NONE, ia.render.transitionOut) // przejście klipu zastąpione przenikaniem
        // Dźwięk krzyżuje się w oknie przenikania.
        assertEquals(1_000L, ib.render.audioFadeInMs)
        assertEquals(1_000L, ib.render.audioFadeOutMs)
        for (layer in 0..1) {
            val segs = Dissolves.segments(items, layer, p.durationMs)
            assertEquals(p.durationMs, segs.sumOf { it.first })
        }
        // Krycie górnej warstwy rośnie w oknie wejścia i maleje w oknie wyjścia.
        assertEquals(0f, ib.alpha(3_000), 1e-4f)
        assertEquals(0.5f, ib.alpha(3_500), 1e-4f)
        assertEquals(1f, ib.alpha(5_000), 1e-4f)
        assertEquals(0.5f, ib.alpha(7_500), 1e-4f)
        assertNotEquals(ia.layer, ib.layer)
        assertTrue(Dissolves.hasAny(p))
    }

    @Test fun preRollShiftsSourceIn() {
        val a = Clip(id = "a", assetId = "v", trackId = "v1", startMs = 0, durationMs = 4_000)
        val b = Clip(id = "b", assetId = "v", trackId = "v1", startMs = 4_000, durationMs = 4_000, sourceInMs = 10_000, speed = 2f, dissolveInMs = 1_000)
        val ib = Dissolves.layers(project(a, b))[1]
        assertEquals(8_000L, ib.render.sourceInMs) // 1 s osi × 2× = 2 s źródła wcześniej
    }

    @Test fun applyToAllSetsEveryCut() {
        val a = Clip(id = "a", assetId = "p", trackId = "v1", startMs = 0, durationMs = 4_000)
        val b = Clip(id = "b", assetId = "p", trackId = "v1", startMs = 4_000, durationMs = 4_000)
        val c = Clip(id = "c", assetId = "p", trackId = "v1", startMs = 9_000, durationMs = 4_000)
        val p = Dissolves.applyToAll(project(a, b, c), 800)
        assertEquals(0L, p.clip("a")!!.dissolveInMs)
        assertEquals(800L, p.clip("b")!!.dissolveInMs)
        assertEquals(0L, p.clip("c")!!.dissolveInMs) // nie styka się z poprzednim
    }

    @Test fun splitResetsDissolveOfRightPart() {
        val a = Clip(id = "a", assetId = "p", trackId = "v1", startMs = 0, durationMs = 4_000)
        val b = Clip(id = "b", assetId = "p", trackId = "v1", startMs = 4_000, durationMs = 4_000, dissolveInMs = 1_000)
        val p = TimelineOps.split(project(a, b), "b", 6_000)!!
        assertEquals(1_000L, p.clip("b")!!.dissolveInMs)
        assertEquals(0L, p.clipsOn("v1").last().dissolveInMs)
    }
}
